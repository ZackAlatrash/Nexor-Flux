package com.zack.recomptracker.ui.component

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple-Intelligence-style edge glow: a soft full-spectrum halo hugging the card perimeter while
 * the glass face stays clean. The hue breathes in place (geometry never rotates). Intensity and
 * speed encode [mode] — brighter and faster while Generating. Falls back to a static glow when
 * system animations are off, and to an un-blurred soft glow below API 31.
 *
 * Apply to a layer placed BEHIND the glass surface so the blurred halo spills outward and the
 * glass covers the centre.
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

    val transition = rememberInfiniteTransition(label = "aiEdgeGlow")
    val cycleMs = if (effectiveMode == AiBorderMode.Generating) 7000 else 16000
    val huePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(cycleMs, easing = LinearEasing)),
        label = "huePhase",
    )

    val targetAlpha = when (effectiveMode) {
        AiBorderMode.Generating -> 0.72f
        AiBorderMode.Preparing -> 0.55f
        AiBorderMode.Ready -> 0.50f
        AiBorderMode.Static -> 0.45f
    }
    val glowAlpha by animateFloatAsState(targetAlpha, tween(600), label = "glowAlpha")

    val shift = if (animationsEnabled) huePhase else 0f
    val colors = IridescentStops.map { it.hueShifted(shift) }
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val haloStroke = 3.dp
    return this
        .drawBehind {
            val inset = haloStroke.toPx() / 2f
            val corner = CornerRadius(cornerRadius.toPx() + inset)
            drawRoundRect(
                brush = Brush.sweepGradient(colors, center = Offset(size.width / 2f, size.height / 2f)),
                topLeft = Offset(-inset, -inset),
                size = Size(size.width + inset * 2f, size.height + inset * 2f),
                cornerRadius = corner,
                style = Stroke(width = haloStroke.toPx() * 2f),
                alpha = glowAlpha,
            )
        }
        .then(if (supportsBlur) Modifier.blur(11.dp) else Modifier)
}
