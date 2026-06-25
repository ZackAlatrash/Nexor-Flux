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
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.CrossMetricContext
import com.zack.recomptracker.ai.InsightContext
import com.zack.recomptracker.ai.InsightGate
import com.zack.recomptracker.ai.InsightKind
import com.zack.recomptracker.ai.InsightRequest
import com.zack.recomptracker.ai.NoiseDefuserContext
import com.zack.recomptracker.ai.PatternInsightContext
import com.zack.recomptracker.ai.TargetChangeContext
import com.zack.recomptracker.domain.insight.CrossMetricDetector
import com.zack.recomptracker.domain.insight.DayNutrition
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
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
    val preferences: PlanPreferences = PlanPreferences(),
    val todayTotals: MacroTotals = MacroTotals(),
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
    val showWeeklyVerdictCard: Boolean = false,   // doctrine "stay quiet": hidden on clean on-track weeks
    val patternInsightContext: PatternInsightContext? = null,
    val targetChangeContext: TargetChangeContext? = null,
    val noiseDefuserContext: NoiseDefuserContext? = null,
    val crossMetricContext: CrossMetricContext? = null,
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

@OptIn(FlowPreview::class)
class DashboardViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val trendCalculator: TrendCalculator,
    private val adherenceCalculator: AdherenceCalculator,
    private val adjustmentEngine: AdjustmentEngine,
    private val aiInsightCoordinator: AiInsightCoordinator,
    private val userProfileStore: UserProfilePreferencesStore,
) : ViewModel() {

    /** Profile photo + initials for the header avatar; updates live with the profile. */
    val headerAvatar: StateFlow<HeaderAvatar> =
        userProfileStore.preferences
            .map { HeaderAvatar(photoUri = it.profilePhotoUri, initials = initialsOf(it.name)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeaderAvatar(null, null))

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val aiInsightState: StateFlow<AiInsightState> = aiInsightCoordinator.state

    val patternInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.WEEKLY_PATTERN)

    val targetChangeInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.TARGET_CHANGE)
    val noiseDefuserInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.NOISE_DEFUSER)
    val crossMetricInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.CROSS_METRIC)

    fun onPatternInsightVisible() {
        val ctx = _uiState.value.patternInsightContext ?: return
        aiInsightCoordinator.onInsightVisible(InsightRequest.WeeklyPattern(ctx))
    }

    fun retryPatternInsight() {
        val ctx = _uiState.value.patternInsightContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.WeeklyPattern(ctx))
    }

    fun onTargetChangeVisible() {
        val ctx = _uiState.value.targetChangeContext ?: return
        aiInsightCoordinator.onInsightVisible(InsightRequest.TargetChange(ctx))
    }
    fun retryTargetChange() {
        val ctx = _uiState.value.targetChangeContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.TargetChange(ctx))
    }
    fun onNoiseDefuserVisible() {
        val ctx = _uiState.value.noiseDefuserContext ?: return
        aiInsightCoordinator.onInsightVisible(InsightRequest.NoiseDefuser(ctx))
    }
    fun retryNoiseDefuser() {
        val ctx = _uiState.value.noiseDefuserContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.NoiseDefuser(ctx))
    }
    fun onCrossMetricVisible() {
        val ctx = _uiState.value.crossMetricContext ?: return
        aiInsightCoordinator.onInsightVisible(InsightRequest.CrossMetric(ctx))
    }
    fun retryCrossMetric() {
        val ctx = _uiState.value.crossMetricContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.CrossMetric(ctx))
    }

    fun onAiCardVisible(result: AdjustmentResult) {
        val state = _uiState.value
        val input = state.adjustmentInput ?: return
        val context = InsightContext(
            result = result,
            input = input,
            targetCalories = state.preferences.targetCalories,
            targetProteinG = state.preferences.targetProteinG,
        )
        // Doctrine "stay quiet": on a clean, on-track HOLD week the verdict card has nothing to add.
        if (!InsightGate.shouldFireWeekly(context)) return
        aiInsightCoordinator.onAiCardVisible(context)
    }

    fun requestModelDownload() = aiInsightCoordinator.requestDownload()

    fun cancelDownload() = aiInsightCoordinator.cancelDownload()

    fun retryGeneration() {
        val state = _uiState.value
        val input = state.adjustmentInput ?: return
        aiInsightCoordinator.retryGeneration(
            InsightContext(
                result = state.result,
                input = input,
                targetCalories = state.preferences.targetCalories,
                targetProteinG = state.preferences.targetProteinG,
            )
        )
    }

    // Picked once at ViewModel construction — stable for the whole session.
    private val todayMessage: String = MOTIVATIONAL_MESSAGES.random()

    // Cached AdjustmentEngine — recreated only when thresholds change, not on every meal add
    private var cachedEngineThresholds: AdjustmentThresholds? = null
    private var cachedEngine: AdjustmentEngine = adjustmentEngine

    // Guard state for persistWeeklyReview — skips the DB write when verdict hasn't changed
    private var lastPersistedVerdict: AdjustmentVerdict? = null
    private var lastPersistedChange: Int = Int.MIN_VALUE

    init {
        val windowStart = dateProvider.today().minusDays(27)
        viewModelScope.launch {
            combine(
                logRepository.observeDailyLogs(),
                logRepository.observeMealEntriesSince(windowStart),
                logRepository.observePerformances(),
                planRepository.preferences,
            ) { logs, meals, performances, preferences ->
                buildState(logs, meals, performances, preferences)
            }
            .debounce(300L)
            .collect { state ->
                _uiState.value = state
                persistWeeklyReview(state)
            }
        }
    }

    private fun buildState(
        logs: List<DailyLogEntity>,
        allMeals: List<MealEntryEntity>,
        performances: List<LiftPerformanceEntity>,
        preferences: PlanPreferences,
    ): DashboardUiState {
        val today = dateProvider.today()
        // Planned (not-yet-eaten) entries never count toward reality — totals, adherence, trend.
        val meals = allMeals.filterNot { it.planned }
        val todayTotals = meals.filter { it.date == today.toString() }.macroTotals()
        val last14Start = today.minusDays(13)
        val last28Start = today.minusDays(27)
        val last7Start  = today.minusDays(6)
        val logsLast28  = logs.filter { it.localDate() in last28Start..today }
        val mealsLast14 = meals.filter { it.localDate() in last14Start..today }
        val mealsByDate = mealsLast14.groupBy { it.localDate() }

        val nutritionDays = (0..13).map { offset ->
            val date = last14Start.plusDays(offset.toLong())
            NutritionDay(date, mealsByDate[date].orEmpty().macroTotals().calories)
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
        val adherence    = adherenceCalculator.calculate(nutritionDays, preferences.targetCalories)
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
        val inZoneDays7 = if (preferences.calorieZoneLowerBound > 0) {
            last7DaysCalories.count {
                it.calories > 0 &&
                it.calories >= preferences.calorieZoneLowerBound &&
                it.calories <= preferences.calorieZoneUpperBound
            }
        } else 0

        val patternDays = (0..13).map { offset ->
            val date = last14Start.plusDays(offset.toLong())
            val totals = mealsByDate[date].orEmpty().macroTotals()
            DayNutrition(
                date = date,
                calories = totals.calories,
                proteinG = totals.proteinG,
                carbsG = totals.carbsG,
                fatG = totals.fatG,
                logged = totals.calories > 0,
            )
        }
        val patternInsightContext = buildPatternInsightContext(patternDays, preferences)

        // Target-change explainer: fires only when the verdict actually moves the target.
        val targetChangeContext = run {
            val change = result.recommendedCalorieChange
            if (change == 0 || result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) {
                null
            } else {
                TargetChangeContext(
                    oldTarget = preferences.targetCalories,
                    newTarget = preferences.targetCalories + change,
                    weightTrendKgPerWeek = weightTrend,
                    adherencePercent = adherence,
                    reasonCodes = result.reasonCodes,
                )
            }
        }

        // Noise-defuser: today's logged weight vs the most recent prior logged weight, gated by the trend.
        val noiseDefuserContext = run {
            val loggedWeights = logsLast28.filter { it.bodyWeightKg != null }.sortedBy { it.localDate() }
            val todayWeight = loggedWeights.lastOrNull { it.localDate() == today }?.bodyWeightKg
            val priorWeight = loggedWeights.lastOrNull { it.localDate() < today }?.bodyWeightKg
            if (todayWeight != null && priorWeight != null) {
                NoiseDefuserContext(todayWeight, priorWeight, weightTrend)
                    .takeIf { InsightGate.shouldFireNoiseDefuser(it) }
            } else {
                null
            }
        }

        // Cross-metric: protein adherence vs hunger over the 14-day window.
        val hungerByDate = logsLast28
            .filter { it.localDate() in last14Start..today && it.hungerScore != null }
            .associate { it.localDate() to it.hungerScore!! }
        val crossMetricContext = CrossMetricDetector
            .detectProteinHungerLink(patternDays, hungerByDate, preferences.targetProteinG)
            ?.let { CrossMetricContext(it) }

        // Hide the verdict card ONLY on a clean on-track HOLD week (doctrine "stay quiet").
        // Non-HOLD verdicts — including WAIT_FOR_DATA, which still shows the new-user placeholder
        // and model-download UI — keep the card.
        val showWeeklyVerdictCard = result.verdict != AdjustmentVerdict.HOLD ||
            InsightGate.shouldFireWeekly(
                InsightContext(
                    result = result,
                    input = adjustmentInput,
                    targetCalories = preferences.targetCalories,
                    targetProteinG = preferences.targetProteinG,
                ),
            )

        return DashboardUiState(
            preferences = preferences,
            todayTotals = todayTotals,
            sevenDayWeightAverage = logs
                .filter { it.localDate() in today.minusDays(6)..today }
                .mapNotNull { it.bodyWeightKg }
                .takeIf { it.isNotEmpty() }
                ?.average(),
            weightTrendKgPerWeek = weightTrend,
            waistTrendCmPerWeek = waistTrend,
            adherencePercent = adherence,
            loggedDaysInWindow = loggedDaysInWindow,
            last7DaysCalories = last7DaysCalories,
            inZoneDays7 = inZoneDays7,
            motivationalMessage = todayMessage,
            result = result,
            adjustmentInput = adjustmentInput,
            patternInsightContext = patternInsightContext,
            targetChangeContext = targetChangeContext,
            noiseDefuserContext = noiseDefuserContext,
            crossMetricContext = crossMetricContext,
            showWeeklyVerdictCard = showWeeklyVerdictCard,
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
