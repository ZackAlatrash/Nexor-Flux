# Recipe-from-Meal + AI Naming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user select items inside a meal slot and save them as a reusable recipe, and add an inline ✨ AI button to the Recipe Builder's name field that generates a funny "gym-bro" recipe name from the ingredients + macros.

**Architecture:** A pure `MealEntryEntity → RecipeIngredientEntity` mapper (data layer) feeds the existing Recipe Builder, pre-filled via a Base64-encoded `seedIngredients` nav arg. A new `RecipeNamer` (in `ai/`) mirrors the existing local/cloud routing used by `RoutingInsightCoordinator`: a `NameGenerator` SAM has Gemma (on-device) and OpenAI-compatible (cloud) implementations, and `RoutingRecipeNamer` picks between them by effective backend and exposes an `available` flag. Availability reuses the already-routed `aiInsightCoordinator.state`. Selection mode lives in `FoodLogViewModel`/`FoodScreen`.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, ViewModel + StateFlow, Room entities, kotlinx.serialization, kotlinx.coroutines, JUnit unit tests.

---

## File Structure

**Created**
- `app/src/main/java/com/zack/recomptracker/data/repository/MealToRecipe.kt` — pure `MealEntryEntity.toRecipeIngredient(sortOrder)`.
- `app/src/test/java/com/zack/recomptracker/data/MealToRecipeTest.kt`
- `app/src/main/java/com/zack/recomptracker/ai/RecipeNamePromptBuilder.kt` — prompt + pure `sanitize()`.
- `app/src/test/java/com/zack/recomptracker/ai/RecipeNamePromptBuilderTest.kt`
- `app/src/main/java/com/zack/recomptracker/ai/RecipeNamer.kt` — `RecipeNamer` interface, `NameGenerator` SAM, `LocalNameGenerator`, `CloudNameGenerator`, `RoutingRecipeNamer`, `StubRecipeNamer`.
- `app/src/test/java/com/zack/recomptracker/ai/RoutingRecipeNamerTest.kt`
- `app/src/main/java/com/zack/recomptracker/ui/today/RecipeSelection.kt` — selection model + pure `recipeIngredientsFromSelection(...)`.
- `app/src/test/java/com/zack/recomptracker/ui/today/RecipeSelectionTest.kt`

**Modified**
- `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` — construct `RecipeNamer`; inject into `RecipeBuilderViewModel`.
- `app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderViewModel.kt` — seed loading; AI naming state + actions.
- `app/src/test/java/com/zack/recomptracker/ui/RecipeBuilderViewModelTest.kt` — new naming + seed tests.
- `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt` — `seedIngredients` nav arg; `onCreateRecipeFromSelection` wiring on `FoodScreen`.
- `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt` — selection state + actions.
- `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt` — overflow menu, selection-mode rows, bottom action bar.
- `app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderScreen.kt` — glass cards; AI ✨ name field.

**Build/test commands** (run from repo root):
- Type-check: `./gradlew :app:compileDebugKotlin`
- All unit tests: `./gradlew :app:testDebugUnitTest`
- One test class: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.MealToRecipeTest"`

---

## Task 1: Pure MealEntry → RecipeIngredient mapper

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/MealToRecipe.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/MealToRecipeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/data/MealToRecipeTest.kt`:

```kotlin
package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.repository.toRecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Test

class MealToRecipeTest {

    private fun meal() = MealEntryEntity(
        id = 7,
        date = "2026-06-13",
        mealType = "lunch",
        name = "Chicken breast",
        calories = 280,
        proteinG = 52.0,
        carbsG = 0.0,
        fatG = 6.0,
        slotId = 3,
        amountGrams = 200.0,
        basePer100Calories = 140,
        basePer100ProteinG = 26.0,
        basePer100CarbsG = 0.0,
        basePer100FatG = 3.0,
        entryServingName = "fillet",
        entryServingGrams = 120.0,
        loggedByServings = true,
        planned = false,
    )

    @Test
    fun `maps every macro and base field, applies sortOrder, resets ids`() {
        val ing = meal().toRecipeIngredient(sortOrder = 4)

        assertEquals(0L, ing.id)
        assertEquals(0L, ing.recipeId)
        assertEquals(4, ing.sortOrder)
        assertEquals("Chicken breast", ing.name)
        assertEquals(280, ing.calories)
        assertEquals(52.0, ing.proteinG, 0.0)
        assertEquals(0.0, ing.carbsG, 0.0)
        assertEquals(6.0, ing.fatG, 0.0)
        assertEquals(200.0, ing.amountGrams)
        assertEquals(140, ing.basePer100Calories)
        assertEquals(26.0, ing.basePer100ProteinG)
        assertEquals(0.0, ing.basePer100CarbsG)
        assertEquals(3.0, ing.basePer100FatG)
        assertEquals("fillet", ing.entryServingName)
        assertEquals(120.0, ing.entryServingGrams)
        assertEquals(true, ing.loggedByServings)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.MealToRecipeTest"`
Expected: FAIL — unresolved reference `toRecipeIngredient`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/data/repository/MealToRecipe.kt`:

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity

/**
 * Converts a logged meal entry into a recipe ingredient. The macro, amount, base-per-100,
 * and serving fields map 1:1; [sortOrder] comes from the ingredient's position in the new
 * recipe, and id/recipeId reset to 0 (assigned on save).
 */
fun MealEntryEntity.toRecipeIngredient(sortOrder: Int): RecipeIngredientEntity =
    RecipeIngredientEntity(
        id = 0,
        recipeId = 0,
        name = name,
        sortOrder = sortOrder,
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        amountGrams = amountGrams,
        basePer100Calories = basePer100Calories,
        basePer100ProteinG = basePer100ProteinG,
        basePer100CarbsG = basePer100CarbsG,
        basePer100FatG = basePer100FatG,
        entryServingName = entryServingName,
        entryServingGrams = entryServingGrams,
        loggedByServings = loggedByServings,
    )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.MealToRecipeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/MealToRecipe.kt app/src/test/java/com/zack/recomptracker/data/MealToRecipeTest.kt
git commit -m "feat(recipes): pure MealEntry -> RecipeIngredient mapper"
```

---

## Task 2: Recipe name prompt builder + sanitizer

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/RecipeNamePromptBuilder.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/RecipeNamePromptBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/RecipeNamePromptBuilderTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeNamePromptBuilderTest {

    private val builder = RecipeNamePromptBuilder()

    private fun ing(name: String) = RecipeIngredientEntity(
        name = name, calories = 100, proteinG = 5.0, carbsG = 10.0, fatG = 2.0,
    )

    @Test
    fun `user prompt names every ingredient and the macro totals`() {
        val prompt = builder.buildUserPrompt(
            listOf(ing("Chicken breast"), ing("Fries")),
            MacroTotals(calories = 680, proteinG = 57.0, carbsG = 40.0, fatG = 14.0),
        )
        assertTrue(prompt.contains("Chicken breast"))
        assertTrue(prompt.contains("Fries"))
        assertTrue(prompt.contains("680"))
        assertTrue(prompt.contains("57"))
    }

    @Test
    fun `sanitize strips quotes`() {
        assertEquals("Anabolic Oats", RecipeNamePromptBuilder.sanitize("\"Anabolic Oats\""))
    }

    @Test
    fun `sanitize takes first non-empty line`() {
        assertEquals("Quad Slayer", RecipeNamePromptBuilder.sanitize("\n  Quad Slayer\nsome rambling explanation"))
    }

    @Test
    fun `sanitize strips markdown and label prefix`() {
        assertEquals("Gains Goblin Stew", RecipeNamePromptBuilder.sanitize("**Gains Goblin Stew**"))
        assertEquals("Bulk Bowl", RecipeNamePromptBuilder.sanitize("Recipe name: Bulk Bowl"))
    }

    @Test
    fun `sanitize caps length`() {
        val long = "A".repeat(100)
        assertEquals(RecipeNamePromptBuilder.MAX_NAME_LENGTH, RecipeNamePromptBuilder.sanitize(long).length)
    }

    @Test
    fun `sanitize returns blank for empty input`() {
        assertEquals("", RecipeNamePromptBuilder.sanitize("   \n  "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RecipeNamePromptBuilderTest"`
Expected: FAIL — unresolved reference `RecipeNamePromptBuilder`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/ai/RecipeNamePromptBuilder.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity

/**
 * Builds the prompt that asks the model for a short, funny "gym-bro" recipe name, and
 * sanitizes the model's reply down to a single clean name. [sanitize] is pure so it can
 * be unit-tested and reused by both the local and cloud generators.
 */
class RecipeNamePromptBuilder {

    fun buildUserPrompt(ingredients: List<RecipeIngredientEntity>, totals: MacroTotals): String {
        val items = ingredients.joinToString(", ") { it.name }
        return buildString {
            append("Invent ONE over-the-top gym-bro name for a meal made of: ")
            append(items).append(".\n")
            append("Macros: ${totals.calories} kcal, ${totals.proteinG.toInt()}g protein, ")
            append("${totals.carbsG.toInt()}g carbs, ${totals.fatG.toInt()}g fat.\n")
            append("Lean into the macros (high protein = \"Anabolic\", high calories = \"Bulk\", etc.). ")
            append("2 to 4 words. Examples: Anabolic Oats, Quad Slayer Bowl, Gains Goblin Stew.\n")
            append("Name:")
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 42

        const val SYSTEM_PROMPT =
            "You are a gym-bro recipe namer. You invent short, funny, slightly unhinged names " +
                "for meals, like a meathead fitness influencer. Reply with ONLY the name — " +
                "no quotes, no explanation, no trailing punctuation."

        private val MARKDOWN = Regex("[*_`#>\"]")
        private val LABEL_PREFIX = Regex("^(recipe\\s+)?name\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE)

        /** Reduce a raw model reply to a single clean recipe name. */
        fun sanitize(raw: String): String {
            val firstLine = raw.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }.orEmpty()
            return firstLine
                .replace(MARKDOWN, "")
                .replace(LABEL_PREFIX, "")
                .trim()
                .trim('"', '\'', '.', ':', '-', ' ')
                .take(MAX_NAME_LENGTH)
                .trim()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RecipeNamePromptBuilderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/RecipeNamePromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/RecipeNamePromptBuilderTest.kt
git commit -m "feat(recipes): gym-bro recipe-name prompt builder + sanitizer"
```

---

## Task 3: RecipeNamer (interface, generators, routing, stub)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/RecipeNamer.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/RoutingRecipeNamerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/RoutingRecipeNamerTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingRecipeNamerTest {

    private fun ings() = listOf(
        RecipeIngredientEntity(name = "Oats", calories = 300, proteinG = 10.0, carbsG = 50.0, fatG = 6.0),
    )

    private fun namer(
        backend: AiBackend = AiBackend.LOCAL,
        local: NameGenerator,
        cloud: NameGenerator,
        timeoutMs: Long = 1_000,
    ) = RoutingRecipeNamer(
        local = local,
        cloud = cloud,
        effectiveBackend = MutableStateFlow(backend),
        available = MutableStateFlow(true),
        timeoutMs = timeoutMs,
    )

    @Test
    fun `routes to local generator when backend is LOCAL`() = runTest {
        var localCalled = false
        val result = namer(
            backend = AiBackend.LOCAL,
            local = NameGenerator { _, _ -> localCalled = true; "Anabolic Oats" },
            cloud = NameGenerator { _, _ -> "WRONG" },
        ).generate(ings(), MacroTotals())
        assertTrue(localCalled)
        assertEquals("Anabolic Oats", result.getOrNull())
    }

    @Test
    fun `routes to cloud generator when backend is CLOUD`() = runTest {
        val result = namer(
            backend = AiBackend.CLOUD,
            local = NameGenerator { _, _ -> "WRONG" },
            cloud = NameGenerator { _, _ -> "\"Cloud Cluck\"" },
        ).generate(ings(), MacroTotals())
        assertEquals("Cloud Cluck", result.getOrNull()) // sanitized
    }

    @Test
    fun `generator exception becomes failure`() = runTest {
        val result = namer(
            local = NameGenerator { _, _ -> throw RuntimeException("boom") },
            cloud = NameGenerator { _, _ -> "x" },
        ).generate(ings(), MacroTotals())
        assertTrue(result.isFailure)
    }

    @Test
    fun `blank name becomes failure`() = runTest {
        val result = namer(
            local = NameGenerator { _, _ -> "   " },
            cloud = NameGenerator { _, _ -> "x" },
        ).generate(ings(), MacroTotals())
        assertTrue(result.isFailure)
    }

    @Test
    fun `timeout becomes failure`() = runTest {
        val result = namer(
            local = NameGenerator { _, _ -> delay(10_000); "too late" },
            cloud = NameGenerator { _, _ -> "x" },
            timeoutMs = 50,
        ).generate(ings(), MacroTotals())
        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RoutingRecipeNamerTest"`
Expected: FAIL — unresolved references `RoutingRecipeNamer`, `NameGenerator`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/ai/RecipeNamer.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout

/** Generates one gym-bro recipe name. */
interface RecipeNamer {
    /** Whether a name can currently be generated (AI enabled + model present / cloud configured). */
    val available: StateFlow<Boolean>

    /** Returns a sanitized name on success, or a failure on timeout / error / empty output. */
    suspend fun generate(
        ingredients: List<RecipeIngredientEntity>,
        totals: MacroTotals,
    ): Result<String>
}

/** Backend-agnostic single-shot text generator. Implementations throw on failure. */
fun interface NameGenerator {
    suspend fun generate(systemPrompt: String, userPrompt: String): String
}

/** On-device Gemma generator. Shares the engine's inferenceLock via [GemmaInsightService]. */
class LocalNameGenerator(
    private val serviceHolder: GemmaServiceHolder,
    private val variant: () -> ModelVariant,
) : NameGenerator {
    override suspend fun generate(systemPrompt: String, userPrompt: String): String {
        val modelPath = serviceHolder.modelFileFor(variant()).absolutePath
        val service = serviceHolder.getOrCreateService(modelPath)
        service.ensureInitialized()
        val sb = StringBuilder()
        service.generateExplanation("$systemPrompt\n\n$userPrompt").collect { sb.append(it) }
        return sb.toString()
    }
}

/** OpenAI-compatible cloud generator. */
class CloudNameGenerator(
    private val client: OpenAiCompatClient,
    private val configFlow: StateFlow<CloudConfig?>,
) : NameGenerator {
    override suspend fun generate(systemPrompt: String, userPrompt: String): String {
        val config = configFlow.value ?: error("Cloud AI not configured")
        val sb = StringBuilder()
        client.streamCompletion(config = config, systemPrompt = systemPrompt, userPrompt = userPrompt)
            .collect { sb.append(it) }
        return sb.toString()
    }
}

/**
 * Picks the local or cloud generator by [effectiveBackend] (same rule as
 * [RoutingInsightCoordinator]), wraps the call in a timeout, and sanitizes the output.
 */
class RoutingRecipeNamer(
    private val local: NameGenerator,
    private val cloud: NameGenerator,
    private val effectiveBackend: StateFlow<AiBackend>,
    override val available: StateFlow<Boolean>,
    private val promptBuilder: RecipeNamePromptBuilder = RecipeNamePromptBuilder(),
    private val timeoutMs: Long = 45_000L,
) : RecipeNamer {

    override suspend fun generate(
        ingredients: List<RecipeIngredientEntity>,
        totals: MacroTotals,
    ): Result<String> {
        val generator = if (effectiveBackend.value == AiBackend.CLOUD) cloud else local
        val userPrompt = promptBuilder.buildUserPrompt(ingredients, totals)
        return try {
            val raw = withTimeout(timeoutMs) {
                generator.generate(RecipeNamePromptBuilder.SYSTEM_PROMPT, userPrompt)
            }
            val name = RecipeNamePromptBuilder.sanitize(raw)
            if (name.isBlank()) Result.failure(IllegalStateException("Empty name"))
            else Result.success(name)
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/** Always-unavailable namer for tests/previews. */
class StubRecipeNamer(
    override val available: StateFlow<Boolean> = MutableStateFlow(false),
    private val name: String = "Anabolic Oats",
) : RecipeNamer {
    override suspend fun generate(
        ingredients: List<RecipeIngredientEntity>,
        totals: MacroTotals,
    ): Result<String> = Result.success(name)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RoutingRecipeNamerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/RecipeNamer.kt app/src/test/java/com/zack/recomptracker/ai/RoutingRecipeNamerTest.kt
git commit -m "feat(recipes): RecipeNamer with local/cloud routing"
```

---

## Task 4: Wire RecipeNamer into AppContainer

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

This task only compiles (no new test); verified by `compileDebugKotlin` and the existing suite.

- [ ] **Step 1: Add imports**

In `AppContainer.kt`, add to the `com.zack.recomptracker.ai` import group:

```kotlin
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.CloudNameGenerator
import com.zack.recomptracker.ai.LocalNameGenerator
import com.zack.recomptracker.ai.RecipeNamer
import com.zack.recomptracker.ai.RoutingRecipeNamer
```

Ensure these flow operators are imported (add any missing):

```kotlin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
```

- [ ] **Step 2: Construct the RecipeNamer**

Immediately AFTER the `aiInsightCoordinator` assignment (the `RoutingInsightCoordinator(...)` block ending around line 271) and BEFORE `coachCoordinator`, add:

```kotlin
    // Effective backend for naming — same rule as RoutingInsightCoordinator.
    private val recipeNamerBackend: StateFlow<AiBackend> =
        combine(uiPreferences.aiBackend, cloudConfigComplete) { backend, complete ->
            if (backend == AiBackend.CLOUD && complete) AiBackend.CLOUD else AiBackend.LOCAL
        }.stateIn(appScope, SharingStarted.Eagerly, AiBackend.LOCAL)

    // Naming is available exactly when the routed insight model is usable (covers
    // AI-enabled, backend, cloud-config, and on-device model presence in one signal).
    private val recipeNamerAvailable: StateFlow<Boolean> =
        aiInsightCoordinator.state.map { state ->
            when (state) {
                AiInsightState.Disabled,
                AiInsightState.ModelMissing,
                is AiInsightState.Downloading,
                AiInsightState.DownloadFailed,
                AiInsightState.ModelVerifying -> false
                else -> true
            }
        }.stateIn(appScope, SharingStarted.Eagerly, false)

    val recipeNamer: RecipeNamer = RoutingRecipeNamer(
        local = LocalNameGenerator(gemmaServiceHolder) { gemmaInsightCoordinator.selectedModel.value },
        cloud = CloudNameGenerator(openAiCompatClient, cloudConfigFlow),
        effectiveBackend = recipeNamerBackend,
        available = recipeNamerAvailable,
    )
```

> Note: `gemmaInsightCoordinator`, `gemmaServiceHolder`, `openAiCompatClient`, `cloudConfigFlow`, `cloudConfigComplete`, `uiPreferences`, and `appScope` are all already declared in this class (see lines ~116–270). `combine` is already imported (used for `cloudConfigComplete`).

- [ ] **Step 3: Inject into RecipeBuilderViewModel factory**

Replace the existing factory branch:

```kotlin
            RecipeBuilderViewModel::class.java -> RecipeBuilderViewModel(
                recipeRepository = container.recipeRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            )
```

with:

```kotlin
            RecipeBuilderViewModel::class.java -> RecipeBuilderViewModel(
                recipeRepository = container.recipeRepository,
                recipeNamer = container.recipeNamer,
                savedStateHandle = extras.createSavedStateHandle(),
            )
```

> This will not compile until Task 5 adds the `recipeNamer` constructor parameter. That is expected — Step 4 below is run after Task 5. If executing strictly in order, skip the verify here and proceed to Task 5, then return.

- [ ] **Step 4: Verify (after Task 5 lands)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit (after Task 5 lands)**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(recipes): construct and inject RecipeNamer"
```

---

## Task 5: RecipeBuilderViewModel — seed loading + AI naming

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderViewModel.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/RecipeBuilderViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

In `app/src/test/java/com/zack/recomptracker/ui/RecipeBuilderViewModelTest.kt`, add these imports at the top (with the others):

```kotlin
import com.zack.recomptracker.ai.RecipeNamer
import com.zack.recomptracker.core.model.MacroTotals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
```

> The test below builds the encoded seed with `Json.encodeToString(list)`; the imports above make the reified extension resolve.

Add a fake namer + helper inside the class:

```kotlin
    private class FakeNamer(
        private val result: Result<String>,
        avail: Boolean = true,
    ) : RecipeNamer {
        override val available: StateFlow<Boolean> = MutableStateFlow(avail)
        var calls = 0
        override suspend fun generate(
            ingredients: List<RecipeIngredientEntity>,
            totals: MacroTotals,
        ): Result<String> { calls++; return result }
    }

    private fun seedHandle(json: String) = SavedStateHandle(mapOf("seedIngredients" to json))
```

Add the tests:

```kotlin
    @Test
    fun `generateName fills name on success`() = runTest {
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("Gains Goblin Stew")), SavedStateHandle())
        vm.addIngredient(ingredient("Oats"))
        vm.generateName()
        assertEquals("Gains Goblin Stew", vm.uiState.value.name)
        assertEquals(false, vm.uiState.value.isGeneratingName)
    }

    @Test
    fun `generateName with no ingredients is a no-op`() = runTest {
        val namer = FakeNamer(Result.success("X"))
        val vm = RecipeBuilderViewModel(stubRepo(), namer, SavedStateHandle())
        vm.generateName()
        assertEquals(0, namer.calls)
        assertEquals("", vm.uiState.value.name)
    }

    @Test
    fun `generateName failure sets error message and leaves name untouched`() = runTest {
        val vm = RecipeBuilderViewModel(
            stubRepo(), FakeNamer(Result.failure(RuntimeException("boom"))), SavedStateHandle(),
        )
        vm.onNameChanged("My Name")
        vm.addIngredient(ingredient("Oats"))
        vm.generateName()
        assertEquals("My Name", vm.uiState.value.name)
        assertTrue(vm.uiState.value.message?.isNotBlank() == true)
        assertEquals(false, vm.uiState.value.isGeneratingName)
    }

    @Test
    fun `seed ingredients are loaded for a new recipe`() {
        val json = Json.encodeToString(listOf(ingredient("Chicken"), ingredient("Fries")))
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("X")), seedHandle(encoded))
        assertEquals(listOf("Chicken", "Fries"), vm.uiState.value.ingredients.map { it.name })
    }
```

> Note: update the four EXISTING `RecipeBuilderViewModel(...)` constructions in this file to pass a namer, e.g. `RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("X")), SavedStateHandle())`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.RecipeBuilderViewModelTest"`
Expected: FAIL — constructor arity mismatch / unresolved `generateName`, `isGeneratingName`.

- [ ] **Step 3: Write the implementation**

Replace the contents of `RecipeBuilderViewModel.kt` with:

```kotlin
package com.zack.recomptracker.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.RecipeNamer
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.repository.RecipeRepository
import com.zack.recomptracker.ui.component.MessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class RecipeBuilderUiState(
    val recipeId: Long? = null,
    val name: String = "",
    val ingredients: List<RecipeIngredientEntity> = emptyList(),
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val message: String? = null,
    val messageKind: MessageKind = MessageKind.ERROR,
    /** Whether the ✨ AI-name button should be shown at all (model present / cloud configured). */
    val aiAvailable: Boolean = false,
    /** True while a name is being generated. */
    val isGeneratingName: Boolean = false,
)

class RecipeBuilderViewModel(
    private val recipeRepository: RecipeRepository,
    private val recipeNamer: RecipeNamer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeBuilderUiState())
    val uiState: StateFlow<RecipeBuilderUiState> = _uiState

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack

    init {
        val recipeId = savedStateHandle.get<Long>("recipeId")?.takeIf { it != -1L }
        if (recipeId != null) {
            _uiState.update { it.copy(recipeId = recipeId) }
            viewModelScope.launch {
                val existing = recipeRepository.getById(recipeId)
                if (existing != null) {
                    _uiState.update { it.copy(name = existing.recipe.name, ingredients = existing.ingredients) }
                }
            }
        } else {
            // New recipe: load seed ingredients passed from a meal slot, if any.
            decodeSeed(savedStateHandle.get<String>("seedIngredients"))?.let { seeded ->
                _uiState.update { it.copy(ingredients = seeded) }
            }
        }

        viewModelScope.launch {
            recipeNamer.available.collect { avail -> _uiState.update { it.copy(aiAvailable = avail) } }
        }
    }

    private fun decodeSeed(encoded: String?): List<RecipeIngredientEntity>? {
        if (encoded.isNullOrBlank()) return null
        return try {
            val json = String(java.util.Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
            Json.decodeFromString<List<RecipeIngredientEntity>>(json)
        } catch (_: Exception) {
            null
        }
    }

    fun onNameChanged(value: String) =
        _uiState.update { it.copy(name = value, isDirty = true, message = null) }

    fun addIngredient(ingredient: RecipeIngredientEntity) =
        _uiState.update { it.copy(ingredients = it.ingredients + ingredient, isDirty = true) }

    fun removeIngredientAt(index: Int) = _uiState.update { state ->
        state.copy(
            ingredients = state.ingredients.toMutableList().also { it.removeAt(index) },
            isDirty = true,
        )
    }

    fun editIngredientAt(index: Int, updated: RecipeIngredientEntity) = _uiState.update { state ->
        val list = state.ingredients.toMutableList()
        list[index] = updated
        state.copy(ingredients = list, isDirty = true)
    }

    /** Generate (or reroll) a gym-bro name from the current ingredients + macros. */
    fun generateName() {
        val state = _uiState.value
        if (state.ingredients.isEmpty() || state.isGeneratingName) return
        _uiState.update { it.copy(isGeneratingName = true, message = null) }
        viewModelScope.launch {
            val totals = MacroTotals(
                calories = state.ingredients.sumOf { it.calories },
                proteinG = state.ingredients.sumOf { it.proteinG },
                carbsG = state.ingredients.sumOf { it.carbsG },
                fatG = state.ingredients.sumOf { it.fatG },
            )
            val result = recipeNamer.generate(state.ingredients, totals)
            result.fold(
                onSuccess = { name ->
                    _uiState.update { it.copy(name = name, isGeneratingName = false, isDirty = true) }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isGeneratingName = false,
                            message = "Couldn't think of a name — try again.",
                            messageKind = MessageKind.ERROR,
                        )
                    }
                },
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(message = "Enter a recipe name.", messageKind = MessageKind.ERROR) }
            return
        }
        if (state.ingredients.isEmpty()) {
            _uiState.update { it.copy(message = "Add at least one ingredient.", messageKind = MessageKind.ERROR) }
            return
        }
        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            val recipeId = state.recipeId
            if (recipeId == null) {
                recipeRepository.saveRecipe(state.name, state.ingredients)
            } else {
                recipeRepository.updateRecipe(recipeId, state.name, state.ingredients)
            }
            _navigateBack.value = true
        }
    }

    fun delete() {
        val recipeId = _uiState.value.recipeId ?: return
        viewModelScope.launch {
            recipeRepository.deleteRecipe(recipeId)
            _navigateBack.value = true
        }
    }

    fun onNavigateBackConsumed() {
        _navigateBack.value = false
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.RecipeBuilderViewModelTest"`
Expected: PASS (all old + new tests).

- [ ] **Step 5: Verify AppContainer now compiles (completes Task 4)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderViewModel.kt app/src/test/java/com/zack/recomptracker/ui/RecipeBuilderViewModelTest.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(recipes): recipe builder seed loading + AI naming"
```

---

## Task 6: Nav graph — seedIngredients arg + FoodScreen wiring

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

Compile-only task (UI wiring); verified by `compileDebugKotlin` after Tasks 7–8 add the FoodScreen param.

- [ ] **Step 1: Extend the RecipeBuilder route + builder**

Replace:

```kotlin
    const val RecipeBuilder = "recipe_builder?recipeId={recipeId}"
    fun recipeBuilder(recipeId: Long? = null) = "recipe_builder?recipeId=${recipeId ?: -1L}"
```

with:

```kotlin
    const val RecipeBuilder = "recipe_builder?recipeId={recipeId}&seedIngredients={seedIngredients}"
    fun recipeBuilder(recipeId: Long? = null, seedIngredients: String? = null): String {
        val seedPart = seedIngredients?.let { "&seedIngredients=$it" }.orEmpty()
        return "recipe_builder?recipeId=${recipeId ?: -1L}$seedPart"
    }
```

> `seedIngredients` is a URL-safe Base64 string (no `+`, `/`, `=` once padding is stripped), so it is safe to splice straight into the route.

- [ ] **Step 2: Add the nav argument**

In the `composable(route = Routes.RecipeBuilder, ...)` block, replace the `arguments = listOf(...)` with:

```kotlin
            arguments = listOf(
                androidx.navigation.navArgument("recipeId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
                androidx.navigation.navArgument("seedIngredients") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
```

> The `seedIngredients` arg flows automatically into `RecipeBuilderViewModel`'s `SavedStateHandle`; no extra reading is needed in the composable.

- [ ] **Step 3: Wire FoodScreen → recipe builder**

In the `FoodScreen(...)` call (around line 124), add a new callback parameter after `onEditEntryAmount`:

```kotlin
                onCreateRecipeFromSelection = { ingredients ->
                    val json = Json.encodeToString(ingredients)
                    val seed = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(json.toByteArray(Charsets.UTF_8))
                    navController.navigate(Routes.recipeBuilder(seedIngredients = seed))
                },
```

Add these imports at the top of the file if missing:

```kotlin
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
```

> `Json.encodeToString(ingredients)` — `ingredients` is `List<RecipeIngredientEntity>` and `RecipeIngredientEntity` is `@Serializable`, so the reified extension serializes the list. `.encodeToString(...)` on the next line is `java.util.Base64.Encoder.encodeToString(ByteArray)` — a different method, no conflict.

> `onCreateRecipeFromSelection: (List<RecipeIngredientEntity>) -> Unit` is added to `FoodScreen`'s signature in Task 8. This file will not compile until then — expected.

- [ ] **Step 4: Verify (after Task 8 lands)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit (after Task 8 lands)**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(recipes): seedIngredients nav arg + food screen wiring"
```

---

## Task 7: FoodLogViewModel — selection state + pure mapping helper

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/today/RecipeSelection.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ui/today/RecipeSelectionTest.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt`

- [ ] **Step 1: Write the failing test for the pure helper**

First confirm where `MealSlotWithEntries` is declared:

Run: `grep -rn "class MealSlotWithEntries\|data class MealSlotWithEntries" app/src/main/java`
Expected: one declaration in `ui/today` (note its exact constructor: `slot`, `entries`, `totals`).

Create `app/src/test/java/com/zack/recomptracker/ui/today/RecipeSelectionTest.kt`:

```kotlin
package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeSelectionTest {

    private fun entry(id: Long, name: String) = MealEntryEntity(
        id = id, date = "2026-06-13", mealType = "lunch", name = name,
        calories = 100, proteinG = 5.0, carbsG = 10.0, fatG = 2.0, slotId = 1,
    )

    private fun slot(vararg entries: MealEntryEntity) = MealSlotWithEntries(
        slot = MealSlotEntity(id = 1, name = "Lunch", sortOrder = 0),
        entries = entries.toList(),
        totals = MacroTotals(),
    )

    @Test
    fun `returns only selected entries, ordered, mapped with sortOrder`() {
        val slots = listOf(slot(entry(10, "Chicken"), entry(11, "Ketchup"), entry(12, "Fries")))
        val selection = RecipeSelection(slotId = 1, selectedIds = setOf(10L, 12L))

        val result = recipeIngredientsFromSelection(slots, selection)

        assertEquals(listOf("Chicken", "Fries"), result.map { it.name })
        assertEquals(listOf(0, 1), result.map { it.sortOrder })
    }

    @Test
    fun `null selection returns empty`() {
        val slots = listOf(slot(entry(10, "Chicken")))
        assertEquals(emptyList<Any>(), recipeIngredientsFromSelection(slots, null))
    }

    @Test
    fun `empty selectedIds returns empty`() {
        val slots = listOf(slot(entry(10, "Chicken")))
        assertEquals(0, recipeIngredientsFromSelection(slots, RecipeSelection(1, emptySet())).size)
    }
}
```

> If the `grep` shows `MealSlotWithEntries` or `MealSlotEntity` constructors differ from the above, adjust the test's constructor calls to match (do not change production types).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.today.RecipeSelectionTest"`
Expected: FAIL — unresolved `RecipeSelection`, `recipeIngredientsFromSelection`.

- [ ] **Step 3: Create the selection model + pure helper**

Create `app/src/main/java/com/zack/recomptracker/ui/today/RecipeSelection.kt`:

```kotlin
package com.zack.recomptracker.ui.today

import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.repository.toRecipeIngredient

/** Active "save as recipe" selection within a single meal slot. */
data class RecipeSelection(
    val slotId: Long,
    val selectedIds: Set<Long>,
)

/**
 * Maps the entries ticked in [selection]'s slot into recipe ingredients, preserving the
 * slot's display order and assigning sequential sortOrder. Returns empty when nothing is
 * selected or the slot is gone.
 */
fun recipeIngredientsFromSelection(
    slots: List<MealSlotWithEntries>,
    selection: RecipeSelection?,
): List<RecipeIngredientEntity> {
    if (selection == null || selection.selectedIds.isEmpty()) return emptyList()
    val slot = slots.firstOrNull { it.slot.id == selection.slotId } ?: return emptyList()
    return slot.entries
        .filter { it.id in selection.selectedIds }
        .mapIndexed { index, entry -> entry.toRecipeIngredient(index) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.today.RecipeSelectionTest"`
Expected: PASS.

- [ ] **Step 5: Add selection state + actions to FoodLogViewModel**

In `FoodLogViewModel.kt`, add `recipeSelection` to `FoodLogUiState` (after `slotsEditMode`):

```kotlin
    /** Active "save as recipe" selection, or null when not selecting. */
    val recipeSelection: RecipeSelection? = null,
```

Add these methods to the `FoodLogViewModel` class (e.g. after `toggleEditMode`):

```kotlin
    fun startRecipeSelection(slotId: Long) =
        _uiState.update { it.copy(recipeSelection = RecipeSelection(slotId, emptySet())) }

    fun toggleRecipeSelection(entryId: Long) = _uiState.update { state ->
        val sel = state.recipeSelection ?: return@update state
        val ids = if (entryId in sel.selectedIds) sel.selectedIds - entryId else sel.selectedIds + entryId
        state.copy(recipeSelection = sel.copy(selectedIds = ids))
    }

    fun cancelRecipeSelection() = _uiState.update { it.copy(recipeSelection = null) }

    /** Ingredients for the current selection, in slot order. Empty if nothing selected. */
    fun selectedRecipeIngredients(): List<RecipeIngredientEntity> =
        recipeIngredientsFromSelection(_uiState.value.slots, _uiState.value.recipeSelection)
```

Add the imports at the top of `FoodLogViewModel.kt`:

```kotlin
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
```

Also clear any active selection when the viewed date changes (stale entry IDs otherwise). In the existing `selectDate` function, add the reset to the `_uiState.update` call:

```kotlin
    fun selectDate(date: LocalDate) {
        val clamped = date.coerceIn(today.minusDays(NAV_WINDOW_DAYS), today.plusDays(NAV_WINDOW_DAYS))
        _selectedDate.value = clamped
        _uiState.update { it.copy(selectedDate = clamped, recipeSelection = null) }
    }
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/RecipeSelection.kt app/src/test/java/com/zack/recomptracker/ui/today/RecipeSelectionTest.kt app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt
git commit -m "feat(recipes): meal selection state + ingredient mapping"
```

---

## Task 8: FoodScreen — overflow menu, selection mode, action bar

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`

UI task — verified by `compileDebugKotlin` and manual run. Reference the existing `LockedSlotCard` (line ~636) and `SlotEntryRow` (line ~757).

- [ ] **Step 1: Add the callback to FoodScreen's signature + plumb VM selection**

Add a parameter to the top-level `FoodScreen` composable signature:

```kotlin
    onCreateRecipeFromSelection: (List<com.zack.recomptracker.data.local.entity.RecipeIngredientEntity>) -> Unit = {},
```

Inside `FoodScreen`, read selection state from the existing `state` (the collected `uiState`). Where each slot's `LockedSlotCard` is rendered, pass the selection props (see Steps 2–3). Where the user confirms, call:

```kotlin
                            onSaveSelection = {
                                onCreateRecipeFromSelection(viewModel.selectedRecipeIngredients())
                                viewModel.cancelRecipeSelection()
                            },
```

- [ ] **Step 2: Add overflow menu + selection state to LockedSlotCard**

Add these parameters to `LockedSlotCard`:

```kotlin
    isSelecting: Boolean,
    selectedIds: Set<Long>,
    onStartRecipeSelection: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onCancelSelection: () -> Unit,
    onSaveSelection: () -> Unit,
```

In the slot header `Row` (where the `LiquidActionButton("＋ Add")` is), wrap the trailing controls so that — only when `hasEntries && !isSelecting` — an overflow button precedes "＋ Add":

```kotlin
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasEntries && !isSelecting) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0FFFFFFF))
                                .border(1.dp, Color(0x17FFFFFF), RoundedCornerShape(8.dp))
                                .clickable(remember { MutableInteractionSource() }, null) { menuOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⋯", fontSize = 15.sp, color = Color(0xBFFFFFFF))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Save as recipe") },
                                onClick = { menuOpen = false; onStartRecipeSelection() },
                            )
                        }
                    }
                }
                if (!isSelecting) {
                    LiquidActionButton(text = "＋ Add", onClick = onAddClick, isPrimary = true, small = true)
                }
            }
```

Add imports near the top of `FoodScreen.kt`:

```kotlin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

(`getValue`, `remember`, `MutableInteractionSource`, `border`, `clip`, `background`, `clickable` are already imported.)

- [ ] **Step 3: Checkbox in rows + bottom action bar**

When rendering each `SlotEntryRow` inside `LockedSlotCard`, pass selection props:

```kotlin
                        SlotEntryRow(
                            entry = entry,
                            isSelecting = isSelecting,
                            isSelected = entry.id in selectedIds,
                            onToggleSelection = { onToggleSelection(entry.id) },
                            canConfirm = entry.planned && !isFuture,
                            canPostpone = !isPast,
                            onDelete = onDeleteEntry,
                            onConfirm = { onConfirmEntry(entry.id) },
                            onPostpone = { onPostponeEntry(entry.id) },
                            onEditAmount = { onEditEntryAmount(entry.id) },
                            onEditMacros = onEditMacros,
                        )
```

Add the new params to `SlotEntryRow`:

```kotlin
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
```

At the very start of `SlotEntryRow`'s `Row`, change the row `clickable` so that during selection it toggles instead of editing, and render a leading checkbox. Insert as the first child of the `Row` (before the name `Column`):

```kotlin
        if (isSelecting) {
            val accentSel = LocalAppAccent.current
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) accentSel.accent else Color(0x14FFFFFF))
                    .border(
                        1.dp,
                        if (isSelected) accentSel.accent else Color(0x33FFFFFF),
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
        }
```

Change the row's `.clickable { ... }` modifier to:

```kotlin
            .clickable {
                if (isSelecting) onToggleSelection()
                else if (amountEditable) onEditAmount() else showMacroEdit = true
            }
```

Wrap the trailing action buttons (confirm/edit/postpone/delete `Box`es) so they are hidden while selecting — surround that block with:

```kotlin
        if (!isSelecting) {
            // ... existing confirm / edit / postpone / delete Boxes unchanged ...
        }
```

Add import if missing:

```kotlin
import androidx.compose.foundation.layout.width
```

Finally, render the bottom action bar inside `LockedSlotCard` (after the entries `Column`, still inside the card `Column`), shown only while selecting:

```kotlin
        if (isSelecting) {
            val accentBar = LocalAppAccent.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentBar.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${selectedIds.size} selected",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentBar.accentLight,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidActionButton(text = "Cancel", onClick = onCancelSelection, isPrimary = false, small = true)
                    if (selectedIds.isNotEmpty()) {
                        LiquidActionButton(text = "Save as recipe", onClick = onSaveSelection, isPrimary = true, small = true)
                    }
                }
            }
        }
```

> When `LockedSlotCard` is invoked, derive `isSelecting`/`selectedIds` from state:
> `val sel = state.recipeSelection?.takeIf { it.slotId == slotWithEntries.slot.id }`
> `isSelecting = sel != null`, `selectedIds = sel?.selectedIds.orEmpty()`.
> Pass `onStartRecipeSelection = { viewModel.startRecipeSelection(slotWithEntries.slot.id) }`,
> `onToggleSelection = viewModel::toggleRecipeSelection`,
> `onCancelSelection = viewModel::cancelRecipeSelection`, and the `onSaveSelection` from Step 1.

- [ ] **Step 4: Verify compilation (completes Task 6)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `LiquidActionButton`'s parameter names differ (check its declaration around `LiquidComponents.kt:832`), adjust the calls to match.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(recipes): meal slot save-as-recipe selection UI"
```

---

## Task 9: RecipeBuilderScreen — glass cards + AI ✨ name field

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderScreen.kt`

UI task — verified by `compileDebugKotlin` and manual run.

- [ ] **Step 1: Replace the name `OutlinedTextField` with a glass field + ✨ button**

For reference, read `DashboardScreen.kt`'s `TodayCard` (line ~228+) to match the glass surface treatment (translucent white fill + subtle border + rounded `CornerCard`).

Replace the name field `item { ... }` block (currently the `OutlinedTextField` for "Recipe name") with:

```kotlin
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChanged,
                    label = { Text("Recipe name") },
                    singleLine = true,
                    enabled = !state.isGeneratingName,
                    trailingIcon = {
                        if (state.aiAvailable) {
                            val enabled = state.ingredients.isNotEmpty() && !state.isGeneratingName
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (enabled)
                                            Brush.linearGradient(listOf(accent.accent, accent.accentLight))
                                        else SolidColor(accent.accent.copy(alpha = 0.16f))
                                    )
                                    .then(
                                        if (enabled) Modifier.clickable(
                                            remember { MutableInteractionSource() }, null,
                                            onClick = viewModel::generateName,
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isGeneratingName) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Generate name with AI",
                                        tint = if (enabled) Color.White else accent.accentLight.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
```

Add the imports:

```kotlin
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
```

(`CircularProgressIndicator`, `Icon`, `OutlinedTextField`, `clickable`, `MutableInteractionSource`, `clip`, `background`, `accent` via `LocalAppAccent.current` are already present.)

- [ ] **Step 2: Apply the glass surface to the ingredient list**

The ingredient rows currently use `CardSurface`. Change the per-ingredient `Column`'s `.background(CardSurface)` to the glass fill used by the app's cards:

```kotlin
                            .background(Color(0x0FFFFFFF))
```

and the "+ Add ingredient" button container is already glass-like; leave it. (Keep the existing rounded-corner logic for first/last rows.)

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderScreen.kt
git commit -m "feat(recipes): glass recipe builder + AI name button"
```

---

## Task 10: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test (on device/emulator)**

Verify each:
- A meal slot with ≥1 entry shows a `⋯` button; empty slots do not.
- `⋯` → "Save as recipe" turns the slot's rows into checkboxes + shows the bottom bar.
- "Save as recipe" is hidden until ≥1 item is ticked; Cancel exits selection mode.
- Saving opens the Recipe Builder pre-filled with exactly the ticked items.
- With AI enabled + a usable backend, the ✨ button shows; it is disabled with 0 ingredients; tapping generates a name; tapping again rerolls.
- With AI off / no model / no cloud config, the ✨ button is hidden and the field still works manually.
- A generated/edited name saves correctly and the recipe appears in the recipe list.

- [ ] **Step 4: Final commit (if any manual fixes were needed)**

```bash
git add -A
git commit -m "fix(recipes): manual verification adjustments"
```

---

## Notes on design decisions

- **Why reuse `aiInsightCoordinator.state` for availability:** it already encodes AI-enabled, effective backend, cloud config, and on-device model presence — and updates reactively (e.g. when a download finishes). Naming shares the same backend/model, so coupling availability to it is correct and avoids duplicate plumbing.
- **Why Base64 for `seedIngredients`:** recipe-ingredient JSON contains `{ } " ,` which are unsafe in a nav route. URL-safe Base64 (via `java.util.Base64`, available on minSdk 26 and in JVM unit tests) is unambiguous and needs no URL-decode dance.
- **Error surfacing:** naming failures reuse the Recipe Builder's existing in-screen `MessageText` (`uiState.message`) rather than a toast — the screen already renders it, so no extra plumbing is added.
