package com.zack.recomptracker.domain

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.domain.plan.PlanCalculator
import com.zack.recomptracker.domain.plan.PlanCalculatorInput
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanCalculatorTest {
    private val calculator = PlanCalculator()

    private fun input(
        heightCm: Int = 180,
        ageYears: Int = 30,
        sex: BiologicalSex = BiologicalSex.MALE,
        activityLevel: ActivityLevel = ActivityLevel.MODERATELY_ACTIVE,
        goal: FitnessGoal = FitnessGoal.RECOMP,
        weightKg: Double = 80.0,
    ) = PlanCalculatorInput(heightCm, ageYears, sex, activityLevel, goal, weightKg)

    @Test
    fun maleRecompModerateActivityProducesExpectedPlan() {
        // BMR = 1780, TDEE = 1780*1.55 = 2759, RECOMP -5% -> 2621.05 -> 2620
        val plan = calculator.generate(input())
        assertEquals(1780, plan.bmr)
        assertEquals(2759, plan.tdee)
        assertEquals(1.55, plan.activityFactor, 0.0001)
        assertEquals(-5, plan.goalDeltaPercent)
        assertEquals(2620, plan.targetCalories)
        assertEquals(160, plan.proteinG)   // 2.0 g/kg * 80
        assertEquals(73, plan.fatG)         // 0.25*2620/9 = 72.78 -> 73
        assertEquals(331, plan.carbsG)      // (2620-640-657)/4 = 330.75 -> 331
        assertEquals(2520, plan.zoneLower)
        assertEquals(2720, plan.zoneUpper)
    }

    @Test
    fun femaleUsesMinus161BmrOffset() {
        // Female 60kg 165cm 28y: BMR = 600 + 1031.25 - 140 - 161 = 1330.25 -> 1330
        val plan = calculator.generate(
            input(heightCm = 165, ageYears = 28, sex = BiologicalSex.FEMALE,
                activityLevel = ActivityLevel.LIGHTLY_ACTIVE, goal = FitnessGoal.MODERATE_CUT, weightKg = 60.0),
        )
        assertEquals(1330, plan.bmr)
        assertEquals(1.375, plan.activityFactor, 0.0001)
        assertEquals(-18, plan.goalDeltaPercent)
        assertEquals(1500, plan.targetCalories) // 1330.25*1.375*0.82 = 1499.86 -> 1500
        assertEquals(132, plan.proteinG)        // cut -> 2.2 g/kg * 60
    }

    @Test
    fun cutGoalsUseTwoPointTwoGramsPerKgProtein() {
        val plan = calculator.generate(input(goal = FitnessGoal.AGGRESSIVE_CUT, weightKg = 80.0))
        assertEquals(176, plan.proteinG) // 2.2 * 80
    }

    @Test
    fun bulkGoalsUseTwoGramsPerKgProtein() {
        val plan = calculator.generate(input(goal = FitnessGoal.LEAN_BULK, weightKg = 80.0))
        assertEquals(160, plan.proteinG) // 2.0 * 80
        assertEquals(8, plan.goalDeltaPercent)
    }

    @Test
    fun goalDeltasMatchTableForEveryGoal() {
        assertEquals(-25, calculator.generate(input(goal = FitnessGoal.AGGRESSIVE_CUT)).goalDeltaPercent)
        assertEquals(-18, calculator.generate(input(goal = FitnessGoal.MODERATE_CUT)).goalDeltaPercent)
        assertEquals(-22, calculator.generate(input(goal = FitnessGoal.MINI_CUT)).goalDeltaPercent)
        assertEquals(-5, calculator.generate(input(goal = FitnessGoal.RECOMP)).goalDeltaPercent)
        assertEquals(8, calculator.generate(input(goal = FitnessGoal.LEAN_BULK)).goalDeltaPercent)
        assertEquals(12, calculator.generate(input(goal = FitnessGoal.MODERATE_BULK)).goalDeltaPercent)
        assertEquals(18, calculator.generate(input(goal = FitnessGoal.AGGRESSIVE_BULK)).goalDeltaPercent)
    }

    @Test
    fun veryActiveUsesHighestFactor() {
        val plan = calculator.generate(input(activityLevel = ActivityLevel.VERY_ACTIVE))
        assertEquals(1.725, plan.activityFactor, 0.0001)
    }

    @Test
    fun sedentaryUsesLowestFactor() {
        val plan = calculator.generate(input(activityLevel = ActivityLevel.SEDENTARY))
        assertEquals(1.2, plan.activityFactor, 0.0001)
    }

    @Test
    fun targetCaloriesClampedToMinimumOfOneThousand() {
        // Female 40kg 150cm 25y sedentary aggressive cut: 1261.8*0.75 = 946 -> clamp 1000
        val plan = calculator.generate(
            input(heightCm = 150, ageYears = 25, sex = BiologicalSex.FEMALE,
                activityLevel = ActivityLevel.SEDENTARY, goal = FitnessGoal.AGGRESSIVE_CUT, weightKg = 40.0),
        )
        assertEquals(1000, plan.targetCalories)
        assertEquals(900, plan.zoneLower)
        assertEquals(1100, plan.zoneUpper)
    }

    @Test
    fun zoneIsTargetPlusMinusOneHundred() {
        val plan = calculator.generate(input())
        assertEquals(plan.targetCalories - 100, plan.zoneLower)
        assertEquals(plan.targetCalories + 100, plan.zoneUpper)
    }
}
