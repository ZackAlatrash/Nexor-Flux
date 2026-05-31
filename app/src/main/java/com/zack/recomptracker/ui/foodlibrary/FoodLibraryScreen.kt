package com.zack.recomptracker.ui.foodlibrary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.domain.food.FoodScaling
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.SectionCard

private val Blue = Color(0xFF3b82f6)
private val Secondary = Color(0xFF6b7280)
private val GreenStar = Color(0xFF15803d)

@Composable
fun FoodLibraryScreen(
    viewModel: FoodLibraryViewModel,
    slotId: Long?,
    slotName: String,
    onBack: () -> Unit,
    editEntryId: Long? = null,
) {
    LaunchedEffect(Unit) { viewModel.init(slotId, slotName, editEntryId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = if (slotId != null) "Add to ${state.slotName}" else "Foods & Meals",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (slotId != null) {
                        Text(
                            "${state.remainingCalories} kcal to zone",
                            fontSize = 11.sp,
                            color = Secondary,
                        )
                    }
                }
            }
            MessageText(state.message)
        }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = {
                    Text(
                        if (state.category == FoodCategory.NEVO) "Search NEVO foods…"
                        else "Search saved foods…"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FoodCategory.entries) { cat ->
                    FilterChip(
                        selected = state.category == cat,
                        onClick = { viewModel.onCategoryChanged(cat) },
                        label = {
                            Text(
                                when (cat) {
                                    FoodCategory.ALL -> "All"
                                    FoodCategory.PROTEINS -> "Proteins"
                                    FoodCategory.CARBS -> "Carbs"
                                    FoodCategory.MEALS -> "Saved Meals"
                                    FoodCategory.NEVO -> "NEVO"
                                },
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Blue,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        if (state.category != FoodCategory.NEVO) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::toggleCreateFoodForm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue),
                        border = BorderStroke(1.5.dp, Blue),
                    ) {
                        Text("+ New food", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = viewModel::openQuickAdd,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                    ) {
                        Text("⚡ Quick add", fontSize = 12.sp)
                    }
                }
            }
        }

        if (state.recentFoods.isNotEmpty() && state.query.isBlank()) {
            item {
                Text(
                    "RECENTS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Secondary,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.recentFoods, key = { "recent_${it.name}" }) { food ->
                        OutlinedButton(onClick = { viewModel.requestLogFood(food) }) {
                            Text(food.name, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (state.category != FoodCategory.MEALS) {
            if (state.filteredFoods.isEmpty()) {
                item {
                    Text(
                        if (state.category == FoodCategory.NEVO)
                            "No NEVO foods imported yet. Go to Settings → Import NEVO CSV."
                        else
                            "No foods found.",
                        color = Secondary,
                    )
                }
            } else {
                items(state.filteredFoods, key = { it.key }) { item ->
                    FoodRow(
                        item = item,
                        onLog = { viewModel.requestLogFood(item.food) },
                        onEdit = if (item.sourceLabel == null) { { viewModel.openEditFood(item.food) } } else null,
                    )
                }
            }
        }

        if (state.category == FoodCategory.ALL || state.category == FoodCategory.MEALS) {
            items(state.filteredMeals, key = { "meal_${it.id}" }) { meal ->
                MealRow(meal = meal, onLog = { viewModel.logMeal(meal) })
            }
        }

        if (state.category != FoodCategory.NEVO) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (slotId != null) {
                        OutlinedButton(
                            onClick = viewModel::openSaveMealDialog,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenStar),
                        ) {
                            Text("Save current slot as meal")
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::toggleCreateFoodForm,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                    ) {
                        Text(if (state.showCreateFoodForm) "Cancel" else "+ Create new food")
                    }
                    if (state.showCreateFoodForm) {
                        CreateFoodForm(state = state, viewModel = viewModel)
                    }
                    OutlinedButton(
                        onClick = viewModel::openQuickAdd,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                    ) {
                        Text("Quick add calories")
                    }
                }
            }
        }
    }

    if (state.showAmountSheet && state.pendingFood != null) {
        AmountSheet(state = state, viewModel = viewModel)
    }

    if (state.showCreateFoodForm) {
        CreateFoodSheet(state = state, viewModel = viewModel)
    }

    if (state.showSaveMealDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveMealDialog,
            title = { Text("Save as meal") },
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

@Composable
private fun FoodRow(item: FoodLibraryItem, onLog: () -> Unit, onEdit: (() -> Unit)? = null) {
    val food = item.food
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (item.sourceLabel != null) {
                    Text(item.sourceLabel, fontSize = 10.sp, color = Blue, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${food.servingName} · ${food.calories} kcal · ${food.proteinG.toInt()}P ${food.carbsG.toInt()}C ${food.fatG.toInt()}F",
                    fontSize = 11.sp,
                    color = Secondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onEdit != null) {
                    TextButton(onClick = onEdit) { Text("Edit", fontSize = 11.sp, color = Secondary) }
                }
                Button(
                    onClick = onLog,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                ) { Text("Log", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun MealRow(meal: SavedMealEntity, onLog: () -> Unit) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(meal.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "${meal.calories} kcal · ${meal.proteinG.toInt()}P ${meal.carbsG.toInt()}C ${meal.fatG.toInt()}F",
                    fontSize = 11.sp,
                    color = Secondary,
                )
            }
            Button(
                onClick = onLog,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) { Text("Log all", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun CreateFoodForm(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (state.editingFoodId != null) "Edit food" else "New food",
                fontWeight = FontWeight.Bold,
            )
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
                    label = { Text("Serving name (e.g. scoop)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                NumberField("Serving grams", state.newFoodServingGrams, viewModel::onNewFoodServingGramsChanged, Modifier.weight(1f), "g")
            }
            Button(onClick = viewModel::saveNewFood, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.editingFoodId != null) "Update food" else "Save food")
            }
        }
    }
}

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
            val reference = "1 $servingLabel = $servingGrams g · ${food.calories} kcal / 100 g"
            Text(
                reference,
                color = Secondary,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AmountPreviewStat("kcal", preview?.calories?.toString() ?: "—")
                AmountPreviewStat("P", preview?.proteinG?.toInt()?.toString() ?: "—")
                AmountPreviewStat("C", preview?.carbsG?.toInt()?.toString() ?: "—")
                AmountPreviewStat("F", preview?.fatG?.toInt()?.toString() ?: "—")
            }

            MessageText(state.message)

            Button(
                onClick = viewModel::confirmAmount,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) {
                Text(
                    if (state.editingEntryId == null) {
                        if (state.slotId != null) "Add to ${state.slotName}" else "Add"
                    } else {
                        "Save"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
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
        OutlinedButton(
            onClick = onMinus,
            modifier = Modifier.size(48.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("−", fontSize = 20.sp)
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
                Text(
                    caption,
                    color = Secondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        OutlinedButton(
            onClick = onPlus,
            modifier = Modifier.size(48.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("+", fontSize = 20.sp)
        }
    }
}

@Composable
private fun AmountPreviewStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = Secondary, fontSize = 10.sp)
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
                Text("Log calories without creating a food", color = Secondary, fontSize = 11.sp)
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
            MessageText(state.message)
            Button(
                onClick = viewModel::confirmQuickAdd,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4b5563)),
            ) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        }
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
                Text("Macros are per 100 g", color = Secondary, fontSize = 11.sp)
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
            MessageText(state.message)
            Button(
                onClick = viewModel::saveNewFood,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) {
                Text(
                    if (state.editingFoodId != null) "Update food" else "Save food",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
