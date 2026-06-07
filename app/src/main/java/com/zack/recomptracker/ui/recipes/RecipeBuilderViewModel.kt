package com.zack.recomptracker.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.repository.RecipeRepository
import com.zack.recomptracker.ui.component.MessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeBuilderUiState(
    val recipeId: Long? = null,
    val name: String = "",
    val ingredients: List<RecipeIngredientEntity> = emptyList(),
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val message: String? = null,
    val messageKind: MessageKind = MessageKind.ERROR,
)

class RecipeBuilderViewModel(
    private val recipeRepository: RecipeRepository,
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
