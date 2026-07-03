package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.domain.food.SuggestionFood

/**
 * Maps a saved food to the pure [SuggestionFood] DTO the MealSuggester engine consumes.
 *
 * SavedFoodEntity macros are stored PER 100 g (see `FoodScaling` / `basePer100Calories`). The engine
 * treats a food's macros as one "serving" and [SuggestionFood.gramsPerServing] as the grams of that
 * serving, so the basis handed to it must be **100 g** — using [householdServingGrams] here would
 * make the engine's gram label disagree with the macros for any food whose serving ≠ 100 g.
 */
fun SavedFoodEntity.toSuggestionFood(): SuggestionFood = SuggestionFood(
    name = name,
    servingLabel = "100 g",
    gramsPerServing = 100.0,
    calories = calories,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
)
