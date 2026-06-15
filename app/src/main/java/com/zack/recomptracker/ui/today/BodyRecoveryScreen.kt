package com.zack.recomptracker.ui.today

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastMessage
import com.zack.recomptracker.ui.toast.ToastType
import com.zack.recomptracker.ui.body.BodyCheckInFormActions
import com.zack.recomptracker.ui.body.BodyCheckInFormState
import com.zack.recomptracker.ui.body.BodyCheckInSheet
import com.zack.recomptracker.ui.body.CheckInSection
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.GeneratedInsightCard
import com.zack.recomptracker.ui.component.NeutralCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.charts.SparklineChart
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BodyRecoveryScreen(
    viewModel: TodayViewModel,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recoveryInsightState by viewModel.recoveryInsightState.collectAsStateWithLifecycle()
    LaunchedEffect(state.recoveryInsightContext?.key()) {
        viewModel.onRecoveryInsightVisible()
    }
    val toastController = LocalToastController.current
    LaunchedEffect(viewModel) {
        viewModel.savedEvent.collect {
            toastController.show(ToastMessage("Check-in saved", ToastType.Success))
        }
    }
    BodyRecoveryContent(
        state = state,
        recoveryInsightState = recoveryInsightState,
        onRetryRecoveryInsight = viewModel::retryRecoveryInsight,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyRecoveryContent(
    state: TodayUiState,
    actions: BodyCheckInFormActions,
    onViewHistory: () -> Unit,
    recoveryInsightState: AiInsightState = AiInsightState.Disabled,
    onRetryRecoveryInsight: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val bodyRecoveryOrbBrush = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.18f), Color.Transparent))
    }
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
        checkInDone = state.checkInDone,
    )

    var showSheet by remember { mutableStateOf(false) }
    var initialSection by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasData = state.bodyWeightKg.isNotEmpty() || state.waistCm.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(bodyRecoveryOrbBrush),
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = FloatingNavHeight + 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ScreenHeader(state) }
            item { MetricsHeroCard(state) }
            item {
                com.zack.recomptracker.ui.component.DismissibleInsight { onDismiss ->
                    GeneratedInsightCard(
                        title = "Recovery readiness",
                        state = recoveryInsightState,
                        onRetry = onRetryRecoveryInsight,
                        variant = com.zack.recomptracker.ui.component.InsightCardVariant.STANDARD,
                        onDismiss = onDismiss,
                    )
                }
            }
            item {
                MetricTilesCard(formState) { section ->
                    initialSection = section
                    showSheet = true
                }
            }
            item {
                RecoveryBandCard(formState) {
                    initialSection = 1
                    showSheet = true
                }
            }
            item { HistoryButton(daysLogged = state.totalDaysLogged, onClick = onViewHistory) }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = FloatingNavHeight + 12.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            LiquidPrimaryButton(
                text = if (hasData) "Edit check-in" else "Log today",
                onClick = { initialSection = 0; showSheet = true },
            )
        }
    }

    if (showSheet) {
        BodyCheckInSheet(
            state = formState,
            actions = actions,
            onDismiss = { showSheet = false },
            initialSection = initialSection,
            sheetState = sheetState,
        )
    }
}

// ── Screen Header ─────────────────────────────────────────────────────────────

@Composable
private fun ScreenHeader(state: TodayUiState) {
    val appColors = LocalAppColors.current
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
            color = appColors.textPrimary,
            letterSpacing = (-0.8).sp,
        )
        Text(text = dateStr, fontSize = 12.sp, color = appColors.textMuted)
    }
}

// ── Metrics Hero Card ─────────────────────────────────────────────────────────

@Composable
private fun MetricsHeroCard(state: TodayUiState) {
    val checkInLabel = state.lastLogDate?.let {
        "LATEST CHECK-IN · ${it.format(DateTimeFormatter.ofPattern("MMM d"))}"
    } ?: "NO CHECK-INS YET"
    val accent = LocalAppAccent.current
    val showSparklines = state.weightSparkline14d.isNotEmpty() || state.waistSparkline14d.isNotEmpty()

    FrostedCard {
        Text(
            text = checkInLabel,
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = accent.inkLight.copy(alpha = 0.80f), letterSpacing = 0.13.sp,
        )
        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            MetricColumn(
                value = state.lastLogWeightKg?.let { "%.1f".format(it) } ?: "—",
                unit = "kg", label = "WEIGHT",
                trend = state.weightChange7d?.let {
                    val sign = if (it <= 0) "↓" else "↑"
                    "$sign ${String.format(Locale.US, "%.1f", Math.abs(it))} kg / week"
                } ?: "",
                sparklineValues = state.weightSparkline14d,
                showSparklineSlot = showSparklines,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(LocalAppColors.current.cardBorder))
            MetricColumn(
                value = state.lastLogWaistCm?.let { "%.1f".format(it) } ?: "—",
                unit = "cm", label = "WAIST",
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
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = value,
                fontSize = 36.sp, fontWeight = FontWeight.Black,
                color = appColors.textPrimary, letterSpacing = (-1.5).sp, lineHeight = 36.sp,
            )
            if (value != "—") {
                Text(
                    text = unit, fontSize = 13.sp,
                    color = appColors.textMuted, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = appColors.textMuted, letterSpacing = 0.10.sp)
        if (trend.isNotEmpty()) {
            Text(text = trend, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent.inkLight)
        }
        if (showSparklineSlot) {
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(appColors.cardBorder))
            Spacer(Modifier.height(8.dp))
            if (sparklineValues.isNotEmpty()) {
                val minVal = remember(sparklineValues) { sparklineValues.min() }
                val maxVal = remember(sparklineValues) { sparklineValues.max() }
                SparklineChart(values = sparklineValues, height = 64.dp, showGlowDot = true)
                Spacer(Modifier.height(3.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("%.1f".format(minVal), fontSize = 8.sp, color = appColors.textFaint)
                    Text("14 days", fontSize = 8.sp, color = appColors.textFaint)
                    Text("%.1f".format(maxVal), fontSize = 8.sp, color = appColors.textFaint)
                }
            } else {
                Spacer(Modifier.height(77.dp))
            }
        }
    }
}

// ── Metric Tiles Card ─────────────────────────────────────────────────────────

@Composable
private fun MetricTilesCard(
    state: BodyCheckInFormState,
    onTileClick: (section: Int) -> Unit,
) {
    FrostedCard {
        SectionLabel("Today")
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Weight", state.bodyWeightKg.ifEmpty { null }, "kg", Modifier.weight(1f)) { onTileClick(0) }
            MetricTile("Waist", state.waistCm.ifEmpty { null }, "cm", Modifier.weight(1f)) { onTileClick(0) }
            MetricTile("Skinfold", state.waistSkinfoldMm.ifEmpty { null }, "mm", Modifier.weight(1f)) { onTileClick(0) }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Sleep", state.sleepHours.ifEmpty { null }, "hrs", Modifier.weight(1f)) { onTileClick(0) }
            MetricTile("Steps", state.steps.ifEmpty { null }, "", Modifier.weight(1f)) { onTileClick(0) }
            MetricTile("Training", if (state.trained) "Yes" else if (state.checkInDone) "No" else null, "", Modifier.weight(1f)) { onTileClick(2) }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String?,
    unit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val empty = value == null
    val valueAlpha by animateFloatAsState(if (empty) 0.28f else 1f, label = "tileAlpha_$label")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CornerSmall))
            .background(if (empty) appColors.cardSurface else accent.accent.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (empty) appColors.cardBorder else accent.accent.copy(alpha = 0.18f),
                RoundedCornerShape(CornerSmall),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = appColors.textMuted, letterSpacing = 0.10.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value ?: "—",
                fontSize = if (empty) 17.sp else 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = appColors.textPrimary.copy(alpha = valueAlpha),
                letterSpacing = (-0.5).sp,
                lineHeight = 20.sp,
            )
            if (unit.isNotEmpty() && !empty) {
                Text(text = unit, fontSize = 8.sp, color = appColors.textMuted, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

// ── Recovery Band Card ────────────────────────────────────────────────────────

@Composable
private fun RecoveryBandCard(state: BodyCheckInFormState, onClick: () -> Unit) {
    NeutralCard(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Recovery")
            Text(text = "tap to edit →", fontSize = 8.sp, color = LocalAppColors.current.textFaint)
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreBar("Energy", state.energyScore)
            ScoreBar("Hunger", state.hungerScore)
            ScoreBar("Soreness", state.sorenessScore)
        }
    }
}

@Composable
private fun ScoreBar(label: String, score: Int) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val fraction by animateFloatAsState(targetValue = score / 10f, label = "bar_$label")
    val (barStart, barEnd, numColor) = when {
        score <= 4 -> Triple(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEF4444))
        score <= 6 -> Triple(Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFF59E0B))
        else -> Triple(accent.accent, accent.accentLighter, accent.accentLight)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = appColors.textDim, modifier = Modifier.width(58.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(appColors.cardBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .background(
                        Brush.horizontalGradient(listOf(barStart, barEnd)),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
        Text(
            text = "$score", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
            color = numColor, modifier = Modifier.width(18.dp),
        )
    }
}

// ── History Button ────────────────────────────────────────────────────────────

@Composable
private fun HistoryButton(daysLogged: Int, onClick: () -> Unit) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    NeutralCard(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Check-in history",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = appColors.textDim,
                )
                Text(text = "$daysLogged days logged · tap to view all", fontSize = 10.sp, color = appColors.textMuted)
            }
            Text("→", fontSize = 16.sp, color = accent.inkBase.copy(alpha = 0.70f))
        }
    }
}
