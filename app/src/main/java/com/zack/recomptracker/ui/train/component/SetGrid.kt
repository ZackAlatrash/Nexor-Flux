package com.zack.recomptracker.ui.train.component

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Display modes for [SetGrid].
 *
 * PLAN     — editable KG / REPS targets; used in Routine Builder.
 * SESSION  — editable actual KG / REPS + check-off; used in Active Session (implemented later).
 * READONLY — static display of completed values; used in Session Detail (implemented later).
 */
enum class SetGridMode { PLAN, SESSION, READONLY }

/**
 * A set row as seen by the Routine Builder.
 *
 * @param targetReps       Optional target repetitions (null = blank, user hasn't filled it in yet)
 * @param targetWeightKg   Optional target weight in kg  (null = blank)
 */
data class SetRowData(
    val targetReps: Int?,
    val targetWeightKg: Double?,
)

/**
 * Reusable per-exercise set grid.
 *
 * PLAN mode is fully implemented — columns: SET · KG · REPS.
 * Cells accept blank (null target means "–").
 * Each row has a remove icon on the trailing edge.
 * Footer has "Add set" affordance.
 *
 * SESSION / READONLY are available as enum values for later screens and currently
 * render a read-only fallback (values display only, no editing/checkbox).
 *
 * @param mode         Which display mode to use.
 * @param sets         Current set data list.
 * @param onAddSet     Called when user taps "Add set".
 * @param onRemoveSet  Called with the 0-based index of the set to remove.
 * @param onSetChanged Called with (setIndex, newReps, newWeightKg) when a PLAN cell changes.
 */
@Composable
fun SetGrid(
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
            // "SET" label left-aligned
            Text(
                text = "SET",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                modifier = Modifier.width(34.dp),
            )
            // KG header — takes ~40 % of remaining space
            Text(
                text = "KG",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // REPS header
            Text(
                text = "REPS",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textMuted,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // Remove icon placeholder so header aligns with rows
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
                        text = "${index + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.width(6.dp))

                // KG cell
                if (mode == SetGridMode.PLAN) {
                    SetInputCell(
                        value = setRow.targetWeightKg?.let { formatWeight(it) } ?: "",
                        placeholder = "–",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                        onChanged = { raw ->
                            val kg = raw.toDoubleOrNull()
                            onSetChanged(index, setRow.targetReps, if (raw.isBlank()) null else kg)
                        },
                    )
                } else {
                    ReadonlySetCell(
                        text = setRow.targetWeightKg?.let { formatWeight(it) } ?: "–",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.width(6.dp))

                // REPS cell
                if (mode == SetGridMode.PLAN) {
                    SetInputCell(
                        value = setRow.targetReps?.toString() ?: "",
                        placeholder = "–",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        onChanged = { raw ->
                            val reps = raw.toIntOrNull()
                            onSetChanged(index, if (raw.isBlank()) null else reps, setRow.targetWeightKg)
                        },
                    )
                } else {
                    ReadonlySetCell(
                        text = setRow.targetReps?.toString() ?: "–",
                        modifier = Modifier.weight(1f),
                    )
                }

                // Remove button — only in PLAN/SESSION (not READONLY)
                if (mode != SetGridMode.READONLY) {
                    IconButton(
                        onClick = { onRemoveSet(index) },
                        modifier = Modifier.width(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove set ${index + 1}",
                            tint = if (sets.size > 1) appColors.textMuted else appColors.textMuted.copy(alpha = 0.3f),
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
            color = appColors.textPrimary,
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
