package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeSelectionTest {

    private fun entry(id: Long, name: String) = MealEntryEntity(
        id = id, date = "2026-06-13", mealType = "lunch", name = name,
        calories = 100, proteinG = 5.0, carbsG = 10.0, fatG = 2.0, slotId = 1,
    )

    private fun slot(vararg entries: MealEntryEntity) = MealSlotWithEntries(
        slot = MealSlotEntity(id = 1, name = "Lunch", sortOrder = 0),
        entries = entries.toList(),
        totals = MacroTotals(),
    )

    @Test
    fun `returns only selected entries, ordered, mapped with sortOrder`() {
        val slots = listOf(slot(entry(10, "Chicken"), entry(11, "Ketchup"), entry(12, "Fries")))
        val selection = RecipeSelection(slotId = 1, selectedIds = setOf(10L, 12L))

        val result = recipeIngredientsFromSelection(slots, selection)

        assertEquals(listOf("Chicken", "Fries"), result.map { it.name })
        assertEquals(listOf(0, 1), result.map { it.sortOrder })
    }

    @Test
    fun `null selection returns empty`() {
        val slots = listOf(slot(entry(10, "Chicken")))
        assertEquals(emptyList<Any>(), recipeIngredientsFromSelection(slots, null))
    }

    @Test
    fun `empty selectedIds returns empty`() {
        val slots = listOf(slot(entry(10, "Chicken")))
        assertEquals(0, recipeIngredientsFromSelection(slots, RecipeSelection(1, emptySet())).size)
    }
}
