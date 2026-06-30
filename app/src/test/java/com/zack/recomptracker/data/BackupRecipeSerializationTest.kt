package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.entity.PlanVersionEntity
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

    @Test
    fun `plan versions round-trip through the backup payload`() {
        val payload = payloadWith(emptyList()).copy(
            planVersions = listOf(
                PlanVersionEntity(
                    effectiveFrom = "2026-01-01",
                    targetCalories = 2200,
                    targetProteinG = 180,
                    targetCarbsG = 200,
                    targetFatG = 70,
                    calorieZoneLowerBound = 2100,
                    calorieZoneUpperBound = 2300,
                    createdAt = "2026-01-01T08:00:00Z",
                ),
                PlanVersionEntity(
                    effectiveFrom = "2026-03-15",
                    targetCalories = 2000,
                    targetProteinG = 175,
                    targetCarbsG = 170,
                    targetFatG = 65,
                    calorieZoneLowerBound = 1900,
                    calorieZoneUpperBound = 2100,
                    createdAt = "2026-03-15T09:30:00Z",
                ),
            ),
        )
        val decoded = json.decodeFromString(
            BackupPayload.serializer(),
            json.encodeToString(BackupPayload.serializer(), payload),
        )
        assertEquals(2, decoded.planVersions.size)
        assertEquals("2026-01-01", decoded.planVersions[0].effectiveFrom)
        assertEquals(2200, decoded.planVersions[0].targetCalories)
        assertEquals("2026-03-15", decoded.planVersions[1].effectiveFrom)
        assertEquals(2100, decoded.planVersions[1].calorieZoneUpperBound)
        assertEquals("2026-03-15T09:30:00Z", decoded.planVersions[1].createdAt)
    }

    @Test
    fun `old backup without a planVersions field decodes to an empty list`() {
        val old = """{"exportedAt":"t","preferences":{},"dailyLogs":[],"mealEntries":[],
            "savedFoods":[],"savedMeals":[],"liftPerformances":[],"weeklyReviews":[]}"""
        val decoded = json.decodeFromString(BackupPayload.serializer(), old)
        assertTrue(decoded.planVersions.isEmpty())
    }
}
