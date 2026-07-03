# Phase 0 — Local-Model Isolation: implementation notes

What was actually done in the Phase-0 isolation/refactor step (2026-07-01, branch
`redesign/ai-coaching`), following the strategy in `08-technical-architecture.md` §5. Goal: make the
cloud AI path independent of the on-device (Gemma/LiteRT) stack **without deleting it**, keep the app
building and existing cloud AI working, and add **no** new features.

Three read-only inspection agents produced the change-set (coupling map, dead-code triage, local-stack
inventory + build-safety). Build config was confirmed to have **no `-Werror`/`allWarningsAsErrors`**, so
`@Deprecated` on still-constructed classes is safe.

## Done

1. **Relocated the trapped shared constants** out of a Gemma file into a neutral home.
   - New `ai/CoachTools.kt` now holds `COACH_TOOL_SCHEMAS`, `COACH_WRITE_TOOLS`,
     `SEARCH_WEB_TOOL_SCHEMA`, `CLOUD_COACH_TOOL_SCHEMAS` (previously inside
     `GemmaCoachCoordinator.kt:42-63`). Same `ai` package → all call sites (cloud coach, container,
     tests, legacy Gemma) resolve unchanged; the cloud coach no longer imports anything from a Gemma
     file for its tool schemas.

2. **Marked the local stack `@Deprecated`** (level WARNING, message points to Phase 6 + the arch doc):
   `GemmaInsightService`, `GemmaServiceHolder`, `GemmaInsightCoordinator`, `GemmaCoachCoordinator`,
   `RoutingInsightCoordinator`, `RoutingCoachCoordinator`, `LocalNameGenerator`, `RoutingRecipeNamer`.
   Every legacy use site (AppContainer wiring, internal cross-refs) now emits a clear deprecation
   warning — the isolation is visible at a glance. Build stays green (no `-Werror`).

3. **Added a boundary guard test** — `ai/AiCoachBoundaryTest.kt` asserts the cloud-first coach building
   blocks (`CoachTools`, `CloudCoachCoordinator`, `CoachToolExecutor`, `CoachToolsAdapter`, the
   `WeeklyBriefing*` spine) — and any future `domain/coach` / `data/coach` package — import **nothing**
   from `Gemma*`/`Routing*`/`ModelVariant`/`AiBackend`/`LocalNameGenerator`. This locks invariant #7 so a
   future edit can't silently recouple the new coach to the local stack.

4. **Removed unambiguously-dead code:** the `rich` (4–6 sentence) prompt modes on
   `buildProgressTrendPrompt`/`buildRecoveryReadinessPrompt`/`buildRestOfDayPrompt` — no production
   caller ever passed `rich=true` (test-only). Removed the param + dead branch and deleted the
   `InsightPromptBuilderRichModeTest`. Production prompt output is byte-identical.

5. **Documented the one remaining cloud→local reference:** `CloudInsightCoordinator`'s inert
   `ModelVariant.GEMMA_2B` stub (forced by the shared `AiInsightCoordinator` interface's model-lifecycle
   members). Annotated in-code as a legacy interface obligation, not relied on by the new coach, removed
   in Phase 6 when the interface is split. It's excluded from the boundary guard for that reason.

**Verification:** full `:app:testDebugUnitTest` passes; `:app:assembleDebug` builds. Existing cloud AI
(insight cards, coach chat, recipe namer) and the legacy on-device path both remain fully functional.

## Deliberately NOT done (per scope + the roadmap)

- **No deletion of the local stack.** All Gemma/Routing/`AiBackend`/`ModelVariant`/download-plumbing/
  model-lifecycle code stays physically present and functional → Phase 6.
- **No package move to `ai/local/`.** The inspection found `AiBackend`/`ModelVariant` are coupled to the
  preferences layer (persisted enums) and a physical move adds churn for little gain now; the
  `@Deprecated` marking + the guard test already give a clear boundary. (Package move remains an option
  for Phase 6.)
- **No live-card removal.** ACTIVITY_NEAT, WEEKLY_PATTERN, CROSS_METRIC, and the legacy weekly-verdict
  card all still render — removing/merging them is Phase 1 restructuring (they were confirmed LIVE, not
  dead). Only the dead `rich` code path was removed.
- **No interface split** (`AiInsightCoordinator` model-lifecycle methods) — deferred to Phase 6 to avoid
  rippling into the still-live routers.
- **No new proactive features** (CoachSignalEngine, digest worker, inbox, notifications) — those are
  Phase 2+.

## Result
The codebase is prepared for the new cloud-first AI coach: shared constants are in neutral ground, the
local stack is clearly marked legacy and fully isolated behind a guard-tested boundary, and it can be
deleted in Phase 6 without touching the new system — while the app keeps building and every existing AI
surface keeps working.
