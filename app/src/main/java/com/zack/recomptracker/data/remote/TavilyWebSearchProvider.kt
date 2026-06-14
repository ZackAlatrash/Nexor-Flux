package com.zack.recomptracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * [WebSearchProvider] backed by the Tavily Search API. The key is read per-call via [keyProvider]
 * (so a settings change takes effect immediately), and a blank key short-circuits to null before
 * any network call. Any HTTP/parse failure also yields null — the caller treats null as
 * "web search unavailable". `open` so tests can subclass with a fake.
 */
open class TavilyWebSearchProvider(
    private val keyProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val endpoint: String = "https://api.tavily.com/search",
) : WebSearchProvider {

    private val jsonMedia = "application/json".toMediaType()

    override suspend fun search(query: String): WebSearchResult? {
        val key = keyProvider().trim()
        if (key.isEmpty() || query.isBlank()) return null

        val bodyJson = buildJsonObject {
            put("api_key", key)
            put("query", query.trim())
            put("include_answer", true)
            put("max_results", 3)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toRequestBody(jsonMedia))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    parseTavilyResponse(response.body?.string().orEmpty())
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
