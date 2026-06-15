# Computed Insight Engine (Option B — Clean 4) — Design

**Date:** 2026-06-15
**Status:** Approved design, pending implementation plan
**Branch:** `feat/ai-insight-engine` (off `develop`, with Option A merged)
**Builds on:** Option A (de-bucketed prompts), `docs/superpowers/specs/2026-06-15-insight-cards-debucket-design.md`

## Problem

Even with real numbers (Option A), the insight cards only *narrate* signals the rule engine
already decided. The market lesson (MacroFactor, Whoop, Noom) is that the **interpretation should
be computed deterministically**, then the model just phrases it. The app already stores per-day
nutrition history it never mines for patterns.

## Goal

Add a pure-Kotlin "insight engine" that detects genuinely non-obvious behavioral patterns from
per-day data, ranks them, and surfaces the single highest-priority fact as a new card on the home
screen — phrased by the **cloud model**. The model's only job is to rephrase a precise computed
fact, which plays to its strengths and avoids fabrication.

## Scope — the "Clean 4" detectors

1. **Weekday vs weekend divergence** — average calorie gap between weekday and weekend days.
2. **Derailment day** — the 1–2 days that drove most of the current week's calorie surplus.
3. **Weakest macro** — calories on target but one macro (protein-prioritized) lagging.
4. **Streak** — consecutive recent days hitting the protein target.

### Non-goals (explicitly deferred to later specs)

- MacroFactor-style back-calculated expenditure + hedged calorie nudge (needs reconciliation with
  the existing `AdjustmentEngine`).
- Goal-projection date (needs a new goal-weight field + settings UI).
- Any Room/DataStore schema or preferences change; any settings-screen change.
- Local-Gemma phrasing for this insight kind (this feature is **cloud-only**).
- A multi-card feed (one top-ranked card only).

## Architecture

```
DashboardViewModel
  → builds last-14-days List<DayNutrition> + NutritionTargets (from data it already loads)
  → InsightEngine.detectTopFact(days, targets): InsightFact?      (null ⇒ no card)
  → PatternInsightContext(fact) → InsightRequest.WeeklyPattern
  → RoutingInsightCoordinator → CloudInsightCoordinator phrases fact.statement
  → home-screen card (existing GeneratedInsightCard); hidden when Disabled / no fact
```

The detection logic is **pure Kotlin** in a new `domain/insight/` package (no Android imports),
mirroring the existing `domain/adjustment`, `domain/trend`, `domain/adherence` structure.

## Components

### 1. `domain/insight/InsightModels.kt`

```
data class DayNutrition(
    val date: LocalDate,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val logged: Boolean,      // true if the day had any eaten intake (calories > 0)
)

data class NutritionTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val calorieZoneLower: Int,
    val calorieZoneUpper: Int,
)

enum class InsightFactType { DERAILMENT_DAY, WEAKEST_MACRO, WEEKDAY_WEEKEND, STREAK }

data class InsightFact(
    val type: InsightFactType,
    val priority: Int,        // higher = surfaced first
    val statement: String,    // canonical factual sentence (carries the numbers); fed to the model and used as dedup key
)
```

### 2. `domain/insight/PatternDetectors.kt` — four pure functions `(days, targets) -> InsightFact?`

Each returns null when its minimum-data guard or firing threshold is not met. Concrete rules
(thresholds are private consts, easy to tune):

- **`detectWeekdayWeekend`** — weekend = SAT/SUN by `date.dayOfWeek`. Guard: ≥3 logged weekday days
  AND ≥2 logged weekend days in the window. Gap = mean(weekend calories) − mean(weekday calories).
  Fire if `abs(gap) ≥ 250`. `statement`: *"Your weekends average {weekendCal} kcal vs {weekdayCal}
  on weekdays — {signed gap}."* `priority = 20 + ((abs(gap) − 250) / 50).coerceIn(0, 15)`.
- **`detectDerailmentDay`** — use the most recent 7 logged days. Per-day surplus = `max(0, calories
  − targets.calories)`; weeklySurplus = sum. Guard: `weeklySurplus ≥ 700`. Rank days by surplus;
  take the smallest set of top days (max 2) whose combined surplus is ≥60% of weeklySurplus. Fire
  if such a set of ≤2 days exists. `statement`: *"{Day(s)} drove {share}% of this week's calorie
  surplus."* `priority = 30 + ((share − 60) / 5).coerceIn(0, 15)`.
- **`detectWeakestMacro`** — over logged days, meanCalories within `[zoneLower, zoneUpper]` (calories
  "on point"). For each macro, attainment = mean(eaten) / target. Pick the lowest-attainment macro;
  fire if its attainment < 0.85, preferring protein on ties. `statement`: *"Calories are on point,
  but {macro} is averaging {pct}% of target — your main gap."* `priority = 30 + ((85 − pct) /
  5).coerceIn(0, 15)`.
- **`detectStreak`** — walk most-recent logged days backward; count consecutive days with
  `proteinG ≥ targets.proteinG` (a day that breaks the run stops the count; unlogged days stop it).
  Fire if streak ≥ 3. `statement`: *"{N} days running at your protein target — keep it going."*
  `priority = 10 + ((streak − 3) * 4).coerceAtMost(25)` (so a long streak can outrank other facts).

### 3. `domain/insight/InsightEngine.kt`

```
fun detectTopFact(days: List<DayNutrition>, targets: NutritionTargets): InsightFact?
```

Runs all four detectors, drops nulls, returns the fact with the highest `priority`. Ties broken by a
fixed type order: DERAILMENT_DAY > WEAKEST_MACRO > WEEKDAY_WEEKEND > STREAK. Returns null when no
detector fires ⇒ the card is hidden (quiet on a normal week — matching the research's
"don't narrate a normal day").

### 4. AI-layer wiring

- `InsightKind` gains `WEEKLY_PATTERN`.
- `PatternInsightContext(val fact: InsightFact)` — `hasSufficientData = true`,
  `key() = fact.statement`.
- `InsightRequest.WeeklyPattern(val context: PatternInsightContext)` — new sealed subtype
  (`kind = WEEKLY_PATTERN`, delegates `hasSufficientData`/`dedupKey`).
- `InsightPromptBuilder.buildPatternInsightPrompt(context)`:
  ```
  You are a body-recomposition coach highlighting one pattern you noticed in recent data.
  Rephrase the finding below into exactly 1–2 short, encouraging sentences. Lead with the specific number. No preamble or filler.
  Use only the finding below; do not invent or calculate any new numbers.
  Keep a calm, supportive, non-judgmental tone — frame it as an observation, not a scolding.

  Finding: <fact.statement>
  ```
  Output post-processed by the existing `limitToSentences(text, 2)` + markdown strip in the
  coordinator.
- **`CloudInsightCoordinator.onInsightVisible`** gains the `is InsightRequest.WeeklyPattern ->
  buildPatternInsightPrompt(request.context)` branch (existing streaming path, 60 s timeout, system
  prompt unchanged).
- **Cloud-only enforcement:** `GemmaInsightCoordinator` and `StubInsightCoordinator` treat
  `WEEKLY_PATTERN` as unsupported — they initialize/keep that kind's `generationState` at
  `AiInsightState.Disabled` and no-op `onInsightVisible`/`retryInsight` for it. So when the effective
  backend is local, the card stays `Disabled` (hidden). `RoutingInsightCoordinator` needs no change
  beyond compiling the new kind (it already iterates `InsightKind.entries` and routes by effective
  backend).

### 5. UI + plumbing

- **Reuse existing AI card components verbatim** — the home card uses the same
  `GeneratedInsightCard` + `AiInsightCard` glass shell (with the `✦ AiBadge` and iridescent rim)
  that Progress/Recovery/Food use. **No new card composable.** Title e.g. "Coach spotted".
- **`HomeDashboardContent`** renders the card wired to `generationState(WEEKLY_PATTERN)` and
  `onInsightVisible(InsightRequest.WeeklyPattern(...))`. Because `GeneratedInsightCard` renders
  nothing for `Disabled`/non-generating states, the card is automatically invisible for non-cloud
  users and quiet weeks.
- **`DashboardViewModel`** assembles `List<DayNutrition>` from
  `LogRepository.getWeekMacros(today.minusDays(13), today)` plus `NutritionTargets` from
  `PlanPreferences`, via a new pure mapper `buildPatternInsightContext(days, targets): PatternInsightContext?`
  (null when `detectTopFact` returns null). Calls `onInsightVisible` when the card becomes visible,
  keyed on `fact.statement` for dedup.

#### Day-list construction (removes ambiguity)

The mapper emits **all 14 calendar days** in chronological order. `getWeekMacros` only returns days
that have eaten entries, so for each of the 14 dates: if present in the map →
`DayNutrition(date, calories, macros…, logged = true)`; if absent → `DayNutrition(date, 0, 0.0, 0.0,
0.0, logged = false)`. Detectors that average (weekday/weekend, weakest-macro) and the derailment
finder operate on `days.filter { it.logged }`; the **streak** detector walks the full ordered list
from the most recent date backward, and a `logged = false` day (a gap) breaks the run. This makes
"logged days" and "consecutive days" unambiguous without a new repository query.

No new repository method is required (`getWeekMacros` already accepts an arbitrary date range).

## Window

14 trailing days ending today feed the engine. The derailment detector internally restricts to the
most recent 7 logged days; the other three use the full 14.

## Testing

All detection logic is pure → unit-tested with crafted day lists:

- **Per detector:** fires when it should; stays null below threshold; respects minimum-data guards;
  correct `statement` text and `priority`. Edge cases: exactly-at-threshold, all-unlogged window,
  weekend-only data.
- **Engine:** ranking picks the highest priority; tie-break order; returns null when nothing fires;
  a long streak can outrank a medium-severity negative.
- **Prompt builder:** `buildPatternInsightPrompt` includes the finding, the "lead with the number"
  and "do not invent or calculate" guards, and the supportive-tone instruction.
- **Mapper / context:** `buildPatternInsightContext` returns null on no-fact; `PatternInsightContext.key()`
  equals the fact statement; `hasSufficientData` true.
- **Coordinator dispatch:** `CloudInsightCoordinator` builds the pattern prompt for the new request
  type; local/stub keep `WEEKLY_PATTERN` `Disabled`.

## Risks & mitigations

- **Contradicting the weekly verdict** — detectors describe *behavioral patterns*, never recommend
  calorie changes, so they don't collide with `AdjustmentEngine`'s verdict. (The expenditure nudge,
  which would, is deferred.)
- **Nagging tone** — positive streaks can outrank negatives via the magnitude bonus; the prompt
  enforces a non-judgmental "observation, not scolding" framing.
- **Cloud cost/availability** — cloud-only by design; card simply hidden when cloud isn't the active
  backend, so no degraded experience and no local 2B variability.
- **Noisy facts on thin data** — every detector has an explicit minimum-logged-days guard and a
  meaningful-magnitude threshold, so a sparse or normal week yields no card.
