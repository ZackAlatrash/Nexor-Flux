# Aurora Background Design

**Date:** 2026-06-03
**Branch:** feat/real-glass-cards

## Goal

Add an animated, gyroscope-reactive background to the app so that the liquid glass cards and buttons have rich, living color to blur through. Currently `contentBackdrop` records only a flat near-black gradient, making the glass effect invisible. This design fixes that by recording animated color pools behind the gradient.

---

## Architecture

One new composable — `AuroraBackground` — is added as a child of the `contentBackdrop` box in `RecompApp.kt`. No other structural changes.

```
Box(Modifier.layerBackdrop(contentBackdrop).fillMaxSize().background(gradient)) {
    AuroraBackground()   // new — recorded by contentBackdrop
}
```

Because `AuroraBackground` lives inside the recording zone, all glass components (`FrostedCard`, `TintedCard`, buttons, nav bar) automatically blur over the moving color with zero other changes needed.

**New file:** `app/src/main/java/com/zack/recomptracker/ui/component/AuroraBackground.kt`

**Modified file:** `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt` — add `AuroraBackground()` inside the contentBackdrop box.

---

## `AuroraBackground` Composable

### Color pools

Three radial gradient "pools" drawn on a `Canvas` that fills `fillMaxSize`. Each pool is one `drawCircle` call with a radial `Brush`. They use `BlendMode.Screen` so pools mix into vivid blended color wherever they overlap — this is the effect that makes the glass look alive.

| Pool | Color | Radius | Ambient speed |
|------|-------|--------|---------------|
| 1 | Deep violet `#7C3AED` at ~45% opacity | ~85% of screen width | Slowest |
| 2 | Indigo `#4F46E5` at ~40% opacity | ~75% of screen width | Medium |
| 3 | Violet-blue `#6D28D9` at ~35% opacity | ~65% of screen width | Fastest |

All colors stay within the app's existing violet/indigo brand palette.

### Ambient animation

Each pool has an independent slow orbit driven by `InfiniteTransition`. Pool positions are computed as:

```
x = homeX + sin(time * freqX + phase) * orbitRadiusX
y = homeY + cos(time * freqY + phase) * orbitRadiusY
```

Frequencies are slightly different per pool (e.g. `0.19`, `0.14`, `0.26` cycles/sec) so the pools never sync, giving organic non-repeating motion. Full cycle is ~15–25 seconds — slow enough to be ambient, visible enough to be interesting.

### Tilt offset

The tilt offset is added on top of the ambient position for all three pools simultaneously, so the whole background shifts as a unit (matching iOS parallax behavior). Each pool has a slightly different tilt multiplier:

| Pool | Tilt multiplier |
|------|----------------|
| 1 | `0.8×` |
| 2 | `1.0×` |
| 3 | `1.2×` |

The difference creates subtle depth parallax between layers.

---

## Sensor Integration

**Sensor:** `TYPE_ROTATION_VECTOR` — fused gyroscope + accelerometer. Stable, drift-free, no manifest permission needed.

**Axes used:**
- Pitch (forward/back tilt) → Y offset
- Roll (left/right tilt) → X offset

**Smoothing:** Raw sensor values (~100Hz, noisy) are fed into two `Animatable<Float>` instances — one per axis — each driven by:
```kotlin
spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.7f)
```
This gives the characteristic iOS "lagging behind" liquid feel.

**Maximum tilt displacement:** ~8–12% of screen dimension. Beyond that it clamps — you don't want the pools to drift completely off-screen.

**Lifecycle:** Registered in `DisposableEffect` — sensor listener starts when the composable enters composition, stops when it leaves. No leaks.

---

## Files Changed

| File | Change |
|------|--------|
| `ui/component/AuroraBackground.kt` | New file — the composable |
| `ui/RecompApp.kt` | Add `AuroraBackground()` inside contentBackdrop box |

No changes to `GlassComponents.kt`, `LiquidComponents.kt`, navigation, data layer, or tests.

---

## What This Unlocks for the Glass

Once `contentBackdrop` records the aurora, every glass surface in the app automatically gets richer:

- `FrostedCard` — blurs deep violet/indigo pools → frosted purple-dark glass
- `TintedCard` — same blur, plus violet tint overlay → jewel-like violet glass
- `LiquidActionButton` / `LiquidStepButton` — small glass pills show concentrated orb color
- `LiquidBottomTabs` — nav bar blurs `navBackdrop` (gradient + content + aurora) → richest blur of all

---

## Non-Goals

- No per-screen background variation
- No touch/tap interaction (gyro only)
- No AGSL shaders (API 33+ only)
- No changes to the glass card parameters (`FrostedCard`, `TintedCard`) — the glass already has the right setup, it just needs better source material
