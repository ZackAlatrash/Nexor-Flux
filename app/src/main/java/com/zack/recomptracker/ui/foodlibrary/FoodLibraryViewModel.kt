package com.zack.recomptracker.ui.foodlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FoodCategory { ALL, PROTEINS, CARBS, MEALS }

data class FoodLibraryUiState(
    val slotId: Long? = null,
    val slotName: String = "Food Log",
    val remainingCalories: Int = 0,
    val query: String = "",
    val category: FoodCategory = FoodCategory.ALL,
    val allFoods: List<SavedFoodEntity> = emptyList(),
    val allMeals: List<SavedMealEntity> = emptyList(),
    val message: String? = null,
    val showQuantityDialog: Boolean = false,
    val pendingFood: SavedFoodEntity? = null,
    val quantityGrams: String = "100",
    val newFoodName: String = "",
    val newFoodServing: String = "100g",
    val newFoodCalories: String = "",
    val newFoodProtein: String = "",
    val newFoodCarbs: String = "",
    val newFoodFat: String = "",
    val showCreateFoodForm: Boolean = false,
    val showSaveMealDialog: Boolean = false,
    val saveMealName: String = "",
) {
    val filteredFoods: List<SavedFoodEntity>
        get() {
            val q = query.trim().lowercase()
            return allFoods.filter { food ->
                (q.isEmpty() || food.name.lowercase().contains(q)) &&
                when (category) {
                    FoodCategory.PROTEINS -> food.proteinG >= food.carbsG && food.proteinG >= food.fatG
                    FoodCategory.CARBS -> food.carbsG >= food.proteinG && food.carbsG > food.fatG
                    else -> true
                }
            }
        }

    val filteredMeals: List<SavedMealEntity>
        get() {
            val q = query.trim().lowercase()
            return allMeals.filter { q.isEmpty() || it.name.lowercase().contains(q) }
        }
}

class FoodLibraryViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodLibraryUiState())
    val uiState: StateFlow<FoodLibraryUiState> = _uiState
    private var initialized = false

    fun init(slotId: Long?, slotName: String) {
        _uiState.update { it.copy(slotId = slotId, slotName = slotName.ifBlank { "Food Log" }) }
        if (initialized) return
        initialized = true
        val today = dateProvider.today()
        viewModelScope.launch {
            combine(
                logRepository.observeSavedFoods(),
                logRepository.observeSavedMeals(),
                planRepository.preferences,
                logRepository.observeDay(today),
            ) { foods, meals, prefs, day ->
                object {
                    val foods = foods
                    val meals = meals
                    val prefs = prefs
                    val eatenCalories = day.totals.calories
                }
            }.collect { data ->
                _uiState.update { current ->
                    current.copy(
                        allFoods = data.foods,
                        allMeals = data.meals,
                        remainingCalories = (data.prefs.calorieZoneLowerBound - data.eatenCalories).coerceAtLeast(0),
                    )
                }
            }
        }
    }

    fun onQueryChanged(q: String) = _uiState.update { it.copy(query = q, message = null) }
    fun onCategoryChanged(c: FoodCategory) = _uiState.update { it.copy(category = c) }

    fun requestLogFood(food: SavedFoodEntity) {
        _uiState.update { it.copy(showQuantityDialog = true, pendingFood = food, quantityGrams = "100") }
    }

    fun onQuantityChanged(v: String) = _uiState.update { it.copy(quantityGrams = v) }

    fun confirmLogFood() {
        val state = _uiState.value
        val food = state.pendingFood ?: return
        val grams = state.quantityGrams.toDoubleOrNull()
        if (grams == null || grams < 1) {
            _uiState.update { it.copy(message = "Enter a valid quantity (min 1g).") }
            return
        }
        val scale = grams / 100.0
        viewModelScope.launch {
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = dateProvider.today(),
                    mealType = "FOOD_LIBRARY",
                    name = food.name,
                    calories = (food.calories * scale).toInt(),
                    proteinG = food.proteinG * scale,
                    carbsG = food.carbsG * scale,
                    fatG = food.fatG * scale,
                ),
                slotId = state.slotId,
            )
            _uiState.update {
                it.copy(showQuantityDialog = false, pendingFood = null, message = "${food.name} logged.")
            }
        }
    }

    fun dismissQuantityDialog() = _uiState.update { it.copy(showQuantityDialog = false, pendingFood = null) }

    fun logMeal(meal: SavedMealEntity) {
        viewModelScope.launch {
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = dateProvider.today(),
                    mealType = meal.mealType,
                    name = meal.name,
                    calories = meal.calories,
                    proteinG = meal.proteinG,
                    carbsG = meal.carbsG,
                    fatG = meal.fatG,
                ),
                slotId = _uiState.value.slotId,
            )
            _uiState.update { it.copy(message = "${meal.name} logged.") }
        }
    }

    fun onNewFoodNameChanged(v: String) = _uiState.update { it.copy(newFoodName = v) }
    fun onNewFoodServingChanged(v: String) = _uiState.update { it.copy(newFoodServing = v) }
    fun onNewFoodCaloriesChanged(v: String) = _uiState.update { it.copy(newFoodCalories = v) }
    fun onNewFoodProteinChanged(v: String) = _uiState.update { it.copy(newFoodProtein = v) }
    fun onNewFoodCarbsChanged(v: String) = _uiState.update { it.copy(newFoodCarbs = v) }
    fun onNewFoodFatChanged(v: String) = _uiState.update { it.copy(newFoodFat = v) }
    fun toggleCreateFoodForm() = _uiState.update { it.copy(showCreateFoodForm = !it.showCreateFoodForm, message = null) }

    fun saveNewFood() {
        val s = _uiState.value
        val cal = s.newFoodCalories.toIntOrNull()
        val p = s.newFoodProtein.toDoubleOrNull()
        val c = s.newFoodCarbs.toDoubleOrNull()
        val f = s.newFoodFat.toDoubleOrNull()
        if (s.newFoodName.isBlank() || cal == null || p == null || c == null || f == null) {
            _uiState.update { it.copy(message = "Fill in all fields with valid numbers.") }
            return
        }
        viewModelScope.launch {
            logRepository.saveFood(
                SavedFoodEntity(
                    name = s.newFoodName.trim(),
                    servingName = s.newFoodServing.trim(),
                    calories = cal,
                    proteinG = p,
                    carbsG = c,
                    fatG = f,
                ),
            )
            _uiState.update {
                it.copy(
                    showCreateFoodForm = false,
                    newFoodName = "", newFoodServing = "100g",
                    newFoodCalories = "", newFoodProtein = "",
                    newFoodCarbs = "", newFoodFat = "",
                    message = "Food saved to library.",
                )
            }
        }
    }

    fun openSaveMealDialog() {
        _uiState.update { it.copy(showSaveMealDialog = true, saveMealName = it.slotName) }
    }

    fun onSaveMealNameChanged(v: String) = _uiState.update { it.copy(saveMealName = v) }

    fun confirmSaveMeal() {
        val s = _uiState.value
        if (s.saveMealName.isBlank()) return
        viewModelScope.launch {
            val slotEntries = logRepository.getMealEntriesForSlot(
                date = dateProvider.today().toString(),
                slotId = s.slotId,
            )
            if (slotEntries.isEmpty()) {
                _uiState.update { it.copy(message = "No foods in slot to save.") }
                return@launch
            }
            logRepository.saveMeal(
                SavedMealEntity(
                    name = s.saveMealName.trim(),
                    mealType = "SAVED",
                    calories = slotEntries.sumOf { it.calories },
                    proteinG = slotEntries.sumOf { it.proteinG },
                    carbsG = slotEntries.sumOf { it.carbsG },
                    fatG = slotEntries.sumOf { it.fatG },
                ),
            )
            _uiState.update {
                it.copy(showSaveMealDialog = false, saveMealName = "", message = "Meal saved.")
            }
        }
    }

    fun dismissSaveMealDialog() = _uiState.update { it.copy(showSaveMealDialog = false) }
}
