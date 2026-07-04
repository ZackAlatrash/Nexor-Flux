# Deferred AI/Training items — design spec

Date: 2026-07-04 · Branch: `feat/pr-banner-muscle-usage`

The four remaining items from the AI-coaching redesign (`docs/ai-redesign/`), all explicitly
deferred in the implementation plan. Decisions below were made with the owner.

## Decisions locked

- **Per-muscle soreness → DERIVED, no new input, no schema change.** Computed from recent training
  (volume × recency per muscle group), not asked of the user. Whole-body `sorenessScore` stays as-is.
- **PR banner → estimated 1-rep-max (Epley).** Reuses the app's existing PR definition; fires when a
  completed set beats the best-ever e1RM for that exercise.
- **Usage tracking → fully local (Room), no external SDK / no Firebase Analytics.** Privacy-first,
  offline, single-user (the owner). Read back on an in-app "Usage" screen.

---

## Shared foundation — `MuscleTrainingAggregator` (domain, pure Kotlin)

New pure-Kotlin component in `domain/workout` (no Android imports; fully unit-tested). Consumed by
features ③ and ④.

- Input: recent completed `WorkoutSession`s (with exercises + sets) and a clock/`today`.
- Maps each exercise → its muscle group(s) via the existing `MuscleCategory` / `MuscleGroup`
  taxonomy (reuse — do not invent a new mapping).
- Produces, per muscle group:
  - **Weekly volume** — Σ(sets × reps × weight) (or the volume metric `WorkoutProgressAnalyzer`
    already uses — reuse it for consistency) over the trailing 7 days, and the prior 7 days for a
    trend delta.
  - **Recovery score** — a 0–1 (or Fresh/Moderate/Recovering band) estimate where recent high volume
    on a muscle = still recovering, decaying to fresh over ~3 days. Deterministic; documented formula.
- No persistence, no new DB column. Multi-muscle exercises attribute to each mapped group (primary
  full weight; secondary at a reduced factor — pick a simple, documented rule).

**Acceptance:** unit tests cover — single-muscle volume, multi-muscle attribution, recency decay
(trained today vs 3 days ago vs 7 days ago), empty history → all-fresh, and the weekly trend delta.

---

## ① Mid-workout PR banner  *(ui/train)*

When a set is marked complete in an active session, if its Epley e1RM (`weight × (1 + reps/30)`)
exceeds the best-ever e1RM for that exercise, show a celebratory banner over the active-session
screen.

- Detection in `ActiveSessionViewModel`: on `toggleComplete(... → completed=true)`, compute the set's
  e1RM and compare against a **prior-best-e1RM-per-exercise** map loaded when the session's exercises
  load (reuse `ExerciseStatsCalculator` / the exercise-history DAO; the "prior best" must exclude the
  current in-progress set but may include earlier sets this session). Reuse the existing PR definition
  (`NewPrDetector` / `PrCalloutFormatter`) — do not redefine what a PR is.
- Emit a one-shot UI event (not persistent state): exercise name + the new e1RM / the achieving
  weight×reps. **Fire at most once per exercise per session**, only on a new max (track already-fired
  exercise ids for the session).
- UI (`ActiveSessionScreen`): a transient banner using the existing celebration skin
  (`celebration*` tokens + trophy, as the Today's-Coaching celebration card does), auto-dismissing
  after ~3–4s, non-blocking (does not interrupt logging). Text via `PrCalloutFormatter`.
- Respects reduced-motion. No new persistence.

**Acceptance:** completing a set that beats prior best shows the banner once; a lesser set shows
nothing; a second PR on a different exercise shows again; repeating the same exercise's PR does not
re-fire. Compiles; VM logic unit-tested where practical.

---

## ② Usage tracking  *(data + a read-out screen)*

**Infra (backend):**
- Room table `usage_events` (`id`, `timestampEpochMs`, `type: String`, `label: String?`) + DAO
  (insert; query counts by type/label over a window; delete-all). Migration **14 → 15** (additive,
  new table only — no changes to existing tables). Bump `RecompDatabase.version` to 15.
- `UsageTracker` interface + Room-backed impl, provided by `AppContainer`. `track(type, label?)` is
  fire-and-forget on `Dispatchers.IO` (never blocks UI). A no-op impl for tests/previews.
- Event vocabulary (small, closed enum/consts): insight card `shown` / `tapped` / `dismissed`
  (label = card kind), `coach_opened`, `today_slot_shown` / `today_slot_action`,
  `weekly_checkin_opened`, `tab_view` (label = tab). Keep it focused on the AI surfaces + tab nav.
- Instrument the call sites for those events (AiInsightCard/GeneratedInsightCard, CoachTodaySlot,
  coach screen entry, weekly-briefing entry, nav tab selection). Scope call sites so they do NOT
  overlap other agents' files in the same wave.

**Read-out (UI):** a simple "Usage" screen reachable from More — last-7-day counts per event type and
a per-card tapped/dismissed tally (which insight cards you actually engage vs scroll past), with a
"Clear usage data" action. Uses the design system (`SubScreenHeader`, `NeutralCard`, `AppType`).
It's a utility/stats screen, not a hero surface — clean and legible over fancy.

**Privacy:** all local; never leaves the device; clearable.

**Acceptance:** events insert off-main; the screen shows correct counts; clear works; migration
14→15 is additive and the app opens on an upgraded DB. No external analytics dependency added.

---

## ③ Per-muscle recovery in Training Readiness  *(ui/train)*

Surface the aggregator's per-muscle recovery on the Train home's Training Readiness area: which
muscle groups are fresh vs. still recovering. Prefer reusing the existing `BodyMap` / `MuscleArt`
heat visual (map recovery → the existing heat scale); a clean labelled list is an acceptable
fallback if the body-map reuse is awkward. Whole-body readiness is unchanged; this is an added layer.

**Acceptance:** with recent training, recently-hammered muscles read "recovering" and untrained ones
read "fresh"; empty history → all fresh; matches the design system; compiles.

---

## ④ Recomp verdict per muscle  *(ui/progress)*

Wire the aggregator's per-muscle weekly volume (+ trend delta) into `ProgressViewModel` (which today
sees only session dates/frequency) and surface a short per-muscle volume read in the recomp verdict
area — e.g. "back volume up, chest flat." Feeds the existing progress narrative; **no new screen**.

**Acceptance:** ProgressViewModel exposes per-muscle volume/trend; the recomp area shows a concise
per-muscle read; empty history handled; compiles; the off-main dispatcher work stays off-main.

---

## Build order (agents)

- **Wave 1 (parallel, disjoint files):** foundation + ④ recomp wiring (Opus, TDD) · ① PR banner
  (UI specialist, `compose-ui`) · ② tracking infra (Opus).
- **Wave 2 (parallel):** ③ recovery UI (UI specialist) · ② usage screen (UI specialist).
- Review + compile/test gate after each wave; final combined review before hand-off.

## Guardrails
- No new user-facing data collection beyond the local usage events (which never leave the device).
- Reuse existing taxonomy/PR/volume logic; don't reinvent. Domain stays pure Kotlin.
- Design system for all UI. Build to a clean compile; owner verifies visuals on device.
