# AI Coaching — Technical Architecture (source of truth)

The single architecture every later implementation follows. Synthesized from the five research docs
in this folder (audit, vision, roadmap, proactive design, data utilization, market research) via five
extraction agents that verified each hook against the code on `develop`. **Planning only — nothing here
is implemented.** Branch: `redesign/ai-coaching`.

**Premise (decided): cloud-first.** The new coaching system is built on one `OpenAiCompatClient` and is
fully independent of the on-device stack. See §5 for the service map.

> **⚠️ Architecture adjustment (2026-07-01): isolate the local stack, don't delete it yet.**
> The on-device Gemma/LiteRT code is **kept and left functional** for now (avoid breaking the app and
> avoid deleting risky code prematurely). Phase 0 changes from "delete the local stack" to
> **"decouple + deprecate"**: move shared constants/utilities the cloud path needs *out* of local-model
> files into neutral homes, add a hard boundary so the new coach system has **zero compile-time
> dependency** on any Gemma/Routing/`AiBackend`/`ModelVariant` class, and mark the local path
> `@Deprecated`/legacy. Actual deletion moves to a later phase (§"Build order", Phase 6) and must be a
> clean removal that touches nothing in `domain/coach`, `data/coach`, or the cloud coordinators.
> Wherever a section below says "delete" for the local stack, read it as **"isolate now, delete in
> Phase 6"** — the target end-state is unchanged; only the timing moves.

---

## 0. The one architectural idea

> **A deterministic engine decides *what* to say and computes *every number*. The cloud LLM only
> phrases it. The LLM is a decoration step, never a dependency.**

Everything below is a consequence of that sentence:
- Numbers on screen are always traceable to an engine field — the model can't invent figures (the
  MacroFactor "explain the why" trust model; the Whoop "one wrong number poisons trust" failure).
- Every signal carries an engine-authored **fallback string**, so the coach still works with no network
  and no model — "never broken, just quieter."
- The engine is pure Kotlin in `domain/` → unit-testable with no network and no model.

```
        ┌─────────────────────────── DETERMINISTIC (domain/, pure Kotlin) ───────────────────────────┐
 Room ─▶│ CoachContextBuilder ─▶ CoachContext ─▶ CoachSignalEngine ─▶ [CoachSignal] ─▶ Ranker+Limiter │
 DataStore                        (snapshot)      (detector catalog)   (facts+verdict+   (one winner)   │
        └───────────────────────────────────────────────────────────────────┬───────────────────────┘
                                                                             ▼
                                                           CoachInboxRepository (persist winner + dedup)
                                                                             │
                    ┌────────────────────────────────────────────────────────┼───────────────────────────┐
                    ▼                                    ▼                     ▼                            ▼
          Today's Coaching slot            Weekly Check-in (spine)      Coach Chat (tools)         Event push
          (renders signal.fallback,        (WeeklyBriefingGenerator     (CloudCoachCoordinator     (NotificationManager,
           LLM-phrased on open)             skeleton-merge, LLM prose)    tool loop)                 capped)
                    └──────────────── all call the LLM as OPTIONAL decoration over engine facts ───────────┘
```

---

## 1. The AI pipeline

Two stages, strictly ordered. The LLM is **downstream** of the decision, never upstream of it.

1. **Decide (deterministic, always runs):** `CoachContextBuilder` reads the four domains into a
   `CoachContext` snapshot → `CoachSignalEngine` runs the detector catalog → each detector returns a
   nullable `CoachSignal` (facts + verdict + rationale + tier/severity + dedupKey + **fallbackText**) →
   `SignalRanker` sorts by `(tier, severity)` → `RateLimiter` applies restraint → **one winner** per
   slot → persisted to `CoachInboxRepository`.
2. **Phrase (cloud, optional decoration):** when a surface is opened (or a push is staged), the LLM
   turns `signal.statement + signal.action` (or the briefing skeleton) into 1–2 natural sentences. On
   timeout/offline/error the surface renders `signal.fallbackText`. **The pipeline is correct and
   complete without stage 2.**

Invariant (testable): any number rendered by any surface equals a field on the `CoachSignal`/context —
never parsed out of model prose.

---

## 2. The deterministic insight engine

`CoachSignalEngine` — pure Kotlin, new package `domain/coach/`. Generalizes the existing
`InsightFact(type, priority, statement)` pattern (`domain/insight/InsightModels.kt:27`) by adding
severity, an attached action, a rationale, a dedup key, and a target surface.

```kotlin
// domain/coach/CoachSignal.kt  (pure Kotlin)
enum class SignalTier { P0, P1, P2, P3 }          // P0 may push; P3 is ambient/in-app only
enum class CoachSurface { PUSH, TODAY, WEEKLY, CHAT_ONLY }

data class SignalFacts(                            // ONLY smoothed, engine-computed numbers reach here
    val values: Map<String, String>,              // e.g. "weightTrendKgPerWk" -> "-0.02"
)
data class SignalRationale(                        // the trust payload (market: "explanation, not the number")
    val primaryCauseByDomain: Map<String, String>,// cross-domain cause: nutrition/training/steps/body
    val behaviorToOutcome: String?,                // "you did X -> trend did Y"
    val confidence: Confidence,                    // HIGH/MEDIUM/LOW/INSUFFICIENT (holding vs updating)
)
data class CoachSignal(
    val kind: SignalKind,                          // stable enum id, used in dedupKey
    val tier: SignalTier,
    val severity: Int,                             // distance past threshold; ranks within a tier
    val facts: SignalFacts,
    val verdict: String,                           // the decision (REQUIRED — no signal without one)
    val action: CoachAction?,                      // deep-link target (log weight / confirm meals / …)
    val rationale: SignalRationale,
    val dedupKey: String,                          // kind + bucketed inputs + ISO week
    val surface: CoachSurface,
    val fallbackText: String,                      // engine-authored, number-safe, LLM-free
)
```

**Detector catalog** — the 19 triggers from `03-proactive-ai-design.md`, most **rerouted** from
existing detectors, a few genuinely new:

| Reuse (rerouted) | Source | Triggers |
|---|---|---|
| `AdjustmentEngine` reason codes (`WAIT_FOR_DATA`, `LOW_ADHERENCE`, `LOSING_WITH_POOR_RECOVERY`, `GAINING_WITH_WAIST_INCREASE`) | `domain/adjustment/AdjustmentEngine.kt:7-55` | weekly verdict, fat-gain warn, recovery, silence rule |
| `PatternDetectors` (derailment, weakest-macro, weekday/weekend, streak) | `domain/insight/PatternDetectors.kt:11-91` | derailment, macro-miss, trained-day nutrition |
| `StreakCalculator` | `domain/streak/StreakCalculator.kt:8` | streak-edge, missed-workout |
| `ActivitySummary` | `domain/activity/ActivitySummary.kt` | NEAT-collapse-explains-stall |
| `TrendCalculator` (weight/waist/recovery) | `domain/trend/TrendCalculator.kt:24,58` | recomp signal, recovery decline |

| New detectors (Phase 4, need training snapshot) | Source | Triggers |
|---|---|---|
| e1RM plateau / new PR | `WorkoutProgressAnalyzer.estimatedOneRepMax` (`:8`), `ExerciseStatsCalculator.oneRepMaxSeries` (`:33`) | plateau, PR celebration |
| Deload-due | RIR (`ExerciseHistoryPoint.rir`) × `recoveryTrend`=POOR | deload |

**Smoothing lives in the engine, before a number becomes a fact.** Raw daily values never reach a
`SignalFacts` or a prompt (trend-smoothing is done with the existing `TrendCalculator`/`MovingAverage`).

**Silence is a first-class output.** When `daysLogged < 14` or the week is noisy, the engine emits
`Confidence.INSUFFICIENT` and a *hold* verdict (mirrors `AdjustmentEngine` `WAIT_FOR_DATA`) — it does
not manufacture an insight. Every surface handles "no eligible signal" as empty/quiet, not an error.

---

## 3. Cloud model responsibilities

The cloud LLM (`OpenAiCompatClient`, `data/remote/OpenAiCompatClient.kt`) has exactly three jobs, and
**computing or surfacing a number is never one of them**:

1. **Phrase an insight/briefing** — `streamCompletion()` (SSE, single-turn, no tools). Input = a
   `CoachSignal` (statement + action + rationale) or the weekly briefing skeleton. Output = 1–2
   sentences (or the briefing's merged prose). Prompt forbids introducing any figure not in the input.
2. **Converse with tools (coach chat)** — `completion()` non-streaming tool loop
   (`CloudCoachCoordinator`, `MAX_TOOL_ROUNDS=12`, 180s/turn). Now **training-aware**: add
   `get_training_summary` to the tool set alongside `get_today_summary` / `get_weekly_trends` /
   `search_food_library` / `search_web` / the write tools.
3. **Name recipes** — `CloudNameGenerator` (unchanged).

Honest-failure behavior stays (`EMPTY_RESPONSE_NUDGE`, never fabricate "Done."). The 2B-specific patches
(`containsEchoPhrase`, empty-text nudge in the Gemma file, `MAX_TOOL_ITERATIONS=5`, `MAX_TURNS=20`
reset) stay confined to the now-deprecated Gemma files and are **never carried into** the cloud path; the
cloud coach's own guards stay. (They get deleted with the rest of the local stack in Phase 6.)

---

## 4. Prompt architecture

- **Engine facts in, phrasing out.** Every insight prompt receives typed, engine-computed, smoothed
  numbers and ends with "use only the figures given; do not do any math; reply with ONLY the
  sentence(s)" — the existing invariant in `InsightPromptBuilder`, now enforced for *all* kinds.
- **Collapse the paraphrase kinds.** WEEKLY_PATTERN and CROSS_METRIC currently feed the model a finished
  sentence for a cosmetic rewrite — remove that layer; the engine statement *is* the content (LLM
  optional).
- **One verdict, one narrator.** The 3 verdict narrators (legacy weekly-verdict card, TARGET_CHANGE
  card, briefing action block) collapse onto the **skeleton-merge** briefing
  (`WeeklyBriefingGenerator`) — the mature form of engine-computes / LLM-phrases. Delete the dead `rich`
  (4–6 sentence) prompt modes or wire them intentionally per surface (decide once; a capable cloud
  model is now guaranteed).
- **Shared scaffold, not 9 bespoke builders.** Move to one prompt scaffold parameterized by the
  `CoachSignal`; keep per-kind wording only where it earns its keep (NOISE_DEFUSER's reassurance tone).
- **Knowledge grounding beyond chat.** The `RetrievalKnowledgeInjector` (today chat-only,
  `AppContainer.kt:372`) feeds the weekly check-in and relevant insight prompts.
- **Structured rationale travels engine → prompt → UI** as fields (not a prose blob), so the UI renders
  the "why" deterministically and the model can only rephrase it.

---

## 5. AI service architecture (cloud-first; local isolated, not deleted)

**Target end-state runtime (what the new system uses):** one `OpenAiCompatClient` → **one**
`CloudInsightCoordinator` + **one** `CloudCoachCoordinator` + **one** `WeeklyBriefingGenerator` + **one**
`CloudNameGenerator`, all sharing **one** `RetrievalKnowledgeInjector`; plus the new `CoachSignalEngine`
+ `CoachContextBuilder` + `CoachInboxRepository` + `CoachDigestWorker` (+ `CoachJourneyStore`, Phase 5).
The new coach system references **none** of the routing/capability/model-lifecycle classes.

**During the transition, the legacy stack stays wired and functional in parallel.** The existing routed
insight cards + coach chat keep working through `Routing*` for now (untouched), so the app never breaks;
the new coaching surfaces (weekly check-in spine, "Today's Coaching" slot, proactive engine) are built
alongside and migrate those surfaces over before Phase 6 removes the legacy path.

### Phase 0 — decouple + deprecate (NO deletion)
The old plan deleted the local stack here; per the 2026-07-01 adjustment we instead **isolate** it. Three
actions, all low-risk:

1. **Extract shared, backend-neutral code out of local-model files into neutral homes** so nothing the
   cloud/new path needs lives inside a Gemma file:
   - **Tool-schema constants** `COACH_TOOL_SCHEMAS`, `COACH_WRITE_TOOLS`, `SEARCH_WEB_TOOL_SCHEMA`,
     `CLOUD_COACH_TOOL_SCHEMAS` — currently **inside** `GemmaCoachCoordinator.kt:42-63` but used by the
     cloud path (`AppContainer.kt:373`). Move to a new neutral `ai/CoachTools.kt`; both the cloud path and
     the (retained) Gemma file then import from there. **This was previously a "blocking gotcha for
     deletion" — it is now the core Phase-0 decoupling task and stands on its own.**
   - Any other shared helpers that happen to sit in a local file (verify: prompt/format helpers, tool
     dispatch, `CoachReadTools`/executor wiring) → relocate to a neutral `ai/` file.
2. **Add the boundary rule (below) and mark the local stack legacy.** Annotate the on-device classes
   `@Deprecated("Local model path is legacy; slated for removal in Phase 6. Do not build on it.")` and/or
   move them under an `ai/local/` (or `ai/legacy/`) subpackage so the boundary is visible at a glance.
3. **Leave the local runtime otherwise untouched** — `Routing*`, `AiBackend`, `ModelVariant`,
   download/verify plumbing, and the model-lifecycle `AiInsightState` members all **stay** and keep
   compiling. Do **not** strip the `AiInsightCoordinator` lifecycle methods yet (the routers still
   implement them); the new system simply doesn't call them.

### The boundary rule (enforced from Phase 0 onward)
The new coach system — everything in `domain/coach/`, `data/coach/`, and the new cloud phrasing path —
must have **zero compile-time dependency** on any of: `Gemma*` (coordinators/service/holder),
`Routing*`, `AiBackend`, `AiCapabilities`, `ModelVariant`, `LocalNameGenerator`, the download/verify
plumbing, or the model-lifecycle `AiInsightState` members. It depends **only** on: `OpenAiCompatClient`,
`CloudInsightCoordinator`, `CloudCoachCoordinator`, `WeeklyBriefingGenerator`, `CloudNameGenerator`, the
knowledge injector, the relocated `ai/CoachTools.kt`, and its own `domain/coach` + `data/coach`. Make
this checkable — a small unit/lint test asserting no `import ...Gemma`/`...Routing`/`AiBackend`/
`ModelVariant` appears under `domain/coach`/`data/coach`, or an ArchUnit-style guard — so future edits
can't silently recouple. When Phase 6 deletes the local stack, nothing in the new system should change.

**Preserve these seams (unchanged):** the `AiInsightCoordinator` **read side** (`state`,
`generationState(kind)`, `onInsightVisible`, `retryInsight`, `onAiCardVisible`, `retryGeneration`) and
the `CoachCoordinator` + `CoachState` interface. New surfaces depend on the **cloud** coordinators
directly (never the routers); `CoachReadTools` stays as the coach test seam. Because the new system talks
to `CloudInsightCoordinator`/`CloudCoachCoordinator` directly, the routing indirection becomes dead for
the new path immediately and dead for the old path once its surfaces migrate — then Phase 6 removes it.

### Phase 6 — retire the legacy local stack (later, clean removal)
Once the new coaching surfaces have replaced the routed insight cards + coach chat, delete (the list
`01-current-ai-audit.md` verified): `GemmaInsightCoordinator`, `GemmaCoachCoordinator`,
`GemmaInsightService`, `GemmaServiceHolder`, `LocalNameGenerator`; the three `Routing*` coordinators;
`AiBackend` + `AiCapabilities`; `ModelVariant` + DownloadManager/StatFs/SHA-256 plumbing;
`CLOUD_ONLY_KINDS`; the model-lifecycle `AiInsightState` members + the `AiInsightCoordinator` lifecycle
methods; the model-variant selector UI; and the `AppContainer` wiring (`effectiveBackend`,
`recipeNamerBackend`, `cloudConfigComplete`, `gemmaServiceHolder`, the two Gemma coordinators). If the
boundary rule held, this touches **nothing** in `domain/coach`, `data/coach`, or the cloud coordinators.

---

## 6. Data flow

`CoachContextBuilder` (new, `data/coach/`) produces one canonical `CoachContext` read-model spanning all
four domains — the join **no current surface performs** (`DashboardViewModel.buildState` omits
`session_sets`, raw sleep, steps).

**Build it as a suspend one-shot snapshot, NOT an 8-way live `combine`** — avoids the documented
first-emit hazard (`LogRepository.kt:51-55`) and the `StreakRepository` per-emission recompute cost.

`CoachContext` (field → source, all verified):
- **Plan:** `PlanRepository.preferences` + `observeVersions()` (targets + history).
- **Profile:** `UserProfilePreferencesStore.preferences` (goal/sex/age/height/activity/gymSessions/stepGoal).
- **Nutrition:** `LogRepository.getDay(today)` + `getWeekMacros` over 28d; adherence via
  `AdherenceCalculator` (`:30,:42`); per-meal type/slot/planned retained for timing insights.
- **Body/recovery/steps:** `DailyLogEntity` series — weight/waist/skinfold (`:11-13`),
  sleep/energy/hunger/soreness (`:17-20`), `trained` (`:21`), `notes` (`:22`, LLM-only);
  trends via `TrendCalculator` (`:24,:58`); steps via `ActivitySummary` (`:41`).
- **Training:** `WorkoutSessionRepository.observeCompletedSessions().first()` (sessions→sets:
  reps/weight/RIR/completed) + `LogRepository.observePerformances()` (manual lifts); per-lift e1RM via
  `ExerciseStatsCalculator.oneRepMaxSeries`.
- **Streaks:** `StreakRepository.streaks().first()` (snapshot, not live).
- **Review history:** `LogRepository.observeWeeklyReviews()` + `CoachJourneyStore` (Phase 5).

**⚠️ Data gotcha (Phase 3/4 prerequisite):** the rich `session_sets` data has **no all-exercises query**
— `WorkoutSessionRepository.getExerciseHistory(exerciseId)` (`WorkoutSessionDao.kt:96`) is per-exercise
only. Add a batched "all completed sets across all exercises in a window" DAO read before the training
detectors (#2/#3/#5) can run without N joins.

The 5 cross-domain insights' exact field plumbing is enumerated in `04-data-utilization.md` §3.

---

## 7. Caching (two-tier, market-driven cadence)

- **Weekly report — recompute weekly, then cache.** The recomposition verdict + rationale is an
  expensive multi-week aggregate. Compute once per weekly boundary (or on a material data change) and
  cache keyed by the **review signature** — reuse `WeeklyBriefingRepository.briefingFor(weekStart,
  signature, generate)` (`:22-48`), which regenerates only when the signature differs. Do **not**
  recompute on every app open.
- **Daily readiness — recompute on open/daily.** Light day-scoped signals (today's adherence, streak
  edge, unconfirmed meals, quiet weigh-ins) are cheap; recompute each morning / on `onStart`.
- **Smoothed trends — cached rolling window,** updated incrementally as entries arrive.
- **Engine output cached; LLM phrasing on demand.** The `CoachSignal` (numbers + rationale) is the
  cacheable artifact (in the inbox); the LLM's natural-language rendering is generated on demand from it.
- **Sufficiency-gated recompute:** only *update* the weekly verdict when data sufficiency is met
  (mirrors MacroFactor "holding" vs "updating"); otherwise keep the cached hold state.
- **Memoize the expensive reads:** `perLiftE1rmSeries` (all-time e1RM per exercise → window to ~28–56d);
  snapshot `Streaks` once via `.first()` (its combine re-folds all history per emission); batch
  `PlanRepository.targetsByDate` instead of `planOn` per date.

---

## 8. Scheduling

`CoachDigestWorker` — a near-verbatim sibling of `HealthSyncWorker` (`data/health/HealthSyncWorker.kt`):
- `CoroutineWorker`; `doWork()` = `applicationContext as? RecompTrackerApp ?: Result.success()` →
  `runCatching { container.coachDigestCoordinator.run() }.fold(success, retry)`.
- `PeriodicWorkRequestBuilder<CoachDigestWorker>(~24h)` (digest is daily; heavy work internally gated) →
  `enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)`; `cancelUniqueWork`
  on disable. **No constraints** (CPU/DB only — the LLM call is deferred to when a surface opens, keeping
  the worker deterministic).
- Copy the `BackgroundSyncScheduler` interface + `Noop` + `WorkManager` impl triad
  (`HealthSyncWorker.kt:52-70`) so the coordinator stays Context-free and testable; wire in `AppContainer`.
- **`onStart` hook:** add `container.coachDigestCoordinator.runIfDue()` to the existing
  `ProcessLifecycleOwner` observer (`RecompTrackerApp.kt:41-47`), debounced once/day (mirror
  `HealthSyncCoordinator.syncIfDue` / `isAutoSyncDue`). Idempotently enqueue the periodic worker on cold
  start for existing users (mirror `RecompTrackerApp.kt:50-54`).
- WorkManager uses default `androidx.startup` auto-init (no `Configuration.Provider` exists, and the
  standard `(Context, WorkerParameters)` constructor pulls deps via `container` — same as HealthSyncWorker).

---

## 9. Proactive engine

Flow: **run catalog → rank → one winner → inbox → (LLM phrases on open / push).** The engine decides
*whether* to speak; the LLM never does.

- **Rhythms:** *daily* (in-app only) evaluates day-scoped triggers on `onStart`/digest; *weekly* (on
  review-signature change) runs the full catalog and produces the check-in; *event* (PR, P0 body verdict)
  fires off session-save/sync.
- **Rank:** sort firing signals by `(tier P0→P3, severity)`. Severity = distance past threshold (the
  existing `priority` int in `PatternDetectors`).
- **One winner per slot;** losers demote to passive per-screen cards (pull), never discarded.
- **Restraint caps (hard, enforced by `RateLimiter`, testable):** push ≤2/week, only P0 or the weekly
  check-in, never consecutive days, respect quiet hours; Today slot = 1 at a time, refreshed ≤once/day,
  auto-dismiss on action/seen; celebrations ≤1/week; a `dedupKey` can't re-surface until its signal
  changes (new week signature) or ≥7 days pass.
- **Silence rules:** hold when `daysLogged < 14` or noisy; never manufacture an insight; never nudge to
  log what was just logged.
- **Dedup** reuses the week-signature `markSeen` chain: `WeeklyReviewComputer.signature(data)`
  (`:45-59`) ↔ a DataStore `lastSeen*` flag, exactly like the weekly-review badge
  (`WeeklyReviewViewModel.kt:57-60,83`).

---

## 10. Memory architecture

Two local (offline-first) stores — memory must survive with no cloud, and the dedup ledger doubles as
journey memory.

- **`CoachInboxRepository`** (DataStore, `data/coach/`) — the *current* featured signal, persisted so it
  survives process restart; exposes `Flow<CoachSignal?>` to the Today slot / weekly report / push;
  `markSeen(dedupKey)` for dedup. Mirrors the `AppPreferences` DataStore pattern
  (`preferencesDataStore` delegate, `.data.map{}` flow, `.edit{}` setters, typed `Keys`) and the
  `lastSeenBriefingSignature` flow/setter pair.
- **`CoachJourneyStore`** (Phase 5; DataStore for the compact ledger, or a small Room table if it grows)
  — append-only: last ~8 weekly review signatures + verdicts, `PlanVersion` transitions, fired-signal
  history (for the no-repeat rule), phase start / `weeksSincePhaseStart` (free from
  `AdjustmentModels.kt:6`). Feeds multi-week narrative into the briefing/chat prompts ("3 weeks ago your
  bench stalled; it's moving again") and lets the engine detect resolved/recurring signals.

Both are read by the engine (recurrence detection) and by the chat tool layer (narrative). Neither
depends on cloud state.

---

## 11. Notification architecture

**Greenfield — no notification code exists today** (no `NotificationManager`/`NotificationChannel` in
the app or manifest), so it's built to the respectful spec from day one (Phase 5).

- **Two channels:** `coaching` (low importance — ambient nudges) and `weekly_check_in` (default
  importance — the spine's one weekly push).
- **Decision-attached gate:** the notification layer **rejects any payload without a `verdict`/`action`**
  — orphaned scores are the universal user complaint. "Never show a number without the 'so do X'."
- **Caps enforced at the emit layer** (the same `RateLimiter`): ≤2 pushes/week, P0-or-weekly only, never
  consecutive days, ≤1 celebration/week, quiet hours respected.
- **Quiet by default; opt-out is first-class.** Default = weekly-report push on, ambient nudges off;
  cadence/tone user-configurable (a new prefs group). With proactivity fully off, the coach still works
  reactively (chat + in-app slot).
- **Delivery:** `CoachDigestWorker` stages the winner; only a P0 or the weekly check-in that also clears
  the `RateLimiter` becomes an actual notification; tapping deep-links to the relevant screen/action.

---

## Package layout (new)

```
domain/coach/            CoachSignal, SignalKind/Tier, CoachSignalEngine, detectors, CoachContext (model) — PURE
data/coach/              CoachContextBuilder, CoachInboxRepository, CoachJourneyStore, CoachDigestCoordinator
data/coach/ (worker)     CoachDigestWorker + BackgroundSyncScheduler-style triad
ai/                      CoachTools.kt (relocated schemas — neutral), CloudInsightCoordinator,
                         CloudCoachCoordinator, WeeklyBriefingGenerator, InsightPromptBuilder (shared
                         scaffold), knowledge/*
ai/local/ (legacy)       Gemma*, Routing*, AiBackend, AiCapabilities, ModelVariant, LocalNameGenerator,
                         download/verify plumbing — @Deprecated, left functional, deleted in Phase 6.
                         Nothing outside this subpackage may depend INTO it from the new coach system.
ui/dashboard/            "Today's Coaching" slot; ui/review/ weekly check-in; ui/coach/ chat (training-aware)
```

Dependency rule preserved: `domain/coach` is pure Kotlin (engine + detectors testable with no
Android/network); `data/coach` does I/O + snapshotting; `ai` does cloud phrasing; `ui` renders and falls
back to `signal.fallbackText`. **Boundary rule:** nothing in `domain/coach`, `data/coach`, or the cloud
coordinators imports from `ai/local/` (guard-tested) — so the legacy subpackage is deletable in Phase 6
in one move.

---

## Build order (maps to `07-roadmap.md`)

| Phase | Architecture deliverable | Prereqs |
|---|---|---|
| **0** | **Decouple + deprecate (NO deletion):** relocate shared tool schemas/helpers out of Gemma files into neutral `ai/CoachTools.kt`; mark the local stack `@Deprecated`/legacy; add the boundary rule + a guard test. Local runtime left functional. | — |
| **1** | Cut paraphrase kinds + dead `rich`; collapse 3 verdict-narrators onto the briefing (cloud-side; doesn't touch the local stack) | 0 |
| **2** | `CoachContext` + `CoachSignalEngine` (rerouted detectors only) + `CoachInboxRepository` + Today slot; then `CoachDigestWorker` + `onStart` + rank/limiter (no push); feed briefing all 4 domains, fire proactively — all cloud-first, obeying the boundary rule | 0,1 |
| **3** | `get_training_summary` tool + body-measurement history in chat; **add batched all-sets DAO read**; knowledge in briefing | 2 |
| **4** | New training/cross-domain detectors (5 links) surfaced through the engine | 2,3 |
| **5** | `CoachJourneyStore` narrative + greenfield push notifications (2 channels, capped) + celebrations | all |
| **6** | **Retire the legacy local stack** (the former Phase-0 deletion): remove Gemma/`Routing*`/`AiBackend`/`ModelVariant`/download plumbing/model-lifecycle states + selector UI + `AppContainer` wiring. Clean removal — touches nothing in `domain/coach`/`data/coach`/cloud coords if the boundary held. | 2–5 (new surfaces have replaced the routed ones) |

## Invariants every implementation must uphold
1. The LLM never introduces a number absent from the engine payload.
2. Every surface renders correctly from `signal.fallbackText` with no network/model.
3. No signal is surfaced without a `verdict` (and, for a push, an `action`).
4. Smoothing happens in the engine before a number becomes a fact.
5. Exactly one winner per slot; restraint caps are enforced code, not guidelines.
6. Silence (`daysLogged < 14` / noisy / nothing crossed threshold) is a valid, first-class output.
7. **The new coach system (`domain/coach`, `data/coach`, cloud phrasing path) has zero compile-time
   dependency on any Gemma/`Routing*`/`AiBackend`/`ModelVariant`/local-lifecycle class** — enforced by a
   guard test, so the local stack can be deleted in Phase 6 without touching the new system.
