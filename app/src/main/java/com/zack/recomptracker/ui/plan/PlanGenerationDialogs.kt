package com.zack.recomptracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.domain.plan.GeneratedPlan

@Composable
fun PlanPreviewDialog(
    plan: GeneratedPlan,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generated plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BreakdownRow("Weight used", "${plan.weightKgUsed} kg")
                BreakdownRow("BMR", "${plan.bmr} kcal")
                BreakdownRow("TDEE (×${plan.activityFactor})", "${plan.tdee} kcal")
                BreakdownRow("Goal adjustment", "${plan.goalDeltaPercent}%")
                Text(
                    "Target: ${plan.targetCalories} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                BreakdownRow("Protein", "${plan.proteinG} g")
                BreakdownRow("Carbs", "${plan.carbsG} g")
                BreakdownRow("Fat", "${plan.fatG} g")
                BreakdownRow("Calorie zone", "${plan.zoneLower}–${plan.zoneUpper} kcal")
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WeightEntryDialog(
    state: PlanGenerationDialog.WeightEntry,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter your weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No logged bodyweight found. Enter your current weight to generate a plan.")
                OutlinedTextField(
                    value = state.weightInput,
                    onValueChange = onValueChange,
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.error != null) {
                    Text(
                        state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
