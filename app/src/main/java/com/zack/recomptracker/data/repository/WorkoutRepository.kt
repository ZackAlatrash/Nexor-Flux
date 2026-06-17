package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.domain.workout.ValidationResult
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutValidation
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Caller-facing template line, decoupled from Room entities. */
data class NewWorkoutLine(
    val exerciseId: Long,
    val plannedSets: Int,
    val targetReps: Int? = null,
    val note: String? = null,
)

open class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val now: () -> String = { Instant.now().toString() },
) {

    open fun observeAll(): Flow<List<WorkoutTemplate>> =
        workoutDao.observeAllWithExercises().map { list -> list.map { it.toDomain() } }

    open suspend fun getById(id: Long): WorkoutTemplate? =
        workoutDao.getWithExercises(id)?.toDomain()

    open suspend fun saveWorkout(name: String, note: String?, lines: List<NewWorkoutLine>): Long {
        validate(name, lines)
        val timestamp = now()
        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = name.trim(), note = note?.trim(), createdAt = timestamp, updatedAt = timestamp),
        )
        workoutDao.replaceExercises(workoutId, lines.toEntities(workoutId))
        return workoutId
    }

    open suspend fun updateWorkout(workoutId: Long, name: String, note: String?, lines: List<NewWorkoutLine>) {
        validate(name, lines)
        val existing = workoutDao.getWithExercises(workoutId)?.workout
        val createdAt = existing?.createdAt ?: now()
        workoutDao.updateWorkout(
            WorkoutEntity(id = workoutId, name = name.trim(), note = note?.trim(), createdAt = createdAt, updatedAt = now()),
        )
        workoutDao.replaceExercises(workoutId, lines.toEntities(workoutId))
    }

    open suspend fun deleteWorkout(workoutId: Long) = workoutDao.deleteWorkoutById(workoutId)

    private fun validate(name: String, lines: List<NewWorkoutLine>) {
        val result = WorkoutValidation.validateTemplate(
            name = name,
            exerciseCount = lines.size,
            plannedSets = lines.map { it.plannedSets },
        )
        if (result is ValidationResult.Invalid) {
            throw IllegalArgumentException(result.reasons.joinToString(" "))
        }
    }

    private fun List<NewWorkoutLine>.toEntities(workoutId: Long): List<WorkoutExerciseEntity> =
        mapIndexed { index, line ->
            WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = line.exerciseId,
                plannedSets = line.plannedSets,
                targetReps = line.targetReps,
                sortOrder = index,
                note = line.note,
            )
        }
}
