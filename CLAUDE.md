# Personal Dietitian — Claude Context

Android body recomposition tracking app. Kotlin, Jetpack Compose, single-module.

**Package:** `com.zack.recomptracker`
**Min SDK:** 26 | **Target SDK:** 35

## Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material3 |
| State | ViewModel + StateFlow + collectAsStateWithLifecycle |
| Local DB | Room (SQLite) |
| Preferences | AndroidX DataStore (Preferences) |
| Async | Coroutines + Flow |
| AI | LiteRT-LM (on-device Gemma 4 2B) |
| Navigation | Navigation Compose |
| DI | Manual — `AppContainer` (no Hilt) |

## Layer Structure

```
ui/          Compose screens + ViewModels
data/        Room entities/DAOs, DataStore preferences, repositories
domain/      Pure Kotlin: adjustment engine, trend, adherence, food logic
core/        AppContainer, DateProvider, shared models, formatters
ai/          On-device AI: insight cards + coach chat
```

**Dependency rule:** UI → ViewModels → Repositories → Room/DataStore. Domain is pure Kotlin (no Android imports). AI layer depends on repositories and preferences but not on UI.

## Design System

@docs/design-system.md

All UI must use the shared design tokens and components — read the guide before creating or
changing any screen. Never hardcode `fontSize`/`fontWeight`: use `AppType`. Use `ScreenScaffold`
+ `ScreenHeader` (tab destinations) / `SubScreenHeader` (pushed screens) for screen frames, the
`FrostedCard`/`NeutralCard` family for cards, and the `Liquid*Button` family for buttons.

## Source of Truth

- **Room** (schema v13) — all logs, meals, food library, body entries, weekly reviews, recipes.
  Meal entries carry a `planned` flag: planned (not-yet-eaten) entries are excluded from eaten
  totals, adherence, and trend until confirmed. See `docs/superpowers/specs/2026-06-10-planned-meals-design.md`.
- **DataStore** — plan targets (`PlanPreferences`), app prefs (`AppPreferences`), user profile (`UserProfilePreferences`)

## Build

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:compileDebugKotlin     # type-check only
```

## AI Coach System

@docs/ai-coach.md
