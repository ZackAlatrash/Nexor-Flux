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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.train.component.ExerciseCard
import com.zack.recomptracker.ui.train.component.SetGrid
import com.zack.recomptracker.ui.train.component.SetGridMode
import com.zack.recomptracker.ui.train.component.SessionSetRow
import kotlinx.coroutines.launch

/**
 * Active Session screen — the live workout tracker.
 *
 * @param viewModel            Active session ViewModel.
 * @param pickedExerciseIds    Non-null when returning from Exercise Picker.
 * @param onPickedConsumed     Clear the savedStateHandle entry after consuming.
 * @param onAddExercise        Navigate to Exercise Picker.
 * @param onMinimize           Pop back to Train Home; session remains ACTIVE.
 * @param onFinish             Called with sessionId after completing.
 */
@Composable
fun ActiveSessionScreen(
    viewModel: ActiveSessionViewModel,
    pickedExerciseIds: LongArray?,
    onPickedConsumed: () -> Unit,
    onAddExercise: () -> Unit,
    onMinimize: () -> Unit,
    onFinish: (sessionId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val prevMap by viewModel.prevMap.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    // Consume picked exercise IDs returned from Exercise Picker
    LaunchedEffect(pickedExerciseIds) {
        pickedExerciseIds?.let { ids ->
            viewModel.addExercises(ids)
            onPickedConsumed()
        }
    }

    // Format elapsed seconds into mm:ss or h:mm:ss
    val elapsedText = remember(elapsed) { formatElapsed(elapsed) }

    // Session note local state (mirrors DB via note field)
    var noteText by remember(session?.note) { mutableStateOf(session?.note ?: "") }

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
    val sortedExercises = s.exercises.sortedBy { it.sortOrder }

    LazyColumn(
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

                Spacer(Modifier.width(4.dp))

                // Stopwatch icon
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = appColors.textMuted,
                    modifier = Modifier.size(16.dp),
                )

                Spacer(Modifier.width(4.dp))

                // Elapsed timer — center
                Text(
                    text = elapsedText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.textPrimary,
                    modifier = Modifier.weight(1f),
                )

                // Workout name (brief)
                Text(
                    text = s.workoutName,
                    fontSize = 13.sp,
                    color = appColors.textMuted,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )

                // Finish button
                LiquidGlassButton(
                    onClick = {
                        scope.launch {
                            viewModel.finish()?.let { sid -> onFinish(sid) }
                        }
                    },
                    tint = accent.accent,
                    surfaceColor = Color.White.copy(alpha = 0.08f),
                    buttonHeight = 36.dp,
                    modifier = Modifier.width(90.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accent.onAccent,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Finish",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent.onAccent,
                    )
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
                    color = appColors.textMuted,
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
        items(sortedExercises, key = { it.id }) { se ->
            ExerciseCard(
                exerciseName = se.exerciseName,
                imageUrl = null, // images resolved by library; session snapshot has no path
                subtitle = "",
                onMoveUp = if (sortedExercises.first().id != se.id) {
                    { viewModel.moveExerciseUp(se) }
                } else null,
                onMoveDown = if (sortedExercises.last().id != se.id) {
                    { viewModel.moveExerciseDown(se) }
                } else null,
                onRemove = { viewModel.removeExercise(se) },
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
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "+ Exercise",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent.onAccent,
                )
            }
        }
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
