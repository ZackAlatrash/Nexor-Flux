package com.zack.recomptracker.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.RecipeNamer
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.repository.RecipeRepository
import com.zack.recomptracker.domain.food.FoodMacros
import com.zack.recomptracker.domain.food.FoodScaling
import com.zack.recomptracker.ui.component.AmountMode
import com.zack.recomptracker.ui.component.MessageKind
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Open amount-editor sheet for one ingredient, or null when closed. */
data class IngredientEditorState(
    val index: Int,
    val name: String,
    val scalable: Boolean,
    val hasServings: Boolean = false,
    val mode: AmountMode = AmountMode.GRAMS,
    val gramsInput: String = "",
    val servingsInput: String = "",
    val caloriesInput: String = "",
    val proteinInput: String = "",
    val carbsInput: String = "",
    val fatInput: String = "",
    val preview: FoodMacros? = null,
)

data class RecipeBuilderUiState(
    val recipeId: Long? = null,
    val name: String = "",
    val ingredients: List<RecipeIngredientEntity> = emptyList(),
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val message: String? = null,
    val messageKind: MessageKind = MessageKind.ERROR,
    /** Whether the ✨ AI-name button should be shown at all (model present / cloud configured). */
    val aiAvailable: Boolean = false,
    /** True while a name is being generated. */
    val isGeneratingName: Boolean = false,
    /** Active ingredient amount/macro editor, or null when no sheet is open. */
    val ingredientEditor: IngredientEditorState? = null,
)

class RecipeBuilderViewModel(
    private val recipeRepository: RecipeRepository,
    private val recipeNamer: RecipeNamer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeBuilderUiState())
    val uiState: StateFlow<RecipeBuilderUiState> = _uiState

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack

    init {
        val recipeId = savedStateHandle.get<Long>("recipeId")?.takeIf { it != -1L }
        if (recipeId != null) {
            _uiState.update { it.copy(recipeId = recipeId) }
            viewModelScope.launch {
                val existing = recipeRepository.getById(recipeId)
                if (existing != null) {
                    _uiState.update { it.copy(name = existing.recipe.name, ingredients = existing.ingredients) }
                }
            }
        } else {
            // New recipe: load seed ingredients passed from a meal slot, if any.
            decodeSeed(savedStateHandle.get<String>("seedIngredients"))?.let { seeded ->
                _uiState.update { it.copy(ingredients = seeded) }
            }
        }

        viewModelScope.launch {
            recipeNamer.available.collect { avail -> _uiState.update { it.copy(aiAvailable = avail) } }
        }
    }

    private fun decodeSeed(encoded: String?): List<RecipeIngredientEntity>? {
        if (encoded.isNullOrBlank()) return null
        return try {
            val json = String(java.util.Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
            Json.decodeFromString<List<RecipeIngredientEntity>>(json)
        } catch (_: Exception) {
            null
        }
    }

    fun onNameChanged(value: String) =
        _uiState.update { it.copy(name = value, isDirty = true, message = null) }

    fun addIngredient(ingredient: RecipeIngredientEntity) =
        _uiState.update { it.copy(ingredients = it.ingredients + ingredient, isDirty = true) }

    fun removeIngredientAt(index: Int) = _uiState.update { state ->
        state.copy(
            ingredients = state.ingredients.toMutableList().also { it.removeAt(index) },
            isDirty = true,
        )
    }

    fun editIngredientAt(index: Int, updated: RecipeIngredientEntity) = _uiState.update { state ->
        val list = state.ingredients.toMutableList()
        list[index] = updated
        state.copy(ingredients = list, isDirty = true)
    }

    // ── Ingredient amount / macro editor ─────────────────────────────────────

    fun startEditingIngredient(index: Int) {
        val ing = _uiState.value.ingredients.getOrNull(index) ?: return
        val base = ing.basePer100()
        val editor = if (ing.amountGrams != null && base != null) {
            val grams = ing.amountGrams
            // Servings is always available for a scalable ingredient — fall back to a
            // default per-serving size when the food has none, matching the add-food sheet.
            val perServing = ing.entryServingGrams ?: FoodScaling.DEFAULT_SERVING_GRAMS
            val mode = if (ing.loggedByServings) AmountMode.SERVINGS else AmountMode.GRAMS
            val servings = grams / perServing
            IngredientEditorState(
                index = index,
                name = ing.name,
                scalable = true,
                hasServings = true,
                mode = mode,
                gramsInput = formatAmount(grams),
                servingsInput = formatAmount(servings),
                preview = FoodScaling.scale(base, grams),
            )
        } else {
            IngredientEditorState(
                index = index,
                name = ing.name,
                scalable = false,
                caloriesInput = ing.calories.toString(),
                proteinInput = formatAmount(ing.proteinG),
                carbsInput = formatAmount(ing.carbsG),
                fatInput = formatAmount(ing.fatG),
            )
        }
        _uiState.update { it.copy(ingredientEditor = editor) }
    }

    fun onEditorAmountModeChanged(mode: AmountMode) = updateEditor { it.copy(mode = mode) }
    fun onEditorGramsChanged(text: String) = updateEditor { it.copy(gramsInput = text) }
    fun onEditorServingsChanged(text: String) = updateEditor { it.copy(servingsInput = text) }
    fun onEditorCaloriesChanged(text: String) = updateEditor { it.copy(caloriesInput = text) }
    fun onEditorProteinChanged(text: String) = updateEditor { it.copy(proteinInput = text) }
    fun onEditorCarbsChanged(text: String) = updateEditor { it.copy(carbsInput = text) }
    fun onEditorFatChanged(text: String) = updateEditor { it.copy(fatInput = text) }

    fun stepEditorGrams(delta: Int) = updateEditor {
        val cur = it.gramsInput.toDoubleOrNull() ?: 0.0
        it.copy(gramsInput = formatAmount((cur + delta).coerceAtLeast(FoodScaling.MIN_GRAMS)))
    }

    fun stepEditorServings(delta: Double) = updateEditor {
        val cur = it.servingsInput.toDoubleOrNull() ?: 0.0
        it.copy(servingsInput = formatAmount((cur + delta).coerceAtLeast(FoodScaling.MIN_SERVINGS)))
    }

    fun confirmIngredientEdit() {
        val editor = _uiState.value.ingredientEditor ?: return
        val ing = _uiState.value.ingredients.getOrNull(editor.index)
        if (ing == null) {
            _uiState.update { it.copy(ingredientEditor = null) }
            return
        }
        val updated = if (editor.scalable) {
            val base = ing.basePer100() ?: return
            val grams = (resolveGrams(editor, ing) ?: return).coerceAtLeast(FoodScaling.MIN_GRAMS)
            val scaled = FoodScaling.scale(base, grams)
            ing.copy(
                amountGrams = grams,
                calories = scaled.calories,
                proteinG = scaled.proteinG,
                carbsG = scaled.carbsG,
                fatG = scaled.fatG,
                loggedByServings = editor.mode == AmountMode.SERVINGS,
            )
        } else {
            ing.copy(
                calories = editor.caloriesInput.toDoubleOrNull()?.roundToInt() ?: 0,
                proteinG = editor.proteinInput.toDoubleOrNull() ?: 0.0,
                carbsG = editor.carbsInput.toDoubleOrNull() ?: 0.0,
                fatG = editor.fatInput.toDoubleOrNull() ?: 0.0,
            )
        }
        editIngredientAt(editor.index, updated)
        _uiState.update { it.copy(ingredientEditor = null) }
    }

    fun cancelIngredientEdit() = _uiState.update { it.copy(ingredientEditor = null) }

    private fun updateEditor(transform: (IngredientEditorState) -> IngredientEditorState) {
        _uiState.update { state ->
            val editor = state.ingredientEditor ?: return@update state
            state.copy(ingredientEditor = recomputePreview(transform(editor)))
        }
    }

    private fun recomputePreview(editor: IngredientEditorState): IngredientEditorState {
        if (!editor.scalable) return editor
        val ing = _uiState.value.ingredients.getOrNull(editor.index) ?: return editor
        val base = ing.basePer100() ?: return editor
        val grams = resolveGrams(editor, ing)?.coerceAtLeast(FoodScaling.MIN_GRAMS)
        return editor.copy(preview = grams?.let { FoodScaling.scale(base, it) })
    }

    private fun resolveGrams(editor: IngredientEditorState, ing: RecipeIngredientEntity): Double? =
        when (editor.mode) {
            AmountMode.GRAMS -> editor.gramsInput.toDoubleOrNull()
            AmountMode.SERVINGS -> {
                val servings = editor.servingsInput.toDoubleOrNull() ?: return null
                val servingGrams = ing.entryServingGrams ?: FoodScaling.DEFAULT_SERVING_GRAMS
                servings * servingGrams
            }
        }

    private fun RecipeIngredientEntity.basePer100(): FoodMacros? {
        val cal = basePer100Calories ?: return null
        return FoodMacros(
            calories = cal,
            proteinG = basePer100ProteinG ?: 0.0,
            carbsG = basePer100CarbsG ?: 0.0,
            fatG = basePer100FatG ?: 0.0,
        )
    }

    private fun formatAmount(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    // ── AI name generation ────────────────────────────────────────────────────

    /** Generate (or reroll) a gym-bro name from the current ingredients + macros. */
    fun generateName() {
        val state = _uiState.value
        if (state.ingredients.isEmpty() || state.isGeneratingName) return
        _uiState.update { it.copy(isGeneratingName = true, message = null) }
        viewModelScope.launch {
            val totals = MacroTotals(
                calories = state.ingredients.sumOf { it.calories },
                proteinG = state.ingredients.sumOf { it.proteinG },
                carbsG = state.ingredients.sumOf { it.carbsG },
                fatG = state.ingredients.sumOf { it.fatG },
            )
            val result = recipeNamer.generate(state.ingredients, totals)
            result.fold(
                onSuccess = { name ->
                    _uiState.update { it.copy(name = name, isGeneratingName = false, isDirty = true) }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isGeneratingName = false,
                            message = "Couldn't think of a name — try again.",
                            messageKind = MessageKind.ERROR,
                        )
                    }
                },
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(message = "Enter a recipe name.", messageKind = MessageKind.ERROR) }
            return
        }
        if (state.ingredients.isEmpty()) {
            _uiState.update { it.copy(message = "Add at least one ingredient.", messageKind = MessageKind.ERROR) }
            return
        }
        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            val recipeId = state.recipeId
            if (recipeId == null) {
                recipeRepository.saveRecipe(state.name, state.ingredients)
            } else {
                recipeRepository.updateRecipe(recipeId, state.name, state.ingredients)
            }
            _navigateBack.value = true
        }
    }

    fun delete() {
        val recipeId = _uiState.value.recipeId ?: return
        viewModelScope.launch {
            recipeRepository.deleteRecipe(recipeId)
            _navigateBack.value = true
        }
    }

    fun onNavigateBackConsumed() {
        _navigateBack.value = false
    }
}
