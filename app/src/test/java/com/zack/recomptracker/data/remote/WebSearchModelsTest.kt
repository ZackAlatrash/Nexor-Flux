package com.zack.recomptracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchModelsTest {

    private val tavilyBody = """
        {
          "query": "big mac calories",
          "answer": "A McDonald's Big Mac has about 563 kcal.",
          "results": [
            {"title": "Big Mac", "url": "https://example.com/bigmac", "content": "563 calories, 26g protein."},
            {"title": "Menu", "url": "https://example.com/menu", "content": "Full menu nutrition."}
          ]
        }
    """.trimIndent()

    @Test
    fun `parseTavilyResponse extracts answer and results`() {
        val result = parseTavilyResponse(tavilyBody)!!
        assertEquals("A McDonald's Big Mac has about 563 kcal.", result.answer)
        assertEquals(2, result.results.size)
        assertEquals("https://example.com/bigmac", result.results[0].url)
        assertEquals("Big Mac", result.results[0].title)
    }

    @Test
    fun `parseTavilyResponse returns null for an empty or junk body`() {
        assertNull(parseTavilyResponse(""))
        assertNull(parseTavilyResponse("not json"))
        assertNull(parseTavilyResponse("""{"results":[]}"""))
    }

    @Test
    fun `toToolJson includes answer and caps results and content`() {
        val big = WebSearchResult(
            answer = "short answer",
            results = (1..5).map { WebResult("t$it", "https://u/$it", "x".repeat(2000)) },
        )
        val json = big.toToolJson(maxResults = 3, maxContentChars = 600)
        assertTrue(json.contains("\"answer\":\"short answer\""))
        // Only 3 of 5 results survive the cap.
        assertEquals(3, Regex("\"url\":").findAll(json).count())
        // No single content field exceeds the per-result cap.
        assertTrue(json.split("\"content\":\"").drop(1).all { it.substringBefore("\"").length <= 600 })
    }

    @Test
    fun `toToolJson omits answer when null`() {
        val json = WebSearchResult(answer = null, results = emptyList()).toToolJson()
        assertTrue(!json.contains("\"answer\""))
        assertTrue(json.contains("\"results\":[]"))
    }
}
