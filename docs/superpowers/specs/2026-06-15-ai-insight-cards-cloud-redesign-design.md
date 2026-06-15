# AI Insight Cards — Cloud Redesign & Iteration Harness (Design)

**Date:** 2026-06-15
**Branch:** `feat/ai-insight-cards-cloud-redesign`
**Status:** Design — awaiting user review before implementation plan.

## Problem

The on-device insight cards are tuned terse for Gemma 2B: they don't compare to the user's
baseline, rarely name the driver behind a number, and always fire (no "stay quiet"). We're moving
insight cards to a **cloud, OpenAI-compatible model** (the `CloudInsightCoordinator` already
exists), which lifts the 2B constraints. We want to make the output **proactive, accurate, and
genuinely useful**, and we want a **fast iteration loop** to get there.

## Goals

1. A repeatable test harness that renders every card's *real* prompt against realistic data and
   prints the model output + an automated quality score — no emulator, seconds per run.
2. Rewrite the card prompts (and extend their input data) to the
   [output doctrine](../../ai-insight-cards/insight-output-doctrine.md).
3. Add 3 high-value new cards: target-change explainer, noise-defuser, cross-metric "aha".
4. Capture new AI ideas in [the backlog](../../ai-insight-cards/ai-ideas-backlog.md).

## Non-goals

- Not changing the on-device Gemma path's behavior in this pass (cloud is the shipping target;
  Gemma remains as-is / offline fallback).
- Not building the proactive *ranking/notification* system (idea A1/A3 — backlog).
- Not wiring new UI surfaces beyond what the 3 new cards need.

## Architecture

### Component 1 — Iteration harness (`InsightHarness`)
- **Location:** app unit-test source set (`app/src/test/java/.../ai/InsightHarnessTest.kt`).
- **Why a JUnit test, not a new module:** it compiles directly against `InsightPromptBuilder`,
  `OpenAiCompatClient`, and `CloudConfig` (all pure JVM + OkHttp, no Android), so no new Gradle
  module or Robolectric is needed. Run with
  `./gradlew :app:testDebugUnitTest --tests "*InsightHarness*"`.
- **Self-skipping:** if `.env.test` (or the env vars) are absent, the harness `assumeTrue`-skips,
  so the normal test suite and CI are unaffected (it never runs without a key).
- **Flow per run:**
  1. Load config from `.env.test` → `CloudConfig` + optional judge model.
  2. For each scenario × applicable card: build the *real* prompt via `InsightPromptBuilder`.
  3. Call the model (reusing `OpenAiCompatClient.streamCompletion`, collected to a string, then the
     same post-processing the coordinator applies — strip markdown, `limitToSentences(…, 2)`).
  4. Call the judge (a second `completion` call with the rubric) → parse 1–5 scores + should-fire.
  5. Print a readable report: scenario, card, prompt (optional), output, scores.

### Component 2 — `.env.test` (git-ignored)
```
INSIGHT_BASE_URL=https://api.example.com/v1
INSIGHT_API_KEY=sk-...
INSIGHT_MODEL=<model id>
INSIGHT_JUDGE_MODEL=<optional; defaults to INSIGHT_MODEL>
```
Added to `.gitignore`. A committed `.env.test.example` documents the keys.

### Component 3 — Scenario fixtures
~8–10 hand-authored, realistic scenarios as plain data, each carrying the inputs for whichever
cards apply. Coverage:
1. On-track fat loss (flat waist, high adherence) — should-fire test for "stay quiet / stay course".
2. Plateau (trend flat 3 wk, adherence high).
3. Fast loss → expenditure change (target-change card).
4. Surplus + waist creeping up (reduce verdict).
5. Poor recovery + trained today.
6. Protein deficit 5 of 7 days (cross-metric / rest-of-day).
7. Weekend derailment pattern.
8. Insufficient data (≤2 logged days) — honest-degradation test.
9. Scale jump vs. flat trend (noise-defuser).
10. Lean-mass gain (weight up, waist stable, lifts up).

Each scenario maps to the existing context types (`InsightContext`, `ProgressInsightContext`,
`RecoveryInsightContext`, `RestOfDayInsightContext`, `PatternInsightContext`) plus the new
fields/contexts added below.

### Component 4 — Prompt + context changes (`InsightPromptBuilder` and context objects)
- Rewrite the 5 prompt builders to the doctrine (baseline comparison, name-the-driver,
  observation→why→action, stay-quiet awareness, hedging rules).
- **Extend context objects** with the baseline/trend fields the doctrine needs (e.g. prior-window
  trend, personal averages for recovery scores, desired weekly rate, prior calorie target). These
  are additive fields; the mappers that build them (`*InsightMapper`) get updated to populate them.
- **Add 3 new cards:** new `InsightKind` entries + builder methods + (for target-change) a context
  carrying old/new target and the causal inputs. Wire into `CloudInsightCoordinator` and the
  request types. UI wiring is minimal — reuse the existing `GeneratedInsightCard` component.

### Component 5 — The LLM-judge
A judge prompt embedding the rubric from the doctrine (§7). Returns strict JSON
(`{accuracy, actionability, proactivity, tone, brevity, shouldFire, notes}`) parsed with the
existing lenient JSON approach. Same model by default (separate call).

## Data flow

```
scenario fixture ──> InsightPromptBuilder.build*Prompt() ──> OpenAiCompatClient ──> card text
                                                                   │
                                          post-process (strip md, 2-sentence limit)
                                                                   │
                                                       Judge call (rubric) ──> scores
                                                                   │
                                                        printed report (per scenario × card)
```

## Error handling
- Missing `.env.test` → skip (assumeTrue), never fail the suite.
- Non-2xx / network error from the model → report the error inline for that cell, continue others.
- Judge returns malformed JSON → record "judge-parse-failed" for that cell, keep the raw output.
- Timeouts reuse the client's existing 60 s read timeout.

## Testing
- The harness *is* the test instrument; its own correctness is verified by: (a) prompt-builder unit
  tests (pure, offline) asserting the new fields appear and the stay-quiet gate triggers on the
  on-track scenario; (b) a fixture-validity test (offline) asserting every scenario constructs.
- No network in normal CI; the cloud calls run only when a key is present locally.

## Iteration loop (how we actually use it)
1. Run harness → read outputs + scores.
2. Revise prompts/contexts.
3. Re-run → compare scores against the prior run.
4. Repeat until every card scores ≥ 4 on all axes across scenarios and should-fire is correct.

## Open questions / decisions made
- **Cloud is the shipping backend** (decided). Gemma path untouched this pass.
- **Provider:** OpenAI-compatible, user-supplied (decided).
- **Judge:** same model, separate call (decided).
- **Scope:** richer data + new cards allowed (decided).
- **Branch:** dedicated `feat/ai-insight-cards-cloud-redesign` (decided).
