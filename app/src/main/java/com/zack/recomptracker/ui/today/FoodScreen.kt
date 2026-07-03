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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.zack.recomptracker.ui.component.PillStatus
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
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiInsightCard
import com.zack.recomptracker.ui.component.InsightCardHeader
import com.zack.recomptracker.ui.component.WeekCalorieStrip
import com.zack.recomptracker.ui.component.charts.CalorieProgressBar
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.VioletBadge
import com.zack.recomptracker.ui.component.ScreenHeader
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.streak.CalorieStreakChip
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.theme.Spacing
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt


@Composable
fun FoodScreen(
    viewModel: FoodLogViewModel,
    onAddToSlot: (slotId: Long, slotName: String, date: LocalDate) -> Unit,
    onBrowseLibrary: () -> Unit,
    onEditEntryAmount: (slotId: Long?, slotName: String, entryId: Long, date: LocalDate) -> Unit,
    onCreateRecipeFromSelection: (List<com.zack.recomptracker.data.local.entity.RecipeIngredientEntity>) -> Unit = {},
    onAskCoachForMeals: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val streakVm: com.zack.recomptracker.ui.streak.StreakViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = com.zack.recomptracker.ui.LocalAppContainer.current.viewModelFactory)
    val streakUi by streakVm.uiState.collectAsStateWithLifecycle()
    val calorieStreak = streakUi.streaks.calorie
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
        calorieStreak     = calorieStreak,
        onAddToSlot       = { slotId, slotName -> onAddToSlot(slotId, slotName, state.selectedDate) },
        onBrowseLibrary   = onBrowseLibrary,
        onEditEntryAmount = { slotId, slotName, entryId -> onEditEntryAmount(slotId, slotName, entryId, state.selectedDate) },
        onSelectDate      = viewModel::selectDate,
        onStartRecipeSelection = viewModel::startRecipeSelection,
        onToggleRecipeSelection = viewModel::toggleRecipeSelection,
        onCancelRecipeSelection = viewModel::cancelRecipeSelection,
        onSaveRecipeSelection = {
            onCreateRecipeFromSelection(viewModel.selectedRecipeIngredients())
            viewModel.cancelRecipeSelection()
        },
        onAskCoachForMeals = onAskCoachForMeals,
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
    calorieStreak: com.zack.recomptracker.domain.streak.StreakResult =
        com.zack.recomptracker.domain.streak.StreakResult.ZERO,
    onStartRecipeSelection: (Long) -> Unit = {},
    onToggleRecipeSelection: (Long) -> Unit = {},
    onCancelRecipeSelection: () -> Unit = {},
    onSaveRecipeSelection: () -> Unit = {},
    onAskCoachForMeals: () -> Unit = {},
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
                        onDaySelected  = onSelectDate,
                        onTodayClick   = { onSelectDate(state.today) },
                    )
                }

                // Nutrition strip
                item {
                    NutritionStrip(
                        state = state,
                        calorieStreak = calorieStreak,
                    )
                }

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

                if (state.isToday) {
                    state.mealSuggestion?.let { suggestion ->
                        item { MealSuggestionCard(state = suggestion, onAskCoach = onAskCoachForMeals) }
                    }
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
                        SectionLabel(text = "Meals")
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
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = "Food Log",
            subtitle = dateStr,
            trailing = {
                // Day navigation stepper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DayNavButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day", onClick = onPrevDay)
                    DayNavButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day", onClick = onNextDay)
                }
            },
        )
        if (isFuture) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.accent.copy(alpha = 0.20f))
                    .border(1.dp, accent.accent.copy(alpha = 0.40f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "PLANNING",
                    style = AppType.metaLabel,
                    color = accent.inkLight,
                )
            }
        }
    }
}

@Composable
private fun DayNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
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
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = appColors.textDim,
            modifier = Modifier.size(18.dp),
        )
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
                style = AppType.cardSubtitle.copy(fontWeight = FontWeight.Bold),
                color = appColors.textPrimary,
            )
            Text(
                text = "You planned $plannedCalories kcal for this day. Did you eat it?",
                style = AppType.metaLabel,
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
            style = AppType.metaLabel,
            color = appColors.textMuted,
        )
    }
}

// ── Meal suggestion ──────────────────────────────────────────────────────────

@Composable
private fun MealSuggestionCard(
    state: MealSuggestionCardState,
    onAskCoach: () -> Unit,
) {
    val appColors = LocalAppColors.current
    // Collapsible glass card — reuses the expandable AI-coach card (AiInsightCard + InsightCardHeader).
    // Starts COLLAPSED as a compact pill showing just the remaining-macro headline; tapping the header
    // expands it to the full suggestions + "Get meal ideas" button, so it takes minimal space by default.
    var collapsed by rememberSaveable(state.headline) { mutableStateOf(true) }
    AiInsightCard(
        borderMode = AiBorderMode.Ready,
        modifier = Modifier.animateContentSize(spring()),
        collapsed = collapsed,
        contentPadding = if (collapsed) 12.dp else 16.dp,
    ) {
        InsightCardHeader(
            title = "What to eat next",
            collapsible = true,
            collapsed = collapsed,
            // Collapsed pill shows the card's title (not the macro headline); the headline moves to
            // the expanded body below.
            collapsedVerdict = "What to eat next",
            onToggle = { collapsed = !collapsed },
        )
        if (!collapsed) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(state.headline, style = AppType.cardTitle, color = appColors.textPrimary)
                if (state.libraryThin) {
                    Text(
                        "Your library is thin here — ask the coach for ideas that fit.",
                        style = AppType.cardSubtitle, color = appColors.textMuted,
                    )
                } else {
                    state.suggestions.forEach { s ->
                        Text(
                            "${s.name} · ${s.amountLabel} · ${mealSuggestionMacroLine(state.focus, s.proteinG, s.carbsG)} · ${s.calories} kcal",
                            style = AppType.body, color = appColors.textSecondary,
                        )
                    }
                    state.combo?.let { c ->
                        Text(
                            "Combo: ${c.items.joinToString(" + ") { it.name }} · ${c.proteinG.roundToInt()} g P · ${c.calories} kcal",
                            style = AppType.cardSubtitle, color = appColors.textMuted,
                        )
                    }
                }
                LiquidActionButton(
                    text = "Get meal ideas",
                    onClick = onAskCoach,
                    isPrimary = true,
                    small = true,
                )
            }
        }
    }
}

private fun mealSuggestionMacroLine(
    focus: com.zack.recomptracker.domain.food.SuggestionFocus,
    proteinG: Double,
    carbsG: Double,
): String = when (focus) {
    com.zack.recomptracker.domain.food.SuggestionFocus.CARBS -> "${carbsG.roundToInt()} g carbs"
    else -> "${proteinG.roundToInt()} g protein"
}

internal enum class CalorieDayStatus { BelowZone, GoalHit, Over, Missed }

internal fun calorieStatus(
    cal: Int,
    zoneLow: Int,
    zoneHigh: Int,
    isToday: Boolean,
    isPast: Boolean,
): CalorieDayStatus = when {
    cal in zoneLow..zoneHigh -> CalorieDayStatus.GoalHit
    cal > zoneHigh           -> CalorieDayStatus.Over
    isPast && cal > 0        -> CalorieDayStatus.Missed
    else                     -> CalorieDayStatus.BelowZone
}

// ── Nutrition Strip ────────────────────────────────────────────────────────────

@Composable
private fun NutritionStrip(
    state: FoodLogUiState,
    calorieStreak: com.zack.recomptracker.domain.streak.StreakResult =
        com.zack.recomptracker.domain.streak.StreakResult.ZERO,
) {
    val cal         = state.totals.calories
    val target      = state.target
    val zoneLow     = target.calorieZoneLowerBound
    val zoneHigh    = target.calorieZoneUpperBound
    val scaleMax    = ((zoneHigh * 1.2).toInt()).coerceAtLeast(1)
    val calFrac     = (cal.toFloat() / scaleMax).coerceIn(0f, 1f)
    val plannedCal  = state.plannedTotals.calories
    val projectedFrac = if (plannedCal > 0)
        ((cal + plannedCal).toFloat() / scaleMax).coerceIn(0f, 1f) else 0f

    val appColors = LocalAppColors.current
    val accent    = LocalAppAccent.current

    val status = calorieStatus(cal, zoneLow, zoneHigh, state.isToday, state.isPast)

    val calSubText = when (status) {
        CalorieDayStatus.GoalHit   -> " kcal"
        CalorieDayStatus.Over      -> " kcal · ${cal - zoneHigh} over"
        CalorieDayStatus.Missed    -> " kcal · ${zoneLow - cal} below zone"
        CalorieDayStatus.BelowZone -> " kcal · ${zoneLow - cal} to zone"
    }

    // ── One-shot celebration (today: BelowZone → GoalHit transition) ──────────
    val prevInZone = remember { mutableStateOf(status == CalorieDayStatus.GoalHit) }
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(status) {
        val nowGoalHit = status == CalorieDayStatus.GoalHit
        if (nowGoalHit && !prevInZone.value && state.isToday) {
            scaleAnim.snapTo(1f)
            scaleAnim.animateTo(1.026f, tween(200))
            scaleAnim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
        }
        prevInZone.value = nowGoalHit
    }

    // ── Animated tint overlay colours ─────────────────────────────────────────
    val overlayColor by animateColorAsState(
        targetValue = when (status) {
            CalorieDayStatus.GoalHit -> if (appColors.isDark) Color(0x5522C55E) else Color(0x3322C55E)
            CalorieDayStatus.Missed  -> if (appColors.isDark) Color(0x55EF4444) else Color(0x33EF4444)
            else                     -> Color.Transparent
        },
        animationSpec = tween(600),
        label = "overlayBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when (status) {
            CalorieDayStatus.GoalHit -> if (appColors.isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
            CalorieDayStatus.Missed  -> if (appColors.isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
            else                     -> Color.Transparent
        },
        animationSpec = tween(600),
        label = "overlayBorder",
    )
    val barStart by animateColorAsState(
        targetValue = when (status) {
            CalorieDayStatus.GoalHit -> Color(0xFF16A34A)
            CalorieDayStatus.Missed  -> Color(0xFF991B1B)
            else                     -> accent.accent
        },
        animationSpec = tween(600),
        label = "barStart",
    )
    val barEnd by animateColorAsState(
        targetValue = when (status) {
            CalorieDayStatus.GoalHit -> Color(0xFF4ADE80)
            CalorieDayStatus.Missed  -> Color(0xFFDC2626)
            else                     -> accent.accentLight
        },
        animationSpec = tween(600),
        label = "barEnd",
    )

    // ── Text colours — force white on the coloured (GoalHit / Missed) states so
    //    content stays legible over the tint regardless of light/dark theme ──────
    val colored = status == CalorieDayStatus.GoalHit || status == CalorieDayStatus.Missed
    val primaryText by animateColorAsState(
        if (colored) Color.White else appColors.textPrimary,
        animationSpec = tween(600), label = "primaryText",
    )
    val mutedText by animateColorAsState(
        if (colored) Color.White.copy(alpha = 0.80f) else appColors.textMuted,
        animationSpec = tween(600), label = "mutedText",
    )
    val veryMutedText by animateColorAsState(
        if (colored) Color.White.copy(alpha = 0.65f) else appColors.textVeryMuted,
        animationSpec = tween(600), label = "veryMutedText",
    )
    val plannedText by animateColorAsState(
        if (colored) Color.White.copy(alpha = 0.90f) else accent.inkLight.copy(alpha = 0.85f),
        animationSpec = tween(600), label = "plannedText",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scaleAnim.value; scaleY = scaleAnim.value },
    ) {
        // surfaceTint paints the colour onto the card surface BELOW the content, so the
        // text and progress bar stay crisp on top — no overlay washing over them.
        FrostedCard(
            surfaceTint = overlayColor,
            borderColor = borderColor,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%,d", cal),
                        style = AppType.statValue.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp),
                        color = primaryText,
                    )
                    Text(
                        text = calSubText,
                        style = AppType.label,
                        color = mutedText,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (calorieStreak.current > 0) {
                        CalorieStreakChip(result = calorieStreak)
                    }
                    AnimatedContent(
                        targetState = status,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                        label = "badge",
                    ) { s ->
                        when (s) {
                            CalorieDayStatus.GoalHit   -> VioletBadge(PillStatus.GOOD,      "Goal hit!")
                            CalorieDayStatus.Missed    -> VioletBadge(PillStatus.OFF_TRACK, "Missed")
                            CalorieDayStatus.Over      -> VioletBadge(text = "Over")
                            CalorieDayStatus.BelowZone -> VioletBadge(text = "Below")
                        }
                    }
                }
            }

            if (plannedCal > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (cal > 0) "+$plannedCal kcal planned · ${cal + plannedCal} projected"
                           else "$plannedCal kcal planned",
                    style = AppType.metaLabel,
                    color = plannedText,
                )
            }
            Spacer(Modifier.height(8.dp))

            CalorieProgressBar(
                progress        = calFrac,
                zoneLowFrac     = (zoneLow.toFloat()  / scaleMax).coerceIn(0f, 1f),
                zoneHighFrac    = (zoneHigh.toFloat() / scaleMax).coerceIn(0f, 1f),
                plannedProgress = projectedFrac,
                fillColorStart  = barStart,
                fillColorEnd    = barEnd,
                modifier        = Modifier.fillMaxWidth().height(8.dp),
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MacroProgressItem(
                    label  = "Protein",
                    value  = "${state.totals.proteinG.toInt()}g",
                    remain = "${(target.targetProteinG - state.totals.proteinG).toInt().coerceAtLeast(0)}g to go",
                    frac   = safeMacroFrac(state.totals.proteinG, target.targetProteinG),
                    labelColor = mutedText,
                    valueColor = primaryText,
                    remainColor = veryMutedText,
                    modifier = Modifier.weight(1f),
                )
                MacroProgressItem(
                    label  = "Carbs",
                    value  = "${state.totals.carbsG.toInt()}g",
                    remain = "${(target.targetCarbsG - state.totals.carbsG).toInt().coerceAtLeast(0)}g to go",
                    frac   = safeMacroFrac(state.totals.carbsG, target.targetCarbsG),
                    labelColor = mutedText,
                    valueColor = primaryText,
                    remainColor = veryMutedText,
                    modifier = Modifier.weight(1f),
                )
                MacroProgressItem(
                    label  = "Fat",
                    value  = "${state.totals.fatG.toInt()}g",
                    remain = "${(target.targetFatG - state.totals.fatG).toInt().coerceAtLeast(0)}g to go",
                    frac   = safeMacroFrac(state.totals.fatG, target.targetFatG),
                    labelColor = mutedText,
                    valueColor = primaryText,
                    remainColor = veryMutedText,
                    modifier = Modifier.weight(1f),
                )
            }
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
    labelColor: Color = Color.Unspecified,
    valueColor: Color = Color.Unspecified,
    remainColor: Color = Color.Unspecified,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val resolvedLabel  = if (labelColor  != Color.Unspecified) labelColor  else appColors.textMuted
    val resolvedValue  = if (valueColor  != Color.Unspecified) valueColor  else appColors.textPrimary
    val resolvedRemain = if (remainColor != Color.Unspecified) remainColor else appColors.textVeryMuted
    val fillBrush = remember(accent.accent, accent.accentLight) {
        Brush.horizontalGradient(listOf(accent.accent, accent.accentLight))
    }
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
                color = resolvedLabel,
                letterSpacing = 0.08.sp,
            )
            Text(
                text = value,
                style = AppType.body.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
                color = resolvedValue,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(appColors.cardBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFrac)
                    .height(6.dp)
                    .background(
                        fillBrush,
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
        Text(text = remain, fontSize = 8.sp, color = resolvedRemain)
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
                    style = AppType.cardSubtitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.02.sp),
                    color = appColors.textPrimary,
                )
                Text(
                    text = if (hasEntries) {
                        val t = slotWithEntries.totals
                        "${t.calories} kcal · ${t.proteinG.roundToInt()}P · ${t.carbsG.roundToInt()}C · ${t.fatG.roundToInt()}F"
                    } else "empty",
                    style = AppType.metaLabel.copy(fontWeight = if (hasEntries) FontWeight.SemiBold else FontWeight.Normal),
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
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = appColors.textDim,
                                modifier = Modifier.size(17.dp),
                            )
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
                    LiquidGlassButton(
                        onClick = onAddClick,
                        tint = accent.accent,
                        surfaceColor = Color.White.copy(alpha = 0.08f),
                        buttonHeight = 32.dp,
                        lite = true,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = accent.accentLighter,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Add",
                            style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                            color = accent.accentLighter,
                        )
                    }
                }
            }
        }

        val hairlineColor = appColors.cardBorder
        if (hasEntries) {
            // Entry area: inset background (dark recess on dark, frosted white on light)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (appColors.isDark) Color(0x33000000) else appColors.cardSurface)
                    .drawBehind {
                        drawLine(
                            color = hairlineColor,
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
                    .background(if (appColors.isDark) Color(0x26000000) else appColors.cardSurface)
                    .drawBehind {
                        drawLine(
                            color = hairlineColor,
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
                Text("nothing logged yet", style = AppType.metaLabel, color = appColors.textFaint)
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
                    style = AppType.label.copy(fontWeight = FontWeight.SemiBold),
                    color = accentBar.inkLight,
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
                if (isSelected) Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
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
                    style = AppType.cardSubtitle.copy(fontWeight = FontWeight.Medium),
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
                        Text("Planned", style = AppType.metaLabel.copy(fontWeight = FontWeight.SemiBold), color = accent.inkLight)
                    }
                }
            }
            Text(text = macroStr, style = AppType.metaLabel.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp), color = appColors.textFaint)
        }
        Text(
            text = "${entry.calories}",
            style = AppType.cardSubtitle.copy(fontWeight = FontWeight.Bold),
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
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(15.dp),
                    )
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
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = accent.inkLight,
                        modifier = Modifier.size(14.dp),
                    )
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Move to next day",
                        tint = appColors.textDim,
                        modifier = Modifier.size(15.dp),
                    )
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
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = ErrorRed.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp),
                )
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
            Text(slotWithEntries.slot.name, style = AppType.cardTitle, color = appColors.textPrimary)
            Text(
                "${slotWithEntries.entries.size} items · ${slotWithEntries.totals.calories} kcal",
                style = AppType.label,
                color    = appColors.textMuted,
            )
        }

        // ── Actions ───────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LiquidActionButton(
                text = "Rename",
                onClick = { showRename = true; renameValue = slotWithEntries.slot.name },
                small = true,
                lite = true,
            )
            LiquidGlassButton(
                onClick = { showDeleteConfirm = true },
                buttonHeight = 32.dp,
                lite = true,
                tint = ErrorRed,
            ) {
                Text("Delete", style = AppType.body.copy(fontWeight = FontWeight.SemiBold), color = ErrorRed)
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
