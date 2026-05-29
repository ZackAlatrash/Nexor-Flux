package com.zack.recomptracker.data.repository

import androidx.room.withTransaction
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.export.BackupPayload
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class BackupRepository(
    private val database: RecompDatabase,
    private val planRepository: PlanRepository,
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
        }
        planRepository.save(payload.preferences)
    }

    suspend fun resetEverything(defaultPreferences: PlanPreferences = PlanPreferences()) {
        database.withTransaction {
            database.clearAllTables()
            // Re-seed default meal slots so Today screen isn't empty after reset
            listOf("Meal 1", "Lunch", "Dinner").forEachIndexed { i, name ->
                database.mealSlotDao().insert(MealSlotEntity(name = name, sortOrder = i))
            }
        }
        planRepository.save(defaultPreferences)
    }
}
