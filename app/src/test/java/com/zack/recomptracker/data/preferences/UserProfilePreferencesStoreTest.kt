package com.zack.recomptracker.data.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the pure read/write mapping used by [UserProfilePreferencesStore].
 * DataStore persistence is just these mappings over an in-memory [Preferences],
 * so a real Context/instrumented test isn't needed to verify round-tripping.
 */
class UserProfilePreferencesStoreTest {

    @Test
    fun `round-trips all fields including training experience and planned days`() {
        val profile = UserProfilePreferences(
            heightCm = 180,
            ageYears = 28,
            biologicalSex = BiologicalSex.MALE,
            activityLevel = ActivityLevel.MODERATELY_ACTIVE,
            weeklyGymSessions = 5,
            goal = FitnessGoal.RECOMP,
            trainingExperience = TrainingExperience.BEGINNER,
            plannedTrainingDays = 4,
        )

        val restored = mutablePreferencesOf()
            .apply { writeUserProfilePreferences(profile) }
            .toUserProfilePreferences()

        assertEquals(profile, restored)
    }

    @Test
    fun `new fields persist with their own values`() {
        val prefs = mutablePreferencesOf().apply {
            writeUserProfilePreferences(
                UserProfilePreferences(
                    trainingExperience = TrainingExperience.ADVANCED,
                    plannedTrainingDays = 6,
                ),
            )
        }

        val restored = prefs.toUserProfilePreferences()

        assertEquals(TrainingExperience.ADVANCED, restored.trainingExperience)
        assertEquals(6, restored.plannedTrainingDays)
    }

    @Test
    fun `unset fields read back as null`() {
        val restored = mutablePreferencesOf()
            .apply { writeUserProfilePreferences(UserProfilePreferences()) }
            .toUserProfilePreferences()

        assertEquals(UserProfilePreferences(), restored)
        assertNull(restored.trainingExperience)
        assertNull(restored.plannedTrainingDays)
    }

    @Test
    fun `clearing a previously-set field removes it`() {
        val prefs = mutablePreferencesOf().apply {
            writeUserProfilePreferences(
                UserProfilePreferences(
                    trainingExperience = TrainingExperience.INTERMEDIATE,
                    plannedTrainingDays = 3,
                ),
            )
            // Re-save with both unset — should remove, not retain stale values.
            writeUserProfilePreferences(UserProfilePreferences())
        }

        val restored = prefs.toUserProfilePreferences()

        assertNull(restored.trainingExperience)
        assertNull(restored.plannedTrainingDays)
    }

    @Test
    fun `invalid stored training experience falls back to null`() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("training_experience") to "NOT_A_LEVEL",
        )

        assertNull(prefs.toUserProfilePreferences().trainingExperience)
    }
}
