package com.zack.recomptracker.domain.coach

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Pure (Android-free) JSON (de)serialization + dedup-ledger merge/prune logic backing
 * [CoachInboxRepository]. Extracted so the value-carrying logic can be unit-tested exhaustively
 * without DataStore I/O. See `docs/ai-redesign/08-technical-architecture.md` §9 (dedup/cooldown)
 * and §10 (memory).
 *
 * Deserialization is failure-tolerant: malformed / stale-schema strings decode to `null`/empty
 * rather than throwing, so a corrupt persisted value can never crash the surface reading it.
 *
 * Public, not `internal`: iOS calls this across the `Shared.framework` boundary, and Kotlin/Native
 * only exports `public` declarations to Objective-C (decision D12).
 */
object CoachInboxSerialization {

    /** How long a seen-ledger entry is retained. Older entries are pruned so the map can't grow unbounded. */
    const val LEDGER_RETENTION_DAYS: Long = 30

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Featured signal ──────────────────────────────────────────────────────

    /** Serialize a [CoachSignal] to a JSON string (round-trips with [decodeSignal]). */
    fun encodeSignal(signal: CoachSignal): String = json.encodeToString(CoachSignal.serializer(), signal)

    /**
     * Decode a persisted [CoachSignal] JSON string. Returns `null` for a blank string or any
     * malformed / schema-incompatible payload (never throws) — the caller logs, not crashes.
     */
    fun decodeSignal(raw: String?): CoachSignal? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(CoachSignal.serializer(), raw) }.getOrNull()
    }

    // ── Seen ledger (dedupKey -> date surfaced) ───────────────────────────────

    /** Serialize the dedup/cooldown ledger to a JSON string. Dates are stored as ISO-8601 (`YYYY-MM-DD`). */
    fun encodeLedger(ledger: Map<String, LocalDate>): String =
        json.encodeToString(
            LEDGER_SERIALIZER,
            ledger.mapValues { (_, date) -> date.toString() },
        )

    /**
     * Decode a persisted seen-ledger. Returns an empty map for a blank string or malformed payload;
     * individual entries with an unparseable date are dropped silently rather than failing the whole map.
     */
    fun decodeLedger(raw: String?): Map<String, LocalDate> {
        if (raw.isNullOrBlank()) return emptyMap()
        val stringMap = runCatching { json.decodeFromString(LEDGER_SERIALIZER, raw) }.getOrNull()
            ?: return emptyMap()
        return stringMap.mapNotNull { (key, iso) ->
            val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return@mapNotNull null
            key to date
        }.toMap()
    }

    /**
     * Record that [dedupKey] was surfaced on [date] (last-write-wins on an existing key) and prune the
     * result to the recent window in one step — the single transform a `markSeen` edit applies.
     */
    fun markSeen(
        ledger: Map<String, LocalDate>,
        dedupKey: String,
        date: LocalDate,
    ): Map<String, LocalDate> = prune(ledger + (dedupKey to date), date)

    /**
     * Drop entries older than [LEDGER_RETENTION_DAYS] before [reference] so the ledger stays bounded.
     * An entry dated exactly on the retention boundary is kept; strictly older ones are dropped.
     * Entries dated in the future relative to [reference] are always kept.
     *
     * Called on WRITE only, via [markSeen]. [decodeLedger] deliberately does not prune: a read must
     * return the ledger as stored, or cooldown windows would shrink on every read.
     */
    fun prune(ledger: Map<String, LocalDate>, reference: LocalDate): Map<String, LocalDate> {
        val cutoff = reference.minus(LEDGER_RETENTION_DAYS, DateTimeUnit.DAY)
        return ledger.filterValues { it >= cutoff }
    }

    private val LEDGER_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
}
