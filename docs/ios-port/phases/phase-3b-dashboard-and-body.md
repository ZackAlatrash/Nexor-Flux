# iOS Phase 3b — Dashboard, Body/Recovery, streaks, charts, rebalance

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** *See how you are doing.* 3a made logging real; 3b makes the numbers mean something —
the Dashboard, the Body/Recovery tab, streaks, the chart kit, and the whole Weekly Rebalance
surface set.

**Architecture:** One `@Observable` model per screen (D19), plus **one shared streak pipeline** and
**one shared chart kit** that four screens consume. Every chart is hand-drawn SwiftUI (D24).

**Tech Stack:** Swift 6.3.2, Xcode 26.5, iOS 26.0, SwiftUI, GRDB 7.11.1, Swift Testing.

**One repo.** Everything lands in `~/Desktop/RecompTracker-IOS`, branch `phase-3b-dashboard-and-body`
(off `phase-3a-food-library`, which is unmerged). The Android repo receives **documentation only** —
see D27.

---

## 🔴 The finding that sizes this phase

Three research passes resolved **every** `domain.*` symbol the Dashboard, Body, streak and rebalance
clusters import. **41 of 41 are already in `shared/src/commonMain` and already exported in
`Shared.h`.** `AdjustmentEngine`, `AdherenceCalculator`, `TrendCalculator`, `StreakCalculator`,
`PlanHistory`, `ActivitySummary`, `EffectiveTargets`, the entire `RebalanceEngine` — all of it is
sitting in the framework the app already links, unconsumed.

**There is no domain port in 3b.** What remains is ~190 lines of pure Kotlin that never made it into
`:shared` (listed in Task 3), plus orchestration and views.

That is why this plan is task-dense but not month-long. **Do not reimplement a single engine.** If
you find yourself writing adherence, trend, streak-gap or rebalance-sizing arithmetic in Swift, stop
— it already exists, call it.

---

## Decisions taken for this phase

| | Decision | Why |
|---|---|---|
| **D24** | **Every chart is hand-drawn SwiftUI (`Canvas`/`Path`/shapes). Swift Charts is not linked and will not be.** | The sparkline needs a left-to-right clip reveal, dots that pop as the reveal passes them, a 3-stop gradient stroke and a 3-layer glow terminal dot; the rebalance bars need per-bar *staggered* animation and a divider injected between two groups. Swift Charts models none of these and fights all of them. `WeekStrip.swift` already proves the hand-drawn path is cheap in this codebase, and it is the house style. |
| **D25** | **One `StreakModel` and one streak fetch**, owned by the chart/streak kit rather than by any screen. | The streak card renders on Dashboard, Body, Food Log and Train. Two implementations would disagree, and the rules (see Task 3) are subtle enough that the disagreement would be invisible. |
| **D26** | **Rebalance ships with its deterministic copy in 3b**, not deferred to Phase 5. | `RebalanceCopyService` is decoration: `RebalanceViewModel` seeds `RebalanceCopyPromptBuilder.fallback(...)` **synchronously** and only then launches a job that may replace it. The fallback is the shipping string. Phase 5 becomes a one-line swap. The AI *badge* and the "Generating" edge glow stay out — an AI badge on a screen with no AI is a lie. |
| **D27** | **`buildStreaks` is ported to Swift, not moved into `:shared`** — despite `:shared` being the architecturally correct home. | Standing rule 1 says every phase after 0 only adds files under the iOS repo and is parallel-safe with Android work, and the user runs concurrent Android sessions. Moving it is a cross-repo change to a module both platforms build. **Recorded as a follow-up**, not done here. The cost is 73 lines of duplication and a drift risk; the mitigation is that the Swift port carries its own tests transcribed from the Kotlin ones. |

Append these to `docs/ios-port/decisions.md` when the phase lands.

---

## 🔴 Screenshots

3a's most valuable agent output was, every single time, *"here is how the screenshot differed from
your brief."* Six structural corrections came from it.

**No 3b screenshots existed when this plan was written.** They have been requested. Check
`~/Desktop/RecompTracker-IOS/screenshots/` before building any view — anything named `1x-*` is 3b.

**If the screenshot for a screen is absent, say so in your report and build from the Kotlin**, the way
Task 10 and 13 of 3a did for the portion sheet and reconcile banner. Do not silently invent layout,
and do not block.

---

## Context you need

Read, in order:
1. `docs/ios-port/STATUS.md`
2. `docs/ios-port/decisions.md` — **D6** (dates as strings), **D14** (Kotlin name qualification),
   **D15–D19** (Phase 2 conventions), **D20–D23** (Phase 3a), **D24–D27** above
3. `docs/ios-port/phases/phase-3a-food-library.md` — its *Established facts* block is still current

### Established facts — do NOT rediscover

- Tests live **flat** in `RecompTracker/RecompTrackerTests/`. Buildable folders mean new source and
  resource files need **no `project.pbxproj` edit**. Never edit it.
- `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`. Persistence types are `nonisolated`, **and so are
  their extensions** — `nonisolated` does not propagate.
- 🔴 **Never put `didSet` on an `@Observable` stored property.** It compiles with no diagnostic and
  then crashes the test runner: `@Observable` rewrites the setter into `withMutation(keyPath:)`, the
  observer fires inside it, and the handler re-enters the registrar. Use explicit get/set over a
  private stored property.
- 🔴 **Never spell a GRDB column with its Swift property name.** `meal_slots.sort_order` is the only
  snake_case column in the schema; `Column("sortOrder")` compiles and throws at runtime.
- ⚠️ **A `@MainActor` model needs `@MainActor` on its test suite**, and inside a `@MainActor async`
  test both `db.reader.read { }` and `db.writer.write { }` resolve to GRDB's **async** overloads —
  they need `await`.
- ⚠️ **SwiftUI localises `Text("\(anInt)")`** — `2550` renders as "2.550" on a Dutch locale. Use
  `Text(verbatim:)` for every bare number. This phase is nothing but numbers.
- ⚠️ `Int(someDouble)` **traps** on overflow. Use `Int(exactly:)`.
- ⚠️ `#expect` cannot appear inside a throwing closure; `#expect(cond, "a" + b)` does not compile.
- ⚠️ `Section("t") { } footer: { }` does not compile — use `Section { } header: { } footer: { }`.
- 🔴 **Never run `git stash`.** `refs/stash` is shared across worktrees.
- With `import Shared`, Kotlin types appear **without** the `Shared` ObjC prefix, and some are nested
  (`MealImpact.Result`, not `MealImpactResult`). Check the generated header, never guess.
- `xcodebuild test` here **intermittently reports a partial run** ("30 tests" + `TEST FAILED`) with no
  failure named. Simulator race — run it again. A real failure always names the test.

### Build and test

```bash
cd ~/Desktop/RecompTracker-IOS
xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -destination 'platform=iOS Simulator,name=iPhone 17' 2>&1 \
  | grep -E "error:|warning:|Test run with|✘|TEST (SUCCEEDED|FAILED)" | grep -v AppIntents
```

Baseline entering 3b: **468 tests**. Also build `-configuration Release` at the end of every task —
Phase 2 found isolation warnings that appear only there.

### Already done — do not redo

**`JSONStore.changes()` landed before this plan** (commit `a15187d`). It emits the current value then
every write, shaped like `AppDatabase.observe`. It was recorded as owed to 3c but is prerequisite
here: the Dashboard reads plan targets, rebalance state and the profile from `JSONStore`, and
rebalance state changes *while the screen is open*.

---

# PART A — Prerequisites (no UI)

Nothing in Parts B–E compiles until these land. Do them first and in order.

## Task 1: The query layer's gaps

**Files:**
- Create: `Persistence/Queries/PlanVersionQueries.swift`
- Create: `Persistence/Queries/BodyQueries.swift`
- Modify: `Persistence/Queries/DailyLogQueries.swift`
- Modify: `Persistence/Queries/TrainingQueries.swift`
- Create: `RecompTrackerTests/PlanVersionQueryTests.swift`, `BodyQueryTests.swift`

🔴 **`plan_versions` has no Swift reader at all.** The record exists and backup restore writes rows,
but nothing reads them — `FoodLogModel` resolves targets straight from `PlanPreferencesStore`,
bypassing the ledger. `PlanHistory.resolve` is what turns that ledger into per-day targets, and every
number on the Dashboard depends on it. **`PlanHistory` currently has zero Swift call sites.**

- [ ] **Step 1: Failing tests first.** Cover: versions come back in effective-date order; a day
      before the first version resolves to the fallback; `PlanHistory.resolve` over real rows agrees
      with the ledger. Then for `DailyLogQueries`: `all()` ordering, `byDate`, and each new updater
      touching **only** its own column.
- [ ] **Step 2: `PlanVersionQueries`** — `all(d:)` ordered by effective date, and whatever
      `PlanHistory.resolve` actually wants. **Read the Kotlin signature in `Shared.h` first.**
- [ ] **Step 3: `DailyLogQueries` additions** — `all(d:)`, `byDate(d:_:)`, `updateWaistSkinfold`,
      `updateSteps` (with `stepsSource`), `updateTrained`, `updateNotes`, and a **whole-row upsert**
      for the check-in save path.

      🔴 That file opens with a comment saying never to collapse the per-column updaters into a
      whole-record save — it exists because Android's whole-row save clobbered concurrent coach
      writes (P1-18 / P2-7). **Both paths must exist and stay distinct.** Add the upsert *beside*
      the updaters with a comment saying which is for what: the check-in sheet saves the whole form
      in one go; everything else touches one column.
- [ ] **Step 4: `TrainingQueries.completedSessionDates(d:since:)`** — a lean `[String]` of dates.
      `completedSessionsSince` exists but loads the whole session graph (exercises + sets); the
      workout streak needs dates only, on every recompute.
- [ ] **Step 5: `BodyQueries`** — `WeeklyReview` upsert (the Dashboard writes one as a side effect of
      state, see Task 11) and `LiftPerformance` reads for `TrendCalculator.performanceTrend`.
- [ ] **Step 6:** Run, Release build, commit.

```bash
git commit -m "feat(persistence): plan-version, body and lean training queries"
```

## Task 2: The four target resolutions, named and tested

**Files:**
- Create: `DesignSystem/../Domain/TargetResolution.swift` (pick a home outside `Features/FoodLog/`)
- Move: `MacroSum` out of `Features/FoodLog/FoodLogModel.swift`
- Move: `PlanTargetsSnapshot` + `calorieStatus` out of `Features/FoodLog/DayCalorieSummary.swift`
- Create: `RecompTrackerTests/TargetResolutionTests.swift`

🔴 **This is the task most likely to produce silently wrong numbers, and the one least likely to fail
a test you didn't deliberately write.** One screen resolves targets **four different ways**:

| Resolution | Used by | Rule |
|---|---|---|
| **Base** | `AdjustmentEngine` | `PlanHistory.resolve(versions, dates)` — the ledger, no rebalance |
| **Effective** | every *display* surface — adherence tile, in-zone-7, today's ring | `EffectiveTargets.resolveAll(...)` — rebalance-reduced |
| **Union zone** | the **calorie streak** only | `EffectiveTargets.unionZone(...)` — the *lenient* union, so a rebalance can widen the band but never break a streak |
| **Effective step goal** | the steps **ring display** only | `EffectiveTargets.effectiveStepGoal(...)` — the steps *streak* still judges against the base goal |

`DashboardViewModel.kt:233-234` and `:129-131` explain the first split: a 2–5 day rebalance blip must
never perturb the long-horizon recomp verdict. `StreakRepository.kt:137,143-144` cover the other two.

- [ ] **Step 1:** Write these as **four explicitly named Swift functions with doc comments quoting
      the Kotlin line that justifies each**, and test each against the others — in particular, a test
      that a rebalance changes the effective target but leaves the base one alone, and a test that a
      rebalance widens the union zone rather than narrowing it.
- [ ] **Step 2:** Move `MacroSum`, `PlanTargetsSnapshot` and `calorieStatus` somewhere both
      `Features/FoodLog/` and `Features/Dashboard/` can import without one feature importing another.
      Pure moves — no behaviour change, existing tests must pass untouched.
- [ ] **Step 3:** Run, Release build, commit.

## Task 3: The streak pipeline (D25)

**Files:**
- Create: `Features/Streaks/StreakModel.swift`, `StreakRules.swift`
- Create: `RecompTrackerTests/StreakRulesTests.swift`, `StreakModelTests.swift`

`StreakCalculator.compute` is in `:shared` and is 27 lines of pure gap arithmetic — **call it, do not
rewrite it.** What is *not* shared is `buildStreaks` + `recentFlags` + `recentMarks`
(`app/.../data/repository/StreakRepository.kt:118-190`, ~73 lines) — and that is where every
interesting rule lives. Port it to Swift (D27).

🔴 **Five rules that are easy to get wrong and hard to notice.** Test each explicitly:

1. **`current` counts calendar days *spanned*, not hits.** With `restDays = 2`, a Monday and a
   Thursday workout give `current = 4`, not 2. The obvious "count consecutive qualifying days" loop
   is wrong and only wrong when rest days are involved — which is always, but plausibly.
2. **A trailing grace period keeps a streak alive without incrementing it.** With `maxGap = 1`,
   yesterday qualifying and today not still yields `current = 1`, not 0. A naive
   `if !qualifies(today) { return 0 }` breaks the app's most visible number every morning before
   the user logs.
3. **Calorie qualifying days use the lenient `unionZone`** (Task 2), and the set is **empty when
   there are no plan versions**.
4. **Steps judge against the base goal**, always; the rebalance boost is display-only. The set is
   **empty when the goal is null or ≤ 0**.
5. **The 7-day REST/MISS strip is independent of the chain math.** A non-hit day is REST only when
   `restDays > 0` **and** a qualifying day exists within `restDays` on *both* sides. Trailing rest
   days after the last workout render MISS even though the streak is alive.

Also: `last7` / `last7Marks` are the repository's job, not the calculator's — `StreakCalculator`
always returns them empty.

- [ ] **Step 1:** Transcribe the Kotlin tests first — `StreakBuilderTest` (6),
      `StreakRepositoryRebalanceTest` (2), `StreakRepositoryZoneTest` (1). They are the executable
      spec, and the rules above are exactly what they pin. Find them in the Android repo under
      `app/src/test/`.
- [ ] **Step 2: `StreakRules`** — pure functions, no database, no actor. Qualifying-day sets for
      workout / calorie / steps, plus the 7-day marks.
- [ ] **Step 3: `StreakModel`** — `@MainActor @Observable`, D19 shape.

      🔴 **The fetch reads four tables and two preference stores.** Android uses a six-way `combine`;
      GRDB's `ValueObservation` observes *one* fetch closure. Use **one `database.observe { }`
      returning a single `Sendable` payload struct** (the `DayPayload` idiom in
      `FoodLogModel.swift:57`), plus `JSONStore.changes()` for the two stores. Do **not** build a nest
      of merged `AsyncSequence`s.
- [ ] **Step 4:** Run, Release build, commit.

---

# PART B — The chart kit

## Task 4: `ChartDefaults` and the motion gate

**Files:** Create `DesignSystem/ChartDefaults.swift`

Android centralises its animation tokens in `ChartDefaults.kt` and reads a reduce-motion flag at
**13 sites**. Build both before any chart, not after.

- [ ] Tokens: `drawIn = 1200ms` (`FastOutSlowIn` ≈ `.easeInOut`), `barRise = 800ms`,
      `barStagger = 60ms`, `dotPop` spring, stroke 1.8, dot radius 4.5, glow 11, halo 16,
      grid alpha 0.04, zone dash alpha ≈ 0.31, dash `[3, 4]`.
- [ ] A `@Environment(\.accessibilityReduceMotion)`-backed helper. Android snaps every animated
      value to its end state when animations are off; match that.

## Task 5: `SparklineChart` — 🔴 the hardest thing in the phase

**Files:** Create `DesignSystem/Charts/SparklineChart.swift`, `RecompTrackerTests/SparklineTests.swift`

Ported from `app/.../ui/component/charts/SparklineChart.kt` (266 lines). **Read it before writing
anything.** Used by Dashboard (full config, with scrub), Body ×2 (height 64, glow, no scrub), and
Progress ×3 in 3c — so **its API is the contract to get right first**. Keep Android's shape:
`values, height, showGlowDot, showScrubber, zoneLow, zoneHigh, onScrubValue, onScrubIndex`.

Draw order, each with its trap:

- [ ] **Grid** — 4 horizontal lines at 0.25/0.5/0.75/1.0, 4% alpha. Trivial.
- [ ] **Zone band + dashed bounds.** `WeekStrip.planOverlay` (`WeekStrip.swift:99-108`) already does
      exactly this shape — reuse the technique.
- [ ] **Cubic-bezier path.** Control points at the **horizontal midpoint**, each at its own
      endpoint's y. 🔴 **Copy the formula exactly** — this is not Catmull-Rom, and
      `.interpolationMethod(.catmullRom)` produces a visibly different curve.
- [ ] **Clip reveal.** 🔴 Use a **mask**, not `Path.trim`. `trim` reveals by *path length*, Android
      clips by *x* — visibly different on steep segments.
- [ ] **Area fill** — same path closed to the baseline, vertical gradient, fading in only after the
      reveal passes 50%.
- [ ] **3-stop gradient stroke.**
- [ ] **Per-dot scale-in** — `dotScale` is a pure function (threshold = `dotX/totalWidth` clamped to
      0.95, then ramps over the next 0.08 of progress). **Port it and test it verbatim.**
- [ ] **3-layer glow terminal dot**, appearing in the last 10% of the reveal.
- [ ] **Scrub.** 🔴 **The single riskiest item in 3b.** This lives inside a vertically scrolling
      parent on the Dashboard. A `DragGesture(minimumDistance: 0)` on a child will either eat the
      scroll or lose the first points. Expect to iterate on `.simultaneousGesture` vs
      `.highPriorityGesture`, and **verify scrolling still works** — it is only truly checkable on
      device. Budget real time for this alone. `nearestPointIndex` is pure; test it.
- [ ] **`calendarSparkline`** (`TodayViewModel.kt:344-364`, 21 lines, 7 Kotlin tests) — 🔴 **port
      this before the drawing code.** It interpolates gaps so a 3-day gap occupies 3× the width.
      Without it the curve is shaped right and spaced wrong.
- [ ] Run, Release, commit.

## Task 6: The small components

**Files:** Create `DesignSystem/Charts/StreakGoalRing.swift`, `ScoreBar.swift`,
`StreakWeekStrip.swift`, `StreakCoin.swift`

All small, all hand-drawn, none a chart in the Swift Charts sense.

- [ ] **`StreakGoalRing`** — two arcs on a 74pt box, 7pt round-cap stroke, progress from −90°.
      Colour flips to `#4ADE80` when the goal is met. `Circle().trim(from:to:)` + `.rotationEffect`.
      ⚠️ Android's **does not animate**; SwiftUI gives you `.animation` free. Taking it is an
      improvement — say in the report that you did.
- [ ] **`ScoreBar`** — 5pt track + fractional gradient fill, three-stop colour ramp on score
      (≤4 red→orange, ≤6 amber, else accent), animated.
- [ ] **`StreakWeekStrip`** — 7 circles: HIT filled accent, REST a 1.5pt accent ring, MISS filled
      `cardBorder`.
- [ ] **`StreakCoin`**, `streakIcon`, `streakLabel`.
- [ ] Add `StreakFlameColor` (`#FB923C`) and `StreakGoalMetColor` (`#4ADE80`) to `StatusColor` — they
      are fixed verdict colours, theme-independent by design.
- [ ] Run, Release, commit.

---

# PART C — The Body tab

## Task 7: `BodyModel` and the check-in write path

**Files:** Create `Features/Body/BodyModel.swift`, `Features/Body/CheckInDraft.swift`,
`RecompTrackerTests/BodyModelTests.swift`

- [ ] Port `latestCheckIn` (9 lines, 5 Kotlin tests) and the 7-day weight/waist deltas (~30 lines,
      🔴 **no Kotlin tests exist — write them**).
- [ ] 🔴 **Port `resolveSavedSteps` / `reconcileSteps` / `StepsSource` now, dormant.** In 3b every
      write is `MANUAL` and this appears to do nothing. It exists to stop a blind save stamping
      synced HealthKit steps as `MANUAL` and freezing them — the exact bug it was written to fix.
      Skipping it means reintroducing that bug the day Phase 4 lands, and it will be hard to
      attribute. Test it with a simulated `HEALTH_CONNECT` row.
- [ ] The save path: validate steps through `:shared`'s `validateStepsInput` (**Invalid must set a
      message and write nothing, keeping the sheet open**), read the existing row, resolve steps,
      then **one whole-row upsert**. Scores coerce to 1...10, notes trimmed.
- [ ] Run, Release, commit.

## Task 8: The Body screen — 🖼️

**Files:** Create `Features/Body/BodyScreen.swift`

From `BodyRecoveryScreen.kt` (548), in order: header · `MetricsHeroCard` (two metric columns split by
a rule, each with a 36pt number, a trend line and a 64pt sparkline) · `StreakGoalRing` ·
**[gap — the recovery insight card is Phase 5]** · `MetricTilesCard` (2×3 tap-to-edit tiles) ·
`RecoveryBandCard` (3 `ScoreBar`s) · history row · floating "Log today" / "Edit check-in" button.

⚠️ The Body ring passes the **base** step goal and the **editable form field**; the Dashboard ring
passes the **boosted** goal and the **persisted** value. Two screens, one component, different
inputs — **at least partly deliberate.** Port as-is and flag it rather than normalising.

⚠️ The Phase 5 insight card renders nothing when disabled on Android, so the layout already works
without it. Leave the gap; do not restyle around it.

- [ ] Build, hand off for the visual pass, commit.

## Task 9: The check-in sheet — 🖼️

**Files:** Create `Features/Body/CheckInSheet.swift`

From `BodyCheckInSheet.kt` (152). Measurements (weight/waist, sleep/steps, skinfold) · divider ·
Recovery (3 steppers) · divider · Activity (training toggle, notes, error, save).

Two behaviours to preserve, both of which were bug fixes:
- 🔴 **The sheet does not self-dismiss.** Only a successful save closes it (P1-11).
- 🔴 **It does not scroll** — Android made it a plain `Column` because a nested scroll fought the
  sheet's drag. On iOS `appSheet()` sizes to content, which is the same intent; **do not** wrap it in
  a `ScrollView` "to be safe".

Reuse `LabelledField` for all five text inputs. A `ScoreStepper` equivalent does not exist — build one.

- [ ] Build, hand off, commit.

## Task 10: Streak stats screen — 🖼️

**Files:** Create `Features/Streaks/StreakStatsScreen.swift`

From `StreakStatsScreen.kt` (90) — three detail cards, each a coin + 36pt count + flame + label,
right-aligned best, a week strip, and a "set a step goal in Profile" hint when the goal is null.

- [ ] Build, hand off, commit.

---

# PART D — The Dashboard

## Task 11: `DashboardModel`

**Files:** Create `Features/Dashboard/DashboardModel.swift`,
`RecompTrackerTests/DashboardModelTests.swift`

The heaviest orchestration in the app so far. Android is a `todayFlow().flatMapLatest { }` over a
five-slot `combine` (the fifth is a nested `combine` because the top level was full), debounced
300 ms.

- [ ] 🔴 **Get Task 2's four target resolutions right here.** Base feeds `AdjustmentEngine`;
      effective feeds every displayed number. Backwards produces numbers no test will catch.
- [ ] Call the engines — `PlanHistory.resolve`, `EffectiveTargets.resolveAll`,
      `TrendCalculator.trendPerWeek` / `performanceTrend` / `recoveryTrend`,
      `AdherenceCalculator.calculate` **twice** (base and effective), `AdjustmentEngine.evaluate`.
      **Every one is in `:shared`.**
- [ ] 🔴 **`persistWeeklyReview` is a write-on-read.** Android saves a `weekly_reviews` row as a side
      effect of state emission, memoised on verdict/change, filed under the *selected* day's week
      rather than the wall clock. Easy to drop on the floor during a port and not notice for weeks.
      Port it, and test the memo (a second emission with the same verdict must not write again).
- [ ] Do **not** port the `aiInsightCoordinator` parameter — Android takes it and never uses it.
- [ ] Run, Release, commit.

## Task 12: The Dashboard screen — 🖼️

**Files:** Create `Features/Dashboard/DashboardScreen.swift` + its section views

From `DashboardScreen.kt` (1249). In order: ambient orbs · header with avatar ·
**[gap — coach slot, Phase 5]** · rebalance note card (Task 16) · motivational card ·
**TodayCard** (rebalance ribbon, badge, 36pt calories, progress bar, zone labels, 3 macro bars;
whole card taps through to Food Log) · stat tiles (adherence, trend) · **SevenDayChartCard** (header
that flips to `Wed · 2,140 kcal` while scrubbing, in-zone pill, sparkline, day labels, macro stats
that track the scrubber) · steps ring · training frequency tile · streaks card ·
**[gap — weekly review pill, Phase 5]** / rebalance reopen pill.

⚠️ **The weekly-review pill and the rebalance reopen pill time-share one slot** and must never stack.
The review pill is Phase 5, but **its layout slot is 3b's problem**.

⚠️ Five models, not one — Dashboard, streak, rebalance, plus the two Phase 5 ones that stay out. Each
gets its own observation lifetime.

- [ ] Build, hand off, commit.

## Task 13: The calorie decision screen — 🖼️

**Files:** Create `Features/Dashboard/CalorieDecisionScreen.swift`

`DashboardScreen.kt:1057-1249`. **The cheapest win in 3b: it shares `DashboardModel` verbatim and
needs no new data.** A verdict hero (36pt verdict word, recommended ±kcal/day, reason chips), a
current-targets card and a trend-summary card. Reached from More → Stats, not from the Dashboard.

⚠️ More is 3c, so this screen has **no entry point in 3b**. Wire it behind the Dashboard temporarily
or leave it reachable only from a preview — say which you chose.

- [ ] Build, hand off, commit.

---

# PART E — Rebalance

**Seven surfaces, not two.** The offer overlay and progress detail are the obvious ones; there are
also the note card (**four skins**), the dashboard ribbon, and the minimized reopen pill.

## Task 14: `RebalanceCoordinator` and the deterministic copy

**Files:** Create `Features/Rebalance/RebalanceCoordinator.swift`, `RebalanceCopy.swift`,
`RecompTrackerTests/RebalanceCoordinatorTests.swift`

🔴 **The first stateful, scheduled, mutating thing on iOS.** Every Swift surface so far has been
read-render-write-once. This owns a once-daily gate, two locks, an observer of plan-version changes,
and two in-memory published values.

- [ ] Android uses `Mutex.withLock { suspend }`, which has no direct Swift equivalent under strict
      concurrency. **Use an `actor`** — and note it changes every call site's isolation. Decide
      deliberately and say why in your report.
- [ ] The engine is entirely in `:shared`: `RebalanceEngine.evaluate` / `.reconcile` / `.customize`,
      `EffectiveTargets.planDayInfo`, `RebalancePlanMath.effectiveCalories`. **Call them.** The
      coordinator's whole job is assembling `RebalanceEvaluationInput` from queries and persisting
      the result. It contains **no numbers of its own**.
- [ ] Port `buildOfferWindow` (~27 lines) — 7 history days + `plan.lengthDays` plan days, the offer
      chart's data.
- [ ] 🔴 **Android holds the offer window in memory only.** Kill the app with an offer pending and
      the card comes back with an empty chart. **Decide deliberately** whether to reproduce that or
      rebuild the window from the store, and say which.
- [ ] `RebalanceCopy` — the eight fallback slots **verbatim**, plus the two strings that live in the
      ViewModel rather than the builder (`STARTS_TOMORROW_LINE`, `fullRecoveryLine`). Android asserts
      these in `RebalanceCopyServiceTest`; do not paraphrase. Transcribe that test.
- [ ] Run, Release, commit.

## Task 15: `RebalanceModel`

**Files:** Create `Features/Rebalance/RebalanceModel.swift`, tests

~410 lines on Android and **it is not the chart work** — face derivation over six statuses, the
minimize-reset guard keyed on plan id, the `plan_edited` auto-dismiss, the accepted-late `dayX == 0`
case, and the partial-vs-full recovery line (always populated so the card height never changes when a
dial flips).

- [ ] Reactive over `JSONStore.changes()` — this is what that stream was built for.
- [ ] Run, Release, commit.

## Task 16: The rebalance surfaces — 🖼️

**Files:** Create `Features/Rebalance/{RebalanceOfferSheet,RebalanceProgressSheet,RebalanceNoteCard,RebalanceRibbon,RebalanceReopenPill}.swift`

- [ ] 🔴 **A swipe-down on the offer sheet must call `onMinimize`, not `onDecline`.** Android's
      dialog dismisses to *minimized*; a sheet's natural dismiss gesture is easy to wire to the wrong
      one, and declining is a decision the user did not make.
- [ ] 🔴 **Card-height stability.** Android fights this three ways — `minLines` on two text blocks and
      keying the lever tiles on `Unit` so they never replay — because flipping a dial recomputes the
      plan and the copy changes length. **`appSheet()` measures its content and re-settles the
      detent**, so every dial tap will resize the sheet. Read `AppSheet.swift`'s own warning; this is
      exactly the case it was written about. Solve it (a floor on the text block, or a fixed detent
      for this one sheet) and say what you did.
- [ ] Two dials — use `Picker(...).pickerStyle(.segmented)`. **Do not rebuild Android's 400-line
      custom toggle**; D15 explicitly licenses the native control.
- [ ] The note card has **four skins** (completion/gold, graceful-end, no-adjustment/green, fallback).
      ⚠️ `frostedCard(padding:)` takes no tint override, unlike Android's `FrostedCard` — you will
      need an overload or a bespoke modifier.
- [ ] ⚠️ **`RebalanceRibbon` renders inside the Dashboard's TodayCard** — a hard seam with Task 12.
      Agree the container contract explicitly.
- [ ] No `AiBadge`, no "Generating" edge glow (D26).
- [ ] Build, hand off, commit.

## Task 17: The rebalance viz

**Files:** Create `Features/Rebalance/RebalanceViz.swift`, tests

- [ ] **`WeeklyBarsChart`** — history + plan bars on one scale, three fills (over-target flat orange
      `#F97316`, plan-day accent gradient, normal grey gradient), a 1pt divider injected between the
      last history bar and the first plan bar, a dashed target rule, per-bar staggered rise.
      `WeekStrip.swift` is ~60% of this structurally, including the same `[4, 3]` dash.
- [ ] **`DayDots`** — an `HStack` of circles, three states, full (14pt, with connecting track and
      numeric labels) and mini (8pt, no track, no labels, no pulse) variants.
      🔴 `dotStateFor` is a pure function with a 1-based↔0-based conversion **that was already a
      shipped bug** (P1-13) and has its own Kotlin test. Port the function and the test.
- [ ] **`LeverTiles`** — three tiles, `—` at 42% alpha for a zero value. Entrance animation keyed so
      that changing a dial updates labels **in place** without replaying the slide-in.
- [ ] ⏭️ **Do NOT port `ConvergenceReadout`** (44 lines). It has zero production call sites — only its
      own preview — and the progress overlay documents why it is unused.
- [ ] `formatK` (`1200 → "1.2k"`) — trivial, but test it.
- [ ] Run, Release, commit.

---

# PART F — Verification

## Task 18: Shell wiring, verification, docs

- [ ] Flip `.home` and `.body` off `PlaceholderScreen` in `RootTabView`, update
      `AppTab.isImplemented`, and change the default selection (there is a TODO in
      `RootTabView.swift:11-12` saying to do this in Phase 3).
- [ ] `DebugSampleData` already seeds days, but check it seeds enough **weight/waist history** for the
      sparklines and enough **variety** for the streaks to be non-trivial. Extend it if not — and add
      a test, the way 3a did after the seeder turned out to have no call site at all.
- [ ] Both configurations, zero warnings:
```bash
xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker -destination 'platform=iOS Simulator,name=iPhone 17'
xcodebuild -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' -configuration Release build
```
- [ ] Leak greps, all of which must be silent:
```bash
cd RecompTracker/RecompTracker
grep -rn "\.font(\.system(size:" Features Shell DesignSystem
grep -rn "Color(\.sRGB\|Color(red:" Features Shell
grep -rn "import Charts" .          # D24 — Swift Charts must NOT be linked
grep -rn "CatalogFood\|catalog_foods" Features Shell   # D20 — NEVO stays out
```
- [ ] Record **D24–D27** in `decisions.md`, and the `buildStreaks` move as a follow-up.
- [ ] Update `parity-ledger.md` (Dashboard, Body/Recovery, Streak Stats, Calorie decision, the three
      rebalance overlays, `SparklineChart`, `StreakGoalRing`, the rebalance viz) **and its summary
      counts** — 3a's summary went stale because only the rows were updated.
- [ ] Update `STATUS.md` — phase board, numbers, a session-log entry within its 3–6 line budget, and
      the *Needs visual check* list.

---

## 🔴 Known gap found during execution — the plan ledger is empty on iOS

Task 2 surfaced this and it belongs to **3c**, not here.

`plan_versions` has **no iOS writer**. Android seeds a `1970-01-01` sentinel row
(`PlanHistoryInitializer`) so the ledger is never empty; iOS has no equivalent, and the only thing
that writes rows is a backup restore. So on a fresh install the ledger is empty, `PlanHistory.resolve`
returns an empty map, and **every target resolution takes its fallback path**.

Two consequences:

1. The documented Source-of-Truth guarantee — *"a plan change never re-judges already-logged days"* —
   is **not implemented on iOS at all** yet. Nothing can change targets in 3b (the Plan screen is 3c),
   so nothing is currently wrong; it becomes wrong the moment 3c ships plan editing without also
   writing a version row.
2. Android is **internally inconsistent** about where the fallback goes: the adherence path applies it
   *after* `resolveAll` (unreduced, `DashboardViewModel.kt:246,:315`), the today-ring path *before*
   `resolve` (reduced, `:322`). Android never notices because its ledger is never empty. iOS took the
   **ring's ordering (reduced)** for all paths, because the unreduced one would make a running
   rebalance invisible across the whole Dashboard on a fresh install. Pinned by
   `anEmptyLedgerFallbackIsStillReducedByARunningRebalance`.

**3c must write a plan-version row when the plan changes, and seed the baseline on first run.**

## What 3b deliberately does NOT do

- **No coach slot, no weekly briefing overlay, no recovery insight card** — Phase 5. Their layout
  slots are 3b's problem; their contents are not.
- **No HealthKit.** Steps are manual-entry only until Phase 4, which means the steps streak looks
  dead on a fresh install. `resolveSavedSteps` ships dormant anyway (Task 7).
- **No `ConvergenceReadout`** — dead on Android.
- **No AI badge or edge glow on the rebalance offer** (D26).
- **No Swift Charts** (D24).
- **No `buildStreaks` move into `:shared`** (D27) — recorded as a follow-up.

## Rollback

Additive in the iOS repo on its own branch, off the unmerged `phase-3a-food-library`. The only
modified existing files are `RootTabView`/`AppTab` (Task 18), `DailyLogQueries` and `TrainingQueries`
(Task 1, additions only), and the `MacroSum`/`PlanTargetsSnapshot` moves (Task 2, pure relocation).
The Android repo receives documentation only.
