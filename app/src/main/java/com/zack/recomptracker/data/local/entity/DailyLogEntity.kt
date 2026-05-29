package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "daily_logs")
data class DailyLogEntity(
    @PrimaryKey val date: String,
    val bodyWeightKg: Double? = null,
    val waistCm: Double? = null,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val energyScore: Int? = null,
    val hungerScore: Int? = null,
    val sorenessScore: Int? = null,
    val trained: Boolean = false,
    val notes: String = "",
)
