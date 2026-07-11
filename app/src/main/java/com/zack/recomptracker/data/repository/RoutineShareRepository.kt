package com.zack.recomptracker.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.domain.share.RoutineShareResolver
import com.zack.recomptracker.domain.share.RoutineShareResult
import com.zack.recomptracker.domain.share.RoutineShareSerializer
import com.zack.recomptracker.domain.share.RoutineSharePayload
import com.zack.recomptracker.domain.share.SharedExercise
import com.zack.recomptracker.domain.share.SharedSet
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Export/import of a single routine as a self-contained `.rtroutine` file. Export reads the DB graph
 * directly (so it can carry each exercise's `source`, which the domain model drops) and writes a
 * cache file shared read-only via [FileProvider]. Import resolves each exercise to a local id —
 * creating a custom exercise for anything the recipient doesn't have — then saves a NEW routine.
 * All disk/CPU work is off the main dispatcher.
 */
open class RoutineShareRepository(
    private val appContext: Context,
    private val workoutDao: WorkoutDao,
    private val workoutRepository: WorkoutRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
) {
    private val fileProviderAuthority = "${appContext.packageName}.fileprovider"

    /**
     * Builds a shareable `.rtroutine` file for [workoutId] and returns its FileProvider Uri, or null
     * if the routine no longer exists. `source`/`externalId`/`name` come straight off the joined
     * ExerciseEntity in the workout graph — never the local exerciseId.
     */
    open suspend fun buildShareFile(workoutId: Long): Uri? = withContext(Dispatchers.IO) {
        val db = workoutDao.getWithExercises(workoutId) ?: return@withContext null
        val payload = RoutineSharePayload(
            name = db.workout.name,
            note = db.workout.note,
            exercises = db.exercises
                .sortedBy { it.workoutExercise.sortOrder }
                .map { line ->
                    SharedExercise(
                        source = line.exercise.source,
                        externalId = line.exercise.externalId,
                        name = line.exercise.name,
                        note = line.workoutExercise.note,
                        sets = line.plannedSets
                            .sortedBy { it.setNumber }
                            .map { SharedSet(it.setNumber, it.targetReps, it.targetWeightKg) },
                    )
                },
        )
        val json = RoutineShareSerializer.encode(payload)
        val dir = File(appContext.cacheDir, "shared_routines").apply { mkdirs() }
        val file = File(dir, "${sanitize(db.workout.name)}.rtroutine")
        file.writeText(json)
        FileProvider.getUriForFile(appContext, fileProviderAuthority, file)
    }

    /** Reads and decodes a tapped file. Any read failure degrades to [RoutineShareResult.Damaged]. */
    suspend fun readPayload(uri: Uri): RoutineShareResult = withContext(Dispatchers.IO) {
        val raw = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull() ?: return@withContext RoutineShareResult.Damaged
        RoutineShareSerializer.decode(raw)
    }

    /**
     * The per-exercise import decisions against the current library — drives the preview "new" badge.
     */
    suspend fun resolve(payload: RoutineSharePayload) = withContext(Dispatchers.Default) {
        RoutineShareResolver.resolve(payload.exercises, exerciseLibraryRepository.all())
    }

    /**
     * Imports [payload] as a brand-new routine and returns its id. Never edits/overwrites existing
     * routines. Unresolved exercises are created as custom (idempotent via insertCustomOrGetExisting).
     */
    suspend fun importRoutine(payload: RoutineSharePayload): Long = withContext(Dispatchers.Default) {
        val resolutions = resolve(payload)
        val lines = resolutions.map { r ->
            val exerciseId = r.existingId ?: exerciseLibraryRepository.addCustomExercise(r.shared.name)
            NewWorkoutLine(
                exerciseId = exerciseId,
                plannedSets = r.shared.sets.map { PlannedSetDraft(it.targetReps, it.targetWeightKg) },
                note = r.shared.note,
            )
        }
        workoutRepository.saveWorkout(payload.name, payload.note, lines)
    }

    // Cache filename only — strip anything that isn't filename-safe; the display name lives in JSON.
    private fun sanitize(name: String): String =
        name.trim().ifBlank { "routine" }.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40)
}
