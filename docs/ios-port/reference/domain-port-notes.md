# Reference — Domain & AI Layer Port Notes

The algorithmic core: what it does, what makes it portable, and exactly where a careless port
diverges silently.

**Provenance:** ✅ verified against `develop` @ `d874aa5` on 2026-08-01 · 📋 from deep-dive analysis.

---

## 1. Why this layer is special

✅ **`domain/` — 70 files, 6,347 LOC, zero `android.*` and zero `androidx.*` imports.**
✅ **Zero `suspend` functions, zero `Flow`s, zero `StateFlow`s.** 107 `data class`, 26 `enum class`,
6 `sealed` types.

It is pure synchronous computation over value types with **injected clocks** — time is a parameter
almost everywhere (`RebalanceEngine.evaluate(input, newId, nowIso)`,
`RateLimiter.decide(candidate, now, …)`, `SignalSelector.select(signals, seen, today, …)`).
That discipline is the reason the layer is portable at all, and it is why the interop tax is near
zero (see [roadmap §4.3](../00-feasibility-and-roadmap.md)).

Test coverage: **55 test files / 6,096 LOC — a ~1:1 test-to-source ratio.**
**These tests are the executable specification.** Whichever path is taken (KMP or Swift port), they
are the safety net.

### 1.1 Subpackage map

| Package | Files | LOC | `java.time` files | What it does |
|---|---:|---:|---:|---|
| `coach` | 16 | **2,477** | 9 | 18 detectors, selector, rate limiter, cross-signal discovery, experiments |
| `workout` | 10 | 783 | 3 | Muscle fatigue aggregation, exercise stats, plan builder, reorder |
| `rebalance` | 4 | **763** | 3 | Rebalance engine + effective-target overlay |
| `foodimport` | 7 | 633 | 1 | NEVO + Samsung CSV parsers, personal-foods codec (**v1.1**) |
| `food` | 6 | 320 | 0 | Scaling, meal impact, suggester, recipe totals |
| `review` | 4 | 293 | 1 | Weekly skeleton, signature hashing |
| `plan` | 6 | 217 | 3 | Mifflin-St Jeor calculator, generator, plan-version ledger |
| `insight` | 3 | 170 | 2 | 4 nutrition pattern detectors + ranker |
| `share` | 3 | 134 | 0 | `.rtroutine` model, codec, library matcher |
| `adjustment` | 2 | 130 | 0 | The calorie-adjustment decision engine |
| `trend` | 2 | 124 | 2 | OLS regression, trend classifiers, moving average |
| `streak` | 2 | 89 | 1 | Gap-tolerant streak chains |
| `export` | 1 | 76 | 0 | Backup payload DTO (**excluded — see §3**) |
| `activity` | 2 | 65 | 1 | Training-day union, weekly frequency, trailing steps |
| `adherence` | 1 | 47 | 1 | Graded adherence, logging consistency |
| `body` | 1 | 26 | 0 | Steps-input validation (0..200,000) |

---

## 2. The algorithms

📋 Descriptions below are the spec a Swift implementation must satisfy. Constants are the ones that
matter; read the source for the rest.

### 2.1 `AdjustmentEngine` — `domain/adjustment/AdjustmentEngine.kt` (85 LOC)

Ordered rule cascade over `AdjustmentInput` → `AdjustmentResult{verdict, recommendedCalorieChange,
reasonCodes, summary}`. Guards in strict order: `daysLogged < 14` → `WAIT_FOR_DATA/INSUFFICIENT_DATA`
(`:7`) → adherence below threshold (`:16`) → early scale-jump hold (`:28`) → losing + poor recovery
→ **+150 kcal** (`:36`) → gaining + waist up → **−100 kcal** (`:48`) → maintenance band hold (`:60`)
→ weight-up/waist-stable/perf-up hold (`:68`) → default hold. **O(1). Trivially portable.**

### 2.2 `TrendCalculator` — `domain/trend/TrendCalculator.kt` (90 LOC)

`trendPerWeek()` is **ordinary least squares** on (days-since-first, value) × 7. Guards <2 points and
zero variance (`:29,:37`). `performanceTrend` uses **Epley e1RM = `weight × (1 + reps/30)`** (`:86`),
first-vs-last % change, ±2% → UP/DOWN/STABLE. `recoveryTrend` thresholds on mean sleep/energy/
soreness (<6h, <5, >7 → POOR; ≥7h, ≥7, ≤5 → GOOD).

⚠️ **This is OLS, not EWMA.** The only exponential smoothing in the app is the fatigue decay in
§2.8. `MovingAverage.calculate` (`domain/trend/MovingAverage.kt:16`) is a naive O(n²) trailing mean.

### 2.3 `AdherenceCalculator` — `domain/adherence/AdherenceCalculator.kt` (47 LOC)

`dailyAdherencePercent = clamp(100 − |cal−target|/target × 100, 0, 100)` (`:19-23`) — documented as
the single source of truth. `calculate` = mean over **logged** days, each graded against **its own**
day target (`:30-35`). `loggingConsistency` = logged/expected × 100 (`:42-46`).
**Quality and consistency are deliberately separate metrics.**

### 2.4 `PlanCalculator` — `domain/plan/PlanCalculator.kt` (72 LOC)

Mifflin-St Jeor: `BMR = 10·kg + 6.25·cm − 5·age + (M:+5 / F:−161)` (`:16-20`) × activity factor
{1.2, 1.375, 1.55, 1.725} (`:51`) × (1 + goalΔ%) with goal deltas {AGG_CUT −25, MOD_CUT −18,
MINI_CUT −22, RECOMP −5, LEAN_BULK +8, MOD_BULK +12, AGG_BULK +18} (`:58`); round to 10, clamp
1000–6000. Protein 2.2 g/kg cutting else 2.0; fat 25% kcal / 9; carbs = remainder / 4; zone = ±100.

### 2.5 `RebalanceEngine` — `domain/rebalance/RebalanceEngine.kt` (451 LOC) 🔴

**The densest algorithm in the app and the #1 reimplementation risk.** Three pure entry points:

- **`evaluate`** (`:29-137`) — 7 sequential gates → `Silent | NoAdjustment | Offer`. Trailing 7-day
  window ending *yesterday* (`:224`); data-trust gate (`:41`); HIGH-day test
  `over ≥ ABS || over ≥ PCT × base` (`:242`); qualifying Sat+Sun pair (`:253`); cooldown +
  strictly-newer-trigger vs the most recent terminal record (`:55-60`); weekly impact `S ≥ 50×7`
  (`:66`); bulk-goal exclusion (`:69`); surplus band → reassurance/resume notes (`:99-100`).
- **`size`** (`:299-395`) — 🔴 **the hard part.** `calCap = min(round10(PCT×base), ABS)`,
  `floorCap = base − MIN_EFFECTIVE_CAL`, `stepsCap = round500(PCT×avgSteps)` clamped; then a
  **mode-dependent per-day capacity** with a documented two-tier MOVE_MORE fallback
  (`moveMoreStepsSuffice`, `:336-337`) and a BALANCED 0.6/0.6 lever split (`:351`);
  **partial recovery** caps the target at `maxDays × perDayCap` (`:361`); smallest feasible `D` via
  linear scan (`:366`); raw R/E split mirrored in **lockstep `when` blocks** (`:372-389`); final
  `round10`/`round100` + clamp. An explicit "steps-unavailable → calorie-only for every mode" rule is
  documented at `:19-23`.
- **`reconcile`** (`:139-187`) — state machine ACTIVE→COMPLETED/ENDED_EARLY (with an
  `isUnrecoverable` slack test at `:408-430`), OFFERED→DECLINED("expired").
- **`customize`** (`:196-219`) — re-sizes an OFFERED plan for a new mode+intensity from stored facts.

Named constants live in `RebalanceDefaults`. **If reimplementing in Swift, port the tests first.**

### 2.6 `EffectiveTargets` — `domain/rebalance/EffectiveTargets.kt` (107 LOC)

The architectural keystone: **never mutates the base plan.** `resolve(base, date, state)` finds the
overriding plan (ACTIVE/COMPLETED/ENDED_EARLY cover `[start,end]`; OFFERED/DECLINED/NO_ADJUSTMENT
never override, `:75-90`) and re-derives `effective = max(base − reduction, MIN_EFFECTIVE_CAL)`,
protein held, fat = 25%·eff/9, carbs = remainder, zone = eff ± margin (`:93-106`). Plus `unionZone`
(lenient streak protection, `:41`), `effectiveStepGoal`, `planDayInfo`.

⚠️ Open bug P2-10: strict `LocalDate.parse` at `:61,:87-88` means a corrupt persisted plan
crash-loops the dashboard/coach/streak paths. **Make this tolerant on iOS.**

### 2.7 `PlanHistory` — `domain/plan/PlanHistory.kt` (46 LOC)

`planOn(versions, date)` = the version with the greatest `effectiveFrom ≤ date`, clamped to earliest;
baseline sentinel `1970-01-01` (`:13`). **Guarantees a plan change never re-judges logged days.**

### 2.8 `MuscleTrainingAggregator` — `domain/workout/MuscleTrainingAggregator.kt` (183 LOC)

The only true exponential smoothing:
`fatigue = Σ_days volume(day) × exp(−daysAgo / RECOVERY_TAU)` with **`RECOVERY_TAU = 1.5` days**
(`:40,:64,:162`), saturating at `RECOVERY_SATURATION_VOLUME = 4000.0` (`:67`), over a 3-day window
(`:61`). Bands: `FRESH_THRESHOLD 0.75`, `MODERATE_THRESHOLD 0.40` (`:82,:85`). Weekly volume trend
with a `TREND_FLAT_FRACTION 0.10` deadband. **v1.1** (Train).

### 2.9 `StreakCalculator` — `domain/streak/StreakCalculator.kt` (48 LOC)

Gap-tolerant chain scan: two qualifying days are in the same chain when `gap ≤ restDays + 1`
(calorie/steps 0, workout 2). `current` = span of the chain containing the most recent day **only if
alive** (`daysSinceLast ∈ 0..maxGap`), else 0; `longest` = max span. ⚠️ Off-by-one risk on chain
liveness — cover it with tests.

### 2.10 The 18 coach detectors — `domain/coach/*` (2,477 LOC)

Catalog and order fixed in `CoachSignalEngine.default()` (`:29-50`). **Gate: `< 14 loggedDaysInWindow`
→ only `INSUFFICIENT_DATA`** (`:19-21`). Every detector is
`CoachDetector { fun detect(ctx: CoachContext): CoachSignal? }` (14 LOC interface) — one input type,
nullable output, trivially testable.

| # | Detector | File:line | Tier | Key thresholds |
|---:|---|---|---|---|
| 1 | `RecompWinDetector` | `BodyDetectors.kt:26` | **P0** | weight flat ±0.20 kg/wk, waist ≤ −0.15 cm/wk |
| 2 | `FatGainWarningDetector` | `BodyDetectors.kt:79` | **P0** | weight > +0.20 **AND** waist > +0.25 |
| 3 | `LowAdherenceDetector` | `NutritionDetectors.kt:20` | P1 | adherence < 80.0 |
| 4 | `RecoveryDeclineDetector` | `RecoveryActivityDetectors.kt:16` | **P0** | sleep/energy/soreness trend |
| 5 | `DeloadDueDetector` | `CrossDomainDetectors.kt:30` | P1 | RIR window 6, low 1.5, fall margin 0.5 |
| 6 | `NeatCollapseDetector` | `RecoveryActivityDetectors.kt:219` | P1 | steps drop ≥ 25% + weight stall 0.15 |
| 7 | `TrainingPlateauDetector` | `TrainingDetectors.kt:25` | P1 | ≥3 e1RM points, flat band 1.0 kg |
| 8 | `NewPrDetector` | `TrainingDetectors.kt:77` | **P0** | PR margin 1.0 kg, recency 7 d |
| 9 | `StepStreakAtRiskDetector` | `TrainingDetectors.kt:141` | P2 | rest days 0 |
| 10 | `UnconfirmedPlannedMealsDetector` | `NutritionDetectors.kt:205` | P2 | stale planned-meal count |
| 11 | `QuietWeighInsDetector` | `BodyDetectors.kt:182` | P2 | ≥ 5 days since weigh-in |
| 12 | `DerailmentDayDetector` | `NutritionDetectors.kt:119` | P2 | one day drives the week's surplus |
| 13 | `ProteinTrainingDayDetector` | `NutritionDetectors.kt:157` | P1 | 12 pp gap, ≥3 days each group |
| 14 | `SleepHungerLinkDetector` | `CrossDomainDetectors.kt:119` | P2 | good ≥7h / poor <6h, hunger gap 1.5 |
| 15 | `CrossSignalDiscoveryDetector` | `CrossSignalDiscovery.kt:315` | — | see below |
| 16 | `MorningReadinessDetector` | `RecoveryActivityDetectors.kt:86` | P1/P2 | ≥2 inputs; gaps sleep 0.75h, energy 1.0, soreness 1.0, hunger 1.5 — **all vs the user's own baseline** |
| 17 | `ConsistencyCheckInDetector` | `NutritionDetectors.kt:62` | P2 | recent 7d < 4 logged vs prior 7d ≥ 6 |
| 18 | `ScaleCheckDetector` | `BodyDetectors.kt:132` | P2 | day-over-day jump ≥ 0.6 kg contradicting flat trend ≤0.10 |
| + | `InsufficientDataDetector` | `InsufficientDataDetector.kt:16` | P3 | the silence signal |

### 2.11 `CrossSignalDiscovery` — 498 LOC, the largest single domain file

The most novel IP. `CrossSignalCorrelations.evaluate` (`:87`) runs 4 deterministic group comparisons:
`proteinHitNextDayHunger` (`:97`, protein-hit fraction 0.9, hunger gap 1.0), `sleepNextDayEnergy`
(`:147`, 7h/6h split, energy gap 1.0), `highCarbSameDayEnergy` (`:197`, gap 1.0),
`weekendCalorieSurplus` (`:253`, gap 150 kcal) — picks the strongest and emits a **testable
hypothesis**. `ExperimentEvaluation` (`:388-497`) then tracks the accepted experiment:
`currentValue` (`:423`), `classify` (`:438`) with `MOVE_EPSILON = 0.3` (`:402`) and a
per-correlation `hopedLowerIsBetter` polarity map (`:408`), emitting `EXPERIMENT_RESULT` at maturity.

### 2.12 `SignalSelector` — `domain/coach/SignalSelector.kt` (117 LOC)

Four stages: total stable rank `compareBy(tier.ordinal).thenByDescending(severity).thenBy(kind.ordinal)`
(`:101-104`); intra-batch dedup by `dedupKey` keeping the best (`:51`); cooldown suppression via an
injected `seen: Map<String, LocalDate>` with `< 7` days (`:85-95`); one winner or `null`. Losers are
preserved in `SelectionResult.ranked`.

### 2.13 `WeeklyReviewComputer` — `domain/review/WeeklyReviewComputer.kt` (151 LOC)

Builds the deterministic weekly skeleton, plus `signature(data)` (`:70`) — a **bucketed hash**
(`bucket(value, width)` at `:90`) used as the idempotency key for journey memory and weekly-push
dedup. Signed formatting with per-signal deadbands (`STEPS_DEADBAND = 500`, `:137`).

### 2.14 Smaller

- `InsightEngine` + `PatternDetectors` (170 LOC) — 4 detectors: derailment day (weekly surplus ≥ 700,
  share ≥ 60%), weakest macro (< 85% attainment), weekday/weekend (gap ≥ 200 kcal), streak (≥ 3 days).
- `MealSuggester` (149 LOC) — focus selection (PROTEIN until `PROTEIN_MET = 0.85`, then CARBS, then
  CALORIES), gap floors (5 g / 10 g / 100 kcal), portions to fill `FILL_FRACTION = 0.5`, caps at 5.

---

## 3. KMP blockers — the Phase 0 work list

> ✅ **All eleven were resolved on 2026-08-01** (branch `feat/ios-shared-core`). The table below is
> kept as the historical estimate; see [STATUS.md](../STATUS.md) for what actually happened. Three
> errata worth carrying: B2's mapping was incomplete (`toEpochDays` returns `Long`; `monthNumber`/
> `dayOfMonth` are deprecated in 0.8.0; `.month.number` needs `import kotlinx.datetime.number`), and
> three JVM-only API classes are **invisible to a `java.*` grep** — `toSortedSet()`/`toSortedMap()`,
> `"%,d".format()`, and `LocalDate.MIN` (which is `internal` in kotlinx-datetime). All three only
> surfaced at Kotlin/Native compile time. B3 and B4 landed bit-exactly on the first attempt.

📋 Ordered. Effort figures are the analysis's, not measured.

| # | Blocker | Sites | Fix | Effort |
|---|---|---|---|---|
| **B1** | **No KMP module exists** | ✅ `settings.gradle.kts` has only `:app` + `:macrobenchmark` | Create `:shared` (androidTarget + iosArm64 + iosSimulatorArm64) | 1–2 d |
| **B2** | **`java.time` → `kotlinx-datetime`** | 27 files / 40 imports **+ 3 fully-qualified inline uses a naive import-rewrite would miss**: `TrainingDetectors.kt:79`, `RebalanceEngine.kt:57` and `:426` | `ChronoUnit.DAYS.between(a,b)` → `a.daysUntil(b)`; `minusDays(n)` → `minus(n, DateTimeUnit.DAY)` | 2–3 d incl. tests |
| **B3** 🔴 | **ISO week number** | `CoachDetectorSupport.kt:5,22` (`temporal.IsoFields` + `"%04d-W%02d"`) | Hand-roll ISO-8601 week calc (~25 LOC). **Feeds every weekly `dedupKey` — must be bit-exact or cooldowns silently break** | 0.5 d + tests |
| **B4** 🔴 | **`String.format`** | `CoachDetectorSupport.kt:22,27,31` (`"%.Nf"`, `"%+.1f"`) | Hand-rolled signed fixed-decimal. **Feeds every signal's `facts`/`fallbackText` — a rounding drift changes user-visible copy** | 0.5 d |
| **B5** | Day-name localisation | `PatternDetectors.kt:4,44` (`getDisplayName(TextStyle.FULL, Locale.US)`) | Static `DayOfWeek→String` map | 1 h |
| **B6** | `java.io` CSV streaming | `NevoCsvParser.kt:3-4,11,13,82,146`; `SamsungHealthFoodCsvParser.kt:3-4` (453 LOC) | **Exclude `domain/foodimport` — it's v1.1 anyway** | 0 |
| **B7** | `BackupModels.kt` imports 19 Room entities | `domain/export/BackupModels.kt:3-21` | **Exclude** — it's a persistence DTO misfiled in domain | 0 |
| **B8** | 3 files typed on Room entities | `RecentFoods.kt:3`, `RecipeWithIngredients.kt:3-4`, `ExerciseLibraryJson.kt:3` (86 LOC) | Pure DTOs + mapper in `:app`, or exclude | 0.5 d |
| **B9** | `domain/plan` + `domain/rebalance` → `data.preferences` | 6 files, 8 imports, **5 symbols** | Move 2 files into `commonMain` — see §3.1 | 0.5 d |
| **B10** | `core.model.MacroTotals` | `CoachContext.kt:3`, `NutritionDetectors.kt:3`, `MealSuggester.kt:3` | Move the 22-LOC pure file | 15 min |
| **B11** | `LocalDate.now()` default arg | `PlanGenerator.kt:24` | Require the caller to pass `today` (already done everywhere else) | 15 min |

**Scope:** all of `domain/` **minus** `export` (76), `foodimport` (633), and the 3 entity-typed files
(86) = **~5,550 LOC shared**. Estimated 8–12 engineer-days to compile K/N-clean and green under
`iosSimulatorArm64Test`, including migrating the tests.

> 🔴 **B3 and B4 are the real risk, not compilation.** ISO-week strings and `%+.1f` formatting are
> inputs to `dedupKey` and `fallbackText`. A subtle difference **silently** changes cooldown
> behaviour and user-visible copy rather than failing loudly.
> **Write golden-value tests pinning current JVM output BEFORE touching the formatters.**

### 3.1 The "documented `data.preferences` exception" is smaller than CLAUDE.md implies

📋 6 files, 8 import lines, **5 distinct symbols**, all pure:

| File:line | Symbol |
|---|---|
| `domain/plan/PlanCalculator.kt:3,4,5` | `ActivityLevel`, `BiologicalSex`, `FitnessGoal` |
| `domain/plan/PlanCalculatorModels.kt:3,4,5` | same 3 |
| `domain/plan/PlanGenerator.kt:3,4` | `UserProfilePreferences`, `ageYears()` |
| `domain/rebalance/RebalanceEngine.kt:3` | `FitnessGoal` |
| `domain/rebalance/RebalanceEvaluationInput.kt:3` | `FitnessGoal` |

All five live in **two files with no Android code at all** — `UserProfilePreferences.kt` (69 LOC:
3 `@Serializable` enums + a data class of primitives + `ageYears()`, which takes `today` as a
parameter) and `PlanPreferences.kt` (a data class + `withCalorieTarget()` + a constant). The
DataStore machinery lives separately in `*Store.kt`/`*Source.kt`.

**Verdict: a package-naming artifact, not architectural entanglement.** Moving those two files into
`commonMain` resolves the whole documented exception in well under a day. `PlanCalculator` and
`RebalanceEngine` never touch DataStore.

---

## 4. The AI layer

📋 `ai/` 3,069 LOC (28 files) · `data/remote/` 504 LOC · `data/coach/` 2,092 LOC = **5,665 LOC**,
of which roughly **83% is pure logic**.

### 4.1 Coupling census

✅ **Only 4 files in all of `ai/` have any Android import, and all four are just `android.util.Log`**
(`CloudCoachCoordinator.kt:3`, `CoachToolExecutor.kt:3`, `CoachPhrasingService.kt:3`,
`RebalanceCopyService.kt`). All 24 other files: zero android/okhttp/java imports.

| Bucket | LOC | Portable? |
|---|---:|---|
| 8 prompt builders + 19 tool schemas + knowledge base + state models | ~1,350 | ✅ trivially |
| `OpenAiCompatModels.kt`, `WebSearchModels`, `OpenFoodFactsModels` | 338 | ✅ pure kotlinx.serialization |
| `OpenAiCompatClient` + `TavilyWebSearchProvider` | 166 | ❌ OkHttp — but both are `open` with injected clients |
| `data/coach` pure core (assembler, builder, cache, emitter, 4 serialisers) | 1,065 | ✅ 51% of the package |
| 6 DataStore-backed stores | 693 | ❌ impls; **interfaces are pure** |
| WorkManager worker + Android notifier | 174 | ❌ |
| `CoachToolExecutor` | **904** | ❌ **rewrite per platform** |

### 4.2 Deterministic-first doctrine — verified in code

- `WeeklyBriefingGenerator.merge()` (`:74-98`) takes `verdict`, every number, and `patternSpotlight`
  straight from `WeeklyReviewData`. Narration supplies only prose fields, each guarded
  `?.takeIf { it.isNotBlank() } ?: data.<deterministic>`.
  **The model structurally cannot alter a number.**
- `CoachPhrasingService.phrase()` (`:40-62`) returns `signal.fallbackText` on missing config,
  timeout, exception, or blank output. Three return paths, never throws.
- `CoachSignal`'s `init` block (`domain/coach/CoachSignal.kt:32-37`) **requires** non-blank `verdict`
  and `fallbackText` — the fallback is a type invariant.

**Preserve all three properties on iOS.** They are what make the AI safe to ship.

### 4.3 The turn loop — `CloudCoachCoordinator.kt` (467 LOC)

Three concurrency primitives:

1. **`turnLock = Mutex()`** (`:74`) — `handleMessage` runs entirely inside it (`:125`), serialising
   whole turns **including the human confirmation wait**. `thinkingSteps` and `committedWrites` are
   plain mutable lists, safe *because* of this lock.
2. **`pendingConfirmation = AtomicReference<CompletableDeferred<Boolean>?>`** (`:87`) —
   `confirmAndRun` (`:286-309`) resolves display text, stores the deferred, emits
   `AwaitingConfirmation`, then `deferred.await()` **with no timeout, by design**.
3. **Claim semantics** — confirm/cancel/`clearHistory` all `getAndSet(null)?.complete(…)`, so the
   first tap atomically wins and a stale tap is a no-op that **can never approve a later turn's
   write**.

Limits: `MAX_TOOL_ROUNDS = 12` (`:417`), `TURN_TIMEOUT_MS = 180_000` per completion (`:418`, and
the confirmation wait is deliberately **outside** it), `MAX_CONTEXT_CHARS = 24_000` (`:423`).
`revertFailedTurn` (`:257-267`) truncates only the failed turn; `trimOldTurns` (`:275-281`) drops
whole `[user … next-user)` blocks so `tool_calls` stay paired with their results and index 0 is never
touched. Empty-response recovery (`:187-199`): one nudge, then an **honest error** — success is never
fabricated.

**Swift shape:** an `actor` + `CheckedContinuation`. ⚠️ Because Swift structured concurrency cancels
child tasks when the parent deallocates, the generation must be owned by an **unstructured `Task`
held by the container** — the equivalent of today's `appScope`.

### 4.4 The 19 tools — `ai/CoachTools.kt`

`COACH_TOOL_SCHEMAS` (8) + `SEARCH_WEB` (1) + `ROUTINE_TOOL_SCHEMAS` (5) + `MEAL_EDIT` (2) +
`MEMORY` (2) + `SUGGESTION` (1). Dispatch: `CoachToolExecutor.execute` `when` at `:90-110`.

**Reads:** `get_today_summary(date?)`, `get_weekly_trends`, `get_training_summary`,
`get_body_trends`, `search_food_library(query, grams?)`, `suggest_meals(date?)`, `search_web(query)`,
`get_routines`, `search_exercises(query)`.

**Writes** (`COACH_WRITE_TOOLS`, confirmation-gated): `log_meal`, `log_metric`,
`update_calorie_target`, `create_routine`, `edit_routine`, `create_exercise`, `delete_meal`,
`edit_meal`.

**Memory** (deliberately unconfirmed — low-stakes, reversible in the Coach-memory screen):
`remember`, `forget`.

⚠️ **The five routine tools are v1.1** — ship them as unavailable in iOS v1 so the coach declines
cleanly rather than writing rows no screen can display.

Validation ranges in `CoachToolExecutor`: calorie target **500–6000**, weight 20–300, waist 40–200,
sleep 0–24, scores whole 1–10 (`:467-490`).

📋 Two internals worth preserving: `MIN_LIBRARY_MATCH_SCORE = 1` (`:46`) gates whether a library hit
overrides the model's name+macros (scorer at `:441-465`: 3=exact, 2=starts-with, 1=contains,
0=rejected); and `ResolvedMeal` (`:57-69`) is produced **once** by `resolveMeal` and consumed by
**both** execution and the confirmation dialog, so the dialog can never describe a different action
than the one performed (P1-9).

### 4.5 The HTTP layer — `data/remote/OpenAiCompatClient.kt` (105 LOC)

One pooled `OkHttpClient` (15s connect / 60s read, **no retries, no `tool_choice`**).
`CloudConfig(baseUrl, apiKey: () -> String, model)` — **the key is a provider lambda read at request
time** (P1-5 fix). SSE is hand-rolled over an okio `BufferedSource` (`:47-74`).

Two JSON parsers by design (`:17,:20`): `strictJson` for server payloads, `lenientJson`
**only** for model-generated tool arguments (handles `"500.0"`-style ints).
⚠️ **Swift `Codable` is stricter than kotlinx.serialization here** — you will need
`@propertyWrapper`-based lenient Int/Double decoders.

Robustness details worth porting: `messageContentText` (`:153-159`) handles `content` being either a
plain string **or** an OpenAI content-parts array (P2-6 fix — reading it as a primitive threw and
failed whole turns); `contentOrNullSafe` (`:142-145`) distinguishes JSON-null from the string
`"null"`.

Errors collapse to `IllegalStateException("HTTP <code>: <body>")`. **No typed taxonomy, no 429
handling** — a known gap, worth improving rather than porting.

### 4.6 The proactive spine — `data/coach/`

Pipeline: `CoachContextBuilder.build()` → `CoachContextAssembler.assemble()` (28-day window) →
`CoachContextCache` (per-calendar-day memo) → `CoachDigestCoordinator.run()` → `CoachSignalEngine` +
experiment → `SignalSelector` → `CoachInbox.stage(winner|null)` → `CoachPushEmitter` → `RateLimiter`
→ notifier.

📋 Design notes worth carrying:
- `CoachContextBuilder` reads **every source as a suspend one-shot `.first()`, never an 8-way live
  `combine`** (documented at `:15-21` to dodge a first-emit hazard).
- `CoachDigestCoordinator` **re-checks the once-a-day debounce inside the lock** (`:93`) — without
  it, the second acquirer stages the runner-up over the real winner (P1-7).
- It selects with `selectForSurface(CoachSurface.TODAY, …)` (`:106`) — **WEEKLY-surface signals must
  not leak into the daily slot.**
- `CoachPushEmitter` (90 LOC) is the **fully-testable push seam with zero Android calls**; a weekly
  check-in is recorded as **non-celebration** so it never burns the celebration budget (`:82`).
- **Every platform dependency already sits behind an interface with a Noop** — `CoachNotifier`,
  `CoachDigestScheduler`, `CoachInbox`, `CoachMemory`, `PushHistory`, `CoachExperiments`,
  `CoachJourney`, `WebSearchProvider`, `KnowledgeRetriever`, `DateProvider`. **The iOS port slots
  into existing seams rather than carving new ones.**

### 4.7 Knowledge base — `ai/knowledge/` (206 LOC, zero platform imports)

**Corpus:** `assets/knowledge/corpus.json`, `{chunks: [{id, title, tags[], source, text}]}`.
`fromJson` (`:38-51`) validates every field non-blank, tags non-empty, ids unique — throws to catch a
broken ingestion before ship. On failure the injector stays a **no-op** (features degrade, never
crash).

**Retrieval** (`KeywordKnowledgeRetriever.kt`): `score = Σ (titleTF×3 + tagHit×2 + bodyTF×1)`
(`:45-58`), **min-score floor 2.0** (`:16`), sort `compareByDescending(score).thenBy(chunk.id)` —
**deterministic tie-break** (`:41`). Tokeniser: lowercase, split `[^a-z0-9]+`, drop length<2 and a
30-word stopword set, then stem (`>5 && endsWith("ing")` → −3; `>4 && "ed"` → −2;
`>3 && "s" && !"ss"` → −1). Applied identically to query and corpus.

**Injection** (`RetrievalKnowledgeInjector.kt`): top 3 chunks in a 2000-char budget.
**Chunk 0 is always included, truncated with "…" if it alone exceeds budget** (`:29-33`).
Format `[n] {title} — {body} (Source: {source})`.

---

## 5. Component-by-component port table

| Component | LOC | Pure % | Shareable | iOS difficulty |
|---|---:|---:|---|---|
| `domain/adjustment` | 130 | 100% | ✅ | Trivial |
| `domain/adherence` | 47 | 100% | ✅ after B2 | Trivial |
| `domain/trend` | 124 | 100% | ✅ after B2 | Trivial |
| `domain/plan` | 217 | 100% | ✅ after B2+B9 | Easy |
| `domain/streak` | 89 | 100% | ✅ after B2 | Easy (off-by-one risk) |
| `domain/insight` | 170 | 100% | ✅ after B2+B5 | Easy |
| `domain/review` | 293 | 100% | ✅ after B2 | Moderate — `signature()` bucketing must match |
| `domain/food` | 320 | 72% | ⚠️ 234 yes / 86 no (B8) | Easy |
| `domain/workout` | 783 | 93% | ⚠️ 729 / 54 (B8) | Moderate — exp-decay model |
| `domain/rebalance` | **763** | 100% | ✅ after B2+B9 | 🔴 **Hard** — `size()` mode interlock |
| `domain/coach` | **2,477** | 100% | ✅ after B2+B3+B4+B10 | 🔴 **Hard** — 18 threshold sets, ISO-week keys |
| `domain/share` | 134 | 100% | ✅ as-is | Trivial |
| `domain/foodimport` | 633 | 100% logic | ❌ B6 (v1.1) | Moderate |
| `domain/export` | 76 | 0% | ❌ B7 | N/A — rewrite against GRDB |
| **`domain/` total** | **6,347** | **~87%** | **~5,550 shareable** | **8–12 d to KMP** |
| Tool schemas | 59 | 100% | ✅ | Trivial (raw JSON) |
| 5 prompt builders | 445 | 100% | ✅ | Easy |
| `ai/knowledge/*` | 206 | 100% | ✅ | Easy |
| `OpenAiCompatModels` | 161 | 100% | ✅ | Easy — or `Codable` |
| `OpenAiCompatClient` | 105 | 0% | ❌ | Moderate — `URLSession.bytes.lines` |
| `CloudCoachCoordinator` | 467 | ~97% | ✅ | 🔴 Hard — actor + continuation |
| `CloudInsightCoordinator` | 177 | 100% | ✅ | Moderate — actor + `Task` cancellation |
| **`CoachToolExecutor`** | **904** | ~10% | ❌ | 🔴 **Hard — the single biggest AI rewrite** |
| `WeeklyBriefingGenerator` + builders | 242 | 100% | ✅ | Easy — `merge()` is the whole trick |
| Phrasing / rebalance copy / recipe namer | 459 | ~99% | ✅ | Easy |
| `data/coach` pure core | 1,065 | 100% | ✅ | Moderate |
| `data/coach` DataStore stores | 693 | 0% | ❌ interfaces yes | Easy — same 6 interfaces |
| Worker + notifier | 174 | 0% | ❌ | Easy |

---

## 6. If you port to Swift instead of sharing

Budget ~5,550 LOC → ~5,000 lines of Swift. The three places bugs will hide:

1. **`RebalanceEngine.size()`** — ~100 LOC of interlocked mode/intensity branches.
2. **`CrossSignalDiscovery`** — 498 LOC.
3. **The 18 detectors' exact thresholds.**

Swift `Foundation`'s `Calendar`/`DateComponents` is a **much worse fit** than `kotlinx.datetime` for
the `LocalDate`-arithmetic style used throughout — expect timezone bugs unless you build a
`LocalDate`-equivalent value type first.

**Mitigation that makes this viable: port the 6,096 LOC of tests first, then implement against
them.** That converts bit-exactness from a hope into a verified property. It is also why this path
is a legitimate fallback rather than a defeat.
