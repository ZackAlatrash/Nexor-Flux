package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.export.BackupPayload
import com.zack.recomptracker.domain.export.RecipeBackup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRecipeSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun payloadWith(recipes: List<RecipeBackup>) = BackupPayload(
        exportedAt = "t",
        preferences = PlanPreferences(),
        dailyLogs = emptyList(),
        mealEntries = emptyList(),
        savedFoods = emptyList(),
        savedMeals = emptyList(),
        liftPerformances = emptyList(),
        weeklyReviews = emptyList(),
        mealSlots = emptyList(),
        recipes = recipes,
    )

    @Test
    fun `recipes and their ingredients round-trip through the backup payload`() {
        val payload = payloadWith(
            listOf(
                RecipeBackup(
                    recipe = RecipeEntity(id = 5, name = "Chili"),
                    ingredients = listOf(
                        RecipeIngredientEntity(id = 1, recipeId = 5, name = "Beans", sortOrder = 0, calories = 120, proteinG = 8.0, carbsG = 20.0, fatG = 1.0),
                        RecipeIngredientEntity(id = 2, recipeId = 5, name = "Beef", sortOrder = 1, calories = 250, proteinG = 26.0, carbsG = 0.0, fatG = 15.0),
                    ),
                ),
            ),
        )
        val decoded = json.decodeFromString(
            BackupPayload.serializer(),
            json.encodeToString(BackupPayload.serializer(), payload),
        )
        assertEquals(1, decoded.recipes.size)
        assertEquals("Chili", decoded.recipes[0].recipe.name)
        assertEquals(2, decoded.recipes[0].ingredients.size)
        assertEquals("Beef", decoded.recipes[0].ingredients[1].name)
        assertEquals(250, decoded.recipes[0].ingredients[1].calories)
    }

    @Test
    fun `old backup without a recipes field decodes to an empty list`() {
        val old = """{"exportedAt":"t","preferences":{},"dailyLogs":[],"mealEntries":[],
            "savedFoods":[],"savedMeals":[],"liftPerformances":[],"weeklyReviews":[]}"""
        val decoded = json.decodeFromString(BackupPayload.serializer(), old)
        assertTrue(decoded.recipes.isEmpty())
    }
}
