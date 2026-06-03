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
            .withComputedFields()

        assertEquals(listOf("personal_1"), state.filteredFoods.map { it.key })
    }

    @Test
    fun typedQueryIncludesMatchingNevoFoodsAfterPersonalFoods() {
        val state = FoodLibraryUiState(
            query = "yog",
            allFoods = listOf(personal),
            allCatalogFoods = listOf(nevo),
        ).withComputedFields()

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
    fun servingsModeWithoutHouseholdServingFallsBackTo100gPerServing() {
        val plain = whey.copy(householdServingName = null, householdServingGrams = null)
        val state = FoodLibraryUiState(
            pendingFood = plain,
            amountMode = AmountMode.SERVINGS,
            servingsValue = "2",
        )
        assertEquals(200.0, state.resolvedGrams!!, 0.001)
        assertEquals(240, state.previewMacros!!.calories) // whey has 120 kcal/100 g; 200 g → 240 kcal
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
    fun fractionalServingsResolveCorrectly() {
        val state = FoodLibraryUiState(
            pendingFood = whey,
            amountMode = AmountMode.SERVINGS,
            servingsValue = "0.5",
        )
        assertEquals(15.0, state.resolvedGrams!!, 0.001) // 0.5 × 30 g/scoop
        assertEquals(18, state.previewMacros!!.calories)  // 15 g × 120 kcal/100 g = 18 kcal
    }

    @Test
    fun servingsBelowMinimumYieldsNullPreview() {
        val state = FoodLibraryUiState(
            pendingFood = whey,
            amountMode = AmountMode.SERVINGS,
            servingsValue = "0.09",
        )
        assertEquals(null, state.resolvedGrams)
        assertEquals(null, state.previewMacros)
    }

    @Test
    fun foodWithHouseholdServingCanUseServings() {
        val state = FoodLibraryUiState(pendingFood = whey)
        assertEquals(true, state.canUseServings)
    }

    @Test
    fun recentFoodsExposeBaseSnapshotAsLoggableFood() {
        val recentEntry = com.zack.recomptracker.data.local.entity.MealEntryEntity(
            id = 9,
            date = "2026-05-30",
            mealType = "FOOD_LIBRARY",
            name = "Whey",
            calories = 72,
            proteinG = 14.0,
            carbsG = 2.0,
            fatG = 1.0,
            amountGrams = 60.0,
            basePer100Calories = 120,
            basePer100ProteinG = 24.0,
            basePer100CarbsG = 3.0,
            basePer100FatG = 2.0,
            entryServingName = "scoop",
            entryServingGrams = 30.0,
        )
        val state = FoodLibraryUiState(recentEntries = listOf(recentEntry)).withComputedFields()
        val recent = state.recentFoods.single()
        assertEquals("Whey", recent.name)
        assertEquals(120, recent.calories)
        assertEquals("scoop", recent.householdServingName)
        assertEquals(30.0, recent.householdServingGrams!!, 0.001)
    }
}
