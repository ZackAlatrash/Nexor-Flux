# AI Coach Reliability Overhaul — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the AI coach so write tools work reliably, responses are visible as they stream, and the session never hangs indefinitely.

**Architecture:** Two files change — `CoachToolExecutor` gets new 2-arg write tools (`log_meal`, `log_metric`) replacing the broken 6-arg versions; `RealCoachCoordinator` gets a shortened system prompt, a 45s timeout, a 5-iteration tool loop cap, 20-turn conversation auto-refresh, and a 35ms streaming delay. No other files change.

**Tech Stack:** Kotlin, LiteRT-LM 0.11.0 (`com.google.ai.edge.litertlm`), Room/Flow, JUnit 4, mockito-kotlin, kotlinx-coroutines-test

---

## File Map

| File | Change |
|---|---|
| `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` | Replace `logMeal` (6-arg) with 2-arg version; replace `logDailyMetrics` with `logMetric(metric, value)` |
| `app/src/main/java/com/zack/recomptracker/ai/RealCoachCoordinator.kt` | New tool schemas, new system prompt, timeout, loop cap, turn tracking, streaming delay |
| `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorTest.kt` | Update existing `log_meal` test; add tests for `log_metric` |

---

## Task 1: Redesign CoachToolExecutor write tools

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt`
- Modify: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorTest.kt`

- [ ] **Step 1: Add failing tests for `log_metric`**

Open `CoachToolExecutorTest.kt`. The file already has a `log_meal` test (line 97) that passes `protein_g` — leave it; the new implementation still accepts and ignores unknown keys. Add these new tests **after** the existing ones:

```kotlin
@Test
fun `log_meal only requires name and calories, ignores extra keys`() = runTest {
    val logRepo = mock<LogRepository>()
    val planRepo = mock<PlanRepository>()
    whenever(logRepo.observeDay(fixedDate)).thenReturn(flowOf(emptyDayLog()))
    whenever(logRepo.addMeal(any())).thenReturn(1L)

    val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
    val result = executor.execute("log_meal", mapOf("name" to "Rice", "calories" to "400"))

    assertTrue("Should succeed", result.contains("\"success\":true"))
    assertTrue("Should echo name", result.contains("Rice"))
    assertTrue("Should echo calories", result.contains("400"))
}

@Test
fun `log_meal without name returns error JSON`() = runTest {
    val logRepo = mock<LogRepository>()
    val planRepo = mock<PlanRepository>()

    val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
    val result = executor.execute("log_meal", mapOf("calories" to "400"))

    assertTrue("Should contain error key", result.contains("\"error\""))
}

@Test
fun `log_metric weight_kg saves weight and returns success`() = runTest {
    var saved: DailyMetricsInput? = null
    val logRepo = mock<LogRepository>()
    val planRepo = mock<PlanRepository>()
    whenever(logRepo.observeDay(fixedDate)).thenReturn(flowOf(emptyDayLog()))
    whenever(logRepo.saveDailyMetrics(any())).thenAnswer { inv ->
        saved = inv.getArgument(0); Unit
    }

    val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
    val result = executor.execute("log_metric", mapOf("metric" to "weight_kg", "value" to "82.5"))

    assertTrue("Should succeed", result.contains("\"success\":true"))
    assertTrue("Should echo metric", result.contains("weight_kg"))
    assertTrue("Should echo value", result.contains("82.5"))
    assertTrue("Weight should be saved", saved?.bodyWeightKg == 82.5)
}

@Test
fun `log_metric sleep_hours saves sleep and returns success`() = runTest {
    var saved: DailyMetricsInput? = null
    val logRepo = mock<LogRepository>()
    val planRepo = mock<PlanRepository>()
    whenever(logRepo.observeDay(fixedDate)).thenReturn(flowOf(emptyDayLog()))
    whenever(logRepo.saveDailyMetrics(any())).thenAnswer { inv ->
        saved = inv.getArgument(0); Unit
    }

    val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
    val result = executor.execute("log_metric", mapOf("metric" to "sleep_hours", "value" to "7.5"))

    assertTrue("Should succeed", result.contains("\"success\":true"))
    assertTrue("Sleep should be saved", saved?.sleepHours == 7.5)
}

@Test
fun `log_metric unknown metric returns error JSON`() = runTest {
    val logRepo = mock<LogRepository>()
    val planRepo = mock<PlanRepository>()

    val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
    val result = executor.execute("log_metric", mapOf("metric" to "mood", "value" to "9"))

    assertTrue("Should contain error key", result.contains("\"error\""))
    assertTrue("Should mention metric name", result.contains("mood"))
}

@Test
fun `log_metric non-numeric value returns error JSON`() = runTest {
    val logRepo = mock<LogRepository>()
    val planRepo = mock<PlanRepository>()

    val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
    val result = executor.execute("log_metric", mapOf("metric" to "weight_kg", "value" to "heavy"))

    assertTrue("Should contain error key", result.contains("\"error\""))
}

@Test
fun `log_daily_metrics is now an unknown tool`() = runTest {
    val logRepo = mock<LogRepository>()
    val planRepo = mock<PlanRepository>()

    val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
    val result = executor.execute("log_daily_metrics", mapOf("weight_kg" to "80.0"))

    assertTrue("Should contain error key", result.contains("\"error\""))
}
```

- [ ] **Step 2: Run the new tests to confirm they fail**

```bash
cd "app" && ../gradlew testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachToolExecutorTest" 2>&1 | tail -20
```

Expected: Several FAILED — `log_metric` doesn't exist yet; `log_daily_metrics` still works.

- [ ] **Step 3: Replace `logMeal` and `logDailyMetrics` in CoachToolExecutor**

Open `CoachToolExecutor.kt`. Make these two changes:

**Change 1** — update the `execute` dispatch (replace the two old branches):

```kotlin
// OLD:
"log_meal" -> logMeal(args)
"log_daily_metrics" -> logDailyMetrics(args)

// NEW:
"log_meal" -> logMeal(args)
"log_metric" -> logMetric(args)
```

**Change 2** — replace the `logMeal` private function entirely:

```kotlin
private suspend fun logMeal(args: Map<String, String>): String {
    val today = dateProvider.today()
    val name = args["name"] ?: return """{"error":"log_meal requires 'name'"}"""
    val calories = args["calories"]?.toIntOrNull() ?: 0
    logRepository.addMeal(
        MealEntryInput(
            date = today,
            mealType = "Snack",
            name = name,
            calories = calories,
            proteinG = 0.0,
            carbsG = 0.0,
            fatG = 0.0,
        ),
    )
    return """{"success":true,"logged":"${name.esc()}","calories":$calories}"""
}
```

**Change 3** — replace the `logDailyMetrics` private function with `logMetric`:

```kotlin
private val validMetrics = setOf(
    "weight_kg", "waist_cm", "sleep_hours",
    "energy_score", "hunger_score", "soreness_score",
)

private suspend fun logMetric(args: Map<String, String>): String {
    val metric = args["metric"] ?: return """{"error":"log_metric requires 'metric'"}"""
    if (metric !in validMetrics) return """{"error":"unknown metric '${metric.esc()}'"}"""
    val value = args["value"]?.toDoubleOrNull()
        ?: return """{"error":"log_metric requires a numeric 'value'"}"""
    val today = dateProvider.today()
    val existing = logRepository.observeDay(today).first().dailyLog
    logRepository.saveDailyMetrics(
        DailyMetricsInput(
            date = today,
            bodyWeightKg = if (metric == "weight_kg") value else existing?.bodyWeightKg,
            waistCm = if (metric == "waist_cm") value else existing?.waistCm,
            waistSkinfoldMm = existing?.waistSkinfoldMm,
            steps = existing?.steps,
            sleepHours = if (metric == "sleep_hours") value else existing?.sleepHours,
            energyScore = if (metric == "energy_score") value.toInt() else existing?.energyScore,
            hungerScore = if (metric == "hunger_score") value.toInt() else existing?.hungerScore,
            sorenessScore = if (metric == "soreness_score") value.toInt() else existing?.sorenessScore,
            trained = existing?.trained ?: false,
            notes = existing?.notes ?: "",
        ),
    )
    return """{"success":true,"metric":"${metric.esc()}","value":$value}"""
}
```

- [ ] **Step 4: Run all CoachToolExecutor tests**

```bash
cd "app" && ../gradlew testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachToolExecutorTest" 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt \
        app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorTest.kt
git commit -m "$(cat <<'EOF'
feat(ai): replace 6-arg write tools with 2-arg log_meal and log_metric

log_meal(name, calories) replaces the unreliable 6-param version.
log_metric(metric, value) replaces log_daily_metrics; metric is one of
weight_kg|waist_cm|sleep_hours|energy_score|hunger_score|soreness_score.
Field data shows 4+ arg tools score 0% on Gemma 4 E2B; ≤2 args score
78-88%.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Update tool schemas and toolStatusText in RealCoachCoordinator

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/RealCoachCoordinator.kt`

- [ ] **Step 1: Replace the COACH_TOOLS companion object**

Find the `val COACH_TOOLS: List<OpenApiTool> = listOf(...)` block and replace the entire contents with:

```kotlin
val COACH_TOOLS: List<OpenApiTool> = listOf(
    SchemaTool(
        """{"name":"get_today_summary","description":"Get a specific day's food log, macro totals, and daily metrics. Omit 'date' for today.","parameters":{"type":"object","properties":{"date":{"type":"string","description":"ISO date YYYY-MM-DD. Omit for today."}},"required":[]}}""",
    ),
    SchemaTool(
        """{"name":"get_weekly_trends","description":"Get last 7 days calorie history and adherence.","parameters":{"type":"object","properties":{},"required":[]}}""",
    ),
    SchemaTool(
        """{"name":"get_plan","description":"Get the user's current calorie and macro targets.","parameters":{"type":"object","properties":{},"required":[]}}""",
    ),
    SchemaTool(
        """{"name":"log_meal","description":"Add a meal to today's food log.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Food name or description"},"calories":{"type":"integer","description":"Calories in kcal"}},"required":["name","calories"]}}""",
    ),
    SchemaTool(
        """{"name":"log_metric","description":"Record a body or recovery metric for today.","parameters":{"type":"object","properties":{"metric":{"type":"string","description":"One of: weight_kg, waist_cm, sleep_hours, energy_score, hunger_score, soreness_score"},"value":{"type":"number","description":"The numeric value to record"}},"required":["metric","value"]}}""",
    ),
    SchemaTool(
        """{"name":"update_calorie_target","description":"Update the daily calorie target.","parameters":{"type":"object","properties":{"target_calories":{"type":"integer"}},"required":["target_calories"]}}""",
    ),
)
```

- [ ] **Step 2: Update `toolStatusText` to include `log_metric`**

Find the `toolStatusText` function and replace it:

```kotlin
private fun toolStatusText(name: String): String = when (name) {
    "get_today_summary" -> "Reading your food log…"
    "get_weekly_trends" -> "Reading your weekly trends…"
    "get_plan" -> "Reading your plan…"
    "log_meal" -> "Logging meal…"
    "log_metric" -> "Saving metric…"
    "update_calorie_target" -> "Updating calorie target…"
    else -> "Running tool…"
}
```

- [ ] **Step 3: Build to verify no compile errors**

```bash
cd "app" && ../gradlew :app:compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/RealCoachCoordinator.kt
git commit -m "$(cat <<'EOF'
feat(ai): update tool schemas to match new 2-arg write tools

Replace log_daily_metrics schema with log_metric(metric, value).
Update log_meal schema to 2 required params only.
Add log_metric to toolStatusText.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Rewrite the system prompt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/RealCoachCoordinator.kt`

- [ ] **Step 1: Replace `buildSystemPrompt`**

Find the `buildSystemPrompt` function (signature: `private fun buildSystemPrompt(prefs: PlanPreferences, today: java.time.LocalDate): String`) and replace the entire body:

```kotlin
private fun buildSystemPrompt(prefs: PlanPreferences, today: java.time.LocalDate): String {
    val yesterday = today.minusDays(1)
    return buildString {
        appendLine("You are a nutrition coach in a body recomposition tracking app.")
        appendLine("Today: $today (${today.dayOfWeek}) | Yesterday: $yesterday")
        appendLine()
        appendLine("Plan: ${prefs.targetCalories} kcal | P ${prefs.targetProteinG}g | C ${prefs.targetCarbsG}g | F ${prefs.targetFatG}g")
        appendLine()
        appendLine("Tools:")
        appendLine("- get_today_summary(date?) — food log + totals. date=YYYY-MM-DD, default=today")
        appendLine("- get_weekly_trends() — last 7 days of calories")
        appendLine("- get_plan() — current targets")
        appendLine("- log_meal(name, calories) — add food to today's log")
        appendLine("- log_metric(metric, value) — metric: weight_kg|waist_cm|sleep_hours|energy_score|hunger_score|soreness_score")
        appendLine("- update_calorie_target(target_calories) — change daily calorie goal")
        appendLine()
        appendLine("Rules:")
        appendLine("1. Be concise: 1–3 sentences unless detail is asked for.")
        appendLine("2. Always call a tool before quoting any numbers — never guess.")
        appendLine("3. Yesterday = get_today_summary(date=\"$yesterday\"). Named days = compute the date.")
        appendLine("4. Before any write tool: state what you will log and wait for the user to confirm.")
        append("5. Stay on topic: nutrition, body composition, training, recovery only.")
    }
}
```

- [ ] **Step 2: Remove the now-redundant `yesterday` lines from the old prompt**

The old prompt had `appendLine("Yesterday: ${today.minusDays(1)}")` as a top-level line AND Rule 3 referring to `today.minusDays(1)`. The new function above handles both inline — verify there are no duplicate references left by scanning the function.

- [ ] **Step 3: Build to verify**

```bash
cd "app" && ../gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/RealCoachCoordinator.kt
git commit -m "$(cat <<'EOF'
feat(ai): shorten system prompt and sharpen date + tool guidance

Cut from ~25 to ~15 lines. Explicit tool+type listing, rule 3 spells
out the yesterday→date mapping, rule 4 is the write-confirmation gate.
Shorter prompt leaves more context window for reasoning on the 2B model.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Add timeout and tool loop cap to `handleMessage`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/RealCoachCoordinator.kt`

- [ ] **Step 1: Add missing imports**

At the top of `RealCoachCoordinator.kt`, add these three imports alongside the existing `kotlinx.coroutines.*` imports:

```kotlin
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
```

- [ ] **Step 2: Replace `handleMessage` with the timeout + loop-cap version**

Find `private suspend fun handleMessage(userText: String)` and replace the entire function:

```kotlin
private suspend fun handleMessage(userText: String) {
    mutableHistory.add(ChatMessage(Role.User, userText))
    _state.value = CoachState.Thinking(mutableHistory.toList())
    try {
        val service = serviceHolder.getOrCreateService()
        withContext(Dispatchers.IO) { service.initialize() }

        if (turnCount >= MAX_TURNS) {
            clearConversation()
            turnCount = 0
        }
        val conv = conversation ?: createConversation(service).also { conversation = it }

        withTimeout(TIMEOUT_MS) {
            var response = withContext(Dispatchers.IO) { conv.sendMessage(userText) }

            var toolIterations = 0
            while (toolIterations < MAX_TOOL_ITERATIONS) {
                val toolCall = response.toolCalls.firstOrNull() ?: break
                toolIterations++
                _state.value = CoachState.Thinking(
                    mutableHistory.toList(),
                    toolStatus = toolStatusText(toolCall.name),
                )
                val argsMap = toolCall.arguments.mapValues { (_, v) -> v.toString() }
                val toolResult = withContext(Dispatchers.IO) {
                    toolExecutor.execute(toolCall.name, argsMap)
                }
                val toolResponse = Contents.of(Content.ToolResponse(toolCall.name, toolResult))
                response = withContext(Dispatchers.IO) { conv.sendMessage(toolResponse) }
            }

            val fullText = extractText(response)
            val words = fullText.split(" ")
            val sb = StringBuilder()
            for (word in words) {
                sb.append(if (sb.isEmpty()) word else " $word")
                _state.value = CoachState.Responding(mutableHistory.toList(), partial = sb.toString())
                delay(STREAM_DELAY_MS)
            }
            mutableHistory.add(ChatMessage(Role.Assistant, sb.toString()))
            turnCount++
            _state.value = CoachState.Idle(mutableHistory.toList())
        }
    } catch (e: TimeoutCancellationException) {
        clearConversation()
        _state.value = CoachState.Error(mutableHistory.toList(), "Took too long — try again.")
    } catch (e: Exception) {
        clearConversation()
        _state.value = CoachState.Error(mutableHistory.toList(), "Something went wrong — try again.")
    }
}
```

- [ ] **Step 3: Add the constants and `turnCount` field to the class body**

Directly below the `private var conversation: Conversation? = null` line, add:

```kotlin
private var turnCount = 0
```

Add the constants to the existing `companion object` block (the one that already holds `COACH_TOOLS`):

```kotlin
private const val TIMEOUT_MS = 45_000L
private const val MAX_TOOL_ITERATIONS = 5
private const val MAX_TURNS = 20
private const val STREAM_DELAY_MS = 35L
```

- [ ] **Step 4: Update `clearHistory` to reset `turnCount`**

Find `override fun clearHistory()` and add `turnCount = 0`:

```kotlin
override fun clearHistory() {
    mutableHistory.clear()
    clearConversation()
    turnCount = 0
    if (_state.value != CoachState.Unavailable) _state.value = CoachState.Ready
}
```

- [ ] **Step 5: Build to verify**

```bash
cd "app" && ../gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/RealCoachCoordinator.kt
git commit -m "$(cat <<'EOF'
feat(ai): add 45s timeout, 5-iteration tool cap, 20-turn auto-refresh, streaming delay

withTimeout(45s) prevents the UI hanging forever on wedged sessions.
Tool loop cap at 5 iterations guards against silent PEG parser drops
looping indefinitely. Conversation auto-refreshes at 20 turns to prevent
context degradation. delay(35ms) per word makes streaming visible (~28 wps).
clearConversation() on all error paths so the next send starts clean.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Run full test suite and verify build

- [ ] **Step 1: Run all unit tests**

```bash
cd "app" && ../gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with all tests passing. If any test fails, read the failure message and fix before continuing.

- [ ] **Step 2: Verify the APK assembles**

```bash
cd "app" && ../gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Manual smoke test on device**

Install the debug APK and open the Coach tab. Clear history (reset button). Test:

1. Ask **"how many calories did I eat today?"** — model should call `get_today_summary` (no date arg), return real data from DB, not a hallucinated number.
2. Ask **"how many calories did I eat yesterday?"** — model should call `get_today_summary(date="<yesterday's date>")`, not say "fetching today's summary".
3. Ask **"log 150g chicken breast, about 250 calories"** — model should say "I'll log 150g chicken breast at 250 calories — confirm?" then call `log_meal("150g chicken breast", 250)` on confirm.
4. Ask **"log my weight as 83kg"** — model should confirm then call `log_metric("weight_kg", 83.0)`.
5. Watch the response text arrive word-by-word (visible streaming, not instant).
6. Confirm no "Thinking…" hangs beyond 45 seconds.

---

## Self-Review Checklist

- [x] **Spec coverage:** Tool redesign ✓ | System prompt ✓ | Timeout ✓ | Loop cap ✓ | Auto-refresh ✓ | Streaming delay ✓ | Error handling (clearConversation on all paths) ✓
- [x] **No placeholders:** All code blocks are complete and compilable
- [x] **Type consistency:** `DailyMetricsInput` fields match what's used in the existing `logDailyMetrics` function (waistSkinfoldMm, steps, trained, notes all preserved) ✓ | `MealEntryInput` fields match existing usage ✓ | Constants defined before used ✓
- [x] **`log_meal` test at line 97 in existing test file:** passes `protein_g` as extra key — new implementation ignores it, test still passes ✓
