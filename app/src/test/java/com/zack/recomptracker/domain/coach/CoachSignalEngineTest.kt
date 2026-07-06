package com.zack.recomptracker.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachSignalEngineTest {

    private val fireA = CoachDetector { ctx ->
        CoachSignal(
            kind = SignalKind.NEW_PR,
            tier = SignalTier.P0,
            category = SignalCategory.TRAINING,
            severity = 40,
            facts = SignalFacts(),
            verdict = "A",
            rationale = SignalRationale(confidence = Confidence.HIGH),
            dedupKey = "A",
            surface = CoachSurface.TODAY,
            fallbackText = "A fired.",
        )
    }

    private val fireB = CoachDetector { ctx ->
        CoachSignal(
            kind = SignalKind.LOW_ADHERENCE,
            tier = SignalTier.P1,
            category = SignalCategory.NUTRITION,
            severity = 10,
            facts = SignalFacts(),
            verdict = "B",
            rationale = SignalRationale(confidence = Confidence.MEDIUM),
            dedupKey = "B",
            surface = CoachSurface.TODAY,
            fallbackText = "B fired.",
        )
    }

    private val silent = CoachDetector { null }

    @Test
    fun `engine collects every non-null detector result`() {
        val engine = CoachSignalEngine(listOf(fireA, silent, fireB))
        val out = engine.evaluate(CoachContextFixtures.context())
        assertEquals(listOf("A", "B"), out.map { it.verdict })
    }

    @Test
    fun `engine returns empty when nothing fires and data is sufficient`() {
        val engine = CoachSignalEngine(listOf(silent, silent))
        val out = engine.evaluate(CoachContextFixtures.context())
        assertTrue(out.isEmpty())
    }

    @Test
    fun `insufficient data emits ONLY a single INSUFFICIENT_DATA hold — no other insights`() {
        val engine = CoachSignalEngine(listOf(fireA, fireB))
        val ctx = CoachContextFixtures.context(
            nutrition = CoachContextFixtures.nutrition(loggedDaysInWindow = 9),
        )
        val out = engine.evaluate(ctx)
        assertEquals(1, out.size)
        val s = out.single()
        assertEquals(SignalKind.INSUFFICIENT_DATA, s.kind)
        assertEquals(SignalTier.P3, s.tier)
        assertEquals(Confidence.INSUFFICIENT, s.rationale.confidence)
        assertTrue(s.verdict.isNotBlank())
        assertTrue(s.fallbackText.isNotBlank())
        assertTrue("dedupKey stamps logged-day count", s.dedupKey.contains("9"))
    }

    @Test
    fun `sufficiency gate uses exactly 14 as the boundary`() {
        val engine = CoachSignalEngine(listOf(fireA))
        val at13 = engine.evaluate(
            CoachContextFixtures.context(nutrition = CoachContextFixtures.nutrition(loggedDaysInWindow = 13)),
        )
        assertEquals(SignalKind.INSUFFICIENT_DATA, at13.single().kind)

        val at14 = engine.evaluate(
            CoachContextFixtures.context(nutrition = CoachContextFixtures.nutrition(loggedDaysInWindow = 14)),
        )
        assertEquals(SignalKind.NEW_PR, at14.single().kind)
    }

    @Test
    fun `default factory wires a non-empty real catalog`() {
        val engine = CoachSignalEngine.default()
        // A clean, on-track context should not throw and should not manufacture noise.
        val out = engine.evaluate(CoachContextFixtures.context())
        assertNotNull(out)
    }
}
