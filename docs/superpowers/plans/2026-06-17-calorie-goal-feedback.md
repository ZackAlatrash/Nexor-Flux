# Calorie Goal Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Color past missed-zone bars dark crimson, and make the NutritionStrip card turn green with a celebration animation when the user hits their calorie zone today (or red when viewing a past missed day).

**Architecture:** Two UI files change. `WeekCalorieStrip.kt` receives `today` threaded into `WeekBarItem` so past below-zone bars get a semantic missed color. `FoodScreen.kt` gains a private `CalorieDayStatus` enum + pure `calorieStatus()` helper, `CalorieProgressBar` gains optional fill-color params, and `NutritionStrip` wraps its `FrostedCard` in a `Box` with an animated color overlay driven by `animateColorAsState` + a one-shot `Animatable<Float>` scale pop.

**Tech Stack:** Kotlin, Jetpack Compose (`AnimatedContent`, `Animatable`, `animateColorAsState`, `graphicsLayer`), JUnit 4.

---

## Files

| File | Change |
|---|---|
| `app/src/main/java/com/zack/recomptracker/ui/component/WeekCalorieStrip.kt` | Add `today: LocalDate` to `WeekBarItem`; missed-bar color logic |
| `app/src/main/java/com/zack/recomptracker/ui/component/charts/CalorieProgressBar.kt` | Add `fillColorStart`/`fillColorEnd` optional params |
| `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt` | `CalorieDayStatus` enum, `calorieStatus()` fn, rewrite `NutritionStrip` |
| `app/src/test/java/com/zack/recomptracker/ui/CalorieDayStatusTest.kt` | Unit tests for `calorieStatus()` |

---

## Task 1: WeekCalorieStrip — thread `today`, recolor missed bars

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/WeekCalorieStrip.kt`

- [ ] **Step 1: Add `today` param to `WeekBarItem` and thread it through**

In `WeekCalorieStrip.kt`, `WeekBarItem` is a private function. Add `today: LocalDate` as its last positional parameter (before `modifier`), and update `barColor` logic.

Replace the entire `WeekBarItem` composable (lines ~197–243) with:

```kotlin
@Composable
private fun WeekBarItem(
    summary: DayCalorieSummary,
    isSelected: Boolean,
    scaleMax: Int,
    targetLow: Int,
    targetHigh: Int,
    today: LocalDate,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val empty = summary.calories == 0
    val targetFrac = if (empty) 0.04f else (summary.calories.toFloat() / scaleMax).coerceIn(0f, 1f)
    val animFrac by animateFloatAsState(targetFrac, tween(400), label = "bar_${summary.date}")

    val isPastMissed = !empty && summary.date < today && summary.calories < targetLow

    val barColor = when {
        empty           -> if (isSelected) appColors.textPrimary.copy(alpha = 0.18f)
                          else appColors.textPrimary.copy(alpha = 0.10f)
        summary.calories in targetLow..targetHigh -> accent.accentLight
        summary.calories > targetHigh             -> Color(0xFFF97316)
        isPastMissed    -> if (appColors.isDark) Color(0xFF7F1D1D)
                          else Color(0xFF9CA3AF).copy(alpha = 0.55f)
        else            -> accent.accentLighter.copy(alpha = 0.75f)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) accent.accent.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) accent.accent.copy(alpha = 0.19f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelected,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .width(if (isSelected) 12.dp else 8.dp)
                .fillMaxHeight(animFrac)
                .background(barColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
        )
    }
}
```

Then in the `WeekBarItem(...)` call site inside `WeekCalorieStrip` (inside the `weekData.forEach` loop), add `today = today`:

```kotlin
weekData.forEach { summary ->
    WeekBarItem(
        summary    = summary,
        isSelected = summary.date == selectedDate,
        scaleMax   = scaleMax,
        targetLow  = targetLow,
        targetHigh = targetHigh,
        today      = today,
        onSelected = { onDaySelected(summary.date) },
        modifier   = Modifier.weight(1f),
    )
}
```

- [ ] **Step 2: Type-check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/WeekCalorieStrip.kt
git commit -m "feat(food): recolor past missed zone bars in week strip"
```

---

## Task 2: CalorieProgressBar — optional fill color params

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/charts/CalorieProgressBar.kt`

- [ ] **Step 1: Add `fillColorStart` and `fillColorEnd` parameters**

Add two optional parameters and resolve them before the Canvas block. Replace the `CalorieProgressBar` signature and the fill brush inside Canvas:

```kotlin
@Composable
fun CalorieProgressBar(
    progress: Float,
    zoneLowFrac: Float,
    zoneHighFrac: Float,
    modifier: Modifier = Modifier,
    plannedProgress: Float = 0f,
    fillColorStart: Color = Color.Unspecified,
    fillColorEnd: Color = Color.Unspecified,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val resolvedStart = if (fillColorStart != Color.Unspecified) fillColorStart else accent.accent
    val resolvedEnd   = if (fillColorEnd   != Color.Unspecified) fillColorEnd   else accent.accentLight
    // ... rest of the existing body unchanged, except replace the fill drawRoundRect brush:
    //   listOf(accent.accent, accent.accentLight)  →  listOf(resolvedStart, resolvedEnd)
```

The only line that changes inside `Canvas` is the fill brush (search for `listOf(accent.accent, accent.accentLight)`):

```kotlin
brush = Brush.horizontalGradient(
    colors = listOf(resolvedStart, resolvedEnd),
    startX = 0f,
    endX   = fillX.coerceAtLeast(r * 2),
),
```

- [ ] **Step 2: Type-check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/charts/CalorieProgressBar.kt
git commit -m "feat(food): add optional fill color params to CalorieProgressBar"
```

---

## Task 3: `CalorieDayStatus` enum + `calorieStatus()` function + unit tests

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ui/CalorieDayStatusTest.kt`

- [ ] **Step 1: Add enum and pure function to `FoodScreen.kt`**

Add these two declarations just above the `NutritionStrip` function (after line 496, before `private fun NutritionStrip`):

```kotlin
private enum class CalorieDayStatus { BelowZone, GoalHit, Over, Missed }

internal fun calorieStatus(
    cal: Int,
    zoneLow: Int,
    zoneHigh: Int,
    isToday: Boolean,
    isPast: Boolean,
): CalorieDayStatus = when {
    cal in zoneLow..zoneHigh -> CalorieDayStatus.GoalHit
    cal > zoneHigh           -> CalorieDayStatus.Over
    isPast && cal > 0        -> CalorieDayStatus.Missed
    else                     -> CalorieDayStatus.BelowZone
}
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/zack/recomptracker/ui/CalorieDayStatusTest.kt`:

```kotlin
package com.zack.recomptracker.ui

import com.zack.recomptracker.ui.today.calorieStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieDayStatusTest {

    private val zoneLow  = 1800
    private val zoneHigh = 2000

    @Test
    fun belowZone_today_returnsBelowZone() {
        val result = calorieStatus(1500, zoneLow, zoneHigh, isToday = true, isPast = false)
        assertEquals("below zone today", com.zack.recomptracker.ui.today.CalorieDayStatus.BelowZone, result)
    }

    @Test
    fun belowZone_future_returnsBelowZone() {
        val result = calorieStatus(0, zoneLow, zoneHigh, isToday = false, isPast = false)
        assertEquals("zero cals future", com.zack.recomptracker.ui.today.CalorieDayStatus.BelowZone, result)
    }

    @Test
    fun inZone_returnsGoalHit() {
        val result = calorieStatus(1900, zoneLow, zoneHigh, isToday = true, isPast = false)
        assertEquals("in zone", com.zack.recomptracker.ui.today.CalorieDayStatus.GoalHit, result)
    }

    @Test
    fun exactlyAtLowerBound_returnsGoalHit() {
        val result = calorieStatus(1800, zoneLow, zoneHigh, isToday = false, isPast = true)
        assertEquals("at lower bound", com.zack.recomptracker.ui.today.CalorieDayStatus.GoalHit, result)
    }

    @Test
    fun exactlyAtUpperBound_returnsGoalHit() {
        val result = calorieStatus(2000, zoneLow, zoneHigh, isToday = true, isPast = false)
        assertEquals("at upper bound", com.zack.recomptracker.ui.today.CalorieDayStatus.GoalHit, result)
    }

    @Test
    fun overZone_returnsOver() {
        val result = calorieStatus(2200, zoneLow, zoneHigh, isToday = true, isPast = false)
        assertEquals("over zone today", com.zack.recomptracker.ui.today.CalorieDayStatus.Over, result)
    }

    @Test
    fun overZone_pastDay_returnsOver() {
        val result = calorieStatus(2500, zoneLow, zoneHigh, isToday = false, isPast = true)
        assertEquals("over zone past", com.zack.recomptracker.ui.today.CalorieDayStatus.Over, result)
    }

    @Test
    fun belowZone_pastDay_withLoggedCals_returnsMissed() {
        val result = calorieStatus(900, zoneLow, zoneHigh, isToday = false, isPast = true)
        assertEquals("past with cals missed", com.zack.recomptracker.ui.today.CalorieDayStatus.Missed, result)
    }

    @Test
    fun zeroCals_pastDay_returnsBelowZone_notMissed() {
        val result = calorieStatus(0, zoneLow, zoneHigh, isToday = false, isPast = true)
        assertEquals("zero cals past = not logged, not missed",
            com.zack.recomptracker.ui.today.CalorieDayStatus.BelowZone, result)
    }
}
```

- [ ] **Step 3: Run tests — expect failures (enum is private)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.CalorieDayStatusTest" 2>&1 | tail -30
```

Expected: compilation error because `CalorieDayStatus` is `private`. That is correct — the next step promotes `calorieStatus()` to `internal` (already done) but leaves the enum private. The tests reference `CalorieDayStatus` directly which won't compile. Fix: make the enum `internal` too.

- [ ] **Step 4: Change `CalorieDayStatus` to `internal`**

In `FoodScreen.kt`, change:

```kotlin
private enum class CalorieDayStatus { BelowZone, GoalHit, Over, Missed }
```

to:

```kotlin
internal enum class CalorieDayStatus { BelowZone, GoalHit, Over, Missed }
```

- [ ] **Step 5: Run tests — expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.CalorieDayStatusTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 9 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt \
        app/src/test/java/com/zack/recomptracker/ui/CalorieDayStatusTest.kt
git commit -m "feat(food): add CalorieDayStatus enum + calorieStatus() with tests"
```

---

## Task 4: Rewrite `NutritionStrip` with status, animation, and overlay

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`

- [ ] **Step 1: Add missing imports to `FoodScreen.kt`**

Add these imports at the top of `FoodScreen.kt` (after the existing animation imports):

```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import com.zack.recomptracker.ui.component.PillStatus
```

- [ ] **Step 2: Replace `NutritionStrip` with the status-aware implementation**

Replace the entire `NutritionStrip` function (from `private fun NutritionStrip` through its closing `}`) with:

```kotlin
@Composable
private fun NutritionStrip(state: FoodLogUiState) {
    val cal         = state.totals.calories
    val target      = state.target
    val zoneLow     = target.calorieZoneLowerBound
    val zoneHigh    = target.calorieZoneUpperBound
    val scaleMax    = ((zoneHigh * 1.2).toInt()).coerceAtLeast(1)
    val calFrac     = (cal.toFloat() / scaleMax).coerceIn(0f, 1f)
    val plannedCal  = state.plannedTotals.calories
    val projectedFrac = if (plannedCal > 0)
        ((cal + plannedCal).toFloat() / scaleMax).coerceIn(0f, 1f) else 0f

    val appColors = LocalAppColors.current
    val accent    = LocalAppAccent.current

    val status = calorieStatus(cal, zoneLow, zoneHigh, state.isToday, state.isPast)

    val calSubText = when (status) {
        CalorieDayStatus.GoalHit   -> " kcal"
        CalorieDayStatus.Over      -> " kcal · ${cal - zoneHigh} over"
        CalorieDayStatus.Missed    -> " kcal · ${zoneLow - cal} below zone"
        CalorieDayStatus.BelowZone -> " kcal · ${zoneLow - cal} to zone"
    }

    // ── One-shot celebration (today: BelowZone → GoalHit transition) ──────────
    val prevInZone = remember { mutableStateOf(status == CalorieDayStatus.GoalHit) }
    var celebrateTriggered by remember { mutableStateOf(false) }
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(status) {
        val nowGoalHit = status == CalorieDayStatus.GoalHit
        if (nowGoalHit && !prevInZone.value && state.isToday) {
            celebrateTriggered = true
            scaleAnim.snapTo(1f)
            scaleAnim.animateTo(1.026f, tween(200))
            scaleAnim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
        }
        prevInZone.value = nowGoalHit
    }

    // ── Animated tint overlay colours ─────────────────────────────────────────
    val overlayColor by animateColorAsState(
        targetValue = when (status) {
            CalorieDayStatus.GoalHit -> if (appColors.isDark) Color(0x400A1A10) else Color(0x2016A34A)
            CalorieDayStatus.Missed  -> if (appColors.isDark) Color(0x401A0E0E) else Color(0x20DC2626)
            else                     -> Color.Transparent
        },
        animationSpec = tween(600),
        label = "overlayBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when (status) {
            CalorieDayStatus.GoalHit -> if (appColors.isDark) Color(0xFF166534) else Color(0x8016A34A)
            CalorieDayStatus.Missed  -> if (appColors.isDark) Color(0xFF4A1515) else Color(0x80DC2626)
            else                     -> Color.Transparent
        },
        animationSpec = tween(600),
        label = "overlayBorder",
    )
    val barStart by animateColorAsState(
        targetValue = when (status) {
            CalorieDayStatus.GoalHit -> Color(0xFF16A34A)
            CalorieDayStatus.Missed  -> Color(0xFF991B1B)
            else                     -> accent.accent
        },
        animationSpec = tween(600),
        label = "barStart",
    )
    val barEnd by animateColorAsState(
        targetValue = when (status) {
            CalorieDayStatus.GoalHit -> Color(0xFF4ADE80)
            CalorieDayStatus.Missed  -> Color(0xFFDC2626)
            else                     -> accent.accentLight
        },
        animationSpec = tween(600),
        label = "barEnd",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scaleAnim.value; scaleY = scaleAnim.value },
    ) {
        FrostedCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%,d", cal),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = appColors.textPrimary,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        text = calSubText,
                        fontSize = 11.sp,
                        color = appColors.textMuted,
                    )
                }
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "badge",
                ) { s ->
                    when (s) {
                        CalorieDayStatus.GoalHit   -> VioletBadge(PillStatus.GOOD,      "Goal hit!")
                        CalorieDayStatus.Missed    -> VioletBadge(PillStatus.OFF_TRACK, "Missed")
                        CalorieDayStatus.Over      -> VioletBadge(text = "Over")
                        CalorieDayStatus.BelowZone -> VioletBadge(text = "Below")
                    }
                }
            }

            if (plannedCal > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (cal > 0) "+$plannedCal kcal planned · ${cal + plannedCal} projected"
                           else "$plannedCal kcal planned",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent.inkLight.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.height(8.dp))

            CalorieProgressBar(
                progress        = calFrac,
                zoneLowFrac     = (zoneLow.toFloat()  / scaleMax).coerceIn(0f, 1f),
                zoneHighFrac    = (zoneHigh.toFloat() / scaleMax).coerceIn(0f, 1f),
                plannedProgress = projectedFrac,
                fillColorStart  = barStart,
                fillColorEnd    = barEnd,
                modifier        = Modifier.fillMaxWidth().height(8.dp),
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MacroProgressItem(
                    label  = "Protein",
                    value  = "${state.totals.proteinG.toInt()}g",
                    remain = "${(target.targetProteinG - state.totals.proteinG).toInt().coerceAtLeast(0)}g to go",
                    frac   = safeMacroFrac(state.totals.proteinG, target.targetProteinG),
                    modifier = Modifier.weight(1f),
                )
                MacroProgressItem(
                    label  = "Carbs",
                    value  = "${state.totals.carbsG.toInt()}g",
                    remain = "${(target.targetCarbsG - state.totals.carbsG).toInt().coerceAtLeast(0)}g to go",
                    frac   = safeMacroFrac(state.totals.carbsG, target.targetCarbsG),
                    modifier = Modifier.weight(1f),
                )
                MacroProgressItem(
                    label  = "Fat",
                    value  = "${state.totals.fatG.toInt()}g",
                    remain = "${(target.targetFatG - state.totals.fatG).toInt().coerceAtLeast(0)}g to go",
                    frac   = safeMacroFrac(state.totals.fatG, target.targetFatG),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Animated tint overlay — drawn on top of the frosted card surface
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(CornerCard))
                .background(overlayColor)
                .border(1.dp, borderColor, RoundedCornerShape(CornerCard)),
        )
    }
}
```

- [ ] **Step 3: Type-check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. If you see `Unresolved reference: Animatable`, ensure the import `androidx.compose.animation.core.Animatable` was added. If `togetherWith` is unresolved, add `androidx.compose.animation.togetherWith`.

- [ ] **Step 4: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
git commit -m "feat(food): NutritionStrip status states + celebration animation"
```

---

## Task 5: Full build verification

- [ ] **Step 1: Assemble debug APK**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run full test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass (including the 9 new `CalorieDayStatusTest` tests).

- [ ] **Step 3: Final commit if any stray changes**

If there are unstaged changes from fixing compile errors:

```bash
git add -p
git commit -m "fix(food): calorie goal feedback compile fixes"
```
