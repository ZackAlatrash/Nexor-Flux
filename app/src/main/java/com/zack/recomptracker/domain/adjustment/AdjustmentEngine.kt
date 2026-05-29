package com.zack.recomptracker.domain.adjustment

class AdjustmentEngine(
    private val thresholds: AdjustmentThresholds = AdjustmentThresholds(),
) {
    fun evaluate(input: AdjustmentInput): AdjustmentResult {
        if (input.daysLogged < 14) {
            return AdjustmentResult(
                verdict = AdjustmentVerdict.WAIT_FOR_DATA,
                recommendedCalorieChange = 0,
                reasonCodes = listOf("INSUFFICIENT_DATA"),
                summary = "Wait for at least 14 usable days before changing calories.",
            )
        }

        if (input.adherencePercent < thresholds.adherenceMinimumPercent) {
            return AdjustmentResult(
                verdict = AdjustmentVerdict.WAIT_FOR_DATA,
                recommendedCalorieChange = 0,
                reasonCodes = listOf("LOW_ADHERENCE"),
                summary = "Hold calories and improve logging consistency before reviewing.",
            )
        }

        val weightThreshold = thresholds.weightTrendThresholdKgPerWeek
        val waistWeeklyThreshold = thresholds.waistIncreaseThresholdCmAcrossTwoWeeks / 2.0

        if (
            input.weeksSincePhaseStart <= 1 &&
            input.weightTrendKgPerWeek > weightThreshold &&
            input.waistTrendCmPerWeek <= waistWeeklyThreshold
        ) {
            return hold("EARLY_SCALE_JUMP", "Early scale jump detected. Hold calories unless waist keeps rising.")
        }

        if (
            input.weightTrendKgPerWeek < -weightThreshold &&
            (input.performanceTrend == PerformanceTrend.DOWN || input.recoveryTrend == RecoveryTrend.POOR)
        ) {
            return AdjustmentResult(
                verdict = AdjustmentVerdict.INCREASE_CALORIES,
                recommendedCalorieChange = 150,
                reasonCodes = listOf("LOSING_WITH_POOR_RECOVERY"),
                summary = "Weight is falling and recovery/performance is not supporting maintenance. Add 100-150 kcal.",
            )
        }

        if (
            input.weightTrendKgPerWeek > weightThreshold &&
            input.waistTrendCmPerWeek > waistWeeklyThreshold
        ) {
            return AdjustmentResult(
                verdict = AdjustmentVerdict.REDUCE_CALORIES,
                recommendedCalorieChange = -100,
                reasonCodes = listOf("GAINING_WITH_WAIST_INCREASE"),
                summary = "Weight and waist are both rising. Remove about 100 kcal and reassess.",
            )
        }

        if (
            kotlin.math.abs(input.weightTrendKgPerWeek) <= weightThreshold &&
            input.waistTrendCmPerWeek <= waistWeeklyThreshold &&
            input.performanceTrend != PerformanceTrend.DOWN
        ) {
            return hold("MAINTENANCE_TREND", "Weight, waist, and performance are consistent with maintenance.")
        }

        if (
            input.weightTrendKgPerWeek > weightThreshold &&
            input.waistTrendCmPerWeek <= waistWeeklyThreshold &&
            input.performanceTrend == PerformanceTrend.UP
        ) {
            return hold("WEIGHT_UP_WAIST_STABLE_PERFORMANCE_UP", "Likely glycogen, water, or lean-mass trend. Do not cut.")
        }

        return hold("NO_CLEAR_CHANGE_SIGNAL", "No strong calorie-change signal. Hold and collect another review period.")
    }

    private fun hold(reasonCode: String, summary: String) = AdjustmentResult(
        verdict = AdjustmentVerdict.HOLD,
        recommendedCalorieChange = 0,
        reasonCodes = listOf(reasonCode),
        summary = summary,
    )
}
