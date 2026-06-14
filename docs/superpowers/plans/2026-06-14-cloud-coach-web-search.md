# Cloud Coach Web Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the cloud coach a `search_web` tool (Tavily-backed) it calls when its data snapshot, food library, and knowledge base can't answer — e.g. "how many calories in a Big Mac?".

**Architecture:** A read-only `search_web` tool, dispatched through the existing `CoachToolExecutor` and run by the existing `CloudCoachCoordinator` tool loop. Tavily lives behind a swappable `WebSearchProvider` interface (mirroring `KnowledgeRetriever`). The tool is added to the **cloud** coach's tool-schema list only — the local Gemma coach's list is untouched, so it never sees the tool. No confirmation dialog (read tool). Degrades to a structured error when no key/offline.

**Tech Stack:** Kotlin, OkHttp, kotlinx.serialization, JUnit4 + kotlinx-coroutines-test + Mockito-Kotlin (existing test stack).

---

## Design decisions (locked from the spec)

- **Cloud-only via the schema list, not the executor.** `CoachToolExecutor` is a single shared instance (used by both coaches). It's safe to host the `search_web` branch there because the local Gemma coach's tool-schema list never includes `search_web`, so the local model can't call it. Cloud-only is enforced by *which schema list each coordinator sends*.
- **Always-present tool, graceful degradation.** `search_web` is always in the cloud list. When no Tavily key is set (or the call fails/offline), the provider returns `null` and the tool returns `{"error":"web search unavailable"}`. The web key is independent of `cloudConfigComplete` — the coach is "ready" with or without it.
- **`WebSearchProvider.search()` returns `WebSearchResult?`** — `null` is the single "unavailable" signal; the executor turns `null` into the error JSON. Keeps the unavailable contract in one place.

## File structure

**Create:**
- `app/src/main/java/com/zack/recomptracker/data/remote/WebSearchProvider.kt` — interface + `WebSearchResult` / `WebResult` models.
- `app/src/main/java/com/zack/recomptracker/data/remote/WebSearchModels.kt` — pure `parseTavilyResponse()` + `toToolJson()` (unit-testable, no HTTP).
- `app/src/main/java/com/zack/recomptracker/data/remote/TavilyWebSearchProvider.kt` — OkHttp impl, key-gated.
- `app/src/test/java/com/zack/recomptracker/data/remote/WebSearchModelsTest.kt`
- `app/src/test/java/com/zack/recomptracker/data/remote/TavilyWebSearchProviderTest.kt`

**Modify:**
- `app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt` — add `SEARCH_WEB_TOOL_SCHEMA` + `CLOUD_COACH_TOOL_SCHEMAS` (top-level vals; `COACH_TOOL_SCHEMAS` itself unchanged).
- `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` — `webSearchProvider` ctor param + `search_web` branch.
- `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt` — `toolSchemas` ctor param; use it in the completion call; status text.
- `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt` — one guideline change in `COACH_PROMPT_GUIDELINES`.
- `app/src/main/java/com/zack/recomptracker/data/preferences/SecureKeyStore.kt` — web-search key accessors + `hasWebSearchKey` flow.
- `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` — build the provider, inject into the executor, pass the cloud tool list to the cloud coach.
- `app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachViewModel.kt` — web-key state + setters.
- `app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachScreen.kt` — Tavily key field.
- `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorTest.kt` — `search_web` branch tests.
- `app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorTest.kt` — cloud-list + no-confirmation tests.
- `app/src/test/java/com/zack/recomptracker/ai/CoachPromptGuidelinesTest.kt` — guideline assertions.

---

## Task 1: Web search models + pure parse/format

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/remote/WebSearchProvider.kt`
- Create: `app/src/main/java/com/zack/recomptracker/data/remote/WebSearchModels.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/remote/WebSearchModelsTest.kt`

- [ ] **Step 1: Write the interface + models**

Create `WebSearchProvider.kt`:

```kotlin
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
```

- [ ] **Step 2: Write the failing test**

Create `WebSearchModelsTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.remote.WebSearchModelsTest"`
Expected: FAIL — `parseTavilyResponse` / `toToolJson` unresolved.

- [ ] **Step 4: Write the implementation**

Create `WebSearchModels.kt`:

```kotlin
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
    val root = webJson.parseToJsonElement(body).jsonObjectOrNull()
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

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
    this as? JsonObject

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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.remote.WebSearchModelsTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/remote/WebSearchProvider.kt \
        app/src/main/java/com/zack/recomptracker/data/remote/WebSearchModels.kt \
        app/src/test/java/com/zack/recomptracker/data/remote/WebSearchModelsTest.kt
git commit -m "feat(web-search): WebSearchProvider interface + Tavily parse/format models"
```

---

## Task 2: TavilyWebSearchProvider (HTTP, key-gated)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/remote/TavilyWebSearchProvider.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/remote/TavilyWebSearchProviderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `TavilyWebSearchProviderTest.kt` (covers the key-gate without HTTP; the HTTP body parsing is already covered by `WebSearchModelsTest`):

```kotlin
package com.zack.recomptracker.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class TavilyWebSearchProviderTest {

    @Test
    fun `returns null when no key is configured`() = runTest {
        val provider = TavilyWebSearchProvider(keyProvider = { "" })
        assertNull(provider.search("big mac calories"))
    }

    @Test
    fun `returns null for a blank query`() = runTest {
        val provider = TavilyWebSearchProvider(keyProvider = { "tvly-key" })
        assertNull(provider.search("   "))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.remote.TavilyWebSearchProviderTest"`
Expected: FAIL — `TavilyWebSearchProvider` unresolved.

- [ ] **Step 3: Write the implementation**

Create `TavilyWebSearchProvider.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.remote.TavilyWebSearchProviderTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/remote/TavilyWebSearchProvider.kt \
        app/src/test/java/com/zack/recomptracker/data/remote/TavilyWebSearchProviderTest.kt
git commit -m "feat(web-search): Tavily HTTP provider, key-gated with null degradation"
```

---

## Task 3: `search_web` tool schema + executor branch

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt:42-52`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt:16-31`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorTest.kt`

- [ ] **Step 1: Add the schema constants**

In `GemmaCoachCoordinator.kt`, immediately AFTER the `COACH_WRITE_TOOLS` declaration (line 52), add:

```kotlin
/**
 * Web-search tool schema. CLOUD COACH ONLY — never added to the local Gemma tool list (the 2B
 * model is poor at tool calls and the local backend is meant to work offline). Read-only, so it
 * is not in [COACH_WRITE_TOOLS] and runs without user confirmation.
 */
val SEARCH_WEB_TOOL_SCHEMA: String =
    """{"name":"search_web","description":"Search the public web for a fact you don't already have — e.g. calories or macros for a restaurant or packaged food that isn't in the user's library, or a general nutrition, supplement, or training question. Returns a short answer plus source URLs. Always cite the source URL in your reply.","parameters":{"type":"object","properties":{"query":{"type":"string","description":"A concise search query, e.g. \"McDonald's Big Mac calories\""}},"required":["query"]}}"""

/** The cloud coach's full tool list: the shared tools plus web search. */
val CLOUD_COACH_TOOL_SCHEMAS: List<String> = COACH_TOOL_SCHEMAS + SEARCH_WEB_TOOL_SCHEMA
```

- [ ] **Step 2: Write the failing test**

In `CoachToolExecutorTest.kt`, add these imports near the top (after line 20):

```kotlin
import com.zack.recomptracker.data.remote.WebResult
import com.zack.recomptracker.data.remote.WebSearchProvider
import com.zack.recomptracker.data.remote.WebSearchResult
```

Then add these tests inside the class (before the final closing brace):

```kotlin
@Test
fun `search_web returns capped JSON from the provider`() = runTest {
    val logRepo = mock<LogRepository>()
    val planRepo = mock<PlanRepository>()
    val provider = object : WebSearchProvider {
        override suspend fun search(query: String): WebSearchResult =
            WebSearchResult(answer = "Big Mac is ~563 kcal.", results = listOf(
                WebResult("Big Mac", "https://example.com/bigmac", "563 calories"),
            ))
    }
    val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider, provider)
    val result = executor.execute("search_web", mapOf("query" to "big mac calories"))
    assertTrue("has answer", result.contains("563 kcal"))
    assertTrue("has source url", result.contains("https://example.com/bigmac"))
}

@Test
fun `search_web is unavailable when no provider is configured`() = runTest {
    val executor = CoachToolExecutor(mock(), mock(), fixedDateProvider) // provider defaults to null
    val result = executor.execute("search_web", mapOf("query" to "big mac calories"))
    assertEquals("""{"error":"web search unavailable"}""", result)
}

@Test
fun `search_web is unavailable when the provider returns null`() = runTest {
    val provider = object : WebSearchProvider {
        override suspend fun search(query: String): WebSearchResult? = null
    }
    val executor = CoachToolExecutor(mock(), mock(), fixedDateProvider, provider)
    val result = executor.execute("search_web", mapOf("query" to "x"))
    assertEquals("""{"error":"web search unavailable"}""", result)
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachToolExecutorTest"`
Expected: FAIL — `CoachToolExecutor` has no 4th param / no `search_web` branch.

- [ ] **Step 4: Add the executor param and branch**

In `CoachToolExecutor.kt`, add the import (after line 8):

```kotlin
import com.zack.recomptracker.data.remote.WebSearchProvider
import com.zack.recomptracker.data.remote.toToolJson
```

Change the constructor (lines 16-20) to add the optional provider:

```kotlin
class CoachToolExecutor(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val webSearchProvider: WebSearchProvider? = null,
) {
```

Add a branch to the `execute` `when` (between the `update_calorie_target` line and `else`, line 29):

```kotlin
        "search_web" -> searchWeb(args)
```

Add the private function (e.g. after `updateCalorieTarget`, before the `String.esc()` helper at line 275):

```kotlin
    private suspend fun searchWeb(args: Map<String, String>): String {
        val query = args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return """{"error":"search_web requires 'query'"}"""
        val provider = webSearchProvider ?: return """{"error":"web search unavailable"}"""
        val result = provider.search(query) ?: return """{"error":"web search unavailable"}"""
        return result.toToolJson()
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachToolExecutorTest"`
Expected: PASS (all existing + 3 new).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt \
        app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt \
        app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorTest.kt
git commit -m "feat(web-search): search_web tool schema + executor dispatch branch"
```

---

## Task 4: Wire `search_web` into the cloud coach (cloud-only, no confirmation)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt:45-52, 136-138, 232-239`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorTest.kt`

- [ ] **Step 1: Write the failing test**

In `CloudCoachCoordinatorTest.kt`, add tests inside the class (before the final closing brace). These use the existing `ScriptedClient` and `FakeExecutor`:

```kotlin
@Test
fun `cloud coach sends the web-search tool when given the cloud tool list`() = runTest {
    val scope = CoroutineScope(coroutineContext + SupervisorJob())
    val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("Answer.", emptyList()))))
    val coach = CloudCoachCoordinator(
        flowOf(true), config(), client, FakeExecutor(), scope,
        toolSchemas = CLOUD_COACH_TOOL_SCHEMAS,
    )
    advanceUntilIdle()
    coach.sendMessage("calories in a big mac?")
    advanceUntilIdle()
    assertTrue(client.lastToolSchemas.any { it.contains("\"search_web\"") })
    scope.cancel()
}

@Test
fun `search_web runs without confirmation then answers`() = runTest {
    val scope = CoroutineScope(coroutineContext + SupervisorJob())
    val responses = ArrayDeque(
        listOf(
            ParsedChatResponse("", listOf(ParsedToolCall("c1", "search_web", mapOf("query" to "big mac calories")))),
            ParsedChatResponse("A Big Mac is ~563 kcal (source).", emptyList()),
        ),
    )
    val client = ScriptedClient(responses)
    val executor = FakeExecutor()
    val coach = CloudCoachCoordinator(
        flowOf(true), config(), client, executor, scope,
        toolSchemas = CLOUD_COACH_TOOL_SCHEMAS,
    )
    advanceUntilIdle()
    coach.sendMessage("calories in a big mac?")
    advanceUntilIdle()
    // No confirmation pause — the tool ran straight through and the coach answered.
    assertEquals(listOf("search_web"), executor.calls.map { it.first })
    assertTrue(coach.state.value is CoachState.Idle)
    scope.cancel()
}
```

Note: `CLOUD_COACH_TOOL_SCHEMAS` is in the same `com.zack.recomptracker.ai` package, so no import is needed.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CloudCoachCoordinatorTest"`
Expected: FAIL — `CloudCoachCoordinator` has no `toolSchemas` parameter.

- [ ] **Step 3: Add the `toolSchemas` parameter**

In `CloudCoachCoordinator.kt`, add the parameter to the constructor (after `knowledgeInjector`, line 51) so existing positional test calls stay valid:

```kotlin
class CloudCoachCoordinator(
    cloudReadyFlow: Flow<Boolean>,
    private val configFlow: StateFlow<CloudConfig?>,
    private val client: OpenAiCompatClient,
    private val tools: CoachReadTools,
    private val scope: CoroutineScope,
    private val knowledgeInjector: KnowledgeInjector = NoOpKnowledgeInjector,
    private val toolSchemas: List<String> = COACH_TOOL_SCHEMAS,
) : CoachCoordinator {
```

Change the completion call (line 137) from `COACH_TOOL_SCHEMAS` to the field:

```kotlin
                    val response = withTimeout(TURN_TIMEOUT_MS) {
                        client.completion(config, requestMessages.toList(), toolSchemas)
                    }
```

Add a status line for the web tool in `toolStatusText` (inside the `when`, line 232-239):

```kotlin
        "search_web" -> "Searching the web…"
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CloudCoachCoordinatorTest"`
Expected: PASS (all existing + 2 new). Existing positional constructions still compile because `toolSchemas` defaults to `COACH_TOOL_SCHEMAS`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt \
        app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorTest.kt
git commit -m "feat(web-search): cloud coach accepts a tool-schema list; web tool runs unconfirmed"
```

---

## Task 5: Coach guideline — when to search and cite

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt:20-25`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachPromptGuidelinesTest.kt`

- [ ] **Step 1: Write the failing test**

In `CoachPromptGuidelinesTest.kt`, add a test inside the class:

```kotlin
@Test
fun `guidelines tell the coach to search the web and cite when knowledge is missing`() {
    assertTrue(COACH_PROMPT_GUIDELINES.contains("search_web"))
    assertTrue(COACH_PROMPT_GUIDELINES.lowercase().contains("cite"))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachPromptGuidelinesTest"`
Expected: FAIL — `search_web` not yet in the guidelines.

- [ ] **Step 3: Update the guidelines constant**

In `CoachToolsAdapter.kt`, replace the entire `COACH_PROMPT_GUIDELINES` value (lines 20-25) with this. It keeps the existing "MUST call search_food_library" and "never estimate" / "REFERENCE KNOWLEDGE" phrases (so the other two guideline tests still pass) and adds the web rules:

```kotlin
internal const val COACH_PROMPT_GUIDELINES: String =
    "- For today's data, you may answer from the snapshot above. Call get_today_summary(date=…) for any other date, and get_weekly_trends() for multi-day or adherence questions.\n" +
        "- For any food's calories or macros you do not already have, you MUST call search_food_library first. If the food is not in the library, call search_web to look it up online — never estimate numbers from memory.\n" +
        "- If a fact you need is not in the snapshot, the food library, or the REFERENCE KNOWLEDGE — for example a restaurant or packaged-food nutrition figure, or a general nutrition, supplement, or training question — call search_web(query=…).\n" +
        "- When you use a web result, cite the source URL in your answer. Answer only from logged data, tool results, REFERENCE KNOWLEDGE, and web results you cite; never invent numbers.\n" +
        "- Use markdown when it improves clarity (short lists, bold key numbers).\n" +
        "- To log food, call log_meal(...); the tool checks the food library automatically. To record a metric, call log_metric(...). These actions are confirmed by the user before they run.\n" +
        "- Stay on topic: nutrition, body composition, training, and recovery."
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachPromptGuidelinesTest"`
Expected: PASS (3 tests — the 2 existing assertions still hold).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt \
        app/src/test/java/com/zack/recomptracker/ai/CoachPromptGuidelinesTest.kt
git commit -m "feat(web-search): coach guideline for web fallback + source citation"
```

---

## Task 6: SecureKeyStore — web-search key

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/SecureKeyStore.kt`

(No unit test: `SecureKeyStore` uses Android `EncryptedSharedPreferences`, which needs an instrumented environment. It is verified by the build compiling and by manual run in Task 9.)

- [ ] **Step 1: Add web-key state, accessors, and flow**

In `SecureKeyStore.kt`, add a second reactive flag after `_hasKey` (line 33):

```kotlin
    private val _hasWebSearchKey = MutableStateFlow(false)
    val hasWebSearchKey: StateFlow<Boolean> = _hasWebSearchKey.asStateFlow()
```

Update `init` (lines 35-37) to seed both flags:

```kotlin
    init {
        _hasKey.value = getApiKey().isNotBlank()
        _hasWebSearchKey.value = getWebSearchKey().isNotBlank()
    }
```

Add the accessor trio after `clearApiKey()` (line 49):

```kotlin
    fun getWebSearchKey(): String = prefs.getString(KEY_WEB_SEARCH, "").orEmpty()

    fun setWebSearchKey(value: String) {
        prefs.edit().putString(KEY_WEB_SEARCH, value.trim()).apply()
        _hasWebSearchKey.value = value.isNotBlank()
    }

    fun clearWebSearchKey() {
        prefs.edit().remove(KEY_WEB_SEARCH).apply()
        _hasWebSearchKey.value = false
    }
```

Add the key name to the companion (line 51-53):

```kotlin
    private companion object {
        const val KEY_API = "cloud_api_key"
        const val KEY_WEB_SEARCH = "web_search_api_key"
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/SecureKeyStore.kt
git commit -m "feat(web-search): store Tavily key in SecureKeyStore alongside cloud key"
```

---

## Task 7: DI wiring in AppContainer

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt:160-164, 277-290`

- [ ] **Step 1: Build the provider and inject it into the executor**

In `AppContainer.kt`, add the import near the other `data.remote` imports (around line 50):

```kotlin
import com.zack.recomptracker.data.remote.TavilyWebSearchProvider
```

Add the provider just above the `coachToolExecutor` declaration (before line 160):

```kotlin
    private val webSearchProvider = TavilyWebSearchProvider(
        keyProvider = { secureKeyStore.getWebSearchKey() },
    )
```

Pass it into `coachToolExecutor` (lines 160-164):

```kotlin
    private val coachToolExecutor = CoachToolExecutor(
        logRepository = logRepository,
        planRepository = planRepository,
        dateProvider = dateProvider,
        webSearchProvider = webSearchProvider,
    )
```

- [ ] **Step 2: Pass the cloud tool list to the cloud coach**

In the `cloudCoachCoordinator` construction (lines 277-290), add the `toolSchemas` argument after `knowledgeInjector`:

```kotlin
    private val cloudCoachCoordinator: CoachCoordinator = CloudCoachCoordinator(
        cloudReadyFlow = cloudReadyFlow,
        configFlow = cloudConfigFlow,
        client = openAiCompatClient,
        tools = CoachToolsAdapter(
            toolExecutor = coachToolExecutor,
            planRepository = planRepository,
            userProfileStore = userProfilePreferencesStore,
            dateProvider = dateProvider,
            handoffStore = coachHandoffStore,
        ),
        scope = appScope,
        knowledgeInjector = knowledgeInjector,
        toolSchemas = CLOUD_COACH_TOOL_SCHEMAS,
    )
```

Note: `CLOUD_COACH_TOOL_SCHEMAS` is a top-level `val` in package `com.zack.recomptracker.ai`. AppContainer already imports from that package (e.g. `CloudCoachCoordinator`); add `import com.zack.recomptracker.ai.CLOUD_COACH_TOOL_SCHEMAS` if the build reports it unresolved. The local `gemmaCoachCoordinator` is unchanged — its model never receives `search_web`.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(web-search): wire Tavily provider into executor + cloud coach tool list"
```

---

## Task 8: Settings UI — Tavily key field

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachViewModel.kt:29-39, 81-112`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachScreen.kt:236-265`

- [ ] **Step 1: Add web-key state + setters to the ViewModel**

In `AiCoachViewModel.kt`, add a field to `AiCoachUiState` (after `cloudHasKey`, line 36):

```kotlin
    val cloudHasWebSearchKey: Boolean = false,
```

In `init`, extend the `secureKeyStore.hasKey` collector block (lines 81-85) to also observe the web key:

```kotlin
        viewModelScope.launch {
            secureKeyStore.hasKey.collect { hasKey ->
                _uiState.update { it.copy(cloudHasKey = hasKey) }
            }
        }
        viewModelScope.launch {
            secureKeyStore.hasWebSearchKey.collect { hasWebKey ->
                _uiState.update { it.copy(cloudHasWebSearchKey = hasWebKey) }
            }
        }
```

Add setters next to `clearCloudApiKey()` (after line 112):

```kotlin
    fun setWebSearchKey(key: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { secureKeyStore.setWebSearchKey(key) } }
    }

    fun clearWebSearchKey() {
        viewModelScope.launch { withContext(Dispatchers.IO) { secureKeyStore.clearWebSearchKey() } }
    }
```

- [ ] **Step 2: Add the key field to the screen**

In `AiCoachScreen.kt`, inside the `AiBackend.CLOUD` `TintedCard`, AFTER the API-key Save/Clear `Row` block (which ends at line 265) and before the `Spacer` + "Test connection" button (line 266), insert a Tavily web-search key field. This reuses `GlassInputField` and `SectionLabel` (already imported/used in this file):

```kotlin
                                Spacer(Modifier.height(14.dp))
                                SectionLabel("Web search (optional)")
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Add a free Tavily API key (tavily.com) to let the coach look up facts it doesn't know, like calories for a restaurant meal.",
                                    color = appColors.textMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                )
                                Spacer(Modifier.height(8.dp))
                                var webKeyInput by remember { mutableStateOf("") }
                                GlassInputField(
                                    label = if (state.cloudHasWebSearchKey) "Tavily key (saved — type to replace)" else "Tavily API key",
                                    value = webKeyInput,
                                    onValueChange = { webKeyInput = it },
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            if (webKeyInput.isNotBlank()) {
                                                viewModel.setWebSearchKey(webKeyInput)
                                                webKeyInput = ""
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Save key", fontSize = 13.sp) }
                                    if (state.cloudHasWebSearchKey) {
                                        Button(
                                            onClick = { viewModel.clearWebSearchKey() },
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Clear", fontSize = 13.sp) }
                                    }
                                }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `remember`/`mutableStateOf`/`Row`/`Arrangement`/`Button`/`Spacer`/`Text`/`SectionLabel`/`appColors` report unresolved, they are already used elsewhere in this same file — confirm the new block sits inside the same `Column`/`item` scope as the existing API-key field.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachViewModel.kt \
        app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachScreen.kt
git commit -m "feat(web-search): Tavily API key field in AI Coach settings"
```

---

## Task 9: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (including the new `WebSearchModelsTest`, `TavilyWebSearchProviderTest`, and the added cases in `CoachToolExecutorTest`, `CloudCoachCoordinatorTest`, `CoachPromptGuidelinesTest`).

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test (on device/emulator)**

1. AI Coach settings → Cloud backend → enter base URL, model id, API key → "Test connection" → OK.
2. Enter a Tavily key in the new "Web search" field → Save.
3. Coach chat: ask "how many calories in a Big Mac?" → expect a "Searching the web…" status, then an answer with a cited source URL.
4. Clear the Tavily key → ask again → coach should answer that it couldn't look it up (no crash).
5. Switch to On-device backend → confirm the coach still works and the local model is never offered `search_web`.

- [ ] **Step 4: Final confirmation**

Confirm the branch `feat/cloud-coach-web-search` holds all task commits and the working tree is clean (`git status`).

---

## Self-review notes

- **Spec coverage:** `search_web` tool (Task 3) · Tavily provider behind `WebSearchProvider` (Tasks 1–2) · cloud-only via tool list (Tasks 3,4,7) · read-only/no confirmation (Task 4) · graceful degradation (Tasks 2,3) · model-decides guideline + citation (Task 5) · key storage (Task 6) · settings UI (Task 8) · key independent of cloud readiness (Task 6/7, no change to `cloudConfigComplete`). All spec sections map to a task.
- **Out of scope (unchanged):** local Gemma backend, nutrition-DB tool, food-library caching, insight-card web search.
- **Type consistency:** `WebSearchProvider.search(): WebSearchResult?`, `WebSearchResult(answer, results)`, `WebResult(title, url, content)`, `toToolJson(maxResults, maxContentChars)`, `CLOUD_COACH_TOOL_SCHEMAS`, `SEARCH_WEB_TOOL_SCHEMA`, `CoachToolExecutor(..., webSearchProvider)`, `CloudCoachCoordinator(..., toolSchemas)`, `SecureKeyStore.getWebSearchKey/setWebSearchKey/clearWebSearchKey/hasWebSearchKey` — names are identical across all tasks.
