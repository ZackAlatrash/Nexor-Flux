package com.zack.recomptracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userProfileDataStore by preferencesDataStore(name = "user_profile_preferences")

class UserProfilePreferencesStore(private val context: Context) {

    val preferences: Flow<UserProfilePreferences> = context.userProfileDataStore.data.map { prefs ->
        UserProfilePreferences(
            heightCm = prefs[Keys.HeightCm],
            ageYears = prefs[Keys.AgeYears],
            biologicalSex = prefs[Keys.BiologicalSex]?.let {
                runCatching { BiologicalSex.valueOf(it) }.getOrNull()
            },
            activityLevel = prefs[Keys.ActivityLevel]?.let {
                runCatching { ActivityLevel.valueOf(it) }.getOrNull()
            },
            weeklyGymSessions = prefs[Keys.WeeklyGymSessions],
            goal = prefs[Keys.Goal]?.let {
                runCatching { FitnessGoal.valueOf(it) }.getOrNull()
            },
        )
    }

    suspend fun save(profile: UserProfilePreferences) {
        context.userProfileDataStore.edit { prefs ->
            if (profile.heightCm != null) prefs[Keys.HeightCm] = profile.heightCm
            else prefs.remove(Keys.HeightCm)

            if (profile.ageYears != null) prefs[Keys.AgeYears] = profile.ageYears
            else prefs.remove(Keys.AgeYears)

            if (profile.biologicalSex != null) prefs[Keys.BiologicalSex] = profile.biologicalSex.name
            else prefs.remove(Keys.BiologicalSex)

            if (profile.activityLevel != null) prefs[Keys.ActivityLevel] = profile.activityLevel.name
            else prefs.remove(Keys.ActivityLevel)

            if (profile.weeklyGymSessions != null) prefs[Keys.WeeklyGymSessions] = profile.weeklyGymSessions
            else prefs.remove(Keys.WeeklyGymSessions)

            if (profile.goal != null) prefs[Keys.Goal] = profile.goal.name
            else prefs.remove(Keys.Goal)
        }
    }

    private object Keys {
        val HeightCm = intPreferencesKey("height_cm")
        val AgeYears = intPreferencesKey("age_years")
        val BiologicalSex = stringPreferencesKey("biological_sex")
        val ActivityLevel = stringPreferencesKey("activity_level")
        val WeeklyGymSessions = intPreferencesKey("weekly_gym_sessions")
        val Goal = stringPreferencesKey("goal")
    }
}
