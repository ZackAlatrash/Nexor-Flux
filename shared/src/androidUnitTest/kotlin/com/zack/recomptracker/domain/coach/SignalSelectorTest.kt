package com.zack.recomptracker.domain.coach

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic rank -> dedup -> cooldown -> one-winner selection
 * (docs/ai-redesign/08-technical-architecture.md §1, §9). Pure Kotlin, real objects, no mocks.
 */
class SignalSelectorTest {

    private val today: LocalDate = LocalDate(2026, 7, 1)
    private val selector = SignalSelector()

    /** Minimal real [CoachSignal]; only the fields the selector reads are meaningful. */
    private fun signal(
        kind: SignalKind,
        tier: SignalTier,
        severity: Int,
        dedupKey: String = "$kind|k",
        surface: CoachSurface = CoachSurface.TODAY,
    ) = CoachSignal(
        kind = kind,
        tier = tier,
        category = SignalCategory.BODY,
        severity = severity,
        facts = SignalFacts(),
        verdict = "Do X.",
        rationale = SignalRationale(confidence = Confidence.HIGH),
        dedupKey = dedupKey,
        surface = surface,
        fallbackText = "Fallback for $kind.",
    )

    @Test
    fun `P0 outranks a higher-severity P2`() {
        val p0 = signal(SignalKind.FAT_GAIN_WARNING, SignalTier.P0, severity = 10, dedupKey = "a")
        val p2 = signal(SignalKind.LOW_ADHERENCE, SignalTier.P2, severity = 99, dedupKey = "b")

        val result = selector.select(listOf(p2, p0), emptyMap(), today)

        assertEquals(p0, result.winner)
        assertEquals(listOf(p0, p2), result.ranked)
    }

    @Test
    fun `a P1 daily signal beats a higher-severity P2 on the same day`() {
        // Q9 tier budget: Morning Readiness (P1) outranks Consistency/Scale Check (P2) even when the
        // P2 is more severe, because tier is the primary sort key.
        val p1 = signal(SignalKind.MORNING_READINESS, SignalTier.P1, severity = 20, dedupKey = "a")
        val p2 = signal(SignalKind.CONSISTENCY_CHECK_IN, SignalTier.P2, severity = 95, dedupKey = "b")

        val result = selector.select(listOf(p2, p1), emptyMap(), today)

        assertEquals(p1, result.winner)
        assertEquals(listOf(p1, p2), result.ranked)
    }

    @Test
    fun `higher severity wins within the P2 tier`() {
        val strongP2 = signal(SignalKind.SCALE_CHECK, SignalTier.P2, severity = 80, dedupKey = "a")
        val weakP2 = signal(SignalKind.CONSISTENCY_CHECK_IN, SignalTier.P2, severity = 20, dedupKey = "b")

        val result = selector.select(listOf(weakP2, strongP2), emptyMap(), today)

        assertEquals(strongP2, result.winner)
        assertEquals(listOf(strongP2, weakP2), result.ranked)
    }

    @Test
    fun `severity breaks ties within a tier`() {
        val low = signal(SignalKind.LOW_ADHERENCE, SignalTier.P1, severity = 20, dedupKey = "a")
        val high = signal(SignalKind.RECOVERY_DECLINE, SignalTier.P1, severity = 80, dedupKey = "b")

        val result = selector.select(listOf(low, high), emptyMap(), today)

        assertEquals(high, result.winner)
        assertEquals(listOf(high, low), result.ranked)
    }

    @Test
    fun `kind ordinal is the final stable tiebreak`() {
        // Same tier, same severity -> the smaller kind.ordinal wins. RECOMP_WIN (0) < FAT_GAIN_WARNING (1).
        val fatGain = signal(SignalKind.FAT_GAIN_WARNING, SignalTier.P1, severity = 50, dedupKey = "a")
        val recompWin = signal(SignalKind.RECOMP_WIN, SignalTier.P1, severity = 50, dedupKey = "b")

        val result = selector.select(listOf(fatGain, recompWin), emptyMap(), today)

        assertEquals(recompWin, result.winner)
        assertEquals(listOf(recompWin, fatGain), result.ranked)
    }

    @Test
    fun `same dedupKey duplicates collapse to the best-ranked one`() {
        val weak = signal(SignalKind.LOW_ADHERENCE, SignalTier.P2, severity = 10, dedupKey = "dup")
        val strong = signal(SignalKind.LOW_ADHERENCE, SignalTier.P0, severity = 10, dedupKey = "dup")

        val result = selector.select(listOf(weak, strong), emptyMap(), today)

        assertEquals(strong, result.winner)
        assertEquals(listOf(strong), result.ranked)
        assertEquals(listOf(weak), result.suppressed)
    }

    @Test
    fun `a signal within cooldown is suppressed and cannot win`() {
        val recent = signal(SignalKind.LOW_ADHERENCE, SignalTier.P0, severity = 90, dedupKey = "cool")
        // Surfaced 3 days ago; cooldown is 7 days -> still on cooldown.
        val seen = mapOf("cool" to today.minus(3, DateTimeUnit.DAY))

        val result = selector.select(listOf(recent), seen, today, cooldownDays = 7)

        assertNull(result.winner)
        assertTrue(result.ranked.isEmpty())
        assertEquals(listOf(recent), result.suppressed)
    }

    @Test
    fun `a signal past cooldown is eligible`() {
        val s = signal(SignalKind.LOW_ADHERENCE, SignalTier.P1, severity = 50, dedupKey = "cool")
        // Surfaced exactly 7 days ago; 7 >= cooldownDays -> eligible again.
        val seen = mapOf("cool" to today.minus(7, DateTimeUnit.DAY))

        val result = selector.select(listOf(s), seen, today, cooldownDays = 7)

        assertEquals(s, result.winner)
        assertEquals(listOf(s), result.ranked)
    }

    @Test
    fun `empty input yields no winner`() {
        val result = selector.select(emptyList(), emptyMap(), today)

        assertNull(result.winner)
        assertTrue(result.ranked.isEmpty())
        assertTrue(result.suppressed.isEmpty())
    }

    @Test
    fun `all-suppressed yields no winner`() {
        val a = signal(SignalKind.LOW_ADHERENCE, SignalTier.P0, severity = 90, dedupKey = "a")
        val b = signal(SignalKind.RECOVERY_DECLINE, SignalTier.P1, severity = 40, dedupKey = "b")
        val seen = mapOf("a" to today.minus(1, DateTimeUnit.DAY), "b" to today.minus(2, DateTimeUnit.DAY))

        val result = selector.select(listOf(a, b), seen, today, cooldownDays = 7)

        assertNull(result.winner)
        assertTrue(result.ranked.isEmpty())
        assertEquals(setOf(a, b), result.suppressed.toSet())
    }

    @Test
    fun `ranked preserves losers so callers can demote them`() {
        val p0 = signal(SignalKind.FAT_GAIN_WARNING, SignalTier.P0, severity = 50, dedupKey = "a")
        val p1 = signal(SignalKind.RECOVERY_DECLINE, SignalTier.P1, severity = 50, dedupKey = "b")
        val p3 = signal(SignalKind.QUIET_WEIGH_INS, SignalTier.P3, severity = 50, dedupKey = "c")

        val result = selector.select(listOf(p3, p1, p0), emptyMap(), today)

        assertEquals(p0, result.winner)
        assertEquals(listOf(p0, p1, p3), result.ranked)
    }

    @Test
    fun `selectForSurface only considers matching signals`() {
        val push = signal(SignalKind.FAT_GAIN_WARNING, SignalTier.P0, severity = 90, dedupKey = "a", surface = CoachSurface.PUSH)
        val today0 = signal(SignalKind.LOW_ADHERENCE, SignalTier.P2, severity = 20, dedupKey = "b", surface = CoachSurface.TODAY)

        val result = selector.selectForSurface(CoachSurface.TODAY, listOf(push, today0), emptyMap(), today)

        assertEquals(today0, result.winner)
        assertEquals(listOf(today0), result.ranked)
    }
}
