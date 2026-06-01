package com.zack.recomptracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.LocalSnackbarHostState
import com.zack.recomptracker.ui.body.BodyCheckInFormActions
import com.zack.recomptracker.ui.body.BodyCheckInFormContent
import com.zack.recomptracker.ui.body.BodyCheckInFormState
import com.zack.recomptracker.ui.component.SectionCard

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
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Body & Recovery",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Daily check-in · ${state.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.weightChange7d != null || state.waistChange7d != null) {
            item { TrendCard(state.weightChange7d, state.waistChange7d) }
        }
        item {
            SectionCard("Daily check-in") {
                BodyCheckInFormContent(
                    state = formState,
                    actions = actions,
                    saveLabel = "Save daily check-in",
                )
            }
        }
        item {
            OutlinedButton(onClick = onViewHistory, modifier = Modifier.fillMaxWidth()) {
                Text("View History")
            }
        }
    }
}

@Composable
private fun TrendCard(weightChange7d: Float?, waistChange7d: Float?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (weightChange7d != null) {
                    TrendChip(
                        value = weightChange7d,
                        unit = "kg",
                        label = "weight this week",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (waistChange7d != null) {
                    TrendChip(
                        value = waistChange7d,
                        unit = "cm",
                        label = "waist this week",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendChip(value: Float, unit: String, label: String, modifier: Modifier = Modifier) {
    val color = when {
        value < 0f -> Color(0xFF34d399)
        value > 0f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = if (value > 0f) "+" else ""
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "$sign${"%.1f".format(value)} $unit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
