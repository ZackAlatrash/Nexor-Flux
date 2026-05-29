package com.zack.recomptracker.domain.adjustment

data class AdjustmentInput(
    val daysLogged: Int,
    val adherencePercent: Double,
    val weeksSincePhaseStart: Int,
    val weightTrendKgPerWeek: Double,
    val waistTrendCmPerWeek: Double,
    val performanceTrend: PerformanceTrend,
    val recoveryTrend: RecoveryTrend,
)

data class AdjustmentResult(
    val verdict: AdjustmentVerdict,
    val recommendedCalorieChange: Int,
    val reasonCodes: List<String>,
    val summary: String,
)

data class AdjustmentThresholds(
    val weightTrendThresholdKgPerWeek: Double = 0.20,
    val waistIncreaseThresholdCmAcrossTwoWeeks: Double = 0.5,
    val adherenceMinimumPercent: Double = 85.0,
)

enum class AdjustmentVerdict {
    WAIT_FOR_DATA,
    HOLD,
    INCREASE_CALORIES,
    REDUCE_CALORIES,
}

enum class PerformanceTrend {
    UP,
    STABLE,
    DOWN,
    UNKNOWN,
}

enum class RecoveryTrend {
    GOOD,
    OK,
    POOR,
    UNKNOWN,
}
