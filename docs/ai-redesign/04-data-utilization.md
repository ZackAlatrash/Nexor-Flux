# 04 — Data Utilization for the Cloud AI Redesign

**Scope:** What the AI coach could do with **data the app already collects** — no new
data-collection surfaces. Assumes a **cloud-only** AI going forward (rich prompts, no 2B
tool-iteration cap, room for multi-signal cross-domain reasoning).

**Method:** Inventoried every Room entity (`data/local/entity`), DataStore store
(`data/preferences`), and derived domain model (`domain/*`), then traced how each currently
reaches the UI and the AI.

---

## Key finding up front

The app is **data-rich and AI-poor**. It already stores waist, waist skinfold, sleep, energy,
hunger, soreness, per-set reps/weight/RIR, planned-vs-eaten meals, and a full plan-version
history — but the AI only ever sees a thin slice of it:

- **Insight cards** get a single pre-computed context object each (steps, or macros, or one
  cross-metric — see the `*InsightContext.kt` family in `ai/`).
- **Coach chat** gets today's meal/macro snapshot + plan + profile, and can tool-fetch a past
  day or 7-day macro trend (`docs/ai-coach.md`). It never sees training, RIR, soreness, waist,
  skinfold, sleep, e1RM, or plan history.
- **Weekly briefing** gets the five deterministic review signals (weight/waist/adherence/
  strength/recovery) as pre-graded values and may only add prose
  (`ai/WeeklyBriefingPromptBuilder.kt:1`).

The single existing cross-domain link in the whole codebase is protein↔hunger
(`domain/insight/CrossMetricDetector.kt:17`). Everything else is single-domain. The training
database (`session_sets` with reps/weight/RIR/completed) and the recovery scores
(`daily_logs.energyScore/hungerScore/sorenessScore`) are almost entirely walled off from the AI.

---

## Data source inventory

Legend for **Underutilized?**: **AI-blind** = data is stored/shown but never reaches any AI
prompt; **Partial** = some AI path uses part of it; **Used** = already well-fed to AI.

### Body / daily — `DailyLogEntity` (`data/local/entity/DailyLogEntity.kt`)

| Field (line) | Currently used (UI + AI) | Underutilized? | Cloud AI could |
|---|---|---|---|
| `bodyWeightKg` (10) | Body screen chart; trend engine → weekly review weight signal; adjustment input | Partial (AI sees only the graded "weight ↑/↓" verdict, not the series) | Read the raw weighted moving-average series, call out water-weight vs. true-trend divergence, contextualize a scale jump against training/sodium/carb days |
| `waistCm` (11) | Body screen; trend engine → waist signal; drives `GAINING_WITH_WAIST_INCREASE` / recomp logic (`domain/adjustment/AdjustmentEngine.kt`) | **AI-blind** to raw series (coach never sees waist at all) | **Recomposition signal**: weight flat + waist ↓ = losing fat/gaining muscle — the single most motivating message this app can send, and it's computed nowhere for the coach |
| `waistSkinfoldMm` (13) | Stored; body screen | **AI-blind** — collected but the AI never references it | Combine with waist + weight as a 3-point body-composition triangulation; flag when skinfold ↓ while weight flat (lean recomp) |
| `steps` + `stepsSource` (14,16) | Activity NEAT insight card (`ai/ActivityInsightContext.kt`); streak; activity metrics | Partial (card sees today vs goal vs 7-day avg only) | Correlate NEAT collapse with low-energy days; explain why weight-loss stalled (steps dropped, not calories) using existing step + energy data |
| `sleepHours` (17) | Recovery trend → weekly review recovery signal; recovery insight card | Partial (graded into GOOD/OK/POOR, raw hours hidden from coach) | **Sleep↔lift-performance link** (below); explain a bad training day or high hunger via last night's sleep |
| `energyScore` (18) | Recovery trend; recovery card | Partial | Cross with steps/training/adherence: is low energy from under-eating, poor sleep, or accumulated fatigue? |
| `hungerScore` (19) | Protein↔hunger cross-metric card (`CrossMetricDetector.kt`) | Partial (only vs protein) | Cross hunger with calorie deficit size, carb intake, and sleep — richer "why am I hungry" answer |
| `sorenessScore` (20) | Recovery trend; recovery card | Partial | **Deload signal** (below): rising soreness + falling RIR + poor sleep → suggest a deload week |
| `trained` (21) | Trained flag on day; streak/activity | **AI-blind** for nutrition reasoning | **Training-day nutrition** (below): compare protein/carb adherence on trained vs rest days |
| `notes` (22) | Free-text shown on day | **AI-blind** — unstructured gold the AI is built to read | Parse user notes ("felt weak", "ate out", "sick") to explain outliers the numeric engine flags — a cloud LLM's core strength, currently unused |

### Nutrition — `MealEntryEntity` (`data/local/entity/MealEntryEntity.kt`), recipes, saved foods

| Source | Currently used (UI + AI) | Underutilized? | Cloud AI could |
|---|---|---|---|
| `MealEntryEntity` per-meal macros + `mealType`/`slotId` | Food log; today-summary snapshot fed to coach (`docs/ai-coach.md`) | Partial (coach sees today's meals + 7-day macro totals, not meal-slot patterns) | Detect **meal-timing / distribution** patterns (protein back-loaded, breakfast skipped, all carbs at dinner) — data is present per-meal but never analyzed |
| `planned` flag (line ~40) | Excludes planned from eaten totals; planned-meals feature | Partial | Compare **planned vs actually-eaten** to measure plan-follow-through per user; coach could praise/coach on adherence-to-own-plan |
| `basePer100*` + `amountGrams` + serving fields | Portion math in food log | **AI-blind** | Suggest portion tweaks in real units ("drop rice from 200g→150g to hit fat target") using the stored per-100g base |
| `RecipeEntity` / `RecipeIngredientEntity` | Recipe screen; recipe-name AI helper | **AI-blind** to macros | Suggest which saved recipe best fills today's remaining macro gap |
| `SavedFoodEntity` / `SavedMealEntity` / `CatalogFoodEntity` | Food library search (coach `search_food_library` tool) | Used (search only) | Recommend from the user's *own* frequent foods to hit a gap, not generic catalog matches |

### Training — sessions, sets, lift performance, exercise library (`data/local/entity/*`)

| Source | Currently used (UI + AI) | Underutilized? | Cloud AI could |
|---|---|---|---|
| `WorkoutSessionEntity` (date, status, durationSeconds) | Train screen; session history | **AI-blind** | Report training frequency/consistency, session duration drift |
| `SessionSetEntity` (reps, weight, `rir`, `completed`) (lines 26–29) | Set grid; e1RM/volume derivations (`domain/workout/WorkoutProgressAnalyzer.kt`) | **AI-blind** — the single richest AI-blind dataset in the app | Everything below: plateau detection, RIR-trend deload, volume progression, per-lift coaching. **The coach cannot currently answer "how's my bench going?"** |
| e1RM (Epley) + volume derivations (`WorkoutProgressAnalyzer.kt`, `ExerciseStatsCalculator.kt`) | Exercise detail charts | **AI-blind** | **Plateau detection from e1RM stall** (below); flag lifts regressing vs progressing |
| `LiftPerformanceEntity` (weight/reps/sets/rir) | Performance logging | **AI-blind** to coach | Feed strength trend into nutrition advice ("strength stalled — don't cut further") |
| `ExerciseEntity` (primaryMuscles, category, equipment) | Library; `TrainStatsBuilder` muscle grouping | **AI-blind** | Detect **muscle-group imbalance / neglect** (e.g. no pull volume in 3 weeks) from existing muscle tags |
| `PlannedSetEntity` (target reps/weight) | Routine builder | **AI-blind** | Compare planned vs performed sets → auto-progression suggestions |

### Plan / targets — `PlanVersionEntity`, `PlanPreferences`, adjustment engine

| Source | Currently used (UI + AI) | Underutilized? | Cloud AI could |
|---|---|---|---|
| `PlanVersionEntity` history (`data/local/entity/PlanVersionEntity.kt`) | Plan history screen | **AI-blind** | Narrate the user's **calorie journey** ("you've dropped from 2700→2400 over 8 weeks; weight tracked as expected") — history is stored, never summarized by AI |
| `PlanPreferences` targets + thresholds (`data/preferences/PlanPreferences.kt`) | Dashboard zone; adjustment engine; coach prompt (static) | Used (targets), Partial (thresholds AI-blind) | Explain *why* a verdict fired in terms of the user's own thresholds |
| `AdjustmentEngine` inputs/outputs (`domain/adjustment/`) | Weekly review verdict | Used (briefing narrates the verdict) | Reason across **multiple** past verdicts (is the plan repeatedly holding? user may be under-eating) — currently each review is stateless |

### Derived signals — streak / trend / adherence / activity

| Source | Currently used (UI + AI) | Underutilized? | Cloud AI could |
|---|---|---|---|
| `StreakCalculator` (calorie/steps/workout streaks) | Streak UI | **AI-blind** | Reinforce momentum; warn when a long streak is about to break (last qualifying day near the rest-day tolerance) |
| `TrendCalculator` (`trendPerWeek`, `performanceTrend`, `recoveryTrend`) | Weekly review inputs | Partial (graded values only reach AI) | Expose raw slopes so AI can quantify ("waist ↓0.3cm/wk") instead of just "down" |
| `AdherenceCalculator` (`dailyAdherencePercent`, `loggingConsistency`) | Adherence % in review | Partial | Separate **quality vs consistency** in coaching (great adherence but only logs 3/7 days is a different problem than logs daily but overshoots) — both already computed, never distinguished for the user |
| `ActivityMetrics` (weekly training freq, 7-day step avg) | Activity card | Partial | Cross training frequency with the user's `weeklyGymSessions` target and recovery scores |

### Profile — `UserProfilePreferences` (`data/preferences/UserProfilePreferences.kt`)

| Field | Currently used (UI + AI) | Underutilized? | Cloud AI could |
|---|---|---|---|
| goal, sex, birthDate→age, heightCm, activityLevel, weeklyGymSessions, dailyStepGoal | Profile; coach system prompt (static block, `docs/ai-coach.md`) | Used | Already fed to coach; cloud AI can lean on it harder for individualized targets |
| `profilePhotoUri` | Avatar only | N/A (avatar, not a body photo) | — |

### Not present (confirmed absent — flagged so the redesign doesn't assume them)

- **No progress-photo table** — only `profilePhotoUri` (a profile avatar) exists.
- **No goal-weight / target-body-fat field** — `targetWeightKg` in the code is a *planned-set*
  lift target (`ui/train/RoutineBuilderViewModel.kt:21`), not a body goal.
- **No body-fat %, no explicit deload flag.** Deload can be *inferred* (see below) but is not stored.

---

## Ranked: high-value insights possible from EXISTING data, not generated today

Ranked by (value × how-cleanly-existing-fields-support-it). Every field named below already exists.

### 1. Recomposition signal — weight flat while waist/skinfold shrinks
**Fields:** `DailyLogEntity.bodyWeightKg`, `waistCm` (11), `waistSkinfoldMm` (13), via
`TrendCalculator.trendPerWeek`. **Why #1:** This is the app's whole thesis (body *recomp*) and
the most motivating message it can deliver, yet the coach never sees waist at all and no card
says "you're recomping." The adjustment engine already reasons about weight-flat-waist-stable
(`AdjustmentEngine.kt`) but only to decide calories — never surfaced as encouragement.

### 2. Plateau detection from e1RM stall + strength↔nutrition link
**Fields:** `SessionSetEntity.reps/weightKg` → `WorkoutProgressAnalyzer.estimatedOneRepMax`
(Epley), per-lift `oneRepMaxSeries` (`ExerciseStatsCalculator.kt`). **Why:** The entire training
DB is AI-blind. A cloud coach could flag a lift whose e1RM has been flat/declining for N sessions
**and tie it to nutrition** ("bench e1RM stalled 3 weeks while in a deficit — the cut may be
capping strength"). Data fully present; zero AI path today.

### 3. Deload suggestion from RIR + soreness + sleep trend
**Fields:** `SessionSetEntity.rir` (28), `DailyLogEntity.sorenessScore` (20), `sleepHours` (17),
`energyScore` (18). **Why:** Falling RIR (grinding reps) + rising soreness + poor sleep is the
classic overreaching pattern. `TrendCalculator.recoveryTrend` already grades recovery POOR from
sleep/energy/soreness — it just never joins RIR, and no feature says "consider a deload."

### 4. Training-day vs rest-day nutrition adherence
**Fields:** `DailyLogEntity.trained` (21) joined to `MealEntryEntity` protein/carbs per date.
**Why:** A protein/carb gap specifically on non-training days (or under-fueling *on* training
days) is highly actionable and trivially computable — join the `trained` flag to eaten macros.
Mirrors the existing weekday/weekend detector (`PatternDetectors.kt:detectWeekdayWeekend`) but on
the training axis, which matters far more for recomp.

### 5. Sleep↔lift-performance / sleep↔hunger link
**Fields:** `DailyLogEntity.sleepHours` (17) vs next-day `SessionSetEntity` volume/e1RM, and vs
`hungerScore` (19). **Why:** Same pattern shape as the one cross-metric that already ships
(protein↔hunger, `CrossMetricDetector.kt:17`), extended to the AI-blind sleep + training data.
"Your best lifting days follow 7h+ sleep" or "short-sleep nights run +2 hunger" — both derivable
from fields already logged daily.

**Honorable mentions (also existing-data-only):** meal-timing/protein-distribution patterns from
per-meal `mealType`; muscle-group neglect from `ExerciseEntity.primaryMuscles` + session history;
calorie-journey narrative from `PlanVersionEntity`; planned-vs-eaten follow-through from the
`planned` flag; streak-about-to-break nudges from `StreakCalculator`; parsing `DailyLogEntity.notes`
free-text to explain numeric outliers (a pure-LLM capability currently unused).

---

## Redesign implications

1. **Feed the coach the training DB.** The biggest single unlock is exposing sessions/sets/RIR
   and e1RM/volume derivations (already computed in `domain/workout/`) to the cloud coach. Under
   the old 2B tool-iteration cap this was risky; cloud has the budget.
2. **Give the coach raw series, not just graded verdicts.** Weight/waist/sleep/soreness currently
   reach the AI only as GOOD/OK/POOR or UP/DOWN. Cloud prompts can hold the numbers.
3. **Add a cross-domain layer.** The insight architecture already has a `CROSS_METRIC` fact type
   and one detector; the redesign should generalize it to the 5 links ranked above rather than
   collect any new data.
4. **Parse `notes`.** Free-text daily notes are stored and never read by the AI — the one place a
   cloud LLM strictly beats the deterministic engine.
