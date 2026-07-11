package com.zack.recomptracker.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1-20: re-creating a custom exercise with the same name must not crash. addCustomExercise used
 * @Insert(REPLACE), which DELETE+INSERTs on the unique (source, externalId) conflict — throwing the
 * FK (NO ACTION) violation when the exercise is already in a routine, and silently re-creating
 * unreferenced duplicates under a new id. The fix is an idempotent lookup-or-get-existing.
 *
 * Real in-memory Room so foreign keys are actually enforced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ExerciseLibraryCustomExerciseTest {

    private lateinit var database: RecompDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecompDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    /** Put [exerciseId] into a routine + a completed session (both FK-reference it). */
    private suspend fun referenceExercise(exerciseId: Long) {
        val workoutId = database.workoutDao().insertWorkout(
            WorkoutEntity(name = "Arms", note = null, createdAt = "t", updatedAt = "t"),
        )
        database.workoutDao().insertWorkoutExercise(
            WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, sortOrder = 0, note = null),
        )
        val sessionId = database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                workoutId = workoutId, workoutName = "Arms", date = "2026-07-05",
                startedAt = "s", completedAt = "e", status = "COMPLETED", note = null, durationSeconds = 60,
            ),
        )
        database.workoutSessionDao().insertSessionExercise(
            SessionExerciseEntity(sessionId = sessionId, exerciseId = exerciseId, exerciseName = "My Cable Curl", sortOrder = 0, note = null),
        )
    }

    @Test
    fun `re-adding a custom exercise already in a routine returns the existing id and does not crash`() = runTest {
        val repo = ExerciseLibraryRepository(database.exerciseDao())
        val firstId = repo.addCustomExercise("My Cable Curl")
        referenceExercise(firstId)

        val secondId = repo.addCustomExercise("My Cable Curl") // same name -> same externalId

        assertEquals(firstId, secondId) // idempotent, not REPLACE-recreated under a new id
        assertNotNull(database.exerciseDao().getById(firstId)) // the referenced row survives
        assertEquals(1, database.exerciseDao().getAll().count { it.source == "user" }) // no duplicate
    }

    @Test
    fun `re-adding an unreferenced custom exercise is idempotent`() = runTest {
        val repo = ExerciseLibraryRepository(database.exerciseDao())
        val firstId = repo.addCustomExercise("Zercher Squat")

        val secondId = repo.addCustomExercise("zercher   squat") // same slug after normalisation

        assertEquals(firstId, secondId)
        assertEquals(1, database.exerciseDao().getAll().count { it.source == "user" })
    }
}
