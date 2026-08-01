package com.zack.recomptracker.domain.coach

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive, deterministic coverage of the Phase-5 restraint caps enforced by [RateLimiter]
 * (docs/ai-redesign/08-technical-architecture.md §9 "Restraint caps" + §11). `now` and the push
 * history are injected so every case is fixed in time — the limiter never reads the wall clock.
 *
 * The rolling window is "within the last 7 days of `now`": an event counts when its timestamp is
 * `>= now.minusDays(7)` (boundary inclusive). Every cap is asserted independently.
 */
class RateLimiterTest {

    private val limiter = RateLimiter()

    // 2026-07-01 is a Wednesday; 13:00 is safely outside the default 22:00–07:00 quiet window.
    private val now: LocalDateTime = LocalDateTime(2026, 7, 1, 13, 0)

    private fun p0(isCelebration: Boolean = false, isWeeklyCheckIn: Boolean = false) =
        PushCandidate(tier = SignalTier.P0, isCelebration = isCelebration, isWeeklyCheckIn = isWeeklyCheckIn)

    private fun event(daysAgo: Long, hour: Int = 13, isCelebration: Boolean = false) =
        PushEvent(
            timestamp = LocalDateTime(now.date.minus(daysAgo, DateTimeUnit.DAY), LocalTime(hour, 0)),
            isCelebration = isCelebration,
        )

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    fun `eligible P0 with empty history at a normal hour is allowed`() {
        val decision = limiter.decide(p0(), now, recentPushes = emptyList())
        assertTrue(decision.allowed)
        assertEquals(PushRejectionReason.ELIGIBLE, decision.reason)
    }

    // ── Cap 2: tier gate (only P0 or the weekly check-in may push) ───────────────

    @Test
    fun `P1 non-weekly candidate is rejected on tier`() {
        val candidate = PushCandidate(SignalTier.P1, isCelebration = false, isWeeklyCheckIn = false)
        val decision = limiter.decide(candidate, now, emptyList())
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.NOT_ELIGIBLE_TIER, decision.reason)
    }

    @Test
    fun `P2 and P3 non-weekly candidates are rejected on tier`() {
        for (tier in listOf(SignalTier.P2, SignalTier.P3)) {
            val decision = limiter.decide(
                PushCandidate(tier, isCelebration = false, isWeeklyCheckIn = false), now, emptyList(),
            )
            assertFalse("tier=$tier should be rejected", decision.allowed)
            assertEquals(PushRejectionReason.NOT_ELIGIBLE_TIER, decision.reason)
        }
    }

    @Test
    fun `weekly check-in is allowed even when tier is not P0`() {
        val candidate = PushCandidate(SignalTier.P3, isCelebration = false, isWeeklyCheckIn = true)
        val decision = limiter.decide(candidate, now, emptyList())
        assertTrue(decision.allowed)
        assertEquals(PushRejectionReason.ELIGIBLE, decision.reason)
    }

    // ── Cap 1: ≤2 pushes per rolling 7-day window ────────────────────────────────

    @Test
    fun `second push in the week is allowed`() {
        // One prior push 3 days ago; yesterday is clear so the consecutive-day rule doesn't fire.
        val decision = limiter.decide(p0(), now, recentPushes = listOf(event(daysAgo = 3)))
        assertTrue(decision.allowed)
        assertEquals(PushRejectionReason.ELIGIBLE, decision.reason)
    }

    @Test
    fun `third push in the week is rejected on the weekly cap`() {
        val history = listOf(event(daysAgo = 5), event(daysAgo = 3))
        val decision = limiter.decide(p0(), now, recentPushes = history)
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.WEEKLY_CAP_REACHED, decision.reason)
    }

    @Test
    fun `pushes older than the 7-day window do not count toward the weekly cap`() {
        // Two pushes just outside the window (8 and 9 days ago) plus one inside → still under cap.
        val history = listOf(event(daysAgo = 9), event(daysAgo = 8), event(daysAgo = 3))
        val decision = limiter.decide(p0(), now, recentPushes = history)
        assertTrue(decision.allowed)
        assertEquals(PushRejectionReason.ELIGIBLE, decision.reason)
    }

    @Test
    fun `an event exactly 7 days ago is inside the window (boundary inclusive)`() {
        // Exactly-7-days-ago pushes count; two of them fills the weekly cap.
        val onBoundary =
            PushEvent(timestamp = LocalDateTime(now.date.minus(7, DateTimeUnit.DAY), now.time), isCelebration = false)
        val history = listOf(onBoundary, event(daysAgo = 3))
        val decision = limiter.decide(p0(), now, recentPushes = history)
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.WEEKLY_CAP_REACHED, decision.reason)
    }

    // ── Cap 3: never on consecutive days ─────────────────────────────────────────

    @Test
    fun `a push emitted yesterday rejects today on the consecutive-day rule`() {
        val decision = limiter.decide(p0(), now, recentPushes = listOf(event(daysAgo = 1)))
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.CONSECUTIVE_DAY, decision.reason)
    }

    @Test
    fun `a push two days ago does not trigger the consecutive-day rule`() {
        val decision = limiter.decide(p0(), now, recentPushes = listOf(event(daysAgo = 2)))
        assertTrue(decision.allowed)
        assertEquals(PushRejectionReason.ELIGIBLE, decision.reason)
    }

    @Test
    fun `a push earlier today blocks a second push the same day`() {
        // A daily P0 already fired this morning; a later push (e.g. the weekly one) must not stack.
        val decision = limiter.decide(p0(), now, recentPushes = listOf(event(daysAgo = 0, hour = 9)))
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.CONSECUTIVE_DAY, decision.reason)
    }

    @Test
    fun `the same-day rule also blocks a weekly check-in when a push already fired today`() {
        // The weekly push is deferred (not stamped) so it retries next run; today it must stay silent.
        val weekly = PushCandidate(SignalTier.P0, isCelebration = true, isWeeklyCheckIn = true)
        val decision = limiter.decide(weekly, now, recentPushes = listOf(event(daysAgo = 0, hour = 9)))
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.CONSECUTIVE_DAY, decision.reason)
    }

    // ── Cap 4: ≤1 celebration per rolling 7-day window ───────────────────────────

    @Test
    fun `a second celebration in the week is rejected on the celebration cap`() {
        val history = listOf(event(daysAgo = 3, isCelebration = true))
        val decision = limiter.decide(p0(isCelebration = true), now, recentPushes = history)
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.CELEBRATION_CAP_REACHED, decision.reason)
    }

    @Test
    fun `a non-celebration P0 is still allowed when a celebration already fired this week`() {
        val history = listOf(event(daysAgo = 3, isCelebration = true))
        val decision = limiter.decide(p0(isCelebration = false), now, recentPushes = history)
        assertTrue(decision.allowed)
        assertEquals(PushRejectionReason.ELIGIBLE, decision.reason)
    }

    @Test
    fun `a celebration older than the window does not block a new celebration`() {
        val history = listOf(event(daysAgo = 9, isCelebration = true))
        val decision = limiter.decide(p0(isCelebration = true), now, recentPushes = history)
        assertTrue(decision.allowed)
        assertEquals(PushRejectionReason.ELIGIBLE, decision.reason)
    }

    @Test
    fun `a weekly check-in celebration is exempt from the celebration cap`() {
        // The weekly report is the guaranteed spine push — a prior ambient celebration this week
        // must NOT block it (a RECOMP_WIN weekly winner is a celebration).
        val history = listOf(event(daysAgo = 3, isCelebration = true))
        val weekly = PushCandidate(SignalTier.P0, isCelebration = true, isWeeklyCheckIn = true)
        val decision = limiter.decide(weekly, now, recentPushes = history)
        assertTrue(decision.allowed)
        assertEquals(PushRejectionReason.ELIGIBLE, decision.reason)
    }

    // ── Cap 5: quiet hours (default 22:00–07:00, wrapping midnight) ───────────────

    @Test
    fun `2330 is inside the default quiet window and is rejected`() {
        val late = LocalDateTime(2026, 7, 1, 23, 30)
        val decision = limiter.decide(p0(), late, emptyList())
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.QUIET_HOURS, decision.reason)
    }

    @Test
    fun `0630 is inside the default quiet window and is rejected`() {
        val early = LocalDateTime(2026, 7, 1, 6, 30)
        val decision = limiter.decide(p0(), early, emptyList())
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.QUIET_HOURS, decision.reason)
    }

    @Test
    fun `1200 is outside the default quiet window and is allowed`() {
        val noon = LocalDateTime(2026, 7, 1, 12, 0)
        val decision = limiter.decide(p0(), noon, emptyList())
        assertTrue(decision.allowed)
        assertEquals(PushRejectionReason.ELIGIBLE, decision.reason)
    }

    @Test
    fun `quiet-window start is inclusive and end is exclusive`() {
        // 22:00 sharp is quiet; 07:00 sharp is the first allowed minute.
        assertFalse(limiter.decide(p0(), LocalDateTime(2026, 7, 1, 22, 0), emptyList()).allowed)
        assertTrue(limiter.decide(p0(), LocalDateTime(2026, 7, 1, 7, 0), emptyList()).allowed)
    }

    @Test
    fun `quiet hours are configurable`() {
        val quiet = QuietHours(start = LocalTime(9, 0), end = LocalTime(17, 0))
        // A non-wrapping daytime window: 13:00 now falls inside it → rejected.
        val decision = limiter.decide(p0(), now, emptyList(), quietHours = quiet)
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.QUIET_HOURS, decision.reason)
        // 20:00 is outside the daytime window → allowed.
        val evening = limiter.decide(p0(), LocalDateTime(2026, 7, 1, 20, 0), emptyList(), quietHours = quiet)
        assertTrue(evening.allowed)
    }

    // ── Precedence: quiet hours is checked first ─────────────────────────────────

    @Test
    fun `quiet hours takes precedence over the tier gate`() {
        val candidate = PushCandidate(SignalTier.P2, isCelebration = false, isWeeklyCheckIn = false)
        val late = LocalDateTime(2026, 7, 1, 23, 30)
        val decision = limiter.decide(candidate, late, emptyList())
        assertFalse(decision.allowed)
        assertEquals(PushRejectionReason.QUIET_HOURS, decision.reason)
    }

    @Test
    fun `QuietHours contains handles a non-wrapping window`() {
        val window = QuietHours(start = LocalTime(9, 0), end = LocalTime(17, 0))
        assertTrue(window.contains(LocalTime(12, 0)))
        assertFalse(window.contains(LocalTime(8, 0)))
        assertFalse(window.contains(LocalTime(17, 0)))
    }

    @Test
    fun `QuietHours contains handles the midnight-wrapping default`() {
        val window = QuietHours()
        assertTrue(window.contains(LocalTime(23, 30)))
        assertTrue(window.contains(LocalTime(0, 30)))
        assertTrue(window.contains(LocalTime(6, 30)))
        assertTrue(window.contains(LocalTime(22, 0)))
        assertFalse(window.contains(LocalTime(7, 0)))
        assertFalse(window.contains(LocalTime(12, 0)))
    }

    @Test
    fun `PushEvent exposes its date for the consecutive-day comparison`() {
        val e = PushEvent(timestamp = LocalDateTime(2026, 6, 30, 13, 0), isCelebration = false)
        assertEquals(LocalDate(2026, 6, 30), e.date)
    }
}
