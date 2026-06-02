package com.zack.recomptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400

/**
 * Smooth bezier area chart matching the approved Glass Premium design.
 * Renders: area fill gradient, stroke line, optional zone band, end dot.
 */
@Composable
fun SparklineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    showGlowDot: Boolean = true,
    zoneLow: Float? = null,
    zoneHigh: Float? = null,
) {
    if (values.isEmpty()) {
        androidx.compose.foundation.layout.Spacer(modifier = modifier.height(height))
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val w = size.width
        val h = size.height
        val sidePad = 4.dp.toPx()
        val usableW = w - 2 * sidePad
        val n = values.size
        val minVal = values.min()
        val maxVal = values.max()
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val paddedMin = minVal - range * 0.10f
        val paddedMax = maxVal + range * 0.10f
        val paddedRange = (paddedMax - paddedMin).coerceAtLeast(1f)

        fun xAt(i: Int) = sidePad + (if (n > 1) i.toFloat() / (n - 1) else 0.5f) * usableW
        fun yAt(v: Float) = h * (1f - (v - paddedMin) / paddedRange)

        val pts = values.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }

        // Zone band
        if (zoneLow != null && zoneHigh != null) {
            val zLowY = yAt(zoneLow).coerceIn(0f, h)
            val zHighY = yAt(zoneHigh).coerceIn(0f, h)
            drawRect(
                color = Color(0x148B5CF6),
                topLeft = Offset(0f, zHighY),
                size = Size(w, (zLowY - zHighY).coerceAtLeast(0f)),
            )
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            drawLine(Color(0x408B5CF6), Offset(0f, zHighY), Offset(w, zHighY), 0.7.dp.toPx(), pathEffect = dash)
            drawLine(Color(0x408B5CF6), Offset(0f, zLowY), Offset(w, zLowY), 0.7.dp.toPx(), pathEffect = dash)
        }

        // Build smooth bezier path
        val linePath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        // Area fill
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x408B5CF6), Color(0x058B5CF6)),
            ),
        )

        // Line stroke
        drawPath(
            path = linePath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF7c3aed).copy(alpha = 0.55f),
                    Violet400,
                    Violet300.copy(alpha = 0.85f),
                ),
            ),
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Glowing end dot
        if (showGlowDot && pts.isNotEmpty()) {
            val last = pts.last()
            drawCircle(color = Color(0x1Fc4b5fd), radius = 9.dp.toPx(), center = last)
            drawCircle(color = Violet300, radius = 4.dp.toPx(), center = last)
        }
    }
}

/**
 * Tiny 36dp-height sparkline for mini chart cards — no zone, no dot glow.
 */
@Composable
fun MiniSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    SparklineChart(
        values = values,
        modifier = modifier,
        height = 36.dp,
        showGlowDot = false,
    )
}
