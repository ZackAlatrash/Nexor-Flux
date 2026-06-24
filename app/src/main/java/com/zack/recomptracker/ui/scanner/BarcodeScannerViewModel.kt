package com.zack.recomptracker.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.repository.BarcodeProduct
import com.zack.recomptracker.data.repository.BarcodeRepository
import com.zack.recomptracker.data.repository.BarcodeResult
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.domain.food.MealEntryTypes
import com.zack.recomptracker.ui.component.MessageKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LogMode { GRAMS, SERVING }

sealed class ScanState {
    object Scanning : ScanState()
    object Loading : ScanState()
    data class ProductFound(
        val product: BarcodeProduct,
        val logMode: LogMode = LogMode.GRAMS,
        val amountInput: String = "100",
    ) : ScanState()
    object NotFound : ScanState()
    object NetworkError : ScanState()
    data class ShowingSuccess(val message: String) : ScanState()
    object Logged : ScanState()
    data class PickedForRecipe(val food: SavedFoodEntity) : ScanState()
}

data class BarcodeScannerUiState(
    val slotId: Long? = null,
    val slotName: String = "",
    val scanState: ScanState = ScanState.Scanning,
    val message: String? = null,
    val messageKind: MessageKind = MessageKind.ERROR,
)

private const val SUCCESS_OVERLAY_MS = 800L

class BarcodeScannerViewModel(
    private val barcodeRepository: BarcodeRepository,
    private val logRepository: LogRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BarcodeScannerUiState())
    val uiState: StateFlow<BarcodeScannerUiState> = _uiState
    private var lastScannedBarcode: String? = null
    private var pickerMode = false

    fun init(slotId: Long?, slotName: String, pickerMode: Boolean = false) {
        this.pickerMode = pickerMode
        _uiState.update { it.copy(slotId = slotId, slotName = slotName) }
    }

    fun onBarcodeDetected(barcode: String) {
        if (barcode == lastScannedBarcode) return
        if (_uiState.value.scanState is ScanState.Loading) return
        lastScannedBarcode = barcode
        _uiState.update { it.copy(scanState = ScanState.Loading) }
        viewModelScope.launch {
            val result = barcodeRepository.lookupBarcode(barcode)
            _uiState.update { state ->
                state.copy(
                    scanState = when (result) {
                        is BarcodeResult.Found -> {
                            // Default to 1 serving when the product defines a serving; otherwise 100 g.
                            val hasServing = (result.product.servingGrams ?: 0.0) >= 1.0
                            ScanState.ProductFound(
                                product = result.product,
                                logMode = if (hasServing) LogMode.SERVING else LogMode.GRAMS,
                                amountInput = if (hasServing) "1" else "100",
                            )
                        }
                        BarcodeResult.NotFound -> ScanState.NotFound
                        BarcodeResult.NetworkError -> ScanState.NetworkError
                    },
                )
            }
        }
    }

    fun onAmountChanged(grams: String) {
        val current = _uiState.value.scanState as? ScanState.ProductFound ?: return
        _uiState.update { it.copy(scanState = current.copy(amountInput = grams)) }
    }

    fun onLogModeChanged(mode: LogMode) {
        val current = _uiState.value.scanState as? ScanState.ProductFound ?: return
        if (current.logMode == mode) return
        val servingGrams = current.product.servingGrams ?: return

        val convertedInput = when (mode) {
            LogMode.SERVING -> {
                val grams = current.amountInput.toDoubleOrNull()
                if (grams != null && grams > 0) {
                    "%.2f".format(java.util.Locale.US, grams / servingGrams).trimEnd('0').trimEnd('.')
                } else "1"
            }
            LogMode.GRAMS -> {
                val servings = current.amountInput.toDoubleOrNull()
                if (servings != null && servings > 0) {
                    (servings * servingGrams).toInt().toString()
                } else "100"
            }
        }
        _uiState.update { it.copy(scanState = current.copy(logMode = mode, amountInput = convertedInput)) }
    }

    private fun resolveGrams(productState: ScanState.ProductFound): Double? {
        return when (productState.logMode) {
            LogMode.GRAMS -> productState.amountInput.toDoubleOrNull()
            LogMode.SERVING -> {
                val servings = productState.amountInput.toDoubleOrNull()
                val servingGrams = productState.product.servingGrams
                if (servings != null && servingGrams != null) servings * servingGrams else null
            }
        }
    }

    fun confirmLog() {
        val state = _uiState.value
        val productState = state.scanState as? ScanState.ProductFound ?: return
        val product = productState.product
        val actualGrams = resolveGrams(productState)
        if (actualGrams == null || actualGrams < 1.0) {
            _uiState.update { it.copy(message = "Enter a valid amount (min 1g).") }
            return
        }
        if (pickerMode) {
            val food = SavedFoodEntity(
                name = product.name,
                servingName = product.servingName ?: "100g",
                calories = product.caloriesPer100g,
                proteinG = product.proteinPer100g,
                carbsG = product.carbsPer100g,
                fatG = product.fatPer100g,
                householdServingName = product.servingName,
                householdServingGrams = product.servingGrams,
            )
            _uiState.update { it.copy(scanState = ScanState.PickedForRecipe(food)) }
            return
        }

        val scale = actualGrams / 100.0
        viewModelScope.launch {
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = dateProvider.today(),
                    mealType = MealEntryTypes.FOOD_LIBRARY,
                    name = product.name,
                    calories = (product.caloriesPer100g * scale).toInt(),
                    proteinG = product.proteinPer100g * scale,
                    carbsG = product.carbsPer100g * scale,
                    fatG = product.fatPer100g * scale,
                    amountGrams = actualGrams,
                    basePer100Calories = product.caloriesPer100g,
                    basePer100ProteinG = product.proteinPer100g,
                    basePer100CarbsG = product.carbsPer100g,
                    basePer100FatG = product.fatPer100g,
                    entryServingName = product.servingName,
                    entryServingGrams = product.servingGrams,
                    loggedByServings = productState.logMode == LogMode.SERVING,
                ),
                slotId = state.slotId,
            )
            val slotLabel = state.slotName.ifBlank { "log" }
            _uiState.update { it.copy(scanState = ScanState.ShowingSuccess("Added to $slotLabel")) }
            delay(SUCCESS_OVERLAY_MS)
            _uiState.update { it.copy(scanState = ScanState.Logged) }
        }
    }

    fun confirmLogAndSave() {
        val state = _uiState.value
        val productState = state.scanState as? ScanState.ProductFound ?: return
        val product = productState.product
        val actualGrams = resolveGrams(productState)
        if (actualGrams == null || actualGrams < 1.0) {
            _uiState.update { it.copy(message = "Enter a valid amount (min 1g).") }
            return
        }
        val scale = actualGrams / 100.0
        viewModelScope.launch {
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = dateProvider.today(),
                    mealType = MealEntryTypes.FOOD_LIBRARY,
                    name = product.name,
                    calories = (product.caloriesPer100g * scale).toInt(),
                    proteinG = product.proteinPer100g * scale,
                    carbsG = product.carbsPer100g * scale,
                    fatG = product.fatPer100g * scale,
                    amountGrams = actualGrams,
                    basePer100Calories = product.caloriesPer100g,
                    basePer100ProteinG = product.proteinPer100g,
                    basePer100CarbsG = product.carbsPer100g,
                    basePer100FatG = product.fatPer100g,
                    entryServingName = product.servingName,
                    entryServingGrams = product.servingGrams,
                    loggedByServings = productState.logMode == LogMode.SERVING,
                ),
                slotId = state.slotId,
            )
            logRepository.saveFood(
                SavedFoodEntity(
                    name = product.name,
                    servingName = product.servingName ?: "100g",
                    calories = product.caloriesPer100g,
                    proteinG = product.proteinPer100g,
                    carbsG = product.carbsPer100g,
                    fatG = product.fatPer100g,
                    householdServingName = product.servingName,
                    householdServingGrams = product.servingGrams,
                ),
            )
            val slotLabel = state.slotName.ifBlank { "log" }
            _uiState.update { it.copy(scanState = ScanState.ShowingSuccess("Saved & added to $slotLabel")) }
            delay(SUCCESS_OVERLAY_MS)
            _uiState.update { it.copy(scanState = ScanState.Logged) }
        }
    }

    fun saveToLibrary() {
        val productState = _uiState.value.scanState as? ScanState.ProductFound ?: return
        val product = productState.product
        viewModelScope.launch {
            logRepository.saveFood(
                SavedFoodEntity(
                    name = product.name,
                    servingName = product.servingName ?: "100g",
                    calories = product.caloriesPer100g,
                    proteinG = product.proteinPer100g,
                    carbsG = product.carbsPer100g,
                    fatG = product.fatPer100g,
                    householdServingName = product.servingName,
                    householdServingGrams = product.servingGrams,
                ),
            )
            _uiState.update { it.copy(message = "${product.name} saved to library.", messageKind = MessageKind.SUCCESS) }
        }
    }

    fun resetScan() {
        lastScannedBarcode = null
        _uiState.update { it.copy(scanState = ScanState.Scanning, message = null) }
    }
}
