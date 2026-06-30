# 05 — Workout / Training / Routines / Exercises

Scope: the Train tab and everything under it — routine builder, active session, session
history/summary/detail, exercise picker, exercise library, and the per-muscle/per-exercise
stats. This area is **feature-complete and polished**; this plan is deliberately narrow —
**edge-case hardening + polish**, not new surface area. Every finding below was verified
against the current code and is cited `file:line`.

> Layering rule (from `CLAUDE.md`): UI → ViewModels → Repositories → Room/DataStore; `domain/`
> is pure Kotlin. Any change here must respect that and the design-system rules in
> `docs/design-system.md` (no bare `fontSize`/`fontWeight`, `ScreenScaffold`/`SubScreenHeader`
> frames, the `Liquid*`/`Glass*`/`*Card` component families, 16dp gutter).

---

## 1. Current state & problems

The training stack is mature: Room is the source of truth (`WorkoutSessionDao`,
`ExerciseDao`, `WorkoutDao`); domain logic is genuinely pure (`TrainStatsBuilder`,
`ExerciseStatsCalculator`, `WorkoutProgressAnalyzer`, `MuscleCategory`); ViewModels are thin
and observe Flows. The problems are all latent edge cases and small polish gaps, not
architectural faults.

Verified findings:

1. **`SessionSetEntity.completed` defaults to `true`** —
   `data/local/entity/SessionSetEntity.kt:29` (`val completed: Boolean = true`). Semantically
   wrong: a freshly created set is *not* completed. In practice every construction site
   overrides it to `false` (`WorkoutSessionRepository.startSession` line 75, `addSet` line 95,
   `replaceSessionExercise` line 146; DAO `insertNextSet` copies an existing entity), so it is
   currently a **latent bug**, not a live one. But the default is a trap: any future call site
   that omits the argument silently writes a completed set, which would corrupt
   `TrainStatsBuilder` (counts exercises with `>=1 completed set`,
   `domain/workout/TrainStatsBuilder.kt:36`) and the `getExerciseHistory` query
   (`WorkoutSessionDao.kt:101`, `st.completed = 1`).

2. **Exercise-library `VERSION` is a hardcoded date string** —
   `data/repository/ExerciseLibraryRepository.kt:60` (`const val VERSION = "2026-06-17"`).
   `seedIfEmpty` (line 47) only re-seeds when the stored version differs **or** the table is
   empty (line 49). So if `assets/exercises/*.json` is refreshed without a human remembering to
   bump this constant, **users keep stale exercise data forever**. The coupling between "edit the
   JSON" and "bump the const" is invisible and manual.

3. **In-session exercise notes are write-only-in-code, dead in UI** —
   `ActiveSessionViewModel.setExerciseNote()` exists
   (`ui/train/ActiveSessionViewModel.kt:330`) and the repo/DAO plumbing is complete
   (`WorkoutSessionRepository.setSessionExerciseNote` line 158 →
   `WorkoutSessionDao.updateSessionExerciseNote` line 84). But **no composable calls it** —
   `grep` for `setExerciseNote` returns only the VM definition. The *session*-level note has a
   full UI (`ActiveSessionScreen.kt:240-256`, `SessionNoteField`); the per-exercise note does
   not. Per-exercise notes set in the routine builder are carried into the session
   (`startSession` line 62) but become read-only once the session starts.

4. **Prefill assumes one row per exercise per routine** —
   `WorkoutSessionRepository.startSession` line 49-51 groups the previous session's exercises by
   `exerciseId` and takes `exs.first()`. If a template legitimately contains the same exercise
   twice (e.g. two Bench Press blocks at different rep ranges), **only the first instance's
   history prefills**; the second falls back to blank (`reps=0, weightKg=null`). The comment at
   line 50 acknowledges this ("if duplicated, take the first").

5. **`replaceSessionExercise()` wipes all sets on swap** —
   `WorkoutSessionRepository.kt:130-150`. Swapping an exercise mid-session deletes every set and
   re-creates the same *count* of blank sets (line 137-148). This is a defensible default
   (reps/weights are exercise-specific) but it is **destructive and unrecoverable** — a mis-tap
   on swap loses logged work with no undo.

6. **`TrainStatsBuilder` silently drops unmapped exercises** —
   `domain/workout/TrainStatsBuilder.kt:46` (`muscleCategoryFor(muscles) ?: return@mapNotNull
   null`). `muscleCategoryFor` (`domain/workout/MuscleCategory.kt:21-32`) maps only a fixed set
   of free-exercise-db muscle strings to the six categories; anything unmapped (neck, custom
   user exercises with `primaryMuscles = "[]"`, or a future DB string) **vanishes from the Stats
   screen with no feedback**. A user who logged a custom exercise will never see it in stats and
   gets no explanation.

7. **No in-UI error surfacing anywhere in Train** — every mutating VM method wraps the repo call
   in `runCatching { … }` and discards the result
   (`ActiveSessionViewModel.kt:128, 147, 165, 186, 204, 217, 231, 241, 253, 270, 286, 295, 308,
   324, 332, 352, 365`). A `grep` for `Snackbar|Toast|errorMessage|_error` across
   `ui/train/` returns **nothing**. A failed save (validation throw from
   `WorkoutValidation.validateSet`, `WorkoutSessionRepository.kt:84-85, 100-101`, or a DB error)
   shows the user **nothing** — the value silently reverts on the next Flow emission.

8. **No bulk session delete; history & stats load all rows unbounded** —
   `WorkoutSessionDao.observeCompletedSessions()` (`WorkoutSessionDao.kt:71-73`) is
   `SELECT * … ORDER BY date DESC` with **no LIMIT** and `@Transaction` (so it hydrates every
   session's full exercise+set graph). `TrainViewModel` consumes it whole
   (`ui/train/TrainViewModel.kt:34`) and also feeds it to `TrainStatsBuilder.build`. There is a
   single-row `deleteSessionById` (`WorkoutSessionDao.kt:24`) but **no bulk delete** and no
   pagination. At high session volume this is a memory/perf risk (full object graph
   materialised on every emission).

9. **`DurationWheelMath` backs a bespoke wheel picker** —
   `ui/train/DurationWheelMath.kt`. The math itself is small and correct (`durationToHm` /
   `hmToSeconds`, clamped). The liability is the **custom wheel UI** it feeds, which is
   hand-rolled and outside the design-system component set — a maintenance/consistency cost, not
   a correctness bug.

10. **`startSession` prefill fetch is not atomic with insert** —
    `WorkoutSessionRepository.startSession` (lines 33-80) does `insertSession` (line 33), then a
    *separate* `getLastCompletedSession` read (line 47), then the per-exercise inserts — all
    outside a single `@Transaction`. A concurrent completion between the insert and the prefill
    read could in theory prefill from a different "last" session. **Low risk** (sessions are
    single-user, serial in practice) but not guaranteed atomic.

---

## 2. UX improvements

Ordered roughly by value/effort.

1. **In-session per-exercise note editing.** Wire the already-existing
   `ActiveSessionViewModel.setExerciseNote()` (`ActiveSessionViewModel.kt:330`) to a UI control
   on each exercise card in `ActiveSessionScreen`/`ExerciseCard`. Reuse the existing
   `SessionNoteField` pattern (`ActiveSessionScreen.kt:253`) — a compact "Add note" affordance
   that expands to a `GlassTextArea`, committing `onFocusLost`. This closes finding #3 with
   almost no new plumbing.

2. **Error toasts/snackbars on failed saves.** Surface the swallowed failures from finding #7.
   Have the mutating VM methods emit a one-shot error event (a `Channel`/`SharedFlow` of a small
   `UiError` type) when `runCatching` fails, and collect it in the screen into a snackbar host.
   The most user-visible case is a validation failure on set entry (`WorkoutValidation`), which
   today just reverts silently. **Per the project memory ("don't fake success"), a failed write
   must tell the user.**

3. **"Swap keeps sets" option.** For `replaceSessionExercise` (finding #5), offer a choice when
   swapping mid-session: *Replace & keep sets* (repoint `exerciseId`/name, leave reps/weights) vs
   *Replace & clear* (current behaviour). Keep-sets is the better default for a like-for-like
   swap (e.g. barbell → dumbbell bench); clear is right for an unrelated exercise. At minimum, do
   not clear silently.

4. **Search match highlighting in the exercise picker.** `ExercisePickerViewModel` already has
   the query (`ui/train/ExercisePickerViewModel.kt:51, 56-62`) and fuzzy search via
   `ExerciseDao.search`. Add match-substring highlighting in the result rows (see UI section).
   `SessionSummaryScreen` already uses `buildAnnotatedString`/`SpanStyle`
   (`SessionSummaryScreen.kt:49-50, 292-295`), so the pattern exists in-app.

5. **Bulk session delete in history.** Add multi-select + delete to the history list (backed by a
   new bulk-delete DAO query, see data section), so users can clear out test/abandoned sessions
   without one-at-a-time taps.

6. **Undo on destructive actions.** Pair the snackbar from #2 with an **Undo** action for the
   genuinely destructive operations: session delete, exercise swap (clear-sets), remove
   exercise, remove set. Cache the deleted entity/graph in the VM until the snackbar dismisses,
   then re-insert on Undo. Highest value on swap-clear (#5) and bulk delete (#5 above).

---

## 3. UI improvements

All changes here must conform to `docs/design-system.md`.

1. **Design-system conformance pass on Train screens.** Audit `ui/train/*` for the standard
   violations: bare `fontSize`/`fontWeight` on `Text`, raw `.background().border()` cards instead
   of `FrostedCard`/`NeutralCard`, Material `Button`/`Switch` instead of `Liquid*`/`Glass*`,
   non-16dp gutters. New controls added by this plan (note field, error host, highlight) must use
   tokens from the start (`AppType`, `LocalAppColors`, `Spacing`, `Liquid*`).

2. **Match-highlighting component.** A small reusable helper that takes `(fullText, query)` and
   returns an `AnnotatedString` bolding/accent-tinting the matched span(s), using
   `AppType.cardTitle` as the base and `LocalAppAccent.current.accent` for the match. Place it as
   a shared util (e.g. under `ui/component/`) so the food library / coach search can reuse it,
   not just the exercise picker.

3. **Error-surfacing component.** A single app-styled snackbar host wired into `ScreenScaffold`
   (or a thin wrapper) so any screen can raise a one-shot error/undo message with consistent
   glass styling, rather than each screen hand-rolling a `Toast`. This is the UI half of the
   error-handling refactor in section 8.

4. **"Unmapped" affordance for dropped stats exercises** (finding #6). Rather than silently
   dropping, surface a quiet count ("3 exercises not shown — no muscle group set") on the Stats
   entry screen, optionally tappable to list them. This needs a small `TrainStatsBuilder` change
   (return the unmapped set alongside the categories) — see data section.

---

## 4. Data / model improvements

1. **Fix `SessionSetEntity.completed` default → `false`** (finding #1). Change
   `SessionSetEntity.kt:29` to `val completed: Boolean = false`. This is the semantically correct
   default and removes the latent trap. No Room migration needed (the stored column is unchanged;
   this only affects the Kotlin default for new in-memory constructions). Verify the three
   explicit `completed = false` call sites are unaffected and add a unit test asserting a
   default-constructed set is incomplete.

2. **Library versioning strategy** (finding #2). Replace the manually-bumped date constant
   (`ExerciseLibraryRepository.kt:60`) with a **content-derived version** so refreshing the JSON
   auto-triggers a re-seed. Options, cheapest first:
   - **(a)** Hash the bundled JSON bytes at first read and use that hash as the `version` passed
     to `seedIfEmpty`. The seed condition (`storedVersion == version`,
     `ExerciseLibraryRepository.kt:49`) then changes automatically whenever the file content
     changes — zero human discipline required. Cost: one hash of the asset on cold start.
   - **(b)** If hashing the whole file is too costly, embed a `version` field *inside* the JSON
     and bump it as part of editing the data (still manual, but co-located with the data instead
     of in a separate Kotlin file).
   Prefer (a). Either way, document the chosen contract next to `SOURCE`/`VERSION`.

3. **Dedupe-aware prefill** (finding #4). In `startSession`
   (`WorkoutSessionRepository.kt:46-53`), stop collapsing duplicate exercises to `first()`.
   Instead match the previous session's rows to the template's rows **positionally per
   exerciseId** (e.g. zip the Nth occurrence of an exercise in the template to the Nth occurrence
   in the previous session), so a template with two Bench Press blocks prefills both. Keep the
   graceful blank fallback when there's no corresponding prior row.

4. **Pagination / bounded loads for history & stats** (finding #8). Two parts:
   - Add a **bulk delete** DAO method (`@Query("DELETE FROM workout_sessions WHERE id IN
     (:ids)")`) plus repo + VM wiring for the bulk-delete UX (section 2).
   - Bound `observeCompletedSessions`. The history list should page (e.g. `LIMIT/OFFSET` or a
     `PagingSource`), and — importantly — **stats should not depend on hydrating every session's
     full set graph**. Consider a lighter projection query for `TrainStatsBuilder` input (only
     the fields it reads: exerciseId, exerciseName, date, and a `hasCompletedSet` flag) so the
     stats screen scales independently of history size. This is the data half of the section-8
     pagination work; keep it as a follow-up once the cheaper fixes land.

5. **`startSession` atomicity** (finding #10). Wrap the prefill-read + all inserts in a single
   `@Transaction` DAO method so the "last completed session" read and the new session's inserts
   are consistent. Low priority given single-user serial usage, but cheap to do correctly when
   touching `startSession` for finding #4.

6. **Stats unmapped surfacing** (supports finding #6 / UI #4). Change `TrainStatsBuilder.build`
   to also return the exercises whose `muscleCategoryFor` is null (currently dropped at
   `TrainStatsBuilder.kt:46`) so the UI can show a count instead of silently hiding them. Pure
   domain change, fully unit-testable.

---

## 5. AI opportunities

Today the coach is **food/metric-only**. Verified: `COACH_TOOLS`
(`ai/GemmaCoachCoordinator.kt:43-48`) exposes `get_today_summary`, `get_weekly_trends`,
`search_food_library`, `log_meal`, `log_metric`, `update_calorie_target` (+ optional
`search_web` line 60); `CoachToolExecutor.kt` has **no** workout/exercise/session reads. The
coach can see the `trained` boolean on the daily log (`CoachToolExecutor.kt:54`) but knows
nothing about lifts, volume, or progression.

**Should the coach see lifts? Yes, but modestly — read-only and pre-fetched, not a new tool.**

The constraints in `docs/ai-coach.md` are explicit and binding here:
- *"Never add a tool for data that is static or session-invariant."* and *"every unnecessary
  tool call risks hitting the cap"* (the 2B model has a 5-iteration cap, `MAX_TOOL_ITERATIONS`).
- The 2B behavioural notes warn that more rules / more tools cause wrong-rule matching and empty
  responses after tool sequences.

Recommended (cheapest, lowest-risk first):

1. **Insight card, not chat.** The lowest-risk lever is a **workout insight card** via
   `GemmaInsightCoordinator` (single-turn, no tool calls) rather than a coach chat tool. Feed it
   a pre-computed one-line training summary (last session, weekly set volume per muscle from
   `TrainStatsBuilder`/`WorkoutProgressAnalyzer`) and let it produce a verdict, exactly like the
   food insight cards. No new tool, no tool-iteration risk.

2. **If chat awareness is wanted: extend the pre-fetched snapshot, do NOT add a workout tool.**
   The system prompt already pre-fetches today's food snapshot (`docs/ai-coach.md`, "Today's
   snapshot"). Add a compact, **read-only** "recent training" block to the same static prompt
   (e.g. last session date, top exercises, weekly set counts) computed once at
   `createConversation()`. This respects the doctrine ("pre-fetch the common read, don't burn a
   tool iteration") and gives the coach lift context for free.

3. **Avoid for now: a `get_workout_history` / `log_set` tool.** A workout *read* tool adds
   another rule the 2B model can mis-match; a workout *write* tool adds another
   confirmation-flow surface. Both fight the model's iteration cap for marginal benefit. Keep
   training data **read-only and static-injected** until (if ever) the app moves to the cloud
   coordinator (`CloudCoachCoordinator`), which has more tool headroom.

Keep this section deliberately small — the AI win here is "the coach can *mention* your training
when relevant," not "the coach manages your training."

---

## 6. Quick wins

These are low-risk, high-clarity, mostly single-file:

- **Flip `SessionSetEntity.completed` default to `false`** (`SessionSetEntity.kt:29`) + a unit
  test. (Finding #1.)
- **Wire `setExerciseNote()` into the exercise card UI** (finding #3) — plumbing already exists,
  it's a UI-only addition reusing `SessionNoteField`.
- **Content-hash library version** (finding #2, option 2a) — small change in
  `ExerciseLibraryRepository.seedIfEmpty` + `AppContainer` seed call; removes a recurring manual
  footgun.
- **Surface unmapped-exercise count on the Stats screen** (findings #6) — pure
  `TrainStatsBuilder` return-shape change + a one-line UI label.
- **Search match highlighting in the exercise picker** (UX #4) — reuse the existing
  `buildAnnotatedString` pattern from `SessionSummaryScreen`.

---

## 7. Medium improvements

- **Error/undo snackbar host** wired into Train screens (UX #2/#6, UI #3) — needs a one-shot
  event channel in the affected ViewModels and a shared snackbar component.
- **"Swap keeps sets" option** for `replaceSessionExercise` (UX #3, finding #5) — repo method
  variant + a choice in the swap UI + ideally undo.
- **Dedupe-aware prefill** in `startSession` (finding #4) — positional matching + tests, done
  while making the same method atomic (finding #10).
- **Bulk session delete** — DAO `DELETE … WHERE id IN (:ids)` + multi-select history UI + undo.

---

## 8. Bigger refactors

Tackle only after the quick/medium wins; each touches multiple files.

1. **Consistent error-handling pattern across Train ViewModels.** Today every mutator is
   `viewModelScope.launch { runCatching { … } }` with the result thrown away
   (`ActiveSessionViewModel`, and the same idiom in the sibling Train VMs). Introduce a small
   shared pattern — a `launchCatching` helper or a base-VM `errors: SharedFlow<UiError>` — so
   that:
   - failures are emitted as one-shot UI events (feeding the section-3 snackbar host) instead of
     silently swallowed, and
   - the boilerplate `runCatching {}` wrapper is centralised.
   This is a cross-cutting change but mechanical; do it once and apply across
   `ActiveSessionViewModel`, `SessionDetailViewModel`, `RoutineBuilderViewModel`,
   `ExerciseStatsViewModel`, `TrainViewModel`. (It also benefits the food/body VMs later.)

2. **Pagination layer for history & a lighter stats projection** (finding #8). Replace the
   unbounded `observeCompletedSessions()` with a paged source for the history list and a
   slim projection query for stats input, so memory/perf scale with screen size, not total
   session count. This is the larger of the two and should land last — it's only worth it once a
   real user has accumulated many sessions, and it interacts with the bulk-delete and
   stats-unmapped changes, so sequence it after those.

3. **Replace the bespoke duration wheel** (finding #9). `DurationWheelMath` is fine; the custom
   wheel *UI* is the liability. Replace it with a design-system-conformant duration control (or a
   simple `GlassInputField` hours/minutes pair) and delete the bespoke picker. Pure polish — do
   it opportunistically, not urgently.

---

## 9. What to avoid for now

- **Don't add a workout read/write tool to the 2B coach** (see section 5) — it fights the
  iteration cap and the model's rule-matching for marginal benefit. Pre-fetch into the static
  prompt or use an insight card instead.
- **Don't build a Room migration for the `completed` default change** — the stored column is
  unchanged; only the Kotlin default moves. No schema bump.
- **Don't rewrite the routine builder / drag-reorder / body-map** — these are polished and
  working (`ReorderSupport`, `BodyMap`, `SwipeToRevealRow`); leave them alone.
- **Don't over-build pagination prematurely.** It's a real risk at volume but a non-issue for a
  fresh user; do the cheap correctness fixes first and land pagination last (section 8).
- **Don't gold-plate the "swap keeps sets" feature** into a full set-by-set merge UI — a binary
  keep/clear choice with undo is enough.
- **Don't change `muscleCategoryFor`'s mapping table** to chase every free-exercise-db string —
  surface the unmapped count instead (section 4.6); expanding the map is whack-a-mole.

---

## 10. Suggested implementation order

1. **Correctness footguns (data, tiny):** flip `SessionSetEntity.completed` default to `false`
   (finding #1) + test; content-hash the library version (finding #2). Lowest risk, removes two
   latent traps.
2. **Dead-feature revival (UI, tiny):** wire in per-exercise note editing (finding #3); add
   exercise-picker match highlighting (UX #4); surface the unmapped-stats count (finding #6).
3. **Error surfacing (cross-cut, medium):** build the shared snackbar/error host + the
   `launchCatching`/`errors` pattern (sections 3, 8.1), starting with `ActiveSessionViewModel`.
   This unblocks every "tell the user it failed" item.
4. **Destructive-action safety (medium):** "swap keeps sets" option (finding #5) and undo, now
   that the snackbar host exists; bulk session delete with undo.
5. **Prefill correctness (data, medium):** dedupe-aware positional prefill (finding #4) + make
   `startSession` atomic (finding #10) in the same pass.
6. **Polish:** replace the bespoke duration wheel (finding #9).
7. **Scale (big, last):** pagination for history + lighter stats projection (finding #8 /
   section 8.2) — only once it's warranted by real session volume.
8. **AI (independent, low priority):** workout insight card and/or a read-only training block in
   the coach's static snapshot (section 5) — can slot in any time after step 1; keep it modest.

> Rationale: steps 1-2 are near-zero-risk correctness/visibility wins; step 3 is the keystone
> that the destructive-action and validation-feedback work (steps 4-5) depend on; pagination
> (step 7) is deferred because it's the most invasive and least urgent for current usage.
