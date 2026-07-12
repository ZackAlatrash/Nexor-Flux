package com.zack.recomptracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "meal_slots")
data class MealSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

/**
 * The default meal slots. Seeded on a fresh install (RecompDatabase onCreate) and, idempotently, at
 * app start when the table is empty (MealSlotInitializer) — so devices first created at schema v>=2
 * that never ran MIGRATION_1_2's seed self-heal (P1-21). Keep in sync with MIGRATION_1_2.
 */
val DEFAULT_MEAL_SLOTS: List<MealSlotEntity> = listOf(
    MealSlotEntity(name = "Meal 1", sortOrder = 0),
    MealSlotEntity(name = "Lunch", sortOrder = 1),
    MealSlotEntity(name = "Dinner", sortOrder = 2),
)
