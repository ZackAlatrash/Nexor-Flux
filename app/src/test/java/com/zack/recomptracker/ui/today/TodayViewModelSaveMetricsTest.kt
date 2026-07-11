package com.zack.recomptracker.ui.today

import com.zack.recomptracker.ai.StubInsightCoordinator
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.FakeDateProvider
import com.zack.recomptracker.data.health.HealthSyncCoordinator
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * P1-11 contract guard: an invalid steps entry must NOT be treated as a successful save. saveMetrics
 * has to leave a message, skip the DB write, and stay silent on savedEvent — that is what lets the
 * check-in sheet keep the whole check-in and surface the error instead of dismissing and dropping it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelSaveMetricsTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 7, 11)
    private val provider = FakeDateProvider(today)

    private lateinit var logRepo: LogRepository
    private lateinit var planRepo: PlanRepository
    private lateinit var health: HealthSyncCoordinator
    private lateinit var insight: StubInsightCoordinator

    private fun emptyDayLog(date: LocalDate) =
        DayLog(date = date, dailyLog = null, meals = emptyList(), totals = MacroTotals())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        logRepo = mock()
        planRepo = mock()
        health = mock()
        insight = StubInsightCoordinator(flowOf(false), scope = CoroutineScope(dispatcher))
        whenever(logRepo.observeDay(any())).thenReturn(flowOf(emptyDayLog(today)))
        whenever(logRepo.observeSlots()).thenReturn(flowOf(emptyList()))
        whenever(logRepo.observeDailyLogs()).thenReturn(flowOf(emptyList()))
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm() = TodayViewModel(
        logRepository = logRepo,
        planRepository = planRepo,
        dateProvider = provider,
        healthSyncCoordinator = health,
        aiInsightCoordinator = insight,
        computeDispatcher = dispatcher,
    )

    @Test
    fun `invalid steps sets a message, skips the write, and does not signal saved`() = runTest {
        val vm = buildVm()
        val messages = mutableListOf<String?>()
        val saved = mutableListOf<Unit>()
        val subs = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            launch { vm.uiState.collect { messages.add(it.message) } }
            launch { vm.savedEvent.collect { saved.add(it) } }
        }
        advanceUntilIdle()

        vm.onBodyWeightChanged("80.0")
        vm.onStepsChanged("12k") // typo — not a whole number
        vm.saveMetrics()
        advanceUntilIdle()

        assertNotNull("expected a validation message", messages.last())
        assertTrue("must not signal a successful save", saved.isEmpty())
        verifyBlocking(logRepo, never()) { saveDailyMetrics(any()) }
        subs.cancel()
    }

    @Test
    fun `valid input writes the metrics and signals saved`() = runTest {
        val vm = buildVm()
        val saved = mutableListOf<Unit>()
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.savedEvent.collect { saved.add(it) }
        }
        advanceUntilIdle()

        vm.onBodyWeightChanged("80.0")
        vm.onStepsChanged("12000")
        vm.saveMetrics()
        advanceUntilIdle()

        verifyBlocking(logRepo) { saveDailyMetrics(argThat { date == today && steps == 12000 }) }
        assertEquals(1, saved.size)
        sub.cancel()
    }
}
