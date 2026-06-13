package com.zack.recomptracker.data.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
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
    fun `round-trips all fields`() {
        val profile = UserProfilePreferences(
            name = "Alex",
            profilePhotoUri = "content://photo/1",
            heightCm = 180,
            birthDate = "1998-01-01",
            biologicalSex = BiologicalSex.MALE,
            activityLevel = ActivityLevel.MODERATELY_ACTIVE,
            weeklyGymSessions = 5,
            goal = FitnessGoal.RECOMP,
        )

        val restored = mutablePreferencesOf()
            .apply { writeUserProfilePreferences(profile) }
            .toUserProfilePreferences()

        assertEquals(profile, restored)
    }

    @Test
    fun `fields persist with their own values`() {
        val prefs = mutablePreferencesOf().apply {
            writeUserProfilePreferences(
                UserProfilePreferences(
                    name = "Sam",
                    birthDate = "1986-01-01",
                    goal = FitnessGoal.LEAN_BULK,
                ),
            )
        }

        val restored = prefs.toUserProfilePreferences()

        assertEquals("Sam", restored.name)
        assertEquals("1986-01-01", restored.birthDate)
        assertEquals(FitnessGoal.LEAN_BULK, restored.goal)
    }

    @Test
    fun `unset fields read back as null`() {
        val restored = mutablePreferencesOf()
            .apply { writeUserProfilePreferences(UserProfilePreferences()) }
            .toUserProfilePreferences()

        assertEquals(UserProfilePreferences(), restored)
        assertNull(restored.name)
        assertNull(restored.birthDate)
        assertNull(restored.goal)
    }

    @Test
    fun `clearing a previously-set field removes it`() {
        val prefs = mutablePreferencesOf().apply {
            writeUserProfilePreferences(
                UserProfilePreferences(
                    name = "Jordan",
                    birthDate = "1990-05-02",
                    goal = FitnessGoal.MODERATE_CUT,
                ),
            )
            // Re-save with everything unset — should remove, not retain stale values.
            writeUserProfilePreferences(UserProfilePreferences())
        }

        val restored = prefs.toUserProfilePreferences()

        assertNull(restored.name)
        assertNull(restored.birthDate)
        assertNull(restored.goal)
    }
}
