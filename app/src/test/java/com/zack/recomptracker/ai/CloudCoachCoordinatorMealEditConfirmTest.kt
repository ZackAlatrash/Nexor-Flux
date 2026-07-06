package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCoachCoordinatorMealEditConfirmTest {
    @Test
    fun `delete_meal summary names the meal`() {
        val s = mealEditActionSummary("delete_meal", mapOf("name" to "2 slices pizza"))
        assertTrue(s.contains("2 slices pizza"))
        assertTrue(s.lowercase().contains("delete") || s.lowercase().contains("remove"))
    }

    @Test
    fun `edit_meal summary names the meal and the new amount`() {
        val s = mealEditActionSummary("edit_meal", mapOf("name" to "chicken breast", "grams" to "200"))
        assertTrue(s.contains("chicken breast"))
        assertTrue(s.contains("200"))
    }
}
