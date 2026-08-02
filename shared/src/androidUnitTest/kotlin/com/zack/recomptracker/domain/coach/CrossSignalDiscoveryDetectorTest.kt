package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.core.model.MacroTotals
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 — [CrossSignalDiscoveryDetector]: the flagship genuine-LLM card. Verifies it fires on the
 * strongest candidate correlation, is silent below the effect threshold / on thin data, and carries a
 * stable per-correlation weekly dedupKey (so the pipeline's 7-day cooldown gates it). Pure objects.
 */
class CrossSignalDiscoveryDetectorTest {

    private val today = CoachContextFixtures.TODAY

    /** A per-day eaten map with a fixed protein value on every logged day. */
    private fun eaten(
        loggedDays: Int,
        protein: Double,
        carbs: Double = 250.0,
        calories: Int = 2400,
    ): Map<LocalDate, MacroTotals> =
        (0 until loggedDays).associate { i ->
            today.minus(i.toLong(), DateTimeUnit.DAY) to MacroTotals(calories, protein, carbs, 70.0)
        }

    // ── Fires on the strongest candidate ─────────────────────────────────────────

    @Test
    fun `fires on the protein-hunger link when it is the strongest clearing candidate`() {
        // Alternate protein-hit (>=162g) and protein-miss days; next-day hunger low after hits, high
        // after misses → a large, clearing effect. Keep other candidates flat so this one wins.
        val proteinMap = (0 until 16).associate { i ->
            val protein = if (i % 2 == 0) 180.0 else 100.0
            today.minus(i.toLong(), DateTimeUnit.DAY) to MacroTotals(2400, protein, 250.0, 70.0)
        }
        // Hunger the day AFTER a protein-hit day (even i) is low; after a miss (odd i) is high.
        // date d's hunger reflects the PREVIOUS day's protein: previous day = d.plusDays? No — the
        // detector reads hunger at date+1 for the eaten day. So eaten day i (date today-i) pairs with
        // hunger at today-i+1. Build hunger so hit days → low next-day hunger.
        val hunger = (0 until 17).map { i ->
            val date = today.minus(i.toLong(), DateTimeUnit.DAY)
            // The eaten day one day earlier is (today-(i+1)); its parity decides hunger here.
            val eatenDayIndex = i + 1
            val value = if (eatenDayIndex % 2 == 0) 3.0 else 8.0 // hit day → low next-day hunger
            MetricPoint(date, value)
        }
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = proteinMap.toMap()),
            body = CoachContextFixtures.body(
                hungerSeries = hunger,
                // Flatten sleep/energy/carb signals so only the protein link clears.
                sleepSeries = CoachContextFixtures.recoverySeries(7.5),
                energySeries = CoachContextFixtures.recoverySeries(8.0),
            ),
        )
        val s = CrossSignalDiscoveryDetector().detect(ctx)!!
        assertEquals(SignalKind.CROSS_SIGNAL_DISCOVERY, s.kind)
        assertEquals(SignalTier.P1, s.tier)
        assertEquals(SignalCategory.BEHAVIOR, s.category)
        assertEquals(CoachSurface.TODAY, s.surface)
        assertEquals(CoachActionType.TRACK_EXPERIMENT, s.action.type)
        assertEquals("Track this", s.action.label)
        assertTrue(s.verdict.isNotBlank())
        assertTrue(s.fallbackText.isNotBlank())
        // The experiment seed is carried as engine-computed facts.
        assertEquals("PROTEIN_HIT_NEXT_DAY_HUNGER", s.facts.values["correlationId"])
        assertEquals("hunger", s.facts.values["trackedMetric"])
        assertTrue(s.facts.values.containsKey("baselineValue"))
        // HIGH severity so it wins the P1 slot the week it fires.
        assertTrue("severity is elevated", s.severity >= 60)
        assertTrue(s.dedupKey.startsWith("CROSS_SIGNAL_DISCOVERY|PROTEIN_HIT_NEXT_DAY_HUNGER|"))
        assertTrue(s.dedupKey.contains("2026-W27"))
    }

    @Test
    fun `picks the strongest of several clearing candidates`() {
        // Strong sleep-energy link (gap 5) AND a weaker weekend-surplus (gap ~200). Sleep should win.
        val sleep = ArrayList<MetricPoint>()
        val energy = ArrayList<MetricPoint>()
        for (i in 0 until 16) {
            val date = today.minus(i.toLong(), DateTimeUnit.DAY)
            sleep += MetricPoint(date, if (i % 2 == 0) 8.0 else 5.0)
        }
        for (i in 0 until 17) {
            val date = today.minus(i.toLong(), DateTimeUnit.DAY)
            // next-day energy: high after good sleep (prev day even), low after poor sleep.
            val prevIndex = i + 1
            energy += MetricPoint(date, if (prevIndex % 2 == 0) 9.0 else 4.0)
        }
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, energySeries = energy),
        )
        val s = CrossSignalDiscoveryDetector().detect(ctx)!!
        assertEquals("SLEEP_NEXT_DAY_ENERGY", s.facts.values["correlationId"])
    }

    // ── Silent under threshold / thin data ───────────────────────────────────────

    @Test
    fun `is silent when no candidate clears its threshold`() {
        // Default fixtures: every day hits protein (180=target), flat hunger/energy, no weekend spread
        // meaningful → protein groups can't split (all hits) and other gaps are ~0.
        val ctx = CoachContextFixtures.context()
        assertNull(CrossSignalDiscoveryDetector().detect(ctx))
    }

    // ── Weekend surplus fires only when weekends are actually HIGHER ──────────────

    /** 28 days of eaten macros where weekend calories differ from weekday by a fixed amount. */
    private fun weekendVsWeekday(weekendCals: Int, weekdayCals: Int): Map<LocalDate, MacroTotals> =
        (0 until 28).associate { i ->
            val date = today.minus(i.toLong(), DateTimeUnit.DAY)
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            // Protein pinned at target on every day so the protein candidate can't split (all hits),
            // carbs constant so the carb candidate stays flat — leaving weekend as the only candidate.
            date to MacroTotals(if (weekend) weekendCals else weekdayCals, 180.0, 250.0, 70.0)
        }

    @Test
    fun `weekend surplus fires when weekends run higher than weekdays`() {
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = weekendVsWeekday(2900, 2500)),
        )
        val s = CrossSignalDiscoveryDetector().detect(ctx)!!
        assertEquals("WEEKEND_CALORIE_SURPLUS", s.facts.values["correlationId"])
        assertTrue("verdict names a positive surplus", s.verdict.contains("+"))
    }

    @Test
    fun `weekend surplus is silent when weekends run LOWER than weekdays`() {
        // effect = 2300 - 2600 = -300; |effect| clears 150 but the direction is wrong — the card
        // must not fire a "+300, weekends build the surplus" claim when weekends are actually lower.
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = weekendVsWeekday(2300, 2600)),
        )
        assertNull(CrossSignalDiscoveryDetector().detect(ctx))
    }

    @Test
    fun `is silent below the minimum logged-days gate`() {
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(loggedDaysInWindow = 10),
        )
        assertNull(CrossSignalDiscoveryDetector().detect(ctx))
    }

    @Test
    fun `is silent when a group has too few days`() {
        // Only 2 protein-miss days (< 3 per group), rest hits → protein candidate can't evaluate; and
        // no other signal moves → silence.
        val proteinMap = (0 until 16).associate { i ->
            val protein = if (i < 2) 100.0 else 180.0
            today.minus(i.toLong(), DateTimeUnit.DAY) to MacroTotals(2400, protein, 250.0, 70.0)
        }
        val hunger = (0 until 17).map { i -> MetricPoint(today.minus(i.toLong(), DateTimeUnit.DAY), 4.0) }
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(eatenByDate = proteinMap.toMap()),
            body = CoachContextFixtures.body(hungerSeries = hunger),
        )
        assertNull(CrossSignalDiscoveryDetector().detect(ctx))
    }
}
