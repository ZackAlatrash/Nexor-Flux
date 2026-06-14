package com.zack.recomptracker.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val webJson = Json { ignoreUnknownKeys = true }

/** A string field's value, or null when the field is absent / not a JSON string. */
private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * Parse a Tavily `/search` response body into a [WebSearchResult]. Returns null when the body is
 * unparseable or carries neither an answer nor any result (treated as "no usable web data").
 */
fun parseTavilyResponse(body: String): WebSearchResult? = try {
    val root = webJson.parseToJsonElement(body) as? JsonObject
    if (root == null) {
        null
    } else {
        val answer = root.str("answer")?.takeIf { it.isNotBlank() }
        val resultsArray = root["results"] as? JsonArray ?: JsonArray(emptyList())
        val results = resultsArray.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val url = o.str("url") ?: return@mapNotNull null
            WebResult(title = o.str("title").orEmpty(), url = url, content = o.str("content").orEmpty())
        }
        if (answer == null && results.isEmpty()) null else WebSearchResult(answer, results)
    }
} catch (_: Exception) {
    null
}

/**
 * Render a [WebSearchResult] as the JSON string handed back to the model. Caps the number of
 * results and the per-result content length so the tool response stays small on a mid-size model.
 */
fun WebSearchResult.toToolJson(maxResults: Int = 3, maxContentChars: Int = 600): String {
    val obj = buildJsonObject {
        answer?.let { put("answer", it) }
        putJsonArray("results") {
            results.take(maxResults).forEach { r ->
                addJsonObject {
                    put("title", r.title)
                    put("url", r.url)
                    put("content", r.content.take(maxContentChars))
                }
            }
        }
    }
    return obj.toString()
}
