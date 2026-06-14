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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.ui.component.GeneratedInsightCard
import com.zack.recomptracker.ui.component.WeekCalorieStrip
import com.zack.recomptracker.ui.component.charts.CalorieProgressBar
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.VioletBadge
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale


@Composable
fun FoodScreen(
    viewModel: FoodLogViewModel,
    onAddToSlot: (slotId: Long, slotName: String, date: LocalDate) -> Unit,
    onBrowseLibrary: () -> Unit,
    onEditEntryAmount: (slotId: Long?, slotName: String, entryId: Long, date: LocalDate) -> Unit,
    onCreateRecipeFromSelection: (List<com.zack.recomptracker.data.local.entity.RecipeIngredientEntity>) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restOfDayInsightState by viewModel.restOfDayInsightState.collectAsStateWithLifecycle()
    val actions = remember {
        FoodActions(
            onToggleEditMode = viewModel::toggleEditMode,
            onAddSlot        = viewModel::addSlot,
            onRenameSlot     = viewModel::renameSlot,
            onDeleteSlot     = viewModel::deleteSlot,
            onReorderSlots   = viewModel::reorderSlots,
            onDeleteMeal     = viewModel::deleteMeal,
            onEditMacros     = viewModel::updateMealMacros,
            onConfirmMeal    = viewModel::confirmMeal,
            onPostponeMeal   = viewModel::postponeMeal,
            onConfirmAllPlanned = viewModel::confirmAllPlanned,
        )
    }
    FoodContent(
        state   = state,
        actions = actions,
        onAddToSlot       = { slotId, slotName -> onAddToSlot(slotId, slotName, state.selectedDate) },
        onBrowseLibrary   = onBrowseLibrary,
        onEditEntryAmount = { slotId, slotName, entryId -> onEditEntryAmount(slotId, slotName, entryId, state.selectedDate) },
        onSelectDate      = viewModel::selectDate,
        restOfDayInsightState  = restOfDayInsightState,
        restOfDayAvailable     = state.restOfDayInsightContext?.hasSufficientData == true,
        onRevealRestOfDay      = viewModel::onRestOfDayInsightVisible,
        onRetryRestOfDay       = viewModel::retryRestOfDayInsight,
        onStartRecipeSelection = viewModel::startRecipeSelection,
        onToggleRecipeSelection = viewModel::toggleRecipeSelection,
        onCancelRecipeSelection = viewModel::cancelRecipeSelection,
        onSaveRecipeSelection = {
            onCreateRecipeFromSelection(viewModel.selectedRecipeIngredients())
            viewModel.cancelRecipeSelection()
        },
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
    val onConfirmMeal: (Long) -> Unit = {},
    val onPostponeMeal: (Long) -> Unit = {},
    val onConfirmAllPlanned: () -> Unit = {},
)

@Composable
fun FoodContent(
    state: FoodLogUiState,
    actions: FoodActions,
    onAddToSlot: (slotId: Long, slotName: String) -> Unit,
    onBrowseLibrary: () -> Unit,
    onEditEntryAmount: (slotId: Long?, slotName: String, entryId: Long) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    restOfDayInsightState: AiInsightState = AiInsightState.Disabled,
    restOfDayAvailable: Boolean = false,
    onRevealRestOfDay: () -> Unit = {},
    onRetryRestOfDay: () -> Unit = {},
    onStartRecipeSelection: (Long) -> Unit = {},
    onToggleRecipeSelection: (Long) -> Unit = {},
    onCancelRecipeSelection: () -> Unit = {},
    onSaveRecipeSelection: () -> Unit = {},
) {
    val accent = LocalAppAccent.current
    val foodScreenOrbBrush = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.16f), Color.Transparent))
    }
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var newSlotName by remember { mutableStateOf("") }

    val currentSlots by rememberUpdatedState(state.slots)
    val currentOnReorderSlots by rememberUpdatedState(actions.onReorderSlots)

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as Long
        val toKey   = to.key   as Long
        val ids     = currentSlots.map { it.slot.id }.toMutableList()
        val fromIdx = ids.indexOf(fromKey)
        val toIdx   = ids.indexOf(toKey)
        if (fromIdx >= 0 && toIdx >= 0) {
            ids.add(toIdx, ids.removeAt(fromIdx))
            currentOnReorderSlots(ids)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Ambient orb — top-left
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(foodScreenOrbBrush),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            FoodScreenHeader(
                date     = state.selectedDate,
                isFuture = state.isFuture,
                onPrevDay = { onSelectDate(state.selectedDate.minusDays(1)) },
                onNextDay = { onSelectDate(state.selectedDate.plusDays(1)) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            )

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    WeekCalorieStrip(
                        weekData       = state.weekSummary,
                        selectedDate   = state.selectedDate,
                        today          = state.today,
                        targetCalories = state.target.targetCalories,
                        targetLow      = state.target.calorieZoneLowerBound,
                        targetHigh     = state.target.calorieZoneUpperBound,
                        onDaySelected  = onSelectDate,
                        onTodayClick   = { onSelectDate(state.today) },
                    )
                }

                // Nutrition strip
                item { NutritionStrip(state) }

                // Reconcile banner — past day with unconfirmed plans
                if (state.isPast && state.hasPlannedEntries) {
                    item {
                        ReconcilePlannedBanner(
                            plannedCalories = state.plannedTotals.calories,
                            onConfirmAll = actions.onConfirmAllPlanned,
                        )
                    }
                }

                // Gentle nudge on today when plans linger unconfirmed on past days
                if (state.isToday && state.stalePlannedCount > 0) {
                    item { StalePlannedHint(count = state.stalePlannedCount) }
                }

                item {
                    RestOfDayReveal(
                        available = restOfDayAvailable,
                        state = restOfDayInsightState,
                        onReveal = onRevealRestOfDay,
                        onRetry = onRetryRestOfDay,
                    )
                }

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
                            color = LocalAppColors.current.textMuted,
                            letterSpacing = 0.12.sp,
                        )
                        LiquidActionButton(
                            text = if (state.slotsEditMode) "Done" else "Edit",
                            onClick = actions.onToggleEditMode,
                            isPrimary = false,
                            small = true,
                        )
                    }
                }

                items(state.slots, key = { it.slot.id }) { slotWithEntries ->
                    if (state.slotsEditMode) {
                        ReorderableItem(reorderState, key = slotWithEntries.slot.id) { isDragging ->
                            EditModeSlotCard(
                                slotWithEntries    = slotWithEntries,
                                isDragging         = isDragging,
                                dragHandleModifier = Modifier.draggableHandle(),
                                onRename           = { actions.onRenameSlot(slotWithEntries.slot.id, it) },
                                onDelete           = { actions.onDeleteSlot(slotWithEntries.slot.id) },
                            )
                        }
                    } else {
                        val sel = state.recipeSelection?.takeIf { it.slotId == slotWithEntries.slot.id }
                        LockedSlotCard(
                            slotWithEntries   = slotWithEntries,
                            isFuture          = state.isFuture,
                            isPast            = state.isPast,
                            onAddClick        = { onAddToSlot(slotWithEntries.slot.id, slotWithEntries.slot.name) },
                            onDeleteEntry     = actions.onDeleteMeal,
                            onEditEntryAmount = { entryId -> onEditEntryAmount(slotWithEntries.slot.id, slotWithEntries.slot.name, entryId) },
                            onEditMacros      = actions.onEditMacros,
                            onConfirmEntry    = actions.onConfirmMeal,
                            onPostponeEntry   = actions.onPostponeMeal,
                            isSelecting       = sel != null,
                            selectedIds       = sel?.selectedIds.orEmpty(),
                            onStartRecipeSelection = { onStartRecipeSelection(slotWithEntries.slot.id) },
                            onToggleSelection = onToggleRecipeSelection,
                            onCancelSelection = onCancelRecipeSelection,
                            onSaveSelection   = onSaveRecipeSelection,
                        )
                    }
                }

                item {
                    LiquidSecondaryButton(
                        text = "+ Add meal slot",
                        onClick = { showAddSlotDialog = true },
                    )
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
private fun FoodScreenHeader(
    date: LocalDate,
    isFuture: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val dateStr = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEE, MMMM d", Locale.getDefault()))
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Food Log",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = appColors.textPrimary,
                letterSpacing = (-0.8).sp,
            )
            if (isFuture) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(accent.accent.copy(alpha = 0.20f))
                        .border(1.dp, accent.accent.copy(alpha = 0.40f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "PLANNING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent.accentLight,
                        letterSpacing = 0.10.sp,
                    )
                }
            }
        }
        // Day navigation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DayNavButton(text = "‹", onClick = onPrevDay)
            Text(
                text = dateStr,
                fontSize = 12.sp,
                color = appColors.textMuted,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            DayNavButton(text = "›", onClick = onNextDay)
        }
    }
}

@Composable
private fun DayNavButton(text: String, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, color = appColors.textDim, fontWeight = FontWeight.Bold)
    }
}

// ── Reconcile planned banner ─────────────────────────────────────────────────

@Composable
private fun ReconcilePlannedBanner(
    plannedCalories: Int,
    onConfirmAll: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(accent.accent.copy(alpha = 0.10f))
            .border(1.dp, accent.accent.copy(alpha = 0.28f), RoundedCornerShape(CornerCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Planned meals not confirmed",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary,
            )
            Text(
                text = "You planned $plannedCalories kcal for this day. Did you eat it?",
                fontSize = 10.sp,
                color = appColors.textMuted,
            )
        }
        LiquidActionButton(
            text = "Confirm all",
            onClick = onConfirmAll,
            isPrimary = true,
            small = true,
        )
    }
}

// ── Rest of Day Insight ──────────────────────────────────────────────────────

@Composable
private fun RestOfDayReveal(
    available: Boolean,
    state: AiInsightState,
    onReveal: () -> Unit,
    onRetry: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    when {
        revealed -> GeneratedInsightCard(
            title = "Rest of day",
            state = state,
            onRetry = onRetry,
        )
        available -> TextButton(onClick = { revealed = true; onReveal() }) {
            Text("✨ Rest of day?")
        }
    }
}

@Composable
private fun StalePlannedHint(count: Int) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerCard))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🗓", fontSize = 13.sp)
        Text(
            text = "$count planned ${if (count == 1) "meal" else "meals"} on past days await confirmation — use ‹ to review.",
            fontSize = 10.sp,
            color = appColors.textMuted,
        )
    }
}

// ── Nutrition Strip ────────────────────────────────────────────────────────────

@Composable
private fun NutritionStrip(state: FoodLogUiState) {
    val cal     = state.totals.calories
    val target  = state.target
    val zoneLow = target.calorieZoneLowerBound
    val zoneHigh = target.calorieZoneUpperBound
    val scaleMax = ((zoneHigh * 1.2).toInt()).coerceAtLeast(1)
    val calFrac  = (cal.toFloat() / scaleMax).coerceIn(0f, 1f)
    val plannedCalForBar = state.plannedTotals.calories
    val projectedFrac = if (plannedCalForBar > 0)
        ((cal + plannedCalForBar).toFloat() / scaleMax).coerceIn(0f, 1f) else 0f

    val isInZone = cal in zoneLow..zoneHigh
    val isOver   = cal > zoneHigh
    val badgeText = if (isInZone) "In zone" else if (isOver) "Over" else "Below"
    val calSubText = when {
        isInZone -> " kcal · in zone"
        isOver   -> " kcal · ${cal - zoneHigh} over"
        else     -> " kcal · ${zoneLow - cal} to zone"
    }

    val appColors = LocalAppColors.current
    FrostedCard {
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
                    color = appColors.textPrimary,
                    letterSpacing = (-0.8).sp,
                )
                Text(
                    text = calSubText,
                    fontSize = 11.sp,
                    color = appColors.textMuted,
                )
            }
            VioletBadge(text = badgeText)
        }

        // Planned projection — shown when the day has any planned (not-yet-eaten) entries
        val plannedCal = state.plannedTotals.calories
        if (plannedCal > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (cal > 0) "+$plannedCal kcal planned · ${cal + plannedCal} projected"
                       else "$plannedCal kcal planned",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppAccent.current.accentLight.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.height(8.dp))

        // Calorie bar
        CalorieProgressBar(
            progress = calFrac,
            zoneLowFrac = (zoneLow.toFloat() / scaleMax).coerceIn(0f, 1f),
            zoneHighFrac = (zoneHigh.toFloat() / scaleMax).coerceIn(0f, 1f),
            plannedProgress = projectedFrac,
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
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
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
                color = appColors.textMuted,
                letterSpacing = 0.08.sp,
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = appColors.textPrimary,
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
                        Brush.horizontalGradient(listOf(accent.accent, accent.accentLight)),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
        Text(text = remain, fontSize = 8.sp, color = appColors.textVeryMuted)
    }
}

// ── Locked Slot Card ───────────────────────────────────────────────────────────

@Composable
private fun LockedSlotCard(
    slotWithEntries: MealSlotWithEntries,
    isFuture: Boolean,
    isPast: Boolean,
    onAddClick: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onEditEntryAmount: (Long) -> Unit,
    onEditMacros: (MealEntryEntity, Int, Double, Double, Double) -> Unit,
    onConfirmEntry: (Long) -> Unit,
    onPostponeEntry: (Long) -> Unit,
    isSelecting: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onStartRecipeSelection: () -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onSaveSelection: () -> Unit = {},
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val hasEntries = slotWithEntries.entries.isNotEmpty()
    val cardBg     = if (hasEntries) accent.accent.copy(alpha = 0.04f) else appColors.cardSurface
    val cardBorder = if (hasEntries) accent.accent.copy(alpha = 0.18f) else appColors.cardBorder

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(CornerCard)),
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
                    color = appColors.textPrimary,
                    letterSpacing = 0.02.sp,
                )
                Text(
                    text = if (hasEntries) "${slotWithEntries.totals.calories} kcal" else "empty",
                    fontSize = 10.sp,
                    fontWeight = if (hasEntries) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (hasEntries) accent.accentLight else appColors.textMuted,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasEntries && !isSelecting) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(appColors.cardSurface)
                                .border(1.dp, appColors.cardBorder, RoundedCornerShape(8.dp))
                                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { menuOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⋯", fontSize = 15.sp, color = appColors.textDim)
                        }
                        androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Save as recipe") },
                                onClick = { menuOpen = false; onStartRecipeSelection() },
                            )
                        }
                    }
                }
                if (!isSelecting) {
                    LiquidActionButton(
                        text = "＋ Add",
                        onClick = onAddClick,
                        isPrimary = true,
                        small = true,
                    )
                }
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
                    key(entry.id) {
                        if (i > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(appColors.cardBorder),
                            )
                        }
                        SlotEntryRow(
                            entry = entry,
                            // Confirm only makes sense for plans on today or a past day —
                            // you can't "have eaten" something on a future date.
                            canConfirm = entry.planned && !isFuture,
                            canPostpone = !isPast,
                            onDelete = onDeleteEntry,
                            onConfirm = { onConfirmEntry(entry.id) },
                            onPostpone = { onPostponeEntry(entry.id) },
                            onEditAmount = { onEditEntryAmount(entry.id) },
                            onEditMacros = onEditMacros,
                            isSelecting = isSelecting,
                            isSelected = entry.id in selectedIds,
                            onToggleSelection = { onToggleSelection(entry.id) },
                        )
                    }
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
                Box(modifier = Modifier.weight(1f).height(1.dp).background(appColors.cardBorder))
                Text("nothing logged yet", fontSize = 10.sp, color = appColors.textFaint)
                Box(modifier = Modifier.weight(1f).height(1.dp).background(appColors.cardBorder))
            }
        }

        if (isSelecting) {
            val accentBar = LocalAppAccent.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentBar.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${selectedIds.size} selected",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentBar.accentLight,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidActionButton(text = "Cancel", onClick = onCancelSelection, isPrimary = false, small = true)
                    if (selectedIds.isNotEmpty()) {
                        LiquidActionButton(text = "Save as recipe", onClick = onSaveSelection, isPrimary = true, small = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotEntryRow(
    entry: MealEntryEntity,
    canConfirm: Boolean,
    canPostpone: Boolean,
    onDelete: (Long) -> Unit,
    onConfirm: () -> Unit,
    onPostpone: () -> Unit,
    onEditAmount: () -> Unit,
    onEditMacros: (MealEntryEntity, Int, Double, Double, Double) -> Unit,
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    var showMacroEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val amountEditable = entry.amountGrams != null && entry.basePer100Calories != null
    val planned = entry.planned

    // Planned entries read as "intentions": dimmer accent rail and text.
    val nameColor = if (planned) appColors.textMuted else appColors.textDim
    val railColor = if (planned) accent.accent.copy(alpha = 0.55f) else accent.accent.copy(alpha = 0.30f)

    val macroStr = buildString {
        entry.amountGrams?.let { append("${it.toInt()}g · ") }
        append("${entry.proteinG.toInt()}P ${entry.carbsG.toInt()}C ${entry.fatG.toInt()}F")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelecting) onToggleSelection()
                else if (amountEditable) onEditAmount() else showMacroEdit = true
            }
            .drawBehind {
                drawRect(
                    color    = railColor,
                    topLeft  = Offset(0f, 0f),
                    size     = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
                )
            }
            .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSelecting) {
            val accentSel = LocalAppAccent.current
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) accentSel.accent else appColors.cardSurface)
                    .border(
                        1.dp,
                        if (isSelected) accentSel.accent else appColors.cardBorder,
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = entry.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (planned) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.accent.copy(alpha = 0.34f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text("Planned", fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = accent.accentLight)
                    }
                }
            }
            Text(text = macroStr, fontSize = 9.sp, color = appColors.textFaint)
        }
        Text(
            text = "${entry.calories}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = appColors.textMuted,
        )
        if (!isSelecting) {
            // Confirm button (planned → eaten) — replaces edit for confirmable plans
            if (canConfirm) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2234D399))
                        .clickable { onConfirm() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", fontSize = 13.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }
            } else {
                // Edit button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.tintedSurface)
                        .clickable { if (amountEditable) onEditAmount() else showMacroEdit = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✎", fontSize = 12.sp, color = accent.accentLight)
                }
            }
            // Postpone button (move to next day) — only when not viewing a past day
            if (canPostpone) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(appColors.cardSurface)
                        .clickable { onPostpone() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⤍", fontSize = 13.sp, color = appColors.textDim)
                }
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
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    var showRename        by remember { mutableStateOf(false) }
    var renameValue       by remember(slotWithEntries.slot.id) { mutableStateOf(slotWithEntries.slot.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (isDragging) {
                    scaleX          = 1.02f
                    scaleY          = 1.02f
                    shadowElevation = 24f
                }
            }
            .clip(RoundedCornerShape(CornerCard))
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerCard))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Drag handle ───────────────────────────────────────────────────────
        Box(
            modifier = dragHandleModifier
                .size(width = 32.dp, height = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(appColors.cardSurface)
                .border(1.dp, appColors.cardBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint               = appColors.textMuted,
                modifier           = Modifier.size(18.dp),
            )
        }

        // ── Slot info ─────────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(slotWithEntries.slot.name, fontWeight = FontWeight.SemiBold, color = appColors.textPrimary)
            Text(
                "${slotWithEntries.entries.size} items · ${slotWithEntries.totals.calories} kcal",
                fontSize = 11.sp,
                color    = appColors.textMuted,
            )
        }

        // ── Actions ───────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.tintedSurface)
                    .border(1.dp, accent.tintedBorder, RoundedCornerShape(8.dp))
                    .clickable { showRename = true; renameValue = slotWithEntries.slot.name }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Rename", fontSize = 11.sp, color = accent.accentLight)
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
                    value         = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine    = true,
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
            title         = "Delete slot?",
            body          = bodyText,
            confirmLabel  = "Delete",
            isDestructive = true,
            onConfirm     = { onDelete(); showDeleteConfirm = false },
            onDismiss     = { showDeleteConfirm = false },
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun safeMacroFrac(value: Double, target: Int): Float =
    if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
