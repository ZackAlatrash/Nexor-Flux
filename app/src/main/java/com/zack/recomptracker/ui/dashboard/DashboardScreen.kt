package com.zack.recomptracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.core.util.formatPercent
import com.zack.recomptracker.core.util.formatSignedOneDecimal
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.ui.component.CalorieProgressBar
import com.zack.recomptracker.ui.component.CalorieZoneBar
import com.zack.recomptracker.ui.component.MacroMiniBar
import com.zack.recomptracker.ui.component.SectionCard
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.FeaturedBorder
import com.zack.recomptracker.ui.theme.FeaturedSurface
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.TextVeryMuted
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400
import com.zack.recomptracker.ui.theme.Violet500
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    viewModel: DashboardViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeDashboardContent(state = state)
}

@Composable
fun HomeDashboardContent(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Ambient orb 1 — top-left violet bloom
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x338B5CF6), Color.Transparent),
                    ),
                ),
        )
        // Ambient orb 2 — right-center secondary bloom
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = 200.dp, y = 260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x14a78bfa), Color.Transparent),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp))

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { TodayCard(state) }
                item { SevenDayChartCard(state) }
            }
        }
    }
}

@Composable
private fun ScreenHeader(modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    val dateStr = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEE, MMMM d", Locale.getDefault()))
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "Dashboard",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = (-0.8).sp,
        )
        Text(
            text = dateStr,
            fontSize = 12.sp,
            color = TextMuted,
        )
    }
}

// ── Card 1: TODAY ─────────────────────────────────────────────────────────────

@Composable
private fun TodayCard(state: DashboardUiState) {
    val prefs    = state.preferences
    val calories = state.todayTotals.calories
    val zoneLow  = prefs.calorieZoneLowerBound
    val zoneHigh = prefs.calorieZoneUpperBound
    val scaleMax = ((zoneHigh * 1.2).toInt()).coerceAtLeast(1)
    val calFrac  = (calories.toFloat() / scaleMax).coerceIn(0f, 1f)

    val isInZone = calories in zoneLow..zoneHigh
    val isOver   = calories > zoneHigh
    val badgeText = when {
        isInZone -> "In zone"
        isOver   -> "Over"
        else     -> "Below"
    }
    val remainText = when {
        isInZone -> ""
        isOver   -> " · ${calories - zoneHigh} over"
        else     -> " · ${zoneLow - calories} to zone"
    }

    val proteinFrac = safeFrac(state.todayTotals.proteinG, prefs.targetProteinG)
    val carbsFrac   = safeFrac(state.todayTotals.carbsG,   prefs.targetCarbsG)
    val fatFrac     = safeFrac(state.todayTotals.fatG,     prefs.targetFatG)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TODAY",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 0.10.sp,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x1F8B5CF6))
                    .border(1.dp, Color(0x338B5CF6), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Violet400,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Big calorie number
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = String.format(Locale.US, "%,d", calories),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-1.5).sp,
                lineHeight = 36.sp,
            )
            Text(
                text = "kcal",
                fontSize = 13.sp,
                color = Color(0x59FFFFFF),
                fontWeight = FontWeight.Normal,
            )
            if (remainText.isNotEmpty()) {
                Text(
                    text = remainText,
                    fontSize = 11.sp,
                    color = TextMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Calorie progress bar with striped zone
        CalorieProgressBar(
            progress = calFrac,
            zoneLowFrac = (zoneLow.toFloat() / scaleMax).coerceIn(0f, 1f),
            zoneHighFrac = (zoneHigh.toFloat() / scaleMax).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
        Spacer(Modifier.height(4.dp))

        // Zone labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", fontSize = 9.sp, color = TextVeryMuted)
            Text(
                "▌ $zoneLow–$zoneHigh",
                fontSize = 9.sp,
                color = Color(0xA68B5CF6),
            )
            Text("$scaleMax", fontSize = 9.sp, color = TextVeryMuted)
        }
        Spacer(Modifier.height(12.dp))

        // Macros row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroBarItem(
                label = "Protein",
                value = "${state.todayTotals.proteinG.toInt()}g",
                fraction = proteinFrac,
                modifier = Modifier.weight(1f),
            )
            MacroBarItem(
                label = "Carbs",
                value = "${state.todayTotals.carbsG.toInt()}g",
                fraction = carbsFrac,
                modifier = Modifier.weight(1f),
            )
            MacroBarItem(
                label = "Fat",
                value = "${state.todayTotals.fatG.toInt()}g",
                fraction = fatFrac,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MacroBarItem(
    label: String,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val animatedFrac by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 900),
        label = "macroFill",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 0.07.sp,
            )
            Text(text = value, fontSize = 9.sp, color = Color(0x80FFFFFF))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x12FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFrac)
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(listOf(Violet500, Violet400)),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

// ── Card 2: LAST 7 DAYS CHART ─────────────────────────────────────────────────

@Composable
private fun SevenDayChartCard(state: DashboardUiState) {
    val inZone = state.inZoneDays7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FeaturedSurface)
            .border(1.dp, FeaturedBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LAST 7 DAYS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xD9a78bfa),
                letterSpacing = 0.13.sp,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x268B5CF6))
                    .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Violet400),
                )
                Text(
                    text = "$inZone of 7 in zone",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Violet300,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // SVG-like chart
        ChartCanvas(
            days = state.last7DaysCalories,
            zoneLow = state.preferences.calorieZoneLowerBound,
            zoneHigh = state.preferences.calorieZoneUpperBound,
        )

        // Day labels
        if (state.last7DaysCalories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                state.last7DaysCalories.forEach { day ->
                    Text(
                        text = day.label,
                        fontSize = 9.sp,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (day.isToday) Violet300 else TextVeryMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Divider
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x0FFFFFFF)),
        )
        Spacer(Modifier.height(10.dp))

        // Stats row
        Row(modifier = Modifier.fillMaxWidth()) {
            ChartStat(
                value = "${state.weightTrendKgPerWeek.formatSignedOneDecimal()} kg",
                label = "Trend/wk",
                valueColor = ErrorRed,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .align(Alignment.CenterVertically)
                    .background(Color(0x12FFFFFF)),
            )
            ChartStat(
                value = state.adherencePercent.formatPercent(),
                label = "Adherence",
                valueColor = Violet400,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .align(Alignment.CenterVertically)
                    .background(Color(0x12FFFFFF)),
            )
            ChartStat(
                value = "${state.daysLogged}",
                label = "Days logged",
                valueColor = Color.White,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChartCanvas(
    days: List<DayCalories>,
    zoneLow: Int,
    zoneHigh: Int,
) {
    if (days.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
        )
        return
    }

    val zoneLowF  = zoneLow.toFloat()
    val zoneHighF = zoneHigh.toFloat()
    val maxCal    = maxOf(days.maxOf { it.calories }.toFloat(), zoneHighF)
    val yMax      = (maxCal * 1.2f).coerceAtLeast(1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
    ) {
        val w        = size.width
        val h        = size.height
        val sidePad  = 12.dp.toPx()
        val usableW  = w - 2 * sidePad
        val n        = days.size

        // Compute point positions
        val pts = days.mapIndexed { i, day ->
            val x = sidePad + (if (n > 1) i.toFloat() / (n - 1) else 0.5f) * usableW
            val y = h * (1f - day.calories.toFloat() / yMax)
            Offset(x, y)
        }

        // Zone band
        val zoneLowY  = h * (1f - zoneLowF  / yMax)
        val zoneHighY = h * (1f - zoneHighF / yMax)
        drawRect(
            color    = Color(0x148B5CF6),
            topLeft  = Offset(0f, zoneHighY),
            size     = Size(w, zoneLowY - zoneHighY),
        )
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        drawLine(
            color       = Color(0x408B5CF6),
            start       = Offset(0f, zoneHighY),
            end         = Offset(w, zoneHighY),
            strokeWidth = 0.7.dp.toPx(),
            pathEffect  = dash,
        )
        drawLine(
            color       = Color(0x408B5CF6),
            start       = Offset(0f, zoneLowY),
            end         = Offset(w, zoneLowY),
            strokeWidth = 0.7.dp.toPx(),
            pathEffect  = dash,
        )

        // Smooth bezier path
        val linePath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0   = pts[i - 1]
                val p1   = pts[i]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        // Area fill under line
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }
        drawPath(
            path  = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x408B5CF6), Color(0x058B5CF6)),
                startY = 0f,
                endY   = h,
            ),
        )

        // Line stroke
        drawPath(
            path  = linePath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF7c3aed).copy(alpha = 0.55f),
                    Color(0xFFa78bfa),
                    Color(0xFFc4b5fd).copy(alpha = 0.85f),
                ),
            ),
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap   = StrokeCap.Round,
                join  = StrokeJoin.Round,
            ),
        )

        // Data point dots
        pts.forEachIndexed { i, pt ->
            val cal = days[i].calories
            val dotColor = when {
                cal <= 0                              -> Color(0x2EFFFFFF)
                cal in zoneLow..zoneHigh              -> Color(0xFFa78bfa)
                cal > zoneHigh                        -> ErrorRed
                else                                  -> Color(0x2EFFFFFF)
            }
            if (days[i].isToday) {
                drawCircle(color = Color(0x1Fc4b5fd), radius = 9.dp.toPx(), center = pt)
                drawCircle(color = Color(0xFFc4b5fd), radius = 4.5.dp.toPx(), center = pt)
            } else {
                drawCircle(color = dotColor, radius = 2.5.dp.toPx(), center = pt)
            }
        }
    }
}

@Composable
private fun ChartStat(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            letterSpacing = (-0.5).sp,
        )
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
            letterSpacing = 0.08.sp,
        )
    }
}

// ── Legacy Stats sub-screen (accessible via More → Stats) ─────────────────────

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Current status and calorie decision")
            }
        }
        item {
            SectionCard("Calorie verdict") {
                Text(state.result.verdict.label(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.result.summary)
                Text("Change: ${state.result.recommendedCalorieChange} kcal/day")
                Text("Reasons: ${state.result.reasonCodes.joinToString()}")
            }
        }
        item {
            SectionCard("Current targets") {
                Text("${state.preferences.targetCalories} kcal")
                Text("${state.preferences.targetProteinG}P / ${state.preferences.targetCarbsG}C / ${state.preferences.targetFatG}F")
            }
        }
        item {
            SectionCard("Today") {
                CalorieZoneBar(
                    eaten = state.todayTotals.calories,
                    zoneLower = state.preferences.calorieZoneLowerBound,
                    zoneUpper = state.preferences.calorieZoneUpperBound,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroMiniBar(
                        label = "Protein",
                        eaten = state.todayTotals.proteinG,
                        target = state.preferences.targetProteinG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Carbs",
                        eaten = state.todayTotals.carbsG,
                        target = state.preferences.targetCarbsG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Fat",
                        eaten = state.todayTotals.fatG,
                        target = state.preferences.targetFatG,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            SectionCard("Trend summary") {
                StatRow("7-day weight average", state.sevenDayWeightAverage?.let { "${String.format(Locale.US, "%.1f", it)} kg" } ?: "No data")
                StatRow("Weight trend", "${state.weightTrendKgPerWeek.formatSignedOneDecimal()} kg/week")
                StatRow("Waist trend", "${state.waistTrendCmPerWeek.formatSignedOneDecimal()} cm/week")
                StatRow("Adherence", state.adherencePercent.formatPercent())
                StatRow("Logged days", state.daysLogged.toString())
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun AdjustmentVerdict.label(): String = when (this) {
    AdjustmentVerdict.WAIT_FOR_DATA    -> "Wait"
    AdjustmentVerdict.HOLD             -> "Hold"
    AdjustmentVerdict.INCREASE_CALORIES -> "Increase"
    AdjustmentVerdict.REDUCE_CALORIES   -> "Reduce"
}

private fun safeFrac(value: Double, target: Int): Float =
    if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
