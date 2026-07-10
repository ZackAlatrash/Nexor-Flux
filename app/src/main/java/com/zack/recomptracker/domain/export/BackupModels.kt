package com.zack.recomptracker.domain.export

import com.zack.recomptracker.data.local.entity.CatalogFoodEntity
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import com.zack.recomptracker.data.local.entity.PlannedSetEntity
import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.rebalance.RebalanceState
import kotlinx.serialization.Serializable

/**
 * Schema version of the backup format the current app writes and can restore.
 *
 * v1 → v2 (July 2026): added the training feature (exercises, workouts + lines + planned sets,
 * sessions + exercises + sets) and the food catalog to the payload. Before v2, restoring a backup
 * ran `clearAllTables()` and never re-inserted these tables, permanently destroying all routines,
 * logged sessions, custom exercises, and the imported catalog (review P0-1). All new fields are
 * additive with defaults, so a v1 backup still decodes and restores.
 */
const val CURRENT_BACKUP_VERSION = 2

@Serializable
data class BackupPayload(
    val version: Int = CURRENT_BACKUP_VERSION,
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
    // --- v2 training + catalog tables. Additive with defaults so v1 backups still decode. ---
    // Kept as flat per-table lists (like dailyLogs/mealEntries) and restored id-for-id, so every
    // foreign key inside the training graph — workout/session lines referencing exercises, planned
    // sets, logged sets — resolves to exactly the rows it did on the source device.
    val exercises: List<ExerciseEntity> = emptyList(),
    val catalogFoods: List<CatalogFoodEntity> = emptyList(),
    val workouts: List<WorkoutEntity> = emptyList(),
    val workoutExercises: List<WorkoutExerciseEntity> = emptyList(),
    val plannedSets: List<PlannedSetEntity> = emptyList(),
    val workoutSessions: List<WorkoutSessionEntity> = emptyList(),
    val sessionExercises: List<SessionExerciseEntity> = emptyList(),
    val sessionSets: List<SessionSetEntity> = emptyList(),
)

/** A recipe plus its ingredients, for backup export/restore. */
@Serializable
data class RecipeBackup(
    val recipe: RecipeEntity,
    val ingredients: List<RecipeIngredientEntity>,
)
