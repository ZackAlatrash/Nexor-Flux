package com.zack.recomptracker.data.rebalance

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.usage.UsageEvents
import com.zack.recomptracker.data.usage.UsageTracker
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.rebalance.RebalanceEvaluationInput
import com.zack.recomptracker.domain.rebalance.RebalanceIntensity
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalancePlanMath
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate as KxLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RebalanceCoordinatorTest {

    private val today = LocalDate.of(2026, 7, 8) // Wednesday

    private class MutableDateProvider(var day: LocalDate) : DateProvider {
        override fun today(): LocalDate = day
    }

    /** Records every [track] call in order, for assertions on which `REBALANCE_*` events fired. */
    private class RecordingUsageTracker : UsageTracker {
        val events = mutableListOf<String>()
        override fun track(type: String, label: String?) {
            events.add(type)
        }
    }

    private fun targets(cal: Int) = PlanTargets(cal, 160, 300, 70, cal - 100, cal + 100)

    /**
     * A canned evaluation input the tests can mutate between runs. Defaults describe a single high
     * day yesterday (eaten 3100 vs base 2500 → over 600) on an otherwise on-target 7-day window, all
     * days logged, a cut goal — i.e. the mandatory worked example that produces a real Offer.
     */
    private fun cannedInput(
        refToday: LocalDate = today,
        base: Int = 2500,
        yesterdayEaten: Int = 3100,
        goal: FitnessGoal? = FitnessGoal.MODERATE_CUT,
        mode: RebalanceMode = RebalanceMode.BALANCED,
        existing: RebalanceState = RebalanceState(mode = mode),
    ): RebalanceEvaluationInput {
        val window = (1..7).map { refToday.minusDays(it.toLong()) }
        val yesterday = refToday.minusDays(1)
        return RebalanceEvaluationInput(
            today = refToday,
            baseTargetsByDate = window.associateWith { targets(base) },
            eatenByDate = window.associateWith { if (it == yesterday) yesterdayEaten else base },
            mealCountByDate = window.associateWith { 3 },
            stepsByDate = emptyMap(),
            baseStepGoal = null,
            goal = goal,
            mode = mode,
            existing = existing,
        )
    }

    private var idSeq = 0
    private val newId: () -> String = { "id-${idSeq++}" }
    private val nowIso: () -> String = { "2026-07-08T09:00:00Z" }

    private fun coordinator(
        store: RebalanceStore,
        buildInput: suspend () -> RebalanceEvaluationInput,
        planVersions: Flow<List<PlanVersion>> = MutableSharedFlow(),
        dateProvider: DateProvider = MutableDateProvider(today),
        usageTracker: UsageTracker = RecordingUsageTracker(),
        scope: CoroutineScope,
    ) = RebalanceCoordinator(
        store = store,
        buildInput = buildInput,
        planVersions = planVersions,
        dateProvider = dateProvider,
        usageTracker = usageTracker,
        scope = scope,
        newId = newId,
        nowIso = nowIso,
    )

    // ── runIfDue: once-daily gate ─────────────────────────────────────────────────────

    @Test
    fun `runIfDue evaluates once per day`() = runTest {
        val store = FakeRebalanceStore()
        val calls = AtomicInteger(0)
        val coordinator = coordinator(
            store = store,
            buildInput = { calls.incrementAndGet(); cannedInput() },
            scope = this,
        )

        coordinator.runIfDue()
        assertEquals("first run builds input and evaluates", 1, calls.get())
        assertNotNull("an offer is persisted", store.current().active)
        assertEquals(RebalanceStatus.OFFERED, store.current().active!!.status)

        // Same-day second call short-circuits on lastEvaluated == today.
        coordinator.runIfDue()
        assertEquals("second same-day call does not re-run", 1, calls.get())
    }

    @Test
    fun `runIfDue reconciles before evaluating and does not immediately re-offer after completion`() = runTest {
        // An ACTIVE plan already past its end → reconcile completes it (history + endedNotice), and the
        // fresh cooldown/new-event gates keep the subsequent evaluate Silent (no immediate re-offer).
        val activePast = RebalancePlan(
            id = "active", triggerDateIso = "2026-07-01", startDateIso = "2026-07-04",
            endDateIso = "2026-07-06", lengthDays = 3, mode = RebalanceMode.EAT_LESS,
            baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
            status = RebalanceStatus.ACTIVE, createdAtIso = "2026-07-03T09:00:00Z",
            decidedAtIso = "2026-07-03T09:00:00Z",
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = activePast))
        // The input still describes a fresh high day; only the gates should suppress a new offer.
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(existing = store.current()) },
            scope = this,
        )

        coordinator.runIfDue()

        val state = store.current()
        assertNull("active plan completed, slot cleared", state.active)
        assertEquals(1, state.history.size)
        assertEquals(RebalanceStatus.COMPLETED, state.history.first().status)
        assertNotNull("completion surfaces an ended notice", coordinator.endedNotice.value)
        assertEquals(RebalanceStatus.COMPLETED, coordinator.endedNotice.value!!.status)
    }

    // ── accept / decline / dismissNote ────────────────────────────────────────────────

    @Test
    fun `accept stamps decided and restamps the window from tomorrow`() = runTest {
        val store = FakeRebalanceStore()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput() },
            scope = this,
        )
        coordinator.runIfDue()
        val offer = store.current().active!!
        assertEquals(RebalanceStatus.OFFERED, offer.status)

        coordinator.accept()

        val active = store.current().active!!
        assertEquals(RebalanceStatus.ACTIVE, active.status)
        assertEquals("decidedAt stamped", nowIso(), active.decidedAtIso)
        assertEquals("startDate re-stamped to tomorrow", today.plusDays(1).toString(), active.startDateIso)
        assertEquals(
            "endDate = start + lengthDays - 1",
            today.plusDays(1).plusDays((active.lengthDays - 1).toLong()).toString(),
            active.endDateIso,
        )
    }

    @Test
    fun `decline moves to history and cooldown blocks the next evaluation`() = runTest {
        val store = FakeRebalanceStore()
        val dp = MutableDateProvider(today)
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(refToday = dp.day, existing = store.current()) },
            dateProvider = dp,
            scope = this,
        )
        coordinator.runIfDue()
        assertNotNull(store.current().active)

        coordinator.decline()

        val afterDecline = store.current()
        assertNull("declined offer clears the active slot", afterDecline.active)
        assertEquals(1, afterDecline.history.size)
        assertEquals(RebalanceStatus.DECLINED, afterDecline.history.first().status)
        assertEquals(nowIso(), afterDecline.history.first().decidedAtIso)

        // Next day: cooldown (< 3 days since decision) keeps the next evaluation Silent.
        dp.day = today.plusDays(1)
        coordinator.runIfDue()
        assertNull("within cooldown → no new offer", store.current().active)
    }

    @Test
    fun `dismiss note archives the no-adjustment record`() = runTest {
        // Too-large surplus → the engine returns a NO_ADJUSTMENT note in active; dismissNote archives it.
        val store = FakeRebalanceStore()
        val coordinator = coordinator(
            store = store,
            buildInput = {
                cannedInput(yesterdayEaten = 12_000, existing = store.current()) // S huge → no adjustment
            },
            scope = this,
        )
        coordinator.runIfDue()
        val note = store.current().active
        assertNotNull(note)
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, note!!.status)

        coordinator.dismissNote()

        val after = store.current()
        assertNull("note dismissed from the active slot", after.active)
        assertEquals(1, after.history.size)
        assertEquals("status stays NO_ADJUSTMENT in history", RebalanceStatus.NO_ADJUSTMENT, after.history.first().status)
        assertEquals(nowIso(), after.history.first().decidedAtIso)
    }

    // ── cancelActive (user cancels a live plan) ────────────────────────────────────────

    private fun activePlan(
        start: LocalDate,
        end: LocalDate,
    ) = RebalancePlan(
        id = "active", triggerDateIso = start.minusDays(2).toString(),
        startDateIso = start.toString(), endDateIso = end.toString(),
        lengthDays = 4, mode = RebalanceMode.BALANCED, baseCalories = 2500,
        dailyCalorieReduction = 200, extraDailySteps = 1200, baseStepGoal = 9000,
        recentAvgSteps = 8000, surplusKcal = 600, recoveredKcal = 460,
        status = RebalanceStatus.ACTIVE, createdAtIso = "2026-07-06T09:00:00Z",
        decidedAtIso = "2026-07-06T09:00:00Z",
    )

    @Test
    fun `cancelActive ends the plan as of yesterday, clears the slot, and shows no notice`() = runTest {
        // Mid-plan: started two days ago, ends in two days. Cancelling today reverts today onward.
        val store = FakeRebalanceStore(
            seed = RebalanceState(active = activePlan(start = today.minusDays(2), end = today.plusDays(1))),
        )
        val usage = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(existing = store.current()) },
            usageTracker = usage,
            scope = this,
        )

        coordinator.cancelActive()

        val state = store.current()
        assertNull("cancel clears the active slot", state.active)
        assertEquals(1, state.history.size)
        val ended = state.history.first()
        assertEquals(RebalanceStatus.ENDED_EARLY, ended.status)
        assertEquals("user cancel", "cancelled", ended.endedReason)
        assertEquals(
            "ends as of yesterday so today reverts to base",
            today.minusDays(1).toString(),
            ended.endDateIso,
        )
        assertNull("a user cancel surfaces no ended-notice card", coordinator.endedNotice.value)
        assertTrue("REBALANCE_CANCELLED tracked", usage.events.contains(UsageEvents.REBALANCE_CANCELLED))
    }

    @Test
    fun `cancelActive on a day-0 plan leaves an inverted window so nothing was ever reduced`() = runTest {
        // Accepted late in the day: start = tomorrow, never in effect. Ending at today-1 (< start)
        // makes [start, end] inverted → EffectiveTargets.overrides matches no date.
        val store = FakeRebalanceStore(
            seed = RebalanceState(active = activePlan(start = today.plusDays(1), end = today.plusDays(4))),
        )
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(existing = store.current()) },
            scope = this,
        )

        coordinator.cancelActive()

        val ended = store.current().history.first()
        assertEquals(today.minusDays(1).toString(), ended.endDateIso)
        assertTrue("inverted window: end precedes start", ended.endDateIso < ended.startDateIso)
    }

    @Test
    fun `cancelActive is a no-op for an offered plan`() = runTest {
        val store = FakeRebalanceStore()
        val usage = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput() },
            usageTracker = usage,
            scope = this,
        )
        coordinator.runIfDue()
        assertEquals(RebalanceStatus.OFFERED, store.current().active!!.status)

        coordinator.cancelActive()

        assertEquals("an offered plan is left untouched", RebalanceStatus.OFFERED, store.current().active!!.status)
        assertTrue("no cancel event fires on a no-op", !usage.events.contains(UsageEvents.REBALANCE_CANCELLED))
    }

    // ── customize ─────────────────────────────────────────────────────────────────────

    @Test
    fun `customize recomputes the offer for the new mode and intensity and stores sticky mode`() = runTest {
        // The engine mints a STANDARD offer (S = 600 → 2-day / 230 kcal). Customizing to EAT_LESS + FULL
        // recomputes to a heavier plan; the mode persists on state (sticky) while intensity does not.
        val store = FakeRebalanceStore()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(existing = store.current()) },
            scope = this,
        )
        coordinator.runIfDue()
        val offer = store.current().active!!
        assertEquals(RebalanceIntensity.STANDARD, offer.intensity)
        assertEquals(230, offer.dailyCalorieReduction)

        coordinator.customize(RebalanceMode.EAT_LESS, RebalanceIntensity.FULL)

        val after = store.current()
        assertNotNull(after.active)
        assertEquals("recomputed plan keeps EAT_LESS", RebalanceMode.EAT_LESS, after.active!!.mode)
        assertEquals("intensity recorded on the plan", RebalanceIntensity.FULL, after.active!!.intensity)
        assertEquals("FULL recovers ~all of the surplus (300 vs 230)", 300, after.active!!.dailyCalorieReduction)
        assertEquals("sticky mode updated on state", RebalanceMode.EAT_LESS, after.mode)
        assertEquals("still OFFERED after customize", RebalanceStatus.OFFERED, after.active!!.status)
    }

    @Test
    fun `customize keeps the un-changed dial from the freshly-read offer`() = runTest {
        // The two dials call customize independently; changing one must preserve the other's current
        // value (read fresh from the offer, not from stale UI state). Regression guard for the fast
        // dual-tap lost update.
        val store = FakeRebalanceStore()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(existing = store.current()) },
            scope = this,
        )
        coordinator.runIfDue()
        val offer = store.current().active!!
        val initialMode = offer.mode
        assertEquals(RebalanceIntensity.STANDARD, offer.intensity)

        // Change ONLY the intensity → the mix is preserved, not reset to a default.
        coordinator.customize(intensity = RebalanceIntensity.FULL)
        val afterIntensity = store.current().active!!
        assertEquals("mix preserved when only intensity changes", initialMode, afterIntensity.mode)
        assertEquals(RebalanceIntensity.FULL, afterIntensity.intensity)

        // Now change ONLY the mix → the FULL intensity is preserved.
        coordinator.customize(mode = RebalanceMode.MOVE_MORE)
        val afterMode = store.current().active!!
        assertEquals("mix updated", RebalanceMode.MOVE_MORE, afterMode.mode)
        assertEquals("intensity preserved when only the mix changes", RebalanceIntensity.FULL, afterMode.intensity)
        assertEquals("sticky mode persisted on state", RebalanceMode.MOVE_MORE, store.current().mode)
    }

    // ── cancel-on-plan-edit hook ────────────────────────────────────────────────────────

    @Test
    fun `a new plan version while active ends the plan as plan_edited`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val versions = MutableSharedFlow<List<PlanVersion>>(replay = 0)
        val activePlan = RebalancePlan(
            id = "active", triggerDateIso = "2026-07-01", startDateIso = "2026-07-06",
            endDateIso = "2026-07-10", lengthDays = 5, mode = RebalanceMode.EAT_LESS,
            baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 1000,
            status = RebalanceStatus.ACTIVE, createdAtIso = "2026-07-05T09:00:00Z",
            decidedAtIso = "2026-07-05T09:00:00Z",
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = activePlan))
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput() },
            planVersions = versions,
            scope = scope,
        )
        coordinator.start()
        runCurrent()

        // Initial emission must NOT cancel (drop(1)).
        versions.emit(emptyList())
        runCurrent()
        assertNotNull("initial version emission does not cancel", store.current().active)

        // A real change (a new version row) while ACTIVE → ENDED_EARLY("plan_edited").
        versions.emit(listOf(PlanVersion(today.toKotlinLocalDate(), targets(2400))))
        runCurrent()

        val after = store.current()
        assertNull("plan edited → active slot cleared", after.active)
        assertEquals(1, after.history.size)
        val ended = after.history.first()
        assertEquals(RebalanceStatus.ENDED_EARLY, ended.status)
        assertEquals("plan_edited", ended.endedReason)
        assertEquals(
            "endDate rewritten to the last effective day (today-1, clamped ≥ start)",
            maxOf(today.minusDays(1), LocalDate.parse("2026-07-06")).toString(),
            ended.endDateIso,
        )
        assertNotNull("ended plan surfaced as a notice", coordinator.endedNotice.value)
        assertEquals(RebalanceStatus.ENDED_EARLY, coordinator.endedNotice.value!!.status)
    }

    @Test
    fun `the initial version emission does not cancel`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val versions = MutableSharedFlow<List<PlanVersion>>(replay = 0)
        val activePlan = RebalancePlan(
            id = "active", triggerDateIso = "2026-07-01", startDateIso = "2026-07-06",
            endDateIso = "2026-07-10", lengthDays = 5, mode = RebalanceMode.EAT_LESS,
            baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 1000,
            status = RebalanceStatus.ACTIVE, createdAtIso = "2026-07-05T09:00:00Z",
            decidedAtIso = "2026-07-05T09:00:00Z",
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = activePlan))
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput() },
            planVersions = versions,
            scope = scope,
        )
        coordinator.start()
        runCurrent()

        versions.emit(listOf(PlanVersion(KxLocalDate.parse("2026-06-01"), targets(2500))))
        runCurrent()

        assertNotNull("only the first (initial) emission is skipped", store.current().active)
        assertNull(coordinator.endedNotice.value)
    }

    // ── concurrency ─────────────────────────────────────────────────────────────────────

    @Test
    fun `concurrent runIfDue calls are serialized`() = runTest {
        // Two overlapping runIfDue coroutines: the Mutex + lastEvaluated gate must let exactly one
        // evaluation through. Mirrors CoachDigestCoordinatorTest's Mutex serialization test.
        val store = FakeRebalanceStore()
        val calls = AtomicInteger(0)
        val coordinator = coordinator(
            store = store,
            buildInput = { calls.incrementAndGet(); cannedInput() },
            scope = this,
        )

        val a = launch { coordinator.runIfDue() }
        val b = launch { coordinator.runIfDue() }
        a.join()
        b.join()

        assertEquals("exactly one evaluation despite two concurrent runs", 1, calls.get())
    }

    // ── analytics (Task 8) ──────────────────────────────────────────────────────────────

    @Test
    fun `a fresh offer fires REBALANCE_OFFERED`() = runTest {
        val store = FakeRebalanceStore()
        val tracker = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput() },
            usageTracker = tracker,
            scope = this,
        )

        coordinator.runIfDue()

        assertEquals(RebalanceStatus.OFFERED, store.current().active!!.status)
        assertEquals(listOf(UsageEvents.REBALANCE_OFFERED), tracker.events)
    }

    @Test
    fun `a NO_ADJUSTMENT note fires no analytics event`() = runTest {
        // Same too-large-surplus setup as "dismiss note archives the no-adjustment record".
        val store = FakeRebalanceStore()
        val tracker = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(yesterdayEaten = 12_000, existing = store.current()) },
            usageTracker = tracker,
            scope = this,
        )

        coordinator.runIfDue()

        assertEquals(RebalanceStatus.NO_ADJUSTMENT, store.current().active!!.status)
        assertTrue("NoAdjustment is not one of the five REBALANCE_* events", tracker.events.isEmpty())
    }

    @Test
    fun `accept fires REBALANCE_ACCEPTED`() = runTest {
        val store = FakeRebalanceStore()
        val tracker = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput() },
            usageTracker = tracker,
            scope = this,
        )
        coordinator.runIfDue()
        tracker.events.clear() // isolate accept()'s own event from the OFFERED one above

        coordinator.accept()

        assertEquals(listOf(UsageEvents.REBALANCE_ACCEPTED), tracker.events)
    }

    @Test
    fun `decline fires REBALANCE_DECLINED`() = runTest {
        val store = FakeRebalanceStore()
        val tracker = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(existing = store.current()) },
            usageTracker = tracker,
            scope = this,
        )
        coordinator.runIfDue()
        tracker.events.clear()

        coordinator.decline()

        assertEquals(listOf(UsageEvents.REBALANCE_DECLINED), tracker.events)
    }

    @Test
    fun `dismissNote fires no analytics event`() = runTest {
        val store = FakeRebalanceStore()
        val tracker = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(yesterdayEaten = 12_000, existing = store.current()) },
            usageTracker = tracker,
            scope = this,
        )
        coordinator.runIfDue()
        tracker.events.clear()

        coordinator.dismissNote()

        assertTrue("dismissNote is not one of the five REBALANCE_* events", tracker.events.isEmpty())
    }

    @Test
    fun `reconcile completing a plan fires REBALANCE_COMPLETED`() = runTest {
        // Mirrors "runIfDue reconciles before evaluating and does not immediately re-offer after completion".
        val activePast = RebalancePlan(
            id = "active", triggerDateIso = "2026-07-01", startDateIso = "2026-07-04",
            endDateIso = "2026-07-06", lengthDays = 3, mode = RebalanceMode.EAT_LESS,
            baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
            status = RebalanceStatus.ACTIVE, createdAtIso = "2026-07-03T09:00:00Z",
            decidedAtIso = "2026-07-03T09:00:00Z",
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = activePast))
        val tracker = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(existing = store.current()) },
            usageTracker = tracker,
            scope = this,
        )

        coordinator.runIfDue()

        assertEquals(RebalanceStatus.COMPLETED, store.current().history.first().status)
        assertTrue(
            "completion fires REBALANCE_COMPLETED (Silent evaluate adds nothing else)",
            tracker.events.contains(UsageEvents.REBALANCE_COMPLETED),
        )
        assertTrue(
            "reconcile-completed never also fires REBALANCE_OFFERED/ENDED_EARLY",
            tracker.events.none { it == UsageEvents.REBALANCE_OFFERED || it == UsageEvents.REBALANCE_ENDED_EARLY },
        )
    }

    @Test
    fun `reconcile ending a plan as unrecoverable fires REBALANCE_ENDED_EARLY`() = runTest {
        // Mirrors RebalanceReconcileTest's "active plan that is clearly unrecoverable ends early":
        // start=07-06, end=07-10, r=100 e=0, yesterday eaten 5500 vs base 2500 -> slack > 75.
        val activePlan = RebalancePlan(
            id = "active", triggerDateIso = "2026-07-01", startDateIso = "2026-07-06",
            endDateIso = "2026-07-10", lengthDays = 5, mode = RebalanceMode.EAT_LESS,
            baseCalories = 2500, dailyCalorieReduction = 100, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
            status = RebalanceStatus.ACTIVE, createdAtIso = "2026-07-01T09:00:00Z",
            decidedAtIso = "2026-07-01T09:00:00Z",
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = activePlan))
        val tracker = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(yesterdayEaten = 5500, existing = store.current()) },
            usageTracker = tracker,
            scope = this,
        )

        coordinator.runIfDue()

        val ended = store.current().history.first()
        assertEquals(RebalanceStatus.ENDED_EARLY, ended.status)
        assertEquals("unrecoverable", ended.endedReason)
        assertTrue(
            "unrecoverable end-early fires REBALANCE_ENDED_EARLY",
            tracker.events.contains(UsageEvents.REBALANCE_ENDED_EARLY),
        )
    }

    @Test
    fun `plan_edited cancel hook fires REBALANCE_ENDED_EARLY`() = runTest {
        // Mirrors "a new plan version while active ends the plan as plan_edited".
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val versions = MutableSharedFlow<List<PlanVersion>>(replay = 0)
        val activePlan = RebalancePlan(
            id = "active", triggerDateIso = "2026-07-01", startDateIso = "2026-07-06",
            endDateIso = "2026-07-10", lengthDays = 5, mode = RebalanceMode.EAT_LESS,
            baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 1000,
            status = RebalanceStatus.ACTIVE, createdAtIso = "2026-07-05T09:00:00Z",
            decidedAtIso = "2026-07-05T09:00:00Z",
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = activePlan))
        val tracker = RecordingUsageTracker()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput() },
            planVersions = versions,
            usageTracker = tracker,
            scope = scope,
        )
        coordinator.start()
        runCurrent()
        versions.emit(emptyList()) // initial emission, dropped
        runCurrent()

        versions.emit(listOf(PlanVersion(today.toKotlinLocalDate(), targets(2400))))
        runCurrent()

        assertEquals(RebalanceStatus.ENDED_EARLY, store.current().history.first().status)
        assertEquals(listOf(UsageEvents.REBALANCE_ENDED_EARLY), tracker.events)
    }

    // ── lastOfferWindow (redesign viz) ────────────────────────────────────────────────

    @Test
    fun `runOnce publishes lastOfferWindow when an offer is produced`() = runTest {
        val store = FakeRebalanceStore()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput() },
            scope = this,
        )

        coordinator.runIfDue()

        val plan = store.current().active!!
        assertEquals(RebalanceStatus.OFFERED, plan.status)
        val window = coordinator.lastOfferWindow.value
        assertNotNull("an offer publishes its weekly-bars window", window)
        assertEquals("7 history days + plan.lengthDays plan days", 7 + plan.lengthDays, window!!.size)

        // The first 7 bars are the trailing-7 history days (today-7 .. today-1), chronological.
        val historyBars = window.take(7)
        assertTrue("history bars are not plan days", historyBars.none { it.isPlanDay })
        val window7 = (1..7).map { today.minusDays(it.toLong()) }.sorted() // today-7 .. today-1
        window7.forEachIndexed { i, d ->
            val expectedEaten = if (d == today.minusDays(1)) 3100 else 2500
            assertEquals("history bar $i valueKcal = eaten that day", expectedEaten, historyBars[i].valueKcal)
            assertEquals("history bar $i targetKcal = base target that day", 2500, historyBars[i].targetKcal)
        }
        assertTrue("the high day drove the offer (at least one over bar)", historyBars.any { it.isOver })

        // The remaining bars are the plan days ahead (all isPlanDay, count == lengthDays).
        val planBars = window.drop(7)
        assertEquals("plan-day count == plan.lengthDays", plan.lengthDays, planBars.size)
        assertTrue("every plan bar is a plan day", planBars.all { it.isPlanDay })
        assertTrue("no plan bar is marked over", planBars.none { it.isOver })
        planBars.forEach {
            assertEquals("plan bar targetKcal = base calories", plan.baseCalories, it.targetKcal)
            assertEquals(
                "plan bar valueKcal = the single floored effective target",
                RebalancePlanMath.effectiveCalories(plan),
                it.valueKcal,
            )
        }
    }

    @Test
    fun `lastOfferWindow is null when the decision is silent`() = runTest {
        // On-target window (no high day) → the engine stays Silent; no offer, no window.
        val store = FakeRebalanceStore()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(yesterdayEaten = 2500) }, // no surplus → Silent
            scope = this,
        )

        coordinator.runIfDue()

        assertNull("silent decision persists no offer", store.current().active)
        assertNull("silent decision clears the offer window", coordinator.lastOfferWindow.value)
    }
}
