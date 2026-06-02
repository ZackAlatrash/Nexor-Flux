# Scanner: Serving Mode + Barcode Icon

**Date:** 2026-06-02

## Overview

Two improvements to the barcode scanning feature:

1. After scanning a product, allow the user to log by **grams** or **servings** (when serving data is available from OpenFoodFacts).
2. Replace the `QrCodeScanner` icon with `BarcodeScanner` in the Food Library screen.

---

## Feature 1: Grams / Serving Mode Toggle

### Data availability

`BarcodeProduct` already carries `servingName: String?` and `servingGrams: Double?` from the OpenFoodFacts API. The toggle is only shown when `servingGrams != null`. If serving data is absent, the sheet behaves exactly as today (grams only).

### State model (`BarcodeScannerViewModel.kt`)

Add an enum:

```kotlin
enum class LogMode { GRAMS, SERVING }
```

Extend `ScanState.ProductFound`:

```kotlin
data class ProductFound(
    val product: BarcodeProduct,
    val logMode: LogMode = LogMode.GRAMS,
    val amountInput: String = "100",   // was amountGrams
) : ScanState()
```

Add a new ViewModel function `onLogModeChanged(mode: LogMode)` that:
- If switching GRAMS → SERVING: converts `amountInput` as `servings = grams / servingGrams`, formatted to 2 decimal places (trailing zeros trimmed).
- If switching SERVING → GRAMS: converts `amountInput` as `grams = servings * servingGrams`, formatted as a rounded integer string.
- Falls back to "1" (serving) or "100" (grams) if the current input is not a valid number.

Update `onAmountChanged` to write into `amountInput` (rename only, no logic change).

### Macro calculation (`confirmLog` / `confirmLogAndSave`)

Resolve actual grams before scaling:

```
val actualGrams = when (productState.logMode) {
    LogMode.GRAMS   -> amountInput.toDoubleOrNull()
    LogMode.SERVING -> amountInput.toDoubleOrNull()?.let { it * product.servingGrams!! }
}
```

Validation: `actualGrams == null || actualGrams < 1.0` → show error "Enter a valid amount (min 1g)." (same message as today).

Everything downstream (scale calculation, `MealEntryInput`, `SavedFoodEntity`) is unchanged — it already works off `grams`.

### UI (`BarcodeScannerScreen.kt` — `ProductFoundSheet`)

When `product.servingGrams != null`, render a `SingleChoiceSegmentedButtonRow` with two segments above the text field:

- Segment 1: **"Grams"** (selected when `logMode == GRAMS`)
- Segment 2: **"Serving"** (selected when `logMode == SERVING`)

Text field label:
- GRAMS mode: `"Amount (grams)"` (unchanged)
- SERVING mode: `"Servings (1 serving = Xg)"` where X is `servingGrams.toInt()`. If `servingName` is non-null, append ` · servingName` — e.g. `"Servings (1 serving = 30g · 1 cup)"`

The macro chips continue to display per-100g values regardless of mode; no changes there.

---

## Feature 2: Replace QR Code Icon with Barcode Icon

**File:** `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- Remove import: `androidx.compose.material.icons.filled.QrCodeScanner`
- Add import: `androidx.compose.material.icons.filled.BarcodeScanner`
- Change line 110: `Icons.Default.QrCodeScanner` → `Icons.Default.BarcodeScanner`

`material-icons-extended` is already a dependency — no build changes needed.

---

## Files Changed

| File | Change |
|---|---|
| `ui/scanner/BarcodeScannerViewModel.kt` | Add `LogMode` enum; extend `ProductFound`; add `onLogModeChanged`; rename `amountGrams` → `amountInput`; update confirm functions |
| `ui/scanner/BarcodeScannerScreen.kt` | Add segmented toggle in `ProductFoundSheet`; update label; wire `onLogModeChanged` |
| `ui/foodlibrary/FoodLibraryScreen.kt` | Swap icon import and usage |

Test file `BarcodeScannerViewModelTest.kt` will need updating for the renamed field and new mode-switching logic.

---

## Out of Scope

- Converting existing grams-only food library entries to support serving mode.
- Persisting the last-used log mode across scans.
- Any changes to the OpenFoodFacts API layer.
