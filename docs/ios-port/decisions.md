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

## Conventions still to decide

Recorded here so they get decided *once*, deliberately, rather than drifting. Move each into a
numbered entry above when settled.

- [ ] **Swift module/target layout** — one app target vs feature frameworks (Phase 1)
- [ ] **View-model shape** — `@Observable` class per screen vs `@State` + plain structs (Phase 2)
- [ ] **Navigation** — one `NavigationStack` behind a custom tab bar (faithful to Android's single
      shared back stack) vs `TabView` + per-tab stacks (idiomatic iOS, different behaviour) (Phase 2)
- [ ] **Error taxonomy** — the Android side collapses everything into one generic message; worth
      doing better on iOS (Phase 5)
- [ ] **Replacement for the 4 `savedStateHandle` reverse-result flows** — bindings vs a shared
      selection coordinator (Phase 3; 2 of the 4 are Train-only and defer to v1.1)
- [ ] **ATS policy** for user-supplied LLM base URLs — block cleartext, or ship an exception (Phase 5)
- [ ] **Whether to implement `selectedFont`** or drop it — it is dead wiring on Android (Phase 2)
- [ ] **Test naming and fixture-sharing convention** with the Kotlin suite (Phase 1)
