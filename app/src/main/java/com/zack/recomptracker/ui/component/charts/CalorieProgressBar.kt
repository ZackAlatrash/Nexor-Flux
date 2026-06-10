package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.LocalAppAccent

@Composable
fun CalorieProgressBar(
    progress: Float,
    zoneLowFrac: Float,
    zoneHighFrac: Float,
    modifier: Modifier = Modifier,
    /**
     * Projected fill fraction including planned (not-yet-eaten) calories — i.e.
     * (eaten + planned) / scaleMax. When greater than [progress], a translucent "ghost"
     * segment extends the solid eaten fill out to here. Defaults to 0 (no projection).
     */
    plannedProgress: Float = 0f,
) {
    val accent = LocalAppAccent.current
    val animatedProgress by animateFloatAsState(
        targetValue   = progress.coerceIn(0f, 1f),
        animationSpec = ChartDefaults.AnimSpec.progressBar,
        label         = "calorieFill",
    )
    val animatedPlanned by animateFloatAsState(
        targetValue   = plannedProgress.coerceIn(0f, 1f),
        animationSpec = ChartDefaults.AnimSpec.progressBar,
        label         = "caloriePlannedFill",
    )
    // Mutable cache for stripe X positions — rebuilt only when zone bounds or canvas size change
    val stripeCache = remember {
        object {
            var lastKey: Long = Long.MIN_VALUE
            var positions: FloatArray = FloatArray(0)
        }
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = h / 2f

        drawRoundRect(color = Color(0x12FFFFFF), cornerRadius = CornerRadius(r))

        val zoneLeft  = (zoneLowFrac  * w).coerceIn(0f, w)
        val zoneRight = (zoneHighFrac * w).coerceIn(0f, w)
        if (zoneRight > zoneLeft) {
            drawRect(
                color   = accent.accent.copy(alpha = 0.13f),
                topLeft = Offset(zoneLeft, 0f),
                size    = Size(zoneRight - zoneLeft, h),
            )
            clipRect(left = zoneLeft, top = 0f, right = zoneRight, bottom = h) {
                val stripeW = 4.dp.toPx()
                val step    = 7.dp.toPx()
                // Rebuild stripe positions only when zone bounds or height change
                val cacheKey = (zoneLeft.toBits().toLong() shl 32) xor zoneRight.toBits().toLong() xor h.toBits().toLong()
                if (stripeCache.lastKey != cacheKey) {
                    stripeCache.lastKey = cacheKey
                    val positions = mutableListOf<Float>()
                    var x = zoneLeft - h
                    while (x < zoneRight + h) { positions.add(x); x += step }
                    stripeCache.positions = positions.toFloatArray()
                }
                stripeCache.positions.forEach { x ->
                    drawLine(
                        color       = accent.accent.copy(alpha = 0.38f),
                        start       = Offset(x, 0f),
                        end         = Offset(x + h, h),
                        strokeWidth = stripeW,
                    )
                }
            }
        }

        val fillX = animatedProgress * w

        // Ghost projection — planned calories shown as a translucent extension of the eaten
        // fill. Drawn first so the solid fill overlays its lower portion, leaving only the
        // planned remainder visible as a faint segment past the solid edge.
        val plannedX = animatedPlanned * w
        if (plannedX > fillX + 0.5f) {
            drawRoundRect(
                color        = accent.accentLight.copy(alpha = 0.22f),
                size         = Size(plannedX.coerceAtLeast(r * 2), h),
                cornerRadius = CornerRadius(r),
            )
        }

        if (fillX > 0.5f) {
            drawRoundRect(
                brush        = Brush.horizontalGradient(
                    colors = listOf(accent.accent, accent.accentLight),
                    startX = 0f,
                    endX   = fillX.coerceAtLeast(r * 2),
                ),
                size         = Size(fillX.coerceAtLeast(r * 2), h),
                cornerRadius = CornerRadius(r),
            )
        }
    }
}
