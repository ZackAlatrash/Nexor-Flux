package com.zack.recomptracker.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.data.repository.DayCalorieSummary
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val STRIP_BAR_HEIGHT = 60.dp

// Dash pattern for the target line — constant pixel lengths, no density dependency, so it's
// built once per process instead of once per drawBehind pass.
private val TargetLineDashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))

@Composable
fun WeekCalorieStrip(
    weekData: List<DayCalorieSummary>,
    selectedDate: LocalDate,
    today: LocalDate,
    onDaySelected: (LocalDate) -> Unit,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (weekData.isEmpty()) return

    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    // Shared vertical scale so bars stay visually comparable across the week,
    // derived from the highest zone upper bound resolved for any day.
    val maxZoneHigh = weekData.maxOf { it.zoneUpperBound }
    val scaleMax = (maxZoneHigh * 1.3f).toInt().coerceAtLeast(1)

    // The dashed target line + zone band overlay follow the currently-viewed day's plan.
    val viewed = weekData.firstOrNull { it.date == selectedDate } ?: weekData.last()
    val hasZone = viewed.zoneLowerBound > 0
    val zoneLowFrac   = (viewed.zoneLowerBound.toFloat()  / scaleMax).coerceIn(0f, 1f)
    val zoneHighFrac  = (viewed.zoneUpperBound.toFloat()  / scaleMax).coerceIn(0f, 1f)
    val targetFrac    = (viewed.targetCalories.toFloat()  / scaleMax).coerceIn(0f, 1f)

    val yTargetDp = STRIP_BAR_HEIGHT * (1f - targetFrac)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (appColors.isDark) Color(0x0D000000) else appColors.cardSurface, RoundedCornerShape(14.dp))
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Bar area + zone label overlays
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(STRIP_BAR_HEIGHT),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .drawBehind {
                        if (!hasZone) return@drawBehind
                        val labelGap = 32.dp.toPx()
                        val yHigh   = size.height * (1f - zoneHighFrac)
                        val yLow    = size.height * (1f - zoneLowFrac)
                        val yTarget = size.height * (1f - targetFrac)

                        // Colored zone band
                        drawRect(
                            color   = accent.accent.copy(alpha = 0.07f),
                            topLeft = Offset(0f, yHigh),
                            size    = Size(size.width, yLow - yHigh),
                        )

                        // Single target line
                        drawLine(
                            color       = accent.accent.copy(alpha = 0.38f),
                            start       = Offset(0f, yTarget),
                            end         = Offset(size.width - labelGap, yTarget),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect  = TargetLineDashEffect,
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
                        today      = today,
                        onSelected = { onDaySelected(summary.date) },
                        modifier   = Modifier.weight(1f),
                    )
                }
            }

            // Single calorie target label, pinned to the right edge of the target line
            if (hasZone) {
                Text(
                    text = "${viewed.targetCalories}",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent.inkBase.copy(alpha = 0.50f),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = yTargetDp - 9.dp),
                )
            }
        }

        // Day-of-week labels — computed once per week/locale instead of 7x per recomposition.
        val dayLabels = remember(weekData, Locale.getDefault()) {
            weekData.map { summary ->
                summary.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            weekData.forEachIndexed { index, summary ->
                val sel = summary.date == selectedDate
                Text(
                    text = dayLabels[index],
                    fontSize = if (sel) 9.sp else 8.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) accent.inkLight else appColors.textDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Today pill — slides in below the day labels when viewing a past day
        AnimatedVisibility(
            visible = selectedDate != today,
            enter = fadeIn(tween(200)) + expandVertically(tween(220), expandFrom = Alignment.Top),
            exit  = fadeOut(tween(150)) + shrinkVertically(tween(170), shrinkTowards = Alignment.Top),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.accent.copy(alpha = 0.10f))
                    .border(1.dp, accent.accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTodayClick,
                    )
                    .padding(horizontal = 18.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Today",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent.inkLight,
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
    today: LocalDate,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val empty = summary.calories == 0
    val targetFrac = if (empty) 0.04f else (summary.calories.toFloat() / scaleMax).coerceIn(0f, 1f)
    val animFrac by animateFloatAsState(targetFrac, tween(400), label = "bar_${summary.date}")

    // Per-day zone — judge each bar against the plan that was in effect on its own date.
    val hasZone = summary.zoneLowerBound > 0
    val targetLow = summary.zoneLowerBound
    val targetHigh = summary.zoneUpperBound
    val isPastMissed = !empty && hasZone && summary.date < today && summary.calories < targetLow

    val barColor = when {
        empty           -> if (isSelected) appColors.textPrimary.copy(alpha = 0.18f)
                          else appColors.textPrimary.copy(alpha = 0.10f)
        !hasZone        -> accent.accentLighter.copy(alpha = 0.75f)
        summary.calories in targetLow..targetHigh -> accent.accentLight
        summary.calories > targetHigh             -> Color(0xFFF97316)
        isPastMissed    -> if (appColors.isDark) Color(0xFF7F1D1D)
                          else Color(0xFF9CA3AF).copy(alpha = 0.55f)
        else            -> accent.accentLighter.copy(alpha = 0.75f)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) accent.accent.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) accent.accent.copy(alpha = 0.19f) else Color.Transparent,
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
