package com.zack.recomptracker.data.coach

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.domain.coach.PushEvent
import kotlinx.coroutines.flow.first

private val Context.pushHistoryDataStore by preferencesDataStore(name = "coach_push_history")

/**
 * The minimal record/read surface the proactive-coach push path depends on for its restraint caps,
 * extracted so callers (the digest coordinator / notification layer) can be unit-tested against a
 * simple in-memory fake instead of the concrete DataStore class. [PushHistoryStore] is the production
 * implementation. Mirrors [CoachJourney] / [CoachInbox].
 */
interface PushHistory {
    /** Append [event] to the ledger (append-only, pruned/capped on write). */
    suspend fun record(event: PushEvent)

    /**
     * The retained push events, oldest first — the input the
     * [com.zack.recomptracker.domain.coach.RateLimiter] measures its rolling caps against. Pruned of the
     * stale tail on read.
     */
    suspend fun recentPushes(): List<PushEvent>
}

/** Inert [PushHistory] for tests / Context-free construction: records nothing, returns nothing. */
object NoopPushHistory : PushHistory {
    override suspend fun record(event: PushEvent) = Unit
    override suspend fun recentPushes(): List<PushEvent> = emptyList()
}

/**
 * Append-only, offline-first local ledger of pushes actually emitted, feeding the Phase-5 restraint
 * caps ([com.zack.recomptracker.domain.coach.RateLimiter]): ≤2 pushes/week, ≤1 celebration/week, never
 * consecutive days. See `docs/ai-redesign/08-technical-architecture.md` §9 and §11.
 *
 * Mirrors [CoachJourneyStore]: a `preferencesDataStore` delegate + an injectable
 * [DataStore]<[Preferences]> primary constructor for tests, `.edit{}` suspend setters, a typed [Keys],
 * and kotlinx.serialization JSON for the event list. All value-carrying logic (append/cap/prune/
 * (de)serialization) lives in the pure [PushHistorySerialization] object and is unit-tested there;
 * this class is thin I/O only.
 *
 * Time is deterministic via the injected [DateProvider] (never `LocalDateTime.now()`). The ledger is
 * day-granular for pruning, so the day-start of "today" is a sufficient reference; the [RateLimiter]
 * itself receives the precise `now` at decision time.
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 */
class PushHistoryStore(
    private val dataStore: DataStore<Preferences>,
    private val dateProvider: DateProvider,
) : PushHistory {

    /** Production constructor — binds to the app-scoped `coach_push_history` DataStore. */
    constructor(context: Context, dateProvider: DateProvider) :
        this(context.pushHistoryDataStore, dateProvider)

    /** Append [event], pruning stale entries and capping the list length in one edit. */
    override suspend fun record(event: PushEvent) {
        val reference = dateProvider.today().atStartOfDay()
        dataStore.edit { prefs ->
            val existing = PushHistorySerialization.decode(prefs[Keys.Events])
            prefs[Keys.Events] = PushHistorySerialization.encode(
                PushHistorySerialization.appendPruned(existing, event, reference),
            )
        }
    }

    /** The retained push events, oldest first, with the stale tail pruned relative to today. */
    override suspend fun recentPushes(): List<PushEvent> {
        val reference = dateProvider.today().atStartOfDay()
        val stored = PushHistorySerialization.decode(dataStore.data.first()[Keys.Events])
        return PushHistorySerialization.prune(stored, reference)
    }

    private object Keys {
        val Events = stringPreferencesKey("push_events")
    }
}
