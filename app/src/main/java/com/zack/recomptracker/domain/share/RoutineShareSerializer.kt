package com.zack.recomptracker.domain.share

import kotlinx.serialization.json.Json

/**
 * Pure (Android-free) encode/decode for the routine-share file format. Encoding is deterministic;
 * decoding validates ownership (`app`), version, and structural sanity, returning a typed
 * [RoutineShareResult] so callers never crash on a bad file (spec: "features degrade, never crash").
 *
 * The version check ordering matters: we parse leniently first (so a future version whose JSON shape
 * still fits decodes), then gate on the marker and version explicitly.
 */
object RoutineShareSerializer {

    // Lenient like the rest of the app (see BackupRepository/ExerciseLibraryJson): ignore unknown keys
    // so a newer app's additive fields don't hard-fail an older reader.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: RoutineSharePayload): String =
        json.encodeToString(RoutineSharePayload.serializer(), payload)

    fun decode(raw: String): RoutineShareResult {
        val payload = runCatching {
            json.decodeFromString(RoutineSharePayload.serializer(), raw)
        }.getOrElse { return RoutineShareResult.Damaged }

        if (payload.app != APP_ID) return RoutineShareResult.NotARoutineFile
        if (payload.version > CURRENT_SHARE_VERSION) return RoutineShareResult.UnsupportedVersion
        // Structurally sane = a name, at least one exercise, and every exercise carrying at least one
        // set. A blank name or a set-less exercise would otherwise throw out of WorkoutRepository
        // .saveWorkout on import; reject here so a bad file degrades to Damaged instead of crashing.
        if (payload.name.isBlank()) return RoutineShareResult.Damaged
        if (payload.exercises.isEmpty()) return RoutineShareResult.Damaged
        if (payload.exercises.any { it.sets.isEmpty() }) return RoutineShareResult.Damaged
        return RoutineShareResult.Success(payload)
    }
}
