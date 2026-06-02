package com.zack.recomptracker.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.FeaturedCard
import com.zack.recomptracker.ui.component.GlassSurfaceCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.charts.SparklineChart
import com.zack.recomptracker.ui.component.charts.MiniSparkline
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.Violet400

@Composable
fun ProgressScreen(viewModel: ProgressViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(
                    Brush.radialGradient(listOf(Color(0x298B5CF6), Color.Transparent)),
                ),
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = FloatingNavHeight + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "Progress",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.8).sp,
                    )
                }
            }

            // Range selector
            item {
                RangeSelector(
                    selected = state.rangeDays,
                    onSelect = viewModel::setRange,
                )
            }

            // Body section
            item { SectionLabel("Body") }
            item { FeaturedChartCard(state.weight) }
            item { FeaturedChartCard(state.waist) }

            // Nutrition section
            item { SectionLabel("Nutrition") }
            item { FeaturedChartCard(state.calories) }
            item { MiniChartPair(state.protein, state.carbs) }
            item { ShortChartCard(state.fat) }

            // Performance section
            item { SectionLabel("Performance") }
            item { MiniChartPair(state.adherence, state.lifts) }
        }
    }
}

// ── Range Selector ─────────────────────────────────────────────────────────────

@Composable
private fun RangeSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(7, 14, 28).forEach { days ->
            val isActive = selected == days
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) Color(0x338B5CF6) else Color(0x0DFFFFFF))
                    .border(
                        1.dp,
                        if (isActive) Color(0x598B5CF6) else Color(0x12FFFFFF),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(days) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${days}d",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFFC4B5FD) else Color(0x59FFFFFF),
                )
            }
        }
    }
}

// ── Featured Chart Card (full-width, violet tint) ─────────────────────────────

@Composable
private fun FeaturedChartCard(series: ChartSeries) {
    var scrubValue by remember { mutableStateOf<Float?>(null) }
    FeaturedCard {
        ChartHeader(series, overrideValue = scrubValue)
        Spacer(Modifier.height(10.dp))
        if (series.values.isNotEmpty() && series.values.any { it != 0f }) {
            SparklineChart(
                values = series.values,
                height = 72.dp,
                showGlowDot = true,
                showScrubber = true,
                onScrubValue = { scrubValue = it },
            )
        } else {
            NoDataLabel()
        }
    }
}

// ── Short Chart Card (full-width, surface style, 44dp chart height) ───────────

@Composable
private fun ShortChartCard(series: ChartSeries) {
    GlassSurfaceCard(cornerRadius = 18) {
        ChartHeader(series)
        Spacer(Modifier.height(8.dp))
        if (series.values.isNotEmpty() && series.values.any { it != 0f }) {
            SparklineChart(values = series.values, height = 44.dp, showGlowDot = false)
        } else {
            NoDataLabel()
        }
    }
}

// ── Mini Chart Pair (2-column) ────────────────────────────────────────────────

@Composable
private fun MiniChartPair(left: ChartSeries, right: ChartSeries) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniChartCard(left, Modifier.weight(1f))
        MiniChartCard(right, Modifier.weight(1f))
    }
}

@Composable
private fun MiniChartCard(series: ChartSeries, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = series.title.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0x66FFFFFF),
            letterSpacing = 0.08.sp,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = series.currentValue?.let {
                    if (series.unit == "%") "${"%.0f".format(it)}%" else "%.1f".format(it)
                } ?: "—",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.8).sp,
                lineHeight = 20.sp,
            )
            if (series.currentValue != null && series.unit != "%") {
                Text(
                    series.unit,
                    fontSize = 10.sp,
                    color = Color(0x4CFFFFFF),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        if (series.trendLabel.isNotEmpty()) {
            Text(
                text = series.trendLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (series.trendIsGood) Violet400 else ErrorRed,
            )
        }
        Spacer(Modifier.height(3.dp))
        if (series.values.isNotEmpty() && series.values.any { it != 0f }) {
            MiniSparkline(values = series.values)
        }
    }
}

// ── Chart Header ──────────────────────────────────────────────────────────────

@Composable
private fun ChartHeader(series: ChartSeries, overrideValue: Float? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = series.title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.08.sp,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val displayValue = overrideValue ?: series.currentValue
                Text(
                    text = displayValue?.let {
                        if (series.unit == "%" || series.unit == "kcal") "%.0f".format(it)
                        else "%.1f".format(it)
                    } ?: "—",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-1).sp,
                    lineHeight = 26.sp,
                )
                if (displayValue != null) {
                    Text(
                        series.unit,
                        fontSize = 12.sp,
                        color = Color(0x59FFFFFF),
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            if (series.trendLabel.isNotEmpty()) {
                Text(
                    text = series.trendLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (series.trendIsGood) Violet400 else ErrorRed,
                )
            }
        }
    }
}

@Composable
private fun NoDataLabel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("No data", fontSize = 11.sp, color = TextMuted)
    }
}
