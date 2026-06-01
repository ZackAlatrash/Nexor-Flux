package com.zack.recomptracker.data.remote

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class OpenFoodFactsApi(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetchByBarcode(barcode: String): OffProductResponse? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "RecompTracker/1.0 (Android; barcode lookup)")
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val responseText = connection.inputStream.bufferedReader().readText()
            json.decodeFromString<OffProductResponse>(responseText)
        } catch (_: Exception) {
            null
        }
    }
}
