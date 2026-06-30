package com.zack.recomptracker.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityNeatPromptTest {

    private val builder = InsightPromptBuilder()

    @Test
    fun `prompt includes steps, goal, and 7-day average`() {
        val prompt = builder.buildActivityNeatPrompt(
            ActivityInsightContext(steps = 6200, stepGoal = 10000, averageDailySteps7 = 8100),
        )
        assertTrue("6200" in prompt)
        assertTrue("10000" in prompt)
        assertTrue("8100" in prompt)
    }

    @Test
    fun `prompt omits the average line when not available`() {
        val prompt = builder.buildActivityNeatPrompt(
            ActivityInsightContext(steps = 6200, stepGoal = 10000, averageDailySteps7 = null),
        )
        assertFalse("7-day average" in prompt)
    }

    @Test
    fun `prompt requests a short, non-echoed coaching reply`() {
        val prompt = builder.buildActivityNeatPrompt(
            ActivityInsightContext(steps = 6200, stepGoal = 10000, averageDailySteps7 = 8100),
        )
        assertTrue("1–2" in prompt || "1-2" in prompt || "1 or 2" in prompt)
        assertTrue("Example output" in prompt)
        assertTrue(prompt.contains("only", ignoreCase = true))
    }
}
