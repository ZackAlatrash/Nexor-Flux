package com.zack.recomptracker.ui.train

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TrainHomeScreen(
    viewModel: TrainViewModel,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Long) -> Unit,
    onStart: (sessionId: Long) -> Unit,
    onResume: (sessionId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FloatingNavHeight + 16.dp),
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Train",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = appColors.textPrimary,
                )
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
            }
        }

        // ── Routines / History segmented pill ──────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(4.dp),
            ) {
                TrainTab.values().forEach { tab ->
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
                            text = if (tab == TrainTab.ROUTINES) "Routines" else "History",
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
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
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 14.dp)
                        .clickable { onResume(session.id) },
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
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = appColors.textPrimary,
                            )
                            Text(
                                text = "In progress · $completedCount of $totalCount done",
                                fontSize = 11.sp,
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
                Text(
                    text = "MY ROUTINES · ${state.routines.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = appColors.textPrimary.copy(alpha = 0.55f),
                    letterSpacing = 0.4.sp,
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
                                val sid = viewModel.startSession(template)
                                onStart(sid)
                            }
                        },
                        onEditClick = { onEditRoutine(template.id) },
                        onDeleteClick = { viewModel.deleteRoutine(template.id) },
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
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
                            .padding(horizontal = 14.dp)
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No workouts yet — start one from Routines.",
                            fontSize = 14.sp,
                            color = appColors.textMuted,
                        )
                    }
                }
            } else {
                items(state.history, key = { it.id }) { session ->
                    HistoryCard(
                        session = session,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 12.dp),
                    )
                }
            }
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$exerciseCount exercises · $totalSets sets",
                    fontSize = 11.sp,
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
                        val imageUrl = ex.exercise.images.firstOrNull()
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(CornerSmall))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (imageUrl != null) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = ex.exercise.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = accent.accentLighter,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            text = ex.exercise.name,
                            fontSize = 13.sp,
                            color = appColors.textPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${ex.plannedSets.size} sets",
                            fontSize = 11.sp,
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
                    fontSize = 11.sp,
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent.onAccent,
                )
            }
        }

        // Disabled hint when no exercises
        if (!hasExercises) {
            Text(
                text = "Add exercises to start",
                fontSize = 11.sp,
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
            .padding(horizontal = 14.dp)
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = appColors.textPrimary,
            )
            Text(
                text = "Build your first workout from 870+ exercises",
                fontSize = 13.sp,
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent.onAccent,
                )
            }
        }
    }
}

// ── History card (minimal, full styling is a later task) ─────────────────────

@Composable
private fun HistoryCard(
    session: WorkoutSession,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val totalSets = session.exercises.sumOf { it.sets.size }
    val dateFormatted = runCatching {
        LocalDate.parse(session.date).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }.getOrElse { session.date }

    FrostedCard(
        modifier = modifier,
        contentPadding = 13.dp,
    ) {
        Text(
            text = session.workoutName,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = appColors.textPrimary,
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = dateFormatted,
                fontSize = 12.sp,
                color = appColors.textMuted,
            )
            Text(
                text = "·",
                fontSize = 12.sp,
                color = appColors.textMuted,
            )
            Text(
                text = "$totalSets sets",
                fontSize = 12.sp,
                color = appColors.textMuted,
            )
        }
    }
}
