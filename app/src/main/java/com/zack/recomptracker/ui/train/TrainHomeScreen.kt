package com.zack.recomptracker.ui.train

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.streak.StreakType
import com.zack.recomptracker.ui.LocalAppContainer
import com.zack.recomptracker.ui.streak.StreakHeroBanner
import com.zack.recomptracker.ui.streak.StreakViewModel
import com.zack.recomptracker.ui.train.component.MuscleGroupIcon
import com.zack.recomptracker.domain.workout.TrainingPlanBuilder
import com.zack.recomptracker.ui.train.component.heatColor
import com.zack.recomptracker.domain.workout.WorkoutProgressAnalyzer
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.ScreenHeader
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TrainHomeScreen(
    viewModel: TrainViewModel,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Long) -> Unit,
    // No sessionId: there is only ever one ACTIVE session at a time, which the Active
    // Session screen observes directly via observeActiveSession(). These just navigate.
    onStart: () -> Unit,
    onResume: () -> Unit,
    onOpenSession: (Long) -> Unit = {},
    onOpenExerciseStats: (Long) -> Unit = {},
    onAskCoach: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    val streakVm: StreakViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LocalAppContainer.current.viewModelFactory,
    )
    val streakUi by streakVm.uiState.collectAsStateWithLifecycle()
    val workoutStreak = streakUi.streaks.workout

    // History grouped by month (newest first). Computed once per history change rather than
    // on every recomposition — the sort + per-row date parse/format is otherwise re-run each pass.
    val historyGrouped = remember(state.history) {
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy").withLocale(java.util.Locale.ENGLISH)
        state.history
            .sortedByDescending { it.date }
            .groupBy { session ->
                runCatching { LocalDate.parse(session.date).format(monthFormatter).uppercase() }
                    .getOrElse { "UNKNOWN" }
            }
            .entries
            .toList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FloatingNavHeight + 16.dp),
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        item {
            ScreenHeader(
                title = "Train",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accent.accent)
                            .clickable { onCreateRoutine() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create routine",
                            tint = accent.onAccent,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                },
            )
        }

        // ── Workout-streak hero banner (only with some history) ────────────────
        if (workoutStreak.longest > 0) {
            item {
                StreakHeroBanner(
                    result = workoutStreak,
                    type = StreakType.WORKOUT,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
            }
        }

        // ── Today's training plan (train/rest + focus + recommended routine) ───
        state.plan?.let { plan ->
            item {
                TrainingPlanCard(
                    plan = plan,
                    onStartRoutine = { routineId ->
                        state.routines.firstOrNull { it.id == routineId }?.let { template ->
                            scope.launch {
                                viewModel.startSession(template)
                                onStart()
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
            }
        }

        // ── Recovery readiness (recovery framing + coach handoff) ──────────────
        state.readiness?.let { readiness ->
            item {
                ReadinessCard(
                    readiness = readiness,
                    onAskCoach = onAskCoach,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
            }
        }

        // ── Routines / History segmented pill ──────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(4.dp),
            ) {
                TrainTab.entries.forEach { tab ->
                    val isActive = state.tab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isActive) accent.accent else Color.Transparent)
                            .clickable { viewModel.selectTab(tab) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (tab) {
                                TrainTab.ROUTINES -> "Routines"
                                TrainTab.HISTORY -> "History"
                                TrainTab.STATS -> "Stats"
                            },
                            style = AppType.body.copy(
                                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                            ),
                            color = if (isActive) accent.onAccent else appColors.textPrimary.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }

        // ── Resume banner (only when active session + on routines tab) ─────────
        if (state.activeSession != null && state.tab == TrainTab.ROUTINES) {
            item {
                val session = state.activeSession!!
                val completedCount = session.exercises.sumOf { ex -> ex.sets.count { it.completed } }
                val totalCount = session.exercises.sumOf { ex -> ex.sets.size }
                FrostedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 14.dp)
                        .clickable { onResume() },
                    contentPadding = 11.dp,
                    surfaceTint = accent.tintedSurface,
                    borderColor = accent.tintedBorder,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = accent.accentLighter,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Resume \"${session.workoutName}\"",
                                style = AppType.body,
                                color = appColors.textPrimary,
                            )
                            Text(
                                text = "In progress · $completedCount of $totalCount done",
                                style = AppType.label,
                                color = appColors.textMuted,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = appColors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // ── Routines tab ───────────────────────────────────────────────────────
        if (state.tab == TrainTab.ROUTINES) {
            item {
                SectionLabel(
                    text = "My routines · ${state.routines.size}",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                )
            }

            if (state.routines.isEmpty()) {
                item { EmptyRoutinesCard(onCreateRoutine = onCreateRoutine) }
            } else {
                items(state.routines, key = { it.id }) { template ->
                    RoutineCard(
                        template = template,
                        onCardClick = { onEditRoutine(template.id) },
                        onStart = {
                            scope.launch {
                                viewModel.startSession(template)
                                onStart()
                            }
                        },
                        onEditClick = { onEditRoutine(template.id) },
                        onDeleteClick = { viewModel.deleteRoutine(template.id) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }

        // ── History tab ────────────────────────────────────────────────────────
        if (state.tab == TrainTab.HISTORY) {
            if (state.history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No workouts yet — start one from Routines.",
                            style = AppType.body,
                            color = appColors.textMuted,
                        )
                    }
                }
            } else {
                historyGrouped.forEach { (monthLabel, sessions) ->
                    item(key = "header_$monthLabel") {
                        SectionLabel(
                            text = monthLabel,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = 6.dp),
                        )
                    }
                    items(sessions, key = { it.id }) { session ->
                        HistoryCard(
                            session = session,
                            onClick = { onOpenSession(session.id) },
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                        )
                    }
                }
            }
        }

        // ── Stats tab ──────────────────────────────────────────────────────────
        if (state.tab == TrainTab.STATS) {
            item { StatsContent(state = state, onOpenExerciseStats = onOpenExerciseStats) }
        }
    }
}

// ── Recovery-readiness card (recovery framing + a coach handoff) ──────────────

@Composable
private fun ReadinessCard(
    readiness: TrainingReadinessMapper.Readiness,
    onAskCoach: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    TintedCard(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = "Recovery")
        Spacer(Modifier.height(8.dp))
        Text(
            text = readiness.headline,
            style = AppType.cardTitle,
            color = appColors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = readiness.adaptHint,
            style = AppType.body,
            color = appColors.textSecondary,
        )
        readiness.loadNote?.let { note ->
            Spacer(Modifier.height(2.dp))
            Text(text = note, style = AppType.cardSubtitle, color = appColors.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        LiquidActionButton(
            text = readiness.actionLabel,
            onClick = onAskCoach,
            isPrimary = true,
            small = true,
        )
    }
}

// ── Today's training-plan card (deterministic: train/rest + focus + routine) ──

@Composable
private fun TrainingPlanCard(
    plan: TrainingPlanBuilder.TrainingPlan,
    onStartRoutine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val isTrain = plan.verdict == TrainingPlanBuilder.Verdict.TRAIN

    // Secondary line: recommended routine + a "keep it light" tempering when recovery is down.
    val subLine = buildList {
        if (isTrain && plan.recommendedRoutineName != null) add("Recommended: ${plan.recommendedRoutineName}")
        if (plan.recoveryTempered) add("keep it light")
    }.joinToString(" · ")

    // Cadence footnote: sessions this week (vs target) + how long since the last one.
    val cadence = buildString {
        append("${plan.sessionsThisWeek}")
        plan.weeklyTarget?.let { append("/$it") }
        append(" this week")
        plan.daysSinceLastSession?.let { d -> if (d > 0) append(" · last trained ${d}d ago") }
    }

    FrostedCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 14.dp,
    ) {
        SectionLabel(text = "Today")
        Spacer(Modifier.height(8.dp))
        Text(
            text = plan.reason,
            style = AppType.cardTitle,
            color = appColors.textPrimary,
        )
        if (subLine.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subLine,
                style = AppType.body,
                color = if (isTrain) accent.inkLight else appColors.textMuted,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(text = cadence, style = AppType.cardSubtitle, color = appColors.textMuted)
        // The one useful action: launch the recommended routine. Rest days (or no match) show none.
        if (isTrain && plan.recommendedRoutineId != null && plan.recommendedRoutineName != null) {
            Spacer(Modifier.height(12.dp))
            LiquidActionButton(
                text = "Start ${plan.recommendedRoutineName}",
                onClick = { onStartRoutine(plan.recommendedRoutineId) },
                isPrimary = true,
                small = true,
            )
        }
    }
}

// ── Routine card ──────────────────────────────────────────────────────────────

@Composable
private fun RoutineCard(
    template: WorkoutTemplate,
    onCardClick: () -> Unit,
    onStart: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val totalSets = template.exercises.sumOf { it.plannedSets.size }
    val exerciseCount = template.exercises.size
    val hasExercises = exerciseCount > 0

    FrostedCard(
        modifier = modifier
            .clickable { onCardClick() },
        contentPadding = 13.dp,
    ) {
        // Name + overflow menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = AppType.cardTitle,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$exerciseCount exercises · $totalSets sets",
                    style = AppType.label,
                    color = appColors.textMuted,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Box {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = appColors.textMuted,
                    modifier = Modifier
                        .size(17.dp)
                        .clickable { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onEditClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = ErrorRed) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                        },
                        onClick = {
                            menuOpen = false
                            showDeleteDialog = true
                        },
                    )
                }
            }
        }

        // Exercise thumbnails (up to 3)
        if (template.exercises.isNotEmpty()) {
            Spacer(Modifier.height(11.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                template.exercises.take(3).forEach { ex ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(CornerSmall))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            MuscleGroupIcon(
                                primaryMuscles = ex.exercise.primaryMuscles,
                                tint = accent.accentLighter,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Text(
                            text = ex.exercise.name,
                            style = AppType.body,
                            color = appColors.textPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${ex.plannedSets.size} sets",
                            style = AppType.label,
                            color = appColors.textMuted,
                        )
                    }
                }
            }
        }

        // "and X more" + Start button row
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val moreCount = template.exercises.size - 3
            if (moreCount > 0) {
                Text(
                    text = "and $moreCount more",
                    style = AppType.label,
                    color = appColors.textMuted,
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }

            LiquidGlassButton(
                onClick = onStart,
                enabled = hasExercises,
                tint = accent.accent,
                surfaceColor = Color.White.copy(alpha = 0.08f),
                buttonHeight = 36.dp,
                modifier = Modifier.width(110.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = accent.onAccent,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Start",
                    style = AppType.body,
                    color = accent.onAccent,
                )
            }
        }

        // Disabled hint when no exercises
        if (!hasExercises) {
            Text(
                text = "Add exercises to start",
                style = AppType.label,
                color = appColors.textMuted,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete \"${template.name}\"?") },
            text = { Text("This routine will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteClick()
                }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyRoutinesCard(
    onCreateRoutine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current

    FrostedCard(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        contentPadding = 24.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = appColors.textMuted,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "No routines yet",
                style = AppType.cardTitle,
                color = appColors.textPrimary,
            )
            Text(
                text = "Build your first workout from 870+ exercises",
                style = AppType.body,
                color = appColors.textMuted,
            )
            Spacer(Modifier.height(4.dp))
            LiquidGlassButton(
                onClick = onCreateRoutine,
                tint = accent.accent,
                surfaceColor = Color.White.copy(alpha = 0.08f),
                buttonHeight = 44.dp,
                modifier = Modifier.fillMaxWidth(0.65f),
            ) {
                Text(
                    text = "Create routine",
                    style = AppType.body,
                    color = accent.onAccent,
                )
            }
        }
    }
}

// ── History card (mockup 6) ───────────────────────────────────────────────────

@Composable
private fun HistoryCard(
    session: WorkoutSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    val completedSets = session.exercises.sumOf { ex -> ex.sets.count { it.completed } }
    val allSets = session.exercises.flatMap { it.sets }
    val volume = WorkoutProgressAnalyzer.sessionVolume(allSets)
    val durationMin = session.durationSeconds?.let { it / 60 }

    val dateFormatted = runCatching {
        LocalDate.parse(session.date).format(DateTimeFormatter.ofPattern("MMM d"))
    }.getOrElse { session.date }

    FrostedCard(
        modifier = modifier.clickable { onClick() },
        contentPadding = 13.dp,
    ) {
        // Name + date row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = session.workoutName,
                style = AppType.cardTitle,
                color = appColors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dateFormatted,
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Stats row
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (durationMin != null && durationMin > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = appColors.textMuted,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "$durationMin min", style = AppType.cardSubtitle, color = appColors.textMuted)
                }
                Text(text = "·", style = AppType.cardSubtitle, color = appColors.textMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FormatListNumbered,
                    contentDescription = null,
                    tint = appColors.textMuted,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "$completedSets sets", style = AppType.cardSubtitle, color = appColors.textMuted)
            }
            if (volume > 0) {
                Text(text = "·", style = AppType.cardSubtitle, color = appColors.textMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = appColors.textMuted,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "${volume.roundToInt()} kg", style = AppType.cardSubtitle, color = appColors.textMuted)
                }
            }
        }
    }
}

// ── Stats tab content ───────────────────────────────────────────────────────────

@Composable
private fun StatsContent(
    state: TrainUiState,
    onOpenExerciseStats: (Long) -> Unit,
) {
    val appColors = LocalAppColors.current
    var selected by remember { mutableStateOf<com.zack.recomptracker.domain.workout.MuscleCategory?>(null) }

    val anyLogged = state.statsCategories.any { it.exercises.isNotEmpty() }

    // Heat = each muscle's ABSOLUTE weekly set count. heatColor maps it to a training-status colour
    // (undertrained → green → overtrained red); 0-set groups render faint on the body map.
    val heat = state.plan?.let { plan ->
        plan.weekly.associate { it.category to it.setsThisWeek.toFloat() }
    } ?: emptyMap()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        com.zack.recomptracker.ui.train.component.BodyMap(
            selected = selected,
            onMuscleTap = { category -> selected = if (selected == category) null else category },
            intensities = heat,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )

        if (!anyLogged) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Log a workout to see your stats by muscle.",
                    style = AppType.body,
                    color = appColors.textMuted,
                )
            }
            return@Column
        }

        // ── This week's muscle balance (collapsible; ranked lagging-first) ──
        state.plan?.let { plan -> WeeklyBalanceSection(plan) }

        SectionLabel(
            text = "By muscle",
            modifier = Modifier.padding(bottom = 8.dp),
        )

        state.statsCategories.forEach { cat ->
            MuscleCategoryRow(
                category = cat,
                expanded = selected == cat.category,
                onToggle = { selected = if (selected == cat.category) null else cat.category },
                onOpenExerciseStats = onOpenExerciseStats,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/** Collapsible "This week" muscle-balance card (ranked lagging-first). Collapsed = a one-line
 *  summary of the lagging groups; expanded = a mini volume bar + recency per muscle, plus a legend. */
@Composable
private fun WeeklyBalanceSection(plan: TrainingPlanBuilder.TrainingPlan) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    var expanded by remember { mutableStateOf(false) }
    val maxSets = plan.weekly.maxOfOrNull { it.setsThisWeek } ?: 0
    val ranked = remember(plan.weekly) { plan.weekly.sortedBy { it.setsThisWeek } } // lagging first
    val chevronRot by animateFloatAsState(if (expanded) 90f else 0f, tween(300), label = "wk-chev")
    val lagging = plan.focus.joinToString(", ") { it.displayName.lowercase() }

    FrostedCard(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(CornerCard))
            .clickable { expanded = !expanded }
            .animateContentSize(),
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(text = "This week")
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (expanded) "Volume by muscle group"
                    else if (lagging.isNotEmpty()) "Lagging: $lagging"
                    else "Balanced across muscle groups",
                    style = AppType.cardSubtitle,
                    color = when {
                        expanded -> appColors.textSecondary
                        lagging.isNotEmpty() -> accent.inkLight
                        else -> appColors.textSecondary
                    },
                )
            }
            // A bordered circular chevron reads clearly as a tap affordance (matches the app's
            // back button), so the whole card obviously expands.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(appColors.cardSurface)
                    .border(1.dp, appColors.cardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse this week" else "Expand this week",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(18.dp).rotate(chevronRot),
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                ranked.forEach { cw ->
                    WeeklyBarRow(week = cw, maxSets = maxSets, isFocus = cw.category in plan.focus)
                }
            }
            Spacer(Modifier.height(14.dp))
            HeatLegend()
        }
    }
}

/** Compact colour key for the volume bars: none → trained → overtrained. */
@Composable
private fun HeatLegend() {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(fill = null, label = "None")
        LegendDot(fill = heatColor(14f), label = "Trained")
        LegendDot(fill = heatColor(30f), label = "Too much")
    }
}

/** One legend swatch: a filled dot ([fill]) or a hollow ring (fill = null → "not trained") + label. */
@Composable
private fun LegendDot(fill: Color?, label: String) {
    val appColors = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .then(
                    if (fill != null) Modifier.background(fill)
                    else Modifier.border(1.dp, appColors.textMuted, CircleShape),
                ),
        )
        Text(text = label, style = AppType.metaLabel, color = appColors.textMuted)
    }
}

/** One muscle group's week: name · volume bar (∝ sets, vs the most-trained group) · sets + recency.
 *  Lagging groups (in the plan's focus) render in the accent colour. */
@Composable
private fun WeeklyBarRow(week: TrainingPlanBuilder.CategoryWeek, maxSets: Int, isFocus: Boolean) {
    val appColors = LocalAppColors.current
    val trained = week.setsThisWeek > 0
    val frac = if (maxSets > 0) week.setsThisWeek.toFloat() / maxSets else 0f // bar WIDTH: relative fill
    val barColor = heatColor(week.setsThisWeek.toFloat()) // COLOR: absolute training-status
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = week.category.displayName,
            style = AppType.cardSubtitle.copy(fontWeight = if (isFocus) FontWeight.SemiBold else FontWeight.Normal),
            color = appColors.textPrimary,
            modifier = Modifier.width(64.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(100))
                .background(appColors.cardSurface),
        ) {
            // Untrained (0 sets) → empty track (uncolored); trained → green bar sized by volume.
            if (trained) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac.coerceIn(0.08f, 1f))
                        .height(7.dp)
                        .clip(RoundedCornerShape(100))
                        .background(barColor),
                )
            }
        }
        Text(
            text = "${week.setsThisWeek} · ${lastHitLabel(week.daysSinceLastHit)}",
            style = AppType.metaLabel,
            color = if (trained) barColor else appColors.textMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}

private fun lastHitLabel(days: Int?): String = when {
    days == null -> "—"
    days <= 0 -> "today"
    days == 1 -> "1d"
    else -> "${days}d"
}

@Composable
private fun MuscleCategoryRow(
    category: com.zack.recomptracker.domain.workout.TrainStatsBuilder.CategoryStats,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenExerciseStats: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val count = category.exercises.size

    FrostedCard(
        modifier = modifier.fillMaxWidth().clickable { onToggle() },
        contentPadding = 13.dp,
        surfaceTint = if (expanded) accent.tintedSurface else Color.Unspecified,
        borderColor = if (expanded) accent.tintedBorder else Color.Unspecified,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.category.displayName,
                style = AppType.cardTitle,
                color = appColors.textPrimary,
            )
            Text(
                text = if (count == 0) "none" else "$count exercise${if (count == 1) "" else "s"}",
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
            )
        }

        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (category.exercises.isEmpty()) {
                    Text(
                        text = "No exercises logged yet.",
                        style = AppType.body,
                        color = appColors.textMuted,
                    )
                } else {
                    category.exercises.forEach { ex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CornerSmall))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { onOpenExerciseStats(ex.exerciseId) }
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ex.name,
                                style = AppType.body,
                                color = appColors.textPrimary.copy(alpha = 0.9f),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = appColors.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
