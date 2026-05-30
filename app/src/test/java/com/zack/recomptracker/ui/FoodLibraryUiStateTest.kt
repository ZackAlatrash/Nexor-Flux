package com.zack.recomptracker.ui

import com.zack.recomptracker.data.local.entity.CatalogFoodEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodLibraryUiStateTest {
    private val personal = SavedFoodEntity(
        id = 1,
        name = "Greek yogurt",
        servingName = "100g",
        calories = 60,
        proteinG = 10.0,
        carbsG = 4.0,
        fatG = 0.0,
    )
    private val nevo = CatalogFoodEntity(
        id = 2,
        source = "NEVO",
        sourceVersion = "2025/9.0",
        externalId = "123",
        name = "Yoghurt halfvol",
        servingName = "100g",
        calories = 60,
        proteinG = 4.0,
        carbsG = 5.0,
        fatG = 2.0,
    )

    @Test
    fun emptyQueryShowsOnlyPersonalFoods() {
        val state = FoodLibraryUiState(allFoods = listOf(personal), allCatalogFoods = listOf(nevo))

        assertEquals(listOf("personal_1"), state.filteredFoods.map { it.key })
    }

    @Test
    fun typedQueryIncludesMatchingNevoFoodsAfterPersonalFoods() {
        val state = FoodLibraryUiState(
            query = "yog",
            allFoods = listOf(personal),
            allCatalogFoods = listOf(nevo),
        )

        assertEquals(listOf("personal_1", "catalog_2"), state.filteredFoods.map { it.key })
        assertEquals("NEVO", state.filteredFoods.last().sourceLabel)
    }
}
