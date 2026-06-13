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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.LocalAppAccent

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x0FFFFFFF))
                    .border(1.dp, Color(0x17FFFFFF), RoundedCornerShape(10.dp))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xBFFFFFFF),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                if (state.recipeId == null) "New Recipe" else "Edit Recipe",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (state.recipeId != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0FFFFFFF))
                        .border(1.dp, Color(0x17FFFFFF), RoundedCornerShape(10.dp))
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
        }

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
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Generate name with AI",
                                        tint = if (enabled) Color.White else accent.accentLight.copy(alpha = 0.5f),
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
                        Text("No ingredients yet. Tap below to add some.", color = TextMuted, fontSize = 13.sp)
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
                            .background(Color(0x0FFFFFFF)),
                    ) {
                        if (!isFirst) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0AFFFFFF)))
                        }
                        IngredientRow(
                            ingredient = ingredient,
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
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, CardBorder, RoundedCornerShape(CornerSmall))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onNavigateToFoodPicker)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+ Add ingredient", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent.accentLighter)
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
                    CircularProgressIndicator(color = accent.accentLight)
                }
            } else {
                LiquidPrimaryButton(
                    text = if (state.recipeId == null) "Save Recipe" else "Update Recipe",
                    onClick = viewModel::save,
                )
            }
        }
    }
}

@Composable
private fun IngredientRow(
    ingredient: RecipeIngredientEntity,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                ingredient.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val amountText = ingredient.amountGrams?.let { "${it.toInt()}g" } ?: ""
            Text(
                "$amountText · ${ingredient.calories} kcal  P${ingredient.proteinG.toInt()}g  C${ingredient.carbsG.toInt()}g  F${ingredient.fatG.toInt()}g",
                fontSize = 11.sp,
                color = TextMuted,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0x0DFFFFFF))
                .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(7.dp))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 13.sp, color = Color(0xBFFF4444))
        }
    }
}
