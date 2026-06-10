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
                    drawRect(Color.White.copy(alpha = 0.06f))
                    drawRect(accent.accent.copy(alpha = 0.05f))
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
