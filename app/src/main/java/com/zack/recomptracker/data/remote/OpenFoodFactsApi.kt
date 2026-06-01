package com.zack.recomptracker.data.remote

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

open class OpenFoodFactsApi(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    open suspend fun fetchByBarcode(barcode: String): OffProductResponse? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
                .openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "RecompTracker/1.0 (Android; barcode lookup)")
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val responseText = connection.inputStream.bufferedReader().readText()
            json.decodeFromString<OffProductResponse>(responseText)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    open suspend fun searchByName(query: String): OffSearchResponse? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val fields = java.net.URLEncoder.encode(
                "product_name,product_name_nl,nutriments,serving_size,serving_quantity", "UTF-8"
            )
            connection = URL(
                "https://world.openfoodfacts.org/api/v2/search" +
                    "?search_terms=$encoded" +
                    "&countries_tags=en:netherlands" +
                    "&fields=$fields" +
                    "&page_size=20"
            ).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "RecompTracker/1.0 (Android; food search)")
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val responseText = connection.inputStream.bufferedReader().readText()
            json.decodeFromString<OffSearchResponse>(responseText)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
