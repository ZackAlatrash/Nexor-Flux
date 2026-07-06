package com.zack.recomptracker.data.rebalance

import android.util.Log
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.usage.UsageEvents
import com.zack.recomptracker.data.usage.UsageTracker
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.rebalance.RebalanceDayBar
import com.zack.recomptracker.domain.rebalance.RebalanceDecision
import com.zack.recomptracker.domain.rebalance.RebalanceEngine
import com.zack.recomptracker.domain.rebalance.RebalanceEvaluationInput
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalancePlanMath
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The weekly-rebalance *spine* (spec §5.8, §7). Deliberately **not reactive**: it runs a once-daily
 * one-shot — reconcile the persisted [com.zack.recomptracker.domain.rebalance.RebalanceState] against
 * the calendar, then evaluate the trailing-7-day window for a fresh offer — plus a small set of
 * user-driven transitions (accept / decline / dismiss / customize). Judging only days `< today` and
 * gating on a once-daily `last_evaluated` stamp is what makes "detect next morning, stable all day,
 * never same-day" true by construction.
 *
 * All numbers come from the pure [RebalanceEngine]; this class only assembles the input (via the
 * injected [buildInput] suspend lambda — the `computeWeeklyReviewData` one-shot pattern) and persists
 * the engine's decisions. Mirrors [com.zack.recomptracker.data.coach.CoachDigestCoordinator]'s shape:
 * a [Mutex]-serialised run, a fire-and-forget error stance (a failed run logs and skips — the feature
 * is "never broken, just quieter"), and an [appScope]-launched version observer for the cancel hook.
 *
 * @param store the persisted state + once-daily gate.
 * @param buildInput assembles the flattened engine input from repositories (one-shot suspend reads).
 * @param currentGoal the user's current [FitnessGoal], read at customize time — the goal is
 *   deliberately NOT stored on the plan record, so [RebalanceEngine.customize] needs it passed in to
 *   keep a RECOMP re-size goal-aware (halved fraction, 3-day cap).
 * @param planVersions `planRepository.observeVersions()`. A version row is written iff the permanent
 *   [com.zack.recomptracker.domain.plan.PlanTargets] actually changed (HC-sync saves don't), so keying
 *   the cancel hook on versions — not on every plan save — cancels an active plan only on a real edit.
 * @param usageTracker analytics sink for the `REBALANCE_*` events (spec §7); fire-and-forget, same
 *   convention as every other `usageTracker.track(...)` call site in the app.
 * @param scope the app scope the version-observer cancel hook runs on.
 */
class RebalanceCoordinator(
    private val store: RebalanceStore,
    private val buildInput: suspend () -> RebalanceEvaluationInput,
    private val currentGoal: suspend () -> FitnessGoal?,
    private val planVersions: Flow<List<PlanVersion>>,
    private val dateProvider: DateProvider,
    private val usageTracker: UsageTracker,
    private val scope: CoroutineScope,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val nowIso: () -> String = { Instant.now().toString() },
) {

    /**
     * Ended-plan notice for the UI (a COMPLETED or ENDED_EARLY plan the card shows once), in-memory
     * only — the persisted history is the durable record. Cleared by [dismissEndedNotice].
     */
    private val _endedNotice = MutableStateFlow<RebalancePlan?>(null)
    val endedNotice: StateFlow<RebalancePlan?> = _endedNotice.asStateFlow()

    /**
     * The weekly-bars viz for the *current* offer (redesign): the trailing-7 history days that drove
     * the offer plus the plan's lighter days ahead. Set only when [runOnce]'s decision is a concrete
     * [RebalanceDecision.Offer]; `null` for Silent / NoAdjustment. In-memory only, built purely from
     * the same evaluation input + plan the offer was derived from (no extra repository reads). The
     * durable record is still the persisted [RebalancePlan]; this is a derived display convenience.
     */
    private val _lastOfferWindow = MutableStateFlow<List<RebalanceDayBar>?>(null)
    val lastOfferWindow: StateFlow<List<RebalanceDayBar>?> = _lastOfferWindow.asStateFlow()

    /** Serialises [runIfDue] so two overlapping foreground calls can't both evaluate the same day. */
    private val runLock = Mutex()

    /**
     * Launches the cancel-on-plan-edit hook: a new [PlanVersion] row while a plan is ACTIVE ends it
     * early (`"plan_edited"`). `drop(1)` skips the flow's initial (current-value) emission so app
     * startup never cancels an active plan — only an *actual* edit does.
     */
    fun start() {
        scope.launch {
            planVersions.drop(1).collect {
                runCatching { onPlanVersionsChanged() }
                    .onFailure { Log.w(TAG, "Rebalance cancel-on-edit failed", it) }
            }
        }
    }

    /**
     * The once-daily one-shot (spec §5.8, §7). Idempotent per day: returns immediately when it has
     * already run today. Otherwise, under [runLock]: reconcile first (persist any transition, surface a
     * completion / graceful-end note), then evaluate for a fresh offer, then stamp today. The whole
     * body is failure-tolerant — a crash here must never take down the app; it logs and skips.
     */
    suspend fun runIfDue() {
        if (store.lastEvaluated() == dateProvider.today()) return
        runLock.withLock {
            // Re-check inside the lock: a concurrent caller may have run + stamped while we waited.
            if (store.lastEvaluated() == dateProvider.today()) return
            runCatching { runOnce() }
                .onFailure { Log.w(TAG, "Rebalance daily run failed", it) }
        }
    }

    private suspend fun runOnce() {
        val today = dateProvider.today()
        // buildInput() is read once and reused for BOTH reconcile (needs the base/eaten maps) and
        // evaluate (needs the full snapshot) — a single set of repository reads per run. Its
        // `existing` field is the same snapshot reconcile starts from (buildInput reads
        // store.current()), so reconciling against it keeps evaluate's `existing` internally consistent.
        val input = buildInput()

        // (1) Reconcile the persisted state against the calendar. Persist only when it actually
        //     changed; surface COMPLETED / ENDED_EARLY as the one-time ended notice.
        val rec = RebalanceEngine.reconcile(
            input.existing,
            today,
            input.baseTargetsByDate,
            input.eatenByDate,
        )
        val reconciled = rec.state
        if (reconciled != input.existing) store.save(reconciled)
        rec.ended?.let { ended ->
            _endedNotice.value = ended
            // Reconcile only ever surfaces COMPLETED (ran its full course) or ENDED_EARLY
            // ("unrecoverable") here — the OFFERED-expiry branch (endedReason "expired") returns
            // `ended = null` and is deliberately not an analytics event (see class doc: only the
            // five REBALANCE_* events, nothing for expiry).
            when (ended.status) {
                RebalanceStatus.COMPLETED -> usageTracker.track(UsageEvents.REBALANCE_COMPLETED)
                RebalanceStatus.ENDED_EARLY -> usageTracker.track(UsageEvents.REBALANCE_ENDED_EARLY)
                else -> Unit
            }
        }

        // (2) Evaluate for a fresh offer against the reconciled state. Only Offer / NoAdjustment write
        //     the active slot; Silent persists nothing. Only a concrete Offer counts as "offered" for
        //     analytics — a NoAdjustment note is supportive copy, not an offer (spec §7 lists five
        //     events; NoAdjustment fires none of them).
        val evalInput = input.copy(existing = reconciled)
        when (val decision = RebalanceEngine.evaluate(evalInput, newId, nowIso)) {
            is RebalanceDecision.Offer -> {
                store.save(reconciled.copy(active = decision.plan))
                // Publish the offer's weekly-bars viz, built purely from this run's input + plan
                // (no extra reads). Only a concrete Offer has a window; the others clear it.
                _lastOfferWindow.value = buildOfferWindow(input, decision.plan)
                usageTracker.track(UsageEvents.REBALANCE_OFFERED)
            }
            is RebalanceDecision.NoAdjustment -> {
                store.save(reconciled.copy(active = decision.plan))
                _lastOfferWindow.value = null
            }
            RebalanceDecision.Silent -> _lastOfferWindow.value = null
        }

        // (3) Stamp today so the gate short-circuits further runs this day.
        store.markEvaluated(today)
    }

    /**
     * Accept the current OFFERED plan (no-op otherwise): status → ACTIVE, `decidedAt` stamped, and the
     * window re-stamped to start tomorrow (Day 1 = accept-day + 1), end = start + lengthDays - 1.
     */
    suspend fun accept() {
        val state = store.current()
        val offer = state.active ?: return
        if (offer.status != RebalanceStatus.OFFERED) return
        val start = dateProvider.today().plusDays(1)
        val end = start.plusDays((offer.lengthDays - 1).toLong())
        val active = offer.copy(
            status = RebalanceStatus.ACTIVE,
            decidedAtIso = nowIso(),
            startDateIso = start.toString(),
            endDateIso = end.toString(),
        )
        store.save(state.copy(active = active))
        usageTracker.track(UsageEvents.REBALANCE_ACCEPTED)
    }

    /** Decline the current OFFERED plan (no-op otherwise): move it to history as DECLINED, clear active. */
    suspend fun decline() {
        val state = store.current()
        val offer = state.active ?: return
        if (offer.status != RebalanceStatus.OFFERED) return
        val declined = offer.copy(status = RebalanceStatus.DECLINED, decidedAtIso = nowIso())
        archive(state, declined, notify = false)
        usageTracker.track(UsageEvents.REBALANCE_DECLINED)
    }

    /**
     * Dismiss the current NO_ADJUSTMENT note (no-op otherwise): move it to history (status stays
     * NO_ADJUSTMENT, `decidedAt` stamped), clear active. Not an analytics event — a NO_ADJUSTMENT note
     * was never an offer (see [runOnce]'s Offer-only REBALANCE_OFFERED comment).
     */
    suspend fun dismissNote() {
        val state = store.current()
        val note = state.active ?: return
        if (note.status != RebalanceStatus.NO_ADJUSTMENT) return
        val archived = note.copy(decidedAtIso = nowIso())
        archive(state, archived, notify = false)
    }

    /** Clears the in-memory ended notice (the card dismissed its completion / graceful-end face). */
    fun dismissEndedNotice() {
        _endedNotice.value = null
    }

    /**
     * User-initiated cancel of the current ACTIVE plan (no-op otherwise): ends it as of yesterday
     * (`today - 1`) so today and every day forward revert to the base plan, moves it to history
     * (`endedReason = "cancelled"`), and clears the active slot. Unlike the reconcile / plan-edit
     * paths this surfaces **no** ended-notice card — a user who just cancelled doesn't need a
     * "your plan ended" note; the caller shows a lightweight confirmation instead. A day-0 plan
     * (start = tomorrow, never in effect) ends with an empty `[start, today-1]` window, so nothing
     * was ever reduced.
     */
    suspend fun cancelActive() {
        val state = store.current()
        val active = state.active ?: return
        if (active.status != RebalanceStatus.ACTIVE) return
        val ended = active.copy(
            status = RebalanceStatus.ENDED_EARLY,
            endedReason = "cancelled",
            endDateIso = dateProvider.today().minusDays(1).toString(),
        )
        archive(state, ended, notify = false)
        usageTracker.track(UsageEvents.REBALANCE_CANCELLED)
    }

    /**
     * Customize the current OFFERED plan to [mode] (no-op otherwise): recompute R/E/D from the facts
     * stored on the offer via [RebalanceEngine.customize], passing the user's current goal so a RECOMP
     * offer keeps its halved fraction and 3-day cap. Persists the recomputed plan AND the sticky
     * [com.zack.recomptracker.domain.rebalance.RebalanceState.mode] so future offers size with [mode].
     */
    suspend fun customize(mode: RebalanceMode) {
        val state = store.current()
        val offer = state.active ?: return
        if (offer.status != RebalanceStatus.OFFERED) return
        val recomputed = RebalanceEngine.customize(offer, mode, currentGoal())
        store.save(state.copy(active = recomputed, mode = mode))
    }

    /**
     * Cancel hook body: if a plan is currently ACTIVE, end it early as `"plan_edited"`, rewriting its
     * end date to the last day it was actually in effect (`max(today-1, startDate)`), move it to
     * history, and surface it as the ended notice. Any other active status (OFFERED / NO_ADJUSTMENT) or
     * an empty slot is left untouched — only a live rebalance responds to a permanent-plan edit.
     */
    private suspend fun onPlanVersionsChanged() {
        val state = store.current()
        val active = state.active ?: return
        if (active.status != RebalanceStatus.ACTIVE) return
        val start = runCatching { LocalDate.parse(active.startDateIso) }.getOrNull()
        val lastEffective = start
            ?.let { maxOf(dateProvider.today().minusDays(1), it) }
            ?: dateProvider.today().minusDays(1)
        val ended = active.copy(
            status = RebalanceStatus.ENDED_EARLY,
            endedReason = "plan_edited",
            endDateIso = lastEffective.toString(),
        )
        archive(state, ended, notify = true)
        usageTracker.track(UsageEvents.REBALANCE_ENDED_EARLY)
    }

    /**
     * Shared "move the active slot to a terminal record" shape used by [decline], [dismissNote], and
     * [onPlanVersionsChanged]: clears [RebalanceState.active], appends [terminal] to
     * [RebalanceState.history], and persists via [store]. When [notify] is true also surfaces
     * [terminal] as the one-time [endedNotice] (the ended-plan card face) — [decline]/[dismissNote]
     * pass `false` since their archived record was a user-driven dismissal, not an ended-plan notice.
     */
    private suspend fun archive(state: RebalanceState, terminal: RebalancePlan, notify: Boolean) {
        store.save(state.copy(active = null, history = state.history + terminal))
        if (notify) _endedNotice.value = terminal
    }

    // ── Developer / debug tools (drive the UI into any phase; NOT part of production flows) ──
    /**
     * Force the dashboard rebalance card into the phase [scenario] describes, so the redesigned UI can
     * be eyeballed on-device without waiting for real data to trigger each state. Sets all three
     * UI-driving sources the `RebalanceViewModel` combines — the persisted [RebalanceState],
     * [_endedNotice], and [_lastOfferWindow] — then stamps `markEvaluated(today)` so the next dashboard
     * [runIfDue] short-circuits and does not clobber the forced state. Purely additive: none of the
     * production transitions (reconcile / accept / decline / customize / [runIfDue] / [buildOfferWindow])
     * are touched. The base calorie target is read from the same [buildInput] snapshot the engine uses
     * (falling back to 2500 if unavailable) so the synthetic bars line up with the user's real plan.
     */
    suspend fun debugApply(scenario: RebalanceDebugScenario) {
        val today = dateProvider.today()
        val base = runCatching { buildInput().baseTargetsByDate[today]?.calories }.getOrNull() ?: 2500
        val app = RebalanceDebugScenarios.build(scenario, today, base, newId, nowIso)
        store.save(app.state)
        _endedNotice.value = app.endedNotice
        _lastOfferWindow.value = app.offerWindow
        store.markEvaluated(today)
    }

    private companion object {
        const val TAG = "RebalanceCoordinator"

        /**
         * Build the offer's weekly-bars viz purely from the evaluation [input] and the offered [plan]
         * — no repository reads. Two segments, chronological:
         *  - History: the trailing 7 days ending yesterday (`today-7 .. today-1`). `valueKcal` is the
         *    day's eaten kcal, `targetKcal` the base target that day; a day over its target is flagged
         *    `isOver` (these are the days that drove the offer).
         *  - Plan: `plan.lengthDays` days ahead starting tomorrow (`today+1 ..`). `valueKcal` is the
         *    floored effective target ([RebalancePlanMath.effectiveCalories]), `targetKcal` the base;
         *    never `isOver`.
         */
        fun buildOfferWindow(input: RebalanceEvaluationInput, plan: RebalancePlan): List<RebalanceDayBar> {
            val today = input.today
            val history = (7 downTo 1).map { offset ->
                val d = today.minusDays(offset.toLong())
                val value = input.eatenByDate[d] ?: 0
                val target = input.baseTargetsByDate[d]?.calories ?: plan.baseCalories
                RebalanceDayBar(
                    label = dayLabel(d),
                    valueKcal = value,
                    targetKcal = target,
                    isPlanDay = false,
                    isOver = value > target,
                )
            }
            val reducedTarget = RebalancePlanMath.effectiveCalories(plan)
            val planDays = (0 until plan.lengthDays).map { i ->
                val d = today.plusDays(1L + i)
                RebalanceDayBar(
                    label = dayLabel(d),
                    valueKcal = reducedTarget,
                    targetKcal = plan.baseCalories,
                    isPlanDay = true,
                    isOver = false,
                )
            }
            return history + planDays
        }

        private fun dayLabel(date: LocalDate): String =
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}
