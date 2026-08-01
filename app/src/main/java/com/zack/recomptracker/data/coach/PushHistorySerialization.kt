package com.zack.recomptracker.data.coach

import com.zack.recomptracker.domain.coach.PushEvent
import java.time.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Pure (Android-free) JSON (de)serialization + append/cap/prune logic backing [PushHistoryStore] — the
 * Phase-5 push ledger the [com.zack.recomptracker.domain.coach.RateLimiter] measures its rolling caps
 * against. Extracted so the value-carrying logic can be unit-tested exhaustively without DataStore I/O.
 * See `docs/ai-redesign/08-technical-architecture.md` §9 (restraint caps) and §11 (notifications).
 *
 * All rolling lists are append-only and both length-capped and time-pruned so the persisted payload
 * can't grow unbounded. Deserialization is failure-tolerant: a malformed / stale-schema string decodes
 * to an empty list rather than throwing, so a corrupt persisted value can never crash a caller.
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 */
internal object PushHistorySerialization {

    /**
     * How long a push event is retained. Comfortably larger than the 7-day cap window so the limiter
     * always sees a full rolling week (plus slack), while keeping the persisted list tiny.
     */
    const val RETENTION_DAYS: Long = 14

    /** Hard cap on the number of retained events — a safety bound in case many fire in a short span. */
    const val MAX_EVENTS: Int = 14

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(events: List<PushEvent>): String = json.encodeToString(SERIALIZER, events)

    /** Decode the push history; returns an empty list for a blank string or malformed payload (never throws). */
    fun decode(raw: String?): List<PushEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(SERIALIZER, raw) }.getOrNull() ?: emptyList()
    }

    /**
     * Append [event] (newest last), drop entries older than the retention window relative to [now], then
     * trim to the newest [MAX_EVENTS] — the single transform a `record` edit applies.
     */
    fun appendPruned(events: List<PushEvent>, event: PushEvent, now: LocalDateTime): List<PushEvent> =
        prune(events + event, now).takeLast(MAX_EVENTS)

    /**
     * Drop events whose timestamp is older than [RETENTION_DAYS] before [now]. An event dated exactly on
     * the retention boundary is kept; strictly-older ones are dropped. Future-dated events are kept.
     */
    fun prune(events: List<PushEvent>, now: LocalDateTime): List<PushEvent> {
        val cutoff = now.minusDays(RETENTION_DAYS).toKotlinLocalDateTime()
        return events.filter { it.timestamp >= cutoff }
    }

    private val SERIALIZER = ListSerializer(PushEvent.serializer())
}
