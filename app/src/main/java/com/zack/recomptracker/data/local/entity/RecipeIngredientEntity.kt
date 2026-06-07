package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["recipeId"])],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val amountGrams: Double? = null,
    val basePer100Calories: Int? = null,
    val basePer100ProteinG: Double? = null,
    val basePer100CarbsG: Double? = null,
    val basePer100FatG: Double? = null,
    val entryServingName: String? = null,
    val entryServingGrams: Double? = null,
    val loggedByServings: Boolean = false,
)
