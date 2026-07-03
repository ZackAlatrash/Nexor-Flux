package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachContextFixtures.TODAY
import com.zack.recomptracker.domain.coach.CoachContextFixtures.recoverySeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryActivityDetectorsTest {

    /** A recovery series that's [baseline] on the prior days and [todayValue] on today. */
    private fun seriesWithToday(baseline: Double, todayValue: Double, priorDays: Int = 10): List<MetricPoint> {
        val prior = (1..priorDays).map { MetricPoint(TODAY.minusDays(it.toLong()), baseline) }
        return prior + MetricPoint(TODAY, todayValue)
    }

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

    // ── MorningReadinessDetector ────────────────────────────────────────────────

    @Test
    fun `morning readiness fires LOW when today's recovery is worse than the user's baseline`() {
        // Today: sleep well short of usual + soreness up vs usual → low readiness.
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                sleepSeries = seriesWithToday(baseline = 7.5, todayValue = 5.5),
                sorenessSeries = seriesWithToday(baseline = 3.0, todayValue = 6.0),
                energySeries = seriesWithToday(baseline = 8.0, todayValue = 8.0),
                hungerSeries = seriesWithToday(baseline = 4.0, todayValue = 4.0),
            ),
        )
        val s = MorningReadinessDetector().detect(ctx)!!
        assertEquals(SignalKind.MORNING_READINESS, s.kind)
        assertEquals(SignalTier.P1, s.tier)
        assertEquals(SignalCategory.RECOVERY, s.category)
        assertEquals(CoachActionType.OPEN_TRAINING, s.action.type)
        assertTrue(s.verdict.contains("light", ignoreCase = true))
        assertTrue(s.dedupKey.endsWith("|low"))
        assertTrue(s.fallbackText.isNotBlank())
        // The decisive driver is named in the rationale.
        assertTrue(s.rationale.primaryCauseByDomain.containsKey("recovery"))
    }

    @Test
    fun `morning readiness fires GOOD when today's recovery matches baseline`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                sleepSeries = seriesWithToday(baseline = 7.5, todayValue = 7.6),
                energySeries = seriesWithToday(baseline = 8.0, todayValue = 8.0),
                sorenessSeries = seriesWithToday(baseline = 3.0, todayValue = 3.0),
                hungerSeries = seriesWithToday(baseline = 4.0, todayValue = 4.0),
            ),
        )
        val s = MorningReadinessDetector().detect(ctx)!!
        assertEquals(SignalKind.MORNING_READINESS, s.kind)
        assertEquals(SignalTier.P1, s.tier)
        assertTrue(s.verdict.contains("train as planned", ignoreCase = true))
        assertTrue(s.dedupKey.endsWith("|good"))
        assertTrue(s.fallbackText.isNotBlank())
    }

    @Test
    fun `morning readiness stays silent when fewer than two inputs are logged today`() {
        // Only sleep logged today (energy/soreness/hunger series exclude today).
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                sleepSeries = seriesWithToday(baseline = 7.5, todayValue = 5.0),
                energySeries = (1..10).map { MetricPoint(TODAY.minusDays(it.toLong()), 8.0) },
                sorenessSeries = (1..10).map { MetricPoint(TODAY.minusDays(it.toLong()), 3.0) },
                hungerSeries = (1..10).map { MetricPoint(TODAY.minusDays(it.toLong()), 4.0) },
            ),
        )
        assertNull(MorningReadinessDetector().detect(ctx))
    }
}
