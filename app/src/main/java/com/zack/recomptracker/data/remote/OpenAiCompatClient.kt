package com.zack.recomptracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Config for one cloud call. [baseUrl] has no trailing slash. [apiKey] is a provider read fresh at
 * request time (not a captured value): replacing the stored key takes effect on the very next
 * request without rebuilding the config, and the plaintext key never lives in this long-lived object
 * or its `toString`. Mirrors [com.zack.recomptracker.data.remote.TavilyWebSearchProvider]'s
 * keyProvider. See review P1-5 — the old captured-String field left a rotated key unused until
 * restart because `cloudConfigFlow` combines on a `hasKey` boolean that conflates true→true.
 */
data class CloudConfig(
    val baseUrl: String,
    val apiKey: () -> String,
    val model: String,
)

/**
 * Minimal OpenAI-compatible client. Two entry points:
 *  - [streamCompletion]: SSE token stream for insight cards.
 *  - [completion]: a single non-streaming completion (with optional tools) for the coach loop.
 *
 * Constructed once and reused (OkHttp pools connections). Per-call config is passed in so a
 * settings change takes effect immediately without rebuilding the client.
 *
 * `open` so tests in other modules can subclass with a fake; production uses the real OkHttp path.
 */
open class OpenAiCompatClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonMedia = "application/json".toMediaType()

    /** Streamed single-turn text generation. Emits text deltas as they arrive. */
    open fun streamCompletion(
        config: CloudConfig,
        systemPrompt: String,
        userPrompt: String,
    ): Flow<String> = flow {
        val bodyJson = buildChatRequestJson(
            model = config.model,
            messages = listOf(
                ChatRequestMessage(role = "system", content = systemPrompt),
                ChatRequestMessage(role = "user", content = userPrompt),
            ),
            toolSchemasJson = emptyList(),
            stream = true,
        )
        val request = newRequest(config, bodyJson)
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty().take(200)
                error("HTTP ${response.code}: $errBody")
            }
            val source = response.body?.source() ?: error("empty response body")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val delta = extractStreamDelta(line)
                if (delta != null) emit(delta)
            }
        }
    }.flowOn(Dispatchers.IO)

    /** One non-streaming completion. [toolSchemasJson] enables tool calling when non-empty. */
    open suspend fun completion(
        config: CloudConfig,
        messages: List<ChatRequestMessage>,
        toolSchemasJson: List<String>,
    ): ParsedChatResponse {
        val bodyJson = buildChatRequestJson(
            model = config.model,
            messages = messages,
            toolSchemasJson = toolSchemasJson,
            stream = false,
        )
        val request = newRequest(config, bodyJson)
        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: ${raw.take(200)}")
                parseChatResponse(raw)
            }
        }
    }

    // internal so the auth-header/key-rotation behavior is unit-testable.
    internal fun newRequest(config: CloudConfig, bodyJson: String): Request =
        Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey()}")
            .post(bodyJson.toRequestBody(jsonMedia))
            .build()
}
