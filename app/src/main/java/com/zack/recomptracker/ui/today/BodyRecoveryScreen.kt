package com.zack.recomptracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.ScoreSlider
import com.zack.recomptracker.ui.component.SectionCard
import com.zack.recomptracker.ui.component.ToggleRow

data class BodyRecoveryActions(
    val onBodyWeightChanged: (String) -> Unit,
    val onWaistChanged: (String) -> Unit,
    val onStepsChanged: (String) -> Unit,
    val onSleepChanged: (String) -> Unit,
    val onEnergyChanged: (Int) -> Unit,
    val onHungerChanged: (Int) -> Unit,
    val onSorenessChanged: (Int) -> Unit,
    val onTrainedChanged: (Boolean) -> Unit,
    val onNotesChanged: (String) -> Unit,
    val onSaveMetrics: () -> Unit,
)

@Composable
fun BodyRecoveryScreen(
    viewModel: TodayViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BodyRecoveryContent(
        state = state,
        actions = BodyRecoveryActions(
            onBodyWeightChanged = viewModel::onBodyWeightChanged,
            onWaistChanged = viewModel::onWaistChanged,
            onStepsChanged = viewModel::onStepsChanged,
            onSleepChanged = viewModel::onSleepChanged,
            onEnergyChanged = viewModel::onEnergyChanged,
            onHungerChanged = viewModel::onHungerChanged,
            onSorenessChanged = viewModel::onSorenessChanged,
            onTrainedChanged = viewModel::onTrainedChanged,
            onNotesChanged = viewModel::onNotesChanged,
            onSaveMetrics = viewModel::saveMetrics,
        ),
        modifier = modifier,
    )
}

@Composable
fun BodyRecoveryContent(
    state: TodayUiState,
    actions: BodyRecoveryActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Body & recovery",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Daily check-in · ${state.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MessageText(state.message)
            }
        }
        item {
            SectionCard("Daily check-in") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NumberField("Weight", state.bodyWeightKg, actions.onBodyWeightChanged, Modifier.weight(1f), "kg")
                    NumberField("Waist", state.waistCm, actions.onWaistChanged, Modifier.weight(1f), "cm")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NumberField("Steps", state.steps, actions.onStepsChanged, Modifier.weight(1f))
                    NumberField("Sleep", state.sleepHours, actions.onSleepChanged, Modifier.weight(1f), "h")
                }
                ScoreSlider("Energy", state.energyScore, actions.onEnergyChanged)
                ScoreSlider("Hunger", state.hungerScore, actions.onHungerChanged)
                ScoreSlider("Soreness", state.sorenessScore, actions.onSorenessChanged)
                ToggleRow("Training day", state.trained, actions.onTrainedChanged)
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = actions.onNotesChanged,
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = actions.onSaveMetrics, modifier = Modifier.fillMaxWidth()) {
                    Text("Save daily check-in")
                }
            }
        }
    }
}
