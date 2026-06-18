package com.zack.recomptracker.ui.train

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.GlassTextArea
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.train.component.ExerciseCard
import com.zack.recomptracker.ui.train.component.SetGrid
import com.zack.recomptracker.ui.train.component.SetGridMode
import com.zack.recomptracker.ui.train.component.SetRowData
import com.zack.recomptracker.ui.train.component.rememberDragHaptics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch

/**
 * Routine Builder screen — create or edit a workout template.
 *
 * @param viewModel            Builder ViewModel.
 * @param pickedExerciseIds    Non-null when returning from the Exercise Picker; the ids to add.
 * @param onPickedConsumed     Called once the ids have been processed so the handle is cleared.
 * @param onClose              Close / discard (a confirm dialog is shown if there are unsaved changes).
 * @param onAddExercise        Navigate to Exercise Picker.
 * @param onSaved              Called after a successful save (e.g. pop back stack).
 */
@Composable
fun RoutineBuilderScreen(
    viewModel: RoutineBuilderViewModel,
    pickedExerciseIds: LongArray?,
    onPickedConsumed: () -> Unit,
    onClose: () -> Unit,
    onAddExercise: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    // ── Consume picker result ─────────────────────────────────────────────────
    LaunchedEffect(pickedExerciseIds) {
        pickedExerciseIds?.let { ids ->
            viewModel.addExercises(ids)
            onPickedConsumed()
        }
    }

    // ── Discard confirm dialog ────────────────────────────────────────────────
    var showDiscardDialog by remember { mutableStateOf(false) }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?", color = appColors.textPrimary) },
            text = {
                Text(
                    "You have unsaved changes to this routine.",
                    color = appColors.textMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onClose()
                }) {
                    Text("Discard", color = com.zack.recomptracker.ui.theme.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing", color = accent.inkLight)
                }
            },
            containerColor = appColors.frostedSurface,
        )
    }

    // ── Total planned set count ───────────────────────────────────────────────
    val totalSets = state.exercises.sumOf { it.sets.size }

    val lazyListState = rememberLazyListState()
    val dragHaptics = rememberDragHaptics()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = state.exercises.indexOfFirst { it.id == from.key }
        val toIdx = state.exercises.indexOfFirst { it.id == to.key }
        if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
            viewModel.reorder(fromIdx, toIdx)
            dragHaptics.move()
        }
    }

    // ── Screen layout ─────────────────────────────────────────────────────────
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {

            // ── Header: ✕ · title · Save ──────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (state.isDirty) showDiscardDialog = true else onClose()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = appColors.textPrimary,
                        )
                    }

                    Text(
                        text = if (state.workoutId != null) "Edit routine" else "New routine",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    )

                    LiquidGlassButton(
                        onClick = {
                            scope.launch {
                                viewModel.save()
                                onSaved()
                            }
                        },
                        enabled = state.canSave && !state.isSaving,
                        tint = accent.accent,
                        surfaceColor = Color.White.copy(alpha = 0.08f),
                        buttonHeight = 36.dp,
                        modifier = Modifier.width(72.dp),
                    ) {
                        Text(
                            text = if (state.isSaving) "Saving…" else "Save",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.canSave) accent.onAccent else appColors.textMuted,
                        )
                    }
                }
            }

            // ── Name field ────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 10.dp),
                ) {
                    GlassInputField(
                        label = "Routine name",
                        value = state.name,
                        onValueChange = { viewModel.setName(it) },
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Note field (optional) ─────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 14.dp),
                ) {
                    Text(
                        text = "NOTE (OPTIONAL)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textMuted,
                        letterSpacing = 0.10.sp,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                    GlassTextArea(
                        value = state.note,
                        onValueChange = { viewModel.setNote(it) },
                        placeholder = "E.g. Push day — focus on chest",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }

            // ── "EXERCISES · N" header ────────────────────────────────────────
            item {
                Text(
                    text = if (state.exercises.isEmpty()) "EXERCISES" else "EXERCISES · ${state.exercises.size}  ·  $totalSets SETS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = appColors.textPrimary.copy(alpha = 0.55f),
                    letterSpacing = 0.4.sp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                )
            }

            // ── Empty state ───────────────────────────────────────────────────
            if (state.exercises.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = appColors.textMuted,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = "Add exercises to build this routine",
                                fontSize = 13.sp,
                                color = appColors.textMuted,
                            )
                        }
                    }
                }
            }

            // ── Exercise cards ────────────────────────────────────────────────
            items(state.exercises, key = { it.id }) { builderEx ->
                val exIndex = state.exercises.indexOfFirst { it.id == builderEx.id }
                val exercise = builderEx.exercise

                val subtitle = listOfNotNull(
                    exercise.primaryMuscles.firstOrNull()?.replaceFirstChar { it.uppercase() },
                    exercise.equipment?.replaceFirstChar { it.uppercase() },
                ).joinToString(" · ")

                ReorderableItem(reorderState, key = builderEx.id) { isDragging ->
                    ExerciseCard(
                        exerciseName = exercise.name,
                        imageUrl = exercise.images.firstOrNull(),
                        subtitle = subtitle,
                        onMoveUp = if (exIndex > 0) { { viewModel.reorder(exIndex, exIndex - 1) } } else null,
                        onMoveDown = if (exIndex < state.exercises.lastIndex) {
                            { viewModel.reorder(exIndex, exIndex + 1) }
                        } else null,
                        onRemove = { viewModel.removeExercise(exIndex) },
                        isDragging = isDragging,
                        dragHandleModifier = Modifier.longPressDraggableHandle(
                            onDragStarted = { dragHaptics.start() },
                            onDragStopped = { dragHaptics.end() },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        SetGrid(
                            mode = SetGridMode.PLAN,
                            sets = builderEx.sets.map { bs ->
                                SetRowData(
                                    targetReps = bs.targetReps,
                                    targetWeightKg = bs.targetWeightKg,
                                )
                            },
                            onAddSet = { viewModel.addSet(exIndex) },
                            onRemoveSet = { setIndex -> viewModel.removeSet(exIndex, setIndex) },
                            onSetChanged = { setIndex, reps, weightKg ->
                                viewModel.setTarget(exIndex, setIndex, reps, weightKg)
                            },
                        )
                    }
                }
            }

            // ── Add exercise button ───────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                LiquidGlassButton(
                    onClick = onAddExercise,
                    tint = accent.accent,
                    surfaceColor = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = "Add exercise",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent.onAccent,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
