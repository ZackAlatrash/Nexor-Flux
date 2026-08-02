# iOS Phase 2 — App shell, design system, Food Log

**Date:** 2026-08-02 · **Status:** approved, ready for an implementation plan
**Repos:** design system + UI land in `~/Desktop/RecompTracker-IOS`. No Android changes.

## Goal

A walking skeleton: the app launches into a native tab bar, one real screen reads and writes the
database, and the design system that Phase 3's twenty screens will lean on exists and has been
looked at.

**Acceptance:** log a meal on iOS via the quick-add sheet, see it land in GRDB, and see the day's
totals recompute.

To be precise about the "matches Android" half, since it is not one assertion: the arithmetic is
`MacroTotals` in `:shared`, already golden-tested on Kotlin/Native, so agreement is guaranteed by
construction rather than checked here. What Phase 2 actually has to prove is the loop around it —
write, observation fires, state updates, view re-renders — which is a Swift test against the
in-memory database. The user confirms the rendered numbers visually.

## Decisions taken in this session

| | Decision | Why |
|---|---|---|
| **D15** | Native iOS 26 Liquid Glass, not a port of the Android glass approximation | The Android glass layer exists *because* Compose has no native equivalent. On iOS the real material is available, and a hand-rolled version of the one component users stare at most would look worse. Accepts that the two apps will not be pixel-identical. |
| **D16** | `TabView` + a `NavigationStack` per tab | Settles the open navigation convention. Android has one shared back stack across tabs; iOS keeps per-tab history. The native tab bar is where iOS 26 glass does its most visible work (floating, minimise-on-scroll, morphing) and it is free only inside `TabView`. |
| **D17** | Four tabs — Home, Body, Food, Coach — with More behind a header control | Train is v1.1 (D4). Reserving the fifth slot means the tab bar never gets re-cut under a user who has already learned it; iOS also starts auto-collapsing past five. |
| **D18** | Dynamic Type, replacing Android's fixed `sp` scale | Accessibility, and App Review notices. Costs pixel-parity with Android at large sizes and requires layouts to flex. |
| **D19** | One `@Observable` model per screen, activated by `.task` | Settles the open view-model convention. Android's 32 ViewModels are uniform `MutableStateFlow<UiState>` classes, so this makes each later screen a mechanical port. `.task` rather than `init` fixes P2-21 in passing. |

These belong in `docs/ios-port/decisions.md` when the plan lands.

## Scope

**In:** the design-system layer · the four-tab shell with three placeholder screens · Food Log
regions 1, 2, 3, 6, 7 · a quick-add sheet.

**Out, deliberately:** the Food Library and the real add path (Phase 3, ~1,900 LOC) · the reconcile
banner and planned-meal flow · the deterministic meal-suggestion card · slot edit mode
(add/rename/delete/reorder) · recipe selection · the macro-edit dialog · the rebalance "Day X of Y"
chip · Home, Body and Coach beyond placeholders.

**Two scope notes that are not arbitrary:**

*The rebalance chip is out; the rebalance maths is in.* `EffectiveTargets.resolve` still runs, because
the day's target must be the rebalance-effective one or the nutrition strip judges the user against a
number they never agreed to. Only the label is deferred.

*Region 7 (Unassigned) is in, despite being outside the chosen slice.* Coach-logged meals carry
`slotId = nil`, and Phase 1b's restore can now put them on the device. Without that section they
count toward totals while being invisible — exactly review bug **P1-22**. It is ~20 lines and it
prevents shipping a known invisible-data defect into the first build in real use.

## Architecture

```
RecompTracker/
  Persistence/            ← complete (Phases 1a, 1b)
  DesignSystem/
    Typography.swift        AppType → Font, Dynamic Type
    Palette.swift           accent themes via @Environment
    Spacing.swift           gutters, corners, rhythm
    Surfaces.swift          .frostedCard() / .neutralCard() / .tintedCard()
    CalendarDay.swift       YYYY-MM-DD value type
    Colors.xcassets         semantic neutrals, Any + Dark
  Shell/
    RootTabView.swift       four tabs
    AppTab.swift
  Features/
    FoodLog/                model + six views
    Home/ Body/ Coach/      placeholders
```

### Design system

**Typography.** Eleven of the thirteen `AppType` tokens map onto a native text style and scale for
free — `cardTitle` 15/SemiBold → `.subheadline.semibold`, `body` 13 → `.footnote`, `screenTitle`
28/ExtraBold → `.largeTitle.weight(.heavy)` with −0.8 tracking preserved. The exceptions are
`sectionLabel` and `metaLabel` at 9pt: iOS has no text style below 11pt, so those use `@ScaledMetric`
relative to `.caption2`. Every token keeps its name, so the design system's leading rule — never
hardcode a font size — survives the port.

**Colour splits in two.** Six of the seventeen semantic tokens exist only to approximate glass:
`frostedSurface`, `frostedSurfaceFallback`, `frostedBorder`, `glassOverlay`, `glassShimmer`,
`glassPillSurface`. `.glassEffect` supplies all six, so they are deleted rather than ported. The
remaining neutrals become Color Sets in an asset catalog with Any/Dark appearances, which gives
light and dark with no `LocalAppColors` equivalent.

The accent cannot be an asset: eleven accent themes are chosen at runtime from `UiPreferences`, so it
stays an `@Environment` value — the direct analogue of `LocalAppAccent`. `themeMode`
(system/light/dark) drives `.preferredColorScheme()`.

**Surfaces are three view modifiers, not three view types.** `.frostedCard()` wraps `.glassEffect`;
`.neutralCard()` and `.tintedCard()` layer the accent. Buttons get no wrapper — `.buttonStyle(.glass)`
and `.glassProminent` are the real thing.

For scale: `LiquidComponents.kt` + `GlassComponents.kt` + `LiquidUtils.kt` +
`LiquidSegmentedToggle.kt` total 2,218 LOC on Android, most of it existing to fake glass. The iOS
design system should land near 400.

**`CalendarDay`.** A ~40-line value type wrapping the `YYYY-MM-DD` string, `Comparable`, with
`±days`. D6 persists dates as strings, `:shared` speaks Kotlin `LocalDate`, and day arithmetic needs
a representation — this keeps D6 honest, keeps Kotlin objects out of view state, and converts only at
the `:shared` boundary. Introducing a second date representation is the mistake that would be
expensive to undo across Phase 3.

### Shell

`RootTabView` holds four `Tab`s (iOS 26's value-based API), each wrapping a `NavigationStack`. The
glass tab bar, scroll-edge effects and minimise-on-scroll come free. Home, Body and Coach are
one-line placeholders; More opens from a toolbar control on Home, matching Android. ~80 LOC.

### Food Log

Six files, against Android's single 1,428-LOC / 16-composable `FoodScreen.kt`:

| File | Responsibility |
|---|---|
| `FoodLogModel.swift` | `@Observable`, mirrors `FoodLogViewModel` |
| `FoodLogScreen.swift` | composition root |
| `DayHeader.swift` | title, date, ‹ › nav clamped to ±30 days |
| `WeekStrip.swift` | seven days coloured against the calorie zone |
| `NutritionStrip.swift` | calorie hero + three macro bars |
| `SlotCard.swift` | one slot: header, entry rows, **+ Add**. The Unassigned section reuses its row view with no slot header. |
| `QuickAddSheet.swift` | name + macros — the write path |

Each fits in one screenful, which is the property the Android file lost.

**Reused from `:shared`, not rewritten:** `EffectiveTargets`, `PlanHistory`, `MacroTotals`, the streak
calculator. All Kotlin, all already golden-tested on iOS. The Swift model is state plumbing.

## Data flow

**Reads.** `.task { }` activates the model, which runs three concurrent `for try await` loops over
`database.observe { }` — the day, the slots, the week window. (Android runs four; the fourth feeds
the deferred suggestion card.) Each assigns to `@Observable` properties and SwiftUI re-renders.

**Preferences are read once, and that is a deliberate, dated shortcut.** Android's day pipeline
`combine`s five sources: the day, the slots, *and* `planRepository.preferences`, `observePlanOn(date)`
and `rebalanceStore.state`. Only the first three are GRDB tables. `PlanPreferences` and
`RebalanceState` live in `JSONStore`, which today exposes `value()` and `set()` and **no change
stream** — so they cannot be observed the way Android observes them.

For Phase 2 this is genuinely sufficient: the only screens that can change a plan target or start a
rebalance are Plan and Settings, and neither exists yet, so a value read at activation cannot go
stale while the user is looking at it. It stops being sufficient the moment Phase 3 lands Plan —
at which point `JSONStore` needs an `AsyncStream` that `set` yields to (~15 lines), and the model
picks up two more loops. **Recorded here so Phase 3 starts by paying it, rather than discovering a
screen that silently shows yesterday's target.**

Scoping to `.task` rather than `init` is deliberate and improves on Android: its ViewModels hold
`combine` pipelines for their whole lifetime, recomputing aggregates for screens nobody is looking at
(review **P2-21** — only `StreakViewModel` uses `WhileSubscribed`). The Phase 1a observation helper
already carries this note.

**Writes.** `QuickAddSheet` → `model.addEntry` → GRDB write → the observation fires → totals
recompute. Nothing updates state manually on the write path. That is what makes the acceptance
criterion one end-to-end assertion rather than three separate ones.

## Error handling

Database-open failure is already handled from Phase 1b. Observation errors keep the last-good state
and surface a non-blocking banner rather than blanking a screen mid-read; write failures keep the
sheet open with an inline message. Android collapses everything into one generic string and
`decisions.md` has a proper error taxonomy queued for Phase 5 — Phase 2 should not invent one, and
should not swallow either.

## Testing

The model tests without UI: inject the in-memory `AppDatabase` that already exists, drive it, assert
published state — the same Swift Testing setup as the 265 existing tests. Pure logic gets extracted
so it is directly testable; `calorieStatus` is currently an `internal fun` buried in the Android
screen file and belongs on iOS as a free function with its own tests.

**No snapshot tests.** Glass renders differently across OS versions and device configurations, so
snapshots of it fail for reasons that are not regressions.

**Needs visual check** (the user verifies UI personally — standing rule 5):
- the tab bar's glass, and minimise-on-scroll
- Dynamic Type at the largest accessibility size
- light mode
- each of the eleven accent themes

## One thing to verify, not assume

Whether SwiftUI cancels and restarts `.task` when switching tabs. If it does, the re-fetch is a
single day-query and should be invisible — but "should be" is not a measurement. Phase 2 measures it
and records the answer; if there is a visible flicker, the fix is to hold the observation above the
tab rather than inside it.

## Rollback

Entirely additive in the iOS repo, on its own branch. Nothing in `Persistence/` changes and the
Android repo is untouched.
