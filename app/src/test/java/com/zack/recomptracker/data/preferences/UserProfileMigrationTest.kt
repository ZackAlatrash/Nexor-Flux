package com.zack.recomptracker.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UserProfileMigrationTest {
    @Test
    fun legacyAgeYears_migratesToApproxBirthDate() {
        val legacy = """{"heightCm":180,"ageYears":28,"goal":"RECOMP"}"""
        val migrated = migrateLegacyProfileJson(legacy, today = LocalDate.of(2026, 6, 13))
        assertEquals("1998-01-01", migrated.birthDate)
        assertEquals(180, migrated.heightCm)
        assertEquals(FitnessGoal.RECOMP, migrated.goal)
    }

    @Test
    fun legacyTrainingFields_areDroppedSilently() {
        val legacy = """{"trainingExperience":"ADVANCED","plannedTrainingDays":5,"ageYears":40}"""
        val migrated = migrateLegacyProfileJson(legacy, today = LocalDate.of(2026, 6, 13))
        assertEquals("1986-01-01", migrated.birthDate)
    }

    @Test
    fun modernJson_withBirthDate_isUnchanged() {
        val modern = """{"birthDate":"1990-05-02","heightCm":175}"""
        val migrated = migrateLegacyProfileJson(modern, today = LocalDate.of(2026, 6, 13))
        assertEquals("1990-05-02", migrated.birthDate)
    }

    @Test
    fun noAge_noBirthDate_staysNull() {
        val migrated = migrateLegacyProfileJson("{}", today = LocalDate.of(2026, 6, 13))
        assertNull(migrated.birthDate)
    }
}
