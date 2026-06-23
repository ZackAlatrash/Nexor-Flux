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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.moveByKey
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.train.component.ExerciseCard
import com.zack.recomptracker.ui.train.component.SetGrid
import com.zack.recomptracker.ui.train.component.SetGridMode
import com.zack.recomptracker.ui.train.component.SessionSetRow
import com.zack.recomptracker.ui.train.component.rememberDragHaptics
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Active Session screen — the live workout tracker.
 *
 * @param viewModel            Active session ViewModel.
 * @param pickedExerciseIds    Non-null when returning from Exercise Picker (add mode).
 * @param onPickedConsumed     Clear the savedStateHandle entry after consuming.
 * @param replacementResult    [targetSessionExerciseId, chosenExerciseId] from the picker (replace mode).
 * @param onReplacementConsumed Clear the replacement savedStateHandle entry after consuming.
 * @param onReplaceExercise    Open the picker in replace mode for the given session exercise.
 * @param onAddExercise        Navigate to Exercise Picker.
 * @param onMinimize           Pop back to Train Home; session remains ACTIVE.
 * @param onFinish             Called with sessionId after completing.
 * @param onDiscard            Called after the active session is abandoned; exits to Train Home.
 */
@Composable
fun ActiveSessionScreen(
    viewModel: ActiveSessionViewModel,
    pickedExerciseIds: LongArray?,
    onPickedConsumed: () -> Unit,
    replacementResult: LongArray?,
    onReplacementConsumed: () -> Unit,
    onReplaceExercise: (sessionExerciseId: Long) -> Unit,
    onAddExercise: () -> Unit,
    onMinimize: () -> Unit,
    onFinish: (sessionId: Long) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val prevMap by viewModel.prevMap.collectAsStateWithLifecycle()
    val exerciseVisuals by viewModel.exerciseVisuals.collectAsStateWithLifecycle()
    val detailExercise by viewModel.detailExercise.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    // Local-only UI state for the header overflow menu + discard confirmation.
    // Kept out of the ViewModel so opening/closing them never touches workout state.
    var menuOpen by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    // Pending replacement awaiting confirmation: (targetSessionExerciseId, chosenExerciseId, oldName).
    // Only set when the target already has logged sets that the replacement would clear.
    var pendingReplacement by remember { mutableStateOf<Triple<Long, Long, String>?>(null) }

    // Consume picked exercise IDs returned from Exercise Picker
    LaunchedEffect(pickedExerciseIds) {
        pickedExerciseIds?.let { ids ->
            viewModel.addExercises(ids)
            onPickedConsumed()
        }
    }

    // Consume a replacement chosen in the picker (replace mode). If the target already has logged
    // sets, defer to a confirm dialog (clearing them is destructive); otherwise replace immediately.
    LaunchedEffect(replacementResult) {
        val result = replacementResult ?: return@LaunchedEffect
        if (result.size == 2) {
            val targetSeId = result[0]
            val chosenExerciseId = result[1]
            val target = viewModel.session.value?.exercises?.firstOrNull { it.id == targetSeId }
            val hasLoggedSets = target?.sets?.any { it.completed || it.reps > 0 || it.weightKg != null } == true
            if (hasLoggedSets) {
                pendingReplacement = Triple(targetSeId, chosenExerciseId, target?.exerciseName ?: "this exercise")
            } else {
                viewModel.replaceExercise(targetSeId, chosenExerciseId)
            }
        }
        onReplacementConsumed()
    }

    // Session note local state — initialized once per session id to avoid wiping mid-typed text
    var noteText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(session?.id) {
        if (session != null) noteText = session!!.note ?: ""
    }

    // Show loading placeholder if no session yet
    if (session == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading session…",
                fontSize = 14.sp,
                color = appColors.textMuted,
            )
        }
        return
    }

    val s = session!!
    val lazyListState = rememberLazyListState()
    val dragHaptics = rememberDragHaptics()
    // Local mirror of the DB exercise order: reordered instantly during a drag (and by the
    // Move up/down arrows), persisted on drop. remember(s.exercises) rebuilds it only when the
    // DB emits a new order — the per-second elapsed timer is a separate flow and won't disturb it.
    val displayExercises = remember(s.exercises) {
        s.exercises.sortedBy { it.sortOrder }.toMutableStateList()
    }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (displayExercises.moveByKey(from.key, to.key) { it.id }) {
            dragHaptics.move()
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Minimize (collapse chevron)
                IconButton(onClick = onMinimize) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Routine title (prominent) + elapsed timer beneath it
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.workoutName,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = appColors.textMuted,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        // Isolated so the per-second tick recomposes only this Text,
                        // not the whole session list (exercise cards stay untouched).
                        ElapsedTimerText(
                            elapsedFlow = viewModel.elapsed,
                            color = appColors.textMuted,
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Finish — filled accent pill (icon + label)
                LiquidGlassButton(
                    onClick = {
                        scope.launch {
                            viewModel.finish()?.let { sid -> onFinish(sid) }
                        }
                    },
                    tint = accent.accent,
                    surfaceColor = Color.White.copy(alpha = 0.08f),
                    buttonHeight = 40.dp,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = accent.onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Finish",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent.onAccent,
                    )
                }

                // Overflow menu — fast path to discard the active workout. The existing
                // Finish → Session Summary → "Discard Workout" flow is unchanged.
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = appColors.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Discard workout", color = ErrorRed) },
                            onClick = {
                                menuOpen = false
                                showDiscardDialog = true
                            },
                        )
                    }
                }
            }
        }

        // ── Session notes ─────────────────────────────────────────────────────
        item {
            FrostedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp),
                contentPadding = 12.dp,
            ) {
                Text(
                    text = "SESSION NOTES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textSecondary,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                SessionNoteField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    onFocusLost = { viewModel.setNote(noteText) },
                )
            }
        }

        // ── Exercise cards ────────────────────────────────────────────────────
        items(displayExercises, key = { it.id }) { se ->
            ReorderableItem(reorderState, key = se.id) { isDragging ->
                val visual = exerciseVisuals[se.exerciseId]
                ExerciseCard(
                    exerciseName = se.exerciseName,
                    imageUrl = visual?.imagePath,
                    fallbackMuscles = visual?.primaryMuscles,
                    subtitle = "",
                    onMoveUp = if (displayExercises.first().id != se.id) {
                        {
                            val idx = displayExercises.indexOfFirst { it.id == se.id }
                            if (idx > 0) {
                                displayExercises.add(idx - 1, displayExercises.removeAt(idx))
                                viewModel.reorderExercises(displayExercises.map { it.id })
                            }
                        }
                    } else null,
                    onMoveDown = if (displayExercises.last().id != se.id) {
                        {
                            val idx = displayExercises.indexOfFirst { it.id == se.id }
                            if (idx < displayExercises.size - 1) {
                                displayExercises.add(idx + 1, displayExercises.removeAt(idx))
                                viewModel.reorderExercises(displayExercises.map { it.id })
                            }
                        }
                    } else null,
                    onRemove = { viewModel.removeExercise(se) },
                    onReplace = { onReplaceExercise(se.id) },
                    onShowDetails = { viewModel.showExerciseDetails(se.exerciseId) },
                    isDragging = isDragging,
                    dragHandleModifier = Modifier.longPressDraggableHandle(
                        onDragStarted = { dragHaptics.start() },
                        onDragStopped = {
                            dragHaptics.end()
                            viewModel.reorderExercises(displayExercises.map { it.id })
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 14.dp),
                ) {
                    // Build session set rows for this exercise
                    val prevList = prevMap[se.exerciseId] ?: emptyList()
                    val sessionRows = se.sets.mapIndexed { idx, set ->
                        SessionSetRow(
                            id = set.id,
                            setNumber = set.setNumber,
                            prev = prevList.getOrNull(idx),
                            reps = set.reps.takeIf { it > 0 },
                            weightKg = set.weightKg,
                            rir = set.rir,
                            completed = set.completed,
                        )
                    }

                    SetGrid(
                        mode = SetGridMode.SESSION,
                        sets = emptyList(), // not used in SESSION mode
                        onAddSet = {},
                        onRemoveSet = {},
                        onSetChanged = { _, _, _ -> },
                        sessionSets = sessionRows,
                        onKgChanged = { row, kg ->
                            val matchingSet = se.sets.firstOrNull { it.id == row.id } ?: return@SetGrid
                            viewModel.updateKg(se, matchingSet, kg)
                        },
                        onRepsChanged = { row, reps ->
                            val matchingSet = se.sets.firstOrNull { it.id == row.id } ?: return@SetGrid
                            viewModel.updateReps(se, matchingSet, reps)
                        },
                        onRirChanged = { row, rir ->
                            val matchingSet = se.sets.firstOrNull { it.id == row.id } ?: return@SetGrid
                            viewModel.updateRir(se, matchingSet, rir)
                        },
                        onToggleComplete = { row ->
                            val matchingSet = se.sets.firstOrNull { it.id == row.id } ?: return@SetGrid
                            viewModel.toggleComplete(se, matchingSet)
                        },
                        onSessionAddSet = { viewModel.addSet(se) },
                        onSessionRemoveSet = { setId -> viewModel.removeSet(setId) },
                    )
                }
            }
        }

        // ── + Exercise button ─────────────────────────────────────────────────
        item {
            LiquidGlassButton(
                onClick = onAddExercise,
                tint = accent.accent,
                surfaceColor = Color.White.copy(alpha = 0.08f),
                buttonHeight = 44.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = accent.onAccent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Add Exercise",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent.onAccent,
                )
            }
        }
    }

    // ── Exercise details popup ──────────────────────────────────────────────────
    // Overlaid above the session list; the workout state (sets, order, timer, notes)
    // is untouched while it is open or when it is dismissed.
    detailExercise?.let { exercise ->
        ExerciseDetailSheet(
            exercise = exercise,
            onDismiss = { viewModel.dismissExerciseDetails() },
        )
    }

    // ── Discard confirmation ────────────────────────────────────────────────────
    if (showDiscardDialog) {
        ConfirmDialog(
            title = "Discard workout?",
            body = "This permanently deletes the current session. This can't be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                showDiscardDialog = false
                scope.launch {
                    viewModel.discard()
                    onDiscard()
                }
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    // ── Replace confirmation (only when logged sets would be cleared) ────────────
    pendingReplacement?.let { (targetSeId, chosenExerciseId, oldName) ->
        ConfirmDialog(
            title = "Replace exercise?",
            body = "You've logged sets for $oldName. Replacing it will clear those sets.",
            confirmLabel = "Replace",
            onConfirm = {
                viewModel.replaceExercise(targetSeId, chosenExerciseId)
                pendingReplacement = null
            },
            onDismiss = { pendingReplacement = null },
        )
    }
}

// ── Session note text field (inline, no external label) ───────────────────────

@Composable
private fun SessionNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) accent.accent.copy(alpha = 0.45f) else appColors.frostedBorder

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            fontSize = 13.sp,
            color = appColors.textPrimary,
        ),
        cursorBrush = SolidColor(accent.accentLighter),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerSmall))
            .background(appColors.cardSurface)
            .border(1.dp, borderColor, RoundedCornerShape(CornerSmall))
            .onFocusChanged { fs ->
                focused = fs.isFocused
                if (!fs.isFocused) onFocusLost()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        minLines = 2,
        maxLines = 5,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = "Add a note about this session…",
                    fontSize = 13.sp,
                    color = appColors.textMuted,
                )
            }
            inner()
        },
    )
}

// ── Elapsed timer (isolated recomposition scope) ──────────────────────────────

/**
 * Renders the live elapsed time. Collects [elapsedFlow] here — and nowhere higher — so the
 * once-per-second tick recomposes only this Text, leaving the exercise list/cards untouched.
 */
@Composable
private fun ElapsedTimerText(
    elapsedFlow: StateFlow<Long>,
    color: Color,
) {
    val elapsed by elapsedFlow.collectAsStateWithLifecycle()
    val text = remember(elapsed) { formatElapsed(elapsed) }
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = color,
    )
}

// ── Elapsed time formatter ────────────────────────────────────────────────────

private fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}
