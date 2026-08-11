# iOS Port — Parity Ledger

One row per Android surface. **This is the "what's left" list.** Update it in the same commit as the
work; a session should be able to answer "what's next" from this file alone.

**Status:** ⬜ not started · 🔨 in progress · ✅ done · 👀 needs visual check · ⏭️ v1.1 · ❌ won't port

Counts and file references come from
[reference/screen-inventory.md](reference/screen-inventory.md) — go there for LOC, effort, and
per-screen notes.

---

## Summary

| Bucket | Total | ✅ | 🔨 | ⬜ | ⏭️ v1.1 | ❌ |
|---|---:|---:|---:|---:|---:|---:|
| Foundations | 8 | 8 | 0 | 0 | 0 | 0 |
| File formats | 4 | 3 | 1 | 0 | 0 | 0 |
| Design system | 7 | 5 | 1 | 1 | 0 | 0 |
| Screens (v1) | 20 | 12 | 1 | 7 | 0 | 0 |
| Overlays & sheets | 11 | 5 | 1 | 4 | 1 | 0 |
| Charts | 8 | 3 | 1 | 1 | 3 | 0 |
| Platform integrations | 9 | 0 | 0 | 9 | 0 | 0 |
| AI | 8 | 0 | 0 | 8 | 0 | 0 |
| Deferred / dropped | 14 | — | — | — | 12 | 2 |
| **Total** | **86** | **36** | **6** | **26** | **16** | **2** |

(File formats were previously counted inside Foundations' 8; they are now their own row, hence 86.)

---

## Foundations (Phase 0–1)

| Item | Android reference | Status |
|---|---|---|
| `:shared` KMP module extracted | `settings.gradle.kts` | ✅ |
| `java.time` → kotlinx-datetime (27 files, +3 inline FQNs) | blocker B2 | ✅ |
| ISO-week + signed-decimal formatters, golden-value tested | blocker B3/B4 🔴 | ✅ |
| Xcode project + SPM + GRDB | — | ✅ |
| 19 tables + 14 migrations (dates as strings) | `RecompDatabase.kt` | ✅ |
| 10 preference stores (actor-backed JSON) | see data-model §3 | ✅ |
| Keychain wrapper (2 keys, key-provider lambda) | `SecureKeyStore.kt` | ✅ |
| Bundled assets + version-gated exercise seed | 4 JSON files | ✅ |

## File formats (Phase 1) — the round-trip guard

| Item | Android reference | Status |
|---|---|---|
| Backup `.json` codec | `BackupModels.kt`, `BackupRepository.kt` | ✅ |
| ↳ **import a real Android backup** | 🔴 blocked: no fixture | 🔨 |
| Personal-foods `.json` codec (⚠️ strict `==` version check) | `PersonalFoodsJsonCodec.kt` | ✅ |
| `.rtroutine` codec + UTI declaration | `RoutineShareModels.kt` | ✅ |

The backup codec is written and covered by 26 synthetic tests; the acceptance test is **armed and
self-skipping** (9 tests) until a real export lands in `RecompTracker/RecompTrackerTests/Fixtures/`.
It is 🔨 rather than ⬜ because the only outstanding work is dropping the file in.

## Design system (Phase 2)

| Item | Android reference | Status |
|---|---|---|
| `AppType` → Font extensions (prefer Dynamic Type) | `ui/theme/Typography.kt` | ✅ |
| `AppColors` + `AppAccent` → asset catalog + `@Environment` | `ui/theme/AppColors.kt`, `DesignTokens.kt` | ✅ |
| Card family (`FrostedCard`/`NeutralCard`/`TintedCard`/`DangerCard`) | `GlassComponents.kt` | ✅ (Danger deferred — DataBackup is Phase 3) |
| Button family → `.buttonStyle(.glass)` / `.glassProminent` | `LiquidComponents.kt` | ✅ |
| `GlassBottomSheet` → `.sheet` + `.presentationDetents` | `GlassBottomSheet.kt` | ✅ shipped in 3a as `appSheet()`; 10 call sites. Sizes to its content rather than taking a fixed detent |
| `GlassAlertDialog` + `ConfirmDialog` | `GlassAlertDialog.kt` | 🔨 `ConfirmDialog` is done — native `.confirmationDialog`, 3 call sites. The general-purpose `GlassAlertDialog` has no iOS caller yet |
| `MarkdownText` → `AttributedString(markdown:)` + custom tables | `MarkdownText.kt` (408 LOC) | ⬜ |

**The consistency pass (2026-08-06) added seven components with no single Android row**, extracted
from spellings that had drifted across Phases 2–3b rather than ported from one file: `ScreenTitle`
(**D32** — the native large title, which replaced the hand-rolled `ScreenHeader` and `DayHeader`),
`ScreenBanner` + `InlineError` (the five message spellings), `AmountPreviewStat`, `AccentPill` (five
near-identical pills), `TapTarget` (D31) and `Formatters`/`AppNumber` (D28).

**3c added four more of the same kind**: `OptionSheet` (one pick-one list for all three profile
pickers), `CheckInFormFields` and `CheckInWriter` (shared by the check-in sheet and Body Edit,
**D35**), and `ProfilePhotoStore`.

## Screens — v1

| # | Screen | Android file | Phase | Status |
|---|---|---|---|---|
| 1 | Food Log | `ui/today/FoodScreen.kt` (1428) | 2 + 3a | 🔨 **most of it** — day nav, week chart, nutrition strip, slots, Unassigned, quick add (2); **+ Add opens the Library, reconcile banner, slot selection → recipe, `today` advances across midnight** (3a). Still deferred: suggestion card, macro-edit dialog, rebalance chip, postpone, stale-plan nudge |
| 2 | Home Dashboard | `ui/dashboard/DashboardScreen.kt` (1249) | 3b | ✅ **minus the coach slot and the weekly-review overlay (Phase 5)** — their layout slots exist |
| 3 | Body / Recovery | `ui/today/BodyRecoveryScreen.kt` (548) | 3b | ✅ **minus the recovery insight card (Phase 5)** — the layout already works without it |
| 4 | Food Library | `ui/foodlibrary/FoodLibraryScreen.kt` (1081) | 3a | ✅ **minus NEVO (D20) and Open Food Facts (D21, → Phase 4)** — four chips instead of six, search, Recents, the three action buttons, picker mode |
| 5 | Plan | `ui/plan/PlanScreen.kt` (341) | 3c | ✅ built against screenshot 17 — targets, zone, collapsed Advanced, generation preview + weight entry, phase-start picker. Writes through `PlanRepository` (**D33**) |
| 6 | Profile | `ui/profile/ProfileScreen.kt` (592) | 3c | ✅ **including the photo picker**, which was going to be deferred — `PhotosPicker` sidesteps Android's persistable-URI bug rather than porting it |
| 7 | Onboarding | `ui/onboarding/OnboardingScreen.kt` (434) | **3d** | 📋 planned — Tasks 8–11. Writes through `PlanRepository`, so first run stamps a plan version (**D42**) |
| 8 | Progress / Trends | `ui/progress/ProgressScreen.kt` (403) | **3d** | 📋 planned — Tasks 6–7. **8 series of 10**: the two Train series are v1.1 (D4) and the insight card is Phase 5 |
| 9 | Body History | `ui/body/BodyHistoryScreen.kt` (140) | 3c | ✅ **built blind** — no screenshot. Missing days are rows, not gaps |
| 10 | Body Edit | `ui/body/BodyEditScreen.kt` (96) | 3c | ✅ **built blind**. Reuses `CheckInDraft` and the extracted `CheckInFormFields` / `CheckInWriter` (**D35**) |
| 11 | Streak Stats | `ui/streak/StreakStatsScreen.kt` (90) | 3b | ✅ |
| 12 | Calorie decision | `DashboardScreen.kt:1058` | 3b + 3c | ✅ built in 3b, **reachable from 3c** via More. Still no screenshot — built blind |
| 13 | More (hub) | `ui/more/MoreScreen.kt` (318) | 3c | ✅ **built blind**. Reached from the Dashboard avatar (**D17**/**D36**); unbuilt rows shown disabled with their phase (**D38**) |
| 14 | Recipe Builder | `ui/recipes/RecipeBuilderScreen.kt` (381) | 3a | ✅ **minus the ✨ AI namer (Phase 5)** — both entry points (library, and a Food Log slot selection), two-mode ingredient editor, delete with confirmation |
| 15 | Barcode Scanner | `ui/scanner/BarcodeScannerScreen.kt` (417) | 4 | ⬜ |
| 16 | Integrations (minus CSV import) | `ui/integrations/IntegrationsScreen.kt` (240) | 4 | ⬜ |
| 17 | Data Backup | `ui/databackup/DataBackupScreen.kt` (350) | 4 | ⬜ |
| 18 | Appearance | `ui/appearance/AppearanceScreen.kt` (137) | 3c | ✅ built against screenshot 19, **including a working font row** — three system designs, not Android's two dead bundled families (**D39**). Accents are a wrapping grid, not Android's clipped scroll |
| 19 | Coach chat | `ui/coach/CoachScreen.kt` (625) | 5 | ⬜ |
| 20 | AI Coach settings | `ui/aicoach/AiCoachScreen.kt` (344) | 5 | ⬜ |
| 21 | Coach Memory | `ui/aicoach/CoachMemoryScreen.kt` (120) | 5 | ⬜ |
| 22 | Usage Stats | `ui/usage/UsageStatsScreen.kt` (159) | **3d** | 📋 planned — Tasks 1–3. 🔴 Opens with a `UsageTracker`: the table has a record and no writer, and 7 of 14 event types are producible today |
| 23 | Developer | `ui/developer/DeveloperScreen.kt` (151) | **3d** | 📋 planned — Tasks 4–5. 🔴 Ordered early: six of its scenarios are the only way to reach the rebalance faces nobody has seen |

## Overlays & sheets

| Surface | Android file | Phase | Status |
|---|---|---|---|
| Weekly Briefing overlay | `ui/review/WeeklyBriefingOverlay.kt` (420) | 5 | ⬜ |
| Rebalance Offer overlay | `ui/dashboard/RebalanceOfferOverlay.kt` (294) | 3b | ✅ device-verified. Fixed `.large` detent, not `appSheet()` — a dial change must not resize the sheet |
| Rebalance Progress Detail | `ui/dashboard/RebalanceProgressDetailOverlay.kt` (253) | 3b | 🔨 day-0 face device-verified; the **running** face (Day X of Y, dot row) is unverified |
| Body Check-in sheet | `ui/body/BodyCheckInSheet.kt` (152) | 3b | ✅ — scrolls, unlike Android's plain `Column`; that was a Compose workaround UIKit does not need |
| Amount / RecipeAmount / CreateFood / QuickAdd sheets | `FoodLibraryScreen.kt:870/943/988/1050` | 3a | ✅ all four. Sized to their content rather than a fixed detent (`appSheet()`), and Quick Add's name is optional as Android's always was — Phase 2 built that one blind and required one |
| Product Found sheet | `BarcodeScannerScreen.kt:286` | 4 | ⬜ |
| Ingredient Amount sheet | `RecipeBuilderScreen.kt:312` | 3a | ✅ both bodies (stepper when the ingredient carries a per-100g base, four typed fields when it does not). Grams-only: Android also offers a Servings toggle here, which the picker's amount sheet already provided |
| Option sheet (Profile) | `ProfileScreen.kt:491` | 3c | ✅ one generic `OptionSheet` for all three pickers. `appSheet()`, so Android's leaking-Material-surface bug does not port |
| Onboarding picker sheet | `OnboardingScreen.kt:400` | **3d** | 📋 planned — reuses 3c's `OptionSheet` rather than a second picker style in the first-run flow |
| Toast overlay | `ui/toast/ToastOverlay.kt` (152) | 2 | ⬜ |
| Exercise Detail sheet | `ui/train/ExerciseDetailSheet.kt` (237) | — | ⏭️ |

## Charts

| Chart | Android file | Phase | Status |
|---|---|---|---|
| `SparklineChart` (bezier + clip reveal + glow dot + scrub) | `charts/SparklineChart.kt` (266) | 3b | ✅ **scrub needed `UIGestureRecognizerRepresentable`** — no SwiftUI `DragGesture` variant coexists with a `ScrollView` |
| `CalorieProgressBar` (candy stripes + ghost extension) | `charts/CalorieProgressBar.kt` (121) | 2 + 3b | ✅ stripes and ghost landed in 3b and the component was extracted, so Food Log and Dashboard share one bar |
| `WeekCalorieStrip` | `ui/component/WeekCalorieStrip.kt` (265) | 2 | ✅ |
| `StreakGoalRing` | `StreakComponents.kt:304` | 3b | ✅ — animates, unlike Android's, which snaps |
| Rebalance viz (bars / day dots / lever tiles) | `ui/dashboard/RebalanceViz.kt` (655) | 3b | 🔨 bars + lever tiles device-verified; **`DayDots` unverified** (needs a plan on day ≥ 1). `ConvergenceReadout` deliberately not ported — dead on Android |
| `ProgressLineChart` | `charts/ProgressLineChart.kt` (84) | — | ⏭️ |
| est-1RM sparkline | `SessionDetailScreen.kt:232` | — | ⏭️ |
| `BodyMap` (SVG paths + heat + hit-test) | `train/component/BodyMap.kt` (159) | — | ⏭️ |

## Platform integrations (Phase 4)

| Item | Android reference | Status |
|---|---|---|
| HealthKit — steps (⚠️ Watch double-count) | `HealthConnectRepository.kt:146` | ⬜ |
| HealthKit — weight | `:156-161` | ⬜ |
| HealthKit — sleep (⚠️ night-grouping, own aggregation) | `:163-169` | ⬜ |
| HealthKit — nutrition + 365-day import (⚠️ `HKCorrelation`) | `:92-112` | ⬜ |
| Permission UX redesign (⚠️ no read-denial introspection) | `HealthSyncCoordinator.kt:80,97,129` | ⬜ |
| Notifications + `RateLimiter`/`QuietHours` port | `RateLimiter.kt`, `AndroidCoachNotifier.kt` | ⬜ |
| Background: `HKObserverQuery` + `BGTaskScheduler` | `HealthSyncWorker.kt`, `CoachDigestWorker.kt` | ⬜ |
| Share / import `.rtroutine` via UTI | `RoutineShareLauncher.kt`, `RoutineShareInbox.kt` | ⬜ |
| Profile photo (⚠️ copy into container, no persistable URI) | `ProfileScreen.kt:105-113` | ⬜ |

## AI (Phase 5)

| Item | Android reference | Status |
|---|---|---|
| `URLSession` SSE client + lenient tool-arg decoding | `OpenAiCompatClient.kt` (105) | ⬜ |
| Turn loop: actor + `CheckedContinuation` + write confirmation | `CloudCoachCoordinator.kt` (467) | ⬜ |
| Tool executor (14 of 19 tools; 5 routine tools unavailable) | `CoachToolExecutor.kt` (904) 🔴 | ⬜ |
| Insight cards + per-kind dedup keys | `CloudInsightCoordinator.kt` (177) | ⬜ |
| Weekly briefing + `merge()` deterministic overlay | `WeeklyBriefingGenerator.kt` | ⬜ |
| Phrasing / rebalance copy / recipe namer (fallback-verbatim) | 6 files (459) | ⬜ |
| Knowledge base: corpus + retriever + injector | `ai/knowledge/*` (206) | ⬜ |
| Proactive spine: assembler → selector → emitter | `data/coach/*` | ⬜ |

## Store readiness (Phase 6)

| Item | Status |
|---|---|
| Demo mode for AI (Guideline 2.1) | ⬜ |
| Third-party-AI consent modal (5.1.2(i)) | ⬜ |
| `PrivacyInfo.xcprivacy` + nutrition labels | ⬜ |
| "Not medical advice" disclaimer (1.4.1) | ⬜ |
| Age rating incl. medical/wellness + social questions | ⬜ |
| Screenshots, metadata, phased release | ⬜ |

---

## Deferred to v1.1

Train hub · ActiveSession · SessionSummary · SessionDetail · ExerciseStats · ExercisePicker ·
RoutineBuilder · SharedRoutineImport · Exercise Detail sheet · `BodyMap` + muscle art ·
5 routine coach tools · NEVO + Samsung CSV import (`domain/foodimport`, 633 LOC)

## Won't port

| Item | Why |
|---|---|
| `FoodsScreen` (242 LOC) | Dead route — registered at `AppNavGraph.kt:568`, never navigated to |
| `MacroRingChart`, `StackedBarChart`, `ui/component/ProgressBar.kt` | No production call sites |

Also evaporating rather than porting (not tracked as rows): Vico, `SwipeToRevealRow`,
`DurationWheelMath` + wheel picker, `DragHaptics`, the two-backdrop workaround, `selectedFont`
wiring, `bg_glass_orbs*.png`, `LiquidSlider`, `LiquidToggle`, the `:macrobenchmark` module, all R8
keep rules, the shared-keystore scheme, FileProvider, and the dual `.rtroutine` intent-filter hack.
