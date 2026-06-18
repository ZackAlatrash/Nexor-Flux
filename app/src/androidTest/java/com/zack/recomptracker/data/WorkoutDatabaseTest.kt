package com.zack.recomptracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.PlannedSetEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WorkoutDatabaseTest {
    private lateinit var database: RecompDatabase

    private fun exercise(externalId: String, name: String) = ExerciseEntity(
        source = "test", sourceVersion = "v1", externalId = externalId, name = name,
        category = "strength", force = null, level = "beginner", mechanic = null, equipment = null,
        primaryMuscles = "[]", secondaryMuscles = "[]", instructions = "[]", images = "[]",
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecompDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun workoutTemplateWithExercisesRoundTrips() = runBlocking {
        val exerciseDao = database.exerciseDao()
        val workoutDao = database.workoutDao()
        exerciseDao.insertAll(listOf(exercise("Squat", "Squat"), exercise("Bench", "Bench Press")))
        val squatId = exerciseDao.search("Squat").first().id
        val benchId = exerciseDao.search("Bench").first().id

        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = "Day A", note = null, createdAt = "2026-06-17T10:00", updatedAt = "2026-06-17T10:00"),
        )
        workoutDao.replaceExercises(
            workoutId,
            listOf(
                WorkoutExerciseEntity(workoutId = workoutId, exerciseId = squatId, sortOrder = 0, note = null) to emptyList(),
                WorkoutExerciseEntity(workoutId = workoutId, exerciseId = benchId, sortOrder = 1, note = null) to emptyList(),
            ),
        )

        val loaded = workoutDao.getWithExercises(workoutId)!!
        assertEquals(2, loaded.exercises.size)
        assertEquals("Squat", loaded.exercises.first { it.workoutExercise.sortOrder == 0 }.exercise.name)
    }

    @Test
    fun lastCompletedSessionReturnsMostRecentAndHistoryIsFlat() = runBlocking {
        val exerciseDao = database.exerciseDao()
        val sessionDao = database.workoutSessionDao()
        val workoutDao = database.workoutDao()
        exerciseDao.insertAll(listOf(exercise("Squat", "Squat")))
        val squatId = exerciseDao.search("Squat").first().id
        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = "Legs", note = null, createdAt = "2026-06-01T10:00", updatedAt = "2026-06-01T10:00"),
        )

        suspend fun logSession(date: String, weight: Double) {
            val sessionId = sessionDao.insertSession(
                WorkoutSessionEntity(
                    workoutId = workoutId, workoutName = "Legs", date = date,
                    startedAt = date + "T10:00", completedAt = date + "T11:00",
                    status = "COMPLETED", note = null, durationSeconds = null,
                ),
            )
            val seId = sessionDao.insertSessionExercise(
                SessionExerciseEntity(sessionId = sessionId, exerciseId = squatId, exerciseName = "Squat", sortOrder = 0, note = null),
            )
            sessionDao.insertSet(SessionSetEntity(sessionExerciseId = seId, setNumber = 1, reps = 5, weightKg = weight, rir = 2, completed = true))
        }

        logSession("2026-06-10", 100.0)
        logSession("2026-06-17", 110.0)

        val last = sessionDao.getLastCompletedSession(workoutId)!!
        assertEquals("2026-06-17", last.session.date)
        assertEquals(110.0, last.exercises.first().sets.first().weightKg!!, 0.0001)

        val history = sessionDao.getExerciseHistory(squatId)
        assertEquals(2, history.size)
        assertEquals("2026-06-10", history[0].date)
        assertEquals(110.0, history[1].weightKg!!, 0.0001)
    }

    @Test
    fun deletingWorkoutNullsSessionLinkButKeepsHistory() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        val workoutDao = database.workoutDao()
        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = "Temp", note = null, createdAt = "2026-06-17T10:00", updatedAt = "2026-06-17T10:00"),
        )
        val sessionId = sessionDao.insertSession(
            WorkoutSessionEntity(
                workoutId = workoutId, workoutName = "Temp", date = "2026-06-17",
                startedAt = "2026-06-17T10:00", completedAt = "2026-06-17T11:00", status = "COMPLETED", note = null, durationSeconds = null,
            ),
        )

        workoutDao.deleteWorkoutById(workoutId)

        val session = sessionDao.getSessionWithDetails(sessionId)!!
        assertNull(session.session.workoutId)
        assertEquals("Temp", session.session.workoutName)
    }
}
