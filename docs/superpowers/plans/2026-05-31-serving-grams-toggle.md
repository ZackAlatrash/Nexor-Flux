# Serving / Grams Toggle — Always Visible

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Always show the Servings / Grams toggle in the logging bottom sheet, even for foods that have no explicit household serving defined.

**Architecture:** The toggle already exists in `AmountSheet` but is gated by `state.canUseServings` (which is `false` when a food lacks `householdServingGrams`). The fix is to always show the toggle and fall back to 100 g-per-serving when no explicit serving size is stored. Two files change: the ViewModel (state computation + initial mode) and the Screen (UI guard removal + reference text).

**Tech Stack:** Kotlin, Jetpack Compose, no new dependencies.

---

## File Map

| File | Change |
|---|---|
| `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt` | `resolvedGrams` fallback; `requestLogFood` always defaults to SERVINGS |
| `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt` | Remove `canUseServings` guard on segmented button; update reference text |
| `app/src/test/java/com/zack/recomptracker/ui/FoodLibraryUiStateTest.kt` | Update stale test; add fallback test |

---

### Task 1: Update `resolvedGrams` to fall back to 100 g/serving

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt` — `FoodLibraryUiState.resolvedGrams`

- [ ] **Step 1: Write the failing test**

In `FoodLibraryUiStateTest.kt`, replace the `foodWithoutHouseholdServingIsGramsOnly` test (which tests old behaviour) and add a new test asserting the fallback:

```kotlin
@Test
fun foodWithoutHouseholdServingIsGramsOnly() {
    // canUseServings remains false — used only for display/reference text
    val plain = whey.copy(householdServingName = null, householdServingGrams = null)
    val state = FoodLibraryUiState(pendingFood = plain)
    assertEquals(false, state.canUseServings)
}

@Test
fun servingsModeWithoutHouseholdServingFallsBackTo100gPerServing() {
    val plain = whey.copy(householdServingName = null, householdServingGrams = null)
    val state = FoodLibraryUiState(
        pendingFood = plain,
        amountMode = AmountMode.SERVINGS,
        servingsValue = "2",
    )
    assertEquals(200.0, state.resolvedGrams!!, 0.001)
    assertEquals(240, state.previewMacros!!.calories) // whey has 120 kcal/100 g; 200 g → 240 kcal
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.FoodLibraryUiStateTest.servingsModeWithoutHouseholdServingFallsBackTo100gPerServing" 2>&1 | tail -20
```

Expected: FAIL — `resolvedGrams` returns `null` because the current code does `food.householdServingGrams ?: return null`.

- [ ] **Step 3: Change `resolvedGrams` in `FoodLibraryUiState`**

In `FoodLibraryViewModel.kt`, locate the `resolvedGrams` computed property inside `FoodLibraryUiState`. Change the SERVINGS branch from:

```kotlin
AmountMode.SERVINGS -> {
    val servings = servingsValue.toDoubleOrNull() ?: return null
    val perServing = food.householdServingGrams ?: return null
    if (servings < 1.0 || perServing < 1.0) null
    else FoodScaling.gramsForServings(servings, perServing)
}
```

to:

```kotlin
AmountMode.SERVINGS -> {
    val servings = servingsValue.toDoubleOrNull() ?: return null
    val perServing = food.householdServingGrams ?: 100.0
    if (servings < 1.0 || perServing < 1.0) null
    else FoodScaling.gramsForServings(servings, perServing)
}
```

- [ ] **Step 4: Run the new test and all UiState tests**

```
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.FoodLibraryUiStateTest" 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/FoodLibraryUiStateTest.kt
git commit -m "fix: serving mode falls back to 100 g/serving for foods without household serving"
```

---

### Task 2: Default to SERVINGS mode when opening any food

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt` — `requestLogFood()`

Currently `requestLogFood` sets `amountMode = if (canServings) AmountMode.SERVINGS else AmountMode.GRAMS`. Since all foods can now use servings (via the 100 g fallback), always default to SERVINGS.

- [ ] **Step 1: Update `requestLogFood`**

Locate `requestLogFood` in `FoodLibraryViewModel`. Change:

```kotlin
fun requestLogFood(food: SavedFoodEntity) {
    val canServings = (food.householdServingGrams ?: 0.0) >= 1.0 && !food.householdServingName.isNullOrBlank()
    _uiState.update {
        it.copy(
            showAmountSheet = true,
            pendingFood = food,
            editingEntryId = null,
            amountMode = if (canServings) AmountMode.SERVINGS else AmountMode.GRAMS,
            servingsValue = "1",
            gramsValue = "100",
            message = null,
        )
    }
}
```

to:

```kotlin
fun requestLogFood(food: SavedFoodEntity) {
    _uiState.update {
        it.copy(
            showAmountSheet = true,
            pendingFood = food,
            editingEntryId = null,
            amountMode = AmountMode.SERVINGS,
            servingsValue = "1",
            gramsValue = "100",
            message = null,
        )
    }
}
```

- [ ] **Step 2: Run all unit tests**

```
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: all tests PASS (no test covers initial `amountMode` selection in `requestLogFood`, so nothing breaks).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt
git commit -m "feat: always open logging sheet in servings mode"
```

---

### Task 3: Always show the Servings/Grams toggle in the UI

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt` — `AmountSheet` composable

- [ ] **Step 1: Remove the `if (state.canUseServings)` guard and update the reference line**

In `AmountSheet`, replace:

```kotlin
val reference = if (state.canUseServings) {
    "1 ${food.householdServingName} = ${food.householdServingGrams?.toInt() ?: "?"} g · ${food.calories} kcal / 100 g"
} else {
    "${food.calories} kcal / 100 g"
}
Text(reference, color = Secondary, fontSize = 11.sp)

if (state.canUseServings) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = state.amountMode == AmountMode.SERVINGS,
            onClick = { viewModel.onAmountModeChanged(AmountMode.SERVINGS) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Servings") }
        SegmentedButton(
            selected = state.amountMode == AmountMode.GRAMS,
            onClick = { viewModel.onAmountModeChanged(AmountMode.GRAMS) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Grams") }
    }
}
```

with:

```kotlin
val servingLabel = food.householdServingName ?: "serving"
val servingGrams = food.householdServingGrams?.toInt() ?: 100
val reference = "1 $servingLabel = $servingGrams g · ${food.calories} kcal / 100 g"
Text(reference, color = Secondary, fontSize = 11.sp)

SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    SegmentedButton(
        selected = state.amountMode == AmountMode.SERVINGS,
        onClick = { viewModel.onAmountModeChanged(AmountMode.SERVINGS) },
        shape = SegmentedButtonDefaults.itemShape(0, 2),
    ) { Text("Servings") }
    SegmentedButton(
        selected = state.amountMode == AmountMode.GRAMS,
        onClick = { viewModel.onAmountModeChanged(AmountMode.GRAMS) },
        shape = SegmentedButtonDefaults.itemShape(1, 2),
    ) { Text("Grams") }
}
```

- [ ] **Step 2: Run all unit tests**

```
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: all tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
git commit -m "feat: always show servings/grams toggle for all foods"
```

---

## Self-Review

**Spec coverage:**
- ✅ Toggle shows for all foods (Task 3)
- ✅ Foods without serving info default to 100 g/serving (Task 1)
- ✅ Sheet opens in servings mode by default (Task 2)

**Placeholder scan:** None found.

**Type consistency:** `AmountMode.SERVINGS`, `AmountMode.GRAMS`, `state.canUseServings`, `resolvedGrams` — all reference existing symbols, no name drift.
