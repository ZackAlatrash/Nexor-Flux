package com.zack.recomptracker.data.coach

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The single active Cross-Signal Discovery experiment (Phase 5). Recorded when the user taps
 * "Track this" on a discovery card: the correlation the hypothesis is about, the phrased hypothesis,
 * the metric to watch, the baseline value at start, and the ISO start date. There is at most ONE
 * active experiment (Q7b) — starting a new one overwrites any existing.
 *
 * Every field is a stored fact; no LLM text beyond the already-number-safe hypothesis string.
 */
@Serializable
data class CoachExperiment(
    /** Stable correlation identity, e.g. "PROTEIN_HIT_NEXT_DAY_HUNGER". */
    val correlationId: String,
    /** The engine-authored, number-safe hypothesis the user chose to test. */
    val hypothesis: String,
    /** The single metric the evaluation reads a current value for, e.g. "hunger" / "energy". */
    val trackedMetric: String,
    /** The tracked metric's window value at the moment the experiment started (the comparison anchor). */
    val baselineValue: Double,
    /** ISO-8601 `YYYY-MM-DD` the experiment started. */
    val startDateIso: String,
)

/**
 * Pure (Android-free) JSON (de)serialization for the single active [CoachExperiment], extracted so
 * the value logic is unit-testable without DataStore I/O. Deserialization is failure-tolerant: a
 * malformed / stale-schema string decodes to `null` rather than throwing, so a corrupt persisted
 * value can never crash a surface reading it. Mirrors [CoachJourneySerialization].
 */
internal object CoachExperimentSerialization {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(experiment: CoachExperiment): String = json.encodeToString(CoachExperiment.serializer(), experiment)

    /** Decode the active experiment; returns `null` for a blank string or malformed payload (never throws). */
    fun decode(raw: String?): CoachExperiment? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(CoachExperiment.serializer(), raw) }.getOrNull()
    }
}
