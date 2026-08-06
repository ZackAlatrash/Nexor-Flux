# iOS Port — STATUS

**Read this first, every session. Keep it under ~160 lines and the session log to 3 entries.**
Anything longer belongs in a phase plan or a reference doc. The session log is the only part that
grows without bound — archive older entries into the docs that carry their detail.

**Last updated:** 2026-08-06 · **Current phase:** Phase 3b + a cross-screen consistency pass — built,
pending visual pass + merge ·
**Branches:** Android `develop` · iOS `phase-3b-dashboard-and-body` (unmerged, off the also-unmerged
`phase-3a-food-library`).
iOS work happens in `~/Desktop/RecompTracker-IOS/`.

---

## Where we are

**Phases 0 through 3b are built** — see the phase board below for the per-phase status and the
linked plan for each one's detail. In plain terms: the database, the ten preference stores, all
three file formats, the design system on native Liquid Glass, a four-tab shell, and five real
screens. The app opens on a working dashboard, food can be logged end to end, and a weekly rebalance
can be offered, customised, accepted and tracked.

On top of 3b, **a cross-screen consistency pass** (17 commits, 3 waves, **D28–D31**): every screen
from Phases 2–3b audited together for title, type, spacing, motion and behaviour, and the drift
fixed. Seven new shared components; the app now has one header system and one number format.

🔴 **One thing from Phase 1b is still outstanding** — the backup acceptance test needs a real
Android export. Its 9 tests are written and **self-skip** until the fixture lands. See
*Blocked / needs you*.

### Numbers

| | |
|---|---|
| iOS tests | **870** (861 running + 9 armed) · 0 warnings · Debug + Release green |
| iOS code | ~4,600 LOC `Persistence/` · ~700 `DesignSystem/` · ~250 `Shell/` · ~1,400 `Features/FoodLog/` · ~2,600 `Features/FoodLibrary/` · ~700 `Features/RecipeBuilder/` |
| `:shared` commonMain | 71 files (66 + the 5 moved codecs) |
| `:shared` commonTest | **372 golden assertions** — run on JVM *and* iOS |
| Kotlin tests | `:app` 967 · `:shared` JVM 450 · `:shared` iOS 11 — **1417 total, unchanged** |
| Release iOS app | **16 MB** (was 14 MB after 1a; +1.2 MB of bundled JSON assets) |
| Kotlin/Native cold compile | **13.2 s** · warm incremental **0.8 s** · XCFramework 22.5 s |

## The decision in one paragraph

Native Swift/SwiftUI on **iOS 26+**, sharing **only `domain/`** via a KMP `:shared` module;
everything else rebuilt natively (GRDB, `URLSession`, Keychain, HealthKit). v1 = core loop **+ AI
coach**; Train and CSV import are **v1.1**. Reasoning:
[00-feasibility-and-roadmap.md](00-feasibility-and-roadmap.md) · conventions: [decisions.md](decisions.md).

## Phase board

| Phase | What | Status |
|---|---|---|
| **0** | Extract `:shared`, `java.time` → kotlinx-datetime, golden-value tests. **Decision gate.** | ✅ **done — gate passed** |
| **[1a](phases/phase-1a-database.md)** | GRDB + 19 tables + records + queries + transactions | ✅ **done — 71 tests** |
| **[1b](phases/phase-1b-stores-and-formats.md)** | Preference stores, Keychain, bundled assets, file codecs | ✅ **done — 265 tests** (fixture pending) |
| **[2](phases/phase-2-shell-and-food-log.md)** | App shell, design system, **Food Log thin slice** | ✅ **done — 351 tests** |
| **[3a](phases/phase-3a-food-library.md)** | **Food Library, Recipe Builder, the logging loop** | ✅ **built — 455 tests** (gates 1–5 reviewed live) |
| **[3b](phases/phase-3b-dashboard-and-body.md)** | **Dashboard, Body/Recovery, streaks, charts, rebalance** | ✅ **built — 810 tests** |
| **3b+** | **Cross-screen consistency pass** — headers, type, locale, tap targets, VoiceOver | ✅ **built — 870 tests** |
| 3c | Plan, Profile, Onboarding, Progress, Body history/edit, More, Appearance, Usage, Developer | ⬜ |
| 4 | HealthKit, scanner, notifications, background → **TestFlight** | ⬜ |
| 5 | AI coach, insight cards, briefing, SSE, tool executor | ⬜ |
| 6 | Store readiness → submit | ⬜ |

Detail: [parity-ledger.md](parity-ledger.md) for surface-level progress.

## Blocked / needs you

**1. 🔴 Export a real Android backup** — the last outstanding piece of Phase 1b. The 9 acceptance
tests are written and **self-skip** until it appears, so the suite is green but the claim "iOS reads
Android's backup" is *unproven*. Settings → Data Backup → Export, from a populated install, saved to
`~/Desktop/RecompTracker-IOS/RecompTracker/RecompTrackerTests/Fixtures/android-backup-v2.json`
(note the nested `RecompTracker/` — the test target lives inside the project folder). Buildable
folders pick it up with no project change; just re-run the suite.
It must contain: meals across **several slots** (P0-2 was exactly this), a `slotId = null`
coach-logged meal, a routine with sessions and sets, and a recipe — `theFixtureIsAdversarialEnough…`
fails loudly if it does not, rather than passing over a weak export.

**2. Apple Developer Program enrolment ($99/yr)** — the only item with a lead time. Needed *from day
one* of Phase 4 (HealthKit + background-delivery entitlements); nothing before then is blocked.

**3. Decide the bundle identifier**, needed at enrolment and permanent once reserved. Still the
Xcode default `Epistles-of-Wisdom.RecompTracker`; `com.zack.recomptracker` would match the Android
package and the `.rtroutine` UTI already declared.

**4. Give the iOS repo a git remote** — still local-only at ~72 commits.

**5. 🔴 Rule on `RebalanceCopy` vs the locale sweep (D28).** It is the one place `AppNumber` was
deliberately *not* applied. Its eight interpolated numbers ("Cut 250 kcal/day for 4 days", and the
rest) are asserted character-for-character against Android's `RebalanceCopyServiceTest`, so
localising them breaks that parity contract — and these are the most user-visible numbers in the
feature, the only ones that will not follow a German reader's locale. Two honest options: **(a)**
localise and re-baseline the Swift transcription of those assertions, accepting that the two
platforms' copy now differs by separator, or **(b)** keep the pin and record it as a deliberate
exemption. Nothing is blocked either way; it just should not drift by default.

## Standing rules

1. ~~Phase 0 is exclusive~~ — **lifted, Phase 0 has landed.** Every later phase only adds files
   under the iOS repo and is parallel-safe with Android work.
2. **One branch per phase**, merged to `develop` on completion. iOS code can't break the Android
   build, so don't hold it back.
3. **Read [decisions.md](decisions.md) before making a convention call.** If you make a new one,
   append it there in the same commit.
4. **Update this file and the parity ledger at the end of every session.** Non-negotiable — it is
   the only thing the next session is guaranteed to read.
5. **The user verifies UI visually.** Don't drive the simulator to "check" a screen; build it, say
   what needs looking at, and list it under *Needs visual check* below.
6. **Tests before implementation for domain logic.** The Android tests are the executable spec.

## Needs visual check

Phase 2's gates A–D and 3a's gates 1–5 were reviewed live against Android screenshots. 3b's
Dashboard, Body, check-in sheet and streak stats were built **from screenshots** (`10`–`16` in the
iOS repo's `screenshots/`) and mostly device-checked by the implementing agents.

**Never looked at by a human:**
- 🔴 **The running rebalance faces** — the progress sheet's Day-X-of-Y state, the ribbon's running
  state, and **`DayDots`** in its full 14pt variant. Unreachable without a plan on day ≥ 1, so no
  screenshot exists and none was captured. The whole P1-13 dot-index fix is invisible until then.
- 🔴 **The rebalance note card, all four skins** (gold completion, graceful end, no-adjustment, the
  defensive fallback). The gold one is the only user of the new tinted-glass card overload.
- **The calorie decision screen** — no screenshot, and no entry point until More lands in 3c.
- 3a's leftovers: the recipe portion sheet, the reconcile banner, slot selection mode.

Still unlooked-at and now covering far more screen:
- **Dynamic Type at the largest accessibility size.** `@ScaledMetric` returns its base value outside
  a hosted view, so no unit test can see the ramp. The consistency pass converted the fixed widths
  that would clip first (`ScoreBar`'s label and number columns), but only on the surfaces it audited.
- **Light mode.** Never judged deliberately. 3a and 3b added ~25 surfaces to it.
- **The eleven accent themes.** The 3b screenshots are on **Silver**, so near-white buttons and
  numbers in them are the theme, not the design.

**Changed by the consistency pass — worth a second look even where you saw the screen before:**
- 🔴 **The four tab roots' titles.** They now share one header (`ScreenHeader`), which changed
  size, weight and spacing on at least one of them. This is the most visible change in the pass.
- **Every number on Dashboard, Body, Food Log and the rebalance tiles** now formats through
  `AppNumber`. On a US device nothing should move; the change is only visible on a non-US locale.
- **Error and confirmation messages** across Body, Food Log and Food Library now render in one
  shared banner rather than five spellings.

## ~~Open question for 3c — number formatting~~ — **settled, see D28**
The owner ruled: **the app follows the device locale, everywhere.** `AppNumber` now owns all three
spellings and every `String(format: "%.1f", …)` is gone. **One exemption is still open** — see
*Blocked / needs you* item 5.

## Session log

Append 3–6 lines per session. Newest first. Archive below 20 entries.

### 2026-08-06 — Cross-screen consistency pass (4 audit agents, then 3 fix waves)
- **iOS 810 → 870 tests**, 0 warnings, Debug *and* Release green. **D28–D31** recorded. Seven new
  `DesignSystem/` components; every screen from Phases 2–3b touched.
- 🔴 **An audit finding is a hypothesis, not a fact.** Two of the four audits were confidently wrong:
  the 44pt `.contentShape` spelling was called broken (Wave 1 built a harness, measured taps, and
  found the *outside* spelling works and the system caps expansion at ~44pt), and the "biggest
  typography gap" was two different Android screens rendered correctly. **Verify before fixing.**
- 🔴 **Fixing the US pinning found the bug it had been hiding.** `RebalanceLever.trailing` takes a
  `locale` and dropped it on one branch — unobservable while `formatK` ignored locales, a wrong
  separator the moment it stopped. A locale-blind formatter hides every call site that forgets to
  thread one through.
- 🔴 **`JSONStore.set` was two `try?`s**, with the cache updated and observers notified *before*
  either — a preference that never reached disk repainted the UI as though it had and reverted on
  next launch. It now throws and rolls the cache back. Nothing could have failed a test before,
  because there was nothing to fail on.
- **Waves were fenced by directory** (Wave 1 `DesignSystem/` alone, Wave 2 three agents on disjoint
  feature dirs, Wave 3 persistence + rebalance). No collisions — but three items fell *between* the
  fences and needed a fourth pass to catch.

### 2026-08-05 — Phase 3b executed (3 research passes, then 8 implementation agents)
- **iOS 455 → 810 tests**, 0 warnings, Debug *and* Release green. **D24–D27** recorded. Dashboard,
  Body/Recovery, streaks, the chart kit and all five rebalance surfaces.
- 🔴 **The research paid for itself before a line was written: 41 of 41 domain symbols these screens
  need were already in `:shared` and exported.** Not one engine was reimplemented. Scope the shared
  module *first* on every remaining phase.
- 🔴 **A SwiftUI `DragGesture` cannot coexist with a `ScrollView`** — `.gesture`,
  `.highPriorityGesture` and `.simultaneousGesture` all break scrolling, and declining inside the
  handler does not hand the touch back. The sparkline's scrub needed a
  `UIGestureRecognizerRepresentable` with a velocity-gated delegate.
- 🔴 **The "simulator flake" that had cost a re-run all through 3a was a real crash.** `PaletteTests`
  resolves colours through `UIColor`, which is main-thread-only, and Swift Testing runs parameterised
  cases in parallel — a malloc double-free took the test host down, reported as "Restarting after
  unexpected exit" with **no test named**. One `@MainActor` fixed it. Never write off an unnamed
  crash as a flake.
- **Owed to 3c, still:** `plan_versions` has no iOS writer, so the ledger is empty and every target
  resolution takes its fallback. Harmless until 3c ships plan editing — wrong the moment it does.

### 2026-08-04 — Phase 3a executed (subagent-driven, 5 live gates then run to completion)
- **iOS 351 → 455 tests**, 0 warnings, Debug *and* Release green. **D20–D23**. Food Library and
  Recipe Builder complete; the logging loop closes end to end.
- 🔴 **Never put `didSet` on an `@Observable` stored property** — it compiles with no diagnostic and
  then crashes the test runner. Explicit get/set over private storage instead.
- 🔴 **The plan was wrong about Android four times**, each caught by an agent reading the Kotlin
  rather than trusting the brief. Verify against the source; a brief is not evidence. Detail in the
  [3a plan](phases/phase-3a-food-library.md).
- ~~Owed to 3c: `JSONStore` has no change stream~~ — added in 3b, and it now throws on a failed
  write (**D30**).

*Older entries archived — Phase 1a/1b/2 detail lives in their phase plans, and the conventions they
produced are in [decisions.md](decisions.md) and
[reference/shared-codec-api.md](reference/shared-codec-api.md). Three rules from Phase 2 outlive
their entry and are repeated here because nothing else states them:*
- 🔴 **No agent may run `git stash` in the iOS repo** — the stash is shared across worktrees, and
  two parallel agents popped each other's files.
- 🔴 **Screenshots beat reading the Kotlin** (D15). Phase 2 and 3b both found layout the source did
  not predict.
- `.task` *is* cancelled and restarted per tab switch (measured); `.task(id:)` is the working
  `flatMapLatest` equivalent.

---

## Map of the docs

| Doc | When to read |
|---|---|
| **STATUS.md** (this) | **every session, first** |
| [decisions.md](decisions.md) | before any convention call |
| [parity-ledger.md](parity-ledger.md) | when picking up work, or closing it out |
| [00-feasibility-and-roadmap.md](00-feasibility-and-roadmap.md) | once, or when questioning the plan |
| [reference/data-model.md](reference/data-model.md) | Phase 1, or any schema question |
| [reference/screen-inventory.md](reference/screen-inventory.md) | Phases 2–3, per screen |
| [reference/platform-api-map.md](reference/platform-api-map.md) | Phase 4, or any "what's the iOS equivalent" |
| [reference/domain-port-notes.md](reference/domain-port-notes.md) | Phase 0 and 5 |
| [reference/shared-codec-api.md](reference/shared-codec-api.md) | any time Swift calls into `:shared` |
| [reference/healthkit-notes.md](reference/healthkit-notes.md) | Phase 4 |
| [reference/architecture-evidence.md](reference/architecture-evidence.md) | only if revisiting the architecture |
| `phases/phase-N-*.md` | the phase you are executing |
