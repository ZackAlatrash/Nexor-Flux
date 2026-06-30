package com.zack.recomptracker.data.preferences

import kotlinx.serialization.Serializable

@Serializable
data class PlanPreferences(
    val targetCalories: Int = 2550,
    val targetProteinG: Int = 165,
    val targetCarbsG: Int = 320,
    val targetFatG: Int = 68,
    val maintenancePhaseStartDate: String? = null,
    val weightTrendThresholdKgPerWeek: Double = 0.20,
    val waistIncreaseThresholdCm: Double = 0.5,
    val adherenceMinimumPercent: Double = 80.0,
    val reviewCadenceDays: Int = 7,
    val useMetricUnits: Boolean = true,
    val calorieZoneLowerBound: Int = 2400,
    val calorieZoneUpperBound: Int = 2600,
    val healthConnectEnabled: Boolean = false,
    /** Epoch millis of the last successful Health Connect sync; null if never synced. */
    val healthConnectLastSyncEpochMs: Long? = null,
) {
    /**
     * Returns a copy with the calorie target set and the calorie zone re-centred to
     * target ± [CALORIE_ZONE_MARGIN]. Use this anywhere calories change so the zone never
     * drifts out of sync with the target (which would make the dashboard zone look
     * disconnected from the plan).
     */
    fun withCalorieTarget(calories: Int): PlanPreferences = copy(
        targetCalories = calories,
        calorieZoneLowerBound = calories - CALORIE_ZONE_MARGIN,
        calorieZoneUpperBound = calories + CALORIE_ZONE_MARGIN,
    )

    companion object {
        /** Half-width of the calorie zone band: zone = target ± this. Matches PlanCalculator. */
        const val CALORIE_ZONE_MARGIN = 100
    }
}
