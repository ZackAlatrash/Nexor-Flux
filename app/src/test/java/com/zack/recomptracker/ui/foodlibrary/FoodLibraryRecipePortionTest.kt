package com.zack.recomptracker.ui.foodlibrary

import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.domain.food.RecipeWithIngredients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodLibraryRecipePortionTest {

    // totals: 300 kcal, 20 P, 30 C, 10 F
    private fun recipe() = RecipeWithIngredients(
        recipe = RecipeEntity(name = "Test"),
        ingredients = listOf(
            RecipeIngredientEntity(name = "A", calories = 200, proteinG = 10.0, carbsG = 20.0, fatG = 5.0),
            RecipeIngredientEntity(name = "B", calories = 100, proteinG = 10.0, carbsG = 10.0, fatG = 5.0),
        ),
    )

    @Test fun wholeRecipeFactorIsOneAndPreviewEqualsTotals() {
        val s = FoodLibraryUiState(pendingRecipe = recipe(), recipePortions = "1")
        assertEquals(1.0, s.recipePortionFactor!!, 0.0001)
        val m = s.recipePreviewMacros!!
        assertEquals(300, m.calories)
        assertEquals(20.0, m.proteinG, 0.0001)
        assertEquals(30.0, m.carbsG, 0.0001)
        assertEquals(10.0, m.fatG, 0.0001)
    }

    @Test fun doublePortionDoublesMacros() {
        val m = FoodLibraryUiState(pendingRecipe = recipe(), recipePortions = "2").recipePreviewMacros!!
        assertEquals(600, m.calories)
        assertEquals(40.0, m.proteinG, 0.0001)
    }

    @Test fun halfPortionHalvesMacros() {
        val m = FoodLibraryUiState(pendingRecipe = recipe(), recipePortions = "0.5").recipePreviewMacros!!
        assertEquals(150, m.calories)
        assertEquals(10.0, m.proteinG, 0.0001)
    }

    @Test fun invalidOrTooSmallPortionIsNull() {
        assertNull(FoodLibraryUiState(pendingRecipe = recipe(), recipePortions = "abc").recipePortionFactor)
        assertNull(FoodLibraryUiState(pendingRecipe = recipe(), recipePortions = "0").recipePortionFactor)
        assertNull(FoodLibraryUiState(pendingRecipe = recipe(), recipePortions = "0").recipePreviewMacros)
    }

    @Test fun noPendingRecipeHasNullPreview() {
        assertNull(FoodLibraryUiState(recipePortions = "1").recipePreviewMacros)
    }
}
