package com.zack.recomptracker.data.remote

/** One web result returned to the model. [content] is Tavily's cleaned/extracted snippet. */
data class WebResult(
    val title: String,
    val url: String,
    val content: String,
)

/** A web search outcome: an optional synthesized answer plus supporting results. */
data class WebSearchResult(
    val answer: String?,
    val results: List<WebResult>,
)

/**
 * Searches the public web. Backed in production by [TavilyWebSearchProvider]; faked in tests.
 * Returns null when web search is unavailable (no key configured, offline, or API error) — the
 * single signal the tool layer turns into a structured "unavailable" response.
 */
interface WebSearchProvider {
    suspend fun search(query: String): WebSearchResult?
}
