package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400
import kotlin.math.abs

internal fun dotScale(dotX: Float, totalWidth: Float, progress: Float): Float {
    if (totalWidth <= 0f) return 0f
    val threshold = (dotX / totalWidth).coerceIn(0f, 0.95f)
    if (progress < threshold) return 0f
    return ((progress - threshold) / 0.08f).coerceIn(0f, 1f)
}

internal fun nearestPointIndex(x: Float, pts: List<Offset>): Int =
    if (pts.isEmpty()) 0
    else pts.indices.minByOrNull { abs(pts[it].x - x) } ?: 0

@Composable
fun SparklineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    showGlowDot: Boolean = true,
    showScrubber: Boolean = false,
    zoneLow: Float? = null,
    zoneHigh: Float? = null,
    onScrubValue: ((Float?) -> Unit)? = null,
) {
    if (values.isEmpty()) {
        Spacer(modifier = modifier.height(height))
        return
    }

    val drawInProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        drawInProgress.animateTo(1f, animationSpec = ChartDefaults.AnimSpec.drawIn)
    }

    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    val gestureModifier = if (showScrubber && values.size >= 2) {
        Modifier.pointerInput(values) {
            detectDragGestures(
                onDragStart = { offset ->
                    val w = size.width.toFloat()
                    val sidePad = 4.dp.toPx()
                    val usableW = w - 2 * sidePad
                    val n = values.size
                    val pts = values.mapIndexed { i, _ ->
                        Offset(sidePad + (if (n > 1) i.toFloat() / (n - 1) else 0.5f) * usableW, 0f)
                    }
                    scrubIndex = nearestPointIndex(offset.x, pts)
                    onScrubValue?.invoke(values[scrubIndex!!])
                },
                onDragEnd = {
                    scrubIndex = null
                    onScrubValue?.invoke(null)
                },
                onDrag = { change, _ ->
                    val w = size.width.toFloat()
                    val sidePad = 4.dp.toPx()
                    val usableW = w - 2 * sidePad
                    val n = values.size
                    val pts = values.mapIndexed { i, _ ->
                        Offset(sidePad + (if (n > 1) i.toFloat() / (n - 1) else 0.5f) * usableW, 0f)
                    }
                    scrubIndex = nearestPointIndex(change.position.x, pts)
                    onScrubValue?.invoke(values[scrubIndex!!])
                },
            )
        }
    } else Modifier

    val progress = drawInProgress.value

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(gestureModifier),
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

        // Grid lines (4 horizontal, 4% opacity)
        for (frac in listOf(0.25f, 0.5f, 0.75f, 1.0f)) {
            drawLine(
                color = Color.White.copy(alpha = ChartDefaults.gridAlpha),
                start = Offset(0f, h * frac),
                end   = Offset(w, h * frac),
                strokeWidth = 0.5.dp.toPx(),
            )
        }

        // Zone band
        if (zoneLow != null && zoneHigh != null) {
            val zLowY  = yAt(zoneLow).coerceIn(0f, h)
            val zHighY = yAt(zoneHigh).coerceIn(0f, h)
            drawRect(
                color    = Color(0x1A8B5CF6),
                topLeft  = Offset(0f, zHighY),
                size     = Size(w, (zLowY - zHighY).coerceAtLeast(0f)),
            )
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            val dashColor = Color(ChartDefaults.zoneDashAlpha shl 24 or 0x8B5CF6)
            drawLine(dashColor, Offset(0f, zHighY), Offset(w, zHighY), 0.7.dp.toPx(), pathEffect = dash)
            drawLine(dashColor, Offset(0f, zLowY),  Offset(w, zLowY),  0.7.dp.toPx(), pathEffect = dash)
        }

        // Build bezier paths
        val linePath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]; val p1 = pts[i]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }

        val clipRight = w * progress

        // Area fill (fades in after line is 50% drawn)
        if (progress > 0.5f) {
            val areaAlpha = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            clipRect(right = clipRight) {
                drawPath(
                    path  = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x408B5CF6), Color(0x058B5CF6)),
                    ),
                    alpha = areaAlpha,
                )
            }
        }

        // Line stroke (revealed left→right)
        clipRect(right = clipRight) {
            drawPath(
                path  = linePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF7c3aed).copy(alpha = 0.55f),
                        Violet400,
                        Violet300.copy(alpha = 0.85f),
                    ),
                ),
                style = Stroke(
                    width = ChartDefaults.strokeWidth.toPx(),
                    cap   = StrokeCap.Round,
                    join  = StrokeJoin.Round,
                ),
            )
        }

        // Dots (scale in as line passes their x)
        pts.forEachIndexed { i, pt ->
            val scale = dotScale(pt.x, w, progress)
            if (scale > 0f) {
                val isActive  = scrubIndex == i
                val isEndDot  = i == pts.lastIndex
                val dotAlpha  = if (scrubIndex != null && !isActive) 0.3f else 1f

                if (isActive) {
                    drawCircle(color = Color(0x1Fc4b5fd), radius = ChartDefaults.glowRadius.toPx(), center = pt)
                    drawCircle(color = Violet300, radius = ChartDefaults.dotRadius.toPx(), center = pt)
                    drawLine(
                        color       = Color(0x50a78bfa),
                        start       = Offset(pt.x, 0f),
                        end         = Offset(pt.x, h),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect  = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                    )
                } else if (showGlowDot && isEndDot && scrubIndex == null) {
                    val glowAlpha = ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f)
                    if (glowAlpha > 0f) {
                        drawCircle(color = Color(0x1Fc4b5fd).copy(alpha = 0.08f * glowAlpha), radius = ChartDefaults.glowHalo.toPx(), center = pt)
                        drawCircle(color = Color(0x1Fc4b5fd).copy(alpha = 0.15f * glowAlpha), radius = ChartDefaults.glowRadius.toPx(), center = pt)
                        drawCircle(color = Violet300.copy(alpha = glowAlpha), radius = ChartDefaults.dotRadius.toPx() * scale, center = pt)
                    }
                } else {
                    drawCircle(
                        color  = Violet300.copy(alpha = dotAlpha),
                        radius = 2.5.dp.toPx() * scale,
                        center = pt,
                    )
                }
            }
        }
    }
}

@Composable
fun MiniSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    SparklineChart(
        values      = values,
        modifier    = modifier,
        height      = 36.dp,
        showGlowDot = false,
    )
}
