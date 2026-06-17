package com.zack.recomptracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.PlannedSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkoutPlannedSetsDatabaseTest {
    private lateinit var database: RecompDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecompDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun exercise(name: String) = ExerciseEntity(
        source = "test", sourceVersion = "v1", externalId = name, name = name,
        category = null, force = null, level = null, mechanic = null, equipment = null,
        primaryMuscles = "[]", secondaryMuscles = "[]", instructions = "[]", images = "[]",
    )

    @Test
    fun plannedSetsRoundTripWithNullableFields() = runBlocking {
        val exerciseDao = database.exerciseDao()
        val workoutDao = database.workoutDao()

        exerciseDao.insertAll(listOf(exercise("Squat")))
        val squatId = exerciseDao.search("Squat").first().id

        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = "Push", note = null, createdAt = "2026-06-17T10:00", updatedAt = "2026-06-17T10:00"),
        )

        workoutDao.replaceExercises(
            workoutId,
            listOf(
                WorkoutExerciseEntity(workoutId = workoutId, exerciseId = squatId, sortOrder = 0, note = null) to listOf(
                    PlannedSetEntity(workoutExerciseId = 0, setNumber = 1, targetReps = 15, targetWeightKg = 10.0),
                    PlannedSetEntity(workoutExerciseId = 0, setNumber = 2, targetReps = null, targetWeightKg = null),
                ),
            ),
        )

        val loaded = workoutDao.getWithExercises(workoutId)!!
        assertEquals(1, loaded.exercises.size)
        val plannedSets = loaded.exercises.single().plannedSets.sortedBy { it.setNumber }
        assertEquals(2, plannedSets.size)
        assertEquals(1, plannedSets[0].setNumber)
        assertEquals(15, plannedSets[0].targetReps)
        assertEquals(10.0, plannedSets[0].targetWeightKg!!, 0.0001)
        assertEquals(2, plannedSets[1].setNumber)
        assertNull(plannedSets[1].targetReps)
        assertNull(plannedSets[1].targetWeightKg)
    }

    @Test
    fun plannedSetsCascadeDeleteWithExercise() = runBlocking {
        val exerciseDao = database.exerciseDao()
        val workoutDao = database.workoutDao()

        exerciseDao.insertAll(listOf(exercise("Bench")))
        val benchId = exerciseDao.search("Bench").first().id

        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = "Chest Day", note = null, createdAt = "2026-06-17T10:00", updatedAt = "2026-06-17T10:00"),
        )

        workoutDao.replaceExercises(
            workoutId,
            listOf(
                WorkoutExerciseEntity(workoutId = workoutId, exerciseId = benchId, sortOrder = 0, note = null) to listOf(
                    PlannedSetEntity(workoutExerciseId = 0, setNumber = 1, targetReps = 10, targetWeightKg = 60.0),
                    PlannedSetEntity(workoutExerciseId = 0, setNumber = 2, targetReps = 10, targetWeightKg = 60.0),
                ),
            ),
        )

        val before = workoutDao.getWithExercises(workoutId)!!
        assertEquals(2, before.exercises.single().plannedSets.size)

        // Delete the workout exercises (simulates replaceExercises clearing)
        workoutDao.deleteExercisesByWorkoutId(workoutId)

        val after = workoutDao.getWithExercises(workoutId)!!
        assertTrue(after.exercises.isEmpty())
        // The planned_sets are CASCADE deleted because workout_exercises was deleted
    }
}
