# iOS Port — Decision Log

Append-only. One entry per binding decision. **Read before making a convention call; append in the
same commit as the code that establishes it.**

Format: `ID · date · decision · why · consequence if reversed`.
Mark superseded entries `~~struck through~~` with a pointer to the replacement — never delete.

---

## D1 · 2026-08-01 · Native Swift/SwiftUI, not Compose Multiplatform

**Why:** JetBrains' own guidance is that Liquid Glass requires a native SwiftUI shell owning
`TabView`/`NavigationStack`, which halves the shared-UI win for an app whose identity *is* a glass
design language. CMP also shipped scroll-fling, back-swipe and frame-drop fixes as recently as
2026-07, carries 139 open iOS-subsystem issues, has VoiceOver role gaps that would fail an audit, and
measures 3–4× the RAM of an RN equivalent on iOS.
**Reversing:** the design system and navigation layer would be rewritten. Evidence to re-read:
[reference/architecture-evidence.md §7](reference/architecture-evidence.md).

## D2 · 2026-08-01 · Share `domain/` only — not `data/`, not `ai/`

**Why:** measured interop surface. `domain/` has **0 suspend / 0 Flow / 0 StateFlow** — it is
synchronous pure functions over value types, so it costs almost nothing at the Kotlin↔Swift boundary.
`ai/` + `data/` carry 395 suspend and 134 flows, and sharing them would additionally commit us to
Room-KMP (no published production report at this schema size), DataStore-KMP, and an OkHttp→Ktor
migration. This also demotes SKIE from load-bearing to nice-to-have.
**Reversing:** widening later is additive and cheap; narrowing later is not. Start narrow.

## D3 · 2026-08-01 · GRDB for persistence — not SwiftData, not Core Data

**Why:** three hard blockers with SwiftData. (a) `BackupRepository` restores 8 tables **id-for-id**
and `PersistentIdentifier` cannot be assigned. (b) The per-column UPDATE pattern in `DailyLogDao` and
`WorkoutSessionDao` is load-bearing — it exists because whole-row read-modify-write lost concurrent
writes (P1-18, P2-7) — and SwiftData's object-graph model naturally does whole-object saves.
(c) The 365-day import is a bulk insert of thousands of rows, a known SwiftData cliff.
GRDB's `DatabaseMigrator` is also a near-mechanical analogue of Room's `Migration` list, and
`ValueObservation` of Room's `Flow`-returning DAOs.
**Reversing:** the entire persistence layer. Treat as fixed.

## D4 · 2026-08-01 · iOS v1 = core loop + AI coach; Train and CSV import are v1.1

**Why:** reaching TestFlight early de-risks the App Store process (entitlements, privacy manifest,
Beta App Review) while the hard surfaces are still being built. Train is the cleanest thing to defer
— 7 of 19 tables and 5 of 19 coach tools serve it alone.
**Consequence:** build the **full schema and all 14 migrations in Phase 1 anyway** (empty tables cost
nothing and it preserves Android backup round-trip), and ship the five routine coach tools as
**unavailable** in v1 so the coach declines cleanly rather than writing rows no screen can show.

## D5 · 2026-08-01 · Deployment target iOS 26+

**Why:** native Liquid Glass with **no `Material` fallback layer** across ~45 surfaces — the largest
single code saving in the port — plus `BGContinuedProcessingTask` for the 365-day import.
**Cost:** smaller addressable device base. **Also:** `UIDesignRequiresCompatibility` is reportedly
ignored under the iOS 27 SDK (secondary sources), so committing to glass is effectively permanent
regardless.
**Open sub-item:** verify Apple's iOS 26 device list before deleting the `AVCaptureMetadataOutput`
scanner fallback — if every iOS 26 device has a Neural Engine, `DataScannerViewController.isSupported`
is always true.

## D6 · 2026-08-01 · Dates persist as `YYYY-MM-DD` strings, never `Date`

**Why:** six tables key or filter on ISO date **strings** and every range predicate depends on
lexicographic TEXT ordering — including the `"9999-12-31"` sentinel at `LogRepository.kt:110`.
Switching to `Date` (a `Double` instant) changes every predicate's meaning and makes
`Calendar.startOfDay` silently shift rows across day boundaries.
**Corollary:** every formatter uses `Locale(identifier: "en_US_POSIX")`. Android's
`LocalDate.toString()` is locale-independent; `DateFormatter` is not, and a non-Gregorian device would
corrupt three tables' primary keys.
**Reversing:** every query and the whole date layer. Treat as fixed.

## D7 · 2026-08-01 · Separate planning sessions from execution sessions

**Why:** a planning session reads widely and burns context on exploration; an execution session should
start with a finished plan and spend its budget writing code. Mixing them risks running dry
mid-implementation.
**Consequence:** each phase gets a planning session that writes `phases/phase-N-*.md`, then one or
more execution sessions. **A phase is a planning and merge unit; a task ending in a green build is
the session unit.** They are not 1:1 — Phase 3 is seven screens.

## D8 · 2026-08-01 · Phase 0 is exclusive; every later phase is parallel-safe

**Why:** Phase 0 restructures the Gradle build (`:app` → `:app` + `:shared`), which conflicts with any
concurrent Android branch. Later phases only add files under `ios/`, which cannot break the Android
build.
**Consequence:** no parallel Android work while Phase 0 lands. After that, one branch per phase merged
to `develop` on completion — no long-lived integration branch.

## D11 · 2026-08-01 · The iOS app lives in a separate sibling repo, fed by a synced XCFramework

`~/Desktop/RecompTracker-IOS/` — a fresh, independent git repo beside `Personal Dietitian/`.
`:shared` publishes `Shared.xcframework`; the iOS repo's `scripts/sync-shared.sh` builds it from the
sibling and drops it in `Frameworks/`, **gitignored** as a build product.

**Why:** keeps the Android repo and Xcode out of each other's way, and — the non-obvious upside —
Xcode never invokes Gradle, so the SwiftUI inner loop is *faster* than an in-project setup. Kotlin is
rebuilt only on a deliberate resync.
**Cost:** the iOS repo is **not standalone-buildable**; the Android repo must sit beside it (override
with `ANDROID_REPO=`). Documented in its README. `embedAndSignAppleFrameworkForXcode` is unusable —
it assumes one project — so XCFramework assembly builds both architectures and is slower per resync.
**Rejected:** committing the XCFramework (multi-MB binary churn in git, easy to forget to rebuild);
GitHub Releases + SPM binary target (tag/release/checksum ceremony on every domain change).
**Consequence:** Phase 0 gate criterion **8** measures whether the resync workflow is actually
tolerable. If it is not, that is a strong argument for the Swift-port fallback, because the shared
core's main benefit is a single place to evolve the engines.
**Docs stay in the Android repo** under `docs/ios-port/` — the reference docs cite Kotlin `file:line`
paths throughout and only resolve there.

---

## D9 · 2026-08-01 · kotlinx-datetime pinned at 0.8.0, Kotlin stays at 2.2.21

**Why:** Phase 0 shares only `domain/`, which needs no Room-KMP or DataStore-KMP, so no Kotlin
upgrade is required. 0.8.0 is the newest that compiles against Kotlin 2.2.21.
**Reversing:** a Kotlin upgrade is independent and can happen later.

## D10 · 2026-08-01 · Phase 0 gate: **KEEP the shared core**. Proceed to Phase 1.

All eight criteria cleared.

| # | Criterion | Result |
|---|---|---|
| 1 | Android tests still green | ✅ `:app` 1017 + `:shared` 400 = 1417; conservation checked at every wave. Sole failure is `InsightHarnessTest` (live OpenRouter, HTTP 429 — documented non-deterministic) |
| 2 | Shared tests green on JVM **and** Kotlin/Native | ✅ 400 JVM, 11 iOS simulator |
| 3 | **Golden corpus bit-exact on Kotlin/Native** | ✅ **372 assertions**, green on both, and confirmed in a running app. `Double.toString()` matches the JVM's shortest repr — no hand-rolled Ryū needed |
| 4 | Boundary conversion sites | ⚠️ **112 across 26 files** — the ≤10 target was miscalibrated (see 4a). All compiler-forced and mechanical, using the shipped `toKotlinLocalDate()`/`toJavaLocalDate()` bridges. Accepted |
| 5 | Kotlin/Native compile time | ✅ cold **13.2 s**, warm **0.8 s**, XCFramework **22.5 s** (target < 3 min) |
| 6 | Swift call sites readable without a wrapper | ⚠️ Mixed. Functions read fine (`DecimalFormatKt.signed1(value:)`). **Kotlin enums export as classes, not Swift enums** → no exhaustive `switch`. Accepted; **evaluate SKIE in Phase 1** |
| 7 | No Kotlin/AGP/Gradle upgrade | ✅ none — but see 7a: kotlinx-serialization pinned **1.11.0 → 1.9.0** project-wide by the Kotlin/Native klib ABI ceiling |
| 8 | Two-repo resync tolerable | ✅ **13 s** total (10.7 s sync + 2.6 s incremental build), one command, **no manual clean** (target < 2 min) |

**Why keep it.** Criterion 3 was the one that could have invalidated the approach, and it passed
outright. The algorithms where reimplementation is most dangerous — `RebalanceEngine.size()`, the
18 detectors, `dedupKey` generation — are now provably identical across platforms rather than
hopefully similar. `size()` needed **no edits at all**. Compile and resync costs came in an order
of magnitude under their thresholds.

**What it costs, honestly.** Two standing taxes: every future `commonMain` dependency must be
checked against the Kotlin 2.2.21 klib ABI ceiling (7a), and ~112 boundary date conversions live in
`:app`, with more arriving as features touch shared types (4a). Neither is fatal. Both are worth
revisiting if the ABI ceiling blocks a dependency we actually need — at which point moving to
Kotlin 2.3.x+ becomes the better trade.

**A caveat about the process, not the decision.** The final behavioural-drift review found a real
bug the golden corpus had missed: `formatFixed` threw on scientific-notation doubles (`|v| < 1e-3`),
reachable from the flat-trend detector branches via OLS floating-point residue, which would have
silently suppressed a day's coach signal. Fixed in `4f6d96d`; corpus extended from 269 to 372
assertions. **A golden corpus is only as good as its input set** — the captured values were right,
the coverage was not. Carry that adversarial-input discipline into Phase 1's persistence work.

## D12 · 2026-08-02 · Share the persistence codecs too — `:shared` widens past `domain/`

The five JSON codecs behind the coach and rebalance stores move into `:shared/commonMain`, and iOS
calls them through the framework rather than re-declaring Swift `Codable` mirrors:
`CoachJourneySerialization`, `CoachExperimentSerialization`, `RebalanceSerialization` (all three have
**zero** platform imports and move unchanged), plus `CoachInboxSerialization` and
`PushHistorySerialization` after a `java.time` → `kotlinx-datetime` port.

**This deliberately extends D2**, which scoped sharing to `domain/`. D2's *reasoning* was the
Kotlin↔Swift interop tax, measured as `suspend`/`Flow`/`StateFlow` crossings. These codecs are pure
synchronous `String` ↔ value-type functions — the same profile as `domain/`, and the same near-zero
tax. The letter of D2 changes; its logic does not.

**Why, concretely.** A Swift mirror has to reproduce behaviour that is invisible unless you already
know it is there:
- `PushEvent.timestamp` serialises as `"2026-08-01T22:00"` — **seconds omitted when zero**. A Swift
  `ISO8601DateFormatter` emits `T22:00:00`, breaking push-history round-trip and **silently resetting
  rate limiting** for existing users.
- `RebalanceSerialization` is the one **hand-rolled** codec: always emits all 19 plan keys, omits
  `active` when null, and defaults a missing `intensity` to `STANDARD` so pre-feature records still
  decode. Not kotlinx-equivalent — hand-reimplementing it is where drift would actually occur.
- `encodeDefaults = true` is load-bearing for exactly one type (`CoachSignal`), and its
  `init { require(...) }` invariants validate on decode. Swift `Codable` inherits neither.

**Cost:** a two-file datetime port that ripples into `CoachInbox`/`PushHistory` interfaces and their
stores. Net simplification, though — `CoachDigestCoordinator` currently converts at that boundary
(`.mapValues { it.value.toKotlinLocalDate() }`) and that conversion disappears.

**Not shared:** `BackupModels.kt` stays in `:app` — it is a DTO over 19 Room entities. The backup
format therefore *is* reimplemented in Swift, which is why round-tripping a **real** Android backup
is Phase 1b's acceptance test rather than a nice-to-have.

## D13 · 2026-08-02 · Phase 1 splits into 1a (database) and 1b (stores, secrets, assets, codecs)

**1a** — GRDB, the 19 tables, all 14 migrations, record types, the non-trivial queries, and the 19
transaction bodies. Ends with a schema that round-trips.
**1b** — the 10 preference stores, Keychain, bundled assets + the version-gated exercise seed, and
the three file-format codecs. Ends with **a real Android backup importing on iOS**.

**Why:** one plan covering all of it runs 20–25 tasks before anything is verifiable end-to-end. Two
plans each end in a genuine acceptance test, and 1b's Swift patterns benefit from seeing how 1a's
actually turned out.

## D14 · 2026-08-02 · Swift persistence mirrors decode per-key, and qualify Kotlin namesakes

Two Phase 1b agents hit the same two traps independently, so both are settled here rather than
re-litigated per store.

**1. Every Swift mirror of a persisted type decodes per-key, never via synthesised `Decodable`.**

Synthesised decoding throws `keyNotFound` on the first missing key. `JSONStore` decodes tolerantly,
so that throw becomes "return the default" — meaning **adding one field to a store silently resets
every existing user's preferences**. DataStore's semantics are per-key defaults, and a hand-written
`init(from:)` using `decodeIfPresent(_:) ?? default` is what reproduces them. Applies to all ten
stores and to the backup payload's optional sections.

Corollary (from B3): decode leniently for *scalars* too, not only enums. A mistyped `"heightCm"`
would otherwise throw and wipe a profile the user can still see on screen.

**2. Where a Swift mirror shares a name with an exported Kotlin type, qualify the Kotlin one.**

`:shared` exports `PlanPreferences`, `UserProfilePreferences`, `BiologicalSex`, `ActivityLevel`,
`FitnessGoal`, `Exercise` and others under exactly those Swift names. Inside a file that
`import Shared`s, a same-named Swift type wins with no diagnostic about the shadowing.

The unqualified name belongs to **the Kotlin type**, because Phase 2+ calls the engines constantly
and those call sites should stay clean. A Swift mirror may keep the same name; any file needing both
writes `Shared.PlanPreferences` / `RecompTracker.PlanPreferences`. Verified to compile and pinned in
`RecompTrackerTests/SharedInteropTests.swift` — that suite is the executable version of
[reference/shared-codec-api.md](reference/shared-codec-api.md); trust it over the prose.

## D15–D19 · 2026-08-03 · Phase 2: glass, navigation, tabs, type, state

Settled in the [Phase 2 design session](../superpowers/specs/2026-08-02-ios-phase-2-design.md) and
proven in code. Five of the "still to decide" list below close here.

**D15 · Native iOS 26 Liquid Glass, not a port of the Android approximation.** The Android glass
layer exists *because* Compose has no native equivalent — 2,218 LOC across four files, plus six
colour tokens whose only job is to fake a material. `.glassEffect` supplies all of it, so those six
tokens were deleted rather than ported and the iOS design system landed near 400 LOC. Accepts that
the two apps are not pixel-identical.

**D16 · `TabView` + a `NavigationStack` per tab.** Android runs one shared back stack across its
tabs; iOS keeps per-tab history. A real behavioural difference, accepted because the native tab bar
is where the glass does its most visible work and that is only free inside `TabView`.

**D17 · Four tabs — Home, Body, Log, Coach — with More behind a header control.** Train is v1.1
(D4), and iOS auto-collapses past five, so the fifth slot stays empty rather than being given to
More and taken back later. **The third tab is labelled "Log", not "Food"** — its route is `food` on
both platforms but the visible label has always been Log.

**D18 · Dynamic Type, replacing Android's fixed `sp` scale.** Eleven of thirteen `AppType` tokens
map to a native text style; the four that sit outside the 11–34pt range (9pt ×2, 36pt, 44pt) carry
an explicit base size and a `relativeTo` style so they scale too. Costs pixel-parity at large sizes.

**D19 · One `@Observable` model per screen, activated by `.task`.** Android's 32 ViewModels are
uniform `MutableStateFlow<UiState>` classes, so this keeps each later screen a mechanical port.
`.task` rather than `init` fixes review **P2-21** in passing — Android's pipelines run for the
ViewModel's whole life and recompute aggregates for screens nobody is looking at.

**Measured, not assumed:** `.task` *is* cancelled and restarted on every tab switch (verified by
instrumenting the activation count and driving the simulator: 1 → 3 over two round-trips). The
re-fetch is one day query plus the week window, and no flicker is visible. `.task(id:)` is
therefore also the working equivalent of Android's `_selectedDate.flatMapLatest { }`.

## Conventions still to decide

Recorded here so they get decided *once*, deliberately, rather than drifting. Move each into a
numbered entry above when settled.

- [x] **Swift module/target layout** — one app target. Settled in practice across Phases 1–2; no
      feature framework has been needed, and buildable folders make adding files free.
- [x] **View-model shape** — **D19**
- [x] **Navigation** — **D16**
- [ ] **Error taxonomy** — the Android side collapses everything into one generic message; worth
      doing better on iOS (Phase 5)
- [ ] **Replacement for the 4 `savedStateHandle` reverse-result flows** — bindings vs a shared
      selection coordinator (Phase 3; 2 of the 4 are Train-only and defer to v1.1)
- [ ] **ATS policy** for user-supplied LLM base URLs — block cleartext, or ship an exception (Phase 5)
- [ ] **Whether to implement `selectedFont`** or drop it — dead wiring on Android (no `res/font`
      directory exists). Phase 2 kept the stored field so backups round-trip but gave it **no
      setter**; deciding needs the appearance screen, so this moves to **Phase 3**.
- [ ] **Test naming and fixture-sharing convention** with the Kotlin suite (Phase 1)
