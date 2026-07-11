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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * P1-10: the Body/Today check-in must follow the real calendar day. A ViewModel that captured
 * "today" once wrote check-ins under yesterday's date after midnight — these assert the working
 * date advances with [com.zack.recomptracker.core.time.DateProvider.todayFlow].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelReactiveTodayTest {

    private val dispatcher = StandardTestDispatcher()
    private val day1 = LocalDate.of(2026, 7, 11)
    private val day2 = LocalDate.of(2026, 7, 12)
    private val provider = FakeDateProvider(day1)

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
        whenever(logRepo.observeDay(any())).thenReturn(flowOf(emptyDayLog(day1)))
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
    fun `saveMetrics writes under the new day after midnight rolls`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        // Simulate the calendar day rolling over while the tab ViewModel stays alive.
        provider.advanceTo(day2)
        advanceUntilIdle()

        vm.onBodyWeightChanged("80.0")
        vm.saveMetrics()
        advanceUntilIdle()

        verifyBlocking(logRepo) { saveDailyMetrics(argThat { date == day2 }) }
    }

    @Test
    fun `working date advances when the day rolls`() = runTest {
        val vm = buildVm()
        val seen = mutableListOf<LocalDate>()
        val job = backgroundScope.launch { vm.uiState.collect { seen.add(it.date) } }
        advanceUntilIdle()
        assertEquals(day1, seen.last())

        provider.advanceTo(day2)
        advanceUntilIdle()
        assertEquals(day2, seen.last())
        job.cancel()
    }
}
