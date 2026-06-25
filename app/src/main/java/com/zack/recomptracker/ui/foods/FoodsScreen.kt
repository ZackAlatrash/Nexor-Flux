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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.core.model.MealType
import com.zack.recomptracker.core.util.formatOneDecimal
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.MealTypeDropdown
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.SectionCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun FoodsScreen(viewModel: FoodsViewModel, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SubScreenHeader(
                    title = "Foods & Meals",
                    subtitle = "Saved shortcuts for faster logging",
                    onBack = onBack,
                )
                MessageText(state.message)
            }
        }
        item {
            FrostedCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Add saved food")
                    GlassInputField(
                        label = "Food name",
                        value = state.foodName,
                        onValueChange = viewModel::updateFoodName,
                        keyboardType = KeyboardType.Text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassInputField(
                        label = "Serving",
                        value = state.servingName,
                        onValueChange = viewModel::updateServingName,
                        keyboardType = KeyboardType.Text,
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
        }
        item {
            FrostedCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Add saved meal")
                    GlassInputField(
                        label = "Meal name",
                        value = state.mealName,
                        onValueChange = viewModel::updateMealName,
                        keyboardType = KeyboardType.Text,
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
        }
        item {
            FrostedCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Saved foods")
                    MealTypeDropdown(state.quickAddMealType, viewModel::updateQuickAddMealType, label = "Quick-add meal type")
                    if (state.savedFoods.isEmpty()) {
                        Text("No saved foods yet.", style = AppType.cardSubtitle, color = appColors.textMuted)
                    } else {
                        state.savedFoods.forEachIndexed { index, food ->
                            SavedFoodRow(food, onAdd = viewModel::addFoodToToday, onDelete = viewModel::deleteFood)
                            if (index != state.savedFoods.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        item {
            FrostedCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Saved meals")
                    if (state.savedMeals.isEmpty()) {
                        Text("No saved meals yet.", style = AppType.cardSubtitle, color = appColors.textMuted)
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
    val appColors = LocalAppColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(food.name, style = AppType.cardTitle, color = appColors.textPrimary)
            Text("${food.servingName} - ${food.calories} kcal", style = AppType.cardSubtitle, color = appColors.textMuted)
            Text(
                "${food.proteinG.formatOneDecimal()}P ${food.carbsG.formatOneDecimal()}C ${food.fatG.formatOneDecimal()}F",
                style = AppType.metaLabel,
                color = appColors.textMuted,
            )
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
    val appColors = LocalAppColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(meal.name, style = AppType.cardTitle, color = appColors.textPrimary)
            Text("${MealType.fromStored(meal.mealType).label} - ${meal.calories} kcal", style = AppType.cardSubtitle, color = appColors.textMuted)
            Text(
                "${meal.proteinG.formatOneDecimal()}P ${meal.carbsG.formatOneDecimal()}C ${meal.fatG.formatOneDecimal()}F",
                style = AppType.metaLabel,
                color = appColors.textMuted,
            )
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
