package com.zack.recomptracker.domain.coach

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.Serializable

/**
 * The Phase-5 push-restraint gate. Pure Kotlin (no Android/LLM/network dependency, per the
 * `domain/coach` rule), fully deterministic: `now` and the push history are injected so behaviour is
 * fixed in time and exhaustively unit-testable. See `docs/ai-redesign/08-technical-architecture.md`
 * §9 ("Restraint caps") and §11 ("Notification architecture").
 *
 * Restraint caps enforced (ALL of them, in this precedence order):
 * 1. **Quiet hours** — reject if `now`'s time-of-day falls inside [QuietHours] (default 22:00–07:00,
 *    wrapping midnight). Checked first so a nudge never fires while the user is asleep, whatever else.
 * 2. **Tier gate** — only a P0 signal *or* the weekly check-in may push; any other candidate is rejected.
 * 3. **One-push-a-day rule** — reject if a push was already emitted today (no same-day doubles) or
 *    yesterday (never on consecutive days). Net effect: ≤1 push/day and never on adjacent days.
 * 4. **Celebration cap** — ≤1 *ambient* celebration push per rolling 7-day window; the weekly
 *    check-in is exempt (it's the guaranteed spine push and doesn't consume the celebration budget).
 * 5. **Weekly cap** — ≤2 pushes per rolling 7-day window.
 *
 * The rolling window is "within the last 7 days of `now`": a [PushEvent] counts when its timestamp is
 * `>= now.minusDays(7)` (the 7-days-ago boundary is inclusive). When several caps apply the first one
 * in the order above is returned; the [PushDecision.reason] tells callers/tests exactly *why*.
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 */
class RateLimiter {

    fun decide(
        candidate: PushCandidate,
        now: LocalDateTime,
        recentPushes: List<PushEvent>,
        quietHours: QuietHours = QuietHours(),
    ): PushDecision {
        // 1. Quiet hours — before anything else, never fire while the user is likely asleep.
        if (quietHours.contains(now.toLocalTime())) {
            return PushDecision(false, PushRejectionReason.QUIET_HOURS)
        }

        // 2. Tier gate — only a P0 signal or the weekly check-in earns the right to push at all.
        if (candidate.tier != SignalTier.P0 && !candidate.isWeeklyCheckIn) {
            return PushDecision(false, PushRejectionReason.NOT_ELIGIBLE_TIER)
        }

        // 3. One-push-a-day rule — reject if a push already landed today (no same-day doubles, e.g. a
        // daily P0 then the weekly push in one run) or yesterday (never on consecutive days). A weekly
        // push deferred by this rule isn't stamped, so it simply retries on the next eligible run.
        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)
        if (recentPushes.any { it.date == today || it.date == yesterday }) {
            return PushDecision(false, PushRejectionReason.CONSECUTIVE_DAY)
        }

        // Everything below is scoped to the rolling 7-day window ending at `now` (boundary inclusive).
        val windowStart = now.minusDays(WINDOW_DAYS)
        val inWindow = recentPushes.filter { !it.timestamp.isBefore(windowStart) }

        // 4. Celebration cap — at most one *ambient* celebration push per rolling week. The weekly
        // check-in is exempt: it's the guaranteed spine push (a RECOMP_WIN weekly winner is a
        // celebration), so it may fire even when an ambient celebration already went out this week.
        if (candidate.isCelebration && !candidate.isWeeklyCheckIn &&
            inWindow.count { it.isCelebration } >= MAX_CELEBRATIONS_PER_WINDOW
        ) {
            return PushDecision(false, PushRejectionReason.CELEBRATION_CAP_REACHED)
        }

        // 5. Weekly cap — at most two pushes per rolling week.
        if (inWindow.size >= MAX_PUSHES_PER_WINDOW) {
            return PushDecision(false, PushRejectionReason.WEEKLY_CAP_REACHED)
        }

        return PushDecision(true, PushRejectionReason.ELIGIBLE)
    }

    companion object {
        /** The rolling window length. An event exactly [WINDOW_DAYS] ago is inside the window. */
        const val WINDOW_DAYS: Long = 7

        /** Hard cap on total pushes per rolling window. */
        const val MAX_PUSHES_PER_WINDOW: Int = 2

        /** Hard cap on celebration pushes per rolling window. */
        const val MAX_CELEBRATIONS_PER_WINDOW: Int = 1
    }
}

/**
 * A staged push awaiting the restraint gate. Carries only what the caps need: its [tier], whether it
 * is a celebration ([CoachSignal.isCelebration]-style — a `NEW_PR`/`RECOMP_WIN` win) and whether it is
 * the weekly check-in (which may push at any tier). Pure; built by the digest coordinator from a
 * [CoachSignal].
 */
data class PushCandidate(
    val tier: SignalTier,
    val isCelebration: Boolean,
    val isWeeklyCheckIn: Boolean,
)

/**
 * A push that was actually emitted — the ledger unit the [RateLimiter] measures its rolling caps
 * against. Persisted by the push-history store, so it is [Serializable]. [timestamp] is stored as a
 * `LocalDateTime`; [date] is derived for the consecutive-day comparison.
 */
@Serializable
data class PushEvent(
    /** ISO-8601 local date-time the push was emitted (e.g. `2026-07-01T13:00`). */
    @Serializable(with = LocalDateTimeIsoSerializer::class)
    val timestamp: LocalDateTime,
    val isCelebration: Boolean,
) {
    /** The calendar day the push landed on — the granularity of the consecutive-day rule. */
    val date: LocalDate get() = timestamp.toLocalDate()
}

/**
 * The quiet window during which pushes are suppressed, as a time-of-day range. Defaults to the
 * spec's 22:00–07:00, which wraps midnight. [start] is inclusive, [end] is exclusive, so 22:00 sharp
 * is quiet and 07:00 sharp is the first allowed minute. A window where `start < end` (e.g. 09:00–17:00)
 * is treated as a same-day range; `start >= end` wraps past midnight.
 */
data class QuietHours(
    val start: LocalTime = LocalTime.of(22, 0),
    val end: LocalTime = LocalTime.of(7, 0),
) {
    /** True when [time] falls inside the quiet window (start inclusive, end exclusive). */
    fun contains(time: LocalTime): Boolean =
        if (start < end) {
            time >= start && time < end
        } else {
            // Wrapping window: quiet is [start, midnight) ∪ [midnight, end).
            time >= start || time < end
        }
}

/** The outcome of a [RateLimiter.decide] call: whether the push is allowed and the deciding [reason]. */
data class PushDecision(
    val allowed: Boolean,
    val reason: PushRejectionReason,
)

/** Why a push was allowed ([ELIGIBLE]) or which cap rejected it. Exposed so tests can assert the cause. */
enum class PushRejectionReason {
    ELIGIBLE,
    QUIET_HOURS,
    NOT_ELIGIBLE_TIER,
    CONSECUTIVE_DAY,
    CELEBRATION_CAP_REACHED,
    WEEKLY_CAP_REACHED,
}
