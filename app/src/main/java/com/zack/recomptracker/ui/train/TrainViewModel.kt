package com.zack.recomptracker.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.TrainStatsBuilder
import com.zack.recomptracker.domain.workout.TrainingPlanBuilder
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TrainTab { ROUTINES, HISTORY, STATS }

data class TrainUiState(
    val tab: TrainTab = TrainTab.ROUTINES,
    val routines: List<WorkoutTemplate> = emptyList(),
    val activeSession: WorkoutSession? = null,
    val history: List<WorkoutSession> = emptyList(),
    val statsCategories: List<TrainStatsBuilder.CategoryStats> = emptyList(),
    /** Deterministic training plan for the Train-home "Today" card (train/rest + focus + routine). */
    val plan: TrainingPlanBuilder.TrainingPlan? = null,
)

class TrainViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    private val logRepository: LogRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    dateProvider: DateProvider,
) : ViewModel() {
    private val tab = MutableStateFlow(TrainTab.ROUTINES)
    private val today: LocalDate = dateProvider.today()

    private val core = combine(
        workoutRepository.observeAll(),
        sessionRepository.observeActiveSession(),
        sessionRepository.observeCompletedSessions(),
        exerciseLibraryRepository.observeAll(),
    ) { routines, active, history, library ->
        Quad(routines, active, history, TrainStatsBuilder.build(history, library))
    }

    /**
     * The deterministic training plan for the Train-home "Today" card. Combines what was trained
     * this week (per-muscle) + training cadence + the profile's weekly target with recovery
     * (recoveryLow / deloadSuggested come from [TrainingReadinessMapper], the single recovery-scoring
     * source) → a train/rest verdict, a muscle focus, and a recommended routine. See [TrainingPlanBuilder].
     */
    private val plan: Flow<TrainingPlanBuilder.TrainingPlan?> = combine(
        workoutRepository.observeAll(),
        exerciseLibraryRepository.observeAll(),
        sessionRepository.observeCompletedSessions(),
        logRepository.observeDailyLogs(),
        userProfileStore.preferences,
    ) { routines, library, history, logs, profile ->
        val weeklyTarget = profile.weeklyGymSessions?.takeIf { it > 0 }

        // Recovery verdict from the recovery mapper (recent 14-day baseline + today's log).
        val todayLog = logs.firstOrNull { it.date == today.toString() }
        val windowStart = today.minusDays(14)
        val recent = logs.filter { l ->
            val d = runCatching { LocalDate.parse(l.date) }.getOrNull()
            d != null && !d.isBefore(windowStart) && d.isBefore(today)
        }
        val baseline = TrainingReadinessMapper.RecoveryBaseline(
            avgSorenessScore = recent.mapNotNull { it.sorenessScore }.avgIntOrNull(),
            avgSleepHours = recent.mapNotNull { it.sleepHours }.avgOrNull(),
            avgEnergyScore = recent.mapNotNull { it.energyScore }.avgIntOrNull(),
        ).takeIf { it.hasAnySignal }
        val sessionDates = history
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .filterNot { it.isAfter(today) }
        val daysSinceLast = sessionDates.maxOrNull()?.let { ChronoUnit.DAYS.between(it, today).toInt() }
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val sessionsThisWeek = sessionDates.count { !it.isBefore(weekStart) }
        val recovery = TrainingReadinessMapper.build(
            recovery = TrainingReadinessMapper.RecoveryToday(
                sorenessScore = todayLog?.sorenessScore,
                sleepHours = todayLog?.sleepHours,
                energyScore = todayLog?.energyScore,
            ),
            baseline = baseline,
            daysSinceLastSession = daysSinceLast,
            sessionsThisWeek = sessionsThisWeek,
            weeklyTarget = weeklyTarget,
        )

        TrainingPlanBuilder.build(
            history = history,
            library = library,
            routines = routines,
            today = today,
            weeklyTarget = weeklyTarget,
            recoveryLow = recovery?.level == TrainingReadinessMapper.Level.LOW,
            deloadSuggested = recovery?.mode == TrainingReadinessMapper.Mode.DELOAD,
        )
    }

    val state: StateFlow<TrainUiState> = combine(tab, core, plan) { t, c, p ->
        TrainUiState(
            tab = t,
            routines = c.routines,
            activeSession = c.active,
            history = c.history,
            statsCategories = c.stats,
            plan = p,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrainUiState())

    private data class Quad(
        val routines: List<WorkoutTemplate>,
        val active: WorkoutSession?,
        val history: List<WorkoutSession>,
        val stats: List<TrainStatsBuilder.CategoryStats>,
    )

    fun selectTab(t: TrainTab) { tab.value = t }
    fun deleteRoutine(id: Long) { viewModelScope.launch { workoutRepository.deleteWorkout(id) } }
    suspend fun startSession(template: WorkoutTemplate): Long = sessionRepository.startSession(template)
}

private fun List<Int>.avgIntOrNull(): Int? =
    if (isEmpty()) null else (sum().toDouble() / size).roundToInt()

private fun List<Double>.avgOrNull(): Double? =
    if (isEmpty()) null else sum() / size
