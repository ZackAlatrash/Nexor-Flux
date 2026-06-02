# Scanner: Serving Mode + Barcode Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a grams/serving toggle to the scan result sheet (when serving data is available), and replace the QR code icon with a barcode icon.

**Architecture:** `LogMode` enum added to the ViewModel layer; `ScanState.ProductFound` gains a `logMode` field and renames `amountGrams` → `amountInput`; the confirm functions resolve actual grams from the input + mode before scaling; the UI adds a `SingleChoiceSegmentedButtonRow` that is only rendered when `product.servingGrams != null`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), CameraX + ML Kit, Coroutines/Flow, Mockito-Kotlin for tests.

---

## File Map

| File | Change |
|---|---|
| `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt` | Add `LogMode` enum; extend `ScanState.ProductFound`; rename `amountGrams→amountInput`; add `onLogModeChanged`; update confirm functions |
| `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerScreen.kt` | Add segmented toggle + dynamic label in `ProductFoundSheet`; wire `onLogModeChanged` |
| `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt` | Swap icon import + usage |
| `app/src/test/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModelTest.kt` | Fix `amountGrams` → `amountInput`; add tests for mode switching and serving-mode confirm |

---

## Task 1: Swap QR code icon for barcode icon

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 1: Update the import**

In `FoodLibraryScreen.kt`, replace:

```kotlin
import androidx.compose.material.icons.filled.QrCodeScanner
```

with:

```kotlin
import androidx.compose.material.icons.filled.BarcodeScanner
```

- [ ] **Step 2: Update the icon usage**

On line 110, replace:

```kotlin
Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
```

with:

```kotlin
Icon(Icons.Default.BarcodeScanner, contentDescription = "Scan barcode")
```

- [ ] **Step 3: Build and verify no compile errors**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
git commit -m "fix: replace QrCodeScanner icon with BarcodeScanner in food library"
```

---

## Task 2: Add LogMode enum and update ScanState.ProductFound

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt`
- Modify: `app/src/test/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModelTest.kt`

- [ ] **Step 1: Add `LogMode` enum and update `ScanState.ProductFound` in the ViewModel**

In `BarcodeScannerViewModel.kt`, add the enum before the `ScanState` sealed class and update `ProductFound`:

```kotlin
enum class LogMode { GRAMS, SERVING }

sealed class ScanState {
    object Scanning : ScanState()
    object Loading : ScanState()
    data class ProductFound(
        val product: BarcodeProduct,
        val logMode: LogMode = LogMode.GRAMS,
        val amountInput: String = "100",
    ) : ScanState()
    object NotFound : ScanState()
    object NetworkError : ScanState()
    data class ShowingSuccess(val message: String) : ScanState()
    object Logged : ScanState()
}
```

- [ ] **Step 2: Update `onAmountChanged` to use the new field name**

Replace:

```kotlin
fun onAmountChanged(grams: String) {
    val current = _uiState.value.scanState as? ScanState.ProductFound ?: return
    _uiState.update { it.copy(scanState = current.copy(amountGrams = grams)) }
}
```

with:

```kotlin
fun onAmountChanged(grams: String) {
    val current = _uiState.value.scanState as? ScanState.ProductFound ?: return
    _uiState.update { it.copy(scanState = current.copy(amountInput = grams)) }
}
```

- [ ] **Step 3: Fix `confirmLog` to use `amountInput`**

In `confirmLog()`, replace:

```kotlin
val grams = productState.amountGrams.toDoubleOrNull()
```

with:

```kotlin
val grams = productState.amountInput.toDoubleOrNull()
```

- [ ] **Step 4: Fix `confirmLogAndSave` to use `amountInput`**

In `confirmLogAndSave()`, replace:

```kotlin
val grams = productState.amountGrams.toDoubleOrNull()
```

with:

```kotlin
val grams = productState.amountInput.toDoubleOrNull()
```

- [ ] **Step 5: Fix the broken test that references `amountGrams`**

In `BarcodeScannerViewModelTest.kt`, find the test `onAmountChanged updates ProductFound state` and replace:

```kotlin
assertEquals("150", state.amountGrams)
```

with:

```kotlin
assertEquals("150", state.amountInput)
```

- [ ] **Step 6: Run existing tests to confirm they still pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.scanner.BarcodeScannerViewModelTest"
```

Expected: all 8 tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModelTest.kt
git commit -m "refactor: add LogMode enum, rename amountGrams to amountInput in ScanState.ProductFound"
```

---

## Task 3: Implement onLogModeChanged with value conversion

**Files:**
- Modify: `app/src/test/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModelTest.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt`

- [ ] **Step 1: Write three failing tests for mode switching**

Add these tests to `BarcodeScannerViewModelTest.kt` (before the closing `}`):

```kotlin
@Test
fun `onLogModeChanged GRAMS to SERVING converts amount correctly`() = runTest {
    // servingGrams = 30.0; starting at 150g → 150/30 = 5.0 → "5"
    val product = BarcodeProduct("Test", 100, 10.0, 20.0, 5.0, "1 portion", 30.0, true)
    whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.Found(product))
    val vm = viewModel()
    vm.onBarcodeDetected("abc")
    testDispatcher.scheduler.advanceUntilIdle()

    vm.onAmountChanged("150")
    vm.onLogModeChanged(LogMode.SERVING)

    val state = vm.uiState.value.scanState as ScanState.ProductFound
    assertEquals(LogMode.SERVING, state.logMode)
    assertEquals("5", state.amountInput)
}

@Test
fun `onLogModeChanged GRAMS to SERVING trims trailing zeros`() = runTest {
    // servingGrams = 30.0; starting at 100g → 100/30 = 3.333... → "3.33"
    val product = BarcodeProduct("Test", 100, 10.0, 20.0, 5.0, "1 portion", 30.0, true)
    whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.Found(product))
    val vm = viewModel()
    vm.onBarcodeDetected("abc")
    testDispatcher.scheduler.advanceUntilIdle()

    vm.onLogModeChanged(LogMode.SERVING)

    val state = vm.uiState.value.scanState as ScanState.ProductFound
    assertEquals("3.33", state.amountInput)
}

@Test
fun `onLogModeChanged SERVING to GRAMS converts amount correctly`() = runTest {
    // servingGrams = 30.0; starting at 2.5 servings → 2.5*30 = 75g → "75"
    val product = BarcodeProduct("Test", 100, 10.0, 20.0, 5.0, "1 portion", 30.0, true)
    whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.Found(product))
    val vm = viewModel()
    vm.onBarcodeDetected("abc")
    testDispatcher.scheduler.advanceUntilIdle()

    vm.onLogModeChanged(LogMode.SERVING)
    vm.onAmountChanged("2.5")
    vm.onLogModeChanged(LogMode.GRAMS)

    val state = vm.uiState.value.scanState as ScanState.ProductFound
    assertEquals(LogMode.GRAMS, state.logMode)
    assertEquals("75", state.amountInput)
}
```

- [ ] **Step 2: Run the new tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.scanner.BarcodeScannerViewModelTest.onLogModeChanged*"
```

Expected: 3 tests FAIL with `Unresolved reference: onLogModeChanged` or `Unresolved reference: LogMode`.

- [ ] **Step 3: Implement `onLogModeChanged` in the ViewModel**

Add this function to `BarcodeScannerViewModel.kt` after `onAmountChanged`:

```kotlin
fun onLogModeChanged(mode: LogMode) {
    val current = _uiState.value.scanState as? ScanState.ProductFound ?: return
    if (current.logMode == mode) return
    val servingGrams = current.product.servingGrams ?: return

    val convertedInput = when (mode) {
        LogMode.SERVING -> {
            val grams = current.amountInput.toDoubleOrNull()
            if (grams != null && grams > 0) {
                "%.2f".format(grams / servingGrams).trimEnd('0').trimEnd('.')
            } else "1"
        }
        LogMode.GRAMS -> {
            val servings = current.amountInput.toDoubleOrNull()
            if (servings != null && servings > 0) {
                (servings * servingGrams).toInt().toString()
            } else "100"
        }
    }
    _uiState.update { it.copy(scanState = current.copy(logMode = mode, amountInput = convertedInput)) }
}
```

- [ ] **Step 4: Run the new tests to confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.scanner.BarcodeScannerViewModelTest.onLogModeChanged*"
```

Expected: 3 tests PASS.

- [ ] **Step 5: Run the full test suite to confirm no regressions**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.scanner.BarcodeScannerViewModelTest"
```

Expected: all 11 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModelTest.kt
git commit -m "feat: add onLogModeChanged with grams/serving conversion"
```

---

## Task 4: Update confirm functions to resolve actual grams from mode

**Files:**
- Modify: `app/src/test/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModelTest.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt`

- [ ] **Step 1: Write failing tests for serving-mode confirmation**

Add these tests to `BarcodeScannerViewModelTest.kt`:

```kotlin
@Test
fun `confirmLog in SERVING mode scales macros by servings times servingGrams`() = runTest {
    // servingGrams=30g, 200kcal/100g; 2 servings = 60g = 0.6 scale → 120 kcal
    val product = BarcodeProduct("Oats", 200, 8.0, 30.0, 4.0, "1 serving", 30.0, true)
    whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.Found(product))
    whenever(logRepository.addMealToSlot(any(), any())).thenReturn(1L)
    val vm = viewModel()
    vm.init(slotId = null, slotName = "")
    vm.onBarcodeDetected("oats123")
    testDispatcher.scheduler.advanceUntilIdle()

    vm.onLogModeChanged(LogMode.SERVING)
    vm.onAmountChanged("2")
    vm.confirmLog()
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(vm.uiState.value.scanState is ScanState.Logged)
    val captor = org.mockito.kotlin.argumentCaptor<com.zack.recomptracker.data.repository.MealEntryInput>()
    org.mockito.kotlin.verify(logRepository).addMealToSlot(captor.capture(), org.mockito.kotlin.isNull())
    assertEquals(120, captor.firstValue.calories)          // 200 * (60/100)
    assertEquals(60.0, captor.firstValue.amountGrams, 0.01)
}

@Test
fun `confirmLog in SERVING mode shows error when amountInput is blank`() = runTest {
    val product = BarcodeProduct("Oats", 200, 8.0, 30.0, 4.0, "1 serving", 30.0, true)
    whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.Found(product))
    val vm = viewModel()
    vm.onBarcodeDetected("oats123")
    testDispatcher.scheduler.advanceUntilIdle()

    vm.onLogModeChanged(LogMode.SERVING)
    vm.onAmountChanged("0")
    vm.confirmLog()
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(vm.uiState.value.scanState is ScanState.ProductFound)
    assertEquals("Enter a valid amount (min 1g).", vm.uiState.value.message)
}
```

- [ ] **Step 2: Run the new tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.scanner.BarcodeScannerViewModelTest.confirmLog in SERVING*"
```

Expected: 2 tests FAIL (the current confirm function ignores logMode).

- [ ] **Step 3: Update `confirmLog` to resolve actual grams from mode**

Replace the existing `confirmLog` function entirely:

```kotlin
fun confirmLog() {
    val state = _uiState.value
    val productState = state.scanState as? ScanState.ProductFound ?: return
    val product = productState.product
    val actualGrams = resolveGrams(productState)
    if (actualGrams == null || actualGrams < 1.0) {
        _uiState.update { it.copy(message = "Enter a valid amount (min 1g).") }
        return
    }
    val scale = actualGrams / 100.0
    viewModelScope.launch {
        logRepository.addMealToSlot(
            input = MealEntryInput(
                date = dateProvider.today(),
                mealType = MealEntryTypes.FOOD_LIBRARY,
                name = product.name,
                calories = (product.caloriesPer100g * scale).toInt(),
                proteinG = product.proteinPer100g * scale,
                carbsG = product.carbsPer100g * scale,
                fatG = product.fatPer100g * scale,
                amountGrams = actualGrams,
                basePer100Calories = product.caloriesPer100g,
                basePer100ProteinG = product.proteinPer100g,
                basePer100CarbsG = product.carbsPer100g,
                basePer100FatG = product.fatPer100g,
                entryServingName = null,
                entryServingGrams = null,
            ),
            slotId = state.slotId,
        )
        val slotLabel = state.slotName.ifBlank { "log" }
        _uiState.update { it.copy(scanState = ScanState.ShowingSuccess("Added to $slotLabel")) }
        delay(SUCCESS_OVERLAY_MS)
        _uiState.update { it.copy(scanState = ScanState.Logged) }
    }
}
```

- [ ] **Step 4: Update `confirmLogAndSave` to use the same helper**

Replace the existing `confirmLogAndSave` function entirely:

```kotlin
fun confirmLogAndSave() {
    val state = _uiState.value
    val productState = state.scanState as? ScanState.ProductFound ?: return
    val product = productState.product
    val actualGrams = resolveGrams(productState)
    if (actualGrams == null || actualGrams < 1.0) {
        _uiState.update { it.copy(message = "Enter a valid amount (min 1g).") }
        return
    }
    val scale = actualGrams / 100.0
    viewModelScope.launch {
        logRepository.addMealToSlot(
            input = MealEntryInput(
                date = dateProvider.today(),
                mealType = MealEntryTypes.FOOD_LIBRARY,
                name = product.name,
                calories = (product.caloriesPer100g * scale).toInt(),
                proteinG = product.proteinPer100g * scale,
                carbsG = product.carbsPer100g * scale,
                fatG = product.fatPer100g * scale,
                amountGrams = actualGrams,
                basePer100Calories = product.caloriesPer100g,
                basePer100ProteinG = product.proteinPer100g,
                basePer100CarbsG = product.carbsPer100g,
                basePer100FatG = product.fatPer100g,
                entryServingName = null,
                entryServingGrams = null,
            ),
            slotId = state.slotId,
        )
        logRepository.saveFood(
            SavedFoodEntity(
                name = product.name,
                servingName = product.servingName ?: "100g",
                calories = product.caloriesPer100g,
                proteinG = product.proteinPer100g,
                carbsG = product.carbsPer100g,
                fatG = product.fatPer100g,
                householdServingName = product.servingName,
                householdServingGrams = product.servingGrams,
            ),
        )
        val slotLabel = state.slotName.ifBlank { "log" }
        _uiState.update { it.copy(scanState = ScanState.ShowingSuccess("Saved & added to $slotLabel")) }
        delay(SUCCESS_OVERLAY_MS)
        _uiState.update { it.copy(scanState = ScanState.Logged) }
    }
}
```

- [ ] **Step 5: Add the `resolveGrams` private helper function**

Add this private function to `BarcodeScannerViewModel` (after `onLogModeChanged`):

```kotlin
private fun resolveGrams(productState: ScanState.ProductFound): Double? {
    return when (productState.logMode) {
        LogMode.GRAMS -> productState.amountInput.toDoubleOrNull()
        LogMode.SERVING -> {
            val servings = productState.amountInput.toDoubleOrNull()
            val servingGrams = productState.product.servingGrams
            if (servings != null && servingGrams != null) servings * servingGrams else null
        }
    }
}
```

- [ ] **Step 6: Run all ViewModel tests to confirm everything passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.scanner.BarcodeScannerViewModelTest"
```

Expected: all 13 tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModelTest.kt
git commit -m "feat: resolve actual grams from LogMode in confirm functions"
```

---

## Task 5: Add segmented toggle and dynamic label to ProductFoundSheet

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerScreen.kt`

- [ ] **Step 1: Add required imports to `BarcodeScannerScreen.kt`**

Add these imports (alongside existing imports):

```kotlin
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
```

- [ ] **Step 2: Add `onLogModeChanged` parameter to `ProductFoundSheet`**

Replace the `ProductFoundSheet` signature:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFoundSheet(
    state: ScanState.ProductFound,
    message: String?,
    onAmountChanged: (String) -> Unit,
    onLogModeChanged: (LogMode) -> Unit,
    onLogAndSave: () -> Unit,
    onLogOnly: () -> Unit,
    onCancel: () -> Unit,
)
```

- [ ] **Step 3: Add the segmented toggle and dynamic label inside `ProductFoundSheet`**

Replace the body of `ProductFoundSheet` (the `Column` content) with the following. The segmented row is placed between the `MacroChip` row and the text field; the text field label is dynamic:

```kotlin
val product = state.product
Column(
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    if (!product.hasCompleteData) {
        Text(
            "Warning: some nutritional data is missing — check values before logging.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MacroChip("Kcal", product.caloriesPer100g.toString())
        MacroChip("Protein", "${product.proteinPer100g}g")
        MacroChip("Carbs", "${product.carbsPer100g}g")
        MacroChip("Fat", "${product.fatPer100g}g")
    }
    Text("per 100g", fontSize = 11.sp, color = Color(0xFF6b7280))
    if (product.servingGrams != null) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.logMode == LogMode.GRAMS,
                onClick = { onLogModeChanged(LogMode.GRAMS) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("Grams") },
            )
            SegmentedButton(
                selected = state.logMode == LogMode.SERVING,
                onClick = { onLogModeChanged(LogMode.SERVING) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Serving") },
            )
        }
    }
    val fieldLabel = if (state.logMode == LogMode.SERVING && product.servingGrams != null) {
        val gramsInt = product.servingGrams.toInt()
        if (product.servingName != null) "Servings (1 serving = ${gramsInt}g · ${product.servingName})"
        else "Servings (1 serving = ${gramsInt}g)"
    } else {
        "Amount (grams)"
    }
    OutlinedTextField(
        value = state.amountInput,
        onValueChange = onAmountChanged,
        label = { Text(fieldLabel) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    MessageText(message, MessageKind.ERROR)
    Button(onClick = onLogAndSave, modifier = Modifier.fillMaxWidth()) {
        Text("Log & Save to Library")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onLogOnly, modifier = Modifier.weight(1f)) {
            Text("Log Only", fontSize = 12.sp)
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text("Cancel", fontSize = 12.sp)
        }
    }
}
```

- [ ] **Step 4: Wire `onLogModeChanged` at the call site in `BarcodeScannerScreen`**

Find the `ProductFoundSheet(...)` call inside the `is ScanState.ProductFound ->` branch and add the new parameter:

```kotlin
is ScanState.ProductFound -> {
    ModalBottomSheet(
        onDismissRequest = viewModel::resetScan,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ProductFoundSheet(
            state = scanState,
            message = state.message,
            onAmountChanged = viewModel::onAmountChanged,
            onLogModeChanged = viewModel::onLogModeChanged,
            onLogAndSave = viewModel::confirmLogAndSave,
            onLogOnly = viewModel::confirmLog,
            onCancel = viewModel::resetScan,
        )
    }
}
```

- [ ] **Step 5: Build to confirm no compile errors**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Run all tests one final time**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerScreen.kt
git commit -m "feat: add grams/serving segmented toggle to scan result sheet"
```
