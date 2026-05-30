package com.zack.recomptracker.domain.food

import com.zack.recomptracker.data.local.entity.MealEntryEntity

object RecentFoods {
    const val DEFAULT_LIMIT: Int = 8

    /**
     * Most recently logged, amount-editable library foods: one row per case-insensitive
     * trimmed name, newest first (highest id), capped at [limit].
     */
    fun fromEntries(entries: List<MealEntryEntity>, limit: Int = DEFAULT_LIMIT): List<MealEntryEntity> =
        entries
            .filter { it.mealType == MealEntryTypes.FOOD_LIBRARY && it.basePer100Calories != null }
            .sortedByDescending { it.id }
            .distinctBy { it.name.trim().lowercase() }
            .take(limit)
}
