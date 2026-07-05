# Weekly Rebalance — Design

**Date:** 2026-07-05 · **Branch:** `feat/weekly-rebalance` · **Status:** Approved by user (interview + design review)

A supportive coaching feature: after a clearly high-calorie day or weekend, the Dashboard offers one
gentle, deterministic 2–5 day plan (slightly reduced calories and/or extra steps) that brings the
weekly average back near target, then reverts automatically. Never punitive, never naggy.

---

## 1. Goals & non-goals

**Goals**
- Detect a meaningful overage (single day or weekend) the *next* morning / next app open — never same-day.
- Offer exactly one recommended plan (no option paralysis): Start / Keep My Normal Plan, Customize behind a tap.
- While active, the *effective* daily calorie target (and step goal, when the plan includes steps) is what
  the Dashboard, Food Log, streaks, progress charts and coach see — with a visible "Rebalance · Day X of Y" chip.
- Auto-revert structurally at window end. All numbers deterministic; cloud AI phrases copy only.

**Non-goals (v1)**
- No push notifications (dashboard card only).
- No changes to on-device AI behavior beyond the target *values* it already quotes (no prompt-structure, model, or tool changes).
- No re-customize mid-plan; mode changes apply to the next offer.
- No new Room tables; no `PlanVersion`/`PlanPreferences` writes.

## 2. Locked product decisions (user interview)

| Topic | Decision |
|---|---|
| Trigger | High day/weekend **and** meaningful weekly-average impact; evaluated next morning / next open |
| Targets | Real temporary targets app-wide with "Rebalance · Day X of Y" indicator; auto-revert |
| Duration | Adaptive 2–5 days, starts at accept (Day 1 = tomorrow), rolling — calendar-week independent |
| AI | Deterministic engine decides everything numeric; cloud AI phrases copy only, template fallback |
| Placement | Dashboard card only; same slot becomes the progress card |
| Re-trigger | Only a **new** qualifying event **and** ≥3 days since the last decline/completion; never while active |
| Mid-plan overage | Hold remaining days steady (never compound cuts); graceful early end if clearly unrecoverable |

## 3. User experience

**Offer card** (Dashboard, conditional slot like `CoachTodaySlot`, directly adjacent to it):
reassurance headline ("Your weekly goal is still within reach") + one concrete plan
("a light 3-day rebalance — about 250 kcal less per day and +1,500 steps").
Buttons: `Start Weekly Rebalance` (primary) · `Keep My Normal Plan` (secondary).
A small `Customize` affordance opens a `GlassBottomSheet` with a `GlassSegmentedToggle`:
*Prefer eating a little less · Balanced · Prefer moving more*. Picking a mode recomputes the offered
plan instantly (deterministically, from facts stored on the offer) and is sticky for future offers.

**Progress card** (same slot once accepted): "Rebalance · Day X of Y", progress fraction = X/Y,
today's effective target, supportive line. Numbers update live; state transitions happen once daily.

**End states:** completion note ("back on track"), graceful early-end note, or a one-time supportive
"no adjustment" note when a surplus is too large to recover comfortably. All dismissible.

**Tone:** never "failed / cheated / make up for it". Always "rebalance / weekly average / small adjustment / back on track".

## 4. Architecture overview

| File (new unless noted) | Purpose |
|---|---|
| `domain/rebalance/RebalanceModels.kt` | `RebalancePlan`, `RebalanceState`, enums, `RebalanceDefaults` (all constants) |
| `domain/rebalance/RebalanceEvaluationInput.kt` | Flattened snapshot the engine consumes (mirrors `AdjustmentInput`) |
| `domain/rebalance/RebalanceEngine.kt` | Pure: `evaluate(input): RebalanceDecision` + `reconcile(state, today): RebalanceState` |
| `domain/rebalance/EffectiveTargets.kt` | Pure resolver: base `PlanTargets` + rebalance windows → effective targets/step goal |
| `data/rebalance/RebalanceSerialization.kt` | Pure JSON codec (malformed → empty state, never throws) |
| `data/rebalance/RebalanceStore.kt` | `RebalanceStore` interface + DataStore impl (`preferencesDataStore("rebalance")`) — clones `CoachExperimentStore` |
| `data/rebalance/RebalanceCoordinator.kt` | Once-daily `runIfDue()`, reconcile-on-open, accept/decline/customize transitions, cancel-on-plan-edit hook |
| `ai/RebalanceCopyService.kt` + `ai/RebalanceCopyPromptBuilder.kt` | Cloud phrasing with deterministic fallbacks — clones `CoachPhrasingService` |
| `ui/dashboard/RebalanceViewModel.kt` + `ui/dashboard/RebalanceCard.kt` | Card VM + composable (offer/progress/note faces), early-returns null |
| *(edited)* `core/AppContainer.kt` | Construction + input assembly + VM factory + version-observer cancel hook |
| *(edited)* `ui/dashboard/DashboardScreen.kt` | Conditional card slot + `LaunchedEffect { vm.onShown() }` |
| *(edited)* consumers per §6 | Wrap `PlanHistory` results with `EffectiveTargets` |
| *(edited)* `domain/export/BackupModels.kt`, `data/repository/BackupRepository.kt` | Backup the rebalance state (nullable, additive) |

Dependency rule respected: `domain/rebalance` is pure Kotlin (no Android imports); data layer does I/O;
UI reads flows. Engine functions take `today: LocalDate` as a parameter (house style); the coordinator
injects `DateProvider`.

### Data model

```kotlin
enum class RebalanceMode { EAT_LESS, BALANCED, MOVE_MORE }
enum class RebalanceStatus { OFFERED, ACTIVE, COMPLETED, ENDED_EARLY, DECLINED, NO_ADJUSTMENT }

data class RebalancePlan(
    val id: String,                    // UUID
    val triggerDateIso: String,        // latest qualifying high day (weekend → the Sunday)
    val startDateIso: String,          // Day 1 (accept-day + 1)
    val endDateIso: String,            // inclusive; start + lengthDays - 1
    val lengthDays: Int,               // 2..5
    val mode: RebalanceMode,
    val baseCalories: Int,             // base target at offer/accept (audit + derivation anchor)
    val dailyCalorieReduction: Int,    // R ≥ 0
    val extraDailySteps: Int,          // E ≥ 0 (0 = calorie-only)
    val baseStepGoal: Int?,            // null = user has no step goal
    val recentAvgSteps: Int?,          // 7-day avg at offer time — lets Customize recompute without re-reading data
    val surplusKcal: Int,              // S the plan was sized against
    val recoveredKcal: Int,            // D * (R + stepKcal(E))
    val status: RebalanceStatus,
    val createdAtIso: String,
    val decidedAtIso: String? = null,
    val endedReason: String? = null,   // "completed" | "unrecoverable" | "plan_edited" | "expired"
)

data class RebalanceState(
    val active: RebalancePlan? = null,          // at most one OFFERED or ACTIVE
    val history: List<RebalancePlan> = emptyList(), // terminal records, capped at 12 (rolling)
    val mode: RebalanceMode = RebalanceMode.BALANCED, // sticky Customize preference
)
```

Effective targets for a plan day are **derived, never stored** (`baseCalories - R` + §5.7 macro scaling).
Persistence: one JSON blob under `stringPreferencesKey("state")` plus `stringPreferencesKey("last_evaluated")`
in the `rebalance` DataStore. Cooldown and new-event gates are derived from `history`.

## 5. Deterministic engine (`RebalanceDefaults` constants inline)

Window: the trailing 7 days ending **yesterday**. Today is never judged. `base(d)` comes from
`PlanHistory`-resolved targets; `over(d) = eaten(d) − base(d)` (eaten totals already exclude
`planned=true` meals; planned kcal are never added to the surplus — a surplus hidden in unconfirmed
planned meals simply doesn't trigger).

### 5.1 Data-trust gates (any failure → Silent)
- ≥ `MIN_LOGGED_DAYS_WINDOW = 4` of the 7 window days have ≥1 meal entry.
- Each specific trigger day has ≥1 meal entry.

### 5.2 High-day / weekend rule
- Day `d` is **HIGH** iff logged and `over(d) ≥ 400` (`HIGH_DAY_ABS_KCAL`) **or** `over(d) ≥ 0.25 × base(d)` (`HIGH_DAY_PCT`).
- **Weekend** qualifies iff the most recent completed Sat+Sun are both logged and
  `max(over(Sat),0) + max(over(Sun),0) ≥ 600` (`WEEKEND_SURPLUS_KCAL`).
- Surplus the plan is sized against: `S = Σ max(over(d), 0)` over the whole window
  (same positive-part convention as `PatternDetectors.detectDerailmentDay`).

### 5.3 Weekly-impact test
Offer only if `S / 7 ≥ 50` (`WEEKLY_IMPACT_MIN_KCAL`). A high day fully offset elsewhere → silent.

### 5.4 Re-trigger gates
- `state.active != null` → Silent (never offer while one is offered/active).
- Cooldown: `< 3` days (`COOLDOWN_DAYS`) since the last terminal record's end/decision date → Silent.
  NO_ADJUSTMENT records count (prevents repeated notes).
- **New-event rule:** the latest qualifying HIGH day must be **strictly after** the most recent terminal
  record's `triggerDate`. The same overage can never re-offer after a decline/completion/expiry/note.

### 5.5 Plan derivation
Caps per day:
- `MAX_CAL_REDUCTION = min(round10(0.15 × base), 300)`; effective target floored at `MIN_EFFECTIVE_CAL = 1200`.
- `MAX_EXTRA_STEPS = clamp(round500(0.25 × recentAvgSteps), 0, 3000)`; `recentAvgSteps` =
  `ActivitySummary.averageDailySteps(stepsByDate, yesterday, 7)`. Null steps data **or** null step goal → calorie-only.
- `KCAL_PER_STEP = 0.04` (single source of truth).

Recovery target: `targetRecover = round(S × RECOVERY_FRACTION)` with `RECOVERY_FRACTION = 0.75`
(deliberately not 100%). Per-day capacity by mode:
`EAT_LESS = MAX_CAL_REDUCTION` · `MOVE_MORE = stepKcal(MAX_EXTRA_STEPS)` (0 → falls back to EAT_LESS) ·
`BALANCED = 0.6×MAX_CAL_REDUCTION + stepKcal(0.6×MAX_EXTRA_STEPS)`.
**When steps are unavailable (null step data or null step goal) the plan is calorie-only for EVERY mode**
— perDayCap is the full `MAX_CAL_REDUCTION` and E = 0 (the 0.6 split applies only when steps exist).
`R` is additionally capped at `base − MIN_EFFECTIVE_CAL` so `recoveredKcal` never overstates what the
floored effective target actually delivers.

Duration `D` = smallest value in 2..5 with `D × perDayCap ≥ targetRecover`. Then `perDay = ceil(targetRecover / D)`,
split by mode (calories filled first for BALANCED, remainder into steps), `R` rounded to 10, `E` to 100,
both capped. If `5 × perDayCap < targetRecover` → the surplus is too large: **NO_ADJUSTMENT note** (§5.6).

### 5.6 Goal-aware & no-offer branches
- `LEAN_BULK / MODERATE_BULK / AGGRESSIVE_BULK`: surplus is intended → fully **silent**.
- `RECOMP`: offer allowed, `RECOVERY_FRACTION_RECOMP = 0.375`, `D ≤ 3` (a "little adjustment").
- Cut goals: full behavior.
- Too-large surplus: supportive **NO_ADJUSTMENT** card ("a mini-plan wouldn't add much — just pick up your
  normal plan tomorrow"). It occupies `state.active` (it *is* the current card, dismissible, no buttons);
  dismissal or next-morning expiry moves it to history, where it blocks repeats via cooldown + new-event.
- Insufficient data: silent, nothing persisted.

### 5.7 Macro scaling for reduced days (PlanCalculator's own formula)
`proteinG` held at base · `fatG = round(0.25 × effective / 9)` ·
`carbsG = round((effective − proteinG×4 − fatG×9) / 4).coerceAtLeast(0)` ·
zone = `effective ± CALORIE_ZONE_MARGIN (100)`.

### 5.8 Daily reconcile (pure; runs before evaluation at app open)
- ACTIVE and `today > endDate` → COMPLETED (to history). Auto-revert is structural — the resolver simply
  stops overriding dates past the window.
- ACTIVE and clearly unrecoverable → ENDED_EARLY (`"unrecoverable"`) + graceful note. Definition:
  recompute `S_now` (trailing-7-day positive surplus vs **base**, excluding today); if
  `(S_now − remainingDays × (R + stepKcal(E))) / 7 > UNRECOVERABLE_SLACK = 75`, end early.
  Reconcile can only *end* a plan — `R`/`E` never increase mid-plan.
- OFFERED (or NO_ADJUSTMENT) and `today > localDate(createdAtIso)` → history as DECLINED
  (`endedReason="expired"`) / NO_ADJUSTMENT respectively. An offer lives exactly the day it was created;
  an ignored offer must not re-nag, and expiry counts like a decline for cooldown/new-event.

## 6. Effective targets & consumer semantics

`EffectiveTargets` (pure): `resolve(base, date, state)`, `resolveAll(baseByDate, state)`,
`effectiveStepGoal(baseGoal, date, state)`, `planDayInfo(date, state)`.
A date inside **any** window — active or historical (ACTIVE/COMPLETED; ENDED_EARLY only up to its ended
date) — resolves to the reduced targets. History retention is what keeps already-elapsed rebalance days
judged as agreed after revert (streaks/adherence re-resolve past days).

| Consumer | Targets | Notes |
|---|---|---|
| Dashboard ring / adherence tile / in-zone-7 / 7-day chart | **Effective** | `DashboardViewModel` wraps its `PlanHistory.resolve` maps |
| Food Log day target + week strip | **Effective** | + "Rebalance · Day X of Y" chip on plan days |
| Streaks — calorie zone | **Lenient union** `[min(lows), max(highs)]` of base & effective zones | A rebalance can never break a streak the user would otherwise have kept |
| Streaks — steps | **Base goal always** | Boosted goal is display/progress only; a temporary goal must never break a streak |
| Dashboard step ring display | **Effective goal shown** | Judgment stays base (row above) |
| Progress 28–90d charts | **Effective** | Target line reflects what was actually in effect |
| Weekly review / `WeeklyReviewComputer` | **Base** | Its `signature()` hashes the target — effective would churn badges/briefings |
| `AdjustmentEngine` inputs | **Base** | Long-horizon verdict must not be perturbed by a 2–5 day blip |
| Plan editor (`PlanViewModel`) | **Base** | Edits the permanent plan |
| Cloud coach context (`CoachContextAssembler`) | **Effective** + explicit `rebalance {active, dayX, ofY, effectiveCalories, extraSteps}` block | Coach must not contradict the card |
| On-device coach (`CoachToolsAdapter` "Plan:" line, `get_weekly_trends` targets) | **Effective values only** | No prompt-structure/model/tool changes — values it already prints |
| `update_calorie_target` (coach write tool) | Writes **base** via `PlanRepository.save` | A real target change → cancels the rebalance (§7) |

## 7. State machine & flows

```
NONE ──(qualifying event, gates pass)──► OFFERED ──accept──► ACTIVE ──(today>end)──► COMPLETED
  ▲                                        │  │                 │
  │                                        │  └─(TTL expiry)─┐  ├─(unrecoverable)──► ENDED_EARLY
  └──(≥3d cooldown + NEW event)── DECLINED ◄──decline────────┘  └─(base PlanTargets change)─► ENDED_EARLY("plan_edited")
                                   (terminal records → history, cap 12)
```

- **Evaluation:** `RebalanceCoordinator.runIfDue()` from `RebalanceViewModel.onShown()`, fired by
  `LaunchedEffect(Unit)` on the Dashboard (the `CoachTodayViewModel` wiring, `DashboardScreen.kt:111`).
  Gated by `last_evaluated == today`; guarded by a `Mutex`. Order: reconcile → evaluate. One-shot suspend
  reads (`getWeekCalories`, meal counts, `targetsByDate`, steps, profile) → pure input → pure engine —
  the `computeWeeklyReviewData` pattern. **Deliberately not reactive**: judging only days < today plus the
  once-daily gate makes "next morning, stable all day, never same-day" true by construction.
- **Presentation is live:** the card binds to `RebalanceStore.state` (Flow); progress/day-X derive on read.
- **Accept:** status→ACTIVE, `decidedAt` stamped, `startDate = today + 1`, base snapshot captured. Card flips.
- **Decline:** plan → history (DECLINED), card disappears, cooldown starts.
- **Customize:** recomputes R/E/D for the OFFERED plan from stored `surplusKcal`/`recentAvgSteps`/`baseCalories`,
  **goal-aware** — the coordinator passes the user's current `FitnessGoal` so a RECOMP offer keeps its halved
  fraction and 3-day cap (the goal is deliberately not stored on the plan record); sticky `mode` saved.
- **Cancel-on-plan-edit:** coordinator collects `planRepository.observeVersions().drop(1)`; a new
  `PlanVersion` row (written iff `PlanTargets` changed — HC-sync saves don't) while ACTIVE → ENDED_EARLY
  (`"plan_edited"`). `resetDefaults()` also writes a version → also cancels (correct).
- Analytics via `UsageTracker`: `REBALANCE_OFFERED / ACCEPTED / DECLINED / COMPLETED / ENDED_EARLY` (fire-and-forget).

## 8. Cloud copy service

`RebalanceCopyService` clones `CoachPhrasingService`: `config() == null` → fallback; `withTimeout(15s)`
`streamCompletion` (plain completion, **no tool calls** — immune to the OpenRouter tool_calls regression);
blank/timeout/error → fallback; `CancellationException` rethrown; same `sanitize()`.

Inputs are pre-formatted deterministic facts (`lengthDays, dailyCalorieReduction, extraDailySteps,
effectiveCalories, dayX, ofY`). System prompt: *"Rephrase a supportive nutrition message. Use ONLY the
numbers provided; invent none. Never imply the user failed, cheated, or must 'make up for' anything.
1–2 warm sentences. No markdown."*

Slots + fallback templates (used verbatim when AI is off/fails — the feature is fully functional without cloud):
- `OFFER_HEADLINE`: "Your weekly goal is still within reach."
- `OFFER_BODY`: "Yesterday ran a bit high. Want a light {lengthDays}-day rebalance — about
  {dailyCalorieReduction} kcal less a day{stepsClause} — to bring your weekly average back near target?"
  (`{stepsClause}` = " and +{extraDailySteps} steps" when `extraDailySteps > 0`, else empty; a MOVE_MORE
  plan with `R == 0` drops the kcal phrase and leads with steps instead.)
- `PROGRESS_LINE`: "You're {dayX} of {ofY} days in — today's target is {effectiveCalories} kcal."
- `GRACEFUL_END`: "Let's ease off the rebalance — this week had a lot in it, and that's completely fine. Back to your normal plan tomorrow."
- `COMPLETION`: "Rebalance complete — nicely done. Your weekly average is back on track."
- `NO_ADJUSTMENT`: "This week ran high enough that a mini-plan wouldn't add much — best to just pick up your normal plan tomorrow."

The card renders the fallback instantly and swaps in phrased copy when it arrives (the `CoachTodaySlot` pattern).

## 9. Persistence & backup

DataStore-JSON (`CoachExperimentStore` pattern): interface + thin store + pure serialization object.
Malformed/blank payload decodes to `RebalanceState()` — never throws. Every transition is one atomic
`store.save()`; process death mid-flow recovers from disk.

Backup: add `val rebalanceState: RebalanceState? = null` to `BackupPayload` (additive; old backups
decode as null → clean state) and read/write it in `BackupRepository`. Rationale: an active rebalance
reshapes how logged days are judged; dropping it on restore would re-judge those days and could re-offer immediately.

## 10. Edge cases

| Case | Handling |
|---|---|
| Process death | Single persisted blob; transitions atomic; reconcile on next open |
| Timezone/DST | `LocalDate` arithmetic only, via injected `DateProvider`; no wall-clock durations |
| HC steps unsynced at 7am | Trigger uses meal data only; steps affect plan *feasibility* — worst case a plan falls back toward calorie-only |
| No cloud config / AI disabled | Card fully functional on templates; phrasing simply skipped. Not gated on the AI-insights toggle (deterministic feature) |
| Multiple opens same day | `last_evaluated` gate + active-plan gate → idempotent; the stored offer never recomputes mid-day |
| Accept late in the day | Day 1 = tomorrow; today keeps base target, no chip |
| User edits yesterday's meals after the offer | Offer stays stable for the day; corrected data re-evaluates next morning; unaccepted stale offers expire (TTL) |
| Overage during plan | Hold steady; end early only per §5.8; never deepen cuts |
| Base plan edited mid-plan | Cancel via version observer (§7) |
| No step goal / no step data | Calorie-only plans |
| Surplus in unconfirmed planned meals | Never counted; can only under-offer, never over-offer |

## 11. Test plan (house style: backtick sentence names, JUnit4, local fakes, `today` as parameter)

- **`RebalanceEngineTest`** (pure): single high day sizes an offer at ~75% of surplus · weekend Sat+Sun ≥600 triggers ·
  300-over day below both thresholds doesn't · high day with `S/7 < 50` doesn't · too-large surplus → NO_ADJUSTMENT ·
  reduction capped at min(15%, 300) · steps capped at min(25% of avg, 3000) · null step goal/data → calorie-only ·
  EAT_LESS/MOVE_MORE/BALANCED splits · bulk silent, recomp halved & ≤3 days · active plan → silent ·
  cooldown < 3d → silent · **same trigger day never re-offers after decline** · <4 logged days → silent ·
  today excluded from surplus.
- **`RebalanceReconcileTest`**: past end → COMPLETED · unrecoverable → ENDED_EARLY · never increases R/E ·
  stale offer expires to DECLINED("expired").
- **`EffectiveTargetsTest`**: active day reduced + zone re-centred · historical day still reduced after revert ·
  non-plan day base · ENDED_EARLY only overrides through ended date · macro scaling (protein hold, fat 25%, carbs ≥0) ·
  effective step goal only on plan days · union zone widens, never narrows.
- **`RebalanceSerializationTest`**: round-trip · malformed → empty state.
- **`RebalanceStoreTest`**: save/current/flow-emission over in-memory DataStore.
- **Streak semantics**: union zone never breaks a kept calorie streak · steps streak on base goal.
- **`RebalanceViewModelTest`**: offer face → accept → progress face · decline clears · phrasing swap + fallback · once-daily onShown.
- **`RebalanceCopyServiceTest`**: null config / blank / timeout / error → fallback · success sanitized · cancellation rethrown.

## 12. Build order

1. **Models + defaults** — `RebalanceModels.kt`, constants. Done: compiles.
2. **Pure engine + resolver** — `RebalanceEngine`, `RebalanceEvaluationInput`, `EffectiveTargets` + their tests. Done: all pure tests green.
3. **Persistence** — serialization + store + tests.
4. **Copy service** — service + prompt builder + tests (no cloud needed).
5. **Coordinator + AppContainer wiring** — input assembly, once-daily gate, transitions, cancel hook + coordinator tests.
6. **Consumer seam** — Dashboard/FoodLog/Progress VMs, StreakRepository (union/base rules), coach context + tool values. Weekly review & AdjustmentEngine stay base.
7. **UI** — `RebalanceCard` + `RebalanceViewModel` + Dashboard slot + VM factory.
8. **Backup + analytics** — BackupPayload field, UsageEvents.

## 13. Follow-ups (out of scope, noted for later)

- CLAUDE.md says Room schema v13; it is v15 — doc refresh.
- `log_metric` coach tool doesn't accept steps; a "log a walk" affordance could complement MOVE_MORE plans.
- Optional Room history table if long-range rebalance analytics are ever wanted (DataStore history is capped at 12).
- Weekly review briefing could *mention* an active rebalance as separate context (kept off v1 to avoid signature churn).
