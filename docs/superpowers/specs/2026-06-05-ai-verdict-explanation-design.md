# AI Verdict Explanation — Design Spec

**Date:** 2026-06-05
**Feature:** Calorie verdict explanation powered by on-device Gemma 4 E2B via LiteRT-LM
**Scope:** AI infrastructure foundation + first AI feature (verdict explanation on Stats screen)

---

## Overview

After the `AdjustmentEngine` produces a weekly verdict (Hold / Increase / Decrease / Wait for Data), display a short natural-language explanation of the reasoning in plain English. The explanation is generated on-device by Gemma 4 E2B using LiteRT-LM. The feature is entirely optional — users opt in via the existing AI Insights toggle in More, and must explicitly download the ~2.6 GB model before AI features activate. Users who do not opt in, or whose device cannot handle it, see no change from today.

The first feature also establishes the AI visual identity and the full AI infrastructure layer that all future AI features will build on.

---

## Guiding Constraints

- AI is an explanation layer only. It never changes or overrides the deterministic `AdjustmentResult`.
- The toggle already exists at `MoreScreen.kt:184` (`UiPreferences.aiInsightsEnabled`). This spec does not move it.
- `minSdk = 26` — compatible with LiteRT-LM.
- Model size: ~2.6 GB. This must be communicated clearly to the user before download begins.
- The explanation only appears when `verdict != WAIT_FOR_DATA`. No download CTA, no AI card for `WAIT_FOR_DATA`.

---

## Architecture

### State Machine

`AiInsightState` represents the model/download/generation lifecycle. The toggle is a separate input read from `UiPreferences.aiInsightsEnabled` — it is not part of this state.

```kotlin
sealed class AiInsightState {
    object Disabled                                // toggle off
    object ModelMissing                            // toggle on, no model file on device
    data class Downloading(val progress: Float?)   // 0.0–1.0; null = indeterminate
    object DownloadFailed
    object ModelReady                              // file present, engine not loaded
    object LoadingModel                            // engine initializing ("Preparing model…")
    data class Generating(val partialText: String) // streaming chunks from LiteRT-LM
    data class Ready(val text: String)             // complete explanation
    data class Error(val message: String)          // human-readable, not raw exception
}
```

### `AiInsightCoordinator` (new, lives in `ai/`)

Owns all heavy AI concerns. Injected from `AppContainer` alongside other services. The ViewModel never touches `DownloadManager` or the LiteRT-LM engine directly.

**Responsibilities:**
- `state: StateFlow<AiInsightState>` — observed by ViewModel
- `requestDownload()` — validates Wi-Fi, free space, then starts download via `DownloadManager`; emits progress; validates checksum on completion
- `cancelDownload()` — cancels in-flight download
- `deleteModel()` — removes model file, resets state to `ModelMissing`
- `generateExplanation(result: AdjustmentResult)` — initializes LiteRT-LM engine lazily, runs inference, emits streaming chunks via state updates

**Download defensiveness:**
- Wi-Fi check before starting (warn if on cellular, don't block)
- Free space check (need ≥ 3 GB free to be safe)
- Resumable (use `DownloadManager` which handles this natively)
- Checksum/hash validation after download completes
- Indeterminate progress fallback if `DownloadManager` does not report total bytes
- Cancel and delete-model actions always available

**Model file location:** `context.filesDir/ai/gemma-4-E2B-it.litertlm`

**Model source:** `litert-community/gemma-4-E2B-it-litert-lm` on HuggingFace

**LiteRT-LM backend priority:** QNN (Snapdragon NPU) → OpenCL (Adreno GPU) → CPU fallback

### `GemmaInsightService` (replaces existing stub)

Thin wrapper around the LiteRT-LM `Engine`. Initialized lazily when first inference is requested. Exposes a single `generateExplanation(prompt: String): Flow<String>` that emits streamed tokens.

### `InsightPromptBuilder` (existing, prompt refined)

Existing "explain without changing verdict" contract is correct. Prompt tuned to:
- Target output: 2–3 sentences, coaching voice, plain English
- Explicit instruction: do not suggest a different verdict, do not repeat numbers already shown on screen
- Max output tokens: 128 (sufficient for 2–3 sentences, prevents runaway output)

### `DashboardViewModel` changes

- Injects `AiInsightCoordinator` from `AppContainer`
- Exposes `aiInsightState: StateFlow<AiInsightState>`
- Exposes `onAiCardVisible(result: AdjustmentResult)` — the explicit trigger for generation
- Exposes `requestModelDownload()`, `cancelDownload()`, `retryGeneration()`

### Generation guard (inside `onAiCardVisible`)

Generation triggers only when all conditions are true:
```
aiInsightsEnabled == true
  && verdict != WAIT_FOR_DATA
  && aiInsightState == ModelReady (or LoadingModel already)
  && resultKey != lastGeneratedKey
```

`resultKey` is a stable hash of `AdjustmentResult` fields. Stored in memory only — no Room persistence. Re-generation also available via a small refresh action on the `Ready` card.

### First implementation slice

Build and validate UX before touching the real model:
1. Wire `AiInsightState` machine and toggle integration
2. Implement `FakeAiInsightCoordinator` that streams a deterministic explanation from `AdjustmentResult.reasonCodes` at ~20 tokens/s
3. Build all UI states against the fake coordinator
4. Once UX is stable: implement real model download + LiteRT-LM engine

---

## UX Flow

### Enabling AI Insights

User toggles "AI Insights" on in More screen (existing toggle). No download prompt at that moment. The download CTA appears contextually on the Stats screen when a real verdict is available.

### Stats screen — per-state behaviour

The AI card appears **only when `verdict != WAIT_FOR_DATA`**. When the verdict is `WAIT_FOR_DATA`, show a single passive line in place of the card: *"AI explanations appear once a weekly verdict is ready."*

| State | What the user sees |
|---|---|
| `Disabled` | Nothing. Stats screen unchanged. |
| `ModelMissing` | `AiInsightCard` with title "Why this verdict", body: "Understand the reasoning behind this verdict.", subtext: "Requires a ~2.6 GB download · Wi-Fi recommended", "Download Model" button. |
| `Downloading` | Progress bar (indeterminate pulse if total bytes unknown) + "X.X GB of 2.6 GB" or "Downloading…" + Cancel link. |
| `DownloadFailed` | Human-readable: "Download failed — check your connection." + "Retry" button. |
| `ModelReady` / `LoadingModel` | Shimmer pulse + label "Preparing model…" |
| `Generating(partialText)` | Streamed text appears naturally as chunks arrive. Animated border active. |
| `Ready(text)` | Full explanation shown. Border settles. Small refresh action visible (icon, top-right of card). |
| `Error` | Human-readable: "Something went wrong." + "Try again" button. |

### Generation trigger

`LaunchedEffect(adjustmentResult)` in the Stats screen composition calls `viewModel.onAiCardVisible(result)`. This is an explicit event, not driven by recomposition directly.

### Model management

Accessible from More screen, below the AI Insights toggle: storage used + "Delete model" action. "Download again" action shown only when model is missing. This lives next to the toggle, not in a separate Settings screen.

---

## Visual Identity

### Principle

AI content gets exactly one distinctive treatment that never appears elsewhere in the app. Identity is carried by the card border animation and a small badge. Card background, padding, corner radius, and blur are identical to `TintedCard` — structural restraint is intentional.

### `AiInsightCard` (new: `ui/component/AiInsightCard.kt`)

Built on the same frosted+tinted foundation as `TintedCard`. The static `.border(1.dp, TintedBorder)` is replaced by a Canvas-drawn animated border controlled by `AiBorderMode`.

```kotlin
enum class AiBorderMode { Preparing, Generating, Ready, Static }
```

**Border behaviour:**

| Mode | Visual |
|---|---|
| `Preparing` | Slow full-perimeter pulse, entire border brightens/dims, ~2s cycle |
| `Generating` | Violet comet travels clockwise around card at ~1.5s/revolution |
| `Ready` | Comet decelerates and fades over ~1s → `Static`. Does not replay. |
| `Static` | Plain `TintedBorder`, no animation (reduced-motion default and post-Ready rest state) |

**Comet appearance:** leading edge near-white `Color(0xA0FFFFFF)`, core `Violet300 (#c4b5fd)`, tail `Violet500 (#8B5CF6)` fading to transparent.

**Animation implementation:** `infiniteTransition` + `animateFloat` for phase offset, following the same pattern as `AuroraBackground`. Isolated entirely within `AiInsightCard.kt`.

**Reduced-motion fallback:** On initialisation, check system animation scale. If animations are disabled or scale is 0, `AiBorderMode` is forced to `Static` permanently. Static: `TintedBorder` + badge only.

**Stability:** Completion to `Static` is tracked via `remember { mutableStateOf(false) }` set once per `Ready` transition. Scrolling away and back does not replay the finish animation.

**Previews:** `@Preview` annotations for all four `AiBorderMode` states.

### `AiBadge` (new: `ui/component/AiBadge.kt`)

Small `✦ AI` label. Appears in the top-right of the card header row. ~9sp, `Violet400`, slightly muted. This is the only text in the app that says "AI".

### Card title

`"Why this verdict"` — not `"AI Explanation"`. The badge handles AI attribution; the title describes the content.

### Text reveal

Streamed chunks from `Generating(partialText)` are displayed directly as they arrive. No artificial per-character delay — the typewriter feel comes from LiteRT-LM's natural streaming cadence (~15–20 tokens/s on GPU). Per-character reveal is not used for MVP.

---

## New Files

| File | Purpose |
|---|---|
| `ai/AiInsightState.kt` | State machine sealed class |
| `ai/AiInsightCoordinator.kt` | Interface + real implementation |
| `ai/FakeAiInsightCoordinator.kt` | Deterministic fake for UX validation |
| `ai/GemmaInsightService.kt` | Replaces stub — LiteRT-LM engine wrapper |
| `ai/InsightPromptBuilder.kt` | Existing file, prompt refined |
| `ui/component/AiInsightCard.kt` | Card + animated border + previews |
| `ui/component/AiBadge.kt` | `✦ AI` badge component |

## Modified Files

| File | Change |
|---|---|
| `core/AppContainer.kt` | Wire `AiInsightCoordinator` |
| `ui/dashboard/DashboardViewModel.kt` | Inject coordinator, expose AI state + actions |
| `ui/dashboard/DashboardScreen.kt` | Render `AiInsightCard` below verdict chip |
| `build.gradle.kts` | Add `com.google.ai.edge.litertlm:litertlm-android:0.11.0` |

---

## Out of Scope

- Conversation history or multi-turn prompts
- Cloud model fallback
- Any other AI features (those build on this foundation once it ships)
- Room persistence of generated explanations
- iOS / other platforms
