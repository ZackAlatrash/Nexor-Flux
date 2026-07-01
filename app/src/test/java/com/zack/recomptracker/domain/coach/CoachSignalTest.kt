package com.zack.recomptracker.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachSignalTest {

    private fun signal(
        verdict: String = "Hold calories this week.",
        fallbackText: String = "Weight is flat and waist is down — keep going.",
        severity: Int = 50,
        dedupKey: String = "RECOMP_WIN|2026-W27",
        kind: SignalKind = SignalKind.RECOMP_WIN,
    ) = CoachSignal(
        kind = kind,
        tier = SignalTier.P1,
        category = SignalCategory.BODY,
        severity = severity,
        facts = SignalFacts(mapOf("weightTrendKgPerWk" to "-0.02")),
        verdict = verdict,
        rationale = SignalRationale(confidence = Confidence.HIGH),
        dedupKey = dedupKey,
        surface = CoachSurface.WEEKLY,
        fallbackText = fallbackText,
    )

    @Test
    fun `a valid signal is constructed`() {
        val s = signal()
        assertEquals(SignalKind.RECOMP_WIN, s.kind)
        assertEquals(CoachAction.None, s.action)
    }

    @Test
    fun `blank verdict is rejected — every signal must carry a decision`() {
        assertThrows(IllegalArgumentException::class.java) { signal(verdict = "  ") }
    }

    @Test
    fun `blank fallback text is rejected — every signal must render without the LLM`() {
        assertThrows(IllegalArgumentException::class.java) { signal(fallbackText = "") }
    }

    @Test
    fun `severity outside 0 to 100 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { signal(severity = -1) }
        assertThrows(IllegalArgumentException::class.java) { signal(severity = 101) }
    }

    @Test
    fun `blank dedup key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { signal(dedupKey = "") }
    }

    @Test
    fun `NEW_PR is a celebration`() {
        assertTrue(signal(kind = SignalKind.NEW_PR).isCelebration)
    }

    @Test
    fun `RECOMP_WIN is a celebration`() {
        assertTrue(signal(kind = SignalKind.RECOMP_WIN).isCelebration)
    }

    @Test
    fun `a non-celebration kind is not a celebration`() {
        assertFalse(signal(kind = SignalKind.LOW_ADHERENCE).isCelebration)
        assertFalse(signal(kind = SignalKind.WEEKLY_VERDICT).isCelebration)
    }
}
