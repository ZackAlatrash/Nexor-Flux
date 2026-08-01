package com.zack.recomptracker.ui.dashboard

import com.zack.recomptracker.ai.RebalanceCopyService
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.rebalance.FakeRebalanceStore
import com.zack.recomptracker.data.rebalance.RebalanceCoordinator
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import com.zack.recomptracker.data.usage.NoOpUsageTracker
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.rebalance.RebalanceEvaluationInput
import com.zack.recomptracker.domain.rebalance.RebalanceIntensity
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.toKotlinLocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors [CoachTodayViewModelTest]'s shape: [StandardTestDispatcher] + `Dispatchers.setMain`, a
 * canned [RebalanceEvaluationInput] a test can tweak, and a REAL [RebalanceCoordinator] wired to
 * [FakeRebalanceStore] + trivial fakes for `buildInput`/`currentGoal`/`planVersions` — lighter than
 * hand-rolling a fake coordinator, since the transitions under test (accept-shaped day math,
 * decline, the cancel-on-edit hook) are exactly what
 * `com.zack.recomptracker.data.rebalance.RebalanceCoordinatorTest` already exercises against the
 * real class. Copy-service success/failure is faked by subclassing [OpenAiCompatClient] (it is
 * `open`) — the same pattern `RebalanceCopyServiceTest` uses, no mocking library needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RebalanceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 7, 8) // Wednesday

    private class FixedDateProvider(private val date: LocalDate) : DateProvider {
        override fun today(): LocalDate = date
    }

    private fun targets(cal: Int) = PlanTargets(cal, 160, 300, 70, cal - 100, cal + 100)

    /** A canned high-yesterday input, mirroring `RebalanceCoordinatorTest`'s `cannedInput`. */
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

    /** Emits a fixed chunk (or nothing) when asked to phrase; tracks whether it was invoked. */
    private class FakeClient(private val chunk: String? = null) : OpenAiCompatClient() {
        var wasCalled: Boolean = false
            private set

        override fun streamCompletion(
            config: CloudConfig,
            systemPrompt: String,
            userPrompt: String,
        ): Flow<String> = flow {
            wasCalled = true
            chunk?.let { emit(it) }
        }

        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse = ParsedChatResponse("", emptyList())
    }

    /** A [RebalanceCopyService] that never has cloud config — deterministically returns the fallback. */
    private fun fallbackOnlyCopyService(): RebalanceCopyService =
        RebalanceCopyService(client = FakeClient()) { null }

    /**
     * A client whose [streamCompletion] flow blocks on a per-call gate before emitting, so a test can
     * hold each phrasing job suspended and release them in a chosen order — the control needed to
     * exercise the phrasing interleaving guard (a superseded job's `finally` must not clear the flag a
     * newer job raised). Each call appends its gate to [gates] in start order.
     */
    private class SuspendingCopyClient : OpenAiCompatClient() {
        val gates = mutableListOf<CompletableDeferred<Unit>>()

        override fun streamCompletion(
            config: CloudConfig,
            systemPrompt: String,
            userPrompt: String,
        ): Flow<String> = flow {
            val gate = CompletableDeferred<Unit>()
            gates.add(gate)
            gate.await() // suspend until the test releases this specific job
            emit("phrased ${gates.size}")
        }

        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse = ParsedChatResponse("", emptyList())
    }

    private fun configuredCopyService(client: OpenAiCompatClient): RebalanceCopyService =
        RebalanceCopyService(client = client) {
            CloudConfig(baseUrl = "https://example.test", apiKey = { "key" }, model = "test-model")
        }

    private fun coordinator(
        store: FakeRebalanceStore,
        scope: CoroutineScope,
        buildInput: suspend () -> RebalanceEvaluationInput = { cannedInput(existing = store.current()) },
        planVersions: Flow<List<PlanVersion>> = MutableSharedFlow(),
        dateProvider: DateProvider = FixedDateProvider(today),
    ) = RebalanceCoordinator(
        store = store,
        buildInput = buildInput,
        planVersions = planVersions,
        dateProvider = dateProvider,
        usageTracker = NoOpUsageTracker,
        scope = scope,
        newId = newId,
        nowIso = nowIso,
    )

    private fun offeredPlan(mode: RebalanceMode = RebalanceMode.BALANCED) = RebalancePlan(
        id = "offer", triggerDateIso = today.minusDays(1).toString(),
        startDateIso = today.toString(), endDateIso = today.plusDays(2).toString(),
        lengthDays = 3, mode = mode, baseCalories = 2500, dailyCalorieReduction = 200,
        extraDailySteps = 0, baseStepGoal = null, recentAvgSteps = null,
        surplusKcal = 600, recoveredKcal = 600, status = RebalanceStatus.OFFERED,
        createdAtIso = "2026-07-08T09:00:00Z",
    )

    private fun activePlan(
        startDateIso: String,
        endDateIso: String,
        lengthDays: Int,
        dailyCalorieReduction: Int = 200,
        extraDailySteps: Int = 0,
    ) = RebalancePlan(
        id = "active", triggerDateIso = today.minusDays(3).toString(),
        startDateIso = startDateIso, endDateIso = endDateIso, lengthDays = lengthDays,
        mode = RebalanceMode.BALANCED, baseCalories = 2500,
        dailyCalorieReduction = dailyCalorieReduction, extraDailySteps = extraDailySteps,
        baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
        status = RebalanceStatus.ACTIVE, createdAtIso = "2026-07-05T09:00:00Z",
        decidedAtIso = "2026-07-05T09:00:00Z",
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `offer state renders the offer face with fallback copy`() = runTest(dispatcher) {
        val store = FakeRebalanceStore(seed = RebalanceState(active = offeredPlan()))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(RebalanceCardUiState.Face.OFFER, state.face)
        assertEquals("Your weekly goal is still within reach.", state.headline)
        assertTrue(
            "offer body mentions the daily reduction",
            state.body.contains("200 kcal less a day"),
        )
        assertEquals(3, state.ofY)
    }

    @Test
    fun `accept flips to the progress face with day math`() = runTest(dispatcher) {
        // Active 3-day plan: start = yesterday, end = tomorrow → today is day 2 of 3 (the shape
        // RebalanceCoordinator.accept() produces once a day has passed since acceptance).
        val plan = activePlan(
            startDateIso = today.minusDays(1).toString(),
            endDateIso = today.plusDays(1).toString(),
            lengthDays = 3,
            dailyCalorieReduction = 200,
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = plan))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(RebalanceCardUiState.Face.PROGRESS, state.face)
        assertEquals(2, state.dayX)
        assertEquals(3, state.ofY)
        assertEquals(2f / 3f, state.progressFraction, 0.0001f)
        assertEquals(2300, state.effectiveCalories) // 2500 - 200
        assertTrue(state.body.contains("2 of 3 days in"))
    }

    @Test
    fun `active plan before its start date shows the starts-tomorrow face`() = runTest(dispatcher) {
        // Accepted late: start = tomorrow, so today falls before the window (spec §10).
        val plan = activePlan(
            startDateIso = today.plusDays(1).toString(),
            endDateIso = today.plusDays(3).toString(),
            lengthDays = 3,
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = plan))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(RebalanceCardUiState.Face.PROGRESS, state.face)
        assertEquals(0, state.dayX)
        assertEquals(
            "Your rebalance starts tomorrow — today stays your normal plan.",
            state.body,
        )
    }

    @Test
    fun `decline clears the card`() = runTest(dispatcher) {
        val store = FakeRebalanceStore(seed = RebalanceState(active = offeredPlan()))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()
        assertEquals(RebalanceCardUiState.Face.OFFER, vm.uiState.value.face)

        vm.onDecline()
        advanceUntilIdle()

        assertEquals(RebalanceCardUiState.Face.NONE, vm.uiState.value.face)
        assertEquals(RebalanceStatus.DECLINED, store.current().history.single().status)
    }

    @Test
    fun `no-adjustment note face dismisses via the coordinator`() = runTest(dispatcher) {
        val note = offeredPlan().copy(
            id = "note", status = RebalanceStatus.NO_ADJUSTMENT,
            dailyCalorieReduction = 0, extraDailySteps = 0,
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = note))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()
        assertEquals(RebalanceCardUiState.Face.NOTE, vm.uiState.value.face)

        vm.onDismiss()
        advanceUntilIdle()

        assertEquals(RebalanceCardUiState.Face.NONE, vm.uiState.value.face)
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, store.current().history.single().status)
        assertNull(store.current().active)
    }

    @Test
    fun `plan-edited ended notice is auto-dismissed and never shown`() = runTest(dispatcher) {
        val versions = MutableSharedFlow<List<PlanVersion>>(replay = 0)
        val active = activePlan(
            startDateIso = today.minusDays(2).toString(),
            endDateIso = today.plusDays(2).toString(),
            lengthDays = 5,
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = active))
        val scope = CoroutineScope(dispatcher)
        val coord = coordinator(store, scope = scope, planVersions = versions)
        coord.start()
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        // Initial (drop(1)) emission never cancels; a real version change does → "plan_edited".
        versions.emit(emptyList())
        advanceUntilIdle()
        versions.emit(listOf(PlanVersion(today.toKotlinLocalDate(), targets(2400))))
        advanceUntilIdle()

        assertEquals(
            "the plan really did end as plan_edited (sanity check on the fixture)",
            "plan_edited",
            store.current().history.single().endedReason,
        )
        assertEquals(
            "the card never shows the plan-edited note — the VM silently dismisses it",
            RebalanceCardUiState.Face.NONE,
            vm.uiState.value.face,
        )
        assertNull("the ended notice was auto-dismissed, not left pending", coord.endedNotice.value)
    }

    @Test
    fun `phrased copy replaces the fallback when the service succeeds and stays on failure`() = runTest(dispatcher) {
        // Succeeding service: config present, client streams a phrased sentence.
        val store = FakeRebalanceStore(seed = RebalanceState(active = offeredPlan()))
        val coord = coordinator(store, scope = backgroundScope)
        val succeedingClient = FakeClient(chunk = "A rephrased, warmer offer.")
        val succeedingService = RebalanceCopyService(client = succeedingClient) {
            CloudConfig(baseUrl = "https://example.test", apiKey = { "key" }, model = "test-model")
        }
        val vm = RebalanceViewModel(store, coord, succeedingService, FixedDateProvider(today))
        advanceUntilIdle()

        assertTrue("phrasing client was actually invoked", succeedingClient.wasCalled)
        assertEquals("A rephrased, warmer offer.", vm.uiState.value.body)

        // Failing service (no config) → the fallback is what's shown and stays (never overwritten).
        val store2 = FakeRebalanceStore(seed = RebalanceState(active = offeredPlan()))
        val coord2 = coordinator(store2, scope = backgroundScope)
        val vm2 = RebalanceViewModel(store2, coord2, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()
        assertTrue(vm2.uiState.value.body.contains("200 kcal less a day"))
    }

    @Test
    fun `onShown triggers the coordinator once`() = runTest(dispatcher) {
        val store = FakeRebalanceStore()
        var buildCalls = 0
        val coord = coordinator(
            store,
            scope = backgroundScope,
            buildInput = { buildCalls++; cannedInput(existing = store.current()) },
        )
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))

        vm.onShown()
        advanceUntilIdle()
        vm.onShown()
        advanceUntilIdle()

        assertEquals("the VM's own guard fires runIfDue at most once per instance", 1, buildCalls)
    }

    @Test
    fun `empty state renders NONE`() = runTest(dispatcher) {
        val store = FakeRebalanceStore()
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        assertEquals(RebalanceCardUiState.Face.NONE, vm.uiState.value.face)
        assertEquals("", vm.uiState.value.body)
    }

    // ── redesign surface: noteKind ────────────────────────────────────────────────────

    @Test
    fun `completion note sets noteKind COMPLETION`() = runTest(dispatcher) {
        // An ACTIVE plan already past its end → reconcile completes it → COMPLETED ended notice.
        val activePast = activePlan(
            startDateIso = today.minusDays(4).toString(),
            endDateIso = today.minusDays(2).toString(),
            lengthDays = 3,
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = activePast))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        vm.onShown()
        advanceUntilIdle()

        assertEquals(RebalanceStatus.COMPLETED, coord.endedNotice.value!!.status)
        val state = vm.uiState.value
        assertEquals(RebalanceCardUiState.Face.NOTE, state.face)
        assertEquals(NoteKind.COMPLETION, state.noteKind)
    }

    @Test
    fun `ended-early note sets noteKind GRACEFUL_END`() = runTest(dispatcher) {
        // An unrecoverable ACTIVE plan → reconcile ends it early → ENDED_EARLY ended notice.
        val active = activePlan(
            startDateIso = today.minusDays(2).toString(),
            endDateIso = today.plusDays(2).toString(),
            lengthDays = 5,
            dailyCalorieReduction = 100,
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = active))
        val coord = coordinator(
            store,
            scope = backgroundScope,
            buildInput = { cannedInput(yesterdayEaten = 5500, existing = store.current()) },
        )
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        vm.onShown()
        advanceUntilIdle()

        val ended = coord.endedNotice.value!!
        assertEquals(RebalanceStatus.ENDED_EARLY, ended.status)
        assertEquals("unrecoverable", ended.endedReason)
        val state = vm.uiState.value
        assertEquals(RebalanceCardUiState.Face.NOTE, state.face)
        assertEquals(NoteKind.GRACEFUL_END, state.noteKind)
    }

    @Test
    fun `no-adjustment note sets noteKind NO_ADJUSTMENT`() = runTest(dispatcher) {
        val note = offeredPlan().copy(
            id = "note", status = RebalanceStatus.NO_ADJUSTMENT,
            dailyCalorieReduction = 0, extraDailySteps = 0,
        )
        val store = FakeRebalanceStore(seed = RebalanceState(active = note))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(RebalanceCardUiState.Face.NOTE, state.face)
        assertEquals(NoteKind.NO_ADJUSTMENT, state.noteKind)
    }

    @Test
    fun `small-surplus note derives REASSURANCE, large-surplus note derives NO_ADJUSTMENT`() = runTest(dispatcher) {
        // Both are NO_ADJUSTMENT-status notes; the VM tells them apart purely from plan.surplusKcal
        // (spec §16): below SMALL_SURPLUS_KCAL (500) is the reassurance note, at/above is the resume note.
        val reassure = offeredPlan().copy(
            id = "reassure", status = RebalanceStatus.NO_ADJUSTMENT,
            dailyCalorieReduction = 0, extraDailySteps = 0, surplusKcal = 300,
        )
        val reassureStore = FakeRebalanceStore(seed = RebalanceState(active = reassure))
        val reassureVm = RebalanceViewModel(
            reassureStore, coordinator(reassureStore, scope = backgroundScope),
            fallbackOnlyCopyService(), FixedDateProvider(today),
        )
        advanceUntilIdle()
        assertEquals(NoteKind.REASSURANCE, reassureVm.uiState.value.noteKind)

        val resume = offeredPlan().copy(
            id = "resume", status = RebalanceStatus.NO_ADJUSTMENT,
            dailyCalorieReduction = 0, extraDailySteps = 0, surplusKcal = 5000,
        )
        val resumeStore = FakeRebalanceStore(seed = RebalanceState(active = resume))
        val resumeVm = RebalanceViewModel(
            resumeStore, coordinator(resumeStore, scope = backgroundScope),
            fallbackOnlyCopyService(), FixedDateProvider(today),
        )
        advanceUntilIdle()
        assertEquals(NoteKind.NO_ADJUSTMENT, resumeVm.uiState.value.noteKind)
    }

    // ── redesign surface: intensity + partial + second dial ─────────────────────────────

    @Test
    fun `offer face carries intensity and partial from the plan`() = runTest(dispatcher) {
        val plan = offeredPlan().copy(intensity = RebalanceIntensity.FULL, partial = true)
        val store = FakeRebalanceStore(seed = RebalanceState(active = plan))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(RebalanceCardUiState.Face.OFFER, state.face)
        assertEquals(RebalanceIntensity.FULL, state.intensity)
        assertTrue("partial threads through to the OFFER face", state.partial)
    }

    @Test
    fun `onCustomizeIntensity recomputes the offer at the new intensity keeping the current mode`() = runTest(dispatcher) {
        // A real STANDARD offer (S = 600 → 230 kcal); switching to FULL via the second dial delegates to
        // the coordinator and recomputes to a heavier plan without changing the mode.
        val store = FakeRebalanceStore()
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()
        vm.onShown()
        advanceUntilIdle()
        assertEquals(RebalanceCardUiState.Face.OFFER, vm.uiState.value.face)
        assertEquals(RebalanceIntensity.STANDARD, vm.uiState.value.intensity)
        val modeBefore = vm.uiState.value.mode

        vm.onCustomizeIntensity(RebalanceIntensity.FULL)
        advanceUntilIdle()

        val after = store.current().active!!
        assertEquals("intensity applied to the plan", RebalanceIntensity.FULL, after.intensity)
        assertEquals("mode unchanged by the intensity dial", modeBefore, after.mode)
        assertEquals("FULL recovers more per day than STANDARD", 300, after.dailyCalorieReduction)
        assertEquals(RebalanceStatus.OFFERED, after.status)
    }

    // ── redesign surface: weekly bars ──────────────────────────────────────────────────

    @Test
    fun `offer face carries the weekly bars from the coordinator`() = runTest(dispatcher) {
        // Drive a real offer through runIfDue so the coordinator publishes lastOfferWindow; the VM
        // threads that window into the OFFER face's weeklyBars.
        val store = FakeRebalanceStore()
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        vm.onShown()
        advanceUntilIdle()

        val offerState = vm.uiState.value
        assertEquals(RebalanceCardUiState.Face.OFFER, offerState.face)
        assertTrue("OFFER face carries the coordinator's weekly bars", offerState.weeklyBars.isNotEmpty())
        assertEquals("baseCalories surfaced for the OFFER face", 2500, offerState.baseCalories)

        // A progress state (no offer window) → weeklyBars empty.
        val activeStore = FakeRebalanceStore(
            seed = RebalanceState(
                active = activePlan(
                    startDateIso = today.minusDays(1).toString(),
                    endDateIso = today.plusDays(1).toString(),
                    lengthDays = 3,
                ),
            ),
        )
        val activeCoord = coordinator(activeStore, scope = backgroundScope)
        val activeVm = RebalanceViewModel(activeStore, activeCoord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        val progressState = activeVm.uiState.value
        assertEquals(RebalanceCardUiState.Face.PROGRESS, progressState.face)
        assertTrue("PROGRESS face has no weekly bars", progressState.weeklyBars.isEmpty())
    }

    // ── redesign surface: offer minimize/expand ────────────────────────────────────────

    @Test
    fun `minimize then expand toggles offerMinimized and never declines`() = runTest(dispatcher) {
        val store = FakeRebalanceStore(seed = RebalanceState(active = offeredPlan()))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()
        assertEquals(RebalanceCardUiState.Face.OFFER, vm.uiState.value.face)
        assertTrue("a fresh offer starts expanded", !vm.offerMinimized.value)

        vm.onMinimizeOffer()
        advanceUntilIdle()
        assertTrue("minimize sets the flag", vm.offerMinimized.value)

        vm.onExpandOffer()
        advanceUntilIdle()
        assertTrue("expand clears the flag", !vm.offerMinimized.value)

        // Minimize/expand must never mutate the plan (never decline).
        assertEquals("the offer is still active, never declined", RebalanceStatus.OFFERED, store.current().active!!.status)
        assertTrue("nothing was archived to history", store.current().history.isEmpty())
    }

    @Test
    fun `a fresh offer resets offerMinimized to false`() = runTest(dispatcher) {
        val store = FakeRebalanceStore(seed = RebalanceState(active = offeredPlan()))
        val coord = coordinator(store, scope = backgroundScope)
        val vm = RebalanceViewModel(store, coord, fallbackOnlyCopyService(), FixedDateProvider(today))
        advanceUntilIdle()

        vm.onMinimizeOffer()
        advanceUntilIdle()
        assertTrue("minimized", vm.offerMinimized.value)

        // A brand-new offer (different plan id) replaces the current one → auto-expand.
        store.save(RebalanceState(active = offeredPlan().copy(id = "offer-2")))
        advanceUntilIdle()

        assertEquals(RebalanceCardUiState.Face.OFFER, vm.uiState.value.face)
        assertTrue("a new offer id resets minimize to expanded", !vm.offerMinimized.value)
    }

    // ── redesign surface: phrasing edge-glow interleaving guard ─────────────────────────

    @Test
    fun `phrasing stays raised when a superseded copy job completes after a newer one starts`() = runTest(dispatcher) {
        // Offer A (job A) and a distinct offer B (job B, different reduction → distinct body). Job A is
        // superseded by B before it finishes; A's finally must NOT clear the glow B just raised
        // (guarded by phrasingJob === job), and only B's completion clears it. Drive with runCurrent()
        // (not advanceUntilIdle) so virtual time never advances through copy()'s 15s withTimeout —
        // that would time the "suspended" jobs out instead of leaving them parked on their gates.
        val store = FakeRebalanceStore(seed = RebalanceState(active = offeredPlan()))
        val coord = coordinator(store, scope = backgroundScope)
        val client = SuspendingCopyClient()
        val vm = RebalanceViewModel(store, coord, configuredCopyService(client), FixedDateProvider(today))
        runCurrent()

        // Job A is in flight (its phrasing gate is registered) and the glow is up.
        assertEquals(RebalanceCardUiState.Face.OFFER, vm.uiState.value.face)
        assertEquals("job A's phrasing call is parked on its gate", 1, client.gates.size)
        assertTrue("the glow is raised while A phrases", vm.phrasing.value)

        // A NEW offer (distinct id + reduction) supersedes A → cancels job A, starts job B.
        store.save(RebalanceState(active = offeredPlan().copy(id = "offer-2", dailyCalorieReduction = 300)))
        runCurrent()

        // Job B is now the in-flight phrasing job; A has unwound (cancelled). The glow must remain up —
        // A's finally saw phrasingJob === jobB (not jobA) and did NOT clear it.
        assertEquals("job B's phrasing call is now parked on its own gate", 2, client.gates.size)
        assertTrue("the glow stays raised: A's finally must not stomp B's flag", vm.phrasing.value)

        // Releasing the (already superseded) job A changes nothing — it is no longer the current job.
        client.gates[0].complete(Unit)
        runCurrent()
        assertTrue("a superseded job settling leaves the glow up", vm.phrasing.value)

        // Only the current job (B) finishing clears the glow.
        client.gates[1].complete(Unit)
        runCurrent()
        assertFalse("the current job finishing clears the glow", vm.phrasing.value)
    }
}
