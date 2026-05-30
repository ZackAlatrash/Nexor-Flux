package com.zack.recomptracker.domain.food

import com.zack.recomptracker.data.local.entity.MealEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFoodsTest {
    private fun entry(
        id: Long,
        name: String,
        mealType: String = "FOOD_LIBRARY",
        base: Int? = 120,
    ) = MealEntryEntity(
        id = id,
        date = "2026-05-30",
        mealType = mealType,
        name = name,
        calories = 100,
        proteinG = 1.0,
        carbsG = 1.0,
        fatG = 1.0,
        amountGrams = 100.0,
        basePer100Calories = base,
        basePer100ProteinG = base?.let { 1.0 },
        basePer100CarbsG = base?.let { 1.0 },
        basePer100FatG = base?.let { 1.0 },
    )

    @Test
    fun keepsMostRecentEntryPerNameNewestFirst() {
        val result = RecentFoods.fromEntries(
            listOf(
                entry(1, "Whey"),
                entry(2, "Oats"),
                entry(3, "whey"), // same name, newer id
            ),
        )
        assertEquals(listOf("whey", "Oats"), result.map { it.name })
        assertEquals(3L, result.first().id)
    }

    @Test
    fun ignoresQuickAddAndUnscalableEntries() {
        val result = RecentFoods.fromEntries(
            listOf(
                entry(1, "Quick add", mealType = "QUICK_ADD"),
                entry(2, "Mystery", base = null),
                entry(3, "Chicken"),
            ),
        )
        assertEquals(listOf("Chicken"), result.map { it.name })
    }

    @Test
    fun capsToLimit() {
        val entries = (1..12).map { entry(it.toLong(), "Food$it") }
        assertEquals(8, RecentFoods.fromEntries(entries, limit = 8).size)
    }
}
