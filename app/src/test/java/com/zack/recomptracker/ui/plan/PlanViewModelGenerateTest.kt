package com.zack.recomptracker.ui.plan

import com.zack.recomptracker.core.time.SystemDateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelGenerateTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var planRepo: PlanRepository
    private lateinit var profileStore: UserProfilePreferencesStore
    private lateinit var logRepo: LogRepository

    // birthDate computed at runtime so derived age is always 30 regardless of run date.
    // Jan-1 birthday has already passed by test time, so age == currentYear - birthYear.
    private val completeProfile = UserProfilePreferences(
        heightCm = 180, birthDate = "${LocalDate.now().year - 30}-01-01",
        biologicalSex = BiologicalSex.MALE,
        activityLevel = ActivityLevel.MODERATELY_ACTIVE, goal = FitnessGoal.RECOMP,
    )

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
    fun generateWithCompleteProfileAndLoggedWeightOpensPreview() = runTest(dispatcher) {
        whenever(profileStore.preferences).thenReturn(flowOf(completeProfile))
        whenever(logRepo.observeDailyLogs()).thenReturn(
            flowOf(listOf(DailyLogEntity(date = "2026-06-12", bodyWeightKg = 80.0))),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.generateFromProfile()
        advanceUntilIdle()

        val dialog = vm.uiState.value.generationDialog
        assertTrue(dialog is PlanGenerationDialog.Preview)
        assertEquals(2620, (dialog as PlanGenerationDialog.Preview).plan.targetCalories)
    }

    @Test
    fun generateWithNoLoggedWeightOpensWeightEntry() = runTest(dispatcher) {
        whenever(profileStore.preferences).thenReturn(flowOf(completeProfile))
        whenever(logRepo.observeDailyLogs()).thenReturn(flowOf(emptyList()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.generateFromProfile()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.generationDialog is PlanGenerationDialog.WeightEntry)
    }

    @Test
    fun generateWithMissingProfileSetsMessageNoDialog() = runTest(dispatcher) {
        whenever(profileStore.preferences).thenReturn(flowOf(UserProfilePreferences()))
        whenever(logRepo.observeDailyLogs()).thenReturn(flowOf(emptyList()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.generateFromProfile()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.generationDialog)
        assertTrue(vm.uiState.value.message!!.contains("Height"))
    }

    @Test
    fun submitWeightFromEntryDialogOpensPreview() = runTest(dispatcher) {
        whenever(profileStore.preferences).thenReturn(flowOf(completeProfile))
        whenever(logRepo.observeDailyLogs()).thenReturn(flowOf(emptyList()))
        val vm = viewModel()
        advanceUntilIdle()
        vm.generateFromProfile()
        advanceUntilIdle()

        vm.submitWeight("80")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.generationDialog is PlanGenerationDialog.Preview)
    }

    @Test
    fun submitInvalidWeightKeepsEntryDialogWithError() = runTest(dispatcher) {
        whenever(profileStore.preferences).thenReturn(flowOf(completeProfile))
        whenever(logRepo.observeDailyLogs()).thenReturn(flowOf(emptyList()))
        val vm = viewModel()
        advanceUntilIdle()
        vm.generateFromProfile()
        advanceUntilIdle()

        vm.submitWeight("0")
        advanceUntilIdle()

        val dialog = vm.uiState.value.generationDialog
        assertTrue(dialog is PlanGenerationDialog.WeightEntry)
        assertEquals("Enter a valid weight in kg.", (dialog as PlanGenerationDialog.WeightEntry).error)
    }

    @Test
    fun applyGeneratedPlanPopulatesFieldsAndMarksDirty() = runTest(dispatcher) {
        whenever(profileStore.preferences).thenReturn(flowOf(completeProfile))
        whenever(logRepo.observeDailyLogs()).thenReturn(
            flowOf(listOf(DailyLogEntity(date = "2026-06-12", bodyWeightKg = 80.0))),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.generateFromProfile()
        advanceUntilIdle()

        vm.applyGeneratedPlan()

        val state = vm.uiState.value
        assertEquals("2620", state.targetCalories)
        assertEquals("160", state.targetProteinG)
        assertEquals("2520", state.calorieZoneLowerBound)
        assertEquals("2720", state.calorieZoneUpperBound)
        assertEquals(null, state.generationDialog)
        assertTrue(state.dirty)
    }

    @Test
    fun preferencesRefreshKeepsOpenDialog() = runTest(dispatcher) {
        val prefsFlow = MutableSharedFlow<PlanPreferences>(replay = 1)
        prefsFlow.emit(PlanPreferences())
        whenever(planRepo.preferences).thenReturn(prefsFlow)
        whenever(profileStore.preferences).thenReturn(flowOf(completeProfile))
        whenever(logRepo.observeDailyLogs()).thenReturn(flowOf(emptyList()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.generateFromProfile() // no logged weight -> WeightEntry dialog opens
        advanceUntilIdle()
        assertTrue(vm.uiState.value.generationDialog is PlanGenerationDialog.WeightEntry)

        prefsFlow.emit(PlanPreferences()) // a non-dirty refresh
        advanceUntilIdle()

        assertTrue(vm.uiState.value.generationDialog is PlanGenerationDialog.WeightEntry)
    }
}
