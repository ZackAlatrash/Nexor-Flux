package com.zack.recomptracker.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.ScoreSlider
import com.zack.recomptracker.ui.component.ToggleRow
import java.time.LocalDate

data class BodyCheckInFormState(
    val date: LocalDate,
    val bodyWeightKg: String = "",
    val waistCm: String = "",
    val waistSkinfoldMm: String = "",
    val steps: String = "",
    val sleepHours: String = "",
    val energyScore: Int = 5,
    val hungerScore: Int = 5,
    val sorenessScore: Int = 5,
    val trained: Boolean = false,
    val notes: String = "",
    val message: String? = null,
)

data class BodyCheckInFormActions(
    val onBodyWeightChanged: (String) -> Unit,
    val onWaistChanged: (String) -> Unit,
    val onWaistSkinfoldChanged: (String) -> Unit,
    val onStepsChanged: (String) -> Unit,
    val onSleepChanged: (String) -> Unit,
    val onEnergyChanged: (Int) -> Unit,
    val onHungerChanged: (Int) -> Unit,
    val onSorenessChanged: (Int) -> Unit,
    val onTrainedChanged: (Boolean) -> Unit,
    val onNotesChanged: (String) -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun BodyCheckInFormContent(
    state: BodyCheckInFormState,
    actions: BodyCheckInFormActions,
    saveLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MessageText(state.message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField("Weight", state.bodyWeightKg, actions.onBodyWeightChanged, Modifier.weight(1f), "kg")
            NumberField("Waist", state.waistCm, actions.onWaistChanged, Modifier.weight(1f), "cm")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField("Belly skinfold", state.waistSkinfoldMm, actions.onWaistSkinfoldChanged, Modifier.weight(1f), "mm")
            NumberField("Sleep", state.sleepHours, actions.onSleepChanged, Modifier.weight(1f), "h")
        }
        NumberField("Steps", state.steps, actions.onStepsChanged, Modifier.fillMaxWidth())
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
        Button(onClick = actions.onSave, modifier = Modifier.fillMaxWidth()) {
            Text(saveLabel)
        }
    }
}
