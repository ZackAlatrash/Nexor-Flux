# Architecture

Recomp Tracker is a single-module Android MVP using Kotlin, Jetpack Compose, Room, DataStore, Coroutines, and Flow.

## Layers
- `ui`: Compose screens and ViewModels. Screens render state and forward events. ViewModels expose `StateFlow`.
- `data`: Room entities/DAOs/database, DataStore preferences, repositories, and JSON backup/restore.
- `domain`: Pure Kotlin adjustment, trend, moving-average, adherence, and export models.
- `core`: App-wide helpers such as `AppContainer`, date provider, model classes, and formatting utilities.
- `ai`: MVP guardrails for future Gemma summaries. AI does not make calorie decisions.

## Dependency flow
UI depends on ViewModels. ViewModels depend on repositories and pure domain calculators. Repositories depend on Room/DataStore. Domain logic does not import Android framework classes.

## Dependency wiring
The MVP uses manual dependency wiring through `AppContainer`, as required by the plan. Hilt is intentionally not used yet.

## Data flow
Room is the source of truth for logs, meals, foods, saved meals, marker lifts, and weekly reviews. DataStore is the source of truth for plan targets and thresholds. Repositories expose `Flow` streams, ViewModels combine those streams into screen state, and Compose collects state with lifecycle-aware collection.
