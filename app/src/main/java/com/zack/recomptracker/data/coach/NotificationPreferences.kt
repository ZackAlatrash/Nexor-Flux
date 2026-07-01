package com.zack.recomptracker.data.coach

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.domain.coach.QuietHours
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.coachNotificationDataStore by preferencesDataStore(name = "coach_notification_prefs")

/**
 * The Phase-5 push-preference surface the [CoachPushEmitter] consults before it ever asks the
 * [com.zack.recomptracker.domain.coach.RateLimiter] to decide. Extracted as an interface so the emitter
 * can be unit-tested against a trivial in-memory fake instead of the concrete DataStore class
 * ([CoachNotificationPreferences] is the production implementation).
 *
 * The three settings mirror the "quiet by default" doctrine of
 * `docs/ai-redesign/08-technical-architecture.md` §11:
 * - [weeklyCheckInPushEnabled] — the spine's one weekly push. **On by default.**
 * - [ambientNudgesEnabled]     — rare P0 alerts + celebrations. **Off by default.**
 * - [quietHours]               — the suppression window handed to the RateLimiter (default 22:00–07:00).
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 */
interface CoachNotifierPreferences {
    /** Whether the single weekly check-in push may fire. Default true. */
    val weeklyCheckInPushEnabled: Flow<Boolean>

    /** Whether rare P0/celebration ambient nudges may fire. Default false. */
    val ambientNudgesEnabled: Flow<Boolean>

    /** The current quiet-hours window as the domain type the RateLimiter consumes. */
    suspend fun quietHours(): QuietHours

    suspend fun setWeeklyCheckInPushEnabled(enabled: Boolean)
    suspend fun setAmbientNudgesEnabled(enabled: Boolean)

    /** Persist the quiet window as whole-hour start/end (0..23). */
    suspend fun setQuietHours(startHour: Int, endHour: Int)

    /**
     * The weekly-review signature the weekly check-in push last fired for (empty string = never). Used
     * by the digest to fire the weekly push exactly once per new week (§9's `lastSeen*` chain).
     */
    suspend fun lastPushedWeeklySignature(): String

    /** Stamp [signature] as the signature the weekly push has now fired for. */
    suspend fun setLastPushedWeeklySignature(signature: String)
}

/**
 * DataStore-backed persistence for the coach's push preferences. Mirrors the
 * [com.zack.recomptracker.data.preferences.AppPreferences] pattern: a `preferencesDataStore` delegate, a
 * `.data.map{}` [Flow] per setting, `.edit{}` suspend setters, and typed [Keys].
 *
 * Defaults encode the spec's respectful posture: weekly push on, ambient nudges off, quiet 22:00–07:00.
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 */
class CoachNotificationPreferences(
    private val dataStore: DataStore<Preferences>,
) : CoachNotifierPreferences {

    /** Production constructor — binds to the app-scoped `coach_notification_prefs` DataStore. */
    constructor(context: Context) : this(context.coachNotificationDataStore)

    override val weeklyCheckInPushEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.WeeklyCheckInEnabled] ?: DEFAULT_WEEKLY_ENABLED }

    override val ambientNudgesEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.AmbientNudgesEnabled] ?: DEFAULT_AMBIENT_ENABLED }

    override suspend fun quietHours(): QuietHours {
        val prefs = dataStore.data.first()
        val start = prefs[Keys.QuietHoursStart] ?: DEFAULT_QUIET_START_HOUR
        val end = prefs[Keys.QuietHoursEnd] ?: DEFAULT_QUIET_END_HOUR
        return QuietHours(LocalTime.of(start.coerceIn(0, 23), 0), LocalTime.of(end.coerceIn(0, 23), 0))
    }

    override suspend fun setWeeklyCheckInPushEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WeeklyCheckInEnabled] = enabled }
    }

    override suspend fun setAmbientNudgesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AmbientNudgesEnabled] = enabled }
    }

    override suspend fun setQuietHours(startHour: Int, endHour: Int) {
        dataStore.edit {
            it[Keys.QuietHoursStart] = startHour.coerceIn(0, 23)
            it[Keys.QuietHoursEnd] = endHour.coerceIn(0, 23)
        }
    }

    override suspend fun lastPushedWeeklySignature(): String =
        dataStore.data.first()[Keys.LastPushedWeeklySignature] ?: ""

    override suspend fun setLastPushedWeeklySignature(signature: String) {
        dataStore.edit { it[Keys.LastPushedWeeklySignature] = signature }
    }

    private object Keys {
        val WeeklyCheckInEnabled = booleanPreferencesKey("weekly_check_in_push_enabled")
        val AmbientNudgesEnabled = booleanPreferencesKey("ambient_nudges_enabled")
        val QuietHoursStart = intPreferencesKey("quiet_hours_start")
        val QuietHoursEnd = intPreferencesKey("quiet_hours_end")
        val LastPushedWeeklySignature = stringPreferencesKey("last_pushed_weekly_signature")
    }

    companion object {
        const val DEFAULT_WEEKLY_ENABLED = true
        const val DEFAULT_AMBIENT_ENABLED = false
        const val DEFAULT_QUIET_START_HOUR = 22
        const val DEFAULT_QUIET_END_HOUR = 7
    }
}
