package com.zack.recomptracker.ui.foods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zack.recomptracker.ui.component.ConfirmDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.core.model.MealType
import com.zack.recomptracker.core.util.formatOneDecimal
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.ui.component.MealTypeDropdown
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.SectionCard

@Composable
fun FoodsScreen(viewModel: FoodsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Foods/Meals", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Saved shortcuts for faster logging")
                MessageText(state.message)
            }
        }
        item {
            SectionCard("Add saved food") {
                OutlinedTextField(
                    value = state.foodName,
                    onValueChange = viewModel::updateFoodName,
                    label = { Text("Food name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.servingName,
                    onValueChange = viewModel::updateServingName,
                    label = { Text("Serving") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MacroFields(
                    calories = state.foodCalories,
                    protein = state.foodProtein,
                    carbs = state.foodCarbs,
                    fat = state.foodFat,
                    onCalories = viewModel::updateFoodCalories,
                    onProtein = viewModel::updateFoodProtein,
                    onCarbs = viewModel::updateFoodCarbs,
                    onFat = viewModel::updateFoodFat,
                )
                LiquidPrimaryButton(text = "Save food", onClick = viewModel::saveFood)
            }
        }
        item {
            SectionCard("Add saved meal") {
                OutlinedTextField(
                    value = state.mealName,
                    onValueChange = viewModel::updateMealName,
                    label = { Text("Meal name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MealTypeDropdown(state.mealType, viewModel::updateMealType)
                MacroFields(
                    calories = state.mealCalories,
                    protein = state.mealProtein,
                    carbs = state.mealCarbs,
                    fat = state.mealFat,
                    onCalories = viewModel::updateMealCalories,
                    onProtein = viewModel::updateMealProtein,
                    onCarbs = viewModel::updateMealCarbs,
                    onFat = viewModel::updateMealFat,
                )
                LiquidPrimaryButton(text = "Save meal", onClick = viewModel::saveMeal)
            }
        }
        item {
            SectionCard("Saved foods") {
                MealTypeDropdown(state.quickAddMealType, viewModel::updateQuickAddMealType, label = "Quick-add meal type")
                if (state.savedFoods.isEmpty()) {
                    Text("No saved foods yet.")
                } else {
                    state.savedFoods.forEachIndexed { index, food ->
                        SavedFoodRow(food, onAdd = viewModel::addFoodToToday, onDelete = viewModel::deleteFood)
                        if (index != state.savedFoods.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
        item {
            SectionCard("Saved meals") {
                if (state.savedMeals.isEmpty()) {
                    Text("No saved meals yet.")
                } else {
                    state.savedMeals.forEachIndexed { index, meal ->
                        SavedMealRow(meal, onAdd = viewModel::addMealToToday, onDelete = viewModel::deleteMeal)
                        if (index != state.savedMeals.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroFields(
    calories: String,
    protein: String,
    carbs: String,
    fat: String,
    onCalories: (String) -> Unit,
    onProtein: (String) -> Unit,
    onCarbs: (String) -> Unit,
    onFat: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NumberField("Calories", calories, onCalories, Modifier.weight(1f), "kcal")
        NumberField("Protein", protein, onProtein, Modifier.weight(1f), "g")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NumberField("Carbs", carbs, onCarbs, Modifier.weight(1f), "g")
        NumberField("Fat", fat, onFat, Modifier.weight(1f), "g")
    }
}

@Composable
private fun SavedFoodRow(
    food: SavedFoodEntity,
    onAdd: (SavedFoodEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(food.name, fontWeight = FontWeight.SemiBold)
            Text("${food.servingName} - ${food.calories} kcal")
            Text("${food.proteinG.formatOneDecimal()}P ${food.carbsG.formatOneDecimal()}C ${food.fatG.formatOneDecimal()}F")
        }
        LiquidActionButton(text = "Add", onClick = { onAdd(food) }, isPrimary = true)
        LiquidActionButton(text = "Delete", onClick = { showDeleteConfirm = true }, isPrimary = false)
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete food?",
            body = "\"${food.name}\" will be permanently removed from your library.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                onDelete(food.id)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun SavedMealRow(
    meal: SavedMealEntity,
    onAdd: (SavedMealEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(meal.name, fontWeight = FontWeight.SemiBold)
            Text("${MealType.fromStored(meal.mealType).label} - ${meal.calories} kcal")
            Text("${meal.proteinG.formatOneDecimal()}P ${meal.carbsG.formatOneDecimal()}C ${meal.fatG.formatOneDecimal()}F")
        }
        LiquidActionButton(text = "Add", onClick = { onAdd(meal) }, isPrimary = true)
        LiquidActionButton(text = "Delete", onClick = { showDeleteConfirm = true }, isPrimary = false)
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete meal?",
            body = "\"${meal.name}\" will be permanently removed from your saved meals.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                onDelete(meal.id)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
