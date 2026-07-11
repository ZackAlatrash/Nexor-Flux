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
import org.junit.Assert.assertNull
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

    // ── P1-14 ─────────────────────────────────────────────────────────────────

    @Test
    fun absurdHeightBlocksContinue() {
        val vm = vm()
        vm.next()                                // -> step 1
        vm.setSex(BiologicalSex.MALE)
        vm.setBirthDate("1996-03-14")
        vm.setHeight("15")                       // 15 cm — implausible
        assertFalse(vm.uiState.value.canContinue)
        vm.setHeight("300")                      // 300 cm — implausible
        assertFalse(vm.uiState.value.canContinue)
        vm.setHeight("178")                      // plausible
        assertTrue(vm.uiState.value.canContinue)
    }

    @Test
    fun absurdWeightBlocksContinue() {
        val vm = vm()
        vm.fillValid()                           // weight 82.5 → can continue
        assertTrue(vm.uiState.value.canContinue)
        vm.setWeight("10")                       // 10 kg — implausible
        assertFalse(vm.uiState.value.canContinue)
        vm.setWeight("400")                      // 400 kg — implausible
        assertFalse(vm.uiState.value.canContinue)
        vm.setWeight("82.5")
        assertTrue(vm.uiState.value.canContinue)
    }

    @Test
    fun finishClampsAbsurdAdjustedCaloriesAndRecomputesZone() = runTest(dispatcher) {
        val vm = vm()
        vm.fillValid()
        vm.next(); advanceUntilIdle()            // generate -> step 3
        vm.setAdjustedCalories("99999")          // absurd
        vm.finish(); advanceUntilIdle()

        val planCaptor = argumentCaptor<PlanPreferences>()
        verify(planRepo).save(planCaptor.capture())
        assertEquals(6000, planCaptor.firstValue.targetCalories) // clamped to max
        assertEquals(5900, planCaptor.firstValue.calorieZoneLowerBound)
        assertEquals(6100, planCaptor.firstValue.calorieZoneUpperBound)
    }

    @Test
    fun finishRecomputesZoneAroundAdjustedTarget() = runTest(dispatcher) {
        val vm = vm()
        vm.fillValid()
        vm.next(); advanceUntilIdle()            // generate -> step 3
        vm.setAdjustedCalories("2000")
        vm.finish(); advanceUntilIdle()

        val planCaptor = argumentCaptor<PlanPreferences>()
        verify(planRepo).save(planCaptor.capture())
        assertEquals(1900, planCaptor.firstValue.calorieZoneLowerBound)
        assertEquals(2100, planCaptor.firstValue.calorieZoneUpperBound)
    }

    @Test
    fun parseHeightCmImperialConvertsAndBoundsChecks() {
        assertEquals(175, parseHeightCmImperial("5", "9"))   // 69 in → 175 cm
        assertEquals(183, parseHeightCmImperial("6", "0"))   // 72 in → 183 cm
        assertEquals(152, parseHeightCmImperial("5", ""))    // whole feet, blank inches → 60 in → 152 cm
        assertNull(parseHeightCmImperial("", "9"))           // no feet
        assertNull(parseHeightCmImperial("5", "13"))         // inches must be a 0..<12 remainder
        assertNull(parseHeightCmImperial("2", "0"))          // 61 cm — implausibly short
    }

    @Test
    fun imperialHeightSplitParsesFeetAndInches() {
        val vm = vm()
        vm.next()                                // -> step 1
        vm.setSex(BiologicalSex.MALE)
        vm.setBirthDate("1996-03-14")
        vm.setUnits(metric = false)
        vm.setHeightFeet("5")
        vm.setHeightInches("9")
        assertTrue(vm.uiState.value.canContinue)
        assertEquals(175, resolveHeightCm(vm.uiState.value)) // 5'9" = 175 cm
    }

    @Test
    fun changingUnitsClearsImperialHeightSplit() {
        val vm = vm()
        vm.next()
        vm.setUnits(metric = false)
        vm.setHeightFeet("5"); vm.setHeightInches("9")
        vm.setUnits(metric = true)
        assertEquals("", vm.uiState.value.heightFeetInput)
        assertEquals("", vm.uiState.value.heightInchesInput)
    }
}
