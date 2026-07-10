package com.zack.recomptracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The auth header reads the API key through [CloudConfig]'s provider at request-build time (review
 * P1-5). The old captured-String field left a rotated key unused until app restart: `cloudConfigFlow`
 * combines on a `hasKey` boolean that conflates true→true, so replacing an existing key never
 * rebuilt the config and the stale key kept being sent.
 */
class OpenAiCompatClientAuthTest {

    private val client = OpenAiCompatClient()

    @Test
    fun `authorization header reflects the current key on every request`() {
        var key = "old-key"
        // Built once and never rebuilt — mirrors cloudConfigFlow not re-firing on a same-presence
        // key replacement.
        val config = CloudConfig(baseUrl = "https://example.test", apiKey = { key }, model = "m")

        assertEquals("Bearer old-key", client.newRequest(config, "{}").header("Authorization"))

        key = "new-key"
        assertEquals(
            "a rotated key is sent on the next request without rebuilding the config",
            "Bearer new-key",
            client.newRequest(config, "{}").header("Authorization"),
        )
    }

    @Test
    fun `request targets the chat completions endpoint with a trimmed base url`() {
        val config = CloudConfig(baseUrl = "https://example.test/", apiKey = { "k" }, model = "m")
        assertEquals(
            "https://example.test/chat/completions",
            client.newRequest(config, "{}").url.toString(),
        )
    }
}
