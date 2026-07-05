package com.zack.recomptracker.domain.export

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.rebalance.RebalanceState
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
    val planVersions: List<PlanVersionEntity> = emptyList(),
    /**
     * The persisted weekly-rebalance state (spec §9). Additive + nullable: an old backup (no key in
     * the JSON) decodes as `null`; [BackupRepository] restores that to a clean [RebalanceState()] —
     * dropping an in-flight rebalance on restore would otherwise re-judge already-reconciled days and
     * risk an immediate re-offer.
     */
    val rebalanceState: RebalanceState? = null,
)

/** A recipe plus its ingredients, for backup export/restore. */
@Serializable
data class RecipeBackup(
    val recipe: RecipeEntity,
    val ingredients: List<RecipeIngredientEntity>,
)
