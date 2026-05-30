# Four-Tab Navigation Redesign - Design Spec

**Date:** 2026-05-30  
**Scope:** Replace the five-tab navigation with focused Home, Food, Body, and More destinations while preserving the existing dark visual language and data flow.

## 1. Goal

Make the most common daily actions easier to find:

- `Home` becomes a compact recomposition dashboard.
- `Food` owns the complete daily nutrition workflow: calorie zone, macros, meal slots, and food logging entry points.
- `Body` owns the daily body and recovery check-in.
- `More` contains secondary analysis and setup: Stats, Charts, Plan, and Settings.

The redesign changes navigation and screen composition only. Existing Room entities, DataStore preferences, repositories, and pure domain calculations remain unchanged.

## 2. Considered Approaches

### Selected: four purpose-specific tabs

Use `Home`, `Food`, `Body`, and `More` as the bottom navigation destinations. Move detailed Stats and Charts under More.

This keeps the bottom bar focused on daily behavior while retaining detailed analysis one tap away. It also avoids duplicating nutrition information between Home and Food.

### Rejected: keep Stats and Charts as bottom tabs

This preserves the current implementation but leaves daily Food and Body workflows buried inside the current Today screen. It also makes the bottom bar analysis-heavy for an app primarily used for daily tracking.

### Rejected: combine Stats and Charts into the Home screen

This would make Home dense and scroll-heavy. Home should summarize decisions and direct users to deeper analysis without reproducing the detailed Stats and Charts screens.

## 3. Navigation

| Destination | Route | Material icon | Role |
|---|---|---|---|
| Home | `home` | `Home` | Recomposition dashboard summary |
| Food | `food` | `Restaurant` | Nutrition target, meal slots, and logging |
| Body | `body` | `Favorite` | Daily body and recovery check-in |
| More | `more` | `MoreHoriz` | Secondary analysis and setup links |

Detailed screens remain navigable routes but are removed from the bottom bar:

| Screen | Route | Entry point |
|---|---|---|
| Stats | `stats` | More |
| Charts | `charts` | More |
| Plan | `plan` | More |
| Settings | `settings` | More |
| Food library | Existing `food_library` route | Food meal-slot add actions and Food library action |

The bottom bar remains visible on all routes for consistency with the existing app behavior.

## 4. Home Dashboard

Home reuses `DashboardViewModel` as its state source. The screen is redesigned into a concise summary:

1. Header: `Dashboard` with a short recomposition snapshot subtitle.
2. Weekly direction card:
   - adjustment verdict
   - short adjustment summary
   - recommended calorie change when non-zero
3. Snapshot grid:
   - weight trend per week
   - adherence percentage
   - calories logged today
   - logged-day count
4. Quick actions:
   - `Log food` navigates to Food
   - `Update body check-in` navigates to Body

Detailed target data and trend breakdowns stay in Stats. Charts stay in their existing detailed screen.

## 5. Food Screen

Food reuses `TodayViewModel` so daily totals and meal operations continue to use the current repository flow.

The screen contains:

1. Header: `Food`, with a Material `Add` action that opens the standalone food library.
2. Existing shared `CalorieZoneBar` and `MacroMiniBar` components.
3. Meal-slot management:
   - locked meal cards with totals and `+ Add`
   - existing edit mode for slot reorder, rename, and delete
   - add-meal-slot action
4. Library action:
   - `Browse saved foods & meals` opens the standalone food library

The existing slot-specific `FoodLibraryScreen` flow remains unchanged. Selecting `+ Add` on a slot opens the library with the slot ID and name.

## 6. Body & Recovery Screen

Body reuses `TodayViewModel` and moves the existing metrics form out of the old Today screen:

- weight
- waist
- steps
- sleep
- energy
- hunger
- soreness
- training-day toggle
- notes
- save action and current validation message

The metrics state, Health Connect hydration, dirty-state handling, and repository save behavior remain unchanged.

## 7. More Screen

More uses grouped, full-width menu rows with Material icons and chevrons:

### Insights

- Stats
- Charts

### Planning

- Plan

### App

- Settings

Food library access moves to Food because it belongs to the nutrition workflow.

## 8. Visual Language

Preserve the established design tokens from the previous UI redesign:

- dark app background and dark cards
- blue active navigation pill and blue action accents
- muted gray supporting text
- single-color Material icons
- compact uppercase section labels
- rounded Material 3 cards and controls

Do not introduce a new theme, emoji icons, or decorative visual effects.

## 9. Component Boundaries

Split the current `TodayScreen` composition into focused screens while preserving stateless child composables:

- `FoodScreen` renders nutrition and meal-slot UI.
- `BodyRecoveryScreen` renders daily metrics.
- Shared meal-slot child composables move with Food or into a focused shared file if needed.
- `DashboardScreen` keeps its existing ViewModel but adopts the compact dashboard composition.
- `MoreScreen` receives navigation callbacks for Stats, Charts, Plan, and Settings.

Screen-level composables collect `StateFlow`. Child composables receive values and event lambdas.

## 10. Error Handling

- Keep the existing body metrics validation and message display.
- Keep blank meal-slot names as no-op behavior.
- Keep existing Food Library slot navigation encoding and decoding.
- Navigation menu actions use existing local routes only.

## 11. Testing And Verification

- Add or update Compose UI tests for the four bottom navigation destinations.
- Verify Food shows nutrition totals and meal slots.
- Verify Body shows the recovery form.
- Verify More exposes Stats, Charts, Plan, and Settings.
- Run `./gradlew test`.
- Run `./gradlew assembleDebug`.
- Run `./gradlew connectedAndroidTest` when an emulator or device is available.

## 12. Out Of Scope

- Room schema changes
- DataStore preference changes
- Dashboard chart redesign
- Food-library feature changes
- New analytics calculations
- Health Connect behavior changes
