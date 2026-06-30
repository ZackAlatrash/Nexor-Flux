package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One historical plan version. PK = effectiveFrom (ISO date); one row per change-day. */
@Serializable
@Entity(tableName = "plan_versions")
data class PlanVersionEntity(
    @PrimaryKey val effectiveFrom: String,
    val targetCalories: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int,
    val targetFatG: Int,
    val calorieZoneLowerBound: Int,
    val calorieZoneUpperBound: Int,
    val createdAt: String,
)
