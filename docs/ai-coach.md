# AI Coach System

**Cloud-only.** All AI features call an OpenAI-compatible chat-completions API (OpenRouter by
default) through one shared `data/remote/OpenAiCompatClient`. The former on-device Gemma/LiteRT
stack was removed ("Phase 8"); `ai/AiCoachBoundaryTest` enforces that no local-stack symbols
return. Every coordinator lives in `AppContainer` on `appScope` (`SupervisorJob + Dispatchers.Default`),
so generations survive navigation.

## Configuration

| What | Where | Notes |
|---|---|---|
| Base URL + model id | `UiPreferences` (DataStore) | `cloudModelId` remaps the deprecated tool-broken `openai/gpt-oss-20b:free` → `nvidia/nemotron-nano-9b-v2:free` at read time |
| Cloud API key + Tavily key | `SecureKeyStore` (EncryptedSharedPreferences, AES256-GCM) | never logged; `hasKey` StateFlows |
| Combined | `AppContainer.cloudConfigFlow: StateFlow<CloudConfig?>` | non-null only when URL + model + key all present; gates every feature |

`OpenAiCompatClient` exposes two entry points over one pooled OkHttp client (connect 15 s, read 60 s,
no retries): `completion()` (non-streaming, supports tool schemas — coach chat, weekly briefing,
Settings test) and `streamCompletion()` (SSE deltas — insights, phrasing, recipe names). Non-2xx →
`IllegalStateException("HTTP <code>: <body>")`. Parsing lives in `OpenAiCompatModels.kt`: strict JSON
for server payloads, lenient only for model-generated tool arguments (handles `"500.0"`-style ints).

## The AI features

| Feature | Class | Shape |
|---|---|---|
| Coach chat | `ai/CloudCoachCoordinator` | Multi-turn, 19 tools, write-confirmation flow |
| Insight cards (Recovery, Progress) | `ai/CloudInsightCoordinator` | Single-turn streamed 2-sentence verdicts, per-kind dedup keys |
| Weekly briefing | `ai/WeeklyBriefingGenerator` | Deterministic skeleton; model adds prose only |
| Signal phrasing | `ai/CoachPhrasingService` | Rewords a proactive-signal card; deterministic fallback verbatim on any failure |
| Rebalance copy | `ai/RebalanceCopyService` | Same shape as phrasing, for the rebalance offer card |
| Recipe namer | `ai/RecipeNamer` (`CloudRecipeNamer`) | One streamed name, sanitized, ≤42 chars |
| Knowledge base | `ai/knowledge/*` | Cited RAG over `assets/knowledge/corpus.json` |

**Deterministic-first doctrine:** engines compute every number and verdict; the model only adds
prose. Briefing narration is overlaid via `merge()` — blank/unparseable narration falls back to
engine text, so the model can never alter numbers. Phrasing/rebalance copy return the deterministic
fallback verbatim on any failure. Insight prompts embed pre-computed, signed-formatted signals.

## Coach chat (`CloudCoachCoordinator`)

- **Turn flow** (all under `turnLock`): first turn seeds one system message from
  `CoachToolsAdapter.systemPromptSnapshot()` — plan line with **rebalance-effective** targets,
  optional profile, a pre-fetched `get_today_summary` JSON snapshot, journey narrative, coach-memory
  bullets, `COACH_PROMPT_GUIDELINES`, plus a one-shot weekly-briefing handoff consumed from
  `CoachHandoffStore`. Each user turn swaps in a fresh knowledge `REFERENCE` block (previous turn's
  block is removed so they never accumulate).
- **Tool loop:** ≤ `MAX_TOOL_ROUNDS` = 12 completions per turn, each under `withTimeout(180 s)`.
  Tool calls dispatch through `CoachToolsAdapter` → `CoachToolExecutor` on `Dispatchers.IO`; results
  are replayed as `role:"tool"` messages. A running `thinkingSteps` log feeds `CoachState.Thinking`.
- **Empty-response recovery** (the fix for the historical "Done." bug): blank text + no tool calls →
  one `EMPTY_RESPONSE_NUDGE`, then an honest error ("The AI didn't complete that action — please try
  again."). Success is never fabricated.
- **Errors** (timeout / exception / round-cap): the model-side context (`requestMessages`) is cleared
  and reseeded on the next message; the visible transcript (`history`) is preserved. Chat history is
  **in-memory only** — process death drops the conversation (confirmed tool writes are already in Room).

### Tools (19, defined in `CoachTools.kt` as `CLOUD_COACH_TOOL_SCHEMAS`)

Core reads: `get_today_summary(date?)`, `get_weekly_trends`, `get_body_trends`,
`get_training_summary`, `search_food_library(query, grams?)`, `suggest_meals`, plus `web_search`
(Tavily; unavailable → tool returns "web search unavailable"). Writes: `log_meal`, `log_metric`,
`update_calorie_target`, routine tools (`create_routine`, `edit_routine`, `create_exercise`, …),
meal edits (`edit_meal`, `delete_meal`). Memory: `remember` / `forget` (deliberately unconfirmed;
persisted in `CoachMemoryStore`, ≤50 facts). A future `date` on `log_meal` **plans** the meal
instead of logging it eaten.

### Write-tool confirmation flow

Tools in `COACH_WRITE_TOOLS` pause before executing:

1. A `PendingCoachAction` (tool, args, display text) + `CompletableDeferred` is stored in an
   `AtomicReference`; `CoachState.AwaitingConfirmation` is emitted.
2. UI shows the confirmation bar. Confirm/cancel/`clearHistory` each `getAndSet(null)?.complete(…)` —
   the first tap atomically claims the deferred; stale taps are no-ops and can never approve a later
   turn's write.
3. Cancel sends `{"cancelled": true}` back to the model. The wait holds `turnLock` (UI disables input
   while busy), and the deferred has no timeout by design.

## Insight cards (`CloudInsightCoordinator`)

Triggered when a card becomes visible (`TodayViewModel` / `ProgressViewModel` build
`RecoveryInsightContext` / `ProgressInsightContext`). Regeneration is gated by a per-kind
**quantized context key** (trends rounded to 0.1) — cards do *not* regenerate on every open, only
when data moves or on explicit retry. Pipeline: static knowledge query → `REFERENCE` block →
`InsightPromptBuilder` (deterministic signals + few-shot style) → SSE stream (60 s timeout) →
markdown-strip → 2-sentence cap → `Ready(text)`. Keys/results are in-memory only.

## Proactive coach spine (`data/coach` + `domain/coach`)

Deterministic pipeline, no LLM required (phrasing is optional decoration):

`CoachContextBuilder` (one-shot repository reads) → pure `CoachContextAssembler.assemble()`
(28-day `CoachContext`) → `CoachContextCache` (memoized per calendar day; invalidated on plan/profile
edits) → `CoachDigestCoordinator.run()` (mutex): AI-toggle gate → evaluate the 18-detector catalog
(`CoachSignalEngine`; < 14 logged days → `INSUFFICIENT_DATA` only) + matured experiment →
`SignalSelector` (tier P0–P3 → severity → cooldowns, 7-day dedup ledger) → stage exactly **one**
winner in `CoachInbox` (or silence) → optional P0 push + separate weekly push through `RateLimiter`
(quiet hours 22:00–07:00, ≤2 pushes / rolling 7 days) and `AndroidCoachNotifier`. Triggers: app
foreground, Today-slot visibility, and a 24 h `CoachDigestWorker`. Notification taps deep-link via
a `CoachActionType` extra on `MainActivity`.

## Knowledge base (`ai/knowledge`)

- **Corpus:** `assets/knowledge/corpus.json` (~116 KB, 77 cited chunks, 4 domains), built by
  `tools/ingest_knowledge.py` from `knowledge-sources/`. Parsed once off-main at app start; on
  failure the injector stays a no-op (features degrade, never crash).
- **Retrieval:** `KeywordKnowledgeRetriever` — tokenization with stopwords + light suffix stemming;
  score = title-tf×3 + tag-hit×2 + body-tf×1, min-score floor 2.0, deterministic tie-breaks.
- **Injection:** `RetrievalKnowledgeInjector` — top 3 chunks in a 2000-char budget, formatted
  `[n] Title — body (Source: X)`. Consumers: coach (per user turn), insights (per kind), briefing.

## Limits & guardrails

| Limit | Value | Where |
|---|---|---|
| Tool rounds per turn | 12 | `CloudCoachCoordinator.MAX_TOOL_ROUNDS` |
| Per-completion timeout (chat) | 180 s | `TURN_TIMEOUT_MS` |
| Insight / recipe / phrasing timeouts | 60 s / 45 s / 15 s | respective services |
| Knowledge block budget | 2000 chars, top 3 chunks | `RetrievalKnowledgeInjector` |
| Coach memory | ≤ 50 facts, case-insensitive dedup | `CoachMemoryStore` |
| Calorie target range (tool) | 500–6000 | `CoachToolExecutor` |
| Score metrics | whole numbers 1–10 | `CoachToolExecutor` |

## Test harness (`app/src/test/.../ai/harness/`)

`InsightHarnessTest` is a live iteration rig for insight-card prompt quality: renders 8 personas
(`InsightScenarios`) through the **production** prompt builder + client, printing outputs for manual
scoring (optional LLM judge via `InsightJudge`, pass = all rubric dims ≥ 4). It reads `.env.test`
(git-ignored) or `INSIGHT_*` env vars and **self-skips when credentials are absent** — but when
credentials exist it hits the live API from the default unit-test task and can fail on upstream
rate limits. Test source set only; never ships.

## Known behavioural notes

- Free OpenRouter routes rate-limit (429) and occasionally change tool-calling behaviour; the model
  remap in `UiPreferences.cloudModelId` is the escape hatch for a route that stops emitting
  `tool_calls`. There is currently no retry/backoff and no `tool_choice` in requests.
- The system snapshot is seeded once per conversation. A calendar-day rollover mid-conversation now
  rebuilds it in place (`CloudCoachCoordinator` tracks the seeded date via `DateProvider`), so the
  date/totals no longer freeze across midnight (P2-3). Post-write totals are only *mitigated*: a
  `COACH_PROMPT_GUIDELINES` line tells the model to re-call `get_today_summary()` after a write before
  quoting today's totals — a model that ignores it can still quote the pre-write snapshot (the
  coordinator does not force a refresh after a confirmed write).
- Error handling collapses all failures into one generic message today; a sealed error taxonomy is
  on the roadmap.
