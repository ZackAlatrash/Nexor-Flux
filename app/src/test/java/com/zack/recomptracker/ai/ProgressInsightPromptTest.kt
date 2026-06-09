package com.zack.recomptracker.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressInsightPromptTest {

    private val builder = InsightPromptBuilder()

    private fun ctx(
        rangeDays: Int = 28,
        weightTrendKgPerWeek: Double? = 0.05,
        waistTrendCmPerWeek: Double? = -0.30,
        liftTrendKgPerWeek: Double? = 0.5,
        adherencePercent: Double? = 91.0,
    ) = ProgressInsightContext(
        rangeDays, weightTrendKgPerWeek, waistTrendCmPerWeek,
        liftTrendKgPerWeek, adherencePercent, weightPointCount = 10, waistPointCount = 10,
    )

    @Test
    fun `includes the range window`() {
        assertTrue("28" in builder.buildProgressTrendPrompt(ctx(rangeDays = 28)))
    }

    @Test
    fun `weight trend is qualitative not numeric`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(weightTrendKgPerWeek = 0.05))
        assertTrue("Weight: stable" in prompt)
        assertFalse("0.05" in prompt)
    }

    @Test
    fun `waist trending down is qualitative`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(waistTrendCmPerWeek = -0.30))
        assertTrue("Waist: trending down" in prompt)
        assertFalse("-0.30" in prompt)
    }

    @Test
    fun `lifts improving is qualitative`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(liftTrendKgPerWeek = 0.5))
        assertTrue("Lifts: improving" in prompt)
    }

    @Test
    fun `null lift trend renders no data`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(liftTrendKgPerWeek = null))
        assertTrue("Lifts: no data" in prompt)
    }

    @Test
    fun `adherence is qualitative`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(adherencePercent = 91.0))
        assertTrue("Adherence: high" in prompt)
        assertFalse("91" in prompt)
    }

    @Test
    fun `instructs not to recommend calorie changes`() {
        val prompt = builder.buildProgressTrendPrompt(ctx())
        assertTrue(prompt.contains("not", ignoreCase = true) && prompt.contains("calorie", ignoreCase = true))
    }

    @Test
    fun `requests short output`() {
        val prompt = builder.buildProgressTrendPrompt(ctx())
        assertTrue("2" in prompt || "3" in prompt)
    }

    @Test
    fun `contains few-shot example`() {
        assertTrue("Example output" in builder.buildProgressTrendPrompt(ctx()))
    }

    @Test
    fun `instructs to avoid inventing data`() {
        assertTrue(builder.buildProgressTrendPrompt(ctx()).contains("invent", ignoreCase = true))
    }

    @Test
    fun `lifts declining is qualitative`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(liftTrendKgPerWeek = -0.5))
        assertTrue("Lifts: declining" in prompt)
    }

    @Test
    fun `null weight trend renders no data`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(weightTrendKgPerWeek = null))
        assertTrue("Weight: no data" in prompt)
    }

    @Test
    fun `null adherence renders no data`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(adherencePercent = null))
        assertTrue("Adherence: no data" in prompt)
    }
}
