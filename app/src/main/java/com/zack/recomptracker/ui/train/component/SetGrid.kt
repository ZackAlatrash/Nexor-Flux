package com.zack.recomptracker.ui.train.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Display modes for [SetGrid].
 *
 * PLAN     — editable KG / REPS targets; used in Routine Builder.
 * SESSION  — editable actual KG / REPS + check-off; used in Active Session.
 * READONLY — static display of completed values; used in Session Detail.
 */
enum class SetGridMode { PLAN, SESSION, READONLY }

/**
 * A set row as seen by the Routine Builder (PLAN mode).
 *
 * @param targetReps       Optional target repetitions (null = blank)
 * @param targetWeightKg   Optional target weight in kg  (null = blank)
 */
data class SetRowData(
    val targetReps: Int?,
    val targetWeightKg: Double?,
)

/**
 * A set row as seen by the Active Session (SESSION mode).
 *
 * @param id            DB primary key of the SessionSetEntity.
 * @param setNumber     Display number (1-based).
 * @param prev          Optional previous-session hint, e.g. "10×15".
 * @param reps          Current reps (null = not yet entered).
 * @param weightKg      Current weight in kg (null = not yet entered).
 * @param rir           Reps-in-reserve (null = not entered).
 * @param completed     Whether this set has been ticked off.
 */
data class SessionSetRow(
    val id: Long,
    val setNumber: Int,
    val prev: String?,
    val reps: Int?,
    val weightKg: Double?,
    val rir: Int?,
    val completed: Boolean,
)

/**
 * Reusable per-exercise set grid.
 *
 * ### PLAN mode (Routine Builder)
 * Columns: SET · KG · REPS. Editable cells + remove icon + add-set footer.
 *
 * ### SESSION mode (Active Session)
 * Columns: SET · PREV · KG · REPS · ✓.
 * - Completed rows: accent-tinted row background + accent-filled check circle.
 * - Pending rows: editable KG/REPS cells + hollow check circle.
 * - Tapping the row reveals an RIR stepper (hidden by default).
 * Uses [sessionSets] + session-specific callbacks; [sets] / [onSetChanged] are ignored.
 *
 * ### READONLY mode
 * Static value display.
 */
@Composable
fun SetGrid(
    mode: SetGridMode,
    sets: List<SetRowData>,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onSetChanged: (index: Int, reps: Int?, weightKg: Double?) -> Unit,
    modifier: Modifier = Modifier,
    // ── SESSION mode parameters (ignored in PLAN / READONLY) ──────────────────
    sessionSets: List<SessionSetRow> = emptyList(),
    onKgChanged: (row: SessionSetRow, kg: Double?) -> Unit = { _, _ -> },
    onRepsChanged: (row: SessionSetRow, reps: Int?) -> Unit = { _, _ -> },
    onRirChanged: (row: SessionSetRow, rir: Int?) -> Unit = { _, _ -> },
    onToggleComplete: (row: SessionSetRow) -> Unit = { _ -> },
    onSessionAddSet: () -> Unit = {},
    onSessionRemoveSet: (setId: Long) -> Unit = { _ -> },
) {
    when (mode) {
        SetGridMode.SESSION -> SessionSetGrid(
            sets = sessionSets,
            onKgChanged = onKgChanged,
            onRepsChanged = onRepsChanged,
            onRirChanged = onRirChanged,
            onToggleComplete = onToggleComplete,
            onAddSet = onSessionAddSet,
            onRemoveSet = onSessionRemoveSet,
            modifier = modifier,
        )
        SetGridMode.READONLY -> ReadonlySetGrid(
            sets = sessionSets,
            modifier = modifier,
        )
        SetGridMode.PLAN -> PlanSetGrid(
            mode = mode,
            sets = sets,
            onAddSet = onAddSet,
            onRemoveSet = onRemoveSet,
            onSetChanged = onSetChanged,
            modifier = modifier,
        )
    }
}

// ── SESSION grid ─────────────────────────────────────────────────────────────

@Composable
private fun SessionSetGrid(
    sets: List<SessionSetRow>,
    onKgChanged: (SessionSetRow, Double?) -> Unit,
    onRepsChanged: (SessionSetRow, Int?) -> Unit,
    onRirChanged: (SessionSetRow, Int?) -> Unit,
    onToggleComplete: (SessionSetRow) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    // Track which rows have the RIR stepper expanded
    val expandedRir = remember { mutableStateOf(setOf<Long>()) }

    Column(modifier = modifier) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // SET
            Text(
                text = "SET",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(6.dp))
            // PREV
            Text(
                text = "PREV",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(52.dp),
            )
            Spacer(Modifier.width(6.dp))
            // KG
            Text(
                text = "KG",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            // REPS
            Text(
                text = "REPS",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // Check placeholder
            Spacer(Modifier.width(32.dp))
        }

        // ── Rows ──────────────────────────────────────────────────────────────
        sets.forEach { row ->
            val isExpanded = row.id in expandedRir.value
            val rowBg = if (row.completed) accent.tintedSurface else Color.Transparent

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(rowBg)
                    .clickable {
                        // toggle RIR reveal
                        expandedRir.value = if (isExpanded)
                            expandedRir.value - row.id
                        else
                            expandedRir.value + row.id
                    }
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Set number badge
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .clip(RoundedCornerShape(CornerSmall))
                            .background(appColors.cardSurface)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${row.setNumber}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (row.completed) accent.inkLighter else appColors.textMuted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // PREV hint
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = row.prev ?: "–",
                            fontSize = 12.sp,
                            color = appColors.textMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // KG cell
                    SetInputCell(
                        value = row.weightKg?.let { formatWeight(it) } ?: "",
                        placeholder = "–",
                        keyboardType = KeyboardType.Decimal,
                        completed = row.completed,
                        modifier = Modifier.weight(1f),
                        onChanged = { raw ->
                            onKgChanged(row, if (raw.isBlank()) null else raw.toDoubleOrNull())
                        },
                    )

                    Spacer(Modifier.width(6.dp))

                    // REPS cell
                    SetInputCell(
                        value = row.reps?.takeIf { it > 0 }?.toString() ?: "",
                        placeholder = "–",
                        keyboardType = KeyboardType.Number,
                        completed = row.completed,
                        modifier = Modifier.weight(1f),
                        onChanged = { raw ->
                            onRepsChanged(row, if (raw.isBlank()) null else raw.toIntOrNull())
                        },
                    )

                    Spacer(Modifier.width(4.dp))

                    // ✓ check button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (row.completed) accent.accent
                                else appColors.cardSurface
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (row.completed) accent.accent
                                        else appColors.frostedBorder,
                                shape = CircleShape,
                            )
                            .clickable { onToggleComplete(row) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (row.completed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = accent.onAccent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Remove icon (same width slot as PLAN mode)
                    IconButton(
                        onClick = { onRemoveSet(row.id) },
                        modifier = Modifier
                            .width(28.dp)
                            .height(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove set ${row.setNumber}",
                            tint = if (sets.size > 1) appColors.textMuted
                                   else appColors.textMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                // ── RIR stepper (revealed on row tap) ────────────────────────
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp, start = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "RIR",
                            fontSize = 11.sp,
                            color = appColors.textMuted,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        // Decrement
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(appColors.cardSurface)
                                .clickable {
                                    val cur = row.rir ?: 0
                                    onRirChanged(row, (cur - 1).coerceAtLeast(0))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("–", fontSize = 16.sp, color = appColors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = row.rir?.toString() ?: "–",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appColors.textPrimary,
                            modifier = Modifier.width(24.dp),
                            textAlign = TextAlign.Center,
                        )
                        // Increment
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(appColors.cardSurface)
                                .clickable {
                                    val cur = row.rir ?: 0
                                    onRirChanged(row, cur + 1)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+", fontSize = 16.sp, color = appColors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        // ── Add set ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(CornerSmall))
                .clickable { onAddSet() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = accent.inkLight,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Add set",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = accent.inkLight,
            )
        }
    }
}

// ── READONLY grid ────────────────────────────────────────────────────────────

@Composable
private fun ReadonlySetGrid(
    sets: List<SessionSetRow>,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current

    Column(modifier = modifier) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SET",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                modifier = Modifier.width(34.dp),
            )
            Text(
                text = "KG",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "REPS",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(32.dp))
        }

        // ── Rows ──────────────────────────────────────────────────────────────
        sets.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .clip(RoundedCornerShape(CornerSmall))
                        .background(appColors.cardSurface)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.width(6.dp))
                ReadonlySetCell(
                    text = row.weightKg?.let { formatWeight(it) } ?: "–",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                ReadonlySetCell(
                    text = row.reps?.takeIf { it > 0 }?.toString() ?: "–",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(32.dp))
            }
        }
    }
}

// ── PLAN / READONLY grid (original plan implementation, kept for PLAN mode) ───

@Composable
private fun PlanSetGrid(
    mode: SetGridMode,
    sets: List<SetRowData>,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onSetChanged: (index: Int, reps: Int?, weightKg: Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    Column(modifier = modifier) {
        // ── Column header ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SET",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                modifier = Modifier.width(34.dp),
            )
            Text(
                text = "KG",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "REPS",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(32.dp))
        }

        // ── Rows ──────────────────────────────────────────────────────────────
        sets.forEachIndexed { index, setRow ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .clip(RoundedCornerShape(CornerSmall))
                        .background(appColors.cardSurface)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.width(6.dp))

                if (mode == SetGridMode.PLAN) {
                    var kgRaw by remember(setRow.targetWeightKg) {
                        mutableStateOf(setRow.targetWeightKg?.let { formatWeight(it) } ?: "")
                    }
                    SetInputCell(
                        value = kgRaw,
                        placeholder = "–",
                        keyboardType = KeyboardType.Decimal,
                        completed = false,
                        modifier = Modifier.weight(1f),
                        onChanged = { raw ->
                            kgRaw = raw
                            if (raw.isBlank()) {
                                onSetChanged(index, setRow.targetReps, null)
                            } else {
                                val kg = raw.toDoubleOrNull()
                                if (kg != null) onSetChanged(index, setRow.targetReps, kg)
                            }
                        },
                    )
                } else {
                    ReadonlySetCell(
                        text = setRow.targetWeightKg?.let { formatWeight(it) } ?: "–",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.width(6.dp))

                if (mode == SetGridMode.PLAN) {
                    var repsRaw by remember(setRow.targetReps) {
                        mutableStateOf(setRow.targetReps?.toString() ?: "")
                    }
                    SetInputCell(
                        value = repsRaw,
                        placeholder = "–",
                        keyboardType = KeyboardType.Number,
                        completed = false,
                        modifier = Modifier.weight(1f),
                        onChanged = { raw ->
                            repsRaw = raw
                            if (raw.isBlank()) {
                                onSetChanged(index, null, setRow.targetWeightKg)
                            } else {
                                val reps = raw.toIntOrNull()
                                if (reps != null) onSetChanged(index, reps, setRow.targetWeightKg)
                            }
                        },
                    )
                } else {
                    ReadonlySetCell(
                        text = setRow.targetReps?.toString() ?: "–",
                        modifier = Modifier.weight(1f),
                    )
                }

                if (mode != SetGridMode.READONLY) {
                    IconButton(
                        onClick = { onRemoveSet(index) },
                        modifier = Modifier.width(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove set ${index + 1}",
                            tint = if (sets.size > 1) appColors.textMuted
                                   else appColors.textMuted.copy(alpha = 0.3f),
                            modifier = Modifier
                                .width(14.dp)
                                .height(14.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(32.dp))
                }
            }
        }

        // ── Add set ───────────────────────────────────────────────────────────
        if (mode == SetGridMode.PLAN || mode == SetGridMode.SESSION) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(CornerSmall))
                    .clickable { onAddSet() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = accent.inkLight,
                    modifier = Modifier
                        .width(14.dp)
                        .height(14.dp),
                )
                Text(
                    text = "Add set",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent.inkLight,
                )
            }
        }
    }
}

// ── Internal helpers ─────────────────────────────────────────────────────────

@Composable
private fun SetInputCell(
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    completed: Boolean,
    onChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) accent.accent.copy(alpha = 0.55f) else appColors.frostedBorder

    BasicTextField(
        value = value,
        onValueChange = onChanged,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (completed) accent.inkLighter else appColors.textPrimary,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(accent.accentLighter),
        modifier = modifier
            .clip(RoundedCornerShape(CornerSmall))
            .background(
                if (focused) accent.tintedSurface
                else appColors.cardSurface
            )
            .border(1.dp, borderColor, RoundedCornerShape(CornerSmall))
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = appColors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun ReadonlySetCell(
    text: String,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CornerSmall))
            .background(appColors.cardSurface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = appColors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatWeight(kg: Double): String =
    if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()
