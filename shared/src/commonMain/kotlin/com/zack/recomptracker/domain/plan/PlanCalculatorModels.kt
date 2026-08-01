package com.zack.recomptracker.domain.plan

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal

/** Everything PlanCalculator needs. All fields required (validated upstream). */
data class PlanCalculatorInput(
    val heightCm: Int,
    val ageYears: Int,
    val sex: BiologicalSex,
    val activityLevel: ActivityLevel,
    val goal: FitnessGoal,
    val weightKg: Double,
)

/** Result of a generation, including intermediates so the UI can show its work. */
data class GeneratedPlan(
    val bmr: Int,
    val tdee: Int,
    val activityFactor: Double,
    val goalDeltaPercent: Int,
    val weightKgUsed: Double,
    val targetCalories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val zoneLower: Int,
    val zoneUpper: Int,
)
