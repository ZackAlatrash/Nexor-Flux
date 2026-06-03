package com.zack.recomptracker.ui.dashboard

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.core.util.formatPercent
import com.zack.recomptracker.core.util.formatSignedOneDecimal
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.ui.component.charts.CalorieProgressBar
import com.zack.recomptracker.ui.component.charts.ChartDefaults
import com.zack.recomptracker.ui.component.charts.SparklineChart
import com.zack.recomptracker.ui.component.CalorieZoneBar
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.VioletBadge
import com.zack.recomptracker.ui.component.MacroMiniBar
import com.zack.recomptracker.ui.component.SectionCard
import com.zack.recomptracker.ui.theme.ErrorRed
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

    FrostedCard {
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
            VioletBadge(text = badgeText)
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
        animationSpec = ChartDefaults.AnimSpec.progressBar,
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

    FrostedCard {
        var scrubCalories by remember { mutableStateOf<Float?>(null) }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (scrubCalories != null) {
                Text(
                    text = String.format(java.util.Locale.US, "%,d kcal", scrubCalories!!.toInt()),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                )
            } else {
                Text(
                    text = "LAST 7 DAYS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xD9a78bfa),
                    letterSpacing = 0.13.sp,
                )
            }
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

        SparklineChart(
            values       = state.last7DaysCalories.map { it.calories.toFloat() },
            height       = 90.dp,
            showGlowDot  = true,
            showScrubber = true,
            zoneLow      = state.preferences.calorieZoneLowerBound.toFloat(),
            zoneHigh     = state.preferences.calorieZoneUpperBound.toFloat(),
            onScrubValue = { scrubCalories = it },
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
