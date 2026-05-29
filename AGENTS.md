# Recomp Tracker Agent Rules

This is a local-first Android MVP for personal recomposition tracking.

## Non-negotiables
- Kotlin, single Activity, Jetpack Compose, Material 3, Room, DataStore, Coroutines/Flow.
- No backend, accounts, network sync, subscriptions, social features, onboarding, barcode scanning, or hosted AI.
- Room is the source of truth for logs, meals, foods, marker lifts, and weekly reviews.
- DataStore stores targets, thresholds, units, and review cadence.
- Domain logic stays pure Kotlin and independent from Android framework classes.
- UI state is exposed through ViewModels and `StateFlow`.
- Export/import is local JSON through Android Storage Access Framework.
- Gemma is excluded from MVP decisions; any future AI may summarize rule outputs only.

## Testing expectations
- Run `./gradlew test` for domain logic.
- Run `./gradlew assembleDebug` before claiming the app compiles.
- Run `./gradlew connectedAndroidTest` when an Android emulator/device is available.
- Core trend, adherence, macro total, and adjustment logic must have unit tests.

## Code style
- Keep the MVP in one app module.
- Prefer simple manual dependency wiring in `AppContainer`.
- Prefer small data classes and deterministic functions over implicit UI-side logic.
- Do not add unfinished TODOs, fake implementations, mock-only features, or placeholder screens.
