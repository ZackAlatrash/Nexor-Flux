package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.TextVeryMuted
import com.zack.recomptracker.ui.theme.Violet300
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DayMacros(
    val label: String,
    val proteinKcal: Float,
    val carbsKcal: Float,
    val fatKcal: Float,
    val isToday: Boolean = false,
)

internal fun stackedYScale(days: List<DayMacros>): Float {
    val maxTotal = days.maxOfOrNull { it.proteinKcal + it.carbsKcal + it.fatKcal } ?: 0f
    return (maxTotal * 1.15f).coerceAtLeast(1f)
}

@Composable
fun StackedBarChart(
    days: List<DayMacros>,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    barCornerRadius: Dp = 4.dp,
) {
    if (days.isEmpty()) return

    val yScale = stackedYScale(days)

    // One Animatable per bar — animated via coroutines with stagger
    val animatables = remember(days.size) { List(days.size) { Animatable(0f) } }
    LaunchedEffect(days.size) {
        animatables.forEachIndexed { i, anim ->
            launch {
                delay(i * ChartDefaults.AnimSpec.barStaggerMs)
                anim.animateTo(1f, animationSpec = ChartDefaults.AnimSpec.barRise)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val w        = size.width
            val h        = size.height
            val n        = days.size
            val gap      = 4.dp.toPx()
            val barWidth = (w - gap * (n - 1)) / n
            val cr       = CornerRadius(barCornerRadius.toPx())

            days.forEachIndexed { i, day ->
                val left  = i * (barWidth + gap)
                val scale = animatables[i].value

                val totalH   = (day.proteinKcal + day.carbsKcal + day.fatKcal) / yScale * h * scale
                val proteinH = day.proteinKcal / yScale * h * scale
                val carbsH   = day.carbsKcal   / yScale * h * scale
                val fatH     = day.fatKcal     / yScale * h * scale

                if (totalH <= 0f) return@forEachIndexed

                // Fat (bottom)
                if (fatH > 0f) {
                    val top = h - fatH
                    drawRoundRect(
                        color        = ChartDefaults.MacroColors.Fat,
                        topLeft      = Offset(left, top),
                        size         = Size(barWidth, fatH),
                        cornerRadius = if (proteinH == 0f && carbsH == 0f) cr else CornerRadius.Zero,
                    )
                }

                // Carbs (middle)
                if (carbsH > 0f) {
                    val top = h - fatH - carbsH
                    drawRect(
                        color   = ChartDefaults.MacroColors.Carbs,
                        topLeft = Offset(left, top),
                        size    = Size(barWidth, carbsH),
                    )
                }

                // Protein (top) — rounded top corners only, flush bottom to connect with carbs
                if (proteinH > 0f) {
                    val top = h - totalH
                    val r = barCornerRadius.toPx()
                    val proteinPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left                   = left,
                                top                    = top,
                                right                  = left + barWidth,
                                bottom                 = top + proteinH,
                                topLeftCornerRadius    = CornerRadius(r),
                                topRightCornerRadius   = CornerRadius(r),
                                bottomRightCornerRadius = CornerRadius.Zero,
                                bottomLeftCornerRadius = CornerRadius.Zero,
                            )
                        )
                    }
                    drawPath(proteinPath, color = ChartDefaults.MacroColors.Protein)
                }

                // Today highlight border
                if (day.isToday) {
                    val top = h - totalH
                    drawRoundRect(
                        color        = Violet300.copy(alpha = 0.5f),
                        topLeft      = Offset(left - 1.dp.toPx(), top - 1.dp.toPx()),
                        size         = Size(barWidth + 2.dp.toPx(), totalH + 2.dp.toPx()),
                        cornerRadius = cr,
                        style        = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        }

        // Day labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            days.forEach { day ->
                Text(
                    text       = day.label,
                    fontSize   = 9.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    color      = if (day.isToday) Violet300 else TextVeryMuted,
                    modifier   = Modifier.weight(1f),
                )
            }
        }
    }
}
