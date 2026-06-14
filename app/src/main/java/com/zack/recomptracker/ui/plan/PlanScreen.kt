package com.zack.recomptracker.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DatePicker
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastMessage
import com.zack.recomptracker.ui.toast.ToastType
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.MessageKind
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.VioletToggle
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun PlanScreen(viewModel: PlanViewModel, onBack: () -> Unit) {
    val appColors = LocalAppColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    val initialDateMillis = remember(state.maintenancePhaseStartDate) {
        state.maintenancePhaseStartDate.takeIf { it.isNotBlank() }?.let {
            runCatching {
                java.time.LocalDate.parse(it)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    LaunchedEffect(showDatePicker) {
        if (showDatePicker) {
            datePickerState.selectedDateMillis = initialDateMillis
        }
    }
    val toastController = LocalToastController.current
    LaunchedEffect(viewModel) {
        viewModel.savedEvent.collect {
            toastController.show(ToastMessage("Plan saved", ToastType.Success))
        }
    }
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
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
                            tint = appColors.textPrimary,
                        )
                    }
                    Text("Plan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Text("Targets and review thresholds")
                MessageText(state.message, MessageKind.ERROR)
            }
        }
        item {
            GenerateHeroCard(onClick = viewModel::generateFromProfile)
        }
        item {
            FrostedCard {
                SectionLabel("Nutrition targets")
                GlassInputField(
                    label = "Calories",
                    value = state.targetCalories,
                    onValueChange = viewModel::updateTargetCalories,
                    unit = "kcal",
                    keyboardType = KeyboardType.Number,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassInputField("Protein", state.targetProteinG, viewModel::updateProtein, Modifier.weight(1f), "g", KeyboardType.Number)
                    GlassInputField("Carbs", state.targetCarbsG, viewModel::updateCarbs, Modifier.weight(1f), "g", KeyboardType.Number)
                    GlassInputField("Fat", state.targetFatG, viewModel::updateFat, Modifier.weight(1f), "g", KeyboardType.Number)
                }
            }
        }
        item {
            FrostedCard {
                SectionLabel("Calorie zone")
                GlassInputField(
                    label = "Lower bound",
                    value = state.calorieZoneLowerBound,
                    onValueChange = viewModel::updateZoneLower,
                    unit = "kcal",
                    keyboardType = KeyboardType.Number,
                )
                GlassInputField(
                    label = "Upper bound",
                    value = state.calorieZoneUpperBound,
                    onValueChange = viewModel::updateZoneUpper,
                    unit = "kcal",
                    keyboardType = KeyboardType.Number,
                )
            }
        }
        item {
            val chevronRotation by animateFloatAsState(
                targetValue = if (advancedOpen) 180f else 0f,
                label = "advancedChevron",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedOpen = !advancedOpen }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Advanced")
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (advancedOpen) "Collapse advanced" else "Expand advanced",
                    tint = appColors.textDim,
                    modifier = Modifier.rotate(chevronRotation),
                )
            }
        }
        item {
            AnimatedVisibility(visible = advancedOpen) {
                FrostedCard {
                    SectionLabel("Review rules")
                    GlassInputField("Weight trend threshold", state.weightTrendThresholdKgPerWeek, viewModel::updateWeightThreshold, unit = "kg/week")
                    GlassInputField("Waist increase threshold", state.waistIncreaseThresholdCm, viewModel::updateWaistThreshold, unit = "cm / 2 weeks")
                    GlassInputField("Adherence minimum", state.adherenceMinimumPercent, viewModel::updateAdherence, unit = "%", keyboardType = KeyboardType.Number)
                    GlassInputField("Review cadence", state.reviewCadenceDays, viewModel::updateReviewCadence, unit = "days", keyboardType = KeyboardType.Number)
                    PhaseStartField(
                        value = state.maintenancePhaseStartDate,
                        onClick = { showDatePicker = true },
                    )
                    VioletToggle("Metric units", state.useMetricUnits, viewModel::updateUnits)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LiquidPrimaryButton(
                    text = "Save plan",
                    onClick = viewModel::save,
                    modifier = Modifier.weight(1f),
                )
                LiquidSecondaryButton(
                    text = "Defaults",
                    onClick = viewModel::resetDefaults,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toString()
                        viewModel.updatePhaseStart(picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    when (val dialog = state.generationDialog) {
        is PlanGenerationDialog.Preview -> PlanPreviewDialog(
            plan = dialog.plan,
            onApply = viewModel::applyGeneratedPlan,
            onDismiss = viewModel::dismissGenerationDialog,
        )
        is PlanGenerationDialog.WeightEntry -> WeightEntryDialog(
            state = dialog,
            onValueChange = viewModel::updateGenerationWeightInput,
            onConfirm = { viewModel.submitWeight(dialog.weightInput) },
            onDismiss = viewModel::dismissGenerationDialog,
        )
        null -> Unit
    }
}

// ── Generate-from-profile hero card ──────────────────────────────────────────
// Violet-tinted glass card; the whole surface triggers generation.

@Composable
private fun GenerateHeroCard(onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    val heroBrush = Brush.linearGradient(
        listOf(accent.accent.copy(alpha = 0.22f), accent.accent.copy(alpha = 0.05f)),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(heroBrush)
            .border(1.dp, accent.accent.copy(alpha = 0.34f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.accent.copy(alpha = 0.20f))
                .border(1.dp, accent.accent.copy(alpha = 0.30f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = accent.inkLighter,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Generate from profile",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary,
            )
            Text(
                "Auto-calc targets from your TDEE",
                fontSize = 10.sp,
                color = appColors.textDim,
            )
        }
        Text(
            "Run ›",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accent.inkLight,
        )
    }
}

// ── Phase-start date — glass tappable row matching GlassInputField ───────────

@Composable
private fun PhaseStartField(value: String, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "PHASE START DATE",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = appColors.textMuted,
            letterSpacing = 0.10.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x40000000))
                .border(1.dp, appColors.cardBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = value.ifBlank { "Not set" },
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (value.isBlank()) appColors.textMuted else appColors.textPrimary,
            )
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = "Pick date",
                tint = appColors.textDim,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
