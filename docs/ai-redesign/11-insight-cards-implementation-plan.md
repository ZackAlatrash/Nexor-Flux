# AI Insight Cards — Implementation Plan

**Branch:** `redesign/ai-coaching` · **Produced:** 2026-07-02 · **Planning only — no code changed.**
**Status:** STEP-0 decisions Q1–Q9 **resolved by the owner (2026-07-02)** — see §2. **ALL PHASES 1–8
IMPLEMENTED & verified green (2026-07-02)** — `assembleDebug` builds a debug APK and the full unit suite
passes (the only red is a self-skipping live-network `InsightHarnessTest`, an env artifact). Each phase was
gated on a green build+tests before the next; Phases 1 & 8 additionally passed a read-only code review.
Per-phase status notes are inline in §4. Not committed (owner commits after visual verification).

This document converts the AI-redesign planning docs (00–10) into a concrete, ordered implementation
plan, grounded in **both** the docs (source of truth) and the **verified current state of the code**
(HEAD `7de76d0`). It was produced by six analysis agents: a decision register, a code-verified
implementation map, a disposition reconciliation, a placement/UX plan, and a dependency/prerequisite
analysis.

It describes **what to implement, modify, and remove; in what order; what depends on what; the technical
risks; and the assumptions/conflicts to resolve before coding.** Where the docs conflict, the conflict is
**flagged, not resolved** (per the task constraint). No new AI feature is proposed beyond what the docs
support. The AI coaching *architecture* (the `CoachSignalEngine` "one brain, three surfaces" model) is
**not** redesigned — this plan builds on it.

Traceability: decisions are tagged `D1`–`D69` (see [decision register](#appendix-a--decision-register-index));
work items are tagged `WI-A`–`WI-G`.

---

## 0. The central reframe (read this first)

The planning docs 01–08 were written 2026-07-01 as "planning only." **The branch has since implemented
most of that architecture.** Doc 10 (2026-07-02, the card-level plan) is the *latest* doc but is **0%
implemented**. So the true starting point is not "nothing built" — it is:

> **A fully-wired proactive coaching engine already runs in production, side-by-side with the original
> reactive insight cards which are all still live and untouched. The redesign's "subtract first" step
> never happened — the branch _added_ the proactive layer on top of the old card sprawl.**

Concretely, verified in code:

| Layer | Status | Evidence |
|---|---|---|
| **Proactive engine** (`CoachSignalEngine`, 14 detectors incl. all 5 cross-domain links; `CoachContextBuilder` spanning 4 domains; `CoachInboxRepository`; `SignalSelector`+`RateLimiter`; `CoachDigestWorker`+`onStart`; `CoachJourneyStore`; push) | **LIVE** | `CoachSignalEngine.kt:30-45`; `CoachDigestCoordinator.kt`; `AppContainer.kt:444-465` |
| **"Today's Coaching" slot** (the one proactive in-app surface) | **LIVE** on the dashboard | `CoachTodaySlot.kt`, `DashboardScreen.kt:277` |
| **Training-aware chat** (19 dispatched tools total, incl. `get_training_summary` + `get_body_trends`) | **LIVE** (Phase 3 done) | `CoachTools.kt`, `CoachToolExecutor.kt:52-72` |
| **Weekly Briefing** (skeleton-merge, knowledge- + journey-grounded) | **LIVE** but on the *older* `WeeklyReviewComputer`, not the engine; gets **3 of 4 domains** (no training) | `WeeklyReviewComputer.kt`, `WeeklyBriefingGenerator.kt:30`, `AppContainer.kt:302` |
| **The 10 original insight cards** | **all LIVE, unchanged** | inventory §A of the map; `DashboardScreen.kt:294/308/318/328/359/1043`, `ProgressScreen.kt:88`, `BodyRecoveryScreen.kt:179`, `FoodScreen.kt:255` |
| **Doc-10 card reforms (Phase 1 subtraction + new cards)** | **NOT started** | no REMOVE/MERGE/de-AI/new card in code |
| **Local Gemma/Routing stack** | **@Deprecated but still compiled and on the live path** (cards route via `RoutingInsightCoordinator`, not `CloudInsightCoordinator`) | `AppContainer.kt:469,504` |

**The single most important open architectural fact (`D45`):** the engine's **`WEEKLY`-surface signals
reach no in-app screen.** `CoachDigestCoordinator.kt:94` stages only the `TODAY` winner into the slot; the
`WEEKLY` winner is routed exclusively to a **push** (`emitWeeklyCheckInPush()`). The weekly check-in the
user actually sees is still driven by `WeeklyReviewComputer` — a *different, older* verdict codepath. Doc 07
tracks this as "engine convergence," explicitly **not done**.

**Therefore this plan is, in priority order:**
1. **Surface what's already built** but stranded (the `WEEKLY` engine signals → a screen) — the D45 convergence.
2. **Apply doc-10's "subtract first"** — de-AI the paraphrase cards, remove dead/redundant cards, consolidate the triple verdict — which the branch skipped.
3. **Add the genuinely-new doc-10 cards** (Cross-Signal Discovery rebuild; the deterministic decision-moment cards).
4. **Finish the cloud-only cutover** (migrate cards off `Routing*`, then delete the local stack — Phase 6).

---

## 1. Scope & non-goals

**In scope:** the AI insight *cards* and the surfaces that render engine signals (Today slot, weekly
check-in, dashboard/food/train/trends cards, push), plus the wiring needed to make them coherent.

**Non-goals (per task + docs):**
- **Do not redesign the AI coaching architecture.** Build on the existing `CoachSignalEngine`/surfaces
  (`D66`: don't rebuild the deterministic engines).
- **No new data collection** — no progress photos, goal-weight, or body-fat (`D64`).
- **No second chatbot / "AI everywhere"** — one coach, chat stays a secondary handoff affordance (`D65`).
- **No invented features** — every item below traces to a doc decision.

**Invariants every item must uphold** (`D12`, `D16`, `D17`, doc 08 §Invariants; already partly
type-enforced — `CoachSignal.kt:34` requires non-blank `fallbackText`):
1. The LLM never introduces a number absent from the engine payload.
2. Every surface renders from `signal.fallbackText` with no network/model.
3. No signal is surfaced without a `verdict` (push also needs an `action`).
4. Smoothing happens in the engine before a number becomes a fact.
5. Exactly one winner per slot; restraint caps are enforced code.
6. Silence (`daysLogged < 14` / noisy / nothing crossed threshold) is first-class.
7. The new coach system has zero compile-time dependency on the local stack (guard-tested).

---

## 2. Decisions — RESOLVED by the owner (2026-07-02)

These were genuine conflicts/ambiguities across the docs and the code. **The owner has now decided all nine.**
The chosen options are recorded below and are baked into the sequenced build order in §4. The full option
detail is retained beneath for traceability.

### Resolved choices

| # | Decision | **Chosen** | Effect on the plan |
|---|---|---|---|
| **Q1** | Weekly-verdict source of truth | **(b)** `WeeklyReviewComputer` stays authoritative for the verdict/number; engine weekly signals fold in as supporting cross-domain narrative, with the computer's verdict taking precedence | No double-verdict; briefing is the single verdict home. Pairs with Q5(b). |
| **Q2** | Placement axis | **(c) hybrid** — route through the proactive slot/briefing; the flagship Cross-Signal gets a distinct slot skin | Dashboard collapses to the Today slot + briefing. Scale Check → slot; Weekly Pattern → briefing section. Makes Q9 mandatory. |
| **Q3** | Doc-10-only deterministic cards | **(c) subset** — build the low-cost ones now (Consistency Check-In E4, Meal Impact Preview E1); Training Readiness Handoff (E2) + inline Live PR (E3) are fast-follows | Splits WI-E into "now" vs "fast-follow." |
| **Q4** | ACTIVITY_NEAT | **(b) input-only** — remove the standalone card; NEAT survives only via the `NEAT_COLLAPSE` detector | Standalone card deleted in WI-A. |
| **Q5** | Dangling enum kinds | **(b) retire** `WEEKLY_VERDICT` + `WORKOUT_STREAK_AT_RISK` (no detector; add later if needed) | Cleaner enum; consistent with Q1(b). |
| **Q6** | Coordinator migration timing | **(a) migrate now** — each edited card moves to `CloudInsightCoordinator` in the same edit | Migration folds into every card phase; Phase-6 deletion becomes a clean sweep, not a separate migration. |
| **Q7** | "Track this" persistence | **(b) DataStore** — one active experiment initially | Cross-Signal uses a single-experiment store; Room only if history is later wanted. |
| **Q8** | Buried-destination visibility | **split** — Recomp Progress Verdict **echoes into the Today slot on review day**; Training Readiness stays tied to the Train-open moment (no push) | Trends verdict gets seen via the slot; Train card relies on organic Train-open. |
| **Q9** | Today-slot priority budget | **recommended tiering** — **P0** events/safety (NEW_PR, fat-gain, recomp win) > **P1** daily decision (Morning Readiness; Cross-Signal with elevated severity the week it fires) > **P2** maintenance (Consistency, quiet weigh-ins) | Defined in `SignalSelector`/`RateLimiter` before new TODAY signals are added. |

### Full option detail (retained for reference)

| # | Decision | Options | Blocks |
|---|---|---|---|
| **Q1** | **Weekly-verdict source of truth.** The engine has a (dangling) `WEEKLY_VERDICT` kind and weekly signals; `WeeklyReviewComputer.verdictLabel` independently computes the live calorie verdict. Which owns the weekly verdict? | (a) Engine becomes source-of-truth, `WeeklyReviewComputer` demoted to a numeric input; (b) keep `WeeklyReviewComputer`, stitch engine signals in with explicit precedence in `WeeklyBriefingGenerator`; (c) write the `WEEKLY_VERDICT` detector wrapping `AdjustmentVerdict`. | WI-B (D45), WI-A verdict-collapse, cards #5/#9 |
| **Q2** | **Placement axis (the big doc conflict).** Doc 10 gives Cross-Signal / Scale Check / Weekly-Pattern their own render slots; docs 02/03/07 fold them into the Today slot / weekly check-in and delete the standalone dashboard cards. | (a) standalone cards (doc 10); (b) fold-into-proactive-surface (02/03/07); (c) hybrid (render through the slot but with a distinct "discovery" skin). | WI-D placement, WI-A (Scale Check, Weekly Pattern) |
| **Q3** | **Are the 4 doc-10-only *deterministic* cards in scope?** Meal Impact Preview, Training Readiness Handoff, Live PR inline, Consistency Check-In are supported **only by doc 10**, contain **zero AI**, and (for the first two) fire on synchronous UI events, outside the digest engine. | (a) in scope as the "house template" generalization; (b) out of scope for an *AI*-coaching workstream; (c) subset. | WI-E |
| **Q4** | **ACTIVITY_NEAT disposition.** Doc 10 allows a deterministic standalone OR merge into Cross-Signal; docs 02/07 say remove the standalone entirely and keep NEAT only as a cross-domain input. | (a) deterministic standalone; (b) input-only (removed). | WI-A |
| **Q5** | **`WEEKLY_VERDICT` / `WORKOUT_STREAK_AT_RISK` dangling enum kinds** (declared, no detector). | (a) write the detectors; (b) retire the enum members. | WI-B |
| **Q6** | **Coordinator migration timing.** Cards still route via `RoutingInsightCoordinator` (imports Gemma). Move them to `CloudInsightCoordinator` now (touch each card once) or keep Routing until Phase 6 (touch twice)? | (a) migrate now; (b) defer to Phase 6. | WI-F, every card edit |
| **Q7** | **"Track this" experiment persistence** for Cross-Signal Discovery — no store exists. | (a) Room table (queryable history); (b) DataStore (single active experiment). | WI-D |
| **Q8** | **Buried-destination echo.** Trends (Recomp Progress Verdict) and Train (Training Handoff / PR) are not bottom-nav tabs, so cards there are rarely seen. Echo them into the Today slot / push, or accept low visibility? | (a) echo to slot; (b) accept; (c) push. | WI-C, WI-E, P1-6 |
| **Q9** | **Today-slot "one winner" budget.** Adding Morning Readiness + Consistency (+ optionally Cross-Signal/Scale Check) to the single slot needs an explicit priority tiering so routine signals don't crowd out the flagship, and vice-versa. | Define the tier/severity ordering in `SignalSelector`/`RateLimiter`. | WI-C, WI-A, WI-D |

---

## 3. Workstreams — what will be implemented, modified, removed

Grouped by work item (`WI-*`). Each lists **IMPLEMENT / MODIFY / REMOVE**, the doc decisions it satisfies,
data readiness, and risk (from the dependency analysis).

### WI-A — Phase-1 subtraction & de-AI (the skipped "subtract first")  ·  risk M
Satisfies `D6` (done), `D28`, `D29`, `D30`, `D31`, `D55`, `D62`.

- **REMOVE** `REST_OF_DAY` (`D55`): dead/dormant — no screen calls it (map §A). Pure deletion of the
  `InsightKind`, its prompt builder, and stub branch.
- **REMOVE / de-AI** the paraphrase cards (`D28`):
  - `WEEKLY_PATTERN` hero (`DashboardScreen.kt:294`) → render the deterministic detector fact directly
    (incl. the discarded 2nd-place fact) as **Weekly Pattern Spotlight** in the briefing (`D62`, WI-B/Q2).
  - `CROSS_METRIC` (`:308`) → its LLM paraphrase is retired; the *concept* is rebuilt in WI-D.
  - `NOISE_DEFUSER` (`:328`) → deterministic **Scale Check** template, keep the ≥0.6 kg gate (`D31`);
    **routed through the Today slot** (Q2c → Scale Check to slot). *(Resolves the doc 07 "keep untouched"
    vs doc 10/02 "de-AI it" conflict in favour of de-AI.)*
- **REMOVE / fold** `ACTIVITY_NEAT` (`:359`) (`D30`, **Q4(b)**): remove the standalone card; NEAT survives
  only as a cross-domain input via the live `NEAT_COLLAPSE` detector.
- **MERGE / consolidate the triple verdict** (`D29`, **Q1(b)**): remove the `TARGET_CHANGE` card (`:318`) and
  the legacy `WEEKLY_VERDICT` card (`:1043`); fold "why the number changed" into the **existing**
  `WeeklyReviewComputer`-driven briefing (which keeps verdict precedence). Buildable on the current briefing;
  the engine cross-domain narrative is added on top in WI-B.
- **Data:** ready — detectors already produce the deterministic sentences (`fallbackText`).
- **Coupling:** these cards render via `RoutingInsightCoordinator` (Q6) — decide migrate-now vs later.

### WI-B — D45 engine convergence: surface the `WEEKLY` signals + consolidate the verdict  ·  risk **H** (central item)
Satisfies `D45`, `D29`, `D35`, and unblocks cards #5 (Weekly Verdict) and #9 (Weekly Pattern Spotlight).

- **IMPLEMENT** a `WEEKLY` in-app consumer: make the weekly check-in read the engine's
  `selectForSurface(WEEKLY, …)` winner (nothing does today — `CoachDigestCoordinator.kt:94` only stages
  `TODAY`).
- **MODIFY** `WeeklyBriefingGenerator` to fold the engine's weekly signals (RECOMP_WIN, FAT_GAIN_WARNING,
  LOW_ADHERENCE, etc.) into the skeleton-merge, **de-duped against `WeeklyReviewComputer.verdictLabel`**
  (the double-verdict collision — the highest-risk design decision; **Q1**).
- **RESOLVE** the dangling `SignalKind.WEEKLY_VERDICT` (`Q5`): either write its detector (engine owns the
  verdict) or retire it.
- **MODIFY** the briefing to also receive the **training domain** (currently 3/4 — no lift data;
  `WeeklyReviewData` lacks training), so the weekly verdict can name the cross-domain cause (`D35`).
- **Data:** partial — signals exist; the routing-to-screen and de-dup do not.
- **Risk:** getting the de-dup wrong yields two contradicting verdicts on one screen. This is the gate for
  all engine-fed weekly content.

### WI-C — Morning Readiness (improve + relocate RECOVERY_READINESS)  ·  risk M
Satisfies `D54`.

- **IMPLEMENT** a morning recovery signal as a **TODAY detector** surfaced in the `CoachTodaySlot`
  (`DashboardScreen.kt:277`): deterministic band + LLM verdict sentence (Mix), comparative to the user's
  own 7-day baseline, adding training-load + nutrition context, ending in an action + "Ask coach →" handoff.
- **MODIFY** the existing `RECOVERY_READINESS` card (`BodyRecoveryScreen.kt:179`) into the compact Body
  **echo** of the same signal — retimed to fire from the morning signal, not post-log (shared render, not a copy).
- **Data:** ready (recovery + cross-domain context in `CoachContextBuilder`).
- **Risk:** slot contention (`Q9`) — Morning Readiness wants the slot most mornings; needs priority tiering.

### WI-D — Cross-Signal Discovery (rebuild CROSS_METRIC; the one genuine-LLM card)  ·  risk **H**
Satisfies `D57`; the differentiator (doc 06 principle 2).

- **IMPLEMENT** the flagship: deterministic detectors surface *candidate* correlations (the 5 links already
  exist in `CrossDomainDetectors.kt`), the cloud LLM picks the most decision-relevant and phrases a
  hypothesis + one-tap **"Track this →"** mini-experiment evaluated by the next weekly card.
- **IMPLEMENT** a **"Track this" experiment persistence layer** — none exists (`Q7`: Room vs DataStore).
- **MODIFY / REMOVE** the old `CROSS_METRIC` card site per **Q2** placement (standalone hero card vs Today
  slot vs hybrid "discovery" skin).
- **Route via `CloudInsightCoordinator`** with a mandatory non-blank `fallbackText` (invariant 2) — this is
  the card where **invariant 1 ("LLM never invents a number")** is load-bearing: the model interprets
  series it is given, never fabricates values.
- **Data:** partial — detectors + 4-domain context ready; experiment store + raw-series payload shape missing.

### WI-E — Doc-10-only deterministic decision-moment cards  ·  risk L–M  ·  gated by **Q3**
Satisfies `D58`–`D61`. All zero-AI; flagged as possibly outside "AI coaching" scope.

- **E1 · Meal Impact Preview** (`D58`, risk L): a deterministic strip **inside the existing** `AmountSheet`/
  `RecipeAmountSheet` `GlassBottomSheet` (`FoodLibraryScreen.kt:846/887`), extending the live
  `AmountPreviewStat` tiles with "this brings you to X% protein / Y% carbs today + one swap." No new data.
- **E2 · Training Readiness Handoff** (`D59`, risk M): a new `FrostedCard` on `TrainHomeScreen` (~`:158`),
  pre-workout, band rules + "Adapt session →" + coach handoff. ⚠️ **Missing data:** per-muscle soreness does
  **not** exist (`DailyLogEntity.sorenessScore` is whole-body) — either scope to whole-body soreness +
  training load (the coach's `get_training_summary` already surfaces recent soreness, `CoachToolExecutor.kt:790`,
  so this scope-down needs **no** migration), or add a per-muscle schema migration for the fuller vision.
  Train is not a tab (visibility, `Q8`).
- **E3 · Live PR Callout** (`D60`, risk L): **two complementary placements** — (i) an **instant inline
  banner** on `ActiveSessionScreen`/`SessionSummaryScreen` at set-save (needs an **event-on-set-save hook**;
  the digest is 24h-batched — architecture mismatch), and (ii) the existing `NEW_PR` digest celebration
  already reaching the slot. `CoachActionType` has no PR-forward action (minor enum gap).
- **E4 · Consistency Check-In** (`D61`, risk L): a `TODAY` signal in the slot (maps to the live
  `QUIET_WEIGH_INS`/logging-consistency detector), framed as data-quality not guilt; `OPEN_FOOD_LOG`/
  `LOG_WEIGHT` actions exist.

### WI-F — Recomp Progress Verdict + knowledge grounding + shared scaffold  ·  risk M
Satisfies `D53`, `D33`, `D34`.

- **MODIFY** `PROGRESS_TREND` (`ProgressScreen.kt:88`) into **Recomp Progress Verdict** — add an action +
  name the single limiting factor; feed it per-muscle volume, training frequency, adherence (`D53`). ⚠️
  Trends is buried (More → Trends) — consider a slot echo on review day (`Q8`).
- **IMPLEMENT** knowledge grounding for insight prompts (`D33`) — the `RetrievalKnowledgeInjector` grounds
  chat + briefing today, **not** insight cards.
- **MODIFY** insight prompts toward one shared `CoachSignal`-parameterized scaffold (`D34`), keeping per-kind
  wording only where it earns its keep.

### WI-G — Cloud-only cutover finish (migrate off Routing → delete local stack)  ·  risk M
Satisfies `D8`, `D9`, `D10`, `D11`, `D37`/`D38` (done), Phase 6.

- **MODIFY** card + chat wiring to depend on `CloudInsightCoordinator`/`CloudCoachCoordinator` directly,
  off `Routing*` (`Q6`). Prereq for deletion.
- **REMOVE** (Phase 6, `D8`): `Gemma*` coordinators/service/holder, `LocalNameGenerator`, the 3 `Routing*`,
  `AiBackend`+`AiCapabilities`, `ModelVariant`+download plumbing, `CLOUD_ONLY_KINDS`, model-lifecycle
  `AiInsightState` members, the model-variant selector UI, and the `AppContainer` wiring; simplify the AI
  settings screen to cloud-only (`D9`); collapse the two coach prompt builders to one (`D10`); delete the
  2B patches (`D11`). The boundary guard test (`AiCoachBoundaryTest.kt`) already ensures this is a clean removal.

### (Cross-cutting) — Instrumentation  ·  Q3-adjacent, greenfield
`D63` (before/after per-card interaction telemetry) has **no infrastructure** in the codebase. Treat as an
optional, greenfield add — decide whether "useful" must be measurable before building the cards, or after.

---

## 4. Final sequenced build order (decisions applied)

STEP 0 (decide) is **complete** — see §2. The order below bakes in Q1(b), Q2(c), Q3(c), Q4(b), Q5(b),
Q6(a), Q7(b), Q8(split), Q9. It follows **subtract-first** (the cleanup the branch skipped), then enrich,
then finish the cloud-only cutover. Every phase is independently shippable and verifiable in the running app.

Because of **Q6(a)**, coordinator migration is **not** a separate late phase — each insight card is moved
onto `CloudInsightCoordinator` in the same edit that reforms it, so by Phase 8 the local stack is unreferenced
and can be deleted in one clean sweep.

```
Phase 1 ─ CLEANUP: subtract & de-AI            (WI-A)         ← the skipped "subtract first"; quick trust win
Phase 2 ─ engine → weekly convergence          (WI-B, Q1b/Q5b)
Phase 3 ─ slot budget + Morning Readiness + Consistency  (WI-C, E4, Q9, Q8-echo infra)
Phase 4 ─ Meal Impact Preview                   (WI-E · E1)   ← deterministic Food-Log card (Q3c "now")
Phase 5 ─ Cross-Signal Discovery                (WI-D, Q2c, Q7b)   ← the one genuine-LLM card
Phase 6 ─ Recomp Progress Verdict + knowledge-in-cards + shared scaffold  (WI-F, Q8, D33, D34)
Phase 7 ─ fast-follows: Training Readiness Handoff + inline Live PR  (WI-E · E2/E3, Q3c "later")
Phase 8 ─ cloud-only cutover finish: delete the local stack  (WI-G, D8–D11)
```

> **✅ ALL PHASES IMPLEMENTED (2026-07-02), each gated on a green `compileDebugKotlin` + `testDebugUnitTest`:**
> **P1** removed REST_OF_DAY/TARGET_CHANGE/CROSS_METRIC/ACTIVITY_NEAT + legacy verdict card; de-AI'd
> WEEKLY_PATTERN/NOISE_DEFUSER. **P2** retired dangling enum kinds; fed the briefing the training domain +
> the engine's WEEKLY signal as supporting narrative (WeeklyReviewComputer keeps verdict precedence, Q1b);
> moved Weekly Pattern into a deterministic briefing section (Spotlight, top-2 facts). **P3** added the Q9
> priority tiering + `MorningReadiness`, `ConsistencyCheckIn`, `ScaleCheck` TODAY detectors — dashboard is
> now just the Today slot. **P4** Meal Impact Preview in the food add-sheet (deterministic). **P5**
> Cross-Signal Discovery genuine-LLM card + `CoachExperimentStore` (DataStore) + "Track this" + discovery
> slot skin + a real ≥7-day evaluation loop. **P6** upgraded PROGRESS_TREND → Recomp Progress Verdict
> (action + training-frequency context), grounded insight prompts in the knowledge base, and migrated both
> insight cards to `CloudInsightCoordinator`. **P7** Training Readiness Handoff (Train home, coach handoff)
> + Live PR Callout on the session summary. **P8** deleted the entire Gemma/LiteRT/Routing/AiBackend/
> ModelVariant stack (incl. the litertlm dependency + native-lib manifest entries), simplified the coordinator
> interface + `AiInsightState`, and made the app cloud-only.
>
> **Reductions/deferrals (all safe, noted for follow-up):** P2/P3 relocations of Weekly Pattern/Scale Check
> are done, but the RECOVERY_READINESS Body card was left as-is (Morning Readiness is the new slot version) —
> a coexisting detail card, not a regression. P6 used training *frequency* (not per-muscle volume — ProgressVM
> doesn't observe sessions) and deferred the Q8 review-day slot-echo. P7 deferred the *instant* in-set PR
> banner on the active-session screen (the session-summary callout + the engine's `NewPrDetector` cover PRs).
> Per-muscle soreness and telemetry (D63) remain unbuilt (no data / greenfield).

### Phase 1 — Cleanup: subtract & de-AI  ·  the AI-insight-card cleanup core
> **✅ IMPLEMENTED 2026-07-02** — `compileDebugKotlin` + `testDebugUnitTest` green (1011 tests, 0 failures).
> Removed `REST_OF_DAY`, `TARGET_CHANGE`, `CROSS_METRIC`, `ACTIVITY_NEAT` kinds + the legacy weekly-verdict
> card path (`onAiCardVisible`/`InsightContext`/`buildWeeklySummaryPrompt`/`shouldFireWeekly`). De-AI'd
> `WEEKLY_PATTERN` + `NOISE_DEFUSER` **in place** (deterministic render, no LLM) — function preserved; their
> Q2c relocation (→ briefing section / Today slot) remains a follow-up. Kinds left: `PROGRESS_TREND`,
> `RECOVERY_READINESS` (LLM) + `WEEKLY_PATTERN`, `NOISE_DEFUSER` (deterministic). Coordinator migration
> (Q6a) deferred: the two surviving LLM cards were not edited, so they still route via `RoutingInsightCoordinator`
> — migrate when they're next touched (Phase 6 / their improve-phases).
- **REMOVE** `REST_OF_DAY` (dead code, `D55`).
- **De-AI** `WEEKLY_PATTERN` → deterministic **Weekly Pattern Spotlight** section in the briefing (incl. the
  discarded 2nd-place fact), and **remove** the dashboard hero card (`D62`; Q2c → briefing section).
- **De-AI** `NOISE_DEFUSER` → deterministic **Scale Check** routed through the **Today slot**, keeping the
  ≥0.6 kg gate; remove the standalone card (`D31`; Q2c).
- **REMOVE** the standalone `ACTIVITY_NEAT` card; NEAT survives only via `NEAT_COLLAPSE` (`D30`; Q4b).
- **REMOVE** the `CROSS_METRIC` paraphrase card (concept rebuilt in Phase 5).
- **Consolidate the verdict** (`D29`; Q1b): remove `TARGET_CHANGE` + legacy `WEEKLY_VERDICT` cards; fold
  "why the number changed" into the **existing** `WeeklyReviewComputer` briefing.
- **Migrate** every card touched here onto `CloudInsightCoordinator` (Q6a).
- **Depends on:** nothing (works on the current briefing). **Verify:** dashboard is down to the Today slot +
  weekly badge; no paraphrase/NEAT/redundant-verdict cards remain; exactly one weekly verdict.

### Phase 2 — Engine → weekly convergence (closes D45)
- Give the engine's `WEEKLY`-surface signals a screen: fold the `selectForSurface(WEEKLY, …)` winner into the
  briefing as the **supporting cross-domain narrative**, de-duped so `WeeklyReviewComputer`'s verdict/number
  wins (Q1b). Feed the briefing the **training domain** (currently 3/4).
- **Retire** the dangling `SignalKind.WEEKLY_VERDICT` + `WORKOUT_STREAK_AT_RISK` (Q5b).
- **Depends on:** Phase 1 (one verdict surface exists). **Verify:** engine weekly signals (recomp-win,
  fat-gain, low-adherence…) surface in the briefing as the "why"; no contradictory numbers; RECOMP_WIN can now
  reach a screen.

### Phase 3 — Slot priority budget + Morning Readiness + Consistency Check-In
- Implement the **Q9 tiering** in `SignalSelector`/`RateLimiter`: P0 events > P1 daily-decision > P2 maintenance.
- **Morning Readiness** (`D54`): a TODAY signal in the slot (deterministic band + LLM verdict, Mix); retime the
  Body `RECOVERY_READINESS` card into the compact echo of the same signal.
- **Consistency Check-In** (`D61`, E4, Q3c): a P2 TODAY signal mapped to the existing logging-consistency
  detector; framed as data-quality, never guilt.
- Build the **Q8 "review-day slot echo"** mechanism here (reused by Phase 6).
- **Depends on:** Phase 1 (slot is now the primary dashboard surface). **Verify:** slot shows one winner per
  the priority budget; Morning Readiness fires mornings and yields to events; Body echo matches.

### Phase 4 — Meal Impact Preview (deterministic, Food Log)
- **Meal Impact Preview** (`D58`, E1, Q3c "now"): a deterministic strip inside the existing amount/recipe
  `GlassBottomSheet`, extending the live `AmountPreviewStat` tiles with "this brings you to X% protein / Y%
  carbs today + one swap." Pure arithmetic, zero AI.
- **Depends on:** nothing (independent, deterministic UI). **Verify:** the preview updates as grams change and
  offers a concrete adjustment before commit.

### Phase 5 — Cross-Signal Discovery (the one genuine-LLM card)
- Rebuild `CROSS_METRIC` into **Cross-Signal Discovery** (`D57`): deterministic detectors surface *candidate*
  correlations; the cloud LLM picks the most decision-relevant and phrases a hypothesis + one-tap "Track this →".
  Render through the Today slot with a **distinct "discovery" skin** and **elevated severity the week it fires**
  (Q2c + Q9).
- Add a **DataStore single-active-experiment** store (Q7b); the next weekly card evaluates it.
- Hold **invariant 1** hard: the LLM only interprets engine-computed series, never fabricates numbers.
- **Depends on:** Phase 3 (slot budget + tiering). **Verify:** fires ≤1/week, wins the slot that week, offers
  "Track this," and the experiment is evaluated the following week; offline renders `fallbackText`.

### Phase 6 — Recomp Progress Verdict + knowledge grounding + shared scaffold
- Upgrade `PROGRESS_TREND` → **Recomp Progress Verdict** (`D53`): add an action + name the single limiting
  factor; feed it per-muscle volume, training frequency, adherence; migrate to `CloudInsightCoordinator` (Q6a).
- **Q8 split:** echo the review-day recomp verdict into the Today slot (Trends is buried).
- Wire the knowledge injector into insight prompts (`D33`); move insight prompts to the shared
  `CoachSignal`-parameterized scaffold (`D34`).
- **Depends on:** Phase 3 (slot-echo infra). **Verify:** Trends card upgraded + seen via the slot on review
  day; insight prompts are knowledge-grounded.

### Phase 7 — Fast-follows (deterministic, Train)
- **Training Readiness Handoff** (`D59`, E2, Q3c "later"): a `FrostedCard` on Train home, pre-workout, scoped
  to **whole-body soreness + training load** (no per-muscle migration needed — `get_training_summary` already
  surfaces soreness); tied to the **Train-open moment**, no push (Q8).
- **Inline Live PR Callout** (`D60`, E3): an instant banner on the active-session/summary screen at set-save
  (needs a **set-save event hook** — the digest is 24h-batched). The delayed NEW_PR slot celebration already ships.
- **Depends on:** Phase 3 (slot). **Verify:** the handoff shows on Train open with a concrete session tweak; a
  record-setting set fires the inline banner immediately.

### Phase 8 — Cloud-only cutover finish (delete the local stack)
- With every card path already on `CloudInsightCoordinator` (Q6a, done incrementally), **delete** the local
  stack (`D8`–`D11`, Phase 6 of docs 08/09): `Gemma*`, the 3 `Routing*`, `AiBackend`/`AiCapabilities`,
  `ModelVariant` + download plumbing, `CLOUD_ONLY_KINDS`, model-lifecycle members + selector UI; simplify the
  AI settings screen to cloud-only; collapse the two coach prompt builders; drop the 2B patches.
- **Depends on:** Phases 1–7 (all routed surfaces replaced). **Verify:** app builds cloud-only; the boundary
  guard test stays green; no dead on-device path remains.

**Explicit "X must precede Y":**
- Phase 1 → Phase 2 (one verdict surface must exist before engine narrative folds in).
- Phase 3 (slot tiering + echo infra) → Phase 5 (Cross-Signal slot skin) and Phase 6 (review-day echo).
- Phase 5 needs the Q7 DataStore store; Phase 7 (inline PR) needs a set-save event hook (net-new).
- Phases 1–7 → Phase 8 (local-stack deletion is last; Q6(a) made migration incremental, not a blocker).

**Deferred / out of this sequence:** per-muscle soreness (only needed for the *fuller* Training Readiness
vision — Phase 7 ships without it), telemetry/instrumentation (`D63`, greenfield — decide separately), and a
URI deep-link intent-filter (only if push tap-through needs true deep links).

---

## 5. Technical risks

| # | Risk | Severity | Mitigation / note |
|---|---|---|---|
| R1 | **Double-verdict collision** — `WeeklyReviewComputer.verdictLabel` and the engine both compute weekly verdicts; a bad de-dup shows two contradicting verdicts. | **High** | Settle Q1 first; make one the source of truth. Gate all engine-fed weekly content on this. |
| R2 | **Today-slot "one winner" starvation** — Morning Readiness, Consistency, NEW_PR, and (Q2b/c) Cross-Signal/Scale Check all compete for one slot with ≤2/wk restraint. | **High** | Define explicit tier/severity ordering (Q9); consider that the flagship Cross-Signal may be suppressed for weeks if routed through the slot. |
| R3 | **Dashboard double-surface crowding** — if doc-10 standalone cards coexist with the slot, the dashboard has two proactive AI surfaces (the clutter the redesign fights). | Med | Resolve Q2 toward slot-only or hybrid. |
| R4 | **Buried destinations** — Trends and Train are not tabs; cards placed there are rarely seen. | Med-High | Q8 — echo to slot or push, or accept low visibility. |
| R5 | **Event-vs-digest mismatch** — Meal Impact Preview (at-commit) and inline Live PR (at-set-save) fire on synchronous UI events, not the 24h digest; need new event hooks or pure-deterministic UI outside the engine. | Med | Treat E1/E3-inline as deterministic UI features, not engine signals. |
| R6 | **Invariant 1 on Cross-Signal** — the one card with genuine LLM synthesis must never fabricate a number. | Med | Feed the LLM engine-computed series only; keep numbers in `SignalFacts`; enforce "phrase, don't compute." |
| R7 | **Coordinator double-touch** — cards still on `Routing*`; changing a card twice (once for content, once for Q6 migration) if timing isn't decided up front. | Med | Decide Q6 before WI-A. |
| R8 | **Deep-link gaps** — push tap routing is a `PendingIntent` extra, not a URI intent-filter; `CoachActionType` lacks a PR-forward and cross-signal action. | Med | Add enum values + (optionally) an intent-filter if true deep-linking is needed. |
| R9 | **Local-stack deletion regressions** — the `Routing*`/`Gemma*` path is still the live card path; Phase-6 deletion must not break cards. | Med | The guard test protects the *new* system; deletion still needs WI-F migration first. |

---

## 6. Missing data / infrastructure (genuinely absent)

1. **Per-muscle soreness** — only whole-body `DailyLogEntity.sorenessScore: Int?`. Blocks the full Training
   Readiness Handoff vision (WI-E2); needs a schema migration or a scope-down.
2. **"Track this" experiment persistence** (WI-D) — no store exists (`Q7`).
3. **A `WEEKLY` in-app surface consumer** — nothing reads `selectForSurface(WEEKLY, …)`; the winner only
   reaches push (WI-B).
4. **Detectors for `SignalKind.WEEKLY_VERDICT` and `WORKOUT_STREAK_AT_RISK`** — dangling enum members (`Q5`).
5. **A set-save event hook** for the instant inline Live PR (WI-E3) — the engine is digest-batched.
6. **Telemetry/instrumentation** (`D63`) — zero analytics infra; greenfield.
7. **Training domain in the weekly briefing** — `WeeklyReviewData` carries nutrition/body/steps, not lifts.
8. **A URI deep-link intent-filter** — absent; push taps use a `PendingIntent` extra only.

*(Note what is **already solved**, contra the older docs: the batched all-sets window read
`getCompletedSessionsSince` exists (D21 done); `get_training_summary`/`get_body_trends` exist (Phase 3 done);
`CoachContextBuilder` already spans 4 domains; `fallbackText` non-blank is type-enforced.)*

---

## 7. Assumptions to validate before implementation

1. **The engine is the intended "one brain" going forward**, and doc-10's cards are its *surfacing layer* —
   i.e. "don't redesign the architecture" means *build on* `CoachSignalEngine`, not add a parallel card
   system. (If the owner instead wants the doc-10 cards as standalone `GeneratedInsightCard`s independent of
   the engine, the whole plan changes.)
2. **Cloud is the only backend that matters** — the local stack is genuinely being retired (Phase 6), so
   card changes needn't preserve local parity.
3. **The recent "remove TEMP DEBUG scaffolding" commit did not remove the production push path** — verified
   true (only the manual test panel/trigger was removed; weekly push is on-by-default and organically
   reachable). Confirm this is the desired state (no manual test trigger).
4. **"Morning" readiness via `runIfDue` on app-open is acceptable** even when the first open is late — or a
   scheduled morning trigger is wanted.
5. **The doc-10-only deterministic cards (WI-E) are wanted in this workstream** despite containing no AI
   (Q3).
6. **Weekly Pattern Spotlight and Weekly Verdict are intended as the engine-fed variants** (blocked on
   WI-B/D45) rather than the buildable-today `WeeklyReviewComputer` deterministic variants — or vice-versa.

---

## 8. Conflicts identified in the planning documents (RESOLVED by the owner 2026-07-02)

These were surfaced for the owner to resolve — and now are (see §2). The record is retained; each resolution
is noted inline.

1. **NOISE_DEFUSER:** doc 07 Phase 1 "keep untouched" vs doc 10/02 "de-AI into a deterministic Scale Check."
   → **RESOLVED (Q2c): de-AI it, route through the Today slot.**
2. **Dashboard pattern/cross-metric placement:** doc 10 gives Cross-Signal / Weekly-Pattern / Scale Check
   their own dashboard/briefing render slots; docs 02/03/07 fold them into the proactive Today slot / weekly
   check-in and **delete** the standalone dashboard cards. → **RESOLVED (Q2c hybrid): fold into slot/briefing;
   Cross-Signal gets a distinct slot skin; standalone dashboard cards removed.**
3. **REST_OF_DAY:** doc 02 "Improve (invest)" vs doc 10 "Remove" (explained by MealSuggestionCard arriving
   after doc 02). → **RESOLVED: Remove (dead code, superseded by MealSuggestionCard).**
4. **ACTIVITY_NEAT:** doc 10 permits a deterministic standalone; docs 02/07 remove the standalone and keep
   NEAT only as an input. → **RESOLVED (Q4b): remove the standalone; NEAT input-only.**
5. **Phase numbering:** doc 07 "Phase 0 = delete local"; docs 08/09 "Phase 0 = isolate, Phase 6 = delete."
   → **RESOLVED: use the 08/09 numbering** (isolate now, delete last).
6. **"DONE at detector layer" vs "reaches no screen":** doc 07 Phase 4 reads "DONE," but the weekly
   cross-domain signals reach no screen (D45). → **RESOLVED: treated as the open item closed in Phase 2.**
7. **"Don't redesign the architecture" vs docs 03–08 being that architecture** (largely built). → **RESOLVED
   (Assumption 1): build ON the existing `CoachSignalEngine`; cards are its surfacing layer.**
8. **Doc-10-only deterministic cards** (Meal Impact Preview, Training Readiness Handoff, inline Live PR,
   Consistency Check-In) — zero AI, doc-10-only. → **RESOLVED (Q3c): include the low-cost ones now (Meal
   Impact Preview, Consistency Check-In); Training Readiness + inline Live PR are Phase-7 fast-follows.**

---

## Appendix A — decision register index

The 69-item decision register (`D1`–`D69`) with per-decision doc citations and DONE/TODO status is the
backbone of this plan. Status highlights (verified against code, which supersedes the docs' own annotations):

- **Already DONE in code:** local-stack isolation + guard test (`D2`–`D7`), dead `rich` modes removed (`D6`),
  all 5 cross-domain detectors (`D41`), `TODAY` detectors incl. deload + sleep↔hunger (`D42`), journey store +
  narrative (`D44`), push + channels + gate + rate-limiter (`D24`,`D46`–`D49`), training-aware chat tools
  (`D37`,`D38`), batched all-sets read (`D21`), Today slot + celebration (`D27`,`D47`).
- **The big open items:** engine convergence (`D45`, WI-B), Phase-1 subtraction (`D28`–`D31`,`D55`,`D62`, WI-A),
  Cross-Signal rebuild (`D57`, WI-D), the doc-10 cards (`D53`,`D54`,`D58`–`D61`, WI-C/E/F), cloud-only cutover
  finish (`D8`–`D11`, WI-G), knowledge-in-cards (`D33`), instrumentation (`D63`).

## Appendix B — target end-state coverage (per screen)

- **Dashboard:** the `CoachTodaySlot` (Morning Readiness, Consistency Check-In, NEW_PR celebration, and —
  per Q2 — possibly Cross-Signal / Scale Check). **Removes** WEEKLY_PATTERN, TARGET_CHANGE, NOISE_DEFUSER,
  ACTIVITY_NEAT, CROSS_METRIC, legacy WEEKLY_VERDICT cards → from **6 stacked cards + slot to ~1 slot**.
- **Food Log:** MealSuggestionCard (kept) + Meal Impact Preview (in the amount sheet).
- **Body:** Morning Readiness compact echo (retimed RECOVERY_READINESS).
- **Trends:** Recomp Progress Verdict (upgraded PROGRESS_TREND) — visibility caveat (Q8).
- **Train:** Training Readiness Handoff + inline Live PR Callout — visibility caveat (Q8).
- **Weekly Briefing overlay:** consolidated Weekly Verdict + Weekly Pattern Spotlight (engine-fed variant
  gated on WI-B).
- **Coach chat:** unchanged surface; gains inbound handoffs.
- **Push:** weekly check-in (kept); enum/deep-link gaps for PR/cross-signal actions.
- **Deterministic-vs-LLM budget (doc 10 §7 target):** of the card set, ~7 deterministic, ~3 skeleton-merge,
  **1 genuine cloud-LLM** (Cross-Signal Discovery).
