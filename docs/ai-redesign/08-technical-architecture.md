# AI Coaching — Technical Architecture (source of truth)

The single architecture every later implementation follows. Synthesized from the five research docs
in this folder (audit, vision, roadmap, proactive design, data utilization, market research) via five
extraction agents that verified each hook against the code on `develop`. **Planning only — nothing here
is implemented.** Branch: `redesign/ai-coaching`.

**Premise (decided): cloud-only.** One `OpenAiCompatClient`; the entire on-device Gemma/LiteRT stack is
deleted (Phase 0). See §5 for the exact keep/delete map.

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
reset) are deleted; the cloud coach's own guards stay.

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

## 5. AI service architecture (post-cutover component map)

**Target runtime (what stays):** one `OpenAiCompatClient` → **one** `CloudInsightCoordinator` + **one**
`CloudCoachCoordinator` + **one** `WeeklyBriefingGenerator` + **one** `CloudNameGenerator`, all sharing
**one** `RetrievalKnowledgeInjector`; plus the new `CoachSignalEngine` + `CoachContextBuilder` +
`CoachInboxRepository` + `CoachDigestWorker` (+ `CoachJourneyStore`, Phase 5). No routing, no capability
flags, no model-lifecycle states.

**Delete in Phase 0** (`01-current-ai-audit.md` verified the list):
`GemmaInsightCoordinator`, `GemmaCoachCoordinator`, `GemmaInsightService`, `GemmaServiceHolder`,
`LocalNameGenerator`; the three `Routing*` coordinators; `AiBackend` + `AiCapabilities`; `ModelVariant`
+ DownloadManager/StatFs/SHA-256 plumbing; `CLOUD_ONLY_KINDS` + its gating; the model-lifecycle
`AiInsightState` members (`ModelMissing`, `Downloading`, `DownloadFailed`, `ModelVerifying`) and the
`AiInsightCoordinator` lifecycle methods (`selectedModel`, `setSelectedModel`, `requestDownload`,
`cancelDownload`, `deleteModel`); the model-variant selector UI; and the `AppContainer` wiring
(`effectiveBackend`, `recipeNamerBackend`, `cloudConfigComplete`, `gemmaServiceHolder`, the two Gemma
coordinators).

**Preserve these seams:** the `AiInsightCoordinator` **read side** (`state`, `generationState(kind)`,
`onInsightVisible`, `retryInsight`, `onAiCardVisible`, `retryGeneration`) and the `CoachCoordinator` +
`CoachState` interface — ViewModels now depend on the cloud coordinators directly (routing indirection
gone). `CoachReadTools` stays as the coach test seam.

**⚠️ Phase 0 blocking gotcha:** the shared tool-schema constants (`COACH_TOOL_SCHEMAS`,
`COACH_WRITE_TOOLS`, `SEARCH_WEB_TOOL_SCHEMA`, `CLOUD_COACH_TOOL_SCHEMAS`) currently live **inside**
`GemmaCoachCoordinator.kt:42-63` but are used by the cloud path (`AppContainer.kt:373`). **Relocate them
to a surviving file (e.g. `ai/CoachTools.kt`) before deleting the Gemma file**, or the build breaks.

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
ai/                      CoachTools.kt (relocated schemas), CloudInsightCoordinator, CloudCoachCoordinator,
                         WeeklyBriefingGenerator, InsightPromptBuilder (shared scaffold), knowledge/*
ui/dashboard/            "Today's Coaching" slot; ui/review/ weekly check-in; ui/coach/ chat (training-aware)
```

Dependency rule preserved: `domain/coach` is pure Kotlin (engine + detectors testable with no
Android/network); `data/coach` does I/O + snapshotting; `ai` does cloud phrasing; `ui` renders and falls
back to `signal.fallbackText`.

---

## Build order (maps to `07-roadmap.md`)

| Phase | Architecture deliverable | Prereqs |
|---|---|---|
| **0** | Cloud-only cutover; **relocate tool schemas first**; collapse to one client + one coord per role; simplify `AiInsightState`/`AppContainer` | — |
| **1** | Cut paraphrase kinds + dead `rich`; collapse 3 verdict-narrators onto the briefing | 0 |
| **2** | `CoachContext` + `CoachContextEngine` (rerouted detectors only) + `CoachInboxRepository` + Today slot; then `CoachDigestWorker` + `onStart` + rank/limiter (no push); feed briefing all 4 domains, fire proactively | 0,1 |
| **3** | `get_training_summary` tool + body-measurement history in chat; **add batched all-sets DAO read**; knowledge in briefing | 2 |
| **4** | New training/cross-domain detectors (5 links) surfaced through the engine | 2,3 |
| **5** | `CoachJourneyStore` narrative + greenfield push notifications (2 channels, capped) + celebrations | all |

## Invariants every implementation must uphold
1. The LLM never introduces a number absent from the engine payload.
2. Every surface renders correctly from `signal.fallbackText` with no network/model.
3. No signal is surfaced without a `verdict` (and, for a push, an `action`).
4. Smoothing happens in the engine before a number becomes a fact.
5. Exactly one winner per slot; restraint caps are enforced code, not guidelines.
6. Silence (`daysLogged < 14` / noisy / nothing crossed threshold) is a valid, first-class output.
