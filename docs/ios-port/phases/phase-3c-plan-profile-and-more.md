# Phase 3c — Plan, Profile, and the More hub

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans`. Read `docs/ios-port/STATUS.md` and
> `docs/ios-port/decisions.md` first.

**Goal:** Close the plan-ledger correctness gap, then ship the settings spine — Plan, Profile, Body
History, Body Edit, Appearance, and the More hub that reaches all of them.

**Architecture:** No new domain. A Swift `PlanRepository` becomes the single choke point for plan
writes (mirroring Android's), and six screens are `@Observable` models over the existing stores and
queries (**D19**), pushed onto the Home tab's `NavigationStack` from a More hub reached by the
Dashboard's toolbar avatar (**D17**).

**Tech Stack:** SwiftUI on iOS 26, GRDB, `JSONStore` preference actors, `:shared` for
`PlanHistory` / `PlanGenerator` / `PlanTargets`.

---

## 🔴 Why this phase exists, and what it must fix first

**`plan_versions` has five readers on iOS and no writer.** Verified at the time of writing:
`Domain/TargetResolution.swift`, `Features/Dashboard/DashboardModel.swift`,
`Features/Streaks/StreakModel.swift`, `Features/Streaks/StreakRules.swift` and
`Features/Rebalance/RebalanceCoordinator.swift` all resolve targets through the ledger, and the only
thing that has ever written a row is a backup restore.

Today that is harmless — an empty ledger makes every resolution take its `…OrFallback` branch, which
is the current plan, which is correct when the plan has never changed. **It becomes wrong the moment
this phase ships a Plan screen**, because editing targets would retroactively re-judge every day
already logged. Adherence, streaks, the seven-day chart and the rebalance evaluation would all
silently restate history.

So Task 1 is not a screen. It is the choke point, and it lands before anything can edit a plan.

## Scope: 3c and 3d

The 3c bucket in the ledger listed ten screens (~2,700 LOC of Compose) — larger than 3b, which took
18 tasks for ~1,900. It is split:

| | Screens | Why together |
|---|---|---|
| **3c** (this) | Plan · Profile · Body History · Body Edit · Appearance · More | The plan/settings subsystem. Shares the preference layer, and closes the ledger gap plus three dangling `nil`s in already-shipped screens. |
| **3d** (next) | Onboarding · Progress/Trends · Usage Stats · Developer | First-run wizard plus read-only analytics. Independent of the above; Onboarding alone is a 21-field state machine. |

Each produces working software on its own.

## What this phase closes in already-shipped code

Three surfaces shipped in 3a/3b with a deliberate `nil` where a destination did not exist yet. Each
is one line once its target lands — and each currently renders as a *non-control*, which is the
convention (`CheckInHistoryRow` hides its affordance rather than offering a dead tap):

| Dangling | Where | Becomes |
|---|---|---|
| Dashboard avatar, inert | `Features/Dashboard/DashboardScreen.swift`, the `avatar` toolbar item | The **More** entry point (**D17**) |
| `onViewHistory: nil` | `Features/Body/BodyScreen.swift` init | Push **Body History** |
| Calorie decision screen | `Features/Dashboard/CalorieDecisionScreen.swift` — built in 3b, no entry point | Reached from **More** |

## Decisions taken for this phase

Append these to `decisions.md` as they are confirmed in code, not up front.

**D33 · A Swift `PlanRepository` owns every plan write.** Android's `PlanRepository.save()` is
explicitly *"the single choke point: every plan writer routes through save(), so history stays
complete and non-target edits (e.g. the Health Connect toggle) never create a version."* iOS has no
equivalent — `PlanPreferencesStore` exposes `save`, `resetDefaults` and `setCalorieTarget`, and all
three bypass the ledger. The store keeps its low-level writes; the repository becomes the only thing
features are allowed to call.

**D34 · `selectedFont` is dropped from the UI.** The long-open question (STATUS, *Conventions still
to decide*). Android stores it, shows a `FontPicker`, reads it in `AppearanceViewModel:23` — and
**no `res/font` directory exists**, so it has never changed a glyph. Phase 2 kept the stored field
so backups round-trip and gave it no setter; that stays. The Appearance screen ships **without** a
font row rather than porting a control that does nothing. Revisit only if custom fonts are ever
added.

**D35 · Body Edit reuses `CheckInDraft`.** Android's `BodyEditUiState` and its check-in sheet state
are the same nine fields (`bodyWeightKg`, `waistCm`, `waistSkinfoldMm`, `steps` + `stepsEdited`,
`sleepHours`, the three scores, `trained`, `notes`). iOS already has that as
`Features/Body/CheckInDraft.swift`, validated and tested. Body Edit is the same draft seeded from a
past date — not a second parallel struct.

**D36 · More is pushed from Home, not a tab.** Already implied by **D17** and by `AppTab`'s own
note. Recorded properly here because this is the phase that makes it real.

## 🔴 Screenshots

**Ask the user before building any 🖼️ task.** The 3b lesson stands: *screenshots beat reading the
Kotlin* (**D15**) — the week strip turned out to be a bar chart, the calorie hero 22pt not 36pt.
Needed, named as they should be saved into the iOS repo's `screenshots/`:

- `17-plan-screen.jpg` — targets, thresholds, the zone row
- `18-plan-generate-dialog.jpg` — the preview state, and the weight-entry state if reachable
- `19-profile-screen.jpg` — top through the step goal
- `20-profile-option-sheet.jpg` — one of the pickers open
- `21-more-hub.jpg` — both sections
- `22-appearance-screen.jpg` — the accent grid and theme mode
- `23-body-history.jpg` — logged and missing rows together
- `24-body-edit.jpg`

If a screenshot cannot be produced, build from the Kotlin and **flag the task** in the ledger as
built-blind, exactly as 3b did for the calorie decision screen.

## Context you need

- **Read the Kotlin, not this brief.** 3a's plan was wrong about Android four times and every one
  was caught by an agent who opened the source. A brief is not evidence.
- Android sources: `ui/plan/PlanScreen.kt` (341) + `PlanViewModel.kt` (235),
  `data/repository/PlanRepository.kt`, `ui/profile/ProfileScreen.kt` (592) + `ProfileViewModel.kt`
  (115), `ui/more/MoreScreen.kt` (318), `ui/appearance/AppearanceScreen.kt` (137) +
  `AppearanceViewModel.kt` (54), `ui/body/BodyHistoryScreen.kt` (140) + `BodyHistoryViewModel.kt`
  (38), `ui/body/BodyEditScreen.kt` (96) + `BodyEditViewModel.kt` (118).
- **Scope `:shared` first** (the 3b finding: 41 of 41 symbols were already exported). `PlanHistory`,
  `PlanGenerator`, `PlanGenerationOutcome`, `GeneratedPlan`, `PlanTargets`, `PlanVersion` all live in
  `shared/…/domain/plan/`. Confirm each is exported before writing a Swift equivalent of anything.
- iOS conventions that bite: no `didSet` on `@Observable` (3a); `nonisolated` does not propagate
  into extensions; `.task(id:)` is `flatMapLatest`; every number goes through `AppNumber` (**D28**);
  every screen title is `.screenTitle(_:subtitle:)` (**D32**) and a pushed screen gets **no**
  subtitle unless it is day-scoped.

---

## Task 1: `PlanRepository` — the ledger choke point 🔴

**Files:** create `Persistence/PlanRepository.swift`, `RecompTrackerTests/PlanRepositoryTests.swift`

The whole correctness fix. Transcribe `data/repository/PlanRepository.kt`:

- `save(_:)` — read previous prefs, write new prefs, and **only if**
  `PlanHistory.targetsChanged(old:new:)` upsert a `plan_versions` row with
  `effectiveFrom = today` and `createdAt = now` (ISO-8601). Upsert by date, so several edits on one
  day collapse to the final value.
- `resetDefaults()` — always upserts a version for today. Not conditional on Android; do not make it
  so.
- `planOn(_:)` / `targetsByDate(_:)` / `observeVersions()` — read paths, delegating to
  `PlanHistory.planOnOrFallback` and `PlanHistory.resolve`.

🔴 **`targetsChanged` compares only the six day-judging fields** (calories, P/C/F, both zone bounds)
because `PlanTargets` holds only those. A Health Connect toggle or a threshold edit must **not**
create a version. Pin that with a test — it is the difference between a ledger and a changelog.

⚠️ **`createdAt` and `today` are injected, not read from the clock.** `DateProvider` equivalent for
the date, and a `now` closure for the timestamp. 3a's plan shipped a clock-reading `activate()` and
it made the tests non-hermetic.

**Tests:** a target edit appends exactly one version; a non-target edit appends none; two edits on
one day leave one row with the final value; `resetDefaults` always appends; resolution after an edit
gives the *old* targets for a day before `effectiveFrom` and the new ones after.

## Task 2: Route every writer through it

**Files:** modify `Persistence/Preferences/PlanPreferencesStore.swift`,
`Features/Dashboard/DashboardModel.swift`, `Features/FoodLog/FoodLogModel.swift`,
`Features/FoodLibrary/FoodLibraryModel.swift`, `Features/Rebalance/RebalanceCoordinator.swift`

Reads stay on the store. **Writes** must go through `PlanRepository`. Today the writers are
`save`, `resetDefaults` and `setCalorieTarget` on the store itself — the third is the one that will
be forgotten, because it is the coach's `update_calorie_target` tool path and has no UI yet.

**Make bypass structurally hard, not discouraged by a comment — Android already shows how.** It
declares a one-method interface, `data/preferences/PlanPreferencesSource.kt`, whose only two
references in the whole app are `PlanRepository`'s constructor parameter and the `AppPreferences`
class that implements it. The raw save is not reachable from a ViewModel because nothing else can
name the type. Do the equivalent in Swift — a small protocol the repository holds, or `internal`
write methods with the repository as the only caller — and pin it with a grep test if neither is
airtight.

⚠️ **The rebalance must keep bypassing it, and that is correct.** `EffectiveTargets` is an *overlay*
that never mutates the base plan (`CLAUDE.md`, Source of Truth). Check `RebalanceCoordinator` writes
only its own store and add a comment saying why it is exempt, so the next reader does not "fix" it.

⚠️ **Backup restore is the other exempt writer** — it writes `plan_versions` rows directly, because
it is restoring a history rather than making one. Confirm `BackupRepository` still does, and that a
restore does not also append a spurious "today" version.

## Task 3: `PlanModel`

**Files:** create `Features/Plan/PlanModel.swift`, `RecompTrackerTests/PlanModelTests.swift`

`@Observable`, over `PlanRepository` + `UserProfileStore` + `DailyLogQueries`. Eleven text fields
plus `useMetricUnits`, mirroring `PlanUiState`.

Traps, all verified in `PlanViewModel.kt`:

- 🔴 **Editing calories re-centres the zone** by `PlanPreferences.calorieZoneMargin` (100, already on
  the iOS struct) — lower = target − 100, upper = target + 100. **A non-numeric in-progress edit
  leaves the zone at its last valid value**, so a half-typed "2" does not blow the zone away.
- 🔴 **`dirty` gates the store subscription.** The model observes preferences, but a live edit must
  not be overwritten by an incoming emission. Android: `if (current.dirty) current else …`.
- Save validates every field parses, then that `zoneLower < zoneUpper`, with the two distinct
  messages Android uses ("Enter valid numeric targets and thresholds." / "Zone lower bound must be
  less than upper bound."). Use `screenBanner(_:)` (Wave 1 component), not a bespoke `Text`.

## Task 4: Plan generation

**Files:** modify `Features/Plan/PlanModel.swift`, create `Features/Plan/PlanGenerationSheet.swift`

`PlanGenerationOutcome` from `:shared` has three cases and each has its own UI:

| Outcome | UI |
|---|---|
| `Ready(plan)` | Preview sheet → **Apply** writes the six fields into the draft and marks it `dirty` (it does **not** save) |
| `NeedsWeight` | Weight-entry sheet, with its own inline error on a non-positive value |
| `MissingProfileFields(fields)` | No sheet — a banner naming the fields |

⚠️ **The seed weight is the latest *logged* weight**, not the profile's: the max-by-date daily log
that has a `bodyWeightKg`. Only when there is none does `NeedsWeight` appear.

Use `appSheet()` (content-sized) — this is a short sheet and a fixed detent would leave it floating.

## Task 5: The Plan screen — 🖼️

**Files:** create `Features/Plan/PlanScreen.swift`

Pushed, so `.screenTitle("Plan")` with **no** subtitle (not day-scoped). Fields via `LabelledField`;
the metric toggle via the existing glass toggle; Save and Generate as `.glassProminent` /
`.glass`. Sections with `SectionLabel`.

## Task 6: `ProfileModel`

**Files:** create `Features/Profile/ProfileModel.swift`, `RecompTrackerTests/ProfileModelTests.swift`

⚠️ **Do not transcribe the shape.** `ProfileViewModel` is *"7 separate StateFlows, no single
UiState"* (screen inventory) — that is the pattern **D19** exists to replace. One `@Observable`
model with stored properties.

Content: the nine `UserProfilePreferences` fields already on iOS, plus two derived read-onlys —
current weight (latest logged) and **7-day average steps**. Input filtering is load-bearing and
Android does it in the setter: height is digits-only capped at 3, step goal digits-only capped at 5.

## Task 7: Profile screen + the option sheet — 🖼️

**Files:** create `Features/Profile/ProfileScreen.swift`, `Features/Profile/OptionSheet.swift`

The enum pickers (`BiologicalSex`, `ActivityLevel`, `FitnessGoal`) go through one reusable option
sheet — Android's `ProfileScreen.kt:491`, which the inventory flags as a **raw `ModalBottomSheet`
leaking the Material default surface**. Ours is `appSheet()`, so that bug does not port.

⚠️ **The profile photo does not ship in 3c.** `profilePhotoPath` stays stored and round-tripped;
Android's picker has a documented flaw (*copy into container, no persistable URI*) and PhotosUI is
its own task. The avatar keeps its placeholder glyph. Say so on screen only if Android does.

## Task 8: Body History — 🖼️

**Files:** create `Features/Body/BodyHistoryModel.swift`, `Features/Body/BodyHistoryScreen.swift`,
tests

🔴 **The window is `min(today − 89 days, earliest log)` through today, newest first**, and every date
in it is a row: `Logged(date, entry)` or `Missing(date)`. Missing days are *shown*, not skipped —
that is the point of the screen. Transcribe `BodyHistoryViewModel.buildItems`; it is 15 lines and
the boundary is easy to get off by one (`0...dayCount`, inclusive).

Wire `BodyScreen`'s `onViewHistory` and let its row reveal its affordance.

## Task 9: Body Edit — 🖼️

**Files:** create `Features/Body/BodyEditScreen.swift`, extend `BodyModel` or add `BodyEditModel`

Per **D35**, reuse `CheckInDraft`. Pushed from a Body History row with a date. Same validation and
the same save path as the check-in sheet — including `stepsEdited`, which exists so an untouched
steps field does not clobber a Health Connect value.

⚠️ The three scores use `ScoreSlider`, not the deleted stepper.

## Task 10: Appearance — 🖼️

**Files:** create `Features/Appearance/AppearanceModel.swift`,
`Features/Appearance/AppearanceScreen.swift`

Accent grid (the eleven `AccentTheme` cases) + theme mode (system/light/dark). **No font row**
(**D34**).

🔴 **This is the first screen that changes a preference another screen is already showing**, which is
exactly what `JSONStore.changes()` was added for (**D30** work). `ThemeHost` currently reads UI
preferences **once** and has a comment saying to convert it to a `for await` loop when Phase 3
arrives. That conversion is part of this task — without it the accent will not change until relaunch.

⚠️ This is also the moment the other ten accents get looked at for the first time. Expect to find
contrast problems in surfaces built and reviewed only on Violet and Silver.

## Task 11: The More hub — 🖼️

**Files:** create `Features/More/MoreScreen.swift`, modify
`Features/Dashboard/DashboardScreen.swift`

Two sections, from `MoreScreen.kt`: **Insights** (Trends) and **Setup** (Profile, Plan, Appearance,
AI & Coach, Integrations, Data & Backup, Usage, Developer).

🔴 **Five of those nine land in later phases** (Trends/Usage/Developer in 3d; Integrations and Data
& Backup in 4; AI & Coach in 5). A row that pushes nothing is the dead-affordance this codebase
refuses. Either omit unbuilt rows or render them disabled with the phase named — pick one, apply it
to all five, and write down which.

The Dashboard avatar becomes the entry point (**D17**, **D36**): wrap the existing circle in a
`Button`, keep the glyph.

Also add the **calorie decision screen** here — built in 3b and unreachable since.

## Task 12: Verification and docs

- Full suite + **Release** build. Release has caught things Debug did not (the 1b `nonisolated`
  fix).
- Re-run the leak greps the consistency pass established: no raw `fontSize`/`fontWeight` beside a
  `style`, no `String(format: "%.1f"`, no hand-rolled screen header, no bare `ModalBottomSheet`.
- Update `decisions.md` (D33–D36), `parity-ledger.md` (six screen rows, plus the design-system
  `GlassAlertDialog` row if the option sheet closes it), and `STATUS.md` — including **removing the
  plan-ledger gap** from *owed to 3c*.
- List every 🖼️ surface under *Needs visual check*, and flag any built blind.

---

## What 3c deliberately does NOT do

- **Onboarding, Progress, Usage, Developer** — 3d.
- **The profile photo picker** — needs PhotosUI and has an Android bug not worth porting.
- **Integrations, Data & Backup** — Phase 4. Their More rows are placeholders.
- **AI & Coach settings** — Phase 5.
- **A font picker** — D34.

## Rollback

One branch, `phase-3c-plan-profile-and-more`, off `main`. Task 1 is the only one that changes
existing behaviour; if it has to be reverted, the ledger returns to empty and every resolution goes
back to its fallback — the current, working state. Tasks 3–11 only add files plus the three
one-line wirings named above.
