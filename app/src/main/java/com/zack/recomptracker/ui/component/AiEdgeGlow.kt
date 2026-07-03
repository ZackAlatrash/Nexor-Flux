package com.zack.recomptracker.ui.component

import android.graphics.BlurMaskFilter
import android.graphics.SweepGradient
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple-Intelligence-style INNER edge glow: a soft, thin iridescent bloom hugging the INSIDE of the
 * card's rounded edge. It is drawn on top of the glass surface and, because the host clips to the
 * card shape before this runs, the bloom is clipped inward — so it reads as light glowing inside
 * the glass, not a rim painted around the outside. The hue breathes in place (geometry never
 * rotates); brighter and a touch faster while Generating.
 *
 * Apply to the glass node itself, AFTER its `.clip(shape)` + surface draw. Softness uses a
 * `BlurMaskFilter` (API 28+); below that it degrades gracefully to a thin crisp stroke. Honours the
 * system reduced-motion setting.
 */
@Composable
fun Modifier.aiEdgeGlow(mode: AiBorderMode, cornerRadius: Dp): Modifier {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val effectiveMode = if (animationsEnabled) mode else AiBorderMode.Static

    // Only the loading-type states cycle the hue. At rest (Ready / Static) the glow is drawn at a
    // FIXED hue so the composable neither runs an infinite animation nor invalidates every frame —
    // a resting card used to re-record 3 IntArray + 3 SweepGradient forever. The infinite
    // transition exists ONLY while active; when it flips to a resting mode the branch drops out
    // and no animation clock remains subscribed.
    val active = animationsEnabled &&
        (effectiveMode == AiBorderMode.Generating || effectiveMode == AiBorderMode.Preparing)
    val generating = effectiveMode == AiBorderMode.Generating

    // The animated value is deliberately NOT read here: reading it in composition would recompose
    // the caller on every animation frame while active. The State is handed to the draw lambda,
    // which reads it in the draw phase so hue changes only invalidate drawing.
    val hueState: State<Float> = if (active) {
        val transition = rememberInfiniteTransition(label = "aiEdgeGlow")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
            label = "huePhase",
        )
    } else {
        // The resting hue == the sweep's start value, so freezing here matches the palette's
        // canonical (accent-led) colours exactly — the glow just stops flowing.
        remember { mutableFloatStateOf(0f) }
    }
    val hueRate = if (generating) 1.6f else 1f

    val targetAlpha = when (effectiveMode) {
        AiBorderMode.Generating -> 0.95f
        AiBorderMode.Preparing -> 0.70f
        AiBorderMode.Ready -> 0.80f
        AiBorderMode.Static -> 0.70f
    }
    // One-shot alpha settle for state TRANSITIONS (in/out of a mode). It converges and then stops,
    // so it does not keep the card invalidating at rest.
    val glowAlphaState = animateFloatAsState(targetAlpha, tween(600), label = "glowAlpha")

    val density = LocalDensity.current
    val strokePx = with(density) { GlowStroke.toPx() }
    val blurPx = with(density) { GlowBlur.toPx() }
    val insetPx = with(density) { GlowInset.toPx() }
    val spillWidthPx = with(density) { GlowSpillWidth.toPx() }
    val spillBlurPx = with(density) { GlowSpillBlur.toPx() }
    val spillInsetPx = with(density) { GlowSpillInset.toPx() }
    val cornerPx = with(density) { cornerRadius.toPx() }
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
        }
    }
    val blurFilter = remember(blurPx) { BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL) }
    val spillBlurFilter = remember(spillBlurPx) { BlurMaskFilter(spillBlurPx, BlurMaskFilter.Blur.NORMAL) }
    // Reused colour-stop buffers: the hue/alpha are written in place each draw rather than
    // allocating three fresh IntArrays per frame (drawn result is identical).
    val stopCount = IridescentStops.size
    val spill = remember(stopCount) { IntArray(stopCount) }
    val bloom = remember(stopCount) { IntArray(stopCount) }
    val core = remember(stopCount) { IntArray(stopCount) }

    return this.drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawWithContent
        val shift = hueState.value * hueRate
        val a = glowAlphaState.value
        for (i in 0 until stopCount) {
            val shifted = IridescentStops[i].hueShifted(shift)
            spill[i] = shifted.copy(alpha = a * 0.16f).toArgb()
            bloom[i] = shifted.copy(alpha = a * 0.75f).toArgb()
            core[i] = shifted.copy(alpha = a).toArgb()
        }
        val r = (cornerPx - insetPx).coerceAtLeast(0f)
        val rSpill = (cornerPx - spillInsetPx).coerceAtLeast(0f)
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            // 0) Light spill: a wide, very soft wash that bleeds the glow's colour onto the glass
            //    face near the edge — like glass catching light. Faint, so it reads as a reflection.
            paint.shader = SweepGradient(w / 2f, h / 2f, spill, null)
            paint.maskFilter = spillBlurFilter
            paint.strokeWidth = spillWidthPx
            c.drawRoundRect(spillInsetPx, spillInsetPx, w - spillInsetPx, h - spillInsetPx, rSpill, rSpill, paint)
            // 1) Soft blurred bloom hugging the inner edge.
            paint.shader = SweepGradient(w / 2f, h / 2f, bloom, null)
            paint.maskFilter = blurFilter
            paint.strokeWidth = strokePx * 2f
            c.drawRoundRect(insetPx, insetPx, w - insetPx, h - insetPx, r, r, paint)
            // 2) Crisp thin core line so the glow reads even when blur is unavailable.
            paint.shader = SweepGradient(w / 2f, h / 2f, core, null)
            paint.maskFilter = null
            paint.strokeWidth = strokePx
            c.drawRoundRect(insetPx, insetPx, w - insetPx, h - insetPx, r, r, paint)
        }
    }
}

private val GlowStroke = 1.dp     // thin
private val GlowBlur = 4.dp       // soft bloom
private val GlowInset = 2.dp      // sits just inside the edge
private val GlowSpillWidth = 16.dp  // wide light wash onto the glass face
private val GlowSpillBlur = 12.dp   // very soft
private val GlowSpillInset = 8.dp   // spills inward from the edge
