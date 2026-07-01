package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachContextFixtures.TODAY
import com.zack.recomptracker.domain.coach.CoachContextFixtures.e1rmPoints
import com.zack.recomptracker.domain.coach.CoachContextFixtures.stepsByDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDetectorsTest {

    // ── TrainingPlateauDetector ────────────────────────────────────────────────

    @Test
    fun `training plateau fires when e1RM is flat over recent sessions`() {
        val ctx = CoachContextFixtures.context(
            training = CoachContextFixtures.training(
                e1rmByExercise = mapOf("Bench" to e1rmPoints(100.0, 100.2, 100.1, 99.9)),
            ),
        )
        val s = TrainingPlateauDetector().detect(ctx)!!
        assertEquals(SignalKind.TRAINING_PLATEAU, s.kind)
        assertEquals(SignalTier.P1, s.tier)
        assertEquals("Bench", s.facts.values["exercise"])
        assertTrue(s.dedupKey.startsWith("TRAINING_PLATEAU|Bench|2026-W27"))
    }

    @Test
    fun `training plateau does NOT fire when e1RM is climbing`() {
        val ctx = CoachContextFixtures.context(
            training = CoachContextFixtures.training(
                e1rmByExercise = mapOf("Bench" to e1rmPoints(95.0, 98.0, 101.0, 104.0)),
            ),
        )
        assertNull(TrainingPlateauDetector().detect(ctx))
    }

    // ── NewPrDetector ──────────────────────────────────────────────────────────

    @Test
    fun `new PR fires when latest e1RM beats prior best by margin`() {
        val ctx = CoachContextFixtures.context(
            training = CoachContextFixtures.training(
                e1rmByExercise = mapOf("Bench" to e1rmPoints(96.0, 99.0, 98.0, 102.0)),
            ),
        )
        val s = NewPrDetector().detect(ctx)!!
        assertEquals(SignalKind.NEW_PR, s.kind)
        assertEquals(SignalTier.P0, s.tier)
        assertEquals("102.0", s.facts.values["e1rmKg"])
        assertEquals("99.0", s.facts.values["priorBestKg"])
        assertTrue(s.dedupKey.startsWith("NEW_PR|Bench"))
        // Date-stamped so the same PR is one event, not a recurring "new PR" after cooldown.
        assertTrue(s.dedupKey.contains("date="))
    }

    @Test
    fun `new PR does NOT fire when the PR was set more than a week ago (stale)`() {
        // Latest point is 10 days old — an all-time max, but not news today.
        val ctx = CoachContextFixtures.context(
            training = CoachContextFixtures.training(
                e1rmByExercise = mapOf(
                    "Bench" to CoachContextFixtures.e1rmPoints(
                        96.0, 99.0, 98.0, 102.0,
                        end = CoachContextFixtures.TODAY.minusDays(10),
                    ),
                ),
            ),
        )
        assertNull(NewPrDetector().detect(ctx))
    }

    @Test
    fun `new PR does NOT fire when latest is within noise of prior best`() {
        val ctx = CoachContextFixtures.context(
            training = CoachContextFixtures.training(
                e1rmByExercise = mapOf("Bench" to e1rmPoints(99.0, 100.0, 100.5)),
            ),
        )
        assertNull(NewPrDetector().detect(ctx))
    }

    // ── StepStreakAtRiskDetector ───────────────────────────────────────────────

    @Test
    fun `step streak at risk fires when a live streak's today is not yet logged`() {
        // Goal 8000. Previous 10 days all hit; today (TODAY) is under goal.
        val goal = 8000
        val steps = buildMap {
            (1..10).forEach { put(TODAY.minusDays(it.toLong()), 9000) }
            put(TODAY, 1200) // today under goal
        }
        val ctx = CoachContextFixtures.context(
            profile = ProfileContext(dailyStepGoal = goal),
            body = CoachContextFixtures.body(stepsByDate = steps),
        )
        val s = StepStreakAtRiskDetector().detect(ctx)!!
        assertEquals(SignalKind.STEP_STREAK_AT_RISK, s.kind)
        assertEquals(SignalTier.P2, s.tier)
        assertEquals(CoachActionType.LOG_STEPS, s.action.type)
        assertEquals("8000", s.facts.values["stepGoal"])
        assertTrue(s.dedupKey.startsWith("STEP_STREAK_AT_RISK|"))
    }

    @Test
    fun `step streak at risk does NOT fire once today already qualifies`() {
        val goal = 8000
        val steps = buildMap {
            (1..10).forEach { put(TODAY.minusDays(it.toLong()), 9000) }
            put(TODAY, 9500) // today already hit
        }
        val ctx = CoachContextFixtures.context(
            profile = ProfileContext(dailyStepGoal = goal),
            body = CoachContextFixtures.body(stepsByDate = steps),
        )
        assertNull(StepStreakAtRiskDetector().detect(ctx))
    }

    @Test
    fun `step streak at risk does NOT fire without a live streak`() {
        val goal = 8000
        // Only sporadic days hit, streak already broken.
        val steps = mapOf(
            TODAY.minusDays(6L) to 9000,
            TODAY.minusDays(2L) to 1000,
            TODAY to 1000,
        )
        val ctx = CoachContextFixtures.context(
            profile = ProfileContext(dailyStepGoal = goal),
            body = CoachContextFixtures.body(stepsByDate = steps),
        )
        assertNull(StepStreakAtRiskDetector().detect(ctx))
    }
}
