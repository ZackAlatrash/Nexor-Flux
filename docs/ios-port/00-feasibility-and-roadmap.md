# iOS Port — Feasibility Assessment & Roadmap

**Date:** 2026-08-01 · **Branch analysed:** `develop` @ `d874aa5` · **Status:** assessment only, no code written

This document answers: is a native iOS version of Recomp Tracker feasible, what is the right
architecture, what actually carries over, what will hurt, and in what order to build it.

Every number below was measured from the tree, not estimated. External claims are dated and
flagged where they are version-dependent.

---

## 1. Verdict

**Feasible, and unusually well-positioned — but it is a rebuild of the app around a reusable
core, not a translation.**

Three findings drive everything else:

1. **Your `domain/` layer is already multiplatform-shaped.** 6,347 LOC of the app's actual IP —
   the adjustment engine, rebalance engine, trend/adherence math, 18 coach detectors — with
   **zero `android.*` and zero `androidx.*` imports**, and (the decisive measurement)
   **zero `suspend` functions, zero `Flow`s, zero `StateFlow`s**. It is pure synchronous
   computation over 107 data classes and 26 enums.
2. **There is no backend, no auth, no sync.** Local-first, outbound-only to four third-party
   HTTP APIs. The hardest part of most cross-platform ports does not exist here.
3. **The UI is 65% of the codebase and none of it carries over.** 35,322 LOC across 143 files,
   45 distinct surfaces. This is the work.

**Recommended architecture: native Swift/SwiftUI, with `domain/` — and only `domain/` — shared
via a Kotlin Multiplatform module.** Reasoning in §4. This is a narrower sharing scope than the
usual KMP advice, and the narrowness is the point.

**Scope decided 2026-08-01:** iOS v1 = core loop + AI coach, on an **iOS 26+** floor. The Train
module and the NEVO/Samsung CSV importers are deferred to v1.1 (§10). Seven phases, with a walking
skeleton at the end of Phase 2 and a **TestFlight build at the end of Phase 4** — before the AI
coach lands, so the store process starts early. Absolute timelines are deliberately omitted; see
§6.1 for why, and for how to calibrate them yourself after Phase 1.

---

## 2. What you are actually porting

### 2.1 Measured inventory

| Layer | Files | LOC | Share | Ports to iOS? |
|---|---:|---:|---:|---|
| `ui/` | 143 | 35,322 | 65% | ❌ rebuild |
| `data/` | 100 | 8,525 | 16% | ⚠️ models yes, plumbing no |
| `domain/` | 70 | 6,347 | 12% | ✅ **shareable as-is** |
| `ai/` | 28 | 3,069 | 6% | ⚠️ ~83% pure, but repo-coupled |
| `core/` | 5 | 1,017 | 2% | ⚠️ mostly |
| **Total (main)** | **346** | **54,280** | | |
| **Tests** | **222** | **25,845** | | 209 of 222 are pure JVM |

### 2.2 Feature surface

- **45 distinct UI surfaces**: 32 navigation destinations (31 live, 1 dead route — `FoodsScreen`)
  + 13 full-screen overlays/sheets, plus 25 dialogs.
- **32 ViewModels**, uniform `MutableStateFlow<UiState>` + `collectAsStateWithLifecycle` pattern.
- **5 bottom-nav tabs** (Home, Body, Food, Coach, Train) over a single flat `NavHost`.
- **19 Room tables** (schema v15), 14 hand-written migrations, 109 DAO queries.
- **10 DataStore stores** (CLAUDE.md says 11 — that is doc drift; the grep finds 10).
- **19 LLM coach tools**, 18 proactive coach detectors, 77-chunk RAG corpus.

### 2.3 External dependencies (all outbound, all optional)

| Service | Endpoint | Auth |
|---|---|---|
| LLM (OpenRouter default) | user-configurable base URL | `Authorization: Bearer`, user-supplied key |
| Tavily web search | `api.tavily.com/search` | key in JSON body, user-supplied |
| Open Food Facts | `world.openfoodfacts.org/api/v2/…` | none |
| Exercise library seed | `raw.githubusercontent.com/…/free-exercise-db` | none |

**No Firebase SDK in the app** (Firebase is CI-side distribution only). No accounts, no server.

---

## 3. What carries over, and what does not

### 3.1 Carries over essentially unchanged

| Asset | Why it survives |
|---|---|
| **`domain/` (6,347 LOC)** | Zero platform imports. Only blockers are `java.time` (27 files), `java.util.Locale` (5), `java.io` (4, in the CSV parsers). |
| **`domain/` tests (6,096 LOC)** | Pure JVM. These are the executable specification of every engine — the single most valuable asset in the port. |
| **The 19 tool JSON schemas** (`ai/CoachTools.kt`) | Raw JSON strings. |
| **5 prompt builders (445 LOC)** | Pure string assembly. |
| **Knowledge base (`ai/knowledge`, 206 LOC + corpus.json)** | Deterministic keyword scorer, zero platform deps. Ship the same `corpus.json`. |
| **All 4 bundled assets** | `exercises.json` (978 KB, 873 exercises), `corpus.json` (113 KB), two muscle-path JSONs. Same files, `Bundle.main` instead of `assets/`. |
| **All 3 file formats** | Backup `.json`, personal-foods `.json`, `.rtroutine`. Plain camelCase JSON → Swift `Codable` 1:1. `.rtroutine` in particular was designed for portability (carries `(source, externalId, name)`, never local ids) — **an iOS app can read and write Android `.rtroutine` files byte-compatibly**. |
| **`RateLimiter` + `QuietHours`** | Pure Kotlin, no platform deps. The highest-fidelity port in the app. |
| **The 19 entity shapes** | **Zero Room `@TypeConverters` in the entire repo.** Every column is a SQLite primitive → 1:1 Swift structs. |

### 3.2 Must be rebuilt

| Area | Android | iOS |
|---|---|---|
| Entire UI (35,322 LOC) | Compose + custom glass DS | SwiftUI |
| Persistence plumbing | Room + KSP | GRDB (see §5.2) |
| Preferences (10 stores) | DataStore | actor-backed JSON stores / `UserDefaults` |
| Secrets | EncryptedSharedPreferences | Keychain |
| Health | Health Connect | HealthKit — **not a translation, see §5.1** |
| Barcode | CameraX + ML Kit | VisionKit `DataScannerViewController` |
| Background work | WorkManager | `BGTaskScheduler` + `HKObserverQuery` — **behaviourally weaker** |
| Notifications | `NotificationCompat` | `UNUserNotificationCenter` |
| HTTP + SSE | OkHttp (hand-rolled SSE) | `URLSession.bytes(for:).lines` |
| `CoachToolExecutor` (904 LOC) | Room-coupled tool impls | rewrite per platform regardless |

### 3.3 Things you delete rather than port

Worth calling out, because it offsets the rebuild cost:

- **Vico** — declared in the build, **zero source references**. All six chart types are hand-rolled
  Canvas. Dead dependency.
- **`FoodsScreen`** (242 LOC) — registered route, never navigated to.
- **`MacroRingChart`, `StackedBarChart`, `ui/component/ProgressBar.kt`** — no production call sites.
- **`SwipeToRevealRow`** (153 LOC bespoke gesture + custom `Layout`) → `.swipeActions` is one line.
- **`DurationWheelMath` + wheel picker** (~130 LOC) → `Picker(.wheel)`.
- **`DragHaptics`** `LocalView` escape hatch → `.sensoryFeedback`.
- **The two-backdrop architecture** in `RecompApp.kt` — a workaround for a Compose circular
  `GraphicsLayer` read. iOS materials sample the live backdrop natively.
- **The Kyant `backdrop`/`shapes` glass stack** — iOS 26 ships Liquid Glass as an OS material.
- **All ProGuard keep rules**, the entire `:macrobenchmark` module (Swift is AOT — baseline
  profiles are meaningless), the shared-keystore scheme, the `<queries>` block and Play Store
  fallback, `READ_HEALTH_DATA_HISTORY` + its feature check, FileProvider + `file_paths.xml`, and
  the EncryptedSharedPreferences corrupt-keyset recovery path.
- **The `selectedFont` preference** — stored, rendered in a picker, and **never applied** (no
  `res/font` directory exists). Don't port dead wiring.

That is roughly 1,500 LOC of UI plus a large amount of build and workaround machinery that simply
evaporates.

---

## 4. Architecture decision

### 4.1 The four options

| | Shared | iOS UI | Android disruption | Verdict |
|---|---|---|---|---|
| **A. Two fully independent apps** | 0% | SwiftUI | none | Viable, but discards leverage you already built |
| **B. KMP shared logic (domain+ai+data) + SwiftUI** | ~35% | SwiftUI | moderate | Over-scoped — see 4.3 |
| **C. Compose Multiplatform** | ~85% | Compose in a SwiftUI shell | high | **Rejected** — see 4.2 |
| **D. Flutter / React Native rewrite** | 0% | Dart/JS | total | **Rejected** — see 4.2 |
| **★ B-minimal. KMP `domain/` only + SwiftUI** | ~11% LOC, ~100% of the algorithmic IP | SwiftUI | low | **Recommended** |

### 4.2 Why the rejected options lose

**Flutter / React Native** discard all 54,280 lines of Kotlin *and* all 25,845 lines of tests to
solve a problem that does not require it. Your domain layer and its test suite are the app's most
valuable and least reproducible assets. Airbnb's 2018 RN postmortem is stale on RN specifics but
its transferable lesson holds: a partial cross-platform migration is worse than either endpoint.
Flutter additionally has **no Liquid Glass support** ([flutter#170310](https://github.com/flutter/flutter/issues/170310),
open since 2025-06, team has stated they are not developing it) and a multi-year unresolved
Impeller blur-jank record — fatal for an app whose entire visual language is stacked backdrop blurs.

**Compose Multiplatform** is the closer call and deserves a fair hearing. iOS was declared Stable
in CMP 1.8.0 (2025-05). But:

- JetBrains was still fixing **scroll inertia and unexpected fling** (1.11.0, 2026-05),
  **back-swipe/pager conflicts** (1.12.0-beta01, 2026-06), and **frame drops when dragging
  scrollable content** (1.12.0-beta02, 2026-07). Fling physics were fixed 12 months *after* the
  "on par with SwiftUI" claim. 139 open YouTrack issues carry `Subsystem: iOS`.
- Native text input arrived only in **CMP 1.11.0 (2026-05)** and is **still opt-in**. Until then,
  no autocorrect, no Autofill, no iOS selection handles, no Look Up/Translate.
- Accessibility gaps are real and would fail an audit: `Checkbox` and `Tab` semantics roles are
  **ignored by VoiceOver** ([CMP-9055](https://youtrack.jetbrains.com/issue/CMP-9055)).
- **The decisive one:** JetBrains' own Liquid Glass guidance (2026-07-21) states that to adopt it
  *"you need a native SwiftUI shell, because Liquid Glass effects are rendered by the system
  through native `TabView`, `NavigationStack`, and toolbar APIs"* — and that Compose then
  *"no longer manages the back stack."* For an app whose identity **is** a glass design language,
  the shared-UI win is halved before you start.
- Independent 2026 benchmarking (Software Mansion, public repos) measures CMP iOS at **157–251 MB
  RAM vs 44.7–131.4 MB** for the React Native equivalent — Skia's persistent buffers, which
  Android gets from the OS and iOS must ship.
- No hot reload on iOS, for 143 UI files of iteration.

Note that Google's own position is instructive: [developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform)
commits to **shared business logic**; shared UI is attributed to JetBrains. Google's largest KMP
deployment (Docs on iOS) uses native UI.

### 4.3 Why `domain/`-only rather than the usual "share domain + data + ai"

This is where the measurement earns its keep. The Kotlin↔Swift boundary tax is not uniform — it
scales almost entirely with **`Flow`, `StateFlow`, `suspend`, and sealed types** crossing it.
Kotlin's Objective-C interop has been officially **Beta since 2018**; without the SKIE compiler
plugin, `Flow<T>` reaches Swift as an untyped, non-cancellable callback and `suspend` becomes a
completion handler.

Measured interop surface:

| | `suspend fun` | `Flow<` | `StateFlow<` | `sealed` | `data class` |
|---|---:|---:|---:|---:|---:|
| **`domain/`** | **0** | **0** | **0** | 6 | 107 |
| `ai/` + `data/` | 395 | 106 | 28 | — | — |

**`domain/` has none of the constructs that make KMP interop painful.** It is synchronous pure
functions over value types. Sharing it costs you almost nothing at the boundary. Sharing `ai/` and
`data/` would drag 395 suspend functions and 134 flows across it — and would additionally commit
you to Room-KMP (for which no production report of a schema this size on iOS exists), DataStore-KMP,
and an OkHttp→Ktor migration.

So: **share the pure math, rebuild the plumbing natively.** You get bit-exact behaviour on the
algorithms where divergence is dangerous, and you keep GRDB, `URLSession`, and Keychain as plain
idiomatic Swift with a full debugger and no interop layer.

### 4.4 What KMP costs you even at this scope

Be clear-eyed; these apply even to the minimal version:

- **`kotlinx-datetime` is officially Alpha** (0.8.0, 2026-05) and pre-1.0 after six years. It is
  also the only way to express `LocalDate` arithmetic in common code. 0.8.0 removed
  `kotlinx.datetime.Instant`/`Clock` in favour of `kotlin.time` equivalents.
- **Two hand-rolled replacements are mandatory and load-bearing.** `temporal.IsoFields` (ISO week
  number, `CoachDetectorSupport.kt:22`) and `String.format(Locale.US, "%+.1f", …)`
  (`:27,:31`) have no common-code equivalent. These feed `dedupKey` and `fallbackText` — a
  rounding or formatting difference silently changes cooldown behaviour and user-visible copy
  rather than failing loudly. **Golden-value tests against current JVM output are non-negotiable.**
- **You cannot evaluate expressions in the debugger.** Kotlin/Native's LLDB support explicitly
  states this is unsupported *"and currently there are no plans for implementing it."*
- **Crash reports point at the boundary, not the Kotlin frame** that threw, unless you add
  [CrashKiOS](https://github.com/touchlab/CrashKiOS). Budget it as mandatory.
- **Your Xcode upgrade cadence becomes coupled to Kotlin releases.**
- **Extracting `:shared` is a real refactor** — the repo is currently a single `:app` module
  (`settings.gradle.kts` includes only `:app` and `:macrobenchmark`).

### 4.5 The honest alternative, and what would flip the decision

If the KMP spike (§7, Phase 0) goes badly, **port `domain/` to Swift instead and accept two
implementations.** That is a legitimate outcome, not a failure, because:

- It is ~5,550 LOC of pure math with **6,096 LOC of tests that are the specification**. Port the
  tests first, then implement against them — the bit-exactness risk becomes a verified property
  rather than a hope.
- **There is no cross-device sync**, so the two platforms' numbers never meet. Divergence has no
  user-visible consequence beyond the shared file formats, which are versioned and explicitly
  designed for portability.

The trade is: KMP buys guaranteed convergence and one place to evolve the engines; the Swift port
buys a simpler toolchain and full debuggability. Phase 0 decides it on evidence.

---

## 5. Top technical risks

Ranked by expected pain, not by how they look on a slide.

### 5.1 🔴 HealthKit is not Health Connect — three structural mismatches

This is the highest-risk area in the whole port and the one most likely to force UX redesign.

**(a) iOS never tells you a read permission was denied.** Apple: *"your app doesn't know whether
someone granted or denied permission to read data from HealthKit."* `authorizationStatus(for:)`
reports **write** status only. There is no `getGrantedPermissions()` equivalent and no programmatic
revoke.

Your code gates three sync entry points on exactly this
(`HealthSyncCoordinator.kt:80,97,129` → `hcRepository.hasPermissions()`), and the entire
Integrations screen state derives from it. **That design cannot port.** You must replace
"you denied X, tap to fix" with "we see no data — check Settings → Privacy → Health."

**(b) Nutrition is not one record.** Health Connect gives you a `NutritionRecord` with a name and
all macros. HealthKit requires one `HKQuantitySample` **per nutrient** (`dietaryEnergyConsumed`,
`dietaryProtein`, `dietaryCarbohydrates`, `dietaryFatTotal`, `dietaryFiber`, `dietarySugar`,
`dietarySodium`), optionally grouped into an `HKCorrelation(.food)` carrying
`HKMetadataKeyFoodType`. Apps that write loose samples without the correlation produce data other
readers cannot interpret. `toFoodImportCandidate()` is a rewrite, not a translation — and the
365-day import may recover **less** data on iOS because many apps don't write correlations.

**(c) Sleep has no session object.** `SleepSessionRecord` (start/end, one call) becomes a flat
stream of `HKCategoryType(.sleepAnalysis)` samples (`.asleepCore/.asleepREM/.asleepDeep/.inBed/
.awake`) that you must group into a night yourself and dedupe across sources. Worse:
**statistics queries only work on `HKQuantityType`** — there is no aggregation API for category
types, so all sleep aggregation moves into your own code. Health Connect's aggregate API covers
sleep duration; this is a genuine regression.

**Plus, new and version-dependent:** iOS 27 adds a **second consent screen letting users grant only
a limited historical window**. Discover the boundary with `getEarliestAuthorizedSampleDate(for:)`
(documented iOS 27.0+ Beta) and treat earlier data as *unknown, not absent*. Your "import 365 days"
flow needs a "we could only reach N days" state.

**One thing that gets easier:** HealthKit read authorization has no separate history-depth
permission, so `READ_HEALTH_DATA_HISTORY`, `supportsHistoricalNutritionImport()`, and the feature
check all disappear on iOS ≤26.

### 5.2 🔴 Persistence choice — pick GRDB, not SwiftData

Two independent analyses converged on this. SwiftData breaks on three specific properties of your
schema:

1. **Id-for-id backup restore is unimplementable.** `BackupRepository.kt:79,85-92` restores 8 tables
   preserving original autoincrement ids — required because `meal_entries.slotId` has **no foreign
   key** and the training graph is id-linked. SwiftData's `PersistentIdentifier` is opaque and
   cannot be assigned. You would have to build an old→new id map for six tables and rewrite every
   referring column — exactly the class of remap that caused P0-2.
2. **Per-column UPDATEs are load-bearing, not an optimisation.** `DailyLogDao.kt:38-63` (six
   single-column updates) and `WorkoutSessionDao.kt:61-73` exist *specifically* because whole-row
   read-modify-write lost concurrent field writes (P1-18, P2-7). SwiftData's object-graph model
   naturally does whole-object saves — a naive port **reintroduces both bugs**.
3. **Bulk insert.** The 365-day nutrition import is thousands of rows in one transaction. Inserting
   thousands of `@Model` objects in one context save is a known SwiftData performance cliff.

GRDB additionally maps almost mechanically: `DatabaseMigrator.registerMigration("v2") { }` is a
direct analogue of each `MIGRATION_n_m`, and `ValueObservation` is a direct analogue of Room's
`Flow`-returning DAOs.

**Two things that make this much easier than it sounds:** there are **zero `@TypeConverters`** in
the repo, and **zero exotic SQL** — no FTS, no window functions, no CTEs, no `@RawQuery`, no
SQLite date functions. Of 109 queries, one is a 3-table JOIN, one is a `GROUP BY`, one is a
`COALESCE(MAX)+1`. All the analytics live in Kotlin. There is essentially no SQL logic to port.

### 5.3 🔴 Dates: keep them as `YYYY-MM-DD` strings

The single most likely silent-corruption bug in the port. Six tables key or filter on ISO date
**strings**, and every range predicate depends on lexicographic TEXT ordering — including the
`"9999-12-31"` sentinel at `LogRepository.kt:110`. The idiomatic Swift move is `Date` (a `Double`
instant). The moment `daily_logs.date` becomes a `Date`:

- `BETWEEN`, `>=`, `< :date` change meaning;
- the sentinel becomes nonsense;
- `Calendar.startOfDay` timezone conversion silently shifts rows across day boundaries.

**Store `String`, expose a computed date in Swift.** And use `Locale(identifier: "en_US_POSIX")` on
every formatter — Android's `LocalDate.toString()` is locale-independent, `DateFormatter` is not,
and a Buddhist-calendar device would corrupt the TEXT primary keys of three tables.

### 5.4 🟠 Background execution is strictly weaker on iOS

`WorkManager` guarantees eventual execution. `BGTaskScheduler` guarantees nothing: no periodic API,
`earliestBeginDate` is a hint, ~30 s budget for `BGAppRefreshTask`, and **a force-quit app never
runs background tasks at all**.

Do not port `CoachDigestWorker` as a BGTask. The layered design that actually works:

1. Schedule the **notification** with `UNCalendarNotificationTrigger`, not the work.
2. Refresh its content opportunistically, in priority order: **`HKObserverQuery` +
   `enableBackgroundDelivery(.daily)`** (you already read steps/weight/sleep, so this is free and is
   the most dependable wake iOS gives you) → `BGAppRefreshTask` → app foreground (your existing
   `CoachContextCache` per-day memoisation already handles this correctly).
3. Keep a rolling ~7 days of pre-scheduled notifications, rewritten on each wake — but mind the
   **64 pending request limit**, which silently drops excess.

Accept that on a force-quit device the digest degrades to a generic prompt. That is the honest iOS
ceiling, and it is Apple's constraint, not an architecture choice.

### 5.5 🟠 App Store: health data + user-supplied LLM key is the most scrutinised intersection

Three specific things will bite:

- **Guideline 2.1 (completeness).** A reviewer with no OpenRouter account sees a dead AI feature →
  rejection. Mitigate with (a) a demo mode producing canned deterministic coach output when no key
  is present, (b) a working rate-limited key in App Review Notes, (c) every AI surface clearly
  optional with an explicit "requires your own API key" empty state.
- **Guideline 5.1.2(i), amended 2025-11-13**, now explicitly names **third-party AI**: you must
  disclose that personal data is shared and obtain explicit permission first. Ship a first-use
  consent modal naming OpenRouter, listing the data categories sent, with a working decline path.
- **Guideline 5.1.3(ii): no personal health information in iCloud.** That rules out CloudKit /
  iCloud Drive / iCloud KVS for logs, body entries, or anything health-derived. Your existing
  local-export-plus-user-driven-share pattern is already compliant — keep it.

Also required: `PrivacyInfo.xcprivacy` with `NSPrivacyCollectedDataTypeHealth`/`…Fitness` plus
required-reason declarations for `UserDefaults` (CA92.1), file timestamps (C617.1), and disk space
(E174.1); privacy nutrition labels that match; a 1.4.1 "not medical advice, consult a professional"
disclaimer; and an honest answer to the age-rating questionnaire's medical/wellness question (a
coach that emits arbitrary model text is conservatively not 4+). **Do not add ATT** — you don't
track, and an unnecessary prompt is itself a rejection reason.

### 5.6 🟡 Smaller but real

| Risk | Detail |
|---|---|
| **Profile photo** | `takePersistableUriPermission` has **no iOS equivalent**. `PhotosPicker` gives a non-durable URL — you must copy bytes into the app container and store a relative path. |
| **Steps dedup** | Your code carries a war story (17k steps on a 4k day) fixed by Health Connect's cross-origin aggregate. The same bug class exists on iOS via iPhone+Watch double-counting, with a *different* fix (`HKStatisticsQuery` + source predicates). Don't assume `.cumulativeSum` is safe out of the box. |
| **ATS vs user-supplied base URL** | iOS blocks cleartext by default. Anyone self-hosting an OpenAI-compatible endpoint over HTTP is blocked without an `NSAppTransportSecurity` exception. Decide this explicitly before shipping. |
| **Samsung ZIP import** | No system zip reader on iOS. Needs ZIPFoundation or a scope cut. |
| **`NevoCsvParser`** | 453 LOC of bespoke dual-format CSV parsing (long/tidy vs wide, delimiter detection, Dutch/English aliases). A genuine port, not a translation — or cut it from iOS v1. |
| **Reverse-result navigation** | Four flows pass results back via `savedStateHandle` on `previousBackStackEntry` (picked exercises, replacement, scanned food, picked ingredient). No SwiftUI analogue — redesign as bindings or a shared selection coordinator. |
| **`androidx.security-crypto` is fully deprecated** | Unrelated to iOS, but worth knowing: all APIs in the library you use for `SecureKeyStore` were deprecated as of 1.1.0 (2025-07-30), with guidance to use Android Keystore directly. |

### 5.7 What is *not* a risk, contrary to expectation

- **Liquid glass.** Your entire Kyant backdrop stack exists because Android has no OS glass
  material. iOS 26 ships it: `.glassEffect(_:in:)`, `GlassEffectContainer`, `.buttonStyle(.glass)`,
  and `TabView` renders the real floating glass tab bar natively. **This is the single biggest
  simplification in the port.** Apple's guidance is explicit that you should *reduce* custom
  backgrounds on navigation and control elements — so porting your custom blur stack verbatim would
  look wrong, perform worse, and read as non-native.
- **SSE streaming.** `for try await line in URLSession.shared.bytes(for: req).0.lines` is cleaner
  than the hand-rolled `BufferedSource.readUtf8Line()` loop.
- **Charts.** Swift Charts covers everything here (Vico is unused). Only the clip-reveal draw-in
  animation, the 3-layer glow end dot, and the candy-stripe progress bar need a custom `Canvas`.
- **The P0 backlog.** All three P0s from the July review are **already fixed in the working tree** —
  `BackupPayload` carries all 7 training tables behind a version field, slots restore id-for-id, and
  the deep-link effect is gated on `onboardingComplete` plus graph readiness. The review doc is
  stale, not the code. Nothing blocks starting iOS work.
- **Your toolchain.** macOS 26.5, Xcode 26.5, Swift 6.3.2, iOS 26.2/26.4/26.5 simulators, Apple
  Silicon. Ready today. Only the $99/yr developer account is missing — and it is needed **from day
  one**, because HealthKit and background modes require a paid account's entitlements.

---

## 6. Phased roadmap

Each phase ends in something runnable and verifiable. No phase depends on a later one.

### Phase 0 — Decide the shared-core question (small, reversible, do this first)

The only phase with a decision gate rather than a deliverable.

1. Extract `domain/` into a `:shared` KMP module (`androidTarget`, `iosArm64`,
   `iosSimulatorArm64`). Migrate `java.time` → `kotlinx-datetime` + `kotlin.time`. Hand-roll the
   ISO-week and signed-decimal formatters. Keep `:app` and all 222 tests untouched in
   `androidUnitTest`.
2. Add **golden-value tests** pinning current JVM output for every `dedupKey` and `fallbackText`
   the coach produces, *before* touching the formatters.
3. Stand up a bare iOS app, consume 2–3 pure `domain/` functions from SwiftUI. Measure the
   ergonomics and the IPA size delta.

**Gate:** if the datetime migration is clean and the Android suite stays green, take the shared
core. If it fights you, port `domain/` to Swift test-first instead (§4.5) and proceed — everything
after this phase is unaffected either way.

**Also do here:** enrol in the Apple Developer Program (there is a lead time, and you need the
entitlements from day one), and reserve the bundle identifier.

### Phase 1 — Foundations (no UI)

- Xcode project + SPM, GRDB, the 19 tables, all 14 migrations expressed as
  `DatabaseMigrator` registrations, with **dates as `YYYY-MM-DD` strings**.
- The 10 preference stores as actor-backed `Codable` JSON stores behind the same interfaces the
  Android code already defines (`CoachInbox`, `CoachMemory`, `PushHistory`, …).
- Keychain wrapper for the two API keys (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` —
  the digest may need to read the key while locked).
- Bundle the four asset JSONs; port the version-gated exercise-library seed.
- **Port the backup/`.rtroutine`/personal-foods codecs and prove round-trip compatibility against
  real files exported from the Android app.** This is the migration path for you as a user, and it
  is the cheapest possible end-to-end integration test of the schema.

**Verifiable:** import an Android backup, assert every table and every slot link.

### Phase 2 — Walking skeleton

- App shell: `TabView` (5 tabs) + `NavigationStack` per tab, native Liquid Glass.
- Design tokens: `AppType` → `Font` extensions (prefer Dynamic Type over fixed points),
  `AppColors`/`AppAccent` → asset catalog + `@Environment` — a near-exact analogue of
  `CompositionLocal`.
- The card/button/sheet family on `.glassEffect` / `Material`.
- **One real screen end-to-end: Food Log.** It is the app's most-used surface, the largest single
  file (1,428 LOC), and exercises day navigation, slots, macro math, and the reconcile banner. If
  this screen feels right, the rest is throughput.

**Verifiable:** log a meal on iOS, see it in the DB, see totals match the Android app for the same
inputs.

### Phase 3 — Core loop

Dashboard, Body/check-in, Food Library + amount sheets, Plan, Profile, Onboarding, Streaks. Charts
(`SparklineChart`, `CalorieProgressBar`, `WeekCalorieStrip`, `StreakGoalRing`) via Swift Charts plus
a small custom `Canvas` layer for the clip-reveal draw-in, the glow end dot, and the candy stripes.

**Verifiable:** a full day can be logged, reviewed, and adjusted without touching Android.

### Phase 4 — Platform integrations → first TestFlight

HealthKit in increasing order of difficulty: **steps → weight → sleep → nutrition**. Barcode
scanning via `DataScannerViewController`. Notifications plus the `RateLimiter`/`QuietHours` port
(the highest-fidelity port in the app — pure logic, no platform deps). `BGTaskScheduler` +
`HKObserverQuery` for the digest. Share/import of `.rtroutine` via a declared UTI.

**Ship to TestFlight internal at the end of this phase** (≤100 testers, no review). It is the only
realistic way to exercise HealthKit, camera, notifications, and background behaviour — **none of
which work meaningfully in the simulator**. Getting here early is the main argument for the reduced
v1 scope.

### Phase 5 — AI coach

The `URLSession` SSE client, the tool executor rewritten against GRDB, the write-confirmation flow,
coach chat, insight cards, weekly briefing, and the knowledge retriever. The 19 tool schemas, the 5
prompt builders, and the RAG corpus port unchanged; `CoachToolExecutor` (904 LOC) is the real work
and must be rewritten per platform regardless of the shared-core decision.

Ship the **five routine tools as unavailable** in v1 — the Train UI does not exist yet, so the coach
should decline them cleanly rather than write rows no screen can show.

Two concurrency notes: the turn loop's `Mutex` + `AtomicReference` + `CompletableDeferred`
confirmation protocol becomes an `actor` + `CheckedContinuation`; and because Swift structured
concurrency cancels child tasks when the parent deallocates, long-running generations must be owned
by an unstructured `Task` held by the container — the equivalent of today's `appScope`.

### Phase 6 — Store readiness

Demo mode for AI (Guideline 2.1), the third-party-AI consent modal (5.1.2(i)), privacy manifest and
nutrition labels, disclaimers (1.4.1), age rating, screenshots at every required size, phased
release. Then submit.

### Deferred to v1.1

Train hub, ActiveSession, RoutineBuilder, SessionSummary, SessionDetail, ExercisePicker; the five
routine coach tools; NEVO and Samsung Health CSV import. See §10.2 — the schema for all of this
still ships in Phase 1.

### 6.1 A note on sequencing and on estimates

**Order rationale:** persistence before UI (everything depends on it); one hard screen before many
easy ones (Food Log is the honest test of the design system); HealthKit late (it is the riskiest
area and benefits from a working app to integrate into); AI last (it is optional at runtime, gated
behind a key, and therefore never blocks a shippable build).

**On timelines:** a conventional estimate for the UI at full parity would be ~18–20 engineer-weeks;
deferring the Train sub-flow removes roughly 4 of those from v1. I am deliberately not putting
calendar dates on this, for two reasons. First, your measured velocity —
1,072 commits and ~80,000 LOC between 2026-05-29 and 2026-07-12 — is far outside the range those
estimates assume, so applying them would understate you. Second, the dominant unknown is not
throughput but **how long it takes you to become fluent in SwiftUI and Xcode**, and there is no
credible published figure for that. Phase 1 is the calibration run: it is small, well-specified,
and mostly non-UI. Time it, then extrapolate.

---

## 7. Recommended project structure

```
Personal Dietitian/
├── settings.gradle.kts          # :app, :macrobenchmark, :shared
├── shared/                      # KMP — commonMain only, no Android/iOS code
│   └── src/commonMain/kotlin/…/domain/
│       ├── adjustment/ adherence/ trend/ plan/ rebalance/
│       ├── coach/               # 18 detectors, selector, RateLimiter
│       ├── food/ workout/ insight/ review/ streak/ share/
│       └── time/                # hand-rolled ISO week + signed formatting
├── app/                         # unchanged Android app, + implementation(project(":shared"))
└── ios/
    ├── RecompTracker.xcodeproj
    └── RecompTracker/
        ├── App/                 # entry point, AppContainer, routing
        ├── Persistence/         # GRDB records, DatabaseMigrator, DAO equivalents
        ├── Preferences/         # 10 actor-backed stores + Keychain
        ├── Platform/            # HealthKit, scanner, notifications, BGTasks, files
        ├── Networking/          # URLSession client, SSE, OFF, Tavily
        ├── AI/                  # coordinators, prompt builders, tool executor
        ├── DesignSystem/        # tokens, glass cards, buttons, sheets
        ├── Features/            # one folder per screen, mirroring ui/
        └── Resources/           # exercises.json, corpus.json, muscle paths, assets
```

**Excluded from `:shared` deliberately:** `domain/export/BackupModels.kt` (a DTO over 19 Room
entities — misfiled in domain), `domain/foodimport/` (`java.io` streaming CSV parsers), and the
three files typed on Room entities (`RecentFoods`, `RecipeWithIngredients`, `ExerciseLibraryJson`).
That is ~795 LOC excluded, leaving **~5,550 LOC shared**.

**Move into `:shared` alongside domain:** `core/model/MacroTotals.kt` and the pure enums +
`UserProfilePreferences`/`PlanPreferences` value types from `data/preferences/`. Those two files
contain no Android code at all — the "documented `domain/plan` → `data.preferences` exception" in
CLAUDE.md is 6 files, 8 imports, 5 pure symbols. It is a package-naming artifact, not
entanglement, and resolving it is under a day's work.

---

## 8. Testing strategy

| Layer | Approach |
|---|---|
| **Shared domain** | Keep the 6,096 LOC of existing tests running in `androidUnitTest` unchanged. Add golden-value tests for ISO-week keys and signed formatting **before** migrating the formatters. If you take the Swift-port path instead, translate the tests **first** and implement against them. |
| **Persistence** | A real GRDB file per test. The single highest-value test: export → wipe → import → assert every table **and every slot link**. That one test would have caught both historical P0s. |
| **Engines on iOS** | Swift Testing (`@Test`/`#expect`). Same fixtures as the Kotlin tests so outputs can be diffed. |
| **Round-trip compatibility** | A committed corpus of real Android-exported `.json` and `.rtroutine` files, asserted to import correctly on iOS. This is your only guard against silent format drift. |
| **UI** | XCUITest for the 3–4 critical paths (log a meal, complete a check-in, run a session). Do not chase the Compose test suite's breadth. |
| **Device-only** | Camera, HealthKit, notifications, and background tasks **cannot be tested in the simulator**. Budget real-device testing from Phase 4 onward. |
| **CI** | `macos-latest` + `xcodebuild test`. Note runner minutes cost roughly 10× Ubuntu — keep the iOS job to unit tests on PR and archive only on merge. |

---

## 9. App Store preparation checklist

**Account & tooling:** Apple Developer Program $99/yr (individual is fine — avoid clinical
positioning that would invoke 5.1.1(ix)); a physical iPhone; Xcode 26+ (mandatory for uploads since
2026-04-28).

**Entitlements & Info.plist:** HealthKit capability + **Background Delivery** entitlement;
`NSHealthShareUsageDescription`, `NSHealthUpdateUsageDescription`, `NSCameraUsageDescription` (all
specific — vague strings are rejected); `BGTaskSchedulerPermittedIdentifiers`; `UIBackgroundModes`
(`fetch`, `processing`); `UTExportedTypeDeclarations` + `CFBundleDocumentTypes` for `.rtroutine`.

**Privacy:** `PrivacyInfo.xcprivacy` (health + fitness data types; required-reason APIs CA92.1,
C617.1, E174.1); nutrition labels matching it; privacy policy URL in Connect **and** in-app; no ATT.

**Review-specific:** demo mode or a working key in App Review Notes (2.1); third-party-AI consent
modal naming the provider (5.1.2(i)); "not medical advice" disclaimer (1.4.1); no PHI in iCloud
(5.1.3(ii)); honest age-rating answers including the medical/wellness and — required from
September 2026 — social-media questions.

**Release:** TestFlight internal (≤100, no review) → external (Beta App Review, <24h typical) →
submission → phased release. Builds expire after 90 days.

---

## 10. Decisions

### Settled (2026-08-01)

1. **iOS v1 scope: core loop + AI coach.** Food Log, Body/check-in, Dashboard, Plan, Profile,
   Onboarding, HealthKit, barcode scanning, coach chat and insight cards. **Deferred to v1.1:** the
   Train module (6 screens, the largest single sub-flow) and the NEVO/Samsung CSV import tooling.
2. **Deployment target: iOS 26+.** Native Liquid Glass with no fallback path. See §10.1 for what
   this buys and what it costs.

### Still open

3. **Shared core.** KMP `:shared` module vs porting `domain/` to Swift. Phase 0 answers this on
   evidence rather than preference — nothing after Phase 0 depends on the outcome.
4. **Backup import as the switching path.** Android backup import already carries `catalogFoods`,
   so an Android user moving to iOS keeps their NEVO catalog without the CSV parser. Worth
   confirming this is the intended migration story before v1.1 scoping.

### 10.1 Consequences of the iOS 26+ floor

**Simplifications this unlocks:**

- `.glassEffect(_:in:)`, `GlassEffectContainer`, `.buttonStyle(.glass)`, and the native glass
  `TabView` are always available. **No `Material` fallback layer for any of the ~45 surfaces** —
  this is the single largest code saving in the port.
- `BGContinuedProcessingTask` (iOS 26+) is available for the 365-day nutrition import: a
  user-initiated long task with a system progress UI, which is a far better fit than
  `BGProcessingTask`.
- `Chart3D` and the iOS 26 reorderable-container APIs are on the table (neither is needed for v1).
- **Likely:** every iOS 26 device has a Neural Engine, which would make
  `DataScannerViewController.isSupported` always true and let you drop the
  `AVCaptureMetadataOutput` fallback entirely. **Verify Apple's iOS 26 device list before removing
  the fallback** — it is a small amount of code to keep as insurance.

**Costs:**

- Smaller addressable device base. Acceptable for a personal-first app; revisit if that changes.
- `UIDesignRequiresCompatibility` (the Liquid Glass opt-out) is reportedly ignored under the iOS 27
  SDK — secondary sources only, but it means committing to glass is effectively permanent anyway.

### 10.2 What v1.1 inherits

The Train module is deliberately deferred, not dropped, and it is the cleanest thing to defer:
7 of the 19 tables (`workouts`, `workout_exercises`, `planned_sets`, `workout_sessions`,
`session_exercises`, `session_sets`, `exercises`) serve it alone, and the routine coach tools are
5 of the 19. **Build the full schema and all 14 migrations in Phase 1 anyway** — the tables cost
nothing empty, and it keeps backup round-trip compatibility with Android intact from day one. Only
the UI and the tool executors wait.

---

## Appendix — sources

Codebase claims were measured directly from `develop` @ `d874aa5` on 2026-08-01 and are cited
inline as `file:line`. External claims are drawn from current official documentation
(Apple Developer, kotlinlang.org, developer.android.com, JetBrains YouTrack) plus dated first-hand
engineering writeups, and were verified on 2026-08-01. Version-dependent and unverified items are
flagged at their point of use. The most significant uncertainties:

- `getEarliestAuthorizedSampleDate(for:)` and the limited-history consent screen are documented
  **iOS 27.0+ Beta** — confirm at GM.
- `HKCategoryValueSleepAnalysis` doc pages list staged cases as iOS 8.0+, contradicting their
  well-known iOS 16 introduction. Gate on iOS 16+.
- `UIDesignRequiresCompatibility` reportedly being ignored under the iOS 27 SDK comes from
  secondary sources only.
- Official Google and JetBrains pages disagree on the AGP 9 legacy-variant-API removal timeline
  (Q2 2026 vs H2 2026).
