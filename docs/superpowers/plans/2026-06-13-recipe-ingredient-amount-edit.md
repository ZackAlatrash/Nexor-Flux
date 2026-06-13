# Recipe Ingredient Amount Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user tap an ingredient in the Recipe Builder to edit its amount (grams/servings, rescaling macros live) — or its raw macros when the ingredient has no per-100g base.

**Architecture:** Extract the existing `AmountStepper`/`AmountPreviewStat`/`AmountMode` from FoodLibrary into a shared `ui/component` location so both editors share controls. Add editor state + actions to `RecipeBuilderViewModel` (reusing the shared `FoodScaling.scale`), and a `ModalBottomSheet` to `RecipeBuilderScreen` opened by tapping a row.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, ViewModel + StateFlow, JUnit unit tests.

---

## File Structure

**Created**
- `app/src/main/java/com/zack/recomptracker/ui/component/AmountControls.kt` — public `AmountMode` enum, `AmountStepper`, `AmountPreviewStat` (moved from FoodLibrary).

**Modified**
- `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt` — delete the two private composables; import the shared ones + `AmountMode`.
- `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt` — delete the `AmountMode` declaration; import it from `ui/component`.
- `app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderViewModel.kt` — editor state + actions.
- `app/src/test/java/com/zack/recomptracker/ui/RecipeBuilderViewModelTest.kt` — editor tests.
- `app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderScreen.kt` — tappable rows + `IngredientAmountSheet`.

**Reused as-is**
- `domain/food/FoodScaling.kt` (`scale`, `MIN_GRAMS`, `MIN_SERVINGS`, `SERVING_STEP`), `FoodMacros`.
- `RecipeBuilderViewModel.editIngredientAt`.

**Build/test commands** (run from worktree root):
- Type-check: `./gradlew :app:compileDebugKotlin`
- All unit tests: `./gradlew :app:testDebugUnitTest`
- One class: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.RecipeBuilderViewModelTest"`

---

## Task 1: Extract shared amount controls

Mechanical move — no behavior change. Verified by compile + existing tests.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/AmountControls.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`

- [ ] **Step 1: Create the shared file**

Create `app/src/main/java/com/zack/recomptracker/ui/component/AmountControls.kt`:

```kotlin
package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.TextSecondary

/** Whether an amount is entered as servings or grams. */
enum class AmountMode { SERVINGS, GRAMS }

@Composable
fun AmountStepper(
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    caption: String,
    suffix: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(CornerSmall))
                .background(Color(0x0DFFFFFF))
                .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(CornerSmall))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMinus,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("−", fontSize = 20.sp, color = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(suffix) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (caption.isNotBlank()) {
                Text(caption, color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(CornerSmall))
                .background(Color(0x0DFFFFFF))
                .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(CornerSmall))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlus,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 20.sp, color = Color.White)
        }
    }
}

@Composable
fun AmountPreviewStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}
```

- [ ] **Step 2: Remove the originals from FoodLibrary**

In `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`, delete the two `private fun AmountStepper(...)` and `private fun AmountPreviewStat(...)` composables (the full function bodies). Add these imports:

```kotlin
import com.zack.recomptracker.ui.component.AmountMode
import com.zack.recomptracker.ui.component.AmountStepper
import com.zack.recomptracker.ui.component.AmountPreviewStat
```

In `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`, delete the line:

```kotlin
enum class AmountMode { SERVINGS, GRAMS }
```

and add the import:

```kotlin
import com.zack.recomptracker.ui.component.AmountMode
```

> Unused imports elsewhere are warnings, not errors — only remove one if the compiler flags it as unused and it's trivially so. Don't hunt.

- [ ] **Step 3: Verify compile + existing tests**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (no behavior changed).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/AmountControls.kt app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt
git commit -m "refactor(food): extract shared AmountStepper/AmountMode to ui/component"
```

---

## Task 2: RecipeBuilderViewModel — editor state + actions

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderViewModel.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/RecipeBuilderViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

In `RecipeBuilderViewModelTest.kt`, add imports:

```kotlin
import com.zack.recomptracker.ui.component.AmountMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
```

Add a helper for a scalable ingredient (alongside the existing `ingredient(...)` helper):

```kotlin
    private fun scalableIngredient(name: String = "Rice", grams: Double = 200.0, byServings: Boolean = false) =
        RecipeIngredientEntity(
            recipeId = 0, name = name, sortOrder = 0,
            calories = 260, proteinG = 5.0, carbsG = 56.0, fatG = 0.6,
            amountGrams = grams,
            basePer100Calories = 130, basePer100ProteinG = 2.5, basePer100CarbsG = 28.0, basePer100FatG = 0.3,
            entryServingName = "cup", entryServingGrams = 100.0, loggedByServings = byServings,
        )

    private fun vmWith(vararg ings: RecipeIngredientEntity): RecipeBuilderViewModel {
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("X")), SavedStateHandle())
        ings.forEach(vm::addIngredient)
        return vm
    }
```

Add the tests:

```kotlin
    @Test
    fun `startEditing opens grams mode for scalable ingredient`() {
        val vm = vmWith(scalableIngredient(grams = 200.0))
        vm.startEditingIngredient(0)
        val ed = vm.uiState.value.ingredientEditor!!
        assertTrue(ed.scalable)
        assertTrue(ed.hasServings)
        assertEquals(AmountMode.GRAMS, ed.mode)
        assertEquals("200", ed.gramsInput)
        assertEquals(260, ed.preview?.calories)
    }

    @Test
    fun `editing grams rescales macros on confirm`() {
        val vm = vmWith(scalableIngredient(grams = 200.0))
        vm.startEditingIngredient(0)
        vm.onEditorGramsChanged("100")
        vm.confirmIngredientEdit()
        val ing = vm.uiState.value.ingredients[0]
        assertEquals(100.0, ing.amountGrams!!, 0.0)
        assertEquals(130, ing.calories)
        assertEquals(2.5, ing.proteinG, 0.001)
        assertNull(vm.uiState.value.ingredientEditor)
    }

    @Test
    fun `servings mode converts to grams on confirm`() {
        val vm = vmWith(scalableIngredient(grams = 200.0, byServings = true))
        vm.startEditingIngredient(0)
        val ed = vm.uiState.value.ingredientEditor!!
        assertEquals(AmountMode.SERVINGS, ed.mode)
        assertEquals("2", ed.servingsInput)
        vm.onEditorServingsChanged("3")
        vm.confirmIngredientEdit()
        val ing = vm.uiState.value.ingredients[0]
        assertEquals(300.0, ing.amountGrams!!, 0.0)
        assertEquals(390, ing.calories)
        assertTrue(ing.loggedByServings)
    }

    @Test
    fun `startEditing opens raw mode for non-scalable ingredient`() {
        val vm = vmWith(ingredient("Mystery"))
        vm.startEditingIngredient(0)
        val ed = vm.uiState.value.ingredientEditor!!
        assertFalse(ed.scalable)
        assertEquals("100", ed.caloriesInput)
    }

    @Test
    fun `raw macro edit updates ingredient on confirm`() {
        val vm = vmWith(ingredient("Mystery"))
        vm.startEditingIngredient(0)
        vm.onEditorCaloriesChanged("250")
        vm.onEditorProteinChanged("30")
        vm.confirmIngredientEdit()
        val ing = vm.uiState.value.ingredients[0]
        assertEquals(250, ing.calories)
        assertEquals(30.0, ing.proteinG, 0.001)
        assertNull(vm.uiState.value.ingredientEditor)
    }

    @Test
    fun `cancel closes editor without changing the ingredient`() {
        val vm = vmWith(ingredient("Mystery"))
        vm.startEditingIngredient(0)
        vm.onEditorCaloriesChanged("999")
        vm.cancelIngredientEdit()
        assertNull(vm.uiState.value.ingredientEditor)
        assertEquals(100, vm.uiState.value.ingredients[0].calories)
    }

    @Test
    fun `grams below minimum clamps on confirm`() {
        val vm = vmWith(scalableIngredient(grams = 200.0))
        vm.startEditingIngredient(0)
        vm.onEditorGramsChanged("0")
        vm.confirmIngredientEdit()
        assertEquals(FoodScaling.MIN_GRAMS, vm.uiState.value.ingredients[0].amountGrams!!, 0.0)
    }
```

Add the import for `FoodScaling` to the test:

```kotlin
import com.zack.recomptracker.domain.food.FoodScaling
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.RecipeBuilderViewModelTest"`
Expected: FAIL — unresolved `ingredientEditor`, `startEditingIngredient`, etc.

- [ ] **Step 3: Implement the editor in RecipeBuilderViewModel.kt**

Add imports:

```kotlin
import com.zack.recomptracker.domain.food.FoodMacros
import com.zack.recomptracker.domain.food.FoodScaling
import com.zack.recomptracker.ui.component.AmountMode
import kotlin.math.roundToInt
```

Add the editor state type (top-level, near `RecipeBuilderUiState`):

```kotlin
/** Open amount-editor sheet for one ingredient, or null when closed. */
data class IngredientEditorState(
    val index: Int,
    val name: String,
    val scalable: Boolean,
    val hasServings: Boolean = false,
    val mode: AmountMode = AmountMode.GRAMS,
    val gramsInput: String = "",
    val servingsInput: String = "",
    val caloriesInput: String = "",
    val proteinInput: String = "",
    val carbsInput: String = "",
    val fatInput: String = "",
    val preview: FoodMacros? = null,
)
```

Add a field to `RecipeBuilderUiState` (after `isGeneratingName`):

```kotlin
    /** Active ingredient amount/macro editor, or null when no sheet is open. */
    val ingredientEditor: IngredientEditorState? = null,
```

Add these members to `RecipeBuilderViewModel` (e.g. after `editIngredientAt`):

```kotlin
    fun startEditingIngredient(index: Int) {
        val ing = _uiState.value.ingredients.getOrNull(index) ?: return
        val base = ing.basePer100()
        val hasServings = ing.entryServingGrams != null
        val editor = if (ing.amountGrams != null && base != null) {
            val grams = ing.amountGrams
            val mode = if (ing.loggedByServings && hasServings) AmountMode.SERVINGS else AmountMode.GRAMS
            val servings = ing.entryServingGrams?.let { grams / it } ?: 0.0
            IngredientEditorState(
                index = index,
                name = ing.name,
                scalable = true,
                hasServings = hasServings,
                mode = mode,
                gramsInput = formatAmount(grams),
                servingsInput = formatAmount(servings),
                preview = FoodScaling.scale(base, grams),
            )
        } else {
            IngredientEditorState(
                index = index,
                name = ing.name,
                scalable = false,
                caloriesInput = ing.calories.toString(),
                proteinInput = formatAmount(ing.proteinG),
                carbsInput = formatAmount(ing.carbsG),
                fatInput = formatAmount(ing.fatG),
            )
        }
        _uiState.update { it.copy(ingredientEditor = editor) }
    }

    fun onEditorAmountModeChanged(mode: AmountMode) = updateEditor { it.copy(mode = mode) }
    fun onEditorGramsChanged(text: String) = updateEditor { it.copy(gramsInput = text) }
    fun onEditorServingsChanged(text: String) = updateEditor { it.copy(servingsInput = text) }
    fun onEditorCaloriesChanged(text: String) = updateEditor { it.copy(caloriesInput = text) }
    fun onEditorProteinChanged(text: String) = updateEditor { it.copy(proteinInput = text) }
    fun onEditorCarbsChanged(text: String) = updateEditor { it.copy(carbsInput = text) }
    fun onEditorFatChanged(text: String) = updateEditor { it.copy(fatInput = text) }

    fun stepEditorGrams(delta: Int) = updateEditor {
        val cur = it.gramsInput.toDoubleOrNull() ?: 0.0
        it.copy(gramsInput = formatAmount((cur + delta).coerceAtLeast(FoodScaling.MIN_GRAMS)))
    }

    fun stepEditorServings(delta: Double) = updateEditor {
        val cur = it.servingsInput.toDoubleOrNull() ?: 0.0
        it.copy(servingsInput = formatAmount((cur + delta).coerceAtLeast(FoodScaling.MIN_SERVINGS)))
    }

    fun confirmIngredientEdit() {
        val editor = _uiState.value.ingredientEditor ?: return
        val ing = _uiState.value.ingredients.getOrNull(editor.index)
        if (ing == null) {
            _uiState.update { it.copy(ingredientEditor = null) }
            return
        }
        val updated = if (editor.scalable) {
            val base = ing.basePer100() ?: return
            val grams = (resolveGrams(editor, ing) ?: return).coerceAtLeast(FoodScaling.MIN_GRAMS)
            val scaled = FoodScaling.scale(base, grams)
            ing.copy(
                amountGrams = grams,
                calories = scaled.calories,
                proteinG = scaled.proteinG,
                carbsG = scaled.carbsG,
                fatG = scaled.fatG,
                loggedByServings = editor.mode == AmountMode.SERVINGS,
            )
        } else {
            ing.copy(
                calories = editor.caloriesInput.toDoubleOrNull()?.roundToInt() ?: 0,
                proteinG = editor.proteinInput.toDoubleOrNull() ?: 0.0,
                carbsG = editor.carbsInput.toDoubleOrNull() ?: 0.0,
                fatG = editor.fatInput.toDoubleOrNull() ?: 0.0,
            )
        }
        editIngredientAt(editor.index, updated)
        _uiState.update { it.copy(ingredientEditor = null) }
    }

    fun cancelIngredientEdit() = _uiState.update { it.copy(ingredientEditor = null) }

    private inline fun updateEditor(transform: (IngredientEditorState) -> IngredientEditorState) {
        _uiState.update { state ->
            val editor = state.ingredientEditor ?: return@update state
            state.copy(ingredientEditor = recomputePreview(transform(editor)))
        }
    }

    private fun recomputePreview(editor: IngredientEditorState): IngredientEditorState {
        if (!editor.scalable) return editor
        val ing = _uiState.value.ingredients.getOrNull(editor.index) ?: return editor
        val base = ing.basePer100() ?: return editor
        val grams = resolveGrams(editor, ing)?.coerceAtLeast(FoodScaling.MIN_GRAMS)
        return editor.copy(preview = grams?.let { FoodScaling.scale(base, it) })
    }

    private fun resolveGrams(editor: IngredientEditorState, ing: RecipeIngredientEntity): Double? =
        when (editor.mode) {
            AmountMode.GRAMS -> editor.gramsInput.toDoubleOrNull()
            AmountMode.SERVINGS -> {
                val servings = editor.servingsInput.toDoubleOrNull() ?: return null
                val servingGrams = ing.entryServingGrams ?: return null
                servings * servingGrams
            }
        }

    private fun RecipeIngredientEntity.basePer100(): FoodMacros? {
        val cal = basePer100Calories ?: return null
        return FoodMacros(
            calories = cal,
            proteinG = basePer100ProteinG ?: 0.0,
            carbsG = basePer100CarbsG ?: 0.0,
            fatG = basePer100FatG ?: 0.0,
        )
    }

    private fun formatAmount(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.RecipeBuilderViewModelTest"`
Expected: PASS (all old + 7 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderViewModel.kt app/src/test/java/com/zack/recomptracker/ui/RecipeBuilderViewModelTest.kt
git commit -m "feat(recipes): ingredient amount/macro editor state in builder VM"
```

---

## Task 3: RecipeBuilderScreen — tappable rows + amount sheet

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderScreen.kt`

UI task — verified by `compileDebugKotlin` + manual run.

- [ ] **Step 1: Make the ingredient row open the editor**

In `RecipeBuilderScreen.kt`, change `IngredientRow` to accept an `onClick` and make its info column clickable.

Update the call site (inside `itemsIndexed`):

```kotlin
                        IngredientRow(
                            ingredient = ingredient,
                            onClick = { viewModel.startEditingIngredient(index) },
                            onRemove = { viewModel.removeIngredientAt(index) },
                        )
```

Update the `IngredientRow` signature and make the left info `Column` tappable:

```kotlin
@Composable
private fun IngredientRow(
    ingredient: RecipeIngredientEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                ingredient.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val amountText = ingredient.amountGrams?.let { "${it.toInt()}g" } ?: ""
            Text(
                "$amountText · ${ingredient.calories} kcal  P${ingredient.proteinG.toInt()}g  C${ingredient.carbsG.toInt()}g  F${ingredient.fatG.toInt()}g",
                fontSize = 11.sp,
                color = TextMuted,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0x0DFFFFFF))
                .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(7.dp))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 13.sp, color = Color(0xBFFF4444))
        }
    }
}
```

- [ ] **Step 2: Render the amount sheet**

Add imports:

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.rememberModalBottomSheetState
import com.zack.recomptracker.ui.component.AmountMode
import com.zack.recomptracker.ui.component.AmountPreviewStat
import com.zack.recomptracker.ui.component.AmountStepper
import com.zack.recomptracker.domain.food.FoodScaling
```

(`Spacer`, `Column`, `Row`, `fillMaxWidth`, `padding`, `Arrangement`, `Text`, `OutlinedTextField`, `LiquidPrimaryButton`, `dp`, `sp`, `FontWeight`, `TextMuted` are already imported.)

Inside `RecipeBuilderScreen`, after the `Column(modifier = Modifier.fillMaxSize())` block that holds the screen (i.e. as a sibling at the end of the composable body), add:

```kotlin
    state.ingredientEditor?.let { editor ->
        IngredientAmountSheet(editor = editor, viewModel = viewModel)
    }
```

> Place this so it's part of the same outer scope as the main `Column`. If the screen's root is a single `Column`, wrap the root `Column` and this sheet call in a `Box(Modifier.fillMaxSize()) { ... }`, or simply put the `state.ingredientEditor?.let { ... }` call immediately after the root `Column { ... }` block within the composable function body — `ModalBottomSheet` overlays regardless of where it sits in the composition.

Add the sheet composable at the bottom of the file:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientAmountSheet(
    editor: IngredientEditorState,
    viewModel: RecipeBuilderViewModel,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = viewModel::cancelIngredientEdit,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(editor.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)

            if (editor.scalable) {
                if (editor.hasServings) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = editor.mode == AmountMode.SERVINGS,
                            onClick = { viewModel.onEditorAmountModeChanged(AmountMode.SERVINGS) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("Servings") }
                        SegmentedButton(
                            selected = editor.mode == AmountMode.GRAMS,
                            onClick = { viewModel.onEditorAmountModeChanged(AmountMode.GRAMS) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("Grams") }
                    }
                }
                if (editor.mode == AmountMode.SERVINGS) {
                    AmountStepper(
                        value = editor.servingsInput,
                        onValueChange = viewModel::onEditorServingsChanged,
                        onMinus = { viewModel.stepEditorServings(-FoodScaling.SERVING_STEP) },
                        onPlus = { viewModel.stepEditorServings(FoodScaling.SERVING_STEP) },
                        caption = "",
                        suffix = "servings",
                    )
                } else {
                    AmountStepper(
                        value = editor.gramsInput,
                        onValueChange = viewModel::onEditorGramsChanged,
                        onMinus = { viewModel.stepEditorGrams(-10) },
                        onPlus = { viewModel.stepEditorGrams(10) },
                        caption = "",
                        suffix = "g",
                    )
                }
                val preview = editor.preview
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AmountPreviewStat("kcal", preview?.calories?.toString() ?: "—")
                    AmountPreviewStat("P", preview?.proteinG?.toInt()?.toString() ?: "—")
                    AmountPreviewStat("C", preview?.carbsG?.toInt()?.toString() ?: "—")
                    AmountPreviewStat("F", preview?.fatG?.toInt()?.toString() ?: "—")
                }
            } else {
                MacroField("Calories (kcal)", editor.caloriesInput, viewModel::onEditorCaloriesChanged)
                MacroField("Protein (g)", editor.proteinInput, viewModel::onEditorProteinChanged)
                MacroField("Carbs (g)", editor.carbsInput, viewModel::onEditorCarbsChanged)
                MacroField("Fat (g)", editor.fatInput, viewModel::onEditorFatChanged)
            }

            LiquidPrimaryButton(text = "Save", onClick = viewModel::confirmIngredientEdit)
        }
    }
}

@Composable
private fun MacroField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
```

> The `caption = editor.preview?.let { "" } ?: ""` line is intentionally empty (the AmountStepper caption is unused here; the macro preview row below shows the resolved values). You may pass `caption = ""` directly.

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `LiquidPrimaryButton`/`SegmentedButton` param names differ, adjust to match the real signatures.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderScreen.kt
git commit -m "feat(recipes): tap ingredient to edit amount/macros in builder"
```

---

## Task 4: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test (device/emulator)**

- Build a recipe (add a food via the picker, or save-from-meal). Tap an ingredient row → the amount sheet opens.
- For a food added with a serving: the Servings/Grams toggle appears; switching modes and stepping updates the live kcal/P/C/F preview; Save updates the row and the recipe totals.
- For a grams-only food: only the grams stepper shows; Save rescales correctly.
- For an ingredient with no per-100g base: the sheet shows four macro fields; editing + Save updates the macros.
- Dismissing the sheet leaves the ingredient unchanged.

- [ ] **Step 4: Final commit (only if manual fixes were needed)**

```bash
git add -A
git commit -m "fix(recipes): amount editor manual verification adjustments"
```

---

## Notes

- **Why reuse `FoodScaling`:** the per-100g→grams math is already implemented and unit-tested there; the VM just calls `scale`.
- **Scalable vs raw decision** mirrors the food log's `amountEditable` rule (`amountGrams != null && basePer100Calories != null`), so behavior is consistent across the app.
- **Servings availability** is gated on `entryServingGrams != null` so foods logged purely by grams don't show a meaningless Servings toggle.
