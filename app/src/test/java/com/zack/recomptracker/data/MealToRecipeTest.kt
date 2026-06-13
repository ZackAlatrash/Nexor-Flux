package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.repository.toRecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Test

class MealToRecipeTest {

    private fun meal() = MealEntryEntity(
        id = 7,
        date = "2026-06-13",
        mealType = "lunch",
        name = "Chicken breast",
        calories = 280,
        proteinG = 52.0,
        carbsG = 0.0,
        fatG = 6.0,
        slotId = 3,
        amountGrams = 200.0,
        basePer100Calories = 140,
        basePer100ProteinG = 26.0,
        basePer100CarbsG = 0.0,
        basePer100FatG = 3.0,
        entryServingName = "fillet",
        entryServingGrams = 120.0,
        loggedByServings = true,
        planned = false,
    )

    @Test
    fun `maps every macro and base field, applies sortOrder, resets ids`() {
        val ing = meal().toRecipeIngredient(sortOrder = 4)

        assertEquals(0L, ing.id)
        assertEquals(0L, ing.recipeId)
        assertEquals(4, ing.sortOrder)
        assertEquals("Chicken breast", ing.name)
        assertEquals(280, ing.calories)
        assertEquals(52.0, ing.proteinG, 0.0)
        assertEquals(0.0, ing.carbsG, 0.0)
        assertEquals(6.0, ing.fatG, 0.0)
        assertEquals(200.0, ing.amountGrams!!, 0.0)
        assertEquals(140, ing.basePer100Calories)
        assertEquals(26.0, ing.basePer100ProteinG)
        assertEquals(0.0, ing.basePer100CarbsG)
        assertEquals(3.0, ing.basePer100FatG)
        assertEquals("fillet", ing.entryServingName)
        assertEquals(120.0, ing.entryServingGrams!!, 0.0)
        assertEquals(true, ing.loggedByServings)
    }
}
