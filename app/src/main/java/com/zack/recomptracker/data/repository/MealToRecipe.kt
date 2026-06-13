package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity

/**
 * Converts a logged meal entry into a recipe ingredient. The macro, amount, base-per-100,
 * and serving fields map 1:1; [sortOrder] comes from the ingredient's position in the new
 * recipe, and id/recipeId reset to 0 (assigned on save).
 */
fun MealEntryEntity.toRecipeIngredient(sortOrder: Int): RecipeIngredientEntity =
    RecipeIngredientEntity(
        id = 0,
        recipeId = 0,
        name = name,
        sortOrder = sortOrder,
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        amountGrams = amountGrams,
        basePer100Calories = basePer100Calories,
        basePer100ProteinG = basePer100ProteinG,
        basePer100CarbsG = basePer100CarbsG,
        basePer100FatG = basePer100FatG,
        entryServingName = entryServingName,
        entryServingGrams = entryServingGrams,
        loggedByServings = loggedByServings,
    )
