package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity

/**
 * Builds the prompt that asks the model for a short, funny "gym-bro" recipe name, and
 * sanitizes the model's reply down to a single clean name. [sanitize] is pure so it can
 * be unit-tested and reused by both the local and cloud generators.
 */
class RecipeNamePromptBuilder {

    fun buildUserPrompt(ingredients: List<RecipeIngredientEntity>, totals: MacroTotals): String {
        val items = ingredients.joinToString(", ") { it.name }
        return buildString {
            append("Invent ONE over-the-top gym-bro name for a meal made of: ")
            append(items).append(".\n")
            append("Macros: ${totals.calories} kcal, ${totals.proteinG.toInt()}g protein, ")
            append("${totals.carbsG.toInt()}g carbs, ${totals.fatG.toInt()}g fat.\n")
            append("Lean into the macros (high protein = \"Anabolic\", high calories = \"Bulk\", etc.). ")
            append("2 to 4 words. Examples: Anabolic Oats, Quad Slayer Bowl, Gains Goblin Stew.\n")
            append("Name:")
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 42

        const val SYSTEM_PROMPT =
            "You are a gym-bro recipe namer. You invent short, funny, slightly unhinged names " +
                "for meals, like a meathead fitness influencer. Reply with ONLY the name — " +
                "no quotes, no explanation, no trailing punctuation."

        private val MARKDOWN = Regex("[*_`#>\"]")
        private val LABEL_PREFIX = Regex("^(recipe\\s+)?name\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE)

        /** Reduce a raw model reply to a single clean recipe name. */
        fun sanitize(raw: String): String {
            val firstLine = raw.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }.orEmpty()
            return firstLine
                .replace(MARKDOWN, "")
                .replace(LABEL_PREFIX, "")
                .trim()
                .trim('"', '\'', '.', ':', '-', ' ')
                .take(MAX_NAME_LENGTH)
                .trim()
        }
    }
}
