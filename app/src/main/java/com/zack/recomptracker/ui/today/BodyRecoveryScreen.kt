package com.zack.recomptracker.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.LocalSnackbarHostState
import com.zack.recomptracker.ui.body.BodyCheckInFormActions
import com.zack.recomptracker.ui.body.BodyCheckInFormContent
import com.zack.recomptracker.ui.body.BodyCheckInFormState
import com.zack.recomptracker.ui.component.FeaturedCard
import com.zack.recomptracker.ui.component.GlassButtonClickable
import com.zack.recomptracker.ui.component.GlassSurfaceCard
import com.zack.recomptracker.ui.component.SparklineChart
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.theme.FeaturedBorder
import com.zack.recomptracker.ui.theme.FeaturedSurface
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BodyRecoveryScreen(
    viewModel: TodayViewModel,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    LaunchedEffect(viewModel) {
        viewModel.savedEvent.collect {
            snackbarHostState.showSnackbar("Check-in saved")
        }
    }
    BodyRecoveryContent(
        state = state,
        onViewHistory = onViewHistory,
        actions = BodyCheckInFormActions(
            onBodyWeightChanged = viewModel::onBodyWeightChanged,
            onWaistChanged = viewModel::onWaistChanged,
            onWaistSkinfoldChanged = viewModel::onWaistSkinfoldChanged,
            onStepsChanged = viewModel::onStepsChanged,
            onSleepChanged = viewModel::onSleepChanged,
            onEnergyChanged = viewModel::onEnergyChanged,
            onHungerChanged = viewModel::onHungerChanged,
            onSorenessChanged = viewModel::onSorenessChanged,
            onTrainedChanged = viewModel::onTrainedChanged,
            onNotesChanged = viewModel::onNotesChanged,
            onSave = viewModel::saveMetrics,
        ),
        modifier = modifier,
    )
}

@Composable
fun BodyRecoveryContent(
    state: TodayUiState,
    actions: BodyCheckInFormActions,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formState = BodyCheckInFormState(
        date = state.date,
        bodyWeightKg = state.bodyWeightKg,
        waistCm = state.waistCm,
        waistSkinfoldMm = state.waistSkinfoldMm,
        steps = state.steps,
        sleepHours = state.sleepHours,
        energyScore = state.energyScore,
        hungerScore = state.hungerScore,
        sorenessScore = state.sorenessScore,
        trained = state.trained,
        notes = state.notes,
        message = state.message,
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Ambient orb
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(
                    Brush.radialGradient(listOf(Color(0x2E8B5CF6), Color.Transparent)),
                ),
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = FloatingNavHeight + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader(state)
            }
            item {
                MetricsHeroCard(state)
            }
            item {
                InlineLogFormCard(formState, actions)
            }
            item {
                HistoryButton(
                    daysLogged = state.totalDaysLogged,
                    onClick = onViewHistory,
                )
            }
        }
    }
}

@Composable
private fun ScreenHeader(state: TodayUiState) {
    val dateStr = remember(state.date) {
        state.date.format(DateTimeFormatter.ofPattern("EEE, MMMM d", Locale.getDefault()))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "Body",
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

// ── Metrics Hero Card ─────────────────────────────────────────────────────────

@Composable
private fun MetricsHeroCard(state: TodayUiState) {
    val checkInLabel = state.lastLogDate?.let { d ->
        "LATEST CHECK-IN · ${d.format(DateTimeFormatter.ofPattern("MMM d"))}"
    } ?: "NO CHECK-INS YET"

    val showSparklines = state.weightSparkline14d.isNotEmpty() || state.waistSparkline14d.isNotEmpty()

    FeaturedCard {
        Text(
            text = checkInLabel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xCCA78BFA),
            letterSpacing = 0.13.sp,
        )
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            MetricColumn(
                value = state.lastLogWeightKg?.let { "%.1f".format(it) } ?: "—",
                unit = "kg",
                label = "WEIGHT",
                trend = state.weightChange7d?.let {
                    val sign = if (it <= 0) "↓" else "↑"
                    "$sign ${String.format(Locale.US, "%.1f", Math.abs(it))} kg / week"
                } ?: "",
                sparklineValues = state.weightSparkline14d,
                showSparklineSlot = showSparklines,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0x0FFFFFFF)),
            )
            MetricColumn(
                value = state.lastLogWaistCm?.let { "%.1f".format(it) } ?: "—",
                unit = "cm",
                label = "WAIST",
                trend = state.waistChange7d?.let {
                    val sign = if (it <= 0) "↓" else "↑"
                    "$sign ${String.format(Locale.US, "%.1f", Math.abs(it))} cm / week"
                } ?: "",
                sparklineValues = state.waistSparkline14d,
                showSparklineSlot = showSparklines,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun MetricColumn(
    value: String,
    unit: String,
    label: String,
    trend: String,
    modifier: Modifier = Modifier,
    sparklineValues: List<Float> = emptyList(),
    showSparklineSlot: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = value,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-1.5).sp,
                lineHeight = 36.sp,
            )
            if (value != "—") {
                Text(
                    text = unit,
                    fontSize = 13.sp,
                    color = Color(0x66FFFFFF),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            letterSpacing = 0.10.sp,
        )
        if (trend.isNotEmpty()) {
            Text(
                text = trend,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Violet400,
            )
        }
        if (showSparklineSlot) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x0FFFFFFF)),
            )
            Spacer(Modifier.height(8.dp))
            if (sparklineValues.isNotEmpty()) {
                val minVal = remember(sparklineValues) { sparklineValues.min() }
                val maxVal = remember(sparklineValues) { sparklineValues.max() }
                SparklineChart(values = sparklineValues, height = 64.dp, showGlowDot = true)
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("%.1f".format(minVal), fontSize = 8.sp, color = Color(0x28FFFFFF))
                    Text("14 days", fontSize = 8.sp, color = Color(0x20FFFFFF))
                    Text("%.1f".format(maxVal), fontSize = 8.sp, color = Color(0x28FFFFFF))
                }
            } else {
                Spacer(Modifier.height(77.dp))
            }
        }
    }
}

// ── Inline Log Form Card ──────────────────────────────────────────────────────

@Composable
private fun InlineLogFormCard(
    state: BodyCheckInFormState,
    actions: BodyCheckInFormActions,
) {
    var collapsed by remember { mutableStateOf(true) }
    val dateStr = remember(state.date) {
        state.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x0F8B5CF6))
            .border(1.dp, Color(0x478B5CF6), RoundedCornerShape(18.dp)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { collapsed = !collapsed }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Today's check-in",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Text(
                    text = dateStr,
                    fontSize = 10.sp,
                    color = Color(0x4CFFFFFF),
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x0FFFFFFF))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = if (collapsed) "Expand ↓" else "Collapse ↑",
                    fontSize = 11.sp,
                    color = Color(0x4CFFFFFF),
                )
            }
        }

        // Animated form body — tighter padding when expanded
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BodyCheckInFormContent(
                    state = state,
                    actions = actions,
                    saveLabel = "Save check-in",
                )
            }
        }
    }
}

// ── History Button ────────────────────────────────────────────────────────────

@Composable
private fun HistoryButton(daysLogged: Int, onClick: () -> Unit) {
    GlassSurfaceCard(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        cornerRadius = 14,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Check-in history",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xBFFFFFFF),
                )
                Text(
                    text = "$daysLogged days logged · tap to view all",
                    fontSize = 10.sp,
                    color = TextMuted,
                )
            }
            Text(
                text = "→",
                fontSize = 16.sp,
                color = Color(0xB38B5CF6),
            )
        }
    }
}
