# AI Card Liquid-Glass Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the shared `AiInsightCard` to an iOS-26 liquid-glass body with a thin iridescent edge whose hue flows in place (no spatial rotation), so every AI surface inherits the new look.

**Architecture:** Add a pure `hueRotatedRgb`/`Color.hueShifted` helper + a fixed `IridescentStops` palette (Task 1, unit-tested). Rewrite `AiInsightCard`'s body (kyant `drawBackdrop` with `lens(chromaticAberration=true)` + `highlight` + `shadow` + a lighter frost surface) and its border (an iridescent sweep rim, hue-shifted per animated phase, opacity per `AiBorderMode`) — Task 2. The `AiBorderMode` enum, `GeneratedInsightCard`, and all consumers are untouched.

**Tech Stack:** Jetpack Compose, `com.kyant.backdrop` (vibrancy/blur/lens/highlight/shadow), JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-10-ai-card-liquid-glass-redesign-design.md`

**Verification note:** The only non-visual logic (`hueRotatedRgb`) is TDD unit-tested. The card restyle is verified by `:app:compileDebugKotlin` + the existing `@Preview`s + the full `:app:testDebugUnitTest` staying green. The visual result is confirmed on-device (manual checkpoint).

---

## File Structure

**Create:**
- `app/src/main/java/com/zack/recomptracker/ui/component/IridescentPalette.kt` — pure hue-rotation helper + `Color.hueShifted` + `IridescentStops`
- `app/src/test/java/com/zack/recomptracker/ui/component/IridescentPaletteTest.kt`

**Modify:**
- `app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt` — full restyle (body + iridescent rim)

---

## Task 1: Iridescent palette + pure hue-rotation helper

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/IridescentPalette.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/component/IridescentPaletteTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class IridescentPaletteTest {

    private fun assertRgb(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected[0], actual[0], 0.01f)
        assertEquals(expected[1], actual[1], 0.01f)
        assertEquals(expected[2], actual[2], 0.01f)
    }

    @Test
    fun `red rotated 120 degrees is green`() {
        assertRgb(floatArrayOf(0f, 1f, 0f), hueRotatedRgb(1f, 0f, 0f, 120f))
    }

    @Test
    fun `red rotated 240 degrees is blue`() {
        assertRgb(floatArrayOf(0f, 0f, 1f), hueRotatedRgb(1f, 0f, 0f, 240f))
    }

    @Test
    fun `red rotated 360 degrees is identity`() {
        assertRgb(floatArrayOf(1f, 0f, 0f), hueRotatedRgb(1f, 0f, 0f, 360f))
    }

    @Test
    fun `gray is unchanged by rotation`() {
        assertRgb(floatArrayOf(0.5f, 0.5f, 0.5f), hueRotatedRgb(0.5f, 0.5f, 0.5f, 90f))
    }

    @Test
    fun `rotation wraps past 360`() {
        assertRgb(hueRotatedRgb(1f, 0f, 0f, 120f), hueRotatedRgb(1f, 0f, 0f, 480f))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.component.IridescentPaletteTest"`
Expected: FAIL — `hueRotatedRgb` unresolved reference.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.zack.recomptracker.ui.component

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Base full-spectrum iridescent stops for the AI card rim. First == last so the sweep wraps
 * seamlessly. The card animates these by hue rotation (see [hueShifted]) — the geometry never
 * rotates, so the colour flows in place rather than spinning.
 */
val IridescentStops: List<Color> = listOf(
    Color(0xFF8B5CF6),
    Color(0xFFFF6EC7),
    Color(0xFF6EC1FF),
    Color(0xFF6EFFD8),
    Color(0xFFFFB86C),
    Color(0xFFFF6EC7),
    Color(0xFF8B5CF6),
)

/**
 * Pure HSV hue rotation on 0..1 sRGB components. [degrees] wraps mod 360. Returns [r, g, b].
 * Saturationless inputs (grays) are returned unchanged.
 */
fun hueRotatedRgb(r: Float, g: Float, b: Float, degrees: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val v = max
    val s = if (max <= 0f) 0f else delta / max
    var h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    if (h < 0f) h += 360f
    h = (h + degrees) % 360f
    if (h < 0f) h += 360f
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return floatArrayOf(r1 + m, g1 + m, b1 + m)
}

/** Returns this colour with its hue rotated by [degrees], preserving alpha. */
fun Color.hueShifted(degrees: Float): Color {
    val out = hueRotatedRgb(red, green, blue, degrees)
    return Color(out[0], out[1], out[2], alpha)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.component.IridescentPaletteTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/IridescentPalette.kt app/src/test/java/com/zack/recomptracker/ui/component/IridescentPaletteTest.kt
git commit -m "feat(ui): add iridescent palette and pure hue-rotation helper"
```

---

## Task 2: Restyle AiInsightCard (liquid-glass body + iridescent rim)

**Files:**
- Modify (full replace): `app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt`

This is a cohesive visual rewrite of one file. No unit test (pure visual). Verified by compile + the `@Preview`s + full suite green. `Color.hueShifted` / `IridescentStops` come from Task 1 (same package — no import).

- [ ] **Step 1: Replace the entire contents of `AiInsightCard.kt`**

```kotlin
package com.zack.recomptracker.ui.component

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import com.zack.recomptracker.ui.liquidglass.LocalBackdrop
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.LocalAppAccent

enum class AiBorderMode {
    Preparing,
    Generating,
    Ready,
    Static,
}

/**
 * Liquid-glass AI card: a translucent frosted body (vibrancy + blur + chromatic lens + specular
 * highlight) with a thin full-spectrum iridescent rim whose hue flows in place — the rim geometry
 * never rotates. Rim intensity/motion is driven by [borderMode]. When system animations are off,
 * it falls back to a static iridescent rim.
 */
@Composable
fun AiInsightCard(
    borderMode: AiBorderMode,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val effectiveMode = if (animationsEnabled) borderMode else AiBorderMode.Static

    val infiniteTransition = rememberInfiniteTransition(label = "aiIridescent")

    // Hue flows in place over a slow cycle — NOT a spatial rotation (no "helicopter").
    val huePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 17000, easing = LinearEasing)),
        label = "huePhase",
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    var readyComplete by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveMode) {
        if (effectiveMode != AiBorderMode.Ready) readyComplete = false
    }
    val readyFadeAlpha by animateFloatAsState(
        targetValue = if (effectiveMode == AiBorderMode.Ready && !readyComplete) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "readyFade",
        finishedListener = { if (effectiveMode == AiBorderMode.Ready) readyComplete = true },
    )

    val accent = LocalAppAccent.current
    val backdrop = LocalBackdrop.current
    val cornerDp = CornerCard

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerDp))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerDp) },
                effects = {
                    vibrancy()
                    blur(22f.dp.toPx())
                    lens(12f.dp.toPx(), 18f.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.35f)) },
                onDrawSurface = {
                    // Lighter translucent frost so the colourful backdrop bleeds through (iOS-26).
                    drawRect(Color.White.copy(alpha = 0.06f))
                    // Faint accent harmony.
                    drawRect(accent.accent.copy(alpha = 0.05f))
                    // Specular top edge.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.28f),
                            0.12f to Color.Transparent,
                        ),
                    )
                },
            )
            .drawWithContent {
                drawContent()
                drawIridescentBorder(
                    mode = effectiveMode,
                    huePhase = huePhase,
                    pulseAlpha = pulseAlpha,
                    readyFadeAlpha = readyFadeAlpha,
                    cornerPx = cornerDp.toPx(),
                    animationsEnabled = animationsEnabled,
                )
            }
            .padding(16.dp),
        content = content,
    )
}

/**
 * Draws the thin full-spectrum iridescent rim. Geometry is fixed (a sweep gradient at the centre);
 * only the stop hues shift by [huePhase], so the colour flows in place. Opacity encodes the mode.
 */
private fun DrawScope.drawIridescentBorder(
    mode: AiBorderMode,
    huePhase: Float,
    pulseAlpha: Float,
    readyFadeAlpha: Float,
    cornerPx: Float,
    animationsEnabled: Boolean,
) {
    val corner = CornerRadius(cornerPx)
    val baseAlpha = when (mode) {
        AiBorderMode.Static -> 0.35f
        AiBorderMode.Preparing -> if (animationsEnabled) pulseAlpha else 0.45f
        AiBorderMode.Generating -> 0.70f
        AiBorderMode.Ready -> 0.50f + 0.20f * readyFadeAlpha
    }
    val rawShift = if (animationsEnabled) huePhase else 0f
    // Generating flows a touch faster by advancing the hue further per frame.
    val shift = if (mode == AiBorderMode.Generating) rawShift * 1.6f else rawShift
    val colors = IridescentStops.map { it.hueShifted(shift) }
    drawRoundRect(
        brush = Brush.sweepGradient(colors, center = Offset(size.width / 2f, size.height / 2f)),
        cornerRadius = corner,
        style = Stroke(width = 1.3.dp.toPx()),
        alpha = baseAlpha,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewStatic() {
    AiInsightCard(borderMode = AiBorderMode.Static) {
        androidx.compose.material3.Text("Static iridescent rim", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewPreparing() {
    AiInsightCard(borderMode = AiBorderMode.Preparing) {
        androidx.compose.material3.Text("Preparing model…", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewGenerating() {
    AiInsightCard(borderMode = AiBorderMode.Generating) {
        androidx.compose.material3.Text("Your weight has been trending down…", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewReady() {
    AiInsightCard(borderMode = AiBorderMode.Ready) {
        androidx.compose.material3.Text("Weight, waist, and performance are all stable.", color = Color.White)
    }
}
```

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `lens` / `Highlight` / `Shadow` fail to resolve, confirm the imports match those used in `app/src/main/java/com/zack/recomptracker/ui/liquidglass/LiquidComponents.kt` (`com.kyant.backdrop.effects.lens`, `com.kyant.backdrop.highlight.Highlight`, `com.kyant.backdrop.shadow.Shadow`) and that the `drawBackdrop` `highlight`/`shadow` lambdas match its `LiquidButton`/`LiquidSlider` usage. If an API genuinely differs, report BLOCKED with the compiler error rather than guessing.

- [ ] **Step 3: Run the full unit-test suite (regression)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all existing tests plus Task 1's `IridescentPaletteTest` pass. (No behavioral change; `AiBorderMode` and consumers are untouched.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt
git commit -m "feat(ui): restyle AiInsightCard to liquid glass with iridescent edge"
```

- [ ] **Step 5: DEVICE CHECKPOINT (manual, by the human)**

Build and run. With AI enabled + model ready, view any AI card (dashboard "Why this verdict", or Progress/Recovery once they generate). Confirm: translucent frosted body, soft specular top edge, thin iridescent rim that *flows* (no spinning), brighter while Generating, settling on Ready. Toggle "Remove animations" (or set animator duration scale to 0) and confirm the rim goes static. Tune constants in `AiInsightCard.kt` / `IridescentPalette.kt` if the intensity or speed needs adjusting (rim alpha values in `drawIridescentBorder`, the `17000`ms hue cycle, the `0.06`/`0.28` frost/specular alphas, or the `IridescentStops` palette).

---

## Self-Review (completed during planning)

**Spec coverage:**
- §3.1 liquid-glass body (frost + specular + chromatic edge) → Task 2 `drawBackdrop` (`vibrancy`+`blur`+`lens(chromaticAberration=true)`+`highlight`+`shadow`+frost/specular `onDrawSurface`). ✓
- §3.2 iridescent rim, hue flows in place not rotation → Task 2 `drawIridescentBorder` (fixed sweep geometry; `hueShifted(huePhase)`) + Task 1 palette/helper. ✓
- §3.3 state → motion mapping (Static 0.35 / Preparing breathe / Generating 0.70 + faster / Ready settle) → Task 2 `baseAlpha` when-block + the `*1.6f` Generating shift + `readyFadeAlpha`. ✓
- §3.4 accent harmony (badge/text/specular accent, rim full-spectrum) → faint `accent.accent` tint + full-spectrum `IridescentStops`; badge/text live in consumers, unchanged. ✓
- §3.5 reduce-motion → static rim → `animationsEnabled` gate (forces `Static`, `shift=0`). ✓
- §4 reuse (kyant + existing border slot + pure hue helper) → Task 2 reuses `drawBackdrop`/`drawWithContent`; Task 1 is the pure helper. ✓
- §5 verification (previews + hueRotatedRgb test + on-device) → Task 1 test, Task 2 previews + checkpoint. ✓
- §6 out of scope (no `AiBorderMode`/consumer/coordinator change) → enum + signature unchanged; only internals replaced. ✓

**Placeholder scan:** none — full file content and the pure helper are given verbatim; the checkpoint names exact constants to tune.

**Type consistency:** `hueRotatedRgb(r,g,b,degrees): FloatArray` and `Color.hueShifted(degrees): Color` defined in Task 1, used in Task 2's `drawIridescentBorder`. `IridescentStops: List<Color>` defined Task 1, mapped in Task 2. `AiBorderMode` members (`Preparing/Generating/Ready/Static`) consistent across the enum and the `when`. `drawIridescentBorder(mode,huePhase,pulseAlpha,readyFadeAlpha,cornerPx,animationsEnabled)` signature matches its single call site. kyant imports match `LiquidComponents.kt`. ✓
