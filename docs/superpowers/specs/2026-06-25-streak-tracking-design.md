# Streak Tracking — Design Spec

**Date:** 2026-06-25
**Branch:** `feat/streak-tracking` (worktree `.claude/worktrees/streak-tracking`)
**Status:** Approved design, pending implementation plan

## Goal

Add three habit streaks to the app — **Workout**, **Calorie**, and **Steps** — each
reporting a **current** streak and a **longest (best)** streak. The design must make
adding future streak types trivial, and must not disturb unrelated functionality.

## Key finding: the data already exists

No new tracking infrastructure is required. All three signals are already recorded as
local-calendar ISO date strings (written via `LocalDate.now()`, so calendar-day and
timezone behaviour is already correct):

| Streak | Source of truth | "Qualified on day X?" |
|---|---|---|
| Workout | `WorkoutSessionEntity` (status `COMPLETED`, `date`) **∪** `DailyLogEntity.trained = true` | a completed session exists that day, or the day's `trained` flag is set |
| Calorie | `MealEntryEntity` (eaten only) totalled per day vs the calorie **zone** | day's eaten calories fall within `[calorieZoneLowerBound, calorieZoneUpperBound]` |
| Steps | `DailyLogEntity.steps` (Health Connect auto-sync **and** manual Body entry) | `steps >= dailyStepGoal` |

The only missing piece of data is the **daily step goal** setting.

## Decisions (locked)

1. **Workout day source:** completed session **OR** the Body check-in `trained` toggle.
   Additionally, **completing a session sets that day's `DailyLogEntity.trained = true`**
   so the two signals stay consistent.
2. **Workout streak number:** **calendar days the streak spans** (not a count of workouts).
3. **Calorie goal = "in zone":** reuse the dashboard's existing rule
   (`calories > 0 && calories in [zoneLower, zoneUpper]`). The zone is `target ± 100`
   (`PlanPreferences.CALORIE_ZONE_MARGIN`).
4. **UI placement:** Hybrid (Option D) — a compact Streaks card on the Dashboard that taps
   through to a dedicated detail screen.
5. **Persistence:** derive-on-read. **No new Room table, no migration.** Current/longest are
   computed from existing history each time. (The only persistence change in the feature is
   the new `dailyStepGoal` DataStore field.)

## Architecture

Dependency direction follows the existing app: `UI → ViewModel → Repository → Room/DataStore`,
with the streak math in the pure-Kotlin `domain/` layer (no Android imports).

```
domain/streak/StreakCalculator.kt      pure logic: qualifying days + restDays -> StreakResult
domain/streak/StreakModels.kt          StreakType, StreakResult, Streaks
data/repository/StreakRepository.kt     assembles qualifying-day sets from existing repos
ui/streak/StreakStatsViewModel.kt       exposes Streaks to the detail screen
ui/streak/StreakStatsScreen.kt          pushed detail screen (current + best per streak)
ui/dashboard/...                        compact Streaks summary card on Home
```

### 1. Streak engine (`domain/streak/`) — pure Kotlin, fully unit-tested

A single calculator shared by every streak type. Each type contributes only a set of
*qualifying days* and a rest-day tolerance.

```kotlin
enum class StreakType { WORKOUT, CALORIE, STEPS }

data class StreakResult(
    val type: StreakType,
    val current: Int,   // calendar days spanned by the live chain (0 if broken)
    val longest: Int,   // largest spanned chain across all history
)

class StreakCalculator {
    /**
     * @param qualifyingDays days the goal was met (any order, de-duplicated by the Set)
     * @param today          reference "now" day (user's local calendar day)
     * @param restDays       allowed consecutive gap days between qualifying days
     *                       (0 = strictly consecutive; 2 = up to two rest days)
     */
    fun compute(qualifyingDays: Set<LocalDate>, today: LocalDate, restDays: Int): StreakResult
}
```

**Chaining rule.** Sort qualifying days ascending. Two adjacent qualifying days belong to
the same chain when `daysBetween <= restDays + 1`. A larger gap starts a new chain.

**Current streak (calendar days spanned).** Take the chain containing the most recent
qualifying day. It is the *current* streak only if that chain is **alive** (see the "Alive
bound" below). When alive:

```
current = ChronoUnit.DAYS.between(firstDayOfChain, lastQualifyingDay) + 1
```

- Interior rest days within a chain **are** counted (they sit between two qualifying days).
- Trailing rest days (after the last qualifying day, up to today) keep the chain alive but
  are **not** counted until the next qualifying day extends the chain.
- If the most recent chain is not alive, `current = 0`.

**Alive bound.** The chain is alive iff the most recent qualifying day is recent enough that
the streak could still legally continue:
`ChronoUnit.DAYS.between(lastQualifyingDay, today) <= restDays + 1`.
- Workout (`restDays = 2`): alive if last qualifying day is ≤ 3 days before today.
- Calorie/Steps (`restDays = 0`): alive if last qualifying day is today or yesterday — i.e.
  an unfinished today never breaks yesterday's streak (the "grace" case). A grace day is not
  counted in `current` until it qualifies.

**Longest streak.** For every chain in history, span = `last − first + 1`. `longest` is the
maximum span. (`longest >= current` always.)

**Worked examples** (today = Thursday):
- Workout Mon, rest Tue/Wed, Workout Thu → one chain Mon–Thu, alive → `current = 4`. ✓
- Workout Mon, rest Tue/Wed/**Thu (today)** → chain = {Mon}, `today − Mon = 3 ≤ 3` alive →
  `current = 1`; on Friday `today − Mon = 4 > 3` → `current = 0`. ✓ (matches the break example)
- Calorie in-zone Mon/Tue/Wed, today Thu not yet logged → chain {Mon,Tue,Wed}, alive (grace),
  `current = 3`; once Thu qualifies → `current = 4`.

### 2. Qualifying-day assembly (`data/repository/StreakRepository.kt`)

Pulls history from existing repositories/DAOs and produces the three qualifying-day sets,
then runs `StreakCalculator`. Scans full available history so "best ever" is correct.

- **Calorie qualifying days:** per-day eaten calorie totals (same plan-excluded totalling the
  dashboard uses, e.g. `LogRepository.getWeekCalories`-style over the full range), keep days
  where `calories > 0 && calories in [zoneLower, zoneUpper]` using current `PlanPreferences`.
- **Steps qualifying days:** `DailyLogEntity` rows where `steps != null && steps >= dailyStepGoal`
  (read `dailyStepGoal` from `UserProfilePreferences`; if unset, the steps streak is shown as
  "set a goal" — see UI).
- **Workout qualifying days:** dates of `COMPLETED` workout sessions, unioned with
  `DailyLogEntity` rows where `trained == true`.

Exposes `fun streaks(): Flow<Streaks>` (or a suspend snapshot) where
`data class Streaks(val workout: StreakResult, val calorie: StreakResult, val steps: StreakResult)`.

> Implementation note: prefer reusing existing read methods on `LogRepository` /
> `WorkoutSessionRepository`. Add narrowly-scoped read helpers (e.g. all-history daily logs,
> all completed-session dates) only where a suitable one does not already exist.

### 3. Step-goal setting (DataStore)

Add `dailyStepGoal: Int? = null` to `UserProfilePreferences`, wired end-to-end through
`UserProfilePreferencesStore` exactly like the existing `weeklyGymSessions`/`heightCm` fields
(Keys entry, read mapping, write mapping). Surface it in `ProfileScreen` as a
`GlassInputField` ("Daily step goal", unit "steps", numeric) backed by a
`ProfileViewModel.setDailyStepGoal(...)` buffer, matching the height/gym-sessions pattern.

### 4. Trained sync on session completion

When a workout session is completed (`WorkoutSessionRepository.completeSession`, the single
place sessions go `ACTIVE → COMPLETED`), also mark that session's date
`DailyLogEntity.trained = true` via a targeted read-modify-write that preserves all other
metrics on that day (create the daily-log row if absent). Wire the needed DAO/dependency
through `AppContainer` without broadening `WorkoutSessionRepository`'s responsibilities more
than necessary.

### 5. Dependency injection (`core/AppContainer.kt`)

- Construct `val streakCalculator = StreakCalculator()` (pure domain).
- Construct `val streakRepository = StreakRepository(...)` from existing repos/DAOs +
  `dateProvider` + `userProfilePreferencesStore` + `planRepository`.
- Register `StreakStatsViewModel` in `AppViewModelFactory`.

## UI (Option D)

Built strictly from the existing design system (see `docs/design-system.md`): `FrostedCard`,
`SectionLabel`, `SubScreenHeader`, `ScreenScaffold`, `AppType`, `LocalAppColors`/`LocalAppAccent`.
No hardcoded type/colors, no new bespoke card or button families.

### Dashboard summary card
A compact `FrostedCard` (with a `SectionLabel("Streaks")`) added to the Home card stack,
showing three items — Workout / Calorie / Steps — each with a 🔥 glyph and the current
day-count (e.g. "4 days"). The whole card taps through to the detail screen. Placement in the
card order to be finalized during implementation (near the 7-day chart / stat tiles), kept
unobtrusive given Home's existing density.

### Detail screen (`ui/streak/StreakStatsScreen.kt`, route `streak_stats`)
A pushed sub-screen (`SubScreenHeader(title = "Streaks", onBack = ...)`) reached from the
Dashboard card (and optionally linkable later from More → Insights). For each streak:
- current streak (days) and **best** streak (days),
- a short "last 7/14 days" qualifying-day strip (met / missed / rest),
- the steps streak prompts the user to set a goal in Profile when `dailyStepGoal` is unset.
Layout leaves room for additional streak types without restructuring.

Navigation: add `const val StreakStats = "streak_stats"` to `Routes`, a `composable(...)` entry
in `AppNavGraph`, and an `onOpenStreaks` callback threaded into `HomeDashboardScreen` (same
pattern as the existing `onOpenCoach`/`onOpenSettings`/`onOpenFoodLog`/`onOpenBody` callbacks).

## Testing

- **Unit (pure domain, no emulator):** `StreakCalculator` covering — the two worked workout
  examples (continue at gap 3, break at gap 4), calorie/steps strict-consecutive, grace-today,
  empty history, single-day chains, `longest` diverging from `current`, and zone-edge calorie
  days. Run via `./gradlew :app:testDebugUnitTest`.
- **Repository:** light tests that qualifying-day assembly applies the zone rule, the steps
  goal, and the completed-session ∪ trained union correctly (using fakes/in-memory where the
  existing test setup allows).
- **Build/type-check:** `./gradlew :app:compileDebugKotlin` and `:app:assembleDebug`.
- **Visual:** user verifies the Dashboard card and detail screen in the running app (per
  project convention, the assistant does not drive the emulator).

## Scope / non-goals

- No notifications, no streak freeze/repair, no "best ever" persisted beyond derived history.
- No Health Connect changes; steps input stays as-is (auto-sync + manual entry). Only the goal
  is added.
- No new Room table or migration.

## Affected files (anticipated)

New:
- `domain/streak/StreakModels.kt`, `domain/streak/StreakCalculator.kt`
- `data/repository/StreakRepository.kt`
- `ui/streak/StreakStatsViewModel.kt`, `ui/streak/StreakStatsScreen.kt`
- `domain/streak/StreakCalculatorTest.kt` (test source set)

Modified:
- `data/preferences/UserProfilePreferences.kt`, `data/preferences/UserProfilePreferencesStore.kt`
- `ui/profile/ProfileScreen.kt`, `ui/profile/ProfileViewModel.kt`
- `data/repository/WorkoutSessionRepository.kt` (trained sync) + DAO/`AppContainer` wiring
- `core/AppContainer.kt` (DI + ViewModel factory)
- `ui/dashboard/DashboardScreen.kt` (+ its ViewModel/state if the card reads streaks there) —
  summary card and `onOpenStreaks` callback
- `ui/navigation/AppNavGraph.kt` (route + wiring)
