# Workout Duration Wheel Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the typed-minutes input in the post-workout duration editor with a dual hours+minutes scroll-wheel picker, inside the existing AlertDialog.

**Architecture:** A pure conversion helper (`durationToHm` / `hmToSeconds`) handles seconds↔(hours,minutes) and is unit-tested. A generic `WheelPicker` composable (LazyColumn + snap fling) renders one snapping column; `DurationWheelPicker` composes two of them (hours 0–12, minutes 0–59) and becomes the body of the existing `DurationEditDialog`. The dialog shell, trigger, and `viewModel.setDuration(seconds)` are unchanged.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit4 unit tests.

---

## File Structure

- **Create** `app/src/main/java/com/zack/recomptracker/ui/train/DurationWheelMath.kt` — pure seconds↔(h,m) helpers + `MAX_DURATION_HOURS`. No Android imports.
- **Create** `app/src/test/java/com/zack/recomptracker/ui/train/DurationWheelMathTest.kt` — unit tests for the helpers.
- **Modify** `app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryScreen.kt` — add `WheelPicker` + `DurationWheelPicker` private composables, rewrite `DurationEditDialog` body, update the dialog call site.

---

## Task 1: Duration conversion helpers (TDD)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/DurationWheelMath.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/train/DurationWheelMathTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/train/DurationWheelMathTest.kt`:

```kotlin
package com.zack.recomptracker.ui.train

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationWheelMathTest {

    @Test
    fun `durationToHm splits whole hours and minutes`() {
        assertEquals(1 to 15, durationToHm(75 * 60)) // 4500s
    }

    @Test
    fun `durationToHm drops sub-minute remainder`() {
        assertEquals(1 to 15, durationToHm(75 * 60 + 30)) // 4530s
    }

    @Test
    fun `durationToHm of zero is zero`() {
        assertEquals(0 to 0, durationToHm(0))
    }

    @Test
    fun `durationToHm clamps hours to twelve`() {
        assertEquals(12 to 0, durationToHm(13 * 3600))
    }

    @Test
    fun `durationToHm clamps negative to zero`() {
        assertEquals(0 to 0, durationToHm(-100))
    }

    @Test
    fun `hmToSeconds combines hours and minutes`() {
        assertEquals(4500, hmToSeconds(1, 15))
    }

    @Test
    fun `hmToSeconds clamps out-of-range inputs`() {
        assertEquals(MAX_DURATION_HOURS * 3600, hmToSeconds(20, 0))
        assertEquals(59 * 60, hmToSeconds(0, 90))
    }

    @Test
    fun `round trips through hm and back`() {
        for (seconds in listOf(0, 60, 4500, 12 * 3600)) {
            val (h, m) = durationToHm(seconds)
            assertEquals(seconds, hmToSeconds(h, m))
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.DurationWheelMathTest"`
Expected: FAIL — compilation error, unresolved references `durationToHm`, `hmToSeconds`, `MAX_DURATION_HOURS`.

- [ ] **Step 3: Write the minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/ui/train/DurationWheelMath.kt`:

```kotlin
package com.zack.recomptracker.ui.train

/** Maximum hours selectable on the duration wheel. */
const val MAX_DURATION_HOURS = 12

/**
 * Splits a duration in seconds into whole (hours, minutes), dropping any
 * sub-minute remainder and clamping hours to [MAX_DURATION_HOURS].
 */
fun durationToHm(seconds: Int): Pair<Int, Int> {
    val safe = seconds.coerceAtLeast(0)
    val hours = (safe / 3600).coerceIn(0, MAX_DURATION_HOURS)
    val minutes = (safe % 3600) / 60
    return hours to minutes
}

/**
 * Combines wheel (hours, minutes) into a duration in seconds, clamping each
 * field to its valid range (hours 0..[MAX_DURATION_HOURS], minutes 0..59).
 */
fun hmToSeconds(hours: Int, minutes: Int): Int =
    (hours.coerceIn(0, MAX_DURATION_HOURS) * 60 + minutes.coerceIn(0, 59)) * 60
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.DurationWheelMathTest"`
Expected: PASS — all 8 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/DurationWheelMath.kt \
        app/src/test/java/com/zack/recomptracker/ui/train/DurationWheelMathTest.kt
git commit -m "feat(workout): duration seconds<->h:m conversion helpers"
```

---

## Task 2: Wheel picker composables + dialog wiring

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryScreen.kt`

This task is gesture-driven UI; verification is a successful compile/build plus in-app manual check (no unit test).

- [ ] **Step 1: Add the new imports**

In `SessionSummaryScreen.kt`, add these imports alongside the existing import block (keep all current imports — `BasicTextField`, `onFocusChanged`, `SolidColor`, `TextStyle` are still used by `SummaryNoteField`):

```kotlin
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs
```

- [ ] **Step 2: Add wheel constants and the `WheelPicker` composable**

Add near the bottom of the file, just above the `// ── Formatters ──` section:

```kotlin
// ── Duration wheel picker ─────────────────────────────────────────────────────

private val WheelItemHeight = 40.dp
private const val WheelVisibleCount = 5

@Composable
private fun WheelPicker(
    count: Int,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (count - 1).coerceAtLeast(0)),
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - center) }
                ?.index ?: selectedIndex
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (!scrolling) onSelectedChange(centeredIndex) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = modifier
                .width(64.dp)
                .height(WheelItemHeight * WheelVisibleCount),
            contentAlignment = Alignment.Center,
        ) {
            // Center selection band
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight)
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(accent.tintedSurface)
                    .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerSmall)),
            )
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    vertical = WheelItemHeight * ((WheelVisibleCount - 1) / 2),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(count) { index ->
                    val distance = abs(index - centeredIndex)
                    val alpha = when (distance) {
                        0 -> 1f
                        1 -> 0.55f
                        2 -> 0.3f
                        else -> 0.15f
                    }
                    Box(
                        modifier = Modifier
                            .height(WheelItemHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = index.toString(),
                            fontSize = 20.sp,
                            fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium,
                            color = appColors.textPrimary.copy(alpha = alpha),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = appColors.textMuted,
        )
    }
}
```

Note: `items(count)` is a member of `LazyListScope` — no extra import needed (only `itemsIndexed` / `items(List)` require imports, and `itemsIndexed` is already imported).

- [ ] **Step 3: Add the `DurationWheelPicker` composable**

Add directly below `WheelPicker`:

```kotlin
@Composable
private fun DurationWheelPicker(
    hours: Int,
    minutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelPicker(
            count = MAX_DURATION_HOURS + 1,
            selectedIndex = hours,
            onSelectedChange = onHoursChange,
            label = "h",
        )
        Spacer(Modifier.width(20.dp))
        WheelPicker(
            count = 60,
            selectedIndex = minutes,
            onSelectedChange = onMinutesChange,
            label = "min",
        )
    }
}
```

- [ ] **Step 4: Rewrite `DurationEditDialog` to use the wheel**

Replace the entire existing `DurationEditDialog` composable (currently at `SessionSummaryScreen.kt:411-482`, the `@Composable private fun DurationEditDialog(...) { ... }` block) with:

```kotlin
@Composable
private fun DurationEditDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit, // total seconds
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    val (initialHours, initialMinutes) = durationToHm(currentSeconds)
    var hours by remember { mutableStateOf(initialHours) }
    var minutes by remember { mutableStateOf(initialMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit duration",
                color = appColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            DurationWheelPicker(
                hours = hours,
                minutes = minutes,
                onHoursChange = { hours = it },
                onMinutesChange = { minutes = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hmToSeconds(hours, minutes)) }) {
                Text(text = "Set", color = accent.inkLight, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = appColors.textMuted)
            }
        },
    )
}
```

- [ ] **Step 5: Update the dialog call site**

At the dialog invocation (currently `SessionSummaryScreen.kt:397-406`), change `onConfirm` to pass total seconds straight through. Replace:

```kotlin
    if (showDurationDialog) {
        DurationEditDialog(
            currentSeconds = state.durationSeconds,
            onDismiss = { showDurationDialog = false },
            onConfirm = { minutes ->
                viewModel.setDuration(minutes * 60)
                showDurationDialog = false
            },
        )
    }
```

with:

```kotlin
    if (showDurationDialog) {
        DurationEditDialog(
            currentSeconds = state.durationSeconds,
            onDismiss = { showDurationDialog = false },
            onConfirm = { totalSeconds ->
                viewModel.setDuration(totalSeconds)
                showDurationDialog = false
            },
        )
    }
```

- [ ] **Step 6: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, no unresolved references.

- [ ] **Step 7: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryScreen.kt
git commit -m "feat(workout): scroll-wheel duration picker in session summary"
```

- [ ] **Step 9: Manual verification (hand off to user)**

Install the debug build and open a finished workout's summary. Tap the pencil on the DURATION tile. Confirm:
- Two wheels appear (hours 0–12, minutes 0–59) seeded to the current duration.
- Scrolling snaps to whole values; the centered value is bold/bright with neighbors faded.
- **Set** updates the DURATION tile to the picked `h:mm`; **Cancel** leaves it unchanged.

---

## Self-Review Notes

- **Spec coverage:** dual wheel (Task 2 Step 3), 1-min steps / 0–59 (`count = 60`), hours 0–12 (`MAX_DURATION_HOURS + 1`), AlertDialog kept (Step 4), seconds↔(h,m) + clamp + remainder drop (Task 1), 12 h clamp & 0:00 allowed (Task 1 tests), keyboard logic removed (old text-field body replaced). All covered.
- **Type consistency:** `durationToHm` returns `Pair<Int,Int>`; `hmToSeconds(Int,Int): Int`; `onConfirm: (Int) -> Unit` (total seconds) matches `viewModel.setDuration(seconds: Int)`. `WheelPicker`/`DurationWheelPicker` signatures match their call sites.
- **No placeholders:** every code step shows full content.
