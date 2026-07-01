package com.zack.recomptracker.data.coach

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.domain.coach.CoachSignal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.coachInboxDataStore by preferencesDataStore(name = "coach_inbox")

/**
 * DataStore-backed persistence for the proactive coach's *current featured signal* plus the
 * dedup/cooldown "seen" ledger and once-a-day debounce stamp. See
 * `docs/ai-redesign/08-technical-architecture.md` §9 (dedup/cooldown) and §10 (memory).
 *
 * Mirrors the [com.zack.recomptracker.data.preferences.AppPreferences] pattern: a
 * `preferencesDataStore` delegate, a `.data.map{}` [Flow], `.edit{}` suspend setters, and typed
 * [Keys]. All value-carrying logic (JSON (de)serialization, ledger merge/prune) lives in the pure
 * [CoachInboxSerialization] object and is unit-tested there; this class is thin I/O only.
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 */
class CoachInboxRepository(
    private val context: Context,
) {

    /**
     * The currently staged winner, or `null` when nothing is staged or the persisted value fails to
     * deserialize (logged, not thrown — a corrupt payload must never crash the surface).
     */
    val current: Flow<CoachSignal?> = context.coachInboxDataStore.data.map { prefs ->
        val raw = prefs[Keys.CurrentSignal]
        val decoded = CoachInboxSerialization.decodeSignal(raw)
        if (decoded == null && !raw.isNullOrBlank()) {
            Log.w(TAG, "Discarding un-deserializable staged CoachSignal")
        }
        decoded
    }

    /** Persist [signal] as the featured winner, or clear the slot when `null`. */
    suspend fun stage(signal: CoachSignal?) {
        context.coachInboxDataStore.edit { prefs ->
            if (signal == null) {
                prefs.remove(Keys.CurrentSignal)
            } else {
                prefs[Keys.CurrentSignal] = CoachInboxSerialization.encodeSignal(signal)
            }
        }
    }

    /** Clear the current signal (user acted on / dismissed it). */
    suspend fun dismissCurrent() {
        context.coachInboxDataStore.edit { it.remove(Keys.CurrentSignal) }
    }

    /**
     * Record that [dedupKey] was surfaced on [date] and prune stale entries, so the [SignalSelector]'s
     * cooldown ("a dedupKey can't re-surface until ≥7 days pass") can consult it.
     */
    suspend fun markSeen(dedupKey: String, date: LocalDate) {
        context.coachInboxDataStore.edit { prefs ->
            val existing = CoachInboxSerialization.decodeLedger(prefs[Keys.SeenLedger])
            val updated = CoachInboxSerialization.markSeen(existing, dedupKey, date)
            prefs[Keys.SeenLedger] = CoachInboxSerialization.encodeLedger(updated)
        }
    }

    /** The dedup/cooldown state the SignalSelector consumes: dedupKey → date last surfaced. */
    suspend fun seenLedger(): Map<String, LocalDate> =
        CoachInboxSerialization.decodeLedger(
            context.coachInboxDataStore.data.first()[Keys.SeenLedger],
        )

    /** The once-a-day debounce stamp for the digest coordinator, or `null` if never run. */
    suspend fun lastRunDate(): LocalDate? {
        val raw = context.coachInboxDataStore.data.first()[Keys.LastRunDate]
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    /** Stamp [date] as the last digest run (once-a-day debounce). */
    suspend fun setLastRunDate(date: LocalDate) {
        context.coachInboxDataStore.edit { it[Keys.LastRunDate] = date.toString() }
    }

    private object Keys {
        val CurrentSignal = stringPreferencesKey("current_signal")
        val SeenLedger = stringPreferencesKey("seen_ledger")
        val LastRunDate = stringPreferencesKey("last_run_date")
    }

    private companion object {
        const val TAG = "CoachInboxRepository"
    }
}
