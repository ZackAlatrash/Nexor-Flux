# Improvement Plan 01 — Steps & Activity

**Section:** Steps & Activity (the weakest, highest-potential pillar)
**App:** Personal Dietitian — `com.zack.recomptracker` · Kotlin / Compose · single-module
**Status:** ✅ ALL PHASES IMPLEMENTED (2026-07-01) on branch `audit/section-improvement-plan`.
Phases 0–7 are committed with unit tests; the debug APK assembles and 757 unit tests pass
(only the live-network `InsightHarnessTest` fails, unrelated). Items needing on-device visual
verification are listed at the very bottom under "Verification still needed".

This plan was written **after verifying every claim against the source tree** (paths + line numbers
below are real as of the audit). Where the original audit brief was wrong, the correction is called
out inline so we don't waste effort re-building something that already exists.

---

## Audit corrections (read first — the brief was partly stale)

The hand-off brief contained a few claims that do **not** match the code. Building from the brief
verbatim would duplicate existing work. Corrections:

1. **A steps goal ring already exists and is already shipped.** `StreakGoalRing(...)` is a real
   `@Composable` in `ui/streak/StreakComponents.kt:304` (frosted card, `Canvas` progress arc, met-color
   swap) and is **already rendered on the Body/Recovery screen** — `ui/today/BodyRecoveryScreen.kt:171`
   wires it with `todayValue = todaySteps`, `goalValue = streakUi.stepGoal`. So "NO UI shows progress
   toward the goal" is **false** for the Body tab. It is true for the **Dashboard** and **Profile**.
2. **The Steps streak is surfaced.** The Dashboard `StreaksCard` renders all three streak rows incl.
   Steps — `ui/dashboard/DashboardScreen.kt:595` (`StreakRow(result = streaks.steps, type = StreakType.STEPS)`).
   `StreakRepository` derives it from `dailyStepGoal` (`data/repository/StreakRepository.kt:84-91`).
   So "the Steps streak exists but is not surfaced" is **half false** — it's surfaced as a row; what's
   missing is a *goal-vs-actual ring on the Dashboard* and *Profile*.
3. **Steps input has validation — partial.** `BodyEditViewModel.kt:85-87` rejects non-integer steps
   ("Steps must be a whole number."). What's missing is an **upper-bound / sanity range** and any
   validation on the quick `BodyCheckInSheet` path (`ui/body/BodyCheckInSheet.kt:114`).
4. **`weeklyGymSessions` is NOT fully dead.** It is *set* in `ProfileScreen.kt:226-229` (a `ScoreStepper`)
   and *read by the AI coach* prompt builders (`ai/GemmaCoachCoordinator.kt:410`,
   `ai/CoachToolsAdapter.kt:74`). What's true: it drives **no UI metric, no trend, no comparison against
   the actual `trained` history**. It's a static profile fact the user sets once and never sees again.
5. **Schema is v13, not v8.** `data/local/RecompDatabase.kt:62` → `version = 13`. (CLAUDE.md's "schema v8"
   line is stale; ignore it for migration numbering — the next migration is **13 → 14**.)
6. **Steps are already in the coach snapshot.** `get_today_summary` already emits `"steps":<n>` and
   `"trained":<bool>` (`ai/CoachToolExecutor.kt:54`). So the AI *can* see steps; what's missing is any
   *activity-specific insight card* or *richer activity framing*.

Net: the pillar is weaker in **plumbing** (no auto-sync, weak source reconciliation, no
training-frequency derivation) than in **surfacing** (rings/streaks mostly exist). This plan
prioritises closing the plumbing gaps and finishing the surfacing on Dashboard/Profile.

---

## 1. Current state & problems

### What exists (verified)
- **Storage.** `steps` is a single nullable `Int?` on `DailyLogEntity` (`data/local/entity/DailyLogEntity.kt:14`),
  one row per day keyed by ISO date string. No timestamp, no source attribution.
- **Manual capture.** Two entry paths write it:
  - `BodyCheckInSheet.kt:114` — quick check-in, plain `GlassInputField("Steps", …, KeyboardType.Number)`,
    **no validation at the sheet level**.
  - `BodyEditViewModel.kt:75/85-97` — full Body edit, parses via `toNullableInt()` and rejects
    non-integers, but no range cap.
- **Health Connect read.** `HealthConnectRepository.readToday(date)` reads steps for the day
  (`readSteps` sums `StepsRecord.count`, `data/health/HealthConnectRepository.kt:79,108-114`), plus weight
  and sleep. History read exists only for **nutrition** (`readHistoricalNutrition`), **not for steps**.
- **Sync trigger.** Only **manual / one-shot**: `SettingsViewModel.syncNow()` (`ui/settings/SettingsViewModel.kt:401`)
  is invoked from the Integrations screen "Sync now" button and once automatically right after permission
  grant (`onPermissionsResult()` → `syncNow()`, line 388). There is **no app-open sync and no periodic
  background sync** — `grep` for `WorkManager` / `androidx.work` returns nothing; it is not even a
  dependency.
- **Reconciliation.** `LogRepository.applyHealthConnectSync` (`data/repository/LogRepository.kt:337-345`)
  uses `steps = existing?.steps ?: result.steps` — i.e. **once any value is in the row (manual OR a prior
  sync), Health Connect can never update it**. A manual 2,000 entered at 9am is never corrected to the
  real 11,000 at 9pm. This is the single biggest correctness bug in the pillar.
- **Goal.** `dailyStepGoal: Int?` lives in `UserProfilePreferences` (`data/preferences/UserProfilePreferences.kt:17`),
  edited in `ProfileScreen.kt:234-237`.
- **Streak.** `StreakRepository`/`buildStreaks` derives a Steps streak: a day qualifies when
  `(steps ?: 0) >= dailyStepGoal` (`StreakRepository.kt:84-91`), `restDays = 0` (strictly consecutive).
  Surfaced as a `StreakRow` on Dashboard and a `StreakGoalRing` on Body/Recovery.

### What's broken / missing / disconnected
- **B1 — HC can never refresh a populated steps value** (`LogRepository.kt:341`). Highest-impact bug.
- **B2 — No automatic sync.** Steps only update if the user opens Integrations and taps "Sync now"
  (`IntegrationsScreen.kt:145` → `viewModel::syncNow`). The pillar is effectively dead for any user who
  connected once and never returns to Settings.
- **B3 — No source attribution.** We cannot tell "manual" vs "Health Connect" vs "stale", so we can't
  reconcile intelligently or show provenance.
- **B4 — No `lastSyncedAt`.** "Synced from Health Connect" message (line 416) is transient; there is no
  persisted last-sync timestamp anywhere, so no UI can say "as of 2h ago" and no scheduler can decide
  freshness.
- **B5 — No goal ring on Dashboard or Profile.** `StreakGoalRing` is only on Body/Recovery
  (`BodyRecoveryScreen.kt:171`). The Dashboard shows only the streak *count row*; Profile shows only the
  goal *number field* with no progress feedback.
- **B6 — `weeklyGymSessions` is a write-only profile fact.** Set in Profile, fed to the AI prompt, but
  never compared to actual training frequency. No "you train 3.4×/wk vs a 4 target" surface.
- **B7 — No training-frequency aggregation.** `trained: Boolean` (`DailyLogEntity.kt:19`) and completed
  workout sessions both exist, and `buildStreaks` already unions them into `workoutDays`
  (`StreakRepository.kt:68-71`), but nothing derives "sessions/week over the last N weeks" as a number or
  trend.
- **B8 — No steps history read.** HC `readToday` is single-day; a freshly-connected user gets only today,
  so streaks/trends start empty even though HC holds weeks of history.
- **B9 — Weak input validation.** No upper bound (a fat-fingered `1000000` is accepted), no sheet-level
  guard.
- **B10 — `activityLevel` is set-and-forget.** Used only by `PlanCalculator`/`PlanGenerator` for the
  Mifflin–St Jeor TDEE multiplier; never revisited against logged activity, so a "Sedentary" user who
  actually walks 12k/day is never nudged to recompute.

---

## 2. UX improvements

Ordered by leverage. Each is design-system compliant (see §3 for exact tokens).

- **U1 — Auto-sync on app open (foreground).** When Health Connect is enabled + permitted, trigger a
  steps/weight/sleep sync on app start / return to foreground, debounced so it runs at most once per
  ~15 min. Hook `ProcessLifecycleOwner` `ON_START` (the pattern `LifecycleEventObserver` is already used
  in the app — `ui/component/AuroraBackground.kt:101`, `GlassOrbBackground.kt:83`). This alone makes the
  pillar feel "live" for the common case.
- **U2 — Periodic background sync (WorkManager).** A `PeriodicWorkRequest` (~every 3–6h, `NetworkType.NOT_REQUIRED`,
  no charging constraint) that runs the same sync path even when the app isn't opened, so streaks don't
  silently break overnight. Gated entirely behind `healthConnectEnabled` (enqueue on connect, cancel on
  disconnect in `onHealthConnectToggled`).
- **U3 — Dashboard activity tile (goal ring).** Add a compact steps-goal ring + "X / goal · streak n"
  to the Dashboard, reusing `StreakGoalRing` (or a slimmer tile variant). Put it near the existing
  `StreaksCard` (`DashboardScreen.kt:575`). This is the headline daily-loop surface.
- **U4 — Profile goal feedback.** On the Profile "Daily step goal" row (`ProfileScreen.kt:234`), show a
  small trailing 7-day average chip ("avg 8.2k / 10k goal") so the user can sanity-check whether the
  goal they're setting is realistic.
- **U5 — Training-frequency surface.** A "Training" tile (Train tab or Dashboard) showing
  **actual sessions/week (last 4 weeks)** vs the `weeklyGymSessions` target, derived from `workoutDays`.
  Turns a dead profile field into a live, motivating comparison.
- **U6 — Clean dual-source handling.** Decide and implement a single, explicit source-of-truth rule
  (see §4 D3) and **show provenance subtly** ("from Health Connect" / "manual") on the Body steps row so
  the user understands why a number changed.
- **U7 — "Sync stale" affordance.** If HC is enabled but last sync is > N hours old, the Dashboard
  activity tile shows a quiet "tap to refresh" rather than a stale number presented as fact.
- **U8 — Activity nudge insight (AI).** See §5 — an optional NEAT/steps one-liner on the Dashboard or
  Body/Recovery card.

---

## 3. UI improvements (design-system compliant)

All of the below obey `docs/design-system.md`: `AppType` tokens only (no bare `fontSize`/`fontWeight`),
`FrostedCard`/`NeutralCard`/`TintedCard`, `Liquid*` buttons, `ScreenScaffold`/`SectionLabel`, 16dp gutter,
color via `LocalAppColors.current` / `LocalAppAccent.current`.

### Components to reuse (do NOT rebuild)
- **`StreakGoalRing`** (`ui/streak/StreakComponents.kt:304`) — already a `FrostedCard` + `Canvas` arc with
  `StreakGoalMetColor` swap and `streakIcon`/`streakLabel`. Reuse verbatim for the Dashboard tile if a
  full-width card is acceptable.
- **`StreakRow`** (`StreakComponents.kt:202`), **`StreakWeekStrip`** (`:131`), **`StreakCountFlame`** (`:174`),
  **`streakIcon`/`streakLabel`** (`:87/:94`) — for any count/strip rendering.
- **`SectionLabel`**, **`FrostedCard`**, **`AppType.statValue` / `metaLabel` / `cardTitle` / `cardSubtitle`**
  for tile text.

### New / changed UI (minimal, all reuse)
- **`ActivityTile` (Dashboard)** — *Quick win.* Easiest path: drop the existing `StreakGoalRing` into a
  `DashboardScreen` `item {}` near `StreaksCard` (`:575`). If a slimmer look is wanted, a new
  `CompactGoalRing` composable in `StreakComponents.kt`: a `FrostedCard` with the same 74dp `Canvas` arc
  on the left, and on the right `Text(steps "8,210", style = AppType.statValue)` over
  `Text("of 10,000 · 🔥 5", style = AppType.metaLabel, color = appColors.textMuted)`. No new card family,
  no raw `.background().border()`.
- **Profile goal chip** — in `ProfileScreen.kt` next to the "Daily step goal" `…Field` (`:234`), a trailing
  `Text("avg 8.2k", style = AppType.metaLabel, color = appColors.textMuted)`. Pull the 7-day average from
  a new repository flow (§4 D5).
- **`TrainingFrequencyTile` (Train tab / Dashboard)** — a `FrostedCard`:
  `SectionLabel("Training")` + `Text("3.4×", style = AppType.statValue)` +
  `Text("/wk · target 4", style = AppType.cardSubtitle, color = appColors.textSecondary)`, optionally a
  `StreakWeekStrip` for the last 7 days using `streaks.workout.last7Marks`.
- **Provenance line** — on the Body steps display, a `Text(source, style = AppType.metaLabel,
  color = appColors.textFaint)` ("Health Connect" / "Manual"). Pure presentation; no new component.
- **Stale-refresh chip** — reuse `LiquidActionButton(text = "Refresh", small = true, …)` rather than a
  Material button, shown only when `lastSyncedAt` is old.

---

## 4. Data / model improvements

### D1 — Keep `steps` as a single `Int?` (do **not** model intra-day buckets)
A body-recomp tracker needs the **daily total**, not a step time-series. Resist expanding `steps` into a
list/relation. The single `Int?` on `DailyLogEntity` is correct. (See §9.)

### D2 — Add source + last-synced metadata (schema 13 → 14)
Add to `DailyLogEntity`:
```kotlin
val stepsSource: String? = null,   // "manual" | "health_connect" | null (unknown/legacy)
```
and persist a global **last Health Connect sync timestamp** in `PlanPreferences` (DataStore — it already
holds `healthConnectEnabled`, `ui/settings/SettingsViewModel.kt:102/235`) rather than per-row, e.g.
`healthConnectLastSyncEpochMs: Long? = null`. Per-row source enables reconciliation (D3); the global
timestamp drives freshness UI (U7) and the periodic-sync freshness check (U2). Room migration **13 → 14**:
`ALTER TABLE daily_logs ADD COLUMN stepsSource TEXT` (nullable, default null = safe for existing rows).
Follow the existing migration-test pattern (recent commit `1fe8fda` added a Robolectric 12→13 migration
test — mirror it for 13→14).

### D3 — Fix the reconciliation rule (the B1 bug)
Replace `steps = existing?.steps ?: result.steps` (`LogRepository.kt:341`) with an explicit policy:
- If `existing.stepsSource == "manual"` → **keep manual** (user typed it deliberately; HC must not clobber).
- Else (source is `health_connect` or null/legacy) → **take the HC value** and set
  `stepsSource = "health_connect"`. HC is monotonic-ish for the current day, so taking the latest read is
  correct and fixes the "stuck at the morning value" bug.

Mirror the same rule for `bodyWeightKg`/`sleepHours` only if desired; steps is the priority. Manual writes
(`BodyEditViewModel`, `BodyCheckInSheet`) must set `stepsSource = "manual"`.

### D4 — Steps history backfill on connect (B8)
Add `HealthConnectRepository.readStepsHistory(days: Long = 30): Map<LocalDate, Int>` (aggregate
`StepsRecord` per day in a single ranged read — `ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(...))`
then group by local date). Call it once from `onPermissionsResult()` right after the first `syncNow()`,
writing each day via the D3 policy. This makes streaks/trends meaningful from day one instead of starting
at a single data point. **No new permission needed** — `StepsRecord` read is already in `requiredPermissions`
(`HealthConnectRepository.kt:34`).

### D5 — Training-frequency + steps-average derivations (pure Kotlin, `domain/`)
Add a pure-Kotlin helper (sibling to `domain/streak/`), e.g. `domain/activity/ActivitySummary.kt`:
- `weeklyTrainingFrequency(workoutDays: Set<LocalDate>, today, weeks = 4): Double` — count of training days
  in the trailing `weeks*7` window ÷ `weeks`. Reuse the exact `workoutDays` union already built in
  `buildStreaks` (`StreakRepository.kt:68-71`) so the two never disagree — extract that union into the
  shared helper and have `buildStreaks` call it.
- `averageDailySteps(dailyLogs, today, days = 7): Int?` — mean of non-null `steps` over the window.
Surface both via a new flow on `StreakRepository` (or a small `ActivityRepository`) so Dashboard/Profile/Train
can `collectAsStateWithLifecycle()` them. Domain stays Android-free (pure `LocalDate` math, like
`StreakCalculator`).

### D6 — Use `weeklyGymSessions` as the *target* in D5's comparison
No schema change; just read the existing field as the target line in `TrainingFrequencyTile` (U5) and in
the activity insight (§5). This is the cheapest way to make a dead-ish field earn its place.

### D7 — Leave `activityLevel` where it is, add one nudge hook (optional)
Don't restructure the TDEE input. Optionally compute "implied activity from 14-day avg steps" and, if it
diverges from the stored `activityLevel` by ≥1 band, surface a *soft* "your steps suggest you're more
active than your plan assumes — recompute?" prompt. Keep it advisory; never auto-mutate the plan.

---

## 5. AI opportunities (on-brand with the 2B constraints)

Constraints from `docs/ai-coach.md`: the on-device Gemma 4 2B is brittle — every unnecessary tool call
risks an empty response; static/session-invariant data belongs in the system prompt, not a tool; insight
cards are **single-turn, no tool calls** (`GemmaInsightCoordinator.generateExplanation()`).

- **A1 — New insight card: `ACTIVITY_NEAT` (preferred).** Add an `InsightKind.ACTIVITY_NEAT` to
  `ai/InsightRequest.kt:3` and a matching `InsightRequest.Activity(context)` + `ActivityInsightContext`,
  following the exact shape of `RecoveryReadiness` (`InsightRequest.kt:24-28`). The context is fully
  pre-computed (today's steps, goal, 7-day avg, streak, training freq vs target — all from §4 D5), so the
  card is single-turn with **no tool calls**, consistent with the other insight cards. Add the prompt
  branch in `InsightPromptBuilder` and a stub in `StubInsightCoordinator` (it already has per-kind
  branches, e.g. `:150`). Render it with the existing `GeneratedInsightCard` (`TintedCard`, 🤖) on the
  Dashboard or under the Body/Recovery `StreakGoalRing` (`BodyRecoveryScreen.kt:171`).
  Example one-liner target: *"You're 1,800 steps short of goal with 4 active hours left — a 20-min walk
  closes it."*
- **A2 — Do NOT add a new coach tool for steps.** `get_today_summary` already returns `steps` and `trained`
  (`CoachToolExecutor.kt:54`), and weekly trends exist. Adding a steps-specific tool would burn a scarce
  tool iteration for data already in the snapshot — exactly what `docs/ai-coach.md` warns against ("Never
  add a tool for data that is static or session-invariant").
- **A3 — Enrich the system-prompt snapshot, not the tool list.** If we want the coach to reason about
  activity, add the pre-computed `steps_vs_goal` / `weekly_training` figures (from §4 D5) into the existing
  static snapshot block built at conversation start, so the model answers from the prompt with zero extra
  iterations.
- **A4 — Keep it advisory.** Any "log your steps" or "recompute your plan" suggestion goes through the
  existing **write-tool confirmation flow** (`log_metric` already supports steps? — note: current
  `log_metric` metrics are `weight_kg/waist_cm/sleep_hours/energy/hunger/soreness`, **steps is not a
  metric**; if we want the coach to log steps, add `steps` to `CoachToolExecutor.logMetric` rather than a
  new tool). Lower priority than A1.

---

## 6. Quick wins (small, high-value)

- **Q1 — Steps goal ring on the Dashboard.** Drop the existing `StreakGoalRing` into a `DashboardScreen`
  `item {}` near `StreaksCard` (`DashboardScreen.kt:575`); data is already in `streakState`
  (`streaks.steps`, `stepGoal`) and today's steps come from the day log. ~30 min, pure reuse.
- **Q2 — Steps input upper-bound validation.** In `BodyEditViewModel` (`:85-87`) extend the existing
  integer check with a sane cap (e.g. reject `> 200_000` and negatives), and add the same guard to the
  `BodyCheckInSheet` path. No new components.
- **Q3 — Profile 7-day-avg chip.** Trailing `metaLabel` chip on the "Daily step goal" row
  (`ProfileScreen.kt:234`) — needs the §4 D5 average flow.
- **Q4 — Provenance label on Body steps.** Show "manual" / "Health Connect" once `stepsSource` (D2) lands.
- **Q5 — Update the stale `Source of Truth` line in `CLAUDE.md`** (says schema v8; real is v13). Trivial,
  prevents future migration-numbering mistakes.

(Q1, Q2, Q5 need no schema change and can ship immediately. Q3/Q4 depend on §4.)

---

## 7. Medium improvements

- **M1 — Foreground auto-sync on app open (U1).** New tiny `HealthSyncCoordinator` (app-scoped, wired in
  `core/AppContainer.kt`) observing `ProcessLifecycleOwner` `ON_START`; debounced via the new
  `healthConnectLastSyncEpochMs` (D2). Calls the same `readToday` + `applyHealthConnectSync` path
  `SettingsViewModel.syncNow()` uses today, but reusing it from a non-Settings entry point. Requires the
  D3 reconciliation fix first (otherwise auto-sync still can't update a populated value).
- **M2 — Steps history backfill on connect (D4/B8).** `readStepsHistory(30)` invoked from
  `onPermissionsResult()`.
- **M3 — Dashboard `ActivityTile` finalized (U3).** Either reuse `StreakGoalRing` (Q1) or the new
  `CompactGoalRing` (§3).
- **M4 — Training-frequency tile (U5 + D5/D6).** Pure-domain derivation + `FrostedCard` tile.
- **M5 — `lastSyncedAt` surfacing + stale chip (U7).**

---

## 8. Bigger refactors

- **R1 — WorkManager periodic background sync (U2).** Add `androidx.work:work-runtime-ktx` (not currently a
  dependency). Create `HealthSyncWorker` (CoroutineWorker) that runs the shared sync path; enqueue a
  `PeriodicWorkRequest` (3–6h) on connect, cancel on disconnect (hook into `onHealthConnectToggled`,
  `SettingsViewModel.kt:222`). Must reuse the *same* repository sync function as foreground sync — extract
  `syncNow()`'s core out of `SettingsViewModel` into a repository/coordinator method so VM + Worker +
  lifecycle observer all share one code path (avoids three drifting copies). This is the structurally
  largest item and unlocks "streaks that don't break while you sleep".
- **R2 — Source-of-truth reconciliation layer (D2 + D3, schema 13 → 14).** The `stepsSource` column +
  policy + manual-write tagging + migration + migration test. Touches entity, DAO writes,
  `applyHealthConnectSync`, both manual write paths, and a new Robolectric migration test (mirror `1fe8fda`).
- **R3 — Shared `ActivityRepository` / domain `activity` package (D5).** Consolidate the steps-average,
  training-frequency, and `workoutDays` union (currently inline in `buildStreaks`) into one reusable place
  so Dashboard, Profile, Train, and the AI snapshot all read consistent numbers.

---

## 9. What to avoid for now (scope discipline)

- **No built-in pedometer / `SensorManager` step counting.** Health Connect is the integration surface;
  duplicating it with a raw `STEP_COUNTER` sensor adds permission, battery, and reconciliation complexity
  for no recomp benefit.
- **No intra-day step time-series.** Keep `steps` a daily `Int?` (D1). Do not add a steps relation/table.
- **No multi-source merge engine.** Exactly **two** sources (manual, Health Connect) with a **single
  deterministic rule** (D3: manual wins, else HC). Do not build priority lists, per-source confidence, or
  Google Fit / Samsung Health *step* fan-in (the Samsung path is nutrition-CSV only and should stay that way).
- **No auto-mutation of `activityLevel` or the plan.** Activity nudges (D7) stay advisory and
  confirmation-gated.
- **No new coach tool for steps** (A2) — enrich the snapshot, don't spend a tool iteration.
- **No real-time / WatchDog-style sync.** Periodic (hours) + on-open is plenty for a daily-total metric;
  don't chase minute-level freshness.

---

## 10. Suggested implementation order

Each phase is independently shippable and verifiable in the running app. Build one screen/feature at a
time and verify it in-app before the next (per project working agreement).

### Phase 0 — Quick wins, zero schema risk *(ship first)*
- [ ] **Q1** Add `StreakGoalRing` to the Dashboard near `StreaksCard` (`DashboardScreen.kt:575`).
- [ ] **Q2** Add upper-bound + negative validation to steps input (`BodyEditViewModel.kt:85-87` and the
      `BodyCheckInSheet` path).
- [ ] **Q5** Fix the stale schema-version line in `CLAUDE.md` (v8 → v13).
- [ ] *Verify:* Dashboard shows a steps ring filling toward goal; entering `999999999` or `-5` steps is
      rejected with a message.

### Phase 1 — Source attribution + reconciliation fix (the core bug) *(schema 13 → 14)*
- [ ] **R2/D2** Add `stepsSource` to `DailyLogEntity`; add `healthConnectLastSyncEpochMs` to `PlanPreferences`.
- [ ] **R2** Room migration 13 → 14 + Robolectric migration test (mirror `1fe8fda`).
- [ ] **D3** Rewrite `applyHealthConnectSync` reconciliation (`LogRepository.kt:341`): manual wins, else HC
      refreshes; tag manual writes with `stepsSource = "manual"`.
- [ ] *Verify:* enter steps manually → sync HC → manual value is preserved; with no manual entry, HC value
      now updates the existing row (the B1 bug is gone).

### Phase 2 — Shared activity derivations (pure domain) *(no schema)*
- [ ] **D5/R3** Extract `workoutDays` union out of `buildStreaks`; add `weeklyTrainingFrequency` and
      `averageDailySteps` in `domain/activity/`; expose flows from the repository.
- [ ] **Q3** Profile 7-day-avg chip; **U4**.
- [ ] *Verify:* Profile shows "avg X / goal"; numbers match hand-computed values from the day logs.

### Phase 3 — Foreground auto-sync *(needs Phase 1)*
- [ ] **M1** Extract `syncNow()` core into a shared coordinator/repository method; add a
      `ProcessLifecycleOwner` `ON_START` observer (debounced via `lastSyncedAt`); wire in `AppContainer`.
- [ ] **U7/M5** Surface `lastSyncedAt` + stale-refresh chip on the Dashboard activity tile.
- [ ] *Verify:* connect HC, leave & re-open the app → steps refresh without visiting Settings; tile shows
      "as of …".

### Phase 4 — Steps history backfill *(needs Phase 1)*
- [ ] **M2/D4** `readStepsHistory(30)` from `onPermissionsResult()`, written via the D3 policy.
- [ ] *Verify:* on a fresh connect with HC history, the Steps streak/strip immediately reflect prior days.

### Phase 5 — Training-frequency surface *(needs Phase 2)*
- [ ] **M4/U5/D6** `TrainingFrequencyTile` (actual sessions/wk vs `weeklyGymSessions`) on Train/Dashboard.
- [ ] *Verify:* tile shows correct trailing-4-week rate vs the profile target.

### Phase 6 — Background periodic sync *(biggest; needs Phase 1 + 3's shared method)*
- [ ] **R1/U2** Add `work-runtime-ktx`; `HealthSyncWorker`; enqueue on connect / cancel on disconnect
      (`onHealthConnectToggled`).
- [ ] *Verify:* with the app closed overnight, the next-morning open shows yesterday already synced and the
      streak intact.

### Phase 7 — AI activity insight *(needs Phase 2's derivations)*
- [ ] **A1/A3** Add `InsightKind.ACTIVITY_NEAT` + context + prompt branch + `StubInsightCoordinator` branch;
      render via `GeneratedInsightCard`. Optionally enrich the coach system-prompt snapshot (A3). Optionally
      add `steps` to `CoachToolExecutor.logMetric` (A4).
- [ ] *Verify (stub first, then on-device):* the card renders a sensible single-line NEAT nudge; no extra
      coach tool calls are introduced.

---

## Verification still needed (on-device, by Zack)

The logic compiles + is unit-tested, but these UI/runtime behaviours can only be confirmed in the
running app (I couldn't drive the emulator overnight):

- **Dashboard steps ring** (Phase 0) fills toward goal / shows "set a goal" hint when unset.
- **Steps validation** (Phase 0) rejects `999999999` / `-5` with the range message; blank still allowed.
- **HC sync reconciliation** (Phase 1): enter steps manually → sync → manual kept; with no manual
  entry, a populated HC day now refreshes (the B1 bug is gone). Migration 13→14 applies on a real
  upgrade without data loss.
- **Profile 7-day avg chip** (Phase 2) shows under the step-goal field.
- **Foreground auto-sync** (Phase 3): connect HC, background the app, reopen → steps refresh without
  visiting Settings.
- **History backfill** (Phase 4): on a fresh connect with HC history, the Steps streak/strip reflect
  prior days immediately.
- **Training-frequency tile** (Phase 5) shows actual ×/wk vs the gym-sessions target.
- **Background sync** (Phase 6): WorkManager job runs (~4h); verify with
  `adb shell dumpsys jobscheduler | grep recomp` or leave overnight.
- **ACTIVITY_NEAT card** (Phase 7) renders on the Dashboard **with a cloud backend selected + a step
  goal set** (it's cloud-only and hidden on the local Gemma backend by design).

### Cross-references (real files this plan touches)
- `data/local/entity/DailyLogEntity.kt:14,19` · `data/local/RecompDatabase.kt:62` · `data/local/dao/DailyLogDao.kt:25`
- `data/health/HealthConnectRepository.kt:34,79,108-114` · `data/health/HealthConnectModels.kt:9`
- `data/repository/LogRepository.kt:337-345` · `data/repository/StreakRepository.kt:28-105`
- `data/preferences/UserProfilePreferences.kt:15,17` · `ui/settings/SettingsViewModel.kt:222,388,401-425`
- `ui/integrations/IntegrationsScreen.kt:145` · `ui/profile/ProfileScreen.kt:226-237`
- `ui/body/BodyCheckInSheet.kt:114` · `ui/body/BodyEditViewModel.kt:75,85-97` · `ui/body/BodyCheckInForm.kt:104`
- `ui/dashboard/DashboardScreen.kt:575,593-595` · `ui/today/BodyRecoveryScreen.kt:122-124,171-176`
- `ui/streak/StreakComponents.kt:87,94,202,304` · `domain/streak/StreakCalculator.kt` · `domain/streak/StreakModels.kt`
- `ai/InsightRequest.kt:3-28` · `ai/CoachToolExecutor.kt:54` · `ai/GemmaCoachCoordinator.kt:410` · `ai/CoachToolsAdapter.kt:74`
