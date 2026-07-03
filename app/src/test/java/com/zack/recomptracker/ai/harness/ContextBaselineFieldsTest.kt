package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.ai.ProgressInsightContext
import com.zack.recomptracker.ai.RecoveryInsightContext
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextBaselineFieldsTest {

    @Test
    fun `progress context carries prior-window trends`() {
        val c = ProgressInsightContext(28, -0.3, 0.0, 0.4, 90.0, 6, 6, priorWeightTrendKgPerWeek = -0.1)
        assertEquals(-0.1, c.priorWeightTrendKgPerWeek!!, 0.0001)
    }

    @Test
    fun `recovery context carries personal averages`() {
        val c = RecoveryInsightContext(5.0, 3, 7, 8, true, avgSleepHours = 7.5, avgEnergyScore = 6)
        assertEquals(7.5, c.avgSleepHours!!, 0.0001)
        assertEquals(6, c.avgEnergyScore)
    }
}
