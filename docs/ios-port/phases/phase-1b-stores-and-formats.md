# iOS Port Phase 1b — Stores, Secrets, Assets and File Formats

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the iOS persistence foundation — the 10 preference stores, the Keychain secrets,
the bundled assets and exercise-library seed, and the three file formats — ending with **a real
Android backup importing successfully on iOS**.

**Architecture:** Preference stores become actor-backed JSON files behind the same interfaces
Android already defines. **The persisted JSON formats are not reimplemented in Swift** — the five
Kotlin codecs move into `:shared` (decision D12) and iOS calls them, so the wire format is identical
by construction. `.rtroutine` needs nothing: it is already in `:shared`. Only the backup payload is
reimplemented in Swift, because it is a DTO over 19 Room entities and cannot leave `:app` — which is
exactly why round-tripping a *real* export is the acceptance test rather than a nicety.

**Tech Stack:** Swift 6.3.2, Xcode 26.5, iOS 26.0, GRDB 7.11.1, Swift Testing. Kotlin side:
Kotlin 2.2.21, kotlinx-serialization 1.9.0, kotlinx-datetime 0.8.0.

**Two repos.** Unlike 1a, this phase spans both:
- **Part A (Kotlin, `~/Desktop/Personal Dietitian`)** — move the codecs into `:shared`, rebuild the
  XCFramework. Tasks A1–A3.
- **Part B (Swift, `~/Desktop/RecompTracker-IOS`)** — everything else. Tasks B1–B11.

Part A must land and the framework must be resynced before Part B tasks B4 and B7 can compile.

---

## 🔴 Prerequisite: a real Android backup export

**Task B10 — the acceptance test — cannot run without one, and none exists.** Verified: there is no
committed backup JSON, golden file or `.rtroutine` anywhere in the Android repo, and nothing in the
iOS repo.

Export from **Settings → Data Backup → Export backup** on a populated install and place it at
`~/Desktop/RecompTracker-IOS/RecompTrackerTests/Fixtures/android-backup-v2.json`.

For the test to be worth anything the export should contain:
- meals spread across **several different slots** (this is what P0-2 broke — slot links)
- meals with `slotId = null` (coach-logged) **and** at least one planned meal
- a few `daily_logs` with a mix of populated and null metrics
- **at least one routine with sessions and sets** — the training tables are what backup v2 added,
  and they are restored id-for-id
- ideally a recipe, since recipes are the one table restored with *remapped* ids

A synthetic fixture I generate would only prove my Swift matches my Swift. Everything except B10 can
proceed without it.

---

## Context you need before starting

Read, in order:
1. `docs/ios-port/STATUS.md` — where the port is
2. `docs/ios-port/decisions.md` — **D3** (GRDB), **D6** (dates as strings), **D11** (two repos),
   **D12** (share the codecs), **D13** (this split) are binding
3. `docs/ios-port/reference/data-model.md` §3 (the 10 stores), §4 (secrets), §6 (file formats),
   §7 (assets)
4. `docs/ios-port/phases/phase-1a-database.md` — its **⚠️ Amendment** section lists Swift/Xcode
   gotchas that still apply

Phase 1a is complete: GRDB, the 19 tables, all 18 record types, the query layer, the seven
transaction bodies. **71 tests pass.**

### Swift/Xcode facts already established — do not rediscover

- Records use `Int64?` primary keys; `nil` means "assign me one".
- The persistence layer is **`nonisolated`** throughout. A same-module protocol refining
  `MutablePersistableRecord` on a MainActor-isolated type produces `error: circular reference` with
  no diagnostics. Keep new persistence types `nonisolated`.
- **`#expect` cannot appear inside a throwing closure.** Write
  `#expect(try db.read { … } == expected)`, not `try db.read { #expect(…) }`.
- Tests live **flat** in `RecompTrackerTests/`, not in subfolders. Fixtures go in
  `RecompTrackerTests/Fixtures/`.
- Buildable folders mean new Swift files need **no `project.pbxproj` edit**. Never edit it.
- Adding a resource *file* (the fixture, the bundled JSONs) to the app or test target **does** need
  care — see Task B6 Step 1.

---

## The shape of the problem

The audit found the persisted state is far less uniform than "10 JSON files" suggests. Three things
in particular will bite a naive port:

**1. Thirteen read-time transforms that are not stored data.** A store that "just reads the key"
silently loses them. The full list is in Task B2/B3, but the sharpest is
`UiPreferences.cloudModelId`: a stored `"openai/gpt-oss-20b:free"` reads back as
`"nvidia/nemotron-nano-9b-v2:free"`, the stored value is **never rewritten**, and
`cloudConfigPresent` deliberately reads the **raw** key bypassing the remap.

**2. Pruning is asymmetric, and the asymmetry is deliberate.**
- `coach_inbox.seen_ledger` prunes at 30 days **on write only** — reading a 90-day-old ledger
  returns all of it.
- `coach_push_history` prunes by **age on both read and write**, but by **count only on write**, and
  the read-time prune is **not written back**.

A port that "prunes on read for safety" changes cooldown behaviour.

**3. Two failure-tolerance doctrines, deliberately mixed.** Almost every codec returns a default on
malformed input. Exactly one throws — `UserProfilePreferencesStore`, and that is a **known bug**
(review P2-9): an unrecognised enum value throws *inside a flow's `map`*, killing every collector.
Port the fixed behaviour, not that one.

---

# PART A — Kotlin: move the codecs into `:shared`

## Task A1: Move the three platform-free codecs

**Files (Android repo):**
- Move: `app/src/main/java/.../data/coach/CoachJourneySerialization.kt` → `shared/src/commonMain/kotlin/.../domain/coach/`
- Move: `app/src/main/java/.../data/coach/CoachExperimentSerialization.kt` → `shared/src/commonMain/kotlin/.../domain/coach/`
- Move: `app/src/main/java/.../data/rebalance/RebalanceSerialization.kt` → `shared/src/commonMain/kotlin/.../domain/rebalance/`

All three have **zero** `android.*`, `androidx.*` and `java.*` imports — verified. They move with no
code change beyond package and visibility.

- [ ] **Step 1: Move with git, keeping history**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
git mv app/src/main/java/com/zack/recomptracker/data/coach/CoachJourneySerialization.kt \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/coach/CoachJourneySerialization.kt
git mv app/src/main/java/com/zack/recomptracker/data/coach/CoachExperimentSerialization.kt \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/coach/CoachExperimentSerialization.kt
git mv app/src/main/java/com/zack/recomptracker/data/rebalance/RebalanceSerialization.kt \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/rebalance/RebalanceSerialization.kt
```

- [ ] **Step 2: Change the package declarations and widen visibility**

Each file declares `package com.zack.recomptracker.data.coach` (or `.data.rebalance`). Change to
`com.zack.recomptracker.domain.coach` / `.domain.rebalance`, and add the matching imports to the
`:app` files that reference them — the compiler will list every one.

These objects are `internal`. `internal` is module-scoped, so once they are in `:shared` their
`:app` callers cannot see them. **Make the codec objects and the record types they define
(`FiredSignalRecord`, `WeeklyVerdictRecord`, `CoachExperiment`) `public`**, and leave a comment
saying why: iOS calls them across the framework boundary.

- [ ] **Step 3: Verify**

```bash
grep -rn "java\.time\|java\.util\|java\.io\|String\.format" shared/src/commonMain/ \
  | grep -v "^\s*\*" | grep -vE ":\s*(//|\*|/\*)"
./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test :app:testDebugUnitTest
```
Expected: grep silent; all suites green. Baseline is `:app` 1017, `:shared` JVM 400, iOS 11.

⚠️ Known flake, not your problem: `ai/harness/InsightHarnessTest` makes live OpenRouter calls when
`.env.test` is present and returns HTTP 429.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "refactor(ios): move the three platform-free codecs to :shared"
```

---

## Task A2: Port and move the two date-carrying codecs

`CoachInboxSerialization.kt` and `PushHistorySerialization.kt` each carry exactly one `java.time`
reference. Porting them **removes** an existing conversion boundary in `:app` rather than adding one.

- [ ] **Step 1: Port `CoachInboxSerialization` to kotlinx-datetime**

`java.time.LocalDate` appears in the signatures of `encodeLedger`, `decodeLedger`, `markSeen` and
`prune`, and behaviourally in `LocalDate.parse`, `date.toString()`, `minusDays(30)` and `isBefore`.

Apply: `import kotlinx.datetime.LocalDate`; `reference.minus(LEDGER_RETENTION_DAYS, DateTimeUnit.DAY)`
for the cutoff; `!it.isBefore(cutoff)` becomes `it >= cutoff` (kotlinx `LocalDate` is `Comparable`).

⚠️ **Preserve the retention semantics exactly**: 30 days, pruned **on write only**, via `markSeen`.
Do not add a read-time prune.

- [ ] **Step 2: Port `PushHistorySerialization`**

This file is already half-converted — `PushEvent.timestamp` is `kotlinx.datetime.LocalDateTime` and
`prune` exists only to bridge (`now.minusDays(14).toKotlinLocalDateTime()`). Change `prune`/
`appendPruned` to take `kotlinx.datetime.LocalDateTime` and both the `java.time` import and the
bridge call disappear.

⚠️ **Preserve the asymmetry**: prune by **age** on both read and write; by **count** (`MAX_EVENTS`)
on write only; the read-time prune is **not** written back.

- [ ] **Step 3: Move both and update their callers**

```bash
git mv app/src/main/java/com/zack/recomptracker/data/coach/CoachInboxSerialization.kt \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/coach/CoachInboxSerialization.kt
git mv app/src/main/java/com/zack/recomptracker/data/coach/PushHistorySerialization.kt \
       shared/src/commonMain/kotlin/com/zack/recomptracker/domain/coach/PushHistorySerialization.kt
```

`CoachInboxRepository` and `PushHistoryStore` in `:app` take `java.time` values from `DateProvider`.
Convert at those call sites with `.toKotlinLocalDate()` / `.toKotlinLocalDateTime()`, not by changing
the shared signatures.

**Net simplification to expect:** `CoachDigestCoordinator` currently does
`inbox.seenLedger().mapValues { it.value.toKotlinLocalDate() }` — that conversion should now be
deletable. If it is, delete it and say so.

- [ ] **Step 4: Verify and commit**

Same commands as A1 Step 3. Then:
```bash
git add -A && git commit -m "refactor(ios): port and move the two date-carrying codecs to :shared"
```

---

## Task A3: Publish the framework and verify the Swift surface

- [ ] **Step 1: Rebuild and sync**

```bash
cd ~/Desktop/RecompTracker-IOS && ./scripts/sync-shared.sh
```
Expected: BUILD SUCCESSFUL, framework installed.

- [ ] **Step 2: Confirm the codecs are actually exported to Swift**

```bash
H=~/Desktop/RecompTracker-IOS/Frameworks/Shared.xcframework/ios-arm64-simulator/Shared.framework/Headers/Shared.h
grep -E "RebalanceSerialization|CoachInboxSerialization|PushHistorySerialization|CoachJourneySerialization|CoachExperimentSerialization" $H | head -20
```
Expected: an `@interface Shared<Name>` for each, with `swift_name(...)` attributes on their methods.

⚠️ **If a codec is missing, it is still `internal`** — Kotlin only exports `public` declarations to
Objective-C. Go back to A1 Step 2. This check exists because the failure mode is silent: the
framework builds fine and the symbol simply is not there.

- [ ] **Step 3: Record the real Swift signatures**

Write the actual `swift_name` for each codec entry point into
`docs/ios-port/reference/shared-codec-api.md` in the Android repo. Part B calls these, and guessing
the names cost a build cycle in Phase 0.

- [ ] **Step 4: Commit both repos**

---

# PART B — Swift: stores, secrets, assets, formats

## Task B1: The JSON store foundation

Every preference store shares one mechanism. Build it once.

**Files:** Create `Persistence/Preferences/JSONStore.swift`, `RecompTrackerTests/JSONStoreTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import Testing
@testable import RecompTracker

@Suite struct JSONStoreTests {

    private func tempURL() -> URL {
        URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("store-\(UUID().uuidString).json")
    }

    @Test func absentFileYieldsTheDefault() async throws {
        let store = JSONStore(url: tempURL(), default: 42)
        #expect(await store.value() == 42)
    }

    @Test func writeThenReadRoundTrips() async throws {
        let store = JSONStore(url: tempURL(), default: 0)
        await store.set(7)
        #expect(await store.value() == 7)
    }

    /// Tolerance is the doctrine almost everywhere on Android: malformed input returns the default
    /// rather than throwing. Only UserProfilePreferences throws, and that is a known bug (P2-9).
    @Test func malformedContentYieldsTheDefaultRatherThanThrowing() async throws {
        let url = tempURL()
        try "{ this is not json".write(to: url, atomically: true, encoding: .utf8)
        let store = JSONStore(url: url, default: 99)
        #expect(await store.value() == 99)
    }

    @Test func aValueSurvivesANewStoreInstance() async throws {
        let url = tempURL()
        await JSONStore(url: url, default: 0).set(123)
        #expect(await JSONStore(url: url, default: 0).value() == 123)
    }

    /// A crash mid-write must not leave a truncated file that reads back as the default and
    /// silently discards state. Writing atomically is what prevents that.
    @Test func writesAreAtomic() async throws {
        let url = tempURL()
        let store = JSONStore(url: url, default: 0)
        await store.set(1)
        let firstSize = try Data(contentsOf: url).count
        await store.set(2)
        #expect(try Data(contentsOf: url).count >= firstSize)
        #expect(await store.value() == 2)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Expected: FAIL — "cannot find 'JSONStore'".

- [ ] **Step 3: Implement**

Create `Persistence/Preferences/JSONStore.swift`:
```swift
import Foundation

/// One preference store, backed by a JSON file in Application Support.
///
/// Replaces Android's DataStore Preferences. An `actor` gives the same serialised-write guarantee
/// DataStore's `edit { }` provides, without its transactional API.
///
/// **Decoding is tolerant by design.** Malformed content returns the default rather than throwing —
/// this is the doctrine at nearly every Android codec (`RebalanceSerialization.decode`,
/// `CoachMemoryStore.decode`, and the rest). The one Android site that throws
/// (`UserProfilePreferencesStore`, on an unrecognised enum) is review bug **P2-9**, which kills
/// every collector of the flow. Do not reproduce it.
actor JSONStore<Value: Codable & Sendable> {
    private let url: URL
    private let fallback: Value
    private var cached: Value?

    init(url: URL, default fallback: Value) {
        self.url = url
        self.fallback = fallback
    }

    func value() -> Value {
        if let cached { return cached }
        guard let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(Value.self, from: data)
        else {
            cached = fallback
            return fallback
        }
        cached = decoded
        return decoded
    }

    func set(_ newValue: Value) {
        cached = newValue
        guard let data = try? JSONEncoder().encode(newValue) else { return }
        try? FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        // Atomic: a crash mid-write must never leave a truncated file that decodes as the
        // default and silently discards the user's state.
        try? data.write(to: url, options: .atomic)
    }

    func mutate(_ transform: (Value) -> Value) {
        set(transform(value()))
    }

    /// Where all preference stores live. Separate from the database file, matching Android's
    /// split between Room and DataStore.
    static func directory() throws -> URL {
        try FileManager.default
            .url(for: .applicationSupportDirectory, in: .userDomainMask,
                 appropriateFor: nil, create: true)
            .appendingPathComponent("Preferences", isDirectory: true)
    }
}
```

- [ ] **Step 4: Run to verify pass, then commit**

```bash
git add -A && git commit -m "feat(prefs): actor-backed JSON store foundation"
```

---

## Task B1a: Test helpers the later tasks assume

Every store test needs a throwaway instance. Add this once so B2–B4 can use it.

**Files:** Create `RecompTrackerTests/StoreTestSupport.swift`

- [ ] **Step 1: Write it**

```swift
import Foundation
@testable import RecompTracker

/// Anchors `Bundle(for:)` so tests can find fixture resources in the test bundle.
final class BundleMarker {}

extension JSONStore {
    /// A store backed by a unique throwaway file, so tests never share state.
    static func temporary(default fallback: Value) -> JSONStore<Value> {
        let url = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("test-\(UUID().uuidString).json")
        return JSONStore(url: url, default: fallback)
    }
}
```

Give each concrete store a matching `static func temporary()` returning an instance over such a
file — `UIPreferencesStore.temporary()`, `UserProfileStore.temporary(rawJSON:)`,
`CoachInboxStore.temporary()` and so on. `rawJSON:` writes the string to the file first, so a test
can seed malformed or legacy content.

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "test(prefs): shared store test helpers"
```

---

## Task B2: `plan_preferences` and `ui_preferences`

**Files:** Create `Persistence/Preferences/PlanPreferencesStore.swift`,
`Persistence/Preferences/UIPreferencesStore.swift`, and matching tests.

`PlanPreferences` already exists as a Kotlin type in `:shared` — but Swift cannot conform a Kotlin
class to `Codable`. Declare a Swift mirror with **exactly** the Android defaults, and pin them.

- [ ] **Step 1: Write the failing tests**

```swift
import Testing
@testable import RecompTracker

@Suite struct PreferenceStoreTests {

    @Test func planDefaultsMatchAndroidExactly() async throws {
        let p = PlanPreferences()
        #expect(p.targetCalories == 2550)
        #expect(p.targetProteinG == 165)
        #expect(p.targetCarbsG == 320)
        #expect(p.targetFatG == 68)
        #expect(p.maintenancePhaseStartDate == nil)
        #expect(p.weightTrendThresholdKgPerWeek == 0.20)
        #expect(p.waistIncreaseThresholdCm == 0.5)
        #expect(p.adherenceMinimumPercent == 80.0)
        #expect(p.reviewCadenceDays == 7)
        #expect(p.useMetricUnits == true)
        #expect(p.calorieZoneLowerBound == 2400)
        #expect(p.calorieZoneUpperBound == 2600)
        #expect(p.healthConnectEnabled == false)
        #expect(p.healthConnectLastSyncEpochMs == nil)
    }

    @Test func uiDefaultsMatchAndroidExactly() async throws {
        let u = UIPreferences()
        #expect(u.selectedFont == "default")
        #expect(u.aiInsightsEnabled == false)
        #expect(u.onboardingComplete == false)
        #expect(u.accentTheme == "VIOLET")
        #expect(u.themeMode == "system")   // storageValue, NOT the enum name
        #expect(u.cloudBaseURL == "")
        #expect(u.cloudModelId == "")
        #expect(u.lastSeenBriefingSignature == "")
    }

    /// A read-time transform, not stored data. Android never rewrites the stored value.
    @Test func deprecatedCloudModelRemapsOnRead() async throws {
        var u = UIPreferences()
        u.cloudModelId = "openai/gpt-oss-20b:free"
        #expect(u.effectiveCloudModelId == "nvidia/nemotron-nano-9b-v2:free")
    }

    @Test func otherCloudModelsPassThroughUnchanged() async throws {
        var u = UIPreferences()
        u.cloudModelId = "anthropic/claude-3.5-sonnet"
        #expect(u.effectiveCloudModelId == "anthropic/claude-3.5-sonnet")
    }

    /// `cloudConfigPresent` deliberately reads the RAW value, bypassing the remap.
    @Test func cloudConfigPresenceUsesTheRawModelId() async throws {
        var u = UIPreferences()
        u.cloudBaseURL = "https://openrouter.ai/api/v1"
        u.cloudModelId = "openai/gpt-oss-20b:free"
        #expect(u.cloudConfigPresent == true)
        u.cloudModelId = ""
        #expect(u.cloudConfigPresent == false)
    }

    @Test func unknownAccentAndThemeFallBackToDefaults() async throws {
        var u = UIPreferences()
        u.accentTheme = "CHARTREUSE"
        u.themeMode = "sepia"
        #expect(u.resolvedAccentTheme == "VIOLET")
        #expect(u.resolvedThemeMode == "system")
    }

    @Test func cloudURLAndModelAreTrimmedOnWrite() async throws {
        let store = try await UIPreferencesStore.temporary()
        await store.setCloudBaseURL("  https://x.test/v1  ")
        #expect(await store.value().cloudBaseURL == "https://x.test/v1")
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement both stores**

`PlanPreferences` is a plain `Codable` struct with the 14 fields and the defaults asserted above,
plus `withCalorieTarget(_:)` applying `CALORIE_ZONE_MARGIN = 100`.

`UIPreferences` carries the 8 fields **and** these read-time transforms as computed properties:
- `effectiveCloudModelId` — remaps `"openai/gpt-oss-20b:free"` → `"nvidia/nemotron-nano-9b-v2:free"`
- `cloudConfigPresent` — non-blank base URL **and** non-blank **raw** `cloudModelId`
- `resolvedAccentTheme` — unknown → `"VIOLET"`
- `resolvedThemeMode` — stored values are `"system"`/`"light"`/`"dark"`, unknown → `"system"`

Setters for `cloudBaseURL` and `cloudModelId` trim.

> ⚠️ `selectedFont` is **dead on Android** — persisted, shown in a picker, and never applied (no
> `res/font` directory exists). Keep the field so backups round-trip, but do not build a font picker
> on it without deciding to implement it properly.

- [ ] **Step 4: Run, then commit**

---

## Task B3: `user_profile_preferences`, including the P2-9 fix

**Files:** Create `Persistence/Preferences/UserProfileStore.swift` + tests.

Android stores this as **nine separate keys**, not a JSON blob, then reassembles them into a
`JsonObject` and decodes it — purely to reuse a legacy migration path. **Port the result, not the
contortion:** one `Codable` struct in one file.

- [ ] **Step 1: Write the failing tests**

```swift
@Test func absentProfileIsAllNil() async throws {
    let store = try await UserProfileStore.temporary()
    let p = await store.value()
    #expect(p.name == nil && p.heightCm == nil && p.birthDate == nil && p.goal == nil)
}

/// P2-9: on Android an unrecognised enum throws inside the flow's map and kills every collector.
/// Port the FIXED behaviour — an unknown value degrades to nil, it does not crash.
@Test func anUnrecognisedEnumValueDegradesToNilRatherThanThrowing() async throws {
    let store = try await UserProfileStore.temporary(rawJSON: """
        {"name":"Zack","biologicalSex":"NONBINARY_UNKNOWN_TO_ANDROID","heightCm":183}
        """)
    let p = await store.value()
    #expect(p.name == "Zack")        // the rest of the profile survives
    #expect(p.heightCm == 183)
    #expect(p.biologicalSex == nil)  // only the unparseable field is lost
}

/// The legacy age_years → birth_date migration. It depends on "today", so it must take a date
/// rather than reading the clock.
@Test func legacyAgeYearsMigratesToABirthDate() async throws {
    let store = try await UserProfileStore.temporary(rawJSON: #"{"ageYears":34}"#)
    let p = await store.migratedValue(today: "2026-08-02")
    #expect(p.birthDate == "1992-01-01")
}

@Test func legacyAgeIsIgnoredWhenABirthDateAlreadyExists() async throws {
    let store = try await UserProfileStore.temporary(
        rawJSON: #"{"ageYears":34,"birthDate":"1990-05-05"}"#)
    #expect(await store.migratedValue(today: "2026-08-02").birthDate == "1990-05-05")
}

@Test func nonPositiveLegacyAgeIsIgnored() async throws {
    let store = try await UserProfileStore.temporary(rawJSON: #"{"ageYears":0}"#)
    #expect(await store.migratedValue(today: "2026-08-02").birthDate == nil)
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

Nine optional fields: `name`, `profilePhotoPath`, `heightCm`, `birthDate`, `biologicalSex`,
`activityLevel`, `weeklyGymSessions`, `goal`, `dailyStepGoal`.

⚠️ **`profilePhotoPath`, not `profilePhotoUri`.** Android stores a persistable content URI; iOS has
no such concept, so this becomes a **relative path to a file copied into the app container**. The
rename is deliberate — flag it in the backup codec (Task B9), where the Android key is
`profilePhotoUri`.

Enum-shaped fields decode leniently: unknown string → `nil`, everything else preserved. That is the
P2-9 fix.

`migratedValue(today:)` applies the legacy `ageYears` → `birthDate` rule
(`LocalDate(today.year - age, 1, 1)`) only when `birthDate` is absent and `age > 0`, and **erases the
legacy key on the next write** so it never re-migrates.

- [ ] **Step 4: Run, then commit**

---

## Task B4: The six coach/rebalance stores — calling the shared codecs

⚠️ **Depends on Part A.** Do not start until `sync-shared.sh` has run and A3 Step 2 confirmed the
codec symbols are exported.

**Files:** Create `Persistence/Preferences/CoachStores.swift`,
`Persistence/Preferences/RebalanceStore.swift` + tests.

**Seven stores**: the six coach ones (`coach_inbox`, `coach_journey`, `coach_memory`,
`coach_experiment`, `coach_push_history`, `coach_notification_prefs`) plus `rebalance`.
With B2's two and B3's one, that completes all ten.

**Encode and decode through the Kotlin codecs**, not Swift `Codable` mirrors. That is D12's whole
point — the wire format then matches by construction rather than by review.

- [ ] **Step 1: Write the failing tests**

The critical ones — each pins a behaviour a Swift mirror would get wrong:

```swift
/// PushEvent's timestamp omits seconds when zero. A Swift ISO8601DateFormatter emits
/// "…T22:00:00" and breaks round-trip, silently resetting push rate limiting.
@Test func pushEventTimestampOmitsZeroSeconds() async throws {
    let json = SharedPushHistorySerialization().encode(events: [samplePushEvent(hour: 22, minute: 0)])
    #expect(json.contains("\"2026-08-01T22:00\""))
    #expect(!json.contains("22:00:00"))
}

/// The rebalance codec is hand-rolled, not kotlinx-equivalent: `active` is omitted ENTIRELY when
/// nil (not written as `"active":null`), and all 19 plan keys are always written.
@Test func rebalanceOmitsActiveWhenNil() async throws {
    let json = RebalanceCodec.encode(RebalanceState.empty)
    #expect(!json.contains("\"active\""))
}

/// A pre-feature record has no `intensity` key. It must default to STANDARD rather than nulling
/// the whole plan — that asymmetry exists so old backups still decode.
@Test func rebalancePlanWithoutIntensityDefaultsToStandard() async throws {
    let withoutIntensity = /* a plan JSON string with every required key except "intensity" */ ""
    let state = RebalanceCodec.decode(withoutIntensity)
    #expect(state.active?.intensity == "STANDARD")
}

/// Retention: the seen ledger prunes at 30 days ON WRITE ONLY. Reading an old ledger returns it
/// whole — a port that prunes on read "for safety" changes cooldown behaviour.
@Test func seenLedgerPrunesOnWriteButNotOnRead() async throws {
    let store = try await CoachInboxStore.temporary()
    await store.writeRawLedger(["old-key": "2026-01-01", "recent-key": "2026-08-01"])
    #expect(await store.seenLedger().count == 2)              // read does NOT prune
    await store.markSeen(dedupKey: "new-key", on: "2026-08-02")
    #expect(await store.seenLedger()["old-key"] == nil)        // write DOES prune
    #expect(await store.seenLedger()["recent-key"] != nil)
}

/// Push history prunes by AGE on read and write, but by COUNT only on write — and the read-time
/// prune is not written back.
@Test func pushHistoryPrunesByAgeOnReadAndCountOnWriteOnly() async throws {
    let store = try await PushHistoryStore.temporary()
    await store.writeRaw(events: (1...20).map { samplePushEvent(daysAgo: 0, index: $0) })
    #expect(await store.recentPushes(now: "2026-08-02T10:00").count == 20)  // age-only on read
    await store.record(samplePushEvent(daysAgo: 0, index: 21))
    #expect(await store.recentPushes(now: "2026-08-02T10:00").count == 14)  // count cap on write
}

/// Caps, each verified against the Android constant.
@Test func everyLedgerCapMatchesAndroid() async throws {
    // fired history 24 · weekly verdicts 8 (idempotent by weekSignature) · memory 50
    // (case-insensitive dedup) · push events 14 · rebalance history 12 (sorted by createdAtIso)
    let journey = try await CoachJourneyStore.temporary()
    for i in 1...30 { await journey.recordFired(sampleFiredRecord(index: i)) }
    #expect(await journey.firedHistory().count == 24)

    let memory = try await CoachMemoryStore.temporary()
    for i in 1...60 { _ = await memory.add(text: "fact \(i)") }
    #expect(await memory.entries().count == 50)
    _ = await memory.add(text: "FACT 60")                     // case-insensitive duplicate
    #expect(await memory.entries().count == 50)
}
```

⚠️ **Symbol names.** `RebalanceCodec` / `CoachInboxStore` above stand in for the real Swift names of
the shared Kotlin codecs, which you recorded in **A3 Step 3**. Substitute the actual ones; do not
guess. If A3 Step 3 was skipped, go back and do it — guessing cost a build cycle in Phase 0.

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement the seven stores**

Each wraps a `JSONStore<String>` holding the **encoded JSON string**, and converts through the
shared Kotlin codec on the way in and out. `coach_notification_prefs` is the exception — plain
scalars (`weeklyCheckInPushEnabled` default **true**, `ambientNudgesEnabled` default **false**,
quiet hours 22/7 **clamped 0…23 on both read and write**, `lastPushedWeeklySignature` default `""`).

- [ ] **Step 4: Run, then commit**

---

## Task B5: Keychain

**Files:** Create `Persistence/Secrets/KeychainStore.swift` + tests.

Two values: `cloud_api_key` and `web_search_api_key`.

- [ ] **Step 1: Write the failing tests**

```swift
@Test func absentKeyReadsAsEmptyString() throws { /* Android returns "".orEmpty(), not nil */ }
@Test func setThenGetRoundTrips() throws { }
@Test func keysAreTrimmedOnWrite() throws { }
@Test func clearRemovesTheItem() throws { }
@Test func hasKeyReflectsPresenceWithoutExposingTheValue() throws { }
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

`kSecClassGenericPassword`, service = bundle id, account = the key name.

⚠️ **`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.** `AfterFirstUnlock` (not `WhenUnlocked`)
because the coach digest may need the key while the device is locked. `ThisDeviceOnly` because it
reproduces Android's intent — the key never restores to a new device. That single attribute replaces
the whole `openEncryptedPrefsWithRecovery` complex Android needs, which exists only because a
device-to-device restore brings back the encrypted file without its Keystore key and crash-loops.

⚠️ **Expose a key *provider*, never a captured value.** Android's `CloudConfig.apiKey` is
`() -> String` read fresh at request time. A Swift config struct that captures the key string
reintroduces review **P1-5**: a rotated key stays unused until app restart, because the presence flag
is a `Bool` and `true → true` does not re-emit. Model it as `@Sendable () -> String`.

- [ ] **Step 4: Run, then commit**

---

## Task B6: Bundled assets and the version-gated exercise seed

**Files:** Add the four JSON assets to the app target; create
`Persistence/Seeding/ExerciseLibrarySeeder.swift` + tests.

- [ ] **Step 1: Copy the assets and confirm they are bundled**

```bash
cd ~/Desktop/RecompTracker-IOS
mkdir -p RecompTracker/RecompTracker/Resources
cp "/Users/zackalatrash/Desktop/Personal Dietitian/app/src/main/assets/exercises/exercises.json" \
   "/Users/zackalatrash/Desktop/Personal Dietitian/app/src/main/assets/knowledge/corpus.json" \
   "/Users/zackalatrash/Desktop/Personal Dietitian/app/src/main/assets/muscles/body_front.json" \
   "/Users/zackalatrash/Desktop/Personal Dietitian/app/src/main/assets/muscles/body_back.json" \
   RecompTracker/RecompTracker/Resources/
```

⚠️ Buildable folders add **source** files automatically, but a `.json` needs to be in the target's
**Resources** build phase to reach `Bundle.main`. Verify rather than assume:
```swift
@Test func bundledAssetsAreReachable() throws {
    for name in ["exercises", "corpus", "body_front", "body_back"] {
        #expect(Bundle.main.url(forResource: name, withExtension: "json") != nil, "missing \(name).json")
    }
}
```
If this fails, the folder is not being treated as a resource — say so and stop rather than
hand-editing `project.pbxproj`.

- [ ] **Step 2: Write the failing seeder tests**

```swift
@Test func seedsAllExercisesOnAnEmptyDatabase() async throws {
    // 873 rows, source "free-exercise-db", sourceVersion "2026-06-17"
}

@Test func doesNotReseedWhenTheVersionMatches() async throws { }

/// P1-19: the re-seed must PRESERVE ids. A delete-then-insert crashed for every user with a
/// routine, because workout_exercises.exerciseId is ON DELETE NO ACTION.
@Test func reseedingANewVersionPreservesExistingIds() async throws { }

/// The four muscle/instruction columns are JSON-array-in-TEXT, not Swift arrays.
@Test func listColumnsAreStoredAsJSONStrings() async throws { }
```

- [ ] **Step 3: Implement**

Parse `exercises.json` (873 records) off the main actor; seed via `Transactions.upsertLibrary`
(already built in 1a) so ids are preserved; gate on `sourceVersion` so a matching version is a no-op.

- [ ] **Step 4: Run, then commit**

---

## Task B7: `.rtroutine` — the format that needs no reimplementation

⚠️ **Depends on Part A** only for the resynced framework; `RoutineShareSerializer` and
`RoutineShareModels` were already moved to `:shared` in Phase 0.

**Files:** Create `Persistence/Formats/RoutineShare.swift` + tests; add the UTI to `Info.plist`.

- [ ] **Step 1: Write the failing tests**

```swift
/// The whole point: iOS reads Android's file byte-for-byte because it runs Android's codec.
@Test func decodesAFileProducedByAndroid() throws { }
@Test func rejectsAFileFromAnotherApp() throws { }          // app != "recomptracker" -> NotARoutineFile
@Test func rejectsAFutureVersion() throws { }               // version > 1 -> UnsupportedVersion
@Test func rejectsABlankNameOrSetlessExercise() throws { }  // -> Damaged
@Test func roundTripsThroughEncodeAndDecode() throws { }
```

Note the decode order is load-bearing: app-id check, **then** version, **then** structural damage.

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement the Swift wrapper and declare the UTI**

Thin wrapper calling `SharedRoutineShareSerializer`. Then in the target's Info settings add:
- `UTExportedTypeDeclarations` → identifier `com.zack.recomptracker.routine`, conforming to
  `public.data` and `public.content`, filename extension `rtroutine`
- `CFBundleDocumentTypes` → `LSItemContentTypes` = that identifier, role `Editor`

⚠️ Files arriving from other apps land in `Documents/Inbox/` and require
`startAccessingSecurityScopedResource()` / `stopAccessingSecurityScopedResource()`. There is no
Android equivalent and forgetting it produces a silent read failure.

- [ ] **Step 4: Run, then commit**

---

## Task B8: The personal-foods codec

**Files:** Create `Persistence/Formats/PersonalFoodsCodec.swift` + tests.

`PersonalFoodsJsonCodec` stayed in `:app` (it lives in `domain/foodimport`, excluded from the KMP
move). Reimplement in Swift — it is small.

Envelope: `{version: Int = 1, exportedAt: String, foods: [{name, servingName, calories, proteinG,
carbsG, fatG}]}`.

- [ ] **Step 1: Write the failing tests**

```swift
/// ⚠️ STRICT equality, unlike the backup's `<=`. Do not unify them.
@Test func rejectsAnyVersionOtherThanOne() throws { }
@Test func roundTripsAFoodList() throws { }
@Test func exportedAtIsAnISO8601Instant() throws { }
```

- [ ] **Step 2–4:** implement, verify, commit.

---

## Task B9: The backup codec

**Files:** Create `Persistence/Formats/BackupPayload.swift`,
`Persistence/Formats/BackupRepository.swift` + tests.

The one format reimplemented in Swift, because `BackupModels.kt` is a DTO over 19 Room entities.

- [ ] **Step 1: Write the failing tests**

```swift
@Test func decodesEveryTopLevelKey() throws { }               // 21 fields
@Test func v1BackupWithoutTrainingKeysDecodes() throws { }    // defaults to empty lists
@Test func rejectsAVersionAboveTwo() throws { }               // require(version <= 2)
@Test func mealSlotJSONKeyIsSortOrderNotSnakeCase() throws { }
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement the payload**

21 top-level fields. **Element keys are the Kotlin property names**, which equal the SQL column names
**with one exception**: `meal_slots.sort_order` serialises as **`sortOrder`**. So the backup codec
must **not** reuse `MealSlot`'s database `CodingKeys` — declare separate coding keys and convert.

`usage_events` has no key at all — it is the only entity Android does not mark `@Serializable`.

`profilePhotoUri` in `PlanPreferences`/profile maps to our `profilePhotoPath` (Task B3). Decide and
document: an Android content URI is meaningless on iOS, so import it as `nil`.

- [ ] **Step 4: Implement restore, in Android's exact order**

Inside one `db.write { }`:
1. `require(payload.version <= 2)` — **before** the transaction, with a user-facing message
2. delete all rows from all 19 tables
3. meal slots — **id-for-id** if present, else seed `Meal 1`/`Lunch`/`Dinner` at sort 0/1/2
4. exercises → catalogFoods → workouts → workoutExercises → plannedSets → workoutSessions →
   sessionExercises → sessionSets, **all id-for-id, parents before children**
5. dailyLogs, mealEntries, savedFoods, savedMeals, liftPerformances, weeklyReviews
6. **recipes — the ONE exception: fresh ids**, ingredients remapped to the new `recipeId`
7. plan versions
8. `usage_events` deliberately left empty

⚠️ **Fix P2-18 rather than porting it.** Android saves preferences and rebalance state *outside* the
transaction, so a failure between steps leaves the database restored and preferences stale. Bring
them inside, or apply them only after the transaction commits successfully.

- [ ] **Step 5: Run, then commit**

---

## Task B10: 🔴 The acceptance test — import a real Android backup

**Requires the fixture.** See the Prerequisite section.

**Files:** Create `RecompTrackerTests/BackupRoundTripTests.swift`

- [ ] **Step 1: Write the test**

```swift
@Suite struct BackupRoundTripTests {

    private func fixture() throws -> Data {
        let url = try #require(Bundle(for: BundleMarker.self)
            .url(forResource: "android-backup-v2", withExtension: "json"))
        return try Data(contentsOf: url)
    }

    @Test func importsARealAndroidExport() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let payload = try BackupRepository.decode(try fixture())
        try BackupRepository.restore(payload, into: db)

        // Every table the payload carried is populated.
        try db.reader.read { d in
            #expect(try DailyLog.fetchCount(d) == payload.dailyLogs.count)
            #expect(try MealEntry.fetchCount(d) == payload.mealEntries.count)
            #expect(try Exercise.fetchCount(d) == payload.exercises.count)
            #expect(try WorkoutSession.fetchCount(d) == payload.workoutSessions.count)
            #expect(try SessionSet.fetchCount(d) == payload.sessionSets.count)
        }
    }

    /// 🔴 THE test. P0-2 was exactly this: slots were re-inserted with fresh ids while meal entries
    /// kept their original slotId, so meals silently vanished from their Breakfast/Lunch/Dinner
    /// cards while still counting toward day totals. There is no foreign key to catch it.
    @Test func everyMealSlotLinkSurvivesTheImport() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let payload = try BackupRepository.decode(try fixture())
        try BackupRepository.restore(payload, into: db)

        let dangling = try db.reader.read { d in
            try Int.fetchOne(d, sql: """
                SELECT COUNT(*) FROM meal_entries
                WHERE slotId IS NOT NULL
                  AND slotId NOT IN (SELECT id FROM meal_slots)
                """) ?? 0
        }
        #expect(dangling == 0)

        // And the links point where the export said they did.
        for entry in payload.mealEntries where entry.slotId != nil {
            let stored = try db.reader.read { try MealEntry.fetchOne($0, key: entry.id!) }
            #expect(stored?.slotId == entry.slotId)
        }
    }

    /// Training rows are restored id-for-id, so every FK in the graph must resolve.
    @Test func theTrainingGraphHasNoDanglingForeignKeys() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try BackupRepository.restore(try BackupRepository.decode(try fixture()), into: db)
        try db.reader.read { d in
            #expect(try Int.fetchOne(d, sql: """
                SELECT COUNT(*) FROM workout_exercises
                WHERE exerciseId NOT IN (SELECT id FROM exercises)
                """) == 0)
            #expect(try Int.fetchOne(d, sql: """
                SELECT COUNT(*) FROM session_sets
                WHERE sessionExerciseId NOT IN (SELECT id FROM session_exercises)
                """) == 0)
        }
    }

    /// Recipes are the ONE table restored with remapped ids — ingredients must follow.
    @Test func recipeIngredientsFollowTheirRemappedRecipeIds() throws { }

    @Test func usageEventsAreLeftEmpty() throws { }

    /// Re-importing must be idempotent, not additive.
    @Test func importingTwiceYieldsTheSameRowCounts() throws { }
}
```

- [ ] **Step 2: Run**

Expected: PASS. **A failure here is the most valuable signal in the phase** — it means the Swift
payload model and Android's serialised shape genuinely disagree. Read the decode error, fix the
model, do not loosen the test.

- [ ] **Step 3: Commit**

---

## Task B11: Verification and documentation

- [ ] **Step 1: Full suite, both configurations**

```bash
cd ~/Desktop/RecompTracker-IOS
xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
xcodebuild -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -configuration Release build
```
Expected: TEST SUCCEEDED, BUILD SUCCEEDED, **zero isolation warnings**.

- [ ] **Step 2: Confirm the Android side is still green**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest :shared:iosSimulatorArm64Test
```
Part A moved five files between modules; this proves nothing regressed.

- [ ] **Step 3: Update the port docs**

`parity-ledger.md` — tick the three Foundations rows and all three File-format rows.
`STATUS.md` — phase board, session-log entry with counts and surprises, and anything needing your
visual check.

- [ ] **Step 4: Commit both repos**

---

## What Phase 1b deliberately does NOT do

- **No UI.** Phase 2.
- **No NEVO or Samsung CSV import.** v1.1 (D4) — 453 LOC of bespoke dual-format parsing, plus a zip
  dependency iOS has no system API for.
- **No HealthKit.** Phase 4.
- **No `PlanHistoryInitializer` equivalent beyond the seed itself.** Wiring app-start initialisation
  belongs with the app shell in Phase 2.

## Rollback

Part A is five `git mv`s plus import edits in the Android repo — revertible as a range. Part B is
additive in the iOS repo. Neither touches Phase 1a's schema or records.
