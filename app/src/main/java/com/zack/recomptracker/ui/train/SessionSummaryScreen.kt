package com.zack.recomptracker.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.GlassAlertDialog
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.train.component.MuscleGroupIcon
import com.zack.recomptracker.ui.train.component.muscleGroupLabel
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerChip
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun SessionSummaryScreen(
    viewModel: SessionSummaryViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }

    if (state.loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Loading…", style = AppType.body, color = appColors.textMuted)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        // ── Completion header ─────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 32.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Accent circle with flag icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent.tintedSurface)
                        .border(1.dp, accent.tintedBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = accent.inkLight,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = "Workout complete",
                    style = AppType.screenTitleCompact,
                    color = appColors.textPrimary,
                )
                Text(
                    text = "${state.workoutName} · ${state.date}",
                    style = AppType.screenSubtitle,
                    color = appColors.textMuted,
                )
            }
        }

        // ── 2×2 stat tiles ────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Duration tile
                FrostedCard(
                    modifier = Modifier.weight(1f),
                    contentPadding = 14.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "DURATION",
                            style = AppType.metaLabel,
                            color = appColors.textMuted,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { showDurationDialog = true },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit duration",
                                tint = appColors.textFaint,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = formatDuration(state.durationSeconds),
                        style = AppType.statValue,
                        color = appColors.textPrimary,
                    )
                }
                // Total volume tile
                FrostedCard(
                    modifier = Modifier.weight(1f),
                    contentPadding = 14.dp,
                ) {
                    Text(
                        text = "VOLUME",
                        style = AppType.metaLabel,
                        color = appColors.textMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${state.totalVolume.toInt()} kg",
                        style = AppType.statValue,
                        color = appColors.textPrimary,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Sets tile
                FrostedCard(
                    modifier = Modifier.weight(1f),
                    contentPadding = 14.dp,
                ) {
                    Text(
                        text = "SETS",
                        style = AppType.metaLabel,
                        color = appColors.textMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${state.totalSets}",
                        style = AppType.statValue,
                        color = appColors.textPrimary,
                    )
                }
                // New PRs tile
                FrostedCard(
                    modifier = Modifier.weight(1f),
                    contentPadding = 14.dp,
                ) {
                    Text(
                        text = "NEW PRs",
                        style = AppType.metaLabel,
                        color = appColors.textMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${state.prCount}",
                        style = AppType.statValue,
                        color = if (state.prCount > 0) accent.inkLight else appColors.textPrimary,
                    )
                }
            }
        }

        // ── Exercises section label ───────────────────────────────────────────
        item {
            SectionLabel(
                text = "Exercises",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }

        // ── Exercise recap rows ───────────────────────────────────────────────
        itemsIndexed(
            state.exercises,
            // Same exercise can appear twice in a session, so key on index + name.
            key = { index, recap -> "${index}_${recap.name}" },
        ) { _, recap ->
            FrostedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                contentPadding = 14.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(CornerSmall))
                            .background(appColors.cardSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        MuscleGroupIcon(
                            primaryMuscles = recap.primaryMuscles,
                            tint = accent.accentLighter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recap.name,
                            style = AppType.cardTitle,
                            color = appColors.textPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        val group = muscleGroupLabel(recap.primaryMuscles)
                        val stats = buildRecapSubtitle(recap)
                        Text(
                            text = buildAnnotatedString {
                                if (group != null) {
                                    withStyle(
                                        SpanStyle(
                                            color = accent.inkLight,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    ) { append(group) }
                                    if (stats.isNotEmpty()) append(" · ")
                                }
                                append(stats)
                            },
                            style = AppType.cardSubtitle,
                            color = appColors.textMuted,
                        )
                    }
                    if (recap.isPr) {
                        Spacer(Modifier.width(8.dp))
                        PrBadge()
                    }
                }
                if (recap.isPr) {
                    Spacer(Modifier.height(11.dp))
                    PrCallout(recap = recap)
                }
            }
        }

        // ── Session note ──────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel(
                text = "Session Note",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
            FrostedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                contentPadding = 12.dp,
            ) {
                SummaryNoteField(
                    value = state.note,
                    onValueChange = { viewModel.setNote(it) },
                )
            }
        }

        // ── Save button ───────────────────────────────────────────────────────
        item {
            LiquidGlassButton(
                onClick = {
                    scope.launch {
                        viewModel.save()
                        onDone()
                    }
                },
                tint = accent.accent,
                surfaceColor = Color.White.copy(alpha = 0.08f),
                buttonHeight = 48.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            ) {
                Text(
                    text = "Save Workout",
                    style = AppType.cardTitle,
                    color = accent.onAccent,
                )
            }
        }

        // ── Discard button ────────────────────────────────────────────────────
        item {
            TextButton(
                onClick = { showDiscardDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "Discard Workout",
                    style = AppType.body,
                    color = ErrorRed,
                )
            }
        }
    }

    // ── Discard confirm dialog ────────────────────────────────────────────────
    if (showDiscardDialog) {
        ConfirmDialog(
            title = "Discard workout?",
            body = "This session will be permanently deleted and won't count toward your history.",
            confirmLabel = "Discard",
            isDestructive = true,
            onConfirm = {
                showDiscardDialog = false
                scope.launch {
                    viewModel.discard()
                    onDone()
                }
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    // ── Duration edit dialog ──────────────────────────────────────────────────
    if (showDurationDialog) {
        DurationEditDialog(
            currentSeconds = state.durationSeconds,
            onDismiss = { showDurationDialog = false },
            onConfirm = { totalSeconds ->
                viewModel.setDuration(totalSeconds)
                showDurationDialog = false
            },
        )
    }
}

// ── Duration picker dialog ────────────────────────────────────────────────────

@Composable
private fun DurationEditDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit, // total seconds
) {
    val (initialHours, initialMinutes) = durationToHm(currentSeconds)
    var hours by remember { mutableStateOf(initialHours) }
    var minutes by remember { mutableStateOf(initialMinutes) }

    GlassAlertDialog(
        onDismiss = onDismiss,
        title = "Edit duration",
        confirmLabel = "Set",
        onConfirm = { onConfirm(hmToSeconds(hours, minutes)) },
        dismissLabel = "Cancel",
        content = {
            DurationWheelPicker(
                hours = hours,
                minutes = minutes,
                onHoursChange = { hours = it },
                onMinutesChange = { minutes = it },
            )
        },
    )
}

// ── Live PR callout (forward-looking record banner) ───────────────────────────

@Composable
private fun PrCallout(recap: ExerciseRecap) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val callout = PrCalloutFormatter.format(recap.name, recap.topSet)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerSmall))
            .background(accent.tintedSurface)
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerSmall))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = accent.inkLight,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = callout.headline,
                style = AppType.label,
                color = accent.inkLight,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = callout.forwardLine,
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
            )
        }
    }
}

// ── PR badge chip ─────────────────────────────────────────────────────────────

@Composable
private fun PrBadge() {
    val accent = LocalAppAccent.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CornerChip))
            .background(accent.tintedSurface)
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerChip))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "PR",
            style = AppType.metaLabel,
            color = accent.inkLight,
        )
    }
}

// ── Session note text field ───────────────────────────────────────────────────

@Composable
private fun SummaryNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) accent.accent.copy(alpha = 0.45f) else appColors.frostedBorder

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = AppType.body.copy(color = appColors.textPrimary),
        cursorBrush = SolidColor(accent.accentLighter),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerSmall))
            .background(appColors.cardSurface)
            .border(1.dp, borderColor, RoundedCornerShape(CornerSmall))
            .onFocusChanged { fs -> focused = fs.isFocused }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        minLines = 3,
        maxLines = 6,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = "How did this session go?",
                    style = AppType.body,
                    color = appColors.textMuted,
                )
            }
            inner()
        },
    )
}

// ── Duration wheel picker ─────────────────────────────────────────────────────

private val WheelItemHeight = 40.dp
private const val WheelVisibleCount = 5

@Composable
private fun WheelPicker(
    count: Int,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (count - 1).coerceAtLeast(0)),
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - center) }
                ?.index ?: selectedIndex
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (!scrolling) onSelectedChange(centeredIndex) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = modifier
                .width(64.dp)
                .height(WheelItemHeight * WheelVisibleCount),
            contentAlignment = Alignment.Center,
        ) {
            // Center selection band
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight)
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(accent.tintedSurface)
                    .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerSmall)),
            )
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    vertical = WheelItemHeight * ((WheelVisibleCount - 1) / 2),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(count) { index ->
                    val distance = abs(index - centeredIndex)
                    val alpha = when (distance) {
                        0 -> 1f
                        1 -> 0.55f
                        2 -> 0.3f
                        else -> 0.15f
                    }
                    Box(
                        modifier = Modifier
                            .height(WheelItemHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = index.toString(),
                            fontSize = 20.sp,
                            fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium,
                            color = appColors.textPrimary.copy(alpha = alpha),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = AppType.body,
            color = appColors.textMuted,
        )
    }
}

@Composable
private fun DurationWheelPicker(
    hours: Int,
    minutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelPicker(
            count = MAX_DURATION_HOURS + 1,
            selectedIndex = hours,
            onSelectedChange = onHoursChange,
            label = "h",
        )
        Spacer(Modifier.width(20.dp))
        WheelPicker(
            count = 60,
            selectedIndex = minutes,
            onSelectedChange = onMinutesChange,
            label = "min",
        )
    }
}

// ── Formatters ────────────────────────────────────────────────────────────────

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) {
        "%d:%02d".format(h, m)
    } else {
        "${m} min"
    }
}

private fun buildRecapSubtitle(recap: ExerciseRecap): String {
    val parts = mutableListOf<String>()
    if (recap.sets > 0) parts.add("${recap.sets} sets")
    if (recap.topSet.isNotEmpty()) parts.add("top ${recap.topSet}")
    if (recap.volume > 0) parts.add("${recap.volume.toInt()} kg")
    return parts.joinToString(" · ")
}
