package com.zack.recomptracker.domain

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.domain.plan.PlanGenerationOutcome
import com.zack.recomptracker.domain.plan.PlanGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanGeneratorTest {
    private val generator = PlanGenerator()

    private val completeProfile = UserProfilePreferences(
        heightCm = 180,
        ageYears = 30,
        biologicalSex = BiologicalSex.MALE,
        activityLevel = ActivityLevel.MODERATELY_ACTIVE,
        goal = FitnessGoal.RECOMP,
    )

    @Test
    fun completeProfileWithWeightProducesReadyPlan() {
        val outcome = generator.generate(completeProfile, weightKg = 80.0)
        assertTrue(outcome is PlanGenerationOutcome.Ready)
        assertEquals(2620, (outcome as PlanGenerationOutcome.Ready).plan.targetCalories)
    }

    @Test
    fun missingWeightAsksForWeight() {
        val outcome = generator.generate(completeProfile, weightKg = null)
        assertEquals(PlanGenerationOutcome.NeedsWeight, outcome)
    }

    @Test
    fun nonPositiveWeightAsksForWeight() {
        val outcome = generator.generate(completeProfile, weightKg = 0.0)
        assertEquals(PlanGenerationOutcome.NeedsWeight, outcome)
    }

    @Test
    fun missingFieldsReportedBeforeWeight() {
        val outcome = generator.generate(UserProfilePreferences(), weightKg = null)
        assertTrue(outcome is PlanGenerationOutcome.MissingProfileFields)
        val fields = (outcome as PlanGenerationOutcome.MissingProfileFields).fields
        assertEquals(listOf("Height", "Age", "Sex", "Activity level", "Goal"), fields)
    }

    @Test
    fun singleMissingFieldNamedCorrectly() {
        val outcome = generator.generate(completeProfile.copy(goal = null), weightKg = 80.0)
        assertTrue(outcome is PlanGenerationOutcome.MissingProfileFields)
        assertEquals(listOf("Goal"), (outcome as PlanGenerationOutcome.MissingProfileFields).fields)
    }
}
