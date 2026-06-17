package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseWithExercise
import com.zack.recomptracker.data.local.entity.WorkoutWithExercisesDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRepositoryTest {

    private fun exerciseEntity(id: Long, name: String) = ExerciseEntity(
        id = id, source = "test", sourceVersion = "v1", externalId = name, name = name,
        category = null, force = null, level = null, mechanic = null, equipment = null,
        primaryMuscles = "[]", secondaryMuscles = "[]", instructions = "[]", images = "[]",
    )

    private class FakeWorkoutDao(
        private val library: Map<Long, ExerciseEntity>,
    ) : WorkoutDao() {
        val workouts = mutableMapOf<Long, WorkoutEntity>()
        val lines = mutableListOf<WorkoutExerciseEntity>()
        private var nextWorkoutId = 0L
        private var nextLineId = 0L

        override fun observeAllWithExercises(): Flow<List<WorkoutWithExercisesDb>> = flowOf(snapshot())
        override suspend fun getWithExercises(id: Long): WorkoutWithExercisesDb? =
            workouts[id]?.let { buildDb(it) }
        override suspend fun insertWorkout(workout: WorkoutEntity): Long {
            val id = ++nextWorkoutId
            workouts[id] = workout.copy(id = id)
            return id
        }
        override suspend fun updateWorkout(workout: WorkoutEntity) { workouts[workout.id] = workout }
        override suspend fun deleteWorkoutById(id: Long) { workouts.remove(id); lines.removeAll { it.workoutId == id } }
        override suspend fun insertWorkoutExercise(line: WorkoutExerciseEntity): Long {
            val id = ++nextLineId
            lines.add(line.copy(id = id))
            return id
        }
        override suspend fun deleteExercisesByWorkoutId(workoutId: Long) { lines.removeAll { it.workoutId == workoutId } }

        private fun buildDb(w: WorkoutEntity) = WorkoutWithExercisesDb(
            workout = w,
            exercises = lines.filter { it.workoutId == w.id }.map { line ->
                WorkoutExerciseWithExercise(line, library.getValue(line.exerciseId))
            },
        )
        private fun snapshot() = workouts.values.map { buildDb(it) }
    }

    private fun repo(): Pair<WorkoutRepository, FakeWorkoutDao> {
        val library = mapOf(1L to exerciseEntity(1, "Squat"), 2L to exerciseEntity(2, "Bench"))
        val dao = FakeWorkoutDao(library)
        return WorkoutRepository(dao) { "2026-06-17T10:00" } to dao
    }

    @Test
    fun `saveWorkout persists workout and ordered exercises`() = runTest {
        val (repo, dao) = repo()

        val id = repo.saveWorkout(
            name = "Day A",
            note = null,
            lines = listOf(
                NewWorkoutLine(exerciseId = 2, plannedSets = 4, targetReps = 8),
                NewWorkoutLine(exerciseId = 1, plannedSets = 3, targetReps = 5),
            ),
        )

        val loaded = repo.getById(id)!!
        assertEquals("Day A", loaded.name)
        assertEquals(listOf(0, 1), loaded.exercises.map { it.sortOrder })
        assertEquals("Bench", loaded.exercises[0].exercise.name)
        assertEquals(4, loaded.exercises[0].plannedSets)
    }

    @Test
    fun `saveWorkout rejects blank name`() = runTest {
        val (repo, _) = repo()
        val ex = runCatching {
            repo.saveWorkout(name = "  ", note = null, lines = listOf(NewWorkoutLine(1, 3, 5)))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `saveWorkout rejects empty exercise list`() = runTest {
        val (repo, _) = repo()
        val ex = runCatching {
            repo.saveWorkout(name = "Empty", note = null, lines = emptyList())
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `deleteWorkout removes it`() = runTest {
        val (repo, dao) = repo()
        val id = repo.saveWorkout("Day A", null, listOf(NewWorkoutLine(1, 3, 5)))

        repo.deleteWorkout(id)

        assertTrue(dao.workouts.isEmpty())
    }
}
