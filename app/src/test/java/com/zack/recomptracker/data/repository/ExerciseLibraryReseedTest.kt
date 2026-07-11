package com.zack.recomptracker.data.repository

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1-19: shipping an updated bundled exercise library must succeed even when the user has routines /
 * sessions referencing the old rows. The prior re-seed did deleteBySource + insertAll; the bulk
 * delete hit the FK (NO ACTION) references from workout_exercises / session_exercises and threw,
 * so the re-seed failed silently on every launch. The id-preserving upsert fixes it.
 *
 * Real in-memory Room so foreign keys are actually enforced (mirrors BackupRepositoryRestoreTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ExerciseLibraryReseedTest {

    private val source = ExerciseLibraryRepository.SOURCE
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

    private fun libraryExercise(externalId: String, name: String) = ExerciseEntity(
        source = source, sourceVersion = "1", externalId = externalId, name = name,
        category = null, force = null, level = null, mechanic = null, equipment = null,
        primaryMuscles = "", secondaryMuscles = "", instructions = "", images = "", userCreated = false,
    )

    /** Insert a routine + a completed session, both referencing [exerciseId]. */
    private suspend fun referenceExercise(exerciseId: Long) {
        val workoutId = database.workoutDao().insertWorkout(
            WorkoutEntity(name = "Leg Day", note = null, createdAt = "t", updatedAt = "t"),
        )
        database.workoutDao().insertWorkoutExercise(
            WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, sortOrder = 0, note = null),
        )
        val sessionId = database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                workoutId = workoutId, workoutName = "Leg Day", date = "2026-07-05",
                startedAt = "s", completedAt = "e", status = "COMPLETED", note = null, durationSeconds = 60,
            ),
        )
        database.workoutSessionDao().insertSessionExercise(
            SessionExerciseEntity(sessionId = sessionId, exerciseId = exerciseId, exerciseName = "Squat", sortOrder = 0, note = null),
        )
    }

    @Test
    fun `re-seed via upsert preserves ids of referenced exercises and updates version`() = runTest {
        val dao = database.exerciseDao()
        // v1 library.
        dao.upsertLibrary(source, "v1", listOf(libraryExercise("a", "Squat"), libraryExercise("b", "Bench")))
        val squatId = dao.findIdBySourceAndExternalId(source, "a")!!
        // The user builds a routine + logs a session against it.
        referenceExercise(squatId)

        // Ship an updated library (v2): "a" renamed, "b" kept, "c" added.
        dao.upsertLibrary(
            source, "v2",
            listOf(libraryExercise("a", "Barbell Squat"), libraryExercise("b", "Bench"), libraryExercise("c", "Deadlift")),
        )

        // The referenced exercise kept its id (FK references still valid) and got the new name.
        assertEquals(squatId, dao.findIdBySourceAndExternalId(source, "a"))
        assertEquals("Barbell Squat", dao.getById(squatId)!!.name)
        // The new exercise landed and the version gate now reflects the completed re-seed.
        assertNotNull(dao.findIdBySourceAndExternalId(source, "c"))
        assertEquals("v2", dao.sourceVersion(source))
    }

    @Test
    fun `the old delete-then-insert re-seed crashes on a referenced exercise`() = runTest {
        val dao = database.exerciseDao()
        dao.upsertLibrary(source, "v1", listOf(libraryExercise("a", "Squat")))
        referenceExercise(dao.findIdBySourceAndExternalId(source, "a")!!)

        // Documents why the fix is needed: deleting the referenced row violates the FK (NO ACTION).
        var threw = false
        try {
            dao.deleteBySource(source)
        } catch (e: SQLiteConstraintException) {
            threw = true
        }
        assertTrue("deleting an FK-referenced exercise must throw a constraint violation", threw)
    }
}
