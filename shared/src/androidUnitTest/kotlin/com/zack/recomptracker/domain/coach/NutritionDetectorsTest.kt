package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.domain.coach.CoachContextFixtures.TODAY
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
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
            put(TODAY.minus(1, DateTimeUnit.DAY), onTarget)
            put(TODAY.minus(2, DateTimeUnit.DAY), onTarget) // Saturday-ish; value drives surplus
            put(TODAY.minus(3, DateTimeUnit.DAY), bigDay)
            put(TODAY.minus(4, DateTimeUnit.DAY), onTarget)
            put(TODAY.minus(5, DateTimeUnit.DAY), onTarget)
            put(TODAY.minus(6, DateTimeUnit.DAY), onTarget)
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
        val map = (0 until 7).associate { TODAY.minus(it.toLong(), DateTimeUnit.DAY) to onTarget }
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
        val trainedDates = setOf(TODAY, TODAY.minus(2, DateTimeUnit.DAY), TODAY.minus(4, DateTimeUnit.DAY))
        val restDates = setOf(TODAY.minus(1, DateTimeUnit.DAY), TODAY.minus(3, DateTimeUnit.DAY), TODAY.minus(5, DateTimeUnit.DAY))
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
        val trainedDates = setOf(TODAY, TODAY.minus(2, DateTimeUnit.DAY), TODAY.minus(4, DateTimeUnit.DAY))
        val restDates = setOf(TODAY.minus(1, DateTimeUnit.DAY), TODAY.minus(3, DateTimeUnit.DAY), TODAY.minus(5, DateTimeUnit.DAY))
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

    // ── ConsistencyCheckInDetector ─────────────────────────────────────────────

    private val meal = MacroTotals(2400, 180.0, 250.0, 70.0)

    /** Logged on the given day-offsets before TODAY (0 == today). */
    private fun loggedOn(vararg offsets: Int) =
        offsets.associate { TODAY.minus(it.toLong(), DateTimeUnit.DAY) to meal }

    @Test
    fun `consistency check-in fires when a good stretch drops to a thin recent week`() {
        // Prior week (offsets 7..13): 7 logged = good stretch. Recent week (0..6): only 3 logged.
        val prior = (7..13).associate { TODAY.minus(it.toLong(), DateTimeUnit.DAY) to meal }
        val recent = loggedOn(0, 2, 4) // 3 of the last 7
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = prior + recent),
        )
        val s = ConsistencyCheckInDetector().detect(ctx)!!
        assertEquals(SignalKind.CONSISTENCY_CHECK_IN, s.kind)
        assertEquals(SignalTier.P2, s.tier)
        assertEquals(SignalCategory.NUTRITION, s.category)
        assertEquals(CoachActionType.OPEN_FOOD_LOG, s.action.type)
        assertEquals("3", s.facts.values["recentDaysLogged"])
        assertTrue(s.verdict.contains("confidence", ignoreCase = true))
        assertTrue(s.fallbackText.isNotBlank())
        assertTrue(s.dedupKey.startsWith("CONSISTENCY_CHECK_IN|"))
    }

    @Test
    fun `consistency check-in stays silent when the recent week is still well logged`() {
        // Recent week has 5 logged (>= floor of 4) -> no slip.
        val prior = (7..13).associate { TODAY.minus(it.toLong(), DateTimeUnit.DAY) to meal }
        val recent = loggedOn(0, 1, 2, 3, 4)
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = prior + recent),
        )
        assertNull(ConsistencyCheckInDetector().detect(ctx))
    }

    @Test
    fun `consistency check-in stays silent without a prior good stretch (new user)`() {
        // Recent week thin (2 logged) but the prior week was also thin -> not a genuine drop.
        val prior = loggedOn(7, 8) // only 2 in the prior window
        val recent = loggedOn(0, 3)
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = prior + recent),
        )
        assertNull(ConsistencyCheckInDetector().detect(ctx))
    }
}
