package com.zack.recomptracker.ui

import com.zack.recomptracker.data.local.entity.CatalogFoodEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.ui.foodlibrary.AmountMode
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

    private val whey = SavedFoodEntity(
        id = 5,
        name = "Whey",
        servingName = "100g",
        calories = 120,
        proteinG = 24.0,
        carbsG = 3.0,
        fatG = 2.0,
        householdServingName = "scoop",
        householdServingGrams = 30.0,
    )

    @Test
    fun servingsModeComputesGramsAndPreviewFromHouseholdServing() {
        val state = FoodLibraryUiState(
            pendingFood = whey,
            amountMode = AmountMode.SERVINGS,
            servingsValue = "2",
        )
        assertEquals(60.0, state.resolvedGrams!!, 0.001)
        assertEquals(72, state.previewMacros!!.calories)
    }

    @Test
    fun gramsModeComputesPreviewDirectly() {
        val state = FoodLibraryUiState(
            pendingFood = whey,
            amountMode = AmountMode.GRAMS,
            gramsValue = "150",
        )
        assertEquals(150.0, state.resolvedGrams!!, 0.001)
        assertEquals(180, state.previewMacros!!.calories)
    }

    @Test
    fun foodWithoutHouseholdServingIsGramsOnly() {
        val plain = whey.copy(householdServingName = null, householdServingGrams = null)
        val state = FoodLibraryUiState(pendingFood = plain)
        assertEquals(false, state.canUseServings)
    }

    @Test
    fun invalidAmountYieldsNullPreview() {
        val state = FoodLibraryUiState(
            pendingFood = whey,
            amountMode = AmountMode.GRAMS,
            gramsValue = "abc",
        )
        assertEquals(null, state.resolvedGrams)
        assertEquals(null, state.previewMacros)
    }

    @Test
    fun gramsBelowMinimumYieldsNullPreview() {
        val state = FoodLibraryUiState(
            pendingFood = whey,
            amountMode = AmountMode.GRAMS,
            gramsValue = "0.9",
        )
        assertEquals(null, state.resolvedGrams)
        assertEquals(null, state.previewMacros)
    }

    @Test
    fun foodWithHouseholdServingCanUseServings() {
        val state = FoodLibraryUiState(pendingFood = whey)
        assertEquals(true, state.canUseServings)
    }
}
