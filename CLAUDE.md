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

## Source of Truth

- **Room** — all logs, meals, food library, body entries, weekly reviews
- **DataStore** — plan targets (`PlanPreferences`), app prefs (`AppPreferences`), user profile (`UserProfilePreferences`)

## Build

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:compileDebugKotlin     # type-check only
```

## AI Coach System

@docs/ai-coach.md
