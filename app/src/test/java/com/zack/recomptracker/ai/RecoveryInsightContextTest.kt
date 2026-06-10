package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryInsightContextTest {

    private fun ctx(
        sleepHours: Double? = 7.0,
        energyScore: Int? = 6,
        hungerScore: Int? = 4,
        sorenessScore: Int? = 5,
        trained: Boolean = true,
    ) = RecoveryInsightContext(sleepHours, energyScore, hungerScore, sorenessScore, trained)

    @Test
    fun `sufficient when only sleep logged`() {
        assertTrue(ctx(sleepHours = 6.0, energyScore = null, hungerScore = null, sorenessScore = null).hasSufficientData)
    }

    @Test
    fun `sufficient when only a score logged`() {
        assertTrue(ctx(sleepHours = null, energyScore = 5, hungerScore = null, sorenessScore = null).hasSufficientData)
    }

    @Test
    fun `insufficient when nothing logged`() {
        assertFalse(ctx(sleepHours = null, energyScore = null, hungerScore = null, sorenessScore = null).hasSufficientData)
    }

    @Test
    fun `key changes when soreness changes`() {
        assertTrue(ctx(sorenessScore = 3).key() != ctx(sorenessScore = 8).key())
    }

    @Test
    fun `key stable for identical inputs`() {
        assertEquals(ctx().key(), ctx().key())
    }
}
