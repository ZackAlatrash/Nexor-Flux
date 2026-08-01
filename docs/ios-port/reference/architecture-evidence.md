# Reference — Architecture Evidence (KMP / CMP / alternatives)

The evidence base behind the architecture decision in
[roadmap §4](../00-feasibility-and-roadmap.md). Kept because **the shared-core question is still
open until the Phase 0 gate** — if Phase 0 goes badly, revisit this rather than re-researching it.

**Provenance:** 🟢 official docs · 🟡 vendor/commercially-interested · 🔵 independent first-hand ·
⚪ measured from this repo. **All verified 2026-08-01.** Version numbers decay fast — re-check
before acting on any of them.

---

## 1. Toolchain versions at capture

| Thing | Version | Date |
|---|---|---|
| Kotlin | **2.4.10** stable | 2026-07-14 |
| Compose Multiplatform | **1.11.1** stable; 1.12.0-beta03 | 2026-06-02 / 2026-07-28 |
| Room 2.x | **2.8.4** | 2025-11-19 |
| Room 3.x | **3.0.1** (`androidx.room3`) | 2026-07-29 |
| androidx.sqlite | **2.7.0** stable | 2026-07-01 |
| DataStore | **1.2.1** stable | 2026-03-11 |
| kotlinx-datetime | **0.8.0** — still **Alpha** | 2026-05-07 |
| Ktor | **3.5.1** | 2026-06-29 |
| kotlinx.serialization | **1.11.0** | 2026-04-09 |
| SKIE | **0.10.14**, tracks Kotlin 2.4.10 | 2026-07-27 |
| Flutter | 3.44 (Dart 3.12) | 2026-05-20 |
| React Native | 0.86 | 2026-06-11 |

⚪ **This repo:** Gradle 9.5.1, Kotlin 2.2.21, AGP 8.13.2, KSP 2.2.21-2.0.5, compileSdk 37,
minSdk 26, Compose BOM 2026.05.01.

---

## 2. The KMP stability ledger — the most decision-relevant document

🟢 [kotlinlang.org/docs/components-stability.html](https://kotlinlang.org/docs/components-stability.html)

| Component | Status | Stable since |
|---|---|---|
| Kotlin/Native | **Stable** | 1.9.0 |
| Kotlin Multiplatform | **Stable** | 1.9.20 |
| klib binaries | **Stable** | 1.9.20 |
| CocoaPods integration | **Stable** | 1.9.20 |
| kotlinx-coroutines | **Stable** | 1.3.0 |
| kotlinx-serialization | **Stable** | 1.0.0 |
| **Kotlin/Native ↔ C and Objective-C interop** | **Beta** | *since 1.3.0 (2018)* |
| **cinterop klib binaries** | **Beta** | *since 1.3.0* |
| **KMP plugin for Android Studio** | **Beta** | 0.8.0 |
| **kotlinx-datetime** | **Alpha** | 0.2.0 |
| Swift export | **Alpha** (Kotlin 2.4) | — |

> **Read those last four rows carefully.** The Objective-C interop layer that *every* Kotlin↔Swift
> call passes through has been officially **Beta since 2018**, and the date library needed to replace
> all `java.time` usage is officially **Alpha** after six years.

This is the single strongest argument for keeping the shared surface **small**, which is exactly what
the `domain/`-only scope does.

---

## 3. Room on KMP — better than expected, but read the version story

🟢 [developer.android.com/kotlin/multiplatform/room](https://developer.android.com/kotlin/multiplatform/room)

**Two parallel lines exist and secondary sources confuse them:**

| Line | Latest | Package | iOS artifacts |
|---|---|---|---|
| Room 2.x | 2.8.4 | `androidx.room` | `room-runtime-iosarm64/-iossimulatorarm64/-iosx64` — **present since 2.7.0-alpha01** |
| Room 3.x | 3.0.1 | `androidx.room3` | `room3-runtime-iosarm64/-iossimulatorarm64` (**no `iosx64`**) |

**The app's existing Room 2.8.4 already publishes iOS artifacts.** No Room 3 migration is required
to go multiplatform. Room 3 brings a package rename, removal of `SupportSQLite`/`Cursor`, mandatory
KSP, and a Flow-based `InvalidationTracker` — **an orthogonal migration, not a prerequisite.**

**Documented KMP limitations:**
- **All DAO functions must be `suspend`** or return `Flow`. Blocking DAO functions are Android-only.
- **Unavailable on KMP:** `setQueryCallback`, `setAutoCloseTimeout`,
  **`createFromAsset`/`createFromFile`/`createFromInputStream`** (pre-packaged databases),
  `enableMultiInstanceInvalidation`, RxJava/LiveData.
- Driver: `BundledSQLiteDriver` (recommended, consistent) vs `NativeSQLiteDriver` (needs the
  `-lsqlite3` linker flag — source of the notorious `Undefined symbols: '_sqlite3_bind_blob'` error).
- KSP must be configured **per target**, multiplying configuration time.
- 🟢 androidx.sqlite 2.7.0-alpha02 (2026-03-25) fixed *"missing symbols on iOS when using
  `NativeSQLiteDriver`"* ([b/434324365](https://issuetracker.google.com/434324365)) — **that class of
  linker bug was live five months before this capture.**

⚠️ **Evidence gap: no published first-hand report of a large migrated Room schema (19 entities,
14 DAOs, 14 migrations) running in production on iOS.** This is untested territory — and it is why
the recommended architecture uses **GRDB natively on iOS** instead of Room-KMP.

---

## 4. Other shared-stack components

**DataStore KMP** — 1.2.1 stable; iOS artifacts since 1.1.0-dev01.
🟢 *"Only DataStore Preferences is supported in KMP projects"* — the app uses 10 Preferences stores,
so fully supported. The cleanest port in the stack, **but not needed under the `domain/`-only scope.**

**Ktor SSE on iOS — resolved in KMP's favour.** ⚪ Verified directly in Ktor source,
`ktor-client-darwin/…/DarwinClientEngine.kt:24`:
```kotlin
override val supportedCapabilities = setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)
```
**SSE streaming on iOS via Ktor Darwin is supported.** (Ktor's docs publish no SSE-per-engine table —
a real documentation gap.) The Darwin engine is still being actively debugged though: 3.4.2 fixed a
`KtorNSURLSessionDelegate` memory leak; 3.4.3 fixed a SIGABRT when `close()` races `execute()`.

**Coroutines on iOS** — 🟢 [native-memory-manager](https://kotlinlang.org/docs/native-memory-manager.html):
**object freezing is gone**; shared heap, any-thread access. Any pre-2022 blog claiming otherwise is
obsolete. Kotlin 2.4.0 made **CMS the default GC**.

⚠️ **The constraint that remains** — 🟢 [native-arc-integration](https://kotlinlang.org/docs/native-arc-integration.html),
verbatim: *"If at least one Objective-C object is present, the retain cycle of a whole graph of
objects cannot be reclaimed, and it's impossible to break the cycle from the Kotlin side."*
You must use `weak`/`unowned` manually.

🔵 Google Workspace at KotlinConf 2025 (Docs on iOS, migrating off J2ObjC): tight loops ~20% faster
than J2ObjC, app-level 2–5% at high percentiles, but *"performance gain is traded off with GC time"*
and *"the memory profile changes dramatically."* **Their flagship KMP deployment required a
Kotlin/Native compiler lead on staff.** Notably, **they use native UI.**

---

## 5. The interop tax — quantified

Without a helper library, 🟢 [kotlin-swift-interopedia](https://github.com/kotlin-hands-on/kotlin-swift-interopedia):

| Kotlin | What Swift gets |
|---|---|
| `Flow<T>` | Callback collector; **generic type lost → `Any?`**; **no cancellation** |
| `suspend fun` | Completion handler; Swift `async` is *"highly experimental"* (KT-47610) |
| `sealed class` | Class hierarchy; `switch` **requires `default`** — no exhaustiveness |
| `sealed interface` | Unrelated protocols |
| Default arguments | **All arguments must be specified** |
| Generic interfaces | **Not supported** |
| `reified` functions | **Crash at runtime** |
| Primitive boxes | `KotlinInt`/`KotlinBoolean`; manual casting |

⚪ **Measured surface in this repo — the basis of the `domain/`-only decision:**

| | `suspend fun` | `Flow<` | `StateFlow<` | `sealed` |
|---|---:|---:|---:|---:|
| **`domain/`** | **0** | **0** | **0** | 6 |
| `ai/` + `data/` | 395 | 106 | 28 | — |

**Sharing `domain/` touches almost none of the pain surface. Sharing `ai/`+`data/` touches all of it.**

### 5.1 SKIE vs Swift export

**SKIE 0.10.14**, free, open source, compiler-plugin. Fixes the table above: Swift enums with
exhaustive switch, `onEnum(of:)` for sealed classes, generated default-argument overloads, real
Swift `async` with two-way cancellation, **Flows as `AsyncSequence` with generics preserved**. Used
by Amazon Music in production.

**Swift export: Alpha as of Kotlin 2.4** — 🟢 *"currently in Alpha and still incomplete, so breaking
changes are expected."* Documented limitations: types inheriting `List`/`Set`/`Map` are ignored
([KT-80416](https://youtrack.jetbrains.com/issue/KT-80416)); generics type-erased to upper bounds;
no cross-language inheritance; direct-integration projects only.

🟡 Touchlab (double COI — they sell KMP consulting *and* author SKIE, but every limitation is
corroborated by JetBrains' own docs) call SKIE *"the definitive choice"* and Swift export suitable
for *"personal projects or small-scope MVPs."*

🟡 JetBrains' stated goal is *"in 2026, aiming for a stable release."* As of 2026-08-01 it is Alpha.
**Treat "Swift export will be stable soon" as an unmet forecast, not a plan input.**

> **Under the `domain/`-only scope, SKIE is a nice-to-have** (6 sealed types would get exhaustive
> switches) **rather than load-bearing.** That is a meaningful de-risking of the small-vendor
> dependency.

---

## 6. Tooling friction (applies at any KMP scope)

🟢 [native-debugging](https://kotlinlang.org/docs/native-debugging.html) — LLDB works for breakpoints
and stepping, **but** documented limitations include:
> *"Expression evaluation in debugger tools is not supported, and currently there are no plans for
> implementing it."*

🔵 Corroborating first-hand: Karel van der Merwe (2025-06-22) *"Breakpoints in shared code rarely
worked in Xcode"*; Luis G. Valle, Perk (2025-12-02) *"XCode debugging and local development tooling
remain significant limitations."*

**Crash reporting is broken by default.** 🟡 Touchlab's CrashKiOS docs: Kotlin exceptions bubble to
an unhandled-exception hook, so *"the crash report shows the point at which we call into Kotlin from
Swift/ObjC"* — not the Kotlin frame that threw. **Budget [CrashKiOS](https://github.com/touchlab/CrashKiOS)
as mandatory, not optional.**

**Build times** 🟢 [native-improving-compilation-time](https://kotlinlang.org/docs/native-improving-compilation-time.html):
release binaries take *"an order of magnitude more time"* than debug; **the link step is not
incremental**; build one architecture locally (`embedAndSignAppleFrameworkForXcode`), never
XCFramework; preserve `~/.konan`.

**Two structural taxes:** 🔵 your Xcode upgrade cadence becomes coupled to Kotlin releases
(*"you have to lag behind a bit on Xcode updates"*); and 🔵 **Swift 6 strict concurrency rejects
Kotlin data classes as non-`Sendable`** — a 2025/26 tax absent from older writeups.

**Kotlin 2.4.0 raised default minimum Apple targets** to iOS 15 (from 14).

---

## 7. Compose Multiplatform — why it was rejected

**iOS declared Stable in CMP 1.8.0 (2025-05-06).** 🟡 That announcement claims *"scrolling performance
is on par with SwiftUI"*, *"~9 MB"* overhead, and *"over 96% of teams report no major performance
concerns"* — **no methodology, no sample size, no source for any of the three**, and **no limitations
section at all.**

🟢 Meanwhile, from JetBrains' own release notes:

| Release | Date | iOS fix |
|---|---|---|
| 1.10.0 | 2026-01-13 | Crash dragging two Scrollables; `NSRangeException` on back gesture |
| 1.11.0 | 2026-05-13 | **Scrolling inertia on short gestures**; **unexpected fling** |
| 1.12.0-beta01 | 2026-06-30 | **Swipe-back conflict with `HorizontalPager`** |
| 1.12.0-beta02 | 2026-07-14 | **Frame drops dragging scrollable content**; `ComposeUIViewController` failing inside SwiftUI |
| 1.12.0-beta03 | 2026-07-28 | Taps missed near screen edge; text-input crash; disposed-`AccessibilityElement` crash |

**Fling physics were fixed in May 2026 — twelve months after the "on par with SwiftUI" claim.**

⚠️ **Methodological trap:** JetBrains bulk-closed GitHub issues in 2024 and migrated to YouTrack.
[compose-multiplatform#3632](https://github.com/JetBrains/compose-multiplatform/issues/3632) shows
`closed` on GitHub but [CMP-3632](https://youtrack.jetbrains.com/issue/CMP-3632) is **Open, Major,
last updated 2026-07-22**. **Use YouTrack `project: CMP`, not GitHub.**
At capture: **139 open issues with `Subsystem: iOS`.**

**Text input** — native UIKit text editing arrived only in **1.11.0 (2026-05-13)** and is **still
opt-in** (`PlatformImeOptions.usingNativeTextInput`). What the flag enables tells you what was
missing until then: native caret, magnifier, double/triple-tap selection, iOS selection handles,
Translate/Look Up/Share, **autocorrect**, and **Autofill**.
[CMP-10598 "Make Native Text Input default"](https://youtrack.jetbrains.com/issue/CMP-10598) was
filed **2026-07-30**.

**Accessibility** — 🟢 the docs claim VoiceOver support with only "Material3 lacks high-contrast
colours" as a gap. But open in YouTrack: [CMP-9055](https://youtrack.jetbrains.com/issue/CMP-9055)
**semantics roles Checkbox and Tab are ignored by VoiceOver**; CMP-8887 `CollectionInfo` broken;
CMP-10420 rotator broken after focus change; CMP-10207 `BasicTextField` not read on tap;
CMP-9360 Full Keyboard Access can't enter Popups/Dialogs. **Ignored Checkbox/Tab roles would fail an
audit.**

**🔴 The decisive finding** — 🟢 [ios-liquid-glass](https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html)
(2026-07-21), verbatim:
> *"To adopt it in a Compose Multiplatform app, **you need a native SwiftUI shell**, because Liquid
> Glass effects are rendered by the system through native `TabView`, `NavigationStack`, and toolbar
> APIs."*

and when you do, *"Compose still renders each screen's content but **no longer manages the back
stack**."* There is **no open implementation issue** for Compose rendering the material.

**For an app whose entire identity is a glass design language, the shared-UI win is halved before you
start.**

**Size / memory** — sources conflict badly:

| Source | Date | Size | Memory |
|---|---|---|---|
| 🟡 JetBrains 1.8.0 post | 2025-05 | *"~9 MB"* unsourced | — |
| 🔵 Jacob Ras, same app 4 ways | 2023-09, CMP 1.4/1.5 **alpha** | 24.8 MB IPA vs **1.7 MB SwiftUI** | — |
| 🟡🔵 Software Mansion, public repos | **2026-07-02**, CMP 1.10 vs RN 0.81 | 11.2 MB download / 32.1 MB installed (RN 9.9 / 29.7) | **CMP 157–251 MB vs RN 44.7–131.4 MB** |

The 2026 independent benchmark shows size roughly at RN parity — **the 2023 number is stale** — but
**3–4× the RAM**, attributed to Skia's persistent buffers (Android reuses OS Skia; iOS must ship it).
Software Mansion is an RN tooling agency (COI), but their Android results strongly favour KMP, which
lends credibility.

**Who ships CMP *UI* on iOS:** Sony (headphone companion, *"millions of users"* — strongest named
datapoint), Wrike, Markaz (100+ screens), Bitkey by Block (95% shared), BiliBili (one feature),
Meituan, Physics Wallah, TravelPerk/Perk, The Respawn.

⚠️ **Do not cite these as CMP-UI proof — they are logic-only KMP with native UI:** Netflix, Cash App,
Forbes, McDonald's, Duolingo, Bolt, Baidu, 9GAG, Booking.com, X, Careem, Meetup, Quizlet.

🟢 **Google's own position:** [developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform)
commits to **shared business logic**; shared UI is attributed to *JetBrains*. In Google's AndroidX KMP
table the iOS implementations of `compose`, `navigation`, and `viewModel-compose` are labelled
**"Built by JetBrains."**

**No hot reload on iOS** — Compose Hot Reload requires a desktop target.

---

## 8. Flutter / React Native — why rejected

**The dispositive argument:** both discard all 54,280 lines of Kotlin *and* all 25,845 lines of tests
— including the 6,347-line domain engine and its ~6,100 lines of tests, which are the app's most
valuable and least reproducible assets. A rewrite is rational when existing code is a liability; here
it is the opposite.

🔵 **Airbnb, "Sunsetting React Native" (2018-06-19)** is eight years stale on RN specifics
(pre-Hermes/Fabric/Bridgeless). Its **transferable** lesson is not: 80,000 lines across 220 RN
screens beside a native codebase 10× larger meant paying for *three* platforms.
**A partial cross-platform migration is worse than either endpoint.**

**Flutter's specific disqualifier — the glass design system.**
🟢 [docs.flutter.dev/platform-integration/ios/ios-latest] lists **Liquid Glass as unsupported**.
[flutter#170310](https://github.com/flutter/flutter/issues/170310) (opened 2025-06-10) is **still
open**, with the team stating they are not developing it and will not accept contributions.
Compounded by the **Material/Cupertino code freeze** (2026-04-07) moving both libraries to pub.dev,
and a multi-year unresolved **Impeller blur-jank** record (#126353, #138615, #161297) whose only
documented workaround (`FLTEnableImpeller: false`) **no longer exists on iOS**.

**React Native's specific disqualifier — health data.** `react-native-health` has **122 open issues**
including **#443 "iOS build fails on RN 0.86 / New Architecture" (2026-07-13)**.
`@kingstinct/react-native-healthkit` v9 is the credible escape route but unverified. RN would also
force **two implementations of the glass design system** — `@shopify/react-native-skia`'s
`BackdropBlur` only blurs inside the Skia Canvas, so it cannot frost arbitrary RN views.

RN does win on two points worth noting: VisionCamera 5.2.0 uses **ML Kit on both platforms**
(preserving exact barcode parity), and real Apple glass is available via `expo-glass-effect` /
`@callstack/liquid-glass`.

---

## 9. Sharing-percentage claims — treat with scepticism

| Claim | Reality |
|---|---|
| "Duolingo 55%" | **Misattribution** — belongs to Rodrigo Sicarelli's KotlinConf'25 talk; employer unconfirmed |
| Forbes 80%, Bitkey 95%, Markaz 91–98%, Respawn 96% | Mix greenfield-LOC, shared-module-LOC, and hand-waves. **None from a migrated brownfield app of this size** |
| "KMP adoption 7% → 18%" | 🟡 A JetBrains marketing page citing their own self-selected survey; **the denominator is never stated** and the figure doesn't appear in the survey write-up |

⚪ **Ceiling for this repo, measured:**

| Layer | LOC | Logic-only KMP | KMP + CMP |
|---|---:|---|---|
| `ui/` | 35,322 | **0%** | ~85–90% |
| `data/` | 8,525 | ~85% | same |
| `domain/` | 6,347 | **~98%** | same |
| `ai/` | 3,069 | ~95% | same |
| `core/` | 1,017 | ~90% | same |
| **Ceiling** | | **~35%** | **~85%** |

**The chosen `domain/`-only scope shares ~11% of LOC but ~100% of the algorithmic IP** — the part
where divergence is dangerous and reimplementation is expensive.

---

## 10. Evidence gaps — what nobody could answer

1. **No company engineering-blog postmortem of a KMP revert exists.** Absence of evidence, not a
   clean bill of health; Reddit and Kotlin Slack were bot-blocked during research.
2. **No independent post-1.8.0 CMP-iOS scroll/jank benchmark.** All "it's fixed now" claims trace to
   JetBrains or to SEO sites paraphrasing JetBrains.
3. **No production report of a large migrated Room schema on iOS.**
4. **No credible solo-dev timeline** for an iOS port via any route.
5. **No user-facing evidence** on whether users notice CMP vs native — no App Store review analysis,
   no A/B test. Claims in both directions are developer impressions.
6. **CJK/IME on CMP iOS: no evidence either way.**
7. 🟢 **Two official pages disagree** on the AGP 9 legacy-variant-API removal timeline (JetBrains says
   ~Q2 2026, Google says H2 2026).

**Discourse-quality warning:** the 2026 "Is CMP production-ready?" genre is overwhelmingly
content-farm output with zero measurements. Several assert *"the 2025 updates fixed the scroll physics
engine"* — the fling fixes actually landed **May 2026**. Hacker News has essentially no CMP-iOS
discussion (the 1.8.0 "Stable" submission: 5 points, 0 comments).

---

## 11. If Phase 0 succeeds — notes for the `:shared` module

⚪ Adopting KMP does **not** restructure the Android app module. Layout becomes `app/` + `shared/` +
`ios/`; `:app` keeps `com.android.application` and adds `implementation(project(":shared"))`.

⚠️ **Two forward-looking constraints:**
- 🟢 [AGP 9 migration for multiplatform](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html):
  AGP 9.0+ is **no longer compatible with `com.android.application`/`library` inside a KMP module** —
  the shared module must use `com.android.kotlin.multiplatform.library`. The
  `android.enableLegacyVariantApi=true` escape hatch is removed in AGP 10.
- 🟢 [The KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin): **no product
  flavors, no `BuildConfig`, no data/view binding**, and Android unit/device tests are **disabled by
  default**. Check for `BuildConfig` usage before moving anything into `:shared`.

⚠️ **Verify:** Kotlin 2.4.0 states Gradle 7.6.3–9.5.0 compatibility; ⚪ this project uses **Gradle
9.5.1**.

⚪ **Three free wins for this specific project:**
- **No Hilt.** The official CMP-migration doc names Hilt→Koin as the hardest blocker.
  `core/AppContainer` is manual DI — **the worst step is skipped entirely.**
- `domain/` and `ai/` already have **zero androidx imports** — ~9,400 LOC already `commonMain`-shaped.
- ⚪ The glass design system's dependencies are **already multiplatform**: `io.github.kyant0` publishes
  `backdrop-iosarm64`, `backdrop-iossimulatorarm64`, `backdrop-macosarm64` (and the same for
  `shapes`) at 2.0.0. `coil3`, `vico`, and `sh.calvin.reorderable` also publish iOS artifacts. *(Not
  needed under the chosen architecture, but it means a CMP fallback is not blocked by dependencies.)*

⚪ **Test-suite reality:** 216 of 222 test files import JUnit4 (**unavailable in `commonTest`**), 0 use
`kotlin.test`, 26 use Mockito (**no Kotlin/Native support, architecturally**), 11 use Robolectric.
**Cheapest correct path: leave all 222 tests in `androidUnitTest` unchanged** and write only *new*
`commonTest` coverage for shared code.
