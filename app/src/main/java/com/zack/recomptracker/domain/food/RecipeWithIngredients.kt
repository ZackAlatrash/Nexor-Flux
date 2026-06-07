package com.zack.recomptracker.domain.food

import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity

data class RecipeWithIngredients(
    val recipe: RecipeEntity,
    val ingredients: List<RecipeIngredientEntity>,
) {
    val totalCalories: Int get() = ingredients.sumOf { it.calories }
    val totalProteinG: Double get() = ingredients.sumOf { it.proteinG }
    val totalCarbsG: Double get() = ingredients.sumOf { it.carbsG }
    val totalFatG: Double get() = ingredients.sumOf { it.fatG }
}
