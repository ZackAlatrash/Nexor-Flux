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
)
