# Decisions

## 2026-05-29: Build from empty workspace
The workspace only contained `android-recomp-tracker-mvp-plan.html`, so the MVP was created from scratch instead of refactoring existing app code.

## 2026-05-29: Manual AppContainer
Manual dependency wiring was used because the plan explicitly chose it for MVP simplicity. Hilt can be introduced later if the app grows.

## 2026-05-29: API 36 build target
The local Android SDK was installed with API 36 because the plan asked for current-version verification and API 36 was available as a stable platform in the SDK metadata.

## 2026-05-29: Deterministic engine over AI
All calorie decisions are made by pure Kotlin rule logic. Gemma is represented only as a future summary boundary.

## 2026-05-29: Local JSON backup
Backups use Storage Access Framework plus kotlinx.serialization JSON, avoiding backend, account, or hosted recovery scope.
