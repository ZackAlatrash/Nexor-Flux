package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.insight.InsightFact
import com.zack.recomptracker.domain.insight.InsightFactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternInsightPromptTest {

    private val builder = InsightPromptBuilder()
    private val fact = InsightFact(
        InsightFactType.WEEKDAY_WEEKEND,
        priority = 27,
        statement = "Your weekend days average 2800 kcal vs 2200 on weekdays (+600 kcal).",
    )
    private val context = PatternInsightContext(fact)

    @Test
    fun `prompt includes the computed finding verbatim`() {
        val prompt = builder.buildPatternInsightPrompt(context)
        assertTrue(fact.statement in prompt)
    }

    @Test
    fun `prompt instructs to lead with the number and not invent`() {
        val prompt = builder.buildPatternInsightPrompt(context)
        assertTrue("Lead with the specific number" in prompt)
        assertTrue(prompt.contains("do not invent", ignoreCase = true))
    }

    @Test
    fun `prompt requests short supportive output`() {
        val prompt = builder.buildPatternInsightPrompt(context)
        assertTrue("2" in prompt)
        assertTrue(prompt.contains("supportive", ignoreCase = true) || prompt.contains("non-judgmental", ignoreCase = true))
    }

    @Test
    fun `context key is the fact statement and data is sufficient`() {
        assertEquals(fact.statement, context.key())
        assertTrue(context.hasSufficientData)
    }
}
