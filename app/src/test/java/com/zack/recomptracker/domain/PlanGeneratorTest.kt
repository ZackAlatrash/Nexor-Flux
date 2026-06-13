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
import java.time.LocalDate

class PlanGeneratorTest {
    private val generator = PlanGenerator()

    // 1996-01-01 birthDate yields age 30 as of the fixed today below.
    private val today = LocalDate.of(2026, 6, 13)

    private val completeProfile = UserProfilePreferences(
        heightCm = 180,
        birthDate = "1996-01-01",
        biologicalSex = BiologicalSex.MALE,
        activityLevel = ActivityLevel.MODERATELY_ACTIVE,
        goal = FitnessGoal.RECOMP,
    )

    @Test
    fun completeProfileWithWeightProducesReadyPlan() {
        val outcome = generator.generate(completeProfile, weightKg = 80.0, today = today)
        assertTrue(outcome is PlanGenerationOutcome.Ready)
        assertEquals(2620, (outcome as PlanGenerationOutcome.Ready).plan.targetCalories)
    }

    @Test
    fun missingWeightAsksForWeight() {
        val outcome = generator.generate(completeProfile, weightKg = null, today = today)
        assertEquals(PlanGenerationOutcome.NeedsWeight, outcome)
    }

    @Test
    fun nonPositiveWeightAsksForWeight() {
        val outcome = generator.generate(completeProfile, weightKg = 0.0, today = today)
        assertEquals(PlanGenerationOutcome.NeedsWeight, outcome)
    }

    @Test
    fun missingFieldsReportedBeforeWeight() {
        val outcome = generator.generate(UserProfilePreferences(), weightKg = null, today = today)
        assertTrue(outcome is PlanGenerationOutcome.MissingProfileFields)
        val fields = (outcome as PlanGenerationOutcome.MissingProfileFields).fields
        assertEquals(listOf("Height", "Age", "Sex", "Activity level", "Goal"), fields)
    }

    @Test
    fun singleMissingFieldNamedCorrectly() {
        val outcome = generator.generate(completeProfile.copy(goal = null), weightKg = 80.0, today = today)
        assertTrue(outcome is PlanGenerationOutcome.MissingProfileFields)
        assertEquals(listOf("Goal"), (outcome as PlanGenerationOutcome.MissingProfileFields).fields)
    }
}
