package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SignedFormatTest {

    @Test
    fun `positive value gets an explicit plus sign`() {
        assertEquals("+0.05", InsightPromptBuilder.signed(0.05, 2))
    }

    @Test
    fun `negative value keeps its minus sign`() {
        assertEquals("-0.35", InsightPromptBuilder.signed(-0.35, 2))
    }

    @Test
    fun `one decimal place rounds and signs`() {
        assertEquals("+0.4", InsightPromptBuilder.signed(0.4, 1))
    }

    @Test
    fun `near-zero renders as unsigned zero`() {
        assertEquals("0.0", InsightPromptBuilder.signed(0.02, 1))
    }

    @Test
    fun `negative-near-zero is normalized to plain zero (no minus)`() {
        assertEquals("0.00", InsightPromptBuilder.signed(-0.001, 2))
    }
}
