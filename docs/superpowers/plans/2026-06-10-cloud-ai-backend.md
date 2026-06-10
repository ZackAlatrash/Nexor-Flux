# Cloud AI Backend (OpenAI-compatible) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a user-selectable OpenAI-compatible cloud AI backend alongside the existing on-device Gemma, so insight cards and the coach chat become richer when cloud is active — without removing or breaking the Gemma path.

**Architecture:** Two new coordinator implementations (`CloudInsightCoordinator`, `CloudCoachCoordinator`) sit behind the *existing* `AiInsightCoordinator` / `CoachCoordinator` interfaces. Two thin router coordinators forward to either the Gemma or Cloud implementation based on an `AiBackend` preference, falling back to LOCAL when cloud config is incomplete. A new `OpenAiCompatClient` (OkHttp) does streaming completions for insights and non-streaming tool-loop completions for the coach. The reused `CoachToolExecutor` is unchanged. ViewModels are untouched because the interfaces don't change.

**Tech Stack:** Kotlin, Coroutines/Flow, OkHttp (new), kotlinx.serialization (existing), AndroidX DataStore (existing), `androidx.security:security-crypto` (new), JUnit4 + mockito-kotlin + kotlinx-coroutines-test (existing).

**Key design decisions (locked from reading the code):**
- The Gemma coach emits its final answer in one shot (no UI streaming). So the **cloud coach uses non-streaming** `/chat/completions` for its tool loop — only **insights stream** via SSE.
- Cloud has no model download/verify lifecycle. `CloudInsightCoordinator`'s download/delete methods are inert; its `state` is `ModelReady` when AI is enabled and config is valid, else `Disabled`.
- The cloud coach's availability is gated by a simple `cloudReady: Flow<Boolean>` (AI enabled + config valid), not by the `AiInsightState` machine.

**Test commands:**
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Type-check: `./gradlew :app:compileDebugKotlin`
- Full debug build: `./gradlew :app:assembleDebug`

---

## File Structure

**Create:**
- `app/src/main/java/com/zack/recomptracker/ai/AiBackend.kt` — `AiBackend` enum + `AiCapabilities` + per-backend capability constants.
- `app/src/main/java/com/zack/recomptracker/data/remote/OpenAiCompatModels.kt` — `@Serializable` request/response DTOs + SSE delta extraction (pure functions).
- `app/src/main/java/com/zack/recomptracker/data/remote/OpenAiCompatClient.kt` — OkHttp client: streaming insight completion + non-streaming coach completion.
- `app/src/main/java/com/zack/recomptracker/data/preferences/SecureKeyStore.kt` — EncryptedSharedPreferences wrapper for the API key, with a reactive `hasKey` flow.
- `app/src/main/java/com/zack/recomptracker/ai/CloudInsightCoordinator.kt` — `AiInsightCoordinator` impl over `OpenAiCompatClient`.
- `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt` — `CoachCoordinator` impl: cloud tool loop + confirmation flow.
- `app/src/main/java/com/zack/recomptracker/ai/RoutingInsightCoordinator.kt` — forwards to Gemma or Cloud insight coordinator.
- `app/src/main/java/com/zack/recomptracker/ai/RoutingCoachCoordinator.kt` — forwards to Gemma or Cloud coach coordinator.
- Tests under `app/src/test/java/com/zack/recomptracker/ai/` and `.../data/remote/`.

**Modify:**
- `gradle/libs.versions.toml` — add OkHttp + security-crypto.
- `app/build.gradle.kts` — wire the two dependencies.
- `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt` — extend `UiPreferences` with backend/baseUrl/modelId keys + a `cloudConfigPresent` flow.
- `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` — add a "rich mode" flag that lifts the "exactly 2–3 sentences" cap for capable backends.
- `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` — build cloud client + cloud coordinators + routers; hand out routers.
- `app/src/main/java/com/zack/recomptracker/ui/more/MoreViewModel.kt` — expose cloud config state + setters + Test Connection.
- `app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt` — backend toggle + base URL / model id / API key fields + Test Connection button.

---

## Task 1: Add dependencies (OkHttp + security-crypto)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:77-125`

- [ ] **Step 1: Add versions + libraries to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` (after line `espresso = "3.7.0"`), add:

```toml
okhttp = "4.12.0"
securityCrypto = "1.1.0-alpha06"
```

Under `[libraries]` (after the `espresso-core` line), add:

```toml
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
```

- [ ] **Step 2: Wire dependencies in the module build file**

In `app/build.gradle.kts`, inside the `dependencies { }` block, after line 107 (`implementation(libs.reorderable)`), add:

```kotlin
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)
```

- [ ] **Step 3: Verify the build resolves the new dependencies**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. (No code uses the libraries yet; this only confirms they resolve.)

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add OkHttp and security-crypto for cloud AI backend"
```

---

## Task 2: `AiBackend` + `AiCapabilities`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/AiBackend.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/AiCapabilitiesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/AiCapabilitiesTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCapabilitiesTest {

    @Test
    fun `local backend has minimal capabilities`() {
        val caps = AiCapabilities.of(AiBackend.LOCAL)
        assertFalse(caps.richInsights)
        assertFalse(caps.longContext)
        assertFalse(caps.unboundedToolLoop)
        assertFalse(caps.proactiveReview)
    }

    @Test
    fun `cloud backend unlocks tier-1 capabilities but not tier-2`() {
        val caps = AiCapabilities.of(AiBackend.CLOUD)
        assertTrue(caps.richInsights)
        assertTrue(caps.longContext)
        assertTrue(caps.unboundedToolLoop)
        assertFalse(caps.proactiveReview) // Tier 2 — deferred
    }

    @Test
    fun `backend parses from stored name with local fallback`() {
        assertEquals(AiBackend.CLOUD, AiBackend.fromStored("CLOUD"))
        assertEquals(AiBackend.LOCAL, AiBackend.fromStored("LOCAL"))
        assertEquals(AiBackend.LOCAL, AiBackend.fromStored(null))
        assertEquals(AiBackend.LOCAL, AiBackend.fromStored("garbage"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.AiCapabilitiesTest"`
Expected: FAIL — `AiBackend` / `AiCapabilities` unresolved (compilation error).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/ai/AiBackend.kt`:

```kotlin
package com.zack.recomptracker.ai

/** Which engine powers the AI features. */
enum class AiBackend {
    LOCAL,
    CLOUD;

    companion object {
        /** Parse a stored preference name, defaulting to [LOCAL] for null/unknown values. */
        fun fromStored(name: String?): AiBackend =
            entries.firstOrNull { it.name == name } ?: LOCAL
    }
}

/**
 * What a backend is allowed to do. Coordinators and UI branch on these flags, never on
 * `backend == CLOUD` directly — so Tier-2 features (e.g. [proactiveReview]) can be switched
 * on later without re-architecting.
 */
data class AiCapabilities(
    val richInsights: Boolean,
    val longContext: Boolean,
    val unboundedToolLoop: Boolean,
    val proactiveReview: Boolean,
) {
    companion object {
        fun of(backend: AiBackend): AiCapabilities = when (backend) {
            AiBackend.LOCAL -> AiCapabilities(
                richInsights = false,
                longContext = false,
                unboundedToolLoop = false,
                proactiveReview = false,
            )
            AiBackend.CLOUD -> AiCapabilities(
                richInsights = true,
                longContext = true,
                unboundedToolLoop = true,
                proactiveReview = false, // Tier 2 — deferred to a follow-up spec.
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.AiCapabilitiesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/AiBackend.kt app/src/test/java/com/zack/recomptracker/ai/AiCapabilitiesTest.kt
git commit -m "feat(ai): add AiBackend and AiCapabilities"
```

---

## Task 3: Cloud request/response models + SSE delta parsing

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/remote/OpenAiCompatModels.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/remote/OpenAiCompatModelsTest.kt`

These are pure data + pure functions — the most important TDD target. The client (Task 4) is a thin wrapper around them.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/data/remote/OpenAiCompatModelsTest.kt`:

```kotlin
package com.zack.recomptracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatModelsTest {

    @Test
    fun `extractStreamDelta returns content from a data line`() {
        val line =
            """data: {"choices":[{"delta":{"content":"Hello"}}]}"""
        assertEquals("Hello", extractStreamDelta(line))
    }

    @Test
    fun `extractStreamDelta returns null for the DONE sentinel`() {
        assertNull(extractStreamDelta("data: [DONE]"))
    }

    @Test
    fun `extractStreamDelta returns null for blank or non-data lines`() {
        assertNull(extractStreamDelta(""))
        assertNull(extractStreamDelta(":keep-alive comment"))
        assertNull(extractStreamDelta("event: ping"))
    }

    @Test
    fun `extractStreamDelta returns null when delta has no content`() {
        // role-only opening delta and tool-call deltas carry no text content
        assertNull(extractStreamDelta("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
    }

    @Test
    fun `parseChatResponse extracts plain text content`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"You are at 1420 kcal."}}]}"""
        val parsed = parseChatResponse(body)
        assertEquals("You are at 1420 kcal.", parsed.text)
        assertTrue(parsed.toolCalls.isEmpty())
    }

    @Test
    fun `parseChatResponse extracts tool calls with arguments`() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":null,
            "tool_calls":[{"id":"call_1","type":"function",
            "function":{"name":"log_meal","arguments":"{\"name\":\"banana\",\"grams\":120}"}}]}}]}
        """.trimIndent()
        val parsed = parseChatResponse(body)
        assertEquals("", parsed.text)
        assertEquals(1, parsed.toolCalls.size)
        val call = parsed.toolCalls.first()
        assertEquals("call_1", call.id)
        assertEquals("log_meal", call.name)
        assertEquals("banana", call.arguments["name"])
        // numeric JSON args are surfaced as strings for CoachToolExecutor
        assertEquals("120", call.arguments["grams"])
    }

    @Test
    fun `buildChatRequestJson includes model, messages, tools and stream flag`() {
        val json = buildChatRequestJson(
            model = "openai/gpt-4o-mini",
            messages = listOf(ChatRequestMessage(role = "user", content = "hi")),
            toolSchemasJson = listOf("""{"name":"get_weekly_trends","description":"d","parameters":{"type":"object","properties":{}}}"""),
            stream = false,
        )
        assertTrue(json.contains("\"model\":\"openai/gpt-4o-mini\""))
        assertTrue(json.contains("\"stream\":false"))
        assertTrue(json.contains("\"role\":\"user\""))
        // tool schema is wrapped as {"type":"function","function":{...}}
        assertTrue(json.contains("\"type\":\"function\""))
        assertTrue(json.contains("get_weekly_trends"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.remote.OpenAiCompatModelsTest"`
Expected: FAIL — functions/types unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/data/remote/OpenAiCompatModels.kt`:

```kotlin
package com.zack.recomptracker.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** One message in the request `messages` array. [content] may be null for tool-result turns. */
data class ChatRequestMessage(
    val role: String,
    val content: String?,
    /** For role="tool": the id of the tool call this responds to. */
    val toolCallId: String? = null,
    /** For role="assistant" replays that requested tools: the raw assistant tool_calls JSON array string. */
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
        val choices = lenientJson.parseToJsonElement(payload)
            .jsonObject["choices"]?.jsonArray ?: return null
        val delta = choices.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: return null
        val content = delta["content"]?.jsonPrimitive?.contentOrNullSafe()
        content?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

/** Parse a full non-streaming `/chat/completions` response body. */
fun parseChatResponse(body: String): ParsedChatResponse {
    val message = lenientJson.parseToJsonElement(body)
        .jsonObject["choices"]?.jsonArray
        ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        ?: return ParsedChatResponse(text = "", toolCalls = emptyList())

    val text = message["content"]?.jsonPrimitive?.contentOrNullSafe()?.trim().orEmpty()

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
                        put("tool_calls", lenientJson.parseToJsonElement(it))
                    }
                }
            }
        }
        if (toolSchemasJson.isNotEmpty()) {
            putJsonArray("tools") {
                toolSchemasJson.forEach { schema ->
                    addJsonObject {
                        put("type", "function")
                        put("function", lenientJson.parseToJsonElement(schema))
                    }
                }
            }
        }
    }
    return obj.toString()
}

private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is JsonPrimitive && this.isString) content
    else if (this.toString() == "null") null
    else content

private fun JsonArray?.orEmptyArray(): JsonArray = this ?: JsonArray(emptyList())
```

> Note: `kotlinx.serialization.json` DSL (`buildJsonObject`, `putJsonArray`, etc.) is available via the existing `kotlinx-serialization-json` dependency.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.remote.OpenAiCompatModelsTest"`
Expected: PASS. If `contentOrNullSafe` mishandles a case, fix until green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/remote/OpenAiCompatModels.kt app/src/test/java/com/zack/recomptracker/data/remote/OpenAiCompatModelsTest.kt
git commit -m "feat(remote): add OpenAI-compatible request/response models + SSE parsing"
```

---

## Task 4: `OpenAiCompatClient` (OkHttp)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/remote/OpenAiCompatClient.kt`

Networking is verified by build + manual run (no MockWebServer dependency added). Parsing — the risky part — is already covered by Task 3.

- [ ] **Step 1: Write the client**

Create `app/src/main/java/com/zack/recomptracker/data/remote/OpenAiCompatClient.kt`:

```kotlin
package com.zack.recomptracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Config snapshot for one cloud call. [baseUrl] has no trailing slash. */
data class CloudConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/**
 * Minimal OpenAI-compatible client. Two entry points:
 *  - [streamCompletion]: SSE token stream for insight cards.
 *  - [completion]: a single non-streaming completion (with optional tools) for the coach loop.
 *
 * Constructed once and reused (OkHttp pools connections). Per-call config is passed in so a
 * settings change takes effect immediately without rebuilding the client.
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
            if (!response.isSuccessful) error("HTTP ${response.code}")
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
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: ${raw.take(200)}")
                parseChatResponse(raw)
            }
        }
    }

    private fun newRequest(config: CloudConfig, bodyJson: String): Request =
        Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMedia))
            .build()
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/remote/OpenAiCompatClient.kt
git commit -m "feat(remote): add OpenAiCompatClient (OkHttp streaming + tool completions)"
```

---

## Task 5: Cloud config preferences + `SecureKeyStore`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt:78-141`
- Create: `app/src/main/java/com/zack/recomptracker/data/preferences/SecureKeyStore.kt`

DataStore + EncryptedSharedPreferences are Android-Context-bound, so these are verified by build + the manual run in Task 11 (not unit-tested), matching the codebase's existing (untested) preference classes.

- [ ] **Step 1: Add cloud keys, flows, and setters to `UiPreferences`**

In `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt`, add this import near the top (after line 11 `import com.zack.recomptracker.ai.ModelVariant`):

```kotlin
import com.zack.recomptracker.ai.AiBackend
```

Inside `class UiPreferences`, after the `selectedModelVariant` flow (line 98), add:

```kotlin
    val aiBackend: kotlinx.coroutines.flow.Flow<AiBackend> =
        context.uiDataStore.data.map { AiBackend.fromStored(it[Keys.AiBackend]) }

    val cloudBaseUrl: kotlinx.coroutines.flow.Flow<String> =
        context.uiDataStore.data.map { it[Keys.CloudBaseUrl] ?: "" }

    val cloudModelId: kotlinx.coroutines.flow.Flow<String> =
        context.uiDataStore.data.map { it[Keys.CloudModelId] ?: "" }

    /** True when base URL and model id are both set (API-key presence is tracked by SecureKeyStore). */
    val cloudConfigPresent: kotlinx.coroutines.flow.Flow<Boolean> =
        context.uiDataStore.data.map {
            !(it[Keys.CloudBaseUrl].isNullOrBlank()) && !(it[Keys.CloudModelId].isNullOrBlank())
        }
```

After the `setSelectedModel` function (line 132), add:

```kotlin
    suspend fun setAiBackend(backend: AiBackend) {
        context.uiDataStore.edit { it[Keys.AiBackend] = backend.name }
    }

    suspend fun setCloudBaseUrl(url: String) {
        context.uiDataStore.edit { it[Keys.CloudBaseUrl] = url.trim() }
    }

    suspend fun setCloudModelId(model: String) {
        context.uiDataStore.edit { it[Keys.CloudModelId] = model.trim() }
    }
```

Inside `private object Keys` of `UiPreferences` (after line 139 `val AccentTheme = ...`), add:

```kotlin
        val AiBackend = stringPreferencesKey("ai_backend")
        val CloudBaseUrl = stringPreferencesKey("cloud_base_url")
        val CloudModelId = stringPreferencesKey("cloud_model_id")
```

- [ ] **Step 2: Create the encrypted key store**

Create `app/src/main/java/com/zack/recomptracker/data/preferences/SecureKeyStore.kt`:

```kotlin
package com.zack.recomptracker.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the single cloud API key in EncryptedSharedPreferences (AES via the Android Keystore).
 * Base URL and model id are NOT secrets and live in [UiPreferences].
 *
 * [hasKey] is a reactive flow so the routing/coach layers can react to the key being set or
 * cleared without a blocking read.
 */
class SecureKeyStore(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "secure_ai_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _hasKey = MutableStateFlow(getApiKey().isNotBlank())
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    fun getApiKey(): String = prefs.getString(KEY_API, "").orEmpty()

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API, value.trim()).apply()
        _hasKey.value = value.isNotBlank()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API).apply()
        _hasKey.value = false
    }

    private companion object {
        const val KEY_API = "cloud_api_key"
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt app/src/main/java/com/zack/recomptracker/data/preferences/SecureKeyStore.kt
git commit -m "feat(prefs): add cloud backend config + encrypted API key store"
```

---

## Task 6: `InsightPromptBuilder` rich mode

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt:39-91`
- Test: `app/src/test/java/com/zack/recomptracker/ai/InsightPromptBuilderRichModeTest.kt`

The cloud insight path reuses the existing prompt builders but passes `rich = true` to lift the "exactly 2–3 sentences" cap and ask for cross-signal depth.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/InsightPromptBuilderRichModeTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightPromptBuilderRichModeTest {

    private val builder = InsightPromptBuilder()

    private fun recoveryContext() = RecoveryInsightContext(
        sleepHours = 6.0,
        energyScore = 4,
        hungerScore = 5,
        sorenessScore = 7,
        trained = true,
    )

    @Test
    fun `default recovery prompt keeps the concise sentence cap`() {
        val prompt = builder.buildRecoveryReadinessPrompt(recoveryContext(), rich = false)
        assertTrue(prompt.contains("exactly 2–3 sentences"))
    }

    @Test
    fun `rich recovery prompt removes the concise cap and asks for depth`() {
        val prompt = builder.buildRecoveryReadinessPrompt(recoveryContext(), rich = true)
        assertFalse(prompt.contains("exactly 2–3 sentences"))
        assertTrue(prompt.contains("cross-signal"))
    }
}
```

> Confirm `RecoveryInsightContext`'s constructor parameter names by opening `app/src/main/java/com/zack/recomptracker/ai/RecoveryInsightContext.kt` before running — adjust the test's named args if they differ. (The five fields used in `buildRecoveryReadinessPrompt` are `sleepHours`, `energyScore`, `hungerScore`, `sorenessScore`, `trained`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightPromptBuilderRichModeTest"`
Expected: FAIL — `buildRecoveryReadinessPrompt` has no `rich` parameter.

- [ ] **Step 3: Add a `rich` parameter to the three insight prompt builders**

In `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt`, change the three public insight builders to accept `rich: Boolean = false` and branch the instruction lines. Replace the **header lines** of each:

For `buildProgressTrendPrompt` (line 39), change the signature and first instruction block:

```kotlin
    fun buildProgressTrendPrompt(context: ProgressInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a body-recomposition coach interpreting an athlete's progress trends.")
        if (rich) {
            appendLine("Write a thorough, cross-signal interpretation (4–6 sentences) of what the combination of trends means for body recomposition. Connect the signals to each other; call out tension or agreement between weight, waist, lifts, and adherence.")
        } else {
            appendLine("Write exactly 2–3 sentences in plain English explaining what the combination of trends means for body recomposition.")
        }
        appendLine("Do NOT recommend changing calories or macros — that decision is made elsewhere. Interpret the trend only.")
        appendLine("Base everything only on the signals below. Do not invent data.")
```

For `buildRecoveryReadinessPrompt` (line 56):

```kotlin
    fun buildRecoveryReadinessPrompt(context: RecoveryInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a training-recovery coach.")
        if (rich) {
            appendLine("Write a thorough, cross-signal readiness assessment (4–6 sentences) for the athlete today. Relate sleep, energy, hunger, and soreness to each other and to whether they trained.")
        } else {
            appendLine("Write exactly 2–3 sentences in plain English about the athlete's training readiness today.")
        }
        appendLine("Give practical training and recovery suggestions only. Do NOT give medical advice or diagnose anything.")
        appendLine("Base everything only on the signals below. Do not invent data.")
```

For `buildRestOfDayPrompt` (line 73):

```kotlin
    fun buildRestOfDayPrompt(context: RestOfDayInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a nutrition coach advising an athlete on the rest of their day.")
        if (rich) {
            appendLine("Write a thorough, cross-signal plan (4–6 sentences): state where they stand, frame the remaining gap, and connect it to meal timing and protein distribution for the rest of the day.")
        } else {
            appendLine("Write exactly 2–3 sentences in plain English: state where they stand and what to prioritize for the remaining meals.")
        }
        appendLine("Do NOT invent specific foods, brands, or macro numbers beyond what is given. Frame the gap and give general guidance.")
        appendLine("Base everything only on the numbers below.")
```

Leave every other line of these functions (the example output, the signal lines) unchanged.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightPromptBuilderRichModeTest"`
Expected: PASS.

- [ ] **Step 5: Run the full insight prompt suite to confirm no regression**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.*"`
Expected: PASS (existing callers still compile — `rich` defaults to false).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/InsightPromptBuilderRichModeTest.kt
git commit -m "feat(ai): add rich-mode insight prompts for capable backends"
```

---

## Task 7: `CloudInsightCoordinator`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/CloudInsightCoordinator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CloudInsightCoordinatorTest.kt`

Implements `AiInsightCoordinator`. Model-lifecycle methods are inert (cloud has no download). `state` is `ModelReady` when AI enabled + config valid, else `Disabled`. Insight generation streams from a `CloudConfigProvider` + `OpenAiCompatClient`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/CloudInsightCoordinatorTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudInsightCoordinatorTest {

    private fun progressRequest() = InsightRequest.ProgressTrend(
        ProgressInsightContext(
            rangeDays = 28,
            weightTrendKgPerWeek = 0.0,
            waistTrendCmPerWeek = -0.3,
            liftTrendKgPerWeek = 0.5,
            adherencePercent = 90.0,
        ),
    )

    private class FakeClient(private val chunks: List<String>) : OpenAiCompatClient() {
        override fun streamCompletion(config: CloudConfig, systemPrompt: String, userPrompt: String): Flow<String> =
            flow { chunks.forEach { emit(it) } }
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse = ParsedChatResponse("", emptyList())
    }

    private fun config() = MutableStateFlow<CloudConfig?>(
        CloudConfig(baseUrl = "https://x", apiKey = "k", model = "m"),
    )

    @Test
    fun `state is ModelReady when enabled and configured`() = runTest {
        val coordinator = CloudInsightCoordinator(
            aiEnabledFlow = flowOf(true),
            configFlow = config(),
            client = FakeClient(emptyList()),
            scope = backgroundScope,
        )
        advanceUntilIdle()
        assertEquals(AiInsightState.ModelReady, coordinator.state.value)
    }

    @Test
    fun `onInsightVisible streams to Ready with the accumulated text`() = runTest {
        val coordinator = CloudInsightCoordinator(
            aiEnabledFlow = flowOf(true),
            configFlow = config(),
            client = FakeClient(listOf("Weight held ", "while waist fell.")),
            scope = backgroundScope,
        )
        advanceUntilIdle()
        coordinator.onInsightVisible(progressRequest())
        advanceUntilIdle()
        val state = coordinator.generationState(InsightKind.PROGRESS_TREND).value
        assertTrue(state is AiInsightState.Ready)
        assertEquals("Weight held while waist fell.", (state as AiInsightState.Ready).text)
    }
}
```

> Confirm `ProgressInsightContext` field names in `app/src/main/java/com/zack/recomptracker/ai/ProgressInsightContext.kt` and adjust named args if needed.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CloudInsightCoordinatorTest"`
Expected: FAIL — `CloudInsightCoordinator` unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/zack/recomptracker/ai/CloudInsightCoordinator.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * [AiInsightCoordinator] backed by an OpenAI-compatible cloud model.
 *
 * Cloud has no model download/verify lifecycle, so [requestDownload], [cancelDownload],
 * [deleteModel], and [setSelectedModel] are inert. [state] reflects readiness only:
 * [AiInsightState.ModelReady] when AI is enabled AND a [CloudConfig] is available,
 * otherwise [AiInsightState.Disabled].
 */
class CloudInsightCoordinator(
    aiEnabledFlow: Flow<Boolean>,
    private val configFlow: StateFlow<CloudConfig?>,
    private val client: OpenAiCompatClient,
    private val scope: CoroutineScope,
) : AiInsightCoordinator {

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
    override val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private val _selectedModel = MutableStateFlow(ModelVariant.GEMMA_2B)
    override val selectedModel: StateFlow<ModelVariant> = _selectedModel.asStateFlow()

    private val promptBuilder = InsightPromptBuilder()
    private val capabilities = AiCapabilities.of(AiBackend.CLOUD)

    private val insightStates: Map<InsightKind, MutableStateFlow<AiInsightState>> =
        InsightKind.entries.associateWith { MutableStateFlow<AiInsightState>(AiInsightState.ModelReady) }
    private val lastInsightKeys = java.util.concurrent.ConcurrentHashMap<InsightKind, String>()

    init {
        scope.launch {
            combine(aiEnabledFlow, configFlow) { enabled, config -> enabled to config }
                .collect { (enabled, config) ->
                    _state.value = if (enabled && config != null) {
                        AiInsightState.ModelReady
                    } else {
                        AiInsightState.Disabled
                    }
                }
        }
    }

    // Cloud has no on-device model lifecycle — these are intentionally inert.
    override fun setSelectedModel(variant: ModelVariant) { _selectedModel.value = variant }
    override fun requestDownload() {}
    override fun cancelDownload() {}
    override fun deleteModel() {}

    // The weekly verdict card uses the same channel as the insights below.
    override fun onAiCardVisible(context: InsightContext) {
        if (context.result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value != AiInsightState.ModelReady) return
        // The weekly card uses the main `state` flow.
        scope.launch { streamInto(_state, promptBuilder.buildWeeklySummaryPrompt(context)) }
    }

    override fun retryGeneration(context: InsightContext) {
        if (_state.value !is AiInsightState.Ready && _state.value != AiInsightState.ModelReady && _state.value !is AiInsightState.Error) return
        _state.value = AiInsightState.ModelReady
        onAiCardVisible(context)
    }

    override fun generationState(kind: InsightKind): StateFlow<AiInsightState> =
        insightStates.getValue(kind).asStateFlow()

    override fun onInsightVisible(request: InsightRequest) {
        if (!request.hasSufficientData) return
        val flow = insightStates.getValue(request.kind)
        if (_state.value != AiInsightState.ModelReady) {
            flow.value = _state.value
            return
        }
        val key = request.dedupKey()
        if (lastInsightKeys[request.kind] == key) return
        lastInsightKeys[request.kind] = key
        val prompt = when (request) {
            is InsightRequest.ProgressTrend -> promptBuilder.buildProgressTrendPrompt(request.context, rich = capabilities.richInsights)
            is InsightRequest.RecoveryReadiness -> promptBuilder.buildRecoveryReadinessPrompt(request.context, rich = capabilities.richInsights)
            is InsightRequest.RestOfDay -> promptBuilder.buildRestOfDayPrompt(request.context, rich = capabilities.richInsights)
        }
        scope.launch { streamInto(flow, prompt) }
    }

    override fun retryInsight(request: InsightRequest) {
        lastInsightKeys.remove(request.kind)
        insightStates.getValue(request.kind).value = AiInsightState.ModelReady
        onInsightVisible(request)
    }

    private suspend fun streamInto(flow: MutableStateFlow<AiInsightState>, prompt: String) {
        val config = configFlow.value ?: run {
            flow.value = AiInsightState.Error("Cloud AI not configured.")
            return
        }
        flow.value = AiInsightState.LoadingModel
        try {
            flow.value = AiInsightState.Generating("")
            val sb = StringBuilder()
            withTimeout(GENERATION_TIMEOUT_MS) {
                client.streamCompletion(
                    config = config,
                    systemPrompt = SYSTEM_PROMPT,
                    userPrompt = prompt,
                ).collect { chunk ->
                    sb.append(chunk)
                    flow.value = AiInsightState.Generating(sb.toString())
                }
            }
            if (flow.value is AiInsightState.Generating) {
                flow.value = AiInsightState.Ready(sb.toString().trim())
            }
        } catch (e: TimeoutCancellationException) {
            flow.value = AiInsightState.Error("Took too long — try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            flow.value = AiInsightState.Error("Cloud request failed — check your settings.")
        }
    }

    private companion object {
        private const val GENERATION_TIMEOUT_MS = 60_000L
        private const val SYSTEM_PROMPT =
            "You are a precise, supportive body-recomposition coach. Answer only from the data you are given."
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CloudInsightCoordinatorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CloudInsightCoordinator.kt app/src/test/java/com/zack/recomptracker/ai/CloudInsightCoordinatorTest.kt
git commit -m "feat(ai): add CloudInsightCoordinator"
```

---

## Task 8: `CloudCoachCoordinator`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorTest.kt`

Implements `CoachCoordinator`. Non-streaming tool loop reusing `CoachToolExecutor` and `COACH_TOOLS` schemas (read via a small accessor). Same `AwaitingConfirmation` flow for `WRITE_TOOLS`. No turn/iteration caps (capability `unboundedToolLoop`), but a hard safety ceiling prevents infinite loops.

- [ ] **Step 1: Expose the coach tool schemas to the cloud coordinator**

`COACH_TOOLS` is `private` inside `GemmaCoachCoordinator`'s companion as `OpenApiTool` objects. The cloud coach needs the same schemas as raw JSON strings. Add a shared top-level constant so both coaches use one source of truth.

In `app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt`, just below the imports and above the class declaration (before line 51 `class GemmaCoachCoordinator`), add:

```kotlin
/**
 * Tool schemas shared by both the Gemma and cloud coaches. Each entry is a raw JSON object
 * `{"name":..,"description":..,"parameters":..}`. The Gemma path wraps these in [SchemaTool];
 * the cloud path sends them as OpenAI `tools` entries.
 */
val COACH_TOOL_SCHEMAS: List<String> = listOf(
    """{"name":"get_today_summary","description":"Get a specific day's food log, macro totals, and daily metrics. Omit 'date' for today.","parameters":{"type":"object","properties":{"date":{"type":"string","description":"ISO date YYYY-MM-DD. Omit for today."}},"required":[]}}""",
    """{"name":"get_weekly_trends","description":"Get last 7 days of daily macro totals (calories, protein, carbs, fat) and adherence percent. Use this for weekly trends or any multi-day macro question.","parameters":{"type":"object","properties":{},"required":[]}}""",
    """{"name":"search_food_library","description":"Search your saved food library by name. If the user specified a weight in grams, pass it as 'grams' and the tool returns macros already scaled to that weight — use those directly in log_meal.","parameters":{"type":"object","properties":{"query":{"type":"string","description":"Food name only — no quantities or weights"},"grams":{"type":"number","description":"Optional: weight in grams requested by the user. If provided, returned macros are pre-scaled to this weight."}},"required":["query"]}}""",
    """{"name":"log_meal","description":"Add a meal to today's food log. The tool looks up your food library automatically and uses the correct macros. Pass grams if the user specified a weight. If the food is NOT in the library, you MUST also provide calories, protein_g, carbs_g, and fat_g.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Food name"},"grams":{"type":"number","description":"Optional: weight in grams. Macros are scaled automatically if food is in library."},"meal_type":{"type":"string","description":"One of: Breakfast, Lunch, Dinner, Snack. Default: Snack"},"calories":{"type":"integer","description":"Required only if food is NOT in your library. Omit for library foods."},"protein_g":{"type":"number","description":"Required only if food is NOT in your library."},"carbs_g":{"type":"number","description":"Required only if food is NOT in your library."},"fat_g":{"type":"number","description":"Required only if food is NOT in your library."}},"required":["name"]}}""",
    """{"name":"log_metric","description":"Record a body or recovery metric for today.","parameters":{"type":"object","properties":{"metric":{"type":"string","description":"One of: weight_kg, waist_cm, sleep_hours, energy_score, hunger_score, soreness_score"},"value":{"type":"number","description":"The numeric value to record"}},"required":["metric","value"]}}""",
    """{"name":"update_calorie_target","description":"Update the daily calorie target. Value must be between 500 and 6000.","parameters":{"type":"object","properties":{"target_calories":{"type":"integer","description":"New daily calorie target in kcal (500–6000)"}},"required":["target_calories"]}}""",
)

/** Tool names that mutate user data and therefore require explicit confirmation. */
val COACH_WRITE_TOOLS: Set<String> = setOf("log_meal", "log_metric", "update_calorie_target")
```

This is additive; it does not change `GemmaCoachCoordinator`'s existing private `COACH_TOOLS`/`WRITE_TOOLS`. (A later cleanup could have Gemma reuse these, but that is out of scope here.)

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import com.zack.recomptracker.data.remote.ParsedToolCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCoachCoordinatorTest {

    private fun config() = MutableStateFlow<CloudConfig?>(
        CloudConfig(baseUrl = "https://x", apiKey = "k", model = "m"),
    )

    /** Scripted client: returns queued responses in order, recording the messages it received. */
    private class ScriptedClient(private val responses: ArrayDeque<ParsedChatResponse>) : OpenAiCompatClient() {
        var lastToolSchemas: List<String> = emptyList()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            lastToolSchemas = toolSchemasJson
            return responses.removeFirst()
        }
    }

    /** Fake executor returning canned JSON; records calls. */
    private class FakeExecutor : CoachReadTools {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun execute(name: String, args: Map<String, String>): String {
            calls += name to args
            return """{"ok":true}"""
        }
        override suspend fun systemPromptSnapshot(): String = "SYSTEM PROMPT"
    }

    @Test
    fun `read-only question answers from a single completion`() = runTest {
        val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("You are at 1500 kcal.", emptyList()))))
        val executor = FakeExecutor()
        val coach = CloudCoachCoordinator(
            cloudReadyFlow = flowOf(true),
            configFlow = config(),
            client = client,
            tools = executor,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        coach.sendMessage("how many calories today?")
        advanceUntilIdle()
        val state = coach.state.value
        assertTrue(state is CoachState.Idle)
        assertEquals("You are at 1500 kcal.", (state as CoachState.Idle).history.last().text)
        assertTrue(client.lastToolSchemas.isNotEmpty()) // tools advertised
    }

    @Test
    fun `read tool runs without confirmation then answers`() = runTest {
        val responses = ArrayDeque(
            listOf(
                ParsedChatResponse("", listOf(ParsedToolCall("c1", "get_weekly_trends", emptyMap()))),
                ParsedChatResponse("Adherence was 86%.", emptyList()),
            ),
        )
        val client = ScriptedClient(responses)
        val executor = FakeExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, executor, backgroundScope)
        advanceUntilIdle()
        coach.sendMessage("how was my week?")
        advanceUntilIdle()
        assertEquals(listOf("get_weekly_trends"), executor.calls.map { it.first })
        assertTrue(coach.state.value is CoachState.Idle)
    }

    @Test
    fun `write tool pauses for confirmation then executes on confirm`() = runTest {
        val responses = ArrayDeque(
            listOf(
                ParsedChatResponse("", listOf(ParsedToolCall("c1", "log_metric", mapOf("metric" to "weight_kg", "value" to "80")))),
                ParsedChatResponse("Logged your weight.", emptyList()),
            ),
        )
        val client = ScriptedClient(responses)
        val executor = FakeExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, executor, backgroundScope)
        advanceUntilIdle()
        coach.sendMessage("log my weight 80")
        advanceUntilIdle()
        assertTrue(coach.state.value is CoachState.AwaitingConfirmation)
        coach.confirmPendingAction()
        advanceUntilIdle()
        assertEquals(listOf("log_metric"), executor.calls.map { it.first })
        assertTrue(coach.state.value is CoachState.Idle)
    }

    @Test
    fun `write tool cancellation skips execution`() = runTest {
        val responses = ArrayDeque(
            listOf(
                ParsedChatResponse("", listOf(ParsedToolCall("c1", "log_metric", mapOf("metric" to "weight_kg", "value" to "80")))),
                ParsedChatResponse("Okay, I didn't log it.", emptyList()),
            ),
        )
        val client = ScriptedClient(responses)
        val executor = FakeExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, executor, backgroundScope)
        advanceUntilIdle()
        coach.sendMessage("log my weight 80")
        advanceUntilIdle()
        coach.cancelPendingAction()
        advanceUntilIdle()
        assertTrue(executor.calls.isEmpty())
        assertTrue(coach.state.value is CoachState.Idle)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CloudCoachCoordinatorTest"`
Expected: FAIL — `CloudCoachCoordinator` and `CoachReadTools` unresolved.

- [ ] **Step 4: Write the implementation**

Create `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Abstraction over the coach's data tools so [CloudCoachCoordinator] is unit-testable without
 * Room. Backed in production by [CoachToolExecutor] + the coach system-prompt builder.
 */
interface CoachReadTools {
    suspend fun execute(name: String, args: Map<String, String>): String
    /** The full system prompt (plan, profile, today's snapshot, rules) for a new conversation. */
    suspend fun systemPromptSnapshot(): String
}

/**
 * [CoachCoordinator] backed by an OpenAI-compatible cloud model. Non-streaming tool loop:
 * send messages → if the model requests tools, run them (confirming WRITE_TOOLS) → resend
 * with tool results → repeat until the model returns text.
 *
 * No per-turn or per-conversation caps (capability `unboundedToolLoop`), but [MAX_TOOL_ROUNDS]
 * is a hard safety ceiling against a runaway loop.
 */
class CloudCoachCoordinator(
    cloudReadyFlow: Flow<Boolean>,
    private val configFlow: StateFlow<CloudConfig?>,
    private val client: OpenAiCompatClient,
    private val tools: CoachReadTools,
    private val scope: CoroutineScope,
) : CoachCoordinator {

    private val _state = MutableStateFlow<CoachState>(CoachState.Unavailable)
    override val state: StateFlow<CoachState> = _state.asStateFlow()

    private val history = mutableListOf<ChatMessage>()
    private val turnLock = Mutex()
    private val requestMessages = mutableListOf<ChatRequestMessage>()
    private var systemSeeded = false

    @Volatile private var pendingConfirmation: CompletableDeferred<Boolean>? = null

    init {
        scope.launch {
            cloudReadyFlow.collect { ready ->
                if (!ready) {
                    _state.value = CoachState.Unavailable
                } else if (_state.value == CoachState.Unavailable) {
                    _state.value = if (history.isEmpty()) CoachState.Ready else CoachState.Idle(history.toList())
                }
            }
        }
    }

    override fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch { handleMessage(trimmed) }
    }

    override fun clearHistory() {
        pendingConfirmation?.complete(false)
        history.clear()
        requestMessages.clear()
        systemSeeded = false
        if (_state.value != CoachState.Unavailable) _state.value = CoachState.Ready
    }

    override fun confirmPendingAction() { pendingConfirmation?.complete(true) }
    override fun cancelPendingAction() { pendingConfirmation?.complete(false) }

    private suspend fun handleMessage(userText: String) {
        turnLock.withLock {
            val config = configFlow.value ?: run {
                _state.value = CoachState.Error(history.toList(), "Cloud AI not configured.")
                return@withLock
            }
            history.add(ChatMessage(Role.User, userText))
            _state.value = CoachState.Thinking(history.toList(), toolStatus = "Thinking…")
            try {
                if (!systemSeeded) {
                    requestMessages.add(ChatRequestMessage(role = "system", content = tools.systemPromptSnapshot()))
                    systemSeeded = true
                }
                requestMessages.add(ChatRequestMessage(role = "user", content = userText))

                var rounds = 0
                while (true) {
                    if (rounds++ >= MAX_TOOL_ROUNDS) {
                        _state.value = CoachState.Error(history.toList(), "Something went wrong — try again.")
                        return@withLock
                    }
                    val response = client.completion(config, requestMessages.toList(), COACH_TOOL_SCHEMAS)

                    if (response.toolCalls.isEmpty()) {
                        val text = response.text.ifBlank { "Done." }
                        requestMessages.add(ChatRequestMessage(role = "assistant", content = text))
                        _state.value = CoachState.Responding(history.toList(), partial = text)
                        history.add(ChatMessage(Role.Assistant, text))
                        _state.value = CoachState.Idle(history.toList())
                        return@withLock
                    }

                    // Replay the assistant turn that requested tools, then each tool result.
                    requestMessages.add(
                        ChatRequestMessage(
                            role = "assistant",
                            content = null,
                            assistantToolCallsJson = encodeToolCalls(response.toolCalls),
                        ),
                    )
                    for (call in response.toolCalls) {
                        _state.value = CoachState.Thinking(history.toList(), toolStatus = toolStatusText(call.name))
                        val result = if (call.name in COACH_WRITE_TOOLS) {
                            confirmAndRun(call)
                        } else {
                            tools.execute(call.name, call.arguments)
                        }
                        requestMessages.add(
                            ChatRequestMessage(
                                role = "tool",
                                content = result,
                                toolCallId = call.id,
                                name = call.name,
                            ),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = CoachState.Error(history.toList(), "Something went wrong — try again.")
            }
        }
    }

    private suspend fun confirmAndRun(call: com.zack.recomptracker.data.remote.ParsedToolCall): String {
        val action = PendingCoachAction(
            toolName = call.name,
            args = call.arguments,
            displayText = pendingActionDisplayText(call.name, call.arguments),
        )
        val deferred = CompletableDeferred<Boolean>()
        pendingConfirmation = deferred
        _state.value = CoachState.AwaitingConfirmation(history.toList(), action)
        val confirmed = deferred.await()
        pendingConfirmation = null
        if (!confirmed) return """{"cancelled":true}"""
        _state.value = CoachState.Thinking(history.toList(), toolStatus = toolStatusText(call.name))
        return tools.execute(call.name, call.arguments)
    }

    private fun encodeToolCalls(calls: List<com.zack.recomptracker.data.remote.ParsedToolCall>): String {
        val array: JsonArray = buildJsonArray {
            calls.forEach { call ->
                addJsonObject {
                    put("id", call.id)
                    put("type", "function")
                    put("function", JsonObject(mapOf(
                        "name" to JsonPrimitive(call.name),
                        "arguments" to JsonPrimitive(encodeArgs(call.arguments)),
                    )))
                }
            }
        }
        return array.toString()
    }

    private fun encodeArgs(args: Map<String, String>): String =
        JsonObject(args.mapValues { (_, v) -> JsonPrimitive(v) }).toString()

    private fun toolStatusText(name: String): String = when (name) {
        "get_today_summary" -> "Reading your food log…"
        "get_weekly_trends" -> "Reading your weekly trends…"
        "log_meal" -> "Logging meal…"
        "log_metric" -> "Saving metric…"
        "update_calorie_target" -> "Updating calorie target…"
        else -> "Running tool…"
    }

    private fun pendingActionDisplayText(toolName: String, args: Map<String, String>): String =
        when (toolName) {
            "log_meal" -> buildString {
                append("Log ${args["name"]}")
                val grams = args["grams"]?.toDoubleOrNull()
                if (grams != null) append(" (${grams.toInt()}g)")
                val cals = args["calories"]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
                if (cals != null && cals > 0) append(" ($cals kcal)")
                val type = args["meal_type"]
                if (!type.isNullOrBlank()) append(" as $type")
                append(" to today's food log")
            }
            "log_metric" -> "Save ${args["metric"]} = ${args["value"]}"
            "update_calorie_target" -> "Update daily calorie target to ${args["target_calories"]} kcal"
            else -> toolName
        }

    private companion object {
        private const val MAX_TOOL_ROUNDS = 12
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CloudCoachCoordinatorTest"`
Expected: PASS. Fix any compile/logic mismatch until green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorTest.kt
git commit -m "feat(ai): add CloudCoachCoordinator with shared tool schemas"
```

---

## Task 9: Routing coordinators

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/RoutingInsightCoordinator.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ai/RoutingCoachCoordinator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/RoutingCoordinatorTest.kt`

Each router holds the Gemma + Cloud delegate, plus a flow that resolves the **effective** backend: CLOUD only when the preference is CLOUD *and* cloud config is complete; otherwise LOCAL (graceful fallback). State is re-pointed with `flatMapLatest`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/RoutingCoordinatorTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingCoordinatorTest {

    /** Minimal fake coach coordinator with a settable state and a recording sendMessage. */
    private class FakeCoach(initial: CoachState) : CoachCoordinator {
        private val _state = MutableStateFlow(initial)
        override val state: StateFlow<CoachState> = _state.asStateFlow()
        var sent: String? = null
        override fun sendMessage(text: String) { sent = text }
        override fun clearHistory() {}
        override fun confirmPendingAction() {}
        override fun cancelPendingAction() {}
    }

    @Test
    fun `coach router forwards to cloud when effective backend is CLOUD`() = runTest {
        val local = FakeCoach(CoachState.Ready)
        val cloud = FakeCoach(CoachState.Idle(listOf(ChatMessage(Role.Assistant, "cloud"))))
        val backend = MutableStateFlow(AiBackend.CLOUD)
        val cloudConfigComplete = MutableStateFlow(true)
        val router = RoutingCoachCoordinator(local, cloud, backend, cloudConfigComplete, backgroundScope)
        advanceUntilIdle()
        router.sendMessage("hi")
        assertEquals("hi", cloud.sent)
        assertEquals(null, local.sent)
    }

    @Test
    fun `coach router falls back to local when cloud config incomplete`() = runTest {
        val local = FakeCoach(CoachState.Ready)
        val cloud = FakeCoach(CoachState.Unavailable)
        val backend = MutableStateFlow(AiBackend.CLOUD)
        val cloudConfigComplete = MutableStateFlow(false) // selected CLOUD but not configured
        val router = RoutingCoachCoordinator(local, cloud, backend, cloudConfigComplete, backgroundScope)
        advanceUntilIdle()
        router.sendMessage("hi")
        assertEquals("hi", local.sent)
        assertEquals(null, cloud.sent)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RoutingCoordinatorTest"`
Expected: FAIL — `RoutingCoachCoordinator` unresolved.

- [ ] **Step 3: Write the coach router**

Create `app/src/main/java/com/zack/recomptracker/ai/RoutingCoachCoordinator.kt`:

```kotlin
package com.zack.recomptracker.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Forwards [CoachCoordinator] calls to either the local (Gemma) or cloud delegate based on the
 * *effective* backend: CLOUD only when the preference is CLOUD and cloud config is complete;
 * otherwise LOCAL.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutingCoachCoordinator(
    private val local: CoachCoordinator,
    private val cloud: CoachCoordinator,
    backendFlow: Flow<AiBackend>,
    cloudConfigCompleteFlow: Flow<Boolean>,
    scope: CoroutineScope,
) : CoachCoordinator {

    private val effectiveBackend: StateFlow<AiBackend> =
        combine(backendFlow, cloudConfigCompleteFlow) { backend, complete ->
            if (backend == AiBackend.CLOUD && complete) AiBackend.CLOUD else AiBackend.LOCAL
        }.stateIn(scope, SharingStarted.Eagerly, AiBackend.LOCAL)

    private fun active(): CoachCoordinator =
        if (effectiveBackend.value == AiBackend.CLOUD) cloud else local

    override val state: StateFlow<CoachState> =
        effectiveBackend
            .flatMapLatest { backend -> if (backend == AiBackend.CLOUD) cloud.state else local.state }
            .stateIn(scope, SharingStarted.Eagerly, CoachState.Unavailable)

    override fun sendMessage(text: String) = active().sendMessage(text)
    override fun clearHistory() = active().clearHistory()
    override fun confirmPendingAction() = active().confirmPendingAction()
    override fun cancelPendingAction() = active().cancelPendingAction()
}
```

- [ ] **Step 4: Write the insight router**

Create `app/src/main/java/com/zack/recomptracker/ai/RoutingInsightCoordinator.kt`:

```kotlin
package com.zack.recomptracker.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Forwards [AiInsightCoordinator] calls to the local (Gemma) or cloud delegate based on the
 * *effective* backend (CLOUD only when selected AND cloud config is complete).
 *
 * Per-kind generation state and the weekly `state` flow are re-pointed via [flatMapLatest] so
 * the UI observes the active delegate. Model-lifecycle calls (download/delete/setSelectedModel)
 * always target the LOCAL delegate, since those manage the on-device Gemma model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutingInsightCoordinator(
    private val local: AiInsightCoordinator,
    private val cloud: AiInsightCoordinator,
    backendFlow: Flow<AiBackend>,
    cloudConfigCompleteFlow: Flow<Boolean>,
    private val scope: CoroutineScope,
) : AiInsightCoordinator {

    private val effectiveBackend: StateFlow<AiBackend> =
        combine(backendFlow, cloudConfigCompleteFlow) { backend, complete ->
            if (backend == AiBackend.CLOUD && complete) AiBackend.CLOUD else AiBackend.LOCAL
        }.stateIn(scope, SharingStarted.Eagerly, AiBackend.LOCAL)

    private fun active(): AiInsightCoordinator =
        if (effectiveBackend.value == AiBackend.CLOUD) cloud else local

    override val state: StateFlow<AiInsightState> =
        effectiveBackend
            .flatMapLatest { backend -> if (backend == AiBackend.CLOUD) cloud.state else local.state }
            .stateIn(scope, SharingStarted.Eagerly, AiInsightState.Disabled)

    // Model selection always reflects the on-device model the LOCAL delegate manages.
    override val selectedModel: StateFlow<ModelVariant> get() = local.selectedModel
    override fun setSelectedModel(variant: ModelVariant) = local.setSelectedModel(variant)
    override fun requestDownload() = local.requestDownload()
    override fun cancelDownload() = local.cancelDownload()
    override fun deleteModel() = local.deleteModel()

    override fun onAiCardVisible(context: InsightContext) = active().onAiCardVisible(context)
    override fun retryGeneration(context: InsightContext) = active().retryGeneration(context)

    override fun generationState(kind: InsightKind): StateFlow<AiInsightState> =
        effectiveBackend
            .flatMapLatest { backend ->
                if (backend == AiBackend.CLOUD) cloud.generationState(kind) else local.generationState(kind)
            }
            .stateIn(scope, SharingStarted.Eagerly, AiInsightState.ModelReady)

    override fun onInsightVisible(request: InsightRequest) = active().onInsightVisible(request)
    override fun retryInsight(request: InsightRequest) = active().retryInsight(request)
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RoutingCoordinatorTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/RoutingInsightCoordinator.kt app/src/main/java/com/zack/recomptracker/ai/RoutingCoachCoordinator.kt app/src/test/java/com/zack/recomptracker/ai/RoutingCoordinatorTest.kt
git commit -m "feat(ai): add routing coordinators with cloud-config fallback"
```

---

## Task 10: Production `CoachReadTools` adapter

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt`

Bridges the cloud coach's `CoachReadTools` interface to the real `CoachToolExecutor` + a coach system-prompt builder. Reuses the exact prompt structure documented in `docs/ai-coach.md`.

- [ ] **Step 1: Write the adapter**

Create `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.preferences.displayName
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Production [CoachReadTools]: dispatches tool calls to [CoachToolExecutor] and builds the coach
 * system prompt (plan + profile + today's snapshot + rules). Mirrors
 * `GemmaCoachCoordinator.buildSystemPrompt`, minus the 2B-specific anti-confusion wording — a
 * capable cloud model needs only clear instructions.
 */
class CoachToolsAdapter(
    private val toolExecutor: CoachToolExecutor,
    private val planRepository: PlanRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val dateProvider: DateProvider,
) : CoachReadTools {

    override suspend fun execute(name: String, args: Map<String, String>): String =
        withContext(Dispatchers.IO) { toolExecutor.execute(name, args) }

    override suspend fun systemPromptSnapshot(): String {
        val prefs = planRepository.preferences.first()
        val profile = userProfileStore.preferences.first()
        val today = dateProvider.today()
        val todaySummary = withContext(Dispatchers.IO) { toolExecutor.execute("get_today_summary", emptyMap()) }
        return buildPrompt(prefs, profile, today, todaySummary)
    }

    private fun buildPrompt(
        prefs: PlanPreferences,
        profile: UserProfilePreferences,
        today: java.time.LocalDate,
        todaySummary: String,
    ): String = buildString {
        val yesterday = today.minusDays(1)
        val dayName = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        appendLine("You are a knowledgeable, supportive nutrition and body-recomposition coach inside a tracking app.")
        appendLine("Today: $today ($dayName) | Yesterday: $yesterday")
        appendLine()
        appendLine("Plan: ${prefs.targetCalories} kcal | P ${prefs.targetProteinG}g | C ${prefs.targetCarbsG}g | F ${prefs.targetFatG}g")
        val profileParts = buildList {
            profile.goal?.let { add("Goal: ${it.displayName()}") }
            profile.biologicalSex?.let { add("Sex: ${it.displayName()}") }
            profile.ageYears?.let { add("Age: $it") }
            profile.heightCm?.let { add("Height: $it cm") }
            profile.activityLevel?.let { add("Activity: ${it.displayName()}") }
            profile.weeklyGymSessions?.let { add("Gym sessions/week: $it") }
        }
        if (profileParts.isNotEmpty()) {
            appendLine()
            appendLine("=== USER PROFILE ===")
            appendLine(profileParts.joinToString(" | "))
            appendLine("=== END PROFILE ===")
        }
        appendLine()
        appendLine("=== TODAY'S DATA SNAPSHOT (fetched at conversation start) ===")
        appendLine(todaySummary)
        appendLine("=== END SNAPSHOT ===")
        appendLine()
        appendLine("Guidelines:")
        appendLine("- For today's data, you may answer from the snapshot above. Call get_today_summary(date=…) for any other date, and get_weekly_trends() for multi-day or adherence questions.")
        appendLine("- Use markdown when it improves clarity (short lists, bold key numbers). Answer only from logged data and tool results; never invent numbers.")
        appendLine("- To log food, call log_meal(...); the tool checks the food library automatically. To record a metric, call log_metric(...). These actions are confirmed by the user before they run.")
        append("- Stay on topic: nutrition, body composition, training, and recovery.")
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. (If `displayName()` import path differs, match the import used in `GemmaCoachCoordinator.kt` line 19.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt
git commit -m "feat(ai): add production CoachReadTools adapter for cloud coach"
```

---

## Task 11: Wire everything in `AppContainer`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt:77-98`

- [ ] **Step 1: Add a derived cloud-config flow and build the cloud stack + routers**

In `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`, add these imports (after line 26 `import com.zack.recomptracker.ai.GemmaInsightCoordinator`):

```kotlin
import com.zack.recomptracker.ai.AiBackend
import com.zack.recomptracker.ai.CloudCoachCoordinator
import com.zack.recomptracker.ai.CloudInsightCoordinator
import com.zack.recomptracker.ai.CoachToolsAdapter
import com.zack.recomptracker.ai.RoutingCoachCoordinator
import com.zack.recomptracker.ai.RoutingInsightCoordinator
import com.zack.recomptracker.data.preferences.SecureKeyStore
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
```

Replace lines 78–98 (from `val gemmaServiceHolder = …` through the end of the `coachCoordinator` assignment) with:

```kotlin
    val gemmaServiceHolder = GemmaServiceHolder(context)
    val secureKeyStore = SecureKeyStore(context)
    val openAiCompatClient = OpenAiCompatClient()

    // Effective cloud config: non-null only when base URL, model id, and API key are all present.
    private val cloudConfigFlow: StateFlow<CloudConfig?> =
        combine(
            uiPreferences.cloudBaseUrl,
            uiPreferences.cloudModelId,
            secureKeyStore.hasKey,
        ) { baseUrl, model, hasKey ->
            if (baseUrl.isNotBlank() && model.isNotBlank() && hasKey) {
                CloudConfig(baseUrl = baseUrl, apiKey = secureKeyStore.getApiKey(), model = model)
            } else {
                null
            }
        }.stateIn(appScope, SharingStarted.Eagerly, null)

    private val cloudConfigComplete: StateFlow<Boolean> =
        combine(uiPreferences.cloudConfigPresent, secureKeyStore.hasKey) { present, hasKey ->
            present && hasKey
        }.stateIn(appScope, SharingStarted.Eagerly, false)

    // ── Local (Gemma) coordinators ───────────────────────────────────────────────
    private val gemmaInsightCoordinator: AiInsightCoordinator = GemmaInsightCoordinator(
        context = context,
        aiEnabledFlow = uiPreferences.aiInsightsEnabled,
        scope = appScope,
        serviceHolder = gemmaServiceHolder,
        uiPreferences = uiPreferences,
    )
    private val coachToolExecutor = CoachToolExecutor(
        logRepository = logRepository,
        planRepository = planRepository,
        dateProvider = dateProvider,
    )
    private val gemmaCoachCoordinator: CoachCoordinator = GemmaCoachCoordinator(
        serviceHolder = gemmaServiceHolder,
        insightCoordinator = gemmaInsightCoordinator,
        toolExecutor = coachToolExecutor,
        planRepository = planRepository,
        userProfileStore = userProfilePreferencesStore,
        dateProvider = dateProvider,
        scope = appScope,
    )

    // ── Cloud coordinators ─────────────────────────────────────────────────────────
    private val cloudInsightCoordinator: AiInsightCoordinator = CloudInsightCoordinator(
        aiEnabledFlow = uiPreferences.aiInsightsEnabled,
        configFlow = cloudConfigFlow,
        client = openAiCompatClient,
        scope = appScope,
    )
    private val cloudReadyFlow = combine(
        uiPreferences.aiInsightsEnabled,
        cloudConfigComplete,
    ) { enabled, complete -> enabled && complete }
    private val cloudCoachCoordinator: CoachCoordinator = CloudCoachCoordinator(
        cloudReadyFlow = cloudReadyFlow,
        configFlow = cloudConfigFlow,
        client = openAiCompatClient,
        tools = CoachToolsAdapter(
            toolExecutor = coachToolExecutor,
            planRepository = planRepository,
            userProfileStore = userProfilePreferencesStore,
            dateProvider = dateProvider,
        ),
        scope = appScope,
    )

    // ── Routers (handed out to ViewModels) ──────────────────────────────────────────
    val aiInsightCoordinator: AiInsightCoordinator = RoutingInsightCoordinator(
        local = gemmaInsightCoordinator,
        cloud = cloudInsightCoordinator,
        backendFlow = uiPreferences.aiBackend,
        cloudConfigCompleteFlow = cloudConfigComplete,
        scope = appScope,
    )
    val coachCoordinator: CoachCoordinator = RoutingCoachCoordinator(
        local = gemmaCoachCoordinator,
        cloud = cloudCoachCoordinator,
        backendFlow = uiPreferences.aiBackend,
        cloudConfigCompleteFlow = cloudConfigComplete,
        scope = appScope,
    )
```

> `appScope` is declared on line 77 (above this block), so it is in scope. The `AiInsightCoordinator` / `CoachCoordinator` / `GemmaCoachCoordinator` / `CoachToolExecutor` imports already exist (lines 21–25).

- [ ] **Step 2: Verify the whole app compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. If a `val` ordering error appears (a `private val` referencing `appScope` declared later), confirm the new block sits entirely below line 77.

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all new tests + existing tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(ai): wire cloud backend + routers into AppContainer"
```

---

## Task 12: Settings UI — backend toggle, config fields, Test Connection

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/more/MoreViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt`

- [ ] **Step 1: Extend `MoreViewModel` with cloud config state + actions**

In `app/src/main/java/com/zack/recomptracker/ui/more/MoreViewModel.kt`:

Add imports (after line 13):

```kotlin
import com.zack.recomptracker.ai.AiBackend
import com.zack.recomptracker.data.preferences.SecureKeyStore
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ChatRequestMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

Add fields to `MoreUiState` (inside the data class, before the closing brace at line 29):

```kotlin
    val aiBackend: AiBackend = AiBackend.LOCAL,
    val cloudBaseUrl: String = "",
    val cloudModelId: String = "",
    val cloudHasKey: Boolean = false,
    val testConnectionResult: String? = null,
    val testingConnection: Boolean = false,
```

Add two constructor params to `MoreViewModel` (after `aiInsightCoordinator` on line 35):

```kotlin
    private val secureKeyStore: SecureKeyStore,
    private val openAiCompatClient: OpenAiCompatClient,
```

In the `init` block, replace the `combine(...)` of two flows with a combine that also folds in the cloud config. Replace lines 46–60 with:

```kotlin
        viewModelScope.launch {
            combine(
                uiPreferences.selectedFont,
                uiPreferences.aiInsightsEnabled,
                uiPreferences.aiBackend,
                uiPreferences.cloudBaseUrl,
                uiPreferences.cloudModelId,
            ) { font, ai, backend, baseUrl, modelId ->
                arrayOf(font, ai, backend, baseUrl, modelId)
            }.collect { values ->
                val connected = hcAvailable && hcRepository.hasPermissions()
                _uiState.update {
                    it.copy(
                        selectedFont = values[0] as String,
                        aiInsightsEnabled = values[1] as Boolean,
                        healthConnectConnected = connected,
                        aiBackend = values[2] as AiBackend,
                        cloudBaseUrl = values[3] as String,
                        cloudModelId = values[4] as String,
                    )
                }
            }
        }
        viewModelScope.launch {
            secureKeyStore.hasKey.collect { hasKey ->
                _uiState.update { it.copy(cloudHasKey = hasKey) }
            }
        }
```

Add action methods (after `setAiInsights`, around line 69):

```kotlin
    fun setAiBackend(backend: AiBackend) {
        viewModelScope.launch { uiPreferences.setAiBackend(backend) }
    }

    fun setCloudBaseUrl(url: String) {
        viewModelScope.launch { uiPreferences.setCloudBaseUrl(url) }
    }

    fun setCloudModelId(model: String) {
        viewModelScope.launch { uiPreferences.setCloudModelId(model) }
    }

    fun setCloudApiKey(key: String) {
        secureKeyStore.setApiKey(key)
    }

    fun clearCloudApiKey() {
        secureKeyStore.clearApiKey()
    }

    fun testCloudConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(testingConnection = true, testConnectionResult = null) }
            val s = _uiState.value
            val key = secureKeyStore.getApiKey()
            if (s.cloudBaseUrl.isBlank() || s.cloudModelId.isBlank() || key.isBlank()) {
                _uiState.update { it.copy(testingConnection = false, testConnectionResult = "Fill in URL, model, and API key first.") }
                return@launch
            }
            val result = try {
                withContext(Dispatchers.IO) {
                    openAiCompatClient.completion(
                        config = CloudConfig(baseUrl = s.cloudBaseUrl, apiKey = key, model = s.cloudModelId),
                        messages = listOf(ChatRequestMessage(role = "user", content = "ping")),
                        toolSchemasJson = emptyList(),
                    )
                }
                "Connection OK"
            } catch (e: Exception) {
                "Failed: ${e.message?.take(120) ?: "unknown error"}"
            }
            _uiState.update { it.copy(testingConnection = false, testConnectionResult = result) }
        }
    }
```

- [ ] **Step 2: Provide the new `MoreViewModel` params in `AppContainer`**

In `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`, in the `MoreViewModel::class.java ->` branch (currently lines 173–178), add the two new params:

```kotlin
            MoreViewModel::class.java -> MoreViewModel(
                uiPreferences = container.uiPreferences,
                hcRepository = container.healthConnectRepository,
                backupRepository = container.backupRepository,
                aiInsightCoordinator = container.aiInsightCoordinator,
                secureKeyStore = container.secureKeyStore,
                openAiCompatClient = container.openAiCompatClient,
            )
```

- [ ] **Step 3: Add the cloud config UI to `MoreScreen`**

In `app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt`, locate the existing AI section (the `SettingRow` with `title = "AI Insights"` and the `ModelVariantSelector`, around lines 211–299 per the inventory). Immediately after the AI Insights toggle row, add a backend selector and — shown only when `state.aiBackend == AiBackend.CLOUD` — the config fields. Add this import block at the top with the other imports:

```kotlin
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import com.zack.recomptracker.ai.AiBackend
```

Add this composable content inside the same `item { }` (or a new `item { }`) that renders the AI controls, after the AI Insights `SettingRow`:

```kotlin
            // ── AI backend selector ──────────────────────────────────────────────
            SettingRow(
                emoji = "☁️",
                title = "AI Backend",
                detail = if (state.aiBackend == AiBackend.CLOUD) "Cloud model (API key)" else "On-device Gemma",
                showDivider = true,
            ) {
                Switch(
                    checked = state.aiBackend == AiBackend.CLOUD,
                    onCheckedChange = { useCloud ->
                        viewModel.setAiBackend(if (useCloud) AiBackend.CLOUD else AiBackend.LOCAL)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.accent,
                        uncheckedThumbColor = Color(0x80FFFFFF),
                        uncheckedTrackColor = Color(0x1AFFFFFF),
                        uncheckedBorderColor = Color(0x26FFFFFF),
                    ),
                )
            }

            if (state.aiBackend == AiBackend.CLOUD) {
                Text(
                    text = "In cloud mode your logged data is sent to the API you configure. On-device Gemma keeps everything private.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
                OutlinedTextField(
                    value = state.cloudBaseUrl,
                    onValueChange = viewModel::setCloudBaseUrl,
                    label = { Text("Base URL (e.g. https://openrouter.ai/api/v1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                OutlinedTextField(
                    value = state.cloudModelId,
                    onValueChange = viewModel::setCloudModelId,
                    label = { Text("Model ID (e.g. anthropic/claude-3.5-sonnet)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                var apiKeyInput by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text(if (state.cloudHasKey) "API key (saved — type to replace)" else "API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            viewModel.setCloudApiKey(apiKeyInput)
                            apiKeyInput = ""
                        }
                    }) { Text("Save key") }
                    Button(onClick = { viewModel.testCloudConnection() }, enabled = !state.testingConnection) {
                        Text(if (state.testingConnection) "Testing…" else "Test connection")
                    }
                }
                state.testConnectionResult?.let {
                    Text(text = it, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
```

Add these imports if not already present (the file already imports `Switch`, `SwitchDefaults`, `Color`, `Modifier`, `padding`, `sp`, `Arrangement`, `Row`, `dp`, `TextMuted`):

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Resolve any unresolved-reference errors by matching the existing import style in `MoreScreen.kt` (e.g. `SettingRow`, `accent`).

- [ ] **Step 5: Build the full debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/more/MoreViewModel.kt app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(ui): add cloud AI backend settings (toggle, config, test connection)"
```

---

## Task 13: Manual end-to-end verification

**Files:** none (manual).

- [ ] **Step 1: Install and launch**

Run: `./gradlew :app:installDebug` and open the app on a device/emulator.

- [ ] **Step 2: Configure cloud backend**

In More → AI section: toggle AI Insights on, toggle AI Backend to Cloud, enter:
- Base URL: `https://openrouter.ai/api/v1` (or your provider)
- Model ID: a valid model (e.g. `anthropic/claude-3.5-sonnet` on OpenRouter)
- API key: your key → tap **Save key** → tap **Test connection**.
Expected: "Connection OK".

- [ ] **Step 3: Verify richer insights**

Open Dashboard / Progress with sufficient logged data. The insight card should produce a multi-sentence, cross-signal explanation (noticeably longer than the Gemma one-liner).

- [ ] **Step 4: Verify the coach**

Open Coach. Ask "how did my week go?" → expect a tool-backed answer. Ask "log my weight 80kg" → expect the confirmation dialog → Confirm → expect a success reply and the metric saved (check Body screen).

- [ ] **Step 5: Verify fallback**

Toggle AI Backend back to Local → confirm Gemma path still works (if the model is present), or shows the normal download/missing state. Set backend to Cloud but clear the API key → confirm the app falls back to local behavior rather than erroring.

- [ ] **Step 6: Final commit (if any manual-fix tweaks were needed)**

```bash
git add -A
git commit -m "fix(ai): cloud backend manual-verification adjustments"
```

---

## Self-Review notes (for the implementer)

- **Spec coverage:** Backend selection (Task 2), capabilities seam (Task 2), routing + fallback (Task 9), cloud client + SSE (Tasks 3–4), encrypted key (Task 5), richer insights (Tasks 6–7), leveled-up coach with no caps (Task 8), settings + test connection (Task 12), privacy note (Task 12). Tier-2 (`proactiveReview`) is intentionally `false` everywhere — deferred.
- **Android-bound classes** (`SecureKeyStore`, `UiPreferences`, UI) are build-verified + manually tested rather than unit-tested, consistent with the existing untested preference/UI layer. All pure logic (capabilities, SSE/JSON parsing, both cloud coordinators, routers, rich prompts) is unit-tested.
- **Type consistency:** `CloudConfig`, `ChatRequestMessage`, `ParsedToolCall`, `ParsedChatResponse`, `CoachReadTools`, `COACH_TOOL_SCHEMAS`, `COACH_WRITE_TOOLS`, `AiBackend`, `AiCapabilities` are defined once and referenced consistently across tasks.
- **Before running context-dependent tests** (Tasks 6, 7), confirm the constructor field names of `RecoveryInsightContext` / `ProgressInsightContext` and adjust the test's named args if they differ.
