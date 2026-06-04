package com.zack.recomptracker.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.data.repository.DayCalorieSummary
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekCalorieStrip(
    weekData: List<DayCalorieSummary>,
    selectedDate: LocalDate,
    today: LocalDate,
    targetLow: Int,
    targetHigh: Int,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (weekData.isEmpty()) return

    val scaleMax = (targetHigh * 1.3f).toInt().coerceAtLeast(1)
    val zoneLowFrac  = (targetLow.toFloat()  / scaleMax).coerceIn(0f, 1f)
    val zoneHighFrac = (targetHigh.toFloat() / scaleMax).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x0D000000), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .drawBehind {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
                    val yHigh = size.height * (1f - zoneHighFrac)
                    val yLow  = size.height * (1f - zoneLowFrac)
                    drawLine(
                        color = Color(0x408B5CF6),
                        start = Offset(0f, yHigh), end = Offset(size.width, yHigh),
                        strokeWidth = 1.dp.toPx(), pathEffect = dash,
                    )
                    drawLine(
                        color = Color(0x258B5CF6),
                        start = Offset(0f, yLow), end = Offset(size.width, yLow),
                        strokeWidth = 1.dp.toPx(), pathEffect = dash,
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            weekData.forEach { summary ->
                WeekBarItem(
                    summary    = summary,
                    isSelected = summary.date == selectedDate,
                    scaleMax   = scaleMax,
                    targetLow  = targetLow,
                    targetHigh = targetHigh,
                    onSelected = { onDaySelected(summary.date) },
                    modifier   = Modifier.weight(1f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            weekData.forEach { summary ->
                val sel = summary.date == selectedDate
                Text(
                    text = summary.date.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .take(2),
                    fontSize = if (sel) 9.sp else 8.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) Violet400 else Color(0xFF555555),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekBarItem(
    summary: DayCalorieSummary,
    isSelected: Boolean,
    scaleMax: Int,
    targetLow: Int,
    targetHigh: Int,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = summary.calories == 0
    val targetFrac = if (empty) 0.04f else (summary.calories.toFloat() / scaleMax).coerceIn(0f, 1f)
    val animFrac by animateFloatAsState(targetFrac, tween(400), label = "bar_${summary.date}")

    val barColor = when {
        empty -> Color.White.copy(alpha = if (isSelected) 0.18f else 0.07f)
        summary.calories in targetLow..targetHigh ->
            Violet400.copy(alpha = if (isSelected) 1f else 0.50f)
        summary.calories > targetHigh ->
            Color(0xFFF97316).copy(alpha = if (isSelected) 1f else 0.50f)
        else -> Violet300.copy(alpha = if (isSelected) 0.80f else 0.30f)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0x158B5CF6) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0x308B5CF6) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelected,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .width(if (isSelected) 12.dp else 8.dp)
                .fillMaxHeight(animFrac)
                .background(barColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
        )
    }
}
