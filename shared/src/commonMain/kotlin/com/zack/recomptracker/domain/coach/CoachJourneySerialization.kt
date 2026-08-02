package com.zack.recomptracker.domain.coach

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A single fired-signal fact in the journey ledger: which [kind] fired, its [dedupKey], the review
 * [weekSignature] it fired under, and the ISO date it fired on. Append-only; never mutated in place.
 */
@Serializable
data class FiredSignalRecord(
    val kind: SignalKind,
    val dedupKey: String,
    val weekSignature: String,
    /** ISO-8601 `YYYY-MM-DD` the signal fired on. */
    val dateIso: String,
)

/**
 * A single weekly-verdict fact in the journey ledger: the review [weekSignature] (idempotency key),
 * the ISO week-end date, and the engine-authored [verdict] text (number-safe — the only text the
 * narrative may echo). Append-only.
 */
@Serializable
data class WeeklyVerdictRecord(
    val weekSignature: String,
    val weekEndDateIso: String,
    val verdict: String,
)

/**
 * Pure (Android-free) JSON (de)serialization + append/cap/idempotence/recurrence/narrative logic
 * backing [CoachJourneyStore]. Extracted so the value-carrying logic can be unit-tested exhaustively
 * without DataStore I/O. See `docs/ai-redesign/08-technical-architecture.md` §10 (memory).
 *
 * All rolling lists are append-only and capped so the persisted payloads can't grow unbounded.
 * Deserialization is failure-tolerant: malformed / stale-schema strings decode to an empty list
 * rather than throwing, so a corrupt persisted value can never crash a surface reading it.
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 *
 * Public, not `internal`: iOS calls this across the `Shared.framework` boundary, and Kotlin/Native
 * only exports `public` declarations to Objective-C (decision D12).
 */
object CoachJourneySerialization {

    /**
     * Cap on the fired-signal history. ~24 entries ≈ 8 weeks of a few signals/week, enough for the
     * recurrence rule (fired → ≥1-week gap → fired) while keeping the persisted JSON small.
     */
    const val FIRED_HISTORY_CAP: Int = 24

    /** Cap on the weekly-verdict list — the last ~8 weeks of verdicts feed the multi-week narrative. */
    const val VERDICT_CAP: Int = 8

    /** Below this many stored weekly verdicts there isn't enough history for a narrative. */
    const val NARRATIVE_MIN_VERDICTS: Int = 2

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Fired-signal history ──────────────────────────────────────────────────

    /** Append [record] to the end (newest last) and trim to the newest [FIRED_HISTORY_CAP] entries. */
    fun appendFired(history: List<FiredSignalRecord>, record: FiredSignalRecord): List<FiredSignalRecord> =
        (history + record).takeLast(FIRED_HISTORY_CAP)

    fun encodeFired(history: List<FiredSignalRecord>): String =
        json.encodeToString(FIRED_SERIALIZER, history)

    /** Decode fired history; returns an empty list for a blank string or malformed payload (never throws). */
    fun decodeFired(raw: String?): List<FiredSignalRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(FIRED_SERIALIZER, raw) }.getOrNull() ?: emptyList()
    }

    // ── Weekly verdicts ───────────────────────────────────────────────────────

    /**
     * Append [record] to the end (newest last), ignoring it if a verdict with the same
     * [WeeklyVerdictRecord.weekSignature] is already present (idempotent — same week recorded twice
     * yields one entry), and trim to the newest [VERDICT_CAP] entries.
     */
    fun appendVerdict(verdicts: List<WeeklyVerdictRecord>, record: WeeklyVerdictRecord): List<WeeklyVerdictRecord> {
        if (verdicts.any { it.weekSignature == record.weekSignature }) return verdicts
        return (verdicts + record).takeLast(VERDICT_CAP)
    }

    fun encodeVerdicts(verdicts: List<WeeklyVerdictRecord>): String =
        json.encodeToString(VERDICT_SERIALIZER, verdicts)

    fun decodeVerdicts(raw: String?): List<WeeklyVerdictRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(VERDICT_SERIALIZER, raw) }.getOrNull() ?: emptyList()
    }

    // ── Recurrence detection ──────────────────────────────────────────────────

    /**
     * True when [kind] fired, then had a gap of ≥1 distinct week with no fire, then fired again — i.e.
     * a signal that resolved and came back. Weeks are compared by [FiredSignalRecord.weekSignature];
     * two fires in consecutive weeks (no quiet week between them) do NOT count as a recurrence.
     */
    fun hasRecurred(history: List<FiredSignalRecord>, kind: SignalKind): Boolean {
        // Distinct weeks this kind fired in, ordered.
        val kindWeeks = history.filter { it.kind == kind }.map { it.weekSignature }.distinct()
        if (kindWeeks.size < 2) return false
        // All distinct weeks seen in the ledger, in order — the timeline the gap is measured against.
        val allWeeks = history.map { it.weekSignature }.distinct()
        // Is there a week in the overall timeline that sits strictly between two of this kind's fires
        // and in which this kind did NOT fire? That intervening quiet week is the "went silent" gap.
        for (i in 0 until kindWeeks.size - 1) {
            val startIdx = allWeeks.indexOf(kindWeeks[i])
            val endIdx = allWeeks.indexOf(kindWeeks[i + 1])
            if (endIdx - startIdx >= 2) return true
        }
        return false
    }

    /** The kinds that recurred (fired → gap → fired), in first-seen order. */
    fun recurredKinds(history: List<FiredSignalRecord>): List<SignalKind> =
        history.map { it.kind }.distinct().filter { hasRecurred(history, it) }

    // ── Narrative ─────────────────────────────────────────────────────────────

    /**
     * A compact, deterministic multi-week narrative built ONLY from stored facts — the last few
     * weekly verdicts plus any recurring signals. Returns "" when there are fewer than
     * [NARRATIVE_MIN_VERDICTS] weekly verdicts (not enough history). Introduces NO numbers beyond
     * those already inside the stored verdict strings. Decoration text — safe to drop.
     */
    fun narrative(verdicts: List<WeeklyVerdictRecord>, history: List<FiredSignalRecord>): String {
        if (verdicts.size < NARRATIVE_MIN_VERDICTS) return ""
        val recent = verdicts.takeLast(3)
        val sb = StringBuilder()
        sb.append("Over the last ")
        sb.append(numberWord(recent.size))
        sb.append(" weekly reviews: ")
        sb.append(recent.joinToString("; ") { it.verdict.trimEnd('.').trim() })
        sb.append('.')

        val recurred = recurredKinds(history)
        if (recurred.isNotEmpty()) {
            sb.append(" Recurring: ")
            sb.append(recurred.joinToString(", ") { it.name })
            sb.append(" (fired, went quiet, then returned).")
        }
        return sb.toString()
    }

    /** Spell small counts as words so the narrative never introduces a bare digit. */
    private fun numberWord(n: Int): String = when (n) {
        1 -> "one"
        2 -> "two"
        3 -> "three"
        else -> "several"
    }

    private val FIRED_SERIALIZER = ListSerializer(FiredSignalRecord.serializer())
    private val VERDICT_SERIALIZER = ListSerializer(WeeklyVerdictRecord.serializer())
}
