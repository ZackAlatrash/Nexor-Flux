package com.zack.recomptracker.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.domain.food.FoodScaling
import com.zack.recomptracker.ui.component.AmountMode
import com.zack.recomptracker.ui.component.AmountPreviewStat
import com.zack.recomptracker.ui.component.AmountStepper
import com.zack.recomptracker.ui.component.GlassBottomSheet
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.GlassSegmentedToggle
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun RecipeBuilderScreen(
    viewModel: RecipeBuilderViewModel,
    pickedIngredientJson: String? = null,
    onPickedIngredientConsumed: () -> Unit = {},
    onNavigateToFoodPicker: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateBack by viewModel.navigateBack.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    // New ingredient picked from FoodLibraryScreen picker mode
    LaunchedEffect(pickedIngredientJson) {
        val json = pickedIngredientJson ?: return@LaunchedEffect
        try {
            val ingredient = kotlinx.serialization.json.Json.decodeFromString<RecipeIngredientEntity>(json)
            viewModel.addIngredient(ingredient)
        } catch (_: Exception) {
            // Ignore malformed JSON
        } finally {
            onPickedIngredientConsumed()
        }
    }

    // Navigate back after save/delete
    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            viewModel.onNavigateBackConsumed()
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top Bar ───────────────────────────────────────────────
        SubScreenHeader(
            title = if (state.recipeId == null) "New recipe" else "Edit recipe",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                if (state.recipeId != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(appColors.cardSurface)
                            .border(1.dp, appColors.cardBorder, RoundedCornerShape(10.dp))
                            .clickable(remember { MutableInteractionSource() }, null, onClick = viewModel::delete),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete recipe",
                            tint = Color(0xBFFF4444),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChanged,
                    label = { Text("Recipe name") },
                    singleLine = true,
                    enabled = !state.isGeneratingName,
                    trailingIcon = {
                        if (state.aiAvailable) {
                            val enabled = state.ingredients.isNotEmpty() && !state.isGeneratingName
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (enabled)
                                            Brush.linearGradient(listOf(accent.accent, accent.accentLight))
                                        else SolidColor(accent.accent.copy(alpha = 0.16f))
                                    )
                                    .then(
                                        if (enabled) Modifier.clickable(
                                            remember { MutableInteractionSource() }, null,
                                            onClick = viewModel::generateName,
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isGeneratingName) {
                                    CircularProgressIndicator(
                                        color = accent.onAccent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Generate name with AI",
                                        tint = if (enabled) accent.onAccent else accent.accentLight.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }

            if (state.ingredients.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No ingredients yet. Tap below to add some.", color = appColors.textMuted, style = AppType.body)
                    }
                }
            } else {
                itemsIndexed(
                    items = state.ingredients,
                    key = { index, _ -> "ingredient_$index" },
                ) { index, ingredient ->
                    val isFirst = index == 0
                    val isLast = index == state.ingredients.lastIndex
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
                        IngredientRow(
                            ingredient = ingredient,
                            onClick = { viewModel.startEditingIngredient(index) },
                            onRemove = { viewModel.removeIngredientAt(index) },
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerSmall))
                        .background(appColors.cardSurface)
                        .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerSmall))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onNavigateToFoodPicker)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+ Add ingredient", style = AppType.body.copy(fontWeight = FontWeight.SemiBold), color = accent.inkLighter)
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Bottom Action Area ────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            MessageText(state.message, state.messageKind)
            Spacer(Modifier.height(8.dp))
            if (state.isSaving) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent.inkLight)
                }
            } else {
                LiquidPrimaryButton(
                    text = if (state.recipeId == null) "Save Recipe" else "Update Recipe",
                    onClick = viewModel::save,
                )
            }
        }
    }

    state.ingredientEditor?.let { editor ->
        IngredientAmountSheet(editor = editor, viewModel = viewModel)
    }
}

@Composable
private fun IngredientRow(
    ingredient: RecipeIngredientEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                ingredient.name,
                style = AppType.cardTitle,
                color = appColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val amountText = ingredient.amountGrams?.let { "${it.toInt()}g" } ?: ""
            Text(
                "$amountText · ${ingredient.calories} kcal  P${ingredient.proteinG.toInt()}g  C${ingredient.carbsG.toInt()}g  F${ingredient.fatG.toInt()}g",
                style = AppType.label,
                color = appColors.textMuted,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(appColors.cardSurface)
                .border(1.dp, appColors.cardBorder, RoundedCornerShape(7.dp))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xBFFF4444),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientAmountSheet(
    editor: IngredientEditorState,
    viewModel: RecipeBuilderViewModel,
) {
    val appColors = LocalAppColors.current
    GlassBottomSheet(onDismiss = viewModel::cancelIngredientEdit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(editor.name, style = AppType.cardTitle.copy(fontWeight = FontWeight.Bold), color = appColors.textPrimary)

            if (editor.scalable) {
                if (editor.hasServings) {
                    GlassSegmentedToggle(
                        options = listOf("Servings", "Grams"),
                        selectedIndex = if (editor.mode == AmountMode.SERVINGS) 0 else 1,
                        onSelect = { viewModel.onEditorAmountModeChanged(if (it == 0) AmountMode.SERVINGS else AmountMode.GRAMS) },
                    )
                }
                if (editor.mode == AmountMode.SERVINGS) {
                    AmountStepper(
                        value = editor.servingsInput,
                        onValueChange = viewModel::onEditorServingsChanged,
                        onMinus = { viewModel.stepEditorServings(-FoodScaling.SERVING_STEP) },
                        onPlus = { viewModel.stepEditorServings(FoodScaling.SERVING_STEP) },
                        caption = "",
                        suffix = "servings",
                    )
                } else {
                    AmountStepper(
                        value = editor.gramsInput,
                        onValueChange = viewModel::onEditorGramsChanged,
                        onMinus = { viewModel.stepEditorGrams(-10) },
                        onPlus = { viewModel.stepEditorGrams(10) },
                        caption = "",
                        suffix = "g",
                    )
                }
                val preview = editor.preview
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountPreviewStat("kcal", preview?.calories?.toString() ?: "—", Modifier.weight(1f))
                    AmountPreviewStat("P", preview?.proteinG?.toInt()?.toString() ?: "—", Modifier.weight(1f))
                    AmountPreviewStat("C", preview?.carbsG?.toInt()?.toString() ?: "—", Modifier.weight(1f))
                    AmountPreviewStat("F", preview?.fatG?.toInt()?.toString() ?: "—", Modifier.weight(1f))
                }
            } else {
                MacroEditField("Calories (kcal)", editor.caloriesInput, viewModel::onEditorCaloriesChanged)
                MacroEditField("Protein (g)", editor.proteinInput, viewModel::onEditorProteinChanged)
                MacroEditField("Carbs (g)", editor.carbsInput, viewModel::onEditorCarbsChanged)
                MacroEditField("Fat (g)", editor.fatInput, viewModel::onEditorFatChanged)
            }

            LiquidPrimaryButton(text = "Save", onClick = viewModel::confirmIngredientEdit)
        }
    }
}

@Composable
private fun MacroEditField(label: String, value: String, onValueChange: (String) -> Unit) {
    GlassInputField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
    )
}
