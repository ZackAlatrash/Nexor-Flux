# First-Run Onboarding Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a guided 4-screen first-run onboarding that collects the 9 fields the plan engine needs, reveals the auto-computed calorie/macro plan, persists everything, and never shows again.

**Architecture:** A single `OnboardingViewModel` holds draft state for all screens, generates the plan via the existing `PlanGenerator`, and on finish writes `UserProfilePreferences` + `PlanPreferences` + today's `daily_logs` row + an `onboardingComplete` flag. A stateless-ish `OnboardingScreen` renders the shared step frame (Direction B: step counter + thin progress bar + grouped glass card + bottom CTA). `RecompApp` gates the nav start destination on the flag; the bottom nav auto-hides because the onboarding route is not a top-level route.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, ViewModel + StateFlow, DataStore (Preferences), Room, manual DI via `AppContainer`. Tests: JUnit4 + mockito-kotlin + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-16-onboarding-flow-design.md`

---

## File Structure

**Create:**
- `app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModel.kt` — UI state, pure parse/convert helpers (`internal`, file-top), and the ViewModel (draft state, step nav, plan generation, finish/persist).
- `app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingScreen.kt` — the 4-step Compose UI + local bottom-sheet/date-picker helpers.
- `app/src/test/java/com/zack/recomptracker/ui/onboarding/OnboardingHelpersTest.kt` — unit tests for the parse/convert helpers.
- `app/src/test/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModelTest.kt` — unit tests for step gating, generation, and finish persistence.

**Modify:**
- `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt` — add `onboardingComplete` flag to `UiPreferences`.
- `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` — construct `OnboardingViewModel` in the factory.
- `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt` — add `Routes.Onboarding`, a `startDestination` param, and the onboarding `composable`.
- `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt` — read the flag, gate the nav host start destination.

**Key reused APIs (verified against current code):**
- `PlanGenerator().generate(profile: UserProfilePreferences, weightKg: Double?, today: LocalDate): PlanGenerationOutcome` → `Ready(plan: GeneratedPlan)` | `NeedsWeight` | `MissingProfileFields(fields: List<String>)`.
- `GeneratedPlan` fields used: `targetCalories, proteinG, carbsG, fatG, zoneLower, zoneUpper` (all `Int`).
- `UserProfilePreferencesStore.preferences: Flow<UserProfilePreferences>`, `suspend fun save(profile: UserProfilePreferences)`.
- `PlanRepository.preferences: Flow<PlanPreferences>`, `suspend fun save(preferences: PlanPreferences)`.
- `LogRepository.saveDailyMetrics(input: DailyMetricsInput)` where `DailyMetricsInput(date: LocalDate, bodyWeightKg: Double?, waistCm: Double?, waistSkinfoldMm: Double? = null, steps: Int?, sleepHours: Double?, energyScore: Int?, hungerScore: Int?, sorenessScore: Int?, trained: Boolean, notes: String)`.
- `DateProvider.today(): LocalDate`.
- Enums: `BiologicalSex { MALE, FEMALE }`, `ActivityLevel { SEDENTARY, LIGHTLY_ACTIVE, MODERATELY_ACTIVE, VERY_ACTIVE }`, `FitnessGoal { AGGRESSIVE_CUT, MODERATE_CUT, MINI_CUT, RECOMP, LEAN_BULK, MODERATE_BULK, AGGRESSIVE_BULK }`.
- Components: `GlassInputField(label, value, onValueChange, modifier, unit, keyboardType)`, `FrostedCard { }`, `NeutralCard { }`, `SectionLabel(text)`, `LiquidPrimaryButton(text, onClick, modifier, enabled)`, `LiquidSecondaryButton(text, onClick, modifier, enabled)`.

---

## Task 1: Onboarding parse/convert helpers (pure, TDD)

These convert raw input strings to canonical metric values, honoring the metric/imperial toggle, and validate birth date. They live at the top of `OnboardingViewModel.kt` as `internal` top-level functions so both the ViewModel and the test (same package) can use them.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModel.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/onboarding/OnboardingHelpersTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/onboarding/OnboardingHelpersTest.kt`:

```kotlin
package com.zack.recomptracker.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnboardingHelpersTest {

    @Test
    fun heightMetricParsesDirectly() {
        assertEquals(178, parseHeightCm("178", metric = true))
    }

    @Test
    fun heightImperialConvertsInchesToCm() {
        // 70 in * 2.54 = 177.8 cm -> rounds to 178
        assertEquals(178, parseHeightCm("70", metric = false))
    }

    @Test
    fun heightBlankOrInvalidIsNull() {
        assertNull(parseHeightCm("", metric = true))
        assertNull(parseHeightCm("abc", metric = true))
    }

    @Test
    fun weightMetricParsesDecimal() {
        assertEquals(82.5, parseWeightKg("82.5", metric = true)!!, 0.001)
    }

    @Test
    fun weightImperialConvertsPoundsToKg() {
        // 180 lb * 0.45359237 = 81.6466... kg
        assertEquals(81.6466, parseWeightKg("180", metric = false)!!, 0.001)
    }

    @Test
    fun waistImperialConvertsInchesToCm() {
        assertEquals(86.36, parseWaistCm("34", metric = false)!!, 0.001)
        assertNull(parseWaistCm("", metric = true))
    }

    @Test
    fun ageYearsFromBirthDate() {
        val today = LocalDate.of(2026, 6, 16)
        assertEquals(30, ageYearsFrom("1996-03-14", today))
        assertNull(ageYearsFrom(null, today))
        assertNull(ageYearsFrom("not-a-date", today))
    }

    @Test
    fun birthDateValidWhenPlausibleAge() {
        val today = LocalDate.of(2026, 6, 16)
        assertTrue(isValidBirthDate("1996-03-14", today))
    }

    @Test
    fun birthDateInvalidWhenFutureOrImplausible() {
        val today = LocalDate.of(2026, 6, 16)
        assertFalse(isValidBirthDate("2030-01-01", today)) // future
        assertFalse(isValidBirthDate("2020-01-01", today)) // age 6 < 13
        assertFalse(isValidBirthDate(null, today))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.onboarding.OnboardingHelpersTest"`
Expected: FAIL — compilation error, `parseHeightCm`/etc. unresolved.

- [ ] **Step 3: Write the minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModel.kt` with ONLY the helpers for now:

```kotlin
package com.zack.recomptracker.ui.onboarding

import java.time.LocalDate
import java.time.Period

private const val CM_PER_INCH = 2.54
private const val KG_PER_POUND = 0.45359237
private const val MIN_AGE = 13
private const val MAX_AGE = 120

/** Height in canonical centimetres from raw input. Metric = cm; imperial = whole inches. */
internal fun parseHeightCm(input: String, metric: Boolean): Int? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    val cm = if (metric) value else value * CM_PER_INCH
    return Math.round(cm).toInt()
}

/** Weight in canonical kilograms from raw input. Metric = kg; imperial = pounds. */
internal fun parseWeightKg(input: String, metric: Boolean): Double? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    return if (metric) value else value * KG_PER_POUND
}

/** Waist in canonical centimetres from raw input. Metric = cm; imperial = inches. Optional → null. */
internal fun parseWaistCm(input: String, metric: Boolean): Double? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    return if (metric) value else value * CM_PER_INCH
}

/** Whole years from an ISO `yyyy-MM-dd` birth date, or null if unset/unparseable/future. */
internal fun ageYearsFrom(birthDate: String?, today: LocalDate): Int? {
    val dob = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (dob.isAfter(today)) return null
    return Period.between(dob, today).years
}

/** A birth date that parses, is not in the future, and yields a plausible age. */
internal fun isValidBirthDate(birthDate: String?, today: LocalDate): Boolean {
    val age = ageYearsFrom(birthDate, today) ?: return false
    return age in MIN_AGE..MAX_AGE
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.onboarding.OnboardingHelpersTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/onboarding/OnboardingHelpersTest.kt
git commit -m "feat(onboarding): pure parse/convert helpers for onboarding inputs"
```

---

## Task 2: OnboardingViewModel — state, step gating, generation, finish (TDD)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModel.kt` (append state class + ViewModel)
- Test: `app/src/test/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModelTest.kt`:

```kotlin
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
import org.mockito.kotlin.any
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.onboarding.OnboardingViewModelTest"`
Expected: FAIL — `OnboardingViewModel` / `OnboardingUiState` unresolved.

- [ ] **Step 3: Write the implementation**

Append to `app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModel.kt` (below the helpers from Task 1). Add these imports at the top of the file:

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.plan.GeneratedPlan
import com.zack.recomptracker.domain.plan.PlanGenerationOutcome
import com.zack.recomptracker.domain.plan.PlanGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
```

Then the state + ViewModel:

```kotlin
/** Total onboarding steps (0..LAST_STEP). 0 About you · 1 Your body · 2 Goal & measurements · 3 Plan. */
const val ONBOARDING_STEPS = 4
private const val LAST_STEP = ONBOARDING_STEPS - 1

data class OnboardingUiState(
    val step: Int = 0,
    // Screen 1 — About you
    val name: String = "",
    val useMetricUnits: Boolean = true,
    // Screen 2 — Your body
    val sex: BiologicalSex? = null,
    val birthDate: String? = null,           // ISO yyyy-MM-dd
    val heightInput: String = "",            // raw, interpreted per useMetricUnits
    // Screen 3 — Goal & measurements
    val goal: FitnessGoal? = null,
    val activityLevel: ActivityLevel? = null,
    val weightInput: String = "",            // raw, interpreted per useMetricUnits
    val waistInput: String = "",             // raw, optional
    // Screen 4 — Plan reveal
    val generatedPlan: GeneratedPlan? = null,
    val adjusting: Boolean = false,
    val adjCalories: String = "",
    val adjProtein: String = "",
    val adjCarbs: String = "",
    val adjFat: String = "",
    // cross-cutting
    val canContinue: Boolean = true,
    val message: String? = null,
    val finished: Boolean = false,
)

class OnboardingViewModel(
    private val userProfileStore: UserProfilePreferencesStore,
    private val planRepository: PlanRepository,
    private val logRepository: LogRepository,
    private val uiPreferences: UiPreferences,
    private val dateProvider: DateProvider,
    private val planGenerator: PlanGenerator = PlanGenerator(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    // --- field setters (synchronous draft updates; nothing persists until finish) ---

    fun setName(value: String) = set { copy(name = value) }

    fun setUnits(metric: Boolean) = set {
        if (metric == useMetricUnits) this
        // Units are chosen before any measurement is entered; if the user goes back and flips them,
        // clear the metric-ambiguous fields so they are re-entered in the new unit.
        else copy(useMetricUnits = metric, heightInput = "", weightInput = "", waistInput = "")
    }

    fun setSex(value: BiologicalSex) = set { copy(sex = value) }
    fun setBirthDate(iso: String) = set { copy(birthDate = iso) }
    fun setHeight(value: String) = set { copy(heightInput = value.filter { it == '.' || it.isDigit() }.take(5)) }

    fun setGoal(value: FitnessGoal) = set { copy(goal = value) }
    fun setActivityLevel(value: ActivityLevel) = set { copy(activityLevel = value) }
    fun setWeight(value: String) = set { copy(weightInput = value.filter { it == '.' || it.isDigit() }.take(6)) }
    fun setWaist(value: String) = set { copy(waistInput = value.filter { it == '.' || it.isDigit() }.take(6)) }

    // --- plan-reveal adjust mode ---

    fun startAdjusting() = set { copy(adjusting = true) }
    fun stopAdjusting() = set { copy(adjusting = false) }
    fun setAdjustedCalories(v: String) = set { copy(adjCalories = v.filter { it.isDigit() }.take(5)) }
    fun setAdjustedProtein(v: String) = set { copy(adjProtein = v.filter { it.isDigit() }.take(4)) }
    fun setAdjustedCarbs(v: String) = set { copy(adjCarbs = v.filter { it.isDigit() }.take(4)) }
    fun setAdjustedFat(v: String) = set { copy(adjFat = v.filter { it.isDigit() }.take(4)) }

    // --- navigation ---

    fun back() = set { if (step > 0) copy(step = step - 1, message = null) else this }

    fun next() {
        val s = _uiState.value
        if (!s.canContinue) return
        when (s.step) {
            0, 1 -> set { copy(step = step + 1) }
            2 -> generatePlan()
            else -> Unit
        }
    }

    private fun generatePlan() {
        val s = _uiState.value
        val weightKg = parseWeightKg(s.weightInput, s.useMetricUnits) ?: return
        viewModelScope.launch {
            when (val outcome = planGenerator.generate(buildDraftProfile(s), weightKg, dateProvider.today())) {
                is PlanGenerationOutcome.Ready -> set {
                    copy(
                        step = 3,
                        generatedPlan = outcome.plan,
                        message = null,
                        adjCalories = outcome.plan.targetCalories.toString(),
                        adjProtein = outcome.plan.proteinG.toString(),
                        adjCarbs = outcome.plan.carbsG.toString(),
                        adjFat = outcome.plan.fatG.toString(),
                    )
                }
                is PlanGenerationOutcome.NeedsWeight ->
                    set { copy(message = "Enter your current weight to continue.") }
                is PlanGenerationOutcome.MissingProfileFields ->
                    set { copy(message = "Missing: ${outcome.fields.joinToString(", ")}") }
            }
        }
    }

    // --- finish: single write point ---

    fun finish() {
        val s = _uiState.value
        val plan = s.generatedPlan ?: return
        val weightKg = parseWeightKg(s.weightInput, s.useMetricUnits) ?: return
        val waistCm = parseWaistCm(s.waistInput, s.useMetricUnits)
        viewModelScope.launch {
            userProfileStore.save(buildDraftProfile(s))
            val base = planRepository.preferences.first()
            planRepository.save(
                base.copy(
                    targetCalories = s.adjCalories.toIntOrNull() ?: plan.targetCalories,
                    targetProteinG = s.adjProtein.toIntOrNull() ?: plan.proteinG,
                    targetCarbsG = s.adjCarbs.toIntOrNull() ?: plan.carbsG,
                    targetFatG = s.adjFat.toIntOrNull() ?: plan.fatG,
                    calorieZoneLowerBound = plan.zoneLower,
                    calorieZoneUpperBound = plan.zoneUpper,
                    useMetricUnits = s.useMetricUnits,
                ),
            )
            // First run by definition: today's row does not exist yet, so an upsert is safe.
            logRepository.saveDailyMetrics(
                DailyMetricsInput(
                    date = dateProvider.today(),
                    bodyWeightKg = weightKg,
                    waistCm = waistCm,
                    steps = null,
                    sleepHours = null,
                    energyScore = null,
                    hungerScore = null,
                    sorenessScore = null,
                    trained = false,
                    notes = "",
                ),
            )
            uiPreferences.setOnboardingComplete(true)
            set { copy(finished = true) }
        }
    }

    // --- internals ---

    private fun buildDraftProfile(s: OnboardingUiState) = UserProfilePreferences(
        name = s.name.trim().ifBlank { null },
        heightCm = parseHeightCm(s.heightInput, s.useMetricUnits),
        birthDate = s.birthDate,
        biologicalSex = s.sex,
        activityLevel = s.activityLevel,
        goal = s.goal,
    )

    private fun computeCanContinue(s: OnboardingUiState): Boolean = when (s.step) {
        0 -> true
        1 -> s.sex != null &&
            isValidBirthDate(s.birthDate, dateProvider.today()) &&
            parseHeightCm(s.heightInput, s.useMetricUnits) != null
        2 -> s.goal != null && s.activityLevel != null &&
            (parseWeightKg(s.weightInput, s.useMetricUnits) ?: 0.0) > 0.0
        else -> true
    }

    private inline fun set(transform: OnboardingUiState.() -> OnboardingUiState) {
        _uiState.update { current ->
            val next = current.transform()
            next.copy(canContinue = computeCanContinue(next))
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.onboarding.OnboardingViewModelTest"`
Expected: PASS (6 tests). If the computed-calorie assertion needs a concrete number, it does not — the test only asserts `> 0`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/onboarding/OnboardingViewModelTest.kt
git commit -m "feat(onboarding): OnboardingViewModel with step gating, plan generation, finish"
```

---

## Task 3: Add `onboardingComplete` flag to UiPreferences

DataStore-backed prefs are not unit-tested in this repo (they require instrumentation), so this is a code change verified by type-check, mirroring the existing `aiInsightsEnabled` boolean exactly.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt`

- [ ] **Step 1: Add the key**

In the `UiPreferences` class's private `Keys` object (the one alongside `AiInsightsEnabled = booleanPreferencesKey("ai_insights_enabled")`), add:

```kotlin
val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
```

- [ ] **Step 2: Add the flow + setter**

In the `UiPreferences` class body, next to `aiInsightsEnabled` / `setAiInsights(...)`, add:

```kotlin
val onboardingComplete: kotlinx.coroutines.flow.Flow<Boolean> =
    context.uiDataStore.data.map { it[Keys.OnboardingComplete] ?: false }

suspend fun setOnboardingComplete(complete: Boolean) {
    context.uiDataStore.edit { it[Keys.OnboardingComplete] = complete }
}
```

(`map`, `edit`, and `booleanPreferencesKey` are already imported in this file — confirm; if not, add `import androidx.datastore.preferences.core.booleanPreferencesKey` and `import kotlinx.coroutines.flow.map`.)

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt
git commit -m "feat(onboarding): onboardingComplete flag in UiPreferences"
```

---

## Task 4: OnboardingScreen UI (4 steps, Direction B)

Compose screens here are verified by type-check (the repo unit-tests ViewModels/state, not screens). Build the screen against the verified component signatures.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Write the screen**

Create `app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingScreen.kt`:

```kotlin
package com.zack.recomptracker.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val STEP_TITLES = listOf("About you", "Your body", "Goal", "Your plan")

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onComplete()
    }

    val appColors = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
            .padding(horizontal = 20.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 32.dp, bottom = 24.dp)) {
            // --- shared frame: step counter + progress bar ---
            Text(
                text = "STEP ${state.step + 1} OF $ONBOARDING_STEPS · ${STEP_TITLES[state.step].uppercase()}",
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            ProgressBar(fraction = (state.step + 1).toFloat() / ONBOARDING_STEPS)
            Spacer(Modifier.height(20.dp))

            // --- step body ---
            Box(modifier = Modifier.weight(1f)) {
                when (state.step) {
                    0 -> AboutYouStep(state, viewModel)
                    1 -> YourBodyStep(state, viewModel)
                    2 -> GoalStep(state, viewModel)
                    else -> PlanStep(state, viewModel)
                }
            }

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }

            // --- bottom CTA ---
            if (state.step < 3) {
                LiquidPrimaryButton(
                    text = if (state.step == 2) "See my plan" else "Continue",
                    onClick = viewModel::next,
                    enabled = state.canContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LiquidSecondaryButton(
                    text = if (state.adjusting) "Done adjusting" else "Adjust targets",
                    onClick = { if (state.adjusting) viewModel.stopAdjusting() else viewModel.startAdjusting() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                LiquidPrimaryButton(
                    text = "Start tracking",
                    onClick = viewModel::finish,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(appColors.textPrimary.copy(alpha = 0.10f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun AboutYouStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    Column {
        StepHeader("Let's get you set up", "First, the basics. This takes about a minute.")
        FrostedCard {
            GlassInputField(
                label = "Your name",
                value = state.name,
                onValueChange = vm::setName,
                keyboardType = KeyboardType.Text,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            SectionLabel("Units")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PillToggle("Metric (kg, cm)", state.useMetricUnits, Modifier.weight(1f)) { vm.setUnits(true) }
                PillToggle("Imperial", !state.useMetricUnits, Modifier.weight(1f)) { vm.setUnits(false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YourBodyStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    Column {
        StepHeader("A few body basics", "Used to estimate your daily energy needs.")
        FrostedCard {
            SectionLabel("Biological sex")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PillToggle("Male", state.sex == BiologicalSex.MALE, Modifier.weight(1f)) { vm.setSex(BiologicalSex.MALE) }
                PillToggle("Female", state.sex == BiologicalSex.FEMALE, Modifier.weight(1f)) { vm.setSex(BiologicalSex.FEMALE) }
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Date of birth")
            PickerRow(value = state.birthDate ?: "Select date") { showDatePicker = true }
            Spacer(Modifier.height(16.dp))
            GlassInputField(
                label = "Height",
                value = state.heightInput,
                onValueChange = vm::setHeight,
                unit = if (state.useMetricUnits) "cm" else "in",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (showDatePicker) {
        val initialMillis = state.birthDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        vm.setBirthDate(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString(),
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun GoalStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    var sheet by remember { mutableStateOf<String?>(null) } // "goal" | "activity" | null
    Column {
        StepHeader("Your goal & starting point", "What you want, and where you're at today.")
        FrostedCard {
            SectionLabel("Goal")
            PickerRow(value = state.goal?.let { goalLabel(it) } ?: "Select goal") { sheet = "goal" }
            Spacer(Modifier.height(14.dp))
            SectionLabel("Activity level")
            PickerRow(value = state.activityLevel?.let { activityLabel(it) } ?: "Select activity") { sheet = "activity" }
            Spacer(Modifier.height(14.dp))
            GlassInputField(
                label = "Current weight",
                value = state.weightInput,
                onValueChange = vm::setWeight,
                unit = if (state.useMetricUnits) "kg" else "lb",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            GlassInputField(
                label = if (state.useMetricUnits) "Waist · optional" else "Waist · optional",
                value = state.waistInput,
                onValueChange = vm::setWaist,
                unit = if (state.useMetricUnits) "cm" else "in",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    when (sheet) {
        "goal" -> OptionSheet("Goal", FitnessGoal.entries.map { it to goalLabel(it) }, state.goal, onDismiss = { sheet = null }) {
            vm.setGoal(it); sheet = null
        }
        "activity" -> OptionSheet("Activity level", ActivityLevel.entries.map { it to activityLabel(it) }, state.activityLevel, onDismiss = { sheet = null }) {
            vm.setActivityLevel(it); sheet = null
        }
    }
}

@Composable
private fun PlanStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    val plan = state.generatedPlan
    val appColors = LocalAppColors.current
    Column {
        StepHeader("Here's your plan", "Tailored to your goal. You can fine-tune anytime.")
        if (plan != null) {
            FrostedCard {
                if (state.adjusting) {
                    GlassInputField("Calories", state.adjCalories, vm::setAdjustedCalories, unit = "kcal", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    GlassInputField("Protein", state.adjProtein, vm::setAdjustedProtein, unit = "g", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    GlassInputField("Carbs", state.adjCarbs, vm::setAdjustedCarbs, unit = "g", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    GlassInputField("Fat", state.adjFat, vm::setAdjustedFat, unit = "g", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        text = (state.adjCalories.ifBlank { plan.targetCalories.toString() }),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = appColors.textPrimary,
                    )
                    Text("kcal / day", fontSize = 12.sp, color = appColors.textSecondary, letterSpacing = 2.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MacroTile("Protein", state.adjProtein.ifBlank { plan.proteinG.toString() }, Modifier.weight(1f))
                        MacroTile("Carbs", state.adjCarbs.ifBlank { plan.carbsG.toString() }, Modifier.weight(1f))
                        MacroTile("Fat", state.adjFat.ifBlank { plan.fatG.toString() }, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Calculated with Mifflin–St Jeor from your profile", fontSize = 10.sp, color = appColors.textSecondary)
        }
    }
}

// --- small local building blocks ---

@Composable
private fun StepHeader(title: String, subtitle: String) {
    val appColors = LocalAppColors.current
    Column {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
        Text(subtitle, fontSize = 13.sp, color = appColors.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
    }
}

@Composable
private fun PillToggle(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else appColors.textPrimary.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) accent else appColors.textSecondary)
    }
}

@Composable
private fun PickerRow(value: String, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = appColors.textPrimary)
        Text("›", fontSize = 18.sp, color = appColors.textSecondary)
    }
}

@Composable
private fun MacroTile(label: String, value: String, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.textPrimary.copy(alpha = 0.05f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${value}g", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
        Text(label.uppercase(), fontSize = 9.sp, letterSpacing = 1.sp, color = appColors.textSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = appColors.textPrimary, modifier = Modifier.padding(vertical = 8.dp))
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, fontSize = 15.sp, color = appColors.textPrimary, fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal)
                    if (value == selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun goalLabel(g: FitnessGoal): String = when (g) {
    FitnessGoal.AGGRESSIVE_CUT -> "Aggressive cut"
    FitnessGoal.MODERATE_CUT -> "Moderate cut"
    FitnessGoal.MINI_CUT -> "Mini cut"
    FitnessGoal.RECOMP -> "Recomp"
    FitnessGoal.LEAN_BULK -> "Lean bulk"
    FitnessGoal.MODERATE_BULK -> "Moderate bulk"
    FitnessGoal.AGGRESSIVE_BULK -> "Aggressive bulk"
}

private fun activityLabel(a: ActivityLevel): String = when (a) {
    ActivityLevel.SEDENTARY -> "Sedentary"
    ActivityLevel.LIGHTLY_ACTIVE -> "Lightly active"
    ActivityLevel.MODERATELY_ACTIVE -> "Moderately active"
    ActivityLevel.VERY_ACTIVE -> "Very active"
}
```

> **Note for the implementer:** `LocalAppColors.current` exposes `background`, `textPrimary`, `textSecondary` (confirm exact property names in `ui/theme/AppColors.kt`; `ProfileScreen.kt` uses `appColors.textPrimary`). If `textSecondary` is named differently (e.g. `textDim`/`textMuted`), use the actual name. The accent color is `MaterialTheme.colorScheme.primary`.

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any color-property name mismatches per the note above.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingScreen.kt
git commit -m "feat(onboarding): 4-step onboarding Compose UI (direction B)"
```

---

## Task 5: Wire OnboardingViewModel into AppContainer

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add the factory branch**

In `AppViewModelFactory.create(...)`'s `when (modelClass)` block (where e.g. `ProfileViewModel::class.java -> ProfileViewModel(...)` is), add a branch before the `else -> error(...)`:

```kotlin
OnboardingViewModel::class.java -> OnboardingViewModel(
    userProfileStore = container.userProfilePreferencesStore,
    planRepository = container.planRepository,
    logRepository = container.logRepository,
    uiPreferences = container.uiPreferences,
    dateProvider = container.dateProvider,
)
```

Add the import at the top of the file:

```kotlin
import com.zack.recomptracker.ui.onboarding.OnboardingViewModel
```

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(onboarding): provide OnboardingViewModel via AppContainer factory"
```

---

## Task 6: Navigation + start-destination gating

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`

- [ ] **Step 1: Add the route constant**

In `AppNavGraph.kt`, inside the `Routes` object, add:

```kotlin
const val Onboarding = "onboarding"
```

- [ ] **Step 2: Add a `startDestination` parameter and the onboarding composable**

Change the `AppNavGraph` signature to accept a start destination (default Home for safety):

```kotlin
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = TopLevelDestination.Home.route,
    modifier: Modifier = Modifier,
) {
    val factory = LocalAppContainer.current.viewModelFactory
    // ... existing transition vals ...
    NavHost(
        navController = navController,
        startDestination = startDestination,   // was: TopLevelDestination.Home.route
        modifier = modifier,
    ) {
        composable(route = Routes.Onboarding) {
            OnboardingScreen(
                viewModel = viewModel<OnboardingViewModel>(factory = factory),
                onComplete = {
                    navController.navigate(TopLevelDestination.Home.route) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                },
            )
        }
        // ... existing composable(...) entries unchanged ...
    }
}
```

Add imports at the top of `AppNavGraph.kt`:

```kotlin
import com.zack.recomptracker.ui.onboarding.OnboardingScreen
import com.zack.recomptracker.ui.onboarding.OnboardingViewModel
```

- [ ] **Step 3: Gate the start destination in RecompApp**

In `RecompApp.kt`, inside the `RecompTrackerTheme { ... }` block (where `accentTheme`, `appColors`, `navController` are set up), read the flag with a nullable initial so the nav host is not composed with the wrong start:

```kotlin
val onboardingComplete by produceState<Boolean?>(initialValue = null) {
    container.uiPreferences.onboardingComplete.collect { value = it }
}
```

Then, at the call site `AppNavGraph(navController = navController, modifier = ...)` (inside the Scaffold content), gate it:

```kotlin
when (onboardingComplete) {
    null -> Box(modifier = Modifier.fillMaxSize().background(appColors.background)) // brief load gate
    else -> AppNavGraph(
        navController = navController,
        startDestination = if (onboardingComplete == true) TopLevelDestination.Home.route else Routes.Onboarding,
        modifier = /* keep the existing modifier passed today */,
    )
}
```

Add imports at the top of `RecompApp.kt` if missing:

```kotlin
import androidx.compose.runtime.produceState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
```

The bottom nav bar already shows only `if (currentRoute in topLevelRoutes)`; `"onboarding"` is not added to `topLevelRoutes`, so the nav bar stays hidden during onboarding and while gating (no current route). Do **not** add `Routes.Onboarding` to `topLevelRoutes`.

- [ ] **Step 4: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt \
        app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt
git commit -m "feat(onboarding): gate start destination on onboardingComplete flag"
```

---

## Task 7: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; the new `OnboardingHelpersTest` and `OnboardingViewModelTest` pass alongside the existing suite.

- [ ] **Step 2: Assemble the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test (emulator or device)**

Install the debug APK on a **fresh** app install (clear app data first so `onboardingComplete=false`). Verify:
1. App opens to onboarding (no bottom nav), Step 1 of 4.
2. Continue is enabled on Step 1; entering sex + a valid DOB + height enables Continue on Step 2; goal + activity + weight enables "See my plan" on Step 3.
3. Step 4 shows a non-default calorie number and macros; "Adjust targets" reveals editable fields; "Start tracking" lands on Home with the bottom nav, and the dashboard reflects the new targets.
4. Killing and relaunching the app goes straight to Home (onboarding does not reappear).
5. The Profile and Plan screens show the values entered during onboarding; today's Body check-in shows the entered weight/waist.

- [ ] **Step 4: Final commit (if any manual-fix tweaks were needed)**

```bash
git add -A
git commit -m "feat(onboarding): verification fixes"
```

---

## Self-Review Notes (author)

- **Spec coverage:** 9-field set (Task 2/4), 4-screen direction-B structure (Task 4), reveal+override (PlanStep + adjust setters), first-run gating via `onboardingComplete` (Tasks 3 & 6), single-write persistence to profile/plan/daily-log (Task 2 `finish()`), validation gating (`computeCanContinue`), units affecting display + stored metric (`parse*` helpers + `setUnits`). All covered.
- **Out of scope honored:** no Room migration (only a DataStore boolean), no photo, no gym sessions, no theme/AI/threshold collection, no edit-onboarding entry point.
- **Type consistency:** `GeneratedPlan` fields (`proteinG/carbsG/fatG/zoneLower/zoneUpper`), `DailyMetricsInput` constructor, `PlanGenerator.generate(profile, weightKg, today)`, and component signatures all match the verified current code.
- **Known follow-up the implementer may hit:** exact `AppColors` property names (`textSecondary` may differ) — flagged inline in Task 4.
