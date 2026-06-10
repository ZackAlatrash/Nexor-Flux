package com.zack.recomptracker.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightPromptBuilderRichModeTest {

    private val builder = InsightPromptBuilder()

    private fun recoveryContext() = RecoveryInsightContext(
        sleepHours = 6.0,
        energyScore = 4,
        hungerScore = 5,
        sorenessScore = 7,
        trained = true,
    )

    @Test
    fun `default recovery prompt keeps the concise sentence cap`() {
        val prompt = builder.buildRecoveryReadinessPrompt(recoveryContext(), rich = false)
        assertTrue(prompt.contains("exactly 2–3 sentences"))
    }

    @Test
    fun `rich recovery prompt removes the concise cap and asks for depth`() {
        val prompt = builder.buildRecoveryReadinessPrompt(recoveryContext(), rich = true)
        assertFalse(prompt.contains("exactly 2–3 sentences"))
        assertTrue(prompt.contains("cross-signal"))
    }
}
