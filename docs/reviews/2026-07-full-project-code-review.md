# Full-Project Code Review — July 2026

**Scope:** the entire `develop` branch (~53,000 lines of main-source Kotlin, ~23,000 lines of tests) reviewed area-by-area: Room + repositories, preferences/remote/health data, domain logic, the cloud AI layer, all UI packages, core/DI, build system, manifest, and CI. Every finding below cites file:line and was verified against the actual code (the four most severe were re-verified independently a second time).

**Test-suite ground truth:** `./gradlew :app:testDebugUnitTest` → **1300 of 1301 tests pass**. The single failure is `ai.harness.InsightHarnessTest`, which makes a live OpenRouter call from the default unit-test task and was rate-limited (HTTP 429) — an infrastructure problem (see P2-24), not a code regression.

**Headline counts:** 3 × P0 (data loss / crash), 23 × P1 (incorrect behavior users can hit), ~30 × P2 (latent bugs, races, perf), P3s batched thematically.

Severity: **P0** crash/data loss/security · **P1** wrong user-visible behavior · **P2** latent bug / performance / architecture violation · **P3** style, consistency, a11y.

---

## P0 — Critical (fix before anything else)

### P0-1 · Restoring a backup permanently deletes all training data
`domain/export/BackupModels.kt:18-38`, `data/repository/BackupRepository.kt:46-79`

`BackupPayload` exports 10 data groups but **none of the 7 training tables** (`workouts`, `workout_exercises`, `planned_sets`, `workout_sessions`, `session_exercises`, `session_sets`, `exercises`) and not `catalog_foods`. `restoreFromJson()` runs `database.clearAllTables()` — which wipes **all 19 tables** — then re-inserts only the payload. Net effect of the flagship "Export backup / Import backup — restore everything" flow: **every routine, every logged training session, every custom exercise, and the imported NEVO catalog are unrecoverably destroyed**. The exercise library re-seeds only on next app launch (Train is broken until restart); NEVO requires a manual CSV re-import.

*Fix:* add the training tables to `BackupPayload` (nullable-with-default fields keep old backups importable), or replace `clearAllTables()` with targeted deletes of only the tables the payload owns. Consider a pre-restore auto-export safety net. Independently found by two reviewers.

### P0-2 · Restore breaks every meal→slot assignment (silently mangled data)
`data/repository/BackupRepository.kt:55`

Slots are re-inserted with `insert(it.copy(id = 0))` — fresh AUTOINCREMENT ids (SQLite's `sqlite_sequence` is *not* reset by `clearAllTables()`, verified against Room 2.8.4 sources, so the sequence continues above the old max) — while `mealEntryDao().insertAll(payload.mealEntries)` keeps each entry's **original** `slotId`. There is no FK on `slotId`, so nothing fails; the references just dangle. After every import, meals disappear from their Breakfast/Lunch/Dinner cards in the Food Log (`ui/today/FoodLogViewModel.kt:128-131` renders only `slotMap[slot.id]`) while still counting in day totals — calories with no visible meals. The recipes block directly below does the id-remap correctly (`ingredient.copy(id = 0, recipeId = newRecipeId)`); slots were simply missed.

*Fix:* since the tables were just cleared, insert slots with their original ids (`insert(it)`), or build an old→new map and rewrite `mealEntries.slotId` like the recipe path. ~5 lines. Independently found by two reviewers.

### P0-3 · Cold-starting from a coach push notification crashes the app
`ui/RecompApp.kt:142-151`, `MainActivity.kt` (`pendingDeepLink` set in `onCreate` before `setContent`)

The deep-link `LaunchedEffect(pendingAction)` navigates immediately, but while `onboardingComplete == null` (first frames, DataStore read pending) the `NavHost` is not composed — `navController.navigate()` throws `IllegalStateException` ("You must call setGraph() before…"). The comment above the effect claims "Guarded on onboardingComplete so we never route over the onboarding flow" — **no such guard exists in the code** (the flag is even declared *after* the effect). Repro: kill the app, tap a coach notification → crash on launch. Secondary bug: on a warm start with onboarding incomplete, the effect navigates over the onboarding flow, exactly what the comment says it prevents.

*Fix:* gate the effect on `onboardingComplete == true` and `navController.currentDestination != null`.

---

## P1 — High (incorrect behavior a user can hit)

### Data & sync

**P1-1 · Health Connect steps are frozen at the last pre-midnight sync — every day is undercounted.**
`data/health/HealthSyncCoordinator.kt:34-43,97-100`, `HealthConnectRepository.kt:76-88,120-135`. Every recurring sync path writes *today only*; nothing ever re-reads a completed day. Steps taken between the last sync of day D and midnight are lost forever (typically hundreds to a few thousand steps daily, bounded by the 4-hour worker cadence). Silently skews step streaks, `avgSteps7`, coach activity signals, and rebalance step math. This is the long-suspected steps-sync bug, now pinned. *Fix:* sync `readStepsHistory(days = 2)` so the first post-midnight sync finalizes yesterday — the provenance-reconciliation machinery (`StepsReconciliation.kt`) already protects manual entries.

**P1-2 · A weight measured up to 30 days ago is written into *today's* log.**
`HealthConnectRepository.kt:80,84,154-159`, `LogRepository.kt:362`. `readToday` returns the newest `WeightRecord` in a trailing 30-day window and `applyHealthConnectSync` stamps it on today whenever today has no weight. A weekly smart-scale weigh-in gets copied onto every intervening day: the weight trend flattens toward zero (feeding the adjustment engine), and `daysSinceLastWeighIn` is always ~0 (silencing the weigh-in reminder signal). *Fix:* attribute the weight to `WeightRecord.time`'s local date.

**P1-3 · Three unserialized Health Connect writers race on app open and can wipe each other's fields.**
`ui/today/TodayViewModel.kt:165-172` bypasses `HealthSyncCoordinator` (whose KDoc claims to be the single entry point) with its own `readToday` + `applyHealthConnectSync`, concurrent with `syncStepsNow()` and `syncIfDue()` from `RecompTrackerApp.onStart`. All three are get-copy-upsert on the whole `DailyLogEntity` row → interleavings revert freshly-written weight/sleep to null. *Fix:* route the ViewModel through the coordinator.

**P1-4 · Restoring the app to a new device crash-loops until the user wipes all data.**
`data/preferences/SecureKeyStore.kt:19-41`, `core/AppContainer.kt:274`, `AndroidManifest.xml:19`. `allowBackup="true"` with no `dataExtractionRules` backs up `secure_ai_prefs` (EncryptedSharedPreferences), but the Keystore master key never leaves the old device. On restore, Tink can't decrypt the keyset and `EncryptedSharedPreferences.create()` throws — inside `Application.onCreate`, because `SecureKeyStore` is constructed eagerly and its `init` block forces the lazy prefs. The only recovery is Clear Data — losing every log, i.e. the exact wipe the shared-keystore scheme exists to prevent. *Fix:* exclude `secure_ai_prefs` via `dataExtractionRules` + catch-and-recreate on decrypt failure. Independently found by two reviewers.

**P1-5 · Replacing an existing API key doesn't take effect until app restart.**
`core/AppContainer.kt:278-289`. `cloudConfigFlow` combines on `secureKeyStore.hasKey` (a `Boolean` StateFlow) and reads the key only when the combine re-fires; setting a new key over an old one keeps `hasKey == true` (conflated) → the old key is used everywhere while Settings' "Test connection" (which reads fresh) succeeds. Maximally confusing key-rotation failure. *Fix:* make `CloudConfig` carry a key provider lambda (the `TavilyWebSearchProvider` pattern).

### Domain & coach logic

**P1-6 · The deload detector reads the RIR series backwards — silent when you're grinding, fires after you've recovered.**
`domain/coach/CrossDomainDetectors.kt:34-44` assumes chronological order; the producer `TrainingDerivations.recentRir` (`domain/coach/TrainingDerivations.kt:70-79`, pinned by its own test) returns **newest-first**, wired verbatim in `CoachContextAssembler.kt:274-275`. Real history "RIR 3 → ground down to 1" arrives as `[1,1,2,3]` → reads as *rising* → silent (the overreaching case the detector exists for); the recovered case fires DELOAD_DUE. `takeLast(6)` also selects the *oldest* six readings. Detector tests pass only because fixtures hand-build chronological lists. *Fix:* define the ordering contract in one place and add a test that pipes `TrainingDerivations` output into the detector.

**P1-7 · Coach-run daily digest double-runs and clobbers its own winner.**
`data/coach/CoachDigestCoordinator.kt:191-196`. The once-a-day debounce is checked *outside* `runLock` and never re-checked inside; app-open fires two triggers within milliseconds (`RecompTrackerApp.onStart` + `CoachTodayViewModel.onShown`). The second run re-evaluates with the ledger the first just wrote — the winner is now on cooldown — so it stages the runner-up (or null), overwriting the correct card and burning lower-ranked signals' 7-day cooldowns. `RebalanceCoordinator.runIfDue` (`RebalanceCoordinator.kt:115-123`) shows the correct in-lock re-check pattern. *Fix:* one line.

### AI layer

**P1-8 · Coach-logged library foods store internally inconsistent entries (amount ≠ macros).**
`ai/CoachToolExecutor.kt:288,314`. When the user gives no grams, macros are scaled at the 100 g basis (`scale = 1.0`) but the displayed amount is set to the household serving — "log ketchup" produces "15 g · 15 kcal" (15 g of ketchup is ~2 kcal). A later amount edit rescales from `basePer100*` and silently changes the calories. `CoachToolExecutorTest:712-732` constructs exactly the triggering data but never asserts `amountGrams`. *Fix:* one basis for both: `grams = requestedGrams ?: householdServingGrams ?: 100.0`.

**P1-9 · The write-confirmation dialog can describe a different action than the one executed.**
`ai/CloudCoachCoordinator.kt:291-302`, `CoachToolExecutor.kt:276-294`. The dialog renders the *model's* args, but the executor then silently overrides name and macros from the library match — whose weakest tier (score 0: "every query word appears somewhere") picks arbitrarily among ties — and a future `date` makes the tool *plan* the meal while the dialog always says "to today's food log". The user approves something other than what is written. *Fix:* resolve the library match and date wording *before* building the `PendingCoachAction`; require a minimum match score for silent override.

### UI — dates, numbers, input

**P1-10 · "Today" freezes at ViewModel creation — after midnight the app displays and writes into the wrong day.**
`ui/today/FoodLogViewModel.kt:96`, `ui/today/TodayViewModel.kt:92`, `ui/dashboard/DashboardViewModel.kt:154,183`, `ui/dashboard/DashboardScreen.kt:452`, `ui/train/TrainViewModel.kt:70`. Tab ViewModels live as long as the process; leave the app open overnight and next morning the Dashboard aggregates yesterday as "today", a Body check-in **saves under yesterday's date**, the Food Log mislabels planned meals, and Train still says "You've trained today". *Fix:* a reactive `DateProvider.todayFlow()` (midnight ticker + `ACTION_DATE_CHANGED`/timezone receiver) combined into the date-scoped pipelines.

**P1-11 · An invalid steps value silently discards the entire body check-in.**
`ui/body/BodyCheckInSheet.kt:58-61`, `ui/today/TodayViewModel.kt:275-281`. The sheet's Save calls `saveMetrics()` (which returns early on invalid steps, setting only `uiState.message`) and then dismisses unconditionally; neither the sheet nor the Body screen ever renders that message. Typo "12k" in steps → weight, sleep, scores, and notes are all silently not persisted. *Fix:* dismiss only on `savedEvent`, or render the error in the sheet and stay open.

**P1-12 · Trends screen inflates weight/waist trends up to 7× on sparse data — and contradicts the Dashboard.**
`ui/progress/ProgressViewModel.kt:161-167`, `ProgressInsightMapper.kt:30-34`. `trendPerWeek` divides by *point count* (`(size-1)/7` weeks) over a series that drops missing days — two weigh-ins 28 days apart are treated as 1/7 week apart. The Dashboard uses the correct date-based regression (`domain/trend/TrendCalculator`), so the two screens disagree on the same data; the inflated number also feeds the AI trend-insight card. *Fix:* delete the local implementation, route through `TrendCalculator`.

**P1-13 · Rebalance progress dots are off by one.**
`ui/dashboard/RebalanceViz.kt:310-311,347-354,369`. `dayX` is 1-based, but `completed = i < dayX` marks *today* as already done and `isToday = i == dayX` puts the ring on *tomorrow*; on the final day there is no ring at all, and the progress bar reads 25% at the *start* of day 1 of 4. *Fix:* `i < dayX - 1` / `i == dayX - 1`; extract a testable `dotStateFor(i, dayX)`.

**P1-14 · Onboarding accepts absurd height/weight; imperial height invites feet-as-inches.**
`ui/onboarding/OnboardingViewModel.kt:33-45,229-237`. No plausibility bounds (birth date *is* range-checked); the imperial height field is a single number labelled "in", so a US user typing "5.9" (5′9″) gets 15 cm and a garbage Mifflin-St Jeor plan with no warning. Also `finish()` accepts any 5-digit adjusted calorie target with the zone still computed from the *original* plan. *Fix:* bound 90–250 cm / 30–300 kg, ft+in split input, clamp adjusted target and recompute zone.

### Training flows

**P1-15 · Editing a routine silently wipes unsaved changes on return from the exercise picker.**
`ui/navigation/AppNavGraph.kt:440`, `ui/train/RoutineBuilderViewModel.kt:65`. `LaunchedEffect(workoutId) { vm.loadWorkout(workoutId) }` re-runs when the builder re-enters composition after the picker pops; `loadWorkout` has no already-loaded guard and overwrites the draft — racing the `addExercises(pickedIds)` effect, so even the just-picked exercises can vanish. Same wipe on rotation. *Fix:* a two-line already-loaded guard.

**P1-16 · Starting a routine while another session is active orphans the first session's logged sets.**
`ui/train/TrainHomeScreen.kt:327-331`, `data/repository/WorkoutSessionRepository.kt:32`. `startSession` inserts unconditionally; the DAO resolves "the" active session by recency. A second ACTIVE session makes the half-finished workout unreachable (not history, not resumable), resurfacing as a stale Resume banner days later. Double-tap on Start also races. *Fix:* transactional abandon-or-confirm in `startSession` + confirm dialog + in-flight disable.

**P1-17 · Removing an exercise mid-session deletes its logged sets instantly with no confirmation.**
`ui/train/ActiveSessionScreen.kt:290`, `SessionExerciseEntity` FK CASCADE. "Remove" sits directly below "Replace exercise" in the same menu — and Replace *does* gate on logged sets. *Fix:* mirror the replace guard.

**P1-18 · Fast set entry can log wrong reps or revert just-typed values.**
`ui/train/ActiveSessionViewModel.kt:176-253`, `ActiveSessionScreen.kt:333-348`. Every keystroke is a separate whole-entity DB write built from the last *emitted* flow snapshot; type "12" then tap ✓ quickly and the toggle writes `reps = 1, completed = true` (the field's local buffer still shows 12). Typing kg then jumping to reps can revert the kg. *Fix:* targeted per-column UPDATE queries; `toggleComplete` re-reads the row inside the coroutine.

### Data layer

**P1-19 · Shipping an updated exercise library can never succeed — and fails silently, forever.**
`data/repository/ExerciseLibraryRepository.kt:56-64`. The version-gated re-seed does `deleteBySource` + `insertAll` without a transaction; `workout_exercises`/`session_exercises` declare FK NO ACTION on `exercises.id`, so the bulk delete throws `SQLiteConstraintException` for any user with ≥1 routine. The caller swallows it (`runCatching → Log.w`), and because the stored version never updates, the full JSON is re-parsed and re-fails on **every launch**. *Fix:* id-preserving upsert keyed on `(source, externalId)` inside a transaction.

**P1-20 · Re-creating a custom exercise with the same name crashes the app.**
`ExerciseLibraryRepository.kt:27-48`, `ExerciseDao.kt:30-31` (`@Insert(REPLACE)`). REPLACE deletes the existing row on the unique-index conflict → NO-ACTION FK throws when the exercise is in a routine → uncaught in `ExercisePickerScreen.kt:356`'s `scope.launch` → crash. Unreferenced duplicates are silently deleted and re-created under a new id. *Fix:* lookup-or-error; ABORT strategy.

**P1-21 · Fresh installs get zero meal slots.**
`data/local/RecompDatabase.kt:94-97`. The default-slot seed ("Meal 1"/"Lunch"/"Dinner") lives only in `MIGRATION_1_2` (verified: no `RoomDatabase.Callback`, no initializer, onboarding creates none). A new device installs at schema v15 directly → empty Food Log with no slot cards; coach-logged and saved-food meals count in totals but are invisible (P1-22). Long-time users never see this — fresh installs do. *Fix:* seed in a `Callback.onCreate` or an idempotent initializer (the `PlanHistoryInitializer` pattern).

**P1-22 · Meals with no slot are hidden from the Food Log but counted in totals.**
`ui/today/FoodLogViewModel.kt:128-131`; contract set by `LogRepository` (several add-paths write `slotId = null`) and `CoachToolExecutor.kt:334-338` (model says "breakfast"/"snack", default slots are "Meal 1/Lunch/Dinner" → no match → null). Tell the coach "log a snack, 300 kcal": the total rises by 300 but no entry appears in the Food Log list (it can't be edited or deleted there). `TodayViewModel` already renders an unslotted group; FoodLog drops it. *Fix:* an "Unassigned" section + coach fallback to the first slot.

### Process

**P1-23 · CI never compiles PRs and never runs a single test or lint anywhere.**
`.github/workflows/distribute.yml:3-5` (and `distribute-dev.yml`). The only triggers are pushes to `main` (+ manual), and the only Gradle invocation is `assembleDebug`. Broken tests or a non-compiling `develop` are invisible until a failed distribute — or testers receive a build whose tests fail. Given the parallel-branch workflow used on this repo, this is the largest process gap. *Fix:* a `pr.yml` running `:app:testDebugUnitTest` + `lint` with Gradle caching.

---

## P2 — Medium (latent bugs, races, performance, architecture)

### Correctness & races
- **P2-1** `ai/CloudCoachCoordinator.kt:186-215` — a confirmed write that succeeds can still surface "try again" when the *final text* completion fails → invites double-logging. Track writes-per-turn; acknowledge persisted writes in the error.
- **P2-2** `ai/CloudInsightCoordinator.kt:79-87,111-137` — concurrent insight generations per kind race (no job cancel; stale text can win) and an empty SSE stream becomes `Ready("")` with the dedup key poisoned (blank card until data moves). Cancel-and-replace per kind; blank → `Error` + drop key.
- **P2-3** `ai/CoachToolsAdapter.kt:62-76` — the coach system snapshot (incl. "Today:" date and totals) is frozen for the whole conversation: midnight crossings answer with yesterday's data; post-write totals can be quoted from the pre-write snapshot. Reseed on date change; add a "re-read after write" guideline.
- **P2-4** `ai/CloudCoachCoordinator.kt:59-140` — unbounded chat history/request context (no trim/summarize) → linear token growth until a provider 400; **and** every error path clears the model-side context while the visible transcript stays (model amnesia; the consumed briefing handoff is permanently lost). 
- **P2-5** `ai/CoachToolExecutor.kt:765-775` — `suggest_meals` computes remaining calories against **base** plan targets while the system prompt and weekly trends use rebalance-**effective** targets → contradictory guidance during an active rebalance.
- **P2-6** `data/remote/OpenAiCompatModels.kt:77` — array-typed `content` (OpenAI content-parts format) throws in parsing → every turn ends "Something went wrong" with nothing logged. Flatten array content; log the swallowed exception in `CloudCoachCoordinator.kt:210-214`.
- **P2-7** `ai/CoachToolExecutor.kt:378-411` — `log_metric` whole-row read-modify-write races the user's check-in sheet save (last-writer-wins field clobber); no `date` arg means "yesterday's weight" writes to today. Same race class as P1-3; fix with partial updates/transactions (also `LogRepository.kt:116-135` vs `354-366`).
- **P2-8** `data/rebalance/RebalanceCoordinator.kt:184-296` — accept/decline/dismiss/cancel/plan-edit transitions are unserialized RMWs on the store (only `customize` and `runIfDue` are locked). Route all transitions through one mutex.
- **P2-9** `data/preferences/UserProfilePreferencesStore.kt:53-86` — profile decode throws on an unrecognized enum *value* inside the flow map → every collector crashes (unlike the failure-tolerant coach/rebalance codecs). `runCatching → default`.
- **P2-10** `domain/rebalance/EffectiveTargets.kt:61,87-88` — strict `LocalDate.parse` on persisted plan dates; a hand-edited/corrupt backup restoring an ACTIVE plan crash-loops the dashboard/coach/streak paths. The engine one file over parses leniently — share its helper.
- **P2-11** `ui/train/SessionSummaryViewModel.kt:117-123` — PR detection excludes *all same-day* history → a weaker second session that day is congratulated as a PR. Drop the date filter. And `SessionDetailViewModel.kt:85-89` — the detail PR badge is structurally false for any non-latest session (compares against a set that includes itself).
- **P2-12** `data/repository/WorkoutSessionRepository.kt:32-156` — `startSession`, `replaceSessionExercise`, `reorderSessionExercises` are non-transactional multi-step writes: process death mid-start leaves a permanent half-built ACTIVE session; reorder emits N invalidations per drag. Also `WorkoutSessionDao.kt:50-54` — deleting then adding a set produces duplicate `setNumber`s ("1, 3, 3") with index-based PREV hints shifting onto wrong rows.
- **P2-13** `ui/dashboard/DashboardViewModel.kt:150-151,337-354` — weekly-review persistence dedup ignores `weekStart`: a verdict unchanged across a Monday boundary never writes the new week's row while the VM lives.
- **P2-14** `ui/profile/ProfileViewModel.kt:90-114` — whole-object profile saves from stale UI snapshots can revert a just-made edit (rapid successive changes). Transform-based per-field saves.
- **P2-15** `ui/profile/ProfileScreen.kt:104-116` — photo-picker URIs aren't persistable; `takePersistableUriPermission` throws (swallowed) and the raw URI is stored → avatar silently disappears after restart. Copy into `filesDir`.
- **P2-16** `ui/scanner/BarcodeScannerScreen.kt:122-145` — permanently-denied camera leaves a dead "Grant Permission" button (no rationale check / settings fallback). The camera lifecycle itself is correct.
- **P2-17** `ui/more/MoreScreen.kt:175-181` — Developer tools (live rebalance-state mutation + destructive clear) visible unconditionally in release builds. Gate on `BuildConfig.DEBUG`.
- **P2-18** `data/repository/BackupRepository.kt:46-47,76-79,82-97` — backup `version` is written but never checked on import (future backups silently partial-restore); prefs/rebalance are saved *outside* the Room transaction (exception → hybrid state); "Reset all — permanently delete everything" leaves profile, coach memory, UI prefs, and API keys.
- **P2-19** `AndroidManifest.xml:7-11` + `data/health/HealthSyncWorker.kt` — the 4-hourly background HC sync lacks `READ_HEALTH_DATA_IN_BACKGROUND`; on Android 15+ the read throws SecurityException → `runCatching` → retry → overnight sync silently never works (foreground syncs mask it). *Verify on device, then declare + request the permission.* Compounds P1-1.
- **P2-20** `ui/coach/CoachScreen.kt:165-200,352-371` — `ChatContent` is a different call site per coach state; every phase transition recreates the message list and its `LazyListState` → visible scroll-to-top flash, all bubbles re-record, streaming runs below the fold. Single call site + hoisted list state.

### Performance
- **P2-21 · ViewModel pipelines run forever.** 6 of 7 core-screen ViewModels collect `combine` pipelines in `init` for the VM's whole life (only `StreakViewModel` uses `stateIn(WhileSubscribed)`): every Room write recomputes the Dashboard's 28-day aggregation (+ a potential weekly-review DB write), FoodLog ×4 pipelines, Progress full per-date maps, etc., even while invisible (`ui/dashboard/DashboardViewModel.kt:153-188` and siblings). Convert to `stateIn(WhileSubscribed(5s))`.
- **P2-22 · The live-session hot path.** Per-keystroke whole-entity write → table invalidation → full-session `@Relation` re-query → whole-list unstable-lambda recomposition (no card skips); the same invalidation also re-runs the Train-home stats pipeline over **unbounded full history** (`TrainViewModel.kt:72-145`, off-main but wasted). Debounced/narrow updates + windowed queries (`getCompletedSessionsSince` already exists).
- **P2-23 · Full-history reactive reads.** `StreakRepository.kt:40-70` (6-source combine over all rows, `LocalDate.parse` per row per emission), `CoachContextBuilder.kt:38` (all daily logs ever), `ProgressViewModel.kt:114` (all-time meal entries filtered in memory), `CoachToolExecutor` per-call full-table reads. Add `since(date)` DAO variants.
- **P2-24 · Test/CI infrastructure.** The default unit-test task runs `ai/harness/InsightHarnessTest` live against OpenRouter when `.env.test` exists (this review's run failed on a real 429) — move behind an explicit Gradle property/tag. `usage_events` grows unboundedly with no index on `timestampEpochMs` (`UsageEventDao.kt:23-37`) — index + retention sweep. Backup JSON encode/decode runs on the main thread (`SettingsViewModel.kt:115-126`). Food-library search filters the full NEVO catalog on the main thread per keystroke (`FoodLibraryViewModel.kt:372-377`). Body-history list builds on main without keys (`BodyHistoryViewModelkt:22-37`). Chart scrubbers use all-direction `detectDragGestures` and steal vertical scroll (`SparklineChart.kt:75-107`).
- **P2-25 · Startup.** The whole `AppContainer` graph (≈40 objects, 8+ DataStores, eager Keystore/EncryptedSharedPreferences I/O) is built synchronously in `Application.onCreate`; the `dbReady` splash gate is vestigial (flips before SQLite opens). No baseline profile has ever been generated despite complete plumbing (`app/build.gradle.kts:161`, empty `app/src/release/generated/baselineProfiles/`) — and testers receive **debug** builds (no R8, no AOT), so distributed perf is bounded by the distribution choice.

### Architecture & build
- **P2-26** Domain purity is broken in 4 files: `domain/export/BackupModels.kt`, `domain/food/RecentFoods.kt`, `domain/food/RecipeWithIngredients.kt`, `domain/workout/ExerciseLibraryJson.kt` import Room entities (transitively androidx.room). Relocate to `data/` and add a CI grep. (`domain/plan`/`rebalance` importing `data.preferences` is a documented, verified-pure exception.)
- **P2-27** `RecompDatabase.kt:66` — `exportSchema = false` with `app/schemas/` gitignored: 14 hand-written migrations with no schema history; only 2 are tested and `MigrationTestHelper` is impossible. Migration SQL was diffed against entities and is currently consistent — nothing protects the next one. Flip it on, commit schemas, add a 1→15 chain test.
- **P2-28** Two "sessions this week" definitions disagree (Monday-anchored in `TrainingPlanBuilder.kt:57,92` vs trailing-7 in `WeeklyTrainingBuilder.kt:29-34`) — the Train card and the weekly briefing can show different counts on the same day. Insight pattern detectors judge a 14-day window against a single *current* target while the adherence path resolves per-day targets via `PlanHistory` (`PatternDetectors.kt:34-99` vs `AppContainer.kt:381-446`) — the same briefing can disagree with itself after a mid-window target change.
- **P2-29** Release path is unproven: R8/minified builds are never built by CI, `proguard-rules.pro` papers over reality with blanket `-keep class ... { *; }` rules, and the committed `app/release/` artifacts are stale local outputs. The Firebase distribution action is pinned to a mutable `@v1` tag while receiving a service-account secret; no gradle-wrapper validation.
- **P2-30** `core/util/NumberFormatters.kt:12-14` — dot-only decimal parsing for user-typed numbers in a Dutch-market app; "82,5" from a comma-decimal keyboard parses to null and the entry is silently dropped (verify whether the keyboard permits commas; normalize regardless). Locale-mixed `"%.1f".format(x)` without an explicit locale in several screens compounds it.

---

## P3 — Low (batched themes; each item has full details in the area notes)

1. **Design-token violations concentrated *inside* `ui/component/`** — the design system's own components hardcode `fontSize`/`fontWeight` (~20 sites: `GeneratedInsightCard`, `GlassInputField`, `BadgePill`, `WeekCalorieStrip` 7–9 sp, pickers, `MarkdownText` headings) plus ~20 more across screens (nav labels, scrub header, MetricTiles, WheelPicker). Sweep onto `AppType`; add a `caption`/`micro` token if sub-10 sp is intentional.
2. **Missing semantic status colors** — ~16 raw hex literals for success/warning/error/over-target across `CoachScreen`, `WeeklyBriefingOverlay` (duplicated confirm/cancel chips), `VioletBadge`, `WeekCalorieStrip`, `IntegrationsComponents`, `NutritionStrip`, ScoreBars, etc. `RebalanceViz` already documents the missing success token. Add mode-aware `success`/`warning`/`overTarget` tokens (the dark-tuned pale greens lose contrast on light theme today).
3. **Dead code (~900 lines)** — `LiquidSlider`/`LiquidToggle` (260 lines of the most complex backdrop code, zero call sites), `SectionCard`×2/`ScoreSlider`/`ToggleRow` (banned Material patterns), `ProgressBar.kt` (duplicate of `charts/CalorieProgressBar`), `LogRepository.copyMeals`/`resetEverything` (dead, and `copyMeals` is destructively non-transactional if ever wired), `BarcodeScannerViewModel.saveToLibrary`, `AdjustmentVerdict.label()`, `ActiveSessionViewModel.moveExerciseUp/Down/setExerciseNote`, unused `aiInsightCoordinator` injection in DashboardViewModel.
4. **Design-system drift** — `docs/design-system.md` documents a 36 dp compact button that doesn't exist (only 48/32 dp; 21 of 27 `LiquidActionButton` call sites pass `small = true` — flip the default); bare `ModalBottomSheet` in Onboarding/Profile; hand-rolled segmented pill in TrainHome instead of `GlassSegmentedToggle`; legacy `ui/foods` screen still routed with Material components; legacy Calorie Decision screen violates the header/scaffold rules; 6 screens hand-build the exact `ScreenScaffold` LazyColumn (give `ScreenScaffold` a header slot and delete them); 3 near-identical menu-row implementations (`MenuRow`/`SettingRow`/`DataRow`).
5. **Accessibility** — sub-44 dp touch targets on the most-used controls (food-entry action row 26 dp ×4, slot menu 28 dp, day-nav 26 dp, memory delete 20 dp); the set-grid ✓ has a `contentDescription` only when already completed; `LiquidGlassButton` disabled state is alpha-only (TalkBack announces it enabled); `BackButton` chains `minimumInteractiveComponentSize` *before* `size(40.dp)`; text glyphs as affordances ("→", "↺", "✓", ‹); RIR "+" stepper silently no-ops past 10 (validation throw swallowed); charts and BodyMap expose no semantics.
6. **Small correctness nits** — slot names URL-encoded then decoded twice ("+" → space, `%` names can throw); `MarkdownText` default `color = Color.White` (latent light-mode invisibility); `WeeklyBriefingRepository` enum `valueOf` outside its `runCatching` (a renamed enum crashes the weekly review); `WorkoutMappers` masks corrupt session status as ACTIVE (ABANDONED is the safe default); `completeSession(duration = null)` default *clears* a stored duration; `ExerciseDao` LIKE doesn't escape `%`/`_`; NEVO wide-format import aborts entirely on one duplicate code while long-format silently dedups and accepts negative nutrient values; Samsung CSV negative-calorie rows fail the whole import batch downstream; custom-exercise muscle tags use UI labels the taxonomy doesn't recognize (4 of 6 groups invisible to filters/stats/recovery/body-map); `StreakCalculator` zeroes the current streak on a future-dated entry; `trendPerWeek` returns 0.0 for both "no data" and "flat"; `PlanCalculator`'s "unreachable" negative-carb clamp is reachable (macros stop summing to the calorie target); Discard-session dialogs say "permanently deletes" but ABANDONED rows are retained forever; "reps×weight" vs "weight×reps" formatting differs between screens; usage stats ViewModel injects a DAO directly (the only layering violation of its kind); repo hygiene (tracked `.DS_Store`, stale `app/release/` outputs, ~1.7 MB unused orb PNGs shipped in every debug APK, stray root-level files, `.gitignore` gaps).

---

## What's in good shape (worth calling out)

- **Domain math quality** — the rebalance engine is exemplary: every gate and exact band boundary is pinned by 39 tests; the 1200-kcal floor makes negative targets impossible; `EffectiveTargets` overlays never mutate the base plan; the union-zone rule means a rebalance can never break a streak. The planned-meals flag is enforced consistently in every aggregate checked. `PlanHistory` per-day target resolution is a sound doctrine (the insight detectors are the one consumer that missed it).
- **Test suite** — 1301 tests, 195 test files; serialization codecs, coordinators, detectors, and the coach tool executor are thoroughly covered. The gaps are systematic rather than random: fake DAOs hide all FK/transaction/REPLACE semantics, and the highest-risk flows (backup restore, migrations, `startSession`, concurrency interleavings) have zero coverage.
- **AI architecture** — deterministic-first design (numbers computed by engines, the model only adds prose; verbatim fallbacks on any failure) is robust; the tool-calling regression ("Done." without tool_calls) is genuinely fixed with an honest nudge-then-error path and a model remap; the knowledge pipeline degrades to no-op instead of crashing; the harness lives test-side only.
- **Compose craft in the right places** — the elapsed-session timer (1 Hz tick recomposing a single leaf `Text`, wall-clock drift-free, survives process death), `GlassOrbBackground` (lifecycle-aware sensor, draw-phase parallax), `MarkdownText` streaming tail-reparse, the `lite` glass modes used exactly where repetition matters, nav-bar backdrop capture gated to top-level routes, and the dialog-safe `LiquidSegmentedToggle` with the institutionalized unbounded-height guard.
- **Data hygiene** — all migrations diffed column-by-column against entities are consistent; FK relationships are mostly sensible; ISO-string date comparisons are correct; steps provenance reconciliation is well-designed and well-tested.

## Cross-cutting themes

1. **The backup/restore path is the single most dangerous surface** (P0-1, P0-2, P2-18) and has essentially no tests. One real-Room export→wipe→import round-trip test would have caught both P0s.
2. **Time is treated as a constant.** Frozen `today` in five ViewModels, the frozen coach snapshot, steps frozen at last sync, wall-clock in `readStepsHistory` — a reactive `todayFlow()` and consistent `DateProvider` injection solves a whole class.
3. **Read-modify-write without transactions** is the recurring race shape: HC sync ×3 writers, `log_metric` vs check-in, profile saves, rebalance transitions, session multi-step writes. A `withTransaction` habit plus partial UPDATEs closes them.
4. **Two implementations of the same concept keep disagreeing:** trend math (Dashboard vs Trends), "sessions this week" (Train vs briefing), per-day vs static targets (adherence vs pattern detectors), trend color semantics (red-when-losing vs good-when-losing). Each needs one shared, goal-aware helper.
5. **Fakes hide the database.** Only one test runs against real Room; FK enforcement, REPLACE semantics, AUTOINCREMENT behavior, and transaction boundaries are invisible to CI — which is exactly where P0-1/2, P1-19/20/21 live.
6. **Docs had drifted from reality** (Gemma/LiteRT architecture deleted, schema v13→15, target SDK 35→37, single-module→two, "fully offline" README) — corrected in this branch alongside this review.
