# First-Run Onboarding Flow — Design

**Date:** 2026-06-16
**Status:** Approved design, pending implementation plan
**Branch:** `feat/onboarding-flow` (off `develop`)
**Implementation:** to be coded in a separate session on this branch

## Problem

The app currently has **no onboarding or first-run flow**. `MainActivity → RecompApp → AppNavGraph`
launches straight to the Home tab (`AppNavGraph.kt`, start destination `"home"`), with no check for
whether the user has been set up. A brand-new user lands on an empty dashboard with no plan: the
calorie/macro engine has none of its required inputs, so targets fall back to the `PlanPreferences`
defaults (2550 kcal, etc.) that have nothing to do with that user.

## Goal

A guided, 4-screen first-run flow that collects the minimum profile needed to generate a real plan,
shows the user that plan, and then drops them onto the dashboard already set up. It must never appear
again after completion. UI reuses the existing liquid-glass component library and the app's dark /
violet aesthetic.

## What we gather

The plan engine (`PlanCalculator`, Mifflin–St Jeor + activity multiplier + goal delta) computes
calories **and** all macros from 6 inputs. Onboarding collects those 6 plus light personalization —
**9 fields total** — and the engine derives the rest.

| # | Field | Storage | Required? | Notes |
|---|-------|---------|-----------|-------|
| 1 | Name | `UserProfilePreferences.name` | Optional (skippable) | Display only |
| 2 | Units (metric/imperial) | `PlanPreferences.useMetricUnits` | Required (defaults metric) | Set first; drives weight/height entry |
| 3 | Biological sex | `UserProfilePreferences.biologicalSex` | **Required** | Engine input |
| 4 | Birth date | `UserProfilePreferences.birthDate` (ISO) | **Required** | Engine input (→ age) |
| 5 | Height | `UserProfilePreferences.heightCm` | **Required** | Engine input |
| 6 | Goal | `UserProfilePreferences.goal` (7 levels) | **Required** | Engine input |
| 7 | Activity level | `UserProfilePreferences.activityLevel` (4) | **Required** | Engine input |
| 8 | Current weight | first `daily_logs` row (`bodyWeightKg`) | **Required** | Engine input + seeds baseline |
| 9 | Starting waist | first `daily_logs` row (`waistCm`) | Optional (skippable) | Seeds baseline only |

**Computed and shown (not typed):** `targetCalories`, `targetProteinG`, `targetCarbsG`, `targetFatG`
→ saved to `PlanPreferences` on finish.

### Explicitly out of scope (stay in Settings, keep their defaults)

Profile photo (removed at user request), weekly gym sessions (currently used by no calculation),
advanced `PlanPreferences` thresholds (trend/waist/adherence/cadence, calorie-zone bounds,
maintenance phase), theme/accent/font, AI backend + API keys, Health Connect.

## Screen structure (4 screens)

Visual direction **B**: an explicit `Step X of 4 · <section>` label + thin progress line, a screen
title + subtitle, all fields grouped in **one** glass card, and a primary CTA pinned at the bottom.

| Screen | Title | Fields | CTA | Skip? |
|--------|-------|--------|-----|-------|
| 1 | About you | Name, Units (Metric/Imperial pill toggle) | Continue | "Skip" (name only) |
| 2 | Your body | Biological sex (pill toggle), Birth date (date picker), Height (number + unit) | Continue | No |
| 3 | Goal & measurements | Goal (tap row → sheet), Activity level (tap row → sheet), Current weight (number + unit), Waist (optional, number + unit) | See my plan | No (waist field is itself optional) |
| 4 | Your plan | **Read-only reveal:** goal badge, calorie hero number, P/C/F macro tiles, "Calculated with Mifflin–St Jeor" caption | **Start tracking** (primary) + **Adjust targets** (ghost) | — |

Notes:
- No separate welcome screen — Screen 1 carries a short header ("Let's get you set up").
- Goal (7 options) and activity (4 options) use tap-to-pick rows opening a `ModalBottomSheet`,
  matching the existing `ProfileScreen` pattern, rather than inline pills.
- **Adjust targets** opens an editable form pre-filled with the computed calories + macros, letting
  the user override before finishing (writes the edited values to `PlanPreferences`).

## Behaviour

### First-run gating
- A new boolean, `onboardingComplete` (default `false`), stored in DataStore — recommended location:
  `UiPreferences` (`AppPreferences`), alongside other app-level flags.
- `AppNavGraph` start destination becomes conditional: if `!onboardingComplete` → onboarding route,
  else → Home. The decision must be made from already-loaded prefs to avoid a Home→onboarding flash
  (gate before first composition / via a splash-held state, consistent with the existing splash setup
  in `MainActivity`).

### Persistence on finish ("Start tracking")
Performed as one commit of effects:
1. `UserProfilePreferences`: name (if given), birthDate, biologicalSex, heightCm, activityLevel, goal.
2. `PlanPreferences`: useMetricUnits, and the computed (or user-adjusted) targetCalories /
   targetProteinG / targetCarbsG / targetFatG.
3. `daily_logs` row for **today's date** with `bodyWeightKg` (and `waistCm` if entered). If a row for
   today already exists (unlikely on first run), update those columns rather than overwriting.
4. Set `onboardingComplete = true`.
5. Navigate to Home, clearing the onboarding route from the back stack.

### Plan computation
Reuse `PlanGenerator` / `PlanCalculator` unchanged. Build `PlanCalculatorInput` from the entered
values; weight comes from Screen 3, age from birth date. Screen 4 renders the resulting `GeneratedPlan`.

## Architecture

```
ui/onboarding/
  OnboardingViewModel      Holds draft state (all 9 fields) across screens; computes the plan
                           via PlanGenerator; persists everything on finish. Reads/writes
                           UserProfilePreferencesStore, PlanPreferences (AppPreferences),
                           LogRepository (today's daily_logs row), and the onboardingComplete flag.
  OnboardingScreen         Hosts the 4-step pager/state; renders the shared step frame (progress
                           label + bar, title, card, CTA) and the per-step content.
  (per-step content)       Screen1 About you · Screen2 Your body · Screen3 Goal & measurements
                           · Screen4 Plan reveal (+ adjust mode).
```

- Draft state lives in the ViewModel so back-navigation preserves entries; nothing is persisted until
  finish (except: completing is the single write point).
- Reuse existing components: `GlassInputField`, pill toggles,
  `ModalBottomSheet` + `ProfileOptionRow`/`OptionSheet`, Material3 `DatePickerDialog`,
  `LiquidPrimaryButton` / `LiquidSecondaryButton`, `FrostedCard`/`NeutralCard`, `SectionLabel`.
  No new general-purpose composables unless nothing fits.
- Onboarding is **not** an AI feature → no `TintedCard` / 🤖 styling.

## Validation & edge cases

- **Required-field gating:** each screen's Continue is disabled until its required fields are valid.
  Height, weight in sensible positive ranges; birth date not in the future and yielding a plausible
  age; sex / goal / activity must be chosen.
- **Units:** changing units re-labels and converts the height/weight inputs; values persist in metric
  internally (`heightCm` Int, `bodyWeightKg` Double) regardless of display unit.
- **Plan generation failure:** `PlanGenerator` reports missing inputs — should be unreachable given
  gating, but if it returns a non-success outcome, surface a message and keep the user on Screen 3
  rather than showing an empty Screen 4.
- **Back navigation / process death:** back moves to the previous step; draft state should survive
  configuration changes (ViewModel) — surviving full process death mid-flow is **not required** (a
  restarted incomplete onboarding simply starts over).

## Out of scope / non-goals

- No Room schema migration (current `daily_logs` already has `bodyWeightKg` / `waistCm`; only a new
  DataStore boolean is added — no DB version bump).
- No re-run / "edit onboarding" entry point — profile and plan remain editable via the existing
  Profile and Plan screens.
- No changes to the Profile/Plan/Settings screens themselves.
- No analytics/telemetry.

## Open questions

None outstanding. (Profile photo and gym sessions resolved as out-of-scope; plan reveal resolved as
reveal-with-optional-override; structure resolved as 4 screens, direction B.)
