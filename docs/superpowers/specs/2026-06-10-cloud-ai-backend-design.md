# Cloud AI Backend (OpenAI-compatible) — Design Spec

**Date:** 2026-06-10
**Feature:** User-selectable cloud AI backend alongside on-device Gemma
**Scope:** Tier 1 (cloud backend + richer insights + leveled-up coach + capability seam). Tier 2 features deferred to a follow-up spec.
**Branch:** `feature/cloud-ai-backend` (new branch off `main`)

---

## Overview

Add a second AI backend — an **OpenAI-compatible cloud model** reached via a user-supplied API key — selectable alongside the existing on-device Gemma model. When the cloud backend is active, the two existing AI features (insight cards + coach chat) become materially richer, because a frontier model can reason, hold long context, and tool-call reliably where the 2B model cannot.

The change is **purely additive**. The `AiInsightCoordinator` and `CoachCoordinator` interfaces are unchanged, so ViewModels are untouched. Gemma keeps working exactly as today. A routing layer picks the active backend at runtime, and a capability descriptor lets future Tier-2 features (weekly auto-review, cross-signal pattern detection, forecasting) switch on without re-architecting.

---

## Why

The app already collects deep, multi-signal data (food, weight, waist, sleep, energy, soreness, strength). The ceiling on "smarter" is the 2B model, not the data. The entire **"2B Model Behavioural Notes"** section in `docs/ai-coach.md` exists to work around a weak model (rule-ambiguity matching, hallucinated dates, empty text after tool calls, echo phrases, 5-iteration tool cap, 20-turn context reset). A frontier cloud model makes most of that section obsolete and unlocks reasoning the 2B simply cannot do.

---

## Guiding Constraints

- **Both backends coexist; user-switchable.** Gemma remains the offline / fully-private option. Cloud is opt-in.
- **Interfaces unchanged.** `AiInsightCoordinator` and `CoachCoordinator` keep their current signatures. ViewModels and UI screens require no changes to their coordinator contracts.
- **`CoachToolExecutor` reused unchanged.** It is already model-agnostic — receives `(name, args)`, returns JSON strings. Both backends call the same executor.
- **Branch on capabilities, not on "is cloud."** Coordinators and UI read an `AiCapabilities` descriptor. This is the seam Tier-2 features hang off.
- **Graceful fallback.** If the backend is set to Cloud but unconfigured or unreachable, the router falls back to local Gemma rather than failing hard.
- **Secrets handled correctly.** The API key is stored encrypted, never in plain DataStore.
- **Privacy is a conscious trade-off.** In cloud mode, food/body/weight data leaves the device for the user's chosen API. This is documented in the Settings UI. Local Gemma stays fully private.
- **`minSdk = 26`.** Two new dependencies total: OkHttp + `androidx.security:security-crypto`.

---

## Architecture

### Backend selection — `AiBackend` + `AiCapabilities`

```kotlin
// ai/AiBackend.kt
enum class AiBackend { LOCAL, CLOUD }

// ai/AiCapabilities.kt
data class AiCapabilities(
    val richInsights: Boolean,     // multi-sentence, cross-signal output vs. one-liners
    val longContext: Boolean,      // full 28-day multi-signal context; no turn-count reset
    val unboundedToolLoop: Boolean,// no MAX_TOOL_ITERATIONS cap
    val proactiveReview: Boolean,  // Tier-2 seam — false for both backends in this spec
)
```

- `AiBackend` is stored in `UiPreferences` DataStore (new key), with `LOCAL` as the default.
- Each backend declares its capabilities. LOCAL = all false (today's behavior). CLOUD = `richInsights`, `longContext`, `unboundedToolLoop` true; `proactiveReview` false until Tier 2.
- Coordinators and UI read capabilities, never `backend == CLOUD` directly.

### Routing layer — the seam

Two new router classes implement the existing interfaces and forward to the active backend:

```kotlin
// ai/RoutingInsightCoordinator.kt  (implements AiInsightCoordinator)
// ai/RoutingCoachCoordinator.kt    (implements CoachCoordinator)
```

- Each router holds both the Gemma instance and the Cloud instance plus the `AiBackend` flow.
- `state` (and the per-kind `generationState`) is exposed via `flatMapLatest` over the backend flow, so switching backend re-points the observed `StateFlow` to the active implementation.
- Write/confirm calls (`confirmPendingAction`, `cancelPendingAction`, `setSelectedModel`, download lifecycle) forward to the active implementation.
- **Fallback rule:** if `backend == CLOUD` but cloud config is incomplete or a request fails to connect, the router routes to LOCAL and surfaces a one-line notice in the coordinator state.

### `AppContainer` wiring

`AppContainer` builds: the existing Gemma coordinators, the new Cloud coordinators, and the two routers. It hands out the **routers** at the current injection points (currently lines ~79–98). ViewModels receive `AiInsightCoordinator` / `CoachCoordinator` exactly as before.

```
ViewModels ─► RoutingCoordinator ─► { GemmaCoordinator | CloudCoordinator }
                     ▲
                aiBackend flow + AiCapabilities
```

### Cloud client — `data/remote/OpenAiCompatClient`

New client mirroring the structure of `OpenFoodFactsApi`, but using **OkHttp** (added dependency) for clean SSE streaming and cancellation.

- Endpoint: `POST {baseUrl}/chat/completions`, `Authorization: Bearer {apiKey}`.
- **Streaming:** `stream: true`; parse `data:` SSE lines, accumulate `choices[].delta.content` → emit `Flow<String>` (the same contract `generateExplanation()` returns today).
- **Insights path:** `CloudInsightCoordinator : AiInsightCoordinator` builds a richer prompt (full 28-day multi-signal context) and streams a multi-sentence result into the existing per-kind `AiInsightState` machine (`LoadingModel → Generating(partial) → Ready/Error`).
- **Coach path:** `CloudCoachCoordinator : CoachCoordinator` sends `tools: [...]` built from the existing `COACH_TOOLS` schemas, runs the tool-call loop, and drives `CoachState` (`Thinking → Responding(partial) → Idle`), including the `AwaitingConfirmation` write-confirmation flow for `WRITE_TOOLS`. No `MAX_TURNS` reset, no `MAX_TOOL_ITERATIONS` cap.
- The "2B behavioural notes" workaround layer (echo detection, empty-text nudge, date-hallucination rules) stays on the **local path only**.

---

## Tier-1 "unlocked" behavior (cloud path only)

| Surface | Local (Gemma) — unchanged | Cloud — unlocked |
|---|---|---|
| Insight cards | Terse one-liner verdict, trimmed snapshot | Multi-sentence, cross-signal reasoning over full 28-day context |
| Coach context | 20-turn reset, trimmed snapshot | Full conversation retained; long context |
| Coach tools | 5-iteration cap, 2B-reliability guards | Reliable multi-step tool calling, no iteration cap |
| Coach formatting | Plain text | Markdown |

No new screens. Same cards, same coach tab — deeper content when cloud is active.

---

## Settings & Configuration

New cloud config, rendered with the existing `SettingRow` + `GlassInputField` pattern (Settings/More screens):

| Field | Stored where | Secret? |
|---|---|---|
| Backend toggle (Local / Cloud) | `UiPreferences` DataStore | no |
| Base URL (presets: OpenRouter, OpenAI, Groq + custom) | DataStore | no |
| Model ID (free text, e.g. `anthropic/claude-…`) | DataStore | no |
| **API key** | **EncryptedSharedPreferences** | **yes** |

- **API key storage:** `androidx.security:security-crypto` (`EncryptedSharedPreferences`). The library is in maintenance/deprecated status but is the simplest correct option; acceptable for a personal single-user app. Base URL + model ID are not secrets → plain DataStore.
- **Test connection** button — fires one tiny non-streaming completion and reports success/error inline, so misconfiguration is caught in Settings, not mid-coach.
- The existing `ModelVariantSelector` (GEMMA_2B / GEMMA_4B) greys out / is marked not-applicable when backend = Cloud.
- A short privacy note appears under the backend toggle when Cloud is selected.

---

## Data / Preferences changes

- `UiPreferences` (DataStore): new keys — `ai_backend` (string enum), `cloud_base_url` (string), `cloud_model_id` (string). No Room schema changes.
- New `EncryptedSharedPreferences` store for the single `cloud_api_key` value.

---

## Dependencies

- **OkHttp** — SSE streaming + cancellation for the cloud client.
- **`androidx.security:security-crypto`** — encrypted API-key storage.

No existing dependency removed. No existing code removed; Gemma path untouched.

---

## Testing

- **Routing:** unit-test that `RoutingInsightCoordinator` / `RoutingCoachCoordinator` forward to the correct implementation per `AiBackend`, and fall back to LOCAL when cloud config is incomplete.
- **Capabilities:** assert LOCAL and CLOUD expose the expected `AiCapabilities`.
- **Cloud client:** test SSE delta parsing (partial chunks, `[DONE]` terminator, error payloads) against captured fixtures; test tool-call request construction from `COACH_TOOLS` and response parsing.
- **Tool executor:** unchanged — existing tests continue to cover it; add a test confirming the cloud coach routes tool calls through the same `CoachToolExecutor`.
- **Settings:** test that the API key is written to the encrypted store (not DataStore) and that "Test connection" reports success/failure states.
- **No-regression:** existing Gemma insight/coach tests must still pass unchanged.

---

## Explicitly deferred — Tier 2 (next spec)

Built later on the `AiCapabilities` seam (`proactiveReview` etc.), no re-architecting required:

- **Weekly auto-review** — proactive "here's your week + verdict" briefing.
- **Cross-signal pattern detection** — e.g. "soreness spiked the 3 days protein was under 140g."
- **Forecasting** — e.g. "at this rate, waist target ~Aug 12."

---

## Out of Scope

- Multiple simultaneous providers / per-feature backend selection.
- Native (non-OpenAI-compatible) provider SDKs.
- Server-side proxying of the API key.
- Any change to the on-device Gemma behavior or model files.
