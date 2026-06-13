package com.zack.recomptracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.key
import com.zack.recomptracker.core.util.formatPercent
import com.zack.recomptracker.core.util.formatSignedOneDecimal
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiInsightCard
import com.zack.recomptracker.ui.component.charts.CalorieProgressBar
import com.zack.recomptracker.ui.component.charts.ChartDefaults
import com.zack.recomptracker.ui.component.charts.SparklineChart
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.VioletBadge
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.TextFaint
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.TextVeryMuted
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.review.WeeklyBriefingOverlay
import com.zack.recomptracker.ui.review.WeeklyReviewViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    viewModel: DashboardViewModel,
    weeklyReviewViewModel: WeeklyReviewViewModel,
    onOpenCoach: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reviewState by weeklyReviewViewModel.uiState.collectAsStateWithLifecycle()
    val badge by weeklyReviewViewModel.badge.collectAsStateWithLifecycle()
    val pendingApply by weeklyReviewViewModel.pendingApply.collectAsStateWithLifecycle()

    HomeDashboardContent(
        state = state,
        showWeeklyReviewBadge = badge,
        onOpenWeeklyReview = { weeklyReviewViewModel.open() },
    )

    WeeklyBriefingOverlay(
        state = reviewState,
        pendingApply = pendingApply,
        onDismiss = { weeklyReviewViewModel.dismiss() },
        onRegenerate = { weeklyReviewViewModel.regenerate() },
        onRequestApply = { weeklyReviewViewModel.requestApply(it) },
        onConfirmApply = { weeklyReviewViewModel.confirmApply() },
        onCancelApply = { weeklyReviewViewModel.cancelApply() },
        onDiscussWithCoach = {
            weeklyReviewViewModel.discussWithCoach()
            weeklyReviewViewModel.dismiss()
            onOpenCoach()
        },
        onOpenSettings = {
            weeklyReviewViewModel.dismiss()
            onOpenSettings()
        },
    )
}

@Composable
fun HomeDashboardContent(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    showWeeklyReviewBadge: Boolean = false,
    onOpenWeeklyReview: (() -> Unit)? = null,
) {
    val accent = LocalAppAccent.current
    val ambientOrbBrush1 = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.20f), Color.Transparent))
    }
    val ambientOrbBrush2 = remember(accent.accentLight) {
        Brush.radialGradient(listOf(accent.accentLight.copy(alpha = 0.08f), Color.Transparent))
    }
    Box(modifier = modifier.fillMaxSize()) {
        // Ambient orb 1 — top-left bloom
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(ambientOrbBrush1),
        )
        // Ambient orb 2 — right-center secondary bloom
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = 200.dp, y = 260.dp)
                .background(ambientOrbBrush2),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp))

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = FloatingNavHeight + 72.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { MotivationalCard(state.motivationalMessage) }
                item { TodayCard(state) }
                item { StatTilesRow(state.adherencePercent, state.weightTrendKgPerWeek) }
                item { SevenDayChartCard(state) }
            }
        }

        // Floating wide liquid-glass Weekly Review pill above the nav bar
        if (onOpenWeeklyReview != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = FloatingNavHeight + 12.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Accent glow halo behind the pill — brighter when a fresh review is waiting.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .blur(radius = 26.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .clip(RoundedCornerShape(100))
                        .background(accent.accent.copy(alpha = if (showWeeklyReviewBadge) 0.55f else 0.32f)),
                )
                LiquidGlassButton(
                    onClick = onOpenWeeklyReview,
                    modifier = Modifier.fillMaxWidth(),
                    tint = accent.accent,
                    surfaceColor = Color.White.copy(alpha = 0.10f),
                ) {
                    Text(
                        text = "✦  Weekly Review",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
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
    val accent = LocalAppAccent.current
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
                color = accent.accent.copy(alpha = 0.65f),
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
    val accent = LocalAppAccent.current
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
                        Brush.horizontalGradient(listOf(accent.accent, accent.accentLight)),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

// ── Card 2: LAST 7 DAYS CHART ─────────────────────────────────────────────────

@Composable
private fun SevenDayChartCard(state: DashboardUiState) {
    val accent = LocalAppAccent.current
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
                    color = accent.accentLight.copy(alpha = 0.85f),
                    letterSpacing = 0.13.sp,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent.accentLight),
                )
                Text(
                    text = "$inZone of 7 in zone",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent.accentLighter,
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
                        color = if (day.isToday) accent.accentLighter else TextVeryMuted,
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
                valueColor = accent.accentLight,
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
                value = "${state.loggedDaysInWindow} / 14",
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

// ── Card: MOTIVATIONAL MESSAGE ────────────────────────────────────────────────

@Composable
private fun MotivationalCard(message: String) {
    val accent = LocalAppAccent.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.accentDark.copy(alpha = 0.14f), accent.accentDark.copy(alpha = 0.06f)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            )
            .border(1.dp, accent.accent.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            text = message,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFEDE9FE),
            lineHeight = 24.sp,
            letterSpacing = (-0.3).sp,
        )
    }
}

// ── Stat Tiles Row ────────────────────────────────────────────────────────────

@Composable
private fun StatTilesRow(
    adherencePercent: Double,
    weightTrendKgPerWeek: Double,
) {
    val accent = LocalAppAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(
            value = adherencePercent.formatPercent(),
            label = "Adherence",
            valueColor = accent.accentLight,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = "${weightTrendKgPerWeek.formatSignedOneDecimal()} kg",
            label = "Trend / week",
            valueColor = if (weightTrendKgPerWeek <= 0.0) ErrorRed else Color(0xFF4ADE80),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = valueColor,
                letterSpacing = (-0.5).sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted,
                letterSpacing = 0.06.sp,
            )
        }
    }
}

// ── Legacy Stats sub-screen (accessible via More → Stats) ─────────────────────

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiInsightState.collectAsStateWithLifecycle()
    // Key on result.key() so the effect only restarts when verdict/reasons/change differ —
    // not on every summary text update, which is not part of the dedup logic.
    LaunchedEffect(state.result.key()) {
        viewModel.onAiCardVisible(state.result)
    }
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Calorie Decision",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        text = "Today's recommendation",
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            }
        }
        item {
            VerdictHero(result = state.result)
        }
        item {
            AiInsightSection(
                result = state.result,
                aiState = aiState,
                onDownload = viewModel::requestModelDownload,
                onCancel = viewModel::cancelDownload,
                onRetry = viewModel::retryGeneration,
            )
        }
        item {
            FrostedCard {
                SectionLabel("Current targets")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatRow("Calories", "${state.preferences.targetCalories} kcal")
                    StatRow(
                        "Protein / Carbs / Fat",
                        "${state.preferences.targetProteinG} / ${state.preferences.targetCarbsG} / ${state.preferences.targetFatG} g",
                    )
                }
            }
        }
        item {
            FrostedCard {
                SectionLabel("Trend summary")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatRow("7-day weight average", state.sevenDayWeightAverage?.let { "${String.format(Locale.US, "%.1f", it)} kg" } ?: "No data")
                    StatRow("Weight trend", "${state.weightTrendKgPerWeek.formatSignedOneDecimal()} kg/week")
                    StatRow("Waist trend", "${state.waistTrendCmPerWeek.formatSignedOneDecimal()} cm/week")
                    StatRow("Adherence", state.adherencePercent.formatPercent())
                    StatRow("Logged days", "${state.loggedDaysInWindow} / 14")
                }
            }
        }
    }
}

// ── Verdict HERO ──────────────────────────────────────────────────────────────

@Composable
private fun VerdictHero(result: AdjustmentResult) {
    val accent = LocalAppAccent.current

    val deltaText = when (result.verdict) {
        AdjustmentVerdict.WAIT_FOR_DATA -> "Not enough data yet"
        else -> {
            val change = result.recommendedCalorieChange
            val sign = if (change > 0) "+" else ""
            "Recommended change · $sign$change kcal/day"
        }
    }

    TintedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "TODAY'S VERDICT",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent.accentLight,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = result.verdict.heroLabel(),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.6).sp,
                lineHeight = 36.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = deltaText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent.accentLighter,
            )

            val reasons = result.reasonCodes.filter { it.isNotBlank() }
            if (reasons.isNotEmpty()) {
                Spacer(Modifier.height(13.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    reasons.forEach { code ->
                        ReasonChip(text = code.humanizeReasonCode())
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasonChip(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = Color(0xB3FFFFFF),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun AiInsightSection(
    result: AdjustmentResult,
    aiState: AiInsightState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val accent = LocalAppAccent.current
    if (result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) {
        if (aiState != AiInsightState.Disabled) {
            Text(
                text = "AI explanations appear once a weekly verdict is ready.",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        return
    }

    when (aiState) {
        AiInsightState.Disabled -> Unit

        AiInsightState.ModelMissing -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Understand the reasoning behind this verdict.",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                Text(
                    text = "Requires a ~2.6 GB download · Wi-Fi recommended",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = onDownload) {
                    Text("Download Model")
                }
            }
        }

        is AiInsightState.Downloading -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(10.dp))
                val progress = aiState.progress
                if (progress != null) {
                    Text(
                        text = "${"%.1f".format(progress * 2.6f)} GB of 2.6 GB",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Downloading…", fontSize = 11.sp, color = TextMuted)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = onCancel) {
                    Text("Cancel", fontSize = 11.sp)
                }
            }
        }

        AiInsightState.DownloadFailed -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text("Download failed — check your connection.", fontSize = 13.sp, color = TextMuted)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = onDownload) { Text("Retry") }
            }
        }

        AiInsightState.ModelVerifying -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accent.accentLight,
                    )
                    Text("Verifying download…", fontSize = 13.sp, color = TextMuted)
                }
            }
        }

        AiInsightState.ModelReady,
        AiInsightState.LoadingModel -> {
            AiInsightCard(borderMode = AiBorderMode.Preparing) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = accent.accentLight,
                    )
                    Text("Preparing model…", fontSize = 13.sp, color = TextMuted)
                }
            }
        }

        is AiInsightState.Generating -> {
            AiInsightCard(borderMode = AiBorderMode.Generating) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text(
                    text = aiState.partialText,
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp,
                )
            }
        }

        is AiInsightState.Ready -> {
            AiInsightCard(borderMode = AiBorderMode.Ready) {
                AiCardHeader(title = "Why this verdict", showRefresh = true, onRefresh = onRetry)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = aiState.text,
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp,
                )
            }
        }

        is AiInsightState.Error -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text(aiState.message, fontSize = 13.sp, color = TextMuted)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun AiCardHeader(
    title: String,
    showRefresh: Boolean,
    onRefresh: () -> Unit,
) {
    val accent = LocalAppAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextFaint,
            letterSpacing = 0.14.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showRefresh) {
                androidx.compose.material3.IconButton(onClick = onRefresh) {
                    Text("↺", fontSize = 14.sp, color = accent.accentLight)
                }
            }
            AiBadge()
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

/** Full-phrase verdict word for the hero card (e.g. "Hold steady"). */
private fun AdjustmentVerdict.heroLabel(): String = when (this) {
    AdjustmentVerdict.WAIT_FOR_DATA     -> "Keep logging"
    AdjustmentVerdict.HOLD              -> "Hold steady"
    AdjustmentVerdict.INCREASE_CALORIES -> "Increase"
    AdjustmentVerdict.REDUCE_CALORIES   -> "Reduce"
}

/**
 * Turns an enum-ish reason code (e.g. "TREND_ON_TRACK") into a tidy chip label
 * ("Trend on track"). Already human-readable strings pass through with just a
 * capitalised first letter.
 */
private fun String.humanizeReasonCode(): String {
    val cleaned = trim()
    val words = if (cleaned.contains('_')) {
        cleaned.split('_').filter { it.isNotBlank() }.joinToString(" ") { it.lowercase() }
    } else {
        cleaned.lowercase()
    }
    return words.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

private fun safeFrac(value: Double, target: Int): Float =
    if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
