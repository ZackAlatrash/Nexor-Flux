package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachContextFixtures.datedSeries
import com.zack.recomptracker.domain.coach.CoachContextFixtures.recoverySeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-4 cross-domain link detectors: [DeloadDueDetector] (RIR falling × recovery POOR) and
 * [SleepHungerLinkDetector] (poor-sleep days run hungrier). Pure objects, no mocks.
 */
class CrossDomainDetectorsTest {

    // ── DeloadDueDetector ──────────────────────────────────────────────────────

    /** Body context whose sleep/energy/soreness force RecoveryTrend.POOR. */
    private fun poorRecoveryBody() = CoachContextFixtures.body(
        sleepSeries = recoverySeries(5.2),
        energySeries = recoverySeries(4.0),
        sorenessSeries = recoverySeries(8.0),
    )

    @Test
    fun `deload due fires when RIR is low and falling and recovery is POOR`() {
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            training = CoachContextFixtures.training(recentRir = listOf(3, 2, 1, 1)),
        )
        val s = DeloadDueDetector().detect(ctx)!!
        assertEquals(SignalKind.DELOAD_DUE, s.kind)
        assertEquals(SignalTier.P1, s.tier)
        assertEquals(SignalCategory.RECOVERY, s.category)
        assertEquals(CoachSurface.TODAY, s.surface)
        assertEquals(CoachActionType.OPEN_TRAINING, s.action.type)
        assertTrue("verdict recommends a deload", s.verdict.contains("deload", ignoreCase = true))
        assertTrue(s.verdict.isNotBlank())
        assertTrue(s.fallbackText.isNotBlank())
        // Facts come from the RIR + recovery numbers only.
        assertTrue(s.facts.values.containsKey("recentAvgRir"))
        assertEquals("1.0", s.facts.values["recentAvgRir"])
        assertTrue(s.facts.values.containsKey("avgSleepHours"))
        assertTrue(s.dedupKey.startsWith("DELOAD_DUE|2026-W27"))
        assertTrue(s.severity in 0..100)
    }

    @Test
    fun `deload due does NOT fire when recovery is fine even if RIR is low`() {
        val ctx = CoachContextFixtures.context(
            // default body has good recovery (sleep 7.5, energy 8, soreness 3)
            training = CoachContextFixtures.training(recentRir = listOf(2, 1, 1, 0)),
        )
        assertNull(DeloadDueDetector().detect(ctx))
    }

    @Test
    fun `deload due does NOT fire when RIR is healthy even if recovery is POOR`() {
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            training = CoachContextFixtures.training(recentRir = listOf(3, 3, 3, 3)),
        )
        assertNull(DeloadDueDetector().detect(ctx))
    }

    @Test
    fun `deload due does NOT fire when RIR is low but rising (recovering)`() {
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            training = CoachContextFixtures.training(recentRir = listOf(0, 1, 2, 3)),
        )
        assertNull(DeloadDueDetector().detect(ctx))
    }

    @Test
    fun `deload due does NOT fire when there is no RIR data`() {
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            training = CoachContextFixtures.training(recentRir = emptyList()),
        )
        assertNull(DeloadDueDetector().detect(ctx))
    }

    // ── SleepHungerLinkDetector ────────────────────────────────────────────────

    @Test
    fun `sleep-hunger link fires when poor-sleep days run hungrier`() {
        // 4 good-sleep days (>=7h) with low hunger, 4 poor-sleep days (<6h) with high hunger.
        // Paired by date: alternate good/poor so both groups have >=3 days.
        val sleep = datedSeries(8.0, 5.0, 8.0, 5.0, 8.0, 5.0, 8.0, 5.0)
        val hunger = datedSeries(3.0, 7.0, 3.0, 7.0, 3.0, 7.0, 3.0, 7.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = hunger),
        )
        val s = SleepHungerLinkDetector().detect(ctx)!!
        assertEquals(SignalKind.SLEEP_HUNGER_LINK, s.kind)
        assertEquals(SignalTier.P2, s.tier)
        assertEquals(SignalCategory.RECOVERY, s.category)
        assertEquals(CoachSurface.TODAY, s.surface)
        assertTrue(s.verdict.isNotBlank())
        assertTrue("verdict mentions sleep", s.verdict.contains("sleep", ignoreCase = true))
        assertTrue(s.fallbackText.isNotBlank())
        // Facts = the two group averages, engine-computed.
        assertEquals("7.0", s.facts.values["poorSleepHunger"])
        assertEquals("3.0", s.facts.values["goodSleepHunger"])
        assertTrue(s.dedupKey.startsWith("SLEEP_HUNGER_LINK|"))
        assertTrue(s.dedupKey.contains("2026-W27"))
        assertTrue(s.severity in 0..100)
    }

    @Test
    fun `sleep-hunger link does NOT fire when the hunger gap is too small`() {
        val sleep = datedSeries(8.0, 5.0, 8.0, 5.0, 8.0, 5.0, 8.0, 5.0)
        // Gap of only 1.0 (< 1.5 min gap).
        val hunger = datedSeries(4.0, 5.0, 4.0, 5.0, 4.0, 5.0, 4.0, 5.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = hunger),
        )
        assertNull(SleepHungerLinkDetector().detect(ctx))
    }

    @Test
    fun `sleep-hunger link does NOT fire without enough days per group`() {
        // Only 2 poor-sleep days (< 3 min per group).
        val sleep = datedSeries(8.0, 8.0, 8.0, 8.0, 5.0, 5.0)
        val hunger = datedSeries(3.0, 3.0, 3.0, 3.0, 7.0, 7.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = hunger),
        )
        assertNull(SleepHungerLinkDetector().detect(ctx))
    }

    @Test
    fun `sleep-hunger link only fires the helpful direction (poor sleep hungrier)`() {
        // Reversed: poor-sleep days are LESS hungry — not the helpful direction, must stay silent.
        val sleep = datedSeries(8.0, 5.0, 8.0, 5.0, 8.0, 5.0, 8.0, 5.0)
        val hunger = datedSeries(7.0, 3.0, 7.0, 3.0, 7.0, 3.0, 7.0, 3.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = hunger),
        )
        assertNull(SleepHungerLinkDetector().detect(ctx))
    }

    @Test
    fun `sleep-hunger link does NOT fire when a day lacks a hunger pairing`() {
        // Sleep present but no hunger series at all → no pairs → silent.
        val sleep = datedSeries(8.0, 5.0, 8.0, 5.0, 8.0, 5.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = emptyList()),
        )
        assertNull(SleepHungerLinkDetector().detect(ctx))
    }
}
