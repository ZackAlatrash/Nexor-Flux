# AI Coach + Enriched Verdict Prompt — Design Spec

**Date:** 2026-06-05
**Feature:** Conversational AI Coach screen (new tab) + enriched verdict explanation prompt
**Scope:** Two features built on the existing AI infrastructure from the verdict explanation sprint
**Branch:** `feature/ai-verdict-explanation` (continue on this branch)

---

## Overview

Two additions to the on-device AI layer:

1. **Enriched verdict explanation** — `InsightPromptBuilder` receives full numerical context (weight/waist trends, performance, recovery, adherence, targets) so the generated explanation names specific signals rather than speaking in generalities.

2. **AI Coach screen** — A new bottom nav tab with a multi-turn conversational interface. The coach can answer questions about the user's progress, log meals, record daily metrics, and update the calorie target — all using on-device Gemma via LiteRT-LM tool calling.

Both features share the same `GemmaInsightService` engine instance (no double memory cost) and the same violet AI visual identity established in the verdict explanation sprint.

---

## Guiding Constraints

- The same on-device Gemma 4 E2B model used for verdict explanations powers the coach. No second model download.
- The `Engine` must never be instantiated twice — both features share one `GemmaServiceHolder`.
- Write operations (log meal, log metrics, update target) require explicit user confirmation in the prompt — the model must state what it is about to do and the user must send a confirming reply before the write tool is called.
- AI is opt-in. `CoachState.Unavailable` is shown when the toggle is off or the model isn't downloaded.
- Session-only conversation history — clears when the user navigates away. No Room schema changes.
- `minSdk = 26`. No new dependencies beyond what the verdict explanation sprint already added.

---

## Architecture

### Shared model layer — `GemmaServiceHolder`

Extracted from `RealAiInsightCoordinator` into `AppContainer`. Both coordinators receive it as a constructor parameter.

```kotlin
// ai/GemmaServiceHolder.kt
class GemmaServiceHolder(private val context: Context) {
    val modelFile: File
        get() = File(context.getExternalFilesDir(null), "ai/gemma-4-E2B-it.litertlm")

    private var _service: GemmaInsightService? = null

    fun getOrCreateService(): GemmaInsightService =
        _service ?: GemmaInsightService(
            modelPath = modelFile.absolutePath,
            cacheDir = context.cacheDir.absolutePath,
        ).also { _service = it }

    fun release() {
        _service?.release()
        _service = null
    }
}
```

`RealAiInsightCoordinator` stops creating `GemmaInsightService` internally and delegates to `GemmaServiceHolder` instead.

---

### Enriched prompt context — `InsightContext`

```kotlin
// ai/InsightContext.kt
data class InsightContext(
    val result: AdjustmentResult,
    val input: AdjustmentInput,
    val targetCalories: Int,
    val targetProteinG: Int,
)
```

`AiInsightCoordinator.onAiCardVisible` and `retryGeneration` both change their parameter from `AdjustmentResult` to `InsightContext`. `FakeAiInsightCoordinator` and `RealAiInsightCoordinator` update their signatures — the change is mechanical.

`DashboardUiState` gains `adjustmentInput: AdjustmentInput? = null`. `DashboardViewModel.buildState()` already computes `AdjustmentInput` locally but currently discards it — it now stores it in state. `onAiCardVisible()` assembles `InsightContext` only when `adjustmentInput` is non-null (it always will be after the first computation; the null default exists only to keep `DashboardUiState()` constructable).

---

### `InsightPromptBuilder` — enriched prompt

`buildWeeklySummaryPrompt(context: InsightContext)` replaces the current `buildWeeklySummaryPrompt(result: AdjustmentResult)`.

Prompt template:
```
You are a concise nutrition coach explaining a weekly calorie verdict to an athlete.
Write exactly 2–3 sentences in plain English. Do not change the verdict.
Explain the reasoning — do not repeat raw numbers already shown on screen.
Be specific about which signals drove the decision. Keep the tone calm and direct.

Verdict: {verdict.name} ({recommendedCalorieChange:+d} kcal)
Reason: {reasonCodes.joinToString()}

Signals this week:
- Weight trend: {weightTrendKgPerWeek} kg/week
- Waist trend: {waistTrendCmPerWeek} cm/week
- Performance: {performanceTrend}
- Recovery: {recoveryTrend}
- Adherence: {adherencePercent}% over {daysLogged} days
- Weeks in current phase: {weeksSincePhaseStart}

Calorie target: {targetCalories} kcal | Protein target: {targetProteinG}g
```

Output token cap: 128 (unchanged).

---

### `CoachCoordinator` interface

```kotlin
// ai/CoachCoordinator.kt

interface CoachCoordinator {
    val state: StateFlow<CoachState>
    fun sendMessage(text: String)
    fun clearHistory()
}

sealed class CoachState {
    object Unavailable : CoachState()
    object Ready : CoachState()
    data class Idle(val history: List<ChatMessage>) : CoachState()
    data class Thinking(val history: List<ChatMessage>) : CoachState()
    data class Responding(val history: List<ChatMessage>, val partial: String) : CoachState()
    data class Error(val history: List<ChatMessage>, val message: String) : CoachState()
}

data class ChatMessage(val role: Role, val text: String)

enum class Role { User, Assistant }
```

`CoachState.Unavailable` is emitted when:
- `aiInsightsEnabled == false`, OR
- The model file does not exist (`AiInsightState` is `Disabled`, `ModelMissing`, `Downloading`, or `DownloadFailed`)

`RealCoachCoordinator` observes `aiInsightCoordinator.state` to derive model availability.

---

### `RealCoachCoordinator`

```kotlin
// ai/RealCoachCoordinator.kt
class RealCoachCoordinator(
    private val serviceHolder: GemmaServiceHolder,
    private val insightCoordinator: AiInsightCoordinator,
    private val toolExecutor: CoachToolExecutor,
    private val scope: CoroutineScope,
) : CoachCoordinator
```

**Conversation lifecycle:**
- A `Conversation` object is created lazily on the first `sendMessage()` call (or on `clearHistory()`).
- The `Engine` is initialized via `serviceHolder.getOrCreateService().initialize()`.
- The system prompt (see below) is injected as the first user message + assistant acknowledgement.
- On `clearHistory()`, the existing `Conversation` is closed and a new one is created.

**Tool call loop:**
```
sendMessage(userText):
  1. append ChatMessage(User, userText) to mutableHistory
  2. _state = Thinking(history)
  3. response = conversation.sendMessage(userText)
  4. while response has Content.FunctionCall:
       a. emit tool status text (e.g. "Reading your food log…")
       b. result = toolExecutor.execute(functionCall)
       c. response = conversation.sendMessage(result)
  5. extract Content.Text from response, stream word-by-word as Responding(history, partial)
  6. append ChatMessage(Assistant, fullText) to mutableHistory
  7. _state = Idle(history)
```

Errors from tool execution are caught and returned as a JSON error payload fed back into the conversation. Unrecoverable exceptions transition to `Error` state.

---

### `CoachToolExecutor`

```kotlin
// ai/CoachToolExecutor.kt
class CoachToolExecutor(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
)
```

Six tools defined as `OpenApiTool` JSON schemas and registered via `ConversationConfig.tools`:

| Tool name | Direction | What it does |
|---|---|---|
| `get_today_summary` | Read | Returns today's `DayLog`: meals list with name/calories/macros, daily log metrics (weight, waist, sleep, energy, hunger, soreness), macro totals |
| `get_weekly_trends` | Read | Returns 7-day calorie history, weight trend (kg/week), waist trend (cm/week), adherence %, days logged |
| `get_plan` | Read | Returns current `PlanPreferences`: calorie target, protein/carbs/fat targets |
| `log_meal` | Write | Calls `logRepository.addMeal()` with today's date. Args: `name`, `calories`, `protein_g`, `carbs_g`, `fat_g`, optional `meal_type` (defaults to "Snack") |
| `log_daily_metrics` | Write | Calls `logRepository.saveDailyMetrics()` for today. All args optional: `weight_kg`, `waist_cm`, `sleep_hours`, `energy_score` (1-10), `hunger_score` (1-10), `soreness_score` (1-10) |
| `update_calorie_target` | Write | Calls `planRepository.save()` with updated `targetCalories`. Args: `new_target_kcal` |

All tool results are returned as compact JSON strings.

**Write confirmation rule:** The system prompt instructs the model to always state the action in a prior message before calling a write tool. This is a prompt-level constraint, not enforced programmatically.

---

### System prompt

Injected as the first exchange in every new `Conversation`:

```
You are a personal nutrition and fitness coach embedded in a body recomposition tracking app.

Current plan:
- Calorie target: {targetCalories} kcal/day
- Protein: {targetProteinG}g | Carbs: {targetCarbsG}g | Fat: {targetFatG}g

Current weekly verdict: {verdict} — {verdictSummary}

Rules:
1. Be concise: 1-3 sentences per response unless the user explicitly asks for detail.
2. Use tools when asked for specific numbers (weight, today's food, trends) — do not guess.
3. Before calling any write tool (log_meal, log_daily_metrics, update_calorie_target),
   tell the user what you are about to write and wait for their confirmation.
4. Never fabricate data. If unsure, say so and offer to fetch it.
5. Stay focused on nutrition, body composition, training, and recovery.
```

`CoachViewModel` reads current `DashboardUiState` (via `planRepository.preferences` + the latest verdict from `DashboardViewModel`) to populate the plan and verdict fields before starting a conversation.

---

## Navigation Changes

### Bottom nav — 5 tabs (Progress removed, Coach added)

| Index | Route | Label | Icon |
|---|---|---|---|
| 0 | `home` | Home | `Icons.Default.Home` |
| 1 | `body` | Body | `Icons.Default.Person` |
| 2 | `food` | (center FAB) | `Icons.Default.Add` |
| 3 | `coach` | Coach | `Icons.Default.AutoAwesome` |
| 4 | `more` | More | `Icons.Default.MoreHoriz` |

### Progress moved to More screen

A tappable navigation row is added to `MoreScreen` above the existing sections:

```
▸  Progress
   View your trends and adherence history
```

Tapping navigates to the existing Progress screen. The Progress screen itself is unchanged.

### `AppNavGraph` changes

- Add `composable(Routes.Coach) { CoachScreen(...) }`
- Remove `TopLevelDestination.Progress` from `tabRoutes`; add `TopLevelDestination.Coach`
- Progress route stays in the nav graph for deep-link compatibility; it is reached via More, not the tab bar

---

## UI — Coach Screen

### File: `ui/coach/CoachScreen.kt`

**When `CoachState.Unavailable`:**
Full screen shows centered `AiInsightCard` (Static border) with:
- Title: "AI Coach"
- Body: "Download the AI model in More → AI Model to unlock the coach."
- Button: "Go to More" → navigates to More tab

**When `CoachState.Ready`:**
Full screen with:
- Header: "Coach" title + clear icon (disabled, history empty)
- Centered area with app name or sparkle graphic
- Three suggestion chips at bottom (above input bar):
  - "How are my calories this week?"
  - "Log 150g chicken breast to lunch"
  - "Should I change my target?"
- Input bar (TextField + send button) — enabled

**When `CoachState.Idle / Thinking / Responding`:**
- `LazyColumn` of `ChatMessage` bubbles, `reverseLayout = false`, auto-scrolled to bottom on new message
- **User bubble**: right-aligned, `TintedSurface` background, `RoundedCornerShape(16.dp)`
- **AI bubble**: left-aligned, wrapped in `AiInsightCard`:
  - `AiBorderMode.Generating` while the message is still `Responding`
  - `AiBorderMode.Ready` on completion (fades to Static naturally)
- **Thinking indicator**: `AiInsightCard(AiBorderMode.Preparing)` with `AiBadge` + "Thinking…" label — shown when `Thinking` state
- **Tool status text**: small muted label between thinking and first response chunk — e.g. *"Reading your food log…"* — replaced by the response as it arrives
- **Input bar**: `OutlinedTextField` (full width) + `LiquidActionButton` (send). Disabled during `Thinking`/`Responding`.
- **Header clear button**: top-right icon, calls `viewModel.clearHistory()`. Disabled when history is empty.

---

## New Files

| File | Purpose |
|---|---|
| `ai/GemmaServiceHolder.kt` | Shared `GemmaInsightService` instance + model file path |
| `ai/InsightContext.kt` | Enriched prompt context bundling result + input + targets |
| `ai/CoachCoordinator.kt` | `CoachCoordinator` interface, `CoachState`, `ChatMessage`, `Role` |
| `ai/RealCoachCoordinator.kt` | LiteRT-LM conversation + tool call loop |
| `ai/CoachToolExecutor.kt` | 6 tool implementations mapping to repositories |
| `ui/coach/CoachViewModel.kt` | UI state, delegates to `CoachCoordinator` |
| `ui/coach/CoachScreen.kt` | Full chat UI |

## Modified Files

| File | Change |
|---|---|
| `ai/AiInsightCoordinator.kt` | `InsightContext` replaces `AdjustmentResult` in `onAiCardVisible` + `retryGeneration` |
| `ai/FakeAiInsightCoordinator.kt` | Signature update only |
| `ai/RealAiInsightCoordinator.kt` | Use `GemmaServiceHolder`; update signature |
| `ai/InsightPromptBuilder.kt` | Richer prompt from `InsightContext` |
| `ui/dashboard/DashboardViewModel.kt` | Add `adjustmentInput` to `DashboardUiState`; build `InsightContext` |
| `core/AppContainer.kt` | Add `GemmaServiceHolder`, `CoachCoordinator`; wire `CoachViewModel` factory |
| `ui/navigation/Routes.kt` | Add `Coach = "coach"` |
| `ui/navigation/TopLevelDestination.kt` | Add `Coach` destination; remove `Progress` from top-level |
| `ui/navigation/AppNavGraph.kt` | Add Coach composable; Progress accessible via More |
| `ui/RecompApp.kt` | Replace Progress tab with Coach tab in `tabRoutes` |
| `ui/more/MoreScreen.kt` | Add Progress navigation row |

---

## Out of Scope

- Persistent conversation history across sessions (Room table for chat)
- Streaming tokens in real-time from LiteRT-LM (word-by-word from completed response, same as verdict card)
- Cloud model fallback
- Voice input
- Proactive coach notifications
- iOS / other platforms
