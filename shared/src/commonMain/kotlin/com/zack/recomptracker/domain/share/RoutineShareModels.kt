package com.zack.recomptracker.domain.share

import kotlinx.serialization.Serializable

/**
 * Marks a payload as produced by this app, so a foreign JSON file that happens to parse is still
 * rejected on import. Never change this string — old shared files carry it forever.
 */
const val APP_ID = "recomptracker"

/**
 * Schema version of the routine-share format. Bump only on a breaking change; additive fields with
 * defaults do not need a bump. Import rejects any payload whose [RoutineSharePayload.version] exceeds
 * this (the file came from a newer app).
 */
const val CURRENT_SHARE_VERSION = 1

/** A shareable, device-portable snapshot of one routine (full copy: structure + reps + weights + notes). */
@Serializable
data class RoutineSharePayload(
    val version: Int = CURRENT_SHARE_VERSION,
    val app: String = APP_ID,
    val name: String,
    val note: String? = null,
    val exercises: List<SharedExercise> = emptyList(),
)

/**
 * One exercise line. Carries the portable identity `(source, externalId, name)` — NOT the sender's
 * local exerciseId, which is a device-assigned autoincrement. `name` is both the display label and
 * the fallback match when `externalId` is unknown on the recipient's device.
 */
@Serializable
data class SharedExercise(
    val source: String,
    val externalId: String,
    val name: String,
    val note: String? = null,
    val sets: List<SharedSet> = emptyList(),
)

/** One planned set target. Reps/weight nullable = "no target" (blank in the builder). Weight in kg. */
@Serializable
data class SharedSet(
    val setNumber: Int,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
)

/** Result of decoding a shared-routine file. Exactly one of these; the UI maps each to a message. */
sealed interface RoutineShareResult {
    data class Success(val payload: RoutineSharePayload) : RoutineShareResult
    /** File isn't ours: wrong/absent `app` marker. */
    data object NotARoutineFile : RoutineShareResult
    /** Payload `version` is newer than this app understands. */
    data object UnsupportedVersion : RoutineShareResult
    /** Unparseable JSON, or structurally empty (no exercises). */
    data object Damaged : RoutineShareResult
}
