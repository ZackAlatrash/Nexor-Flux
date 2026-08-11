# Phase 3d — Onboarding, Progress, and the developer surfaces

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans`. Read `docs/ios-port/STATUS.md` and
> `docs/ios-port/decisions.md` first.

**Goal:** Close Phase 3 — the first-run wizard, the trends screen, and the two tool screens behind
More — and give `usage_events` a writer while doing it.

**Architecture:** No new domain: `TrendCalculator`, `AdherenceCalculator` and `PlanGenerator` are
already exported from `:shared`. Four `@Observable` models (**D19**) over the existing queries, plus
one new persistence type (`UsageTracker`) and one ported enum (the rebalance debug scenarios).

**Tech Stack:** SwiftUI on iOS 26, GRDB, the hand-drawn chart kit from 3b, `:shared` for the
engines.

---

## 🔴 Two findings that shape this phase

**1. `usage_events` has a record, a backup exclusion, and no writer.** Exactly the shape of the
plan-ledger gap 3c opened with — the table has existed since Phase 1a and nothing has ever inserted
a row. Ship the Usage screen without fixing that and it reads an empty table forever.

The reflex is to conclude the screen belongs in Phase 5, since most event types are AI. **That is
wrong, and worth checking rather than assuming.** Of Android's fourteen event types, **seven are
already producible on iOS today**:

| Event | Produced by | Shipped in |
|---|---|---|
| `tab_view` | the four-tab shell | Phase 2 |
| `rebalance_offered` · `_accepted` · `_declined` | the offer sheet | 3b |
| `rebalance_completed` · `_ended_early` · `_cancelled` | the coordinator and progress sheet | 3b |

The other seven (`insight_*`, `coach_opened`, `today_slot_*`, `weekly_checkin_opened`) are Phase 5
and stay unwired. So Usage Stats ships with real data on day one — **provided the tracker lands
before the screen**, which is why it is Task 1 rather than part of Task 3.

**2. The Developer screen unblocks the longest-standing visual gap in the port.** STATUS has carried
this since 3b:

> 🔴 **The running rebalance faces** — the progress sheet's Day-X-of-Y state, the ribbon's running
> state, and **`DayDots`** in its full 14pt variant. Unreachable without a plan on day ≥ 1, so no
> screenshot exists and none was captured. The whole P1-13 dot-index fix is invisible until then.

Six of Android's twelve debug scenarios (`PROGRESS_MID`, `PROGRESS_FINAL`, `COMPLETION`,
`GRACEFUL_END`, `NO_ADJUSTMENT`, `REASSURANCE_NOTE`) put the app into exactly those states on
demand. **That is why Developer is Task 4 and not Task 11**: it is small, and finishing it lets the
owner finally look at four surfaces that have been shipped-but-unseen for two phases.

## What is in, and what is cut

| Screen | Android | Ships as |
|---|---|---|
| Onboarding | `OnboardingScreen.kt` (434) + a 21-field wizard VM | All four steps, complete |
| Progress / Trends | `ProgressScreen.kt` (403) | **8 series of 10** — see below |
| Usage Stats | `UsageStatsScreen.kt` (159) | Complete, with a tracker behind it |
| Developer | `DeveloperScreen.kt` (151) | All 12 scenarios |

🔴 **Progress loses two series and one card, and all three are already-decided deferrals — not new
scope cuts.** `lifts` (marker-lift e1RM) and `muscleVolumeReads` are **Train** data, which is v1.1
(**D4**), and `ProgressInsightContext` feeds the AI insight card (Phase 5). The remaining eight —
weight, waist, calories, protein, carbs, fat, adherence, logging — are the whole screen for a v1
user, who has no Train data to chart.

⚠️ Leave the layout slots where the Kotlin puts them, exactly as 3b did for the Dashboard's coach
slot. Do not restyle the screen around their absence.

## Decisions to take

Append to `decisions.md` as each is confirmed in code.

**D40 · `UsageTracker` is fire-and-forget and never fails a user action.** A telemetry write that
can surface an error, block a tap, or roll back a transaction is worse than no telemetry. It writes
on a detached task and swallows its own errors — the **one** place in this codebase where swallowing
is correct, and it must say so at the call site, because `JSONStore.set` (**D30**) and
`FoodLogModel.deleteEntry` both state the opposite rule for real writes.

**D41 · The rebalance debug scenarios are ported to Swift, not moved into `:shared`.** Same
reasoning as **D27** (`buildStreaks`): `RebalanceDebugScenarios.kt` lives in Android's `data/` layer,
and standing rule 2 says a phase after 0 only adds files under the iOS repo. The Swift port carries
its own tests.

**D42 · Onboarding writes through `PlanRepository`, so first run stamps a plan version.** Android's
`finish()` already calls `planRepository.save()`; on iOS that now appends a `plan_versions` row
effective on day 1 (**D33**). That is correct and worth stating: it means every day the user ever
logs resolves against a real version rather than against the fallback.

**D43 · Imperial input is a display concern, and storage stays metric.** Android's wizard carries
`heightFeetInput`/`heightInchesInput` beside `heightInput` and parses weight/waist per
`useMetricUnits`. The **stored** values are always cm and kg. Do not let a unit preference reach the
database — every engine in `:shared` takes metric.

## 🔴 Screenshots

**Ask the owner before building any 🖼️ task.** Needed, named as they should be saved into the iOS
repo's `screenshots/`:

- `20-onboarding-step1-about-you.jpg`
- `21-onboarding-step2-your-body.jpg`
- `22-onboarding-step3-goal-and-measurements.jpg`
- `23-onboarding-step4-plan-reveal.jpg` — and the **adjusting** state if it is reachable
- `24-progress-top-range-and-body-charts.jpg`
- `25-progress-nutrition-and-adherence.jpg`
- `26-usage-stats.jpg`
- `27-developer-scenarios.jpg`

3c shipped four surfaces blind and they are still unverified. If a screenshot cannot be produced,
build from the Kotlin and **flag the task** in the ledger, as 3c did — but say so out loud rather
than letting it accumulate silently.

## Context you need

- **Read the Kotlin, not this brief.** 3a's plan was wrong about Android four times and every one
  was caught by an agent who opened the source.
- Android sources: `ui/onboarding/OnboardingScreen.kt` (434) + `OnboardingViewModel.kt` (286),
  `ui/progress/ProgressScreen.kt` (403) + `ProgressViewModel.kt` (325),
  `ui/usage/UsageStatsScreen.kt` (159) + `UsageStatsViewModel.kt` (148),
  `ui/developer/DeveloperScreen.kt` (151) + `DeveloperViewModel.kt` (71),
  `data/usage/UsageTracker.kt`, `data/rebalance/RebalanceDebugScenarios.kt`.
- **Scope `:shared` first.** `TrendCalculator`, `AdherenceCalculator`, `PlanGenerator`,
  `GeneratedPlan`, `PlanTargets` are all exported and all already used by iOS code. Confirm before
  writing a Swift equivalent of anything.
- Conventions that bite: no `didSet` on `@Observable` (3a); `nonisolated` does not propagate into
  extensions; `.task(id:)` is `flatMapLatest`; every number through `AppNumber` (**D28**); every
  title through `.screenTitle(_:subtitle:)` (**D32**), and a pushed screen gets no subtitle unless
  it is day-scoped; a named preference store is shared per name (**D37**), so do not build one per
  call site.

---

## Task 1: `UsageTracker` — the writer 🔴

**Files:** create `Persistence/UsageTracker.swift`, `RecompTrackerTests/UsageTrackerTests.swift`

Transcribe `data/usage/UsageTracker.kt`: an event is `(type, label?, timestamp)` inserted into
`usage_events`. Add a `UsageEventQueries` read side — counts by type since a timestamp, and the
per-card shown/tapped/dismissed tally the screen needs — plus `clear()`.

🔴 **Fire-and-forget (D40).** `track()` returns immediately, writes on a detached task, and swallows
its own failure. Say why in a comment: this is the one place the codebase's write-failures-are-loud
rule is deliberately inverted, and without the note it reads as the bug that rule exists to prevent.

⚠️ **`now` is injected**, not read from the clock — the 7-day window is the thing under test and 3a
shipped a clock-reading `activate()` that made its tests non-hermetic.

**Tests:** an event round-trips; counts group by type; the window excludes an event 8 days old and
includes one 6 days old; `clear()` empties the table; a failed write does not throw.

## Task 2: Wire the seven producible events

**Files:** modify `Shell/RootTabView.swift`, `Features/Rebalance/RebalanceCoordinator.swift`,
`Features/Rebalance/RebalanceModel.swift`

`tab_view` on selection change (label = the tab). The six `rebalance_*` at the points the
coordinator already distinguishes — it has a case for each, so this is one line per branch.

⚠️ **`rebalance_offered` fires only for a *concrete* offer**, not for a no-adjustment note. Android's
comment says so explicitly. Getting that wrong inflates the one number this screen exists to show.

⚠️ Do **not** wire the seven Phase 5 event types. A tracker call next to a feature that does not
exist is dead code that looks live.

## Task 3: Usage Stats — 🖼️

**Files:** create `Features/Usage/UsageStatsModel.swift`, `Features/Usage/UsageStatsScreen.swift`

Last-7-day counts by type, the per-card engagement tally, a total, and a Clear action.

⚠️ **The card-engagement section will be empty until Phase 5**, because every `insight_*` event is
produced by a coordinator that does not exist yet. Give it an honest empty state naming the phase —
the same rule More's disabled rows follow (**D38**) — rather than a zero-filled table that looks
like nobody has ever tapped a card.

Clear is destructive: confirm it with `.confirmationDialog`, as the entry-delete flow does.

## Task 4: The rebalance debug scenarios 🔴

**Files:** create `Features/Developer/RebalanceScenarios.swift`, tests

Port the twelve from `RebalanceDebugScenarios.kt` (**D41**): each is a label, a description, and the
`RebalanceState` it writes. Six of them are the states nobody has ever seen — see the finding at the
top.

⚠️ These write **through `RebalanceStore`**, which is the rebalance overlay and is deliberately
exempt from `PlanRepository` (see its own note). A scenario must not touch the base plan.

**Tests:** each scenario produces a state whose face is the one its label claims; the six progress /
note scenarios produce a state the Dashboard renders as a running plan or a note rather than as
nothing.

## Task 5: The Developer screen — 🖼️

**Files:** create `Features/Developer/DeveloperScreen.swift`, wire `MoreDestination.developer`

A list of scenario rows, each applying its state and reporting the phase label. Android also shows
the current phase; keep that — it is how you tell the scenario landed.

**After this task, ask the owner to look at the rebalance faces.** That is the point of ordering it
here, and the request should be explicit rather than left in the ledger.

## Task 6: `ProgressModel`

**Files:** create `Features/Progress/ProgressModel.swift`, tests

Eight `ChartSeries` over a selectable range (Android's default is 28 days). Reuses
`TrendCalculator` and `AdherenceCalculator` from `:shared` — **the Dashboard already does, and P1-12
is the review finding that says the two screens must not each roll their own regression.**

⚠️ **The adherence series is judged against rebalance-*effective* targets**, not base ones, and per
day. `TargetResolution` already does this for the Dashboard; use it rather than reading
`PlanPreferences` directly, or a week under a rebalance will read as a compliance failure.

## Task 7: The Progress screen — 🖼️

**Files:** create `Features/Progress/ProgressScreen.swift`

A range selector plus eight charts. `SparklineChart` already exists (3b) and Progress is one of its
documented consumers.

⚠️ **Leave the two Train slots and the insight-card slot empty**, in the Kotlin's order.

## Task 8: `OnboardingDraft` and its validation

**Files:** create `Features/Onboarding/OnboardingDraft.swift`, tests

The 21-field wizard state as one struct with a `step`. Pure validation — `canContinue` per step —
tested without hosting a view.

🔴 **Imperial is display-only (D43).** `heightFeetInput`/`heightInchesInput` compose into cm; weight
and waist parse per `useMetricUnits` into kg and cm. Nothing but metric reaches the draft's output.

⚠️ **Flipping units mid-wizard** has a defined behaviour in `setUnits` — read it rather than
guessing, and pin it. It is the sort of thing that silently converts 80 kg into 80 lb.

## Task 9: Onboarding steps 1–3 — 🖼️

**Files:** create `Features/Onboarding/OnboardingScreen.swift`,
`Features/Onboarding/OnboardingSteps.swift`

About you (name, units) · Your body (sex, birth date, height) · Goal & measurements (goal, activity,
weight, waist). Reuse `OptionSheet` from 3c for the three enum pickers — it is already generic over
its option, and a second picker style in a first-run flow would be the first thing a new user sees
of the app's inconsistency.

## Task 10: Onboarding step 4 — the plan reveal — 🖼️

**Files:** modify the above

Runs `PlanGenerator` and shows the result, with an **adjusting** mode that lets the four targets be
hand-edited before committing.

🔴 **A hand-edited calorie target is clamped and re-centres the zone.** Android:
`.coerceIn(MIN_CALORIE_TARGET, MAX_CALORIE_TARGET)` then `withCalorieTarget(...)`, and its comment
cites **P1-14** — without the re-centre the zone stays pinned to the *generated* target while the
saved target has moved, so day one is judged against a zone the user never saw.

## Task 11: Finish, and gate the start destination

**Files:** modify `Shell/RootTabView.swift` or `RecompTrackerApp.swift`

`finish()` writes the profile, the plan (**through `PlanRepository`** — D42), today's weight and
waist as a daily log, and sets `onboardingComplete`.

⚠️ **The daily-log write is an upsert and Android notes why it is safe**: first run by definition
means today's row does not exist. Use `CheckInWriter` if it fits, or state why it does not.

The app opens on onboarding when `onboardingComplete` is false. `UIPreferences.onboardingComplete`
already exists and has a setter; nothing reads it yet.

⚠️ **A `.task`-driven gate flashes the Dashboard first.** The flag has to be resolved before the
first paint, or every launch shows a frame of the app the user has not set up.

## Task 12: Verification and docs

- Full suite + **Release** build.
- Re-run the consistency greps: no raw `fontSize` beside a `style`, no `String(format: "%.1f"`, no
  hand-rolled header, no bare `ModalBottomSheet`.
- Update `decisions.md` (D40–D43), `parity-ledger.md` (four screens, and the Usage/Developer
  entries under Overlays if any), and `STATUS.md`.
- 🔴 **Phase 3 is complete after this.** Say so in STATUS, and re-check the *Needs visual check*
  list end to end — several 3b/3c entries should be closable once Developer exists.

---

## What 3d deliberately does NOT do

- **Train's two Progress series** and the AI insight card — v1.1 and Phase 5.
- **The seven AI usage events** — Phase 5 wires them to the coordinators that emit them.
- **CSV import** — v1.1 (**D4**), even though Integrations is adjacent.

## Rollback

One branch, `phase-3d-onboarding-progress-and-tools`, off `main`. Tasks 1–2 are the only ones that
change existing behaviour, and both are additive: reverting them leaves `usage_events` empty, which
is where it is today. Task 11's start-destination gate is the one change that affects launch — keep
it in its own commit.
