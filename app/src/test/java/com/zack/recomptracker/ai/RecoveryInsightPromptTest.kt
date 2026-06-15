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
    fun `sleep shows hours and poor label`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sleepHours = 5.5))
        assertTrue("Sleep: 5.5 h (poor)" in prompt)
    }

    @Test
    fun `energy shows score out of ten and low label`() {
        assertTrue("Energy: 3/10 (low)" in builder.buildRecoveryReadinessPrompt(ctx(energyScore = 3)))
    }

    @Test
    fun `soreness shows score out of ten and high label`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sorenessScore = 8))
        assertTrue("Soreness: 8/10 (high)" in prompt)
    }

    @Test
    fun `hunger shows score out of ten and moderate label`() {
        assertTrue("Hunger: 5/10 (moderate)" in builder.buildRecoveryReadinessPrompt(ctx(hungerScore = 5)))
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

    @Test
    fun `sleep between six and seven point five is acceptable`() {
        assertTrue("Sleep: 7.0 h (acceptable)" in builder.buildRecoveryReadinessPrompt(ctx(sleepHours = 7.0)))
    }

    @Test
    fun `sleep at or above seven point five is good`() {
        assertTrue("Sleep: 8.0 h (good)" in builder.buildRecoveryReadinessPrompt(ctx(sleepHours = 8.0)))
    }

    @Test
    fun `energy seven is high`() {
        assertTrue("Energy: 7/10 (high)" in builder.buildRecoveryReadinessPrompt(ctx(energyScore = 7)))
    }

    @Test
    fun `shows not trained status`() {
        assertTrue("Trained today: no" in builder.buildRecoveryReadinessPrompt(ctx(trained = false)))
    }

    @Test
    fun `instructs to lead with a number`() {
        assertTrue("Lead with the most decisive number" in builder.buildRecoveryReadinessPrompt(ctx()))
    }

    @Test
    fun `forbids the model doing its own math`() {
        assertTrue("do not do any math" in builder.buildRecoveryReadinessPrompt(ctx()))
    }
}
