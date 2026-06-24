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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.workout.ExerciseTrendPoint
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.train.component.SetGrid
import com.zack.recomptracker.ui.train.component.SetGridMode
import com.zack.recomptracker.ui.train.component.SessionSetRow
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt

@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    if (state.loading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = accent.accent)
        }
        return
    }

    // Format date
    val dateFormatted = runCatching {
        LocalDate.parse(state.date).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }.getOrElse { state.date }

    val durationMin = state.durationSeconds / 60
    val volumeDisplay = if (state.totalVolume == 0.0) "0 kg"
    else "${state.totalVolume.roundToInt()} kg"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        item {
            SubScreenHeader(
                title = state.workoutName,
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 16.dp),
                subtitle = buildString {
                    append(dateFormatted)
                    if (durationMin > 0) append(" · $durationMin min")
                    if (state.totalVolume > 0) append(" · $volumeDisplay")
                },
            )
        }

        item { Spacer(Modifier.height(12.dp)) }

        // ── Exercise cards ─────────────────────────────────────────────────────
        items(state.exercises) { ex ->
            ExerciseDetailCard(
                exercise = ex,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp),
            )
        }

        if (state.exercises.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No exercises logged.",
                        style = AppType.body,
                        color = appColors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseDetailCard(
    exercise: ExerciseDetail,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    FrostedCard(
        modifier = modifier,
        contentPadding = 14.dp,
    ) {
        // ── Exercise title row + PR badge ──────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = exercise.name,
                style = AppType.cardTitle,
                color = appColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (exercise.isPr) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(accent.tintedSurface)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "PR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent.accentLighter,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Read-only set grid ─────────────────────────────────────────────────
        SetGrid(
            mode = SetGridMode.READONLY,
            sets = emptyList(),
            onAddSet = {},
            onRemoveSet = {},
            onSetChanged = { _, _, _ -> },
            sessionSets = exercise.sets.mapIndexed { idx, s ->
                SessionSetRow(
                    id = s.id,
                    setNumber = idx + 1,
                    prev = null,
                    reps = s.reps,
                    weightKg = s.weightKg,
                    rir = s.rir,
                    completed = s.completed,
                )
            },
        )

        Spacer(Modifier.height(16.dp))

        // ── Est-1RM sparkline ──────────────────────────────────────────────────
        val trendValues = exercise.trend.mapNotNull { it.bestEstimatedOneRepMax }
        if (trendValues.size >= 2) {
            // Draw sparkline
            val accentColor = accent.accent
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                drawSparkline(trendValues, accentColor)
            }
            // Delta label
            if (exercise.deltaLabel != null) {
                Text(
                    text = exercise.deltaLabel,
                    style = AppType.cardSubtitle.copy(fontWeight = FontWeight.Medium),
                    color = accent.accentLighter,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Text(
                text = "Not enough data yet",
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
            )
        }
    }
}

private fun DrawScope.drawSparkline(
    values: List<Double>,
    color: Color,
) {
    if (values.size < 2) return
    val minV = values.min()
    val maxV = values.max()
    val rangeV = (maxV - minV).let { if (it == 0.0) 1.0 else it }
    val padV = size.height * 0.1f
    val drawH = size.height - 2 * padV

    val points = values.mapIndexed { i, v ->
        val x = i / (values.size - 1).toFloat() * size.width
        val y = padV + (1f - ((v - minV) / rangeV).toFloat()) * drawH
        Offset(x, y)
    }

    // Baseline
    drawLine(
        color = color.copy(alpha = 0.15f),
        start = Offset(0f, size.height - padV),
        end = Offset(size.width, size.height - padV),
        strokeWidth = 1.dp.toPx(),
    )

    // Polyline
    val path = Path()
    points.forEachIndexed { i, pt ->
        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // Dot at last point
    drawCircle(
        color = color,
        radius = 3.dp.toPx(),
        center = points.last(),
    )
}
