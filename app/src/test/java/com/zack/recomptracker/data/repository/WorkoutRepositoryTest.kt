package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.PlannedSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseWithExercise
import com.zack.recomptracker.data.local.entity.WorkoutWithExercisesDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // stores (WorkoutExerciseEntity, List<PlannedSetEntity>)
        val lines = mutableListOf<Pair<WorkoutExerciseEntity, List<PlannedSetEntity>>>()
        private var nextWorkoutId = 0L
        private var nextLineId = 0L
        private var nextPlannedSetId = 0L

        override fun observeAllWithExercises(): Flow<List<WorkoutWithExercisesDb>> = flowOf(snapshot())
        override suspend fun getWithExercises(id: Long): WorkoutWithExercisesDb? =
            workouts[id]?.let { buildDb(it) }
        override suspend fun getAllWorkouts(): List<WorkoutEntity> = workouts.values.toList()
        override suspend fun getAllWorkoutExercises(): List<WorkoutExerciseEntity> = lines.map { it.first }
        override suspend fun getAllPlannedSets(): List<PlannedSetEntity> = lines.flatMap { it.second }
        override suspend fun insertWorkout(workout: WorkoutEntity): Long {
            val id = ++nextWorkoutId
            workouts[id] = workout.copy(id = id)
            return id
        }
        override suspend fun updateWorkout(workout: WorkoutEntity) { workouts[workout.id] = workout }
        override suspend fun deleteWorkoutById(id: Long) {
            workouts.remove(id)
            lines.removeAll { it.first.workoutId == id }
        }
        override suspend fun insertWorkoutExercise(line: WorkoutExerciseEntity): Long {
            val id = ++nextLineId
            // stored via replaceExercises, not directly
            return id
        }
        override suspend fun insertPlannedSet(set: PlannedSetEntity): Long {
            val id = ++nextPlannedSetId
            return id
        }
        override suspend fun deleteExercisesByWorkoutId(workoutId: Long) {
            lines.removeAll { it.first.workoutId == workoutId }
        }
        override suspend fun deletePlannedSetsByExerciseId(workoutExerciseId: Long) {
            // no-op: cascade handled by deleteExercisesByWorkoutId in fake
        }

        // Override replaceExercises to store both exercise + planned sets
        override suspend fun replaceExercises(workoutId: Long, rawLines: List<Pair<WorkoutExerciseEntity, List<PlannedSetEntity>>>) {
            deleteExercisesByWorkoutId(workoutId)
            rawLines.forEachIndexed { index, (line, planned) ->
                val exId = ++nextLineId
                val storedLine = line.copy(workoutId = workoutId, sortOrder = index, id = exId)
                val storedPlanned = planned.mapIndexed { n, ps ->
                    ps.copy(workoutExerciseId = exId, setNumber = n + 1, id = ++nextPlannedSetId)
                }
                lines.add(storedLine to storedPlanned)
            }
        }

        private fun buildDb(w: WorkoutEntity) = WorkoutWithExercisesDb(
            workout = w,
            exercises = lines.filter { it.first.workoutId == w.id }.map { (line, planned) ->
                WorkoutExerciseWithExercise(line, library.getValue(line.exerciseId), planned)
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
                NewWorkoutLine(exerciseId = 2, plannedSets = listOf(
                    PlannedSetDraft(targetReps = 8, targetWeightKg = null),
                    PlannedSetDraft(targetReps = 8, targetWeightKg = null),
                    PlannedSetDraft(targetReps = 8, targetWeightKg = null),
                    PlannedSetDraft(targetReps = 8, targetWeightKg = null),
                )),
                NewWorkoutLine(exerciseId = 1, plannedSets = listOf(
                    PlannedSetDraft(targetReps = 5, targetWeightKg = null),
                    PlannedSetDraft(targetReps = 5, targetWeightKg = null),
                    PlannedSetDraft(targetReps = 5, targetWeightKg = null),
                )),
            ),
        )

        val loaded = repo.getById(id)!!
        assertEquals("Day A", loaded.name)
        assertEquals(listOf(0, 1), loaded.exercises.map { it.sortOrder })
        assertEquals("Bench", loaded.exercises[0].exercise.name)
        assertEquals(4, loaded.exercises[0].plannedSets.size)
    }

    @Test
    fun `saveWorkout rejects blank name`() = runTest {
        val (repo, _) = repo()
        val ex = runCatching {
            repo.saveWorkout(name = "  ", note = null, lines = listOf(
                NewWorkoutLine(1, listOf(PlannedSetDraft(5, null)))
            ))
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
        val id = repo.saveWorkout("Day A", null, listOf(
            NewWorkoutLine(1, listOf(PlannedSetDraft(5, null)))
        ))

        repo.deleteWorkout(id)

        assertTrue(dao.workouts.isEmpty())
    }

    @Test
    fun `saveWorkout persists per-set planned targets including nulls`() = runTest {
        val (repo, dao) = repo()
        val id = repo.saveWorkout(
            name = "Push", note = null,
            lines = listOf(
                NewWorkoutLine(exerciseId = 1, plannedSets = listOf(
                    PlannedSetDraft(targetReps = 15, targetWeightKg = 10.0),
                    PlannedSetDraft(targetReps = null, targetWeightKg = null),
                )),
            ),
        )
        val loaded = repo.getById(id)!!
        val sets = loaded.exercises.single().plannedSets
        assertEquals(2, sets.size)
        assertEquals(15, sets[0].targetReps)
        assertEquals(10.0, sets[0].targetWeightKg!!, 0.0001)
        assertNull(sets[1].targetReps)
        assertNull(sets[1].targetWeightKg)
    }
}
