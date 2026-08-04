# iOS Port — STATUS

**Read this first, every session. Keep it under ~160 lines and the session log to 3 entries.**
Anything longer belongs in a phase plan or a reference doc. The session log is the only part that
grows without bound — archive older entries into the docs that carry their detail.

**Last updated:** 2026-08-04 · **Current phase:** Phase 3a — built, pending visual pass + merge ·
**Branches:** Android `develop` · iOS `phase-3a-food-library` (unmerged, off `main`).
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
three Food Log changes that make logging real. **455 tests**, zero warnings, Debug and Release both
green. Decisions **D20–D23** settled. You can now log from your own library in servings or grams,
create and correct foods, build recipes from picked ingredients, log a portion of one, confirm
planned meals, and turn a slot into a recipe.

### Numbers

| | |
|---|---|
| iOS tests | **455** (446 running + 9 armed) · 0 warnings · Debug + Release green |
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
| 3b | Dashboard, Body/Recovery, check-in, streaks, charts, rebalance surfaces | ⬜ |
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

Phase 2's gates A–D and Phase 3a's gates 1–5 (library list, amount sheet, food editor, quick add,
recipe builder) were reviewed live and corrected against Android screenshots. **Phase 3a's last
three surfaces were built after the user waived the remaining gates and have never been looked at:**

- **The recipe portion sheet** — no screenshot existed; built from `FoodLibraryScreen.kt:812-856`.
- **The reconcile banner** — no screenshot existed (none of the nine captured a day with planned
  entries); built from `FoodScreen.kt:458-496`. Its button-vs-text balance at an accessibility text
  size is the specific worry.
- **Slot selection mode** — the tick boxes, the footer bar, and the `⋮` overflow that enters it.
- **The two-mode ingredient editor** — both bodies were driven in the simulator by the implementing
  agent, but not judged by a human.

Still unlooked-at from Phase 2, and now covering far more screen:
- **Dynamic Type at the largest accessibility size.** Nothing proves the ramp — `@ScaledMetric`
  returns its base value outside a hosted view, so no unit test can see it.
- **Light mode.** Never judged deliberately. 3a added ~12 surfaces to it.
- **The eleven accent themes.** `AccentPreviews.swift` in the canvas; there is still no Settings
  screen to switch them (3c), and the app defaults to VIOLET.

## Session log

Append 3–6 lines per session. Newest first. Archive below 20 entries.

### 2026-08-04 — Phase 3a executed (subagent-driven, 5 live gates then run to completion)
- **iOS 351 → 455 tests**, 0 warnings, Debug *and* Release green. **D20–D23** recorded. Food Library
  and Recipe Builder complete; the logging loop closes end to end.
- 🔴 **`didSet` on an `@Observable` stored property compiles with no diagnostic and then crashes the
  test runner.** `@Observable` rewrites the setter into `withMutation(keyPath:)`, the observer fires
  *inside* it, and the handler re-enters the registrar. Use explicit get/set over a private stored
  property. Failure mode is a dead runner, not a compile error — worth remembering for every model.
- 🔴 **My own plan was wrong on four counts, each caught by an agent reading the Kotlin rather than
  trusting the brief:** Android always shows the Servings toggle (only the *scanner* gates it);
  quick add writes `mealType = "QUICK_ADD"` and recipe logging `"RECIPE"`, not `"MEAL"`/
  `"FOOD_LIBRARY"`; and **logging a recipe writes one entry per ingredient**, not one flattened row
  — the per-ingredient shape is what keeps each row's per-100g base, so it stays re-scalable and a
  restored Android backup renders identically. Screenshots corrected six more UI details.
- 🔴 **`DebugSampleData` had no call site for the whole of Phase 2** — every screen was reviewed
  against an empty database and nobody noticed. Wiring it exposed a second bug: its guard required
  zero `MealSlot`s, which the migration's three default slots made permanently false. Then my own
  fix spelled `Column("sortOrder")` where the column is `sort_order`, and the `try?` at the call
  site swallowed the throw — a silently empty library that looked like a broken screen. Now covered
  by `DebugSampleDataTests`, and the call site asserts rather than discarding.
- Deferred out of 3a and still owed: postpone, the stale-plan nudge, the ✨ recipe namer (Phase 5),
  Open Food Facts + the camera button (Phase 4). **`JSONStore` still has no change stream — 3c must
  add one before it ships Plan or Settings**, or every screen reading plan targets shows a stale value.

### 2026-08-03 — Phase 2 executed (4 worktree agents + 4 live visual gates)
- **iOS 265 → 351 tests**, 0 warnings, Debug *and* Release green. D15–D19 settled.
- 🔴 **A Phase 1a gap surfaced only when a screen finally rendered slots: our migration never seeded
  the three default meal slots Android's does.** A fresh install therefore had nowhere to log food,
  and because `meal_entries.slotId` has no foreign key nothing failed loudly. `SchemaEquivalenceTests`
  pinned table *structure*, never seeded *rows*. Fixed with four tests; five existing tests that had
  assumed a virgin table broke, which was the correct signal.
- 🔴 **Screenshots beat reading the Kotlin.** The week strip is a bar chart with a dashed target line
  and a zone band — I had built dots. The calorie hero is 22pt Black, not 36pt. And **hitting the
  zone tints the whole card green**, which I had reduced to a coloured word. Layout should match
  Android even though the *materials* deliberately do not (D15).
- 🔴 **SwiftUI localises `Text("\(anInt)")`** — the target label rendered "2.550" on a Dutch locale.
  Four call sites now use `Text(verbatim:)`.
- **`.task` IS cancelled and restarted per tab switch** (measured: 1 → 3 over two round-trips). No
  flicker; `.task(id:)` is the working `flatMapLatest` equivalent.
- 🔴 **`git stash` is shared across worktrees.** Two parallel agents stashed concurrently and each
  popped the other's files. Nothing was lost — both verified byte-identical recovery — but **no agent
  may run `git stash` in this repo**.
- Agents corrected the plan repeatedly: my `screenTitleCompact`/`statValue` mapping was inverted, the
  two display tokens silently ignored Dynamic Type, my colour-resolution test passed on the exact
  failure it existed to catch, and my `CalendarDay` accepted `"+026-08-02"`.

### 2026-08-02 — Phase 1b executed · detail in the [1b plan](phases/phase-1b-stores-and-formats.md)
iOS 71 → 265 tests; Kotlin unchanged at 1417. Three findings that became conventions: the generated
header spells types with a `Shared` prefix Swift does not use (pinned in `SharedInteropTests`);
synthesised `Decodable` would silently wipe preferences, hence **D14**; and Kotlin's
`encodeDefaults = true` vs Swift's omit-nil means a Swift-written backup needs a hand-written
`encode(to:)` or Android rejects it. Also: a Debug run does not prove zero isolation warnings.

*Older entries archived — Phase 1a's detail lives in the
[1a plan](phases/phase-1a-database.md), and the conventions it produced are in
[decisions.md](decisions.md) and [reference/shared-codec-api.md](reference/shared-codec-api.md).*

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
