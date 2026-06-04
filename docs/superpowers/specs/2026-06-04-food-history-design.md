# Food History & Week Navigation — Design Spec
_Date: 2026-06-04_

## Overview

Allow users to view and fully edit their food log for any of the past 7 days (today + 6 previous days) from within the existing Food Log screen. Navigation is handled by a Samsung Health-style week bar chart strip added above the existing nutrition strip.

---

## UX Flow

1. User opens the **Food Log** screen (same entry point as today).
2. A **`WeekCalorieStrip`** sits between the screen header and the `NutritionStrip`.
   - Shows 7 bars: today (rightmost) and the 6 preceding days.
   - Each bar height represents that day's calorie total relative to the target zone.
   - Dotted lines mark the upper and lower calorie zone bounds.
   - Bar colours: violet = in zone, orange = over zone, muted violet = below zone, near-transparent = nothing logged.
   - The selected day has a pill/capsule highlight behind its bar (matching the Samsung Health pattern).
3. Tapping any bar selects that day. The `NutritionStrip`, meal slots, and header date all update immediately.
4. When the selected day is not today, a **"Today" pill** appears in the header top-right. Tapping it returns to today.
5. Adding food to a past day works identically to today — `+ Add` on any slot navigates to `FoodLibraryScreen` with the selected date passed through, so new entries are written to the correct date.
6. Editing and deleting entries on past days works exactly as on today (inline edit dialog, delete with confirm dialog).

---

## Architecture

### New: `FoodLogViewModel`

Replace `TodayViewModel` on the `Routes.Food` destination with a new, dedicated `FoodLogViewModel`.

**Why a new ViewModel instead of extending `TodayViewModel`:**
- `TodayViewModel` is shared with `BodyRecoveryScreen` and carries body metrics state (weight, waist, sleep, scores). Mixing multi-date food navigation into it would create tangled state.
- A dedicated ViewModel gives clean separation: `TodayViewModel` stays for today-only body metrics; `FoodLogViewModel` owns all food log interactions for any date.

**`FoodLogViewModel` responsibilities:**
- Holds `selectedDate: MutableStateFlow<LocalDate>` (initialised to today).
- Observes `logRepository.observeDay(selectedDate)` — reactive, re-collects when `selectedDate` changes.
- Observes `logRepository.observeWeekCalories(today.minusDays(6), today)` for the strip data.
- Exposes all food operations already in `TodayViewModel`: `addSlot`, `renameSlot`, `deleteSlot`, `reorderSlots`, `deleteMeal`, `updateMealMacros`, `toggleEditMode`.
- Adds `selectDate(date: LocalDate)` — updates `selectedDate` (clamped to `today.minusDays(6)..today`).

**`TodayViewModel`** — unchanged. `BodyRecoveryScreen` continues to use it.

---

### Data Layer

#### `LogRepository` — one new method

```kotlin
fun observeWeekCalories(start: LocalDate, end: LocalDate): Flow<Map<LocalDate, Int>>
```

Uses the already-existing `MealEntryDao.observeBetween(startDate, endDate)` query, groups entries by date, sums calories, and emits a `Map<LocalDate, Int>`.

No schema changes. No new DAOs. No migrations.

#### FoodLibrary date threading

`FoodLibraryScreen` currently always adds entries with today's date (the date comes from inside `FoodLibraryViewModel`). When navigating from a past day, the selected date must be forwarded so new entries are written to the right day.

- Add an optional `date` query parameter to the `FoodLibrary` route: `"food_library?slotId={slotId}&slotName={slotName}&editEntryId={editEntryId}&date={date}"`.
- `FoodLogViewModel` passes the selected date when launching the library navigation.
- `FoodLibraryViewModel` reads the `date` arg and uses it (falls back to today if absent, preserving backwards-compat for any other caller).

---

## New Composables

### `WeekCalorieStrip`

```
WeekCalorieStrip(
    weekData: List<DayCalorieSummary>,   // 7 entries, oldest → newest
    selectedDate: LocalDate,
    today: LocalDate,
    targetLow: Int,
    targetHigh: Int,
    onDaySelected: (LocalDate) -> Unit,
)
```

- Renders 7 animated bars in a `Row`.
- Dotted target zone lines drawn with `Canvas` / `drawBehind`.
- Selected bar has a rounded-rect pill background (animated with `animateColorAsState`).
- Bar heights animated with `animateFloatAsState`.
- Bar colour computed from calories vs. zone: violet / orange / muted / transparent.
- Tapping a bar calls `onDaySelected`.
- `targetLow` and `targetHigh` are sourced from `PlanPreferences.calorieZoneLowerBound` / `calorieZoneUpperBound`, same values already used by `NutritionStrip`.
- Lives in `ui/component/WeekCalorieStrip.kt`.

### `DayCalorieSummary` (data class, same file or `LogModels.kt`)

```kotlin
data class DayCalorieSummary(val date: LocalDate, val calories: Int)
```

---

## Updated Composables

### `FoodScreen`

- Accepts `FoodLogViewModel` instead of `TodayViewModel`.
- `FoodScreenHeader` gains a `showTodayPill: Boolean` and `onTodayClick: () -> Unit` param.
- `WeekCalorieStrip` inserted between header and `NutritionStrip`.
- No other structural changes — `NutritionStrip`, slot cards, and the add-slot dialog are all unchanged.

---

## Navigation Changes (`AppNavGraph.kt`)

| Change | Detail |
|--------|--------|
| `Routes.Food` uses `FoodLogViewModel` | swap `TodayViewModel` for `FoodLogViewModel` at this destination |
| `onAddToSlot` callback passes selected date | `"${Routes.FoodLibrary}?slotId=$slotId&slotName=$slotName&date=$selectedDate"` |
| `onEditEntryAmount` callback passes selected date | same pattern |
| `FoodLibrary` route gains optional `date` arg | `defaultValue = ""` (empty = today) |

---

## Out of Scope

- Copying a past day's log to today (not requested).
- Navigating further than 6 days back.
- Any changes to `BodyRecoveryScreen` or `TodayViewModel`.
- Swipe gestures between days (strip tap is the only selection mechanism).
