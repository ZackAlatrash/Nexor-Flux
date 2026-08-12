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

**D20 · NEVO is not built on iOS.** No tab, no catalogue query, no CSV importer. Its only fill
mechanisms are the CSV import (v1.1 per **D4**) and a backup restore, so on a fresh install the tab
is permanently empty behind a message pointing at a Settings screen that will not exist.
`catalog_foods` and the backup's `catalogFoods` key **stay untouched**, so Phase 1a's schema parity
and Phase 1b's round-trip both still hold — iOS simply never reads the table. Phase 3a's
verification greps `Features/` and `Shell/` for `CatalogFood` to keep it that way.

**D21 · Open Food Facts moves to Phase 4**, with the barcode scanner, which is its natural
companion. Removing it also removes Phase 3a's only network path — and the camera button inside the
library's search field goes with it.

**D22 · Pickers are sheets with completion closures.** Settles the reverse-result convention below.
Android's `FoodLibraryScreen(onIngredientPicked:)` is *already* a closure; the Base64/JSON encoding
around it exists only because Compose nav arguments must be strings. All four `savedStateHandle`
flows are outside Phase 3a — **three** are Train/v1.1 and the fourth is the Phase 4 scanner — so no
general mechanism is needed yet.

**D23 · Dismissal uses the environment's `dismiss` action**, not a `navigateBack` flag on the model.
`RecipeBuilderViewModel` publishes `navigateBack: StateFlow<Boolean>` only because Compose gives it
no upward signal; every Phase 3 form screen would otherwise grow the same field.

**D24 · Every chart is hand-drawn SwiftUI. Swift Charts is not linked and will not be.** The
sparkline needs a left-to-right clip reveal, dots that pop as the reveal passes them, a 3-stop
gradient stroke and a 3-layer glow terminal dot; the rebalance bars need per-bar *staggered*
animation and a divider injected between two groups. Swift Charts models none of these and fights
all of them. `WeekStrip.swift` proved the hand-drawn path is cheap. Task 18 greps for
`import Charts` to keep it that way.

**D25 · One `StreakModel` and one streak fetch**, owned by the streak feature rather than by any
screen. The streak card renders on Dashboard, Body, Food Log and Train; two implementations would
disagree, and the rules are subtle enough (calendar days *spanned* not hits, a trailing grace
period, the lenient union zone, base-goal-always for steps) that the disagreement would be
invisible.

**D26 · Rebalance ships its deterministic copy in 3b**, not deferred to Phase 5.
`RebalanceCopyService` is decoration: the ViewModel seeds `RebalanceCopyPromptBuilder.fallback(...)`
**synchronously** and only then launches a job that may replace it. The fallback is the shipping
string. Phase 5 becomes a one-line swap. The AI *badge* and the "Generating" edge glow stay out — an
AI badge on a screen with no AI is a lie.

**D27 · `buildStreaks` is ported to Swift, not moved into `:shared`** — despite `:shared` being its
architecturally correct home (a shared kdoc already references it). Standing rule 2 says every phase
after 0 only adds files under the iOS repo and is parallel-safe with Android work, and the user runs
concurrent Android sessions. The cost is 73 lines of duplication and a drift risk; the mitigation is
that the Swift port carries its own tests, transcribed from the Kotlin ones — **and writing them
found that the Kotlin tests covered only 5 of the 9 rules**, so the port is now better tested than
the original. Moving it remains the right follow-up.

**D28 · The app follows the device locale, everywhere. Owner decision.** A German reader sees
`2.288` and `74,7`; a US reader sees `2,288` and `74.7`. There is no US-pinned corner and no "it's
only a weight" exception. This settles the open question 3b left: calories already went through
`.formatted(.number.grouping(.automatic))` while weights went through `String(format: "%.1f", …)`,
which takes **no locale at all** — the Dashboard showed `2,288` in one tile and `74.7` twelve points
away. `DesignSystem/Formatters.swift` owns the three spellings (`grouped` for quantities, `plain`
for axis ticks and ranges where a separator competes with an en dash, `oneDecimal`/`signedOneDecimal`
for every former `%.1f`). Each takes a `locale` defaulting to the device's, so tests pin `en_US` and
`de_DE` explicitly rather than reading whatever the simulator is set to. Note this **diverges from
Android**, which pins `Locale.US` in places. 🔴 One exemption is still open — `RebalanceCopy`, whose
eight interpolations are asserted character-for-character against `RebalanceCopyServiceTest`. See
STATUS *Blocked / needs you* item 5.

**D29 · ~~Two header tiers on iOS~~ — superseded by D32 for the tab roots.** The half that stands:
**the Android design-system doc is normative for iOS too** wherever a rule is about structure rather
than about Compose. iOS had grown four different spellings of a tab title because that rule was
written down in `docs/design-system.md` and the port never restated it. The half that did not: this
consolidated the four roots onto a shared hand-rolled `ScreenHeader`, which was the wrong of the two
systems in the app. **D32** consolidates them onto the native title instead.

**D30 · A preference write that did not land throws.** `JSONStore.set` was two `try?`s with the cache
updated and every observer notified *before* either was attempted, so a write that never reached disk
repainted the UI as though it had and the old value came back on next launch. It now propagates the
error and rolls the cache back to what is actually stored, so a caller that swallows it still
converges on the disk instead of drifting until relaunch. ⚠️ **This does not change reads**, which
stay tolerant (a malformed file returns the default) — the two are different questions. A bad *read*
has a sensible answer and nobody is waiting on it; a failed *write* means the decision the user just
made did not happen, and only they can be told.

**D31 · `minimumTapTarget()` goes on the outside of a `Button`, and 44pt is a ceiling.** Settled
empirically rather than by reading — an audit claimed the existing spelling was broken, so Wave 1
built a harness and measured actual tap delivery. The outside spelling works; the inside one does
not; and the system will not expand a control's hit region much past ~44pt however it is asked, so
a larger number is not a bigger target. ⚠️ The one deliberate exception is `DismissButton` at 34pt:
a card header is a single line of 9pt section label, and a 44pt control sets the header's height to
44 (Android's is 28). Every surface using it has a second, larger way out.

**D32 · One title system for the whole app, and it is the platform's — the native large title.**
Supersedes the tab-root half of **D29**. Owner's call, asked for explicitly: *"think of something
iOS native."*

The port had inherited **three** behaviours across four tab roots and a fourth on pushed screens.
Dashboard and Food Log pinned a hand-rolled 28pt `VStack` header above their `ScrollView`; Body put
the same component *inside* its scroll, so its title left the screen entirely; the placeholders drew
it above a `ContentUnavailableView` that never scrolled; and Streak Stats, Food Library and Recipe
Builder already used the native large title with collapse-on-scroll. Each was a faithful port —
Android's `DashboardScreen` puts its header outside the `LazyColumn` and `BodyRecoveryScreen` puts
its in an `item { }` — so the port inherited an inconsistency that is invisible on Android, which
has no platform title to be inconsistent *with*.

Every root now calls `.screenTitle(_:subtitle:)` (`DesignSystem/ScreenTitle.swift`):
`.navigationTitle` + iOS 26's `.navigationSubtitle` + `.large`. Trailing controls move into
`.toolbar { ToolbarItem(placement: .topBarTrailing) }`. `ScreenHeader` and `DayHeader` are deleted
and `RootTabView`'s `.toolbar(.hidden, for: .navigationBar)` is gone.

🔴 **The large title is a behaviour, not a style, and a `VStack` gets none of it:** collapse into a
centred inline title with the scroll-edge glass appearing exactly as content passes under it;
tap-status-bar-to-scroll-to-top; the rotor heading VoiceOver lands on; the system Dynamic Type ramp;
the push/back-swipe title animation; and the tab bar's iOS 26 scroll-minimise reading the same
scroll view.

⚠️ **A deliberate divergence from Android** — layout matches Android by default (**D15**), but a
title system is behaviour and Android has no collapse to match. ⚠️ **Food Log's day steppers came
out better**: they were the whole reason that screen pinned its header, and in the toolbar they stay
visible *after* the title collapses, which the pinned header only managed by never collapsing.
Verified on device across all four tabs, the collapse, the steppers and the push seam.

**D33 · A Swift `PlanRepository` owns every plan write.** Confirmed as planned. `save()` appends a
`plan_versions` row effective today **only when the six day-judging fields moved**, so a threshold
edit or the Health Connect toggle writes preferences and appends nothing — that condition is what
keeps the table a ledger of plans rather than a changelog of settings taps. `resetDefaults()` is
unconditional, matching Android. The store's three write methods are renamed
`unsafe*BypassingLedger`, because Swift has no way to make them unreachable inside one module the
way Android's `PlanPreferencesSource` interface does.

**D34 · ~~`selectedFont` is dropped from the UI~~ — reversed the same day, see D39.** The reasoning
stands and is worth keeping: Android's Default / Space / Jakarta row changes nothing, because no
`res/font` directory exists, so *porting* it would have meant writing a control known to be inert.
The entry said reversal was open if the row were made real. It was.

**D39 · The font picker is real on iOS, with three system designs rather than two bundled files.**
Owner's decision, taken against the stated alternative of downloading Space Grotesk and Plus Jakarta
Sans from Google Fonts and committing ~250 KB of binary.

`AppFontDesign` offers **Default / Rounded / Serif** — SF Pro, SF Pro Rounded and New York. They
bring what a bundled TTF does not: full Dynamic Type ramps, every weight and optical size, correct
metrics in every script the device supports, and no bytes in the app. ⚠️ `.monospaced` is
deliberately absent — a fine design for code and a poor one for the coach's prose, which this
setting also re-renders.

🔴 **It takes two mechanisms, not one.** `.fontDesign` in `ThemeHost` re-typefaces every `Text` in
one line, because no `AppType` token names a design — and it stops dead at the **navigation bar's
large title** and the **tab bar's labels**, which are UIKit. Since **D32** made the native large
title the app's one title system, that is the biggest text on every screen; leaving it in SF Pro
read as a bug rather than as a boundary. `ChromeTypeface` covers both bars via appearance proxies
**plus a sweep over the bars already on screen** — a proxy does not retroactively restyle a live
view, so without the sweep the setting appears to do nothing until you navigate.

⚠️ **The stored vocabularies diverge**: Android writes `"space"`/`"jakarta"`, iOS writes
`"rounded"`/`"serif"`; `"default"` is shared. Both sides fall back to their own default on an
unrecognised value, so a backup crossing either way degrades to the default typeface rather than to
a broken read. That is a shrug only because **the field is inert on Android** — if Android ever
ships real fonts, `AppFontDesign.isAndroidValue(_:)` is where the two vocabularies get reconciled.

**D40 · `UsageTracker` is fire-and-forget, and it is the one place a swallowed write error is
correct.** Everywhere else the rule is the opposite — `JSONStore.set` was changed in 3b precisely so
a failed write cannot repaint the UI as though it landed (**D30**), and `FoodLogModel.deleteEntry`
says the same for the database. Telemetry is not a feature the app depends on: an insert that could
surface an error, block a tap, or roll back the transaction it rode in on would trade a real
interaction for a count nobody is waiting for. It writes on a **detached** task, which is also what
guarantees it cannot join the caller's transaction. ⚠️ The inversion has to be stated at the call
site; without the note the `try?` reads as exactly the bug D30 exists to prevent.

**D41 · The rebalance debug scenarios are ported to Swift.** Same call **D27** made for
`buildStreaks`: `RebalanceDebugScenarios.kt` lives in Android's `data/` layer, and standing rule 2
says a phase after 0 only adds files under the iOS repo.

**D42 · Onboarding writes through `PlanRepository`, so first run stamps a plan version.** Android's
`finish()` already called `planRepository.save()`; on iOS that now appends a `plan_versions` row
effective on day 1 (**D33**), which is what makes every day the user ever logs resolve against a
real version rather than the fallback. A free consequence of ordering 3c's ledger fix first.

**D43 · Imperial input is a display concern; storage is metric.** The wizard carries
`heightFeet`/`heightInches` beside `height` and reads weight and waist per `useMetric`. Only cm and
kg leave the draft, because every engine in `:shared` takes metric. ⚠️ **Flipping units clears every
measurement** — converting silently would change a number the user entered, and keeping them would
read 80 kg as 80 lb.

**D44 · The app's rebalance coordinator lives on `AppContainer`.** Found while building the
Developer screen: `RebalanceModel.live` built a coordinator per screen, and the ended-plan notice is
**in-memory on the instance** — so a debug scenario's note would never have reached the Dashboard's
card. The persisted state propagates anyway now that stores are shared per name (**D37**), which is
the half-working behaviour that makes this class of bug hard to see. Android's `CLAUDE.md` says the
same thing: *"Every coordinator lives in `AppContainer` on `appScope`."*

## Conventions still to decide

**D35 · Body Edit reuses `CheckInDraft`.** Confirmed, and it paid twice. Two extractions fell out of
it — `CheckInFormFields` (the nine inputs, now shared by the check-in sheet and the past-day editor)
and `CheckInWriter` (the whole-row upsert plus its steps reconciliation). Android keeps the two
apart and its copies already disagree about `stepsEdited`, which is the drift this avoids.

**D36 · More is pushed from Home.** Confirmed. The Dashboard avatar, drawn-but-inert since 3b, is
the entry point.

**D37 · One `JSONStore` per named file.** Found on the first live check of Appearance: tapping an
accent wrote to disk and nothing moved.

Every model resolves its store as `injected ?? (try? SomeStore())`, so a fresh `JSONStore` was built
at each call site. **Two instances over one file are two caches and two observer lists** — Appearance
wrote through one while `ThemeHost` observed another. Plan → Dashboard and Plan → Food Log were
queued up behind the same silence; the plan observation added in this phase would never have fired.

`JSONStore.shared(name:default:)` now returns the one instance for a name. A `static func` rather
than an initialiser, because an actor's `init` cannot reassign `self`. ⚠️ The **URL** initialiser
stays unshared — it is what tests use, and sharing by throwaway URL would leak state between them.

**D38 · An unbuilt More row is shown and disabled, with its phase named.** Not hidden. Hiding would
make the hub change shape three more times and answer nothing when someone goes looking for Data &
Backup; a row that pushes nothing is the dead affordance this codebase refuses everywhere else. The
list of what is built lives on `MoreDestination.arrivingIn` and has a test, so a landed phase that
forgets to update it fails rather than shipping a greyed row next to the screen it opens.

**D40 · `UsageTracker` is fire-and-forget, and it is the one place a swallowed write error is
correct.** Everywhere else the rule is the opposite — `JSONStore.set` was changed in 3b precisely so
a failed write cannot repaint the UI as though it landed (**D30**), and `FoodLogModel.deleteEntry`
says the same for the database. Telemetry is not a feature the app depends on: an insert that could
surface an error, block a tap, or roll back the transaction it rode in on would trade a real
interaction for a count nobody is waiting for. It writes on a **detached** task, which is also what
guarantees it cannot join the caller's transaction. ⚠️ The inversion has to be stated at the call
site; without the note the `try?` reads as exactly the bug D30 exists to prevent.

**D41 · The rebalance debug scenarios are ported to Swift.** Same call **D27** made for
`buildStreaks`: `RebalanceDebugScenarios.kt` lives in Android's `data/` layer, and standing rule 2
says a phase after 0 only adds files under the iOS repo.

**D42 · Onboarding writes through `PlanRepository`, so first run stamps a plan version.** Android's
`finish()` already called `planRepository.save()`; on iOS that now appends a `plan_versions` row
effective on day 1 (**D33**), which is what makes every day the user ever logs resolve against a
real version rather than the fallback. A free consequence of ordering 3c's ledger fix first.

**D43 · Imperial input is a display concern; storage is metric.** The wizard carries
`heightFeet`/`heightInches` beside `height` and reads weight and waist per `useMetric`. Only cm and
kg leave the draft, because every engine in `:shared` takes metric. ⚠️ **Flipping units clears every
measurement** — converting silently would change a number the user entered, and keeping them would
read 80 kg as 80 lb.

**D44 · The app's rebalance coordinator lives on `AppContainer`.** Found while building the
Developer screen: `RebalanceModel.live` built a coordinator per screen, and the ended-plan notice is
**in-memory on the instance** — so a debug scenario's note would never have reached the Dashboard's
card. The persisted state propagates anyway now that stores are shared per name (**D37**), which is
the half-working behaviour that makes this class of bug hard to see. Android's `CLAUDE.md` says the
same thing: *"Every coordinator lives in `AppContainer` on `appScope`."*

## Conventions still to decide

Recorded here so they get decided *once*, deliberately, rather than drifting. Move each into a
numbered entry above when settled.

- [x] **Swift module/target layout** — one app target. Settled in practice across Phases 1–2; no
      feature framework has been needed, and buildable folders make adding files free.
- [x] **View-model shape** — **D19**
- [x] **Navigation** — **D16**
- [ ] **Error taxonomy** — the Android side collapses everything into one generic message; worth
      doing better on iOS (Phase 5)
- [x] **Replacement for the 4 `savedStateHandle` reverse-result flows** — **D22**. (Corrects the
      earlier count: **3** of the 4 are Train-only and defer to v1.1, not 2; the fourth is the
      Phase 4 barcode scanner.)
- [ ] **ATS policy** for user-supplied LLM base URLs — block cleartext, or ship an exception (Phase 5)
- [ ] **Whether `RebalanceCopy` is exempt from D28** — localising its eight interpolations breaks a
      character-for-character parity contract with `RebalanceCopyServiceTest`. Needs the owner.
- [ ] **Test naming and fixture-sharing convention** with the Kotlin suite (Phase 1)

**D53 · The app's backdrop is Android's artwork, in eleven imagesets.** The owner asked for the
same background the Android app has — per accent theme, and per light/dark. The twenty-two
`bg_<accent>_<mode>` files are copied rather than re-drawn: the point is that the two apps look
like one app.

🔴 **WebP in an asset catalog compiles to nothing, and says nothing.** Xcode accepted the files,
`actool` ran over them without a warning, and the built `Assets.car` held only the eleven colours —
so the app rendered no background at all while looking plausible, because the accent chrome already
tinted the same corner. They are **HEIC** now, and the check that matters is reading `Assets.car`
(`assetutil --info`), not looking at the screen.

**Eleven imagesets, not twenty-two images.** Each holds the light artwork plus a
`luminosity: dark` variant, so `Image("bg_violet")` is already mode-correct — no Swift reads the
colour scheme, and the light/dark crossfade is the system's. Android needs a 22-branch `when` whose
`else` quietly returns Silver; the Swift equivalent is one string and a test that every theme
resolves.

⚠️ **`Form` and `List` paint an opaque background where a `ScrollView` does not**, so a settings-
style screen silently opts out of the backdrop. Two screens needed
`.scrollContentBackground(.hidden)`; any future `Form` will too.

🔴 **The backdrop is applied on `screenTitle`, and that is the only placement that works.** Three
that should have — `ThemeHost`'s content, `.background` on the `NavigationStack`, and
`.containerBackground(for: .navigation)` — are painted over: the first by the `TabView` (nothing
visible at all), the other two by every pushed screen. Only a background *inside* a screen's own
content survives, so it rides on the one modifier every screen already has (**D32**). A screen
cannot forget its backdrop without forgetting its title.

⚠️ **This was reported as working when it was not**, and the way it passed is worth remembering:
the check was "the artwork ships in `Assets.car`" plus "a top-left tint follows the accent" — and
the tint was `DashboardScreen.ambientOrbs`, the Dashboard's own accent orb in the same corner. The
test that settles it is replacing the whole backdrop with a flat colour. No unit test can see that
a view is covered; the `everyThemeHasArtwork` assertion passed the whole time.

The parallax is **re-derived, not transcribed** — Android's quaternion×0.28 with a ±0.09 clamp
works back to saturation at ~37° of tilt, which Core Motion expresses as one angle. It also fixes
an axis mix-up (Android maps rotation about *x* to `translationX`, so its backdrop slides sideways
when the phone tilts forwards) and adds the Reduce Motion gate the map flagged as missing.

**D54 · Titles are `.inlineLarge`, not `.large`.** A large title is a *second row*: the bar's own
row carries the back button and the toolbar items, and the title is a block beneath it. On a tab
root whose only item is a trailing avatar that row is empty, so every screen opened with ~65pt of
nothing above its title.

⚠️ **The avatar was not the cause**, though it is what draws the eye — it is the one thing floating
in the empty band. Removing the toolbar item entirely left the title at exactly the same height, and
a stock Settings screenshot puts its own title at the same y. The measurement is the point: the
suspect that looks guilty here is innocent, and two screenshots settle it.

`.toolbarTitleDisplayMode(.inlineLarge)` moves the title into that row. **Everything D32 bought is
kept** — the collapse to a centred inline title on scroll, scroll-to-top, the accessibility heading,
the Dynamic Type ramp, the tab-bar minimise. ⚠️ **Pushed screens change**: they now open with the
platform's centred inline title rather than a large one, which is iOS's own convention for a push
and removes the same empty row there.

⚠️ `.navigationBarTitleDisplayMode(.large)` is **deleted, not left unset** — it wins over
`.inlineLarge` and silently restores the gap. The first attempt at this appeared to do nothing for
exactly that reason.

🔴 **Four screens had opted out of `screenTitle` entirely** — Food Library, Recipe Builder, Streak
Stats and Calorie Decision all predate it and still called `.navigationTitle` directly, so they
carried neither the backdrop (**D53**) nor this title treatment. The food picker having no
background is how it surfaced. "A screen cannot forget its backdrop without forgetting its title"
only holds while nothing titles itself another way, and four things already did.

The guard is a **source scan** (`ScreenTitleUsageTests`), not a rendering test: nothing in the type
system prevents a raw `.navigationTitle`, and a test that runs a view cannot see that it is missing
a background. It allows only `ScreenTitle.swift` and names the offenders. ⚠️ It was checked by
breaking a screen on purpose and watching it fail with that file named — a guard nobody has seen
fail is not a guard, which is the same lesson D53's false verification taught.

**D55 · The Dashboard has no subtitle.** A toolbar item centres against the *whole* title block, so
with a date underneath the avatar sat ~6pt below the word "Dashboard". One line puts them level.
⚠️ Two fixes were tried and rejected: `.frame(maxHeight:alignment:)` does nothing to a bar button
item (it is laid out at its intrinsic size), and bottom padding rides the circle up but the
toolbar's glass capsule grows with it and clips the avatar. The alignment is not nudgeable — the
block has to be one line. Affordable **here only**: the Dashboard is always today and has no day
picker, so the date restated the TODAY card.

**Food Log had the same offset and a different answer**, because its date is *state*: you navigate
days with the arrows, so dropping the label would leave them saying nothing about which day you are
on. The date moved **into** the stepper — `‹ Tue, Aug 11 ›` — which fixes the alignment and is the
better home for it regardless: it now sits in the control that changes it. ⚠️ **One `ToolbarItem`,
not three**: on iOS 26 each item gets its own glass capsule, so a date between two of them reads as
three unrelated controls rather than one day picker.
