# Reference — Android Platform API → iOS Mapping

Every OS-level dependency in the Android app and its iOS counterpart.

**Provenance:** ✅ verified against `develop` @ `d874aa5` on 2026-08-01 · 📋 from deep-dive analysis ·
🌐 external docs, verified 2026-08-01 (version-dependent items flagged).

**Risk key:** **L** mechanical · **M** redesign required, behaviour differs · **H** no clean
equivalent · **+** iOS is simpler or strictly better.

---

## 1. AndroidManifest surface

📋 The entire platform declaration is 93 lines — a small surface for an app this size.

**Permissions:** `CAMERA`, `INTERNET`, `POST_NOTIFICATIONS`, and five health reads
(`READ_STEPS`, `READ_WEIGHT`, `READ_SLEEP`, `READ_NUTRITION`, `READ_HEALTH_DATA_HISTORY`).

**Notable absences:** no health `WRITE_*` (the app is read-only against Health Connect), no
`ACTIVITY_RECOGNITION`, no storage permissions (all I/O is SAF/FileProvider), no
`FOREGROUND_SERVICE`, no `RECEIVE_BOOT_COMPLETED`, no `SCHEDULE_EXACT_ALARM`.

**Components — exactly four:** `MainActivity` (4 intent-filters: launcher, HC rationale for API ≤33,
and two `.rtroutine` VIEW filters), a `ViewPermissionUsageActivity` alias for the Android 14+ health
privacy entry point, and `FileProvider`. **No services, no broadcast receivers of our own.**

The two `.rtroutine` filters exist because Android extension matching is unreliable — one on
`content:` + `application/octet-stream`, one on `*/*` + `pathPattern`/`pathSuffix`. The in-file
comment documents the limitation. **On iOS a properly declared UTI makes this reliable**, so the
dual-filter hack disappears.

---

## 2. Health Connect (`data/health/`, 5 files)

### 2.1 What's read

📋 `HealthConnectRepository.kt:37-46` — four record types, **all read-only, zero writes**:
`StepsRecord`, `WeightRecord`, `SleepSessionRecord`, and `NutritionRecord` (a *separate, optional*
permission tier requested only for the historical import, alongside
`PERMISSION_READ_HEALTH_DATA_HISTORY`).

### 2.2 Queries in use

| What | How | Line |
|---|---|---|
| Steps today | `aggregate(AggregateRequest(StepsRecord.COUNT_TOTAL, …))` | `:146-154` |
| Steps history | `aggregateGroupByPeriod(…, Period.ofDays(1))` → `Map<LocalDate, Int>` | `:122-137` |
| Weight | raw `readRecords`, `maxByOrNull { it.time }` | `:156-161` |
| Sleep | raw `readRecords` over `[yesterday 12:00, now]`, `maxByOrNull { it.endTime }` | `:163-169` |
| Nutrition history | cursor-paginated `readRecords` with `pageToken` loop, 365 days | `:92-112` |

⚠️ **The steps war story is load-bearing.** The KDoc at `:139-145` records it verbatim: the
aggregate API is used *instead of* summing raw records because multiple sources (Samsung Health,
watch, phone counter) each write their own `StepsRecord` for the same walk — summing showed **17k
steps on a 4k day**. The aggregate deduplicates across data origins.

**The same bug class exists on iOS with a different root cause and a different fix.** iPhone+Watch
double-counting is handled by `HKStatisticsQuery` + source predicates, not automatically. Do not
assume `.cumulativeSum` is safe out of the box.

📋 A Samsung-specific filter drops meal-type aggregates by name in EN/NL/KO (30+ literal strings,
`:177-187`), and all-zero-macro records are dropped (`:201-203`).

### 2.3 Sync triggers — three, all through one `Mutex`

1. **App foreground** (`RecompTrackerApp.kt:41-51`) — `ProcessLifecycleOwner` → `syncStepsNow()`
   (no debounce) + `syncIfDue()` (15-min debounce) + `coachDigestCoordinator.runIfDue()`
2. **Manual** — Settings "Sync now"
3. **Periodic** — `HealthSyncWorker`, 4h

📋 Both historically-reported sync bugs are **fixed in the current tree**:
`STEPS_FINALIZE_DAYS = 2L` + `finalizeRecentSteps()` (`:116-119,151`) closes out yesterday after
midnight (P1-1), and weight is now read only over `[startOfDay(date), now]` (P1-2).

### 2.4 HealthKit differences — see [healthkit-notes.md](healthkit-notes.md)

Summarised in the mapping table below; the full analysis including type identifiers, the correlation
model, and the iOS 27 limited-history consent screen is in the dedicated document.

---

## 3. WorkManager — exactly two workers

📋 Both are `CoroutineWorker`s reaching DI via `applicationContext as? RecompTrackerApp`, and both
sit behind a `Noop*`/`WorkManager*` scheduler interface pair so the coordinators stay Context-free.
**That seam is what makes the iOS port slot in cleanly.**

| | `HealthSyncWorker` | `CoachDigestWorker` |
|---|---|---|
| File | `data/health/HealthSyncWorker.kt` | `data/coach/CoachDigestWorker.kt` |
| Unique name | `health_connect_periodic_sync` | `coach_digest_periodic` |
| Interval | 4 h | 24 h |
| Constraints | **none** | **none, deliberately** — KDoc `:17`: pure CPU/DB, no cloud call |
| Policy | `KEEP` | `KEEP` |
| On failure | `Result.retry()` | **`Result.success()`** — `:27-29`: a deterministic failure would fail identically on backoff and burn wakeups |

No `OneTimeWorkRequest` anywhere. Neither sets initial delay, backoff, tags, or input data.

**iOS: do not port either as a `BGTask`.** See the mapping table rows 16–18 and
[the roadmap §5.4](../00-feasibility-and-roadmap.md).

---

## 4. Notifications

📋 Two channels (`data/coach/AndroidCoachNotifier.kt:34-49`): `coaching` (IMPORTANCE_LOW) and
`weekly_check_in` (IMPORTANCE_DEFAULT). Notification id = `4200 + ordinal` (`:97-98`) so a new push
of the same kind **replaces** the previous one.

Tap → `PendingIntent` into `MainActivity` with `DEEP_LINK_EXTRA = "coach_push_action"` carrying a
`CoachActionType` name. ✅ Cold start reads it only when `savedInstanceState == null`
(`MainActivity.kt:54-57`); warm taps via `onNewIntent`. Routing at `ui/RecompApp.kt:166`, gated on
`onboardingComplete` **and** `navController.currentBackStackEntryFlow.first()` — the P0-3 fix.

📋 `POST_NOTIFICATIONS` is requested **deferred** — only once AI insights are enabled
(`MainActivity.kt:105-117`).

### 4.1 `RateLimiter` — pure Kotlin, the highest-fidelity port in the app

`domain/coach/RateLimiter.kt`. Five caps in strict precedence (`decide()`, `:37-79`):

1. **Quiet hours** — default 22:00–07:00, wrapping (`:118-133`; start inclusive, end exclusive)
2. **Tier gate** — only `SignalTier.P0` or the weekly check-in
3. **One push/day AND never consecutive days**
4. **Celebration cap** — ≤1 ambient celebration per rolling 7 days (weekly check-in exempt)
5. **Weekly cap** — ≤2 per rolling 7 days

Zero platform dependencies, fully injected `now` + `recentPushes`, returns a typed
`PushDecision(allowed, PushRejectionReason)` so the cause is assertable. **Ports verbatim to Swift.**

⚠️ iOS has **no quiet-hours API**. Implement exactly as on Android — compute the next allowed fire
time and never schedule inside the window. Do **not** reach for `.timeSensitive` interruption level;
Apple reserves it for account security and delivery-type alerts, and it breaks through Focus.

---

## 5. Camera + barcode

📋 `ui/scanner/BarcodeScannerScreen.kt` (417 LOC). CameraX `ProcessCameraProvider.awaitInstance` +
`Preview` + `ImageAnalysis(STRATEGY_KEEP_ONLY_LATEST)` on a single-thread executor, bound to
lifecycle with `DEFAULT_BACK_CAMERA`. ML Kit `BarcodeScanning.getClient()` with **no
`BarcodeScannerOptions`** — so **all 13 formats are enabled**. Careful `AtomicBoolean` +
`providerHolder` teardown (`:353-364`).

✅ This screen contains the app's **only** `AndroidView` (`:395`). Together with one `LocalView` in
`DragHaptics.kt`, that is the entire View-interop debt in 35,322 lines of UI.

⚠️ **iOS requires enumerating symbologies explicitly** — the "all formats by default" behaviour does
not carry over.

**Open Food Facts** (`data/remote/OpenFoodFactsApi.kt`) uses **raw `HttpURLConnection`**, not OkHttp:
8s/8s timeouts, custom `User-Agent`, every exception swallowed to `null`.
⚠️ `countries_tags=en:netherlands` is **hardcoded** in the search endpoint — a locale assumption
worth revisiting for the port.

---

## 6. Sharing and file I/O

📋 **Export:** `RoutineShareRepository.kt:36-61` writes to `cacheDir/shared_routines/<sanitised>.rtroutine`
and returns a `FileProvider` URI (authority `${packageName}.fileprovider`, `res/xml/file_paths.xml`
declares one `<cache-path>`). Sanitiser: `[^A-Za-z0-9._-]+` → `_`, 40-char cap (`:92-93`).
`ui/share/RoutineShareLauncher.kt:12-21` sends `ACTION_SEND` + `FLAG_GRANT_READ_URI_PERMISSION`
through a chooser.

📋 **Import:** `MainActivity.readShareUri()` (`:102-103`) → `RoutineShareInbox`, an
`AtomicReference<Uri?>` with `offer`/`consume` (`getAndSet(null)`) so a rotation can't re-import the
same file. **Port that one-shot guard** — iOS has the same cold-launch race.

📋 **SAF — 6 launchers:** export/import backup, export/import personal foods
(`DataBackupScreen.kt:83-92`), Samsung CSV/ZIP and NEVO catalog (`IntegrationsScreen.kt:84,87`),
plus `PickVisualMedia` for the profile photo (`ProfileScreen.kt:105`).

⚠️ **The profile photo does not port.** It takes a **persistable URI permission** (`:109-113`) and
stores the URI string in DataStore. iOS has no persistable-URI concept — `PhotosPicker` returns a
non-durable URL. You must **copy the bytes into the app container** and store a relative path.

---

## 7. Other platform APIs

| Area | Android | Notes |
|---|---|---|
| **Splash** | `installSplashScreen()` + `setKeepOnScreenCondition { !dbReady }` (`MainActivity.kt:42-44`) | 📋 Known bug: post-splash theme hardcodes light system bars, so dark-mode cold starts flash light bars until a `SideEffect` corrects them (`:70-75`); no `values-night/themes.xml` |
| **Foreground hook** | `ProcessLifecycleOwner` `onStart` (`RecompTrackerApp.kt:41-51`) | iOS fires `scenePhase == .active` far more often (control centre, notification shade) — **keep the 15-min debounce** |
| **Haptics** | Compose `LocalHapticFeedback` (CoachScreen, 6 sites) + `DragHaptics.kt` via `LocalView` for `CLOCK_TICK`/`CONTEXT_CLICK` | The `LocalView` escape hatch exists only because Compose exposes too few constants. Deletes on iOS |
| **Reduced motion** | `Settings.Global.ANIMATOR_DURATION_SCALE` (`AnimationGate.kt:17-27`), duplicated raw in `AiEdgeGlow.kt:42` and `PrBanner.kt:58` | iOS `@Environment(\.accessibilityReduceMotion)` is **reactive**; the Android version is `remember`ed once and never re-read |
| **Motion sensor** | `TYPE_ROTATION_VECTOR` for glass-orb parallax (`GlassOrbBackground.kt:70-94`), `TILT_SCALE 0.28f` clamped ±0.09, 0.004f delta threshold, register/unregister on RESUME/PAUSE | ⚠️ Not reduced-motion gated today. Core Motion axis conventions differ — retune empirically |
| **Canvas graphics** | `SweepGradient` + `BlurMaskFilter` (`AiEdgeGlow.kt`), `Region`/`RectF` hit-testing (`BodyMap.kt`), Kyant `backdrop` | The whole Kyant stack exists because Android has no OS glass material |
| **Baseline profiles** | `:macrobenchmark` module + `BaselineProfileRule` + `profileinstaller` | **Delete.** Swift is AOT; there is no ART JIT layer |
| **R8 keep rules** | Room, serialization, OkHttp, ML Kit, Health Connect | **Delete.** Swift `Codable` is compile-time synthesised |

---

## 8. Networking

📋 **Three distinct HTTP stacks** — collapse to one `URLSession` on iOS.

| Client | Used by | Config |
|---|---|---|
| OkHttp | `OpenAiCompatClient` | 15s connect / 60s read, **no retries**, one pooled instance |
| OkHttp (separate) | `TavilyWebSearchProvider` | 10s / 15s |
| `HttpURLConnection` | `OpenFoodFactsApi` | 8s / 8s |

**Endpoints:** LLM at `{userBaseUrl}/chat/completions` with `Authorization: Bearer`;
Tavily at `api.tavily.com/search` with the **key in the JSON body**; Open Food Facts unauthenticated.

✅ **Key rotation (P1-5 fix):** `CloudConfig.apiKey` is a `() -> String` **provider**, not a captured
string, so replacing the stored key takes effect on the next request and the plaintext never lives
in a long-lived object or its `toString`. **Preserve this on iOS** (`@Sendable () -> String` reading
Keychain).

📋 **SSE is hand-rolled** (`OpenAiCompatClient.kt:45-74`): `while (!source.exhausted())
{ source.readUtf8Line() }` over an okio `BufferedSource`, `flowOn(Dispatchers.IO)`. ~20 lines, no
event-type handling, no reconnect. **`URLSession.bytes(for:).lines` is cleaner.**

⚠️ **No `networkSecurityConfig`, no cert pinning, no cleartext allowance.** iOS ATS is *stricter* —
anyone self-hosting an OpenAI-compatible endpoint over HTTP is blocked without an
`NSAppTransportSecurity` exception. **Decide this explicitly before shipping.**

---

## 9. Build and release

📋 **Signing:** one committed `"shared"` keystore used by debug, release, **and** benchmark, so the
signature is stable and installs never force an uninstall (which would wipe data).
**On iOS this motivation does not exist** — app-container data survives updates regardless of signing
identity. The **bundle identifier** becomes the thing you must never change.

📋 **`versionCode`** = `git rev-list --count HEAD` via CI env. Same trick works for `CFBundleVersion`.

📋 **Three workflows:** `pr.yml` (unit tests + lint on PR and pushes to develop/main),
`distribute.yml` (push to main → Firebase App Distribution, group `testers`),
`distribute-dev.yml` (manual dispatch, group `developers`). **The distributed artifact is the debug
APK** — hence debug using the shared key.

🌐 **iOS:** `macos-latest` + `xcodebuild test` / `archive`, TestFlight in place of Firebase.
Two differences that matter: macOS runners cost **~10× Ubuntu minutes**, and TestFlight **external**
testing requires Beta App Review (Firebase has no review step). TestFlight **internal** (≤100 users,
no review) is the closest match to the current `testers`/`developers` groups.

---

## 10. The mapping table

| # | Android | Evidence | iOS equivalent | Risk |
|---|---|---|---|---|
| 1 | `uses-permission CAMERA` | Manifest:3 | `NSCameraUsageDescription` + `AVCaptureDevice.requestAccess` | L |
| 2 | `INTERNET` | Manifest:4 | — implicit | + |
| 3 | `POST_NOTIFICATIONS` | Manifest:5; MainActivity:105-117 | `UNUserNotificationCenter.requestAuthorization` | L |
| 4 | 4 × `health.READ_*` | Manifest:7-10 | HealthKit entitlement + `NSHealthShareUsageDescription` + `requestAuthorization(toShare:read:)` | M |
| 5 | `READ_HEALTH_DATA_HISTORY` + feature gate | Manifest:11; HCRepo:43-46,67-70 | **none needed** — read auth is time-unbounded (≤ iOS 26) | + |
| 6 | `<queries>` healthdata package | Manifest:13-15 | none | + |
| 7 | `getSdkStatus()` tri-state + Play Store fallback | HCRepo:48-54; IntegrationsScreen:148-156 | `HKHealthStore.isHealthDataAvailable()` (Bool) | + |
| 8 | `createRequestPermissionResultContract()` | HCRepo:56-57 | `requestAuthorization` (async, no Activity contract) | L |
| 9 | **`getGrantedPermissions()` → `hasPermissions()`** | HCRepo:59-74; HealthSyncCoordinator:80,97,129 | **No equivalent.** `authorizationStatus(for:)` reports write only | **H** |
| 10 | `StepsRecord.COUNT_TOTAL` aggregate (cross-origin dedup) | HCRepo:146-154 | `HKStatisticsQuery(.stepCount, .cumulativeSum)` — **different dedup semantics** | **H** |
| 11 | `aggregateGroupByPeriod(Period.ofDays(1))` | HCRepo:122-137 | `HKStatisticsCollectionQuery(anchorDate:intervalComponents:)` | L |
| 12 | `WeightRecord` → `inKilograms` | HCRepo:156-161 | `HKQuantityType(.bodyMass)`, sorted desc, limit 1 | L |
| 13 | `SleepSessionRecord` (a session) | HCRepo:163-169 | `HKCategoryType(.sleepAnalysis)` — **stage samples, no session; no statistics query for category types** | **H** |
| 14 | `NutritionRecord` (one record, all macros) + `pageToken` | HCRepo:92-112,189-204 | `HKCorrelationType(.food)` + N `HKQuantityType`s + `HKMetadataKeyFoodType`; `HKAnchoredObjectQuery` | **H** |
| 15 | Samsung meal-type filter (EN/NL/KO) | HCRepo:177-187 | rewrite for Apple Health / MFP sources | M |
| 16 | `HealthSyncWorker` 4h periodic | HealthSyncWorker:32-48 | **Replace with `HKObserverQuery` + `enableBackgroundDelivery`** (needs its own entitlement) | **H** design change |
| 17 | `CoachDigestWorker` 24h | CoachDigestWorker:37-55 | `BGProcessingTaskRequest` + keep foreground `runIfDue` as primary | **H** |
| 18 | `ExistingPeriodicWorkPolicy.KEEP` | both workers | none — `submit` replaces by identifier; guard duplicates yourself | M |
| 19 | `ProcessLifecycleOwner` onStart | RecompTrackerApp:41-51 | `@Environment(\.scenePhase)` / `didBecomeActiveNotification` | L |
| 20 | 2 × `NotificationChannel` (LOW/DEFAULT) | AndroidCoachNotifier:34-49 | `UNNotificationCategory` + `interruptionLevel` (`.passive`/`.active`) — **no per-channel user toggle** | M |
| 21 | `NotificationCompat` + `BigTextStyle` | AndroidCoachNotifier:69-80 | `UNMutableNotificationContent` | L |
| 22 | `PendingIntent` + extra | AndroidCoachNotifier:58-67; MainActivity:95-99 | `content.userInfo` + `UNUserNotificationCenterDelegate` | L |
| 23 | `notify(4200+ordinal)` replace-by-id | AndroidCoachNotifier:97-98 | `UNNotificationRequest(identifier:)` | L |
| 24 | `areNotificationsEnabled()` | AndroidCoachNotifier:54 | `await notificationSettings().authorizationStatus` | L |
| 25 | **`RateLimiter` + `QuietHours`** | RateLimiter.kt:37-79,118-133 | **pure Swift port, verbatim** | L + |
| 26 | `CoachPushEmitter` gates + ledger | CoachPushEmitter:51-85 | pure Swift + JSON ledger | L |
| 27 | CameraX `bindToLifecycle` + `PreviewView` | BarcodeScannerScreen:366-398 | `DataScannerViewController` (VisionKit) **or** `AVCaptureSession` in `UIViewRepresentable` | M / + |
| 28 | `STRATEGY_KEEP_ONLY_LATEST` | BarcodeScannerScreen:374-384 | `AVCaptureMetadataOutput` serial queue — no backpressure concept | L |
| 29 | ML Kit **all 13 formats by default** | BarcodeScannerScreen:346 | **must enumerate symbologies explicitly** | M |
| 30 | `InputImage.fromMediaImage(_, rotationDegrees)` | BarcodeScannerScreen:411 | `AVCaptureConnection.videoRotationAngle` | + |
| 31 | OFF via `HttpURLConnection`, NL filter | OpenFoodFactsApi:12-52 | `URLSession`; revisit `countries_tags` | L |
| 32 | `FileProvider` + `file_paths.xml` | Manifest:82-90; RoutineShareRepository:57-60 | plain `temporaryDirectory` file URL — **no provider needed** | + |
| 33 | `ACTION_SEND` + chooser | RoutineShareLauncher:12-21 | `ShareLink` / `UIActivityViewController` | L |
| 34 | Dual `ACTION_VIEW` filters for `.rtroutine` | Manifest:48-65 | `UTExportedTypeDeclarations` + `CFBundleDocumentTypes` + `.onOpenURL` | M, **+ reliability** |
| 35 | `RoutineShareInbox` one-shot `AtomicReference` | RoutineShareInbox.kt:11-17 | Swift `actor`, same `getAndSet(nil)` | L |
| 36 | `savedInstanceState == null` launch guard | MainActivity:54-57,88-93 | `scene(_:openURLContexts:)` — no rotation/recreate hazard | + |
| 37 | `contentResolver.open*Stream` | SettingsViewModel (6 sites) | `Data(contentsOf:)` + **`startAccessingSecurityScopedResource()`** | M |
| 38 | `CreateDocument("application/json")` × 2 | DataBackupScreen:83,89 | `.fileExporter` | L |
| 39 | `OpenDocument()` × 4 | DataBackupScreen:86,92; IntegrationsScreen:84,87 | `.fileImporter` + security-scoped access | M |
| 40 | **`PickVisualMedia` + `takePersistableUriPermission`** | ProfileScreen:105-113 | `PhotosPicker` → **copy bytes into the container**; no persistable-URI concept | **H** |
| 41 | `ZipInputStream` for Samsung export | SettingsViewModel:507-530 | no system zip reader — ZIPFoundation or cut (v1.1 anyway) | M |
| 42 | `EncryptedSharedPreferences` + recovery | SecureKeyStore:72-83,93-107 | Keychain + `…ThisDeviceOnly` — **recovery path deletable** | L + |
| 43 | Backup extraction-rule exclusions | res/xml/*; Manifest:20-21 | `isExcludedFromBackup` / Keychain accessibility class | L + |
| 44 | `setKeepOnScreenCondition { !dbReady }` | MainActivity:42-44 | `UILaunchScreen` — **no keep-on-screen hook**; render your own loading view | M |
| 45 | Adaptive icon (fg/bg, 5 densities) | mipmap-anydpi-v26 | single 1024² in `AppIcon.appiconset` | L |
| 46 | `LocalHapticFeedback` + `LocalView` constants | CoachScreen (6); DragHaptics:15-21 | `UIImpactFeedbackGenerator` / `UISelectionFeedbackGenerator` / `.sensoryFeedback` — call `.prepare()` | L |
| 47 | `ANIMATOR_DURATION_SCALE` gate (3 sites) | AnimationGate:17-27 | `@Environment(\.accessibilityReduceMotion)` | + |
| 48 | `TYPE_ROTATION_VECTOR` parallax | GlassOrbBackground:70-94 | `CMMotionManager` `attitude.roll/.pitch`; retune constants | M |
| 49 | Kyant `backdrop` + `SweepGradient`/`BlurMaskFilter` | AiEdgeGlow.kt; LiquidComponents.kt | `.glassEffect()` / `Material`, `AngularGradient`, `.blur()` | + |
| 50 | `Region`/`RectF` hit-testing | BodyMap.kt | `Path.contains(_:)` / `.contentShape(Path)` | L + |
| 51 | `assets.open()` × 3 (1.1 MB JSON) | AppContainer:265,280; MuscleArt:41 | `Bundle.main.url(forResource:)` | + |
| 52 | 10 × DataStore, `Flow` per key | see data-model.md §3 | `actor`-backed `Codable` JSON stores + `AsyncStream` | M |
| 53 | Room v15, 19 tables, 14 migrations | RecompDatabase:44-69,358-366 | **GRDB** (`DatabaseMigrator` + `ValueObservation`) | M (**H** if SwiftData) |
| 54 | 3 HTTP stacks | see §8 | one `URLSession` | + |
| 55 | Hand-rolled SSE over `BufferedSource` | OpenAiCompatClient:45-74 | `for try await line in bytes(for:).0.lines` | + |
| 56 | `() -> String` key provider | OpenAiCompatClient:22-26 | `@Sendable () -> String` reading Keychain | L |
| 57 | Tavily key in JSON body | TavilyWebSearchProvider:38 | identical | L |
| 58 | No network-security-config | (absence) | **ATS stricter** — user-entered `http://` blocked | M |
| 59 | R8 keep rules | proguard-rules.pro | none — WMO + `-dead_strip` | + |
| 60 | `:macrobenchmark` + baseline profiles | macrobenchmark/** | **delete** | + |
| 61 | Shared committed keystore | build.gradle.kts:13-21,51-82 | Apple certs; **bundle ID** is the invariant | L + |
| 62 | `versionCode` from git count | build.gradle.kts:34 | `CFBundleVersion` via script | L |
| 63 | Firebase App Distribution | distribute*.yml | TestFlight internal / external (+ Beta App Review) | M |
| 64 | GitHub Actions ubuntu + JDK 17 | 3 workflows | `macos-latest` + `xcodebuild` — **~10× runner cost** | M |
| 65 | `force("androidx.concurrent:…")` CameraX workaround | build.gradle.kts:40-45 | n/a | + |
| 66 | Robolectric for migration tests | build.gradle.kts:84-90 | XCTest against a real simulator SQLite file | + |
| 67 | ~1300 JVM unit tests | app/src/test/** | Swift Testing — **domain is the highest-value target** | L |

---

## 11. Ranked risks

1. **#9 — HealthKit read-permission introspection.** Gates three sync entry points and the whole
   Integrations UI. Redesign required *before* any health code is ported.
2. **#13, #14 — sleep and nutrition data shapes.** Rewrites, not translations. The 365-day import may
   recover materially less data on iOS.
3. **#16, #17 — background execution.** WorkManager guarantees; `BGAppRefreshTask` does not.
4. **#10 — steps cross-source dedup.** Same bug class, different fix.
5. **#40 — profile photo persistable URI.** No iOS counterpart; must become copy-into-container.
6. **#53 — persistence choice.** 14 explicit migrations + id-for-id restore strongly favour GRDB.
7. **#58 — ATS vs a user-supplied base URL.** Decide the self-hosted story before shipping.

## 12. Net deletions

Things that evaporate rather than port: the `<queries>` block and Play Store fallback,
`READ_HEALTH_DATA_HISTORY` + its feature check, FileProvider + `file_paths.xml`, the
EncryptedSharedPreferences corrupt-keyset recovery, all R8 keep rules, the entire `:macrobenchmark`
module, the shared-keystore scheme, the Kyant backdrop glass stack, the three-HTTP-stack split, the
`LocalView` haptics escape hatch, and the dual `.rtroutine` intent-filter hack.
