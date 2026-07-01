package com.zack.recomptracker.data.coach

import android.util.Log
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.PushCandidate
import com.zack.recomptracker.domain.coach.PushEvent
import com.zack.recomptracker.domain.coach.RateLimiter
import com.zack.recomptracker.domain.coach.isCelebration
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first

/**
 * The Phase-5 push emit seam — the one place that decides whether a staged [CoachSignal] becomes an
 * actual notification, and the only piece of the push path that is fully unit-testable. It contains no
 * Android calls: every collaborator (the [CoachNotifier] interface, the [PushHistory] ledger, the
 * [CoachNotifierPreferences], the [RateLimiter], and the `now` clock) is injected, so the whole
 * decision is deterministic under a fake.
 *
 * The pipeline for [emit] is, in order (matching `docs/ai-redesign/08-technical-architecture.md` §11):
 * 1. Build a [PushCandidate] from the signal (tier, celebration, weekly-check-in).
 * 2. **Preference gate** — a weekly push needs [CoachNotifierPreferences.weeklyCheckInPushEnabled];
 *    everything else (P0 + celebrations) needs [CoachNotifierPreferences.ambientNudgesEnabled]. Off → no push.
 * 3. **Restraint gate** — [RateLimiter.decide] over the push history + quiet hours (≤2/wk, P0-or-weekly,
 *    never consecutive days, ≤1 celebration/wk, quiet hours).
 * 4. **Decision-attached gate** — [CoachPushPayload.from] refuses a payload lacking a verdict + action.
 * 5. If all pass → [CoachNotifier.show]; only when the platform actually posts it do we
 *    [PushHistory.record] the event (so a denied/failed post doesn't burn a weekly slot).
 *
 * Silence is normal: any gate failing returns `false` and touches neither the notifier nor the ledger.
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 */
class CoachPushEmitter(
    private val notifier: CoachNotifier,
    private val pushHistory: PushHistory,
    private val preferences: CoachNotifierPreferences,
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val now: () -> LocalDateTime,
) {

    /**
     * Consider [signal] for a push. [isWeeklyCheckIn] routes it to the weekly channel/pref and lets it
     * clear the RateLimiter's tier gate regardless of tier. Returns `true` iff a notification was shown
     * (and thus recorded). Never throws — failures are logged and treated as silence.
     */
    suspend fun emit(signal: CoachSignal, isWeeklyCheckIn: Boolean): Boolean =
        runCatching { emitOrThrow(signal, isWeeklyCheckIn) }
            .onFailure { Log.w(TAG, "Coach push emit failed", it) }
            .getOrDefault(false)

    private suspend fun emitOrThrow(signal: CoachSignal, isWeeklyCheckIn: Boolean): Boolean {
        val candidate = PushCandidate(
            tier = signal.tier,
            isCelebration = signal.isCelebration,
            isWeeklyCheckIn = isWeeklyCheckIn,
        )

        // 2. Preference gate.
        val prefAllows =
            if (isWeeklyCheckIn) preferences.weeklyCheckInPushEnabled.first()
            else preferences.ambientNudgesEnabled.first()
        if (!prefAllows) return false

        // 3. Restraint gate.
        val at = now()
        val decision = rateLimiter.decide(
            candidate = candidate,
            now = at,
            recentPushes = pushHistory.recentPushes(),
            quietHours = preferences.quietHours(),
        )
        if (!decision.allowed) return false

        // 4. Decision-attached gate — build the payload for the correct channel or bail.
        val channel = if (isWeeklyCheckIn) CoachPushChannel.WEEKLY_CHECK_IN else CoachPushChannel.COACHING
        val payload = CoachPushPayload.from(signal, channel) ?: return false

        // 5. Show, then record only what actually went out.
        if (!notifier.show(payload)) return false
        pushHistory.record(PushEvent(timestamp = at, isCelebration = candidate.isCelebration))
        return true
    }

    private companion object {
        const val TAG = "CoachPushEmitter"
    }
}
