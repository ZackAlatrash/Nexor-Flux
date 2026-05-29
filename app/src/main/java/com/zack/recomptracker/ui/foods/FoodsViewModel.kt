package com.zack.recomptracker.ui.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.model.MealType
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.core.util.toNullableDouble
import com.zack.recomptracker.core.util.toNullableInt
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodsUiState(
    val savedFoods: List<SavedFoodEntity> = emptyList(),
    val savedMeals: List<SavedMealEntity> = emptyList(),
    val foodName: String = "",
    val servingName: String = "",
    val foodCalories: String = "",
    val foodProtein: String = "",
    val foodCarbs: String = "",
    val foodFat: String = "",
    val mealName: String = "",
    val mealType: MealType = MealType.LUNCH,
    val mealCalories: String = "",
    val mealProtein: String = "",
    val mealCarbs: String = "",
    val mealFat: String = "",
    val quickAddMealType: MealType = MealType.LUNCH,
    val message: String? = null,
)

class FoodsViewModel(
    private val logRepository: LogRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FoodsUiState())
    val uiState: StateFlow<FoodsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                logRepository.observeSavedFoods(),
                logRepository.observeSavedMeals(),
            ) { foods, meals -> foods to meals }
                .collect { (foods, meals) ->
                    _uiState.update { it.copy(savedFoods = foods, savedMeals = meals) }
                }
        }
    }

    fun updateFoodName(value: String) = _uiState.update { it.copy(foodName = value, message = null) }
    fun updateServingName(value: String) = _uiState.update { it.copy(servingName = value, message = null) }
    fun updateFoodCalories(value: String) = _uiState.update { it.copy(foodCalories = value, message = null) }
    fun updateFoodProtein(value: String) = _uiState.update { it.copy(foodProtein = value, message = null) }
    fun updateFoodCarbs(value: String) = _uiState.update { it.copy(foodCarbs = value, message = null) }
    fun updateFoodFat(value: String) = _uiState.update { it.copy(foodFat = value, message = null) }
    fun updateMealName(value: String) = _uiState.update { it.copy(mealName = value, message = null) }
    fun updateMealType(value: MealType) = _uiState.update { it.copy(mealType = value, message = null) }
    fun updateMealCalories(value: String) = _uiState.update { it.copy(mealCalories = value, message = null) }
    fun updateMealProtein(value: String) = _uiState.update { it.copy(mealProtein = value, message = null) }
    fun updateMealCarbs(value: String) = _uiState.update { it.copy(mealCarbs = value, message = null) }
    fun updateMealFat(value: String) = _uiState.update { it.copy(mealFat = value, message = null) }
    fun updateQuickAddMealType(value: MealType) = _uiState.update { it.copy(quickAddMealType = value, message = null) }

    fun saveFood() {
        val state = _uiState.value
        val entity = SavedFoodEntity(
            name = state.foodName.trim(),
            servingName = state.servingName.trim().ifBlank { "1 serving" },
            calories = state.foodCalories.toNullableInt() ?: return invalid(),
            proteinG = state.foodProtein.toNullableDouble() ?: return invalid(),
            carbsG = state.foodCarbs.toNullableDouble() ?: return invalid(),
            fatG = state.foodFat.toNullableDouble() ?: return invalid(),
        )
        if (entity.name.isBlank()) return invalid("Food name is required.")
        viewModelScope.launch {
            logRepository.saveFood(entity)
            _uiState.update {
                it.copy(
                    foodName = "",
                    servingName = "",
                    foodCalories = "",
                    foodProtein = "",
                    foodCarbs = "",
                    foodFat = "",
                    message = "Saved food added.",
                )
            }
        }
    }

    fun saveMeal() {
        val state = _uiState.value
        val entity = SavedMealEntity(
            name = state.mealName.trim(),
            mealType = state.mealType.name,
            calories = state.mealCalories.toNullableInt() ?: return invalid(),
            proteinG = state.mealProtein.toNullableDouble() ?: return invalid(),
            carbsG = state.mealCarbs.toNullableDouble() ?: return invalid(),
            fatG = state.mealFat.toNullableDouble() ?: return invalid(),
        )
        if (entity.name.isBlank()) return invalid("Meal name is required.")
        viewModelScope.launch {
            logRepository.saveMeal(entity)
            _uiState.update {
                it.copy(
                    mealName = "",
                    mealCalories = "",
                    mealProtein = "",
                    mealCarbs = "",
                    mealFat = "",
                    message = "Saved meal added.",
                )
            }
        }
    }

    fun deleteFood(id: Long) {
        viewModelScope.launch {
            logRepository.deleteFood(id)
            _uiState.update { it.copy(message = "Saved food deleted.") }
        }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch {
            logRepository.deleteSavedMeal(id)
            _uiState.update { it.copy(message = "Saved meal deleted.") }
        }
    }

    fun addFoodToToday(food: SavedFoodEntity) {
        val mealType = _uiState.value.quickAddMealType
        viewModelScope.launch {
            logRepository.addSavedFoodToDate(food, dateProvider.today(), mealType.name)
            _uiState.update { it.copy(message = "${food.name} added to today.") }
        }
    }

    fun addMealToToday(meal: SavedMealEntity) {
        viewModelScope.launch {
            logRepository.addSavedMealToDate(meal, dateProvider.today())
            _uiState.update { it.copy(message = "${meal.name} added to today.") }
        }
    }

    private fun invalid(message: String = "Enter valid calories and macro numbers.") {
        _uiState.update { it.copy(message = message) }
    }
}
