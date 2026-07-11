package com.zack.recomptracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.rebalance.RebalanceStore
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import com.zack.recomptracker.data.repository.toPlanTargets
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.rebalance.EffectiveTargets
import com.zack.recomptracker.domain.rebalance.PlanDayInfo
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.adjustment.AdjustmentEngine
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentThresholds
import androidx.compose.runtime.Immutable
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.adherence.NutritionDay
import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.domain.trend.PerformancePoint
import com.zack.recomptracker.domain.trend.RecoveryPoint
import com.zack.recomptracker.domain.trend.TrendCalculator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@Immutable
data class DayCalories(
    val label: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val isToday: Boolean,
)

data class DashboardUiState(
    /** The calendar day this state describes; advances at midnight so the header date stays live. */
    val today: LocalDate = LocalDate.now(),
    val preferences: PlanPreferences = PlanPreferences(),
    val todayTotals: MacroTotals = MacroTotals(),
    val todaySteps: Int = 0,
    val sevenDayWeightAverage: Double? = null,
    val weightTrendKgPerWeek: Double = 0.0,
    val waistTrendCmPerWeek: Double = 0.0,
    val adherencePercent: Double = 0.0,
    val loggedDaysInWindow: Int = 0,   // food-logged days within the last 14 (matches adherence window)
    val last7DaysCalories: ImmutableList<DayCalories> = persistentListOf(),
    val inZoneDays7: Int = 0,
    val result: AdjustmentResult = AdjustmentResult(
        verdict = AdjustmentVerdict.WAIT_FOR_DATA,
        recommendedCalorieChange = 0,
        reasonCodes = listOf("NO_DATA"),
        summary = "Log today to start building a review window.",
    ),
    val motivationalMessage: String = "",   // display-only, at end
    val adjustmentInput: AdjustmentInput? = null,
    /**
     * "Rebalance · Day X of Y" info for today when an active rebalance covers it, else null.
     * Data only — Task 7 renders the card; this ViewModel just supplies the position.
     */
    val rebalanceToday: PlanDayInfo? = null,
)

/** Profile visual for the dashboard header avatar. */
data class HeaderAvatar(val photoUri: String?, val initials: String?)

/**
 * Derives up-to-two-letter initials from a display name: first + last word's first
 * letters (or a single letter for one word), uppercased. Null when there's no name.
 */
internal fun initialsOf(name: String?): String? {
    val parts = name?.trim().orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> null
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val trendCalculator: TrendCalculator,
    private val adherenceCalculator: AdherenceCalculator,
    private val adjustmentEngine: AdjustmentEngine,
    private val aiInsightCoordinator: AiInsightCoordinator,
    private val userProfileStore: UserProfilePreferencesStore,
    // Rebalance state overlays effective (reduced) targets on the today ring, adherence tile,
    // in-zone-7, and 7-day chart, and supplies today's "Rebalance · Day X of Y" info. The
    // AdjustmentEngine input stays on BASE targets (a 2–5 day blip must not perturb the verdict).
    private val rebalanceStore: RebalanceStore,
    // Off-main dispatcher for the combine transform + debounce (list filters, LocalDate.parse over
    // 14–28-day windows, trend/adherence/adjustment math). Injectable so tests can pass their
    // TestDispatcher; default keeps AppContainer/call sites unchanged.
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** Profile photo + initials for the header avatar; updates live with the profile. */
    val headerAvatar: StateFlow<HeaderAvatar> =
        userProfileStore.preferences
            .map { HeaderAvatar(photoUri = it.profilePhotoUri, initials = initialsOf(it.name)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeaderAvatar(null, null))

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Picked once at ViewModel construction — stable for the whole session.
    private val todayMessage: String = MOTIVATIONAL_MESSAGES.random()

    // Cached AdjustmentEngine — recreated only when thresholds change, not on every meal add
    private var cachedEngineThresholds: AdjustmentThresholds? = null
    private var cachedEngine: AdjustmentEngine = adjustmentEngine

    // Guard state for persistWeeklyReview — skips the DB write when verdict hasn't changed
    private var lastPersistedVerdict: AdjustmentVerdict? = null
    private var lastPersistedChange: Int = Int.MIN_VALUE

    init {
        viewModelScope.launch {
            // Re-subscribe the whole pipeline when the calendar day rolls (P1-10): a tab ViewModel
            // outlives midnight, so a frozen windowStart/today kept aggregating the opening day.
            // flatMapLatest recomputes windowStart and re-runs buildState against the new day.
            dateProvider.todayFlow().flatMapLatest { today ->
                val windowStart = today.minusDays(27)
                combine(
                    logRepository.observeDailyLogs(),
                    logRepository.observeMealEntriesSince(windowStart),
                    logRepository.observePerformances(),
                    planRepository.preferences,
                    // The 5-slot combine is full, so fold the plan-version history + rebalance state
                    // into one nested typed combine and unpack it below (mirrors ProgressViewModel's
                    // TrainingInputs pattern) — no unchecked casts.
                    combine(
                        planRepository.observeVersions(),
                        rebalanceStore.state,
                    ) { versions, rebalanceState -> DashboardStreams(versions, rebalanceState) },
                ) { logs, allMeals, performances, preferences, streams ->
                    val (versions, rebalanceState) = streams
                    buildState(
                        today = today,
                        logs = logs,
                        allMeals = allMeals,
                        performances = performances,
                        preferences = preferences,
                        versions = versions,
                        rebalanceState = rebalanceState,
                    )
                }
            }
            .debounce(300L)
            // Run the combine transform and debounce off the main thread; the terminal collect
            // still resumes on viewModelScope's main dispatcher to publish state.
            .flowOn(computeDispatcher)
            .collect { state ->
                _uiState.value = state
                persistWeeklyReview(state)
            }
        }
    }

    private fun buildState(
        today: LocalDate,
        logs: List<DailyLogEntity>,
        allMeals: List<MealEntryEntity>,
        performances: List<LiftPerformanceEntity>,
        preferences: PlanPreferences,
        versions: List<PlanVersion>,
        rebalanceState: RebalanceState,
    ): DashboardUiState {
        // Planned (not-yet-eaten) entries never count toward reality — totals, adherence, trend.
        val meals = allMeals.filterNot { it.planned }
        val todayTotals = meals.filter { it.date == today.toString() }.macroTotals()
        val todaySteps = logs.lastOrNull { it.localDate() == today }?.steps ?: 0
        val last14Start = today.minusDays(13)
        val last28Start = today.minusDays(27)
        val last7Start  = today.minusDays(6)
        val logsLast28  = logs.filter { it.localDate() in last28Start..today }
        val mealsLast14 = meals.filter { it.localDate() in last14Start..today }
        val mealsByDate = mealsLast14.groupBy { it.localDate() }
        // BASE per-day targets (what the permanent plan says) — feeds the AdjustmentEngine input.
        val dayTargets = PlanHistory.resolve(
            versions,
            (0..13).map { last14Start.plusDays(it.toLong()) } + (0..6).map { last7Start.plusDays(it.toLong()) },
        )
        // EFFECTIVE per-day targets (reduced on rebalance days) — feeds the display surfaces: the
        // adherence tile, in-zone-7, and today's ring. Behaviour-neutral with an empty state.
        val effectiveTargets = EffectiveTargets.resolveAll(dayTargets, rebalanceState)

        // Adherence for the AdjustmentEngine stays on BASE targets: a 2–5 day rebalance blip must
        // never perturb the long-horizon recomp verdict (spec §6, AdjustmentEngine inputs = base).
        val nutritionDays = (0..13).map { offset ->
            val date = last14Start.plusDays(offset.toLong())
            NutritionDay(
                date = date,
                calories = mealsByDate[date].orEmpty().macroTotals().calories,
                targetCalories = dayTargets[date]?.calories ?: preferences.targetCalories,
            )
        }
        // Effective adherence drives the DISPLAYED tile (graded against the agreed reduced targets).
        val effectiveNutritionDays = (0..13).map { offset ->
            val date = last14Start.plusDays(offset.toLong())
            NutritionDay(
                date = date,
                calories = mealsByDate[date].orEmpty().macroTotals().calories,
                targetCalories = effectiveTargets[date]?.calories ?: preferences.targetCalories,
            )
        }
        val loggedDates = logsLast28.map { it.date }.toSet() + mealsLast14.map { it.date }.toSet()
        val weightPoints = logsLast28.map { MeasurementPoint(it.localDate(), it.bodyWeightKg) }
        val waistPoints  = logsLast28.map { MeasurementPoint(it.localDate(), it.waistCm) }
        val performancePoints = performances
            .filter { it.localDate() in last28Start..today }
            .map { PerformancePoint(it.localDate(), it.weight, it.reps, it.sets) }
        val recoveryPoints = logs
            .filter { it.localDate() in last14Start..today }
            .map { RecoveryPoint(it.localDate(), it.sleepHours, it.energyScore, it.sorenessScore) }

        val weightTrend  = trendCalculator.trendPerWeek(weightPoints)
        val waistTrend   = trendCalculator.trendPerWeek(waistPoints)
        // BASE adherence for the AdjustmentEngine; EFFECTIVE adherence for the displayed tile.
        val adherence    = adherenceCalculator.calculate(nutritionDays)
        val displayAdherence = adherenceCalculator.calculate(effectiveNutritionDays)
        val loggedDaysInWindow = nutritionDays.count { it.calories > 0 }
        val weeksSincePhaseStart = preferences.maintenancePhaseStartDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) / 7 }
            ?.toInt()
            ?: 4
        val thresholds = AdjustmentThresholds(
            weightTrendThresholdKgPerWeek = preferences.weightTrendThresholdKgPerWeek,
            waistIncreaseThresholdCmAcrossTwoWeeks = preferences.waistIncreaseThresholdCm,
            adherenceMinimumPercent = preferences.adherenceMinimumPercent,
        )
        if (thresholds != cachedEngineThresholds) {
            cachedEngineThresholds = thresholds
            cachedEngine = AdjustmentEngine(thresholds)
        }
        val adjustmentInput = AdjustmentInput(
            // Engine data-sufficiency gate: counts ANY logged day (incl. body-only) in the window.
            // Distinct from the UI's loggedDaysInWindow, which counts only food-logged days.
            daysLogged = loggedDates.count { LocalDate.parse(it) in last14Start..today },
            adherencePercent = adherence,
            weeksSincePhaseStart = weeksSincePhaseStart,
            weightTrendKgPerWeek = weightTrend,
            waistTrendCmPerWeek = waistTrend,
            performanceTrend = trendCalculator.performanceTrend(performancePoints),
            recoveryTrend = trendCalculator.recoveryTrend(recoveryPoints),
        )
        val result = cachedEngine.evaluate(adjustmentInput)

        val last7DaysCalories = (0..6).map { offset ->
            val date = last7Start.plusDays(offset.toLong())
            val dayTotals = mealsByDate[date].orEmpty().macroTotals()
            DayCalories(
                label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                calories = dayTotals.calories,
                proteinG = dayTotals.proteinG.roundToInt(),
                carbsG = dayTotals.carbsG.roundToInt(),
                fatG = dayTotals.fatG.roundToInt(),
                isToday = date == today,
            )
        }.toImmutableList()
        val inZoneDays7 = (0..6).count { offset ->
            val date = last7Start.plusDays(offset.toLong())
            // Judged against the EFFECTIVE zone (reduced on a rebalance day, base otherwise).
            val z = effectiveTargets[date] ?: preferences.toPlanTargets()
            val cals = mealsByDate[date].orEmpty().macroTotals().calories
            z.zoneLowerBound > 0 && cals > 0 && cals >= z.zoneLowerBound && cals <= z.zoneUpperBound
        }

        // Today's ring shows the EFFECTIVE target: overlay today's reduced calories/macros/zone onto
        // the exposed preferences (the ring + macro rows read state.preferences). Base today target is
        // the resolved plan for today, falling back to current prefs when the ledger isn't seeded yet.
        val baseToday = dayTargets[today] ?: preferences.toPlanTargets()
        val effectiveToday = EffectiveTargets.resolve(baseToday, today, rebalanceState)
        val effectivePreferences = preferences.copy(
            targetCalories = effectiveToday.calories,
            targetProteinG = effectiveToday.proteinG,
            targetCarbsG = effectiveToday.carbsG,
            targetFatG = effectiveToday.fatG,
            calorieZoneLowerBound = effectiveToday.zoneLowerBound,
            calorieZoneUpperBound = effectiveToday.zoneUpperBound,
        )

        return DashboardUiState(
            today = today,
            preferences = effectivePreferences,
            todayTotals = todayTotals,
            todaySteps = todaySteps,
            sevenDayWeightAverage = logs
                .filter { it.localDate() in today.minusDays(6)..today }
                .mapNotNull { it.bodyWeightKg }
                .takeIf { it.isNotEmpty() }
                ?.average(),
            weightTrendKgPerWeek = weightTrend,
            waistTrendCmPerWeek = waistTrend,
            // Displayed tile = effective adherence; the base `adherence` fed the AdjustmentEngine above.
            adherencePercent = displayAdherence,
            loggedDaysInWindow = loggedDaysInWindow,
            last7DaysCalories = last7DaysCalories,
            inZoneDays7 = inZoneDays7,
            motivationalMessage = todayMessage,
            result = result,
            adjustmentInput = adjustmentInput,
            rebalanceToday = EffectiveTargets.planDayInfo(today, rebalanceState),
        )
    }

    private suspend fun persistWeeklyReview(state: DashboardUiState) {
        val verdict = state.result.verdict
        val change = state.result.recommendedCalorieChange
        if (verdict == lastPersistedVerdict && change == lastPersistedChange) return
        lastPersistedVerdict = verdict
        lastPersistedChange = change
        val today = dateProvider.today()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        logRepository.saveWeeklyReview(
            WeeklyReviewEntity(
                weekStart = weekStart.toString(),
                verdict = verdict.name,
                recommendedCalorieChange = change,
                reasonCodes = state.result.reasonCodes.joinToString(","),
                generatedAt = Instant.now().toString(),
            ),
        )
    }

    private fun DailyLogEntity.localDate(): LocalDate = LocalDate.parse(date)
    private fun MealEntryEntity.localDate(): LocalDate = LocalDate.parse(date)
    private fun LiftPerformanceEntity.localDate(): LocalDate = LocalDate.parse(date)

    /**
     * Bundles the two flows folded into the nested combine (the top-level combine's 5 slots are
     * full). Destructured back out in the transform above.
     */
    private data class DashboardStreams(
        val versions: List<PlanVersion>,
        val rebalanceState: RebalanceState,
    )

    companion object {
        val MOTIVATIONAL_MESSAGES: List<String> = listOf(
            "Small daily improvements lead to stunning long-term results.",
            "Discipline is the bridge between goals and accomplishment.",
            "You don't have to be extreme, just consistent.",
            "Progress, not perfection.",
            "Every rep, every meal — it all compounds.",
            "The body achieves what the mind believes.",
            "Eat well. Move well. Sleep well. Repeat.",
            "Trust the process — the data doesn't lie.",
            "One more logged day. One step closer.",
            "Recomposition is a marathon, not a sprint.",
            "Fuel your body like you mean it.",
            "You showed up today. That's already a win.",
            "Strong is built one decision at a time.",
            "Consistency beats intensity every time.",
            "Log it, track it, own it.",
            "Your future self will thank you.",
            "Build habits, not excuses.",
            "The scale tells one story. The trend tells the truth.",
            "Focus on what you can control today.",
            "Every check-in is a data point in your favour.",
        )
    }
}

private operator fun ClosedRange<LocalDate>.contains(date: LocalDate): Boolean =
    !date.isBefore(start) && !date.isAfter(endInclusive)
