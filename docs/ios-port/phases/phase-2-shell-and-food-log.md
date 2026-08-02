# iOS Phase 2 — App shell, design system, Food Log

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app launches into a native tab bar, one real screen reads and writes the database, and
the design system Phase 3's twenty screens depend on exists and has been looked at.

**Architecture:** Port the design *tokens* faithfully and let SwiftUI supply the *components* —
native iOS 26 Liquid Glass, not a port of Android's approximation. One `@Observable` model per
screen, activated by `.task`, fed by the `ValueObservation` helper Phase 1a built. Food Log is a
thin slice: read the day, see totals, add an entry through a quick-add sheet.

**Tech Stack:** Swift 6.3.2, Xcode 26.5, iOS 26.0, SwiftUI, GRDB 7.11.1, Swift Testing.

**Design spec:** [`docs/superpowers/specs/2026-08-02-ios-phase-2-design.md`](../../superpowers/specs/2026-08-02-ios-phase-2-design.md)
(in the Android repo). Decisions **D15–D19** are settled there and must not be re-litigated.

**One repo.** Everything lands in `~/Desktop/RecompTracker-IOS`. The Android repo is untouched.

---

## Context you need before starting

Read, in order:
1. `docs/ios-port/STATUS.md` — where the port is
2. `docs/ios-port/decisions.md` — **D6** (dates as strings), **D14** (per-key decoding, Kotlin name
   qualification) are binding; **D15–D19** arrive with this phase
3. `docs/ios-port/reference/shared-codec-api.md` — the real Swift surface of `:shared`
4. The design spec above

Phases 1a and 1b are complete: GRDB, 19 tables, 18 record types, the query layer, seven
transactions, ten preference stores, Keychain, bundled assets, three file formats. **265 tests.**

### Established facts — do NOT rediscover

- Tests live **flat** in `RecompTracker/RecompTrackerTests/`, never in subfolders.
- Buildable folders: new `.swift` files need **no `project.pbxproj` edit**. Never edit it. This
  holds for resources too — `Resources/` was picked up automatically in Phase 1b.
- `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`. Persistence types are `nonisolated`, **and so are
  their extensions** — `nonisolated` does not propagate. UI types are MainActor and want to be.
- **A Debug run does not prove zero isolation warnings.** Two appeared only in Release last phase.
  Build both.
- `#expect` cannot appear inside a throwing closure. Hoist the query out first.
- A test marked `async` can make Swift pick GRDB's *async* `read` overload and then demand an
  `await` inside a throwing autoclosure. Drop `async` from the test signature if you hit that.
- With `import Shared`, Kotlin types appear **without** the `Shared` ObjC prefix the header shows.
  `RecompTrackerTests/SharedInteropTests.swift` pins this.
- A bare `** TEST FAILED **` with no `error:` line has been seen once as a simulator flake. Re-run
  before believing it.

### Build and test commands

```bash
cd ~/Desktop/RecompTracker-IOS
xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' 2>&1 \
  | grep -E "error:|warning:|Test run with|✘|TEST (SUCCEEDED|FAILED)" | grep -v AppIntents
```

Baseline: **265 tests** (256 running + 9 armed and skipping).

### One correction to the design spec

The spec says `MacroTotals` comes from `:shared`. It does not — the *type* is exported, but the
summing extension `macroTotals()` lives in `:app`'s `data/repository`, operating on Room entities.
Totals are therefore a **Swift sum**, which is trivial arithmetic tested directly here. What
genuinely comes from `:shared` is `EffectiveTargets.resolve` and `PlanHistory.planOnOrFallback` —
the parts that are not trivial.

---

# PART A — Design system

## Task 1: `CalendarDay`

D6 persists dates as `YYYY-MM-DD` strings; `:shared` speaks Kotlin `LocalDate`; day arithmetic needs
a representation. One value type prevents a second date model taking root across Phase 3.

**Files:**
- Create: `RecompTracker/RecompTracker/DesignSystem/CalendarDay.swift`
- Create: `RecompTracker/RecompTrackerTests/CalendarDayTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import Shared
import Testing
@testable import RecompTracker

@Suite struct CalendarDayTests {

    @Test func parsesAValidISODate() {
        let day = CalendarDay("2026-08-02")
        #expect(day?.iso == "2026-08-02")
    }

    @Test func rejectsMalformedInput() {
        #expect(CalendarDay("2026-8-2") == nil)      // unpadded
        #expect(CalendarDay("02-08-2026") == nil)    // wrong order
        #expect(CalendarDay("") == nil)
        #expect(CalendarDay("2026-13-01") == nil)    // month 13
        #expect(CalendarDay("2026-02-30") == nil)    // no such day
    }

    /// Lexicographic ordering IS chronological for zero-padded ISO — the property every range
    /// predicate in the persistence layer already depends on (D6).
    @Test func ordersLexicographicallyAndChronologically() {
        let a = CalendarDay("2026-08-02")!
        let b = CalendarDay("2026-09-15")!
        let c = CalendarDay("2026-12-31")!
        #expect(a < b)
        #expect(b < c)
        #expect([c, a, b].sorted() == [a, b, c])
    }

    @Test func addsAndSubtractsDaysAcrossMonthAndYearBoundaries() {
        #expect(CalendarDay("2026-08-31")!.adding(days: 1).iso == "2026-09-01")
        #expect(CalendarDay("2026-01-01")!.adding(days: -1).iso == "2025-12-31")
        #expect(CalendarDay("2024-02-28")!.adding(days: 1).iso == "2024-02-29")  // leap year
        #expect(CalendarDay("2026-08-02")!.adding(days: 0).iso == "2026-08-02")
    }

    @Test func computesTheDayDifference() {
        let a = CalendarDay("2026-08-02")!
        let b = CalendarDay("2026-08-09")!
        #expect(a.days(until: b) == 7)
        #expect(b.days(until: a) == -7)
    }

    /// The `:shared` boundary. Kotlin objects must never leak into view state, so conversion is
    /// explicit and one-directional at the call site.
    @Test func convertsToAndFromTheKotlinDate() {
        let day = CalendarDay("2026-08-02")!
        let kotlin = day.kotlin
        #expect(kotlin.year == 2026)
        #expect(kotlin.month.name == "AUGUST")
        #expect(kotlin.day == 2)
        #expect(CalendarDay(kotlin) == day)
    }

    @Test func clampsIntoAClosedRange() {
        let low = CalendarDay("2026-08-01")!
        let high = CalendarDay("2026-08-31")!
        #expect(CalendarDay("2026-07-15")!.clamped(to: low...high) == low)
        #expect(CalendarDay("2026-09-15")!.clamped(to: low...high) == high)
        #expect(CalendarDay("2026-08-10")!.clamped(to: low...high).iso == "2026-08-10")
    }
}
```

- [ ] **Step 2: Run to verify failure**

Expected: FAIL — "cannot find 'CalendarDay' in scope".

- [ ] **Step 3: Implement**

```swift
import Foundation
import Shared

/// A calendar day as the app persists it: a `YYYY-MM-DD` string (decision D6).
///
/// Deliberately **not** a `Date`. Every range predicate in the persistence layer compares these
/// lexicographically, which is only chronological because the components are zero-padded — and a
/// `Date` would silently shift rows across day boundaries by timezone. Kotlin conversion happens
/// explicitly at the `:shared` boundary so Kotlin objects never reach view state.
nonisolated struct CalendarDay: Hashable, Comparable, Codable, Sendable, CustomStringConvertible {

    let iso: String

    var description: String { iso }

    /// Fails for anything that is not a real, zero-padded `YYYY-MM-DD` day.
    init?(_ iso: String) {
        guard iso.count == 10 else { return nil }
        let parts = iso.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3,
              parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2])
        else { return nil }
        var components = DateComponents()
        components.year = y; components.month = m; components.day = d
        // isValidDate rejects 2026-13-01 and 2026-02-30, which the digit checks above allow.
        guard Self.calendar.date(from: components) != nil,
              components.isValidDate(in: Self.calendar)
        else { return nil }
        self.iso = iso
    }

    init(_ kotlin: Kotlinx_datetimeLocalDate) {
        self.iso = kotlin.description()
    }

    /// Today in the user's current timezone.
    static var today: CalendarDay {
        CalendarDay(from: Date())
    }

    init(from date: Date) {
        let c = Self.calendar.dateComponents([.year, .month, .day], from: date)
        self.iso = String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }

    func adding(days: Int) -> CalendarDay {
        guard days != 0 else { return self }
        let shifted = Self.calendar.date(byAdding: .day, value: days, to: date)!
        return CalendarDay(from: shifted)
    }

    func days(until other: CalendarDay) -> Int {
        Self.calendar.dateComponents([.day], from: date, to: other.date).day!
    }

    func clamped(to range: ClosedRange<CalendarDay>) -> CalendarDay {
        min(max(self, range.lowerBound), range.upperBound)
    }

    /// Only cross here — never hold the Kotlin value in state.
    var kotlin: Kotlinx_datetimeLocalDate {
        let parts = iso.split(separator: "-")
        return Kotlinx_datetimeLocalDate(
            year: Int32(parts[0])!, month: Int32(parts[1])!, day: Int32(parts[2])!)
    }

    static func < (lhs: CalendarDay, rhs: CalendarDay) -> Bool { lhs.iso < rhs.iso }

    // MARK: - Internals

    /// Gregorian with the *current* timezone: "today" is the user's today, and day arithmetic
    /// must respect their calendar, not UTC.
    private static let calendar = Calendar(identifier: .gregorian)

    private var date: Date {
        let parts = iso.split(separator: "-")
        var c = DateComponents()
        c.year = Int(parts[0]); c.month = Int(parts[1]); c.day = Int(parts[2])
        c.hour = 12  // midday, so a DST transition can never shift the day
        return Self.calendar.date(from: c)!
    }
}
```

- [ ] **Step 4: Run to verify pass, then commit**

```bash
git add -A && git commit -m "feat(design): CalendarDay, the YYYY-MM-DD value type (D6)"
```

---

## Task 2: Typography

**Files:**
- Create: `RecompTracker/RecompTracker/DesignSystem/Typography.swift`
- Create: `RecompTracker/RecompTrackerTests/TypographyTests.swift`

Android's thirteen `AppType` tokens carry size, weight **and letter-spacing**. SwiftUI splits those:
`Font` carries size and weight, `.tracking()` is a view modifier. So a token is a small struct and
the call site applies it with one modifier — preserving the rule that no screen ever writes a raw
font size.

- [ ] **Step 1: Write the failing tests**

```swift
import SwiftUI
import Testing
@testable import RecompTracker

@Suite struct TypographyTests {

    /// Every Android AppType token has a Swift counterpart. If one is missing, a screen will
    /// reach for a raw `.system(size:)` and the design system starts leaking.
    @Test(arguments: [
        AppType.screenTitle, AppType.screenTitleCompact, AppType.screenSubtitle,
        AppType.sectionLabel, AppType.cardTitle, AppType.cardSubtitle, AppType.body,
        AppType.label, AppType.metaLabel, AppType.displayHero, AppType.displayLarge,
        AppType.statValue, AppType.statValueSmall,
    ])
    func everyTokenExists(token: TypeToken) {
        #expect(token.tracking.isFinite)
    }

    /// Tracking is the part SwiftUI does not carry on Font, so it is the part most likely to be
    /// dropped in the port. These are the four Android tokens that set it.
    @Test func trackingMatchesAndroid() {
        #expect(AppType.screenTitle.tracking == -0.8)
        #expect(AppType.screenTitleCompact.tracking == -0.4)
        #expect(AppType.displayHero.tracking == -1.0)
        #expect(AppType.displayLarge.tracking == -0.5)
        #expect(AppType.statValue.tracking == -0.3)
        #expect(AppType.sectionLabel.tracking == 0.14)
        #expect(AppType.metaLabel.tracking == 0.4)
    }

    @Test func tokensWithoutTrackingAreZeroNotAccidentallyInherited() {
        #expect(AppType.cardTitle.tracking == 0)
        #expect(AppType.body.tracking == 0)
        #expect(AppType.statValueSmall.tracking == 0)
    }

    /// The two 9pt tokens have no native text style below 11pt, so they carry an explicit scaled
    /// size instead. Pinning it here records *why* they are the odd ones out.
    @Test func theTwoSubElevenPointTokensDeclareAFixedBaseSize() {
        #expect(AppType.sectionLabel.fixedBaseSize == 9)
        #expect(AppType.metaLabel.fixedBaseSize == 9)
        #expect(AppType.cardTitle.fixedBaseSize == nil)
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

```swift
import SwiftUI

/// One typography token: a font plus the letter-spacing SwiftUI does not carry on `Font`.
///
/// Apply with `.appType(_:)` rather than reading the parts — that is what keeps a raw
/// `.system(size:)` from ever appearing in a screen.
nonisolated struct TypeToken: Sendable, Hashable {
    let font: Font
    let tracking: CGFloat
    /// Set only for tokens below 11pt, which have no native text style to scale against.
    let fixedBaseSize: CGFloat?

    init(_ font: Font, tracking: CGFloat = 0, fixedBaseSize: CGFloat? = nil) {
        self.font = font
        self.tracking = tracking
        self.fixedBaseSize = fixedBaseSize
    }
}

/// The named type scale. Mirrors Android's `AppType` one-for-one.
///
/// Sizes come from native text styles rather than fixed points, so the app honours the reader's
/// Dynamic Type setting (decision D18). The mapping is by *role*, not by pixel value — `cardTitle`
/// is `.subheadline` because both are the 15pt row-title slot, not by coincidence.
nonisolated enum AppType {
    // Screen headers
    static let screenTitle        = TypeToken(.system(.largeTitle, weight: .heavy), tracking: -0.8)
    static let screenTitleCompact = TypeToken(.system(.title2, weight: .bold), tracking: -0.4)
    static let screenSubtitle     = TypeToken(.system(.footnote, weight: .regular))

    // Sections and rows
    static let sectionLabel = TypeToken(.system(size: 9, weight: .bold), tracking: 0.14, fixedBaseSize: 9)
    static let cardTitle    = TypeToken(.system(.subheadline, weight: .semibold))
    static let cardSubtitle = TypeToken(.system(.caption, weight: .regular))
    static let body         = TypeToken(.system(.footnote, weight: .regular))
    static let label        = TypeToken(.system(.caption2, weight: .medium))
    static let metaLabel    = TypeToken(.system(size: 9, weight: .bold), tracking: 0.4, fixedBaseSize: 9)

    // Data display
    static let displayHero     = TypeToken(.system(size: 44, weight: .heavy), tracking: -1.0)
    static let displayLarge    = TypeToken(.system(size: 36, weight: .heavy), tracking: -0.5)
    static let statValue       = TypeToken(.system(.title3, weight: .bold), tracking: -0.3)
    static let statValueSmall  = TypeToken(.system(.headline, weight: .bold))
}

extension View {
    /// Applies a type token. The only sanctioned way to set type in this app.
    func appType(_ token: TypeToken) -> some View {
        font(token.font).tracking(token.tracking)
    }
}
```

> ⚠️ `sectionLabel` and `metaLabel` use `.system(size:)`, which does **not** scale with Dynamic Type
> on its own. Task 4 adds the `@ScaledMetric` wrapper that makes them scale; they are declared here
> so the token set is complete in one place.

- [ ] **Step 4: Run, then commit**

```bash
git add -A && git commit -m "feat(design): AppType type scale on Dynamic Type (D18)"
```

---

## Task 3: Colour — asset catalog plus the eleven accent themes

**Files:**
- Create: `RecompTracker/RecompTracker/DesignSystem/Palette.swift`
- Create: `RecompTracker/RecompTracker/Resources/Colors.xcassets/` (11 color sets, below)
- Create: `RecompTracker/RecompTrackerTests/PaletteTests.swift`

Colour splits in two. The **semantic neutrals** become Color Sets with Any/Dark appearances, so
light and dark come free with no `LocalAppColors` equivalent. The **accent** cannot: eleven themes
are chosen at runtime from `UiPreferences`, so it stays an `@Environment` value.

Six Android tokens — `frostedSurface`, `frostedSurfaceFallback`, `frostedBorder`, `glassOverlay`,
`glassShimmer`, `glassPillSurface` — exist only to fake glass. `.glassEffect` supplies all six.
**Do not port them.**

- [ ] **Step 1: Create the color sets**

Eleven sets under `RecompTracker/RecompTracker/Resources/Colors.xcassets/`, each a directory
containing `Contents.json`. Values are the Android `AppColors.Dark` / `.Light` pairs converted from
`0xAARRGGBB` to sRGB float components.

| Color set | Any (light) | Dark |
|---|---|---|
| `textPrimary` | `#141019` @ 1.00 | `#FFFFFF` @ 1.00 |
| `textSecondary` | `#141019` @ 0.70 | `#FFFFFF` @ 0.70 |
| `textMuted` | `#141019` @ 0.60 | `#FFFFFF` @ 0.28 |
| `textDim` | `#141019` @ 0.75 | `#FFFFFF` @ 0.40 |
| `textFaint` | `#141019` @ 0.50 | `#FFFFFF` @ 0.25 |
| `textVeryMuted` | `#141019` @ 0.40 | `#FFFFFF` @ 0.22 |
| `cardSurface` | `#FFFFFF` @ 0.56 | `#FFFFFF` @ 0.04 |
| `cardBorder` | `#000000` @ 0.14 | `#FFFFFF` @ 0.07 |
| `scrim` | `#FFFFFF` @ 0.25 | `#000000` @ 0.55 |
| `celebrationInk` | `#92400E` @ 1.00 | `#FBBF24` @ 1.00 |
| `errorInk` | `#DC2626` @ 1.00 | `#FB7185` @ 1.00 |

Template `Contents.json` — repeat per set, substituting the two colours:

```json
{
  "colors": [
    { "idiom": "universal",
      "color": { "color-space": "srgb",
        "components": { "red": "0x14", "green": "0x10", "blue": "0x19", "alpha": "1.000" } } },
    { "idiom": "universal",
      "appearances": [ { "appearance": "luminosity", "value": "dark" } ],
      "color": { "color-space": "srgb",
        "components": { "red": "0xFF", "green": "0xFF", "blue": "0xFF", "alpha": "1.000" } } }
  ],
  "info": { "author": "xcode", "version": 1 }
}
```

Also create `Colors.xcassets/Contents.json`:

```json
{ "info": { "author": "xcode", "version": 1 } }
```

- [ ] **Step 2: Write the failing tests**

```swift
import SwiftUI
import Testing
@testable import RecompTracker

@Suite struct PaletteTests {

    /// Every semantic neutral resolves. A typo in an asset name yields a silent fallback colour
    /// rather than a build error, so this is the only thing standing between us and grey text.
    @Test(arguments: AppColor.allCases)
    func everySemanticColorResolves(token: AppColor) {
        #expect(UIColor(token.color).cgColor.alpha >= 0)
    }

    @Test func allElevenAndroidAccentThemesExist() {
        #expect(AccentTheme.allCases.count == 11)
        #expect(AccentTheme.allCases.map(\.rawValue) == [
            "VIOLET", "INDIGO", "BLUE", "CYAN", "EMERALD", "LIME",
            "AMBER", "ORANGE", "ROSE", "SLATE", "SILVER",
        ])
    }

    /// Raw values are the persisted strings in ui_preferences, so they must be the Kotlin enum
    /// names exactly — an unknown value falls back to VIOLET (Phase 1b's UIPreferences).
    @Test func rawValuesArePersistedKotlinNames() {
        #expect(AccentTheme(rawValue: "EMERALD") == .emerald)
        #expect(AccentTheme(rawValue: "emerald") == nil)
    }

    /// Ported verbatim from Android: white ink reads on saturated accents, dark ink on pale ones.
    /// Silver, Lime and Amber are the three that flip — get this wrong and their buttons are
    /// white-on-white.
    @Test func onAccentFlipsToDarkInkForPaleAccents() {
        #expect(AppAccent(theme: .silver).onAccentIsDark)
        #expect(AppAccent(theme: .lime).onAccentIsDark)
        #expect(AppAccent(theme: .amber).onAccentIsDark)
        #expect(!AppAccent(theme: .violet).onAccentIsDark)
        #expect(!AppAccent(theme: .indigo).onAccentIsDark)
        #expect(!AppAccent(theme: .rose).onAccentIsDark)
    }

    /// Accent ink is mode-aware: the bright shades glow on dark and are illegible on light.
    @Test func accentInkDeepensInLightMode() {
        #expect(AppAccent(theme: .violet, darkMode: true).inkLight
                != AppAccent(theme: .violet, darkMode: false).inkLight)
    }
}
```

- [ ] **Step 3: Run to verify failure**

- [ ] **Step 4: Implement**

```swift
import SwiftUI

/// Semantic neutral colours, resolved from the asset catalog so light and dark come free.
///
/// The six Android glass tokens (`frostedSurface`, `glassOverlay`, `glassShimmer`, …) are
/// deliberately absent: they existed to approximate a material Compose lacks, and `.glassEffect`
/// supplies all of them natively (decision D15).
nonisolated enum AppColor: String, CaseIterable, Sendable {
    case textPrimary, textSecondary, textMuted, textDim, textFaint, textVeryMuted
    case cardSurface, cardBorder, scrim, celebrationInk, errorInk

    var color: Color { Color(rawValue, bundle: .main) }
}

/// The eleven accent presets. Raw values are the persisted `ui_preferences` strings, so they must
/// stay exactly the Kotlin enum names.
nonisolated enum AccentTheme: String, CaseIterable, Sendable {
    case violet = "VIOLET", indigo = "INDIGO", blue = "BLUE", cyan = "CYAN"
    case emerald = "EMERALD", lime = "LIME", amber = "AMBER", orange = "ORANGE"
    case rose = "ROSE", slate = "SLATE", silver = "SILVER"

    /// (accent, accentLight, accentLighter, accentDark, accentInk) — verbatim from
    /// Android's `DesignTokens.kt`.
    var ramp: (Color, Color, Color, Color, Color) {
        switch self {
        case .violet:  return (h(0x8B5CF6), h(0xA78BFA), h(0xC4B5FD), h(0x7C3AED), h(0x6D28D9))
        case .indigo:  return (h(0x6366F1), h(0x818CF8), h(0xA5B4FC), h(0x4338CA), h(0x4338CA))
        case .blue:    return (h(0x3B82F6), h(0x60A5FA), h(0x93C5FD), h(0x1D4ED8), h(0x1D4ED8))
        case .cyan:    return (h(0x06B6D4), h(0x22D3EE), h(0x67E8F9), h(0x0891B2), h(0x0E7490))
        case .emerald: return (h(0x10B981), h(0x34D399), h(0x6EE7B7), h(0x059669), h(0x047857))
        case .lime:    return (h(0x84CC16), h(0xA3E635), h(0xBEF264), h(0x65A30D), h(0x4D7C0F))
        case .amber:   return (h(0xF59E0B), h(0xFBBF24), h(0xFCD34D), h(0xB45309), h(0x92400E))
        case .orange:  return (h(0xF97316), h(0xFB923C), h(0xFDBA74), h(0xEA580C), h(0xC2410C))
        case .rose:    return (h(0xF43F5E), h(0xFB7185), h(0xFDA4AF), h(0xBE123C), h(0xBE123C))
        case .slate:   return (h(0x64748B), h(0x94A3B8), h(0xCBD5E1), h(0x334155), h(0x334155))
        case .silver:  return (h(0xCBD5E1), h(0xE2E8F0), h(0xF1F5F9), h(0x94A3B8), h(0x475569))
        }
    }

    /// sRGB components of the base accent, for the luminance test below.
    var accentComponents: (r: Double, g: Double, b: Double) {
        let hex: UInt32
        switch self {
        case .violet: hex = 0x8B5CF6; case .indigo: hex = 0x6366F1; case .blue: hex = 0x3B82F6
        case .cyan: hex = 0x06B6D4; case .emerald: hex = 0x10B981; case .lime: hex = 0x84CC16
        case .amber: hex = 0xF59E0B; case .orange: hex = 0xF97316; case .rose: hex = 0xF43F5E
        case .slate: hex = 0x64748B; case .silver: hex = 0xCBD5E1
        }
        return (Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }

    private func h(_ v: UInt32) -> Color {
        Color(.sRGB,
              red: Double((v >> 16) & 0xFF) / 255,
              green: Double((v >> 8) & 0xFF) / 255,
              blue: Double(v & 0xFF) / 255,
              opacity: 1)
    }
}

/// Runtime accent token bag — the analogue of Android's `LocalAppAccent`.
///
/// `darkMode` affects only the ink colours: the bright ramp glows on dark surfaces and is
/// illegible on light, so light mode collapses ink to the deepened `accentInk`.
nonisolated struct AppAccent: Sendable, Equatable {
    let theme: AccentTheme
    let darkMode: Bool

    init(theme: AccentTheme = .violet, darkMode: Bool = true) {
        self.theme = theme
        self.darkMode = darkMode
    }

    var accent: Color        { theme.ramp.0 }
    var accentLight: Color   { theme.ramp.1 }
    var accentLighter: Color { theme.ramp.2 }
    var accentDark: Color    { theme.ramp.3 }

    var inkBase: Color    { darkMode ? theme.ramp.0 : theme.ramp.4 }
    var inkLight: Color   { darkMode ? theme.ramp.1 : theme.ramp.4 }
    var inkLighter: Color { darkMode ? theme.ramp.2 : theme.ramp.4 }

    /// Ported verbatim: white reads on saturated accents, dark ink on pale ones (Silver, Lime,
    /// Amber). Depends on the fill's brightness only, never on mode.
    var onAccentIsDark: Bool {
        let c = theme.accentComponents
        return (0.299 * c.r + 0.587 * c.g + 0.114 * c.b) > 0.62
    }

    var onAccent: Color {
        onAccentIsDark ? Color(.sRGB, red: 0.078, green: 0.063, blue: 0.098, opacity: 1) : .white
    }

    var tintedSurface: Color  { accent.opacity(0.08) }
    var tintedBorder: Color   { accent.opacity(0.22) }
    var backgroundTint: Color { accent.opacity(0.06) }
}

extension EnvironmentValues {
    /// The analogue of Android's `LocalAppAccent`.
    @Entry var appAccent = AppAccent()
}
```

- [ ] **Step 5: Run, then commit**

```bash
git add -A && git commit -m "feat(design): semantic colour assets and the eleven accent themes"
```

---

## Task 4: Spacing, corners, and the surface modifiers

**Files:**
- Create: `RecompTracker/RecompTracker/DesignSystem/Spacing.swift`
- Create: `RecompTracker/RecompTracker/DesignSystem/Surfaces.swift`
- Create: `RecompTracker/RecompTrackerTests/SpacingTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import SwiftUI
import Testing
@testable import RecompTracker

@Suite struct SpacingTests {

    /// One gutter, 16pt. The design system's fourth principle, and the one most easily eroded.
    @Test func screenGutterIsSixteen() {
        #expect(Layout.screenPaddingH == 16)
        #expect(Layout.screenSpacing == 10)
    }

    @Test func spacingScaleMatchesAndroid() {
        #expect(Spacing.xs == 4)
        #expect(Spacing.sm == 8)
        #expect(Spacing.md == 12)
        #expect(Spacing.lg == 16)
        #expect(Spacing.xl == 20)
    }

    @Test func cornerScaleMatchesAndroid() {
        #expect(Corner.small == 10)
        #expect(Corner.card == 16)
        #expect(Corner.chip == 20)
        #expect(Corner.pill == 100)
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement the tokens**

```swift
import SwiftUI

/// Screen-level layout tokens. `screenPaddingH` is the single horizontal gutter for all
/// screen content — 14 and 20 are not options.
nonisolated enum Layout {
    static let screenPaddingH: CGFloat = 16
    static let screenSpacing: CGFloat = 10
}

/// In-card / inline spacing scale.
nonisolated enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
}

/// Corner radii. `pill` is for glass elements only.
nonisolated enum Corner {
    static let small: CGFloat = 10
    static let card: CGFloat = 16
    static let chip: CGFloat = 20
    static let pill: CGFloat = 100
}
```

- [ ] **Step 4: Implement the surfaces**

```swift
import SwiftUI

/// The card family, as *modifiers* rather than wrapper views.
///
/// Modifiers because the native material does the work: `.glassEffect` replaces the entire
/// `FrostedCard` implementation Android hand-rolls, along with its six supporting colour tokens
/// (decision D15). A wrapper view would add a layout container for no reason.
extension View {

    /// Primary / featured data cards, charts, hero tiles — Android's `FrostedCard`.
    func frostedCard(padding: CGFloat = Spacing.lg) -> some View {
        self.padding(padding)
            .glassEffect(.regular, in: .rect(cornerRadius: Corner.card))
    }

    /// Quieter list rows, menus, form containers — Android's `NeutralCard`.
    func neutralCard(padding: CGFloat = Spacing.lg) -> some View {
        self.padding(padding)
            .background(AppColor.cardSurface.color, in: .rect(cornerRadius: Corner.card))
            .overlay(
                RoundedRectangle(cornerRadius: Corner.card)
                    .strokeBorder(AppColor.cardBorder.color, lineWidth: 1)
            )
    }

    /// AI features only — Android's `TintedCard`.
    func tintedCard(padding: CGFloat = Spacing.lg) -> some View {
        modifier(TintedCardModifier(padding: padding))
    }

    /// The 16pt screen gutter.
    func screenGutter() -> some View {
        self.padding(.horizontal, Layout.screenPaddingH)
    }
}

private struct TintedCardModifier: ViewModifier {
    @Environment(\.appAccent) private var accent
    let padding: CGFloat

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(accent.tintedSurface, in: .rect(cornerRadius: Corner.card))
            .overlay(
                RoundedRectangle(cornerRadius: Corner.card)
                    .strokeBorder(accent.tintedBorder, lineWidth: 1)
            )
    }
}

/// Scales the two sub-11pt tokens with Dynamic Type.
///
/// `.system(size:)` is fixed by design, and `sectionLabel` / `metaLabel` sit below the smallest
/// native text style — so without this they are the only text in the app that ignores the reader's
/// accessibility setting.
struct ScaledCaption: ViewModifier {
    @ScaledMetric(relativeTo: .caption2) private var scale: CGFloat = 1
    let token: TypeToken

    func body(content: Content) -> some View {
        content
            .font(.system(size: (token.fixedBaseSize ?? 9) * scale, weight: .bold))
            .tracking(token.tracking)
    }
}

extension View {
    /// Use for `AppType.sectionLabel` and `AppType.metaLabel`; `.appType(_:)` for everything else.
    func appTypeScaled(_ token: TypeToken) -> some View {
        modifier(ScaledCaption(token: token))
    }
}
```

- [ ] **Step 5: Run, then commit**

```bash
git add -A && git commit -m "feat(design): spacing tokens and the glass surface modifiers"
```

---

# PART B — App shell

## Task 5: Four-tab shell

**Files:**
- Create: `RecompTracker/RecompTracker/Shell/AppTab.swift`
- Create: `RecompTracker/RecompTracker/Shell/RootTabView.swift`
- Create: `RecompTracker/RecompTracker/Shell/PlaceholderScreen.swift`
- Modify: `RecompTracker/RecompTracker/ContentView.swift`
- Create: `RecompTracker/RecompTrackerTests/AppTabTests.swift`

Four tabs, not five: Train is v1.1 (D4), and reserving its slot means the bar is never re-cut under
a user who has learned it (D17). More opens from a toolbar control on Home, matching Android.

- [ ] **Step 1: Write the failing tests**

```swift
import Testing
@testable import RecompTracker

@Suite struct AppTabTests {

    /// Four, in this order. The fifth slot is deliberately empty — Train lands there in v1.1
    /// (D17), and iOS starts auto-collapsing tabs past five.
    @Test func thereAreFourTabsInAndroidsOrder() {
        #expect(AppTab.allCases == [.home, .body, .food, .coach])
        #expect(AppTab.allCases.count < 5, "a fifth tab triggers iOS's automatic More list")
    }

    @Test func everyTabHasATitleAndASystemImage() {
        for tab in AppTab.allCases {
            #expect(!tab.title.isEmpty)
            #expect(!tab.systemImage.isEmpty)
        }
    }

    /// Food is the only tab Phase 2 actually builds.
    @Test func onlyFoodIsImplementedInPhaseTwo() {
        #expect(AppTab.food.isImplemented)
        #expect(!AppTab.home.isImplemented)
        #expect(!AppTab.body.isImplemented)
        #expect(!AppTab.coach.isImplemented)
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

`AppTab.swift`:

```swift
import Foundation

/// The bottom-tab destinations.
///
/// Four, matching Android's order minus Train, which is v1.1 (decision D4). The empty fifth slot
/// is deliberate: iOS collapses tabs into an automatic "More" list past five, so leaving room now
/// means the bar never changes shape under someone who has already learned it (D17).
///
/// Android's "More" is not a tab there either — it is reached by a push, and it is a toolbar
/// control on Home here.
nonisolated enum AppTab: String, CaseIterable, Hashable, Sendable {
    case home, body, food, coach

    var title: String {
        switch self {
        case .home: "Home"
        case .body: "Body"
        case .food: "Food"
        case .coach: "Coach"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "house"
        case .body: "figure"
        case .food: "fork.knife"
        case .coach: "bubble.left.and.text.bubble.right"
        }
    }

    /// Phase 2 builds Food only; the rest are placeholders until Phases 3 and 5.
    var isImplemented: Bool { self == .food }
}
```

`PlaceholderScreen.swift`:

```swift
import SwiftUI

/// Stands in for a tab that later phases build. Says which phase, so it never reads as a bug.
struct PlaceholderScreen: View {
    let tab: AppTab
    let arrivingIn: String

    var body: some View {
        ContentUnavailableView {
            Label(tab.title, systemImage: tab.systemImage)
        } description: {
            Text("Arriving in \(arrivingIn).")
        }
        .navigationTitle(tab.title)
    }
}
```

`RootTabView.swift`:

```swift
import SwiftUI

/// The app shell: four tabs, each with its own navigation stack.
///
/// Per-tab stacks are decision D16. Android runs one shared back stack across all five tabs, so
/// back retraces a single trail there and per-tab history here — a real behavioural difference,
/// accepted because the native tab bar is where iOS 26's glass does its most visible work
/// (floating, minimise-on-scroll, morphing) and that is only free inside `TabView`.
struct RootTabView: View {
    @Environment(AppContainer.self) private var container
    @State private var selection: AppTab = .food

    var body: some View {
        TabView(selection: $selection) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                Tab(tab.title, systemImage: tab.systemImage, value: tab) {
                    NavigationStack {
                        screen(for: tab)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func screen(for tab: AppTab) -> some View {
        switch tab {
        case .food:  FoodLogScreen(database: container.database)
        case .home:  PlaceholderScreen(tab: tab, arrivingIn: "Phase 3")
        case .body:  PlaceholderScreen(tab: tab, arrivingIn: "Phase 3")
        case .coach: PlaceholderScreen(tab: tab, arrivingIn: "Phase 5")
        }
    }
}
```

> Phase 2 starts on the Food tab because it is the only built screen. Change the default to `.home`
> in Phase 3.

- [ ] **Step 4: Point `ContentView` at the shell**

Replace `ContentView`'s body with `RootTabView()`. Leave `RecompTrackerApp.swift` alone — its
database-failure path from Phase 1b is still correct.

- [ ] **Step 5: Run, then commit**

```bash
git add -A && git commit -m "feat(shell): four-tab TabView with per-tab navigation stacks (D16, D17)"
```

---

## Task 5b: Wire the accent theme and colour scheme from `UIPreferences`

**Files:**
- Create: `RecompTracker/RecompTracker/Shell/ThemeHost.swift`
- Create: `RecompTracker/RecompTracker/Shell/AccentPreviews.swift`
- Create: `RecompTracker/RecompTrackerTests/ThemeHostTests.swift`

Without this, `\.appAccent` stays at its Violet default forever and `themeMode` does nothing — so
the "check all eleven accent themes" item on the visual list would be impossible. Phase 2 has no
Settings screen to *change* them, so this reads the stored values once and renders the rest as
previews.

- [ ] **Step 1: Write the failing tests**

```swift
import SwiftUI
import Testing
@testable import RecompTracker

@Suite struct ThemeHostTests {

    /// `resolvedAccentTheme` already falls back to VIOLET for anything unknown (Phase 1b), so this
    /// pins that the shell honours it rather than re-deciding.
    @Test func mapsStoredAccentNameToATheme() {
        #expect(ThemeHost.accent(named: "EMERALD") == .emerald)
        #expect(ThemeHost.accent(named: "VIOLET") == .violet)
        #expect(ThemeHost.accent(named: "CHARTREUSE") == .violet, "unknown falls back")
    }

    /// Stored values are the storageValue strings, not enum names — `"system"`, not `"SYSTEM"`.
    @Test func mapsStoredThemeModeToAColorScheme() {
        #expect(ThemeHost.colorScheme(for: "light") == .light)
        #expect(ThemeHost.colorScheme(for: "dark") == .dark)
        #expect(ThemeHost.colorScheme(for: "system") == nil, "nil means follow the system")
        #expect(ThemeHost.colorScheme(for: "sepia") == nil, "unknown follows the system")
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

```swift
import SwiftUI

/// Reads the stored appearance preferences once and injects them into the environment.
///
/// Once, not observed: `UIPreferencesStore` is a `JSONStore` with no change stream, and Phase 2
/// has no Settings screen that could change these. Phase 3 adds the stream (see Task 7's note) and
/// this becomes a `for await` loop.
struct ThemeHost<Content: View>: View {
    @ViewBuilder let content: Content

    @State private var accent = AppAccent()
    @State private var scheme: ColorScheme?
    @Environment(\.colorScheme) private var systemScheme

    var body: some View {
        content
            .environment(\.appAccent, accent)
            .preferredColorScheme(scheme)
            .task {
                guard let store = try? UIPreferencesStore() else { return }
                let prefs = await store.value()
                let resolved = Self.colorScheme(for: prefs.resolvedThemeMode)
                scheme = resolved
                accent = AppAccent(theme: Self.accent(named: prefs.resolvedAccentTheme),
                                   darkMode: (resolved ?? systemScheme) == .dark)
            }
    }

    static func accent(named name: String) -> AccentTheme {
        AccentTheme(rawValue: name) ?? .violet
    }

    /// `nil` means "follow the system" — the same meaning `"system"` has on Android.
    static func colorScheme(for mode: String) -> ColorScheme? {
        switch mode {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }
}
```

- [ ] **Step 4: Wrap the shell**

In `ContentView`, wrap the shell: `ThemeHost { RootTabView() }`.

- [ ] **Step 5: Add previews for all eleven accents**

```swift
import SwiftUI

/// The visual check for the accent themes. Phase 2 has no Settings screen, so previews are how
/// all eleven get looked at — open the canvas and step through them.
#Preview("Accents — dark") {
    ScrollView {
        VStack(spacing: Spacing.md) {
            ForEach(AccentTheme.allCases, id: \.self) { theme in
                AccentSwatch(accent: AppAccent(theme: theme, darkMode: true))
            }
        }
        .padding()
    }
    .preferredColorScheme(.dark)
}

#Preview("Accents — light") {
    ScrollView {
        VStack(spacing: Spacing.md) {
            ForEach(AccentTheme.allCases, id: \.self) { theme in
                AccentSwatch(accent: AppAccent(theme: theme, darkMode: false))
            }
        }
        .padding()
    }
    .preferredColorScheme(.light)
}

private struct AccentSwatch: View {
    let accent: AppAccent

    var body: some View {
        HStack(spacing: Spacing.md) {
            Text(accent.theme.rawValue).appType(AppType.cardTitle)
                .foregroundStyle(accent.inkLight)
            Spacer()
            Text("Button").appType(AppType.label)
                .foregroundStyle(accent.onAccent)
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.sm)
                .background(accent.accent, in: .capsule)
        }
        .tintedCard()
        .environment(\.appAccent, accent)
    }
}
```

> The button swatch is the point: Silver, Lime and Amber are the three accents whose ink flips to
> dark, and this is where you see whether it actually did.

- [ ] **Step 6: Run, then commit**

```bash
git add -A && git commit -m "feat(shell): accent and colour-scheme wiring, plus accent previews"
```

---

# PART C — Food Log

## Task 6: `FoodLogModel` — state and the day/slot observations

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLog/FoodLogModel.swift`
- Create: `RecompTracker/RecompTrackerTests/FoodLogModelTests.swift`

Mirrors `FoodLogViewModel` (`app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt`)
for the thin slice.

> **Do Task 7 Step 3 first.** It defines `CalorieDayStatus`, `calorieStatus`, `DayCalorieSummary`
> and `PlanTargetsSnapshot` — pure value types with no dependency on the model, which this task
> references. They are grouped under Task 7 because that is where their tests live.

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import GRDB
import Testing
@testable import RecompTracker

@Suite struct FoodLogModelTests {

    private func seed(_ db: AppDatabase, date: String, slotId: Int64?, calories: Int,
                      name: String = "Meal") throws {
        try db.writer.write { d in
            var entry = MealEntry(
                id: nil, date: date, mealType: "MEAL", name: name, calories: calories,
                proteinG: 10, carbsG: 20, fatG: 5, slotId: slotId, amountGrams: nil,
                basePer100Calories: nil, basePer100ProteinG: nil, basePer100CarbsG: nil,
                basePer100FatG: nil, planned: false)
            try entry.insertPreservingID(d)
        }
    }

    private func seedSlots(_ db: AppDatabase) throws {
        try db.writer.write { d in
            for (i, name) in ["Breakfast", "Lunch", "Dinner"].enumerated() {
                var slot = MealSlot(id: nil, name: name, sortOrder: i)
                try slot.insertPreservingID(d)
            }
        }
    }

    @Test func startsOnTodayWithEmptyTotals() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        #expect(model.selectedDate.iso == "2026-08-02")
        #expect(model.totals.calories == 0)
        #expect(model.isToday)
    }

    @Test func loadsTheDaysEntriesGroupedIntoSlots() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        try seed(db, date: "2026-08-02", slotId: 1, calories: 400)
        try seed(db, date: "2026-08-02", slotId: 2, calories: 600)

        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()

        #expect(model.totals.calories == 1000)
        #expect(model.slots.count == 3)
        #expect(model.slots[0].entries.count == 1)
        #expect(model.slots[0].totals.calories == 400)
        #expect(model.slots[2].entries.isEmpty)
    }

    /// Entries on other days must not leak in — the query is date-scoped, and a bug here is
    /// invisible until totals are quietly wrong.
    @Test func ignoresEntriesOnOtherDays() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        try seed(db, date: "2026-08-02", slotId: 1, calories: 400)
        try seed(db, date: "2026-08-03", slotId: 1, calories: 999)

        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()
        #expect(model.totals.calories == 400)
    }

    /// 🔴 P1-22. A coach-logged meal has `slotId = nil`, and Phase 1b's restore can now put one on
    /// the device. It must appear in `unslotted` — otherwise it counts toward totals while being
    /// invisible on screen.
    @Test func surfacesUnslottedEntriesRatherThanHidingThem() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        try seed(db, date: "2026-08-02", slotId: nil, calories: 250, name: "Coach-logged snack")

        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()

        #expect(model.unslotted.count == 1)
        #expect(model.unslotted[0].name == "Coach-logged snack")
        #expect(model.totals.calories == 250, "it counts toward totals AND is visible")
    }

    /// An entry pointing at a since-deleted slot is the same bug wearing a different hat.
    @Test func treatsAnEntryWithADanglingSlotIdAsUnslotted() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        try seed(db, date: "2026-08-02", slotId: 999, calories: 100)

        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()
        #expect(model.unslotted.count == 1)
    }

    /// Changing the day must actually change the data, not just the label. On Android this is
    /// `_selectedDate.flatMapLatest { }`; here the restart comes from `.task(id:)`, so the model
    /// exposes the per-day work as its own entry point and this test drives it directly.
    @Test func selectingADifferentDayLoadsThatDaysEntries() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        try seed(db, date: "2026-08-02", slotId: 1, calories: 400)
        try seed(db, date: "2026-08-03", slotId: 1, calories: 900)

        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()
        #expect(model.totals.calories == 400)

        model.selectDate(CalendarDay("2026-08-03")!)
        try await model.loadOnce()
        #expect(model.totals.calories == 900)
    }

    @Test func navigatesDaysAndClampsToThirtyEachWay() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)

        model.selectDate(CalendarDay("2026-08-03")!)
        #expect(model.selectedDate.iso == "2026-08-03")
        #expect(model.isFuture)

        model.selectDate(CalendarDay("2027-01-01")!)
        #expect(model.selectedDate.iso == "2026-09-01", "clamped to +30 days")

        model.selectDate(CalendarDay("2020-01-01")!)
        #expect(model.selectedDate.iso == "2026-07-03", "clamped to -30 days")
    }

    @Test func writesAnEntryAndTotalsRecompute() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.loadOnce()
        #expect(model.totals.calories == 0)

        try await model.addEntry(name: "Oats", calories: 350, proteinG: 12, carbsG: 60,
                                 fatG: 6, slotId: 1)
        try await model.loadOnce()

        #expect(model.totals.calories == 350)
        #expect(model.totals.proteinG == 12)
        #expect(model.slots[0].entries.count == 1)
    }

    @Test func rejectsABlankNameWithoutWriting() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)

        await #expect(throws: FoodLogModel.WriteError.blankName) {
            try await model.addEntry(name: "   ", calories: 100, proteinG: 0, carbsG: 0,
                                     fatG: 0, slotId: 1)
        }
        #expect(try db.reader.read { try MealEntry.fetchCount($0) } == 0)
    }

    @Test func rejectsNegativeMacrosWithoutWriting() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)

        await #expect(throws: FoodLogModel.WriteError.negativeValue) {
            try await model.addEntry(name: "Bad", calories: -1, proteinG: 0, carbsG: 0,
                                     fatG: 0, slotId: 1)
        }
        #expect(try db.reader.read { try MealEntry.fetchCount($0) } == 0)
    }

    /// Entries are written eaten, not planned — the planned-meal flow is out of scope, and a
    /// planned entry would be excluded from totals and look like a lost write.
    @Test func writesEntriesAsEatenNotPlanned() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seedSlots(db)
        let model = FoodLogModel(database: db, today: CalendarDay("2026-08-02")!)
        try await model.addEntry(name: "Eggs", calories: 200, proteinG: 14, carbsG: 1,
                                 fatG: 15, slotId: 1)

        let stored = try #require(try db.reader.read { try MealEntry.fetchOne($0) })
        #expect(stored.planned == false)
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

```swift
import Foundation
import GRDB
import Observation
import Shared

/// One slot with its entries and their totals.
struct SlotWithEntries: Identifiable, Equatable, Sendable {
    let slot: MealSlot
    let entries: [MealEntry]
    let totals: MacroSum
    var id: Int64 { slot.id ?? -1 }
}

/// Summed macros for a set of entries.
///
/// A Swift sum rather than `:shared`'s `MacroTotals`: the *type* is exported, but Android's
/// `macroTotals()` extension lives in `:app` and operates on Room entities. The arithmetic is
/// addition, so crossing the Kotlin boundary for it would buy nothing.
struct MacroSum: Equatable, Sendable {
    var calories: Int = 0
    var proteinG: Double = 0
    var carbsG: Double = 0
    var fatG: Double = 0

    /// Planned entries are excluded — they are not eaten yet, so they must not count toward
    /// totals, adherence or trend.
    init(_ entries: [MealEntry] = []) {
        for e in entries where !e.planned {
            calories += e.calories
            proteinG += e.proteinG
            carbsG += e.carbsG
            fatG += e.fatG
        }
    }
}

/// Food Log's screen state. Mirrors Android's `FoodLogViewModel` for the Phase 2 slice.
@MainActor
@Observable
final class FoodLogModel {

    // MARK: - Published state

    private(set) var selectedDate: CalendarDay
    private(set) var today: CalendarDay
    private(set) var totals = MacroSum()
    private(set) var slots: [SlotWithEntries] = []
    private(set) var unslotted: [MealEntry] = []
    private(set) var week: [DayCalorieSummary] = []
    private(set) var target = PlanTargetsSnapshot.default
    /// Non-blocking banner text. Set on a failed read; the last-good state stays on screen.
    private(set) var errorMessage: String?

    var isToday: Bool  { selectedDate == today }
    var isFuture: Bool { selectedDate > today }
    var isPast: Bool   { selectedDate < today }

    /// How far the log lets you navigate in each direction. Matches Android's `NAV_WINDOW_DAYS`.
    static let navWindowDays = 30

    // MARK: - Init

    private let database: AppDatabase

    init(database: AppDatabase, today: CalendarDay = .today) {
        self.database = database
        self.today = today
        self.selectedDate = today
    }

    // MARK: - Activation

    /// Day-independent work: the week strip, which is scoped to `today`, not to the selected day.
    /// Call from `.task`, never from `init`.
    ///
    /// Scoping observations to view lifetime is a deliberate improvement over Android, whose
    /// ViewModels hold `combine` pipelines forever and recompute aggregates for screens nobody is
    /// looking at (review P2-21).
    func activate() async {
        await observeWeek()
    }

    /// Everything scoped to the **selected** day. Driven by `.task(id: model.selectedDate)`, so
    /// SwiftUI cancels and restarts it whenever the day changes.
    ///
    /// This is the `flatMapLatest` in Android's `_selectedDate.flatMapLatest { … }` — without the
    /// restart, the observation would keep serving whichever day happened to be selected when the
    /// screen appeared, and the ‹ › buttons would move the label but not the data.
    func observeSelectedDay() async {
        await loadTargets()
        await observeDay()
    }

    /// Single-shot load, for tests and for the initial paint.
    func loadOnce() async throws {
        let date = selectedDate.iso
        let (entries, allSlots) = try database.reader.read { d in
            (try MealEntryQueries.between(d: d, start: date, end: date),
             try MealSlot.order(Column("sort_order")).fetchAll(d))
        }
        apply(entries: entries, slots: allSlots)
    }

    // MARK: - Actions

    func selectDate(_ date: CalendarDay) {
        let low = today.adding(days: -Self.navWindowDays)
        let high = today.adding(days: Self.navWindowDays)
        selectedDate = date.clamped(to: low...high)
    }

    func stepDay(_ delta: Int) {
        selectDate(selectedDate.adding(days: delta))
    }

    enum WriteError: Error, Equatable {
        case blankName
        case negativeValue
    }

    /// Writes an eaten entry. The observation picks it up — nothing here updates state directly,
    /// which is what makes the write path end-to-end rather than two half-paths.
    func addEntry(name: String, calories: Int, proteinG: Double, carbsG: Double,
                  fatG: Double, slotId: Int64?) async throws {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw WriteError.blankName }
        guard calories >= 0, proteinG >= 0, carbsG >= 0, fatG >= 0 else {
            throw WriteError.negativeValue
        }
        let date = selectedDate.iso
        try database.writer.write { d in
            var entry = MealEntry(
                id: nil, date: date, mealType: "MEAL", name: trimmed, calories: calories,
                proteinG: proteinG, carbsG: carbsG, fatG: fatG, slotId: slotId,
                amountGrams: nil, basePer100Calories: nil, basePer100ProteinG: nil,
                basePer100CarbsG: nil, basePer100FatG: nil,
                // Eaten, not planned: a planned entry is excluded from totals and would look
                // exactly like a lost write. The planned flow is Phase 3.
                planned: false)
            try entry.insertPreservingID(d)
        }
    }

    // MARK: - Observations

    private func observeDay() async {
        do {
            let date = selectedDate.iso
            for try await pair in database.observe({ d in
                DayPayload(
                    entries: try MealEntryQueries.between(d: d, start: date, end: date),
                    slots: try MealSlot.order(Column("sort_order")).fetchAll(d))
            }) {
                apply(entries: pair.entries, slots: pair.slots)
                errorMessage = nil
            }
        } catch {
            // Keep the last-good state on screen rather than blanking a day mid-read.
            errorMessage = "Couldn't refresh this day."
        }
    }

    private func observeWeek() async {
        // Implemented in Task 7.
    }

    private func apply(entries: [MealEntry], slots allSlots: [MealSlot]) {
        let known = Set(allSlots.compactMap(\.id))
        let grouped = Dictionary(grouping: entries, by: { $0.slotId })

        self.slots = allSlots.map { slot in
            let mine = grouped[slot.id] ?? []
            return SlotWithEntries(slot: slot, entries: mine, totals: MacroSum(mine))
        }
        // Entries with no slot, or a slot that no longer exists, would otherwise vanish from the
        // list while still counting toward totals (P1-22).
        self.unslotted = entries.filter { $0.slotId == nil || !known.contains($0.slotId!) }
        self.totals = MacroSum(entries)
    }

    private struct DayPayload: Sendable {
        let entries: [MealEntry]
        let slots: [MealSlot]
    }
}
```

> `loadTargets()` is defined in Task 7 Step 4 and called from `activate()` above — without that
> call the screen renders against the default 2550 kcal target forever, which looks correct on a
> fresh install and silently wrong for every real user.

- [ ] **Step 4: Run, then commit**

```bash
git add -A && git commit -m "feat(foodlog): observable model, day grouping, and the write path"
```

---

## Task 7: Targets, the week strip, and `calorieStatus`

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLog/DayCalorieSummary.swift`
- Modify: `RecompTracker/RecompTracker/Features/FoodLog/FoodLogModel.swift`
- Create: `RecompTracker/RecompTrackerTests/CalorieStatusTests.swift`

`calorieStatus` is an `internal fun` buried in Android's screen file. On iOS it is a free function
with its own tests, because it decides what colour every day in the week strip is.

- [ ] **Step 1: Write the failing tests**

```swift
import Testing
@testable import RecompTracker

@Suite struct CalorieStatusTests {

    /// Ported from `FoodScreen.kt`'s `calorieStatus`. Order matters: the zone check comes first,
    /// so a day inside the zone is a hit even if it is in the past.
    @Test func insideTheZoneIsAGoalHit() {
        #expect(calorieStatus(cal: 2500, zoneLow: 2400, zoneHigh: 2600,
                              isToday: true, isPast: false) == .goalHit)
        #expect(calorieStatus(cal: 2400, zoneLow: 2400, zoneHigh: 2600,
                              isToday: false, isPast: true) == .goalHit, "boundary is inclusive")
        #expect(calorieStatus(cal: 2600, zoneLow: 2400, zoneHigh: 2600,
                              isToday: false, isPast: true) == .goalHit, "boundary is inclusive")
    }

    @Test func aboveTheZoneIsOverRegardlessOfDay() {
        #expect(calorieStatus(cal: 2601, zoneLow: 2400, zoneHigh: 2600,
                              isToday: true, isPast: false) == .over)
        #expect(calorieStatus(cal: 5000, zoneLow: 2400, zoneHigh: 2600,
                              isToday: false, isPast: true) == .over)
    }

    /// The distinction that matters: a past day with food logged but under the zone is a MISS;
    /// today under the zone is simply not there yet.
    @Test func belowTheZoneIsAMissOnlyForAPastDayWithFood() {
        #expect(calorieStatus(cal: 1200, zoneLow: 2400, zoneHigh: 2600,
                              isToday: false, isPast: true) == .missed)
        #expect(calorieStatus(cal: 1200, zoneLow: 2400, zoneHigh: 2600,
                              isToday: true, isPast: false) == .belowZone)
    }

    /// An empty past day is NOT a miss — you did not fail a day you never logged.
    @Test func anEmptyPastDayIsBelowZoneNotMissed() {
        #expect(calorieStatus(cal: 0, zoneLow: 2400, zoneHigh: 2600,
                              isToday: false, isPast: true) == .belowZone)
    }

    @Test func aFutureDayIsNeverAMiss() {
        #expect(calorieStatus(cal: 0, zoneLow: 2400, zoneHigh: 2600,
                              isToday: false, isPast: false) == .belowZone)
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

```swift
import Foundation

/// How a day reads against its calorie zone.
nonisolated enum CalorieDayStatus: Sendable, Equatable {
    case belowZone, goalHit, over, missed
}

/// Ported verbatim from `FoodScreen.kt`.
///
/// The clause order is load-bearing: the zone test runs first, so a past day inside the zone is a
/// hit rather than a miss — and an empty past day is `belowZone`, not `missed`, because you did
/// not fail a day you never logged.
nonisolated func calorieStatus(cal: Int, zoneLow: Int, zoneHigh: Int,
                               isToday: Bool, isPast: Bool) -> CalorieDayStatus {
    if cal >= zoneLow && cal <= zoneHigh { return .goalHit }
    if cal > zoneHigh { return .over }
    if isPast && cal > 0 { return .missed }
    return .belowZone
}

/// One day in the week strip.
struct DayCalorieSummary: Identifiable, Equatable, Sendable {
    let date: CalendarDay
    let calories: Int
    let targetCalories: Int
    let zoneLowerBound: Int
    let zoneUpperBound: Int
    var id: String { date.iso }
}

/// The plan targets in force on a given day, already rebalance-adjusted.
///
/// A Swift snapshot rather than the Kotlin `PlanPreferences`, so Kotlin objects stay out of view
/// state (D14). The *resolution* is still `:shared`'s — see `FoodLogModel.resolveTarget`.
struct PlanTargetsSnapshot: Equatable, Sendable {
    var calories: Int
    var proteinG: Int
    var carbsG: Int
    var fatG: Int
    var zoneLowerBound: Int
    var zoneUpperBound: Int

    /// Android's `PlanPreferences` defaults, verified in Phase 1b.
    static let `default` = PlanTargetsSnapshot(
        calories: 2550, proteinG: 165, carbsG: 320, fatG: 68,
        zoneLowerBound: 2400, zoneUpperBound: 2600)
}
```

- [ ] **Step 4: Replace the `observeWeek` stub and add the target read**

Replace the `observeWeek` stub in `FoodLogModel`:

```swift
    /// The trailing seven days, each with the target that was in force on it.
    private func observeWeek() async {
        let start = today.adding(days: -6)
        let days = (0...6).map { start.adding(days: $0) }
        do {
            for try await totalsByDate in database.observe({ d -> [String: Int] in
                let rows = try MealEntryQueries.between(
                    d: d, start: start.iso, end: self.today.iso)
                return rows.reduce(into: [:]) { acc, e in
                    guard !e.planned else { return }
                    acc[e.date, default: 0] += e.calories
                }
            }) {
                self.week = days.map { day in
                    DayCalorieSummary(
                        date: day,
                        calories: totalsByDate[day.iso] ?? 0,
                        targetCalories: self.target.calories,
                        zoneLowerBound: self.target.zoneLowerBound,
                        zoneUpperBound: self.target.zoneUpperBound)
                }
            }
        } catch {
            errorMessage = "Couldn't refresh the week."
        }
    }

    /// Reads plan targets and rebalance state ONCE, at activation, and overlays the
    /// rebalance-effective target for the selected day.
    ///
    /// The overlay is not optional: during a rebalance the agreed target is *reduced*, and judging
    /// the day against the base number would tell the user they are under when they are not.
    ///
    /// ⚠️ Deliberate, dated shortcut. Android observes both as Flows, but they live in
    /// `JSONStore`, which has `value()`/`set()` and **no change stream**. Nothing in Phase 2 can
    /// change them — Plan and Settings do not exist yet — so a value read here cannot go stale
    /// while the user is looking at it. **Phase 3 must add an `AsyncStream` to `JSONStore` before
    /// it ships Plan**, or this screen will quietly show yesterday's target.
    func loadTargets() async {
        guard let planStore = try? PlanPreferencesStore(),
              let rebalanceStore = try? RebalanceStore()
        else { return }   // keep the defaults; the banner is not worth it for a first paint

        let plan = await planStore.value()
        let state = await rebalanceStore.current()

        let base = PlanTargets(calories: Int32(plan.targetCalories),
                               proteinG: Int32(plan.targetProteinG),
                               carbsG: Int32(plan.targetCarbsG),
                               fatG: Int32(plan.targetFatG),
                               zoneLowerBound: Int32(plan.calorieZoneLowerBound),
                               zoneUpperBound: Int32(plan.calorieZoneUpperBound))
        let effective = EffectiveTargets.shared.resolve(
            base: base, date: selectedDate.kotlin, state: state)

        target = PlanTargetsSnapshot(
            calories: Int(effective.calories), proteinG: Int(effective.proteinG),
            carbsG: Int(effective.carbsG), fatG: Int(effective.fatG),
            zoneLowerBound: Int(effective.zoneLowerBound),
            zoneUpperBound: Int(effective.zoneUpperBound))
    }
```

> Signatures confirmed against the generated header, not guessed:
> `EffectiveTargets.shared.resolve(base:date:state:)` returns `PlanTargets`, and
> `PlanTargets(calories:proteinG:carbsG:fatG:zoneLowerBound:zoneUpperBound:)` takes `Int32`.
> `RebalanceStore.current()` already returns a decoded `RebalanceState` — do **not** call
> `RebalanceSerialization.decode` again on top of it.
>
> `loadTargets()` resolves for `selectedDate`, so it must re-run when the day changes — which it
> does, because it is called from `observeSelectedDay()` and that is driven by
> `.task(id: model.selectedDate)`. Without the restart, a user stepping into a rebalance window
> would keep the previous day's target.

- [ ] **Step 5: Run, then commit**

```bash
git add -A && git commit -m "feat(foodlog): calorie status, week strip, and rebalance-effective targets"
```

---

## Task 8: The views

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLog/FoodLogScreen.swift`
- Create: `RecompTracker/RecompTracker/Features/FoodLog/DayHeader.swift`
- Create: `RecompTracker/RecompTracker/Features/FoodLog/WeekStrip.swift`
- Create: `RecompTracker/RecompTracker/Features/FoodLog/NutritionStrip.swift`
- Create: `RecompTracker/RecompTracker/Features/FoodLog/SlotCard.swift`

Six files against Android's single 1,428-LOC screen. No tests here — this is rendering, and it is
verified visually (standing rule 5). Every view reads its type from `AppType` and its colour from
`AppColor` / `\.appAccent`; **a raw `.font(.system(size:))` in any of these files is a defect.**

- [ ] **Step 1: `DayHeader.swift`**

```swift
import SwiftUI

/// Title, the selected date, and ‹ › navigation clamped to ±30 days.
struct DayHeader: View {
    let date: CalendarDay
    let isToday: Bool
    let onStep: (Int) -> Void

    var body: some View {
        HStack(spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text("Food Log").appType(AppType.screenTitle)
                    .foregroundStyle(AppColor.textPrimary.color)
                Text(subtitle).appType(AppType.screenSubtitle)
                    .foregroundStyle(AppColor.textSecondary.color)
            }
            Spacer()
            Button { onStep(-1) } label: { Image(systemName: "chevron.left") }
                .accessibilityLabel("Previous day")
            Button { onStep(1) } label: { Image(systemName: "chevron.right") }
                .accessibilityLabel("Next day")
        }
        .buttonStyle(.glass)
        .screenGutter()
    }

    private var subtitle: String {
        isToday ? "Today · \(date.iso)" : date.iso
    }
}
```

- [ ] **Step 2: `NutritionStrip.swift`**

```swift
import SwiftUI

/// The calorie hero plus three macro bars.
struct NutritionStrip: View {
    let totals: MacroSum
    let target: PlanTargetsSnapshot
    let status: CalorieDayStatus

    @Environment(\.appAccent) private var accent

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.xs) {
                Text("\(totals.calories)").appType(AppType.displayLarge)
                    .foregroundStyle(AppColor.textPrimary.color)
                Text(subtext).appType(AppType.cardSubtitle)
                    .foregroundStyle(AppColor.textSecondary.color)
            }
            MacroBar(label: "Protein", value: totals.proteinG, target: Double(target.proteinG))
            MacroBar(label: "Carbs", value: totals.carbsG, target: Double(target.carbsG))
            MacroBar(label: "Fat", value: totals.fatG, target: Double(target.fatG))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frostedCard()
        .screenGutter()
    }

    /// Mirrors Android's `calSubText`: the same four statuses, the same phrasing.
    private var subtext: String {
        switch status {
        case .goalHit:   " kcal"
        case .over:      " kcal · \(totals.calories - target.zoneUpperBound) over"
        case .missed:    " kcal · \(target.zoneLowerBound - totals.calories) below zone"
        case .belowZone: " kcal · \(target.zoneLowerBound - totals.calories) to zone"
        }
    }
}

private struct MacroBar: View {
    let label: String
    let value: Double
    let target: Double

    @Environment(\.appAccent) private var accent

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack {
                Text(label.uppercased()).appTypeScaled(AppType.metaLabel)
                    .foregroundStyle(AppColor.textMuted.color)
                Spacer()
                Text("\(Int(value.rounded()))/\(Int(target))g").appType(AppType.label)
                    .foregroundStyle(AppColor.textSecondary.color)
            }
            ProgressView(value: fraction)
                .tint(accent.inkLight)
        }
    }

    /// Guards the divide: a zero target must not produce NaN and blank the bar.
    private var fraction: Double {
        guard target > 0 else { return 0 }
        return min(max(value / target, 0), 1)
    }
}
```

- [ ] **Step 3: `WeekStrip.swift`**

```swift
import SwiftUI

/// Seven days, each coloured by how it read against its calorie zone.
struct WeekStrip: View {
    let days: [DayCalorieSummary]
    let today: CalendarDay
    let onSelect: (CalendarDay) -> Void

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(days) { day in
                Button { onSelect(day.date) } label: {
                    VStack(spacing: Spacing.xs) {
                        Text(weekdayInitial(day.date)).appTypeScaled(AppType.metaLabel)
                        Circle().fill(color(for: day)).frame(width: 8, height: 8)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .foregroundStyle(AppColor.textSecondary.color)
                .accessibilityLabel("\(day.date.iso), \(day.calories) kilocalories")
            }
        }
        .frostedCard(padding: Spacing.md)
        .screenGutter()
    }

    private func color(for day: DayCalorieSummary) -> Color {
        switch calorieStatus(cal: day.calories,
                             zoneLow: day.zoneLowerBound, zoneHigh: day.zoneUpperBound,
                             isToday: day.date == today, isPast: day.date < today) {
        case .goalHit:   .green
        case .over, .missed: AppColor.errorInk.color
        case .belowZone: AppColor.textVeryMuted.color
        }
    }

    /// First letter of the weekday, from the ISO string — no `Date` round-trip (D6).
    private func weekdayInitial(_ day: CalendarDay) -> String {
        let names = ["S", "M", "T", "W", "T", "F", "S"]
        var c = DateComponents()
        let parts = day.iso.split(separator: "-")
        c.year = Int(parts[0]); c.month = Int(parts[1]); c.day = Int(parts[2]); c.hour = 12
        let cal = Calendar(identifier: .gregorian)
        guard let d = cal.date(from: c) else { return "?" }
        return names[cal.component(.weekday, from: d) - 1]
    }
}
```

- [ ] **Step 4: `SlotCard.swift`**

```swift
import SwiftUI

/// One meal slot: header, its entries, and the add affordance.
struct SlotCard: View {
    let title: String
    let entries: [MealEntry]
    let totals: MacroSum
    /// Nil for the Unassigned section, which has no slot to add into.
    let onAdd: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                Text(title.uppercased()).appTypeScaled(AppType.sectionLabel)
                    .foregroundStyle(AppColor.textMuted.color)
                Spacer()
                Text("\(totals.calories) kcal").appType(AppType.label)
                    .foregroundStyle(AppColor.textSecondary.color)
            }
            if entries.isEmpty {
                Text("Nothing logged").appType(AppType.cardSubtitle)
                    .foregroundStyle(AppColor.textVeryMuted.color)
            } else {
                ForEach(entries, id: \.id) { EntryRow(entry: $0) }
            }
            if let onAdd {
                Button("Add", systemImage: "plus", action: onAdd)
                    .buttonStyle(.glass)
                    .controlSize(.small)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .neutralCard()
        .screenGutter()
    }
}

private struct EntryRow: View {
    let entry: MealEntry

    var body: some View {
        HStack {
            Text(entry.name).appType(AppType.cardTitle)
                .foregroundStyle(AppColor.textPrimary.color)
            Spacer()
            Text("\(entry.calories)").appType(AppType.statValueSmall)
                .foregroundStyle(AppColor.textSecondary.color)
        }
        .accessibilityElement(children: .combine)
    }
}
```

- [ ] **Step 5: `FoodLogScreen.swift`**

```swift
import SwiftUI

/// Composition root. Owns the model and its activation; every child is a pure view of state.
struct FoodLogScreen: View {
    let database: AppDatabase

    @State private var model: FoodLogModel
    @State private var addingToSlot: MealSlot?

    init(database: AppDatabase) {
        self.database = database
        _model = State(initialValue: FoodLogModel(database: database))
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: Layout.screenSpacing) {
                DayHeader(date: model.selectedDate, isToday: model.isToday) { model.stepDay($0) }
                WeekStrip(days: model.week, today: model.today) { model.selectDate($0) }
                NutritionStrip(totals: model.totals, target: model.target, status: status)

                ForEach(model.slots) { slotted in
                    SlotCard(title: slotted.slot.name, entries: slotted.entries,
                             totals: slotted.totals) { addingToSlot = slotted.slot }
                }
                // P1-22: coach-logged meals carry no slot. Without this they count toward totals
                // while being invisible.
                if !model.unslotted.isEmpty {
                    SlotCard(title: "Unassigned", entries: model.unslotted,
                             totals: MacroSum(model.unslotted), onAdd: nil)
                }
            }
            .padding(.vertical, Layout.screenSpacing)
        }
        .background(AppColor.scrim.color)
        // Two tasks, deliberately. The week strip is scoped to `today`; everything else is scoped
        // to the SELECTED day, and `.task(id:)` restarts it on every day change — the SwiftUI
        // equivalent of Android's `_selectedDate.flatMapLatest { }`.
        .task { await model.activate() }
        .task(id: model.selectedDate) { await model.observeSelectedDay() }
        .sheet(item: $addingToSlot) { slot in
            QuickAddSheet(slotName: slot.name) { name, cal, p, c, f in
                try await model.addEntry(name: name, calories: cal, proteinG: p,
                                         carbsG: c, fatG: f, slotId: slot.id)
            }
        }
        .overlay(alignment: .top) {
            if let message = model.errorMessage {
                Text(message).appType(AppType.body)
                    .foregroundStyle(AppColor.textPrimary.color)
                    .frostedCard(padding: Spacing.md)
                    .screenGutter()
            }
        }
    }

    private var status: CalorieDayStatus {
        calorieStatus(cal: model.totals.calories,
                      zoneLow: model.target.zoneLowerBound,
                      zoneHigh: model.target.zoneUpperBound,
                      isToday: model.isToday, isPast: model.isPast)
    }
}

extension MealSlot: Identifiable {}
```

- [ ] **Step 6: Build both configurations, then commit**

```bash
git add -A && git commit -m "feat(foodlog): the six screen views on native glass"
```

---

## Task 9: Quick-add sheet

**Files:**
- Create: `RecompTracker/RecompTracker/Features/FoodLog/QuickAddSheet.swift`
- Create: `RecompTracker/RecompTrackerTests/QuickAddValidationTests.swift`

The Food Library is Phase 3, so this is how Phase 2 writes. It is not scaffolding — a manual quick
add is a genuinely useful path, and it stays as the fallback once the Library lands.

- [ ] **Step 1: Write the failing tests**

```swift
import Testing
@testable import RecompTracker

@Suite struct QuickAddValidationTests {

    @Test func acceptsAWellFormedEntry() {
        let draft = QuickAddDraft(name: "Oats", calories: "350", proteinG: "12",
                                  carbsG: "60", fatG: "6")
        #expect(draft.validated() != nil)
    }

    /// Blank and whitespace-only names are the same thing — the Android codecs treat them alike.
    @Test func rejectsABlankOrWhitespaceName() {
        #expect(QuickAddDraft(name: "", calories: "100").validated() == nil)
        #expect(QuickAddDraft(name: "   ", calories: "100").validated() == nil)
    }

    @Test func rejectsNonNumericOrNegativeValues() {
        #expect(QuickAddDraft(name: "X", calories: "abc").validated() == nil)
        #expect(QuickAddDraft(name: "X", calories: "-5").validated() == nil)
        #expect(QuickAddDraft(name: "X", calories: "100", proteinG: "-1").validated() == nil)
    }

    /// Empty macro fields mean zero, not invalid — you should be able to log calories alone.
    @Test func treatsEmptyMacroFieldsAsZero() {
        let draft = QuickAddDraft(name: "Coffee", calories: "5")
        let v = try? #require(draft.validated())
        #expect(v?.proteinG == 0)
        #expect(v?.carbsG == 0)
        #expect(v?.fatG == 0)
    }

    @Test func trimsTheNameBeforeStoringIt() {
        #expect(QuickAddDraft(name: "  Oats  ", calories: "350").validated()?.name == "Oats")
    }
}
```

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement**

```swift
import SwiftUI

/// The raw text fields, before validation. Separating this from the view makes the rules testable
/// without driving UI.
struct QuickAddDraft: Equatable {
    var name: String = ""
    var calories: String = ""
    var proteinG: String = ""
    var carbsG: String = ""
    var fatG: String = ""

    struct Validated: Equatable {
        let name: String
        let calories: Int
        let proteinG: Double
        let carbsG: Double
        let fatG: Double
    }

    /// Nil when the draft is not yet loggable. Empty macro fields are zero — logging calories
    /// alone is a legitimate thing to want.
    func validated() -> Validated? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        guard let cal = Int(calories), cal >= 0 else { return nil }
        guard let p = optionalDouble(proteinG), let c = optionalDouble(carbsG),
              let f = optionalDouble(fatG) else { return nil }
        return Validated(name: trimmed, calories: cal, proteinG: p, carbsG: c, fatG: f)
    }

    private func optionalDouble(_ s: String) -> Double? {
        if s.isEmpty { return 0 }
        guard let v = Double(s), v >= 0 else { return nil }
        return v
    }
}

/// Manual entry — the Phase 2 write path, and the fallback once the Library lands in Phase 3.
struct QuickAddSheet: View {
    let slotName: String
    let onSave: (String, Int, Double, Double, Double) async throws -> Void

    @State private var draft = QuickAddDraft()
    @State private var errorMessage: String?
    @State private var isSaving = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $draft.name)
                    TextField("Calories", text: $draft.calories).keyboardType(.numberPad)
                }
                Section("Macros (grams)") {
                    TextField("Protein", text: $draft.proteinG).keyboardType(.decimalPad)
                    TextField("Carbs", text: $draft.carbsG).keyboardType(.decimalPad)
                    TextField("Fat", text: $draft.fatG).keyboardType(.decimalPad)
                }
                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(AppColor.errorInk.color) }
                }
            }
            .navigationTitle("Add to \(slotName)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .disabled(draft.validated() == nil || isSaving)
                }
            }
        }
    }

    private func save() {
        guard let v = draft.validated() else { return }
        isSaving = true
        Task {
            do {
                try await onSave(v.name, v.calories, v.proteinG, v.carbsG, v.fatG)
                dismiss()
            } catch {
                // Keep the sheet open with what the user typed — dismissing on failure loses it.
                errorMessage = "Couldn't save that entry."
                isSaving = false
            }
        }
    }
}
```

- [ ] **Step 4: Run, then commit**

```bash
git add -A && git commit -m "feat(foodlog): quick-add sheet, the Phase 2 write path"
```

---

# PART D — Verification

## Task 10: Measure the tab-switch observation behaviour

The spec names this as a thing to verify rather than assume.

- [ ] **Step 1: Add an instrumented counter**

Temporarily add a `private(set) var activationCount = 0` to `FoodLogModel`, incremented at the top
of `activate()`.

- [ ] **Step 2: Measure in the simulator**

Launch, switch Food → Home → Food three times, and read the counter (a `Text` in the header, or a
breakpoint).

- [ ] **Step 3: Record the answer**

Write what you observed into the *Session log* entry in `docs/ios-port/STATUS.md`:
- If `.task` is cancelled and restarted per switch: note it, confirm the re-fetch produces no
  visible flicker, and move on — one day-query is cheap.
- If there IS a flicker: hoist the observation above the `TabView` so it survives tab switches, and
  record that as the reason.

- [ ] **Step 4: Remove the counter and commit**

```bash
git add -A && git commit -m "test(foodlog): measure .task lifetime across tab switches"
```

---

## Task 11: Full verification and documentation

- [ ] **Step 1: Both configurations**

```bash
cd ~/Desktop/RecompTracker-IOS
xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
xcodebuild -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -configuration Release build
```

Expected: TEST SUCCEEDED, BUILD SUCCEEDED, **zero warnings in first-party code**. Release is not
optional — it caught two isolation warnings Debug missed in Phase 1b.

- [ ] **Step 2: Confirm no raw type or colour crept in**

```bash
cd ~/Desktop/RecompTracker-IOS/RecompTracker/RecompTracker
grep -rn "\.font(\.system(size:" Features Shell | grep -v DesignSystem
grep -rn "Color(red:\|Color(\.sRGB" Features Shell
```
Expected: both silent. A hit is a design-system leak, not a style preference.

- [ ] **Step 3: Record D15–D19**

Append decisions D15–D19 to `docs/ios-port/decisions.md` in the Android repo, from the design spec's
table, and tick their entries under *Conventions still to decide*.

- [ ] **Step 4: Update the port docs**

`parity-ledger.md` — tick the four Design-system rows this phase delivers (tokens, colours, card
family, buttons) and the Food Log screen row as 🔨.
`STATUS.md` — phase board, a session-log entry with counts and surprises, and the *Needs visual
check* list below.

- [ ] **Step 5: Hand off for visual check**

List for the user:
- the tab bar's glass, and minimise-on-scroll
- Dynamic Type at the largest accessibility size
- light mode
- each of the eleven accent themes

- [ ] **Step 6: Commit both repos**

---

## What Phase 2 deliberately does NOT do

- **No Food Library** — Phase 3, ~1,900 LOC.
- **No planned-meal flow** — no reconcile banner, no confirm/postpone.
- **No slot edit mode**, recipe selection, or macro-edit dialog.
- **No meal-suggestion card.**
- **No rebalance chip** — though the rebalance *maths* runs, because the day's target must be the
  effective one.
- **No Home, Body or Coach** beyond placeholders.
- **No `JSONStore` change stream** — read-once is sufficient while nothing can change preferences.
  Phase 3 must add it before shipping Plan.

## Rollback

Entirely additive, on its own branch. Nothing in `Persistence/` changes; the Android repo receives
documentation only.
