# Reference — HealthKit Notes

The highest-risk area of the port. Health Connect and HealthKit are not the same model with
different names — three structural mismatches force UX redesign, not just code translation.

**Provenance:** 🌐 Apple Developer documentation and bundled iOS skills, verified 2026-08-01 ·
📋 from deep-dive analysis of the Android side. ⚠️ = version-dependent, re-verify before relying on it.

**Platform baseline at capture:** iOS 26.5/26.6 shipping · **iOS 27 in developer beta since
2026-06-08**, ships ~Sept 2026 · Xcode 26 / iOS 26 SDK required for App Store uploads since
2026-04-28.

---

## 1. What the Android app reads

📋 `data/health/HealthConnectRepository.kt:37-46` — four record types, **read-only, zero writes**:

| Health Connect | Permission tier |
|---|---|
| `StepsRecord` | base |
| `WeightRecord` | base |
| `SleepSessionRecord` | base |
| `NutritionRecord` | **separate, optional** — requested only for the 365-day import, alongside `PERMISSION_READ_HEALTH_DATA_HISTORY` |

---

## 2. Type mapping

### 2.1 Core metrics — `HKQuantityType(.identifier)`

| Domain | Health Connect | HealthKit identifier | Canonical `HKUnit` |
|---|---|---|---|
| Steps | `StepsRecord` | `.stepCount` | `.count()` |
| Body weight | `WeightRecord` | `.bodyMass` | `.gramUnit(with: .kilo)` |
| Body fat | `BodyFatRecord` | `.bodyFatPercentage` | `.percent()` |
| Lean mass | `LeanBodyMassRecord` | `.leanBodyMass` | `.gramUnit(with: .kilo)` |
| Height | `HeightRecord` | `.height` | `.meter()` |
| Waist | — | `.waistCircumference` | `.meter()` |
| Active energy | `ActiveCaloriesBurnedRecord` | `.activeEnergyBurned` | `.kilocalorie()` |
| Resting HR | `RestingHeartRateRecord` | `.restingHeartRate` | `.count()/.minute()` |
| Sleep | `SleepSessionRecord` + stages | **`HKCategoryType(.sleepAnalysis)`** | category, not quantity |

Note `.waistCircumference` exists on iOS but has no Health Connect counterpart — the app currently
stores waist locally only. Worth considering whether to write it.

### 2.2 Nutrition — the set this app needs

Each is a **separate** `HKQuantityTypeIdentifier`:

`dietaryEnergyConsumed` (kcal) · `dietaryProtein` (g) · `dietaryCarbohydrates` (g) ·
`dietaryFatTotal` (g) · `dietaryFiber` (g) · `dietarySugar` (g) · `dietarySodium` (mg)

Apple's full set additionally covers saturated/mono/poly fat, cholesterol, 8 minerals, 13 vitamins,
6 ultratrace elements, `dietaryWater`, and `dietaryCaffeine`.

⚠️ **There is no HealthKit "meal type" quantity.** Breakfast/lunch/dinner slot must live in your own
DB or in correlation metadata (custom metadata keys are allowed).

---

## 3. The three structural mismatches

### 3.1 🔴 iOS never tells you a read permission was denied

Apple, verbatim: *"your app doesn't know whether someone granted or denied permission to read data
from HealthKit."*

- `authorizationStatus(for:)` reports **write** status only
  (`.notDetermined` / `.sharingAuthorized` / `.sharingDenied`).
- There is **no** `getGrantedPermissions()` equivalent and **no** programmatic revoke.
- `getRequestStatusForAuthorization(toShare:read:)` tells you only whether the system *would* show a
  sheet.
- Re-calling `requestAuthorization` for already-decided types is a silent no-op.

📋 **What this breaks in the current design:** `hcRepository.hasPermissions()` gates three sync entry
points (`HealthSyncCoordinator.kt:80,97,129`) and the entire Integrations screen state derives from
it. Health Connect's granular grant introspection has no counterpart.

**The redesign:** replace "you denied X, tap to fix" with "we see no data — check Settings → Privacy
& Security → Health → Recomp Tracker." Track a local "we asked" flag; infer availability from
whether queries return data or error with `.errorAuthorizationDenied`.

### 3.2 🔴 Nutrition is N samples plus a correlation

Health Connect gives one `NutritionRecord` with a name and every macro. HealthKit requires:

1. One `HKQuantitySample` **per nutrient**;
2. **Do not save them individually** — wrap them in
   `HKCorrelation(type: HKCorrelationType(.food), start:end:objects:metadata: [HKMetadataKeyFoodType: "Chicken breast"])`
   and save the correlation.

Apps that write loose samples without the correlation produce data other readers (including the
Health app) cannot interpret as a meal.

⚠️ **Consequence for the 365-day import:** many third-party apps write loose quantities without
correlations, so **iOS may recover materially less usable named-food data than Health Connect did.**
Design the import UX to be honest about partial results.

⚠️ **Background delivery is not supported for `HKCorrelationType`** — observe the underlying quantity
types instead.

`toFoodImportCandidate()` (`HealthConnectRepository.kt:189-204`) is a rewrite, not a translation.

### 3.3 🔴 Sleep has no session object

`SleepSessionRecord` (one object with start/end and a stage list) becomes a **flat stream of
`HKCategoryType(.sleepAnalysis)` samples**: `.inBed`, `.awake`, `.asleepCore`, `.asleepREM`,
`.asleepDeep`, `.asleepUnspecified`.

You must:
- **Group samples into "a night" yourself** — typically 18:00→18:00 windows, merging gaps under
  ~30–60 min.
- **Dedupe across sources** (Apple Watch and a third-party sleep app both write).
- **Aggregate in your own code** — ⚠️ **statistics queries only work on `HKQuantityType`.**
  There is no `HKStatisticsQuery` for category types. Health Connect's aggregate API *does* cover
  sleep duration, so **this is a genuine capability regression.**

📋 The current Android implementation is a one-liner
(`Duration.between(latest.startTime, latest.endTime)`, `:163-169`). It does not port.

⚠️ Apple's current doc page lists all staged cases as iOS 8.0+, which contradicts their well-known
iOS 16 introduction. **Gate on iOS 16+** regardless (moot at an iOS 26 floor, but worth knowing).

---

## 4. Steps — the dedup trap

📋 The Android KDoc (`HealthConnectRepository.kt:139-145`) records the war story: multiple sources
(Samsung Health, watch, phone counter) each write their own `StepsRecord` for the same walk, and
summing raw records showed **17k steps on a 4k day**. Health Connect's `aggregate` API deduplicates
across data origins automatically.

⚠️ **The same bug class exists on iOS, with a different cause and a different fix.** HealthKit
de-overlaps cumulative quantities from the *same* source, but iPhone + Apple Watch double-counting is
handled differently. Use `HKStatisticsQuery(.stepCount, options: .cumulativeSum)` and be deliberate
about source filtering.

**Do not assume `.cumulativeSum` is safe out of the box. Test with a Watch paired.**

---

## 5. Queries — the mapping

| Purpose | Health Connect | HealthKit |
|---|---|---|
| Today's steps | `aggregate(AggregateRequest(COUNT_TOTAL, …))` | `HKStatisticsQueryDescriptor(predicate:options: .cumulativeSum)` |
| Daily steps series | `aggregateGroupByPeriod(…, Period.ofDays(1))` | `HKStatisticsCollectionQueryDescriptor(anchorDate:intervalComponents: DateComponents(day: 1))`, enumerate with `collection.enumerateStatistics(from:to:)` |
| Latest weight | `readRecords` + `maxByOrNull { it.time }` | `HKSampleQuery` sorted `endDate` desc, `limit: 1` |
| Change tracking | `getChanges(token)` | **`HKAnchoredObjectQueryDescriptor`** — returns added samples *and* `HKDeletedObject`s plus a new `HKQueryAnchor` |
| Pagination | `pageToken` loop | `HKAnchoredObjectQuery` with `limit` + anchor, or a moving start-date cursor |
| Source filtering | data origin | `HKQuery.predicateForObjects(from: Set<HKSource>)`; `sourceRevision` on every `HKObject` |
| Exclude manual entries | — | `predicateForObjects(withMetadataKey: HKMetadataKeyWasUserEntered, allowedValues: [true])` inside `NSCompoundPredicate` |

**Anchors** don't expire (unlike HC change tokens, ~30 days), but persist them
(`NSKeyedArchiver`) and still handle a full-resync path.
⚠️ Community reports of anchored queries updating more slowly on 26.1 — treat freshness as
best-effort.

### 5.1 Statistics gotcha

`.cumulativeSum` on a **discrete** type (weight, HR) returns `nil`. Use `.discreteAverage` /
`.discreteMin` / `.discreteMax`. And again: **no statistics query exists for category types.**

---

## 6. Reading 365 days of nutrition

**What's required:**

1. `requestAuthorization(toShare:read:)` including every dietary type you read. ⚠️ **Over-requesting
   types is a documented App Review rejection cause** — request only what you use.
2. Predicate: `HKQuery.predicateForSamples(withStart: oneYearAgo, end: now, options: [.strictStartDate])`.
3. **Prefer `HKStatisticsCollectionQueryDescriptor` with `DateComponents(day: 1)` per nutrient** over
   a raw sample query. A year of meal logging can be 10k–100k samples across 7 nutrients;
   `HKObjectQueryNoLimit` will spike memory. Page with `limit:` + a moving cursor if you need
   per-meal granularity.
4. Run off the main actor — the descriptor `result(for:)` APIs are `async`.

**Gotchas:**

- ⚠️ **iOS 27 limited historical authorization — the biggest new one.** Apple's *Authorizing access
  to health data* now states that after the data-type screen, *"a second screen prompts them to
  choose how much historical data to grant your app, either a recent limited window or their full
  history."* Discover the boundary with **`getEarliestAuthorizedSampleDate(for:completion:)`**
  (documented **iOS 27.0+ Beta**), which returns type → earliest readable date, **omitting types with
  full or denied access**. Apple's guidance: treat data before that date as **unknown, not absent**,
  and clamp your query start to it.
  **Your import flow needs a "we could only reach N days — grant full history in Settings" state.**
  Re-verify at GM.
- `earliestPermittedSampleDate()` (iOS 9+) is a systemwide floor.
- HealthKit merges all sources — a year of imported nutrition may include another app's entries.
  Decide up front whether you re-import them and record provenance.
- ✅ **One thing that gets easier:** HealthKit read auth has **no separate history-depth permission**,
  so `READ_HEALTH_DATA_HISTORY`, `supportsHistoricalNutritionImport()`, and the feature check all
  disappear (on iOS ≤26).
- **Simulator has no meaningful Health data.** Seed on a real device or write samples in a debug build.

---

## 7. Background delivery — better than WorkManager here

`HKObserverQuery` + `enableBackgroundDelivery(for:frequency:)` is **the only mechanism that wakes a
backgrounded or terminated app on new health data**, and in practice it is **the most reliable
background wake iOS gives you** — more reliable than `BGAppRefreshTask`.

Frequencies: `.immediate`, `.hourly`, `.daily`, `.weekly`. `.immediate` is best-effort; the system
batches by battery and thermal state.

**Rules:**
- ⚠️ Requires the entitlement `com.apple.developer.healthkit.background-delivery = true` (Xcode:
  "Background Delivery" under the HealthKit capability). **Without it `enableBackgroundDelivery`
  fails.**
- **Always call the observer's `completionHandler()` on every path** (`defer { handler() }`).
  Skipping it stops future deliveries.
- Register the observer and enable delivery once at launch; registration persists across launches.
- ⚠️ **`HKAnchoredObjectQueryDescriptor` does not survive backgrounding** — its long-running
  `results(for:)` sequence is cancelled when the app leaves the foreground.
  **Pattern:** observer wakes you → run a *one-shot* anchored query → persist the anchor →
  `handler()`.

**Exploit this for the digest.** See [roadmap §5.4](../00-feasibility-and-roadmap.md) — schedule the
notification with `UNCalendarNotificationTrigger` and refresh its content on health wakes.

---

## 8. Writing to HealthKit

The Android app writes nothing. If iOS ever writes meals back:

- Set **`HKMetadataKeySyncIdentifier`** (your stable row id) + **`HKMetadataKeySyncVersion`**
  (monotonic Int) on every sample. HealthKit then **replaces rather than duplicates** on re-save,
  and a higher version wins. **Without these, re-running an export duplicates everything.**
- Exclude your own writes when reading:
  `NSCompoundPredicate(notPredicateWithSubpredicate: HKQuery.predicateForObjects(from: [HKSource.default()]))`.
- You can only delete objects your app created.
- Requires `NSHealthUpdateUsageDescription`.

---

## 9. Info.plist, entitlements, review

**Required:**
- `NSHealthShareUsageDescription` (read) — must be **specific**; vague strings are rejected.
- `NSHealthUpdateUsageDescription` (only if writing).
- Entitlement `com.apple.developer.healthkit`.
- Entitlement `com.apple.developer.healthkit.background-delivery`.
- `com.apple.developer.healthkit.access` **only** for Clinical Health Records — not needed here.

**Availability guard:** `HKHealthStore.isHealthDataAvailable()`.
⚠️ Contradictory reports on iPadOS/Catalyst — test on device before shipping iPad support.

**Review guidelines that bite:**

| Guideline | Effect |
|---|---|
| **5.1.3(i)** | Health data may not be disclosed to third parties except to improve health management, and **only with permission**. You must disclose the specific health data collected. ⚠️ **This constrains what health-derived data may go to the LLM at all** — frame the disclosure as "to improve your health management," never as analytics or model training. |
| **5.1.3(ii)** | **May not store personal health information in iCloud.** Rules out CloudKit / iCloud Drive / iCloud KVS for logs, body entries, or anything health-derived. The existing local-export + user-driven-share pattern is already compliant. |
| **5.1.2(vi)** | No HealthKit data for marketing, advertising, or use-based data mining. |
| **2.5.1** | HealthKit "should be used for health and fitness purposes **and integrate with the Health app**." Read-only is fine, but the integration must be genuine. |
| **1.4.1** | Medical/health apps get greater scrutiny; disclose the methodology behind accuracy claims; **"should remind users to check with a doctor."** |
| **5.1.1(ix)** | Apps in highly regulated fields "should be submitted by a legal entity." A general-wellness tracker normally passes as an individual — **avoid clinical framing.** |
| Authorization hygiene | Over-requesting types is a documented rejection cause. |

---

## 10. Porting checklist

- [ ] HealthKit capability + **background-delivery entitlement** enabled
- [ ] `NSHealthShareUsageDescription` written specifically (not "we need health data")
- [ ] Read set requested = exactly the types used, nothing more
- [ ] `hasPermissions()` gates replaced with a no-data empty state + Settings deep link
- [ ] Steps via `HKStatisticsQuery`, **tested with a paired Watch** for double-counting
- [ ] Daily steps series via `HKStatisticsCollectionQuery`
- [ ] Sleep: night-grouping + cross-source dedup + **own aggregation** (no statistics query)
- [ ] Nutrition read via `HKCorrelation(.food)`, with a documented fallback for loose samples
- [ ] 365-day import via `HKStatisticsCollectionQuery` per nutrient, off the main actor
- [ ] ⚠️ iOS 27: `getEarliestAuthorizedSampleDate(for:)` availability-gated, "partial import" UX
- [ ] `HKObserverQuery` + `enableBackgroundDelivery(.daily)`, `defer { handler() }` on every path
- [ ] Anchors persisted; one-shot anchored query inside the observer, not a long-running one
- [ ] Provenance recorded so imported third-party entries are distinguishable
- [ ] Tested **on a physical device** — the simulator proves nothing here
