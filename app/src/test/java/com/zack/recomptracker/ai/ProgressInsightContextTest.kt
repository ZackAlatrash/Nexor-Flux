package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressInsightContextTest {

    private fun ctx(
        rangeDays: Int = 28,
        weightTrendKgPerWeek: Double? = -0.2,
        waistTrendCmPerWeek: Double? = -0.3,
        liftTrendKgPerWeek: Double? = 0.5,
        adherencePercent: Double? = 90.0,
        weightPointCount: Int = 10,
        waistPointCount: Int = 8,
        trainingSessionsPerWeek: Double? = 3.0,
        weeklyGymSessionsTarget: Int? = 4,
    ) = ProgressInsightContext(
        rangeDays, weightTrendKgPerWeek, waistTrendCmPerWeek,
        liftTrendKgPerWeek, adherencePercent, weightPointCount, waistPointCount,
        trainingSessionsPerWeek = trainingSessionsPerWeek,
        weeklyGymSessionsTarget = weeklyGymSessionsTarget,
    )

    @Test
    fun `sufficient when at least two weight points`() {
        assertTrue(ctx(weightPointCount = 2, waistPointCount = 0).hasSufficientData)
    }

    @Test
    fun `sufficient when at least two waist points`() {
        assertTrue(ctx(weightPointCount = 0, waistPointCount = 2).hasSufficientData)
    }

    @Test
    fun `insufficient when fewer than two of both`() {
        assertFalse(ctx(weightPointCount = 1, waistPointCount = 1).hasSufficientData)
    }

    @Test
    fun `key changes when weight trend changes meaningfully`() {
        assertTrue(ctx(weightTrendKgPerWeek = -0.2).key() != ctx(weightTrendKgPerWeek = 0.4).key())
    }

    @Test
    fun `key stable for identical inputs`() {
        assertEquals(ctx().key(), ctx().key())
    }

    @Test
    fun `key distinguishes null trend from near-zero trend`() {
        assertTrue(ctx(weightTrendKgPerWeek = null).key() != ctx(weightTrendKgPerWeek = 0.0).key())
    }

    @Test
    fun `key changes when lift trend changes`() {
        assertTrue(ctx(liftTrendKgPerWeek = 0.5).key() != ctx(liftTrendKgPerWeek = -0.5).key())
    }

    @Test
    fun `key changes when training frequency changes meaningfully`() {
        assertTrue(ctx(trainingSessionsPerWeek = 2.0).key() != ctx(trainingSessionsPerWeek = 4.0).key())
    }

    @Test
    fun `key changes when gym-sessions target changes`() {
        assertTrue(ctx(weeklyGymSessionsTarget = 3).key() != ctx(weeklyGymSessionsTarget = 5).key())
    }

    @Test
    fun `key distinguishes null frequency from a value`() {
        assertTrue(ctx(trainingSessionsPerWeek = null).key() != ctx(trainingSessionsPerWeek = 0.0).key())
    }
}
