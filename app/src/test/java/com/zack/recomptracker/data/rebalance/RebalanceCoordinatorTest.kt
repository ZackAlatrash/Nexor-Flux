package com.zack.recomptracker.data.rebalance

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.rebalance.RebalanceEvaluationInput
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
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
        currentGoal: suspend () -> FitnessGoal? = { FitnessGoal.MODERATE_CUT },
        planVersions: Flow<List<PlanVersion>> = MutableSharedFlow(),
        dateProvider: DateProvider = MutableDateProvider(today),
        scope: CoroutineScope,
    ) = RebalanceCoordinator(
        store = store,
        buildInput = buildInput,
        currentGoal = currentGoal,
        planVersions = planVersions,
        dateProvider = dateProvider,
        usageTracker = null,
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

    // ── customize ─────────────────────────────────────────────────────────────────────

    @Test
    fun `customize recomputes the offer with the current goal and stores sticky mode`() = runTest {
        // currentGoal returns RECOMP → the re-size keeps RECOMP's 3-day length cap. The offer itself is
        // built by the engine (realistic), then customized to EAT_LESS.
        val store = FakeRebalanceStore()
        val coordinator = coordinator(
            store = store,
            buildInput = { cannedInput(goal = FitnessGoal.RECOMP, existing = store.current()) },
            currentGoal = { FitnessGoal.RECOMP },
            scope = this,
        )
        coordinator.runIfDue()
        assertNotNull(store.current().active)

        coordinator.customize(RebalanceMode.EAT_LESS)

        val after = store.current()
        assertNotNull(after.active)
        assertEquals("recomputed plan keeps EAT_LESS", RebalanceMode.EAT_LESS, after.active!!.mode)
        assertTrue("RECOMP caps length at 3 days", after.active!!.lengthDays <= 3)
        assertEquals("sticky mode updated on state", RebalanceMode.EAT_LESS, after.mode)
        assertEquals("still OFFERED after customize", RebalanceStatus.OFFERED, after.active!!.status)
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
        versions.emit(listOf(PlanVersion(today, targets(2400))))
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

        versions.emit(listOf(PlanVersion(LocalDate.parse("2026-06-01"), targets(2500))))
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
}
