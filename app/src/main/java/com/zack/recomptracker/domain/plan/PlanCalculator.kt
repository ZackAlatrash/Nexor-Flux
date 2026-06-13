package com.zack.recomptracker.domain.plan

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import kotlin.math.roundToInt

/**
 * Pure TDEE → calorie/macro generator. Mifflin-St Jeor BMR, activity-level
 * multiplier, percentage-of-TDEE goal delta, protein g/kg, fat 25% kcal, carbs
 * remainder. No Android, no IO. See the design spec for the formula and rationale.
 */
class PlanCalculator {

    fun generate(input: PlanCalculatorInput): GeneratedPlan {
        val sexOffset = when (input.sex) {
            BiologicalSex.MALE -> 5.0
            BiologicalSex.FEMALE -> -161.0
        }
        val bmr = 10.0 * input.weightKg + 6.25 * input.heightCm - 5.0 * input.ageYears + sexOffset

        val factor = activityFactor(input.activityLevel)
        val tdee = bmr * factor

        val deltaPercent = goalDeltaPercent(input.goal)
        val targetRaw = tdee * (1.0 + deltaPercent / 100.0)
        val target = ((targetRaw / 10.0).roundToInt() * 10).coerceIn(1000, 6000)

        val proteinPerKg = if (input.goal.isCut()) 2.2 else 2.0
        val proteinG = (proteinPerKg * input.weightKg).roundToInt()
        val fatG = (0.25 * target / 9.0).roundToInt()
        val carbsKcal = target - proteinG * 4 - fatG * 9
        val carbsG = (carbsKcal / 4.0).roundToInt().coerceAtLeast(0)

        return GeneratedPlan(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            activityFactor = factor,
            goalDeltaPercent = deltaPercent,
            weightKgUsed = input.weightKg,
            targetCalories = target,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            zoneLower = target - 100,
            zoneUpper = target + 100,
        )
    }

    private fun activityFactor(level: ActivityLevel): Double = when (level) {
        ActivityLevel.SEDENTARY -> 1.2
        ActivityLevel.LIGHTLY_ACTIVE -> 1.375
        ActivityLevel.MODERATELY_ACTIVE -> 1.55
        ActivityLevel.VERY_ACTIVE -> 1.725
    }

    private fun goalDeltaPercent(goal: FitnessGoal): Int = when (goal) {
        FitnessGoal.AGGRESSIVE_CUT -> -25
        FitnessGoal.MODERATE_CUT -> -18
        FitnessGoal.MINI_CUT -> -22
        FitnessGoal.RECOMP -> -5
        FitnessGoal.LEAN_BULK -> 8
        FitnessGoal.MODERATE_BULK -> 12
        FitnessGoal.AGGRESSIVE_BULK -> 18
    }

    private fun FitnessGoal.isCut(): Boolean =
        this == FitnessGoal.AGGRESSIVE_CUT ||
            this == FitnessGoal.MODERATE_CUT ||
            this == FitnessGoal.MINI_CUT
}
