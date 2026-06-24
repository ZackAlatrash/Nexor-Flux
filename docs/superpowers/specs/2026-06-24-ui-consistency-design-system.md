# UI Consistency Design System

**Date:** 2026-06-24
**Branch:** `feat/ui-consistency-design-system`
**Status:** Approved — ready for implementation plan

## Problem

The app's screens look similar at a glance but diverge in detail. The root cause is
that there is **no typography scale**: every screen hardcodes `fontSize` / `fontWeight`
inline, and headers, padding, spacing, cards, and buttons were each rebuilt ad-hoc per
screen. A good token + component foundation already exists (`DesignTokens.kt`,
`AppColors`, `FrostedCard`/`NeutralCard`/`TintedCard`, `SectionLabel`, the `Liquid*Button`
family) — screens simply drift from it.

### Audited inconsistencies (across ~25 screens)

| Dimension | Drift observed |
|---|---|
| Screen title | `28sp ExtraBold/-0.8` (most tab screens) vs `21sp Medium` (TrainHome) vs `18–19sp SemiBold` (Active/SessionDetail/Picker/Stats) vs `17sp ExtraBold/-0.3` (FoodLibrary, RecipeBuilder) vs `22sp Bold` (Coach, SessionSummary centered) vs `headlineMedium` (Plan, Foods) vs Material `TopAppBar` (BodyEdit, BodyHistory) |
| Back button | `40dp CircleShape` vs `34dp RoundedCornerShape(10)` vs `34dp RoundedCornerShape(100)` vs `IconButton` vs `TopAppBar` nav icon |
| Header padding | vertical `18`/`14`/`12`dp; horizontal `20`/`16`/`8`/`4`dp — often differs from the screen's own content padding |
| Content h-padding | `16dp` (tab screens) vs `14dp` (train cards) vs `20dp` (onboarding/review/scanner) |
| Section spacing | `spacedBy(10dp)` vs `14dp` vs manual `padding(bottom = 8/12/14dp)` (train) |
| Section labels | shared `SectionLabel()` (9sp Bold/0.14) vs inline `9sp Bold/0.12 textMuted` vs `11–12sp Normal/0.4` (train) vs `9sp Bold/0.4 textSecondary` |
| Cards | `FrostedCard`/`NeutralCard` vs raw `.background/.border` boxes vs bespoke `SettingsCard`/`MenuCard`/`SectionCard`/`DataCard` vs Material `Card` (body) |
| Buttons | `Liquid*Button` family (good) **plus** Material `Button` (AiCoach), `Surface` button (BodyHistory), raw `Box` clickables; heights `36/40/44/48/50` |
| Icons | Material icons mixed with emoji/text glyphs (`✓ ✕ › → ↑ ↓ ⏱ ◆ 🗓`); chevrons at `14/16/18sp` |

Biggest outliers: the **Train** screens (smaller/lighter titles, manual spacing), the
**Body** sub-screens (Material `TopAppBar`), and **Plan/Foods** (`headlineMedium`).

## Decisions

1. **Two-tier headers.** Top-level tab destinations get the big display header; pushed
   sub-screens get a compact header with a standard back button. One reusable component
   per tier.
2. **Full migration.** Build the foundation, then migrate every screen in this one
   branch. Build after each batch; the user does visual verification per screen.
3. **Named data-display scale.** Big numbers (calorie hero, metric tiles) keep their
   emphasis via named display/stat text styles rather than being flattened.

## Design

### 1. Typography scale — `ui/theme/Typography.kt` (new)

A named `AppType` object exposing `TextStyle`s. **Color is not baked in** — it is passed at
the call site via `Text(..., color = appColors.x)` because color is theme-dependent
(dark/light). Styles carry only size / weight / letterSpacing / lineHeight.

| Token | Spec | Replaces |
|---|---|---|
| `screenTitle` | 28sp ExtraBold, -0.8 | tier-1 tab titles |
| `screenTitleCompact` | 20sp Bold, -0.4 | the 17/18/19/21/22sp + `headlineMedium` sub-screen titles |
| `screenSubtitle` | 13sp Normal | header subtitles |
| `sectionLabel` | 9sp Bold, 0.14, UPPERCASE | all section labels (incl. the 11–12sp/0.4 train variant) |
| `cardTitle` | 15sp SemiBold | row/card titles (currently 14–16sp) |
| `cardSubtitle` | 12sp Normal | secondary row text (currently 10–12sp) |
| `body` | 13sp Normal | body copy |
| `label` | 11sp Medium | small labels |
| `metaLabel` | 9sp Bold, 0.4, UPPERCASE | tile/stat captions |
| `displayLarge` | 36sp ExtraBold, -0.5 | hero metric numbers (Body 36, Dashboard calorie) |
| `displayHero` | 44sp ExtraBold, -1.0 | onboarding result calorie |
| `statValue` | 22sp Bold, -0.3 | tile big numbers (Food total, summary stats) |
| `statValueSmall` | 17sp Bold | compact stat values |

`SectionLabel()` is updated to render via `AppType.sectionLabel` (its current values match,
so no visual change) to keep one source of truth.

### 2. Spacing / layout tokens — added to `DesignTokens.kt`

```kotlin
val ScreenPaddingH = 16.dp     // canonical horizontal padding for every screen
val ScreenSpacing  = 10.dp     // vertical gap between cards/sections in a screen

object Spacing {               // in-card / inline spacing
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
}
```

Corner-radius tokens (`CornerSmall/Card/Chip/Pill`) already exist and are unchanged.
`FrostedCard`/`NeutralCard` keep their 16.dp default content padding.

### 3. Two-tier header + scaffold — `ui/component/ScreenScaffold.kt` (new)

- **`ScreenScaffold`** — the standard screen frame: `Box(fillMaxSize)` + background +
  `LazyColumn` with `contentPadding = PaddingValues(horizontal = ScreenPaddingH, top = 4.dp,
  bottom = FloatingNavHeight + 16.dp)` and `verticalArrangement = spacedBy(ScreenSpacing)`.
  A variant without the nav-bar bottom inset is provided for pushed sub-screens. Exposes a
  `LazyListScope` builder so screens add their items.
- **`ScreenHeader`** (tier-1) — `AppType.screenTitle` + optional `screenSubtitle` + optional
  trailing slot (`@Composable () -> Unit` for avatar / action). Header vertical padding 18dp.
  Used by the 6 tab destinations (Dashboard, Food, Body, Trends, Train, More).
- **`SubScreenHeader`** (tier-2) — canonical **40dp CircleShape back button**
  (`Icons.AutoMirrored.Filled.ArrowBack`, 1dp hairline border) + `AppType.screenTitleCompact`
  + optional subtitle + optional trailing slot. Header vertical padding 14dp. Replaces every
  ad-hoc back button **and the Material `TopAppBar`s** in BodyEdit / BodyHistory.

### 4. Component consolidation

- **Cards** → `FrostedCard` (primary data / featured) and `NeutralCard` (list rows, forms,
  menus). Migrate raw `.background/.border` boxes and bespoke `SettingsCard` / `MenuCard` /
  `SectionCard` / `DataCard` / Material `Card` onto these two. Specialized cards that carry
  real semantics (`DangerCard` red-tint, `TintedCard` AI) stay but are re-expressed in terms
  of the shared primitives where practical.
- **Buttons** → the `Liquid*Button` family only. Replace the Material `Button` in AiCoach and
  the `Surface`-based button in BodyHistory. Standard heights: full-width primary **48dp**,
  compact inline **36dp**, small **32dp**. Remove per-call-site height overrides that don't
  map to these.
- **Section labels** → `SectionLabel()` everywhere; retire all inline label `Text`s.
- **Icons** → Material icons for UI affordances (back, chevron, add, close). Retire text-glyph
  stand-ins used as icons (`› → ✕ ✓ ↺`). Keep emoji that are genuinely *content* (e.g. food).
  Standard chevron via `Icons.Default.ChevronRight` at a single size.

### 5. Migration scope & order — all ~25 screens, one branch

Migrate screen-by-screen, running `./gradlew :app:compileDebugKotlin` after each batch.
Suggested batches:

1. **Foundation** — Typography.kt, DesignTokens spacing, ScreenScaffold/ScreenHeader/SubScreenHeader, update `SectionLabel`.
2. **Tab destinations** — Dashboard, Food, BodyRecovery, Progress (Trends), More.
3. **Train** — TrainHome, ActiveSession, SessionSummary, SessionDetail, RoutineBuilder, ExercisePicker, ExerciseStats.
4. **Settings / profile** — Profile, Appearance, Integrations, DataBackup, Plan.
5. **Food / library** — FoodLibrary, Foods, RecipeBuilder, BarcodeScanner.
6. **Coach / onboarding / body** — Coach, AiCoach, Onboarding, BodyEdit, BodyHistory, Review overlay.

The user performs visual verification per screen in the running app; this spec does not
drive the emulator.

### 6. Documentation deliverables

- **This spec** — the decisions + migration plan (committed now).
- **`docs/design-system.md`** — the durable, forward-looking design guide that future agents
  read before creating or changing UI. It documents the type scale, spacing tokens, the
  header tiers, the card/button/label/icon rules, and "do / don't" examples. It is linked from
  `CLAUDE.md` so it's always in context.

## Non-goals

- No color-palette or accent-theme changes (the accent system stays as-is).
- No navigation-structure or screen-flow changes.
- No new features; this is purely a consistency refactor.
- No changes to the liquid-glass nav bar material or backdrop system.

## Risks

- Large diff across many files. Mitigated by batching + compile checks + per-screen visual
  verification.
- Subtle visual regressions (a screen that relied on an off-scale size). Mitigated by keeping
  the named scale close to the dominant existing values, so most screens shift only slightly.
- `FoodScreen.kt` / `ActiveSessionScreen.kt` carry unrelated uncommitted edits from another
  session; per user direction these are not a concern and will be migrated normally.
