package com.zack.recomptracker.domain.plan

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanGeneratorBirthDateTest {
    private val gen = PlanGenerator()
    private val today = LocalDate.of(2026, 6, 13)

    private fun fullProfile() = UserProfilePreferences(
        heightCm = 180,
        birthDate = "1998-01-01",
        biologicalSex = BiologicalSex.MALE,
        activityLevel = ActivityLevel.MODERATELY_ACTIVE,
        goal = FitnessGoal.RECOMP,
    )

    @Test
    fun missingBirthDate_reportsAgeMissing() {
        val outcome = gen.generate(fullProfile().copy(birthDate = null), weightKg = 80.0, today = today)
        assertTrue(outcome is PlanGenerationOutcome.MissingProfileFields)
        assertTrue((outcome as PlanGenerationOutcome.MissingProfileFields).fields.contains("Age"))
    }

    @Test
    fun completeProfile_withBirthDate_generates() {
        val outcome = gen.generate(fullProfile(), weightKg = 80.0, today = today)
        assertTrue(outcome is PlanGenerationOutcome.Ready)
    }
}
