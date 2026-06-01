package com.zack.recomptracker.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.ui.component.CalorieZoneBar
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.MacroMiniBar
import com.zack.recomptracker.ui.component.SectionCard

private val Blue = Color(0xFF3b82f6)
private val DangerBg = Color(0xFF3d1515)
private val DangerText = Color(0xFFf87171)
private val Secondary = Color(0xFF6b7280)

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
            onAddSlot = viewModel::addSlot,
            onRenameSlot = viewModel::renameSlot,
            onDeleteSlot = viewModel::deleteSlot,
            onReorderSlots = viewModel::reorderSlots,
            onDeleteMeal = viewModel::deleteMeal,
            onEditMacros = viewModel::updateMealMacros,
        ),
        onAddToSlot = onAddToSlot,
        onBrowseLibrary = onBrowseLibrary,
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

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Food",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Daily nutrition · ${state.date}",
                        color = Secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onBrowseLibrary) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Browse saved foods and meals",
                        tint = Blue,
                    )
                }
            }
        }

        item {
            SectionCard("Nutrition target") {
                CalorieZoneBar(
                    eaten = state.totals.calories,
                    zoneLower = state.target.calorieZoneLowerBound,
                    zoneUpper = state.target.calorieZoneUpperBound,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroMiniBar(
                        label = "Protein",
                        eaten = state.totals.proteinG,
                        target = state.target.targetProteinG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Carbs",
                        eaten = state.totals.carbsG,
                        target = state.target.targetCarbsG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Fat",
                        eaten = state.totals.fatG,
                        target = state.target.targetFatG,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MEALS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                    color = Secondary,
                )
                TextButton(onClick = actions.onToggleEditMode) {
                    Icon(
                        imageVector = if (state.slotsEditMode) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = if (state.slotsEditMode) "Done" else "Reorder",
                        tint = if (state.slotsEditMode) Blue else Secondary,
                    )
                    Text(
                        text = if (state.slotsEditMode) "Done" else "Reorder",
                        color = if (state.slotsEditMode) Blue else Secondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
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
                        if (i > 0) {
                            ids.add(i - 1, ids.removeAt(i))
                            actions.onReorderSlots(ids)
                        }
                    },
                    onMoveDown = {
                        val ids = state.slots.map { it.slot.id }.toMutableList()
                        val i = ids.indexOf(slotWithEntries.slot.id)
                        if (i < ids.lastIndex) {
                            ids.add(i + 1, ids.removeAt(i))
                            actions.onReorderSlots(ids)
                        }
                    },
                    onRename = { actions.onRenameSlot(slotWithEntries.slot.id, it) },
                    onDelete = { actions.onDeleteSlot(slotWithEntries.slot.id) },
                )
            } else {
                LockedSlotCard(
                    slotWithEntries = slotWithEntries,
                    onAddClick = { onAddToSlot(slotWithEntries.slot.id, slotWithEntries.slot.name) },
                    onDeleteEntry = actions.onDeleteMeal,
                    onEditEntryAmount = { entryId -> onEditEntryAmount(slotWithEntries.slot.id, slotWithEntries.slot.name, entryId) },
                    onEditMacros = actions.onEditMacros,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = { showAddSlotDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add meal slot", modifier = Modifier.padding(start = 4.dp))
            }
        }

        item {
            OutlinedButton(
                onClick = onBrowseLibrary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null)
                Text("Browse saved foods & meals", modifier = Modifier.padding(start = 8.dp))
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
                TextButton(onClick = { showAddSlotDialog = false; newSlotName = "" }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun LockedSlotCard(
    slotWithEntries: MealSlotWithEntries,
    onAddClick: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onEditEntryAmount: (Long) -> Unit,
    onEditMacros: (MealEntryEntity, Int, Double, Double, Double) -> Unit,
) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = slotWithEntries.slot.name.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Secondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (slotWithEntries.entries.isNotEmpty()) {
                        Text(
                            text = "${slotWithEntries.totals.calories} kcal",
                            fontSize = 11.sp,
                            color = Secondary,
                        )
                    }
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    ) {
                        Text("+ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (slotWithEntries.entries.isEmpty()) {
                Text("Empty — tap + Add", fontSize = 12.sp, color = Color(0xFF444444))
            } else {
                slotWithEntries.entries.forEachIndexed { i, entry ->
                    if (i > 0) HorizontalDivider(color = Color(0xFF222222))
                    SlotEntryRow(
                        entry = entry,
                        onDelete = onDeleteEntry,
                        onEditAmount = { onEditEntryAmount(entry.id) },
                        onEditMacros = onEditMacros,
                    )
                }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (amountEditable) onEditAmount() else showMacroEdit = true },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "${entry.calories} kcal · ${entry.proteinG.toInt()}P ${entry.carbsG.toInt()}C ${entry.fatG.toInt()}F",
                fontSize = 11.sp,
                color = Secondary,
            )
        }
        TextButton(onClick = { showDeleteConfirm = true }) {
            Text("Delete", color = DangerText, fontSize = 11.sp)
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
            onConfirm = {
                onDelete(entry.id)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun MacroEditDialog(
    entry: MealEntryEntity,
    onDismiss: () -> Unit,
    onSave: (Int, Double, Double, Double) -> Unit,
) {
    var cal by remember(entry.id) { mutableStateOf(entry.calories.toString()) }
    var p by remember(entry.id) { mutableStateOf(entry.proteinG.toInt().toString()) }
    var c by remember(entry.id) { mutableStateOf(entry.carbsG.toInt().toString()) }
    var f by remember(entry.id) { mutableStateOf(entry.fatG.toInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${entry.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.zack.recomptracker.ui.component.NumberField("Calories", cal, { cal = it }, suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.zack.recomptracker.ui.component.NumberField("Protein", p, { p = it }, Modifier.weight(1f), "g")
                    com.zack.recomptracker.ui.component.NumberField("Carbs", c, { c = it }, Modifier.weight(1f), "g")
                    com.zack.recomptracker.ui.component.NumberField("Fat", f, { f = it }, Modifier.weight(1f), "g")
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

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Up/down arrows
            Column {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = if (canMoveUp) Blue else Color(0xFF333333),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = if (canMoveDown) Blue else Color(0xFF333333),
                    )
                }
            }
            // Slot info
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(slotWithEntries.slot.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${slotWithEntries.entries.size} items · ${slotWithEntries.totals.calories} kcal",
                    fontSize = 11.sp,
                    color = Secondary,
                )
            }
            // Rename / Delete
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { showRename = true; renameValue = slotWithEntries.slot.name },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                ) { Text("Rename", fontSize = 11.sp) }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerBg),
                ) { Text("Delete", fontSize = 11.sp, color = DangerText) }
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
}
