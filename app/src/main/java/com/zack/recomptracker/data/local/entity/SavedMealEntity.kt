package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "saved_meals")
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mealType: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
