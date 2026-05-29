package com.zack.recomptracker.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.zack.recomptracker.ui.component.SectionCard

@Composable
fun ProgressScreen(viewModel: ProgressViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 28).forEach { days ->
                        FilterChip(
                            selected = state.rangeDays == days,
                            onClick = { viewModel.setRange(days) },
                            label = { Text("${days}d") },
                        )
                    }
                }
            }
        }
        item { ChartCard(state.weight) }
        item { ChartCard(state.waist) }
        item { ChartCard(state.calories) }
        item { ChartCard(state.protein) }
        item { ChartCard(state.carbs) }
        item { ChartCard(state.fat) }
        item { ChartCard(state.adherence) }
        item { ChartCard(state.lifts) }
    }
}

@Composable
private fun ChartCard(series: ChartSeries) {
    SectionCard("${series.title} (${series.unit})") {
        if (series.values.isEmpty() || series.values.all { it == 0f }) {
            Text("No data for this range.")
        } else {
            VicoLineChart(series.values)
        }
    }
}

@Composable
private fun VicoLineChart(values: List<Float>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        modelProducer.runTransaction {
            lineSeries {
                series(values)
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}
