# 01 — Current AI Audit (pre-redesign)

Read-only audit of every AI-driven feature in the app, written to inform a **cloud-only** AI
redesign. The working assumption throughout: **on-device Gemma / LiteRT-LM is being removed.** Local
code is flagged as *[LOCAL — slated for deletion]* where relevant, but the analysis focuses on the
cloud experience the user will actually get.

Cross-checked against `docs/ai-coach.md` and `docs/improvement-plans/06-ai-coaching.md`. Where the
older plan is now stale, it is corrected inline. All citations are `file:line` against the `develop`
branch on 2026-07-01.

---

## 0. TL;DR of what exists

The AI layer has **four distinct feature families**, all backed by one OpenAI-compatible cloud client
(`data/remote/OpenAiCompatClient.kt`) or the on-device Gemma engine, chosen by a routing layer:

1. **Insight cards** — 8 `InsightKind`s + 1 legacy "weekly verdict" card, single-shot streamed
   one-liners.
2. **Coach chat** — multi-turn, tool-calling conversation (`ui/coach/CoachScreen.kt`, a bottom-nav tab).
3. **Weekly Briefing** — cloud-only structured weekly review overlay (prose merged onto a
   deterministic skeleton).
4. **Recipe namer** — one-shot "gym-bro" name generator in the recipe builder.

Plus the **knowledge base** (77-chunk corpus + keyword retriever) which grounds the *cloud coach only*.

**Every AI surface is reactive.** Nothing reaches out to the user. The most "proactive" thing is a
badge on the dashboard's Weekly Review pill; the AI still only runs when the user taps it.

---

## 1. Routing & backend model (how it all connects)

### 1.1 `AiBackend` and capabilities
`ai/AiBackend.kt` defines `LOCAL` / `CLOUD` and an `AiCapabilities` table (`AiBackend.kt:20-42`):
- LOCAL → `richInsights=false, longContext=false, unboundedToolLoop=false, proactiveReview=false`
- CLOUD → `richInsights=true, longContext=true, unboundedToolLoop=true, proactiveReview=false`

`proactiveReview` is a **Tier-2 capability that is `false` on both backends** (`AiBackend.kt:38`) —
i.e. the "AI reaches out to the user" feature is scaffolded but **never enabled anywhere**. This is
the single clearest signal that the AI is entirely reactive today.

### 1.2 The three routers
All three route on the same rule: **effective backend = CLOUD only when the preference is CLOUD *and*
cloud config is complete; otherwise LOCAL** (`RoutingInsightCoordinator.kt:28-31`,
`RoutingCoachCoordinator.kt:26-29`, and the `recipeNamerBackend` combine at `AppContainer.kt:386-389`).

| Router | Interface | Local delegate | Cloud delegate |
|---|---|---|---|
| `RoutingInsightCoordinator` | `AiInsightCoordinator` | `GemmaInsightCoordinator` | `CloudInsightCoordinator` |
| `RoutingCoachCoordinator` | `CoachCoordinator` | `GemmaCoachCoordinator` | `CloudCoachCoordinator` |
| `RoutingRecipeNamer` | `RecipeNamer` | `LocalNameGenerator` | `CloudNameGenerator` |

Model-lifecycle calls (`requestDownload`, `deleteModel`, `setSelectedModel`) always target the local
delegate (`RoutingInsightCoordinator.kt:41-46`) — pure Gemma-download plumbing that **disappears
entirely once local is removed.**

`cloudConfigFlow` is non-null only when base URL + model id + API key are all present
(`AppContainer.kt:209-220`); `CloudConfig` lives in `data/remote/OpenAiCompatClient.kt:15`.

### 1.3 What simplifies when local is removed
- **Delete outright:** `GemmaInsightCoordinator`, `GemmaCoachCoordinator`, `GemmaInsightService`,
  `GemmaServiceHolder`, `LocalNameGenerator`, `ModelVariant`, `AiInsightState`'s download/verify
  states (`ModelMissing`/`Downloading`/`DownloadFailed`/`ModelVerifying`), all of
  `GemmaInsightCoordinator`'s ~200 lines of DownloadManager/StatFs/SHA-256 code
  (`GemmaInsightCoordinator.kt:84-200,351-379`), the model-variant selector UI
  (`AiCoachComponents.kt`), and the SchemaTool/ToolProvider wrapping (`GemmaCoachCoordinator.kt:508-520`).
- **Collapse the routers:** all three `Routing*` classes become no-ops; ViewModels can depend on the
  cloud coordinator directly. `effectiveBackend`, `cloudConfigComplete`, `recipeNamerBackend`, the
  `AiBackend` enum, and the whole "which engine" toggle vanish.
- **The 2B behavioural patches go away:** `containsEchoPhrase()` (`GemmaCoachCoordinator.kt:376-386`),
  the empty-text nudge, `MAX_TOOL_ITERATIONS=5`, `MAX_TURNS=20` context-reset logic — all exist purely
  to nurse the 2B model and are unnecessary against a capable cloud model.
- **Two separate system-prompt builders merge into one:** `GemmaCoachCoordinator.buildSystemPrompt`
  (`:388-452`, with the fragile 2B rule wording) and `CoachToolsAdapter.buildPrompt` (`:56-89`, the
  clean cloud version). Only the cloud one survives.

---

## 2. Feature-by-feature: the insight cards

There are **8 `InsightKind`s** (`ai/InsightRequest.kt:3-12`) plus a **9th legacy surface**, the
"weekly verdict" card, which is *not* an `InsightKind` but flows through the same coordinator via
`onAiCardVisible`/`buildWeeklySummaryPrompt`.

All insight cards share the same lifecycle: a ViewModel builds a typed `*Context`, the screen fires
`onInsightVisible(request)` from a `LaunchedEffect` keyed on `context.key()` (reactive — runs when the
card scrolls into view), the coordinator dedups on `dedupKey()`, streams the model output, sanitizes
(`replace(Regex("[*_\`#>]"),"")` + `limitToSentences(…, 2)`), and emits `Generating`→`Ready`/`Error`.
Rendered by `ui/component/GeneratedInsightCard.kt` inside the glass `AiInsightCard` shell (edge-glow,
collapsible pill).

**Cloud supports all 8** (`CloudInsightCoordinator.kt:106-115`). **Local supports only 3** —
`GemmaInsightCoordinator.CLOUD_ONLY_KINDS` hides WEEKLY_PATTERN, TARGET_CHANGE, NOISE_DEFUSER,
CROSS_METRIC, **and ACTIVITY_NEAT** by setting their state to `Disabled`
(`GemmaInsightCoordinator.kt:225-229,397-403`). Note: the improvement plan says 4 cloud-only kinds and
7 total — **both are stale.** It is now **5 cloud-only kinds and 8 total** (ACTIVITY_NEAT was added).
On local, those 5 cards **silently vanish with no placeholder or "cloud only" affordance** — a
confirmed UX gap that *disappears once the app is cloud-only* (all 8 always available).

Prompt builders are all in `ai/InsightPromptBuilder.kt`. Every prompt is heavily hand-tuned, US-locale,
"use only the figures given, do no math," "reply with ONLY the coaching sentence(s)."

### 2.1 PROGRESS_TREND — "Trends" screen
- **Where:** ProgressScreen, via `ProgressViewModel.kt:67` (`onInsightVisible(InsightRequest.ProgressTrend)`).
- **Prompt:** `buildProgressTrendPrompt` (`InsightPromptBuilder.kt:56-85`). 1–2 sentences; connect
  weight/waist/lift/adherence; "only describe fat loss when waist is trending down"; end with ONE thing
  to keep doing; supports a `rich` 4–6 sentence mode (never called from either coordinator today).
- **Consumes** (`ProgressInsightContext`): `rangeDays`, `weightTrendKgPerWeek`, `waistTrendCmPerWeek`,
  `liftTrendKgPerWeek`, `adherencePercent`, `priorWeightTrendKgPerWeek`, point counts (gate:
  ≥2 weight or waist points).
- **Ignores:** all recovery signals (sleep/energy/soreness), calorie/macro *intake* levels, step/NEAT
  activity, and any per-lift detail (only a single aggregate e1RM slope). It's a trend narrator with no
  access to *why* the trend is what it is.

### 2.2 RECOVERY_READINESS — "Body" screen
- **Where:** BodyRecoveryScreen, via `TodayViewModel.kt:101`.
- **Prompt:** `buildRecoveryReadinessPrompt` (`:87-118`). Lead with the single most decisive signal,
  give ONE suggestion, respect the trained flag, frame against the user's own recent average, no
  medical advice. `rich` mode exists, unused.
- **Consumes** (`RecoveryInsightContext`): `sleepHours`, `energyScore`, `hungerScore`, `sorenessScore`,
  `trained`, plus `avgSleepHours`/`avgEnergyScore` baselines. Gate: any one metric non-null.
- **Ignores:** *actual training load* (only a boolean `trained`, not what/how much was lifted),
  nutrition (under-eating drives poor recovery — invisible here), and multi-day soreness/energy trend
  (only today + a sleep/energy average, no soreness/hunger baseline).

### 2.3 REST_OF_DAY — "Food Log" screen (today only)
- **Where:** FoodScreen `RestOfDayReveal`, via `FoodLogViewModel.kt:80`. Only built when the viewed day
  is today.
- **Prompt:** `buildRestOfDayPrompt` (`:120-153`). State standing vs plan, name the biggest remaining
  gap (calories or protein), ONE priority for remaining meals; negative "remaining" = OVER target;
  uses day-elapsed % for pacing.
- **Consumes** (`RestOfDayInsightContext`): `caloriesConsumed`, `targetCalories`, calorie-zone bounds,
  `proteinConsumedG`, `proteinTargetG`, `mealsLoggedCount`, `fractionOfDayElapsed`. Gate: ≥1 meal.
- **Ignores:** carbs & fat entirely (protein is the only macro it steers on), what foods were actually
  eaten (can't say "you're low on protein, your usual chicken is in the library"), planned/upcoming
  meals (the `planned` flag is stripped), and the food library (can't suggest a concrete food).

### 2.4 WEEKLY_PATTERN — Dashboard "Coach spotted" (HERO card) *[cloud-only]*
- **Where:** DashboardScreen (`DashboardViewModel.kt:179`). Rendered as the large hero insight.
- **Backing detector:** `domain/insight/` — `InsightEngine.detectTopFact` picks the single
  highest-priority `InsightFact` from 4 pure detectors (`PatternDetectors.kt`): DERAILMENT_DAY
  ("Saturday drove 68% of this week's surplus"), WEAKEST_MACRO ("protein averaging 72% of target"),
  WEEKDAY_WEEKEND ("weekends average +400 kcal"), STREAK ("5 days at protein target"). The winning
  fact's `.statement` becomes both the prompt input and the dedup key.
- **Prompt:** `buildPatternInsightPrompt` (`:155-164`). Rephrase the finding + ONE small suggestion.
  **The AI adds almost nothing** — the detector already produced a complete numeric sentence; the model
  just paraphrases it. This is the weakest "AI" surface: it's a deterministic template with a
  cosmetic LLM rewrite.
- **Ignores:** everything except the one pre-computed statement (`PatternInsightContext(val fact)`).

### 2.5 TARGET_CHANGE — Dashboard "Why your target changed" *[cloud-only]*
- **Where:** DashboardScreen (`DashboardViewModel.kt:189`). Shown when the plan's calorie target moved.
- **Prompt:** `buildTargetChangePrompt` (`:166-190`). Explain the change: what data showed → why the
  target moves → the new number; verb "raise"/"trim" chosen deterministically; pin on data not effort.
- **Consumes** (`TargetChangeContext`): `oldTarget`, `newTarget`, `weightTrendKgPerWeek`,
  `adherencePercent`, `reasonCodes`, `desiredWeeklyRateKg`. Gate: `oldTarget != newTarget`.
- **Overlap:** this is a strict subset of the legacy weekly-verdict card (§2.9) and the Weekly Briefing
  action block (§4) — all three narrate the same adjustment-engine decision. **Redundant.**

### 2.6 NOISE_DEFUSER — Dashboard "Scale check" *[cloud-only]*
- **Where:** DashboardScreen (`DashboardViewModel.kt:197`). Gated by `InsightGate.shouldFireNoiseDefuser`
  (`InsightGate.kt:36-45`): today's day-over-day jump ≥0.6 kg AND it contradicts the smoothed trend.
- **Prompt:** `buildNoiseDefuserPrompt` (`:192-205`). Acknowledge the jump, contrast with trend,
  explain it's water/food/glycogen, nothing to act on.
- **Consumes** (`NoiseDefuserContext`): `todayWeightKg`, `priorWeightKg`, `smoothedTrendKgPerWeek`
  (2 numbers → a delta). Gate: always true (the real gate is `InsightGate`).
- **Ignores:** everything else. This is a good, tightly-scoped reassurance card — but it's a very thin
  2-number prompt the model can't meaningfully enrich.

### 2.7 CROSS_METRIC — Dashboard "Coach noticed a link" *[cloud-only]*
- **Where:** DashboardScreen (`DashboardViewModel.kt:205`).
- **Backing detector:** `CrossMetricDetector.detectProteinHungerLink` (`CrossMetricDetector.kt`) — the
  **only** cross-metric relationship implemented: do protein-hit days run ≥2 points lower on hunger
  than protein-miss days? One correlation, one direction.
- **Prompt:** `buildCrossMetricPrompt` (`:207-215`). State the link, hedge as a tendency, ONE
  suggestion. Again a paraphrase of a complete pre-computed statement.
- **Ignores:** every other possible cross-metric link (sleep↔adherence, steps↔weight trend,
  training↔recovery, weekend↔weight). The concept is powerful; the implementation is one hard-coded pair.

### 2.8 ACTIVITY_NEAT — Dashboard "Activity" *[cloud-only]*
- **Where:** DashboardScreen (`DashboardViewModel.kt:170`). The newest kind (added after the improvement
  plan was written; that plan predates it).
- **Prompt:** `buildActivityNeatPrompt` (`:217-230`). If short of step goal, one encouraging nudge; if
  hit, acknowledge + tie to recomposition.
- **Consumes** (`ActivityInsightContext`): `steps`, `stepGoal`, `averageDailySteps7`. Gate: goal set and
  steps present.
- **Ignores:** the relationship between steps and everything else (weight trend, calorie balance,
  recovery). It's a standalone step nudge — a smart-scale-style "walk more" card with no recomp context.

### 2.9 Legacy WEEKLY_VERDICT card — Dashboard / "Calorie Decision"
- **Where:** DashboardScreen `AiInsightSection`, driven by `DashboardViewModel.onAiCardVisible`
  (`DashboardScreen.kt:951`, `DashboardViewModel.kt:212-224`). Gated by `InsightGate.shouldFireWeekly`
  (`InsightGate.kt:20-30`): fires on any non-HOLD verdict, low adherence, or waist drift — stays quiet
  on a clean on-track HOLD.
- **Prompt:** `buildWeeklySummaryPrompt` (`InsightPromptBuilder.kt:13-54`) — the richest insight prompt:
  full `AdjustmentResult` + all weekly signals + reason codes + prior/new target.
- **Consumes** (`InsightContext`): `AdjustmentResult`, `AdjustmentInput` (weight/waist/perf/recovery
  trends + adherence + weeks-in-phase), targets, desired rate, prior target.
- **Redundancy:** this overlaps heavily with TARGET_CHANGE (§2.5) and the Weekly Briefing (§4) — three
  surfaces narrating the same weekly adjustment verdict, with three different prompt builders and
  copy styles.

### 2.10 Insight prompt-builder observations
- Two of the eight kinds (WEEKLY_PATTERN, CROSS_METRIC) feed the model a **pre-baked complete sentence**
  and ask it to paraphrase — near-zero marginal value from the LLM.
- `rich` (4–6 sentence) variants exist for PROGRESS/RECOVERY/REST_OF_DAY (`InsightPromptBuilder.kt:58,
  89,122`) but **are never invoked** — dead capability. Even the CLOUD backend calls the plain (1–2
  sentence) forms (`CloudInsightCoordinator.kt:107-114`). The `AiCapabilities.richInsights` flag is
  never actually wired to `rich=true`. **Biggest missed cloud lever:** the cloud model is being asked
  for the same one-liner the 2B model gives.
- No insight card can see **training** (except a single lift-slope in PROGRESS) or the **food library**,
  and none can see another card's data — every card is a silo.

---

## 3. Feature: Coach chat

- **Where:** `ui/coach/CoachScreen.kt`, wired as a **bottom-nav tab** (`AppNavGraph.kt:231-238`,
  `TopLevelDestination.Coach`). Also reachable via "Discuss with coach" on the Weekly Briefing overlay
  (`DashboardScreen.kt:163-167` → `onOpenCoach()`), which passes weekly context through
  `CoachHandoffStore` (a one-shot carrier consumed at conversation start,
  `CoachToolsAdapter.kt:52-53`). `CoachViewModel` is a thin delegate over `RoutingCoachCoordinator`.
- **Cloud path:** `CloudCoachCoordinator` — non-streaming tool loop (`CloudCoachCoordinator.kt:127-197`):
  send → run tools (confirming writes) → resend → repeat, `MAX_TOOL_ROUNDS=12`, 180 s per call. Injects
  a fresh knowledge REFERENCE block per turn (`:116-124`). Honest failure handling — nudges once on
  empty, never fabricates "Done." (`:143-162`).
- **Tools** (`COACH_TOOL_SCHEMAS`, `GemmaCoachCoordinator.kt:42-63`; executed by
  `CoachToolExecutor.kt`): `get_today_summary`, `get_weekly_trends`, `search_food_library`, `log_meal`,
  `log_metric`, `update_calorie_target`, plus **`search_web` (cloud coach only)**. Writes
  (`log_meal`/`log_metric`/`update_calorie_target`) require user confirmation via
  `AwaitingConfirmation` (`CloudCoachCoordinator.kt:214-228`).
- **System prompt (cloud):** `CoachToolsAdapter.buildPrompt` (`:56-89`) — plan targets + user profile +
  today's snapshot (pre-fetched) + `COACH_PROMPT_GUIDELINES` (food-lookup-first, web-search fallback,
  cite sources, stay on topic).
- **Consumes:** food log, weekly macros/adherence, food library, plan, profile, web, knowledge corpus.
- **Ignores / biggest gap:** **no workout/training tool.** `CoachToolExecutor` (`:26-35`) has no
  `get_training_summary`/lift/session read path, despite a rich workout layer (`WorkoutRepository`,
  `PerformanceDao`, `domain/workout/*`). A body-*recomposition* coach that can't discuss lifts is the
  headline value gap. It also can't read *body-measurement* history (only today's weight/waist via the
  snapshot), recipes, or streaks.
- **Design-system debt (noted, not this doc's job):** `CoachScreen.kt` uses a Material `Surface` chat
  bubble and bare `fontSize = 14.sp` — flagged in the improvement plan, still present.

---

## 4. Feature: Weekly Briefing (cloud-only)

- **Where:** full-screen `WeeklyBriefingOverlay` (Dialog), opened from the dashboard's "Weekly Review"
  pill (`DashboardScreen.kt:155-173`), driven by `WeeklyReviewViewModel.open()` (`:68-91`).
- **Trigger / gating:** a badge appears on the pill when cloud is active AND the deterministic week's
  data signature differs from last-seen (`WeeklyReviewViewModel.kt:57-60`). **Cloud-only:** with no
  cloud key the overlay shows `Upsell` (`:71,82`); requires ≥7 days logged else `InsufficientData`.
  This is the app's *most* proactive surface — but it's still a passive badge, not a push.
- **How it works:** `WeeklyBriefingGenerator.generate` (`WeeklyBriefingGenerator.kt:17-21`) asks the
  cloud model for **prose-only JSON** (`WeeklyBriefingPromptBuilder`, headline/narrative/per-signal
  interpretations/action rationale/watch-next) and **merges it onto the deterministic
  `WeeklyReviewData` skeleton** (`:41-63`) so the model can never alter numbers or the verdict. Robust
  JSON parsing with a retry (`WeeklyBriefing.kt:56-77`, `WeeklyBriefingGenerator.kt:19`). Fully wired
  and tested (`AppContainer.kt:257,261-267`, `WeeklyBriefingRepository` caches per week+signature).
- **Consumes** (`WeeklyReviewData`): phase, days-logged, `AdjustmentInput` (all weekly trends), verdict,
  per-signal skeletons (weight/waist/adherence/strength/recovery), current & apply targets.
- **Redundancy:** narrates the same adjustment-engine verdict as the legacy weekly-verdict card (§2.9)
  and TARGET_CHANGE (§2.5). The Briefing is the best-built of the three (deterministic-skeleton +
  prose-merge pattern is the right architecture); the other two should arguably fold into it.

---

## 5. Feature: Recipe namer

- **Where:** `ui/recipes/RecipeBuilderViewModel.kt:56,84,258` — a "generate a name" action in the
  recipe builder. `RoutingRecipeNamer` (`RecipeNamer.kt:63-93`) picks cloud/local, 45 s timeout,
  sanitizes to one clean name.
- **Prompt:** `RecipeNamePromptBuilder` — invent one "gym-bro" name from the ingredient list + macros
  ("Anabolic Oats", "Quad Slayer Bowl"). `SYSTEM_PROMPT` at `RecipeNamePromptBuilder.kt:29`.
- **Assessment:** cheap, self-contained, genuinely on-brand novelty. Low stakes, low cost. Cloud-ready
  as-is (`CloudNameGenerator`). Keep.

---

## 6. Feature: Knowledge base

- **Corpus:** `app/src/main/assets/knowledge/corpus.json` — **77 chunks** (verified), tagged across
  domains (muscle/hypertrophy, calories/loss, sleep/fatigue/recovery, supplements, strength).
- **Pipeline:** `KnowledgeCorpus.fromJson` → `KeywordKnowledgeRetriever` (pure-Kotlin weighted keyword
  scoring with light stemming + stopwords, `KeywordKnowledgeRetriever.kt`, `minScore=2.0`) →
  `RetrievalKnowledgeInjector` (caps `maxChunks=3`, `maxChars=2000`, emits a REFERENCE block or "").
  Loaded once, synchronously, in `AppContainer.kt:349-358`; degrades to `NoOpKnowledgeInjector` on
  parse failure.
- **Wiring gap:** injected into the **cloud coach only** (`AppContainer.kt:372`,
  `CloudCoachCoordinator.kt:116-124`). It is **not** wired into the local coach, and — more importantly
  for the redesign — **not into any insight-card prompt or the Weekly Briefing.** The corpus is a
  chat-only grounding source today.
- **Missed opportunity:** the same retriever could ground insight cards ("your protein is low" + a cited
  chunk on protein targets) and the briefing, at zero extra tool cost (it's prepended text, not a tool).

---

## 7. User journey: when does the user actually meet the AI?

| Surface | Location | Trigger | Reactive/Proactive |
|---|---|---|---|
| PROGRESS_TREND | Trends screen | scroll card into view | Reactive |
| RECOVERY_READINESS | Body screen | scroll into view | Reactive |
| REST_OF_DAY | Food Log (today) | reveal/scroll | Reactive |
| WEEKLY_PATTERN (hero) | Dashboard | scroll into view | Reactive |
| CROSS_METRIC | Dashboard | scroll into view | Reactive |
| TARGET_CHANGE | Dashboard | target changed + scroll | Reactive |
| NOISE_DEFUSER | Dashboard | big scale jump + scroll | Reactive |
| ACTIVITY_NEAT | Dashboard | scroll into view | Reactive |
| Weekly verdict (legacy) | Dashboard | non-HOLD/adherence gate | Reactive |
| Weekly Briefing | Dashboard overlay | tap Weekly Review pill (badge hints) | Semi-proactive (badge only) |
| Coach chat | Coach tab | user opens & types | Reactive |
| Recipe namer | Recipe builder | user taps generate | Reactive |

**Conclusion:** the AI is **100% reactive.** It waits to be scrolled to, tapped, or typed at. There is
no notification, no morning check-in, no "you're trending off-plan" nudge, no end-of-week push — even
though `AiCapabilities.proactiveReview` and the whole deterministic weekly-review pipeline are already
in place to power one. **The biggest strategic gap is the absence of any proactive AI touchpoint.**

---

## 8. Critical findings

### 8.1 Redundant / overlapping
- **Three surfaces narrate the same weekly adjustment verdict:** legacy weekly-verdict card (§2.9),
  TARGET_CHANGE card (§2.5), Weekly Briefing action block (§4). Three prompt builders, three copy
  styles, one underlying decision. Consolidate onto the Briefing's skeleton-merge pattern.
- **Two coach system-prompt builders** (`GemmaCoachCoordinator.buildSystemPrompt` vs
  `CoachToolsAdapter.buildPrompt`) — one dies with local.

### 8.2 Weak / near-zero-value insights
- **WEEKLY_PATTERN and CROSS_METRIC** hand the model a finished numeric sentence and ask it to
  paraphrase. The detector does the work; the LLM adds cosmetics. On cloud you're paying tokens for a
  rewrite.
- **ACTIVITY_NEAT and NOISE_DEFUSER** are 2–3 number prompts the model can't enrich — fine as templated
  copy, questionable as "AI."
- **`rich` insight modes are dead code** — cloud never asks for the deeper 4–6 sentence analysis it's
  uniquely capable of. The cloud model is being under-used, generating the same one-liners as the 2B.

### 8.3 Duplicated / dead functionality slated for deletion
- Entire on-device stack (§1.3): coordinators, service, model download/verify, variant selector,
  SchemaTool wrapping, 2B echo/nudge/iteration-cap patches, `CLOUD_ONLY_KINDS` gating.

### 8.3b Rich data logged but never shown to any AI
The DB entities carry far more than the contexts pass. Fields that exist in Room but reach **no**
prompt today:
- **`DailyLogEntity`:** `notes` (free-text — the single most obvious grounding source, never used),
  `waistSkinfoldMm`, `stepsSource`.
- **`MealEntryEntity`:** meal `name`, `mealType`, `amountGrams`, serving info, timing — only
  aggregate macro **totals** are ever passed. No card or coach read-tool sees *what* was eaten
  (except the coach's on-demand `get_today_summary`, which does include per-meal names).
- **`LiftPerformanceEntity`:** `liftName`, `reps`, `sets`, `rir` — only a single aggregate e1RM
  **slope** feeds PROGRESS_TREND; everything specific about training is discarded.
- **Across the board:** meal timing, carbs/fat in REST_OF_DAY, micronutrients, per-day granularity,
  and any second-place pattern fact (detectors surface only the top-1). Insight prompts intentionally
  receive a minimal aggregated slice — sensible for a token-limited 2B model, but leaves substantial
  headroom for a cloud model that could reason over richer context.

### 8.4 Biggest missed opportunities
1. **No proactive AI at all** — the scaffolding (`proactiveReview`, deterministic weekly pipeline,
   handoff store) exists; nothing fires. A weekly/daily push or in-app nudge is the highest-leverage add.
2. **Coach & insights are blind to training** — no workout tool, no lift/session context, for a
   *recomposition* app. Add a read-only `get_training_summary`.
3. **Insight cards are silos** — no card can cross-reference another (nutrition↔recovery,
   steps↔weight, training↔readiness). Only one cross-metric relationship is even implemented.
4. **Knowledge base grounds chat only** — not insights, not the briefing.
5. **Food library & eaten-foods invisible to REST_OF_DAY** — it can't suggest a concrete food from the
   user's own library to close a protein gap.
6. **Cloud capability wasted** — `rich` analysis, long context, and unbounded tools are available but
   the insight prompts still target 2B-sized one-liners.

---

## 9. Cloud-readiness summary

| Component | Status |
|---|---|
| Coach chat | **Cloud-wired** (`CloudCoachCoordinator`), tool loop + web + knowledge; drop the 2B twin. |
| Insight cards (all 8) | **Cloud-wired** (`CloudInsightCoordinator` handles all 8); local hides 5. Removing local makes all 8 universally available and deletes `CLOUD_ONLY_KINDS`. |
| Weekly verdict (legacy) | Cloud-wired via `onAiCardVisible`; candidate to fold into the Briefing. |
| Weekly Briefing | **Cloud-only already** — no change; just always-available once cloud is the only backend. |
| Recipe namer | Cloud-wired (`CloudNameGenerator`); keep. |
| Knowledge base | Cloud-wired to coach only; **extend** to insights + briefing. |
| Routing layer | **Delete** all three `Routing*` + `AiBackend` toggle + `cloudConfigComplete`/`effectiveBackend`. |
| On-device stack | **Delete** entirely (§1.3). |

Once local is gone, the mental model collapses to: **one cloud client → one coach coordinator + one
insight coordinator + one briefing generator + one namer, all sharing one knowledge injector.** The
routing indirection, the capability flags, the model-lifecycle UI, and every 2B behavioural patch all
disappear — a large net simplification.
