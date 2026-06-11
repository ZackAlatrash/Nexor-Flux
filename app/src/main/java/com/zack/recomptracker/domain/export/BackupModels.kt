package com.zack.recomptracker.domain.export

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: String,
    val preferences: PlanPreferences,
    val dailyLogs: List<DailyLogEntity>,
    val mealEntries: List<MealEntryEntity>,
    val savedFoods: List<SavedFoodEntity>,
    val savedMeals: List<SavedMealEntity>,
    val liftPerformances: List<LiftPerformanceEntity>,
    val weeklyReviews: List<WeeklyReviewEntity>,
    val mealSlots: List<MealSlotEntity> = emptyList(),
    val recipes: List<RecipeBackup> = emptyList(),
)

/** A recipe plus its ingredients, for backup export/restore. */
@Serializable
data class RecipeBackup(
    val recipe: RecipeEntity,
    val ingredients: List<RecipeIngredientEntity>,
)
