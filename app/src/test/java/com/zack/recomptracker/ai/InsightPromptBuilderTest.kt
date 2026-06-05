package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightPromptBuilderTest {

    private val builder = InsightPromptBuilder()

    private fun result(
        verdict: AdjustmentVerdict = AdjustmentVerdict.HOLD,
        codes: List<String> = listOf("MAINTENANCE_TREND"),
        change: Int = 0,
    ) = AdjustmentResult(
        verdict = verdict,
        recommendedCalorieChange = change,
        reasonCodes = codes,
        summary = "Stable.",
    )

    @Test
    fun `prompt contains verdict name`() {
        val prompt = builder.buildWeeklySummaryPrompt(result(verdict = AdjustmentVerdict.INCREASE_CALORIES))
        assertTrue("Expected INCREASE_CALORIES in prompt", "INCREASE_CALORIES" in prompt)
    }

    @Test
    fun `prompt contains reason codes`() {
        val prompt = builder.buildWeeklySummaryPrompt(result(codes = listOf("LOSING_WITH_POOR_RECOVERY")))
        assertTrue("Expected reason code in prompt", "LOSING_WITH_POOR_RECOVERY" in prompt)
    }

    @Test
    fun `prompt instructs not to change verdict`() {
        val prompt = builder.buildWeeklySummaryPrompt(result())
        assertTrue("Expected instruction to preserve verdict", prompt.contains("do not change", ignoreCase = true))
    }

    @Test
    fun `prompt requests short output`() {
        val prompt = builder.buildWeeklySummaryPrompt(result())
        assertTrue("Expected short-output instruction", prompt.contains("2") || prompt.contains("3"))
    }

    @Test
    fun `prompt does not include raw calorie numbers from result`() {
        val prompt = builder.buildWeeklySummaryPrompt(result(change = 150))
        assertFalse("Prompt should not include raw change value", "150" in prompt)
    }
}
