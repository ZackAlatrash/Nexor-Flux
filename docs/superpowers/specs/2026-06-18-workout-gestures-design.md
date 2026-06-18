# Workout List Gestures — Design Spec

**Date:** 2026-06-18
**Branch:** `feat/workout-tracking`
**Status:** Approved (design)

## Goal

Two gesture-driven UX improvements to the workout exercise/set lists:

1. **Swipe a set to delete it.** Replace the inline `X` button on set rows with a
   swipe-to-reveal **Remove** action: swipe the row left → a red Remove button is
   revealed → tap it to delete the set. Deletion stays a deliberate two-step action
   (swipe, then tap) — no full-swipe auto-delete.
2. **Long-press drag to reorder exercises.** The drag-handle icon is currently
   decorative (reordering happens via the ⋮ menu). Make it a real handle: press and
   hold it → haptic feedback → drag the card to a new position → release to persist.

Both changes apply to **both** screens that share these components:
- **Active Session** (live workout) — `SetGrid` SESSION mode, `ExerciseCard` list.
- **Routine Builder** (create/edit routine) — `SetGrid` PLAN mode, `ExerciseCard` list.

The **Session Detail** screen (READONLY) is unchanged — no remove, no reorder.

## Non-Goals

- No DB/schema changes. Both reorders use existing persistence
  (`reorderSessionExercises` for the session; in-memory `reorder` for the builder).
- No confirm dialog for set removal — the swipe-then-tap is itself the confirmation.
- The ⋮ menu **Move up / Move down** items stay as an accessible fallback; drag
  becomes the primary reorder path.

## Part 1 — Swipe-to-Reveal Set Removal

### New component: `SwipeToRevealRow`

A reusable composable in `ui/train/component/SwipeToRevealRow.kt`:

- Wraps arbitrary row `content` and exposes a single trailing **Remove** action.
- Built with Compose Foundation `AnchoredDraggable` with two anchors:
  - `Closed` — offset 0 (default).
  - `Revealed` — offset `-revealWidth` (reveal button fully shown).
- Background layer: a red **Remove** button (icon + label, `ErrorRed`) docked to the
  end, sized `revealWidth`. Tapping it invokes `onRemove`.
- Foreground layer: the `content`, translated by the current drag offset.
- Closing: swiping back to `Closed`, or tapping the foreground row, settles to closed.
- `enabled: Boolean` param — when `false` the row is not draggable (used for the
  last-set guard, below). When disabled, the row renders normally with no swipe.

Interface:
```
@Composable
fun SwipeToRevealRow(
    onRemove: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

### Integration into `SetGrid`

- **Remove the inline `X` (`Close`) IconButton** from both `SessionSetGrid` and
  `PlanSetGrid` rows.
- Wrap each set row in `SwipeToRevealRow`:
  - SESSION: `onRemove = { onSessionRemoveSet(row.id) }`.
  - PLAN: `onRemove = { onRemoveSet(index) }`.
- **Last-set guard:** `enabled = sets.size > 1`. This preserves today's behavior
  where the dimmed `X` prevented removing the only set (an exercise must keep ≥1 set).
- The SESSION row's existing tap-to-reveal-RIR stepper still works. Horizontal swipe
  (AnchoredDraggable) and the vertical-agnostic tap (`clickable`) do not collide.
  When a row is open, a tap closes it instead of toggling RIR.

## Part 2 — Long-Press Drag Reorder

### Dependency

Add `sh.calvin.reorderable` (reorderable LazyColumn for Compose) via the version
catalog. Compatible with the project's Compose BOM (`2026.05.01`, Foundation ≥1.8).
Exact version pinned in the implementation plan and verified at build time.

### `ExerciseCard` changes

Add two parameters so each screen wires its own drag handle and lift visual:
- `dragHandleModifier: Modifier = Modifier` — applied to the drag-handle `Icon`.
  The screen passes the reorderable `Modifier.longPressDraggableHandle(...)` here.
- `isDragging: Boolean = false` — when true, apply a subtle lift (scale ≈1.03 +
  shadow/elevation) so the dragged card reads as picked up.

The handle `Icon` keeps its current position and appearance; only its modifier and
the card's elevation change. The ⋮ menu (incl. Move up/down + Remove) is untouched.

### Screen wiring (both `ActiveSessionScreen` and `RoutineBuilderScreen`)

- `rememberReorderableLazyListState` over the exercise `LazyColumn`.
- Each exercise item rendered inside `ReorderableItem`, which provides `isDragging`
  and the `longPressDraggableHandle` modifier (passed to `ExerciseCard`).
- Display list during drag: a local `mutableStateListOf` mirrored from the source
  list. `onMove(from, to)` reorders the local list for instant visual feedback;
  drag stop persists the final order. This decouples drag smoothness from
  persistence (relevant for the DB-backed session, where we persist once on drop
  rather than on every move).
  - Active Session: on drop → `viewModel.reorderExercises(orderedIds)`
    (wraps `sessionRepository.reorderSessionExercises`).
  - Routine Builder: on drop → `viewModel.reorder` applied to reach final order
    (in-memory state).

### Haptics

Fire haptic feedback via `View.performHapticFeedback` (or `LocalHapticFeedback`):
- On drag start (long-press engaged) — `LONG_PRESS` / `GESTURE_START`.
- On each reorder move (crossing a neighbor) — light tick (`SEGMENT_TICK` /
  `CLOCK_TICK`).
- On drop — `GESTURE_END` (or light confirm).

The reorderable library exposes `onDragStarted` / `onDragStopped` callbacks on the
handle modifier and an `onMove` callback on the list state — haptics hook into these.

## Files Touched

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add `reorderable` version + library alias |
| `app/build.gradle.kts` | Add `implementation(libs.reorderable)` |
| `ui/train/component/SwipeToRevealRow.kt` | **New** swipe-to-reveal component |
| `ui/train/component/SetGrid.kt` | Remove inline `X`; wrap rows in `SwipeToRevealRow` |
| `ui/train/component/ExerciseCard.kt` | Add `dragHandleModifier` + `isDragging` |
| `ui/train/ActiveSessionScreen.kt` | Reorderable LazyColumn + haptics; add `reorderExercises` call |
| `ui/train/ActiveSessionViewModel.kt` | Add `reorderExercises(orderedIds)` if not already exposed |
| `ui/train/RoutineBuilderScreen.kt` | Reorderable LazyColumn + haptics |

## Testing

- Domain/repository unit tests unchanged (no logic moved into domain). Existing
  reorder repo methods already covered.
- Manual verification on emulator:
  - Swipe a set → Remove revealed → tap → set deleted; last set cannot be swiped.
  - Long-press handle → haptic + lift → drag → order persists after drop and
    survives a screen reopen.
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` stays green.
