package com.zack.recomptracker.domain.food

import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeWithIngredientsTest {

    private fun ingredient(calories: Int, proteinG: Double, carbsG: Double, fatG: Double) =
        RecipeIngredientEntity(
            recipeId = 1, name = "x", sortOrder = 0,
            calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
        )

    @Test
    fun `totals sum all ingredients`() {
        val recipe = RecipeWithIngredients(
            recipe = RecipeEntity(id = 1, name = "Test"),
            ingredients = listOf(
                ingredient(200, 10.0, 30.0, 5.0),
                ingredient(150, 8.0, 20.0, 3.0),
            ),
        )
        assertEquals(350, recipe.totalCalories)
        assertEquals(18.0, recipe.totalProteinG, 0.001)
        assertEquals(50.0, recipe.totalCarbsG, 0.001)
        assertEquals(8.0, recipe.totalFatG, 0.001)
    }

    @Test
    fun `empty recipe returns zero totals`() {
        val recipe = RecipeWithIngredients(RecipeEntity(id = 1, name = "Empty"), emptyList())
        assertEquals(0, recipe.totalCalories)
        assertEquals(0.0, recipe.totalProteinG, 0.001)
        assertEquals(0.0, recipe.totalCarbsG, 0.001)
        assertEquals(0.0, recipe.totalFatG, 0.001)
    }
}
