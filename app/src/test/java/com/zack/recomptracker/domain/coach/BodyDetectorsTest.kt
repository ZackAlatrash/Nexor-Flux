package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachContextFixtures.TODAY
import com.zack.recomptracker.domain.coach.CoachContextFixtures.flatSeries
import com.zack.recomptracker.domain.coach.CoachContextFixtures.linearSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyDetectorsTest {

    // ── RecompWinDetector ──────────────────────────────────────────────────────

    @Test
    fun `recomp win fires when weight is flat and waist is falling`() {
        // weight flat at 80; waist falls ~0.3 cm/wk over 21 days (negative delta = falling).
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightSeries = flatSeries(80.0),
                waistSeries = linearSeries(endValue = 85.0, perDayDelta = -0.3 / 7.0),
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
                weightSeries = flatSeries(80.0),
                waistSeries = flatSeries(85.0),
            ),
        )
        assertNull(RecompWinDetector().detect(ctx))
    }

    @Test
    fun `recomp win does NOT fire when weight is climbing`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(
                weightSeries = linearSeries(endValue = 80.0, perDayDelta = 0.4 / 7.0),
                waistSeries = linearSeries(endValue = 85.0, perDayDelta = 0.3 / 7.0),
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
                weightSeries = linearSeries(endValue = 80.0, perDayDelta = 0.4 / 7.0),
                waistSeries = linearSeries(endValue = 85.0, perDayDelta = 0.4 / 7.0),
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
                weightSeries = linearSeries(endValue = 80.0, perDayDelta = 0.4 / 7.0),
                waistSeries = flatSeries(85.0),
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
}
