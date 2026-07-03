# Coach: Fix Logging Mistakes (delete/edit meals) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the AI coach remove or change an already-logged meal from chat, identified by name, with confirmation.

**Architecture:** Two new tools (`delete_meal`, `edit_meal`) dispatched through `CoachToolExecutor`, resolving a meal name against `LogRepository.getDay(date).meals` with a word scorer, then calling `deleteMeal(id)` / `updateMealEntry(entry)`. Both route through the existing `COACH_WRITE_TOOLS` confirmation flow. No schema/migration change.

**Tech Stack:** Kotlin, coroutines, Room, JUnit + mockito-kotlin, kotlinx.serialization (JSON returns).

**Spec:** `docs/superpowers/specs/2026-07-01-coach-fix-logging-mistakes-design.md`
**Branch:** `redesign/ai-coaching`. **Run in the MAIN checkout** (worktree isolation branches from the wrong base in this repo). Each implementer must first verify base: `git branch --show-current` == `redesign/ai-coaching` AND `grep -c "private suspend fun resolveExerciseId" app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` == 1. If not, STOP.

---

## File Structure
- `ai/CoachToolExecutor.kt` — add `scoredMealMatches`, `deleteMeal`, `editMeal`, dispatch entries (Tasks 1–2).
- `ai/CoachTools.kt` — `MEAL_EDIT_TOOL_SCHEMAS`, append to `CLOUD_COACH_TOOL_SCHEMAS`, add both to `COACH_WRITE_TOOLS` (Task 1).
- `ai/CloudCoachCoordinator.kt` — confirm summaries + status labels (Task 3).
- `ai/CoachToolsAdapter.kt` — prompt routing (Task 4).
- Test: `ai/CoachToolExecutorMealEditTest.kt` (new).

**Build/verify:** `./gradlew :app:compileDebugKotlin` · `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorMealEdit*" --tests "*AiCoachBoundaryTest*"` · full: `./gradlew :app:testDebugUnitTest` (only `InsightHarnessTest` may fail — known `.env.test` network test) · `./gradlew :app:assembleDebug`.

---

## Task 1: `delete_meal` (+ meal name scorer)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachTools.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorMealEditTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CoachToolExecutorMealEditTest {

    private val fixedDate = LocalDate.of(2026, 6, 5)
    private val dateProvider = object : DateProvider { override fun today() = fixedDate }

    private fun meal(
        id: Long, name: String, calories: Int = 500, grams: Double? = null,
        protein: Double = 10.0, carbs: Double = 50.0, fat: Double = 20.0,
        basePer100Cal: Int? = null, basePer100P: Double? = null,
        basePer100C: Double? = null, basePer100F: Double? = null,
        date: LocalDate = fixedDate,
    ) = MealEntryEntity(
        id = id, date = date.toString(), mealType = "Snack", name = name,
        calories = calories, proteinG = protein, carbsG = carbs, fatG = fat,
        amountGrams = grams, basePer100Calories = basePer100Cal, basePer100ProteinG = basePer100P,
        basePer100CarbsG = basePer100C, basePer100FatG = basePer100F,
    )

    private fun dayLog(date: LocalDate, meals: List<MealEntryEntity>) =
        DayLog(date = date, dailyLog = null, meals = meals, totals = MacroTotals())

    private fun executor(log: LogRepository) = CoachToolExecutor(
        logRepository = log,
        planRepository = mock<PlanRepository>(),
        dateProvider = dateProvider,
    )

    @Test
    fun `delete_meal resolves a name to its entry and deletes it`() = runTest {
        val log = mock<LogRepository>()
        whenever(log.getDay(fixedDate)).thenReturn(dayLog(fixedDate, listOf(
            meal(1, "2 slices pizza", calories = 520),
            meal(2, "Green salad", calories = 90),
        )))
        val json = executor(log).execute("delete_meal", mapOf("name" to "pizza"))
        assertTrue(json.contains("\"success\":true"))
        assertTrue(json.contains("520"))
        verify(log).deleteMeal(1L)
    }

    @Test
    fun `delete_meal with two matching entries asks for disambiguation and deletes nothing`() = runTest {
        val log = mock<LogRepository>()
        whenever(log.getDay(fixedDate)).thenReturn(dayLog(fixedDate, listOf(
            meal(1, "Chicken breast", calories = 300),
            meal(2, "Grilled chicken thigh", calories = 250),
        )))
        val json = executor(log).execute("delete_meal", mapOf("name" to "chicken"))
        assertTrue(json.contains("needs_disambiguation"))
        verify(log, org.mockito.kotlin.never()).deleteMeal(org.mockito.kotlin.any())
    }

    @Test
    fun `delete_meal with no match returns an error and deletes nothing`() = runTest {
        val log = mock<LogRepository>()
        whenever(log.getDay(fixedDate)).thenReturn(dayLog(fixedDate, listOf(meal(1, "Oatmeal"))))
        val json = executor(log).execute("delete_meal", mapOf("name" to "sushi"))
        assertTrue(json.contains("error"))
        verify(log, org.mockito.kotlin.never()).deleteMeal(org.mockito.kotlin.any())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorMealEditTest*"`
Expected: FAIL — `delete_meal` returns `unknown tool` / not implemented.

- [ ] **Step 3: Add the scorer + `deleteMeal` + dispatch**

In `CoachToolExecutor.kt`, add the dispatch entry in `execute(...)`:
```kotlin
        "delete_meal" -> deleteMeal(args)
```

Add a meal-name scorer (mirrors `scoredFoodMatches`) and the `deleteMeal` method. `MealEntryEntity` is already imported in this file.
```kotlin
    /** Confident name matches among a day's logged meal entries (exact › startsWith › contains › all-words). */
    private fun scoredMealMatches(query: String, meals: List<MealEntryEntity>): List<MealEntryEntity> {
        val q = query.lowercase().trim()
        val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun score(name: String): Int {
            val n = name.lowercase()
            return when {
                n == q -> 3
                n.startsWith(q) -> 2
                n.contains(q) -> 1
                words.isNotEmpty() && words.all { n.contains(it) } -> 0
                else -> -1
            }
        }
        return meals.map { it to score(it.name) }
            .filter { (_, s) -> s >= 0 }
            .sortedByDescending { (_, s) -> s }
            .map { it.first }
    }

    private fun mealMatchJson(m: MealEntryEntity): String =
        """{"name":"${m.name.esc()}","meal_type":"${m.mealType.esc()}","calories":${m.calories}""" +
            (m.amountGrams?.let { ""","grams":${it.toInt()}""" } ?: "") + "}"

    private suspend fun deleteMeal(args: Map<String, String>): String {
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"delete_meal requires 'name'"}"""
        val date = args["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateProvider.today()
        val matches = scoredMealMatches(name, logRepository.getDay(date).meals)
        return when {
            matches.isEmpty() -> """{"error":"no logged meal matching '${name.esc()}' on $date"}"""
            matches.size > 1 -> """{"needs_disambiguation":true,"matches":[${matches.take(5).joinToString(",") { mealMatchJson(it) }}]}"""
            else -> {
                val entry = matches.first()
                logRepository.deleteMeal(entry.id)
                """{"success":true,"deleted":"${entry.name.esc()}","calories":${entry.calories}}"""
            }
        }
    }
```

- [ ] **Step 4: Add schema + write-tool registration in `CoachTools.kt`**

Add a new list (cloud-only, mirroring `ROUTINE_TOOL_SCHEMAS`) with BOTH meal-edit schemas (edit_meal's handler lands in Task 2; until then it returns a graceful `unknown tool` error — the branch isn't shipped mid-plan):
```kotlin
/** Cloud-coach tools for fixing mislogged meals. */
val MEAL_EDIT_TOOL_SCHEMAS: List<String> = listOf(
    """{"name":"delete_meal","description":"Remove a meal the user already logged, identified by name. Pass 'date' (YYYY-MM-DD) if it was not today. If several entries match, the tool returns needs_disambiguation with the candidates — ask the user which.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Name of the logged meal to remove"},"date":{"type":"string","description":"Optional ISO date; omit for today"}},"required":["name"]}}""",
    """{"name":"edit_meal","description":"Change a meal the user already logged. Pass 'grams' to rescale its macros, or pass calories/macros directly to override. Identified by name; pass 'date' if not today.","parameters":{"type":"object","properties":{"name":{"type":"string"},"grams":{"type":"number"},"calories":{"type":"integer"},"protein_g":{"type":"number"},"carbs_g":{"type":"number"},"fat_g":{"type":"number"},"date":{"type":"string","description":"Optional ISO date; omit for today"}},"required":["name"]}}""",
)
```

Append it to the cloud list (leave `COACH_TOOL_SCHEMAS`, the legacy set, untouched):
```kotlin
val CLOUD_COACH_TOOL_SCHEMAS: List<String> = COACH_TOOL_SCHEMAS + SEARCH_WEB_TOOL_SCHEMA + ROUTINE_TOOL_SCHEMAS + MEAL_EDIT_TOOL_SCHEMAS
```

Add both tools to the confirmation set (find the current `COACH_WRITE_TOOLS` and add the two names):
```kotlin
val COACH_WRITE_TOOLS: Set<String> =
    setOf("log_meal", "log_metric", "update_calorie_target", "create_routine", "edit_routine", "create_exercise", "delete_meal", "edit_meal")
```

- [ ] **Step 5: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorMealEditTest*"` (Expected: 3 delete tests PASS) and `./gradlew :app:compileDebugKotlin` (Expected: SUCCESS).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt app/src/main/java/com/zack/recomptracker/ai/CoachTools.kt app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorMealEditTest.kt
git commit -m "feat(coach): delete_meal tool with name resolution + disambiguation"
```

---

## Task 2: `edit_meal`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorMealEditTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `edit_meal rescales a library-food entry by grams using basePer100`() = runTest {
        val log = mock<LogRepository>()
        // 100 g base = 165 kcal / 31 P / 0 C / 4 F ; currently logged at 100 g.
        whenever(log.getDay(fixedDate)).thenReturn(dayLog(fixedDate, listOf(
            meal(1, "Chicken breast", calories = 165, grams = 100.0, protein = 31.0, carbs = 0.0, fat = 4.0,
                basePer100Cal = 165, basePer100P = 31.0, basePer100C = 0.0, basePer100F = 4.0),
        )))
        val json = executor(log).execute("edit_meal", mapOf("name" to "chicken breast", "grams" to "200"))
        assertTrue(json.contains("\"success\":true"))
        val captor = org.mockito.kotlin.argumentCaptor<MealEntryEntity>()
        verify(log).updateMealEntry(captor.capture())
        val saved = captor.firstValue
        assertTrue(saved.amountGrams == 200.0)
        assertTrue(saved.calories == 330)      // 165 * 200/100
        assertTrue(saved.proteinG == 62.0)     // 31 * 2
    }

    @Test
    fun `edit_meal rescales a non-library entry proportionally from amountGrams`() = runTest {
        val log = mock<LogRepository>()
        whenever(log.getDay(fixedDate)).thenReturn(dayLog(fixedDate, listOf(
            meal(1, "Leftover curry", calories = 400, grams = 200.0, protein = 20.0, carbs = 40.0, fat = 15.0),
        )))
        executor(log).execute("edit_meal", mapOf("name" to "curry", "grams" to "100"))
        val captor = org.mockito.kotlin.argumentCaptor<MealEntryEntity>()
        verify(log).updateMealEntry(captor.capture())
        assertTrue(captor.firstValue.calories == 200)   // 400 * 100/200
        assertTrue(captor.firstValue.amountGrams == 100.0)
    }

    @Test
    fun `edit_meal applies explicit macro overrides`() = runTest {
        val log = mock<LogRepository>()
        whenever(log.getDay(fixedDate)).thenReturn(dayLog(fixedDate, listOf(meal(1, "Protein shake", calories = 200))))
        executor(log).execute("edit_meal", mapOf("name" to "shake", "calories" to "150", "protein_g" to "30"))
        val captor = org.mockito.kotlin.argumentCaptor<MealEntryEntity>()
        verify(log).updateMealEntry(captor.capture())
        assertTrue(captor.firstValue.calories == 150)
        assertTrue(captor.firstValue.proteinG == 30.0)
    }

    @Test
    fun `edit_meal resolves against a past date when provided`() = runTest {
        val past = LocalDate.of(2026, 6, 1)
        val log = mock<LogRepository>()
        whenever(log.getDay(past)).thenReturn(dayLog(past, listOf(meal(9, "Bagel", calories = 250, date = past))))
        val json = executor(log).execute("edit_meal", mapOf("name" to "bagel", "calories" to "300", "date" to "2026-06-01"))
        assertTrue(json.contains("\"success\":true"))
        verify(log).updateMealEntry(org.mockito.kotlin.any())
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorMealEditTest*"`
Expected: FAIL — `edit_meal` not implemented (`unknown tool`).

- [ ] **Step 3: Implement `editMeal` + dispatch**

Add the dispatch entry in `execute(...)`:
```kotlin
        "edit_meal" -> editMeal(args)
```

```kotlin
    private suspend fun editMeal(args: Map<String, String>): String {
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"edit_meal requires 'name'"}"""
        val date = args["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateProvider.today()
        val matches = scoredMealMatches(name, logRepository.getDay(date).meals)
        if (matches.isEmpty()) return """{"error":"no logged meal matching '${name.esc()}' on $date"}"""
        if (matches.size > 1) return """{"needs_disambiguation":true,"matches":[${matches.take(5).joinToString(",") { mealMatchJson(it) }}]}"""
        val entry = matches.first()

        var cal = entry.calories
        var p = entry.proteinG
        var c = entry.carbsG
        var f = entry.fatG
        var amt = entry.amountGrams

        val grams = args["grams"]?.toDoubleOrNull()
        if (grams != null && grams > 0) {
            val base = entry.basePer100Calories
            if (base != null) {
                cal = (base * grams / 100.0).toInt()
                p = (entry.basePer100ProteinG ?: 0.0) * grams / 100.0
                c = (entry.basePer100CarbsG ?: 0.0) * grams / 100.0
                f = (entry.basePer100FatG ?: 0.0) * grams / 100.0
                amt = grams
            } else if (entry.amountGrams != null && entry.amountGrams > 0) {
                val scale = grams / entry.amountGrams
                cal = (entry.calories * scale).toInt()
                p = entry.proteinG * scale
                c = entry.carbsG * scale
                f = entry.fatG * scale
                amt = grams
            }
            // else: no gram basis — fall through; require explicit macros below.
        }

        // Explicit macro args override any rescale (integer-as-double tolerated, matching log_meal).
        args["calories"]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }?.let { cal = it }
        args["protein_g"]?.toDoubleOrNull()?.let { p = it }
        args["carbs_g"]?.toDoubleOrNull()?.let { c = it }
        args["fat_g"]?.toDoubleOrNull()?.let { f = it }

        val nothingChanged = cal == entry.calories && p == entry.proteinG && c == entry.carbsG &&
            f == entry.fatG && amt == entry.amountGrams
        if (nothingChanged) {
            return """{"error":"nothing to change — provide grams or calories/macros (this entry has no gram basis to rescale)"}"""
        }

        logRepository.updateMealEntry(
            entry.copy(calories = cal, proteinG = p, carbsG = c, fatG = f, amountGrams = amt),
        )
        return """{"success":true,"updated":"${entry.name.esc()}","calories":$cal}"""
    }
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorMealEditTest*"`
Expected: PASS (all delete + edit tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorMealEditTest.kt
git commit -m "feat(coach): edit_meal tool with gram rescale + explicit macro override"
```

---

## Task 3: Confirmation summaries + status labels

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt` (`pendingActionDisplayText`, `toolStatusText`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorMealEditConfirmTest.kt` (create)

- [ ] **Step 1: Write the failing test**

The confirm summary comes from `pendingActionDisplayText`. Extract a top-level `internal fun mealEditActionSummary(toolName, args)` and test it directly (mirrors the existing `routineActionSummary`).

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCoachCoordinatorMealEditConfirmTest {
    @Test
    fun `delete_meal summary names the meal`() {
        val s = mealEditActionSummary("delete_meal", mapOf("name" to "2 slices pizza"))
        assertTrue(s.contains("2 slices pizza"))
        assertTrue(s.lowercase().contains("delete") || s.lowercase().contains("remove"))
    }

    @Test
    fun `edit_meal summary names the meal and the new amount`() {
        val s = mealEditActionSummary("edit_meal", mapOf("name" to "chicken breast", "grams" to "200"))
        assertTrue(s.contains("chicken breast"))
        assertTrue(s.contains("200"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CloudCoachCoordinatorMealEditConfirmTest*"`
Expected: FAIL — `mealEditActionSummary` unresolved.

- [ ] **Step 3: Add `mealEditActionSummary` + wire it in**

Add a top-level `internal fun` in `CloudCoachCoordinator.kt` (next to `routineActionSummary`):
```kotlin
internal fun mealEditActionSummary(toolName: String, args: Map<String, String>): String = when (toolName) {
    "delete_meal" -> "Delete \"${args["name"].orEmpty()}\" from ${args["date"] ?: "today"}'s log"
    "edit_meal" -> buildString {
        append("Change \"${args["name"].orEmpty()}\"")
        args["grams"]?.toDoubleOrNull()?.let { append(" to ${it.toInt()} g") }
        args["calories"]?.let { append(" (${it} kcal)") }
    }
    else -> toolName
}
```

In `pendingActionDisplayText`, add a branch before the `else`:
```kotlin
            "delete_meal", "edit_meal" -> mealEditActionSummary(toolName, args)
```

In `toolStatusText`, add:
```kotlin
        "delete_meal" -> "Removing meal…"
        "edit_meal" -> "Updating meal…"
```

- [ ] **Step 4: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "*CloudCoachCoordinatorMealEditConfirmTest*"` (Expected: PASS) and `./gradlew :app:compileDebugKotlin` (Expected: SUCCESS).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorMealEditConfirmTest.kt
git commit -m "feat(coach): confirm summaries + status labels for delete_meal/edit_meal"
```

---

## Task 4: System-prompt routing

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt` (`COACH_PROMPT_GUIDELINES`)

- [ ] **Step 1: Read the constant**

Run: `grep -n "COACH_PROMPT_GUIDELINES" app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt` and read the existing bullet string to match its `"- …\n" +` format.

- [ ] **Step 2: Append the routing line**

Add this bullet (matching the existing format/placement) to `COACH_PROMPT_GUIDELINES`:
```
- To fix a logged meal, use delete_meal or edit_meal, identifying it by name; pass a past 'date' if it wasn't today. If the tool returns needs_disambiguation, ask the user which one.
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt
git commit -m "feat(coach): system-prompt routing for delete_meal/edit_meal"
```

---

## Task 5: Full verification

- [ ] **Step 1: Boundary + full suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: only `InsightHarnessTest > runInsightHarness` fails (known `.env.test` network test); `AiCoachBoundaryTest` and everything else green.

- [ ] **Step 2: APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: On-device (user)**

Log a meal in chat, then *"delete it"* / *"change it to 200g"* — confirm the dialog shows the right summary, the meal updates in the food log, and asking to delete an ambiguous name prompts a which-one question.

---

## Notes for the implementer

- Args arrive as `Map<String, String>`; `date` is an ISO string, macros/grams are numeric strings — parse with `toIntOrNull()`/`toDoubleOrNull()` (tolerate integer-as-double like the existing `log_meal`).
- Reuse the existing private `esc()` extension for all interpolated strings in JSON returns.
- Every tool returns a JSON string: success / `{"error":...}` / `{"needs_disambiguation":...}` — matching the existing contract so the coordinator relays it to the model.
- `MealEntryEntity` is a `data class` → `.copy(...)` is available. `LogRepository` is mockable with mockito-kotlin (existing `CoachToolExecutorTest` does this).
- Both write tools MUST be in `COACH_WRITE_TOOLS` (Task 1) so they require confirmation — never let a delete run unconfirmed.
