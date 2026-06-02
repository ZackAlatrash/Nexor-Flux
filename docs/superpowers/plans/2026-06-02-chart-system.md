# Chart System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade all charts with draw-in animation and scrubber interactivity, reorganize into a `charts/` package with shared defaults, and add MacroRingChart and StackedBarChart as reusable components.

**Architecture:** New `ui/component/charts/` package contains all chart components. A plain `ChartDefaults` object provides shared animation specs, stroke widths, glow radii, and macro colors so every chart is visually consistent. Canvas-drawn Composables use `Animatable` for draw-in and `pointerInput` for scrubbing; pure helper functions are extracted and unit-tested.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.animation.core` (Animatable, spring, tween), `androidx.compose.foundation.Canvas`, `androidx.compose.foundation.gestures` (detectDragGestures)

---

## File Map

```
CREATE app/src/main/java/com/zack/recomptracker/ui/component/charts/ChartDefaults.kt
MOVE   ui/component/SparklineChart.kt       → ui/component/charts/SparklineChart.kt   (+ animate + scrubber)
MOVE   ui/component/CalorieProgressBar.kt   → ui/component/charts/CalorieProgressBar.kt (+ spring)
CREATE app/src/main/java/com/zack/recomptracker/ui/component/charts/MacroRingChart.kt
CREATE app/src/main/java/com/zack/recomptracker/ui/component/charts/StackedBarChart.kt

MODIFY app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
       — MacroBarItem: tween→spring
       — SevenDayChartCard: replace inline ChartCanvas with SparklineChart
MODIFY app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt
       — FeaturedChartCard: add scrubValue state + showScrubber=true
       — update imports

CREATE app/src/test/java/com/zack/recomptracker/ui/charts/ChartHelpersTest.kt
CREATE app/src/test/java/com/zack/recomptracker/ui/charts/MacroRingChartTest.kt
CREATE app/src/test/java/com/zack/recomptracker/ui/charts/StackedBarChartTest.kt
```

---

## Task 1: ChartDefaults

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/charts/ChartDefaults.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/charts/ChartHelpersTest.kt`

- [ ] **Step 1: Create ChartDefaults.kt**

```kotlin
package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ChartDefaults {

    object AnimSpec {
        val drawIn      = tween<Float>(durationMillis = 1200, easing = FastOutSlowInEasing)
        val areaFade    = tween<Float>(durationMillis = 600)
        val dotPop      = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
        val barRise     = tween<Float>(durationMillis = 800, easing = FastOutSlowInEasing)
        val ringArc     = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)
        val progressBar = spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        )
        const val ringStaggerMs = 300L
        const val barStaggerMs  = 60L
    }

    val strokeWidth  = 1.8.dp
    val dotRadius    = 4.5.dp
    val glowRadius   = 11.dp
    val glowHalo     = 16.dp

    const val gridAlpha     = 0.04f
    const val zoneBandAlpha = 0.10f
    const val zoneDashAlpha = 0x50

    object MacroColors {
        val Protein = Color(0xFFa78bfa)
        val Carbs   = Color(0xFF34d399)
        val Fat     = Color(0xFFfb923c)
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/charts/ChartHelpersTest.kt`:

```kotlin
package com.zack.recomptracker.ui.charts

import com.zack.recomptracker.ui.component.charts.ChartDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartHelpersTest {

    @Test
    fun chartDefaultsGridAlphaIsCorrect() {
        assertEquals(0.04f, ChartDefaults.gridAlpha, 0.001f)
    }

    @Test
    fun chartDefaultsMacroColorsAreDefined() {
        // just verify the objects are accessible (compilation check)
        val p = ChartDefaults.MacroColors.Protein
        val c = ChartDefaults.MacroColors.Carbs
        val f = ChartDefaults.MacroColors.Fat
        // violet, emerald, orange — all fully opaque
        assertEquals(0xFF.toLong(), (p.value shr 24) and 0xFF)
        assertEquals(0xFF.toLong(), (c.value shr 24) and 0xFF)
        assertEquals(0xFF.toLong(), (f.value shr 24) and 0xFF)
    }
}
```

- [ ] **Step 3: Run test — expect it to fail** (file not importable yet)

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.charts.ChartHelpersTest" -q 2>&1 | tail -10
```

Expected: compilation error — `ChartDefaults` not found.

- [ ] **Step 4: Run test again after creating ChartDefaults.kt — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.charts.ChartHelpersTest" -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/charts/ChartDefaults.kt \
        app/src/test/java/com/zack/recomptracker/ui/charts/ChartHelpersTest.kt
git commit -m "feat: add ChartDefaults shared constants for chart system"
```

---

## Task 2: SparklineChart — move to charts/ + draw-in animation

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/charts/SparklineChart.kt`
- Delete: `app/src/main/java/com/zack/recomptracker/ui/component/SparklineChart.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt` (import update)

The draw-in uses a single `Animatable(0f)→1f`. The Canvas is clipped to `size.width * progress` so the line reveals left-to-right. Area fill fades in once progress > 0.5. Dots scale in as the clip passes their x-position via a pure `dotScale()` function.

- [ ] **Step 1: Write the failing test for `dotScale` and `nearestPointIndex`**

Add to `ChartHelpersTest.kt`:

```kotlin
import androidx.compose.ui.geometry.Offset
import com.zack.recomptracker.ui.component.charts.dotScale
import com.zack.recomptracker.ui.component.charts.nearestPointIndex

@Test
fun dotScaleIsZeroBeforeThreshold() {
    assertEquals(0f, dotScale(dotX = 100f, totalWidth = 200f, progress = 0.3f), 0.001f)
}

@Test
fun dotScaleIsOneWhenProgressWellPastThreshold() {
    // threshold = 100/200 = 0.5, progress = 0.7 → local = (0.7-0.5)/0.08 = 2.5 → clamped to 1.0
    assertEquals(1f, dotScale(dotX = 100f, totalWidth = 200f, progress = 0.7f), 0.001f)
}

@Test
fun dotScaleIsPartialMidTransition() {
    // threshold = 0.5, progress = 0.54 → local = 0.04/0.08 = 0.5
    assertEquals(0.5f, dotScale(dotX = 100f, totalWidth = 200f, progress = 0.54f), 0.001f)
}

@Test
fun nearestPointIndexReturnsClosest() {
    val pts = listOf(Offset(10f, 0f), Offset(50f, 0f), Offset(90f, 0f))
    assertEquals(1, nearestPointIndex(52f, pts))
}

@Test
fun nearestPointIndexReturnsZeroForEmptyList() {
    assertEquals(0, nearestPointIndex(50f, emptyList()))
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.charts.ChartHelpersTest" -q 2>&1 | tail -10
```

Expected: compilation error — `dotScale`, `nearestPointIndex` not found.

- [ ] **Step 3: Create the new SparklineChart.kt**

Create `app/src/main/java/com/zack/recomptracker/ui/component/charts/SparklineChart.kt`:

```kotlin
package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400
import androidx.compose.foundation.gestures.detectDragGestures
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun dotScale(dotX: Float, totalWidth: Float, progress: Float): Float {
    if (totalWidth <= 0f) return 0f
    val threshold = (dotX / totalWidth).coerceIn(0f, 0.95f)
    if (progress < threshold) return 0f
    return ((progress - threshold) / 0.08f).coerceIn(0f, 1f)
}

internal fun nearestPointIndex(x: Float, pts: List<Offset>): Int =
    if (pts.isEmpty()) 0
    else pts.indices.minByOrNull { abs(pts[it].x - x) } ?: 0

@Composable
fun SparklineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    showGlowDot: Boolean = true,
    showScrubber: Boolean = false,
    zoneLow: Float? = null,
    zoneHigh: Float? = null,
    onScrubValue: ((Float?) -> Unit)? = null,
) {
    if (values.isEmpty()) {
        Spacer(modifier = modifier.height(height))
        return
    }

    val drawInProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        drawInProgress.animateTo(1f, animationSpec = ChartDefaults.AnimSpec.drawIn)
    }

    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    val gestureModifier = if (showScrubber && values.size >= 2) {
        Modifier.pointerInput(values) {
            detectDragGestures(
                onDragStart = { offset ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val sidePad = 4.dp.toPx()
                    val usableW = w - 2 * sidePad
                    val n = values.size
                    val pts = values.mapIndexed { i, _ ->
                        Offset(sidePad + (if (n > 1) i.toFloat() / (n - 1) else 0.5f) * usableW, 0f)
                    }
                    scrubIndex = nearestPointIndex(offset.x, pts)
                    onScrubValue?.invoke(values[scrubIndex!!])
                },
                onDragEnd = {
                    scrubIndex = null
                    onScrubValue?.invoke(null)
                },
                onDrag = { change, _ ->
                    val w = size.width.toFloat()
                    val sidePad = 4.dp.toPx()
                    val usableW = w - 2 * sidePad
                    val n = values.size
                    val pts = values.mapIndexed { i, _ ->
                        Offset(sidePad + (if (n > 1) i.toFloat() / (n - 1) else 0.5f) * usableW, 0f)
                    }
                    scrubIndex = nearestPointIndex(change.position.x, pts)
                    onScrubValue?.invoke(values[scrubIndex!!])
                },
            )
        }
    } else Modifier

    val progress = drawInProgress.value

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(gestureModifier),
    ) {
        val w = size.width
        val h = size.height
        val sidePad = 4.dp.toPx()
        val usableW = w - 2 * sidePad
        val n = values.size
        val minVal = values.min()
        val maxVal = values.max()
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val paddedMin = minVal - range * 0.10f
        val paddedMax = maxVal + range * 0.10f
        val paddedRange = (paddedMax - paddedMin).coerceAtLeast(1f)

        fun xAt(i: Int) = sidePad + (if (n > 1) i.toFloat() / (n - 1) else 0.5f) * usableW
        fun yAt(v: Float) = h * (1f - (v - paddedMin) / paddedRange)

        val pts = values.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }

        // Grid lines (4 horizontal, 4% opacity)
        for (frac in listOf(0.25f, 0.5f, 0.75f, 1.0f)) {
            drawLine(
                color = Color.White.copy(alpha = ChartDefaults.gridAlpha),
                start = Offset(0f, h * frac),
                end   = Offset(w, h * frac),
                strokeWidth = 0.5.dp.toPx(),
            )
        }

        // Zone band
        if (zoneLow != null && zoneHigh != null) {
            val zLowY  = yAt(zoneLow).coerceIn(0f, h)
            val zHighY = yAt(zoneHigh).coerceIn(0f, h)
            drawRect(
                color    = Color(0x1A8B5CF6),
                topLeft  = Offset(0f, zHighY),
                size     = Size(w, (zLowY - zHighY).coerceAtLeast(0f)),
            )
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            val dashColor = Color(ChartDefaults.zoneDashAlpha shl 24 or 0x8B5CF6)
            drawLine(dashColor, Offset(0f, zHighY), Offset(w, zHighY), 0.7.dp.toPx(), pathEffect = dash)
            drawLine(dashColor, Offset(0f, zLowY),  Offset(w, zLowY),  0.7.dp.toPx(), pathEffect = dash)
        }

        // Build bezier paths
        val linePath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]; val p1 = pts[i]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }

        val clipRight = w * progress

        // Area fill (fades in after line is 50% drawn)
        if (progress > 0.5f) {
            val areaAlpha = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            clipRect(right = clipRight) {
                drawPath(
                    path  = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x408B5CF6), Color(0x058B5CF6)),
                    ),
                    alpha = areaAlpha,
                )
            }
        }

        // Line stroke (revealed left→right)
        clipRect(right = clipRight) {
            drawPath(
                path  = linePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF7c3aed).copy(alpha = 0.55f),
                        Violet400,
                        Violet300.copy(alpha = 0.85f),
                    ),
                ),
                style = Stroke(
                    width = ChartDefaults.strokeWidth.toPx(),
                    cap   = StrokeCap.Round,
                    join  = StrokeJoin.Round,
                ),
            )
        }

        // Dots (scale in as line passes their x)
        pts.forEachIndexed { i, pt ->
            val scale = dotScale(pt.x, w, progress)
            if (scale > 0f) {
                val isActive  = scrubIndex == i
                val isEndDot  = i == pts.lastIndex
                val dotAlpha  = if (scrubIndex != null && !isActive) 0.3f else 1f

                if (isActive) {
                    // Scrubber active dot
                    drawCircle(color = Color(0x1Fc4b5fd), radius = ChartDefaults.glowRadius.toPx(), center = pt)
                    drawCircle(color = Violet300, radius = ChartDefaults.dotRadius.toPx(), center = pt)
                    // Vertical indicator line
                    drawLine(
                        color       = Color(0x50a78bfa),
                        start       = Offset(pt.x, 0f),
                        end         = Offset(pt.x, h),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect  = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                    )
                } else if (showGlowDot && isEndDot && scrubIndex == null) {
                    // Animated end glow dot
                    val glowAlpha = ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f)
                    if (glowAlpha > 0f) {
                        drawCircle(color = Color(0x1Fc4b5fd).copy(alpha = 0.08f * glowAlpha), radius = ChartDefaults.glowHalo.toPx(), center = pt)
                        drawCircle(color = Color(0x1Fc4b5fd).copy(alpha = 0.15f * glowAlpha), radius = ChartDefaults.glowRadius.toPx(), center = pt)
                        drawCircle(color = Violet300.copy(alpha = glowAlpha), radius = ChartDefaults.dotRadius.toPx() * scale, center = pt)
                    }
                } else {
                    drawCircle(
                        color  = Violet300.copy(alpha = dotAlpha),
                        radius = 2.5.dp.toPx() * scale,
                        center = pt,
                    )
                }
            }
        }
    }
}

@Composable
fun MiniSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    SparklineChart(
        values      = values,
        modifier    = modifier,
        height      = 36.dp,
        showGlowDot = false,
    )
}
```

- [ ] **Step 4: Delete the old SparklineChart.kt**

```bash
rm "app/src/main/java/com/zack/recomptracker/ui/component/SparklineChart.kt"
```

- [ ] **Step 5: Update imports in ProgressScreen.kt**

In `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt`, replace:

```kotlin
import com.zack.recomptracker.ui.component.SparklineChart
import com.zack.recomptracker.ui.component.MiniSparkline
```

With:

```kotlin
import com.zack.recomptracker.ui.component.charts.SparklineChart
import com.zack.recomptracker.ui.component.charts.MiniSparkline
```

- [ ] **Step 6: Run failing tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.charts.ChartHelpersTest" -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Verify build compiles**

```bash
./gradlew :app:assembleDebug -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: move SparklineChart to charts/ package with draw-in animation and scrubber"
```

---

## Task 3: CalorieProgressBar — move to charts/ + spring animation

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/charts/CalorieProgressBar.kt`
- Delete: `app/src/main/java/com/zack/recomptracker/ui/component/CalorieProgressBar.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt` (import)

- [ ] **Step 1: Create the new CalorieProgressBar.kt**

Create `app/src/main/java/com/zack/recomptracker/ui/component/charts/CalorieProgressBar.kt`:

```kotlin
package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.Violet400
import com.zack.recomptracker.ui.theme.Violet500

@Composable
fun CalorieProgressBar(
    progress: Float,
    zoneLowFrac: Float,
    zoneHighFrac: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue    = progress.coerceIn(0f, 1f),
        animationSpec  = ChartDefaults.AnimSpec.progressBar,
        label          = "calorieFill",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = h / 2f

        drawRoundRect(color = Color(0x12FFFFFF), cornerRadius = CornerRadius(r))

        val zoneLeft  = (zoneLowFrac  * w).coerceIn(0f, w)
        val zoneRight = (zoneHighFrac * w).coerceIn(0f, w)
        if (zoneRight > zoneLeft) {
            drawRect(
                color    = Color(0x228B5CF6),
                topLeft  = Offset(zoneLeft, 0f),
                size     = Size(zoneRight - zoneLeft, h),
            )
            clipRect(left = zoneLeft, top = 0f, right = zoneRight, bottom = h) {
                val stripeW = 4.dp.toPx()
                val step    = 7.dp.toPx()
                var x = zoneLeft - h
                while (x < zoneRight + h) {
                    drawLine(
                        color       = Color(0x608B5CF6),
                        start       = Offset(x, 0f),
                        end         = Offset(x + h, h),
                        strokeWidth = stripeW,
                    )
                    x += step
                }
            }
        }

        val fillX = animatedProgress * w
        if (fillX > 0.5f) {
            drawRoundRect(
                brush  = Brush.horizontalGradient(
                    colors = listOf(Violet500, Violet400),
                    startX = 0f,
                    endX   = fillX.coerceAtLeast(r * 2),
                ),
                size          = Size(fillX.coerceAtLeast(r * 2), h),
                cornerRadius  = CornerRadius(r),
            )
        }
    }
}
```

- [ ] **Step 2: Delete the old CalorieProgressBar.kt**

```bash
rm "app/src/main/java/com/zack/recomptracker/ui/component/CalorieProgressBar.kt"
```

- [ ] **Step 3: Update import in DashboardScreen.kt**

In `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`, replace:

```kotlin
import com.zack.recomptracker.ui.component.CalorieProgressBar
```

With:

```kotlin
import com.zack.recomptracker.ui.component.charts.CalorieProgressBar
```

- [ ] **Step 4: Upgrade MacroBarItem animation in DashboardScreen.kt**

In `DashboardScreen.kt`, find the `MacroBarItem` composable. Replace the `animateFloatAsState` call:

```kotlin
// BEFORE
val animatedFrac by animateFloatAsState(
    targetValue   = fraction,
    animationSpec = tween(durationMillis = 900),
    label         = "macroFill",
)
```

```kotlin
// AFTER
val animatedFrac by animateFloatAsState(
    targetValue   = fraction,
    animationSpec = ChartDefaults.AnimSpec.progressBar,
    label         = "macroFill",
)
```

Also add the import at the top of `DashboardScreen.kt`:

```kotlin
import com.zack.recomptracker.ui.component.charts.ChartDefaults
```

And remove the now-unused `tween` import if it's only used there (check — it may still be used by `SevenDayChartCard`'s own `animateFloatAsState` calls; only remove if unused after this change).

- [ ] **Step 5: Verify build compiles**

```bash
./gradlew :app:assembleDebug -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: move CalorieProgressBar to charts/, upgrade progress bars to spring animation"
```

---

## Task 4: Wire scrubber in ProgressScreen FeaturedChartCard

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt`

The `FeaturedChartCard` shows `series.currentValue` in the header. When scrubbing, override that with the scrubbed value. The `ChartHeader` composable needs an optional `overrideValue` param.

- [ ] **Step 1: Add `overrideValue` to `ChartHeader`**

In `ProgressScreen.kt`, find `ChartHeader` and update its signature and the value display:

```kotlin
// BEFORE
@Composable
private fun ChartHeader(series: ChartSeries) {
    Row(...) {
        Column(...) {
            ...
            Row(...) {
                Text(
                    text = series.currentValue?.let {
                        if (series.unit == "%" || series.unit == "kcal") "%.0f".format(it)
                        else "%.1f".format(it)
                    } ?: "—",
                    ...
                )
```

```kotlin
// AFTER
@Composable
private fun ChartHeader(series: ChartSeries, overrideValue: Float? = null) {
    Row(...) {
        Column(...) {
            ...
            val displayValue = overrideValue ?: series.currentValue
            Row(...) {
                Text(
                    text = displayValue?.let {
                        if (series.unit == "%" || series.unit == "kcal") "%.0f".format(it)
                        else "%.1f".format(it)
                    } ?: "—",
                    ...
                )
```

- [ ] **Step 2: Add scrub state to `FeaturedChartCard` and enable scrubber**

```kotlin
// BEFORE
@Composable
private fun FeaturedChartCard(series: ChartSeries) {
    FeaturedCard {
        ChartHeader(series)
        Spacer(Modifier.height(10.dp))
        if (series.values.isNotEmpty() && series.values.any { it != 0f }) {
            SparklineChart(
                values      = series.values,
                height      = 72.dp,
                showGlowDot = true,
            )
        } else {
            NoDataLabel()
        }
    }
}
```

```kotlin
// AFTER
@Composable
private fun FeaturedChartCard(series: ChartSeries) {
    var scrubValue by remember { mutableStateOf<Float?>(null) }
    FeaturedCard {
        ChartHeader(series, overrideValue = scrubValue)
        Spacer(Modifier.height(10.dp))
        if (series.values.isNotEmpty() && series.values.any { it != 0f }) {
            SparklineChart(
                values       = series.values,
                height       = 72.dp,
                showGlowDot  = true,
                showScrubber = true,
                onScrubValue = { scrubValue = it },
            )
        } else {
            NoDataLabel()
        }
    }
}
```

Add the missing import at the top of `ProgressScreen.kt`:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

(Some of these may already be present — only add the ones that are missing.)

- [ ] **Step 3: Verify build compiles**

```bash
./gradlew :app:assembleDebug -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt
git commit -m "feat: wire scrubber in ProgressScreen FeaturedChartCard — header value updates on drag"
```

---

## Task 5: Replace ChartCanvas in DashboardScreen with SparklineChart

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`

The inline `ChartCanvas` private composable (lines 466–588) is replaced by `SparklineChart` from the new package. The `SevenDayChartCard` holds the scrub state.

- [ ] **Step 1: Add import for SparklineChart to DashboardScreen.kt**

```kotlin
import com.zack.recomptracker.ui.component.charts.SparklineChart
```

- [ ] **Step 2: Add scrub state to `SevenDayChartCard` and replace `ChartCanvas`**

In `SevenDayChartCard`, replace the chart section:

```kotlin
// BEFORE (inside SevenDayChartCard)
ChartCanvas(
    days     = state.last7DaysCalories,
    zoneLow  = state.preferences.calorieZoneLowerBound,
    zoneHigh = state.preferences.calorieZoneUpperBound,
)
```

```kotlin
// AFTER (inside SevenDayChartCard, add scrubValue state at top of composable)
var scrubCalories by remember { mutableStateOf<Float?>(null) }

// Replace ChartCanvas with SparklineChart:
SparklineChart(
    values       = state.last7DaysCalories.map { it.calories.toFloat() },
    height       = 90.dp,
    showGlowDot  = true,
    showScrubber = true,
    zoneLow      = state.preferences.calorieZoneLowerBound.toFloat(),
    zoneHigh     = state.preferences.calorieZoneUpperBound.toFloat(),
    onScrubValue = { scrubCalories = it },
)
```

Also add the `remember`/`mutableStateOf`/`setValue` imports if not already present (same ones as Task 4 Step 2).

- [ ] **Step 3: Delete the now-unused `ChartCanvas` private function**

Remove the entire `ChartCanvas` composable function from `DashboardScreen.kt` (the function starting at approximately line 466 that takes `days: List<DayCalories>, zoneLow: Int, zoneHigh: Int`).

- [ ] **Step 4: Verify build compiles**

```bash
./gradlew :app:assembleDebug -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat: replace inline ChartCanvas in DashboardScreen with SparklineChart from charts/"
```

---

## Task 6: MacroRingChart

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/charts/MacroRingChart.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/charts/MacroRingChartTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/charts/MacroRingChartTest.kt`:

```kotlin
package com.zack.recomptracker.ui.charts

import com.zack.recomptracker.ui.component.charts.macroSweepAngles
import org.junit.Assert.assertEquals
import org.junit.Test

class MacroRingChartTest {

    @Test
    fun equalMacrosProduceEqualSweeps() {
        val (p, c, f) = macroSweepAngles(100f, 100f, 100f)
        // 360/3 = 120, minus 2dp gap each = 118
        assertEquals(118f, p, 0.5f)
        assertEquals(118f, c, 0.5f)
        assertEquals(118f, f, 0.5f)
    }

    @Test
    fun sweepsDoNotExceed360Total() {
        val (p, c, f) = macroSweepAngles(200f, 100f, 50f)
        assert(p + c + f <= 360f) { "total sweep=${p+c+f} exceeds 360" }
    }

    @Test
    fun zeroMacrosReturnZeroSweeps() {
        val (p, c, f) = macroSweepAngles(0f, 0f, 0f)
        assertEquals(0f, p, 0.001f)
        assertEquals(0f, c, 0.001f)
        assertEquals(0f, f, 0.001f)
    }

    @Test
    fun onlyProteinGetsMostSweep() {
        val (p, c, f) = macroSweepAngles(300f, 50f, 50f)
        assert(p > c) { "protein should have more sweep than carbs" }
        assert(p > f) { "protein should have more sweep than fat" }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.charts.MacroRingChartTest" -q 2>&1 | tail -10
```

Expected: compilation error — `macroSweepAngles` not found.

- [ ] **Step 3: Create MacroRingChart.kt**

Create `app/src/main/java/com/zack/recomptracker/ui/component/charts/MacroRingChart.kt`:

```kotlin
package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.TextMuted
import kotlinx.coroutines.delay

internal fun macroSweepAngles(
    proteinKcal: Float,
    carbsKcal: Float,
    fatKcal: Float,
): Triple<Float, Float, Float> {
    val total = (proteinKcal + carbsKcal + fatKcal).coerceAtLeast(1f)
    val gapDeg = 2f
    val proteinSweep = (proteinKcal / total * 360f - gapDeg).coerceAtLeast(0f)
    val carbsSweep   = (carbsKcal   / total * 360f - gapDeg).coerceAtLeast(0f)
    val fatSweep     = (fatKcal     / total * 360f - gapDeg).coerceAtLeast(0f)
    return Triple(proteinSweep, carbsSweep, fatSweep)
}

@Composable
fun MacroRingChart(
    proteinKcal: Float,
    carbsKcal: Float,
    fatKcal: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
) {
    val (targetProtein, targetCarbs, targetFat) = macroSweepAngles(proteinKcal, carbsKcal, fatKcal)

    var proteinTarget by remember { mutableFloatStateOf(0f) }
    var carbsTarget   by remember { mutableFloatStateOf(0f) }
    var fatTarget     by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(proteinKcal, carbsKcal, fatKcal) {
        proteinTarget = targetProtein
        delay(ChartDefaults.AnimSpec.ringStaggerMs)
        carbsTarget = targetCarbs
        delay(ChartDefaults.AnimSpec.ringStaggerMs)
        fatTarget = targetFat
    }

    val proteinSweep by animateFloatAsState(targetValue = proteinTarget, animationSpec = ChartDefaults.AnimSpec.ringArc, label = "proteinArc")
    val carbsSweep   by animateFloatAsState(targetValue = carbsTarget,   animationSpec = ChartDefaults.AnimSpec.ringArc, label = "carbsArc")
    val fatSweep     by animateFloatAsState(targetValue = fatTarget,     animationSpec = ChartDefaults.AnimSpec.ringArc, label = "fatArc")

    val totalKcal = (proteinKcal + carbsKcal + fatKcal).toInt()

    Box(
        modifier        = modifier.size(ringSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke    = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset     = strokeWidth.toPx() / 2f
            val arcSize   = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft   = Offset(inset, inset)

            // Track ring
            drawArc(
                color       = Color(0xFF1a1a2e),
                startAngle  = 0f,
                sweepAngle  = 360f,
                useCenter   = false,
                topLeft     = topLeft,
                size        = arcSize,
                style       = Stroke(width = strokeWidth.toPx()),
            )

            // Protein arc (starts at top = -90°)
            var currentAngle = -90f
            if (proteinSweep > 0f) {
                drawArc(
                    color      = ChartDefaults.MacroColors.Protein,
                    startAngle = currentAngle,
                    sweepAngle = proteinSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = stroke,
                )
                currentAngle += proteinSweep + 2f
            }

            // Carbs arc
            if (carbsSweep > 0f) {
                drawArc(
                    color      = ChartDefaults.MacroColors.Carbs,
                    startAngle = currentAngle,
                    sweepAngle = carbsSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = stroke,
                )
                currentAngle += carbsSweep + 2f
            }

            // Fat arc
            if (fatSweep > 0f) {
                drawArc(
                    color      = ChartDefaults.MacroColors.Fat,
                    startAngle = currentAngle,
                    sweepAngle = fatSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = stroke,
                )
            }
        }

        // Center labels
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "$totalKcal",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Black,
                color      = Color.White,
                letterSpacing = (-0.5).sp,
                lineHeight = 20.sp,
            )
            Text(
                text     = "kcal",
                fontSize = 9.sp,
                color    = TextMuted,
            )
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.charts.MacroRingChartTest" -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Verify build compiles**

```bash
./gradlew :app:assembleDebug -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/charts/MacroRingChart.kt \
        app/src/test/java/com/zack/recomptracker/ui/charts/MacroRingChartTest.kt
git commit -m "feat: add MacroRingChart — animated three-segment donut for macro breakdown"
```

---

## Task 7: StackedBarChart

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/charts/StackedBarChart.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/charts/StackedBarChartTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/charts/StackedBarChartTest.kt`:

```kotlin
package com.zack.recomptracker.ui.charts

import com.zack.recomptracker.ui.component.charts.DayMacros
import com.zack.recomptracker.ui.component.charts.stackedYScale
import org.junit.Assert.assertEquals
import org.junit.Test

class StackedBarChartTest {

    private fun day(p: Float, c: Float, f: Float) = DayMacros("Mon", p, c, f)

    @Test
    fun yScaleIs115PercentOfMaxTotal() {
        val days = listOf(day(100f, 200f, 50f), day(50f, 100f, 30f))
        // max total = 100+200+50 = 350; scale = 350 * 1.15 = 402.5
        assertEquals(402.5f, stackedYScale(days), 0.5f)
    }

    @Test
    fun yScaleIsAtLeastOne() {
        assertEquals(1f, stackedYScale(emptyList()), 0.001f)
    }

    @Test
    fun yScaleHandlesSingleDay() {
        val days = listOf(day(200f, 300f, 100f))
        assertEquals(690f, stackedYScale(days), 0.5f)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.charts.StackedBarChartTest" -q 2>&1 | tail -10
```

Expected: compilation error — `DayMacros`, `stackedYScale` not found.

- [ ] **Step 3: Create StackedBarChart.kt**

Create `app/src/main/java/com/zack/recomptracker/ui/component/charts/StackedBarChart.kt`:

```kotlin
package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.TextVeryMuted
import com.zack.recomptracker.ui.theme.Violet300
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DayMacros(
    val label: String,
    val proteinKcal: Float,
    val carbsKcal: Float,
    val fatKcal: Float,
    val isToday: Boolean = false,
)

internal fun stackedYScale(days: List<DayMacros>): Float {
    val maxTotal = days.maxOfOrNull { it.proteinKcal + it.carbsKcal + it.fatKcal } ?: 0f
    return (maxTotal * 1.15f).coerceAtLeast(1f)
}

@Composable
fun StackedBarChart(
    days: List<DayMacros>,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    barCornerRadius: Dp = 4.dp,
) {
    if (days.isEmpty()) return

    val yScale = stackedYScale(days)

    // One Animatable per bar — created once per days.size, animated via coroutines.
    // Animatable.value is read directly in Canvas draw scope to trigger recomposition.
    val animatables = remember(days.size) { List(days.size) { Animatable(0f) } }
    LaunchedEffect(days.size) {
        animatables.forEachIndexed { i, anim ->
            launch {
                delay(i * ChartDefaults.AnimSpec.barStaggerMs)
                anim.animateTo(1f, animationSpec = ChartDefaults.AnimSpec.barRise)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val w        = size.width
            val h        = size.height
            val n        = days.size
            val gap      = 4.dp.toPx()
            val barWidth = (w - gap * (n - 1)) / n
            val cr       = CornerRadius(barCornerRadius.toPx())

            days.forEachIndexed { i, day ->
                val left  = i * (barWidth + gap)
                val scale = animatables[i].value

                val totalH   = (day.proteinKcal + day.carbsKcal + day.fatKcal) / yScale * h * scale
                val proteinH = day.proteinKcal / yScale * h * scale
                val carbsH   = day.carbsKcal   / yScale * h * scale
                val fatH     = day.fatKcal     / yScale * h * scale

                if (totalH <= 0f) return@forEachIndexed

                // Fat (bottom)
                if (fatH > 0f) {
                    val top = h - fatH
                    drawRoundRect(
                        color        = ChartDefaults.MacroColors.Fat,
                        topLeft      = Offset(left, top),
                        size         = Size(barWidth, fatH),
                        cornerRadius = if (proteinH == 0f && carbsH == 0f) cr else CornerRadius.Zero,
                    )
                }

                // Carbs (middle)
                if (carbsH > 0f) {
                    val top = h - fatH - carbsH
                    drawRect(
                        color   = ChartDefaults.MacroColors.Carbs,
                        topLeft = Offset(left, top),
                        size    = Size(barWidth, carbsH),
                    )
                }

                // Protein (top)
                if (proteinH > 0f) {
                    val top = h - totalH
                    drawRoundRect(
                        color        = ChartDefaults.MacroColors.Protein,
                        topLeft      = Offset(left, top),
                        size         = Size(barWidth, proteinH),
                        cornerRadius = CornerRadius(barCornerRadius.toPx(), barCornerRadius.toPx(), 0f, 0f),
                    )
                }

                // Today highlight border
                if (day.isToday) {
                    val top = h - totalH
                    drawRoundRect(
                        color        = Violet300.copy(alpha = 0.5f),
                        topLeft      = Offset(left - 1.dp.toPx(), top - 1.dp.toPx()),
                        size         = Size(barWidth + 2.dp.toPx(), totalH + 2.dp.toPx()),
                        cornerRadius = cr,
                        style        = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        }

        // Day labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            days.forEach { day ->
                Text(
                    text       = day.label,
                    fontSize   = 9.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    color      = if (day.isToday) Violet300 else TextVeryMuted,
                    modifier   = Modifier.weight(1f),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.charts.StackedBarChartTest" -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Verify build compiles**

```bash
./gradlew :app:assembleDebug -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/charts/StackedBarChart.kt \
        app/src/test/java/com/zack/recomptracker/ui/charts/StackedBarChartTest.kt
git commit -m "feat: add StackedBarChart — animated stacked macro bars for 7-day breakdown"
```

---

## Task 8: Final verification

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL` with no test failures.

- [ ] **Step 2: Clean build**

```bash
./gradlew :app:assembleDebug --rerun-tasks -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Confirm no references to old package paths remain**

```bash
grep -r "ui.component.SparklineChart\|ui.component.CalorieProgressBar\|ui.component.MiniSparkline" \
    app/src/main/java/ 2>/dev/null
```

Expected: no output (all imports updated).

- [ ] **Step 4: Commit any final cleanup**

```bash
git add -A
git status
# Only commit if there are changes
git commit -m "chore: final chart system cleanup and import verification" || echo "nothing to commit"
```

---

## Summary of what was built

| Component | Location | Status |
|---|---|---|
| `ChartDefaults` | `charts/ChartDefaults.kt` | New |
| `SparklineChart` + `MiniSparkline` | `charts/SparklineChart.kt` | Upgraded (draw-in + scrubber) |
| `CalorieProgressBar` | `charts/CalorieProgressBar.kt` | Upgraded (spring animation) |
| `MacroRingChart` | `charts/MacroRingChart.kt` | New |
| `StackedBarChart` | `charts/StackedBarChart.kt` | New |
| `MacroBarItem` in Dashboard | `DashboardScreen.kt` | Upgraded (spring animation) |
| `FeaturedChartCard` | `ProgressScreen.kt` | Scrubber wired |
| `SevenDayChartCard` | `DashboardScreen.kt` | ChartCanvas replaced with SparklineChart |
