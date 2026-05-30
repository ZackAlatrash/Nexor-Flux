package com.zack.recomptracker.domain.foodimport

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalFoodMergerTest {
    private val yogurt = FoodImportCandidate(null, "Greek yogurt", "250g", 160, 25.0, 10.0, 2.0)
    private val oats = FoodImportCandidate(null, "Oats", "100g", 370, 13.0, 60.0, 7.0)

    @Test
    fun insertsOnlyFoodsMissingFromExistingLibrary() {
        val result = PersonalFoodMerger.newFoods(
            existing = listOf(yogurt),
            incoming = listOf(yogurt.copy(name = " greek  yogurt "), oats),
        )

        assertEquals(listOf(oats), result.foodsToInsert)
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun removesDuplicatesWithinIncomingFoods() {
        val result = PersonalFoodMerger.newFoods(
            existing = emptyList(),
            incoming = listOf(oats, oats.copy(name = "OATS")),
        )

        assertEquals(listOf(oats), result.foodsToInsert)
        assertEquals(1, result.duplicateCount)
    }
}
