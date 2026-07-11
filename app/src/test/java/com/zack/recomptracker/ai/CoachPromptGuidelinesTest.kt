package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class CoachPromptGuidelinesTest {

    @Test
    fun `guidelines force food lookups and forbid estimating`() {
        assertTrue(COACH_PROMPT_GUIDELINES.contains("MUST call search_food_library"))
        assertTrue(COACH_PROMPT_GUIDELINES.lowercase().contains("never estimate"))
    }

    @Test
    fun `guidelines allow reference knowledge as a source`() {
        assertTrue(COACH_PROMPT_GUIDELINES.contains("REFERENCE KNOWLEDGE"))
    }

    @Test
    fun `guidelines tell the coach to search the web and cite when knowledge is missing`() {
        assertTrue(COACH_PROMPT_GUIDELINES.contains("search_web"))
        assertTrue(COACH_PROMPT_GUIDELINES.lowercase().contains("cite"))
    }

    @Test
    fun `guidelines tell the coach to re-read today's summary after a write (P2-3)`() {
        // A write makes the conversation-start snapshot stale; the model must re-fetch before
        // quoting today's totals/remaining macros.
        assertTrue(COACH_PROMPT_GUIDELINES.contains("get_today_summary"))
        assertTrue(COACH_PROMPT_GUIDELINES.lowercase().contains("out of date"))
    }
}
