package com.zack.recomptracker.ui.plan

import com.zack.recomptracker.core.time.SystemDateProvider
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The calorie zone is a band around the calorie target. Editing the target must re-centre the
 * zone to target ± [PlanPreferences.CALORIE_ZONE_MARGIN]; otherwise the dashboard/food zone looks
 * disconnected from the plan. See fix/cards-not-connected-to-plan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelZoneTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var planRepo: PlanRepository
    private lateinit var profileStore: UserProfilePreferencesStore
    private lateinit var logRepo: LogRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        planRepo = mock()
        profileStore = mock()
        logRepo = mock()
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PlanViewModel(planRepo, profileStore, logRepo, SystemDateProvider())

    @Test
    fun editingCaloriesRecentresZone() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateTargetCalories("2800")

        val state = vm.uiState.value
        assertEquals("2800", state.targetCalories)
        assertEquals("2700", state.calorieZoneLowerBound)
        assertEquals("2900", state.calorieZoneUpperBound)
    }

    @Test
    fun editingCaloriesToNonNumberLeavesZoneUntouched() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        // establish a known zone first
        vm.updateTargetCalories("2800")

        vm.updateTargetCalories("28x")

        val state = vm.uiState.value
        assertEquals("28x", state.targetCalories)
        // zone stays at the last valid recompute rather than being cleared
        assertEquals("2700", state.calorieZoneLowerBound)
        assertEquals("2900", state.calorieZoneUpperBound)
    }

    @Test
    fun withCalorieTargetRecentresZoneOnPreferences() {
        val updated = PlanPreferences().withCalorieTarget(3000)

        assertEquals(3000, updated.targetCalories)
        assertEquals(2900, updated.calorieZoneLowerBound)
        assertEquals(3100, updated.calorieZoneUpperBound)
    }
}
