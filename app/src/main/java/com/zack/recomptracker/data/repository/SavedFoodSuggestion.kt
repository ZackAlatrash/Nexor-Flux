package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.domain.food.SuggestionFood

/** Maps a saved food to the pure [SuggestionFood] DTO the MealSuggester engine consumes. */
fun SavedFoodEntity.toSuggestionFood(): SuggestionFood = SuggestionFood(
    name = name,
    servingLabel = servingName,
    gramsPerServing = householdServingGrams,
    calories = calories,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
)
