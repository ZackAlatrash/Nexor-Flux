# AI Coach System

On-device AI coach powered by Gemma 4 2B running via LiteRT-LM. Two separate AI features share a single Engine instance.

**Model file:** `<externalFilesDir>/ai/gemma-4-E2B-it.litertlm`
(dropped manually onto the device; not bundled in the APK)

---

## Two AI Features

| Feature | Class | What it does |
|---|---|---|
| Insight cards | `GemmaInsightCoordinator` | Single-turn: generates a one-liner verdict shown on the dashboard. Uses `generateExplanation()` — streaming, no tool calls. |
| Coach chat | `GemmaCoachCoordinator` | Multi-turn: full conversation with tool calling. User types messages; model can read data and log on their behalf. |

Both features share the same `GemmaInsightService` / Engine via `GemmaServiceHolder`. They never run concurrently — `inferenceLock` enforces mutual exclusion.

---

## Class Map

```
GemmaServiceHolder          App-scoped owner of GemmaInsightService.
                            Lazy-creates it; closes it on release().

GemmaInsightService         Owns the LiteRT-LM Engine.
                            - initLock: prevents double-init
                            - inferenceLock: mutual exclusion across ALL engine use
                            - generateExplanation(): streaming single-turn
                            - createConversation(): for multi-turn coach
                            - withInferenceLock(): lets coordinator hold lock across turns

GemmaInsightCoordinator     Drives insight card generation. Calls generateExplanation().
AiInsightCoordinator        Interface for the above (StubInsightCoordinator for tests).

GemmaCoachCoordinator       Drives multi-turn coach conversation.
                            Implements CoachCoordinator interface.
                            Manages Conversation lifecycle, tool call loop, state machine.

CoachToolExecutor           Executes tool calls. Model-agnostic — receives (name, args)
                            and returns JSON strings. Backed by LogRepository + PlanRepository.

CoachCoordinator            Interface: sendMessage(), clearHistory(), confirm/cancelPendingAction()
CoachState                  Sealed class: Unavailable | Ready | Idle | Thinking | Responding
                                          | Error | AwaitingConfirmation

CoachViewModel              Thin wrapper — delegates everything to CoachCoordinator.
```

---

## Tool List

All tools are defined as `SchemaTool` entries in `COACH_TOOLS` (JSON schema only — `execute()` is never called on them). Dispatch goes through `CoachToolExecutor.execute(name, args)`.

| Tool | Args | Returns | Notes |
|---|---|---|---|
| `get_today_summary` | `date?: String` (ISO `YYYY-MM-DD`) | JSON: meals, macro totals, daily log | Omit `date` for today. Pass ISO date for any past date. Falls back to today if date fails to parse — always pass ISO format. |
| `get_weekly_trends` | — | JSON: 7-day daily macros + adherence % | Last 7 days ending today. |
| `search_food_library` | `query: String`, `grams?: Number` | JSON: up to 5 matches, macros scaled to `grams` if provided | Fuzzy scored: exact > starts-with > contains > all-words. |
| `log_meal` | `name`, `meal_type`, `grams?`, `calories?`, `protein_g?`, `carbs_g?`, `fat_g?` | `{success, logged, calories}` | Always checks food library first. Macro args only needed if food not in library. **Write tool — requires user confirmation.** |
| `log_metric` | `metric: String`, `value: Number` | `{success, metric, value}` | Metrics: `weight_kg`, `waist_cm`, `sleep_hours`, `energy_score`, `hunger_score`, `soreness_score`. Scores must be whole numbers 1–10. **Write tool — requires user confirmation.** |
| `update_calorie_target` | `target_calories: Int` | `{success, new_target_calories}` | Range: 500–6000. **Write tool — requires user confirmation.** |

### Write Tool Confirmation Flow

Tools in `WRITE_TOOLS` (`log_meal`, `log_metric`, `update_calorie_target`) pause for user confirmation before executing:

1. `CoachState.AwaitingConfirmation` is emitted with a `PendingCoachAction` (tool name, args, display text).
2. UI shows a confirmation dialog.
3. User taps Confirm → `confirmPendingAction()` → `CompletableDeferred.complete(true)` → tool runs.
4. User taps Cancel → `cancelPendingAction()` → tool response sent as `{"cancelled": true}`.
5. `inferenceLock` is **not** held during the wait — the insight flow can run freely while the user reads the dialog.

---

## System Prompt Structure

Built in `GemmaCoachCoordinator.buildSystemPrompt()` once per conversation (at `createConversation()`).

```
You are a nutrition coach...
Today: <date> (<DayName>) | Yesterday: <date>

Plan: <calories> kcal | P <g> | C <g> | F <g>

=== USER PROFILE ===              ← only present if user has set any fields
Goal: X | Sex: X | Age: X | Height: X cm | Activity: X | Gym sessions/week: X
=== END PROFILE ===

=== TODAY'S DATA SNAPSHOT ===     ← fetched once at conversation start
<get_today_summary JSON>
=== END SNAPSHOT ===

Rules (follow exactly):
1. TODAY questions → answer from snapshot, do NOT call get_today_summary
2. YESTERDAY or past date questions → MUST call get_today_summary(date="YYYY-MM-DD"), do NOT use snapshot
3. Weekly questions → call get_weekly_trends()
4. After a tool returns → answer in 1–3 sentences from the JSON only
5. Logging food → call log_meal(...), tool checks library automatically
6. Stay on topic
```

### What's Static vs. Tool-Fetched

| Data | Where | Why |
|---|---|---|
| Plan targets | System prompt (static) | Never changes mid-conversation; no tool iteration wasted |
| User profile | System prompt (static) | Read-only, session-invariant |
| Today's snapshot | System prompt (pre-fetched) | Avoids a tool iteration for the most common read |
| Yesterday / past data | Tool call (`get_today_summary`) | Not pre-fetched; model must call the tool |
| Weekly trends | Tool call (`get_weekly_trends`) | Not pre-fetched |

**Never add a tool for data that is static or session-invariant.** The 2B model has limited tool iterations; every unnecessary tool call risks hitting the cap or producing an empty response.

---

## Concurrency Model

```
initLock (GemmaInsightService)
  └── Prevents double-initialisation on first call. Released immediately after.

inferenceLock (GemmaInsightService)
  └── Mutual exclusion across ALL engine use.
      - generateExplanation() holds it for full streaming duration.
      - GemmaCoachCoordinator holds it per sendMessage() call (not per turn)
        so the insight flow can run during user-confirmation wait.
      - release() holds it to prevent use-after-close.

turnLock (GemmaCoachCoordinator, Mutex)
  └── Serialises handleMessage() so only one user message is in-flight at a time.
      clearConversation() is also dispatched through turnLock to wait for
      any in-progress turn before closing the Conversation object.
```

---

## Limits & Guardrails

| Limit | Value | Where |
|---|---|---|
| Turn timeout | 45 s | `TIMEOUT_MS` in `GemmaCoachCoordinator` |
| Max tool iterations per turn | 5 | `MAX_TOOL_ITERATIONS` |
| Max turns before context reset | 20 | `MAX_TURNS` |
| Calorie target range | 500–6000 kcal | `CoachToolExecutor.updateCalorieTarget` |
| Score metrics range | 1–10, whole numbers | `CoachToolExecutor.logMetric` |

After the turn limit the model emits a "context reset" notice and `clearConversation()` is called (Conversation object closed, new one created on next message). History is kept in `mutableHistory` so the chat UI stays intact.

---

## 2B Model Behavioural Notes

These are known tendencies of the Gemma 4 2B model that affect how the system prompt must be written:

- **Rule ambiguity causes wrong-rule matching.** If two rules could match a question, the model picks the simpler one (usually Rule 1). Rules must be separated by clear, non-overlapping conditions — e.g., date-based ("today only" vs. "yesterday or past") rather than intent-based ("read-only" vs. "write").
- **Hallucinated dates.** When answering from the snapshot, the model may label the answer with a different date if the user's question mentions a date. Always make the snapshot's date scope explicit in the rule.
- **Empty text after tool sequence.** After one or more tool calls the model sometimes returns no text. `GemmaCoachCoordinator` sends a nudge ("Confirm what you just did in one sentence.") once before giving up with an error.
- **Echo phrases.** After a tool returns data the model sometimes echoes its pre-tool planning text ("I need to call…") instead of answering. Detected via `containsEchoPhrase()` and corrected with a follow-up prompt.
- **Integer values as doubles.** LiteRT-LM may surface integer JSON values as `"500.0"`. `CoachToolExecutor` uses `toIntOrNull() ?: toDoubleOrNull()?.toInt()` everywhere integers are expected.
