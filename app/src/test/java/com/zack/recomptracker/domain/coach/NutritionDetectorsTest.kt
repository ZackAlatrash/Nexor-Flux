package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.domain.coach.CoachContextFixtures.TODAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionDetectorsTest {

    // ── LowAdherenceDetector ───────────────────────────────────────────────────

    @Test
    fun `low adherence fires below the 80 percent floor`() {
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(adherencePercent = 71.0),
        )
        val s = LowAdherenceDetector().detect(ctx)!!
        assertEquals(SignalKind.LOW_ADHERENCE, s.kind)
        assertEquals(SignalTier.P1, s.tier)
        assertEquals("71%", s.facts.values["adherencePercent"])
        assertTrue(s.dedupKey.startsWith("LOW_ADHERENCE|2026-W27"))
    }

    @Test
    fun `low adherence does NOT fire at or above the floor`() {
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(adherencePercent = 88.0),
        )
        assertNull(LowAdherenceDetector().detect(ctx))
    }

    // ── DerailmentDayDetector ──────────────────────────────────────────────────

    @Test
    fun `derailment day fires when one day drives the week's surplus`() {
        // 6 on-target days + one big Saturday surplus. Target 2400.
        val onTarget = MacroTotals(2400, 180.0, 250.0, 70.0)
        val bigDay = MacroTotals(3600, 200.0, 400.0, 120.0) // +1200 kcal
        val map = buildMap {
            // Ensure >= 14 logged days for the engine gate isn't relevant here (unit test),
            // but detectDerailmentDay only looks at the last 7 logged days.
            put(TODAY, onTarget)
            put(TODAY.minusDays(1), onTarget)
            put(TODAY.minusDays(2), onTarget) // Saturday-ish; value drives surplus
            put(TODAY.minusDays(3), bigDay)
            put(TODAY.minusDays(4), onTarget)
            put(TODAY.minusDays(5), onTarget)
            put(TODAY.minusDays(6), onTarget)
        }
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = map),
        )
        val s = DerailmentDayDetector().detect(ctx)!!
        assertEquals(SignalKind.DERAILMENT_DAY, s.kind)
        assertEquals(SignalTier.P2, s.tier)
        assertTrue(s.facts.values["statement"]!!.contains("surplus"))
        assertTrue(s.dedupKey.startsWith("DERAILMENT_DAY|2026-W27"))
    }

    @Test
    fun `derailment day does NOT fire on a clean week`() {
        val onTarget = MacroTotals(2400, 180.0, 250.0, 70.0)
        val map = (0 until 7).associate { TODAY.minusDays(it.toLong()) to onTarget }
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = map),
        )
        assertNull(DerailmentDayDetector().detect(ctx))
    }

    // ── ProteinTrainingDayDetector ─────────────────────────────────────────────

    @Test
    fun `protein-miss-on-training-days fires when trained days lag rest days`() {
        val target = 180
        val restDay = MacroTotals(2400, 180.0, 250.0, 70.0)       // 100% protein
        val trainedDay = MacroTotals(2400, 120.0, 300.0, 80.0)    // ~67% protein
        val trainedDates = setOf(TODAY, TODAY.minusDays(2), TODAY.minusDays(4))
        val restDates = setOf(TODAY.minusDays(1), TODAY.minusDays(3), TODAY.minusDays(5))
        val map = buildMap {
            trainedDates.forEach { put(it, trainedDay) }
            restDates.forEach { put(it, restDay) }
        }
        val ctx = CoachContextFixtures.context(
            plan = CoachContextFixtures.plan(targetProteinG = target),
            nutrition = CoachContextFixtures.nutrition(eatenByDate = map),
            body = CoachContextFixtures.body(trainedDates = trainedDates),
        )
        val s = ProteinTrainingDayDetector().detect(ctx)!!
        assertEquals(SignalKind.PROTEIN_MISS_TRAINING_DAY, s.kind)
        assertEquals(SignalTier.P1, s.tier)
        assertTrue(s.facts.values.containsKey("proteinPctTrainingDays"))
        assertTrue(s.facts.values.containsKey("proteinPctRestDays"))
    }

    @Test
    fun `protein-miss does NOT fire when trained and rest days match`() {
        val even = MacroTotals(2400, 180.0, 250.0, 70.0)
        val trainedDates = setOf(TODAY, TODAY.minusDays(2), TODAY.minusDays(4))
        val restDates = setOf(TODAY.minusDays(1), TODAY.minusDays(3), TODAY.minusDays(5))
        val map = (trainedDates + restDates).associateWith { even }
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = map),
            body = CoachContextFixtures.body(trainedDates = trainedDates),
        )
        assertNull(ProteinTrainingDayDetector().detect(ctx))
    }

    // ── UnconfirmedPlannedMealsDetector ────────────────────────────────────────

    @Test
    fun `unconfirmed planned meals fires when count is over zero`() {
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(unconfirmedPlannedCount = 3),
        )
        val s = UnconfirmedPlannedMealsDetector().detect(ctx)!!
        assertEquals(SignalKind.UNCONFIRMED_PLANNED_MEALS, s.kind)
        assertEquals(SignalTier.P2, s.tier)
        assertEquals(CoachActionType.CONFIRM_PLANNED_MEALS, s.action.type)
        assertEquals("3", s.facts.values["unconfirmedPlannedCount"])
    }

    @Test
    fun `unconfirmed planned meals does NOT fire when there are none`() {
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(unconfirmedPlannedCount = 0),
        )
        assertNull(UnconfirmedPlannedMealsDetector().detect(ctx))
    }
}
