package com.zack.recomptracker.ui.foodlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.repository.FoodCatalogRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.food.FoodMacros
import com.zack.recomptracker.domain.food.FoodScaling
import com.zack.recomptracker.domain.food.MealEntryTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FoodCategory { ALL, PROTEINS, CARBS, MEALS, NEVO }

enum class AmountMode { SERVINGS, GRAMS }

data class FoodLibraryItem(
    val key: String,
    val food: SavedFoodEntity,
    val sourceLabel: String? = null,
)

data class FoodLibraryUiState(
    val slotId: Long? = null,
    val slotName: String = "Food Log",
    val remainingCalories: Int = 0,
    val query: String = "",
    val category: FoodCategory = FoodCategory.ALL,
    val allFoods: List<SavedFoodEntity> = emptyList(),
    val allCatalogFoods: List<com.zack.recomptracker.data.local.entity.CatalogFoodEntity> = emptyList(),
    val allMeals: List<SavedMealEntity> = emptyList(),
    val message: String? = null,
    val showAmountSheet: Boolean = false,
    val pendingFood: SavedFoodEntity? = null,
    val editingEntryId: Long? = null,
    val amountMode: AmountMode = AmountMode.SERVINGS,
    val servingsValue: String = "1",
    val gramsValue: String = "100",
    val newFoodName: String = "",
    val newFoodServing: String = "100g",
    val newFoodCalories: String = "",
    val newFoodProtein: String = "",
    val newFoodCarbs: String = "",
    val newFoodFat: String = "",
    val showCreateFoodForm: Boolean = false,
    val showSaveMealDialog: Boolean = false,
    val saveMealName: String = "",
    val recentEntries: List<com.zack.recomptracker.data.local.entity.MealEntryEntity> = emptyList(),
    val showQuickAddDialog: Boolean = false,
    val quickAddName: String = "",
    val quickAddCalories: String = "",
    val quickAddProtein: String = "",
    val quickAddCarbs: String = "",
    val quickAddFat: String = "",
) {
    val filteredFoods: List<FoodLibraryItem>
        get() {
            val q = query.trim().lowercase()

            // NEVO tab: show the full catalog, searchable, no personal foods mixed in.
            if (category == FoodCategory.NEVO) {
                return allCatalogFoods
                    .filter { q.isEmpty() || it.name.lowercase().contains(q) }
                    .map { food ->
                        FoodLibraryItem(
                            key = "catalog_${food.id}",
                            food = SavedFoodEntity(
                                name = food.name,
                                servingName = food.servingName,
                                calories = food.calories,
                                proteinG = food.proteinG,
                                carbsG = food.carbsG,
                                fatG = food.fatG,
                            ),
                            sourceLabel = "NEVO",
                        )
                    }
            }

            // All / Proteins / Carbs / Meals tabs: personal foods always shown,
            // catalog foods only appear when searching.
            val personal = allFoods.filter { food ->
                (q.isEmpty() || food.name.lowercase().contains(q)) &&
                    food.matchesCategory()
            }.map { food -> FoodLibraryItem(key = "personal_${food.id}", food = food) }
            val catalog = if (q.isEmpty()) {
                emptyList()
            } else {
                allCatalogFoods.filter { food ->
                    food.name.lowercase().contains(q) &&
                        SavedFoodEntity(
                            name = food.name,
                            servingName = food.servingName,
                            calories = food.calories,
                            proteinG = food.proteinG,
                            carbsG = food.carbsG,
                            fatG = food.fatG,
                        ).matchesCategory()
                }.map { food ->
                    FoodLibraryItem(
                        key = "catalog_${food.id}",
                        food = SavedFoodEntity(
                            name = food.name,
                            servingName = food.servingName,
                            calories = food.calories,
                            proteinG = food.proteinG,
                            carbsG = food.carbsG,
                            fatG = food.fatG,
                        ),
                        sourceLabel = food.source,
                    )
                }
            }
            return personal + catalog
        }

    private fun SavedFoodEntity.matchesCategory(): Boolean = when (category) {
        FoodCategory.PROTEINS -> proteinG >= carbsG && proteinG >= fatG
        FoodCategory.CARBS -> carbsG >= proteinG && carbsG > fatG
        else -> true
    }

    val filteredMeals: List<SavedMealEntity>
        get() {
            val q = query.trim().lowercase()
            return allMeals.filter { q.isEmpty() || it.name.lowercase().contains(q) }
        }

    val canUseServings: Boolean
        get() = (pendingFood?.householdServingGrams ?: 0.0) >= 1.0 &&
            !pendingFood?.householdServingName.isNullOrBlank()

    val resolvedGrams: Double?
        get() {
            val food = pendingFood ?: return null
            return when (amountMode) {
                AmountMode.SERVINGS -> {
                    val servings = servingsValue.toDoubleOrNull() ?: return null
                    val perServing = food.householdServingGrams ?: return null
                    if (servings < 1.0 || perServing < 1.0) null
                    else FoodScaling.gramsForServings(servings, perServing)
                }
                AmountMode.GRAMS -> {
                    val g = gramsValue.toDoubleOrNull() ?: return null
                    if (g < FoodScaling.MIN_GRAMS) null else g
                }
            }
        }

    val previewMacros: FoodMacros?
        get() {
            val food = pendingFood ?: return null
            val grams = resolvedGrams ?: return null
            return FoodScaling.scale(
                FoodMacros(food.calories, food.proteinG, food.carbsG, food.fatG),
                grams,
            )
        }

    val recentFoods: List<SavedFoodEntity>
        get() = recentEntries.map { e ->
            SavedFoodEntity(
                name = e.name,
                servingName = e.entryServingName ?: "100g",
                calories = e.basePer100Calories ?: 0,
                proteinG = e.basePer100ProteinG ?: 0.0,
                carbsG = e.basePer100CarbsG ?: 0.0,
                fatG = e.basePer100FatG ?: 0.0,
                householdServingName = e.entryServingName,
                householdServingGrams = e.entryServingGrams,
            )
        }
}

class FoodLibraryViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val foodCatalogRepository: FoodCatalogRepository,
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
                foodCatalogRepository.observeCatalogFoods(),
                logRepository.observeSavedMeals(),
                planRepository.preferences,
                logRepository.observeDay(today),
            ) { foods, catalogFoods, meals, prefs, day ->
                object {
                    val foods = foods
                    val catalogFoods = catalogFoods
                    val meals = meals
                    val prefs = prefs
                    val eatenCalories = day.totals.calories
                }
            }.collect { data ->
                _uiState.update { current ->
                    current.copy(
                        allFoods = data.foods,
                        allCatalogFoods = data.catalogFoods,
                        allMeals = data.meals,
                        remainingCalories = (data.prefs.calorieZoneLowerBound - data.eatenCalories).coerceAtLeast(0),
                    )
                }
            }
        }
        viewModelScope.launch {
            logRepository.observeRecentFoods().collect { recents ->
                _uiState.update { it.copy(recentEntries = recents) }
            }
        }
    }

    fun onQueryChanged(q: String) = _uiState.update { it.copy(query = q, message = null) }
    fun onCategoryChanged(c: FoodCategory) = _uiState.update { it.copy(category = c) }

    fun requestLogFood(food: SavedFoodEntity) {
        val canServings = (food.householdServingGrams ?: 0.0) >= 1.0 && !food.householdServingName.isNullOrBlank()
        _uiState.update {
            it.copy(
                showAmountSheet = true,
                pendingFood = food,
                editingEntryId = null,
                amountMode = if (canServings) AmountMode.SERVINGS else AmountMode.GRAMS,
                servingsValue = "1",
                gramsValue = "100",
                message = null,
            )
        }
    }

    fun onAmountModeChanged(mode: AmountMode) = _uiState.update { it.copy(amountMode = mode) }
    fun onServingsChanged(v: String) = _uiState.update { it.copy(servingsValue = v) }
    fun onGramsChanged(v: String) = _uiState.update { it.copy(gramsValue = v) }

    fun stepServings(delta: Int) = _uiState.update {
        val current = it.servingsValue.toDoubleOrNull() ?: 1.0
        val next = (current + delta).coerceAtLeast(1.0)
        val text = if (next == next.toLong().toDouble()) next.toLong().toString() else next.toString()
        it.copy(servingsValue = text)
    }

    fun stepGrams(delta: Int) = _uiState.update {
        val current = it.gramsValue.toDoubleOrNull() ?: 100.0
        it.copy(gramsValue = (current + delta).coerceAtLeast(FoodScaling.MIN_GRAMS).toInt().toString())
    }

    fun confirmAmount() {
        val state = _uiState.value
        val food = state.pendingFood ?: return
        val grams = state.resolvedGrams
        val preview = state.previewMacros
        if (grams == null || preview == null) {
            _uiState.update { it.copy(message = "Enter a valid amount (min ${FoodScaling.MIN_GRAMS.toInt()}g).") }
            return
        }
        val servingName = food.householdServingName?.takeIf { state.amountMode == AmountMode.SERVINGS }
        val servingGrams = food.householdServingGrams?.takeIf { state.amountMode == AmountMode.SERVINGS }
        viewModelScope.launch {
            val editingId = state.editingEntryId
            if (editingId == null) {
                logRepository.addMealToSlot(
                    input = MealEntryInput(
                        date = dateProvider.today(),
                        mealType = MealEntryTypes.FOOD_LIBRARY,
                        name = food.name,
                        calories = preview.calories,
                        proteinG = preview.proteinG,
                        carbsG = preview.carbsG,
                        fatG = preview.fatG,
                        amountGrams = grams,
                        basePer100Calories = food.calories,
                        basePer100ProteinG = food.proteinG,
                        basePer100CarbsG = food.carbsG,
                        basePer100FatG = food.fatG,
                        entryServingName = servingName,
                        entryServingGrams = servingGrams,
                    ),
                    slotId = state.slotId,
                )
                _uiState.update {
                    it.copy(showAmountSheet = false, pendingFood = null, message = "${food.name} logged.")
                }
            } else {
                val existing = logRepository.getMealEntriesForSlot(
                    date = dateProvider.today().toString(),
                    slotId = state.slotId,
                ).firstOrNull { it.id == editingId }
                if (existing == null) {
                    _uiState.update { it.copy(message = "Couldn't find that entry to update.") }
                    return@launch
                }
                logRepository.updateMealEntry(
                    existing.copy(
                        name = food.name,
                        calories = preview.calories,
                        proteinG = preview.proteinG,
                        carbsG = preview.carbsG,
                        fatG = preview.fatG,
                        amountGrams = grams,
                        basePer100Calories = food.calories,
                        basePer100ProteinG = food.proteinG,
                        basePer100CarbsG = food.carbsG,
                        basePer100FatG = food.fatG,
                        entryServingName = servingName,
                        entryServingGrams = servingGrams,
                    ),
                )
                _uiState.update {
                    it.copy(showAmountSheet = false, pendingFood = null, editingEntryId = null, message = "Entry updated.")
                }
            }
        }
    }

    fun dismissAmountSheet() = _uiState.update {
        it.copy(showAmountSheet = false, pendingFood = null, editingEntryId = null)
    }

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

    fun openQuickAdd() = _uiState.update {
        it.copy(showQuickAddDialog = true, quickAddName = "", quickAddCalories = "", quickAddProtein = "", quickAddCarbs = "", quickAddFat = "", message = null)
    }
    fun dismissQuickAdd() = _uiState.update { it.copy(showQuickAddDialog = false) }
    fun onQuickAddNameChanged(v: String) = _uiState.update { it.copy(quickAddName = v) }
    fun onQuickAddCaloriesChanged(v: String) = _uiState.update { it.copy(quickAddCalories = v) }
    fun onQuickAddProteinChanged(v: String) = _uiState.update { it.copy(quickAddProtein = v) }
    fun onQuickAddCarbsChanged(v: String) = _uiState.update { it.copy(quickAddCarbs = v) }
    fun onQuickAddFatChanged(v: String) = _uiState.update { it.copy(quickAddFat = v) }

    fun confirmQuickAdd() {
        val s = _uiState.value
        val cal = s.quickAddCalories.toIntOrNull()
        if (cal == null || cal < 0) {
            _uiState.update { it.copy(message = "Enter a calorie amount.") }
            return
        }
        viewModelScope.launch {
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = dateProvider.today(),
                    mealType = MealEntryTypes.QUICK_ADD,
                    name = s.quickAddName.ifBlank { "Quick add" },
                    calories = cal,
                    proteinG = s.quickAddProtein.toDoubleOrNull() ?: 0.0,
                    carbsG = s.quickAddCarbs.toDoubleOrNull() ?: 0.0,
                    fatG = s.quickAddFat.toDoubleOrNull() ?: 0.0,
                ),
                slotId = s.slotId,
            )
            _uiState.update { it.copy(showQuickAddDialog = false, message = "Quick add logged.") }
        }
    }
}
