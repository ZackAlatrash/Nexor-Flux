# Body & Recovery Improvements — Design Spec

**Date:** 2026-05-31  
**Status:** Approved

---

## Overview

Five improvements to the Body & Recovery section of Recomp Tracker:

1. **Check-in History screen** — scrollable list of all past daily check-ins with missing-day indicators
2. **Backdating / editing past entries** — full-screen form to add or edit any past day's check-in
3. **Belly skinfold field (caliper)** — new `waistSkinfoldMm` field in the daily check-in form to record caliper pinch thickness in mm
4. **7-day trend card** — card at the top of the Body tab showing this-week delta for weight and waist
5. **"View History" button** — entry point from the Body tab to the History screen

---

## Architecture

**Approach: New dedicated ViewModels — no changes to TodayViewModel's responsibilities.**

`TodayViewModel` is left untouched for food/meal/slot/HealthConnect logic. Two new ViewModels are added. A minor addition (trend fields) is made to `TodayUiState`.

---

## Section 1 — Data Layer

### DailyLogEntity

Add one nullable field:

```kotlin
val waistSkinfoldMm: Double? = null
```

This is the raw caliper skinfold pinch thickness in mm at the belly/waist. It is distinct from `waistCm` (tape measure circumference).

### DailyMetricsInput

Add the same field to `DailyMetricsInput` in `LogModels.kt`:

```kotlin
val waistSkinfoldMm: Double? = null
```

`LogRepository.saveDailyMetrics()` passes it through to the entity unchanged.

### Database Migration

- Bump `RecompDatabase` version: **4 → 5**
- Add `MIGRATION_4_5`:

```kotlin
db.execSQL("ALTER TABLE daily_logs ADD COLUMN waistSkinfoldMm REAL")
```

### Backup Compatibility

`DailyLogEntity` is already `@Serializable` with all fields nullable or defaulted. The new field defaults to `null`, so existing backup files deserialise without changes.

---

## Section 2 — BodyHistoryViewModel & BodyHistoryScreen

### BodyHistoryItem (sealed class)

```kotlin
sealed class BodyHistoryItem {
    data class Logged(val date: LocalDate, val entity: DailyLogEntity) : BodyHistoryItem()
    data class Missing(val date: LocalDate) : BodyHistoryItem()
}
```

### BodyHistoryViewModel

- Observes `logRepository.observeDailyLogs()` (already exists in `DailyLogDao`)
- Generates items from the **earlier of** (90 days ago) and (the date of the earliest log entry) **to today**, sorted **newest first**
- Each day in that range is either `Logged` (entity found) or `Missing` (no entry)
- Exposes `uiState: StateFlow<List<BodyHistoryItem>>`

### BodyHistoryScreen

- `LazyColumn` of rows
- **Logged row:** date · weight · waist · caliper (if present) · energy score · sleep · trained indicator — "Edit" action in blue on the right
- **Missing row:** date in error color (`#f87171`) · "no entry" italic text · blue "+ Add" chip on the right
- Tapping either row calls `onEditDay(date: LocalDate)` callback
- `onBack` callback for back arrow

---

## Section 3 — BodyEditViewModel & BodyEditScreen

### Shared Form Composable

Extract the body check-in fields from `BodyRecoveryContent` into a reusable `BodyCheckInFormContent(state, actions)` composable. Fields:

- Weight (kg) + Waist (cm) — side by side
- Belly skinfold (mm) + Sleep (h) — side by side
- Steps
- Energy slider (1–10)
- Hunger slider (1–10)
- Soreness slider (1–10)
- Training day toggle
- Notes (multi-line)
- Save button (label includes the date when editing a past entry: "Save check-in for May 29")

`BodyRecoveryContent` is updated to use `BodyCheckInFormContent` internally.

### BodyEditViewModel

- Constructor param: `date: LocalDate`
- On init: loads `DailyLogEntity` for that date via `logRepository` (null = new entry)
- Manages the same form field state as `TodayViewModel` does for today
- `saveMetrics()` calls `logRepository.saveDailyMetrics(DailyMetricsInput(date = date, ...))`
- After save: emits a one-shot "saved" event so the screen can pop back to history

### BodyEditScreen

- Full-screen with a back arrow
- Header: date as title (e.g. "May 29"), subtitle "Past check-in" or "Past check-in · no entry yet"
- Renders `BodyCheckInFormContent`
- Pre-fills fields from the loaded entity (empty/default for missing days)

---

## Section 4 — Trend Card & Body Tab Updates

### Trend Computation

`TodayViewModel` gains two new read-only fields in `TodayUiState`:

```kotlin
val weightChange7d: Float? = null   // kg delta vs 7 days ago
val waistChange7d: Float? = null    // cm delta vs 7 days ago
```

In `TodayViewModel.init`, add one additional `logRepository.observeDailyLogs()` observation limited to the last 14 days. Compute the delta as: most recent logged value minus the value logged closest to 7 days prior. If fewer than 2 data points exist, both fields remain `null`. Uses the existing `TrendCalculator` where applicable, otherwise simple subtraction.

### Trend Card UI

Added to `BodyRecoveryContent` above the check-in card. Only rendered when at least one trend value is non-null.

Two side-by-side chips:
- Weight chip: value + "kg this week", green (`#34d399`) for negative (down), red (`#f87171`) for positive (up)
- Waist chip: value + "cm waist", same colour logic

### View History Button

Added below the save button in `BodyRecoveryContent`. Outlined button, "📋 View History" with "All entries ›" right-aligned. Calls `onViewHistory()` callback.

`BodyRecoveryScreen` is updated to accept and pass through `onViewHistory: () -> Unit`.

---

## Section 5 — Navigation

### New Routes

Added to `Routes` object in `AppNavGraph.kt`:

```kotlin
const val BodyHistory = "body_history"
const val BodyEdit = "body_edit/{date}"
fun bodyEdit(date: LocalDate) = "body_edit/$date"
```

### AppNavGraph Changes

```kotlin
composable(Routes.BodyHistory) {
    BodyHistoryScreen(
        viewModel = viewModel<BodyHistoryViewModel>(factory = factory),
        onEditDay = { date -> navController.navigate(Routes.bodyEdit(date)) },
        onBack = { navController.popBackStack() },
    )
}

composable(
    route = Routes.BodyEdit,
    arguments = listOf(navArgument("date") { type = NavType.StringType }),
) {
    BodyEditScreen(
        viewModel = viewModel<BodyEditViewModel>(factory = factory),
        onBack = { navController.popBackStack() },
    )
}
```

The existing Body tab composable is updated:

```kotlin
composable(TopLevelDestination.Body.route) {
    BodyRecoveryScreen(
        viewModel = viewModel<TodayViewModel>(factory = factory),
        onViewHistory = { navController.navigate(Routes.BodyHistory) },
    )
}
```

### BodyEditViewModel — Date Injection

`BodyEditViewModel` reads the `date` navArg from `SavedStateHandle["date"]` (a `String`, parsed to `LocalDate`). This is the standard Compose Navigation pattern — no custom factory needed. The ViewModel is created with `viewModel<BodyEditViewModel>(factory = factory)` exactly like all other screens.

---

## Files Changed / Created

| File | Change |
|------|--------|
| `DailyLogEntity.kt` | + `waistSkinfoldMm: Double?` |
| `LogModels.kt` | + `waistSkinfoldMm` in `DailyMetricsInput` |
| `LogRepository.kt` | pass `waistSkinfoldMm` through in `saveDailyMetrics` |
| `RecompDatabase.kt` | version 4→5, `MIGRATION_4_5` |
| `TodayUiState` / `TodayViewModel` | + `weightChange7d`, `waistChange7d` trend fields |
| `BodyRecoveryScreen.kt` | + trend card, + caliper field, + View History button, extract `BodyCheckInFormContent` |
| `AppNavGraph.kt` | + `BodyHistory` and `BodyEdit` routes, update Body tab composable |
| `BodyHistoryViewModel.kt` | **new** |
| `BodyHistoryScreen.kt` | **new** |
| `BodyEditViewModel.kt` | **new** |
| `BodyEditScreen.kt` | **new** |
| `AppContainer.kt` | register new ViewModels |
