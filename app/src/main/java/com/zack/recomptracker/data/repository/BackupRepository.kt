package com.zack.recomptracker.data.repository

import androidx.room.withTransaction
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.rebalance.RebalanceStore
import com.zack.recomptracker.domain.export.BackupPayload
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
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    suspend fun restoreFromJson(rawJson: String) {
        val payload = json.decodeFromString(BackupPayload.serializer(), rawJson)
        database.withTransaction {
            database.clearAllTables()
            if (payload.mealSlots.isEmpty()) {
                listOf("Meal 1", "Lunch", "Dinner").forEachIndexed { i, name ->
                    database.mealSlotDao().insert(MealSlotEntity(name = name, sortOrder = i))
                }
            } else {
                payload.mealSlots.forEach { database.mealSlotDao().insert(it.copy(id = 0)) }
            }
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
