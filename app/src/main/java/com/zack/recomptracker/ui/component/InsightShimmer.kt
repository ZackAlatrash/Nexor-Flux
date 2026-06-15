package com.zack.recomptracker.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.LocalAppAccent

/** Three colored shimmer lines that read as "the coach is writing", not a gray spinner. */
@Composable
fun InsightShimmerLines(modifier: Modifier = Modifier) {
    val accent = LocalAppAccent.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "shimmerPhase",
    )
    Column(modifier.fillMaxWidth()) {
        ShimmerBar(0.92f, phase, accent.accentLighter)
        ShimmerBar(0.78f, phase, accent.accentLighter, top = 9.dp)
        ShimmerBar(0.55f, phase, accent.accentLighter, top = 9.dp)
    }
}

@Composable
private fun ShimmerBar(
    widthFraction: Float,
    phase: androidx.compose.runtime.State<Float>,
    shine: Color,
    top: Dp = 0.dp,
) {
    Box(
        Modifier
            .padding(top = top)
            .fillMaxWidth(widthFraction)
            .height(13.dp)
            .clip(RoundedCornerShape(6.dp))
            .drawBehind {
                val w = size.width
                val travel = w * 2f
                val start = -w + phase.value * travel
                val brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.05f),
                    0.5f to shine.copy(alpha = 0.30f),
                    1f to Color.White.copy(alpha = 0.05f),
                    start = Offset(start, 0f),
                    end = Offset(start + w, 0f),
                )
                drawRect(brush)
            },
    )
}
