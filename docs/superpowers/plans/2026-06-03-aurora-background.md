# Aurora Background Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an animated, gyroscope-reactive visionOS-style aurora background to the app so that liquid glass cards and buttons have vivid, living color to blur through.

**Architecture:** One new composable `AuroraBackground` draws three overlapping radial gradient "pools" (deep violet, indigo, violet-blue) on a `Canvas`. The pools orbit slowly using `withFrameNanos` delta-time animation, and shift with device tilt via `SensorManager + animateFloatAsState`. The composable is placed as a child of the `contentBackdrop` recording box in `RecompApp.kt` so all glass surfaces automatically blur over the moving color.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.foundation.Canvas`, `withFrameNanos`, `animateFloatAsState`, `android.hardware.SensorManager` (`TYPE_ROTATION_VECTOR`), `DisposableEffect`, `LocalLifecycleOwner`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/com/zack/recomptracker/ui/component/AuroraBackground.kt` | **Create** | Animated aurora composable — pool data, canvas drawing, time animation, sensor tilt |
| `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt` | **Modify** | Add `AuroraBackground()` inside the `contentBackdrop` box |

---

## Task 1: Create `AuroraBackground.kt` with animated color pools

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/AuroraBackground.kt`

- [ ] **Step 1: Create the file with pool data and animated Canvas drawing**

Create `app/src/main/java/com/zack/recomptracker/ui/component/AuroraBackground.kt` with this exact content:

```kotlin
package com.zack.recomptracker.ui.component

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.withFrameNanos
import kotlin.math.cos
import kotlin.math.sin

private data class AuroraPool(
    val homeX: Float,          // resting X as fraction of screen width  (0..1)
    val homeY: Float,          // resting Y as fraction of screen height (0..1)
    val orbitRadiusX: Float,   // orbit amplitude as fraction of screen width
    val orbitRadiusY: Float,   // orbit amplitude as fraction of screen height
    val freqX: Float,          // horizontal orbit frequency (cycles/sec)
    val freqY: Float,          // vertical orbit frequency  (cycles/sec)
    val phase: Float,          // phase offset in radians — keeps pools out of sync
    val radiusFraction: Float, // gradient radius as fraction of screen width
    val color: Color,
    val tiltMultiplier: Float, // parallax weight — different depths appear to move differently
)

private val POOLS = listOf(
    AuroraPool(0.30f, 0.25f, 0.18f, 0.14f, 0.19f, 0.23f, 0.00f, 0.85f, Color(0x737C3AED), 0.8f),
    AuroraPool(0.70f, 0.70f, 0.15f, 0.18f, 0.14f, 0.17f, 2.09f, 0.75f, Color(0x664F46E5), 1.0f),
    AuroraPool(0.50f, 0.50f, 0.14f, 0.12f, 0.26f, 0.21f, 4.19f, 0.65f, Color(0x596D28D9), 1.2f),
)

// How much the raw rotation-vector reading maps to a pool offset fraction
private const val TILT_SCALE = 0.30f
// Maximum tilt displacement in either direction (fraction of screen dimension)
private const val MAX_TILT = 0.10f

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    // ── Time accumulation (delta-time, wraps at 1000 to preserve float precision) ──
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = (nanos - lastNanos) / 1_000_000_000f
                    t = (t + dt) % 1000f
                }
                lastNanos = nanos
            }
        }
    }

    // ── Gyroscope tilt (raw sensor → smoothed spring) ──
    var rawTiltX by remember { mutableFloatStateOf(0f) }
    var rawTiltY by remember { mutableFloatStateOf(0f) }
    val tiltX by animateFloatAsState(
        targetValue = rawTiltX,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.7f),
        label = "tiltX",
    )
    val tiltY by animateFloatAsState(
        targetValue = rawTiltY,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.7f),
        label = "tiltY",
    )

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // event.values[0] = sin(θ/2)*x  →  maps to left/right roll
                // event.values[1] = sin(θ/2)*y  →  maps to forward/back pitch
                rawTiltX = (event.values[0] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
                rawTiltY = (event.values[1] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                Lifecycle.Event.ON_PAUSE  -> sm?.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sm?.unregisterListener(listener)
        }
    }

    // ── Canvas drawing ──
    Canvas(modifier = modifier.fillMaxSize()) {
        for (pool in POOLS) {
            val cx = (pool.homeX
                    + sin(t * pool.freqX + pool.phase) * pool.orbitRadiusX
                    + tiltX * pool.tiltMultiplier) * size.width
            val cy = (pool.homeY
                    + cos(t * pool.freqY + pool.phase) * pool.orbitRadiusY
                    + tiltY * pool.tiltMultiplier) * size.height
            val radius = pool.radiusFraction * size.width
            val center = Offset(cx, cy)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pool.color, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
                blendMode = BlendMode.Screen,
            )
        }
    }
}
```

- [ ] **Step 2: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected output: `BUILD SUCCESSFUL` with no errors. The only acceptable warning is the pre-existing `ExperimentalGetImage` camera warning.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/AuroraBackground.kt
git commit -m "feat(aurora): add animated gyroscope-reactive aurora background composable"
```

---

## Task 2: Wire `AuroraBackground` into `RecompApp.kt`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`

The `contentBackdrop` box is currently an empty Box. It needs one child — `AuroraBackground()` — so the aurora is recorded by the backdrop and all glass composables blur over it.

- [ ] **Step 1: Add the import to `RecompApp.kt`**

In `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`, add this import after the existing component imports:

```kotlin
import com.zack.recomptracker.ui.component.AuroraBackground
```

- [ ] **Step 2: Add `AuroraBackground()` inside the contentBackdrop box**

Find this block (around line 120–133):

```kotlin
                    // Gradient captured into contentBackdrop (renders before Scaffold below).
                    Box(
                        modifier = Modifier
                            .layerBackdrop(contentBackdrop)
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f    to BgDeep,
                                        0.45f to BgMid,
                                        1f    to BgDark,
                                    ),
                                ),
                            ),
                    )
```

Replace it with:

```kotlin
                    // Gradient + aurora captured into contentBackdrop.
                    // Glass composables inside AppNavGraph read this backdrop and blur over it.
                    Box(
                        modifier = Modifier
                            .layerBackdrop(contentBackdrop)
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f    to BgDeep,
                                        0.45f to BgMid,
                                        1f    to BgDark,
                                    ),
                                ),
                            ),
                    ) {
                        AuroraBackground()
                    }
```

- [ ] **Step 3: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run unit tests to confirm nothing is broken**

```bash
./gradlew testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` — all existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt
git commit -m "feat(aurora): wire AuroraBackground into contentBackdrop recording zone"
```

---

## Task 3: Manual device verification

No automated tests cover Canvas rendering or sensor input. Verify on device or emulator:

- [ ] **Step 1: Build and install debug APK**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 2: Verify aurora is visible**

Open the app. The background should show soft violet/indigo glow pools drifting slowly. The pools should never fully sync into the same position (they have different frequencies).

- [ ] **Step 3: Verify glass cards reflect the aurora**

Navigate to the Home screen. The `FrostedCard` and `TintedCard` components should now show the aurora colors blurred through them — a dark frosted glass that picks up the violet/indigo glow rather than looking like a flat opaque rectangle.

- [ ] **Step 4: Verify gyroscope response**

Tilt the device left/right and forward/back. The aurora pools should shift with a springy follow — they lag behind the tilt slightly and ease to rest when still. This is the iOS parallax feel.

**If gyroscope is unavailable** (emulator without sensor simulation): the app should still work normally — `sm?.getDefaultSensor(TYPE_ROTATION_VECTOR)` returns null, `registerListener` is never called, and `rawTiltX`/`rawTiltY` stay at `0f`. The ambient animation still runs.

- [ ] **Step 5: Tune if needed**

If the aurora pools are too bright, reduce the alpha byte in the `Color(0xAARRGGBB)` values in `POOLS` (first two hex digits after `0x`):
- Pool 1: `0x73` (45%) → try `0x59` (35%)
- Pool 2: `0x66` (40%) → try `0x4C` (30%)
- Pool 3: `0x59` (35%) → try `0x40` (25%)

If the pools move too fast, reduce `freqX`/`freqY` values in `POOLS` (e.g. `0.19f → 0.12f`).

If the tilt effect is too strong, reduce `TILT_SCALE` (e.g. `0.30f → 0.18f`).

- [ ] **Step 6: Final commit after any tuning**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/AuroraBackground.kt
git commit -m "fix(aurora): tune pool opacity and tilt sensitivity after device testing"
```

*(Skip this step if no tuning was needed.)*
