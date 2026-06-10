package com.zack.recomptracker.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressInsightMapperTest {

    @Test
    fun `maps counts and range`() {
        val ctx = buildProgressInsightContext(
            rangeDays = 28,
            weightValues = listOf(80f, 79.5f, 79f),
            waistValues = listOf(85f, 84f),
            liftValues = listOf(100f, 102f),
            adherencePercent = 90f,
        )
        assertEquals(28, ctx.rangeDays)
        assertEquals(3, ctx.weightPointCount)
        assertEquals(2, ctx.waistPointCount)
        assertEquals(90.0, ctx.adherencePercent!!, 0.001)
        assertTrue(ctx.hasSufficientData)
    }

    @Test
    fun `single point yields null trend`() {
        val ctx = buildProgressInsightContext(
            rangeDays = 7,
            weightValues = listOf(80f),
            waistValues = emptyList(),
            liftValues = emptyList(),
            adherencePercent = null,
        )
        assertNull(ctx.weightTrendKgPerWeek)
        assertNull(ctx.adherencePercent)
        assertFalse(ctx.hasSufficientData)
    }

    @Test
    fun `computes a weekly trend from two weeks of points`() {
        // 8 points span 7 days = 1.0 week; (last - first) / 1.0 week
        val ctx = buildProgressInsightContext(
            rangeDays = 7,
            weightValues = listOf(80f, 80f, 80f, 80f, 80f, 80f, 80f, 79f),
            waistValues = emptyList(),
            liftValues = emptyList(),
            adherencePercent = null,
        )
        assertEquals(-1.0, ctx.weightTrendKgPerWeek!!, 0.001)
    }
}
