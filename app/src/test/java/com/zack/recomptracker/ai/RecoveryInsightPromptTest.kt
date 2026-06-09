package com.zack.recomptracker.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryInsightPromptTest {

    private val builder = InsightPromptBuilder()

    private fun ctx(
        sleepHours: Double? = 5.5,
        energyScore: Int? = 3,
        hungerScore: Int? = 5,
        sorenessScore: Int? = 8,
        trained: Boolean = true,
    ) = RecoveryInsightContext(sleepHours, energyScore, hungerScore, sorenessScore, trained)

    @Test
    fun `sleep below six is poor and not numeric`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sleepHours = 5.5))
        assertTrue("Sleep: poor" in prompt)
        assertFalse("5.5" in prompt)
    }

    @Test
    fun `energy three is low`() {
        assertTrue("Energy: low" in builder.buildRecoveryReadinessPrompt(ctx(energyScore = 3)))
    }

    @Test
    fun `soreness eight is high and not numeric`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sorenessScore = 8))
        assertTrue("Soreness: high" in prompt)
        assertFalse("Soreness: 8" in prompt)
    }

    @Test
    fun `hunger five is moderate`() {
        assertTrue("Hunger: moderate" in builder.buildRecoveryReadinessPrompt(ctx(hungerScore = 5)))
    }

    @Test
    fun `omits a signal that was not logged`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sleepHours = null))
        assertFalse("Sleep:" in prompt)
    }

    @Test
    fun `shows trained status`() {
        assertTrue("Trained today: yes" in builder.buildRecoveryReadinessPrompt(ctx(trained = true)))
    }

    @Test
    fun `instructs no medical advice`() {
        assertTrue(builder.buildRecoveryReadinessPrompt(ctx()).contains("medical", ignoreCase = true))
    }

    @Test
    fun `requests short output and few-shot`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx())
        assertTrue("2" in prompt || "3" in prompt)
        assertTrue("Example output" in prompt)
    }
}
