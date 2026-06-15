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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.zack.recomptracker.ui.liquidglass.LocalBackdrop
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerPill
import com.zack.recomptracker.ui.theme.LocalAppColors

enum class AiBorderMode {
    Preparing,
    Generating,
    Ready,
    Static,
}

/**
 * Liquid-glass AI card: exact nav-bar glass recipe (vibrancy + blur + lens) with an [aiEdgeGlow]
 * halo layer sitting BEHIND the glass. Supports a [collapsed] pill shape (Capsule) in addition to
 * the default rounded-card shape.
 */
@Composable
fun AiInsightCard(
    borderMode: AiBorderMode,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val cornerDp = if (collapsed) CornerPill else CornerCard
    val isDark = LocalAppColors.current.isDark
    val containerColor =
        if (isDark) Color(0xFF121212).copy(alpha = 0.40f) else Color(0xFFFAFAFA).copy(alpha = 0.40f)

    Box(modifier = modifier.fillMaxWidth()) {
        // Halo layer (blurred) sits behind the glass; this Box draws ONLY the glow.
        Box(
            Modifier
                .matchParentSize()
                .aiEdgeGlow(borderMode, cornerDp),
        )
        // Glass layer — exact nav-bar recipe.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(if (collapsed) Capsule() else RoundedCornerShape(cornerDp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { if (collapsed) Capsule() else RoundedRectangle(cornerDp) },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Draws the node's content, then the thin full-spectrum iridescent rim whose hue flows in place —
 * the rim geometry never rotates. Intensity/motion encode [mode]; falls back to a static rim when
 * system animations are off. This has **no backdrop dependency**, so it renders correctly inside a
 * Dialog window (where the liquid-glass backdrop layer is unavailable) — used both by
 * [AiInsightCard] and by the backdrop-free weekly-briefing modal.
 */
@Composable
fun Modifier.aiIridescentRim(mode: AiBorderMode, cornerRadius: Dp = CornerCard): Modifier {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val effectiveMode = if (animationsEnabled) mode else AiBorderMode.Static

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

    return drawWithContent {
        drawContent()
        drawIridescentBorder(
            mode = effectiveMode,
            huePhase = huePhase,
            pulseAlpha = pulseAlpha,
            readyFadeAlpha = readyFadeAlpha,
            cornerPx = cornerRadius.toPx(),
            animationsEnabled = animationsEnabled,
        )
    }
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
private fun PreviewCollapsedPill() {
    AiInsightCard(borderMode = AiBorderMode.Static, collapsed = true, contentPadding = 12.dp) {
        androidx.compose.material3.Text("You're 24g under protein today", color = Color.White)
    }
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
