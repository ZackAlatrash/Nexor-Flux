package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "weekly_reviews")
data class WeeklyReviewEntity(
    @PrimaryKey val weekStart: String,
    val verdict: String,
    val recommendedCalorieChange: Int,
    val reasonCodes: String,
    val generatedAt: String,
    val briefingJson: String? = null,
    val briefingSignature: String? = null,
    val briefingGeneratedAt: String? = null,
)
