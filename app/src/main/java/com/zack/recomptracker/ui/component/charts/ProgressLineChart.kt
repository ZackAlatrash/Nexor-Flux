package com.zack.recomptracker.ui.component.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Minimal line chart: plots [values] in order, scaled to their own min/max, with dot markers and
 * an accent stroke. Themed entirely via LocalAppAccent / LocalAppColors. Shows a placeholder when
 * fewer than 2 points (a single point can't show a trend).
 */
@Composable
fun ProgressLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 140.dp,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    if (values.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(
                text = if (values.isEmpty()) "No data yet" else "Log this exercise again to see a trend",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = appColors.textMuted,
            )
        }
        return
    }

    val minV = values.min()
    val maxV = values.max()
    val span = (maxV - minV).takeIf { it > 0f } ?: 1f
    val lineColor = accent.accent
    val dotColor = accent.accentLighter

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val padX = 12.dp.toPx()
        val padY = 14.dp.toPx()
        val w = size.width - padX * 2
        val h = size.height - padY * 2

        fun pointAt(i: Int): Offset {
            val x = padX + if (values.size == 1) w / 2 else w * i / (values.size - 1)
            val norm = (values[i] - minV) / span
            val y = padY + h * (1f - norm)
            return Offset(x, y)
        }

        // baseline
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(padX, padY + h),
            end = Offset(padX + w, padY + h),
            strokeWidth = 1.dp.toPx(),
        )

        val path = Path()
        values.indices.forEach { i ->
            val pt = pointAt(i)
            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))
        values.indices.forEach { i ->
            drawCircle(color = dotColor, radius = 3.dp.toPx(), center = pointAt(i))
        }
    }
}
