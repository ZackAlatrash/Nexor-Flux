package com.zack.recomptracker.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses full product response`() {
        val raw = """
            {
              "status": 1,
              "product": {
                "product_name": "Hagelslag",
                "product_name_nl": "Hagelslag Puur",
                "serving_size": "15g",
                "serving_quantity": 15.0,
                "nutriments": {
                  "energy-kcal_100g": 408.0,
                  "proteins_100g": 5.3,
                  "carbohydrates_100g": 72.4,
                  "fat_100g": 11.2
                }
              }
            }
        """.trimIndent()

        val result = json.decodeFromString<OffProductResponse>(raw)

        assertEquals(1, result.status)
        assertEquals("Hagelslag Puur", result.product?.productNameNl)
        assertEquals("Hagelslag", result.product?.productName)
        assertEquals(408.0, result.product?.nutriments?.energyKcal100g!!, 0.001)
        assertEquals(5.3, result.product?.nutriments?.proteins100g!!, 0.001)
        assertEquals(72.4, result.product?.nutriments?.carbohydrates100g!!, 0.001)
        assertEquals(11.2, result.product?.nutriments?.fat100g!!, 0.001)
        assertEquals("15g", result.product?.servingSize)
        assertEquals(15.0, result.product?.servingQuantity!!, 0.001)
    }

    @Test
    fun `parses not-found response`() {
        val raw = """{"status": 0}"""
        val result = json.decodeFromString<OffProductResponse>(raw)
        assertEquals(0, result.status)
        assertNull(result.product)
    }

    @Test
    fun `parses product with missing nutriments gracefully`() {
        val raw = """
            {
              "status": 1,
              "product": {
                "product_name": "Unknown Item",
                "product_name_nl": "",
                "nutriments": {}
              }
            }
        """.trimIndent()

        val result = json.decodeFromString<OffProductResponse>(raw)
        assertNull(result.product?.nutriments?.energyKcal100g)
        assertNull(result.product?.nutriments?.proteins100g)
    }

    @Test
    fun `parses search response with products`() {
        val raw = """
            {
              "count": 2,
              "products": [
                {
                  "product_name": "Hagelslag",
                  "product_name_nl": "Hagelslag Puur",
                  "serving_size": "15g",
                  "serving_quantity": 15.0,
                  "nutriments": {
                    "energy-kcal_100g": 408.0,
                    "proteins_100g": 5.3,
                    "carbohydrates_100g": 72.4,
                    "fat_100g": 11.2
                  }
                },
                {
                  "product_name": "Stroopwafel",
                  "product_name_nl": "",
                  "nutriments": {}
                }
              ]
            }
        """.trimIndent()

        val result = json.decodeFromString<OffSearchResponse>(raw)

        assertEquals(2, result.count)
        assertEquals(2, result.products.size)
        assertEquals("Hagelslag Puur", result.products[0].productNameNl)
        assertEquals(408.0, result.products[0].nutriments.energyKcal100g!!, 0.001)
        assertEquals("Stroopwafel", result.products[1].productName)
        assertNull(result.products[1].nutriments.energyKcal100g)
    }
}
