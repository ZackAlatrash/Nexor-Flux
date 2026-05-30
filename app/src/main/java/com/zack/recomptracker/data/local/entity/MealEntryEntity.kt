package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "meal_entries",
    indices = [Index(value = ["date"])],
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealType: String,
    val name: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val slotId: Long? = null,
    val amountGrams: Double? = null,
    val basePer100Calories: Int? = null,
    val basePer100ProteinG: Double? = null,
    val basePer100CarbsG: Double? = null,
    val basePer100FatG: Double? = null,
    val entryServingName: String? = null,
    val entryServingGrams: Double? = null,
)
