package com.zack.recomptracker.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.ui.component.CalorieProgressBar
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.TextVeryMuted
import com.zack.recomptracker.ui.theme.Violet400
import com.zack.recomptracker.ui.theme.Violet500
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FoodScreen(
    viewModel: TodayViewModel,
    onAddToSlot: (slotId: Long, slotName: String) -> Unit,
    onBrowseLibrary: () -> Unit,
    onEditEntryAmount: (slotId: Long?, slotName: String, entryId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FoodContent(
        state = state,
        actions = FoodActions(
            onToggleEditMode = viewModel::toggleEditMode,
            onAddSlot        = viewModel::addSlot,
            onRenameSlot     = viewModel::renameSlot,
            onDeleteSlot     = viewModel::deleteSlot,
            onReorderSlots   = viewModel::reorderSlots,
            onDeleteMeal     = viewModel::deleteMeal,
            onEditMacros     = viewModel::updateMealMacros,
        ),
        onAddToSlot       = onAddToSlot,
        onBrowseLibrary   = onBrowseLibrary,
        onEditEntryAmount = onEditEntryAmount,
    )
}

data class FoodActions(
    val onToggleEditMode: () -> Unit,
    val onAddSlot: (String) -> Unit,
    val onRenameSlot: (Long, String) -> Unit,
    val onDeleteSlot: (Long) -> Unit,
    val onReorderSlots: (List<Long>) -> Unit,
    val onDeleteMeal: (Long) -> Unit,
    val onEditMacros: (MealEntryEntity, Int, Double, Double, Double) -> Unit,
)

@Composable
fun FoodContent(
    state: TodayUiState,
    actions: FoodActions,
    onAddToSlot: (slotId: Long, slotName: String) -> Unit,
    onBrowseLibrary: () -> Unit,
    onEditEntryAmount: (slotId: Long?, slotName: String, entryId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var newSlotName by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        // Ambient orb — top-left
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x298B5CF6), Color.Transparent),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed screen header
            FoodScreenHeader(date = state.date, modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp))

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Nutrition strip
                item { NutritionStrip(state) }

                // Meals header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "MEALS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.12.sp,
                        )
                        Text(
                            text = if (state.slotsEditMode) "Done" else "Reorder",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xB38B5CF6),
                            modifier = Modifier.clickable(onClick = actions.onToggleEditMode),
                        )
                    }
                }

                items(state.slots, key = { it.slot.id }) { slotWithEntries ->
                    val index = state.slots.indexOf(slotWithEntries)
                    if (state.slotsEditMode) {
                        EditModeSlotCard(
                            slotWithEntries = slotWithEntries,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.slots.lastIndex,
                            onMoveUp = {
                                val ids = state.slots.map { it.slot.id }.toMutableList()
                                val i = ids.indexOf(slotWithEntries.slot.id)
                                if (i > 0) { ids.add(i - 1, ids.removeAt(i)); actions.onReorderSlots(ids) }
                            },
                            onMoveDown = {
                                val ids = state.slots.map { it.slot.id }.toMutableList()
                                val i = ids.indexOf(slotWithEntries.slot.id)
                                if (i < ids.lastIndex) { ids.add(i + 1, ids.removeAt(i)); actions.onReorderSlots(ids) }
                            },
                            onRename = { actions.onRenameSlot(slotWithEntries.slot.id, it) },
                            onDelete = { actions.onDeleteSlot(slotWithEntries.slot.id) },
                        )
                    } else {
                        LockedSlotCard(
                            slotWithEntries   = slotWithEntries,
                            onAddClick        = { onAddToSlot(slotWithEntries.slot.id, slotWithEntries.slot.name) },
                            onDeleteEntry     = actions.onDeleteMeal,
                            onEditEntryAmount = { entryId -> onEditEntryAmount(slotWithEntries.slot.id, slotWithEntries.slot.name, entryId) },
                            onEditMacros      = actions.onEditMacros,
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                            .clickable { showAddSlotDialog = true }
                            .padding(13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("＋", fontSize = 16.sp, color = Color(0x80FFFFFF))
                            Text(
                                "Add meal slot",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextVeryMuted,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (showAddSlotDialog) {
        AlertDialog(
            onDismissRequest = { showAddSlotDialog = false; newSlotName = "" },
            title = { Text("New meal slot") },
            text = {
                OutlinedTextField(
                    value = newSlotName,
                    onValueChange = { newSlotName = it },
                    label = { Text("Slot name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.onAddSlot(newSlotName)
                    newSlotName = ""
                    showAddSlotDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddSlotDialog = false; newSlotName = "" }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FoodScreenHeader(date: LocalDate, modifier: Modifier = Modifier) {
    val dateStr = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEE, MMMM d", Locale.getDefault()))
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "Food Log",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = (-0.8).sp,
        )
        Text(text = dateStr, fontSize = 12.sp, color = TextMuted)
    }
}

// ── Nutrition Strip ────────────────────────────────────────────────────────────

@Composable
private fun NutritionStrip(state: TodayUiState) {
    val cal     = state.totals.calories
    val target  = state.target
    val zoneLow = target.calorieZoneLowerBound
    val zoneHigh = target.calorieZoneUpperBound
    val scaleMax = ((zoneHigh * 1.2).toInt()).coerceAtLeast(1)
    val calFrac  = (cal.toFloat() / scaleMax).coerceIn(0f, 1f)

    val isInZone = cal in zoneLow..zoneHigh
    val isOver   = cal > zoneHigh
    val badgeText = if (isInZone) "In zone" else if (isOver) "Over" else "Below"
    val calSubText = when {
        isInZone -> " kcal · in zone"
        isOver   -> " kcal · ${cal - zoneHigh} over"
        else     -> " kcal · ${zoneLow - cal} to zone"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%,d", cal),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-0.8).sp,
                )
                Text(
                    text = calSubText,
                    fontSize = 11.sp,
                    color = Color(0x4DFFFFFF),
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x268B5CF6))
                    .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(text = badgeText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Violet400)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Calorie bar
        CalorieProgressBar(
            progress = calFrac,
            zoneLowFrac = (zoneLow.toFloat() / scaleMax).coerceIn(0f, 1f),
            zoneHighFrac = (zoneHigh.toFloat() / scaleMax).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
        Spacer(Modifier.height(12.dp))

        // Macros with bars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroProgressItem(
                label  = "Protein",
                value  = "${state.totals.proteinG.toInt()}g",
                remain = "${(target.targetProteinG - state.totals.proteinG).toInt().coerceAtLeast(0)}g to go",
                frac   = safeMacroFrac(state.totals.proteinG, target.targetProteinG),
                modifier = Modifier.weight(1f),
            )
            MacroProgressItem(
                label  = "Carbs",
                value  = "${state.totals.carbsG.toInt()}g",
                remain = "${(target.targetCarbsG - state.totals.carbsG).toInt().coerceAtLeast(0)}g to go",
                frac   = safeMacroFrac(state.totals.carbsG, target.targetCarbsG),
                modifier = Modifier.weight(1f),
            )
            MacroProgressItem(
                label  = "Fat",
                value  = "${state.totals.fatG.toInt()}g",
                remain = "${(target.targetFatG - state.totals.fatG).toInt().coerceAtLeast(0)}g to go",
                frac   = safeMacroFrac(state.totals.fatG, target.targetFatG),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MacroProgressItem(
    label: String,
    value: String,
    remain: String,
    frac: Float,
    modifier: Modifier = Modifier,
) {
    val animatedFrac by animateFloatAsState(
        targetValue = frac,
        animationSpec = tween(durationMillis = 900),
        label = "macroFill",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0x4DFFFFFF),
                letterSpacing = 0.08.sp,
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.3).sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x12FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFrac)
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(listOf(Violet500, Violet400)),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
        Text(text = remain, fontSize = 8.sp, color = Color(0x38FFFFFF))
    }
}

// ── Locked Slot Card ───────────────────────────────────────────────────────────

@Composable
private fun LockedSlotCard(
    slotWithEntries: MealSlotWithEntries,
    onAddClick: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onEditEntryAmount: (Long) -> Unit,
    onEditMacros: (MealEntryEntity, Int, Double, Double, Double) -> Unit,
) {
    val hasEntries = slotWithEntries.entries.isNotEmpty()
    val cardBg     = if (hasEntries) Color(0x0A8B5CF6) else CardSurface
    val cardBorder = if (hasEntries) Color(0x2E8B5CF6) else CardBorder

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp)),
    ) {
        // Slot header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = slotWithEntries.slot.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.02.sp,
                )
                Text(
                    text = if (hasEntries) "${slotWithEntries.totals.calories} kcal" else "empty",
                    fontSize = 10.sp,
                    fontWeight = if (hasEntries) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (hasEntries) Violet400 else TextMuted,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Violet500)
                    .clickable(onClick = onAddClick)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text("＋ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        if (hasEntries) {
            // Entry area: dark inset background
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33000000))
                    .drawBehind {
                        drawLine(
                            color = Color(0x0DFFFFFF),
                            start = Offset(0f, 0f),
                            end   = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    },
            ) {
                slotWithEntries.entries.forEachIndexed { i, entry ->
                    if (i > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0x0AFFFFFF)),
                        )
                    }
                    SlotEntryRow(
                        entry = entry,
                        onDelete = onDeleteEntry,
                        onEditAmount = { onEditEntryAmount(entry.id) },
                        onEditMacros = onEditMacros,
                    )
                }
            }
        } else {
            // Empty state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x26000000))
                    .drawBehind {
                        drawLine(
                            color = Color(0x0AFFFFFF),
                            start = Offset(0f, 0f),
                            end   = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0x0DFFFFFF)))
                Text("nothing logged yet", fontSize = 10.sp, color = Color(0x26FFFFFF))
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0x0DFFFFFF)))
            }
        }
    }
}

@Composable
private fun SlotEntryRow(
    entry: MealEntryEntity,
    onDelete: (Long) -> Unit,
    onEditAmount: () -> Unit,
    onEditMacros: (MealEntryEntity, Int, Double, Double, Double) -> Unit,
) {
    var showMacroEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val amountEditable = entry.amountGrams != null && entry.basePer100Calories != null

    val macroStr = buildString {
        entry.amountGrams?.let { append("${it.toInt()}g · ") }
        append("${entry.proteinG.toInt()}P ${entry.carbsG.toInt()}C ${entry.fatG.toInt()}F")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (amountEditable) onEditAmount() else showMacroEdit = true }
            .drawBehind {
                drawRect(
                    color    = Color(0x4D8B5CF6),
                    topLeft  = Offset(0f, 0f),
                    size     = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
                )
            }
            .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xBFFFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = macroStr, fontSize = 9.sp, color = Color(0x40FFFFFF))
        }
        Text(
            text = "${entry.calories}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0x66FFFFFF),
        )
        // Edit button
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1F8B5CF6))
                .clickable { if (amountEditable) onEditAmount() else showMacroEdit = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("✎", fontSize = 12.sp, color = Violet400)
        }
        // Delete button
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1AFB7185))
                .clickable { showDeleteConfirm = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 12.sp, color = ErrorRed.copy(alpha = 0.6f))
        }
    }

    if (showMacroEdit) {
        MacroEditDialog(
            entry = entry,
            onDismiss = { showMacroEdit = false },
            onSave = { cal, p, c, f -> onEditMacros(entry, cal, p, c, f); showMacroEdit = false },
        )
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete entry?",
            body = "Remove \"${entry.name}\" from this slot?",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = { onDelete(entry.id); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

// ── Macro Edit Dialog ──────────────────────────────────────────────────────────

@Composable
private fun MacroEditDialog(
    entry: MealEntryEntity,
    onDismiss: () -> Unit,
    onSave: (Int, Double, Double, Double) -> Unit,
) {
    var cal by remember(entry.id) { mutableStateOf(entry.calories.toString()) }
    var p   by remember(entry.id) { mutableStateOf(entry.proteinG.toInt().toString()) }
    var c   by remember(entry.id) { mutableStateOf(entry.carbsG.toInt().toString()) }
    var f   by remember(entry.id) { mutableStateOf(entry.fatG.toInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${entry.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Calories", cal, { cal = it }, suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Protein", p, { p = it }, Modifier.weight(1f), "g")
                    NumberField("Carbs",   c, { c = it }, Modifier.weight(1f), "g")
                    NumberField("Fat",     f, { f = it }, Modifier.weight(1f), "g")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    cal.toIntOrNull() ?: entry.calories,
                    p.toDoubleOrNull() ?: entry.proteinG,
                    c.toDoubleOrNull() ?: entry.carbsG,
                    f.toDoubleOrNull() ?: entry.fatG,
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Edit Mode Slot Card ────────────────────────────────────────────────────────

@Composable
private fun EditModeSlotCard(
    slotWithEntries: MealSlotWithEntries,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var renameValue by remember(slotWithEntries.slot.id) { mutableStateOf(slotWithEntries.slot.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (canMoveUp) Violet400 else Color(0x33FFFFFF),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (canMoveDown) Violet400 else Color(0x33FFFFFF),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(slotWithEntries.slot.name, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                "${slotWithEntries.entries.size} items · ${slotWithEntries.totals.calories} kcal",
                fontSize = 11.sp,
                color = TextMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1F8B5CF6))
                    .border(1.dp, Color(0x338B5CF6), RoundedCornerShape(8.dp))
                    .clickable { showRename = true; renameValue = slotWithEntries.slot.name }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Rename", fontSize = 11.sp, color = Violet400)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1AFB7185))
                    .border(1.dp, Color(0x33FB7185), RoundedCornerShape(8.dp))
                    .clickable { showDeleteConfirm = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Delete", fontSize = 11.sp, color = ErrorRed)
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename slot") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(renameValue); showRename = false }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }
    if (showDeleteConfirm) {
        val entryCount = slotWithEntries.entries.size
        val bodyText = if (entryCount > 0)
            "\"${slotWithEntries.slot.name}\" and its $entryCount ${if (entryCount == 1) "entry" else "entries"} will be removed."
        else
            "\"${slotWithEntries.slot.name}\" will be removed."
        ConfirmDialog(
            title = "Delete slot?",
            body = bodyText,
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = { onDelete(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun safeMacroFrac(value: Double, target: Int): Float =
    if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
