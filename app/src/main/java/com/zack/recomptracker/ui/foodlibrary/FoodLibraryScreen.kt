package com.zack.recomptracker.ui.foodlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
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
) {
    LaunchedEffect(Unit) { viewModel.init(slotId, slotName) }
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
                    FoodRow(item = item, onLog = { viewModel.requestLogFood(item.food) })
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
                }
            }
        }
    }

    // TODO Task 7: replace with AmountPickerSheet (showAmountSheet / confirmAmount / dismissAmountSheet)
    if (state.showAmountSheet && state.pendingFood != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAmountSheet,
            title = { Text("How many grams?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.pendingFood?.name.orEmpty(), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.gramsValue,
                        onValueChange = viewModel::onGramsChanged,
                        label = { Text("Grams") },
                        singleLine = true,
                        suffix = { Text("g") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAmount) { Text("Log") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAmountSheet) { Text("Cancel") }
            },
        )
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
}

@Composable
private fun FoodRow(item: FoodLibraryItem, onLog: () -> Unit) {
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
            Button(
                onClick = onLog,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) { Text("Log", fontSize = 11.sp) }
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
            Text("New food", fontWeight = FontWeight.Bold)
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
            Button(onClick = viewModel::saveNewFood, modifier = Modifier.fillMaxWidth()) {
                Text("Save food")
            }
        }
    }
}
