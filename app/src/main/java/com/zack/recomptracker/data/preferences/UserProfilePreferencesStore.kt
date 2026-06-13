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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

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
    val Name = stringPreferencesKey("name")
    val ProfilePhotoUri = stringPreferencesKey("profile_photo_uri")
    val HeightCm = intPreferencesKey("height_cm")
    val BirthDate = stringPreferencesKey("birth_date")
    val BiologicalSex = stringPreferencesKey("biological_sex")
    val ActivityLevel = stringPreferencesKey("activity_level")
    val WeeklyGymSessions = intPreferencesKey("weekly_gym_sessions")
    val Goal = stringPreferencesKey("goal")

    /** Legacy key — read-only, migrated into [BirthDate]. Never written. */
    val LegacyAgeYears = intPreferencesKey("age_years")
}

private val migrationJson = Json { ignoreUnknownKeys = true }

/** Decodes persisted profile JSON, converting a legacy ageYears int into an approx birthDate. */
fun migrateLegacyProfileJson(raw: String, today: LocalDate = LocalDate.now()): UserProfilePreferences {
    val base = migrationJson.decodeFromString<UserProfilePreferences>(raw)
    if (base.birthDate != null) return base
    val legacyAge = runCatching {
        migrationJson.parseToJsonElement(raw).jsonObject["ageYears"]?.jsonPrimitive?.intOrNull
    }.getOrNull()?.takeIf { it > 0 } ?: return base
    return base.copy(birthDate = LocalDate.of(today.year - legacyAge, 1, 1).toString())
}

/**
 * Pure read mapping — kept free of Context so it can be unit-tested directly.
 *
 * Reassembles the stored per-key entries into the canonical profile JSON and decodes it
 * through [migrateLegacyProfileJson], so the legacy `age_years` → `birthDate` migration
 * (and tolerance of any dropped keys) is shared with the unit-tested JSON path.
 */
internal fun Preferences.toUserProfilePreferences(): UserProfilePreferences {
    val obj = buildMap<String, JsonElement> {
        this@toUserProfilePreferences[Keys.Name]?.let { put("name", JsonPrimitive(it)) }
        this@toUserProfilePreferences[Keys.ProfilePhotoUri]?.let { put("profilePhotoUri", JsonPrimitive(it)) }
        this@toUserProfilePreferences[Keys.HeightCm]?.let { put("heightCm", JsonPrimitive(it)) }
        this@toUserProfilePreferences[Keys.BirthDate]?.let { put("birthDate", JsonPrimitive(it)) }
        this@toUserProfilePreferences[Keys.BiologicalSex]?.let { put("biologicalSex", JsonPrimitive(it)) }
        this@toUserProfilePreferences[Keys.ActivityLevel]?.let { put("activityLevel", JsonPrimitive(it)) }
        this@toUserProfilePreferences[Keys.WeeklyGymSessions]?.let { put("weeklyGymSessions", JsonPrimitive(it)) }
        this@toUserProfilePreferences[Keys.Goal]?.let { put("goal", JsonPrimitive(it)) }
        // Legacy: surface as `ageYears` so the JSON migration converts it to a birthDate.
        if (this@toUserProfilePreferences[Keys.BirthDate] == null) {
            this@toUserProfilePreferences[Keys.LegacyAgeYears]?.let { put("ageYears", JsonPrimitive(it)) }
        }
    }
    return migrateLegacyProfileJson(JsonObject(obj).toString())
}

/** Pure write mapping — unset fields are removed so absent values read back as null. */
internal fun MutablePreferences.writeUserProfilePreferences(profile: UserProfilePreferences) {
    putOrRemove(Keys.Name, profile.name)
    putOrRemove(Keys.ProfilePhotoUri, profile.profilePhotoUri)
    putOrRemove(Keys.HeightCm, profile.heightCm)
    putOrRemove(Keys.BirthDate, profile.birthDate)
    putOrRemove(Keys.BiologicalSex, profile.biologicalSex?.name)
    putOrRemove(Keys.ActivityLevel, profile.activityLevel?.name)
    putOrRemove(Keys.WeeklyGymSessions, profile.weeklyGymSessions)
    putOrRemove(Keys.Goal, profile.goal?.name)
    // Drop the legacy age key on any save so migrated profiles don't re-trigger migration.
    remove(Keys.LegacyAgeYears)
}

private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value != null) this[key] = value else remove(key)
}
