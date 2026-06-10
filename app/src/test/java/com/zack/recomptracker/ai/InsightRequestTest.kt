package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightRequestTest {

    private val progressCtx = ProgressInsightContext(
        rangeDays = 28,
        weightTrendKgPerWeek = -0.2,
        waistTrendCmPerWeek = -0.3,
        liftTrendKgPerWeek = 0.5,
        adherencePercent = 90.0,
        weightPointCount = 1,
        waistPointCount = 1,
    )

    private val recoveryCtx = RecoveryInsightContext(
        sleepHours = 7.0, energyScore = 6, hungerScore = 4, sorenessScore = 5, trained = true,
    )

    @Test
    fun `progress request reports its kind`() {
        assertEquals(InsightKind.PROGRESS_TREND, InsightRequest.ProgressTrend(progressCtx).kind)
    }

    @Test
    fun `recovery request reports its kind`() {
        assertEquals(InsightKind.RECOVERY_READINESS, InsightRequest.RecoveryReadiness(recoveryCtx).kind)
    }

    @Test
    fun `request delegates sufficiency to context`() {
        assertFalse(InsightRequest.ProgressTrend(progressCtx).hasSufficientData)
        assertTrue(InsightRequest.RecoveryReadiness(recoveryCtx).hasSufficientData)
    }

    @Test
    fun `request delegates dedup key to context`() {
        assertEquals(progressCtx.key(), InsightRequest.ProgressTrend(progressCtx).dedupKey())
    }
}
