package com.zack.recomptracker.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.component.GlassTextArea
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.ScoreStepper
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.VioletToggle
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
    val checkInDone: Boolean = false,
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

private val Divider: @Composable () -> Unit = {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(appColors.cardBorder),
    )
}

@Composable
fun BodyCheckInFormContent(
    state: BodyCheckInFormState,
    actions: BodyCheckInFormActions,
    saveLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MessageText(state.message)

        // ── Group 1: Measurements ─────────────────────────────────────────────
        SectionLabel("Measurements")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GlassInputField(
                label = "Weight",
                value = state.bodyWeightKg,
                onValueChange = actions.onBodyWeightChanged,
                unit = "kg",
                modifier = Modifier.weight(1f),
            )
            GlassInputField(
                label = "Waist",
                value = state.waistCm,
                onValueChange = actions.onWaistChanged,
                unit = "cm",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GlassInputField(
                label = "Sleep",
                value = state.sleepHours,
                onValueChange = actions.onSleepChanged,
                unit = "hrs",
                modifier = Modifier.weight(1f),
            )
            GlassInputField(
                label = "Steps",
                value = state.steps,
                onValueChange = actions.onStepsChanged,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        GlassInputField(
            label = "Skinfold",
            value = state.waistSkinfoldMm,
            onValueChange = actions.onWaistSkinfoldChanged,
            unit = "mm",
        )

        Divider()

        // ── Group 2: Recovery Scores ──────────────────────────────────────────
        SectionLabel("Recovery")
        ScoreStepper("Energy", state.energyScore, actions.onEnergyChanged)
        ScoreStepper("Hunger", state.hungerScore, actions.onHungerChanged)
        ScoreStepper("Soreness", state.sorenessScore, actions.onSorenessChanged)

        Divider()

        // ── Group 3: Training + Notes ─────────────────────────────────────────
        VioletToggle("Training day", state.trained, actions.onTrainedChanged)
        GlassTextArea(
            value = state.notes,
            onValueChange = actions.onNotesChanged,
            placeholder = "Notes…",
            minLines = 2,
        )

        LiquidPrimaryButton(text = saveLabel, onClick = actions.onSave)
    }
}
