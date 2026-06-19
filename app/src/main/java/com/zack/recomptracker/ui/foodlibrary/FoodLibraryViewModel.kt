package com.zack.recomptracker.ui.foodlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.repository.FoodCatalogRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.RecipeRepository
import com.zack.recomptracker.domain.food.FoodMacros
import com.zack.recomptracker.domain.food.FoodScaling
import com.zack.recomptracker.domain.food.MealEntryTypes
import com.zack.recomptracker.domain.food.RecipeWithIngredients
import com.zack.recomptracker.ui.component.AmountMode
import com.zack.recomptracker.ui.component.MessageKind
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEBOUNCE_SEARCH_MS = 600L

enum class FoodCategory { ALL, PROTEINS, CARBS, MEALS, NEVO, OFF }

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
    val messageKind: MessageKind = MessageKind.INFO,
    val showAmountSheet: Boolean = false,
    val pendingFood: SavedFoodEntity? = null,
    val editingEntryId: Long? = null,
    val amountMode: AmountMode = AmountMode.SERVINGS,
    val servingsValue: String = "1",
    val gramsValue: String = "100",
    // Recipe portion picker: log a multiple of the whole recipe (1 = whole recipe).
    val showRecipeAmountSheet: Boolean = false,
    val pendingRecipe: RecipeWithIngredients? = null,
    val recipePortions: String = "1",
    val editingFoodId: Long? = null,
    val newFoodName: String = "",
    val newFoodServing: String = "100g",
    val newFoodCalories: String = "",
    val newFoodProtein: String = "",
    val newFoodCarbs: String = "",
    val newFoodFat: String = "",
    val newFoodServingName: String = "",
    val newFoodServingGrams: String = "",
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
    val offSearchResults: List<com.zack.recomptracker.data.local.entity.SavedFoodEntity> = emptyList(),
    val offSearchLoading: Boolean = false,
    val allRecipes: List<RecipeWithIngredients> = emptyList(),
    val filteredRecipes: List<RecipeWithIngredients> = emptyList(),
    val pickerMode: Boolean = false,
    // Pre-computed by the ViewModel whenever filtering inputs change — never computed
    // on the main thread during recomposition, preventing jank and ANR-style crashes.
    val filteredFoods: List<FoodLibraryItem> = emptyList(),
    val filteredMeals: List<SavedMealEntity> = emptyList(),
    val recentFoods: List<SavedFoodEntity> = emptyList(),
) {
    val canUseServings: Boolean
        get() = (pendingFood?.householdServingGrams ?: 0.0) >= 1.0 &&
            !pendingFood?.householdServingName.isNullOrBlank()

    val resolvedGrams: Double?
        get() {
            val food = pendingFood ?: return null
            return when (amountMode) {
                AmountMode.SERVINGS -> {
                    val servings = servingsValue.toDoubleOrNull() ?: return null
                    val perServing = food.householdServingGrams ?: FoodScaling.DEFAULT_SERVING_GRAMS
                    if (servings < FoodScaling.MIN_SERVINGS || perServing < 1.0) null
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

    /** Portion multiplier for the pending recipe (1 = whole recipe). Null if invalid. */
    val recipePortionFactor: Double?
        get() {
            val v = recipePortions.toDoubleOrNull() ?: return null
            return if (v < FoodScaling.MIN_SERVINGS) null else v
        }

    /** Whole-recipe macros scaled by [recipePortionFactor], for the live preview. */
    val recipePreviewMacros: FoodMacros?
        get() {
            val recipe = pendingRecipe ?: return null
            val factor = recipePortionFactor ?: return null
            return FoodMacros(
                calories = (recipe.totalCalories * factor).roundToInt(),
                proteinG = recipe.totalProteinG * factor,
                carbsG = recipe.totalCarbsG * factor,
                fatG = recipe.totalFatG * factor,
            )
        }

    fun withComputedFields(): FoodLibraryUiState {
        val q = query.trim().lowercase()
        return copy(
            filteredFoods = computeFilteredFoods(q),
            filteredMeals = allMeals.filter { q.isEmpty() || it.name.lowercase().contains(q) },
            filteredRecipes = allRecipes.filter { q.isEmpty() || it.recipe.name.lowercase().contains(q) },
            recentFoods = recentEntries.map { e ->
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
            },
        )
    }

    private fun computeFilteredFoods(q: String): List<FoodLibraryItem> {
        val cat = this.category
        fun SavedFoodEntity.matchesCat(): Boolean = when (cat) {
            FoodCategory.PROTEINS -> proteinG >= carbsG && proteinG >= fatG
            FoodCategory.CARBS -> carbsG >= proteinG && carbsG > fatG
            else -> true
        }

        if (cat == FoodCategory.OFF) {
            return offSearchResults.mapIndexed { i, food ->
                FoodLibraryItem(key = "off_${food.name}_$i", food = food, sourceLabel = "Open Food Facts")
            }
        }

        // NEVO: require a search term — the catalog can have thousands of entries.
        if (cat == FoodCategory.NEVO) {
            if (q.isEmpty()) return emptyList()
            return allCatalogFoods
                .filter { it.name.lowercase().contains(q) }
                .map { food ->
                    FoodLibraryItem(
                        key = "catalog_${food.id}",
                        food = SavedFoodEntity(
                            name = food.name, servingName = food.servingName,
                            calories = food.calories, proteinG = food.proteinG,
                            carbsG = food.carbsG, fatG = food.fatG,
                        ),
                        sourceLabel = "NEVO",
                    )
                }
        }

        val personal = allFoods.filter { food ->
            (q.isEmpty() || food.name.lowercase().contains(q)) && food.matchesCat()
        }.map { food -> FoodLibraryItem(key = "personal_${food.id}", food = food) }

        val catalog = if (q.isEmpty()) emptyList() else {
            allCatalogFoods.filter { food ->
                food.name.lowercase().contains(q) &&
                    SavedFoodEntity(
                        name = food.name, servingName = food.servingName,
                        calories = food.calories, proteinG = food.proteinG,
                        carbsG = food.carbsG, fatG = food.fatG,
                    ).matchesCat()
            }.map { food ->
                FoodLibraryItem(
                    key = "catalog_${food.id}",
                    food = SavedFoodEntity(
                        name = food.name, servingName = food.servingName,
                        calories = food.calories, proteinG = food.proteinG,
                        carbsG = food.carbsG, fatG = food.fatG,
                    ),
                    sourceLabel = food.source,
                )
            }
        }
        return personal + catalog
    }
}

class FoodLibraryViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val foodCatalogRepository: FoodCatalogRepository,
    private val barcodeRepository: com.zack.recomptracker.data.repository.BarcodeRepository,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodLibraryUiState())
    val uiState: StateFlow<FoodLibraryUiState> = _uiState

    private val _loggedEvent = MutableSharedFlow<String>(replay = 0)
    val loggedEvent: SharedFlow<String> = _loggedEvent

    private val _ingredientPickedEvent = MutableSharedFlow<RecipeIngredientEntity>(replay = 0)
    val ingredientPickedEvent: SharedFlow<RecipeIngredientEntity> = _ingredientPickedEvent

    private var initialized = false
    private var logDate: LocalDate = dateProvider.today()
    private var offSearchJob: Job? = null

    /** Adding food onto a future day creates plans, not eaten entries. */
    private fun isPlannedDate(): Boolean = logDate.isAfter(dateProvider.today())

    private fun dayLabel(): String =
        logDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d"))

    /** Toast wording that reflects whether the entry was logged (eaten) or planned. */
    private fun addedMessage(name: String, slotLabel: String): String =
        if (isPlannedDate()) "Planned $name for ${dayLabel()}" else "Added $name to $slotLabel"

    fun init(slotId: Long?, slotName: String, editEntryId: Long? = null, logDateStr: String = "", pickerMode: Boolean = false) {
        logDate = if (logDateStr.isNotEmpty()) LocalDate.parse(logDateStr) else dateProvider.today()
        _uiState.update { it.copy(slotId = slotId, slotName = slotName.ifBlank { "Food Log" }, pickerMode = pickerMode) }
        if (editEntryId != null && _uiState.value.editingEntryId != editEntryId) {
            _uiState.update { it.copy(editingEntryId = editEntryId) }
            viewModelScope.launch {
                logRepository.getMealEntry(editEntryId)?.let { requestEditEntry(it) }
            }
        }
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
                    ).withComputedFields()
                }
            }
        }
        viewModelScope.launch {
            logRepository.observeRecentFoods().collect { recents ->
                _uiState.update { it.copy(recentEntries = recents).withComputedFields() }
            }
        }
        viewModelScope.launch {
            recipeRepository.observeAll().collect { recipes ->
                _uiState.update { it.copy(allRecipes = recipes).withComputedFields() }
            }
        }
    }

    private fun triggerOffSearch() {
        offSearchJob?.cancel()
        val q = _uiState.value.query.trim()
        if (q.isBlank()) {
            _uiState.update { it.copy(offSearchResults = emptyList(), offSearchLoading = false, message = null).withComputedFields() }
            return
        }
        offSearchJob = viewModelScope.launch {
            delay(DEBOUNCE_SEARCH_MS)
            searchOff()
        }
    }

    fun onQueryChanged(q: String) {
        _uiState.update { it.copy(query = q, message = null).withComputedFields() }
        if (_uiState.value.category == FoodCategory.OFF) {
            triggerOffSearch()
        }
    }

    fun onCategoryChanged(c: FoodCategory) {
        _uiState.update { it.copy(category = c).withComputedFields() }
        if (c == FoodCategory.OFF && _uiState.value.query.isNotBlank()) {
            triggerOffSearch()
        }
    }

    fun requestLogFood(food: SavedFoodEntity) {
        _uiState.update {
            it.copy(
                showAmountSheet = true,
                pendingFood = food,
                editingEntryId = null,
                amountMode = AmountMode.SERVINGS,
                servingsValue = "1",
                gramsValue = "100",
                message = null,
            )
        }
    }

    fun requestEditEntry(entry: com.zack.recomptracker.data.local.entity.MealEntryEntity) {
        val baseCalories = entry.basePer100Calories ?: return
        val base = SavedFoodEntity(
            name = entry.name,
            servingName = entry.entryServingName ?: "100g",
            calories = baseCalories,
            proteinG = entry.basePer100ProteinG ?: 0.0,
            carbsG = entry.basePer100CarbsG ?: 0.0,
            fatG = entry.basePer100FatG ?: 0.0,
            householdServingName = entry.entryServingName,
            householdServingGrams = entry.entryServingGrams,
        )
        val servingGrams = entry.entryServingGrams
        val useServings = (entry.loggedByServings && servingGrams != null) ||
            (servingGrams != null && entry.entryServingName != null)
        _uiState.update {
            it.copy(
                showAmountSheet = true,
                pendingFood = base,
                editingEntryId = entry.id,
                amountMode = if (useServings) AmountMode.SERVINGS else AmountMode.GRAMS,
                servingsValue = if (useServings && servingGrams != null && servingGrams >= 1.0) {
                    val servings = ((entry.amountGrams ?: 0.0) / servingGrams).coerceAtLeast(1.0)
                    if (servings == servings.toLong().toDouble()) servings.toLong().toString() else servings.toString()
                } else "1",
                gramsValue = (entry.amountGrams ?: 100.0).toInt().toString(),
                message = null,
            )
        }
    }

    fun onAmountModeChanged(mode: AmountMode) = _uiState.update { it.copy(amountMode = mode) }
    fun onServingsChanged(v: String) = _uiState.update { it.copy(servingsValue = v) }
    fun onGramsChanged(v: String) = _uiState.update { it.copy(gramsValue = v) }

    fun stepServings(delta: Double) = _uiState.update {
        val current = it.servingsValue.toDoubleOrNull() ?: 1.0
        val next = (current + delta).coerceAtLeast(FoodScaling.MIN_SERVINGS)
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
            _uiState.update { it.copy(message = "Enter a valid amount (min ${FoodScaling.MIN_GRAMS.toInt()}g).", messageKind = MessageKind.ERROR) }
            return
        }
        val servingName = food.householdServingName
        val servingGrams = food.householdServingGrams
        if (state.pickerMode) {
            val ingredient = RecipeIngredientEntity(
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
                loggedByServings = state.amountMode == AmountMode.SERVINGS,
            )
            viewModelScope.launch {
                _uiState.update { it.copy(showAmountSheet = false, pendingFood = null, message = null) }
                _ingredientPickedEvent.emit(ingredient)
            }
            return
        }
        viewModelScope.launch {
            val editingId = state.editingEntryId
            if (editingId == null) {
                logRepository.addMealToSlot(
                    input = MealEntryInput(
                        date = logDate,
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
                        loggedByServings = state.amountMode == AmountMode.SERVINGS,
                        planned = isPlannedDate(),
                    ),
                    slotId = state.slotId,
                )
                _uiState.update { it.copy(showAmountSheet = false, pendingFood = null, message = null) }
                val slotLabel = if (state.slotId != null) state.slotName else "log"
                _loggedEvent.emit(addedMessage(food.name, slotLabel))
            } else {
                val existing = logRepository.getMealEntry(editingId)
                if (existing == null) {
                    _uiState.update { it.copy(message = "Couldn't find that entry to update.", messageKind = MessageKind.ERROR) }
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
                        loggedByServings = state.amountMode == AmountMode.SERVINGS,
                    ),
                )
                _uiState.update { it.copy(showAmountSheet = false, pendingFood = null, editingEntryId = null, message = null) }
                _loggedEvent.emit("${food.name} updated")
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
                    date = logDate,
                    mealType = meal.mealType,
                    name = meal.name,
                    calories = meal.calories,
                    proteinG = meal.proteinG,
                    carbsG = meal.carbsG,
                    fatG = meal.fatG,
                    planned = isPlannedDate(),
                ),
                slotId = _uiState.value.slotId,
            )
            val slotLabel = if (_uiState.value.slotId != null) _uiState.value.slotName else "log"
            _uiState.update { it.copy(message = null) }
            _loggedEvent.emit(addedMessage(meal.name, slotLabel))
        }
    }

    fun requestLogRecipe(recipe: RecipeWithIngredients) {
        _uiState.update {
            it.copy(
                showRecipeAmountSheet = true,
                pendingRecipe = recipe,
                recipePortions = "1",
                message = null,
            )
        }
    }

    fun onRecipePortionsChanged(v: String) = _uiState.update { it.copy(recipePortions = v) }

    fun stepRecipePortions(delta: Double) = _uiState.update {
        val current = it.recipePortions.toDoubleOrNull() ?: 1.0
        val next = (current + delta).coerceAtLeast(FoodScaling.MIN_SERVINGS)
        val text = if (next == next.toLong().toDouble()) next.toLong().toString() else next.toString()
        it.copy(recipePortions = text)
    }

    fun dismissRecipeAmountSheet() = _uiState.update {
        it.copy(showRecipeAmountSheet = false, pendingRecipe = null)
    }

    /** Logs each ingredient scaled by the chosen portion factor (1 = whole recipe). */
    fun confirmLogRecipe() {
        val state = _uiState.value
        val recipe = state.pendingRecipe ?: return
        val factor = state.recipePortionFactor
        if (factor == null) {
            _uiState.update { it.copy(message = "Enter a valid portion.", messageKind = MessageKind.ERROR) }
            return
        }
        viewModelScope.launch {
            recipe.ingredients.forEach { ingredient ->
                logRepository.addMealToSlot(
                    input = MealEntryInput(
                        date = logDate,
                        mealType = MealEntryTypes.RECIPE,
                        name = ingredient.name,
                        calories = (ingredient.calories * factor).roundToInt(),
                        proteinG = ingredient.proteinG * factor,
                        carbsG = ingredient.carbsG * factor,
                        fatG = ingredient.fatG * factor,
                        amountGrams = ingredient.amountGrams?.let { it * factor },
                        basePer100Calories = ingredient.basePer100Calories,
                        basePer100ProteinG = ingredient.basePer100ProteinG,
                        basePer100CarbsG = ingredient.basePer100CarbsG,
                        basePer100FatG = ingredient.basePer100FatG,
                        entryServingName = ingredient.entryServingName,
                        entryServingGrams = ingredient.entryServingGrams,
                        loggedByServings = ingredient.loggedByServings,
                        planned = isPlannedDate(),
                    ),
                    slotId = state.slotId,
                )
            }
            _uiState.update { it.copy(showRecipeAmountSheet = false, pendingRecipe = null, message = null) }
            val slotLabel = if (state.slotId != null) state.slotName else "log"
            val verb = if (isPlannedDate()) "planned for ${dayLabel()}" else "added to $slotLabel"
            _loggedEvent.emit("${recipe.recipe.name} $verb (${recipe.ingredients.size} items)")
        }
    }

    fun onNewFoodNameChanged(v: String) = _uiState.update { it.copy(newFoodName = v) }
    fun onNewFoodServingChanged(v: String) = _uiState.update { it.copy(newFoodServing = v) }
    fun onNewFoodCaloriesChanged(v: String) = _uiState.update { it.copy(newFoodCalories = v) }
    fun onNewFoodProteinChanged(v: String) = _uiState.update { it.copy(newFoodProtein = v) }
    fun onNewFoodCarbsChanged(v: String) = _uiState.update { it.copy(newFoodCarbs = v) }
    fun onNewFoodFatChanged(v: String) = _uiState.update { it.copy(newFoodFat = v) }
    fun toggleCreateFoodForm() = _uiState.update {
        // Toggling always starts from a clean slate (a fresh "create"); editing is
        // entered via openEditFood(), which prefills explicitly.
        it.copy(
            showCreateFoodForm = !it.showCreateFoodForm,
            editingFoodId = null,
            newFoodName = "",
            newFoodServing = "100g",
            newFoodCalories = "",
            newFoodProtein = "",
            newFoodCarbs = "",
            newFoodFat = "",
            newFoodServingName = "",
            newFoodServingGrams = "",
            message = null,
        )
    }

    fun onNewFoodServingNameChanged(v: String) = _uiState.update { it.copy(newFoodServingName = v) }
    fun onNewFoodServingGramsChanged(v: String) = _uiState.update { it.copy(newFoodServingGrams = v) }

    fun openEditFood(food: SavedFoodEntity) = _uiState.update {
        it.copy(
            showCreateFoodForm = true,
            editingFoodId = food.id,
            newFoodName = food.name,
            newFoodServing = food.servingName,
            newFoodCalories = food.calories.toString(),
            newFoodProtein = food.proteinG.toString(),
            newFoodCarbs = food.carbsG.toString(),
            newFoodFat = food.fatG.toString(),
            newFoodServingName = food.householdServingName.orEmpty(),
            newFoodServingGrams = food.householdServingGrams?.toInt()?.toString().orEmpty(),
            message = null,
        )
    }

    fun saveNewFood() {
        val s = _uiState.value
        val cal = s.newFoodCalories.toIntOrNull()
        val p = s.newFoodProtein.toDoubleOrNull()
        val c = s.newFoodCarbs.toDoubleOrNull()
        val f = s.newFoodFat.toDoubleOrNull()
        if (s.newFoodName.isBlank() || cal == null || p == null || c == null || f == null) {
            _uiState.update { it.copy(message = "Fill in all fields with valid numbers.", messageKind = MessageKind.ERROR) }
            return
        }
        val servingGrams = s.newFoodServingGrams.toDoubleOrNull()
        val servingName = s.newFoodServingName.trim().ifBlank { null }
        viewModelScope.launch {
            logRepository.saveFood(
                SavedFoodEntity(
                    id = s.editingFoodId ?: 0,
                    name = s.newFoodName.trim(),
                    servingName = s.newFoodServing.trim(),
                    calories = cal,
                    proteinG = p,
                    carbsG = c,
                    fatG = f,
                    householdServingName = servingName?.takeIf { servingGrams != null && servingGrams >= 1.0 },
                    householdServingGrams = servingGrams?.takeIf { it >= 1.0 && servingName != null },
                ),
            )
            _uiState.update {
                it.copy(
                    showCreateFoodForm = false,
                    editingFoodId = null,
                    newFoodName = "", newFoodServing = "100g",
                    newFoodCalories = "", newFoodProtein = "",
                    newFoodCarbs = "", newFoodFat = "",
                    newFoodServingName = "", newFoodServingGrams = "",
                    message = if (s.editingFoodId != null) "Food updated." else "Food saved to library.",
                    messageKind = MessageKind.SUCCESS,
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
                date = logDate.toString(),
                slotId = s.slotId,
            )
            if (slotEntries.isEmpty()) {
                _uiState.update { it.copy(message = "No foods in slot to save.", messageKind = MessageKind.ERROR) }
                return@launch
            }
            val ingredients = slotEntries.mapIndexed { index, entry ->
                RecipeIngredientEntity(
                    name = entry.name,
                    sortOrder = index,
                    calories = entry.calories,
                    proteinG = entry.proteinG,
                    carbsG = entry.carbsG,
                    fatG = entry.fatG,
                    amountGrams = entry.amountGrams,
                    basePer100Calories = entry.basePer100Calories,
                    basePer100ProteinG = entry.basePer100ProteinG,
                    basePer100CarbsG = entry.basePer100CarbsG,
                    basePer100FatG = entry.basePer100FatG,
                    entryServingName = entry.entryServingName,
                    entryServingGrams = entry.entryServingGrams,
                    loggedByServings = entry.loggedByServings,
                )
            }
            recipeRepository.saveRecipe(s.saveMealName.trim(), ingredients)
            _uiState.update {
                it.copy(showSaveMealDialog = false, saveMealName = "", message = "Recipe saved.", messageKind = MessageKind.SUCCESS)
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
        if (cal == null || cal <= 0) {
            _uiState.update { it.copy(message = "Enter a calorie amount.", messageKind = MessageKind.ERROR) }
            return
        }
        viewModelScope.launch {
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = logDate,
                    mealType = MealEntryTypes.QUICK_ADD,
                    name = s.quickAddName.ifBlank { "Quick add" },
                    calories = cal,
                    proteinG = s.quickAddProtein.toDoubleOrNull() ?: 0.0,
                    carbsG = s.quickAddCarbs.toDoubleOrNull() ?: 0.0,
                    fatG = s.quickAddFat.toDoubleOrNull() ?: 0.0,
                    planned = isPlannedDate(),
                ),
                slotId = s.slotId,
            )
            _uiState.update { it.copy(showQuickAddDialog = false, message = null) }
            _loggedEvent.emit(if (isPlannedDate()) "Quick add planned for ${dayLabel()}" else "Quick add logged")
        }
    }

    fun searchOff() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) {
            _uiState.update { it.copy(message = "Enter a search term.", messageKind = MessageKind.ERROR) }
            return
        }
        _uiState.update { it.copy(offSearchLoading = true, message = null, offSearchResults = emptyList()) }
        viewModelScope.launch {
            val products = barcodeRepository.searchFoods(q)
            _uiState.update { state ->
                state.copy(
                    offSearchLoading = false,
                    offSearchResults = products.map { product ->
                        com.zack.recomptracker.data.local.entity.SavedFoodEntity(
                            name = product.name,
                            servingName = product.servingName ?: "100g",
                            calories = product.caloriesPer100g,
                            proteinG = product.proteinPer100g,
                            carbsG = product.carbsPer100g,
                            fatG = product.fatPer100g,
                            householdServingName = product.servingName,
                            householdServingGrams = product.servingGrams,
                        )
                    },
                    message = if (products.isEmpty()) "No products found for '$q'." else null,
                    messageKind = MessageKind.INFO,
                ).withComputedFields()
            }
        }
    }
}
