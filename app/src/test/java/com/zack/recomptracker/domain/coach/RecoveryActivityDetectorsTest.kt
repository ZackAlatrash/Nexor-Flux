package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachContextFixtures.recoverySeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryActivityDetectorsTest {

    // ── RecoveryDeclineDetector ────────────────────────────────────────────────

    @Test
    fun `recovery decline fires when the recovery trend is POOR`() {
        // sleep < 6h forces RecoveryTrend.POOR in TrendCalculator.
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                sleepSeries = recoverySeries(5.2),
                energySeries = recoverySeries(4.0),
                sorenessSeries = recoverySeries(8.0),
            ),
        )
        val s = RecoveryDeclineDetector().detect(ctx)!!
        assertEquals(SignalKind.RECOVERY_DECLINE, s.kind)
        assertEquals(SignalTier.P0, s.tier)
        assertEquals(SignalCategory.RECOVERY, s.category)
        assertTrue(s.facts.values.containsKey("avgSleepHours"))
        assertTrue(s.dedupKey.startsWith("RECOVERY_DECLINE|2026-W27"))
    }

    @Test
    fun `recovery decline does NOT fire on good recovery`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                sleepSeries = recoverySeries(8.0),
                energySeries = recoverySeries(8.0),
                sorenessSeries = recoverySeries(3.0),
            ),
        )
        assertNull(RecoveryDeclineDetector().detect(ctx))
    }

    // ── NeatCollapseDetector ───────────────────────────────────────────────────

    @Test
    fun `NEAT collapse fires when steps drop 25 percent and weight is stalled`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                avgSteps7 = 6000,
                avgStepsPrev7 = 9000, // 33% drop
                weightTrendKgPerWeek = 0.02, // stalled
            ),
        )
        val s = NeatCollapseDetector().detect(ctx)!!
        assertEquals(SignalKind.NEAT_COLLAPSE, s.kind)
        assertEquals(SignalTier.P1, s.tier)
        assertEquals(SignalCategory.ACTIVITY, s.category)
        assertEquals("6000", s.facts.values["avgSteps7"])
        assertTrue(s.dedupKey.startsWith("NEAT_COLLAPSE|2026-W27"))
    }

    @Test
    fun `NEAT collapse does NOT fire when steps hold`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                avgSteps7 = 8800,
                avgStepsPrev7 = 9000, // ~2% drop
                weightTrendKgPerWeek = 0.02,
            ),
        )
        assertNull(NeatCollapseDetector().detect(ctx))
    }

    @Test
    fun `NEAT collapse does NOT fire when weight is clearly moving`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                avgSteps7 = 6000,
                avgStepsPrev7 = 9000, // steps dropped
                weightTrendKgPerWeek = -0.4, // but weight is dropping fast — not a stall
            ),
        )
        assertNull(NeatCollapseDetector().detect(ctx))
    }
}
