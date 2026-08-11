# iOS Port — STATUS

**Read this first, every session. Keep it under ~160 lines and the session log to 3 entries.**
Anything longer belongs in a phase plan or a reference doc. The session log is the only part that
grows without bound — archive older entries into the docs that carry their detail.

**Last updated:** 2026-08-11 · **Current phase:** **3c merged. [3d is planned](phases/phase-3d-onboarding-progress-and-tools.md), 12 tasks, unstarted.** ·
**Branches:** Android `develop` · iOS `main` (928 tests). Nothing unmerged.
iOS work happens in `~/Desktop/RecompTracker-IOS/`.

---

## Where we are

**Phases 0 through 3b are built** — see the phase board below for the per-phase status and the
linked plan for each one's detail. In plain terms: the database, the ten preference stores, all
three file formats, the design system on native Liquid Glass, a four-tab shell, and five real
screens. The app opens on a working dashboard, food can be logged end to end, and a weekly rebalance
can be offered, customised, accepted and tracked.

On top of 3b, **a cross-screen consistency pass** (**D28–D32**): every screen from Phases 2–3b
audited together for title, type, spacing, motion and behaviour, and the drift fixed. Seven new
shared components; the app now has one number format and — after **D32** replaced the hand-rolled
header with the native large title — one title system across tab roots *and* pushed screens.

✅ **Phase 3c is built** — Plan (with the plan-version ledger it was blocked on), Profile including a
real photo picker, Check-in History, Body Edit, Appearance and the More hub. **D33–D38**. Every
dangling `nil` from 3a/3b is now wired: the Dashboard avatar opens More, the history row opens
Check-in History, and the calorie decision screen has an entry point at last.

🔴 **One thing from Phase 1b is still outstanding** — the backup acceptance test needs a real
Android export. Its 9 tests are written and **self-skip** until the fixture lands. See
*Blocked / needs you*.

### Numbers

| | |
|---|---|
| iOS tests | **928** (919 running + 9 armed) · 0 warnings · Debug + Release green |
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
| **3b+** | **Native title system** — every screen on `.navigationTitle` + iOS 26 subtitle (**D32**) | ✅ **built — 869 tests** |
| **3b+** | **Red→green score slider** replaces the check-in stepper | ✅ **built — 876 tests** |
| — | **3a + 3b merged to `main`**, tested and approved by the owner | ✅ **876 tests on `main`** |
| **[3c](phases/phase-3c-plan-profile-and-more.md)** | **Plan, Profile, Body history/edit, Appearance, More** — opened with the plan-ledger fix | ✅ **merged — 928 tests** |
| **[3d](phases/phase-3d-onboarding-progress-and-tools.md)** | **Onboarding, Progress/Trends, Usage Stats, Developer** — closes Phase 3 | 📋 **planned, 12 tasks** |
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

**4. 🔴 Give the iOS repo a git remote** — still local-only, now ~87 commits on `main` including
every phase to date. This is the whole port, on one machine, with no copy anywhere. Cheapest
outstanding item and the highest consequence if the disk goes.

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
  **[3d's Developer screen](phases/phase-3d-onboarding-progress-and-tools.md) is what makes these
  reachable** — six of its twelve scenarios put the app into exactly these states, which is why that
  task is ordered fourth rather than last.
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

**New in 3c, none of it looked at by you yet** — merged to `main` before the visual pass at your
request, so anything wrong here is on `main` rather than on a branch:
- **Plan** and **Appearance** were built against screenshots 17 and 19 and device-checked by me.
  Appearance now carries a **working font picker** (**D39**) — worth trying all three on a screen
  with real content, since only the settings screens were looked at closely.
- 🔴 **Four surfaces were built blind** — no screenshot exists: **More**, **Check-in History**,
  **Body Edit**, and the **plan-generation sheets** (preview + weight entry). Treat their spacing
  and hierarchy as a first draft.
- **Profile** matches screenshot 18 including the photo picker; the picker itself is untested with a
  real image on device.
- ⚠️ **The generate-from-profile flow needs a complete profile to reach its preview.** With an empty
  profile it correctly reports the missing fields instead, so the preview sheet is unverified.

**Changed by the consistency pass — worth a second look even where you saw the screen before:**
- 🔴 **Every title in the app is now the native large one** (**D32**) — it collapses into a centred
  inline title as you scroll, and the bar takes its glass as content passes under. Device-checked on
  all four tabs, but light mode and the other ten accents are not.
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

### 2026-08-11 — Phase 3c built (six screens, inline rather than by subagent)
- **iOS 876 → 921 tests**, 0 warnings, Debug *and* Release green. **D33–D38**.
- 🔴 **`JSONStore` was one instance per call site, not one per file** — found on the *first* live
  check of Appearance: tapping an accent wrote to disk and nothing moved. Every model resolves its
  store as `injected ?? (try? SomeStore())`, so two instances over one file meant two caches and two
  observer lists. Plan → Dashboard and Plan → Food Log were queued up behind the same silence, and
  the plan observation added earlier in the phase would never have fired. `JSONStore.shared(name:)`
  fixes it at the source (**D37**).
- 🔴 **The ledger fix went first, before any screen, and that ordering was the point.** `save()`
  appends a version only when the six day-judging fields moved — a threshold edit appends nothing —
  which is what keeps `plan_versions` a ledger rather than a changelog of settings taps.
- **Two bugs were caught by tests written after the code, not before**: `PlanModel` declared carried
  Health Connect fields and never seeded them, so every save switched the toggle off; and the photo
  downscale worked in points, so a 512 target wrote a 1536px file on a 3× device. Both were invisible
  on screen.
- **The screenshots changed two planned decisions.** The photo picker was going to be deferred and
  is now shipped (`PhotosPicker` sidesteps Android's persistable-URI bug instead of porting it);
  **D34** was re-confirmed against a screenshot showing the font row, then **reversed the same day**
  on the owner's call (**D39**): the picker is real, with three *system* font designs rather than two
  bundled TTFs. That needed two mechanisms — `.fontDesign` for SwiftUI and `ChromeTypeface` for the
  UIKit nav-bar title and tab-bar labels, which read nothing from the SwiftUI environment. A font
  setting that misses the largest text on every screen reads as a bug.

### 2026-08-06 (later) — Native titles, the score slider, the merge, and the 3c plan
- **D32**: the four tab roots moved off a hand-rolled header onto the native large title +
  `.navigationSubtitle`. They had had *three* different behaviours between them and a fourth on
  pushed screens; each was a faithful port of an Android inconsistency that is invisible in Compose
  because there is no platform title to clash with. Food Log's day steppers moved to the toolbar and
  came out better — visible *after* the title collapses, which the pinned header only managed by
  never collapsing.
- The check-in scores are a **red→green `ScoreSlider`**, and `ScoreBar` went green with them:
  its "best" tier used to be the accent, which is near-white on Silver and contradicted the rule
  `StatusColor` states twice — green is a verdict, the accent is a brand surface.
- 🔴 **`SparklineChart`'s private `ScrubPan` is now shared as `HorizontalPan`.** The slider sits in a
  `ScrollView` inside a `.sheet` — two competing pan recognizers — and needed exactly the same
  velocity-gated UIKit delegate. A second copy of a measured workaround is what goes stale silently.
- **3a and 3b merged to `main`** after the owner tested them: 876 tests, two merge commits keeping
  the phase boundaries.
- **[Phase 3c planned](phases/phase-3c-plan-profile-and-more.md)** — 12 tasks. The ten-screen 3c
  bucket was split; 3d takes Onboarding, Progress, Usage and Developer.

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

*Older entries archived — Phase 1a/1b/2/3b detail lives in their phase plans, and the conventions they
produced are in [decisions.md](decisions.md) and
[reference/shared-codec-api.md](reference/shared-codec-api.md). Three rules from Phase 2 outlive
their entry and are repeated here because nothing else states them:*
- 🔴 **Never put `didSet` on an `@Observable` stored property** (3a) — it compiles with no diagnostic
  and then crashes the test runner. Explicit get/set over private storage instead.
- 🔴 **A brief is not evidence** (3a) — the plan was wrong about Android four times, each caught by
  an agent who opened the Kotlin instead of trusting the task description.
- 🔴 **No agent may run `git stash` in the iOS repo** — the stash is shared across worktrees, and
  two parallel agents popped each other's files.
- 🔴 **Screenshots beat reading the Kotlin** (D15). Phase 2 and 3b both found layout the source did
  not predict.
- `.task` *is* cancelled and restarted per tab switch (measured); `.task(id:)` is the working
  `flatMapLatest` equivalent.
- 🔴 **Scope `:shared` before writing anything** (3b) — 41 of 41 domain symbols that phase needed
  were already exported, and not one engine had to be reimplemented.
- 🔴 **A SwiftUI `DragGesture` cannot coexist with a `ScrollView`** (3b). `HorizontalPan` is the
  measured answer; both the sparkline scrub and the score slider use it.
- 🔴 **An unnamed "Restarting after unexpected exit" is a real crash, not a flake** (3b) — it was
  `UIColor` resolution off the main thread in a parameterised test.

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
