# iOS Port — STATUS

**Read this first, every session. Keep it under ~160 lines and the session log to 3 entries.**
Anything longer belongs in a phase plan or a reference doc. The session log is the only part that
grows without bound — archive older entries into the docs that carry their detail.

**Last updated:** 2026-08-05 · **Current phase:** Phase 3b — built, pending visual pass + merge ·
**Branches:** Android `develop` · iOS `phase-3b-dashboard-and-body` (unmerged, off the also-unmerged
`phase-3a-food-library`).
iOS work happens in `~/Desktop/RecompTracker-IOS/`.

---

## Where we are

✅ **Phase 0 complete, gate passed (D10).** The shared core is kept and merged to `develop`.

**Phase 1 was split into 1a and 1b (D13). Both are now built.**
✅ **[Phase 1a](phases/phase-1a-database.md)** — GRDB 7.11.1, the 19 tables, all 18 record types,
the query layer and the seven transaction bodies. Schema pinned against Room v15 byte for byte.
✅ **[Phase 1b](phases/phase-1b-stores-and-formats.md)** — all five persistence codecs moved into
`:shared` (D12), the ten preference stores, Keychain, bundled assets + the exercise seed, and all
three file formats. 🔴 **One thing outstanding: the acceptance test needs a real Android backup.**
Its 9 tests are written and **self-skip** until the fixture lands — see *Blocked / needs you*.

✅ **[Phase 2](phases/phase-2-shell-and-food-log.md) is built** — the design system on native
Liquid Glass, a four-tab shell, and Food Log's thin slice reading and writing the database.
**351 tests**, zero warnings, Debug and Release both green. Decisions **D15–D19** settled.

✅ **[Phase 3a](phases/phase-3a-food-library.md) is built** — Food Library, Recipe Builder, and the
three Food Log changes that make logging real. Decisions **D20–D23**.

✅ **[Phase 3b](phases/phase-3b-dashboard-and-body.md) is built** — Dashboard, Body/Recovery, the
streak pipeline, the chart kit, and all five rebalance surfaces. Decisions **D24–D27**. **810 tests**,
zero warnings, Debug and Release green. Home and Body are no longer placeholders: the app opens on a
real dashboard, and a weekly rebalance can be offered, customised, accepted and tracked.

### Numbers

| | |
|---|---|
| iOS tests | **810** (801 running + 9 armed) · 0 warnings · Debug + Release green |
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
| **1a** | GRDB + 19 tables + records + queries + transactions | ✅ **done — 71 tests** |
| **1b** | Preference stores, Keychain, bundled assets, file codecs | ✅ **done — 265 tests** (fixture pending) |
| **2** | App shell, design system, **Food Log thin slice** | ✅ **done — 351 tests** |
| **3a** | **Food Library, Recipe Builder, the logging loop** | ✅ **built — 455 tests** (gates 1–5 reviewed live) |
| **3b** | **Dashboard, Body/Recovery, streaks, charts, rebalance** | ✅ **built — 810 tests** |
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

**4. Give the iOS repo a git remote** — still local-only at ~55 commits.

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
  a hosted view, so no unit test can see the ramp.
- **Light mode.** Never judged deliberately. 3a and 3b added ~25 surfaces to it.
- **The eleven accent themes.** The 3b screenshots are on **Silver**, so near-white buttons and
  numbers in them are the theme, not the design.

## Open question for 3c — number formatting is inconsistent
Weights and trends format US-pinned via `String(format:)`; calories use
`.formatted(.number.grouping(.automatic))`, which follows the device locale — so on a Dutch device
the hero reads **`3.080 kcal`** where Android pins `Locale.US` and reads `3,080`. Both iOS calorie
heroes agree with each other, which is why it was left, but nobody has ruled on whether the app
speaks the device's locale or pins one format.

## Session log

Append 3–6 lines per session. Newest first. Archive below 20 entries.

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
- 🔴 **The "simulator flake" that had been costing a re-run all through 3a was a real crash.**
  `PaletteTests` resolves colours through `UITraitCollection`/`UIColor`, which is main-thread-only,
  and Swift Testing runs parameterised cases in parallel — a malloc double-free took the whole test
  host down, reporting as "Restarting after unexpected exit" with **no test named**. One `@MainActor`
  fixed it; 8 consecutive runs clean. 13 sleep-based waits were also converted to condition polls.
- **Owed to 3c, still:** `plan_versions` has no iOS writer, so the ledger is empty and every target
  resolution takes its fallback. Harmless until 3c ships plan editing — wrong the moment it does.

### 2026-08-04 — Phase 3a executed (subagent-driven, 5 live gates then run to completion)
- **iOS 351 → 455 tests**, 0 warnings, Debug *and* Release green. **D20–D23** recorded. Food Library
  and Recipe Builder complete; the logging loop closes end to end.
- 🔴 **Never put `didSet` on an `@Observable` stored property.** It compiles with no diagnostic and
  then *crashes the test runner* — `@Observable` rewrites the setter into `withMutation(keyPath:)`
  and the observer re-enters the registrar. Explicit get/set over a private stored property instead.
- 🔴 **The plan was wrong about Android four times, each caught by an agent reading the Kotlin
  rather than trusting the brief** — the servings toggle is never gated, quick add writes
  `"QUICK_ADD"` and recipes `"RECIPE"`, and logging a recipe writes **one entry per ingredient**.
  Verify against the source; a brief is not evidence. Detail in the [3a plan](phases/phase-3a-food-library.md).
- 🔴 **`DebugSampleData` had no call site for all of Phase 2**, so every screen was reviewed against
  an empty database. Wiring it exposed two more bugs behind a `try?`. Now covered by tests.
- **Owed to 3c: `JSONStore` still has no change stream.** Add one before shipping Plan or Settings,
  or every screen reading plan targets shows a stale value.

### 2026-08-03 — Phase 2 executed (4 worktree agents + 4 live visual gates)
- **iOS 265 → 351 tests**, 0 warnings, Debug *and* Release green. D15–D19 settled.
- 🔴 **A Phase 1a gap surfaced only when a screen finally rendered slots:** the migration never
  seeded Android's three default meal slots, so a fresh install had nowhere to log food — and
  `meal_entries.slotId` has no foreign key, so nothing failed loudly. Schema tests pinned table
  *structure*, never seeded *rows*.
- 🔴 **Screenshots beat reading the Kotlin.** The week strip is a bar chart, not dots; the calorie
  hero is 22pt Black, not 36pt; hitting the zone tints the *whole card* green. Layout should match
  Android even where the materials deliberately do not (D15).
- 🔴 **SwiftUI localises `Text("\(anInt)")`** — rendered "2.550" on a Dutch locale. Use
  `Text(verbatim:)` for every bare number.
- 🔴 **`git stash` is shared across worktrees** — two parallel agents popped each other's files.
  Nothing was lost, but **no agent may run it in this repo**.
- `.task` *is* cancelled and restarted per tab switch (measured). `.task(id:)` is the working
  `flatMapLatest` equivalent.

*Older entries archived — Phase 1a/1b detail lives in their phase plans, and the conventions they
produced are in [decisions.md](decisions.md) and
[reference/shared-codec-api.md](reference/shared-codec-api.md).*

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
