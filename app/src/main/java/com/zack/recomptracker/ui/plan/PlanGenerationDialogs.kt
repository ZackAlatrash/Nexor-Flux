package com.zack.recomptracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.domain.plan.GeneratedPlan
import com.zack.recomptracker.ui.component.GlassAlertDialog
import com.zack.recomptracker.ui.component.GlassInputField

@Composable
fun PlanPreviewDialog(
    plan: GeneratedPlan,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismiss = onDismiss,
        title = "Generated plan",
        confirmLabel = "Apply",
        onConfirm = onApply,
        dismissLabel = "Cancel",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BreakdownRow("Weight used", "%.1f kg".format(java.util.Locale.US, plan.weightKgUsed))
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
    }
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
    GlassAlertDialog(
        onDismiss = onDismiss,
        title = "Enter your weight",
        confirmLabel = "Continue",
        onConfirm = onConfirm,
        dismissLabel = "Cancel",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No logged bodyweight found. Enter your current weight to generate a plan.")
            GlassInputField(
                label = "Weight (kg)",
                value = state.weightInput,
                onValueChange = onValueChange,
                keyboardType = KeyboardType.Decimal,
            )
            if (state.error != null) {
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
