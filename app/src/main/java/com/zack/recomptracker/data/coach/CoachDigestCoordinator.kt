package com.zack.recomptracker.data.coach

import android.util.Log
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.domain.coach.CoachContext
import com.zack.recomptracker.domain.coach.CoachSignalEngine
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.SignalSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The proactive-coaching *spine*: it runs the deterministic pipeline once and stages the single
 * winner (or silence) to the inbox. See `docs/ai-redesign/08-technical-architecture.md` §8
 * (scheduling) and §9 (proactive flow: run catalog → rank → one winner → inbox).
 *
 * **The engine decides whether to speak; the LLM never does.** [run] is pure CPU/DB work — it builds
 * the [CoachContext] snapshot, evaluates the detector catalog, ranks/cooldown-filters to one winner,
 * and persists it. No cloud config is touched here: phrasing is a separate decoration step that
 * happens later when a surface opens (`CoachPhrasingService`). Silence — no eligible signal — is a
 * valid, first-class output that stages `null`.
 *
 * Mirrors [com.zack.recomptracker.data.health.HealthSyncCoordinator]: fire-and-forget [runIfDue] with
 * a once-a-day debounce gate, plus [enableBackgroundDigest]/[disableBackgroundDigest] delegating to a
 * WorkManager-free [CoachDigestScheduler] so this class stays Context-free and unit-testable.
 *
 * Boundary rule (§5, invariant #7): depends only on `domain/coach` + `data/coach` + the AI-enabled
 * gate — never on any Gemma/Routing/AiBackend/ModelVariant class.
 *
 * @param contextProvider builds the snapshot; injected as a suspend lambda (AppContainer passes
 *   `{ coachContextCache.get() }`) so [run] is testable without the repository graph.
 * @param aiEnabledFlow the master AI gate; when off the coach stays silent (stages `null`).
 * @param journey the multi-week memory ledger; the digest records the fired winner and each week's
 *   verdict into it so prompts can reference the journey. A no-op default keeps existing tests and
 *   any Context-free construction working.
 * @param cooldownDays a dedupKey can't re-surface until this many days pass (default 7, §9).
 */
class CoachDigestCoordinator(
    private val contextProvider: suspend () -> CoachContext,
    private val engine: CoachSignalEngine,
    private val selector: SignalSelector,
    private val inbox: CoachInbox,
    private val aiEnabledFlow: Flow<Boolean>,
    private val dateProvider: DateProvider,
    private val appScope: CoroutineScope,
    private val journey: CoachJourney = NoopCoachJourney,
    private val scheduler: CoachDigestScheduler = NoopCoachDigestScheduler,
    private val cooldownDays: Int = SignalSelector.DEFAULT_COOLDOWN_DAYS,
    private val pushEmitter: CoachPushEmitter? = null,
    private val notificationPreferences: CoachNotifierPreferences? = null,
) {

    /**
     * Runs the full deterministic pipeline once and stages the outcome. Suspends until complete so
     * background work ([CoachDigestWorker]) can report a result.
     *
     * - AI disabled → stage `null` and return (no build, no evaluate — the coach is fully off).
     * - otherwise → build the snapshot, evaluate detectors, select one winner, stage it (or `null`
     *   for silence), mark a real winner's dedupKey seen, and stamp today's run for the debounce.
     */
    suspend fun run() {
        if (!aiEnabledFlow.first()) {
            inbox.stage(null)
            return
        }
        val today = dateProvider.today()
        val ctx = contextProvider()
        // Journey memory: idempotently record every weekly-review verdict the snapshot carries so the
        // multi-week narrative accretes over time. recordWeeklyVerdict dedups per signature, so
        // re-recording the same weeks on each daily run is a no-op. The snapshot only carries a week
        // start, so we stamp that as the week's date (no invented data).
        ctx.history.weeklyReviews.forEach { review ->
            journey.recordWeeklyVerdict(review.signature, review.weekStart.toString(), review.verdict)
        }
        val signals = engine.evaluate(ctx)
        // The digest feeds the "Today's Coaching" slot, so only TODAY-surface signals are eligible.
        // WEEKLY-surface signals (e.g. the recomp verdict) belong to the weekly check-in surface and
        // must not leak into the daily slot.
        val winner = selector.selectForSurface(CoachSurface.TODAY, signals, inbox.seenLedger(), today, cooldownDays).winner
        inbox.stage(winner)
        if (winner != null) {
            inbox.markSeen(winner.dedupKey, today)
            // Record the fired winner under the current week signature so recurrence detection aligns
            // with the weekly rhythm. Reuse the latest weekly-review signature the snapshot already
            // built (no new heavy computation); fall back to today's ISO date when no review exists.
            journey.recordFiredSignal(winner, currentWeekSignature(ctx, today))
            // Event/daily push: only a P0 winner ever clears the RateLimiter's tier gate; the emitter
            // is a no-op for anything else. Injected + fully gated, so this is safe on every run.
            pushEmitter?.emit(winner, isWeeklyCheckIn = false)
        }
        emitWeeklyCheckInPush(signals, ctx, today)
        inbox.setLastRunDate(today)
    }

    /**
     * Fire the single weekly check-in push at most once per new week. Gated on the latest weekly-review
     * signature differing from the last one we pushed for (reusing the §9 `lastSeen*` chain), so a daily
     * digest run re-firing the same week is a no-op. The pushed signal is the WEEKLY-surface winner from
     * this run's catalog; the emitter still applies prefs + RateLimiter + the decision-attached gate.
     */
    private suspend fun emitWeeklyCheckInPush(
        signals: List<com.zack.recomptracker.domain.coach.CoachSignal>,
        ctx: CoachContext,
        today: java.time.LocalDate,
    ) {
        val emitter = pushEmitter ?: return
        val prefs = notificationPreferences ?: return
        val signature = ctx.history.weeklyReviews.lastOrNull()?.signature ?: return
        if (signature == prefs.lastPushedWeeklySignature()) return
        val weeklyWinner = selector.selectForSurface(
            CoachSurface.WEEKLY, signals, inbox.seenLedger(), today, cooldownDays,
        ).winner ?: return
        if (emitter.emit(weeklyWinner, isWeeklyCheckIn = true)) {
            prefs.setLastPushedWeeklySignature(signature)
        }
    }

    /** The week signature history is keyed by: the latest stored weekly review's, or today's date. */
    private fun currentWeekSignature(ctx: CoachContext, today: java.time.LocalDate): String =
        ctx.history.weeklyReviews.lastOrNull()?.signature ?: today.toString()

    /**
     * Fire-and-forget daily digest, safe to call on every app foreground. Skips when it has already
     * run today (once-a-day debounce). Failures are logged, never surfaced — the coach is "never
     * broken, just quieter."
     */
    fun runIfDue() {
        appScope.launch {
            if (inbox.lastRunDate() == dateProvider.today()) return@launch
            runCatching { run() }.onFailure { Log.w(TAG, "Coach digest run failed", it) }
        }
    }

    /** Schedule the periodic background digest (idempotent). Call when AI becomes enabled. */
    fun enableBackgroundDigest() = scheduler.enable()

    /** Cancel the periodic background digest. Call when AI is disabled. */
    fun disableBackgroundDigest() = scheduler.disable()

    private companion object {
        const val TAG = "CoachDigestCoordinator"
    }
}
