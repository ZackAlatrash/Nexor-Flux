package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachContextFixtures.TODAY
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyDetectorsTest {

    /** A weight series that reads [priorValue] on the most recent prior day and [todayValue] today. */
    private fun weightWithToday(todayValue: Double, priorValue: Double): List<MetricPoint> = listOf(
        MetricPoint(TODAY.minus(2, DateTimeUnit.DAY), priorValue),
        MetricPoint(TODAY.minus(1, DateTimeUnit.DAY), priorValue),
        MetricPoint(TODAY, todayValue),
    )

    // ── RecompWinDetector ──────────────────────────────────────────────────────

    @Test
    fun `recomp win fires when weight is flat and waist is falling`() {
        // Detectors read the builder-computed smoothed trends (per §2), not raw series.
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightTrendKgPerWeek = 0.0,
                waistTrendCmPerWeek = -0.3,
            ),
        )
        val s = RecompWinDetector().detect(ctx)!!
        assertEquals(SignalKind.RECOMP_WIN, s.kind)
        assertEquals(SignalTier.P0, s.tier)
        assertEquals(SignalCategory.BODY, s.category)
        assertTrue(s.verdict.contains("recomp", ignoreCase = true))
        assertTrue(s.dedupKey.startsWith("RECOMP_WIN|2026-W27"))
        assertTrue(s.facts.values.containsKey("waistTrendCmPerWk"))
        assertTrue(s.fallbackText.isNotBlank())
    }

    @Test
    fun `recomp win does NOT fire when waist is flat`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightTrendKgPerWeek = 0.0,
                waistTrendCmPerWeek = 0.0,
            ),
        )
        assertNull(RecompWinDetector().detect(ctx))
    }

    @Test
    fun `recomp win does NOT fire when weight is climbing`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightTrendKgPerWeek = 0.4,
                waistTrendCmPerWeek = -0.3,
            ),
        )
        assertNull(RecompWinDetector().detect(ctx))
    }

    @Test
    fun `recomp win does NOT fire when the trend is unknown (thin data)`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightTrendKgPerWeek = null,
                waistTrendCmPerWeek = null,
            ),
        )
        assertNull(RecompWinDetector().detect(ctx))
    }

    // ── FatGainWarningDetector ─────────────────────────────────────────────────

    @Test
    fun `fat-gain warning fires when weight and waist both climb`() {
        val ctx = CoachContextFixtures.context(
            plan = CoachContextFixtures.plan(targetCalories = 2400),
            body = CoachContextFixtures.body(
                weightTrendKgPerWeek = 0.4,
                waistTrendCmPerWeek = 0.4,
            ),
        )
        val s = FatGainWarningDetector().detect(ctx)!!
        assertEquals(SignalKind.FAT_GAIN_WARNING, s.kind)
        assertEquals(SignalTier.P0, s.tier)
        assertEquals("2300", s.facts.values["newTargetCalories"])
        assertEquals(CoachActionType.APPLY_TARGET, s.action.type)
        assertTrue(s.dedupKey.startsWith("FAT_GAIN_WARNING|2026-W27"))
    }

    @Test
    fun `fat-gain warning does NOT fire when waist is stable`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightTrendKgPerWeek = 0.4,
                waistTrendCmPerWeek = 0.0,
            ),
        )
        assertNull(FatGainWarningDetector().detect(ctx))
    }

    // ── QuietWeighInsDetector ──────────────────────────────────────────────────

    @Test
    fun `quiet weigh-ins fires after 5+ silent days`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(daysSinceLastWeighIn = 7),
        )
        val s = QuietWeighInsDetector().detect(ctx)!!
        assertEquals(SignalKind.QUIET_WEIGH_INS, s.kind)
        assertEquals(SignalTier.P2, s.tier)
        assertEquals(CoachActionType.LOG_WEIGHT, s.action.type)
        assertEquals("7", s.facts.values["daysSinceLastWeighIn"])
    }

    @Test
    fun `quiet weigh-ins does NOT fire when weighed recently`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(daysSinceLastWeighIn = 2),
        )
        assertNull(QuietWeighInsDetector().detect(ctx))
    }

    // ── ScaleCheckDetector ─────────────────────────────────────────────────────

    @Test
    fun `scale check fires on a big jump against a flat trend`() {
        // +0.9 kg today, trend essentially flat -> water/food, defuse.
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightSeries = weightWithToday(todayValue = 81.0, priorValue = 80.1),
                weightTrendKgPerWeek = -0.02,
            ),
        )
        val s = ScaleCheckDetector().detect(ctx)!!
        assertEquals(SignalKind.SCALE_CHECK, s.kind)
        assertEquals(SignalTier.P2, s.tier)
        assertEquals(SignalCategory.BODY, s.category)
        assertEquals(CoachActionType.NONE, s.action.type) // reassurance: no action
        assertTrue(s.verdict.contains("not fat", ignoreCase = true))
        assertTrue(s.facts.values.containsKey("todayDeltaKg"))
        assertTrue(s.dedupKey.startsWith("SCALE_CHECK|"))
        assertTrue(s.fallbackText.isNotBlank())
    }

    @Test
    fun `scale check fires when today contradicts the trend direction`() {
        // Up 0.7 kg today but the smoothed trend is downward -> contradicts, defuse.
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightSeries = weightWithToday(todayValue = 80.7, priorValue = 80.0),
                weightTrendKgPerWeek = -0.30,
            ),
        )
        assertEquals(SignalKind.SCALE_CHECK, ScaleCheckDetector().detect(ctx)!!.kind)
    }

    @Test
    fun `scale check stays quiet when the jump agrees with the trend`() {
        // Up 0.7 kg today and the trend is also up -> real signal, not noise.
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightSeries = weightWithToday(todayValue = 80.7, priorValue = 80.0),
                weightTrendKgPerWeek = 0.30,
            ),
        )
        assertNull(ScaleCheckDetector().detect(ctx))
    }

    @Test
    fun `scale check stays quiet for a small daily wobble`() {
        // Only +0.2 kg -> below the jump threshold, nothing to defuse.
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightSeries = weightWithToday(todayValue = 80.2, priorValue = 80.0),
                weightTrendKgPerWeek = -0.30,
            ),
        )
        assertNull(ScaleCheckDetector().detect(ctx))
    }

    @Test
    fun `scale check stays quiet when there is no weigh-in today`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightSeries = listOf(
                    MetricPoint(TODAY.minus(2, DateTimeUnit.DAY), 80.0),
                    MetricPoint(TODAY.minus(1, DateTimeUnit.DAY), 81.0),
                ),
                weightTrendKgPerWeek = -0.02,
            ),
        )
        assertNull(ScaleCheckDetector().detect(ctx))
    }
}
