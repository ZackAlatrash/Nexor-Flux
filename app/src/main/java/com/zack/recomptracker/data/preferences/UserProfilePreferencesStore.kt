package com.zack.recomptracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userProfileDataStore by preferencesDataStore(name = "user_profile_preferences")

class UserProfilePreferencesStore(private val context: Context) {

    val preferences: Flow<UserProfilePreferences> =
        context.userProfileDataStore.data.map { it.toUserProfilePreferences() }

    suspend fun save(profile: UserProfilePreferences) {
        context.userProfileDataStore.edit { prefs ->
            prefs.writeUserProfilePreferences(profile)
        }
    }
}

private object Keys {
    val HeightCm = intPreferencesKey("height_cm")
    val AgeYears = intPreferencesKey("age_years")
    val BiologicalSex = stringPreferencesKey("biological_sex")
    val ActivityLevel = stringPreferencesKey("activity_level")
    val WeeklyGymSessions = intPreferencesKey("weekly_gym_sessions")
    val Goal = stringPreferencesKey("goal")
    val TrainingExperience = stringPreferencesKey("training_experience")
    val PlannedTrainingDays = intPreferencesKey("planned_training_days")
}

/** Pure read mapping — kept free of Context so it can be unit-tested directly. */
internal fun Preferences.toUserProfilePreferences(): UserProfilePreferences =
    UserProfilePreferences(
        heightCm = this[Keys.HeightCm],
        ageYears = this[Keys.AgeYears],
        biologicalSex = this[Keys.BiologicalSex]?.let {
            runCatching { BiologicalSex.valueOf(it) }.getOrNull()
        },
        activityLevel = this[Keys.ActivityLevel]?.let {
            runCatching { ActivityLevel.valueOf(it) }.getOrNull()
        },
        weeklyGymSessions = this[Keys.WeeklyGymSessions],
        goal = this[Keys.Goal]?.let {
            runCatching { FitnessGoal.valueOf(it) }.getOrNull()
        },
        trainingExperience = this[Keys.TrainingExperience]?.let {
            runCatching { TrainingExperience.valueOf(it) }.getOrNull()
        },
        plannedTrainingDays = this[Keys.PlannedTrainingDays],
    )

/** Pure write mapping — unset fields are removed so absent values read back as null. */
internal fun MutablePreferences.writeUserProfilePreferences(profile: UserProfilePreferences) {
    putOrRemove(Keys.HeightCm, profile.heightCm)
    putOrRemove(Keys.AgeYears, profile.ageYears)
    putOrRemove(Keys.BiologicalSex, profile.biologicalSex?.name)
    putOrRemove(Keys.ActivityLevel, profile.activityLevel?.name)
    putOrRemove(Keys.WeeklyGymSessions, profile.weeklyGymSessions)
    putOrRemove(Keys.Goal, profile.goal?.name)
    putOrRemove(Keys.TrainingExperience, profile.trainingExperience?.name)
    putOrRemove(Keys.PlannedTrainingDays, profile.plannedTrainingDays)
}

private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value != null) this[key] = value else remove(key)
}
