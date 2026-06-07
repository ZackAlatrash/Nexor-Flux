---
name: ai-coach-reliability
description: Full reliability overhaul of the on-device AI coach — tool redesign, prompt optimisation, conversation management, timeout/error handling, and visible streaming
metadata:
  type: project
---

# AI Coach Reliability Overhaul

**Date:** 2026-06-06  
**Branch:** feature/ai-verdict-explanation  
**Model:** Gemma 4 E2B via LiteRT-LM 0.11.0, CPU backend  

## Problem Statement

The AI coach produces unreliable results. Root causes identified through field research and code review:

1. **Write tools have 0% pass rate.** `log_meal` (6 args) and `log_daily_metrics` (6 args) exceed the Gemma 4 E2B limit. Field data shows 4+ arg tools score 0% on this model; the tools will never work as designed.
2. **Date confusion.** The previous session added today's date to the system prompt, but the prompt is too verbose for a 2B model to apply consistently.
3. **No timeout.** A hung LiteRT-LM session shows "Thinking…" forever — no watchdog exists.
4. **Silent tool-call drops.** LiteRT-LM's PEG parser silently discards malformed tool calls; the tool loop can run indefinitely.
5. **Invisible streaming.** The word-by-word loop runs at CPU speed with no delay — the text appears instantly, defeating the animation.
6. **Context degradation.** The `Conversation` object is never refreshed, causing quality to drop over long sessions.

## Design

### 1. Tool Architecture

All tools must have ≤2 arguments. This is the hard reliability ceiling for Gemma 4 E2B.

**Pass rates by arg count (field data, 325 cases):**

| Args | Pass rate |
|---|---|
| 0–1 | 87–100% |
| 2 | 78–88% |
| 3 | 43–67% |
| 4+ | 0% |

**Unchanged tools (already ≤2 args):**

| Tool | Args | Purpose |
|---|---|---|
| `get_today_summary(date?)` | 0–1 | Day food log + totals. `date` = YYYY-MM-DD; omit for today |
| `get_weekly_trends()` | 0 | Last 7 days calorie history |
| `get_plan()` | 0 | Current calorie + macro targets |
| `update_calorie_target(target_calories)` | 1 | Change daily calorie goal |

**Replaced tools:**

`log_meal(name, calories)` — replaces the 6-arg version. Drops `protein_g`, `carbs_g`, `fat_g`, `meal_type` from AI scope. Macros remain editable in the manual food log UI. Calorie tracking is the primary value; requiring the model to emit 6 structured numbers from natural language is out of scope for a 2B on-device model.

`log_metric(metric, value)` — replaces `log_daily_metrics`. Single tool for all body metrics. `metric` is one of: `weight_kg` | `waist_cm` | `sleep_hours` | `energy_score` | `hunger_score` | `soreness_score`. `value` is a number. The executor maps each metric name to the correct field in `DailyMetricsInput`, preserving all existing values for fields not being updated.

**Final tool set: 6 tools, all ≤2 args.**

### 2. System Prompt

Shortened from ~25 lines to ~15 lines. Every line earns its place.

```
You are a nutrition coach in a body recomposition tracking app.
Today: {date} ({dayOfWeek}) | Yesterday: {yesterday}

Plan: {calories} kcal | P {protein}g | C {carbs}g | F {fat}g

Tools:
- get_today_summary(date?) — food log + totals for a day. date=YYYY-MM-DD, default=today
- get_weekly_trends() — last 7 days of calories
- get_plan() — current targets
- log_meal(name, calories) — add food to today's log
- log_metric(metric, value) — metric: weight_kg|waist_cm|sleep_hours|energy_score|hunger_score|soreness_score
- update_calorie_target(target_calories) — change daily calorie goal

Rules:
1. Be concise: 1–3 sentences unless detail is asked for.
2. Always call a tool before quoting any numbers — never guess.
3. Yesterday = get_today_summary(date="{yesterday}"). Named days = compute the date and use get_today_summary.
4. Before any write tool: state what you'll log and wait for the user to confirm.
5. Stay on topic: nutrition, body composition, training, recovery only.
```

Key properties:
- Explicit tool listing with parameter types so the model knows exactly what to emit
- Rule 3 spells out the date mapping — eliminates "fetching today's summary" for yesterday queries
- Rule 4 is the confirmation gate — prevents silent DB writes

### 3. Conversation Management

**Timeout**

Every `conv.sendMessage()` call is wrapped in `withTimeout(45_000L)`. On `TimeoutCancellationException`: clear the conversation, surface `CoachState.Error("Took too long — try again.")`. Next send creates a fresh session.

**Tool loop cap**

The tool-calling loop runs for a maximum of 5 iterations. If the model keeps emitting tool calls after 5 rounds without producing final text, break out and surface an error. Prevents infinite loops caused by silent tool-call drops.

**Auto-refresh at 20 turns**

Track `turnCount` in `RealCoachCoordinator`. After 20 turns, call `clearConversation()` and reset `turnCount = 0` before the next message. The `mutableHistory` list shown in the UI is preserved — only the engine-side `Conversation` object is replaced. The system prompt is re-injected at recreation time, picking up the current date for long-running sessions.

### 4. Visible Streaming

Add `delay(35L)` between word emissions in the streaming loop (~28 words/sec). The animated `AiBorderMode.Generating` border is already in place and looks correct; it just needs the pacing to be visible.

```kotlin
for (word in words) {
    sb.append(if (sb.isEmpty()) word else " $word")
    _state.value = CoachState.Responding(mutableHistory.toList(), partial = sb.toString())
    delay(35L)
}
```

### 5. Error Handling

| Failure mode | Detection | Response | Recovery |
|---|---|---|---|
| Timeout | `TimeoutCancellationException` | "Took too long — try again." | `clearConversation()` |
| Tool loop stuck | Iteration count ≥ 5 | "Something went wrong — try again." | `clearConversation()` |
| General exception | `catch (e: Exception)` | "Something went wrong — try again." | `clearConversation()` |

All three paths call `clearConversation()` so the next user message starts from a clean session rather than replaying into a broken one.

## Files Changed

| File | Change |
|---|---|
| `ai/RealCoachCoordinator.kt` | New system prompt, tool schemas, timeout, loop cap, auto-refresh, streaming delay |
| `ai/CoachToolExecutor.kt` | Replace `log_meal` (6-arg) with `log_meal` (2-arg); replace `log_daily_metrics` with `log_metric(metric, value)` |

No other files change. `AppContainer.kt` was already updated in the previous session to pass `dateProvider`.

## Expected Outcomes

| Metric | Before | After |
|---|---|---|
| `log_meal` pass rate | 0% | ~80% |
| `log_metric` pass rate | 0% | ~80% |
| Read tool pass rate | 87–100% | 87–100% (unchanged) |
| Hung "Thinking…" UI | Possible (unbounded) | Impossible (45s cap) |
| Streaming visible | No | Yes (~28 wps) |
| Context degradation | After ~10 turns | Reset at 20 turns |

## Out of Scope

- Macro logging via AI (macros stay in manual UI)
- Meal type assignment via AI
- Separate Engine instances for coach vs insight card (Option C — not needed unless collision is observed)
- Model upgrade to E4B
