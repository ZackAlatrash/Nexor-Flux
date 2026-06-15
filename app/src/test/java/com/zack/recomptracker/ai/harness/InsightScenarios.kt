package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.ai.CrossMetricContext
import com.zack.recomptracker.ai.InsightContext
import com.zack.recomptracker.ai.InsightPromptBuilder
import com.zack.recomptracker.ai.NoiseDefuserContext
import com.zack.recomptracker.ai.PatternInsightContext
import com.zack.recomptracker.ai.ProgressInsightContext
import com.zack.recomptracker.ai.RecoveryInsightContext
import com.zack.recomptracker.ai.RestOfDayInsightContext
import com.zack.recomptracker.ai.TargetChangeContext
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.insight.InsightFact
import com.zack.recomptracker.domain.insight.InsightFactType

/** A realistic persona's data for one or more cards. Nulls mean "this card doesn't apply". */
data class InsightScenario(
    val name: String,
    val weekly: InsightContext? = null,
    val progress: ProgressInsightContext? = null,
    val recovery: RecoveryInsightContext? = null,
    val restOfDay: RestOfDayInsightContext? = null,
    val pattern: PatternInsightContext? = null,
    val targetChange: TargetChangeContext? = null,
    val noiseDefuser: NoiseDefuserContext? = null,
    val crossMetric: CrossMetricContext? = null,
) {
    /** (cardLabel, renderedPrompt) for each present card. */
    fun cards(): List<Pair<String, String>> {
        val b = InsightPromptBuilder()
        val out = ArrayList<Pair<String, String>>()
        weekly?.let { out += "Weekly Summary" to b.buildWeeklySummaryPrompt(it) }
        progress?.let { out += "Progress Trend" to b.buildProgressTrendPrompt(it) }
        recovery?.let { out += "Recovery Readiness" to b.buildRecoveryReadinessPrompt(it) }
        restOfDay?.let { out += "Rest of Day" to b.buildRestOfDayPrompt(it) }
        pattern?.let { out += "Weekly Pattern" to b.buildPatternInsightPrompt(it) }
        targetChange?.let { out += "Target Change" to b.buildTargetChangePrompt(it) }
        noiseDefuser?.let { out += "Noise Defuser" to b.buildNoiseDefuserPrompt(it) }
        crossMetric?.let { out += "Cross Metric" to b.buildCrossMetricPrompt(it) }
        return out
    }
}

object InsightScenarios {

    private fun weekly(
        verdict: AdjustmentVerdict,
        codes: List<String>,
        weight: Double,
        waist: Double,
        perf: PerformanceTrend,
        recov: RecoveryTrend,
        adherence: Double,
        weeks: Int,
        targetCals: Int,
        summary: String,
        desiredRate: Double? = null,
        priorTarget: Int? = null,
        change: Int = 0,
    ) = InsightContext(
        result = AdjustmentResult(verdict, change, codes, summary),
        input = AdjustmentInput(14, adherence, weeks, weight, waist, perf, recov),
        targetCalories = targetCals,
        targetProteinG = 165,
        desiredWeeklyRateKg = desiredRate,
        priorTargetCalories = priorTarget,
    )

    val ALL: List<InsightScenario> = listOf(
        InsightScenario(
            name = "On-track fat loss (stay-quiet test)",
            weekly = weekly(
                AdjustmentVerdict.HOLD, listOf("MAINTENANCE_TREND"),
                weight = -0.38, waist = -0.10, perf = PerformanceTrend.STABLE,
                recov = RecoveryTrend.GOOD, adherence = 92.0, weeks = 3,
                targetCals = 2550, summary = "On plan, no change.",
                desiredRate = -0.40,
            ),
            progress = ProgressInsightContext(28, -0.38, -0.20, 0.4, 92.0, 6, 6, priorWeightTrendKgPerWeek = -0.36),
        ),
        InsightScenario(
            name = "Plateau (flat 3 weeks, high adherence)",
            weekly = weekly(
                AdjustmentVerdict.REDUCE_CALORIES, listOf("NO_CLEAR_CHANGE_SIGNAL"),
                weight = -0.02, waist = 0.0, perf = PerformanceTrend.STABLE,
                recov = RecoveryTrend.OK, adherence = 90.0, weeks = 4,
                targetCals = 2300, summary = "Loss has stalled at high adherence.",
                desiredRate = -0.40, priorTarget = 2500, change = -200,
            ),
        ),
        InsightScenario(
            name = "Fast loss -> expenditure change",
            weekly = weekly(
                AdjustmentVerdict.INCREASE_CALORIES, listOf("LOSING_WITH_POOR_RECOVERY"),
                weight = -0.85, waist = -0.4, perf = PerformanceTrend.DOWN,
                recov = RecoveryTrend.POOR, adherence = 95.0, weeks = 2,
                targetCals = 2450, summary = "Losing faster than target with recovery dropping.",
                desiredRate = -0.40, priorTarget = 2300, change = 150,
            ),
        ),
        InsightScenario(
            name = "Surplus + waist creeping up",
            weekly = weekly(
                AdjustmentVerdict.REDUCE_CALORIES, listOf("GAINING_WITH_WAIST_INCREASE"),
                weight = 0.35, waist = 0.4, perf = PerformanceTrend.STABLE,
                recov = RecoveryTrend.OK, adherence = 80.0, weeks = 5,
                targetCals = 2600, summary = "Weight and waist both rising.",
                desiredRate = -0.30, priorTarget = 2800, change = -200,
            ),
        ),
        InsightScenario(
            name = "Lean-mass gain (weight up, waist stable, lifts up)",
            weekly = weekly(
                AdjustmentVerdict.HOLD, listOf("WEIGHT_UP_WAIST_STABLE_PERFORMANCE_UP"),
                weight = 0.25, waist = 0.0, perf = PerformanceTrend.UP,
                recov = RecoveryTrend.GOOD, adherence = 88.0, weeks = 6,
                targetCals = 2900, summary = "Likely lean mass.",
                desiredRate = 0.20,
            ),
            progress = ProgressInsightContext(28, 0.25, 0.0, 0.6, 88.0, 6, 6, priorWeightTrendKgPerWeek = 0.22),
        ),
        InsightScenario(
            name = "Poor recovery + trained today",
            recovery = RecoveryInsightContext(
                sleepHours = 5.0, energyScore = 3, hungerScore = 7, sorenessScore = 8, trained = true,
                avgSleepHours = 7.4, avgEnergyScore = 6,
            ),
        ),
        InsightScenario(
            name = "Good recovery, rest day",
            recovery = RecoveryInsightContext(
                sleepHours = 8.2, energyScore = 8, hungerScore = 4, sorenessScore = 2, trained = false,
                avgSleepHours = 7.5, avgEnergyScore = 6,
            ),
        ),
        InsightScenario(
            name = "Mid-day protein deficit, dinner to go",
            restOfDay = RestOfDayInsightContext(
                caloriesConsumed = 1420, targetCalories = 2200,
                calorieZoneLowerBound = 2050, calorieZoneUpperBound = 2350,
                proteinConsumedG = 72.0, proteinTargetG = 165, mealsLoggedCount = 2,
                fractionOfDayElapsed = 0.6,
            ),
        ),
        InsightScenario(
            name = "Over calories already, evening",
            restOfDay = RestOfDayInsightContext(
                caloriesConsumed = 2380, targetCalories = 2200,
                calorieZoneLowerBound = 2050, calorieZoneUpperBound = 2350,
                proteinConsumedG = 150.0, proteinTargetG = 165, mealsLoggedCount = 4,
                fractionOfDayElapsed = 0.85,
            ),
        ),
        InsightScenario(
            name = "Weekend derailment pattern",
            pattern = PatternInsightContext(
                InsightFact(
                    type = InsightFactType.WEEKDAY_WEEKEND,
                    priority = 2,
                    statement = "You average 2,150 kcal on weekdays but 3,050 kcal on weekends.",
                ),
            ),
        ),
        InsightScenario(
            name = "Target change — expenditure rose (raise)",
            targetChange = TargetChangeContext(
                oldTarget = 2300,
                newTarget = 2450,
                weightTrendKgPerWeek = -0.85,
                adherencePercent = 95.0,
                reasonCodes = listOf("LOSING_WITH_POOR_RECOVERY"),
                desiredWeeklyRateKg = -0.40,
            ),
        ),
        InsightScenario(
            name = "Target change — stall (trim)",
            targetChange = TargetChangeContext(
                oldTarget = 2500,
                newTarget = 2300,
                weightTrendKgPerWeek = -0.02,
                adherencePercent = 90.0,
                reasonCodes = listOf("NO_CLEAR_CHANGE_SIGNAL"),
                desiredWeeklyRateKg = -0.40,
            ),
        ),
        InsightScenario(
            name = "Noise — scary morning spike vs flat trend",
            noiseDefuser = NoiseDefuserContext(
                todayWeightKg = 79.7,
                priorWeightKg = 78.95,
                smoothedTrendKgPerWeek = -0.05,
            ),
        ),
        InsightScenario(
            name = "Cross-metric — protein vs hunger",
            crossMetric = CrossMetricContext(
                InsightFact(
                    type = InsightFactType.WEAKEST_MACRO,
                    priority = 20,
                    statement = "On days you hit your protein target, your hunger score averages 3/10 versus 6/10 on days you miss it.",
                ),
            ),
        ),
    )
}
