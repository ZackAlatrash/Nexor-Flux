package com.zack.recomptracker.data.repository

import androidx.room.withTransaction
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.rebalance.RebalanceStore
import com.zack.recomptracker.domain.export.BackupPayload
import com.zack.recomptracker.domain.export.CURRENT_BACKUP_VERSION
import com.zack.recomptracker.domain.export.RecipeBackup
import com.zack.recomptracker.domain.rebalance.RebalanceState
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class BackupRepository(
    private val database: RecompDatabase,
    private val planRepository: PlanRepository,
    private val rebalanceStore: RebalanceStore,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackupJson(): String {
        val payload = BackupPayload(
            exportedAt = Instant.now().toString(),
            preferences = planRepository.preferences.first(),
            dailyLogs = database.dailyLogDao().getAll(),
            mealEntries = database.mealEntryDao().getAll(),
            savedFoods = database.savedFoodDao().getAll(),
            savedMeals = database.savedMealDao().getAll(),
            liftPerformances = database.performanceDao().getAll(),
            weeklyReviews = database.weeklyReviewDao().getAll(),
            mealSlots = database.mealSlotDao().getAll(),
            recipes = database.recipeDao().getAllWithIngredients().map {
                RecipeBackup(recipe = it.recipe, ingredients = it.ingredients)
            },
            planVersions = database.planVersionDao().getAll(),
            rebalanceState = rebalanceStore.current(),
            exercises = database.exerciseDao().getAll(),
            catalogFoods = database.catalogFoodDao().getAll(),
            workouts = database.workoutDao().getAllWorkouts(),
            workoutExercises = database.workoutDao().getAllWorkoutExercises(),
            plannedSets = database.workoutDao().getAllPlannedSets(),
            workoutSessions = database.workoutSessionDao().getAllSessions(),
            sessionExercises = database.workoutSessionDao().getAllSessionExercises(),
            sessionSets = database.workoutSessionDao().getAllSessionSets(),
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    suspend fun restoreFromJson(rawJson: String) {
        val payload = json.decodeFromString(BackupPayload.serializer(), rawJson)
        require(payload.version <= CURRENT_BACKUP_VERSION) {
            "This backup (v${payload.version}) was created by a newer version of the app and can't be " +
                "restored here. Update the app first."
        }
        database.withTransaction {
            // Wipes all tables, then re-inserts everything the payload carries below. The one table
            // deliberately not restored is `usage_events` (local, transient telemetry — never user
            // data worth carrying across a restore); it is simply cleared.
            database.clearAllTables()
            if (payload.mealSlots.isEmpty()) {
                listOf("Meal 1", "Lunch", "Dinner").forEachIndexed { i, name ->
                    database.mealSlotDao().insert(MealSlotEntity(name = name, sortOrder = i))
                }
            } else {
                // Keep the original slot ids: meal_entries reference them by id (with no foreign key
                // to catch a mismatch), so reassigning slot ids here would silently orphan every
                // restored meal from its slot (review P0-2).
                payload.mealSlots.forEach { database.mealSlotDao().insert(it) }
            }
            // Training + catalog, id-for-id, parents before children so every foreign key resolves.
            // Exercises come first: workout/session lines reference them by id, and both the bundled
            // library and the user's custom exercises must exist before those lines are inserted
            // (without this whole block, restore permanently wiped all training data — review P0-1).
            database.exerciseDao().insertAll(payload.exercises)
            database.catalogFoodDao().insertAll(payload.catalogFoods)
            payload.workouts.forEach { database.workoutDao().insertWorkout(it) }
            payload.workoutExercises.forEach { database.workoutDao().insertWorkoutExercise(it) }
            payload.plannedSets.forEach { database.workoutDao().insertPlannedSet(it) }
            payload.workoutSessions.forEach { database.workoutSessionDao().insertSession(it) }
            payload.sessionExercises.forEach { database.workoutSessionDao().insertSessionExercise(it) }
            payload.sessionSets.forEach { database.workoutSessionDao().insertSet(it) }
            database.dailyLogDao().insertAll(payload.dailyLogs)
            database.mealEntryDao().insertAll(payload.mealEntries)
            database.savedFoodDao().insertAll(payload.savedFoods)
            database.savedMealDao().insertAll(payload.savedMeals)
            database.performanceDao().insertAll(payload.liftPerformances)
            database.weeklyReviewDao().insertAll(payload.weeklyReviews)
            // Re-insert recipes with fresh ids so ingredient->recipe links stay consistent.
            payload.recipes.forEach { backup ->
                val newRecipeId = database.recipeDao().insertRecipe(backup.recipe.copy(id = 0))
                backup.ingredients.forEach { ingredient ->
                    database.recipeDao().insertIngredient(
                        ingredient.copy(id = 0, recipeId = newRecipeId),
                    )
                }
            }
            // Restore the plan-version ledger (cleared by clearAllTables above).
            database.planVersionDao().deleteAll()
            payload.planVersions.forEach { database.planVersionDao().upsert(it) }
        }
        planRepository.save(payload.preferences)
        // A v1 backup (no rebalanceState key) restores to a clean empty state (spec §9) rather than
        // leaving whatever was previously persisted on this device.
        rebalanceStore.save(payload.rebalanceState ?: RebalanceState())
    }

    suspend fun resetEverything(defaultPreferences: PlanPreferences = PlanPreferences()) {
        database.withTransaction {
            database.clearAllTables()
            // Clear the plan-version ledger; PlanHistoryInitializer reseeds a baseline on next start.
            database.planVersionDao().deleteAll()
            // Re-seed default meal slots so Today screen isn't empty after reset
            listOf("Meal 1", "Lunch", "Dinner").forEachIndexed { i, name ->
                database.mealSlotDao().insert(MealSlotEntity(name = name, sortOrder = i))
            }
        }
        planRepository.save(defaultPreferences)
        // Clear the weekly-rebalance state too. The save above only conditionally writes a plan
        // version row, so the coordinator's cancel-on-plan-edit hook is not guaranteed to fire — an
        // ACTIVE rebalance (and stale history/cooldowns) must not survive a full reset.
        rebalanceStore.save(RebalanceState())
    }
}
