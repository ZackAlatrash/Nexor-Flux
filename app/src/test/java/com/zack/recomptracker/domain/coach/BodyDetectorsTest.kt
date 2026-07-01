package com.zack.recomptracker.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyDetectorsTest {

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
}
