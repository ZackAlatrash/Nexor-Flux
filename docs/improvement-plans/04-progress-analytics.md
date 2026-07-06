# Improvement Plan 04 — Progress / Analytics / Body Metrics / Dashboard / Reviews / Streaks

Scope: the analytics surface of the app — Dashboard, Progress, Body check-in/history, Weekly
Review, and Streaks — plus the domain layers they feed from (trend, streak, review, insight).
This area is already polished; this plan is targeted hardening, not a rewrite. Every finding
below was verified against the current code on `develop` with file:line citations.

All UI changes must conform to `docs/design-system.md` (AppType tokens, FrostedCard/NeutralCard,
Liquid* buttons, ScreenScaffold/headers, 16dp gutter). Do **not** implement from this doc — it is
a plan only.

---

## 1. Current state & problems

**Dashboard cold-start is sparse.** `DashboardScreen.kt:226–287` gates every AI insight card on a
non-null context (`patternInsightContext`, `crossMetricContext`, `targetChangeContext`,
`noiseDefuserContext`). For a brand-new user all four are null, so the `LazyColumn` renders only
`MotivationalCard` (274), `TodayCard` (275), `StatTilesRow` (276), `SevenDayChartCard` (283), and
the conditional `StreaksCard` (284). With no logs, the chart and tiles are empty and there is no
"get started / log your first meal" guidance — the screen reads as broken rather than new.

**Two near-duplicate chart UIs.** Both screens already share the same low-level primitives
(`SparklineChart` / `MiniSparkline` in `ui/component/charts/SparklineChart.kt:45,256`), but each
screen re-implements the *card chrome* around them independently:
- Progress: `FeaturedChartCard` (`ProgressScreen.kt:165`), `ShortChartCard` (187),
  `MiniChartPair`/`MiniChartCard` (202/213), `ChartHeader` (272), `RangeSelector` (126).
- Dashboard: `SevenDayChartCard` (`DashboardScreen.kt:603`) — its own header, day-label row, macro
  row, and divider, none reused from Progress.

The duplication is the card scaffolding (header + value + trend badge + no-data fallback + scrub
state), not the line drawing. `MiniChartCard` (213) also hand-rolls a raw
`.clip().background().border()` card instead of `NeutralCard`, against the design-system rule.

**Design-system violations in the dashboard chart.** `SevenDayChartCard` hardcodes
`fontSize`/`fontWeight`/`letterSpacing` on raw `Text`s at `DashboardScreen.kt:625–628` (scrub
header) and `683–685` (day labels) — exactly the bare-`fontSize` pattern the design system
forbids. These should be `AppType` tokens.

**Insight context builders are fragmented.** There is no unified builder. `ProgressInsightMapper.kt`
(`buildProgressInsightContext`) and `PatternInsightMapper.kt` (`buildPatternInsightContext`) each
build their own context, and the wider family fans out further:
`ui/today/RecoveryInsightMapper.kt`, `ui/today/RestOfDayInsightMapper.kt`, plus context data
classes `ai/ProgressInsightContext.kt`, `ai/PatternInsightContext.kt`, `ai/RecoveryInsightContext.kt`,
`ai/RestOfDayInsightContext.kt`, `ai/InsightContext.kt`. Each ViewModel wires its own mapper; there
is no shared assembly point or naming convention.

**Two different trend math implementations.** `ProgressInsightMapper.kt:26` computes "trend per
week" as a naive **first-to-last delta** (`(last - first) / weeks`), while
`domain/trend/TrendCalculator.kt:24` (`trendPerWeek`) does a proper **least-squares linear
regression**. The same conceptual metric is computed two different ways depending on entry point,
and the regression path ignores `MovingAverage` (`domain/trend/MovingAverage.kt`), which exists but
has **no production caller** for trend smoothing.

**Streaks recompute from scratch every emission.** `StreakRepository.streaks()`
(`StreakRepository.kt:28`) `combine`s five flows and re-runs `buildStreaks` → `StreakCalculator.compute`
(`StreakCalculator.kt:21`, O(n log n) sort over all qualifying days) on *every* change to any of
the five sources. Correct and fine at current data sizes; wasteful as history grows because the
full window is rebuilt for a one-day delta.

**Body data is a single row per day.** `DailyLogEntity` (`DailyLogEntity.kt:9`) keys on
`@PrimaryKey val date: String` and packs weight, waist, skinfold, steps, sleep, energy/hunger/
soreness, trained, and notes into one row. A second check-in the same day overwrites the first;
there is no place for morning-vs-evening weight or multiple measurements.

**Weekly Review signals are hardcoded.** `WeeklyReviewComputer.signals()`
(`WeeklyReviewComputer.kt:70`) returns a fixed list of exactly five `SignalSkeleton`s (weight,
waist, adherence, strength, recovery). Adding/removing a signal means editing this method; there is
no data-driven signal registry. (Note: the audit's claim that `SEEN_AWAIT_TIMEOUT_MS=5000` is
"inline" is **not accurate** — it is already a named constant in a companion object at
`WeeklyReviewViewModel.kt:126`. No change needed there.)

**Dead `initialSection` param.** `BodyCheckInSheet.kt` threads `initialSection`
(default `CheckInSection.MEASUREMENTS`) through `BodyCheckInSheet` (52) → `BodyCheckInSheetContent`
(75 → 62), but it is explicitly a no-op: the comment at `BodyCheckInSheet.kt:83–84` states "every
section is visible without scrolling." The `CheckInSection` object (33), the param on both
composables, and the kdoc at 43–44 are all dead.

---

## 2. UX improvements

1. **Dashboard cold-start / empty state.** When the user has no logs and all four AI contexts are
   null (`DashboardScreen.kt:226–273`), render a single "Get started" card above
   `MotivationalCard` (274) instead of an empty dashboard. It should offer 2–3 first actions
   (Log a meal → `onOpenFoodLog`; Do your first check-in → body sheet; optionally set targets).
   Drive it off a `state.isNewUser`/`state.hasAnyData` flag computed in `DashboardViewModel`
   (it already derives `last7DaysCalories`, so emptiness is cheap to detect). Use `FrostedCard`
   (memory: prefer solid FrostedCard over faint NeutralCard for readability), `AppType` text, and
   `LiquidPrimaryButton`/`LiquidActionButton` — no bespoke layout.

2. **Per-chart empty states everywhere.** Progress already has `NoDataLabel` inside
   `FeaturedChartCard`/`ShortChartCard` (`ProgressScreen.kt:178,193`). `SevenDayChartCard` has no
   equivalent — when `days` is empty the chart and macro row render as zeros. Add the same
   "log to see your trend" fallback so the cards degrade gracefully rather than showing flat zeros.

3. **Clearer trend storytelling.** Trend is currently a bare signed number (kg/wk, cm/wk) in stat
   tiles and the Weekly Review signal list (`WeeklyReviewComputer.kt:74–80`). Add a one-line plain
   reading next to it — e.g. "−0.4 kg/wk · on pace to lose ~1.6 kg this month" — derived in the
   ViewModel, shown via `AppType.cardSubtitle`. Tie the direction arrow to whether the trend is
   *good for the user's goal* (loss-vs-gain), not just its sign.

4. **Range selector consistency.** Progress has a working `7/14/28d` `RangeSelector`
   (`ProgressScreen.kt:126`). The Dashboard's `SevenDayChartCard` is hardwired to 7 days. Either
   (a) keep Dashboard fixed at 7 (it is a glanceable summary) and document that as intentional, or
   (b) if a range toggle is wanted on Dashboard, reuse the *same* selector component (see §3) so
   the two screens never drift in look. Recommended: keep Dashboard fixed, add range only on
   Progress where deep analysis belongs.

---

## 3. UI improvements

1. **Extract a shared chart-card component** under `ui/component/charts/` so Dashboard and Progress
   stop re-implementing card chrome. Proposed surface:
   - `ChartCard(title, currentValue, unit, trendLabel, trendIsGood, values, height, variant, …)` —
     wraps `SparklineChart`, owns the header (replacing both `ProgressScreen.ChartHeader:272` and
     the inline header in `SevenDayChartCard:617–658`), the scrub state, the trend badge, and the
     `NoDataLabel` fallback. `variant` picks Frosted (featured) vs Neutral (mini/short).
   - Migrate `FeaturedChartCard`/`ShortChartCard`/`MiniChartCard` and `SevenDayChartCard`'s upper
     half onto it. `SevenDayChartCard`'s macro row stays dashboard-specific (it is not a chart).
   - This is a *consolidation*, not new component proliferation (memory: reuse the existing glass
     library; only add a composable when nothing fits — here nothing shared fits, so one new
     `ChartCard` replacing four hand-rolled ones is justified).

2. **Promote the range selector to a shared component.** Move `RangeSelector`
   (`ProgressScreen.kt:126`) into `ui/component/` (or reuse `GlassSegmentedToggle` from the design
   system, which is the sanctioned glass pill toggle) so any future range control matches.

3. **Fix design-system violations.** Replace the hardcoded `fontSize`/`fontWeight`/`letterSpacing`
   in `SevenDayChartCard` (`DashboardScreen.kt:625–628`, `683–685`) with `AppType` tokens (the
   scrub header maps to `statValueSmall`/`cardTitle`; day labels to `metaLabel`). Replace the raw
   `.clip().background().border()` card in `MiniChartCard` (`ProgressScreen.kt:213–220`) with
   `NeutralCard`. Do these together with the §3.1 extraction so the new shared component is
   design-system-clean from the start.

---

## 4. Data / model improvements

1. **Unify insight context building.** Introduce a single `InsightContextBuilder` (in `ai/` or a new
   `domain/insight/`) that owns construction of the per-feature contexts currently scattered across
   `ProgressInsightMapper.kt`, `PatternInsightMapper.kt`, `ui/today/RecoveryInsightMapper.kt`,
   `ui/today/RestOfDayInsightMapper.kt`. Keep the per-feature context data classes; centralize the
   *assembly* (shared trend math, shared sufficiency thresholds, consistent rounding). This removes
   the divergence noted in §1 and gives one place to evolve.

2. **One trend implementation.** Make `ProgressInsightMapper.trendPerWeek` (`ProgressInsightMapper.kt:26`)
   delegate to `TrendCalculator.trendPerWeek` (`TrendCalculator.kt:24`) instead of the naive
   first-to-last delta, so the regression is the single source of truth. (Requires mapping
   `List<Float>` series to `MeasurementPoint`s with real dates — the ViewModel has the dates.)

3. **Optional trend smoothing.** Wire `MovingAverage` (`MovingAverage.kt`) into the trend path as an
   opt-in pre-smoothing step before regression, to reduce day-to-day scale noise on weight/waist
   (e.g. 7-point MA feeding `trendPerWeek`). Keep it behind a parameter so existing tests/behavior
   are unchanged by default; this directly addresses the "noise defuser" insight's intent.

4. **Streak memoization.** `StreakRepository.streaks()` (`StreakRepository.kt:28`) rebuilds the full
   window on every emission. Cheap win: cache the last computed `Streaks` keyed by a lightweight
   signature of the inputs (qualifying-day sets + today + step goal) and skip recompute when
   unchanged. Bigger win (only if profiling shows it matters): incrementally extend the streak for
   the today delta rather than re-sorting all history in `StreakCalculator.compute`
   (`StreakCalculator.kt:21`). Treat as scale-prep, not urgent.

5. **Body schema rigidity — note, don't act yet.** `DailyLogEntity`'s `date` primary key
   (`DailyLogEntity.kt:9`) forecloses multiple check-ins per day. This is a *latent* constraint:
   no current feature needs intra-day entries. Document it as a known limit. A real fix is a schema
   migration (see §8) and should wait until a feature actually demands it — do not migrate
   speculatively.

6. **Remove the dead `initialSection` param.** Delete the param from `BodyCheckInSheet` (52) and
   `BodyCheckInSheetContent` (75), the pass-through at line 62, the `CheckInSection` object (33),
   and the kdoc lines 43–44 and 83–84. Verify the only callers don't pass it (grep
   `initialSection`/`CheckInSection` across `ui/`). Pure dead-code removal.

7. **Weekly Review signal registry (optional).** If signals are expected to grow, turn the
   hardcoded list in `WeeklyReviewComputer.signals()` (`WeeklyReviewComputer.kt:70`) into a
   data-driven list of `(id, label, valueExtractor, directionExtractor)` descriptors. Low priority
   — five signals is manageable; only worth it if more are planned.

---

## 5. AI opportunities

Respect the constraints in `docs/ai-coach.md`: the on-device path is a 2B model with limited tool
iterations; **never add a tool for static/session-invariant data**, and keep rules non-overlapping.
The insight-card path (`GemmaInsightCoordinator`, single-turn, no tools) is the right vehicle for
everything below — these are read-only one-liners, not coach tools.

1. **Richer trend insight via the unified context (§4.1).** Once trend math is unified and
   smoothing is available, the `ProgressInsightContext`/`PatternInsightContext` can carry a
   *smoothed* trend plus a sufficiency flag. This lets the insight card say "your 14-day weight
   trend is real, not scale noise" instead of reacting to a single-day blip — feeding the model
   better facts without adding a tool call.

2. **Cross-metric surfacing.** The dashboard already has a `crossMetricContext`
   (`DashboardScreen.kt:244`) and a "Coach noticed a link" card. Strengthen the *inputs* (e.g.
   adherence↔weight-trend, sleep↔soreness correlations) computed deterministically in the unified
   builder, and pass the pre-computed correlation as a fact. The model phrases it; it does **not**
   compute it. This keeps the 2B model on its strength (one-sentence phrasing of a given fact).

3. **Weekly Review briefing already cloud-backed.** `WeeklyReviewViewModel` routes through a cloud
   `briefingFor` (`WeeklyReviewViewModel.kt:81`) — that path can absorb richer signals freely.
   The on-device insight cards must stay lean.

**Guardrail:** all AI additions here are *fact enrichment* for existing single-turn cards. Do not
introduce new coach tools, do not pre-fetch more into the system prompt, and do not add rules that
overlap the existing today/yesterday/weekly split.

---

## 6. Quick wins

- Remove the dead `initialSection` param and `CheckInSection` object (§4.6).
- Fix the hardcoded `fontSize`/`fontWeight` in `SevenDayChartCard` (§3.3, `DashboardScreen.kt:625–628,683–685`).
- Swap `MiniChartCard`'s raw card for `NeutralCard` (§3.3, `ProgressScreen.kt:213`).
- Add the `NoDataLabel` empty-state to `SevenDayChartCard` (§2.2).
- Add a streak-result memo cache keyed by input signature (§4.4, shallow version).

## 7. Medium improvements

- Dashboard cold-start "Get started" card + `hasAnyData` flag in `DashboardViewModel` (§2.1).
- Extract the shared `ChartCard` component and migrate all four chart cards onto it (§3.1).
- Promote `RangeSelector` to a shared component / `GlassSegmentedToggle` (§3.2).
- Unify trend math: `ProgressInsightMapper` → `TrendCalculator.trendPerWeek` (§4.2).
- Trend storytelling line in stat tiles + Weekly Review signals (§2.3).
- Optional `MovingAverage` smoothing in the trend path (§4.3).

## 8. Bigger refactors

- **Shared charting layer.** Beyond the single `ChartCard` (§3.1), pull header/scrub/trend-badge/
  no-data into a small `ui/component/charts/` module so Dashboard, Progress, and any future
  analytics screen consume one charting vocabulary. This is the durable fix for the duplication;
  do it incrementally behind §3.1 rather than as a big-bang rewrite.
- **Unified `InsightContextBuilder`** (§4.1) — consolidating the four mappers is a multi-file change
  touching `ai/` and several ViewModels; stage it after the trend-math unification so the shared
  builder has one trend implementation to call.
- **Multi-check-in body schema (only if justified).** Migrating `DailyLogEntity` off a `date`
  primary key to a `(date, id)` or timestamped row (schema v8 → v9, plus DAO/repository/UI changes)
  is the real fix for §4.5. **Do not do this without a concrete feature** (e.g. morning/evening
  weigh-ins). It is the largest item here and the least currently-needed.

## 9. What to avoid for now

- **Do not migrate `DailyLogEntity`** speculatively — no feature needs intra-day check-ins today
  (§4.5/§8). The PK change ripples through Room migration, DAOs, repositories, body history UI, and
  trend inputs for zero current payoff.
- **Do not add new AI coach tools** or expand the system prompt for analytics — the 2B model's tool
  budget is precious (`docs/ai-coach.md`). Enrich facts for existing single-turn insight cards
  instead (§5).
- **Do not** "fix" `SEEN_AWAIT_TIMEOUT_MS` — it is already a named companion constant
  (`WeeklyReviewViewModel.kt:126`); the audit note was inaccurate.
- **Do not** rewrite the streak engine for performance now — memoize first (§4.4); only go
  incremental if profiling at real data sizes shows a problem.
- **Do not** add a range selector to the Dashboard summary unless explicitly wanted — keep it the
  glanceable 7-day view and reserve ranges for Progress (§2.4).

## 10. Suggested implementation order

1. **Quick wins, isolated:** delete dead `initialSection` (§4.6); fix `SevenDayChartCard`
   design-system violations (§3.3); `MiniChartCard` → `NeutralCard` (§3.3); add `SevenDayChartCard`
   no-data state (§2.2). Independent, low-risk, no behavior change.
2. **Dashboard cold-start** (§2.1) — add `hasAnyData` to `DashboardViewModel`, render the
   "Get started" `FrostedCard`. Verify on a fresh install (memory: build, then hand to user for
   visual verification — don't drive the emulator unless asked).
3. **Extract shared `ChartCard`** (§3.1) and migrate Progress's three cards + `SevenDayChartCard`'s
   header onto it; promote `RangeSelector` (§3.2). Build + verify each screen renders unchanged.
4. **Unify trend math** (§4.2): point `ProgressInsightMapper` at `TrendCalculator`; add tests
   pinning regression output. Then layer in optional `MovingAverage` smoothing (§4.3).
5. **Unified `InsightContextBuilder`** (§4.1) once trend math is single-sourced; migrate the four
   mappers. Re-verify each insight card still fires.
6. **Streak memoization** (§4.4) — shallow signature cache.
7. **AI fact enrichment** (§5) on top of the unified context — smoothed trend + cross-metric facts
   into existing single-turn cards.
8. **Deferred / only-if-justified:** body-metrics schema migration (§8), Weekly Review signal
   registry (§4.7).

Build/verify per step with `./gradlew :app:compileDebugKotlin` (type-check),
`./gradlew :app:testDebugUnitTest` (domain/ViewModel tests), then hand UI changes to the user for
visual confirmation one screen at a time.
