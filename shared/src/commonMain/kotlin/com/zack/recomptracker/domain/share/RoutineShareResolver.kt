package com.zack.recomptracker.domain.share

import com.zack.recomptracker.domain.workout.Exercise

/**
 * A per-exercise import decision. [existingId] non-null = resolved to that local exercise;
 * null = not in the recipient's library, so import will create it as a custom exercise.
 */
data class Resolution(
    val shared: SharedExercise,
    val existingId: Long?,
) {
    val willCreate: Boolean get() = existingId == null
}

/**
 * Pure resolution of a shared routine's exercises against the recipient's library snapshot. Match
 * order: exact `externalId` (stable across installs that share the bundled free-exercise-db), then
 * case-insensitive `name` (survives a library-version drift), else unresolved → create on import.
 *
 * `source` is intentionally not part of the match: the domain [Exercise] model doesn't carry it, and
 * `externalId` is effectively unique across the two namespaces in practice (free-exercise-db slugs vs
 * `user_`-prefixed ids). It stays in the payload as self-describing metadata for future importers.
 */
object RoutineShareResolver {

    fun resolve(exercises: List<SharedExercise>, library: List<Exercise>): List<Resolution> {
        val byExternalId = library.associateBy { it.externalId }
        val byName = library.associateBy { it.name.lowercase() }
        return exercises.map { shared ->
            val existing = byExternalId[shared.externalId] ?: byName[shared.name.lowercase()]
            Resolution(shared = shared, existingId = existing?.id)
        }
    }
}
