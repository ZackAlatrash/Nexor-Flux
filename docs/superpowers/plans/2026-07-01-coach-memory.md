# Coach Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the coach a user-visible, editable freeform memory of user facts — read into its chat prompt, writable on request (`remember`/`forget`), and managed in a dedicated screen.

**Architecture:** A `CoachMemoryStore` (DataStore, `data/coach`) holds a flat capped list of `{id,text,createdAtIso}` entries. `CoachToolsAdapter` injects them into the chat system prompt; `CoachToolExecutor` adds `remember`/`forget` tools (non-confirmed); a `CoachMemoryScreen` + `CoachMemoryViewModel` let the user add/edit/delete, reached from the AI Coach screen.

**Tech Stack:** Kotlin, coroutines/Flow, AndroidX DataStore (Preferences), kotlinx.serialization, Jetpack Compose + Material3, JUnit + mockito-kotlin + temp-dir DataStore fakes.

**Spec:** `docs/superpowers/specs/2026-07-01-coach-memory-design.md`
**Branch:** `redesign/ai-coaching`. **Run in the MAIN checkout** (worktree isolation branches from the wrong base in this repo). Each implementer verifies base FIRST: `git branch --show-current` == `redesign/ai-coaching` AND `grep -c "delete_meal" app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` ≥ 1 (prior features present). If not, STOP.

---

## File Structure
- `data/coach/CoachMemoryStore.kt` — NEW: `CoachMemoryEntry`, `CoachMemory` interface, `NoopCoachMemory`, `CoachMemoryStore` (Task 1).
- `ai/CoachToolExecutor.kt` — `remember`/`forget` methods + dispatch + `coachMemory` ctor param (Task 2).
- `ai/CoachTools.kt` — `MEMORY_TOOL_SCHEMAS`, append to `CLOUD_COACH_TOOL_SCHEMAS` (NOT `COACH_WRITE_TOOLS`) (Task 2).
- `ai/CoachToolsAdapter.kt` — inject memory block + `coachMemory` ctor param + guidelines (Task 3).
- `ai/CloudCoachCoordinator.kt` — `toolStatusText` labels (Task 3).
- `ui/aicoach/CoachMemoryViewModel.kt` — NEW (Task 4).
- `ui/aicoach/CoachMemoryScreen.kt` — NEW; `ui/aicoach/AiCoachScreen.kt` entry row; `ui/navigation/AppNavGraph.kt` route (Task 5).
- `core/AppContainer.kt` — construct store, wire into executor (T2), adapter (T3), VM factory (T5).
- Tests: `data/coach/CoachMemoryStoreTest.kt`, `ai/CoachToolExecutorMemoryTest.kt`, `ai/CoachToolsAdapterMemoryTest.kt` (or extend existing adapter test), `ui/aicoach/CoachMemoryViewModelTest.kt`.

**Build/verify:** `./gradlew :app:compileDebugKotlin` · `./gradlew :app:testDebugUnitTest --tests "*CoachMemory*" --tests "*AiCoachBoundaryTest*"` · full: `./gradlew :app:testDebugUnitTest` (only `InsightHarnessTest` may fail — known network test) · `./gradlew :app:assembleDebug`.

---

## Task 1: `CoachMemoryStore`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/coach/CoachMemoryStore.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/coach/CoachMemoryStoreTest.kt`

- [ ] **Step 1: Write the failing test**

Mirror `CoachJourneyStoreTest`'s temp-dir DataStore setup (read it for the exact `PreferenceDataStoreFactory.create` + `@After` cleanup + `TestScope` idioms).

```kotlin
package com.zack.recomptracker.data.coach

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.zack.recomptracker.core.time.DateProvider
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachMemoryStoreTest {
    private val tmp = File.createTempFile("coach_memory_test", ".preferences_pb").apply { delete() }
    private fun newStore(): CoachMemoryStore {
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create（produceFile = { tmp }）
        return CoachMemoryStore(ds, object : DateProvider { override fun today() = LocalDate.of(2026, 6, 5) })
    }
    @After fun cleanup() { tmp.delete() }

    @Test fun `add stores a trimmed entry and returns it`() = runTest {
        val store = newStore()
        val e = store.add("  Vegetarian  ")
        assertEquals("Vegetarian", e!!.text)
        assertEquals(listOf("Vegetarian"), store.all().map { it.text })
    }

    @Test fun `add ignores blank text`() = runTest {
        val store = newStore()
        assertNull(store.add("   "))
        assertTrue(store.all().isEmpty())
    }

    @Test fun `add dedupes case-insensitive duplicates`() = runTest {
        val store = newStore()
        store.add("Vegetarian")
        store.add("vegetarian")
        assertEquals(1, store.all().size)
    }

    @Test fun `update changes an entry's text`() = runTest {
        val store = newStore()
        val e = store.add("Bad knee")!!
        store.update(e.id, "Bad left knee — no barbell squats")
        assertEquals("Bad left knee — no barbell squats", store.all().single().text)
    }

    @Test fun `delete removes by id`() = runTest {
        val store = newStore()
        val e = store.add("Trains at home")!!
        store.delete(e.id)
        assertTrue(store.all().isEmpty())
    }

    @Test fun `removeMatching removes the best word match and returns it`() = runTest {
        val store = newStore()
        store.add("Vegetarian")
        store.add("Trains at home")
        val removed = store.removeMatching("i am vegetarian")
        assertEquals("Vegetarian", removed!!.text)
        assertEquals(listOf("Trains at home"), store.all().map { it.text })
    }

    @Test fun `removeMatching returns null when nothing matches`() = runTest {
        val store = newStore()
        store.add("Vegetarian")
        assertNull(store.removeMatching("deadlift PR"))
        assertEquals(1, store.all().size)
    }
}
```

NOTE: type the two fullwidth parens in `newStore()` as normal ASCII `(` `)` — they are shown fullwidth here only to survive formatting; use `PreferenceDataStoreFactory.create(produceFile = { tmp })`.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachMemoryStoreTest*"`
Expected: FAIL — `CoachMemoryStore` / `CoachMemory` don't exist (compile error).

- [ ] **Step 3: Implement the store**

```kotlin
package com.zack.recomptracker.data.coach

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.core.time.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.coachMemoryDataStore by preferencesDataStore(name = "coach_memory")

/** One freeform fact the coach knows about the user. */
@Serializable
data class CoachMemoryEntry(val id: String, val text: String, val createdAtIso: String)

/** Read/write surface for the coach's freeform memory, so callers unit-test against a fake. */
interface CoachMemory {
    fun observe(): Flow<List<CoachMemoryEntry>>
    suspend fun all(): List<CoachMemoryEntry>
    /** Adds a trimmed entry (dedupes case-insensitive duplicates); null if blank. */
    suspend fun add(text: String): CoachMemoryEntry?
    suspend fun update(id: String, text: String)
    suspend fun delete(id: String)
    /** Removes the single best word-match for [query]; null if nothing matches. */
    suspend fun removeMatching(query: String): CoachMemoryEntry?
}

/** Inert memory for tests / Context-free construction. */
object NoopCoachMemory : CoachMemory {
    override fun observe(): Flow<List<CoachMemoryEntry>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun all(): List<CoachMemoryEntry> = emptyList()
    override suspend fun add(text: String): CoachMemoryEntry? = null
    override suspend fun update(id: String, text: String) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun removeMatching(query: String): CoachMemoryEntry? = null
}

/**
 * DataStore-backed flat list of user facts. Mirrors [CoachJourneyStore]: `preferencesDataStore`
 * delegate, `.edit{}` setters, one JSON key, deterministic "today" via [DateProvider]. Boundary rule:
 * imports nothing from `ai/local`.
 */
class CoachMemoryStore(
    private val dataStore: DataStore<Preferences>,
    private val dateProvider: DateProvider,
) : CoachMemory {
    constructor(context: Context, dateProvider: DateProvider) :
        this(context.coachMemoryDataStore, dateProvider)

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(raw: String?): List<CoachMemoryEntry> =
        raw?.let { runCatching { json.decodeFromString<List<CoachMemoryEntry>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    override fun observe(): Flow<List<CoachMemoryEntry>> = dataStore.data.map { decode(it[ENTRIES]) }

    override suspend fun all(): List<CoachMemoryEntry> = decode(dataStore.data.first()[ENTRIES])

    override suspend fun add(text: String): CoachMemoryEntry? {
        val t = text.trim()
        if (t.isBlank()) return null
        var result: CoachMemoryEntry? = null
        dataStore.edit { prefs ->
            val list = decode(prefs[ENTRIES]).toMutableList()
            val existing = list.firstOrNull { it.text.equals(t, ignoreCase = true) }
            if (existing != null) { result = existing; return@edit }
            val nextId = ((list.mapNotNull { it.id.toLongOrNull() }.maxOrNull() ?: 0L) + 1L).toString()
            val entry = CoachMemoryEntry(nextId, t, dateProvider.today().toString())
            list.add(entry)
            val capped = if (list.size > MAX_ENTRIES) list.takeLast(MAX_ENTRIES) else list
            prefs[ENTRIES] = json.encodeToString(capped)
            result = entry
        }
        return result
    }

    override suspend fun update(id: String, text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        dataStore.edit { prefs ->
            val list = decode(prefs[ENTRIES]).map { if (it.id == id) it.copy(text = t) else it }
            prefs[ENTRIES] = json.encodeToString(list)
        }
    }

    override suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            prefs[ENTRIES] = json.encodeToString(decode(prefs[ENTRIES]).filter { it.id != id })
        }
    }

    override suspend fun removeMatching(query: String): CoachMemoryEntry? {
        var removed: CoachMemoryEntry? = null
        dataStore.edit { prefs ->
            val list = decode(prefs[ENTRIES])
            val match = bestMatch(query, list) ?: return@edit
            prefs[ENTRIES] = json.encodeToString(list.filter { it.id != match.id })
            removed = match
        }
        return removed
    }

    /** Confident word match (exact › startsWith › contains › all-words); null otherwise. */
    private fun bestMatch(query: String, list: List<CoachMemoryEntry>): CoachMemoryEntry? {
        val q = query.lowercase().trim()
        val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun score(text: String): Int {
            val n = text.lowercase()
            return when {
                n == q -> 3
                n.startsWith(q) || q.startsWith(n) -> 2
                n.contains(q) || q.contains(n) -> 1
                words.isNotEmpty() && words.any { n.contains(it) } -> 0
                else -> -1
            }
        }
        return list.map { it to score(it.text) }.filter { it.second >= 0 }.maxByOrNull { it.second }?.first
    }

    private companion object {
        val ENTRIES = stringPreferencesKey("entries")
        const val MAX_ENTRIES = 50
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachMemoryStoreTest*"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/coach/CoachMemoryStore.kt app/src/test/java/com/zack/recomptracker/data/coach/CoachMemoryStoreTest.kt
git commit -m "feat(coach): CoachMemoryStore — flat editable list of user facts"
```

---

## Task 2: `remember` + `forget` tools

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachTools.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorMemoryTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.coach.CoachMemory
import com.zack.recomptracker.data.coach.CoachMemoryEntry
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class CoachToolExecutorMemoryTest {
    private val dateProvider = object : DateProvider { override fun today() = LocalDate.of(2026, 6, 5) }

    private class FakeMemory(seed: List<String> = emptyList()) : CoachMemory {
        val items = seed.mapIndexed { i, t -> CoachMemoryEntry("${i + 1}", t, "2026-06-05") }.toMutableList()
        override fun observe(): Flow<List<CoachMemoryEntry>> = flowOf(items)
        override suspend fun all() = items.toList()
        override suspend fun add(text: String): CoachMemoryEntry? {
            val e = CoachMemoryEntry("${items.size + 1}", text.trim(), "2026-06-05"); items.add(e); return e
        }
        override suspend fun update(id: String, text: String) {}
        override suspend fun delete(id: String) {}
        override suspend fun removeMatching(query: String): CoachMemoryEntry? {
            val m = items.firstOrNull { it.text.contains(query, ignoreCase = true) || query.contains(it.text, ignoreCase = true) }
            if (m != null) items.remove(m); return m
        }
    }

    private fun executor(mem: CoachMemory) = CoachToolExecutor(
        logRepository = mock<LogRepository>(),
        planRepository = mock<PlanRepository>(),
        dateProvider = dateProvider,
        coachMemory = mem,
    )

    @Test fun `remember adds a fact to memory`() = runTest {
        val mem = FakeMemory()
        val json = executor(mem).execute("remember", mapOf("text" to "Vegetarian"))
        assertTrue(json.contains("\"success\":true"))
        assertTrue(mem.items.any { it.text == "Vegetarian" })
    }

    @Test fun `remember rejects blank text`() = runTest {
        val json = executor(FakeMemory()).execute("remember", mapOf("text" to "  "))
        assertTrue(json.contains("error"))
    }

    @Test fun `forget removes a matching fact`() = runTest {
        val mem = FakeMemory(listOf("Vegetarian", "Trains at home"))
        val json = executor(mem).execute("forget", mapOf("text" to "vegetarian"))
        assertTrue(json.contains("\"success\":true"))
        assertTrue(mem.items.none { it.text == "Vegetarian" })
    }

    @Test fun `forget with no match returns an error`() = runTest {
        val json = executor(FakeMemory(listOf("Vegetarian"))).execute("forget", mapOf("text" to "deadlift"))
        assertTrue(json.contains("error"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorMemoryTest*"`
Expected: FAIL — `CoachToolExecutor` has no `coachMemory` param and no `remember`/`forget`.

- [ ] **Step 3: Add ctor param, dispatch, and the two tools**

In `CoachToolExecutor.kt` add the import `import com.zack.recomptracker.data.coach.CoachMemory` and a constructor param (after the existing ones, with a default so other call sites are unaffected):
```kotlin
    private val coachMemory: CoachMemory? = null,
```

Add dispatch entries in `execute(...)`:
```kotlin
        "remember" -> remember(args)
        "forget" -> forget(args)
```

Implement (reuse the existing `esc()`):
```kotlin
    private suspend fun remember(args: Map<String, String>): String {
        val text = args["text"]?.trim().orEmpty()
        if (text.isBlank()) return """{"error":"remember requires 'text'"}"""
        val mem = coachMemory ?: return """{"error":"memory unavailable"}"""
        val entry = mem.add(text) ?: return """{"error":"could not remember that"}"""
        return """{"success":true,"remembered":"${entry.text.esc()}"}"""
    }

    private suspend fun forget(args: Map<String, String>): String {
        val text = args["text"]?.trim().orEmpty()
        if (text.isBlank()) return """{"error":"forget requires 'text'"}"""
        val mem = coachMemory ?: return """{"error":"memory unavailable"}"""
        val removed = mem.removeMatching(text) ?: return """{"error":"nothing in memory matching '${text.esc()}'"}"""
        return """{"success":true,"forgot":"${removed.text.esc()}"}"""
    }
```

- [ ] **Step 4: Schemas in `CoachTools.kt` (NOT in COACH_WRITE_TOOLS)**

```kotlin
/** Cloud-coach memory tools. Deliberately NOT in COACH_WRITE_TOOLS — low-stakes, reversible in the
 *  Coach memory screen, so they run without a confirmation dialog. */
val MEMORY_TOOL_SCHEMAS: List<String> = listOf(
    """{"name":"remember","description":"Save a fact about the user to your memory (diet, injuries, preferences, context) when they ask you to remember something. It will show in the user's Coach memory screen.","parameters":{"type":"object","properties":{"text":{"type":"string","description":"The fact to remember, e.g. 'Vegetarian' or 'Bad left knee — no barbell squats'"}},"required":["text"]}}""",
    """{"name":"forget","description":"Remove a fact from your memory when the user asks you to forget it. Matches the closest stored fact.","parameters":{"type":"object","properties":{"text":{"type":"string","description":"The fact to forget"}},"required":["text"]}}""",
)
```

Append to the cloud list only:
```kotlin
val CLOUD_COACH_TOOL_SCHEMAS: List<String> = COACH_TOOL_SCHEMAS + SEARCH_WEB_TOOL_SCHEMA + ROUTINE_TOOL_SCHEMAS + MEAL_EDIT_TOOL_SCHEMAS + MEMORY_TOOL_SCHEMAS
```

Do NOT touch `COACH_WRITE_TOOLS`.

- [ ] **Step 5: Wire the store into the executor in `AppContainer.kt`**

Construct the store once (near the other `data/coach` stores, e.g. next to `coachJourneyStore`):
```kotlin
    val coachMemoryStore = CoachMemoryStore(context.applicationContext, dateProvider)
```
Add the import `import com.zack.recomptracker.data.coach.CoachMemoryStore`. Pass it to the `CoachToolExecutor(...)` construction:
```kotlin
        coachMemory = coachMemoryStore,
```

- [ ] **Step 6: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorMemoryTest*"` (Expected: 4 pass) and `./gradlew :app:compileDebugKotlin` (Expected: SUCCESS).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt app/src/main/java/com/zack/recomptracker/ai/CoachTools.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorMemoryTest.kt
git commit -m "feat(coach): remember/forget tools backed by CoachMemoryStore"
```

---

## Task 3: Inject memory into the chat prompt + guidelines + status labels

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolsAdapterMemoryTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Read the existing `CoachToolsAdapterTest` for how it constructs the adapter (fakes for `CoachReadTools` deps, `LogRepository`, `UserProfilePreferencesStore`, `journey`, etc.) and mirror it, passing a `CoachMemory` fake.

```kotlin
    @Test
    fun `systemPromptSnapshot includes a memory block when memory is non-empty`() = runTest {
        val adapter = adapterWith(memory = fakeMemory(listOf("Vegetarian", "Bad left knee")))
        val prompt = adapter.systemPromptSnapshot()
        assertTrue(prompt.contains("WHAT I KNOW ABOUT YOU"))
        assertTrue(prompt.contains("Vegetarian"))
        assertTrue(prompt.contains("Bad left knee"))
    }

    @Test
    fun `systemPromptSnapshot omits the memory block when memory is empty`() = runTest {
        val adapter = adapterWith(memory = fakeMemory(emptyList()))
        assertFalse(adapter.systemPromptSnapshot().contains("WHAT I KNOW ABOUT YOU"))
    }
```

Provide `adapterWith(memory)` + `fakeMemory(list)` helpers mirroring the existing adapter test setup (a `CoachMemory` returning the given entries from `all()`).

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolsAdapterMemoryTest*"`
Expected: FAIL — adapter has no `coachMemory` param / no memory block.

- [ ] **Step 3: Inject the memory block**

In `CoachToolsAdapter.kt`: add ctor param `private val coachMemory: com.zack.recomptracker.data.coach.CoachMemory = com.zack.recomptracker.data.coach.NoopCoachMemory,`. In `systemPromptSnapshot()`, read the entries and format a block, passing it into `buildPrompt`:
```kotlin
        val memoryBlock = coachMemory.all()
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { "- ${it.text}" }
            .orEmpty()
```
Add a `memoryBlock: String` parameter to `buildPrompt(...)` and, right after the JOURNEY block (before `Guidelines:`), append:
```kotlin
        if (memoryBlock.isNotBlank()) {
            appendLine()
            appendLine("=== WHAT I KNOW ABOUT YOU ===")
            appendLine(memoryBlock)
            appendLine("=== END ===")
        }
```
Pass `memoryBlock` in the `buildPrompt(...)` call.

- [ ] **Step 4: Guidelines + status labels**

In `CoachToolsAdapter.COACH_PROMPT_GUIDELINES`, append (match the `"- …\n" +` format):
```
- You have a memory of facts about the user (diet, injuries, preferences). Use remember when they ask you to remember something and forget when they ask you to drop it. Respect these facts in your advice, and don't store the same fact twice.
```
In `CloudCoachCoordinator.toolStatusText`, add:
```kotlin
        "remember" -> "Updating memory…"
        "forget" -> "Updating memory…"
```

- [ ] **Step 5: Wire the store into the adapter in `AppContainer.kt`**

At the `CoachToolsAdapter(...)` construction, pass:
```kotlin
        coachMemory = coachMemoryStore,
```

- [ ] **Step 6: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolsAdapterMemoryTest*"` (Expected: PASS) and `./gradlew :app:compileDebugKotlin` (Expected: SUCCESS).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/test/java/com/zack/recomptracker/ai/CoachToolsAdapterMemoryTest.kt
git commit -m "feat(coach): inject memory into chat prompt + remember/forget guidance"
```

---

## Task 4: `CoachMemoryViewModel`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/aicoach/CoachMemoryViewModel.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/aicoach/CoachMemoryViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ui.aicoach

import com.zack.recomptracker.data.coach.CoachMemory
import com.zack.recomptracker.data.coach.CoachMemoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachMemoryViewModelTest {
    private class FakeMemory : CoachMemory {
        val state = MutableStateFlow<List<CoachMemoryEntry>>(emptyList())
        override fun observe(): Flow<List<CoachMemoryEntry>> = state
        override suspend fun all() = state.value
        override suspend fun add(text: String): CoachMemoryEntry? {
            val e = CoachMemoryEntry("${state.value.size + 1}", text.trim(), "2026-06-05")
            state.value = state.value + e; return e
        }
        override suspend fun update(id: String, text: String) {
            state.value = state.value.map { if (it.id == id) it.copy(text = text) else it }
        }
        override suspend fun delete(id: String) { state.value = state.value.filterNot { it.id == id } }
        override suspend fun removeMatching(query: String): CoachMemoryEntry? = null
    }

    @Test fun `add appends and delete removes, reflected in state`() = runTest {
        val mem = FakeMemory()
        val vm = CoachMemoryViewModel(mem)
        vm.add("Vegetarian")
        vm.add("Bad knee")
        assertEquals(listOf("Vegetarian", "Bad knee"), mem.state.value.map { it.text })
        vm.delete(mem.state.value.first().id)
        assertEquals(listOf("Bad knee"), mem.state.value.map { it.text })
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachMemoryViewModelTest*"`
Expected: FAIL — `CoachMemoryViewModel` doesn't exist.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.zack.recomptracker.ui.aicoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.coach.CoachMemory
import com.zack.recomptracker.data.coach.CoachMemoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CoachMemoryViewModel(private val memory: CoachMemory) : ViewModel() {
    val entries: StateFlow<List<CoachMemoryEntry>> =
        memory.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String) { if (text.isNotBlank()) viewModelScope.launch { memory.add(text) } }
    fun update(id: String, text: String) { if (text.isNotBlank()) viewModelScope.launch { memory.update(id, text) } }
    fun delete(id: String) { viewModelScope.launch { memory.delete(id) } }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachMemoryViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/aicoach/CoachMemoryViewModel.kt app/src/test/java/com/zack/recomptracker/ui/aicoach/CoachMemoryViewModelTest.kt
git commit -m "feat(coach): CoachMemoryViewModel over CoachMemoryStore"
```

---

## Task 5: `CoachMemoryScreen` + nav + entry row (UI — user verifies on device)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/aicoach/CoachMemoryScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachScreen.kt` (entry row + an `onOpenCoachMemory` param)
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt` (route + wiring)
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (VM factory case)

- [ ] **Step 1: AppContainer VM factory case**

In the `ViewModelProvider.Factory` `when` block in `AppContainer.kt`, add:
```kotlin
            CoachMemoryViewModel::class.java -> CoachMemoryViewModel(container.coachMemoryStore)
```
Add the import `import com.zack.recomptracker.ui.aicoach.CoachMemoryViewModel`. (`container.coachMemoryStore` was created in Task 2.)

- [ ] **Step 2: The screen (design-system compliant)**

Create `CoachMemoryScreen.kt`. Read `docs/design-system.md` and an existing sub-screen (e.g. `ui/train/ExerciseStatsScreen.kt` or a screen using `ScreenScaffold`+`SubScreenHeader`) to match idioms. Skeleton:
```kotlin
package com.zack.recomptracker.ui.aicoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.NeutralCard
import com.zack.recomptracker.ui.component.ScreenScaffold
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun CoachMemoryScreen(viewModel: CoachMemoryViewModel, onBack: () -> Unit) {
    val appColors = LocalAppColors.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    ScreenScaffold(withNavBarInset = false) {
        item { SubScreenHeader(title = "Coach memory", onBack = onBack) }
        item {
            Text(
                "Facts the coach remembers about you. It reads these in every chat — add, edit, or remove anything.",
                style = AppType.cardSubtitle, color = appColors.textSecondary,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassInputField(
                    value = draft, onValueChange = { draft = it },
                    label = "Add a fact", modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                LiquidActionButton(text = "Add", onClick = {
                    if (draft.isNotBlank()) { viewModel.add(draft); draft = "" }
                }, isPrimary = true, small = true)
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "The coach doesn't know anything about you yet — add a fact above, or tell it in chat (\"remember I'm vegetarian\").",
                    style = AppType.cardSubtitle, color = appColors.textMuted,
                )
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                var editing by remember(entry.id) { mutableStateOf(entry.text) }
                NeutralCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassInputField(
                            value = editing, onValueChange = { editing = it },
                            label = "", modifier = Modifier.weight(1f),
                            onDone = { if (editing.isNotBlank() && editing != entry.text) viewModel.update(entry.id, editing) },
                        )
                        Icon(
                            Icons.Rounded.Delete, contentDescription = "Delete",
                            tint = appColors.textVeryMuted,
                            modifier = Modifier.padding(start = 8.dp).size(20.dp)
                                .clickableNoRipple { viewModel.delete(entry.id) },
                        )
                    }
                }
            }
        }
    }
}
```
NOTE: match the ACTUAL signatures of `GlassInputField` (label/onValueChange/onDone params), `LiquidActionButton`, and how the codebase does a tappable icon (there may be a helper; otherwise use `Modifier.clickable`). Inspect `GlassComponents.kt` and an existing screen and adapt — the goal is a labelled text field per row with inline edit-on-done + a delete icon, and an add field at top. Keep it design-system compliant (no raw `fontSize`/hex).

- [ ] **Step 3: Entry row in `AiCoachScreen` + nav param**

In `AiCoachScreen.kt`: add an `onOpenCoachMemory: () -> Unit` parameter, and (inside the AI-insights-enabled section, near the Notifications group) a row/card "What the coach knows about you" with a chevron that calls `onOpenCoachMemory()`. Match the existing row idiom in that screen.

- [ ] **Step 4: Route in `AppNavGraph`**

Add `const val CoachMemory = "coach_memory"` to `Routes`. Pass `onOpenCoachMemory = { navController.navigate(Routes.CoachMemory) }` into the `AiCoachScreen(...)` call. Add the destination (mirror the `StreakStats` sub-screen pattern):
```kotlin
        composable(Routes.CoachMemory) {
            CoachMemoryScreen(
                viewModel = viewModel<CoachMemoryViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
```
Add the imports for `CoachMemoryScreen` and `CoachMemoryViewModel`.

- [ ] **Step 5: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any signature mismatches against the real `GlassInputField`/`LiquidActionButton`/`AiCoachScreen` params.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/aicoach/CoachMemoryScreen.kt app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachScreen.kt app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(coach): Coach memory screen — view/add/edit/delete facts"
```

---

## Task 6: Full verification

- [ ] **Step 1: Boundary + full suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: only `InsightHarnessTest` fails (known network test); `AiCoachBoundaryTest` and all else green.

- [ ] **Step 2: APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: On-device (user)**

AI Coach → "What the coach knows about you" → add/edit/delete a fact. In chat: *"remember I'm vegetarian"* → appears in the screen and is respected in advice; *"forget I'm vegetarian"* → removed.

---

## Notes for the implementer

- `remember`/`forget` are intentionally NOT in `COACH_WRITE_TOOLS` — they run without a confirm dialog. Do not add them there.
- Reuse the existing private `esc()` in `CoachToolExecutor`; tools return JSON strings matching the existing contract.
- The store mirrors `CoachJourneyStore` (read it) — injectable `DataStore<Preferences>` + `Context` secondary ctor; deterministic "today" via `DateProvider`.
- UI signatures (`GlassInputField`, `LiquidActionButton`, `AiCoachScreen` row idiom): inspect the real files and match — the plan's screen code is a faithful skeleton, not guaranteed to match every param name.
- `AppContainer` is edited in Tasks 2/3/5 (construct store; wire executor, adapter, VM factory) — run tasks in order; each `git add`s only its files by explicit path (there are unrelated TEMP DEBUG commits in history — never `git add -A`).
