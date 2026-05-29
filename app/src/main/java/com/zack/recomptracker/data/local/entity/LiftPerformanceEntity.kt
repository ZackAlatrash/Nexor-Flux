package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "lift_performance",
    indices = [Index(value = ["date"]), Index(value = ["liftName"])],
)
data class LiftPerformanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val liftName: String,
    val weight: Double,
    val reps: Int,
    val sets: Int,
    val rir: Int?,
)
