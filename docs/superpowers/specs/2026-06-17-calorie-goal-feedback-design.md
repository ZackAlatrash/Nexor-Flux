# Calorie Goal Feedback Design

**Date:** 2026-06-17
**Branch:** feat/calorie-goal-feedback

## Overview

Add visual feedback to the food logging screen that rewards users for hitting their calorie zone and surfaces a clear "missed" signal for past days that ended below the zone. Two UI areas are affected: the week-strip bar chart and the nutrition card.

---

## Week Strip Bars (`WeekCalorieStrip.kt`)

**One change only:** below-zone bars on *past days* get a semantic "missed" color instead of the current muted `accent.accentLighter.copy(alpha = 0.75f)`.

| Bar state | Color rule |
|---|---|
| In zone (past) | `accent.accentLight` — unchanged |
| Below zone (past) | Dark mode: `Color(0xFF7F1D1D)` (dark crimson). Light mode: `Color(0xFF9CA3AF).copy(alpha = 0.55f)` (desaturated grey, avoids Rose accent clash) |
| Over zone (past) | `Color(0xFFF97316)` (orange) — unchanged |
| Today | `accent.accent` — unchanged |
| Future / empty | existing dim — unchanged |

No icons above bars. The distinction between "hit" and "missed" is color only.

**Implementation:** `WeekBarItem` receives `today: LocalDate` (passed through from `WeekCalorieStrip`). The missed color is only applied when `summary.date < today && summary.calories < targetLow && summary.calories > 0`. Zero-calorie past days keep the existing empty styling (no food logged that day is a different signal).

---

## Nutrition Card (`NutritionStrip` in `FoodScreen.kt`)

### States

Four distinct visual states based on day type and calorie position:

| State | Condition | Card background | Card border | Progress bar |
|---|---|---|---|---|
| `BelowZone` | Today or future, calories < zone low | Existing neutral card | Existing | Accent gradient |
| `GoalHit` | Any day, calories in zone | Dark mode: `#0A1A10`. Light mode: `#16A34A` at 12% alpha over frosted white | Dark: `#166534`. Light: `#16A34A` at 50% alpha | Green gradient (`#16a34a` → `#4ade80`) |
| `Over` | Any day, calories > zone high | Existing neutral card | Existing | Accent gradient |
| `Missed` | Past day only, calories < zone low (and > 0) | Dark mode: `#1A0E0E`. Light mode: `#DC2626` at 12% alpha | Dark: `#4A1515`. Light: `#DC2626` at 50% alpha | Red gradient (`#991b1b` → `#dc2626`) |

Zero-calorie past days (no food logged) are treated as `BelowZone` visually — showing as neutral, not "missed", since the user may have forgotten to log rather than actually skipped eating.

### Badge

The existing `VioletBadge` call is replaced with a status-aware call:

| State | Badge |
|---|---|
| `BelowZone` | `VioletBadge("Below")` — existing |
| `GoalHit` | `VioletBadge(PillStatus.GOOD, "Goal hit!")` |
| `Over` | `VioletBadge("Over")` — existing |
| `Missed` | `VioletBadge(PillStatus.OFF_TRACK, "Missed")` |

### Sub-text (next to calorie number)

The `"· in zone"` variant is removed. States:

| State | Sub-text |
|---|---|
| `BelowZone` | `" kcal · X to zone"` |
| `GoalHit` | `" kcal"` (badge carries the message) |
| `Over` | `" kcal · X over"` |
| `Missed` | `" kcal · X below zone"` |

---

## Celebration Animation (today → GoalHit transition only)

Fires **once** when the user's eaten calories cross from below into the zone on today's screen. Does not play when viewing a past in-zone day — the green state renders directly with no animation.

**Sequence (all steps overlap, total ~1 second):**
1. Card background fades from neutral → `GoalHit` green
2. Card briefly scales up (pop to ~1.026×) then settles back to 1.0
3. Green border glow pulses then settles to steady green border
4. Progress bar width grows to new position + color shifts accent → green gradient
5. Badge cross-fades: current badge → "Goal hit!" green pill

**Trigger mechanism:** Tracked with local `remember` state inside `NutritionStrip`. A `LaunchedEffect(isInZone)` watches the boolean. When `isInZone` becomes `true`, `state.isToday` is true, and the previous value was `false`, a `celebrateTriggered` flag is set. Animations are driven by `Animatable` instances for color and scale. Navigating away and back resets the `remember` state, so the animation replays — this is acceptable.

**No animation for:**
- "Missed" state — just renders on past-day tap
- Crossing back out of the zone (eating more and going over) — card transitions back to neutral silently

---

## Files Changed

| File | Change |
|---|---|
| `ui/component/WeekCalorieStrip.kt` | Add `today` param to `WeekBarItem`; new missed-bar color logic |
| `ui/today/FoodScreen.kt` (`NutritionStrip`) | `CalorieDayStatus` enum; status-aware card colors; animation; updated badge + sub-text |

No ViewModel changes. No new composables (reuses `VioletBadge(PillStatus)` which already exists). No data model changes.

---

## Theme Compatibility

| Theme | Notes |
|---|---|
| Dark + any accent | Crimson missed bars contrast clearly against all accent bar colors |
| Dark + Emerald/Lime | In-zone bars keep accent color (green) — no separate "hit" green avoids invisible bars |
| Light + any accent | Grey missed bars avoid red-on-rose clash; card states use transparent tints |
| Light + Rose | Grey missed bars chosen specifically to avoid dark-red-on-rose confusion |
