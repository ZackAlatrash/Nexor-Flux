# Workout Readability + Summary Muscle Group Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the active-workout set grid readable via a 3-tier text scheme (new `textSecondary` token), and show each exercise's muscle group on the end-of-workout summary (leading muscle icon + group-name subtitle prefix).

**Architecture:** Add a `textSecondary` `AppColors` token and reassign active-workout text into primary/secondary/muted tiers (scoped to `SessionSetGrid` + the session-notes label). For the summary, resolve `primaryMuscles` per exercise in `SessionSummaryViewModel` (via the exercise library, like the active session), add a pure `muscleGroupLabel` helper, and render a leading `MuscleGroupIcon` + an `AnnotatedString` subtitle on each recap row.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit4.

---

## File Structure

- **Modify** `ui/theme/AppColors.kt` — add `textSecondary` (dark + light).
- **Modify** `ui/train/component/SetGrid.kt` — reassign `SessionSetGrid` text tiers.
- **Modify** `ui/train/ActiveSessionScreen.kt` — "SESSION NOTES" label → secondary.
- **Modify** `ui/train/component/MuscleGroup.kt` — add `muscleGroupLabel`.
- **Modify/Test** `ui/train/component/MuscleGroupTest.kt` (or new test file) — `muscleGroupLabel` tests.
- **Modify** `ui/train/SessionSummaryViewModel.kt` — `exerciseLibraryRepository`, `ExerciseRecap.primaryMuscles`.
- **Modify** `core/AppContainer.kt` — wire the library into the summary VM.
- **Modify** `ui/train/SessionSummaryScreen.kt` — recap row icon + annotated subtitle.

---

## Task 1: Add the `textSecondary` token

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/theme/AppColors.kt`

- [ ] **Step 1: Add the field to the data class**

In the `data class AppColors(...)` constructor, add the field right after `val textMuted: Color,`:

```kotlin
    val textMuted: Color,
    val textSecondary: Color,
```

- [ ] **Step 2: Add the dark value**

In the `Dark = AppColors(` block, add right after the `textMuted = Color(0x47FFFFFF),` line:

```kotlin
            textMuted              = Color(0x47FFFFFF),
            textSecondary          = Color(0xB3FFFFFF),
```

- [ ] **Step 3: Add the light value**

In the `Light = AppColors(` block, add right after the `textMuted = Color(0x99141019),` line:

```kotlin
            textMuted              = Color(0x99141019),
            textSecondary          = Color(0xB3141019),
```

- [ ] **Step 4: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If any other `AppColors(...)` construction exists it would error for a missing arg — search `grep -rn "AppColors(" app/src/main/java | grep -v "AppColors.kt"`; there should be none besides the two companion instances.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/theme/AppColors.kt
git commit -m "feat(theme): add textSecondary readable-grey token"
```

---

## Task 2: Reassign active-workout text tiers

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/component/SetGrid.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt`

All edits are inside `SessionSetGrid` (the SESSION grid) — do NOT change `ReadonlySetGrid` or `PlanSetGrid`, which have identical-looking lines.

- [ ] **Step 1: Column labels → textSecondary**

In `SetGrid.kt`, the four header `Text(...)` blocks for `"SET"`, `"PREV"`, `"KG"`, `"REPS"` (inside `SessionSetGrid`) each have `color = appColors.textMuted,`. Change each of those four to `color = appColors.textSecondary,`. They are uniquely identified by their `text = "SET"` / `"PREV"` / `"KG"` / `"REPS"` lines. For example the SET header becomes:

```kotlin
            Text(
                text = "SET",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textSecondary,
                letterSpacing = 0.4.sp,
                modifier = Modifier.width(28.dp),
            )
```

Apply the same `textMuted → textSecondary` change to the `"PREV"`, `"KG"`, and `"REPS"` header `Text`s.

- [ ] **Step 2: Set number → textSecondary (incomplete rows)**

Find the set-number `Text` (its `text = "${row.setNumber}"`). Change its color line:

```kotlin
                                color = if (row.completed) accent.inkLighter else appColors.textMuted,
```

to:

```kotlin
                                color = if (row.completed) accent.inkLighter else appColors.textSecondary,
```

- [ ] **Step 3: PREV value → textPrimary (white)**

Find the PREV-hint `Text` (its `text = row.prev ?: "–"`). Change:

```kotlin
                                text = row.prev ?: "–",
                                fontSize = 12.sp,
                                color = appColors.textMuted,
```

to:

```kotlin
                                text = row.prev ?: "–",
                                fontSize = 12.sp,
                                color = appColors.textPrimary,
```

- [ ] **Step 4: RIR label → textSecondary**

Find the `Text` with `text = "RIR"` (in the RIR stepper row). Change its `color = appColors.textMuted,` to `color = appColors.textSecondary,`:

```kotlin
                            Text(
                                text = "RIR",
                                fontSize = 11.sp,
                                color = appColors.textSecondary,
                                fontWeight = FontWeight.Medium,
                            )
```

(Leave the KG/REPS empty-cell `placeholder` "–" in `SetInputCell` at `textMuted` — faint is correct for empty state.)

- [ ] **Step 5: "SESSION NOTES" label → textSecondary**

In `ActiveSessionScreen.kt`, find the `Text(text = "SESSION NOTES", ...)` block and change its `color = appColors.textMuted,` to `color = appColors.textSecondary,`. (Leave the note-field placeholder faint.)

- [ ] **Step 6: Type-check + build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/SetGrid.kt \
        app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt
git commit -m "feat(workout): readable 3-tier text in active session set grid"
```

---

## Task 3: `muscleGroupLabel` helper (TDD)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroup.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/train/component/MuscleGroupLabelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/train/component/MuscleGroupLabelTest.kt`:

```kotlin
package com.zack.recomptracker.ui.train.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleGroupLabelTest {

    @Test
    fun `direct group names`() {
        assertEquals("Chest", muscleGroupLabel(listOf("chest")))
        assertEquals("Shoulders", muscleGroupLabel(listOf("shoulders")))
        assertEquals("Biceps", muscleGroupLabel(listOf("biceps")))
        assertEquals("Triceps", muscleGroupLabel(listOf("triceps")))
        assertEquals("Forearms", muscleGroupLabel(listOf("forearms")))
        assertEquals("Abs", muscleGroupLabel(listOf("abdominals")))
        assertEquals("Glutes", muscleGroupLabel(listOf("glutes")))
        assertEquals("Traps", muscleGroupLabel(listOf("traps")))
        assertEquals("Neck", muscleGroupLabel(listOf("neck")))
    }

    @Test
    fun `back muscles group to Back`() {
        assertEquals("Back", muscleGroupLabel(listOf("lats")))
        assertEquals("Back", muscleGroupLabel(listOf("middle back")))
        assertEquals("Back", muscleGroupLabel(listOf("lower back")))
    }

    @Test
    fun `lower-body muscles group to Legs`() {
        assertEquals("Legs", muscleGroupLabel(listOf("quadriceps")))
        assertEquals("Legs", muscleGroupLabel(listOf("hamstrings")))
        assertEquals("Legs", muscleGroupLabel(listOf("calves")))
        assertEquals("Legs", muscleGroupLabel(listOf("adductors")))
        assertEquals("Legs", muscleGroupLabel(listOf("abductors")))
    }

    @Test
    fun `uses first, case and space insensitive`() {
        assertEquals("Chest", muscleGroupLabel(listOf("  Chest ", "triceps")))
        assertEquals("Back", muscleGroupLabel(listOf("LATS")))
    }

    @Test
    fun `unknown or empty returns null`() {
        assertNull(muscleGroupLabel(emptyList()))
        assertNull(muscleGroupLabel(listOf("eyeballs")))
        assertNull(muscleGroupLabel(listOf("")))
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.component.MuscleGroupLabelTest"`
Expected: FAIL — unresolved reference `muscleGroupLabel`.

- [ ] **Step 3: Implement**

In `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroup.kt`, add at the end of the file:

```kotlin
/**
 * Friendly muscle-group label for the first of [primaryMuscles] (case/space-insensitive).
 * Back muscles collapse to "Back"; lower-body to "Legs". Null when empty or unmapped.
 */
fun muscleGroupLabel(primaryMuscles: List<String>): String? {
    val key = primaryMuscles.firstOrNull()?.trim()?.lowercase() ?: return null
    return when (key) {
        "chest" -> "Chest"
        "shoulders" -> "Shoulders"
        "biceps" -> "Biceps"
        "triceps" -> "Triceps"
        "forearms" -> "Forearms"
        "abdominals" -> "Abs"
        "glutes" -> "Glutes"
        "traps" -> "Traps"
        "neck" -> "Neck"
        "lats", "middle back", "lower back" -> "Back"
        "quadriceps", "hamstrings", "calves", "adductors", "abductors" -> "Legs"
        else -> null
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.component.MuscleGroupLabelTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleGroup.kt \
        app/src/test/java/com/zack/recomptracker/ui/train/component/MuscleGroupLabelTest.kt
git commit -m "feat(workout): muscleGroupLabel friendly group names"
```

---

## Task 4: Resolve primary muscles in the summary ViewModel

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add the repository import + constructor param**

In `SessionSummaryViewModel.kt`, add the import:

```kotlin
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
```

Change the constructor to add the repository (after `sessionRepository`):

```kotlin
class SessionSummaryViewModel(
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
```

- [ ] **Step 2: Add `primaryMuscles` to `ExerciseRecap`**

Change the `ExerciseRecap` data class to:

```kotlin
data class ExerciseRecap(
    val name: String,
    val sets: Int,
    val topSet: String,
    val volume: Double,
    val isPr: Boolean,
    val primaryMuscles: List<String> = emptyList(),
)
```

- [ ] **Step 3: Resolve muscles when building each recap**

In `load()`, replace the recap construction at the end of the `.map { ex -> ... }` block:

```kotlin
            ExerciseRecap(
                name = ex.exerciseName,
                sets = completedSets.size,
                topSet = topSetStr,
                volume = vol,
                isPr = isPr,
            )
```

with:

```kotlin
            ExerciseRecap(
                name = ex.exerciseName,
                sets = completedSets.size,
                topSet = topSetStr,
                volume = vol,
                isPr = isPr,
                primaryMuscles = exerciseLibraryRepository.getById(ex.exerciseId)?.primaryMuscles
                    ?: emptyList(),
            )
```

(`getById` is a `suspend fun`; `load()` is already a suspend function, so this is fine.)

- [ ] **Step 4: Wire the repository in `AppContainer`**

In `AppContainer.kt`, the `SessionSummaryViewModel::class.java -> SessionSummaryViewModel(...)` construction currently passes `sessionRepository` + `savedStateHandle`. Add the library:

```kotlin
            SessionSummaryViewModel::class.java -> SessionSummaryViewModel(
                sessionRepository = container.workoutSessionRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            )
```

- [ ] **Step 5: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryViewModel.kt \
        app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(workout): resolve primary muscles for summary recaps"
```

---

## Task 5: Summary recap row — muscle icon + group label

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryScreen.kt`

- [ ] **Step 1: Add imports**

Add these imports to `SessionSummaryScreen.kt` (some may already be present — only add missing ones):

```kotlin
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.zack.recomptracker.ui.train.component.MuscleGroupIcon
import com.zack.recomptracker.ui.train.component.muscleGroupLabel
```

- [ ] **Step 2: Replace the recap row body**

In the exercise-recap `itemsIndexed(...) { _, recap -> FrostedCard { Row { ... } } }` block, replace the inner `Row { Column { name + subtitle } ; PR }` content. The current body is:

```kotlin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recap.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appColors.textPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = buildRecapSubtitle(recap),
                            fontSize = 12.sp,
                            color = appColors.textMuted,
                        )
                    }
                    if (recap.isPr) {
                        Spacer(Modifier.width(8.dp))
                        PrBadge()
                    }
                }
```

Replace it with:

```kotlin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(CornerSmall))
                            .background(appColors.cardSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        MuscleGroupIcon(
                            primaryMuscles = recap.primaryMuscles,
                            tint = accent.accentLighter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recap.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appColors.textPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        val group = muscleGroupLabel(recap.primaryMuscles)
                        val stats = buildRecapSubtitle(recap)
                        Text(
                            text = buildAnnotatedString {
                                if (group != null) {
                                    withStyle(
                                        SpanStyle(
                                            color = accent.inkLight,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    ) { append(group) }
                                    if (stats.isNotEmpty()) append(" · ")
                                }
                                append(stats)
                            },
                            fontSize = 12.sp,
                            color = appColors.textMuted,
                        )
                    }
                    if (recap.isPr) {
                        Spacer(Modifier.width(8.dp))
                        PrBadge()
                    }
                }
```

- [ ] **Step 3: Ensure `accent` is in scope**

The replacement uses `accent.accentLighter` / `accent.inkLight`. Confirm the enclosing composable has `val accent = LocalAppAccent.current`. If `accent` is not already defined in that composable's scope, add `val accent = LocalAppAccent.current` alongside the existing `val appColors = LocalAppColors.current`. (`LocalAppAccent` is already imported.)

- [ ] **Step 4: Type-check + build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `Box`, `size`, `clip`, `background`, `RoundedCornerShape`, `CornerSmall`, `fillMaxSize` are reported unresolved, add their imports — they are standard and most are already used in this file.)
Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryScreen.kt
git commit -m "feat(workout): muscle icon + group label on summary recap rows"
```

- [ ] **Step 6: Manual verification (hand off to user)**

Install the debug build. (a) During a workout: the set grid reads clearly — column labels and set numbers legible, PREV value bright/white, entered values white, empty cells still faint. (b) End a workout: each recap row shows the trained muscle icon and the group name (e.g. "Chest · 3 sets · top 62.5×8").

---

## Self-Review Notes

- **Spec coverage:** `textSecondary` token (T1); active-workout tier reassignments incl. PREV→white, labels/set-number/RIR→secondary, SESSION NOTES→secondary, placeholders unchanged (T2); `muscleGroupLabel` with Back/Legs groupings + null (T3); summary VM resolves `primaryMuscles` (T4); recap row icon + accent group label via AnnotatedString (T5). All covered.
- **Type consistency:** `textSecondary: Color` used identically across T1/T2; `muscleGroupLabel(List<String>): String?` defined T3, used T5; `ExerciseRecap.primaryMuscles` defined T4, used T5; `MuscleGroupIcon(primaryMuscles, tint, modifier)` matches its T4-feature signature.
- **No placeholders:** all code shown in full.
- **Scope:** only `SessionSetGrid` + session-notes label touched for readability; routine builder / session detail grids untouched; global `textMuted` unchanged.
