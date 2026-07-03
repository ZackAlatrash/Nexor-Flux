package com.zack.recomptracker.ui.train

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.moveByKey
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerChip
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.train.component.ExerciseCard
import com.zack.recomptracker.ui.train.component.exerciseImageUrl
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
    var showDiscardDialog by remember { mutableStateOf(false) }
    // Set when Finish is tapped while exercises still have unchecked sets — gates the actual finish
    // behind a confirmation so the user can choose to keep going instead.
    var showUnfinishedDialog by remember { mutableStateOf(false) }
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

    // Preload this session's exercise thumbnails at display size as soon as their paths resolve, so
    // the decode + GPU upload happens up front instead of mid-scroll (the image scroll-jank cost).
    // Bounded to a handful of exercises; the app's Coil loader caches them as hardware bitmaps.
    val imageContext = LocalContext.current
    val thumbPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    LaunchedEffect(exerciseVisuals) {
        val loader = imageContext.imageLoader
        exerciseVisuals.values.forEach { visual ->
            val path = visual.imagePath?.takeIf { it.isNotBlank() } ?: return@forEach
            loader.enqueue(
                ImageRequest.Builder(imageContext)
                    .data(exerciseImageUrl(path))
                    .size(thumbPx)
                    .build(),
            )
        }
    }

    // Show loading placeholder if no session yet
    if (session == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading session…",
                style = AppType.body,
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Pinned header (always visible) ────────────────────────────────────
            ActiveSessionHeader(
                workoutName = s.workoutName,
                elapsedFlow = viewModel.elapsed,
                onMinimize = onMinimize,
                onFinish = {
                    // If any set is still unchecked, confirm before finishing; otherwise finish directly.
                    if (s.exercises.any { ex -> ex.sets.any { !it.completed } }) {
                        showUnfinishedDialog = true
                    } else {
                        scope.launch {
                            viewModel.finish()?.let { sid -> onFinish(sid) }
                        }
                    }
                },
                onDiscardRequest = { showDiscardDialog = true },
            )

            // ── Scrolling content ─────────────────────────────────────────────────
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                // ── Session notes ─────────────────────────────────────────────────
                item {
                    FrostedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 14.dp, bottom = 14.dp),
                        contentPadding = 12.dp,
                    ) {
                        SectionLabel(
                            text = "Session notes",
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        SessionNoteField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            onFocusLost = { viewModel.setNote(noteText) },
                        )
                    }
                }

                // ── Exercise cards ────────────────────────────────────────────────
                items(displayExercises, key = { it.id }, contentType = { "exercise" }) { se ->
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
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 14.dp),
                        ) {
                            // Build session set rows for this exercise. prevMap is populated once
                            // per session load and static thereafter, so keying only on se.sets
                            // (the actual per-keystroke-changing input) is sufficient — this list
                            // was otherwise rebuilt on every recomposition, including every
                            // keystroke in any KG/REPS field on any exercise card.
                            val prevList = prevMap[se.exerciseId] ?: emptyList()
                            val sessionRows = remember(se.sets) {
                                se.sets.mapIndexed { idx, set ->
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

                // ── Add Exercise button ───────────────────────────────────────────
                item {
                    LiquidGlassButton(
                        onClick = onAddExercise,
                        tint = accent.accent,
                        surfaceColor = Color.White.copy(alpha = 0.08f),
                        buttonHeight = 44.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
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
                            style = AppType.body.copy(fontWeight = FontWeight.Medium),
                            color = accent.onAccent,
                        )
                    }
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

        // ── Unfinished-sets confirmation (Finish tapped with unchecked sets) ─────────
        if (showUnfinishedDialog) {
            val incompleteExercises = s.exercises.filter { ex -> ex.sets.any { !it.completed } }
            val incompleteSetCount = incompleteExercises.sumOf { ex -> ex.sets.count { !it.completed } }
            UnfinishedSetsOverlay(
                incompleteSets = incompleteSetCount,
                incompleteExercises = incompleteExercises.size,
                onEndAnyway = {
                    showUnfinishedDialog = false
                    scope.launch {
                        viewModel.finish()?.let { sid -> onFinish(sid) }
                    }
                },
                onKeepGoing = { showUnfinishedDialog = false },
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
}

// ── Pinned header (title · timer pill · Finish · overflow) ────────────────────

@Composable
private fun ActiveSessionHeader(
    workoutName: String,
    elapsedFlow: StateFlow<Long>,
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
    onDiscardRequest: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 14.dp, bottom = 10.dp),
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

            // Routine title (prominent) + elapsed timer pill beneath it
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workoutName,
                    style = AppType.screenTitleCompact,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                ElapsedTimerPill(elapsedFlow = elapsedFlow)
            }

            Spacer(Modifier.width(10.dp))

            // Finish — filled accent pill (icon + label)
            LiquidGlassButton(
                onClick = onFinish,
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
                    style = AppType.body.copy(fontWeight = FontWeight.Medium),
                    color = accent.onAccent,
                )
            }

            // Overflow menu — fast path to discard the active workout.
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
                            onDiscardRequest()
                        },
                    )
                }
            }
        }

        // Subtle divider so the header reads as pinned above the scrolling list.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(appColors.frostedBorder),
        )
    }
}

// ── Elapsed timer pill (accent-tinted, isolated recomposition) ────────────────

@Composable
private fun ElapsedTimerPill(elapsedFlow: StateFlow<Long>) {
    val accent = LocalAppAccent.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CornerChip))
            .background(accent.tintedSurface)
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerChip))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            tint = accent.inkLight,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        ElapsedTimerText(
            elapsedFlow = elapsedFlow,
            color = accent.inkLight,
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
        textStyle = AppType.body.copy(color = appColors.textPrimary),
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
                    style = AppType.body,
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
        style = AppType.statValueSmall,
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

// ── Unfinished-sets overlay (custom glass modal with liquid-glass buttons) ────

/**
 * Shown when the user taps Finish while some sets are still unchecked. Rendered in-composition
 * (not in a Dialog window) so the [LiquidGlassButton] actions read the live app backdrop and render
 * as true liquid glass — a Dialog window has no backdrop layer, so glass would fall flat there.
 *
 * A dimming scrim sits over the workout screen; tapping it (outside the card) is treated as
 * "Keep going". The card matches the app's [WeeklyBriefingOverlay] modal surface (dark translucent
 * fill, top sheen, hairline edge); no AI edge glow, which is reserved for AI features.
 *
 * "Keep going" is the encouraged path (accent-tinted glass pill). "End anyway" is a clear glass pill —
 * ending with unchecked sets isn't destructive (the sets carry through to the summary).
 */
@Composable
private fun UnfinishedSetsOverlay(
    incompleteSets: Int,
    incompleteExercises: Int,
    onEndAnyway: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val shape = RoundedCornerShape(24.dp)
    val surface = if (appColors.isDark) Color(0xFF101014).copy(alpha = 0.92f)
                  else Color(0xFFF6F6F8).copy(alpha = 0.95f)
    val hairline = Color.White.copy(alpha = if (appColors.isDark) 0.16f else 0.24f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // Tap outside the card dismisses (= keep going). No ripple on the scrim.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onKeepGoing,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(shape)
                .background(surface)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.12f),
                            0.14f to Color.Transparent,
                        ),
                    )
                }
                .border(0.5.dp, hairline, shape)
                // Swallow taps on the card so they don't fall through to the scrim's dismiss.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(22.dp),
        ) {
            Text(
                text = "Unfinished sets",
                style = AppType.screenTitleCompact,
                color = appColors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            val setWord = if (incompleteSets == 1) "set" else "sets"
            val exWord = if (incompleteExercises == 1) "exercise" else "exercises"
            Text(
                text = "You still have $incompleteSets unchecked $setWord across " +
                    "$incompleteExercises $exWord. End the workout anyway, or keep going to complete them?",
                style = AppType.body,
                color = appColors.textMuted,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // End anyway — clear glass pill (not destructive: sets carry through to the summary).
                LiquidGlassButton(
                    onClick = onEndAnyway,
                    surfaceColor = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "End anyway",
                        style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = appColors.textPrimary,
                    )
                }
                // Keep going — accent-tinted glass pill (the encouraged path).
                LiquidGlassButton(
                    onClick = onKeepGoing,
                    tint = accent.accent,
                    surfaceColor = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Keep going",
                        style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = accent.onAccent,
                    )
                }
            }
        }
    }
}
