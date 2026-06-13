package com.zack.recomptracker.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.RecipeNamer
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.repository.RecipeRepository
import com.zack.recomptracker.ui.component.MessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
