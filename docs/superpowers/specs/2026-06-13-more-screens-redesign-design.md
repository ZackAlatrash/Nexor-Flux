# More Section Redesign — Design Spec

**Date:** 2026-06-13
**Branch:** `feature/more-screens-redesign`
**Status:** Approved structure + per-screen UI; pending spec review.

## Goal

Rebuild the "More" tab and every screen reachable from it. Today More is a junk
drawer that (a) re-links to tabs the user already has, (b) duplicates half of a
906-line Settings monolith, and (c) buries real configuration in inline sprawl.
The redesign turns More into a clean **hub of 8 focused screens**, eliminates all
duplication, de-duplicates the profile data model, and applies the app's existing
dark-violet liquid-glass language consistently.

Non-goals: changing the bottom-nav tabs (Home · Body · Food · Coach · More),
redesigning Home/Body/Food/Coach, or altering the domain engines (adjustment,
trend, adherence, plan calculator).

## Design language (applies to every screen)

- Dark violet gradient background (`#0D0818 → #0F0B1C → #090A12`) with the
  existing `GlassOrbBackground`.
- Frosted glass cards: white ~4% fill (`CardSurface`), ~7% border (`CardBorder`),
  rounded ~15–18dp.
- Accent violet (`#8B5CF6` family), themeable via the existing 11 `AccentTheme`s.
- **AI convention (global):** every AI-related section/card across the whole app
  uses the tinted-violet card (`TintedSurface`/`TintedBorder`) with a 🤖 header —
  identical to the "Why this verdict" card. No AI feature gets a plain card.
- Section headers: small uppercase muted labels. Rows: icon tile · title ·
  subtitle · chevron.

## Information architecture — the new "More" hub

More dissolves the standalone "Settings" screen entirely. It becomes two groups:

```
MORE
├─ INSIGHTS
│   • Calorie Decision   — FEATURED live card (verdict + adherence preview)
│   • Trends             — normal list row
└─ SETUP  (fuller list rows: icon · title · subtitle · chevron)
    • Profile
    • Plan
    • Appearance
    • AI & Coach
    • Integrations
    • Data & Backup
```

### What was removed / merged vs. today

| Today | Fate |
|---|---|
| Insights → Stats (old `DashboardScreen`) | **Kept & renamed** "Calorie Decision" (it's the only verdict surface). |
| Insights → Charts + Insights → Progress (both `ProgressScreen`) | **De-duplicated** into one **Trends** screen. Old `charts`/`progress` routes collapse to one. |
| Appearance → Font (More) + Accent (Settings) | **Merged** into one Appearance screen. |
| AI config (More inline) + AI "integrations" framing (Settings) | **Merged** into one AI & Coach screen. |
| Settings → Health Connect + Samsung Health + NEVO | **Merged** into Integrations. |
| Data → Export/Import (More) **and** Backup (Settings) | **De-duplicated** into one Data & Backup screen. |
| Settings → Profile | **Extracted** into its own Profile screen. |
| "Settings" monolith + Health Connect read-only badge in More | **Deleted** as standalone concepts. |

## Per-screen designs (all approved via visual companion)

### 1. Calorie Decision  *(old `DashboardScreen` / "stats" route, restyled)*
Purpose: surface the adjustment engine's recommendation. **No "Today" intake card**
(that lives on Home + Logs). Layout, top→bottom:
1. **Verdict hero** — big verdict ("Hold steady"), recommended kcal change, reason-code chips.
2. **AI "Why this verdict"** — tinted AI card (existing multi-state: model missing/
   downloading/generating/ready/error, plus WAIT_FOR_DATA notice).
3. **Current targets** — calories + P/C/F.
4. **Trend summary** — weight 7-day avg, weight trend, waist trend, adherence, logged days.

### 2. Trends  *(the kept `ProgressScreen`, restyled)*
Reuses the existing chart-card component. Layout:
- 7d / 14d / 28d range selector.
- **AI "Trend analysis"** tinted card at top.
- **Body** section: Weight, Waist (full-width chart cards).
- **Nutrition** section: Calories (featured), Protein, Carbs, Fat.
- **Performance** section: Lifts.
- **New per-card treatment:** each chart card gets a **per-metric color** and a
  **verdict pill** beside the value. Pill colors: green = good, amber = caution,
  rose = off-track, neutral = steady. Verdict text derived from the metric's trend
  vs. its goal (e.g. Weight "On track ↓", Carbs "Slightly over", Waist "Steady").

### 3. Plan  *(`PlanScreen`, restyled — option B: advanced tucked away)*
- **Generate from profile** hero button (existing dialog flow: weight entry →
  preview → apply).
- **Nutrition targets** card: Calories + P/C/F.
- **Calorie zone** card: lower + upper bound (surfaced, since users touch these).
- **Advanced → Review rules** collapsible (default collapsed): weight-trend
  threshold, waist threshold, min adherence, review cadence, phase start date,
  metric units toggle.
- **Save plan** (primary) + **Defaults** (ghost) buttons.

### 4. Profile  *(extracted from Settings; data model changes — see below)*
- **Header:** circular avatar with photo upload + editable name.
- **Current weight** read-only context card (pulled from Body tracking; links to
  Body; no duplicate storage). No target-weight field exists in the app today.
- **Plan inputs** card: Goal (picker row + hint), Activity level (picker row +
  hint), Gym sessions/week (stepper 0–7).
- **About you** card: Biological sex (picker row — rarely changes, demoted from
  prominent chips), Date of birth (date picker; **age derived** and shown), Height.
- Goal (7 options) and Activity (4 options) open a bottom sheet listing all options
  with their descriptive hints; the row shows the current selection + hint.

### 5. Appearance  *(merged Font + Accent — option A: live preview)*
- **Preview** card at top: sample text + button + chip that reflect the currently
  selected font and accent live.
- **Font** — full-width cards, each rendering "Aa" in its real typeface with a
  description (Default / Space Grotesk / Plus Jakarta Sans).
- **Accent color** — swatch grid of all 11 `AccentTheme`s; selected one ringed.

### 6. AI & Coach  *(merged from More-inline + Settings — option B: radio cards)*
All cards use the tinted AI style.
- **Enable AI** master toggle (tinted card with 🤖). When off, the rest is hidden.
- **Engine** — two descriptive radio cards: **On-device (Gemma)** / **Cloud API**.
- **Contextual config** for the selected engine:
  - On-device: model manager — current model + status badge (Ready/Missing/
    Downloading w/ progress), size, Download/Retry/Delete/Cancel; larger-model
    download option.
  - Cloud: connection card — provider preset chips (OpenRouter/OpenAI/Groq/Custom),
    Base URL, Model ID, API key (password), Test connection.

### 7. Integrations  *(merged HC + Samsung + NEVO — option B: grouped)*
- **Health sync** group: **Health Connect** full card — enable toggle, status/last-sync
  meta, "Sync now" + "Import foods" actions, install link + permission prompts as today.
- **Food sources** group (both feed the food library, so compact rows):
  - Samsung Health — "Import" (food_info CSV, normalized to per-100g, with progress).
  - NEVO catalog — "Manage" (import/replace CSV, version display, remove; RIVM note).

### 8. Data & Backup  *(merged; kills More's duplicate — option A: grouped sections)*
- **Full backup**: Export backup (all data → JSON) · Import backup (restore, with
  confirmation).
- **Personal foods**: Export personal foods · Import personal foods (JSON/CSV).
- **Danger zone** (red-tinted card): Clear food library · Reset logs only · Reset
  all local data — each with its existing confirmation dialog.

## Data model changes (`UserProfilePreferences`)

| Change | Field | Reason |
|---|---|---|
| **Remove** | `plannedTrainingDays` | Collected but read by nothing in the app. |
| **Remove** | `trainingExperience` (+ enum) | Collected but read by nothing (not plan, not coach). |
| **Add** | `name: String?` | For the new profile header. |
| **Add** | `profilePhotoUri: String?` | For the avatar; photo copied to internal storage, URI persisted. |
| **Replace** | `ageYears: Int?` → `birthDate` (ISO `YYYY-MM-DD` or epoch-day) | Store DOB; derive age for the TDEE calc. |
| **Keep** | `weeklyGymSessions` | Feeds the AI coach system prompt. |
| **Keep** | `heightCm`, `biologicalSex`, `activityLevel`, `goal` | Required by `PlanGenerator`/`PlanCalculator`. |

- `PlanGenerator`/`PlanCalculator` continue to receive an `ageYears` value —
  computed from `birthDate` at call time (relative to the current date). The
  missing-fields check uses `birthDate` instead of `ageYears`.
- The AI coach prompt builders (`CoachToolsAdapter`, `GemmaCoachCoordinator`) drop
  any reference to removed fields; `weeklyGymSessions` line is retained.
- DataStore serialization: dropped fields are simply no longer read; `birthDate`
  is a new field. Existing installs with an `ageYears` value: migrate by converting
  to an approximate `birthDate` (Jan 1 of `currentYear - ageYears`) on first read,
  or treat as unset and prompt — **decision point for the plan phase.**

## Navigation changes (`AppNavGraph` / `RecompApp`)

- More hub routes to 8 destinations: `calorie_decision` (was `stats`), `trends`
  (replaces both `charts` and `progress`), `profile`, `plan`, `appearance`,
  `ai_coach`, `integrations`, `data_backup`.
- Bottom tabs unchanged: Home · Body · Food · Coach · More.
- `TopLevelDestination.Progress` enum entry + the duplicate `charts`/`progress`
  routes are removed/collapsed to the single `trends` route.
- The standalone `settings` route/screen is removed; its content is split across
  Profile / Appearance / Integrations / Data & Backup, and AI config moves to
  AI & Coach.

## Affected files (indicative, not exhaustive)

- `ui/more/MoreScreen.kt`, `MoreViewModel.kt` — rebuilt as the hub.
- `ui/dashboard/DashboardScreen.kt` — `DashboardScreen` composable restyled →
  Calorie Decision (the `HomeDashboardScreen` for the Home tab is untouched).
- `ui/progress/ProgressScreen.kt` — restyled Trends + per-metric color/verdict pills.
- `ui/plan/PlanScreen.kt` — collapsible advanced rules.
- New: `ui/profile/`, `ui/appearance/`, `ui/aicoach/`, `ui/integrations/`,
  `ui/databackup/` screens + ViewModels (extracted from `ui/settings/`).
- `ui/settings/SettingsScreen.kt` / `SettingsViewModel.kt` — decomposed and removed.
- `data/preferences/UserProfilePreferences.kt` + `UserProfilePreferencesStore.kt`
  — field changes + migration.
- `domain/plan/PlanGenerator.kt` — birthDate→age.
- `ai/CoachToolsAdapter.kt`, `ai/GemmaCoachCoordinator.kt` — drop removed fields.
- `ui/navigation/AppNavGraph.kt`, `ui/RecompApp.kt` — route changes.

## Testing

- Unit: `PlanGenerator` age-from-birthDate computation; missing-field check uses
  birthDate; profile migration (ageYears → birthDate).
- Unit: Trends per-metric verdict derivation (good/caution/off-track/steady).
- Existing domain tests (adjustment/trend/adherence) must remain green — engines
  are untouched.
- Manual: each of the 8 screens renders, navigates from the hub, and persists its
  settings; backup export/import round-trips; AI engine switch (on-device ↔ cloud).

## Open decisions for the implementation plan

1. **ageYears → birthDate migration** strategy (approximate DOB vs. prompt-on-next-open).
2. Whether to add a real **target-weight** field (out of scope unless requested).
3. Profile photo storage mechanism (internal file copy vs. persistable URI permission).
