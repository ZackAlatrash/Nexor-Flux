package com.zack.recomptracker.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.CatalogFoodEntity
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.PlannedSetEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.PlanPreferencesSource
import com.zack.recomptracker.data.rebalance.FakeRebalanceStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Full export -> restore round-trip through the REAL restore path (in-memory Room + real
 * [PlanRepository] over an in-memory [PlanPreferencesSource], against a [FakeRebalanceStore]).
 *
 * This is the safety net the July 2026 review found missing: [BackupRepository.restoreFromJson]
 * runs `clearAllTables()` (wiping all 19 tables) and then only re-inserts a subset, so anything the
 * payload forgets is destroyed. These tests pin the two data-loss regressions (review P0-1 training
 * tables, P0-2 orphaned meal-slot links). Mirrors [BackupRepositoryResetTest]'s Robolectric shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class BackupRepositoryRestoreTest {

    private class FakePreferences(initial: PlanPreferences = PlanPreferences()) : PlanPreferencesSource {
        private val state = MutableStateFlow(initial)
        override val preferences: Flow<PlanPreferences> = state
        override suspend fun save(preferences: PlanPreferences) { state.value = preferences }
        override suspend fun resetDefaults() { state.value = PlanPreferences() }
    }

    private class FixedDateProvider(private val date: LocalDate) : DateProvider {
        override fun today(): LocalDate = date
    }

    private lateinit var database: RecompDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecompDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newRepository() = BackupRepository(
        database = database,
        planRepository = PlanRepository(
            appPreferences = FakePreferences(),
            planVersionDao = database.planVersionDao(),
            dateProvider = FixedDateProvider(LocalDate.of(2026, 7, 8)),
        ),
        rebalanceStore = FakeRebalanceStore(),
    )

    private data class SeededIds(
        val exerciseId: Long,
        val workoutId: Long,
        val sessionId: Long,
        val lunchSlotId: Long,
        val mealEntryId: Long,
    )

    /**
     * Seeds a custom exercise, a routine referencing it (workout -> line -> planned set), a
     * completed session referencing it (session -> exercise -> set), a NEVO catalog row, three meal
     * slots, and a meal entry assigned to the *second* slot. Inserted in foreign-key-safe order.
     *
     * The three slots take ids 1, 2, 3 and advance the AUTOINCREMENT sequence to 3; a buggy restore
     * that re-inserts slots with `id = 0` therefore hands out fresh ids 4, 5, 6, orphaning the
     * entry's `slotId = 2`.
     */
    private suspend fun seedTrainingAndSlottedMeal(): SeededIds {
        val exerciseId = database.exerciseDao().insertReturningId(
            ExerciseEntity(
                source = "user", sourceVersion = "1", externalId = "user_cable_row",
                name = "Cable Row", category = null, force = null, level = null, mechanic = null,
                equipment = null, primaryMuscles = "lats", secondaryMuscles = "", instructions = "",
                images = "", userCreated = true,
            ),
        )
        val workoutId = database.workoutDao().insertWorkout(
            WorkoutEntity(
                name = "Pull Day", note = null,
                createdAt = "2026-07-01T08:00:00Z", updatedAt = "2026-07-01T08:00:00Z",
            ),
        )
        val workoutExerciseId = database.workoutDao().insertWorkoutExercise(
            WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, sortOrder = 0, note = null),
        )
        database.workoutDao().insertPlannedSet(
            PlannedSetEntity(workoutExerciseId = workoutExerciseId, setNumber = 1, targetReps = 8, targetWeightKg = 60.0),
        )
        val sessionId = database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                workoutId = workoutId, workoutName = "Pull Day", date = "2026-07-05",
                startedAt = "2026-07-05T09:00:00Z", completedAt = "2026-07-05T10:00:00Z",
                status = "COMPLETED", note = null, durationSeconds = 3600,
            ),
        )
        val sessionExerciseId = database.workoutSessionDao().insertSessionExercise(
            SessionExerciseEntity(
                sessionId = sessionId, exerciseId = exerciseId, exerciseName = "Cable Row",
                sortOrder = 0, note = null,
            ),
        )
        database.workoutSessionDao().insertSet(
            SessionSetEntity(sessionExerciseId = sessionExerciseId, setNumber = 1, reps = 8, weightKg = 60.0, rir = 2, completed = true),
        )
        database.catalogFoodDao().insertAll(
            listOf(
                CatalogFoodEntity(
                    source = "nevo", sourceVersion = "2023", externalId = "1001", name = "Gouda cheese",
                    servingName = "100 g", calories = 356, proteinG = 25.0, carbsG = 0.0, fatG = 28.0,
                ),
            ),
        )
        database.mealSlotDao().insert(MealSlotEntity(name = "Breakfast", sortOrder = 0))
        val lunchSlotId = database.mealSlotDao().insert(MealSlotEntity(name = "Lunch", sortOrder = 1))
        database.mealSlotDao().insert(MealSlotEntity(name = "Dinner", sortOrder = 2))
        database.dailyLogDao().upsert(DailyLogEntity(date = "2026-07-05", trained = true))
        val mealEntryId = database.mealEntryDao().insert(
            MealEntryEntity(
                date = "2026-07-05", mealType = "RECIPE", name = "Lunch bowl",
                calories = 500, proteinG = 40.0, carbsG = 50.0, fatG = 15.0, slotId = lunchSlotId,
            ),
        )
        return SeededIds(exerciseId, workoutId, sessionId, lunchSlotId, mealEntryId)
    }

    @Test
    fun `restoring a backup preserves routines, sessions, custom exercises, and the catalog`() = runTest {
        val repository = newRepository()
        val ids = seedTrainingAndSlottedMeal()

        val json = repository.createBackupJson()
        repository.restoreFromJson(json)

        assertNotNull("custom exercise survives restore", database.exerciseDao().getById(ids.exerciseId))

        val workout = database.workoutDao().getWithExercises(ids.workoutId)
        assertNotNull("workout template survives restore", workout)
        assertEquals("workout keeps its exercise line", 1, workout!!.exercises.size)
        assertEquals("workout keeps its planned set", 1, workout.exercises.single().plannedSets.size)

        val session = database.workoutSessionDao().getSessionWithDetails(ids.sessionId)
        assertNotNull("logged session survives restore", session)
        assertEquals("session keeps its logged set", 1, session!!.exercises.single().sets.size)

        assertTrue("NEVO catalog survives restore", database.catalogFoodDao().getAll().isNotEmpty())
    }

    @Test
    fun `restoring a backup keeps meal entries linked to their slot`() = runTest {
        val repository = newRepository()
        val ids = seedTrainingAndSlottedMeal()

        val json = repository.createBackupJson()
        repository.restoreFromJson(json)

        val slotIds = database.mealSlotDao().getAll().map { it.id }.toSet()
        val entry = database.mealEntryDao().getById(ids.mealEntryId)
        assertNotNull("meal entry survives restore", entry)
        assertNotNull("meal entry keeps a slot assignment", entry!!.slotId)
        assertTrue(
            "restored meal entry still points at an existing slot (slotId=${entry.slotId}, slots=$slotIds)",
            entry.slotId in slotIds,
        )
    }

    @Test
    fun `restoring a backup from a newer app version is refused without wiping existing data`() = runTest {
        val repository = newRepository()
        val ids = seedTrainingAndSlottedMeal()
        // A backup claiming a schema version this app doesn't understand yet.
        val futureBackup = repository.createBackupJson().replace("\"version\": 2", "\"version\": 99")

        val error = runCatching { repository.restoreFromJson(futureBackup) }.exceptionOrNull()

        assertTrue("restore of a newer-version backup is rejected", error is IllegalArgumentException)
        // The version guard runs before clearAllTables, so existing data must be untouched.
        assertNotNull(
            "existing data is preserved when a restore is refused",
            database.exerciseDao().getById(ids.exerciseId),
        )
    }
}
