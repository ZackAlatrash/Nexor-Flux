package com.zack.recomptracker.ui.foodlibrary

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.zack.recomptracker.domain.food.RecipeWithIngredients
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastMessage
import com.zack.recomptracker.ui.toast.ToastType
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.component.MessageKind
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerChip
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.NavLogEnd
import com.zack.recomptracker.ui.theme.NavLogStart
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400
import com.zack.recomptracker.ui.theme.Violet500
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val TextSecondary = Color(0x47FFFFFF) // TextMuted

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
            onScanBarcode = onScanBarcode,
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
                        .background(if (isActive) Color(0x338B5CF6) else Color(0x0DFFFFFF))
                        .border(
                            1.dp,
                            if (isActive) Color(0x598B5CF6) else Color(0x14FFFFFF),
                            RoundedCornerShape(CornerChip),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { viewModel.onCategoryChanged(cat) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = cat.label(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) Violet300 else Color(0x59FFFFFF),
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
                    text = "+ New food",
                    onClick = viewModel::toggleCreateFoodForm,
                    isPrimary = true,
                    modifier = Modifier.weight(1f),
                )
                GlassActionButton(
                    text = "⚡ Quick add",
                    onClick = viewModel::openQuickAdd,
                    isPrimary = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Create Recipe button (pinned above list, hidden in picker mode) ───
        if (!pickerMode && (state.category == FoodCategory.ALL || state.category == FoodCategory.MEALS)) {
            GlassActionButton(
                text = "+ Create Recipe",
                onClick = onCreateRecipe,
                isPrimary = true,
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
                            CircularProgressIndicator(color = Violet400)
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
                                        .background(Color(0x0DFFFFFF))
                                        .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(CornerChip))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { viewModel.requestLogFood(food) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(food.name, fontSize = 12.sp, color = Color(0xBFFFFFFF))
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
                                    .background(CardSurface),
                            ) {
                                if (!isFirst) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0x0AFFFFFF)),
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
                                    .background(CardSurface),
                            ) {
                                if (!isFirst) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0AFFFFFF)))
                                }
                                GlassRecipeRow(
                                    recipe = recipe,
                                    pickerMode = pickerMode,
                                    onLog = { viewModel.logRecipe(recipe) },
                                    onEdit = { onEditRecipe(recipe.recipe.id) },
                                )
                            }
                        }
                    }

                    // ── Legacy Saved Meals (faded, read-only) ────────────────
                    if (filteredMeals.isNotEmpty()) {
                        item(key = "${category}_legacy_header") {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "LEGACY SAVED MEALS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 0.14.sp,
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
                                    .background(CardSurface)
                                    .alpha(0.5f),
                            ) {
                                if (!isFirst) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0AFFFFFF)))
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
    if (state.showCreateFoodForm) {
        CreateFoodSheet(state = state, viewModel = viewModel)
    }
    if (state.showSaveMealDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveMealDialog,
            title = { Text("Save as recipe") },
            text = {
                OutlinedTextField(
                    value = state.saveMealName,
                    onValueChange = viewModel::onSaveMealNameChanged,
                    label = { Text("Meal name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSaveMeal) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSaveMealDialog) { Text("Cancel") }
            },
        )
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
    onScanBarcode: () -> Unit,
    pickerMode: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Back button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x0FFFFFFF))
                .border(1.dp, Color(0x17FFFFFF), RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xBFFFFFFF),
                modifier = Modifier.size(18.dp),
            )
        }

        // Title column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    pickerMode -> "Add Ingredient"
                    slotId != null -> "Add to $slotName"
                    else -> "Foods & Meals"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.3).sp,
            )
            if (slotId != null) {
                Text(
                    text = "$remainingCalories kcal remaining to zone",
                    fontSize = 11.sp,
                    color = Color(0xBFA78BFA),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Camera scan button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x268B5CF6))
                .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(10.dp))
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
                tint = Violet300,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Glass Search Field ────────────────────────────────────────────────────────

@Composable
private fun GlassSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    category: FoodCategory,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) Color(0x738B5CF6) else Color(0x1AFFFFFF)
    val bgColor = if (focused) Color(0x128B5CF6) else Color(0x0FFFFFFF)
    val placeholder = when (category) {
        FoodCategory.NEVO -> "Search NEVO foods…"
        FoodCategory.OFF -> "Search Dutch products…"
        else -> "Search foods…"
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = Color(0xBFFFFFFF)),
        cursorBrush = SolidColor(Violet300),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        decorationBox = { innerField ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0x40FFFFFF),
                    modifier = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(placeholder, fontSize = 14.sp, color = Color(0x40FFFFFF))
                    }
                    innerField()
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
) {
    LiquidActionButton(
        text = text,
        onClick = onClick,
        isPrimary = isPrimary,
        modifier = modifier,
    )
}

// ── Glass Food Row ────────────────────────────────────────────────────────────

@Composable
private fun GlassFoodRow(
    item: FoodLibraryItem,
    onLog: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.sourceLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x1F8B5CF6))
                            .border(1.dp, Color(0x338B5CF6), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(item.sourceLabel, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xB38B5CF6))
                    }
                }
            }
            Text(
                text = "${food.proteinG.toInt()}P ${food.carbsG.toInt()}C ${food.fatG.toInt()}F",
                fontSize = 10.sp,
                color = TextSecondary,
            )
        }
        Text(
            text = "${food.calories} kcal",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0x66FFFFFF),
        )
        if (onEdit != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x0DFFFFFF))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEdit,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("✎", fontSize = 13.sp, color = Violet400)
            }
        }
        // Add button
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Violet500)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLog,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Light, color = Color.White)
        }
    }
}

// ── Glass Meal Row ────────────────────────────────────────────────────────────

@Composable
private fun GlassMealRow(meal: SavedMealEntity, onLog: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x0A8B5CF6))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = meal.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1F8B5CF6))
                        .border(1.dp, Color(0x338B5CF6), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("Meal", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xB38B5CF6))
                }
            }
            Text(
                text = "${meal.proteinG.toInt()}P ${meal.carbsG.toInt()}C ${meal.fatG.toInt()}F",
                fontSize = 10.sp,
                color = TextSecondary,
            )
        }
        Text(
            text = "${meal.calories} kcal",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0x66FFFFFF),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Violet500)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLog,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Light, color = Color.White)
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
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1F8B5CF6))
                        .border(1.dp, Color(0x338B5CF6), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("Recipe", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xB38B5CF6))
                }
            }
            Text(
                "${recipe.ingredients.size} ingredients · " +
                "${recipe.totalProteinG.toInt()}P ${recipe.totalCarbsG.toInt()}C ${recipe.totalFatG.toInt()}F",
                fontSize = 10.sp,
                color = TextSecondary,
            )
        }
        Text(
            text = "${recipe.totalCalories} kcal",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0x66FFFFFF),
        )
        if (!pickerMode) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x0DFFFFFF))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text("✎", fontSize = 13.sp, color = Violet400)
            }
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Violet500)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLog),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Light, color = Color.White)
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyStateLabel(category: FoodCategory) {
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
            fontSize = 12.sp,
            color = TextMuted,
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
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissAmountSheet,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(food.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val servingLabel = food.householdServingName ?: "serving"
            val servingGrams = food.householdServingGrams?.toInt() ?: 100
            Text(
                "1 $servingLabel = $servingGrams g · ${food.calories} kcal / 100 g",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.amountMode == AmountMode.SERVINGS,
                    onClick = { viewModel.onAmountModeChanged(AmountMode.SERVINGS) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Servings") }
                SegmentedButton(
                    selected = state.amountMode == AmountMode.GRAMS,
                    onClick = { viewModel.onAmountModeChanged(AmountMode.GRAMS) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Grams") }
            }
            if (state.amountMode == AmountMode.SERVINGS) {
                AmountStepper(
                    value = state.servingsValue,
                    onValueChange = viewModel::onServingsChanged,
                    onMinus = { viewModel.stepServings(-FoodScaling.SERVING_STEP) },
                    onPlus = { viewModel.stepServings(FoodScaling.SERVING_STEP) },
                    caption = state.resolvedGrams?.let { "${it.toInt()} g" } ?: "",
                    suffix = "servings",
                )
            } else {
                AmountStepper(
                    value = state.gramsValue,
                    onValueChange = viewModel::onGramsChanged,
                    onMinus = { viewModel.stepGrams(-10) },
                    onPlus = { viewModel.stepGrams(10) },
                    caption = "",
                    suffix = "g",
                )
            }
            val preview = state.previewMacros
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AmountPreviewStat("kcal", preview?.calories?.toString() ?: "—")
                AmountPreviewStat("P", preview?.proteinG?.toInt()?.toString() ?: "—")
                AmountPreviewStat("C", preview?.carbsG?.toInt()?.toString() ?: "—")
                AmountPreviewStat("F", preview?.fatG?.toInt()?.toString() ?: "—")
            }
            MessageText(state.message, state.messageKind)
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

@Composable
private fun AmountStepper(
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    caption: String,
    suffix: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(CornerSmall))
                .background(Color(0x0DFFFFFF))
                .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(CornerSmall))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMinus,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("−", fontSize = 20.sp, color = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(suffix) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (caption.isNotBlank()) {
                Text(caption, color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(CornerSmall))
                .background(Color(0x0DFFFFFF))
                .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(CornerSmall))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlus,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 20.sp, color = Color.White)
        }
    }
}

@Composable
private fun AmountPreviewStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFoodSheet(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = viewModel::toggleCreateFoodForm,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (state.editingFoodId != null) "Edit: ${state.newFoodName}" else "New food",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text("Macros are per 100 g", color = TextSecondary, fontSize = 11.sp)
            }
            OutlinedTextField(
                value = state.newFoodName,
                onValueChange = viewModel::onNewFoodNameChanged,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.newFoodServing,
                onValueChange = viewModel::onNewFoodServingChanged,
                label = { Text("Serving (e.g. 100g, 1 scoop)") },
                singleLine = true,
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
                OutlinedTextField(
                    value = state.newFoodServingName,
                    onValueChange = viewModel::onNewFoodServingNameChanged,
                    label = { Text("Serving name (opt.)") },
                    singleLine = true,
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
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissQuickAdd,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚡ Quick add", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Log calories without creating a food", color = TextSecondary, fontSize = 11.sp)
            }
            OutlinedTextField(
                value = state.quickAddName,
                onValueChange = viewModel::onQuickAddNameChanged,
                label = { Text("Name (optional)") },
                singleLine = true,
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
