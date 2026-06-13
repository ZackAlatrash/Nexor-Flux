# Initial Plan Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate an initial calorie + macro plan from the user's profile and latest logged bodyweight via a TDEE calculation, previewed and applied on the Plan screen.

**Architecture:** A pure-Kotlin `domain/plan/` unit does the math (`PlanCalculator`) and validation/assembly (`PlanGenerator`, returning a sealed outcome). `PlanViewModel` reads profile (`UserProfilePreferencesStore`) and latest weight (`LogRepository`), drives a preview/weight-entry dialog, and on Apply populates the existing editable Plan fields (user persists with the existing Save). `AdjustmentEngine` and DataStore schemas are untouched.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, ViewModel + StateFlow, JUnit4 + Mockito-kotlin + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-13-initial-plan-generator-design.md`

---

## File Structure

| File | Responsibility |
|---|---|
| `domain/plan/PlanCalculatorModels.kt` (create) | `PlanCalculatorInput` + `GeneratedPlan` data classes |
| `domain/plan/PlanCalculator.kt` (create) | Pure math: BMR → TDEE → calories → macros → zone |
| `domain/plan/PlanGenerator.kt` (create) | Validation + assembly; sealed `PlanGenerationOutcome` |
| `ui/plan/PlanViewModel.kt` (modify) | New deps, dialog state, generate/submit-weight/apply actions |
| `ui/plan/PlanGenerationDialogs.kt` (create) | Preview dialog + weight-entry dialog composables |
| `ui/plan/PlanScreen.kt` (modify) | "Generate from profile" button + dialog host |
| `core/AppContainer.kt` (modify) | Inject new `PlanViewModel` deps |
| `test/.../domain/PlanCalculatorTest.kt` (create) | Math unit tests |
| `test/.../domain/PlanGeneratorTest.kt` (create) | Validation/outcome tests |
| `test/.../ui/plan/PlanViewModelGenerateTest.kt` (create) | VM wiring tests |

**Note on domain dependency:** `PlanCalculator`/`PlanGenerator` import the pure `@Serializable` enums from `data.preferences` (`BiologicalSex`, `ActivityLevel`, `FitnessGoal`) and the `UserProfilePreferences` data class. These carry no Android imports, so the "domain is pure Kotlin (no Android imports)" rule is satisfied.

---

## Task 1: PlanCalculator (pure math)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/plan/PlanCalculatorModels.kt`
- Create: `app/src/main/java/com/zack/recomptracker/domain/plan/PlanCalculator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/PlanCalculatorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/domain/PlanCalculatorTest.kt`:

```kotlin
package com.zack.recomptracker.domain

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.domain.plan.PlanCalculator
import com.zack.recomptracker.domain.plan.PlanCalculatorInput
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanCalculatorTest {
    private val calculator = PlanCalculator()

    private fun input(
        heightCm: Int = 180,
        ageYears: Int = 30,
        sex: BiologicalSex = BiologicalSex.MALE,
        activityLevel: ActivityLevel = ActivityLevel.MODERATELY_ACTIVE,
        goal: FitnessGoal = FitnessGoal.RECOMP,
        weightKg: Double = 80.0,
    ) = PlanCalculatorInput(heightCm, ageYears, sex, activityLevel, goal, weightKg)

    @Test
    fun maleRecompModerateActivityProducesExpectedPlan() {
        // BMR = 1780, TDEE = 1780*1.55 = 2759, RECOMP -5% -> 2621.05 -> 2620
        val plan = calculator.generate(input())
        assertEquals(1780, plan.bmr)
        assertEquals(2759, plan.tdee)
        assertEquals(1.55, plan.activityFactor, 0.0001)
        assertEquals(-5, plan.goalDeltaPercent)
        assertEquals(2620, plan.targetCalories)
        assertEquals(160, plan.proteinG)   // 2.0 g/kg * 80
        assertEquals(73, plan.fatG)         // 0.25*2620/9 = 72.78 -> 73
        assertEquals(331, plan.carbsG)      // (2620-640-657)/4 = 330.75 -> 331
        assertEquals(2520, plan.zoneLower)
        assertEquals(2720, plan.zoneUpper)
    }

    @Test
    fun femaleUsesMinus161BmrOffset() {
        // Female 60kg 165cm 28y: BMR = 600 + 1031.25 - 140 - 161 = 1330.25 -> 1330
        val plan = calculator.generate(
            input(heightCm = 165, ageYears = 28, sex = BiologicalSex.FEMALE,
                activityLevel = ActivityLevel.LIGHTLY_ACTIVE, goal = FitnessGoal.MODERATE_CUT, weightKg = 60.0),
        )
        assertEquals(1330, plan.bmr)
        assertEquals(1.375, plan.activityFactor, 0.0001)
        assertEquals(-18, plan.goalDeltaPercent)
        assertEquals(1500, plan.targetCalories) // 1330.25*1.375*0.82 = 1499.86 -> 1500
        assertEquals(132, plan.proteinG)        // cut -> 2.2 g/kg * 60
    }

    @Test
    fun cutGoalsUseTwoPointTwoGramsPerKgProtein() {
        val plan = calculator.generate(input(goal = FitnessGoal.AGGRESSIVE_CUT, weightKg = 80.0))
        assertEquals(176, plan.proteinG) // 2.2 * 80
    }

    @Test
    fun bulkGoalsUseTwoGramsPerKgProtein() {
        val plan = calculator.generate(input(goal = FitnessGoal.LEAN_BULK, weightKg = 80.0))
        assertEquals(160, plan.proteinG) // 2.0 * 80
        assertEquals(8, plan.goalDeltaPercent)
    }

    @Test
    fun veryActiveUsesHighestFactor() {
        val plan = calculator.generate(input(activityLevel = ActivityLevel.VERY_ACTIVE))
        assertEquals(1.725, plan.activityFactor, 0.0001)
    }

    @Test
    fun sedentaryUsesLowestFactor() {
        val plan = calculator.generate(input(activityLevel = ActivityLevel.SEDENTARY))
        assertEquals(1.2, plan.activityFactor, 0.0001)
    }

    @Test
    fun targetCaloriesClampedToMinimumOfOneThousand() {
        // Female 40kg 150cm 25y sedentary aggressive cut: 1261.8*0.75 = 946 -> clamp 1000
        val plan = calculator.generate(
            input(heightCm = 150, ageYears = 25, sex = BiologicalSex.FEMALE,
                activityLevel = ActivityLevel.SEDENTARY, goal = FitnessGoal.AGGRESSIVE_CUT, weightKg = 40.0),
        )
        assertEquals(1000, plan.targetCalories)
        assertEquals(900, plan.zoneLower)
        assertEquals(1100, plan.zoneUpper)
    }

    @Test
    fun zoneIsTargetPlusMinusOneHundred() {
        val plan = calculator.generate(input())
        assertEquals(plan.targetCalories - 100, plan.zoneLower)
        assertEquals(plan.targetCalories + 100, plan.zoneUpper)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.PlanCalculatorTest"`
Expected: FAIL — unresolved references `PlanCalculator`, `PlanCalculatorInput`.

- [ ] **Step 3: Create the models**

Create `app/src/main/java/com/zack/recomptracker/domain/plan/PlanCalculatorModels.kt`:

```kotlin
package com.zack.recomptracker.domain.plan

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal

/** Everything PlanCalculator needs. All fields required (validated upstream). */
data class PlanCalculatorInput(
    val heightCm: Int,
    val ageYears: Int,
    val sex: BiologicalSex,
    val activityLevel: ActivityLevel,
    val goal: FitnessGoal,
    val weightKg: Double,
)

/** Result of a generation, including intermediates so the UI can show its work. */
data class GeneratedPlan(
    val bmr: Int,
    val tdee: Int,
    val activityFactor: Double,
    val goalDeltaPercent: Int,
    val weightKgUsed: Double,
    val targetCalories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val zoneLower: Int,
    val zoneUpper: Int,
)
```

- [ ] **Step 4: Implement the calculator**

Create `app/src/main/java/com/zack/recomptracker/domain/plan/PlanCalculator.kt`:

```kotlin
package com.zack.recomptracker.domain.plan

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import kotlin.math.roundToInt

/**
 * Pure TDEE → calorie/macro generator. Mifflin-St Jeor BMR, activity-level
 * multiplier, percentage-of-TDEE goal delta, protein g/kg, fat 25% kcal, carbs
 * remainder. No Android, no IO. See the design spec for the formula and rationale.
 */
class PlanCalculator {

    fun generate(input: PlanCalculatorInput): GeneratedPlan {
        val sexOffset = when (input.sex) {
            BiologicalSex.MALE -> 5.0
            BiologicalSex.FEMALE -> -161.0
        }
        val bmr = 10.0 * input.weightKg + 6.25 * input.heightCm - 5.0 * input.ageYears + sexOffset

        val factor = activityFactor(input.activityLevel)
        val tdee = bmr * factor

        val deltaPercent = goalDeltaPercent(input.goal)
        val targetRaw = tdee * (1.0 + deltaPercent / 100.0)
        val target = ((targetRaw / 10.0).roundToInt() * 10).coerceIn(1000, 6000)

        val proteinPerKg = if (input.goal.isCut()) 2.2 else 2.0
        val proteinG = (proteinPerKg * input.weightKg).roundToInt()
        val fatG = (0.25 * target / 9.0).roundToInt()
        val carbsKcal = target - proteinG * 4 - fatG * 9
        val carbsG = (carbsKcal / 4.0).roundToInt().coerceAtLeast(0)

        return GeneratedPlan(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            activityFactor = factor,
            goalDeltaPercent = deltaPercent,
            weightKgUsed = input.weightKg,
            targetCalories = target,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            zoneLower = target - 100,
            zoneUpper = target + 100,
        )
    }

    private fun activityFactor(level: ActivityLevel): Double = when (level) {
        ActivityLevel.SEDENTARY -> 1.2
        ActivityLevel.LIGHTLY_ACTIVE -> 1.375
        ActivityLevel.MODERATELY_ACTIVE -> 1.55
        ActivityLevel.VERY_ACTIVE -> 1.725
    }

    private fun goalDeltaPercent(goal: FitnessGoal): Int = when (goal) {
        FitnessGoal.AGGRESSIVE_CUT -> -25
        FitnessGoal.MODERATE_CUT -> -18
        FitnessGoal.MINI_CUT -> -22
        FitnessGoal.RECOMP -> -5
        FitnessGoal.LEAN_BULK -> 8
        FitnessGoal.MODERATE_BULK -> 12
        FitnessGoal.AGGRESSIVE_BULK -> 18
    }

    private fun FitnessGoal.isCut(): Boolean =
        this == FitnessGoal.AGGRESSIVE_CUT ||
            this == FitnessGoal.MODERATE_CUT ||
            this == FitnessGoal.MINI_CUT
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.PlanCalculatorTest"`
Expected: PASS (8 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/plan/PlanCalculatorModels.kt \
        app/src/main/java/com/zack/recomptracker/domain/plan/PlanCalculator.kt \
        app/src/test/java/com/zack/recomptracker/domain/PlanCalculatorTest.kt
git commit -m "feat(plan): add TDEE-based PlanCalculator (pure domain)"
```

---

## Task 2: PlanGenerator (validation + assembly)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/plan/PlanGenerator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/PlanGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/domain/PlanGeneratorTest.kt`:

```kotlin
package com.zack.recomptracker.domain

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.domain.plan.PlanGenerationOutcome
import com.zack.recomptracker.domain.plan.PlanGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanGeneratorTest {
    private val generator = PlanGenerator()

    private val completeProfile = UserProfilePreferences(
        heightCm = 180,
        ageYears = 30,
        biologicalSex = BiologicalSex.MALE,
        activityLevel = ActivityLevel.MODERATELY_ACTIVE,
        goal = FitnessGoal.RECOMP,
    )

    @Test
    fun completeProfileWithWeightProducesReadyPlan() {
        val outcome = generator.generate(completeProfile, weightKg = 80.0)
        assertTrue(outcome is PlanGenerationOutcome.Ready)
        assertEquals(2620, (outcome as PlanGenerationOutcome.Ready).plan.targetCalories)
    }

    @Test
    fun missingWeightAsksForWeight() {
        val outcome = generator.generate(completeProfile, weightKg = null)
        assertEquals(PlanGenerationOutcome.NeedsWeight, outcome)
    }

    @Test
    fun nonPositiveWeightAsksForWeight() {
        val outcome = generator.generate(completeProfile, weightKg = 0.0)
        assertEquals(PlanGenerationOutcome.NeedsWeight, outcome)
    }

    @Test
    fun missingFieldsReportedBeforeWeight() {
        val outcome = generator.generate(UserProfilePreferences(), weightKg = null)
        assertTrue(outcome is PlanGenerationOutcome.MissingProfileFields)
        val fields = (outcome as PlanGenerationOutcome.MissingProfileFields).fields
        assertEquals(listOf("Height", "Age", "Sex", "Activity level", "Goal"), fields)
    }

    @Test
    fun singleMissingFieldNamedCorrectly() {
        val outcome = generator.generate(completeProfile.copy(goal = null), weightKg = 80.0)
        assertTrue(outcome is PlanGenerationOutcome.MissingProfileFields)
        assertEquals(listOf("Goal"), (outcome as PlanGenerationOutcome.MissingProfileFields).fields)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.PlanGeneratorTest"`
Expected: FAIL — unresolved references `PlanGenerator`, `PlanGenerationOutcome`.

- [ ] **Step 3: Implement the generator**

Create `app/src/main/java/com/zack/recomptracker/domain/plan/PlanGenerator.kt`:

```kotlin
package com.zack.recomptracker.domain.plan

import com.zack.recomptracker.data.preferences.UserProfilePreferences

/** Outcome of attempting to generate a plan from the current profile + weight. */
sealed interface PlanGenerationOutcome {
    data class Ready(val plan: GeneratedPlan) : PlanGenerationOutcome
    data class MissingProfileFields(val fields: List<String>) : PlanGenerationOutcome
    data object NeedsWeight : PlanGenerationOutcome
}

/**
 * Validates the profile, supplies weight, and delegates the math to PlanCalculator.
 * Pure — no Android, no IO. Missing required fields take priority over missing weight.
 */
class PlanGenerator(
    private val calculator: PlanCalculator = PlanCalculator(),
) {
    fun generate(profile: UserProfilePreferences, weightKg: Double?): PlanGenerationOutcome {
        val missing = buildList {
            if (profile.heightCm == null) add("Height")
            if (profile.ageYears == null) add("Age")
            if (profile.biologicalSex == null) add("Sex")
            if (profile.activityLevel == null) add("Activity level")
            if (profile.goal == null) add("Goal")
        }
        if (missing.isNotEmpty()) return PlanGenerationOutcome.MissingProfileFields(missing)
        if (weightKg == null || weightKg <= 0.0) return PlanGenerationOutcome.NeedsWeight

        val plan = calculator.generate(
            PlanCalculatorInput(
                heightCm = profile.heightCm!!,
                ageYears = profile.ageYears!!,
                sex = profile.biologicalSex!!,
                activityLevel = profile.activityLevel!!,
                goal = profile.goal!!,
                weightKg = weightKg,
            ),
        )
        return PlanGenerationOutcome.Ready(plan)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.PlanGeneratorTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/plan/PlanGenerator.kt \
        app/src/test/java/com/zack/recomptracker/domain/PlanGeneratorTest.kt
git commit -m "feat(plan): add PlanGenerator validation + sealed outcome"
```

---

## Task 3: Wire PlanViewModel + AppContainer

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (the `PlanViewModel::class.java ->` branch, ~line 350)
- Test: `app/src/test/java/com/zack/recomptracker/ui/plan/PlanViewModelGenerateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/plan/PlanViewModelGenerateTest.kt`:

```kotlin
package com.zack.recomptracker.ui.plan

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
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

    private val completeProfile = UserProfilePreferences(
        heightCm = 180, ageYears = 30, biologicalSex = BiologicalSex.MALE,
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

    private fun viewModel() = PlanViewModel(planRepo, profileStore, logRepo)

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.plan.PlanViewModelGenerateTest"`
Expected: FAIL — `PlanViewModel` constructor takes 1 arg; `generationDialog`, `generateFromProfile`, `submitWeight`, `applyGeneratedPlan`, `PlanGenerationDialog` unresolved.

- [ ] **Step 3: Add dialog state model + new imports to PlanViewModel**

In `app/src/main/java/com/zack/recomptracker/ui/plan/PlanViewModel.kt`, add imports after the existing import block:

```kotlin
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.domain.plan.GeneratedPlan
import com.zack.recomptracker.domain.plan.PlanGenerationOutcome
import com.zack.recomptracker.domain.plan.PlanGenerator
import kotlinx.coroutines.flow.first
```

Add this sealed interface above `data class PlanUiState`:

```kotlin
/** Drives the generate-plan dialog: either a computed preview or an inline weight prompt. */
sealed interface PlanGenerationDialog {
    data class Preview(val plan: GeneratedPlan) : PlanGenerationDialog
    data class WeightEntry(val weightInput: String = "", val error: String? = null) : PlanGenerationDialog
}
```

Add a field to `PlanUiState` (after `message`):

```kotlin
    val message: String? = null,
    val generationDialog: PlanGenerationDialog? = null,
```

- [ ] **Step 4: Update the constructor and add the actions**

Change the `PlanViewModel` constructor:

```kotlin
class PlanViewModel(
    private val planRepository: PlanRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val logRepository: LogRepository,
    private val planGenerator: PlanGenerator = PlanGenerator(),
) : ViewModel() {
```

Add these methods inside the class (e.g. just above the private `edit` helper):

```kotlin
    fun generateFromProfile() {
        viewModelScope.launch {
            val profile = userProfileStore.preferences.first()
            handleOutcome(planGenerator.generate(profile, latestLoggedWeightKg()))
        }
    }

    fun submitWeight(value: String) {
        val weight = value.toNullableDouble()
        if (weight == null || weight <= 0.0) {
            _uiState.update {
                val dialog = it.generationDialog
                if (dialog is PlanGenerationDialog.WeightEntry) {
                    it.copy(generationDialog = dialog.copy(error = "Enter a valid weight in kg."))
                } else {
                    it
                }
            }
            return
        }
        viewModelScope.launch {
            val profile = userProfileStore.preferences.first()
            handleOutcome(planGenerator.generate(profile, weight))
        }
    }

    fun updateGenerationWeightInput(value: String) {
        _uiState.update {
            val dialog = it.generationDialog
            if (dialog is PlanGenerationDialog.WeightEntry) {
                it.copy(generationDialog = dialog.copy(weightInput = value, error = null))
            } else {
                it
            }
        }
    }

    fun applyGeneratedPlan() {
        val dialog = _uiState.value.generationDialog
        if (dialog !is PlanGenerationDialog.Preview) return
        val plan = dialog.plan
        _uiState.update {
            it.copy(
                targetCalories = plan.targetCalories.toString(),
                targetProteinG = plan.proteinG.toString(),
                targetCarbsG = plan.carbsG.toString(),
                targetFatG = plan.fatG.toString(),
                calorieZoneLowerBound = plan.zoneLower.toString(),
                calorieZoneUpperBound = plan.zoneUpper.toString(),
                generationDialog = null,
                dirty = true,
                message = null,
            )
        }
    }

    fun dismissGenerationDialog() {
        _uiState.update { it.copy(generationDialog = null) }
    }

    private suspend fun latestLoggedWeightKg(): Double? =
        logRepository.observeDailyLogs().first()
            .filter { it.bodyWeightKg != null }
            .maxByOrNull { it.date }
            ?.bodyWeightKg

    private fun handleOutcome(outcome: PlanGenerationOutcome) {
        _uiState.update { state ->
            when (outcome) {
                is PlanGenerationOutcome.Ready ->
                    state.copy(generationDialog = PlanGenerationDialog.Preview(outcome.plan), message = null)
                is PlanGenerationOutcome.NeedsWeight ->
                    state.copy(generationDialog = PlanGenerationDialog.WeightEntry(), message = null)
                is PlanGenerationOutcome.MissingProfileFields ->
                    state.copy(
                        generationDialog = null,
                        message = "Complete your profile in Settings first: " +
                            "${outcome.fields.joinToString(", ")}.",
                    )
            }
        }
    }
```

- [ ] **Step 5: Update AppContainer wiring**

In `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`, replace the line:

```kotlin
            PlanViewModel::class.java -> PlanViewModel(container.planRepository)
```

with:

```kotlin
            PlanViewModel::class.java -> PlanViewModel(
                planRepository = container.planRepository,
                userProfileStore = container.userProfilePreferencesStore,
                logRepository = container.logRepository,
            )
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.plan.PlanViewModelGenerateTest"`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/plan/PlanViewModel.kt \
        app/src/main/java/com/zack/recomptracker/core/AppContainer.kt \
        app/src/test/java/com/zack/recomptracker/ui/plan/PlanViewModelGenerateTest.kt
git commit -m "feat(plan): wire generate-from-profile into PlanViewModel"
```

---

## Task 4: Plan screen UI (button + dialogs)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanGenerationDialogs.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt`

No unit test (Compose UI); verified by a successful build.

- [ ] **Step 1: Create the dialog composables**

Create `app/src/main/java/com/zack/recomptracker/ui/plan/PlanGenerationDialogs.kt`:

```kotlin
package com.zack.recomptracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.zack.recomptracker.domain.plan.GeneratedPlan

@Composable
fun PlanPreviewDialog(
    plan: GeneratedPlan,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generated plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BreakdownRow("Weight used", "${plan.weightKgUsed} kg")
                BreakdownRow("BMR", "${plan.bmr} kcal")
                BreakdownRow("TDEE (×${plan.activityFactor})", "${plan.tdee} kcal")
                BreakdownRow("Goal adjustment", "${plan.goalDeltaPercent}%")
                Text(
                    "Target: ${plan.targetCalories} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                BreakdownRow("Protein", "${plan.proteinG} g")
                BreakdownRow("Carbs", "${plan.carbsG} g")
                BreakdownRow("Fat", "${plan.fatG} g")
                BreakdownRow("Calorie zone", "${plan.zoneLower}–${plan.zoneUpper} kcal")
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WeightEntryDialog(
    state: PlanGenerationDialog.WeightEntry,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter your weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No logged bodyweight found. Enter your current weight to generate a plan.")
                OutlinedTextField(
                    value = state.weightInput,
                    onValueChange = onValueChange,
                    label = { Text("Weight") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.error != null) {
                    Text(state.error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

- [ ] **Step 2: Add the "Generate from profile" button to PlanScreen**

In `app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt`, add the new item immediately after the header `item { ... }` block (the one containing `Text("Plan", ...)`), before the `SectionCard("Nutrition targets")` item:

```kotlin
        item {
            LiquidSecondaryButton(
                text = "Generate from profile",
                onClick = viewModel::generateFromProfile,
                modifier = Modifier.fillMaxWidth(),
            )
        }
```

`LiquidSecondaryButton` and `Modifier.fillMaxWidth` are already imported in this file.

- [ ] **Step 3: Host the generation dialogs in PlanScreen**

In `PlanScreen.kt`, add this block at the end of the `@Composable fun PlanScreen` body, right after the existing `if (showDatePicker) { ... }` block (still inside the function):

```kotlin
    when (val dialog = state.generationDialog) {
        is PlanGenerationDialog.Preview -> PlanPreviewDialog(
            plan = dialog.plan,
            onApply = viewModel::applyGeneratedPlan,
            onDismiss = viewModel::dismissGenerationDialog,
        )
        is PlanGenerationDialog.WeightEntry -> WeightEntryDialog(
            state = dialog,
            onValueChange = viewModel::updateGenerationWeightInput,
            onConfirm = { viewModel.submitWeight(dialog.weightInput) },
            onDismiss = viewModel::dismissGenerationDialog,
        )
        null -> Unit
    }
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Full unit test run**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (all tests, including the 3 new test classes, pass).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/plan/PlanGenerationDialogs.kt \
        app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt
git commit -m "feat(plan): add generate-from-profile button and preview/weight dialogs"
```

---

## Manual Verification

After Task 4, on a device/emulator:

1. With a complete profile and at least one logged bodyweight → Plan screen → **Generate from profile** → preview dialog shows BMR/TDEE/target/macros → **Apply** → fields populate, **Save** persists.
2. With a complete profile but no logged weight → tapping Generate shows the weight-entry dialog → enter weight → **Continue** → preview appears.
3. With an incomplete profile (e.g. no goal) → tapping Generate shows the "Complete your profile in Settings first: …" message, no dialog.

---

## Self-Review Notes

- **Spec coverage:** BMR (T1), activity factor (T1), goal deltas (T1), macro split incl. cut protein bump (T1), zone ±100 (T1), weight source = latest logged + fallback ask (T3 `latestLoggedWeightKg` + WeightEntry), missing-field handling (T2/T3), preview-then-apply (T3/T4), placement on Plan screen (T4). All covered.
- **Carb ≥0 clamp** is defensive (unreachable for self-consistent single-weight inputs since protein scales with the same weight that drives BMR); kept in code, not unit-tested in isolation.
- **Type consistency:** `GeneratedPlan`, `PlanGenerationOutcome`, `PlanGenerationDialog`, `PlanCalculatorInput` names and fields are identical across tasks.
