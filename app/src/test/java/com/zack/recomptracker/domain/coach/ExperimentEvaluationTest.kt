package com.zack.recomptracker.domain.coach

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5F — [ExperimentEvaluation]: the deterministic experiment-result direction logic and the
 * follow-up signal it produces. Pure objects.
 */
class ExperimentEvaluationTest {

    // ── Direction classification ─────────────────────────────────────────────────

    @Test
    fun `lower hunger counts as improved for the protein-hunger experiment`() {
        // baseline 5.0 → current 3.5: hunger fell, and lower is better → IMPROVED.
        assertEquals(
            ExperimentDirection.IMPROVED,
            ExperimentEvaluation.classify("PROTEIN_HIT_NEXT_DAY_HUNGER", baseline = 5.0, current = 3.5),
        )
        // Hunger rose → WORSENED.
        assertEquals(
            ExperimentDirection.WORSENED,
            ExperimentEvaluation.classify("PROTEIN_HIT_NEXT_DAY_HUNGER", baseline = 5.0, current = 6.5),
        )
    }

    @Test
    fun `higher energy counts as improved for the sleep-energy experiment`() {
        assertEquals(
            ExperimentDirection.IMPROVED,
            ExperimentEvaluation.classify("SLEEP_NEXT_DAY_ENERGY", baseline = 6.0, current = 8.0),
        )
        assertEquals(
            ExperimentDirection.WORSENED,
            ExperimentEvaluation.classify("SLEEP_NEXT_DAY_ENERGY", baseline = 6.0, current = 4.0),
        )
    }

    @Test
    fun `fewer weekend calories counts as improved for the weekend-surplus experiment`() {
        assertEquals(
            ExperimentDirection.IMPROVED,
            ExperimentEvaluation.classify("WEEKEND_CALORIE_SURPLUS", baseline = 2800.0, current = 2400.0),
        )
    }

    @Test
    fun `a move smaller than epsilon reads as no change`() {
        assertEquals(
            ExperimentDirection.NO_CHANGE,
            ExperimentEvaluation.classify("SLEEP_NEXT_DAY_ENERGY", baseline = 6.0, current = 6.1),
        )
    }

    // ── The result signal ────────────────────────────────────────────────────────

    @Test
    fun `evaluate emits an EXPERIMENT_RESULT signal comparing current to baseline`() {
        // Current hunger series averages 3.0; baseline was 5.0 → improved.
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(hungerSeries = CoachContextFixtures.recoverySeries(3.0)),
        )
        val experiment = ActiveExperiment(
            correlationId = "PROTEIN_HIT_NEXT_DAY_HUNGER",
            hypothesis = "Hitting protein may curb next-day cravings.",
            trackedMetric = "hunger",
            baselineValue = 5.0,
            startDateIso = "2026-06-20",
        )
        val s = ExperimentEvaluation.evaluate(experiment, ctx)!!
        assertEquals(SignalKind.EXPERIMENT_RESULT, s.kind)
        assertEquals(CoachSurface.TODAY, s.surface)
        assertEquals(SignalTier.P1, s.tier)
        assertTrue(s.verdict.isNotBlank())
        assertTrue(s.fallbackText.isNotBlank())
        assertEquals("IMPROVED", s.facts.values["direction"])
        assertEquals("5.0", s.facts.values["baselineValue"])
        assertEquals("3.0", s.facts.values["currentValue"])
        assertTrue(s.dedupKey.startsWith("EXPERIMENT_RESULT|PROTEIN_HIT_NEXT_DAY_HUNGER|"))
    }

    @Test
    fun `currentValue reads only the test window since the experiment start, not the whole context window`() {
        // Baseline (pre-experiment) hunger was 6.0; since starting the experiment hunger has run 3.0.
        // The evaluation must compare the SINCE-START window (3.0), not a blended full-window average
        // (~4.9) that barely moves off the overlapping baseline.
        val start = LocalDate.of(2026, 6, 24)
        val hunger = (0 until 22).map { i ->
            val date = CoachContextFixtures.TODAY.minusDays(i.toLong()) // 2026-07-01 .. 2026-06-10
            MetricPoint(date, if (date.isBefore(start)) 6.0 else 3.0)
        }
        val ctx = CoachContextFixtures.context(body = CoachContextFixtures.body(hungerSeries = hunger))
        val experiment = ActiveExperiment(
            correlationId = "PROTEIN_HIT_NEXT_DAY_HUNGER",
            hypothesis = "Hitting protein may curb next-day cravings.",
            trackedMetric = "hunger",
            baselineValue = 6.0,
            startDateIso = "2026-06-24",
        )

        assertEquals(3.0, ExperimentEvaluation.currentValue(experiment, ctx)!!, 0.001)

        val s = ExperimentEvaluation.evaluate(experiment, ctx)!!
        assertEquals("3.0", s.facts.values["currentValue"])
        assertEquals("IMPROVED", s.facts.values["direction"])
    }

    @Test
    fun `evaluate is silent when the tracked metric has no current value`() {
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(energySeries = emptyList()),
        )
        val experiment = ActiveExperiment(
            correlationId = "SLEEP_NEXT_DAY_ENERGY",
            hypothesis = "Protecting sleep may lift your energy.",
            trackedMetric = "energy",
            baselineValue = 6.0,
            startDateIso = "2026-06-20",
        )
        assertNull(ExperimentEvaluation.evaluate(experiment, ctx))
    }
}
