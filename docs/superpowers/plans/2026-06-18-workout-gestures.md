# Workout List Gestures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the set-row `X` with a swipe-to-reveal Remove action, and turn the decorative exercise drag handle into real long-press drag-to-reorder with haptics — on both the Active Session and Routine Builder screens.

**Architecture:** A new self-contained `SwipeToRevealRow` composable (stable `draggable` + `Animatable` offset, no AnchoredDraggable) wraps each set row. Reordering uses the already-present `sh.calvin.reorderable` library (`ReorderableItem` + `longPressDraggableHandle`) over each screen's exercise `LazyColumn`. A pure `moveByKey` helper handles list reordering and is unit-tested. Haptics go through a small `DragHaptics` wrapper over `View.performHapticFeedback`.

**Tech Stack:** Kotlin, Jetpack Compose (Foundation `draggable`/`Animatable`), `sh.calvin.reorderable` 2.4.3 (already in `libs.versions.toml` and `app/build.gradle.kts`), JUnit.

**Reference spec:** `docs/superpowers/specs/2026-06-18-workout-gestures-design.md`

**Pre-flight (read before starting):**
- `app/src/main/java/com/zack/recomptracker/ui/train/component/SetGrid.kt` — current set grids (SESSION/PLAN/READONLY) with inline `X`.
- `app/src/main/java/com/zack/recomptracker/ui/train/component/ExerciseCard.kt` — decorative `DragHandle` + ⋮ menu.
- `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt` — exercise `LazyColumn` (`items(sortedExercises, key = { it.id })`).
- `app/src/main/java/com/zack/recomptracker/ui/train/RoutineBuilderScreen.kt` — exercise `LazyColumn` (`itemsIndexed(state.exercises, key = { _, ex -> ex.exercise.id })`).
- `app/src/main/java/com/zack/recomptracker/ui/train/RoutineBuilderViewModel.kt` — `BuilderExercise`, `reorder`, `addExercises`, `loadWorkout`.
- `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionViewModel.kt` — `moveExerciseUp/Down` (calls `sessionRepository.reorderSessionExercises(...)`).

**Color/util facts (verified):**
- `LocalAppColors.current` exposes `frostedSurfaceFallback`, `cardSurface`, `textMuted`, `textPrimary`, `frostedBorder`.
- `ErrorRed` lives in `com.zack.recomptracker.ui.theme.ErrorRed`.
- `CornerSmall` lives in `com.zack.recomptracker.ui.theme.CornerSmall`.

---

## File Structure

| File | Responsibility | Action |
|---|---|---|
| `domain/workout/ReorderSupport.kt` | Pure `moveByKey` list reorder helper | Create |
| `src/test/.../domain/workout/ReorderSupportTest.kt` | Unit test for `moveByKey` | Create |
| `ui/train/component/DragHaptics.kt` | Haptic start/move/end wrapper | Create |
| `ui/train/component/SwipeToRevealRow.kt` | Swipe-to-reveal Remove wrapper | Create |
| `ui/train/component/SetGrid.kt` | Remove inline `X`; wrap rows in `SwipeToRevealRow` | Modify |
| `ui/train/component/ExerciseCard.kt` | Add `dragHandleModifier` + `isDragging` lift | Modify |
| `ui/train/RoutineBuilderViewModel.kt` | Add stable `id` to `BuilderExercise` | Modify |
| `ui/train/ActiveSessionViewModel.kt` | Add `reorderExercises(orderedIds)` | Modify |
| `ui/train/ActiveSessionScreen.kt` | Reorderable wiring + haptics | Modify |
| `ui/train/RoutineBuilderScreen.kt` | Reorderable wiring + haptics | Modify |

No dependency or schema changes (the `reorderable` lib is already wired).

---

## Task 1: Pure `moveByKey` reorder helper (TDD)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/workout/ReorderSupport.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/workout/ReorderSupportTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/domain/workout/ReorderSupportTest.kt`:

```kotlin
package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderSupportTest {

    private data class Item(val id: Long, val name: String)

    @Test
    fun `moves item from earlier to later position`() {
        val list = mutableListOf(Item(1, "a"), Item(2, "b"), Item(3, "c"))
        val moved = list.moveByKey(fromKey = 1L, toKey = 3L) { it.id }
        assertTrue(moved)
        assertEquals(listOf(2L, 3L, 1L), list.map { it.id })
    }

    @Test
    fun `moves item from later to earlier position`() {
        val list = mutableListOf(Item(1, "a"), Item(2, "b"), Item(3, "c"))
        val moved = list.moveByKey(fromKey = 3L, toKey = 1L) { it.id }
        assertTrue(moved)
        assertEquals(listOf(3L, 1L, 2L), list.map { it.id })
    }

    @Test
    fun `returns false and leaves list unchanged when a key is missing`() {
        val list = mutableListOf(Item(1, "a"), Item(2, "b"))
        val moved = list.moveByKey(fromKey = 1L, toKey = 99L) { it.id }
        assertFalse(moved)
        assertEquals(listOf(1L, 2L), list.map { it.id })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.ReorderSupportTest"`
Expected: FAIL — `moveByKey` unresolved reference (compile error).

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/zack/recomptracker/domain/workout/ReorderSupport.kt`:

```kotlin
package com.zack.recomptracker.domain.workout

/**
 * Moves the element whose key equals [fromKey] to the current index of the element
 * whose key equals [toKey], shifting the others. Pure, in-place, no Android deps.
 *
 * @return true if both keys were found and the move was applied; false otherwise
 *         (list left untouched).
 */
fun <T> MutableList<T>.moveByKey(fromKey: Any?, toKey: Any?, keyOf: (T) -> Any?): Boolean {
    val from = indexOfFirst { keyOf(it) == fromKey }
    val to = indexOfFirst { keyOf(it) == toKey }
    if (from == -1 || to == -1 || from == to) return false
    add(to, removeAt(from))
    return true
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.ReorderSupportTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/workout/ReorderSupport.kt \
        app/src/test/java/com/zack/recomptracker/domain/workout/ReorderSupportTest.kt
git commit -m "feat(workout): add moveByKey reorder helper with tests"
```

---

## Task 2: `DragHaptics` helper

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/component/DragHaptics.kt`

- [ ] **Step 1: Write the implementation**

Create the file. Uses only haptic constants available at minSdk 26 (`LONG_PRESS`, `CLOCK_TICK`, `CONTEXT_CLICK`):

```kotlin
package com.zack.recomptracker.ui.train.component

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Small wrapper that fires the three reorder-drag haptics through the host [View].
 * All constants used are available since API 23 (minSdk is 26).
 */
class DragHaptics(private val view: View) {
    /** Long-press engaged — drag begins. */
    fun start() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    /** Item crossed a neighbour during the drag. */
    fun move() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** Item dropped. */
    fun end() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

@Composable
fun rememberDragHaptics(): DragHaptics {
    val view = LocalView.current
    return remember(view) { DragHaptics(view) }
}
```

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/DragHaptics.kt
git commit -m "feat(workout): add DragHaptics helper for reorder feedback"
```

---

## Task 3: `SwipeToRevealRow` component

Swipe the row left to reveal a docked red Remove button; tap it to delete. The
Remove strip only occupies the area the row has vacated (width = drag distance), so a
transparent foreground never bleeds the red through. Tapping an open row closes it.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/component/SwipeToRevealRow.kt`

- [ ] **Step 1: Write the implementation**

```kotlin
package com.zack.recomptracker.ui.train.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val RevealWidth = 92.dp

/**
 * Wraps [content] with a trailing swipe-to-reveal Remove action.
 *
 * Swipe left → red Remove button is revealed; tap it (only when fully open) → [onRemove].
 * Tapping the row while open closes it. When [enabled] is false the row is static (used
 * for the last-set guard — an exercise must keep at least one set).
 */
@Composable
fun SwipeToRevealRow(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealPx = with(density) { RevealWidth.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(modifier.clipToBounds()) {
        val revealed = -offsetX.value
        if (revealed > 0.5f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(with(density) { revealed.toDp() })
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(ErrorRed)
                    .clickable(enabled = revealed >= revealPx * 0.95f) {
                        scope.launch { offsetX.animateTo(0f) }
                        onRemove()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.width(16.dp).fillMaxHeight(),
                    )
                    Text(
                        text = "Remove",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = {
                        val target = if (offsetX.value < -revealPx / 2f) -revealPx else 0f
                        scope.launch { offsetX.animateTo(target) }
                    },
                ),
        ) {
            content()
            if (-offsetX.value > 0.5f) {
                // Absorb taps while open → close instead of hitting inner clickables.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { scope.launch { offsetX.animateTo(0f) } },
                )
            }
        }
    }
}
```

Note: add the missing imports the compiler asks for — `androidx.compose.foundation.layout.matchParentSize` is a `BoxScope` member (no import), and `androidx.compose.ui.layout.offset`/`offset { }` is `androidx.compose.foundation.layout.offset`. Add `import androidx.compose.foundation.layout.offset` and `import androidx.compose.foundation.layout.matchParentSize` is not needed (BoxScope). If `matchParentSize` is unresolved, it is `BoxScope.matchParentSize()` — ensure the call is inside the inner `Box` content lambda (it is).

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any unresolved imports flagged (notably `import androidx.compose.foundation.layout.offset`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/SwipeToRevealRow.kt
git commit -m "feat(workout): add SwipeToRevealRow swipe-to-delete component"
```

---

## Task 4: Integrate `SwipeToRevealRow` into `SetGrid`; remove inline `X`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/component/SetGrid.kt`

- [ ] **Step 1: SESSION grid — wrap row, drop the X**

In `SessionSetGrid`, the per-row loop currently is:

```kotlin
        sets.forEach { row ->
            val isExpanded = row.id in expandedRir.value
            val rowBg = if (row.completed) accent.tintedSurface else Color.Transparent

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(rowBg)
                    .clickable {
                        expandedRir.value = if (isExpanded)
                            expandedRir.value - row.id
                        else
                            expandedRir.value + row.id
                    }
                    .padding(vertical = 4.dp),
            ) {
                Row(...) { /* badge, prev, KG, REPS, ✓, and the Remove IconButton */ }
                AnimatedVisibility(...) { /* RIR stepper */ }
            }

            Spacer(Modifier.height(4.dp))
        }
```

Change to wrap the `Column` in `SwipeToRevealRow` and **delete the trailing Remove `IconButton`** (the whole `IconButton(onClick = { onRemoveSet(row.id) }, ...) { Icon(Icons.Default.Close, ...) }` block inside the inner `Row`):

```kotlin
        sets.forEach { row ->
            val isExpanded = row.id in expandedRir.value
            val rowBg = if (row.completed) accent.tintedSurface else Color.Transparent

            SwipeToRevealRow(
                onRemove = { onRemoveSet(row.id) },
                enabled = sets.size > 1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerSmall))
                        .background(rowBg)
                        .clickable {
                            expandedRir.value = if (isExpanded)
                                expandedRir.value - row.id
                            else
                                expandedRir.value + row.id
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // badge, prev, KG cell, REPS cell, ✓ check  (UNCHANGED) ...
                        // DELETE the trailing Remove IconButton entirely.
                    }
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        // RIR stepper (UNCHANGED) ...
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
```

(`onRemoveSet` is `SessionSetGrid`'s existing `(Long) -> Unit` param — no signature change.)

- [ ] **Step 2: PLAN grid — wrap row, drop the X, fix header**

In `PlanSetGrid`, the per-row loop currently is a `Row { badge; KG cell; REPS cell; IconButton(Close) }`. Wrap it and delete the `IconButton`:

```kotlin
        sets.forEachIndexed { index, setRow ->
            SwipeToRevealRow(
                onRemove = { onRemoveSet(index) },
                enabled = sets.size > 1,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // badge, KG cell (kgRaw), REPS cell (repsRaw)  (UNCHANGED) ...
                    // DELETE the trailing Remove IconButton entirely.
                }
            }
        }
```

Then in the `PlanSetGrid` **column header** delete the trailing `Spacer(Modifier.width(32.dp))` (the old X column reservation) so KG/REPS headers stay aligned with the rows.

- [ ] **Step 3: Imports + add the SwipeToRevealRow import**

Add `import com.zack.recomptracker.ui.train.component.SwipeToRevealRow` is **not** needed (same package). Remove now-unused imports if the compiler flags them: `androidx.compose.material3.IconButton` and `androidx.compose.material.icons.filled.Close` are no longer referenced once both X buttons are gone — delete those two imports. Keep `Icons`, `Add`, `Check`.

- [ ] **Step 4: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/SetGrid.kt
git commit -m "feat(workout): swipe-to-delete set rows; remove inline X"
```

---

## Task 5: `ExerciseCard` — drag handle modifier + drag lift

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/component/ExerciseCard.kt`

- [ ] **Step 1: Add params**

Add to the `ExerciseCard` signature (after `modifier`, before `content`):

```kotlin
    dragHandleModifier: Modifier = Modifier,
    isDragging: Boolean = false,
```

- [ ] **Step 2: Apply the lift to the card**

Change the `FrostedCard(modifier = modifier, ...)` call to apply a scale + shadow when dragging:

```kotlin
    FrostedCard(
        modifier = modifier.graphicsLayer {
            val s = if (isDragging) 1.02f else 1f
            scaleX = s
            scaleY = s
            shadowElevation = if (isDragging) 16f else 0f
            shape = RoundedCornerShape(20.dp)
            clip = false
        },
        contentPadding = 12.dp,
    ) {
```

Add imports:
```kotlin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape  // already imported — keep
```

- [ ] **Step 3: Attach the handle modifier to the drag-handle icon**

Change the drag-handle `Icon` modifier from `Modifier.size(18.dp)` to:

```kotlin
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = appColors.textMuted.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp).then(dragHandleModifier),
            )
```

- [ ] **Step 4: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/ExerciseCard.kt
git commit -m "feat(workout): ExerciseCard drag handle modifier + drag lift"
```

---

## Task 6: Stable id on `BuilderExercise`

Reorderable items need stable unique keys. `BuilderExercise` currently keys on
`exercise.id`, which collides if the same exercise is added twice. Add a per-instance id.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/RoutineBuilderViewModel.kt`

- [ ] **Step 1: Add `id` to the data class**

```kotlin
/** One exercise line in the builder. [id] is a stable per-instance key for reorder. */
data class BuilderExercise(
    val id: Long,
    val exercise: Exercise,
    val sets: List<BuilderSet>,
    val note: String?,
)
```

- [ ] **Step 2: Add an id counter and assign on creation**

In the `RoutineBuilderViewModel` body, add:

```kotlin
    private var nextBuilderId = 0L
    private fun newBuilderId(): Long = nextBuilderId++
```

In `loadWorkout`, change the mapped `BuilderExercise(...)` to include `id = newBuilderId()`:

```kotlin
        val exercises = template.exercises.map { templateEx ->
            BuilderExercise(
                id = newBuilderId(),
                exercise = templateEx.exercise,
                sets = templateEx.plannedSets.map { ps ->
                    BuilderSet(targetReps = ps.targetReps, targetWeightKg = ps.targetWeightKg)
                }.ifEmpty { listOf(BuilderSet(null, null)) },
                note = templateEx.note,
            )
        }
```

In `addExercises`, change the mapped `BuilderExercise(...)`:

```kotlin
            val newExercises = resolved.map { exercise ->
                BuilderExercise(
                    id = newBuilderId(),
                    exercise = exercise,
                    sets = listOf(BuilderSet(null, null)),
                    note = null,
                )
            }
```

`addSet`, `removeSet`, `setTarget`, `removeExercise`, and `reorder` all use `.copy(...)` or index ops that preserve `id` — no changes needed there.

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (RoutineBuilderScreen still compiles — it keys on `ex.exercise.id`; that line is replaced in Task 8).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/RoutineBuilderViewModel.kt
git commit -m "feat(workout): stable id on BuilderExercise for reorder keys"
```

---

## Task 7: `ActiveSessionViewModel.reorderExercises`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionViewModel.kt`

- [ ] **Step 1: Add the method**

After `moveExerciseDown`, add:

```kotlin
    /** Persist a full exercise order (used by drag-to-reorder). */
    fun reorderExercises(orderedIds: List<Long>) {
        viewModelScope.launch {
            runCatching { sessionRepository.reorderSessionExercises(orderedIds) }
        }
    }
```

(`reorderSessionExercises` already exists — it's what `moveExerciseUp/Down` call.)

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionViewModel.kt
git commit -m "feat(workout): ActiveSessionViewModel.reorderExercises"
```

---

## Task 8: Active Session — reorderable exercise list

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt`

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.toMutableStateList
import com.zack.recomptracker.domain.workout.moveByKey
import com.zack.recomptracker.ui.train.component.rememberDragHaptics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
```

- [ ] **Step 2: Build reorder state above the `LazyColumn`**

Replace `val sortedExercises = s.exercises.sortedBy { it.sortOrder }` with a mutable display list + reorder state:

```kotlin
    val lazyListState = rememberLazyListState()
    val dragHaptics = rememberDragHaptics()
    val displayExercises = remember(s.exercises) {
        s.exercises.sortedBy { it.sortOrder }.toMutableStateList()
    }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (displayExercises.moveByKey(from.key, to.key) { it.id }) {
            dragHaptics.move()
        }
    }
```

- [ ] **Step 3: Wire the LazyColumn state and the exercise items**

Add `state = lazyListState` to the `LazyColumn(...)` call.

Replace the exercise `items(sortedExercises, key = { it.id }) { se -> ... }` block with a
`ReorderableItem` wrapper that reads from `displayExercises` and passes the drag handle +
`isDragging` into `ExerciseCard`. The `onMoveUp`/`onMoveDown`/`onRemove`/`SetGrid` content
inside stays exactly as today, except first/last checks read `displayExercises`:

```kotlin
        items(displayExercises, key = { it.id }) { se ->
            ReorderableItem(reorderState, key = se.id) { isDragging ->
                ExerciseCard(
                    exerciseName = se.exerciseName,
                    imageUrl = null,
                    subtitle = "",
                    onMoveUp = if (displayExercises.first().id != se.id) {
                        { viewModel.moveExerciseUp(se) }
                    } else null,
                    onMoveDown = if (displayExercises.last().id != se.id) {
                        { viewModel.moveExerciseDown(se) }
                    } else null,
                    onRemove = { viewModel.removeExercise(se) },
                    isDragging = isDragging,
                    dragHandleModifier = Modifier.longPressDraggableHandle(
                        onDragStarted = { dragHaptics.start() },
                        onDragStopped = {
                            dragHaptics.end()
                            viewModel.reorderExercises(displayExercises.map { it.id })
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 14.dp),
                ) {
                    // BODY UNCHANGED: build sessionRows from se.sets and call SetGrid(SESSION, ...)
                    // exactly as in the current file.
                }
            }
        }
```

Note: `longPressDraggableHandle` resolves against the `ReorderableCollectionItemScope`
receiver of the `ReorderableItem` content lambda — it must be constructed inside that lambda
(it is, above).

- [ ] **Step 4: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt
git commit -m "feat(workout): long-press drag reorder in Active Session"
```

---

## Task 9: Routine Builder — reorderable exercise list

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/RoutineBuilderScreen.kt`

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.zack.recomptracker.ui.train.component.rememberDragHaptics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
```

- [ ] **Step 2: Build reorder state above the `LazyColumn`**

After `val totalSets = ...` and before `Box(modifier = ...) { LazyColumn(...) }`, add:

```kotlin
    val lazyListState = rememberLazyListState()
    val dragHaptics = rememberDragHaptics()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = state.exercises.indexOfFirst { it.id == from.key }
        val toIdx = state.exercises.indexOfFirst { it.id == to.key }
        if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
            viewModel.reorder(fromIdx, toIdx)
            dragHaptics.move()
        }
    }
```

Add `state = lazyListState` to the `LazyColumn(...)` call.

- [ ] **Step 3: Convert the exercise items to keyed + reorderable**

Replace `itemsIndexed(state.exercises, key = { _, ex -> ex.exercise.id }) { exIndex, builderEx -> ... }`
with `items(state.exercises, key = { it.id })`, deriving `exIndex` by id, and wrapping in
`ReorderableItem`. The subtitle, `SetGrid(PLAN, ...)` body, and callbacks stay the same:

```kotlin
            items(state.exercises, key = { it.id }) { builderEx ->
                val exIndex = state.exercises.indexOfFirst { it.id == builderEx.id }
                val exercise = builderEx.exercise

                val subtitle = listOfNotNull(
                    exercise.primaryMuscles.firstOrNull()?.replaceFirstChar { it.uppercase() },
                    exercise.equipment?.replaceFirstChar { it.uppercase() },
                ).joinToString(" · ")

                ReorderableItem(reorderState, key = builderEx.id) { isDragging ->
                    ExerciseCard(
                        exerciseName = exercise.name,
                        imageUrl = exercise.images.firstOrNull(),
                        subtitle = subtitle,
                        onMoveUp = if (exIndex > 0) { { viewModel.reorder(exIndex, exIndex - 1) } } else null,
                        onMoveDown = if (exIndex < state.exercises.lastIndex) {
                            { viewModel.reorder(exIndex, exIndex + 1) }
                        } else null,
                        onRemove = { viewModel.removeExercise(exIndex) },
                        isDragging = isDragging,
                        dragHandleModifier = Modifier.longPressDraggableHandle(
                            onDragStarted = { dragHaptics.start() },
                            onDragStopped = { dragHaptics.end() },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        SetGrid(
                            mode = SetGridMode.PLAN,
                            sets = builderEx.sets.map { bs ->
                                SetRowData(targetReps = bs.targetReps, targetWeightKg = bs.targetWeightKg)
                            },
                            onAddSet = { viewModel.addSet(exIndex) },
                            onRemoveSet = { setIndex -> viewModel.removeSet(exIndex, setIndex) },
                            onSetChanged = { setIndex, reps, weightKg ->
                                viewModel.setTarget(exIndex, setIndex, reps, weightKg)
                            },
                        )
                    }
                }
            }
```

Remove the now-unused `import androidx.compose.foundation.lazy.itemsIndexed` if the compiler flags it.

- [ ] **Step 4: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/RoutineBuilderScreen.kt
git commit -m "feat(workout): long-press drag reorder in Routine Builder"
```

---

## Task 10: Full verification

- [ ] **Step 1: Compile + unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. `ReorderSupportTest` passes. (`InsightHarnessTest` self-skips without `.env.test` — ignore it; it is an unrelated live-cloud harness.)

- [ ] **Step 2: Manual emulator verification**

Build, install, drive to a live session (the existing `PushDay` session works):
- Swipe a set row left → red **Remove** is revealed → tap it → set is deleted.
- An exercise with only one set: swiping does nothing (guard).
- Press-and-hold the drag handle on an exercise → haptic + the card lifts → drag over a
  neighbour (tick) → release (haptic) → order persists after leaving and reopening the screen.
- Repeat both in the Routine Builder (create/edit a routine), then Save and reopen to
  confirm the saved order.

- [ ] **Step 3: Final commit (if any verification fixups were needed)**

```bash
git add -A
git commit -m "fix(workout): gesture verification fixups"
```

(Skip if nothing changed.)

---

## Self-Review Notes

- **Spec coverage:** swipe-to-reveal Remove (Tasks 3–4) ✓; last-set guard (`enabled = sets.size > 1`) ✓; both screens (Tasks 8–9) ✓; long-press drag + haptics start/move/end (Tasks 2, 8, 9) ✓; drag lift visual (Task 5) ✓; ⋮ Move up/down fallback kept (Tasks 8–9) ✓; READONLY untouched (no change to `ReadonlySetGrid`) ✓; no schema change ✓; reorderable already wired ✓.
- **Type consistency:** `moveByKey(fromKey, toKey){keyOf}` signature matches its one call site; `BuilderExercise.id: Long` keyed consistently in Tasks 6 & 9; `reorderExercises(orderedIds: List<Long>)` matches its call in Task 8; `dragHandleModifier`/`isDragging` params match call sites.
- **Known follow-up to watch at build time:** exact import path for `offset { }` in `SwipeToRevealRow` is `androidx.compose.foundation.layout.offset`; the reorderable `longPressDraggableHandle` is only resolvable inside a `ReorderableItem` content lambda.
