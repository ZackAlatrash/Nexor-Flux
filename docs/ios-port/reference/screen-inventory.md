# Reference — Screen & UI Inventory

Complete catalogue of the Android UI: 143 files, 35,322 LOC, 45 distinct surfaces, 32 ViewModels.
This is the work list for the SwiftUI rebuild and the source data for
[parity-ledger.md](../parity-ledger.md).

**Provenance:** ✅ verified against `develop` @ `d874aa5` on 2026-08-01 · 📋 from deep-dive analysis.
**Effort:** S ≈ <1 day · M ≈ 1–3 d · L ≈ 4–8 d · XL ≈ 2+ weeks (conventional single-engineer pace;
see [roadmap §6.1](../00-feasibility-and-roadmap.md) on why calendar estimates are omitted).

✅ Verified counts: **32 `composable()` entries**, **32 ViewModels**, **2 View-interop escape hatches**
in the entire UI layer.

---

## 1. Navigation destinations (32)

All in one flat `NavHost` — `ui/navigation/AppNavGraph.kt:157-805`. No nested graphs.

| # | Screen | File | LOC | Route | Kind | v1? | Effort |
|---|---|---|---:|---|---|---|---|
| 1 | `OnboardingScreen` | `ui/onboarding/OnboardingScreen.kt:59` | 434 | `onboarding` | conditional start | ✅ | M |
| 2 | `HomeDashboardScreen` | `ui/dashboard/DashboardScreen.kt:114` | 1249¹ | `home` | **Tab 1** | ✅ | **XL** |
| 3 | `BodyRecoveryScreen` | `ui/today/BodyRecoveryScreen.kt:74` | 548 | `body` | **Tab 2** | ✅ | L |
| 4 | `FoodScreen` | `ui/today/FoodScreen.kt:103` | **1428** | `food` | **Tab 3** | ✅ | **XL** |
| 5 | `CoachScreen` | `ui/coach/CoachScreen.kt:89` | 625 | `coach` | **Tab 4** | ✅ | L |
| 6 | `TrainHomeScreen` | `ui/train/TrainHomeScreen.kt:100` | 1192 | `train` | **Tab 5** | v1.1 | **XL** |
| 7 | `SharedRoutineImportScreen` | `ui/share/SharedRoutineImportScreen.kt:32` | 129 | `shared_routine_import` | pushed (file open) | v1.1 | S |
| 8 | `ActiveSessionScreen` | `ui/train/ActiveSessionScreen.kt:103` | 791 | `active_session` | pushed | v1.1 | **XL** |
| 9 | `SessionSummaryScreen` | `ui/train/SessionSummaryScreen.kt:73` | 686 | `session_summary/{id}` | pushed | v1.1 | L |
| 10 | `SessionDetailScreen` | `ui/train/SessionDetailScreen.kt:60` | 278 | `session_detail/{id}` | pushed | v1.1 | M |
| 11 | `ExerciseStatsScreen` | `ui/train/ExerciseStatsScreen.kt:52` | 210 | `exercise_stats/{id}` | pushed | v1.1 | M |
| 12 | `ExercisePickerScreen` | `ui/train/ExercisePickerScreen.kt:74` | 495 | `exercise_picker?replaceTargetId` | pushed, **dual-mode** | v1.1 | M |
| 13 | `RoutineBuilderScreen` | `ui/train/RoutineBuilderScreen.kt:66` | 327 | `routine_builder?workoutId` | pushed | v1.1 | L |
| 14 | `BodyHistoryScreen` | `ui/body/BodyHistoryScreen.kt:46` | 140 | `body_history` | pushed | ✅ | S |
| 15 | `BodyEditScreen` | `ui/body/BodyEditScreen.kt:38` | 96 | `body_edit/{date}` | pushed | ✅ | S |
| 16 | Calorie decision (legacy "Stats") | `ui/dashboard/DashboardScreen.kt:1058` | —¹ | `calorie_decision` | pushed | ✅ | M |
| 17 | `ProgressScreen` | `ui/progress/ProgressScreen.kt:61` | 403 | `trends` | pushed | ✅ | L |
| 18 | `PlanScreen` | `ui/plan/PlanScreen.kt:68` | 341 | `plan` | pushed | ✅ | M |
| 19 | `MoreScreen` | `ui/more/MoreScreen.kt:58` | 318 | `more` | hub, pushed w/ back | ✅ | S |
| 20 | ~~`FoodsScreen`~~ | `ui/foods/FoodsScreen.kt:42` | 242 | `foods` | **DEAD ROUTE** | ❌ | — |
| 21 | `FoodLibraryScreen` | `ui/foodlibrary/FoodLibraryScreen.kt:97` | 1081 | `food_library?` **5 args** | pushed, 2 modes | ✅ | **XL** |
| 22 | `BarcodeScannerScreen` | `ui/scanner/BarcodeScannerScreen.kt:69` | 417 | `barcode_scanner?` 4 args | full-bleed camera | ✅ | L |
| 23 | `RecipeBuilderScreen` | `ui/recipes/RecipeBuilderScreen.kt:65` | 381 | `recipe_builder?` | pushed | ✅ | M |
| 24 | `StreakStatsScreen` | `ui/streak/StreakStatsScreen.kt:26` | 90 | `streak_stats` | pushed | ✅ | S |
| 25 | `ProfileScreen` | `ui/profile/ProfileScreen.kt:82` | 592 | `profile` | pushed | ✅ | L |
| 26 | `AppearanceScreen` | `ui/appearance/AppearanceScreen.kt:43` | 137 | `appearance` | pushed | ✅ | S |
| 27 | `AiCoachScreen` | `ui/aicoach/AiCoachScreen.kt:55` | 344 | `ai_coach` | pushed | ✅ | M |
| 28 | `CoachMemoryScreen` | `ui/aicoach/CoachMemoryScreen.kt:35` | 120 | `coach_memory` | pushed | ✅ | S |
| 29 | `IntegrationsScreen` | `ui/integrations/IntegrationsScreen.kt:61` | 240 | `integrations` | pushed | ✅² | M |
| 30 | `DataBackupScreen` | `ui/databackup/DataBackupScreen.kt:66` | 350 | `data_backup` | pushed | ✅ | M |
| 31 | `UsageStatsScreen` | `ui/usage/UsageStatsScreen.kt:37` | 159 | `usage` | pushed | ✅ | S |
| 32 | `DeveloperScreen` | `ui/developer/DeveloperScreen.kt:40` | 151 | `developer` | pushed | ✅ | S |

¹ `DashboardScreen.kt` is one 1,249-LOC file containing both the Home tab and the calorie-decision
screen. ² Integrations minus the NEVO/Samsung importers, which are v1.1.

⚠️ **`FoodsScreen` is registered at `AppNavGraph.kt:568` but nothing navigates to it.** Do not port.

---

## 2. Non-route surfaces (13)

| Surface | File | LOC | Mechanism | v1? | Effort |
|---|---|---:|---|---|---|
| `WeeklyBriefingOverlay` | `ui/review/WeeklyBriefingOverlay.kt:46` | 420 | full-bleed `Dialog` | ✅ | L |
| `RebalanceOfferOverlay` | `ui/dashboard/RebalanceOfferOverlay.kt:79` | 294 | full-bleed `Dialog`; dismiss == *minimise* | ✅ | M |
| `RebalanceProgressDetailOverlay` | `ui/dashboard/RebalanceProgressDetailOverlay.kt:62` | 253 | full-bleed `Dialog` | ✅ | M |
| `BodyCheckInSheet` | `ui/body/BodyCheckInSheet.kt:51` | 152 | `GlassBottomSheet` | ✅ | S |
| `ExerciseDetailSheet` | `ui/train/ExerciseDetailSheet.kt:50` | 237 | `GlassBottomSheet` + remote image | v1.1 | S |
| `AmountSheet` / `RecipeAmountSheet` / `CreateFoodSheet` / `QuickAddSheet` | `FoodLibraryScreen.kt:870/943/988/1050` | — | `GlassBottomSheet` ×4 | ✅ | M total |
| `ProductFoundSheet` | `BarcodeScannerScreen.kt:286` | — | `GlassBottomSheet` | ✅ | S |
| `IngredientAmountSheet` | `RecipeBuilderScreen.kt:312` | — | `GlassBottomSheet` | ✅ | S |
| `OptionSheet` | `ProfileScreen.kt:491` | — | **raw** `ModalBottomSheet` | ✅ | S |
| Onboarding picker sheet | `OnboardingScreen.kt:400` | — | **raw** `ModalBottomSheet` ⚠️ leaks Material default surface colour | ✅ | S |
| `ToastOverlay` | `ui/toast/ToastOverlay.kt:46` | 152 | app-root overlay above nav | ✅ | S |

Plus **25 dialogs** (10 `GlassAlertDialog`, 15 `ConfirmDialog`) and 3 `DatePickerDialog`s.
📋 There is **no Material `AlertDialog` anywhere** in the app.

---

## 3. ViewModels (32)

Uniform pattern: `MutableStateFlow<XUiState>` + `.asStateFlow()`, collected with
`collectAsStateWithLifecycle()`. **No Hilt** — a hand-written `AppViewModelFactory`
(`core/AppContainer.kt:682-684`) does a `when (modelClass)` switch. `SavedStateHandle` injected in
5 places.

> ✅ **No Hilt is a significant free win.** The official CMP-migration guidance names Hilt→Koin as
> the hardest blocker; manual DI skips it entirely.

| ViewModel | File | LOC | Notes |
|---|---|---:|---|
| `FoodLibraryViewModel` | `ui/foodlibrary/…:260` | **824** | The monster. `FoodLibraryUiState` has **53 fields**, 44 methods |
| `SettingsViewModel` | `ui/settings/…:58` | 527 | **Shared by 2 screens** (Integrations + DataBackup), separate instances per nav entry |
| `RebalanceViewModel` | `ui/dashboard/…:116` | 409 | 3 StateFlows; holds the AI copy service |
| `DashboardViewModel` | `ui/dashboard/…:118` | 408 | Midnight rollover; holds `aiInsightCoordinator` |
| `ActiveSessionViewModel` | `ui/train/…:41` | 406 | **6 independent StateFlows, no single UiState**; 1 Hz elapsed ticker |
| `TodayViewModel` | `ui/today/…:84` | 364 | `TodayUiState` has 29 fields; holds `CloudInsightCoordinator` |
| `FoodLogViewModel` | `ui/today/…:88` | 330 | Day navigation, planned-meal reconcile |
| `ProgressViewModel` | `ui/progress/…:78` | 325 | 9 constructor deps |
| `BarcodeScannerViewModel` | `ui/scanner/…:77` | 319 | Sealed `ScanState` (7 cases), scan dedupe |
| `RecipeBuilderViewModel` | `ui/recipes/…:54` | 309 | `SavedStateHandle` |
| `OnboardingViewModel` | `ui/onboarding/…:131` | 286 | 21-field wizard state machine |
| `PlanViewModel` | `ui/plan/…:47` | 235 | Sealed `PlanGenerationDialog` |
| `TrainViewModel` | `ui/train/…:58` | 217 | `combine(tab, core, planAndReadiness)`, `stateIn` |
| `RoutineBuilderViewModel` | `ui/train/…:49` | 205 | `loadWorkout` re-entry guard (P1-15) |
| `AiCoachViewModel` | `ui/aicoach/…:39` | 184 | |
| `SessionSummaryViewModel` | `ui/train/…:40` | 174 | `SavedStateHandle` |
| ~~`FoodsViewModel`~~ | `ui/foods/…:38` | 156 | dead screen |
| `UsageStatsViewModel` | `ui/usage/…:62` | 148 | `stateIn` |
| `WeeklyReviewViewModel` | `ui/review/…:43` | 128 | Sealed state (5 cases) |
| `CoachTodayViewModel` | `ui/dashboard/…:49` | 123 | Takes function refs as ctor params |
| `BodyEditViewModel` | `ui/body/…:39` | 118 | `SavedStateHandle` (`date`) |
| `ProfileViewModel` | `ui/profile/…:24` | 115 | **7 separate StateFlows**, no single UiState |
| `SessionDetailViewModel` | `ui/train/…:33` | 109 | `SavedStateHandle` |
| `ExercisePickerViewModel` | `ui/train/…:47` | 101 | `combine(query, filter, selected)` |
| `SharedRoutineImportViewModel` | `ui/share/…:30` | 87 | Sealed `ImportUiState` |
| `DeveloperViewModel` | `ui/developer/…:28` | 71 | |
| `AppearanceViewModel` | `ui/appearance/…:18` | 54 | 3 `stateIn` flows |
| `ExerciseStatsViewModel` | `ui/train/…:24` | 51 | `SavedStateHandle` |
| `StreakViewModel` | `ui/streak/…:23` | 46 | **Instantiated 4×** — nav graph + nested in 3 screens |
| `BodyHistoryViewModel` | `ui/body/…:17` | 38 | |
| `CoachViewModel` | `ui/coach/…:8` | **21** | **Pure passthrough** to `CloudCoachCoordinator` |
| `CoachMemoryViewModel` | `ui/aicoach/…:12` | 19 | |

### 3.1 Patterns that change shape on iOS

- **VMs holding coordinators.** Six VMs expose an app-scoped coordinator's own `StateFlow` directly.
  On iOS these become `@Observable` singletons injected via `@Environment`; the VM layer for them is
  nearly vestigial (see `CoachViewModel` at 21 LOC).
- **Nested `viewModel()` inside screens.** `BodyRecoveryScreen.kt:127`, `FoodScreen.kt:113`,
  `TrainHomeScreen.kt:127` each create a `StreakViewModel` inline.
- **No shared parent-scoped VMs.** `viewModel()` scopes per `NavBackStackEntry`, so Home and More
  each get their *own* `DashboardViewModel`.

---

## 4. Navigation mechanics

### 4.1 Tabs

📋 5 bottom-nav tabs (`ui/RecompApp.kt:94-100`): `home, body, food, coach, train`, indexed by
`routeToTabIndex()` (`:85-92`). The nav bar renders only when `currentRoute in topLevelRoutes`
(`:282`). Note `TopLevelDestination` (`AppNavGraph.kt:86-94`) declares only 4 and includes `More`,
which is *not* a bottom-nav tab — a small inconsistency, don't replicate it.

### 4.2 Three argument mechanisms

1. **Path args** — `session_summary/{sessionId}` etc. (`NavType.LongType`), `body_edit/{date}`.
2. **Query args with `-1L` sentinel defaults** — `food_library?slotId&slotName&editEntryId&date&pickerMode`
   is the worst (5 args). `slotName` is manually `URLEncoder`/`URLDecoder`'d.
   `seedIngredients` is **base64-url-encoded JSON of a `List<RecipeIngredientEntity>`** (`:242-245`).
3. ⚠️ **Reverse results via `savedStateHandle` on `previousBackStackEntry`** — **the pattern with no
   SwiftUI analogue.** Four sites:
   - `picked_exercise_ids: LongArray` (`:445-448` → `:338-340`, `:484-486`)
   - `replacement_result: LongArray` = `[targetSessionExerciseId, chosenExerciseId]` (`:451-455`)
   - `scanned_food: String` JSON (`:676` → `:610-612`)
   - `picked_ingredient: String` JSON (`:630` → `:697-699`)

   Each is read as `getStateFlow(...).collectAsStateWithLifecycle()` and explicitly `remove()`d by an
   `onXConsumed` callback. **Redesign as bindings or a shared selection coordinator — do not
   translate.** Two of the four (`picked_exercise_ids`, `replacement_result`) are Train-only and
   defer to v1.1.

### 4.3 Back-stack behaviour

Tab switches use `popUpTo(Home) { saveState = true }; launchSingleTop = true; restoreState = true`
(~9 sites). ⚠️ **This means the app uses a *single shared* back stack across tabs** — cross-tab
pushes (Home→Food, Train→Body) share it. SwiftUI's `TabView` + per-tab `NavigationStack` changes
that behaviour; a single `NavigationStack` behind a custom tab bar is the faithful port. **Decide
deliberately.**

Conditional start (`RecompApp.kt:265-277`): `onboardingComplete` is a **tri-state `Boolean?`**;
`null` renders an empty `Box` and the NavHost is not composed at all, avoiding a Home-flash.

### 4.4 Deep links

`CoachActionType?.toDeepLinkRoute()` maps 7 enum values to 4 tab routes.
`deepLinkNavRoute(action, onboardingComplete)` gates on `onboardingComplete == true`
(unit-tested: `app/src/test/.../ui/DeepLinkNavRouteTest.kt`), and the effect awaits
`navController.currentBackStackEntryFlow.first()` before navigating. **On iOS the setGraph race
disappears, but the cold-launch delegate race replaces it** — set the
`UNUserNotificationCenterDelegate` before `didFinishLaunching` returns.

### 4.5 Transitions

Tabs `fadeIn(220)/fadeOut(200)`; pushed screens `slideInVertically(280){it/16} + fadeIn` — a
*vertical* 1/16-height slide. **The iOS default horizontal push is almost certainly better; don't
port the vertical slide.**

---

## 5. Design system

Governing doc: `docs/design-system.md` (read it — it is load-bearing on the Android side).

### 5.1 Tokens

| Set | File | Contents |
|---|---|---|
| `AppType` | `ui/theme/Typography.kt:14-46` | 13 `TextStyle`s, size/weight/tracking only, **no colour** |
| `AppColors` | `ui/theme/AppColors.kt:11-97` | 18 semantic tokens × 2 (dark/light) |
| `AppAccent` | `ui/theme/DesignTokens.kt:42-63` | 11 accent themes × 5 shades; mode-aware inks; `onAccent` computed by luma (`:55-58`) |
| Spacing/radii | `ui/theme/DesignTokens.kt:96-115` | `CornerSmall/Card/Chip/Pill = 10/16/20/100`; `ScreenPaddingH 16`, `ScreenSpacing 10`; `Spacing.xs…xl = 4/8/12/16/20` |

The 13 type tokens: `screenTitle` 28 ExtraBold −0.8 · `screenTitleCompact` 20 Bold −0.4 ·
`screenSubtitle` 13 · `sectionLabel` 9 Bold 0.14 · `cardTitle` 15 SemiBold · `cardSubtitle` 12 ·
`body` 13 · `label` 11 Medium · `metaLabel` 9 Bold 0.4 · `displayHero` 44 ExtraBold −1.0 ·
`displayLarge` 36 ExtraBold −0.5 · `statValue` 22 Bold −0.3 · `statValueSmall` 17 Bold.

⚠️ On iOS, **prefer Dynamic Type over fixed point sizes.** Hardcoded sizes are more of a problem on
iOS than on Android — Dynamic Type support is an accessibility expectation and a review checklist
item.

### 5.2 Component catalogue

**Frame** (`ui/component/ScreenScaffold.kt`): `ScreenScaffold(withNavBarInset)` (`:44`),
`ScreenHeader` tier-1 (`:70`), `SubScreenHeader` tier-2 (`:99`), `BackButton` (`:125`).

**Cards** (`ui/component/GlassComponents.kt`): `NeutralCard` (`:67`), `FrostedCard` (`:86`, the core
glass card), `GlassSpeechBubble` (`:183`), `TintedCard` (`:239`, AI only), `DangerCard`
(⚠️ **duplicated** in `DataBackupScreen.kt:271` and `DeveloperScreen.kt:99`).

**Buttons** (`ui/liquidglass/LiquidComponents.kt`): `LiquidButton` base (`:365`),
`LiquidGlassButton`/`LiteGlassButton` (`:728/:778`), `LiquidPrimaryButton` (`:825`),
`LiquidSecondaryButton` (`:849`), `LiquidActionButton` (`:914`), `LiquidStepButton` (`:870`),
`LiquidBottomTabs` (`:139`), `LiquidSegmentedToggle`. 📋 `LiquidSlider` (`:443`) and `LiquidToggle`
(`:579`) are **unused in screens**.

**Inputs:** `GlassInputField` (`:426`), `GlassTextArea` (`:547`), `ScoreStepper` (`:591`),
`VioletToggle` (`:493` — a **fake switch**: tap-only, because a Material `Switch`'s internal
`draggable` fights `ModalBottomSheet` drag), `GlassSegmentedToggle`, `AmountStepper`,
`FoodAmountPanel`.

**Modals:** `GlassBottomSheet` (`:41`), `GlassAlertDialog` (`:55`), `ConfirmDialog`.

**AI family:** `AiInsightCard` + `AiBorderMode` enum, `GeneratedInsightCard`, `ConfidenceBadge`,
`Modifier.aiEdgeGlow`, `AiBadge`, `AiDialogCard`, `InsightShimmerLines`, `IridescentPalette`, and
**`MarkdownText` (408 LOC, hand-written parser** — bullets, numbered lists, tables with alignment,
inline bold/italic/code; no third-party lib).

**Streaks** (`StreakComponents.kt`, 602 LOC): `StreakCoin`, `StreakWeekStrip`, `StreakCountFlame`,
`StreakRow`, `StreakHeroBanner`, `StreakGoalRing`, `CalorieStreakBadge`, `StreakSpeechBubble`.

**Train** (v1.1): `SetGrid` (770), `ExerciseCard` (280), `SwipeToRevealRow` (153), `BodyMap` (159),
`MuscleRecoveryStrip` (107), `PrBanner` (125), `MuscleGroupIcon` (85), `MuscleArt` (53).

### 5.3 "Liquid glass" — exactly what it is

**Library:** `io.github.kyant0:backdrop:2.0.0` + `shapes:1.2.0`.

📋 **Two backdrop layers** (`ui/RecompApp.kt:191-234`) to avoid a circular `GraphicsLayer` read that
crashed on transitions: `contentBackdrop` (gradient only, via `LocalBackdrop`) and `navBackdrop`
(gradient + full content, nav bar only). `layerBackdrop(navBackdrop)` is **conditionally dropped**
on non-top-level routes because per-frame recording was measurable scroll jank.

**Primitives** (all from the library): `blur(px)` 2/4/8/12dp, `lens(w, h, chromaticAberration)`,
`vibrancy()`, `Highlight`, `Shadow`, `InnerShadow`.

📋 **Exactly one app-authored AGSL shader:** a 10-line radial press-glow
(`ui/liquidglass/LiquidUtils.kt:230-247`), `BlendMode.Plus`, guarded by `isRuntimeShaderSupported()`
with a flat-white fallback.

📋 **One raw-native blur:** `BlurMaskFilter` in `ui/component/AiEdgeGlow.kt:100-142` — three
concentric `drawRoundRect` passes with hue-rotated `SweepGradient`s. ⚠️ Its KDoc claims an API-28
guard that **does not exist in the file**, and three `SweepGradient`s are reallocated every frame.

**The nav bar** (`LiquidComponents.kt:139-359`) is the most complex component: three stacked layers
including an `alpha(0f)` tinted clone, a sliding indicator over a combined backdrop with
press-driven chromatic aberration, and **velocity-driven squash-and-stretch** — it is
**horizontally draggable**, not just tappable, via `DampedDragAnimation` (5 `Animatable`s,
`MutatorMutex`, `VelocityTracker`).

**iOS verdict:** most of this **deletes**. `.glassEffect(_:in:)`, `GlassEffectContainer`,
`.buttonStyle(.glass)`, `.sheet` + `.presentationBackground`, and a native glass `TabView` cover it.
The draggable indicator with velocity squash is the one thing not native — **accept the system tab
bar** unless you specifically want it back (budget L if so).

---

## 6. Charts

✅ **Vico is declared in the build and has ZERO source references.** Every chart is hand-rolled
Compose `Canvas`. Remove the dependency.

| Chart | File:line | LOC | Complexity | Used by |
|---|---|---:|---|---|
| `SparklineChart`/`MiniSparkline` | `ui/component/charts/SparklineChart.kt:123/:256` | 266 | **The complex one** — cubic-bezier smoothing, gradient area fill inside a left→right clip reveal, 4 grid lines, zone band + dashed bounds, 3-layer glow end dot, drag-scrub with crosshair, `Path` reuse | Progress ×3, BodyRecovery, Dashboard |
| `CalorieProgressBar` | `.../CalorieProgressBar.kt:57` | 121 | Rounded track + zone band + **45° candy stripes** + gradient fill + translucent "ghost" planned extension; 2 springs | Dashboard, FoodScreen |
| `WeekCalorieStrip` | `ui/component/WeekCalorieStrip.kt:56` | 265 | 7 bars, `drawBehind` zone band + dashed target | FoodScreen |
| `ProgressLineChart` | `.../ProgressLineChart.kt:53` | 84 | Simple polyline + dots | ExerciseStats (v1.1) |
| `StreakGoalRing` | `StreakComponents.kt:304` | — | Track + progress arc, round caps | BodyRecovery, Dashboard |
| est-1RM sparkline | `SessionDetailScreen.kt:232-278` | — | **Third** independent sparkline impl | SessionDetail (v1.1) |
| `WeeklyBarsChart`/`DayDots`/`LeverTiles` | `ui/dashboard/RebalanceViz.kt:96/:257/:517` | 655 | Per-bar `Animatable` stagger, dot pulse | Rebalance overlays |
| `BodyMap` | `ui/train/component/BodyMap.kt:104-159` | 159 | SVG `d` strings from assets → `PathParser`, fit-center transform, per-muscle heat, `Region` hit-testing | TrainHome (v1.1) |

📋 **Dead chart code — do not port:** `MacroRingChart` (158 LOC), `StackedBarChart` (165 LOC),
`ui/component/ProgressBar.kt` (stale duplicate). Only unit tests touch their pure helpers.

**iOS:** Swift Charts covers `ProgressLineChart`, `WeekCalorieStrip`, and the bar strips directly
(`RuleMark` + `.lineStyle(dash:)` replaces the dashed target lines). Needs a custom `Canvas` overlay
for: the clip-reveal draw-in animation, the 3-layer glow end dot, and the candy stripes.
`StreakGoalRing` → `Circle().trim(from:to:).stroke(style:)`. `BodyMap` → parse the same JSON to
`Path`, hit-test with `Path.contains(_:)` — **simpler than the Android `Region` dance**.

---

## 7. Compose patterns that don't map cleanly

| Pattern | Count / where | iOS |
|---|---|---|
| `collectAsStateWithLifecycle()` | 98 sites | `@Observable` property reads, or `.task { for await … }`. ⚠️ **Lifecycle-aware collection has no exact equivalent** — `.task(id:)` cancels on disappear, close to but not the same as `WhileSubscribed(5000)` |
| `LaunchedEffect` | 95 sites | `.task {}` / `.onChange(of:)` |
| `derivedStateOf` | **only 5** real uses | Computed properties. **All 5 are recomposition optimisations that simply disappear** |
| `DisposableEffect` | only 2 (sensor, camera) | `.onAppear`/`.onDisappear` or `.task` cancellation |
| `produceState` | 1 (onboarding tri-state gate) | `@State var x: Bool?` + `.task` |
| `snapshotFlow` | 6 sites | `AsyncStream` / `.onChange`. `LiquidUtils.kt:169`'s "suspend until settled within 2.5%" needs a `CheckedContinuation` |
| `rememberUpdatedState` | 2 sites | **Disappears** — Swift closures capture by reference |
| `LazyColumn` + `key` | 42 lazy containers, **only 7 pass keys** | `ForEach(_, id:)` — `Identifiable` is mandatory on iOS, so this gets **stricter and safer** |
| Custom `Layout` | 1 (`SwipeToRevealRow`) | Deleted — `.swipeActions` |
| `CompositionLocal` | 6 (`LocalAppContainer`, `LocalAppColors`, `LocalAppAccent`, `LocalBackdrop`, `LocalToastController`, `LocalLiquidBottomTabScale`) | `@Environment` — **1:1** |
| Actions-holder records | `FoodScreen.kt:115` builds a 15-method `FoodActions` struct in `remember` for stability | Pass the `@Observable` model — pattern disappears |
| Isolated recomposition scopes | `ActiveSessionScreen.kt:582,657` (1 Hz timer in a leaf so the list doesn't recompose) | SwiftUI diffing is coarser — extract into its own small `View` with `TimelineView(.periodic)` |
| `graphicsLayer` | 21 sites, alpha/scale/translation/colorFilter only | `.scaleEffect`/`.offset`/`.opacity` — 1:1 |

⚠️ **A general rule:** roughly a dozen sites carry comments recording a specific Compose
recomposition regression they were written to avoid (`SwipeToRevealRow.kt:70-74`,
`RecompApp.kt:191-196`, `SparklineChart.kt:118-120`, `ActiveSessionScreen.kt:206-208`,
`SetGrid.kt:698`, `BodyMap.kt:99-101`, `LiquidComponents.kt:178-182`, …).
**Read the comments for the *behaviour* they protect, then implement plainly.** Most of the
workarounds are Compose artefacts and should not be ported.

---

## 8. Theming

- `ThemeMode` SYSTEM/LIGHT/DARK (`ui/theme/ThemeMode.kt:4-13`) → `.preferredColorScheme(_:)`.
- **No Material You / dynamic colour** — `dynamicDarkColorScheme` is never called. Colours are
  fully app-owned, which makes the iOS port straightforward.
- 11 accent presets × 5 shades, each with a per-mode background `.webp`
  (`DesignTokens.kt:129-152` → 22 files, 1.9 MB). 📋 `bg_glass_orbs.png` /
  `bg_glass_orbs_blurred.png` are **unreferenced dead assets**.
- Distribution via `CompositionLocalProvider(LocalAppAccent, LocalAppColors)` (`Theme.kt:64-67`).
- 📋 Remaining Material3 surface to hand-roll: `IconButton` ×10, `CircularProgressIndicator` ×9,
  `TextButton` ×8, `OutlinedTextField` ×4, `HorizontalDivider` ×4, `DropdownMenu` ×4, `DatePicker`
  ×3, plus singles.
- ⚠️ **`selectedFont` is dead** — stored, shown in `FontPicker`, read by `AppearanceViewModel:23`,
  and **never applied** (no `res/font` directory). Either implement it properly on iOS or drop it.

---

## 9. Accessibility — a gap worth closing on the way over

📋 Thin today: **8 `Modifier.semantics` blocks total**, 143 `contentDescription`s. No custom actions
on swipe-to-reveal, bare numeric wheel items, undescribed camera overlays, and the sensor parallax
is **not** reduced-motion gated.

SwiftUI gives a lot of this free (`.swipeActions` ships VoiceOver actions; `Picker(.wheel)` is
accessible; Dynamic Type is built in). **The rebuild is the cheapest moment this will ever be to
fix** — `.accessibilityLabel` / `.accessibilityAction` cost almost nothing at write time.

---

## 10. Rolled-up effort (conventional pace)

| Bucket | Effort | v1? |
|---|---|---|
| 5 tab screens (Home XL, Food XL, Train XL, Body L, Coach L) | XL (~5–6 wk) | Train → v1.1 |
| Train sub-flow (6 screens) | XL (~4 wk) | **v1.1** |
| Food sub-flow (FoodLibrary XL, Scanner L, RecipeBuilder M) | L–XL (~2.5 wk) | ✅ |
| Settings/More cluster (13 screens, mostly S–M) | L (~1.5 wk) | ✅ |
| Overlays + sheets (13) | L (~1 wk) | mostly ✅ |
| Design system | L (~1–1.5 wk) | ✅ |
| Charts | L (~1 wk) | ✅ |
| ViewModel layer (32) | L (~1.5 wk) | ✅ |
| Navigation + deep links | L (~1 wk) | ✅ |
| Theming | M (~3 d) | ✅ |

**Do not port (~1,500 LOC):** `FoodsScreen`, `MacroRingChart`, `StackedBarChart`,
`ui/component/ProgressBar.kt`, Vico, `SwipeToRevealRow`, `DurationWheelMath` + wheel picker,
`DragHaptics`, the two-backdrop workaround, `selectedFont` wiring, `bg_glass_orbs*.png`,
`LiquidSlider`, `LiquidToggle`.
