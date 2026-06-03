package com.zack.recomptracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.LocalSnackbarHostState
import com.zack.recomptracker.ui.component.MessageKind
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.SectionCard
import com.zack.recomptracker.ui.component.ToggleRow
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(viewModel: PlanViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
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
    val snackbarHostState = LocalSnackbarHostState.current
    LaunchedEffect(viewModel) {
        viewModel.savedEvent.collect {
            snackbarHostState.showSnackbar("Plan saved")
        }
    }
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Plan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Targets and review thresholds")
                MessageText(state.message, MessageKind.ERROR)
            }
        }
        item {
            SectionCard("Nutrition targets") {
                NumberField("Calories", state.targetCalories, viewModel::updateTargetCalories, suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NumberField("Protein", state.targetProteinG, viewModel::updateProtein, Modifier.weight(1f), "g")
                    NumberField("Carbs", state.targetCarbsG, viewModel::updateCarbs, Modifier.weight(1f), "g")
                    NumberField("Fat", state.targetFatG, viewModel::updateFat, Modifier.weight(1f), "g")
                }
            }
        }
        item {
            SectionCard("Review rules") {
                NumberField("Weight trend threshold", state.weightTrendThresholdKgPerWeek, viewModel::updateWeightThreshold, suffix = "kg/week")
                NumberField("Waist increase threshold", state.waistIncreaseThresholdCm, viewModel::updateWaistThreshold, suffix = "cm / 2 weeks")
                NumberField("Adherence minimum", state.adherenceMinimumPercent, viewModel::updateAdherence, suffix = "%")
                NumberField("Review cadence", state.reviewCadenceDays, viewModel::updateReviewCadence, suffix = "days")
                NumberField("Calorie zone lower", state.calorieZoneLowerBound, viewModel::updateZoneLower, suffix = "kcal")
                NumberField("Calorie zone upper", state.calorieZoneUpperBound, viewModel::updateZoneUpper, suffix = "kcal")
                OutlinedTextField(
                    value = state.maintenancePhaseStartDate,
                    onValueChange = {},
                    label = { Text("Phase start date") },
                    placeholder = { Text("Not set") },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow("Metric units", state.useMetricUnits, viewModel::updateUnits)
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
}
