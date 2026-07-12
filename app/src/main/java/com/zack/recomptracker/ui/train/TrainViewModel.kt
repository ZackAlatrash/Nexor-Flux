package com.zack.recomptracker.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.RoutineShareRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.MuscleCategory
import com.zack.recomptracker.domain.workout.MuscleTrainingAggregator
import com.zack.recomptracker.domain.workout.TrainStatsBuilder
import com.zack.recomptracker.domain.workout.TrainingPlanBuilder
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TrainTab { ROUTINES, HISTORY, STATS }

/**
 * Compact per-muscle recovery read for the Train-home Training Readiness area. Derived (no new
 * input/schema) from recent completed training via [MuscleTrainingAggregator]: [recoveryScore] runs
 * `0f` (freshly hammered, still recovering) → `1f` (fully fresh); [band] is the coarse UI banding.
 */
data class MuscleRecovery(
    val category: MuscleCategory,
    val recoveryScore: Float,
    val band: MuscleTrainingAggregator.RecoveryBand,
)

data class TrainUiState(
    val tab: TrainTab = TrainTab.ROUTINES,
    val routines: List<WorkoutTemplate> = emptyList(),
    val activeSession: WorkoutSession? = null,
    val history: List<WorkoutSession> = emptyList(),
    val statsCategories: List<TrainStatsBuilder.CategoryStats> = emptyList(),
    /** Deterministic training plan for the Train-home "Today" card (train/rest + focus + routine). */
    val plan: TrainingPlanBuilder.TrainingPlan? = null,
    /** Recovery-readiness card state (headline + adapt hint); null = hide. */
    val readiness: TrainingReadinessMapper.Readiness? = null,
    /**
     * Per-muscle-group recovery (fresh vs still recovering), an added layer under the whole-body
     * readiness card. Ordered fresh-last so the UI can lead with what still needs rest. Empty history
     * → every group fresh.
     */
    val muscleRecovery: List<MuscleRecovery> = emptyList(),
    /** Whether today's recovery (soreness/sleep/energy) has been logged — drives the "Log recovery" action. */
    val recoveryLoggedToday: Boolean = false,
)

class TrainViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    private val logRepository: LogRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val routineShareRepository: RoutineShareRepository,
    dateProvider: DateProvider,
    // Off-main dispatcher for the stats/plan/readiness transforms (TrainStatsBuilder, LocalDate.parse
    // over history + logs, TrainingReadinessMapper, TrainingPlanBuilder). Injectable for tests;
    // default keeps AppContainer unchanged.
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val tab = MutableStateFlow(TrainTab.ROUTINES)

    /**
     * Builds a shareable `.rtroutine` file for [templateId] and returns its Uri, or null if the
     * routine no longer exists. The caller (UI) launches the Android share sheet with it.
     */
    suspend fun buildShareFile(templateId: Long): android.net.Uri? =
        routineShareRepository.buildShareFile(templateId)

    // dateProvider.todayFlow() is folded into both combines below so every "today"-derived value
    // (recovery, plan, "trained today") advances at midnight instead of freezing on the day the
    // Train tab was opened (P1-10).
    private val core = combine(
        workoutRepository.observeAll(),
        sessionRepository.observeActiveSession(),
        sessionRepository.observeCompletedSessions(),
        exerciseLibraryRepository.observeAll(),
        dateProvider.todayFlow(),
    ) { routines, active, history, library, today ->
        // Per-muscle recovery is derived from the same history+library already loaded here (no new
        // repo/dependency). Ordered so still-recovering groups come first for the compact UI strip.
        val recovery = MuscleTrainingAggregator.aggregate(history, library, today)
            .map { MuscleRecovery(it.category, it.recoveryScore, it.recoveryBand) }
            .sortedBy { it.recoveryScore }
        CoreState(routines, active, history, TrainStatsBuilder.build(history, library), recovery)
    }

    /**
     * The deterministic training plan for the Train-home "Today" card. Combines what was trained
     * this week (per-muscle) + training cadence + the profile's weekly target with recovery
     * (recoveryLow / deloadSuggested come from [TrainingReadinessMapper], the single recovery-scoring
     * source) → a train/rest verdict, a muscle focus, and a recommended routine. See [TrainingPlanBuilder].
     */
    private val planAndReadiness: Flow<PlanBundle> = combine(
        workoutRepository.observeAll(),
        exerciseLibraryRepository.observeAll(),
        sessionRepository.observeCompletedSessions(),
        // Bundle logs+profile into one source so the reactive "today" fits as a 5th source within
        // combine's 5-argument typed arity, so plan/readiness recomputes at midnight (P1-10).
        combine(logRepository.observeDailyLogs(), userProfileStore.preferences) { logs, profile -> logs to profile },
        dateProvider.todayFlow(),
    ) { routines, library, history, logsAndProfile, today ->
        val (logs, profile) = logsAndProfile
        val weeklyTarget = profile.weeklyGymSessions?.takeIf { it > 0 }

        // Recovery verdict from the recovery mapper (recent 14-day baseline + today's log).
        val todayLog = logs.firstOrNull { it.date == today.toString() }
        val windowStart = today.minusDays(14)
        val recent = logs.filter { l ->
            val d = runCatching { LocalDate.parse(l.date) }.getOrNull()
            d != null && !d.isBefore(windowStart) && d.isBefore(today)
        }
        val baseline = TrainingReadinessMapper.RecoveryBaseline(
            avgSorenessScore = recent.mapNotNull { it.sorenessScore }.avgOrNull(),
            avgSleepHours = recent.mapNotNull { it.sleepHours }.avgOrNull(),
            avgEnergyScore = recent.mapNotNull { it.energyScore }.avgOrNull(),
        ).takeIf { it.hasAnySignal }
        val sessionDates = history
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .filterNot { it.isAfter(today) }
        val daysSinceLast = sessionDates.maxOrNull()?.let { ChronoUnit.DAYS.between(it, today).toInt() }
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val sessionsThisWeek = sessionDates.count { !it.isBefore(weekStart) }
        val recoveryToday = TrainingReadinessMapper.RecoveryToday(
            sorenessScore = todayLog?.sorenessScore,
            sleepHours = todayLog?.sleepHours,
            energyScore = todayLog?.energyScore,
        )
        val recovery = TrainingReadinessMapper.build(
            recovery = recoveryToday,
            baseline = baseline,
            daysSinceLastSession = daysSinceLast,
            sessionsThisWeek = sessionsThisWeek,
            weeklyTarget = weeklyTarget,
        )

        PlanBundle(
            plan = TrainingPlanBuilder.build(
                history = history,
                library = library,
                routines = routines,
                today = today,
                weeklyTarget = weeklyTarget,
                recoveryLow = recovery?.level == TrainingReadinessMapper.Level.LOW,
                deloadSuggested = recovery?.mode == TrainingReadinessMapper.Mode.DELOAD,
            ),
            readiness = recovery,
            recoveryLoggedToday = recoveryToday.hasAnySignal,
        )
    }

    val state: StateFlow<TrainUiState> = combine(tab, core, planAndReadiness) { t, c, pr ->
        TrainUiState(
            tab = t,
            routines = c.routines,
            activeSession = c.active,
            history = c.history,
            statsCategories = c.stats,
            muscleRecovery = c.muscleRecovery,
            plan = pr.plan,
            readiness = pr.readiness,
            recoveryLoggedToday = pr.recoveryLoggedToday,
        )
    }
        // Runs every upstream transform (core stats + plan/readiness) off the main thread.
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrainUiState())

    private data class CoreState(
        val routines: List<WorkoutTemplate>,
        val active: WorkoutSession?,
        val history: List<WorkoutSession>,
        val stats: List<TrainStatsBuilder.CategoryStats>,
        val muscleRecovery: List<MuscleRecovery>,
    )

    private data class PlanBundle(
        val plan: TrainingPlanBuilder.TrainingPlan?,
        val readiness: TrainingReadinessMapper.Readiness?,
        val recoveryLoggedToday: Boolean,
    )

    fun selectTab(t: TrainTab) { tab.value = t }
    fun deleteRoutine(id: Long) { viewModelScope.launch { workoutRepository.deleteWorkout(id) } }
    suspend fun startSession(template: WorkoutTemplate): Long = sessionRepository.startSession(template)

    /**
     * Starts a lighter version of [template] for a reduced-recovery day: one fewer planned set per
     * exercise (never below one). Everything else (exercises, target reps/weight) is unchanged, so
     * the session is the same workout at trimmed volume.
     */
    suspend fun startLightSession(template: WorkoutTemplate): Long {
        val light = template.copy(
            exercises = template.exercises.map { ex ->
                if (ex.plannedSets.size > 1) ex.copy(plannedSets = ex.plannedSets.dropLast(1)) else ex
            },
        )
        return sessionRepository.startSession(light)
    }
}

@JvmName("avgIntOrNull")
private fun List<Int>.avgOrNull(): Double? =
    if (isEmpty()) null else sum().toDouble() / size

private fun List<Double>.avgOrNull(): Double? =
    if (isEmpty()) null else sum() / size
