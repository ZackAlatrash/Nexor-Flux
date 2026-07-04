package com.zack.recomptracker.ui.foodlibrary

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.domain.food.FoodScaling
import com.zack.recomptracker.domain.food.MealImpact
import com.zack.recomptracker.domain.food.RecipeWithIngredients
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastMessage
import com.zack.recomptracker.ui.toast.ToastType
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.component.AmountMode
import com.zack.recomptracker.ui.component.GlassBottomSheet
import com.zack.recomptracker.ui.component.GlassSegmentedToggle
import com.zack.recomptracker.ui.component.AmountPreviewStat
import com.zack.recomptracker.ui.component.AmountStepper
import com.zack.recomptracker.ui.component.FoodAmountPanel
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.GlassAlertDialog
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.MessageKind
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerChip
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun FoodLibraryScreen(
    viewModel: FoodLibraryViewModel,
    slotId: Long?,
    slotName: String,
    onBack: () -> Unit,
    editEntryId: Long? = null,
    logDate: String = "",
    onScanBarcode: () -> Unit,
    pickerMode: Boolean = false,
    scannedFoodJson: String? = null,
    onScannedFoodConsumed: () -> Unit = {},
    onIngredientPicked: (String) -> Unit = {},
    onCreateRecipe: () -> Unit = {},
    onEditRecipe: (Long) -> Unit = {},
) {
    LaunchedEffect(Unit) { viewModel.init(slotId, slotName, editEntryId, logDate, pickerMode) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    val toastController = LocalToastController.current
    LaunchedEffect(viewModel) {
        viewModel.loggedEvent.collect { message ->
            toastController.show(ToastMessage(message, ToastType.Success))
        }
    }

    // Scanned food arrives from barcode scanner in picker mode
    LaunchedEffect(scannedFoodJson) {
        val json = scannedFoodJson ?: return@LaunchedEffect
        try {
            val food = Json.decodeFromString<SavedFoodEntity>(json)
            viewModel.requestLogFood(food)
        } catch (_: Exception) {
            // Malformed JSON — discard silently
        } finally {
            onScannedFoodConsumed()
        }
    }

    // Ingredient confirmed in picker mode — return it to RecipeBuilderScreen
    LaunchedEffect(viewModel) {
        viewModel.ingredientPickedEvent.collect { ingredient ->
            onIngredientPicked(Json.encodeToString(ingredient))
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top Bar ───────────────────────────────────────────────────────────
        FoodLibraryTopBar(
            slotId = slotId,
            slotName = state.slotName,
            remainingCalories = state.remainingCalories,
            onBack = onBack,
            pickerMode = pickerMode,
        )

        MessageText(
            message = state.message,
            kind = state.messageKind,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // ── Search Field ──────────────────────────────────────────────────────
        GlassSearchField(
            query = state.query,
            onQueryChanged = viewModel::onQueryChanged,
            category = state.category,
            onScanBarcode = onScanBarcode,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        // ── Category Chips ────────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(FoodCategory.entries) { cat ->
                val isActive = state.category == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CornerChip))
                        .background(if (isActive) accent.accent.copy(alpha = 0.20f) else appColors.cardSurface)
                        .border(
                            1.dp,
                            if (isActive) accent.accent.copy(alpha = 0.35f) else appColors.cardBorder,
                            RoundedCornerShape(CornerChip),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { viewModel.onCategoryChanged(cat) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = cat.label(),
                        style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isActive) accent.accentLighter else appColors.textPrimary.copy(alpha = 0x59 / 255f),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Action Row ────────────────────────────────────────────────────────
        if (state.category != FoodCategory.NEVO && state.category != FoodCategory.OFF) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassActionButton(
                    text = "New food",
                    onClick = viewModel::toggleCreateFoodForm,
                    isPrimary = true,
                    leadingIcon = Icons.Default.Add,
                    modifier = Modifier.weight(1f),
                )
                GlassActionButton(
                    text = "Quick add",
                    onClick = viewModel::openQuickAdd,
                    isPrimary = false,
                    leadingIcon = Icons.Default.Bolt,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Create Recipe button (pinned above list, hidden in picker mode) ───
        if (!pickerMode && (state.category == FoodCategory.ALL || state.category == FoodCategory.MEALS)) {
            GlassActionButton(
                text = "Create Recipe",
                onClick = onCreateRecipe,
                isPrimary = true,
                leadingIcon = Icons.Default.Add,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
        }

        // ── Food List ─────────────────────────────────────────────────────────
        // filteredFoods / filteredMeals / recentFoods are pre-computed by the ViewModel
        // (via withComputedFields()) whenever filtering inputs change — no main-thread
        // work here, just reading pre-computed fields from state.
        val filteredFoods = state.filteredFoods
        val filteredMeals = state.filteredMeals

        val category = state.category
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
                if (state.offSearchLoading) {
                    item(key = "${category}_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = accent.inkLight)
                        }
                    }
                }

                if (state.recentFoods.isNotEmpty() && state.query.isBlank()) {
                    item(key = "${category}_recents_header") {
                        SectionLabel("Recents", modifier = Modifier.padding(vertical = 8.dp))
                    }
                    item(key = "${category}_recents_row") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.recentFoods, key = { "recent_${it.name}" }) { food ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(CornerChip))
                                        .background(appColors.cardSurface)
                                        .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerChip))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { viewModel.requestLogFood(food) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                ) {
                                    Text(food.name, style = AppType.body, color = appColors.textPrimary.copy(alpha = 0xBF / 255f))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (category != FoodCategory.MEALS) {
                    if (filteredFoods.isEmpty() && !state.offSearchLoading) {
                        item(key = "${category}_empty_state") { EmptyStateLabel(category) }
                    } else {
                        itemsIndexed(
                            items = filteredFoods,
                            key = { _, item -> "${category}_${item.key}" },
                        ) { index, item ->
                            val isFirst = index == 0
                            val isLast = index == filteredFoods.lastIndex
                            val topCorner = if (isFirst) CornerCard else 0.dp
                            val bottomCorner = if (isLast) CornerCard else 0.dp
                            val shape = RoundedCornerShape(
                                topStart = topCorner, topEnd = topCorner,
                                bottomStart = bottomCorner, bottomEnd = bottomCorner,
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(appColors.cardSurface),
                            ) {
                                if (!isFirst) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(appColors.cardBorder),
                                    )
                                }
                                GlassFoodRow(
                                    item = item,
                                    onLog = { viewModel.requestLogFood(item.food) },
                                    onEdit = if (item.sourceLabel == null) {
                                        { viewModel.openEditFood(item.food) }
                                    } else null,
                                )
                            }
                        }
                    }
                }

                if (category == FoodCategory.ALL || category == FoodCategory.MEALS) {
                    // ── Recipe rows ──────────────────────────────────────────
                    val filteredRecipes = state.filteredRecipes
                    if (filteredRecipes.isNotEmpty()) {
                        itemsIndexed(
                            items = filteredRecipes,
                            key = { _, r -> "${category}_recipe_${r.recipe.id}" },
                        ) { index, recipe ->
                            val isFirst = index == 0
                            val isLast = index == filteredRecipes.lastIndex
                            val topCorner = if (isFirst) CornerCard else 0.dp
                            val bottomCorner = if (isLast) CornerCard else 0.dp
                            val shape = RoundedCornerShape(
                                topStart = topCorner, topEnd = topCorner,
                                bottomStart = bottomCorner, bottomEnd = bottomCorner,
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(appColors.cardSurface),
                            ) {
                                if (!isFirst) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(appColors.cardBorder))
                                }
                                GlassRecipeRow(
                                    recipe = recipe,
                                    pickerMode = pickerMode,
                                    onLog = { viewModel.requestLogRecipe(recipe) },
                                    onEdit = { onEditRecipe(recipe.recipe.id) },
                                )
                            }
                        }
                    }

                    // ── Legacy Saved Meals (faded, read-only) ────────────────
                    if (filteredMeals.isNotEmpty()) {
                        item(key = "${category}_legacy_header") {
                            Spacer(Modifier.height(12.dp))
                            SectionLabel(
                                "Legacy saved meals",
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        itemsIndexed(
                            items = filteredMeals,
                            key = { _, meal -> "${category}_meal_${meal.id}" },
                        ) { index, meal ->
                            val isFirst = index == 0
                            val isLast = index == filteredMeals.lastIndex
                            val topCorner = if (isFirst) CornerCard else 0.dp
                            val bottomCorner = if (isLast) CornerCard else 0.dp
                            val shape = RoundedCornerShape(
                                topStart = topCorner, topEnd = topCorner,
                                bottomStart = bottomCorner, bottomEnd = bottomCorner,
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(appColors.cardSurface)
                                    .alpha(0.5f),
                            ) {
                                if (!isFirst) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(appColors.cardBorder))
                                }
                                GlassMealRow(meal = meal, onLog = { viewModel.logMeal(meal) })
                            }
                        }
                    }
                }


                item(key = "${category}_bottom_spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ── Bottom Sheets ─────────────────────────────────────────────────────────
    if (state.showAmountSheet && state.pendingFood != null) {
        AmountSheet(state = state, viewModel = viewModel)
    }
    if (state.showRecipeAmountSheet && state.pendingRecipe != null) {
        RecipeAmountSheet(state = state, viewModel = viewModel)
    }
    if (state.showCreateFoodForm) {
        CreateFoodSheet(state = state, viewModel = viewModel)
    }
    if (state.showSaveMealDialog) {
        GlassAlertDialog(
            onDismiss = viewModel::dismissSaveMealDialog,
            title = "Save as recipe",
            confirmLabel = "Save",
            confirmEnabled = state.saveMealName.isNotBlank(),
            onConfirm = viewModel::confirmSaveMeal,
            dismissLabel = "Cancel",
        ) {
            GlassInputField(
                label = "Meal name",
                value = state.saveMealName,
                onValueChange = viewModel::onSaveMealNameChanged,
                keyboardType = KeyboardType.Text,
            )
        }
    }
    if (state.showQuickAddDialog) {
        QuickAddSheet(state = state, viewModel = viewModel)
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────

@Composable
private fun FoodLibraryTopBar(
    slotId: Long?,
    slotName: String,
    remainingCalories: Int,
    onBack: () -> Unit,
    pickerMode: Boolean = false,
) {
    SubScreenHeader(
        title = when {
            pickerMode -> "Add Ingredient"
            slotId != null -> "Add to $slotName"
            else -> "Foods & Meals"
        },
        onBack = onBack,
        modifier = Modifier.padding(horizontal = 16.dp),
        subtitle = if (slotId != null) "$remainingCalories kcal remaining to zone" else null,
    )
}

// ── Glass Search Field ────────────────────────────────────────────────────────

@Composable
private fun GlassSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    category: FoodCategory,
    onScanBarcode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) accent.accent.copy(alpha = 0.45f) else appColors.cardBorder
    val bgColor = if (focused) accent.accent.copy(alpha = 0.07f) else appColors.cardSurface
    val placeholder = when (category) {
        FoodCategory.NEVO -> "Search NEVO foods…"
        FoodCategory.OFF -> "Search Dutch products…"
        else -> "Search foods or scan…"
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        textStyle = AppType.body.copy(color = appColors.textPrimary.copy(alpha = 0xBF / 255f)),
        cursorBrush = SolidColor(accent.accentLighter),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        decorationBox = { innerField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = appColors.textFaint,
                    modifier = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f).padding(vertical = 7.dp)) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = AppType.body, color = appColors.textFaint)
                    }
                    innerField()
                }
                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(appColors.cardBorder),
                )
                // Scan button integrated into the search field
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.tintedSurface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onScanBarcode,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Scan barcode",
                        tint = accent.inkLighter,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}

// ── Glass Action Buttons ──────────────────────────────────────────────────────

@Composable
private fun GlassActionButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val contentColor = if (isPrimary) accent.accentLighter else appColors.textPrimary.copy(alpha = 0.85f)
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier,
        tint = if (isPrimary) accent.accent else Color.Unspecified,
        surfaceColor = if (isPrimary) Color.White.copy(alpha = 0.08f)
            else if (appColors.isDark) Color.White.copy(alpha = 0.14f) else appColors.glassPillSurface,
        buttonHeight = 32.dp,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = AppType.body.copy(fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium),
            color = contentColor,
        )
    }
}

// ── Glass Food Row ────────────────────────────────────────────────────────────

@Composable
private fun GlassFoodRow(
    item: FoodLibraryItem,
    onLog: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val food = item.food
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = food.name,
                    style = AppType.cardTitle,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.sourceLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.tintedSurface)
                            .border(1.dp, accent.tintedBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(item.sourceLabel, style = AppType.metaLabel, color = accent.inkBase.copy(alpha = 0.70f))
                    }
                }
            }
            Text(
                text = "${food.proteinG.toInt()}P ${food.carbsG.toInt()}C ${food.fatG.toInt()}F",
                style = AppType.metaLabel,
                color = appColors.textMuted,
            )
        }
        Text(
            text = "${food.calories} kcal",
            style = AppType.label.copy(fontWeight = FontWeight.Bold),
            color = appColors.textDim,
        )
        if (onEdit != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(appColors.cardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEdit,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = accent.inkLight,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        // Add button
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLog,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add",
                tint = accent.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Glass Meal Row ────────────────────────────────────────────────────────────

@Composable
private fun GlassMealRow(meal: SavedMealEntity, onLog: () -> Unit) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.accent.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = meal.name,
                    style = AppType.cardTitle,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.tintedSurface)
                        .border(1.dp, accent.tintedBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("Meal", style = AppType.metaLabel, color = accent.inkBase.copy(alpha = 0.70f))
                }
            }
            Text(
                text = "${meal.proteinG.toInt()}P ${meal.carbsG.toInt()}C ${meal.fatG.toInt()}F",
                style = AppType.metaLabel,
                color = appColors.textMuted,
            )
        }
        Text(
            text = "${meal.calories} kcal",
            style = AppType.label.copy(fontWeight = FontWeight.Bold),
            color = appColors.textDim,
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLog,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add",
                tint = accent.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Glass Recipe Row ──────────────────────────────────────────────────────────

@Composable
private fun GlassRecipeRow(
    recipe: RecipeWithIngredients,
    pickerMode: Boolean,
    onLog: () -> Unit,
    onEdit: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    recipe.recipe.name,
                    style = AppType.cardTitle,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.tintedSurface)
                        .border(1.dp, accent.tintedBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("Recipe", style = AppType.metaLabel, color = accent.inkBase.copy(alpha = 0.70f))
                }
            }
            Text(
                "${recipe.ingredients.size} ingredients · " +
                "${recipe.totalProteinG.toInt()}P ${recipe.totalCarbsG.toInt()}C ${recipe.totalFatG.toInt()}F",
                style = AppType.metaLabel,
                color = appColors.textMuted,
            )
        }
        Text(
            text = "${recipe.totalCalories} kcal",
            style = AppType.label.copy(fontWeight = FontWeight.Bold),
            color = appColors.textDim,
        )
        if (!pickerMode) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(appColors.cardSurface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = accent.inkLight,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.accent)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLog),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add",
                tint = accent.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyStateLabel(category: FoodCategory) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (category) {
                FoodCategory.NEVO -> "Search above to find NEVO foods.\nImport the CSV via More → Settings."
                FoodCategory.OFF -> "Use the search bar above to find\nproducts from Open Food Facts."
                else -> "No foods found."
            },
            style = AppType.cardSubtitle,
            color = appColors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun FoodCategory.label() = when (this) {
    FoodCategory.ALL -> "All"
    FoodCategory.PROTEINS -> "Proteins"
    FoodCategory.CARBS -> "Carbs"
    FoodCategory.MEALS -> "Recipes"
    FoodCategory.NEVO -> "NEVO"
    FoodCategory.OFF -> "Open Food Facts"
}

// ── Amount Sheet (unchanged functionality, glass style) ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSheet(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    val food = state.pendingFood ?: return
    val servingLabel = food.householdServingName ?: "serving"
    val servingGrams = food.householdServingGrams?.toInt() ?: 100
    val servingMode = state.amountMode == AmountMode.SERVINGS
    GlassBottomSheet(onDismiss = viewModel::dismissAmountSheet) {
        FoodAmountPanel(
            title = food.name,
            subtitle = "1 $servingLabel = $servingGrams g · ${food.calories} kcal / 100 g",
            showServingToggle = true,
            servingsSelected = servingMode,
            onModeSelected = { servings ->
                viewModel.onAmountModeChanged(if (servings) AmountMode.SERVINGS else AmountMode.GRAMS)
            },
            amountValue = if (servingMode) state.servingsValue else state.gramsValue,
            onAmountChange = if (servingMode) viewModel::onServingsChanged else viewModel::onGramsChanged,
            onMinus = { if (servingMode) viewModel.stepServings(-FoodScaling.SERVING_STEP) else viewModel.stepGrams(-10) },
            onPlus = { if (servingMode) viewModel.stepServings(FoodScaling.SERVING_STEP) else viewModel.stepGrams(10) },
            amountSuffix = if (servingMode) {
                state.resolvedGrams?.let { "servings · ${it.toInt()} g" } ?: "servings"
            } else {
                "g"
            },
            preview = state.previewMacros,
            message = state.message,
            messageKind = state.messageKind,
        ) {
            // Deterministic meal-impact strip (today-only; hidden when gated out).
            state.mealImpact?.let { MealImpactStrip(it) }
            LiquidPrimaryButton(
                text = when {
                    state.pickerMode -> "Add to Recipe"
                    state.editingEntryId == null -> if (state.slotId != null) "Add to ${state.slotName}" else "Add"
                    else -> "Save"
                },
                onClick = viewModel::confirmAmount,
            )
        }
    }
}

// ── Meal Impact Strip (deterministic, zero-AI: where this item lands you today) ─

@Composable
private fun MealImpactStrip(impact: MealImpact.Result) {
    val appColors = LocalAppColors.current
    // Only name macros that actually have a target — a missing target is not "0%".
    val parts = buildList {
        if (impact.protein.hasTarget) add("${impact.protein.percent}% protein")
        if (impact.carbs.hasTarget) add("${impact.carbs.percent}% carbs")
        if (isEmpty() && impact.calories.hasTarget) add("${impact.calories.percent}% calories")
    }
    FrostedCard(contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (parts.isNotEmpty()) {
                Text(
                    text = "Puts you at ${parts.joinToString(", ")} for today",
                    style = AppType.cardSubtitle,
                    color = appColors.textSecondary,
                )
            }
            Text(
                text = impact.hint,
                style = AppType.metaLabel,
                color = if (impact.calories.over || impact.carbs.over) appColors.textPrimary
                    else appColors.textMuted,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeAmountSheet(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    val recipe = state.pendingRecipe ?: return
    val appColors = LocalAppColors.current
    GlassBottomSheet(onDismiss = viewModel::dismissRecipeAmountSheet) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(recipe.recipe.name, style = AppType.screenTitleCompact, color = appColors.textPrimary)
            Text(
                "Whole recipe: ${recipe.totalCalories} kcal · ${recipe.ingredients.size} items",
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AmountStepper(
                value = state.recipePortions,
                onValueChange = viewModel::onRecipePortionsChanged,
                onMinus = { viewModel.stepRecipePortions(-FoodScaling.SERVING_STEP) },
                onPlus = { viewModel.stepRecipePortions(FoodScaling.SERVING_STEP) },
                caption = "",
                suffix = "portions",
            )
            val preview = state.recipePreviewMacros
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountPreviewStat("kcal", preview?.calories?.toString() ?: "—", Modifier.weight(1f))
                AmountPreviewStat("P", preview?.proteinG?.toInt()?.toString() ?: "—", Modifier.weight(1f))
                AmountPreviewStat("C", preview?.carbsG?.toInt()?.toString() ?: "—", Modifier.weight(1f))
                AmountPreviewStat("F", preview?.fatG?.toInt()?.toString() ?: "—", Modifier.weight(1f))
            }
            MessageText(state.message, state.messageKind)
            LiquidPrimaryButton(
                text = if (state.slotId != null) "Add to ${state.slotName}" else "Add",
                onClick = viewModel::confirmLogRecipe,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFoodSheet(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    GlassBottomSheet(onDismiss = viewModel::toggleCreateFoodForm, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (state.editingFoodId != null) "Edit: ${state.newFoodName}" else "New food",
                    style = AppType.screenTitleCompact,
                    color = appColors.textPrimary,
                )
                Text("Macros are per 100 g", style = AppType.cardSubtitle, color = appColors.textMuted)
            }
            GlassInputField(
                label = "Name",
                value = state.newFoodName,
                onValueChange = viewModel::onNewFoodNameChanged,
                keyboardType = KeyboardType.Text,
                modifier = Modifier.fillMaxWidth(),
            )
            GlassInputField(
                label = "Serving (e.g. 100g, 1 scoop)",
                value = state.newFoodServing,
                onValueChange = viewModel::onNewFoodServingChanged,
                keyboardType = KeyboardType.Text,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Calories", state.newFoodCalories, viewModel::onNewFoodCaloriesChanged, Modifier.weight(1f), "kcal")
                NumberField("Protein", state.newFoodProtein, viewModel::onNewFoodProteinChanged, Modifier.weight(1f), "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Carbs", state.newFoodCarbs, viewModel::onNewFoodCarbsChanged, Modifier.weight(1f), "g")
                NumberField("Fat", state.newFoodFat, viewModel::onNewFoodFatChanged, Modifier.weight(1f), "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlassInputField(
                    label = "Serving name (opt.)",
                    value = state.newFoodServingName,
                    onValueChange = viewModel::onNewFoodServingNameChanged,
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.weight(1f),
                )
                NumberField("Serving grams", state.newFoodServingGrams, viewModel::onNewFoodServingGramsChanged, Modifier.weight(1f), "g")
            }
            MessageText(state.message, state.messageKind)
            LiquidPrimaryButton(
                text = if (state.editingFoodId != null) "Update food" else "Save food",
                onClick = viewModel::saveNewFood,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    val appColors = LocalAppColors.current
    GlassBottomSheet(onDismiss = viewModel::dismissQuickAdd) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Quick add", style = AppType.screenTitleCompact, color = appColors.textPrimary)
                Text("Log calories without creating a food", style = AppType.cardSubtitle, color = appColors.textMuted)
            }
            GlassInputField(
                label = "Name (optional)",
                value = state.quickAddName,
                onValueChange = viewModel::onQuickAddNameChanged,
                keyboardType = KeyboardType.Text,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField("Calories", state.quickAddCalories, viewModel::onQuickAddCaloriesChanged, suffix = "kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Protein", state.quickAddProtein, viewModel::onQuickAddProteinChanged, Modifier.weight(1f), "g")
                NumberField("Carbs", state.quickAddCarbs, viewModel::onQuickAddCarbsChanged, Modifier.weight(1f), "g")
                NumberField("Fat", state.quickAddFat, viewModel::onQuickAddFatChanged, Modifier.weight(1f), "g")
            }
            MessageText(state.message, state.messageKind)
            LiquidPrimaryButton(text = "Add", onClick = viewModel::confirmQuickAdd)
        }
    }
}
