# Chart System Design
**Date:** 2026-06-02  
**Status:** Approved

## Overview

Upgrade all existing charts with draw-in animations and scrubber interactivity. Reorganize chart components into a dedicated `charts/` package with shared defaults. Add two new reusable chart types (Macro Ring, Stacked Bar) to the component library.

---

## Decisions

| Dimension | Decision |
|---|---|
| Style | Elevated Glass — subtle grid lines, crisper violet zone bands, richer glow |
| Entry animation | Draw-In — line draws left→right, area fades in at 50%, dots pop in staggered |
| Interaction | Scrubber — drag to scrub, vertical indicator snaps to nearest point, header updates live |
| New chart types | Macro Ring (Donut) + Stacked Bar |
| Architecture | New `ui/component/charts/` package with shared `ChartDefaults` |

---

## 1. Architecture

### Package structure

```
ui/component/charts/
  ChartDefaults.kt        ← shared constants (new)
  SparklineChart.kt       ← moved + upgraded
  CalorieProgressBar.kt   ← moved + upgraded
  MacroRingChart.kt       ← new
  StackedBarChart.kt      ← new
```

`ui/component/` retains all non-chart components unchanged:
`CalorieZoneBar.kt`, `MacroMiniBar.kt`, `ProgressBar.kt`, `GlassComponents.kt`, `Components.kt`

All existing call sites (ProgressScreen, DashboardScreen) update their imports to the new package path.

### ChartDefaults.kt

Plain Kotlin `object` — no DI, no state. All charts read from it.

```kotlin
object ChartDefaults {
    // Animation specs
    object AnimSpec {
        val drawIn     = tween<Float>(1200, easing = FastOutSlowInEasing)
        val dotPop     = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
        val areaFade   = tween<Float>(600)
        val barRise    = tween<Float>(800, easing = FastOutSlowInEasing)
        val ringArc    = tween<Float>(900, easing = FastOutSlowInEasing)
        val ringStagger = 300  // ms between arc segments
        val barStagger  = 60   // ms between bar columns
        val scrubReturn = tween<Float>(400, easing = FastOutSlowInEasing)
    }

    // Geometry
    val strokeWidth  = 1.8.dp
    val dotRadius    = 4.5.dp
    val glowRadius   = 11.dp
    val glowHalo     = 16.dp
    val gridAlpha    = 0.04f
    val zoneBandAlpha = 0.10f
    val zoneDashAlpha = 0x50

    // Macro color palette
    object MacroColors {
        val Protein = Color(0xFFa78bfa)   // violet
        val Carbs   = Color(0xFF34d399)   // emerald
        val Fat     = Color(0xFFfb923c)   // orange
    }
}
```

---

## 2. SparklineChart

### Draw-in animation

- `LaunchedEffect(Unit)` triggers `animatedProgress` (`Animatable(0f)`) → animates to `1f` using `ChartDefaults.AnimSpec.drawIn`
- Canvas draws a `clipRect(right = size.width * animatedProgress)` over the line path and end dot
- Area fill starts at `alpha = 0f`, fades to full once `animatedProgress > 0.5f` using `AnimSpec.areaFade`
- Each dot `i` has its own `dotVisible[i]` driven by `animatedProgress > dotThreshold[i]` where threshold = `(xPosition / totalWidth) + 0.05f`; scale animates in with `AnimSpec.dotPop`
- Glow halo blooms when `animatedProgress > 0.9f`

### Scrubber interaction

Opt-in via `showScrubber: Boolean = false` (false on MiniSparkline, true on full-size charts in ProgressScreen).

```kotlin
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
)
```

Gesture handling (only registered when `showScrubber = true` and `values.size >= 2`):
- `pointerInput` detects horizontal drag; `scrubFraction` state tracks position as 0f–1f
- Nearest data point found by `minByOrNull { abs(it.x - scrubX) }`
- Active point: glow circle `r=glowRadius`, solid dot `r=dotRadius`, all other dots at `alpha=0.3f`
- Vertical dashed line at `scrubX` in `Color(0x50a78bfa)`
- On drag: call `onScrubValue(values[nearestIndex])`
- On release: `scrubX` animates back to `pts.last().x` via `Animatable` + `AnimSpec.scrubReturn`; call `onScrubValue(null)`

### Visual refinements

- Four horizontal grid lines at 25%/50%/75%/100% of chart height: `Color(0x0AFFFFFF)`, `0.5dp` stroke
- Zone band alpha: `ChartDefaults.zoneBandAlpha` (0x1A → 0x1A stays, dash alpha increases to `0x50`)
- End glow dot uses `glowRadius=11dp` + outer halo ring at `glowHalo=16dp`, `alpha=0.08f`

### MiniSparkline

No API change. Gains draw-in animation automatically. `showScrubber` remains `false`.

---

## 3. CalorieProgressBar

No API change. Upgrade animation spec from `tween(800)` to `spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)` for a subtle bounce on fill. No scrubber — it is a status indicator, not a time-series.

Same upgrade applied to `MacroBarItem` in `DashboardScreen` (tween 900 → spring).

---

## 4. MacroRingChart (new)

```kotlin
@Composable
fun MacroRingChart(
    proteinKcal: Float,
    carbsKcal: Float,
    fatKcal: Float,
    modifier: Modifier = Modifier,
    ringStrokeWidth: Dp = 10.dp,
)
```

**Rendering:**
- Track ring: `Color(0xFF1a1a2e)`, full 360°
- Three arcs drawn in Canvas using `drawArc`, each covering its proportional sweep angle
- Order: protein starts at -90° (top), carbs follows, fat follows
- A 2dp gap between segments (subtract from sweep angle)
- Center text: total kcal in white (26sp bold), "kcal" label below in muted

**Animation:**
- Three `animateFloatAsState` values: `proteinSweep`, `carbsSweep`, `fatSweep`
- Each targets its final sweep angle (degrees), using `ChartDefaults.AnimSpec.ringArc`
- `proteinSweep` starts immediately, `carbsSweep` delayed 300ms, `fatSweep` delayed 600ms
- Delays implemented via `LaunchedEffect` + `delay()` before setting target

**Colors:** `ChartDefaults.MacroColors.Protein / Carbs / Fat`

---

## 5. StackedBarChart (new)

```kotlin
data class DayMacros(
    val label: String,
    val proteinKcal: Float,
    val carbsKcal: Float,
    val fatKcal: Float,
    val isToday: Boolean = false,
)

@Composable
fun StackedBarChart(
    days: List<DayMacros>,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    barCornerRadius: Dp = 4.dp,
)
```

**Rendering:**
- Canvas draws 7 vertical bars side by side with equal spacing
- Each bar = 3 stacked rects: fat (bottom, orange) → carbs (middle, emerald) → protein (top, violet)
- Bar width = `(usableWidth - gaps) / dayCount`
- Y scale: `maxTotal * 1.15f` so bars don't hit the top
- Today's bar: `1dp` border in `Color(0x80c4b5fd)` drawn over the bar

**Animation:**
- Each bar column has its own `animateFloatAsState` scaling from 0f to 1f
- `transform-origin` = bottom of bar (achieved via translating the draw calls)
- Stagger: column `i` target set after `i * ChartDefaults.AnimSpec.barStagger` ms delay via `LaunchedEffect`

**No scrubber.** Bars are discrete days; touch-to-highlight is a future enhancement.

**Day labels:** Row of Text composables below the Canvas, same pattern as existing `ChartCanvas` in `DashboardScreen`.

---

## 6. Call Sites

### ProgressScreen
- `SparklineChart` in `FeaturedChartCard` → add `showScrubber = true`, wire `onScrubValue` to update a local `scrubValue` state that overrides `series.currentValue` in the header
- `SparklineChart` in `ShortChartCard` → `showScrubber = false` (too small)
- `MiniSparkline` in `MiniChartCard` → no change needed

### DashboardScreen
- `ChartCanvas` (inline in `SevenDayChartCard`) → replace with `SparklineChart` from new package (same data, gains animation + scrubber)
- `MacroBarItem` → spring animation upgrade only

### New chart placement (future screens — not wired in this work)
- `MacroRingChart` → natural fit: Dashboard TodayCard as an alternative to the three macro bars
- `StackedBarChart` → natural fit: Progress screen Nutrition section, replacing or augmenting the existing sparklines

---

## 7. Out of Scope

- `CalorieZoneBar` / `MacroMiniBar` — legacy stats screen, not upgraded
- Bar chart (option A from brainstorm) — not selected, not built
- Radial progress ring (option C from brainstorm) — not selected, not built
- Tap-to-highlight on StackedBarChart — future enhancement
- MacroRingChart / StackedBarChart placement on live screens — components built but not wired to screens; placement is a separate task
