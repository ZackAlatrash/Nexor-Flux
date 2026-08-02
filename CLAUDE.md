# Personal Dietitian — Claude Context

Android body recomposition tracking app. Kotlin, Jetpack Compose. Modules: `:app` (the app) + `:macrobenchmark` (baseline-profile generator).

**Package:** `com.zack.recomptracker`
**Min SDK:** 26 | **Target/Compile SDK:** 37

## Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material3 (custom glass design system) |
| State | ViewModel + StateFlow + collectAsStateWithLifecycle |
| Local DB | Room (SQLite), schema **v15**, `exportSchema = false` |
| Preferences | AndroidX DataStore (Preferences) — 11 stores |
| Secrets | EncryptedSharedPreferences (`SecureKeyStore`: cloud + Tavily API keys) |
| Async | Coroutines + Flow; WorkManager for background sync/digest |
| AI | **Cloud-only** LLM via OpenAI-compatible API (OpenRouter; OkHttp SSE streaming + tool calling). The on-device Gemma/LiteRT stack was removed — `AiCoachBoundaryTest` enforces it stays gone. |
| Health | Health Connect (steps/weight/sleep read + 365-day nutrition import) |
| Camera | CameraX + ML Kit barcode scanning (Open Food Facts lookup) |
| Navigation | Navigation Compose |
| DI | Manual — `core/AppContainer` (no Hilt), built in `Application.onCreate` |

## Layer Structure

```
ui/          Compose screens + ViewModels
data/        Room entities/DAOs, DataStore preferences, repositories,
             remote clients (OpenAiCompatClient, Tavily, Open Food Facts),
             Health Connect sync, proactive-coach spine (data/coach)
domain/      Pure Kotlin: adjustment engine, trend, adherence, plan/rebalance
             engines, food logic, workout analyzers, coach signal detectors
core/        AppContainer, DateProvider, shared models, formatters
ai/          Cloud AI: coach chat coordinator, insight cards, weekly briefing,
             phrasing services, recipe namer, knowledge-base retrieval
```

**Dependency rule:** UI → ViewModels → Repositories → Room/DataStore. Domain is pure Kotlin (no Android imports; `domain/plan`+`domain/rebalance` importing `data.preferences` models is a documented exception). AI layer depends on repositories and preferences but not on UI.

## Design System

@docs/design-system.md

All UI must use the shared design tokens and components — read the guide before creating or
changing any screen. Never hardcode `fontSize`/`fontWeight`: use `AppType`. Use `ScreenScaffold`
+ `ScreenHeader` (tab destinations) / `SubScreenHeader` (pushed screens) for screen frames, the
`FrostedCard`/`NeutralCard` family for cards, and the `Liquid*Button` family for buttons.

## Source of Truth

- **Room** (schema v15) — all logs, meals, food library, NEVO catalog, body entries, weekly
  reviews, recipes, workout templates/sessions, exercise library, plan-version ledger, usage events.
  Meal entries carry a `planned` flag: planned (not-yet-eaten) entries are excluded from eaten
  totals, adherence, and trend until confirmed. See `docs/superpowers/specs/2026-06-10-planned-meals-design.md`.
  Per-day targets resolve through the `plan_versions` ledger (`PlanHistory`) — a plan change never
  re-judges already-logged days.
- **DataStore** — plan targets (`PlanPreferences`), UI prefs incl. cloud base-URL/model
  (`UiPreferences`), user profile (`UserProfilePreferences`), coach inbox/journey/memory/
  push-history/notification-prefs/experiment stores, rebalance state (`DataStoreRebalanceStore`).
- **Weekly Rebalance** is an *effective-target overlay* (`domain/rebalance/EffectiveTargets`) —
  it never mutates the base plan; consumers resolve effective targets per date.

## Build

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # unit tests (~1300 tests)
./gradlew :app:compileDebugKotlin     # type-check only
```

Note: `ai/harness/InsightHarnessTest` makes **live** OpenRouter calls when `.env.test` (or
`INSIGHT_*` env vars) is present, and can fail on upstream rate limits; it self-skips when
credentials are absent. Signing uses the committed shared keystore — never regenerate it.

## AI Coach System

@docs/ai-coach.md

## Current Review & Roadmap

Full-project code review (July 2026): `docs/reviews/2026-07-full-project-code-review.md` —
P0/P1 findings with file:line. Prioritized roadmap: `docs/improvement-plans/00-roadmap-2026-07.md`.
Note: the three P0s in that review are **already fixed** on `develop` — the doc is stale, the code
is not.

## iOS Port

A native Swift/SwiftUI iOS app is planned. **If you are doing iOS work, read
`docs/ios-port/STATUS.md` first** — it is the session spine and names the current phase.

Architecture: native SwiftUI on iOS 26+, sharing **only `domain/`** via a KMP `:shared` module
(GRDB, not SwiftData; `URLSession`, not Ktor; dates persist as `YYYY-MM-DD` strings). Full
assessment and 7-phase roadmap: `docs/ios-port/00-feasibility-and-roadmap.md`. Binding conventions:
`docs/ios-port/decisions.md`. Progress: `docs/ios-port/parity-ledger.md`.

**Phase 0 restructures Gradle** (`:app` → `:app` + `:shared`) — it is exclusive, so no parallel
Android branch work while it lands. Every later phase only adds files under `ios/`.
