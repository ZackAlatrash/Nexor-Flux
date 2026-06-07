package com.zack.recomptracker.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class RecipeWithIngredientsDb(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "recipeId",
    )
    val ingredients: List<RecipeIngredientEntity>,
)
