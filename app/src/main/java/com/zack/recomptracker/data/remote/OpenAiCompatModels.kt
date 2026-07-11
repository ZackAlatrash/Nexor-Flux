package com.zack.recomptracker.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Strict parser for server-generated JSON: unknown keys are tolerated but malformed values are not silently coerced. */
private val strictJson = Json { ignoreUnknownKeys = true }

/** Lenient parser used ONLY for model-generated tool-call arguments, which may emit loosely-quoted values or numbers like `500.0`. */
private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** One message in the request `messages` array. [content] may be null for tool-result turns. */
data class ChatRequestMessage(
    val role: String,
    val content: String?,
    /** For role="tool": the id of the tool call this responds to. */
    val toolCallId: String? = null,
    /**
     * For role="assistant" replays that requested tools: the raw assistant tool_calls JSON array string.
     * WARNING: MUST be complete, valid JSON — it is re-parsed via [strictJson] when building a request.
     */
    val assistantToolCallsJson: String? = null,
    /** For role="tool": the function name. */
    val name: String? = null,
)

/** A tool call surfaced by the model. [arguments] values are stringified for CoachToolExecutor. */
data class ParsedToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, String>,
)

/** Parsed non-streaming completion. [text] is "" when the model only requested tools. */
data class ParsedChatResponse(
    val text: String,
    val toolCalls: List<ParsedToolCall>,
)

/**
 * Extract the incremental text from one SSE line of a streamed completion.
 * Returns null for the `[DONE]` sentinel, comments, non-data lines, and deltas with no text.
 */
fun extractStreamDelta(line: String): String? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("data:")) return null
    val payload = trimmed.removePrefix("data:").trim()
    if (payload.isEmpty() || payload == "[DONE]") return null
    return try {
        val choices = strictJson.parseToJsonElement(payload)
            .jsonObject["choices"]?.jsonArray ?: return null
        val delta = choices.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: return null
        delta["content"].messageContentText().takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

/** Parse a full non-streaming `/chat/completions` response body. */
fun parseChatResponse(body: String): ParsedChatResponse {
    val message = strictJson.parseToJsonElement(body)
        .jsonObject["choices"]?.jsonArray
        ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        ?: return ParsedChatResponse(text = "", toolCalls = emptyList())

    val text = message["content"].messageContentText().trim()

    val toolCalls = (message["tool_calls"] as? JsonArray).orEmptyArray().mapNotNull { element ->
        val obj = element.jsonObject
        val function = obj["function"]?.jsonObject ?: return@mapNotNull null
        val name = function["name"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
        val id = obj["id"]?.jsonPrimitive?.contentOrNullSafe() ?: name
        val argsRaw = function["arguments"]?.jsonPrimitive?.contentOrNullSafe() ?: "{}"
        ParsedToolCall(id = id, name = name, arguments = parseArgsToStringMap(argsRaw))
    }
    return ParsedChatResponse(text = text, toolCalls = toolCalls)
}

/** Arguments arrive as a JSON *string*; flatten each top-level value to its string form. */
private fun parseArgsToStringMap(argsRaw: String): Map<String, String> = try {
    lenientJson.parseToJsonElement(argsRaw).jsonObject.mapValues { (_, v) ->
        (v as? JsonPrimitive)?.content ?: v.toString()
    }
} catch (_: Exception) {
    emptyMap()
}

/** Build the request JSON for `/chat/completions`. [toolSchemasJson] are raw `{name,description,parameters}` objects. */
fun buildChatRequestJson(
    model: String,
    messages: List<ChatRequestMessage>,
    toolSchemasJson: List<String>,
    stream: Boolean,
): String {
    val obj = buildJsonObject {
        put("model", model)
        put("stream", stream)
        putJsonArray("messages") {
            messages.forEach { msg ->
                addJsonObject {
                    put("role", msg.role)
                    msg.content?.let { put("content", it) }
                    msg.name?.let { put("name", it) }
                    msg.toolCallId?.let { put("tool_call_id", it) }
                    msg.assistantToolCallsJson?.let {
                        put("tool_calls", strictJson.parseToJsonElement(it))
                    }
                }
            }
        }
        if (toolSchemasJson.isNotEmpty()) {
            putJsonArray("tools") {
                toolSchemasJson.forEach { schema ->
                    addJsonObject {
                        put("type", "function")
                        put("function", strictJson.parseToJsonElement(schema))
                    }
                }
            }
        }
    }
    return obj.toString()
}

/**
 * Safe content accessor for a [JsonPrimitive].
 * - String primitives: returns the unquoted string value.
 * - JSON `null` literal: returns null (so callers treat it as absent content).
 * - Non-string primitives (numbers, booleans): [content] returns the raw unquoted value
 *   (e.g. `"42"` for a JSON number), which is the desired stringification for downstream use.
 */
private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this.isString) content
    else if (this.toString() == "null") null
    else content

/**
 * Text of a message `content` field, which may be a plain string OR an OpenAI content-parts array
 * (e.g. `[{"type":"text","text":"…"}]`). Some OpenAI-compatible routes return the array form; reading
 * it directly as a primitive threw and failed the whole turn with a generic error (review P2-6).
 * Returns "" for null, JSON null, or an unrecognised shape; concatenates the text parts of an array.
 */
private fun JsonElement?.messageContentText(): String = when (this) {
    is JsonPrimitive -> contentOrNullSafe().orEmpty()
    is JsonArray -> joinToString("") { part ->
        ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
    }
    else -> ""
}

private fun JsonArray?.orEmptyArray(): JsonArray = this ?: JsonArray(emptyList())
