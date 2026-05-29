package com.zack.recomptracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.core.util.formatOneDecimal
import com.zack.recomptracker.core.util.formatPercent
import com.zack.recomptracker.core.util.formatSignedOneDecimal
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.ui.component.CalorieZoneBar
import com.zack.recomptracker.ui.component.MacroMiniBar
import com.zack.recomptracker.ui.component.SectionCard

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Current status and calorie decision")
            }
        }
        item {
            SectionCard("Calorie verdict") {
                Text(state.result.verdict.label(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.result.summary)
                Text("Change: ${state.result.recommendedCalorieChange} kcal/day")
                Text("Reasons: ${state.result.reasonCodes.joinToString()}")
            }
        }
        item {
            SectionCard("Current targets") {
                Text("${state.preferences.targetCalories} kcal")
                Text("${state.preferences.targetProteinG}P / ${state.preferences.targetCarbsG}C / ${state.preferences.targetFatG}F")
            }
        }
        item {
            SectionCard("Today") {
                CalorieZoneBar(
                    eaten = state.todayTotals.calories,
                    zoneLower = state.preferences.calorieZoneLowerBound,
                    zoneUpper = state.preferences.calorieZoneUpperBound,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroMiniBar(
                        label = "Protein",
                        eaten = state.todayTotals.proteinG,
                        target = state.preferences.targetProteinG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Carbs",
                        eaten = state.todayTotals.carbsG,
                        target = state.preferences.targetCarbsG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Fat",
                        eaten = state.todayTotals.fatG,
                        target = state.preferences.targetFatG,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            SectionCard("Trend summary") {
                StatRow("7-day weight average", state.sevenDayWeightAverage?.let { "${it.formatOneDecimal()} kg" } ?: "No data")
                StatRow("Weight trend", "${state.weightTrendKgPerWeek.formatSignedOneDecimal()} kg/week")
                StatRow("Waist trend", "${state.waistTrendCmPerWeek.formatSignedOneDecimal()} cm/week")
                StatRow("Adherence", state.adherencePercent.formatPercent())
                StatRow("Logged days", state.daysLogged.toString())
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun AdjustmentVerdict.label(): String = when (this) {
    AdjustmentVerdict.WAIT_FOR_DATA -> "Wait"
    AdjustmentVerdict.HOLD -> "Hold"
    AdjustmentVerdict.INCREASE_CALORIES -> "Increase"
    AdjustmentVerdict.REDUCE_CALORIES -> "Reduce"
}
