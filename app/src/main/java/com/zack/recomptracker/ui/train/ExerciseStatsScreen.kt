package com.zack.recomptracker.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.workout.ExerciseStatsCalculator
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.component.VioletBadge
import com.zack.recomptracker.ui.component.charts.ProgressLineChart
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class ChartMetric(val label: String) { ONE_RM("Est. 1RM"), TOP_SET("Top set"), VOLUME("Volume") }

// Immutable + thread-safe — hoisted to file scope instead of rebuilt on every friendlyDate() call
// (friendlyDate runs once per row inside the recent-sessions items() list).
private val friendlyDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun ExerciseStatsScreen(
    viewModel: ExerciseStatsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            SubScreenHeader(
                title = state.exerciseName,
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        val stats = state.stats
        if (state.loading) {
            item { CenterText("Loading…") }
        } else if (stats == null || !stats.hasData) {
            item { CenterText("No history for this exercise yet.") }
        } else {
            item {
                state.primaryMuscleLabel?.let { muscle ->
                    val label = listOfNotNull(state.category?.displayName, muscle).joinToString(" · ")
                    VioletBadge(text = label.uppercase(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                }
            }
            item { QuickStats(stats, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) }
            item { ChartCard(stats, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) }
            item { PersonalRecords(stats, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) }
            item {
                SectionLabel("RECENT SESSIONS", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            items(stats.recentSessions.take(8)) { day ->
                RecentSessionCard(day, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    val appColors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = AppType.body, color = appColors.textMuted)
    }
}

@Composable
private fun QuickStats(stats: ExerciseStatsCalculator.ExerciseStats, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("EST. 1RM (BEST)", stats.bestOneRepMax?.let { "${it.roundToInt()} kg" } ?: "—", Modifier.weight(1f))
            StatChip("HEAVIEST SET", if (stats.heaviestWeightKg != null) "${stats.heaviestWeightKg!!.roundToInt()} kg × ${stats.heaviestReps}" else "${stats.maxReps} reps", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("FREQUENCY", stats.sessionsPerWeek?.let { "${(it * 10).roundToInt() / 10.0}× / wk" } ?: "—", Modifier.weight(1f))
            StatChip("LAST DONE", stats.lastPerformedDate?.let { friendlyDate(it) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    FrostedCard(modifier = modifier, contentPadding = 11.dp) {
        Text(text = label, style = AppType.metaLabel, color = appColors.textFaint)
        Spacer(Modifier.height(3.dp))
        Text(text = value, style = AppType.statValueSmall, color = appColors.textPrimary)
    }
}

@Composable
private fun ChartCard(stats: ExerciseStatsCalculator.ExerciseStats, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    var metric by remember { mutableStateOf(ChartMetric.ONE_RM) }

    val values: List<Float> = when (metric) {
        ChartMetric.ONE_RM -> stats.oneRepMaxSeries.map { it.value.toFloat() }
        ChartMetric.TOP_SET -> stats.topSetSeries.map { it.value.toFloat() }
        ChartMetric.VOLUME -> stats.volumeSeries.map { it.value.toFloat() }
    }

    FrostedCard(modifier = modifier.fillMaxWidth(), contentPadding = 13.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartMetric.entries.forEach { m ->
                val active = m == metric
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) accent.accent else Color.White.copy(alpha = 0.06f))
                        .clickable { metric = m }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = m.label,
                        style = AppType.label.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                        color = if (active) accent.onAccent else appColors.textMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressLineChart(values = values)
    }
}

@Composable
private fun PersonalRecords(stats: ExerciseStatsCalculator.ExerciseStats, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionLabel("PERSONAL RECORDS", modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrChip("MAX WEIGHT", stats.heaviestWeightKg?.let { "${it.roundToInt()} kg" } ?: "—", Modifier.weight(1f))
            PrChip("MOST REPS", stats.maxReps?.toString() ?: "—", Modifier.weight(1f))
            PrChip("BEST VOL/DAY", stats.bestDayVolume?.let { it.roundToInt().toString() } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PrChip(label: String, value: String, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    FrostedCard(modifier = modifier, contentPadding = 10.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = AppType.metaLabel, color = appColors.textFaint)
            Spacer(Modifier.height(3.dp))
            Text(text = value, style = AppType.statValueSmall, color = appColors.textPrimary)
        }
    }
}

@Composable
private fun RecentSessionCard(day: ExerciseStatsCalculator.DaySession, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    FrostedCard(modifier = modifier.fillMaxWidth(), contentPadding = 11.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = friendlyDate(day.date), style = AppType.cardTitle, color = appColors.textPrimary)
            Text(text = "vol ${day.volume.roundToInt()}", style = AppType.cardSubtitle, color = appColors.textMuted)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = day.sets.joinToString(" · ") { s ->
                val w = s.weightKg?.let { "${it.roundToInt()}kg" } ?: "BW"
                "${s.reps}×$w"
            },
            style = AppType.cardSubtitle,
            color = appColors.textPrimary.copy(alpha = 0.7f),
        )
    }
}

private fun friendlyDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(friendlyDateFormatter) }.getOrDefault(iso)
