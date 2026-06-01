package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.remote.OpenFoodFactsApi

data class BarcodeProduct(
    val name: String,
    val caloriesPer100g: Int,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingName: String?,
    val servingGrams: Double?,
    val hasCompleteData: Boolean,
)

sealed class BarcodeResult {
    data class Found(val product: BarcodeProduct) : BarcodeResult()
    object NotFound : BarcodeResult()
    object NetworkError : BarcodeResult()
}

open class BarcodeRepository(private val api: OpenFoodFactsApi = OpenFoodFactsApi()) {

    open suspend fun lookupBarcode(barcode: String): BarcodeResult {
        val response = api.fetchByBarcode(barcode) ?: return BarcodeResult.NetworkError
        if (response.status != 1 || response.product == null) return BarcodeResult.NotFound

        val product = response.product
        val name = product.productNameNl.trim().ifBlank { product.productName.trim() }
        if (name.isBlank()) return BarcodeResult.NotFound

        val n = product.nutriments
        val hasComplete = n.energyKcal100g != null && n.proteins100g != null &&
            n.carbohydrates100g != null && n.fat100g != null

        return BarcodeResult.Found(
            BarcodeProduct(
                name = name,
                caloriesPer100g = n.energyKcal100g?.toInt() ?: 0,
                proteinPer100g = n.proteins100g ?: 0.0,
                carbsPer100g = n.carbohydrates100g ?: 0.0,
                fatPer100g = n.fat100g ?: 0.0,
                servingName = product.servingSize?.trim()?.takeIf { it.isNotBlank() },
                servingGrams = product.servingQuantity,
                hasCompleteData = hasComplete,
            ),
        )
    }
}
