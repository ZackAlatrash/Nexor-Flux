package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeNamePromptBuilderTest {

    private val builder = RecipeNamePromptBuilder()

    private fun ing(name: String) = RecipeIngredientEntity(
        name = name, calories = 100, proteinG = 5.0, carbsG = 10.0, fatG = 2.0,
    )

    @Test
    fun `user prompt names every ingredient and the macro totals`() {
        val prompt = builder.buildUserPrompt(
            listOf(ing("Chicken breast"), ing("Fries")),
            MacroTotals(calories = 680, proteinG = 57.0, carbsG = 40.0, fatG = 14.0),
        )
        assertTrue(prompt.contains("Chicken breast"))
        assertTrue(prompt.contains("Fries"))
        assertTrue(prompt.contains("680"))
        assertTrue(prompt.contains("57"))
    }

    @Test
    fun `sanitize strips quotes`() {
        assertEquals("Anabolic Oats", RecipeNamePromptBuilder.sanitize("\"Anabolic Oats\""))
    }

    @Test
    fun `sanitize takes first non-empty line`() {
        assertEquals("Quad Slayer", RecipeNamePromptBuilder.sanitize("\n  Quad Slayer\nsome rambling explanation"))
    }

    @Test
    fun `sanitize strips markdown and label prefix`() {
        assertEquals("Gains Goblin Stew", RecipeNamePromptBuilder.sanitize("**Gains Goblin Stew**"))
        assertEquals("Bulk Bowl", RecipeNamePromptBuilder.sanitize("Recipe name: Bulk Bowl"))
    }

    @Test
    fun `sanitize caps length`() {
        val long = "A".repeat(100)
        assertEquals(RecipeNamePromptBuilder.MAX_NAME_LENGTH, RecipeNamePromptBuilder.sanitize(long).length)
    }

    @Test
    fun `sanitize returns blank for empty input`() {
        assertEquals("", RecipeNamePromptBuilder.sanitize("   \n  "))
    }
}
