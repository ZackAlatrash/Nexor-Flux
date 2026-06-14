package com.zack.recomptracker.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun CalorieProgressBar(
    progress: Float,
    zoneLowFrac: Float,
    zoneHighFrac: Float,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    // Hoist chrome color out of DrawScope
    val trackColor = appColors.cardBorder
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "calorieFill",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = h / 2f

        drawRoundRect(color = trackColor, cornerRadius = CornerRadius(r))

        val zoneLeft = (zoneLowFrac * w).coerceIn(0f, w)
        val zoneRight = (zoneHighFrac * w).coerceIn(0f, w)
        if (zoneRight > zoneLeft) {
            drawRect(
                color = accent.accent.copy(alpha = 0.13f),
                topLeft = Offset(zoneLeft, 0f),
                size = Size(zoneRight - zoneLeft, h),
            )
            clipRect(left = zoneLeft, top = 0f, right = zoneRight, bottom = h) {
                val stripeW = 4.dp.toPx()
                val step = 7.dp.toPx()
                var x = zoneLeft - h
                while (x < zoneRight + h) {
                    drawLine(
                        color = accent.accent.copy(alpha = 0.38f),
                        start = Offset(x, 0f),
                        end = Offset(x + h, h),
                        strokeWidth = stripeW,
                    )
                    x += step
                }
            }
        }

        val fillX = animatedProgress * w
        if (fillX > 0.5f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(accent.accent, accent.accentLight),
                    startX = 0f,
                    endX = fillX.coerceAtLeast(r * 2),
                ),
                size = Size(fillX.coerceAtLeast(r * 2), h),
                cornerRadius = CornerRadius(r),
            )
        }
    }
}
