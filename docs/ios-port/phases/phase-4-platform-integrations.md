# Phase 4 — Platform integrations → first TestFlight

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans`. Read `docs/ios-port/STATUS.md` and
> `docs/ios-port/decisions.md` first.

**Goal:** Give the app the four things it can only get from the OS — Health data, the camera, files,
and notifications — and put the result on a physical device through TestFlight.

**Architecture:** Three branches, in order. Each ends with something the owner can look at; the last
ends with a build on a phone. No new persistence: the database, all ten preference stores, the backup
engine, and the `.rtroutine` codec have existed since Phase 1b. What is new is a `Health/` folder, a
`Scanner/` folder, a `Coach/` spine, and three screens.

**Tech Stack:** HealthKit, VisionKit (`DataScannerViewController`), `UserNotifications`,
`BGTaskScheduler`, `URLSession`, SwiftUI on iOS 26, GRDB, `:shared` for every engine.

---

## 🔴 Three findings that shape this phase

### 1. Two of the roadmap's five Phase 4 bullets have already evaporated

The roadmap ([§Phase 4](../00-feasibility-and-roadmap.md)) lists HealthKit, the scanner,
*"notifications plus the `RateLimiter`/`QuietHours` port"*, background work, and *"share/import of
`.rtroutine` via a declared UTI"*. Two of those are no longer work:

**The `RateLimiter` port is done — Phase 0 did it without anyone noticing.** It was described as
"the highest-fidelity port in the app — pure logic, no platform deps", which is exactly why Phase 0
swept it into `:shared` along with the rest of `domain/coach`. Already exported and callable from
Swift today: `RateLimiter`, `QuietHours`, `PushCandidate`/`PushEvent`/`PushDecision`,
`CoachSignalEngine`, `SignalSelector`, all 18 detectors, and the four serializers.
**Scope `:shared` before writing a line of push logic** — this is the third phase running where that
rule has paid (3b found 41 of 41 symbols already exported).

**`.rtroutine` share/import moves to v1.1, with Train.** There is no routine UI to import *into*.
Shipping the file handler now means a `.rtroutine` in Mail opens the app to a screen that does not
exist — the "record with no writer" shape 3d caught, in its most user-visible form. The UTI is
already declared, which is the only part that had to happen early. See **D51**.

### 2. Notifications have no producer until the spine lands — and the spine is not AI

The app has exactly one notification producer: `AndroidCoachNotifier`, fed by
`CoachDigestCoordinator` → `SignalSelector` → `CoachPushEmitter`. Nothing else in 54,000 lines posts
a notification. So "add notifications" in Phase 4 means one of three things, and the reflex answer is
the wrong one:

| Option | Verdict |
|---|---|
| Ship the permission + delivery layer now, wire it in Phase 5 | 🔴 **No.** A notification system that cannot fire is `usage_events` again, and TestFlight's whole purpose is exercising what the simulator can't |
| Invent an iOS-only notification Android doesn't have | No. Divergence for its own sake |
| **Ship the deterministic spine with it** | ✅ **Yes** — see below |

**The spine is not AI work.** `ai-coach.md` states it outright: *"Deterministic pipeline, no LLM
required (phrasing is optional decoration)."* `CoachDigestCoordinator`'s own KDoc: *"The engine
decides whether to speak; the LLM never does. `run` is pure CPU/DB work."* It needs no API key, so
it works in a TestFlight build the owner has not configured.

And most of it already exists on iOS:

| Piece | Where it is |
|---|---|
| `RateLimiter` + `QuietHours` + engine + selector + 18 detectors | ✅ `:shared`, exported |
| Inbox / journey / memory / experiment / **push-history** / **notification-prefs** stores | ✅ Swift, Phase 1b (`CoachStores.swift`, 606 LOC) |
| `StepsReconciliation`, `buildStreaks`, `TargetResolution` | ✅ Swift, Phases 2–3 |
| `CoachContextBuilder` (79) + `CoachContextAssembler` (358) | ⬜ **the real work** |
| `CoachDigestCoordinator` + `CoachPushEmitter` (91) + notifier | ⬜ small, and pure |

So the genuinely new code is one 358-line pure assembler and its I/O front. That is a task, not a
phase. **Phase 5 keeps the LLM half** — phrasing, insight cards, chat, briefing (**D50**).

⚠️ One consequence to state up front: with Train deferred (**D4**), `completedSessions` is always
empty, so the training detectors never fire on iOS v1. That is correct, not a bug — but assert it,
or a future reader will "fix" it.

### 3. HealthKit's permission model breaks the Integrations screen before any health code is written

Ranked risk #1 in [platform-api-map.md](../reference/platform-api-map.md), and it is a design
problem, not a translation problem. Apple, verbatim: *"your app doesn't know whether someone granted
or denied permission to read data from HealthKit."*

`hasPermissions()` gates **three** sync entry points (`HealthSyncCoordinator.kt:80,97,129`) and the
entire Integrations screen state derives from it. There is no counterpart —
`authorizationStatus(for:)` reports *write* status only, and re-requesting an already-decided type is
a silent no-op. **Design the replacement first** (**D45**), because every task in 4a consumes it.

---

## The three blocks

| Branch | What | Why in this order |
|---|---|---|
| **4a** `phase-4a-healthkit` | Permission model, reader, sync coordinator, Integrations screen, 365-day import, background delivery | The highest-risk area in the port. It also has the longest tail of device-only verification, so it should be on a phone as early as possible |
| **4b** `phase-4b-scanner-and-backup` | Barcode scanner, Open Food Facts, Data Backup screen | Self-contained, no HealthKit entitlement, and it closes **D21** and the last file-format surface. Can be built while enrolment clears |
| **4c** `phase-4c-notifications-and-testflight` | The coach spine, notifications, `BGTaskScheduler`, archive → TestFlight | Everything that must be true before a build leaves this machine |

⚠️ **If enrolment has not cleared, start with 4b.** It is the one block that needs nothing from Apple
beyond a device to run on.

## What is in, and what is cut

| Surface | Android | Ships as |
|---|---|---|
| Integrations | `IntegrationsScreen.kt` (240) | **Health half only** — the CSV/NEVO half is v1.1 (**D4**, **D20**) |
| Barcode Scanner + Product Found | `BarcodeScannerScreen.kt` (417) | Complete |
| Data Backup | `DataBackupScreen.kt` (350) | Complete — the engine already exists |
| Notifications | `AndroidCoachNotifier.kt` (98) + emitter (91) | Complete, with a real producer |
| Proactive spine | `data/coach/*` | **Deterministic half only** — phrasing is Phase 5 |
| `.rtroutine` share/import | `RoutineShareLauncher.kt` etc. | ❌ **v1.1, with Train** (**D51**) |

## Decisions to take

Append to `decisions.md` as each is confirmed in code.

**D45 · HealthKit availability is inferred from data, never from permission introspection.** iOS
cannot tell you a read was denied, so `hasPermissions()` has no port. Replace it with two facts the
app *can* know: a persisted **"we asked"** flag, and whether the last read returned anything. The
Integrations screen says "we see no data — check Settings › Privacy & Security › Health" instead of
"you denied steps, tap to fix". ⚠️ Do not fake it by writing a probe sample; the app is read-only
against Health (`NSHealthUpdateUsageDescription` is not requested) and writing to prove a read works
would be both a lie and a review risk.

**D46 · Steps stay an aggregate, and the source set is a deliberate decision.** Android's KDoc
records the war story — summing raw records showed **17k steps on a 4k day** — and Health Connect's
aggregate deduplicates across origins for free. `HKStatisticsQuery(.cumulativeSum)` does *not* give
that for free across iPhone + Watch. 🔴 **Test with a paired Watch before believing the number.** The
apply path keeps `StepsReconciliation` (already ported), so a manual entry is never clobbered.

**D47 · Sleep is grouped into nights in our own code.** `SleepSessionRecord` is one object with a
start and end; `HKCategoryType(.sleepAnalysis)` is a flat stream of stage samples with **no
statistics query available** (they exist only for quantity types). Group into 18:00→18:00 windows,
merge gaps under ~30–60 min, dedupe across sources, sum in Swift. Android's one-liner
(`Duration.between(latest.startTime, latest.endTime)`) does not port. This is a genuine capability
regression and should be stated in the ledger as one.

**D48 · The 365-day import is honest about what it could not reach.** Two independent reasons it
returns less than Android: HealthKit needs `HKCorrelation(.food)` to reconstruct a named meal and
many apps write loose samples without one; and **iOS 27 added a limited-history consent screen**, so
the user may have granted a recent window rather than everything. Query the boundary with
`getEarliestAuthorizedSampleDate(for:)` (availability-gated — iOS 27+), clamp the query start to it,
and treat data before it as **unknown, not absent**. The import UI needs a "we could reach N days"
state. ⚠️ Re-verify the API at GM; it was beta at capture.

**D49 · Background is `HKObserverQuery` first, `BGTask` second.** The 4-hour `HealthSyncWorker`
becomes `HKObserverQuery` + `enableBackgroundDelivery(.hourly)` — the most reliable background wake
iOS offers, and strictly better than what it replaces. The 24-hour `CoachDigestWorker` becomes a
`BGProcessingTaskRequest`, but **the foreground `runIfDue` stays the primary trigger** because
`BGProcessingTask` carries no delivery guarantee and WorkManager did. ⚠️ `defer { handler() }` on
every path out of the observer, or future deliveries stop. ⚠️ An anchored query does **not** survive
backgrounding — the observer runs a *one-shot* anchored query and persists the anchor.

**D50 · The deterministic coach spine ships in Phase 4, not Phase 5.** It is the producer that makes
notifications honest, it needs no API key, and two-thirds of it already exists (see finding 2).
Phase 5 keeps everything that talks to a model.

**D51 · `.rtroutine` share and import follow Train to v1.1.** The UTI stays declared — it costs
nothing and reserving it early is the point. The handler does not ship until there is a screen behind
it.

**D52 · Open Food Facts drops the hardcoded Dutch filter.** Android pins
`countries_tags=en:netherlands` in the *search* endpoint (barcode lookup is already global). That is
an unexamined assumption about one user, and it contradicts **D28**, which put the app on the device
locale everywhere. Either use the device region or send no country filter; decide in code and say
which.

## 🔴 Blocked on the owner

**1. Apple Developer Program enrolment.** Gates TestFlight (all of 4c's tail) and the
background-delivery entitlement (4a's last task).

🔴 **Unverified and worth checking on day one: whether a free personal team can enable the HealthKit
capability at all.** If it can, 4a's first five tasks can run on a device before enrolment clears; if
it cannot, 4a is blocked and **4b should go first**. Check this before planning the week, not after.

**2. The bundle identifier**, needed before an App ID is registered and permanent afterwards. Still
`Epistles-of-Wisdom.RecompTracker`; `com.zack.recomptracker` matches the Android package and the
declared UTI.

**3. A physical device**, with the Health app populated. The simulator proves nothing here — no
meaningful Health data, no camera, no background delivery.

**4. The Android backup fixture** (outstanding since Phase 1b). 4b's Data Backup screen is the first
time the restore path is reachable through the UI, which makes it the natural moment to close this.

## 🔴 Screenshots

**Ask the owner before building any 🖼️ task**, named as they should be saved into the iOS repo's
`screenshots/`:

- `28-integrations-health-sync.jpg` — and the **connected** state if reachable
- `29-data-backup.jpg`
- `30-barcode-scanner.jpg`
- `31-product-found-sheet.jpg`

3c and 3d shipped ten surfaces blind and every one is still unverified. If a screenshot cannot be
produced, build from the Kotlin and **say so out loud** rather than letting it accumulate.

## Context you need

- **Read the Kotlin, not this brief.** 3a's plan was wrong about Android four times; every one was
  caught by an agent who opened the source.
- Android sources: `data/health/*` (5 files, 473), `ui/integrations/IntegrationsScreen.kt` (240) +
  `IntegrationsComponents.kt`, `ui/scanner/BarcodeScannerScreen.kt` (417),
  `data/remote/OpenFoodFactsApi.kt` (54), `ui/databackup/DataBackupScreen.kt` (350),
  `data/coach/{CoachContextBuilder,CoachContextAssembler,CoachDigestCoordinator,CoachPushEmitter,AndroidCoachNotifier}.kt`,
  `domain/foodimport/{FoodNameNormalizer,PersonalFoodMerger,HistoricalFoodImporter}.kt` (52 total).
- **Reference docs carry the analysis**: [healthkit-notes.md](../reference/healthkit-notes.md) is the
  single highest-value document in this phase — read it whole before task 1. Then
  [platform-api-map.md](../reference/platform-api-map.md) §4–§7 and its ranked risks.
- Conventions that bite: no `didSet` on `@Observable` (3a); `nonisolated` does not propagate into
  extensions; `.task(id:)` is `flatMapLatest`; every number through `AppNumber` (**D28**); every
  title through `.screenTitle(_:subtitle:)` (**D32**); one store per named file (**D37**); an unbuilt
  row is shown and disabled with its phase named (**D38**); a swallowed write error is wrong
  everywhere except telemetry (**D30**, **D40**).

---

# Block 4a — HealthKit

## Task 1: The permission model 🔴

**Files:** create `Health/HealthKitAuthorization.swift`, `RecompTrackerTests/HealthAuthTests.swift`

Two tiers, as Android does: the base three — `.stepCount`, `.bodyMass`, `.sleepAnalysis` — and
nutrition separately, requested only for the import.

🔴 **The nutrition tier is four types, not seven.**
[healthkit-notes.md §2.2](../reference/healthkit-notes.md) lists seven as "the set this app needs",
but `toFoodImportCandidate()` (`HealthConnectRepository.kt:189-204`) maps exactly four — energy,
protein, total carbohydrate, total fat. Fiber, sugar and sodium are read on Android only because
Health Connect hands over one whole `NutritionRecord`; on iOS each is a separate request.
⚠️ **Over-requesting is a documented review rejection cause**, so ask for
`.dietaryEnergyConsumed`, `.dietaryProtein`, `.dietaryCarbohydrates`, `.dietaryFatTotal` and stop.

Implement **D45**: a persisted "we asked" flag, `HKHealthStore.isHealthDataAvailable()` for hard
availability, and a derived state of *unknown / asked-and-flowing / asked-and-silent*. No call to
`authorizationStatus(for:)` for a read type — it answers a different question and using it here is
the bug this decision exists to prevent.

**Tests:** the state machine is pure and testable without a store — never-asked → unknown; asked with
data → connected; asked with no data and no error → silent, with the Settings-path message.

## Task 2: `HealthKitReader` behind a protocol

**Files:** create `Health/HealthDataSource.swift`, `Health/HealthKitReader.swift`, tests with a fake

Port Android's `HealthDataSource` seam verbatim in shape — `hasData`/`readToday(date:)`/
`readStepsHistory(days:)` — because it is *why* the coordinator is unit-testable, and it is the one
piece of Android's health layer that transfers unchanged. Its KDoc says as much.

- **Steps** (**D46**): `HKStatisticsQueryDescriptor(.cumulativeSum)` for today, a
  `HKStatisticsCollectionQueryDescriptor` with `DateComponents(day: 1)` for the history window.
- **Weight**: discrete type — ⚠️ `.cumulativeSum` returns `nil` on discrete types. Sample query
  sorted by `endDate` descending, `limit: 1`, over `[startOfDay(date), now]` only. Android narrowed
  this window deliberately (P1-2): a weekly smart-scale reading copied forward onto every day flattens
  the weight trend and silences the weigh-in reminder.
- **Sleep** (**D47**): the night-grouping algorithm, written here and tested exhaustively — it is the
  one piece of this task with no Android counterpart to check against.

**Tests:** run against a fake conforming to the protocol. The real reader is device-only, and a test
that needs a phone is not a test.

## Task 3: `HealthSyncCoordinator`

**Files:** create `Health/HealthSyncCoordinator.swift`, tests

A near-mechanical port of the Kotlin, which is unusually well-specified — read its KDoc line by line.
The `Mutex` becomes an `actor`, and every constant carries a reason:

- `syncToday()` — read, apply, then **`finalizeRecentSteps()`**, then stamp the last-sync time.
  🔴 The finalize call is the **P1-1** fix: without it yesterday's total freezes at its last
  pre-midnight value and the evening tail is lost forever.
- 🔴 **The preference fields keep their Health *Connect* names.** `healthConnectEnabled` and
  `healthConnectLastSyncEpochMs` already exist in `PlanPreferences` **and in the backup payload**, so
  they are a wire contract with Android, not a leftover. Renaming them to `healthKit*` is the tidy
  thing to do and it silently breaks backup round-trip in one direction. Leave them; say why in a
  comment.
- `STEPS_FINALIZE_DAYS = 2` — must be ≥ 2, for exactly that reason.
- `syncIfDue(minInterval: 15 min)` — debounced; `syncStepsNow()` — **no debounce**, because steps
  rise all day. ⚠️ `scenePhase == .active` fires far more often on iOS than
  `ProcessLifecycleOwner.onStart` does on Android (control centre, notification shade). **Keep the
  debounce**; it is load-bearing here in a way it was not there.
- Apply through the existing `StepsReconciliation` and mirror `applyHealthConnectSync` exactly:
  weight and sleep are `existing ?? incoming` — **a manual value always wins** — and only a changed
  row is written.
- The sync timestamp is a non-target edit, so it must **not** append a plan version (**D33**).
  `PlanRepository.save()` already gets this right; assert it, because it is one field away from
  turning the ledger into a changelog of background syncs.

**Tests:** the debounce gate (`isAutoSyncDue` is already pure on Android — port it as such); manual
steps survive a sync; yesterday is finalized after midnight; a sync writes no plan version.

## Task 4: The Integrations screen — 🖼️

**Files:** create `Features/Integrations/IntegrationsModel.swift`,
`Features/Integrations/IntegrationsScreen.swift`; wire `MoreDestination.integrations` (already
present, disabled, marked "Phase 4")

Health sync section: availability, connect, last-sync time, "Sync now", and the **D45** empty state.
⚠️ **The Food sources section is v1.1** — CSV and NEVO (**D4**, **D20**). Show it disabled with the
version named, per **D38**, rather than deleting it: a user who imported a NEVO catalogue on Android
and restores that backup on iOS will look for this.

## Task 5: The 365-day nutrition import

**Files:** create `Health/NutritionImporter.swift`, `Domain/PersonalFoodMerge.swift`, tests

Port the ~52 lines of `FoodNameNormalizer` + `PersonalFoodMerger` + `HistoricalFoodImporter` — they
stayed in `:app` because the *CSV parsers* beside them use `java.io`, not because they are
Android-coupled. `FoodImportCandidate` already exists in Swift (Phase 1b, `PersonalFoodsCodec.swift`).

Read per **D48**: `HKStatisticsCollectionQueryDescriptor` per nutrient over daily buckets, off the
main actor. ⚠️ A raw sample query over a year is 10k–100k samples across seven types and will spike
memory.

Two filters carry over from Android and both are load-bearing: drop records whose name is only a
**meal-type tag** (Samsung writes one aggregate per meal type in EN/NL/KO — 30+ strings at
`HealthConnectRepository.kt:177-187`; keep the list, it is data not code), and drop all-zero-macro
rows. ⚠️ The equivalent iOS sources are Apple Health and MyFitnessPal, not Samsung — **re-derive the
list rather than transcribing it**, and say in a comment that it was re-derived.

## Task 6: Background delivery 🔴 *(needs the entitlement)*

**Files:** modify `Health/HealthKitReader.swift`, `AppContainer.swift`, entitlements

`HKObserverQuery` + `enableBackgroundDelivery(for:frequency:)` per **D49**. Register once at launch.
⚠️ `defer { handler() }` on **every** path, including the error path. ⚠️ One-shot anchored query
inside the observer, anchor persisted (`NSKeyedArchiver`) — a long-running
`HKAnchoredObjectQueryDescriptor` is cancelled the moment the app backgrounds.

## Task 7: Device verification 🔴

Steps against the Health app's own number **with a Watch paired** (**D46**); a weigh-in appearing
after a sync; a night of sleep grouped correctly; the import's partial-result state; a background
wake with the app terminated. Then hand off for the owner's visual pass on Integrations.

---

# Block 4b — Scanner, Open Food Facts, and Data Backup

## Task 8: The Open Food Facts client

**Files:** create `Networking/OpenFoodFactsClient.swift`, tests

One `URLSession` (8 s timeouts, matching Android), the same `User-Agent`, decode with
`JSONDecoder`. ⚠️ Android swallows **every** exception to `null` — that is right for a
best-effort lookup and wrong for the UI: return a typed failure so the sheet can say "no network"
rather than "not found". They are different answers and the user acts differently on each.

Apply **D52** to the search endpoint.

## Task 9: The scanner — 🖼️

**Files:** create `Features/Scanner/BarcodeScannerScreen.swift`, `Features/Scanner/ScannerModel.swift`

`DataScannerViewController` (VisionKit) in a `UIViewControllerRepresentable`.
⚠️ **iOS requires enumerating symbologies explicitly** — ML Kit's "all 13 formats by default" does
not carry over. Food barcodes need at minimum `.ean8`, `.ean13`, `.upce`, `.code128`.
⚠️ `DataScannerViewController.isSupported` is false on the simulator *and* on devices without the
Neural Engine — have a real fallback path, not a crash.

One-shot guard on the first hit (Android's `AtomicBoolean`), `NSCameraUsageDescription`, and the
Product Found sheet: name, macros per 100 g, and the amount entry that already exists in the food
library's sheet family. Reuse it — a second amount editor in the same app is exactly what 3b's
consistency pass spent a phase undoing.

## Task 10: Put the camera button back

**Files:** modify `Features/FoodLibrary/*`

**D21** removed the camera button from the library's search field along with Open Food Facts. It
comes back here, with a lookup path for a scanned product that is not in the library.

## Task 11: The Data Backup screen — 🖼️

**Files:** create `Features/DataBackup/DataBackupScreen.swift`, `Features/DataBackup/BackupModel.swift`;
wire `MoreDestination.dataBackup`

🔴 **The engine already exists** — `BackupRepository.export`/`.restore` and `PersonalFoodsCodec`
landed in Phase 1b with 9 armed acceptance tests. This task is the screen and the file plumbing only:
`.fileExporter` / `.fileImporter`, and ⚠️ **`startAccessingSecurityScopedResource()` around every
read** — an imported URL is not readable without it, and the failure looks like a corrupt file.

⚠️ **Restore is destructive and irreversible.** Android's screen states it; the 3c reset flow already
has the confirmation pattern. This is also the P0 the July Android review found (restore wiping
training data) — read that finding before writing the confirm copy.

## Task 12: Close the fixture 🔴

With the screen live, an Android export can be imported through the real UI. Ask the owner for the
export (see *Blocked*), drop it in `Fixtures/android-backup-v2.json`, and unskip the nine tests. **The
claim "iOS reads Android's backup" has been unproven since Phase 1b** — this is the task that either
proves it or finds out otherwise.

---

# Block 4c — Notifications, the spine, and TestFlight

## Task 13: `CoachContextAssembler` 🔴

**Files:** create `Coach/CoachContextAssembler.swift`, `RecompTrackerTests/CoachContextTests.swift`

The 358-line pure derivation: a 28-day `CoachContext` from logs, meals, plan targets, streaks,
profile, and rebalance state. **Transcribe the Kotlin tests with it** — they are the specification,
and 3b's experience says transcribing them finds gaps in the original.

⚠️ `completedSessions` is always empty on iOS v1 (**D4**). Assert that the assembler tolerates it and
that no training detector fires, rather than leaving it to be discovered.

## Task 14: `CoachContextBuilder` and the digest

**Files:** create `Coach/CoachContextBuilder.swift`, `Coach/CoachDigestCoordinator.swift`, tests

The builder is I/O only: **suspend one-shot reads, never a live combine** — the Kotlin comment
explains why (a first-emit hazard and the streak recompute cost), and the same trap exists with
`ValueObservation`.

The coordinator runs the pipeline under a lock and stages exactly one winner, or silence.
⚠️ **Silence is a first-class output**, not a failure. Cache per calendar day (`CoachContextCache`),
invalidated on plan or profile edits.

⚠️ The AI-enabled gate: Android's coordinator takes an `aiEnabledFlow` and stays silent when it is
off. On iOS v1 there is no AI settings screen until Phase 5 — decide whether the gate defaults open
or closed and **state it**, because "the coach never speaks" and "the coach is off" look identical
from outside.

## Task 15: The push path

**Files:** create `Coach/CoachPushEmitter.swift`, `Coach/CoachNotifier.swift`,
`Coach/UserNotificationCenterNotifier.swift`, tests

`CoachPushEmitter` is 91 lines of pure gating and ports directly: preference gate → `RateLimiter`
(**from `:shared`**) → payload gate → show → **record only what actually went out**. That last
ordering is deliberate: a denied or failed post must not burn a weekly slot.

The notifier: `UNMutableNotificationContent`, `UNNotificationRequest(identifier:)` keyed per channel
so a new push replaces the old one (Android's `4200 + ordinal` trick), `.passive` for coaching and
`.active` for the weekly check-in. ⚠️ **Do not reach for `.timeSensitive`** — Apple reserves it for
account security and delivery alerts, and it breaks through Focus, which is the opposite of what a
quiet-hours-respecting coach is for.

⚠️ **iOS has no quiet-hours API.** `QuietHours` from `:shared` is the whole implementation: compute
the next allowed fire time and never schedule inside the window.

**Tests:** the five caps in precedence order (the Kotlin tests assert each rejection reason — port
them); a failed post records nothing.

## Task 16: Permission, preferences, and the tap

**Files:** modify `Features/Integrations/IntegrationsScreen.swift`, `Shell/`

Request notification authorization **deferred**, as Android does — not at launch. A first-run
permission prompt for a notification the app cannot yet send is the fastest way to a permanent no.

Surface the three preferences that already exist in `CoachNotificationPreferences` (ambient nudges,
weekly check-in, quiet hours). Android puts them on the AI & Coach screen, which is Phase 5 — a
**Notifications** section on Integrations is the honest v1 home, since that screen is already where
platform permissions live.

Tap routing: `content.userInfo` carries the `CoachActionType`, `UNUserNotificationCenterDelegate`
receives it. 🔴 **Android's P0-3 was exactly this deep link crashing on cold start** — the fix gated
navigation on onboarding being complete *and* the nav host being ready. iOS has the same race with a
different shape. Read that finding.

## Task 17: `BGTaskScheduler`

**Files:** modify `AppContainer.swift`, `RecompTrackerApp.swift`, Info.plist

A `BGProcessingTaskRequest` for the digest per **D49**, with the foreground `runIfDue` staying
primary. ⚠️ Register the identifier before `application(_:didFinishLaunchingWithOptions:)` returns or
the submit throws. ⚠️ Android's `CoachDigestWorker` returns `success` on failure deliberately — a
deterministic failure would fail identically on retry and burn wakeups. Keep that reasoning.

## Task 18: TestFlight 🔴

**Files:** entitlements, Info.plist, `AppIcon.appiconset`, `.github/workflows/ios.yml`

- Usage strings, written **specifically** — ⚠️ vague `NSHealthShareUsageDescription` copy is a
  documented rejection.
- HealthKit + background-delivery entitlements; camera; notifications.
- `CFBundleVersion` from `git rev-list --count HEAD`, the same trick Android uses.
- Archive, upload, **internal** testing (≤100 testers, no review — external needs Beta App Review).
- CI on `macos-latest`. ⚠️ ~10× the per-minute cost of the Ubuntu runners the Android side uses; run
  it on PR and on `main`, not on every push.

## Task 19: Verification and docs

- Full suite + **Release** build + the consistency greps.
- Update `decisions.md` (D45–D52), `parity-ledger.md` (three screens, nine platform rows, the spine),
  and `STATUS.md`.
- 🔴 **Re-check *Needs visual check* end to end** and close what this phase closed.

---

## What Phase 4 deliberately does NOT do

- **Writing to HealthKit.** The Android app writes nothing; §8 of the notes covers what it would take
  (`HKMetadataKeySyncIdentifier`, or every re-export duplicates), for whenever that comes up.
- **CSV / NEVO import** — v1.1 (**D4**, **D20**), even though Integrations is where it lives.
- **`.rtroutine` share and import** — v1.1 with Train (**D51**).
- **Anything that talks to a model** — phrasing, insight cards, chat, briefing. Phase 5.
- **iCloud sync of anything health-derived** — ⚠️ prohibited by review guideline **5.1.3(ii)**, and
  worth knowing before someone proposes it as a convenience.

## Rollback

Three branches off `main`, merged in order. 4a and 4c both change app-launch behaviour (an observer
registration and a `BGTask` registration respectively) — keep each in its own commit. 4b is purely
additive: reverting it leaves two More rows disabled, which is where they are today.
