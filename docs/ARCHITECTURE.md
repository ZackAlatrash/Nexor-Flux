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
Room is the source of truth for logs, meals, personal foods, imported reference foods, saved meals, marker lifts, and weekly reviews. DataStore is the source of truth for plan targets and thresholds. Repositories expose `Flow` streams, ViewModels combine those streams into screen state, and Compose collects state with lifecycle-aware collection.

## Local food imports
- `PersonalFoodRepository` exports and merge-imports editable personal foods as local JSON.
- `FoodCatalogRepository` imports an official NEVO CSV export after the user accepts RIVM's download conditions. It transactionally replaces only the prior NEVO catalog.
- `HealthConnectRepository` can read named nutrition records from the last 365 days when Health Connect supports and grants full-history access. The Settings review flow deduplicates those records and saves only selected rows as personal foods.
- Health Connect nutrition history is not a lossless Samsung Health library export: the records do not guarantee the original serving weight. The review dialog makes clear that selected macros become the app's 100g baseline.
