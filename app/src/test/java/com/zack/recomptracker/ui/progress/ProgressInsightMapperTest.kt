package com.zack.recomptracker.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressInsightMapperTest {

    @Test
    fun `passes through pre-computed trends, counts and range`() {
        val ctx = buildProgressInsightContext(
            rangeDays = 28,
            weightTrendKgPerWeek = -0.25,
            waistTrendCmPerWeek = -0.10,
            liftTrendKgPerWeek = 1.5,
            weightPointCount = 3,
            waistPointCount = 2,
            adherencePercent = 90f,
        )
        assertEquals(28, ctx.rangeDays)
        assertEquals(-0.25, ctx.weightTrendKgPerWeek!!, 1e-9)
        assertEquals(-0.10, ctx.waistTrendCmPerWeek!!, 1e-9)
        assertEquals(1.5, ctx.liftTrendKgPerWeek!!, 1e-9)
        assertEquals(3, ctx.weightPointCount)
        assertEquals(2, ctx.waistPointCount)
        assertEquals(90.0, ctx.adherencePercent!!, 0.001)
        assertTrue(ctx.hasSufficientData)
    }

    @Test
    fun `null trends and sparse counts propagate`() {
        val ctx = buildProgressInsightContext(
            rangeDays = 7,
            weightTrendKgPerWeek = null,
            waistTrendCmPerWeek = null,
            liftTrendKgPerWeek = null,
            weightPointCount = 1,
            waistPointCount = 0,
            adherencePercent = null,
        )
        assertNull(ctx.weightTrendKgPerWeek)
        assertNull(ctx.adherencePercent)
        assertFalse(ctx.hasSufficientData)
    }
}
