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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.zack.recomptracker.ui.liquidglass.LocalBackdrop
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.TintedBorder
import com.zack.recomptracker.ui.theme.TintedSurface
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet500

enum class AiBorderMode {
    Preparing,
    Generating,
    Ready,
    Static,
}

@Composable
fun AiInsightCard(
    borderMode: AiBorderMode,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val effectiveMode = if (animationsEnabled) borderMode else AiBorderMode.Static

    val infiniteTransition = rememberInfiniteTransition(label = "aiComet")

    val cometPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
        ),
        label = "cometPhase",
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    var readyComplete by remember { mutableStateOf(false) }
    if (effectiveMode != AiBorderMode.Ready) readyComplete = false

    val readyFadeAlpha by animateFloatAsState(
        targetValue = if (effectiveMode == AiBorderMode.Ready && !readyComplete) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "readyFade",
        finishedListener = { if (effectiveMode == AiBorderMode.Ready) readyComplete = true },
    )

    val backdrop = LocalBackdrop.current
    var cardWidth by remember { mutableIntStateOf(0) }
    val shimmerBrush = remember(cardWidth) {
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, TintedBorder, TintedBorder, Color.Transparent),
            startX = cardWidth * 0.10f,
            endX = cardWidth * 0.90f,
        )
    }
    val cornerDp = CornerCard

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerDp))
            .onSizeChanged { cardWidth = it.width }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerDp) },
                effects = {
                    vibrancy()
                    blur(20f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(TintedSurface)
                    val shimmerY = 1.dp.toPx() / 2f
                    drawLine(
                        brush = shimmerBrush,
                        start = Offset(0f, shimmerY),
                        end = Offset(size.width, shimmerY),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            )
            .drawWithContent {
                drawContent()
                drawAnimatedBorder(
                    mode = effectiveMode,
                    cometPhase = cometPhase,
                    pulseAlpha = pulseAlpha,
                    readyFadeAlpha = readyFadeAlpha,
                    cornerPx = cornerDp.value * density,
                )
            }
            .padding(16.dp),
        content = content,
    )
}

private fun DrawScope.drawAnimatedBorder(
    mode: AiBorderMode,
    cometPhase: Float,
    pulseAlpha: Float,
    readyFadeAlpha: Float,
    cornerPx: Float,
) {
    val strokeWidth = 1.5.dp.toPx()
    val corner = CornerRadius(cornerPx)

    when (mode) {
        AiBorderMode.Static -> {
            drawRoundRect(
                color = TintedBorder,
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        AiBorderMode.Preparing -> {
            drawRoundRect(
                color = Violet300.copy(alpha = pulseAlpha),
                cornerRadius = corner,
                style = Stroke(width = strokeWidth),
            )
        }

        AiBorderMode.Generating, AiBorderMode.Ready -> {
            drawRoundRect(
                color = TintedBorder,
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
            val alpha = if (mode == AiBorderMode.Ready) readyFadeAlpha else 1f
            rotate(
                degrees = cometPhase * 360f,
                pivot = Offset(size.width / 2f, size.height / 2f),
            ) {
                drawRoundRect(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.06f to Color(0x70FFFFFF),
                            0.11f to Violet300,
                            0.19f to Violet500,
                            0.23f to Color.Transparent,
                            1.00f to Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                    ),
                    cornerRadius = corner,
                    style = Stroke(width = strokeWidth),
                    alpha = alpha,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewStatic() {
    AiInsightCard(borderMode = AiBorderMode.Static) {
        androidx.compose.material3.Text("Static border", color = Color.White)
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
