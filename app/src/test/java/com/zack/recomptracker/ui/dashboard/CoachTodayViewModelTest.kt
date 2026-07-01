package com.zack.recomptracker.ui.dashboard

import com.zack.recomptracker.data.coach.CoachInbox
import com.zack.recomptracker.domain.coach.CoachAction
import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.Confidence
import com.zack.recomptracker.domain.coach.SignalCategory
import com.zack.recomptracker.domain.coach.SignalFacts
import com.zack.recomptracker.domain.coach.SignalKind
import com.zack.recomptracker.domain.coach.SignalRationale
import com.zack.recomptracker.domain.coach.SignalTier
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoachTodayViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** In-memory [CoachInbox] fake; only the members the ViewModel touches are backed. */
    private class FakeInbox : CoachInbox {
        val flow = MutableStateFlow<CoachSignal?>(null)
        var dismissCount = 0
        override val current: Flow<CoachSignal?> = flow
        override suspend fun stage(signal: CoachSignal?) { flow.value = signal }
        override suspend fun dismissCurrent() { dismissCount++; flow.value = null }
        override suspend fun markSeen(dedupKey: String, date: LocalDate) = Unit
        override suspend fun seenLedger(): Map<String, LocalDate> = emptyMap()
        override suspend fun lastRunDate(): LocalDate? = null
        override suspend fun setLastRunDate(date: LocalDate) = Unit
    }

    private fun signal(
        fallback: String = "Log your weight to keep the trend fresh.",
        action: CoachAction = CoachAction(CoachActionType.LOG_WEIGHT, "Log weight"),
    ) = CoachSignal(
        kind = SignalKind.QUIET_WEIGH_INS,
        tier = SignalTier.P2,
        category = SignalCategory.BODY,
        severity = 20,
        facts = SignalFacts(),
        verdict = "Weigh in soon.",
        action = action,
        rationale = SignalRationale(confidence = Confidence.MEDIUM),
        dedupKey = "quiet_weigh_ins",
        surface = CoachSurface.TODAY,
        fallbackText = fallback,
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `null signal keeps slot hidden`() = runTest(dispatcher) {
        val inbox = FakeInbox()
        val vm = CoachTodayViewModel(inbox, phrase = { it.fallbackText }, onVisibleRefresh = {})
        advanceUntilIdle()
        assertNull(vm.uiState.value.signal)
    }

    @Test
    fun `signal shows fallback first then phrased text`() = runTest(dispatcher) {
        val inbox = FakeInbox()
        val vm = CoachTodayViewModel(
            inbox,
            phrase = { "Phrased: ${it.fallbackText}" },
            onVisibleRefresh = {},
        )
        inbox.flow.value = signal(fallback = "raw fallback")
        // Let the flow emit + fallback seed run, but not the phrasing continuation yet — assert
        // fallback is displayed immediately.
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(SignalKind.QUIET_WEIGH_INS, state.signal?.kind)
        assertEquals("Phrased: raw fallback", state.displayText)
    }

    @Test
    fun `fallback stays when phrasing returns fallback`() = runTest(dispatcher) {
        val inbox = FakeInbox()
        val vm = CoachTodayViewModel(inbox, phrase = { it.fallbackText }, onVisibleRefresh = {})
        inbox.flow.value = signal(fallback = "just the fallback")
        advanceUntilIdle()
        assertEquals("just the fallback", vm.uiState.value.displayText)
        assertTrue(!vm.uiState.value.phrasing)
    }

    @Test
    fun `dismiss clears the inbox`() = runTest(dispatcher) {
        val inbox = FakeInbox()
        val vm = CoachTodayViewModel(inbox, phrase = { it.fallbackText }, onVisibleRefresh = {})
        inbox.flow.value = signal()
        advanceUntilIdle()
        vm.dismiss()
        advanceUntilIdle()
        assertEquals(1, inbox.dismissCount)
        assertNull(vm.uiState.value.signal)
    }

    @Test
    fun `onShown calls the refresh lambda`() = runTest(dispatcher) {
        val inbox = FakeInbox()
        var refreshed = 0
        val vm = CoachTodayViewModel(inbox, phrase = { it.fallbackText }, onVisibleRefresh = { refreshed++ })
        vm.onShown()
        assertEquals(1, refreshed)
    }
}
