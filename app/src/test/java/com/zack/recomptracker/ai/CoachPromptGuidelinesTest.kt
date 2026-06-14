package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class CoachPromptGuidelinesTest {

    @Test
    fun `guidelines force food lookups and forbid estimating`() {
        assertTrue(COACH_PROMPT_GUIDELINES.contains("search_food_library"))
        assertTrue(COACH_PROMPT_GUIDELINES.lowercase().contains("never estimate"))
    }

    @Test
    fun `guidelines allow reference knowledge as a source`() {
        assertTrue(COACH_PROMPT_GUIDELINES.contains("REFERENCE KNOWLEDGE"))
    }
}
