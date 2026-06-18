# Workout Duration Wheel Picker — Design

**Date:** 2026-06-18
**Branch:** `feat/workout-tracking`
**Status:** Approved

## Goal

Replace the typed-minutes input in the post-workout duration editor with an
iOS-style scroll-wheel picker (hours + minutes).

## Current State

`DurationEditDialog` in
`app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryScreen.kt`
is an `AlertDialog` whose body is a numeric `BasicTextField` for whole minutes.
The pencil icon on the summary screen's duration tile opens it; **Set** calls
`viewModel.setDuration(minutes * 60)`. Duration is stored in seconds
(`SessionSummaryViewModel.setDuration(seconds: Int)`).

No scroll-wheel / picker component exists anywhere in the codebase, so this is
net-new UI.

## Decisions

| Question | Decision |
|---|---|
| Layout | Dual wheel: **hours + minutes** |
| Minutes granularity | **1-minute** steps (0–59) |
| Hours range | **0–12** |
| Container | **Keep the existing `AlertDialog`** (same trigger, same Set/Cancel) |

## Scope

- Swap the `BasicTextField` body of `DurationEditDialog` for the dual wheel.
- Keep the AlertDialog shell, the "Edit duration" title, the Set/Cancel
  buttons, the pencil-icon trigger, and `viewModel.setDuration(seconds)`.
- Remove the now-unused keyboard / digit-filtering logic.

## Components

All new composables are **private to `SessionSummaryScreen.kt`**, matching how
`DurationEditDialog` is already scoped there. Nothing reusable elsewhere yet, so
no shared component (YAGNI).

### `WheelPicker` (generic single column)

```
WheelPicker(
    count: Int,                 // number of items, values are 0..count-1
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    label: String,              // unit label shown beside the wheel ("h" / "min")
    modifier: Modifier = Modifier,
)
```

- `LazyColumn` with `rememberSnapFlingBehavior(lazyListState)` for snap-to-item.
- Fixed item height (~42 dp); visible window ~5 items.
- `contentPadding` of `2 × itemHeight` top and bottom so index 0 and the last
  index can rest dead-center.
- Center selection band: the tinted-glass strip (reuse accent `tintedSurface` /
  frosted border tokens) overlaid at the vertical center, `pointer-events`-free.
- Distance-from-center drives per-item alpha/weight: centered item = full
  `textPrimary` + bold; neighbors fade toward `textMuted`.
- The centered index = item nearest the viewport center. Report via
  `onSelectedChange` when the scroll settles
  (`derivedStateOf` on `lazyListState.isScrollInProgress` flipping to `false`).

### `DurationWheelPicker` (the dialog body)

```
DurationWheelPicker(
    currentSeconds: Int,
    onChange: (totalSeconds: Int) -> Unit,
)
```

- Two `WheelPicker`s in a `Row`: hours (`count = 13`, 0–12) and minutes
  (`count = 60`, 0–59), each with its unit label.
- Owns local hours/minutes index state, seeded from `currentSeconds`.
- On either wheel settling, recomputes `totalSeconds` and calls `onChange`.

## Data Flow

1. **Open** — seed from `currentSeconds`:
   - `hours = (currentSeconds / 3600).coerceIn(0, 12)`
   - `minutes = (currentSeconds % 3600) / 60`
2. **Scroll/settle** — each wheel updates its local index; combined value is
   `(hours * 60 + minutes) * 60` seconds.
3. **Set** — `onConfirm(totalSeconds)` → `viewModel.setDuration(totalSeconds)`.
4. **Cancel** — dialog dismisses, no change.

Editing snaps to whole minutes; any sub-minute remainder in the stored duration
is dropped on Set — identical to the old whole-minutes text field.

## Edge Cases

- Stored duration ≥ 12 h → hours wheel clamps to 12.
- `0 h 0 min` is permitted; Set stores `0`.
- No text entry, so the digit-filtering and `KeyboardOptions` code is deleted.

## Testing

- **Unit:** extract the conversion as pure helpers
  `durationToHm(seconds): Pair<Int, Int>` and `hmToSeconds(h, m): Int`.
  Test round-trips, the seconds-remainder drop, and the 12 h clamp.
- **Manual:** debug build; user verifies the scroll/snap feel and visuals
  in-app (per the project's verify-in-app workflow).

## Out of Scope

- Changing where/how duration is captured by the timer.
- Reusing the wheel elsewhere (no other caller today).
- Bottom-sheet presentation (explicitly kept as an AlertDialog).
