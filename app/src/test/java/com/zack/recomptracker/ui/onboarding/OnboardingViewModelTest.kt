package com.zack.recomptracker.ui.onboarding

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.DailyMetricsInput
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 6, 16)

    private lateinit var profileStore: UserProfilePreferencesStore
    private lateinit var planRepo: PlanRepository
    private lateinit var logRepo: LogRepository
    private lateinit var uiPrefs: UiPreferences
    private lateinit var dateProvider: DateProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        profileStore = mock()
        planRepo = mock()
        logRepo = mock()
        uiPrefs = mock()
        dateProvider = mock()
        whenever(dateProvider.today()).thenReturn(today)
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnboardingViewModel(profileStore, planRepo, logRepo, uiPrefs, dateProvider)

    /** Drive a VM to a fully-valid pre-generation state (still on step 2). */
    private fun OnboardingViewModel.fillValid() {
        setName("Zack")
        next()                                   // step 0 -> 1
        setSex(BiologicalSex.MALE)
        setBirthDate("1996-03-14")
        setHeight("178")
        next()                                   // step 1 -> 2
        setGoal(FitnessGoal.RECOMP)
        setActivityLevel(ActivityLevel.MODERATELY_ACTIVE)
        setWeight("82.5")
    }

    @Test
    fun initialStepIsZeroAndCanContinue() {
        val vm = vm()
        assertEquals(0, vm.uiState.value.step)
        assertTrue(vm.uiState.value.canContinue) // step 0 only needs units (defaulted)
    }

    @Test
    fun bodyStepBlocksUntilRequiredFieldsValid() {
        val vm = vm()
        vm.next()                                // -> step 1
        assertEquals(1, vm.uiState.value.step)
        assertFalse(vm.uiState.value.canContinue)
        vm.setSex(BiologicalSex.MALE)
        vm.setBirthDate("1996-03-14")
        assertFalse(vm.uiState.value.canContinue) // height still missing
        vm.setHeight("178")
        assertTrue(vm.uiState.value.canContinue)
    }

    @Test
    fun changingUnitsClearsMeasurementInputs() {
        val vm = vm()
        vm.next(); vm.setHeight("178")
        vm.setUnits(metric = false)
        assertEquals("", vm.uiState.value.heightInput)
        assertFalse(vm.uiState.value.useMetricUnits)
    }

    @Test
    fun nextFromMeasurementsGeneratesPlanAndAdvances() = runTest(dispatcher) {
        val vm = vm()
        vm.fillValid()
        vm.next()                                // step 2 -> generate
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.generatedPlan)
        assertTrue(vm.uiState.value.generatedPlan!!.targetCalories > 0)
    }

    @Test
    fun finishPersistsProfilePlanLogAndFlag() = runTest(dispatcher) {
        val vm = vm()
        vm.fillValid()
        vm.next(); advanceUntilIdle()            // generate -> step 3
        vm.finish(); advanceUntilIdle()

        // Profile saved with the entered fields.
        val profileCaptor = argumentCaptor<UserProfilePreferences>()
        verify(profileStore).save(profileCaptor.capture())
        assertEquals(178, profileCaptor.firstValue.heightCm)
        assertEquals(BiologicalSex.MALE, profileCaptor.firstValue.biologicalSex)
        assertEquals(FitnessGoal.RECOMP, profileCaptor.firstValue.goal)
        assertEquals("Zack", profileCaptor.firstValue.name)

        // Plan targets saved (computed, non-zero).
        val planCaptor = argumentCaptor<PlanPreferences>()
        verify(planRepo).save(planCaptor.capture())
        assertTrue(planCaptor.firstValue.targetCalories > 0)
        assertTrue(planCaptor.firstValue.targetProteinG > 0)

        // Today's weight written to the daily log.
        val logCaptor = argumentCaptor<DailyMetricsInput>()
        verify(logRepo).saveDailyMetrics(logCaptor.capture())
        assertEquals(today, logCaptor.firstValue.date)
        assertEquals(82.5, logCaptor.firstValue.bodyWeightKg!!, 0.001)

        // Flag flipped, finished signalled.
        verify(uiPrefs).setOnboardingComplete(true)
        assertTrue(vm.uiState.value.finished)
    }

    @Test
    fun adjustedTargetsOverrideComputedOnFinish() = runTest(dispatcher) {
        val vm = vm()
        vm.fillValid()
        vm.next(); advanceUntilIdle()            // generate -> step 3
        vm.setAdjustedCalories("2000")
        vm.finish(); advanceUntilIdle()

        val planCaptor = argumentCaptor<PlanPreferences>()
        verify(planRepo).save(planCaptor.capture())
        assertEquals(2000, planCaptor.firstValue.targetCalories)
    }
}
