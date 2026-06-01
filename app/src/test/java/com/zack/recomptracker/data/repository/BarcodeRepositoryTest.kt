package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.remote.OffNutriments
import com.zack.recomptracker.data.remote.OffProduct
import com.zack.recomptracker.data.remote.OffProductResponse
import com.zack.recomptracker.data.remote.OffSearchProduct
import com.zack.recomptracker.data.remote.OffSearchResponse
import com.zack.recomptracker.data.remote.OpenFoodFactsApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeRepositoryTest {

    private fun api(response: OffProductResponse?) = object : OpenFoodFactsApi() {
        override suspend fun fetchByBarcode(barcode: String) = response
    }

    private fun apiWithSearch(response: OffSearchResponse?) = object : OpenFoodFactsApi() {
        override suspend fun fetchByBarcode(barcode: String) = null
        override suspend fun searchByName(query: String) = response
    }

    @Test
    fun `returns Found with complete data for full product`() = runTest {
        val response = OffProductResponse(
            status = 1,
            product = OffProduct(
                productName = "Hagelslag",
                productNameNl = "Hagelslag Puur",
                servingSize = "15g",
                servingQuantity = 15.0,
                nutriments = OffNutriments(
                    energyKcal100g = 408.0,
                    proteins100g = 5.3,
                    carbohydrates100g = 72.4,
                    fat100g = 11.2,
                ),
            ),
        )
        val repo = BarcodeRepository(api(response))
        val result = repo.lookupBarcode("8710522955")

        assertTrue(result is BarcodeResult.Found)
        val found = result as BarcodeResult.Found
        assertEquals("Hagelslag Puur", found.product.name)
        assertEquals(408, found.product.caloriesPer100g)
        assertEquals(5.3, found.product.proteinPer100g, 0.001)
        assertEquals(72.4, found.product.carbsPer100g, 0.001)
        assertEquals(11.2, found.product.fatPer100g, 0.001)
        assertEquals("15g", found.product.servingName)
        assertEquals(15.0, found.product.servingGrams!!, 0.001)
        assertTrue(found.product.hasCompleteData)
    }

    @Test
    fun `falls back to productName when productNameNl is blank`() = runTest {
        val response = OffProductResponse(
            status = 1,
            product = OffProduct(
                productName = "Stroopwafel",
                productNameNl = "",
                nutriments = OffNutriments(energyKcal100g = 450.0, proteins100g = 5.0, carbohydrates100g = 70.0, fat100g = 17.0),
            ),
        )
        val repo = BarcodeRepository(api(response))
        val result = repo.lookupBarcode("12345") as BarcodeResult.Found
        assertEquals("Stroopwafel", result.product.name)
    }

    @Test
    fun `hasCompleteData is false when a nutriment is missing`() = runTest {
        val response = OffProductResponse(
            status = 1,
            product = OffProduct(
                productName = "Mystery Item",
                productNameNl = "",
                nutriments = OffNutriments(energyKcal100g = null, proteins100g = 5.0, carbohydrates100g = 20.0, fat100g = 3.0),
            ),
        )
        val repo = BarcodeRepository(api(response))
        val result = repo.lookupBarcode("99999") as BarcodeResult.Found
        assertFalse(result.product.hasCompleteData)
    }

    @Test
    fun `returns NotFound when status is 0`() = runTest {
        val response = OffProductResponse(status = 0)
        val repo = BarcodeRepository(api(response))
        assertTrue(repo.lookupBarcode("00000") is BarcodeResult.NotFound)
    }

    @Test
    fun `returns NotFound when product name is blank`() = runTest {
        val response = OffProductResponse(
            status = 1,
            product = OffProduct(productName = "", productNameNl = "", nutriments = OffNutriments()),
        )
        val repo = BarcodeRepository(api(response))
        assertTrue(repo.lookupBarcode("11111") is BarcodeResult.NotFound)
    }

    @Test
    fun `returns NetworkError when api returns null`() = runTest {
        val repo = BarcodeRepository(api(null))
        assertTrue(repo.lookupBarcode("22222") is BarcodeResult.NetworkError)
    }

    @Test
    fun `searchFoods returns list of BarcodeProduct for valid response`() = runTest {
        val response = OffSearchResponse(
            count = 2,
            products = listOf(
                OffSearchProduct(
                    productName = "Hagelslag",
                    productNameNl = "Hagelslag Puur",
                    servingSize = "15g",
                    servingQuantity = 15.0,
                    nutriments = OffNutriments(408.0, 5.3, 72.4, 11.2),
                ),
                OffSearchProduct(
                    productName = "Stroopwafel",
                    productNameNl = "",
                    nutriments = OffNutriments(450.0, 4.5, 68.0, 16.0),
                ),
            ),
        )
        val repo = BarcodeRepository(apiWithSearch(response))
        val results = repo.searchFoods("hagelslag")

        assertEquals(2, results.size)
        assertEquals("Hagelslag Puur", results[0].name)
        assertEquals(408, results[0].caloriesPer100g)
        assertEquals("Stroopwafel", results[1].name)
    }

    @Test
    fun `searchFoods filters out products with blank names`() = runTest {
        val response = OffSearchResponse(
            count = 1,
            products = listOf(OffSearchProduct(productName = "", productNameNl = "")),
        )
        val repo = BarcodeRepository(apiWithSearch(response))
        val results = repo.searchFoods("test")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchFoods returns empty list when api returns null`() = runTest {
        val repo = BarcodeRepository(apiWithSearch(null))
        val results = repo.searchFoods("anything")
        assertTrue(results.isEmpty())
    }
}
