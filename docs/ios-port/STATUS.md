# iOS Port — STATUS

**Read this first, every session. Keep it under ~160 lines and the session log to 3 entries.**
Anything longer belongs in a phase plan or a reference doc. The session log is the only part that
grows without bound — archive older entries into the docs that carry their detail.

**Last updated:** 2026-08-03 · **Current phase:** Phase 2 — built, pending merge ·
**Branches:** Android `develop` (1b merged) · iOS `phase-2-shell-and-food-log` (unmerged).
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
The app launches, you can log a meal, and the totals recompute from the observation.

### Numbers

| | |
|---|---|
| iOS tests | **351** (342 running + 9 armed) · 0 warnings · Debug + Release green |
| iOS code | ~4,600 LOC `Persistence/` · ~460 `DesignSystem/` · ~250 `Shell/` · ~900 `Features/FoodLog/` |
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
| 3 | Food Library, Dashboard, Body, Plan, Profile, Onboarding, Streaks, charts | ⬜ |
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

Gates A–D were reviewed live in the simulator during the Phase 2 session and corrected against
Android screenshots. Still unlooked-at:
- **Dynamic Type at the largest accessibility size.** Nothing proves the ramp — `@ScaledMetric`
  returns its base value outside a hosted view, so no unit test can see it.
- **Light mode.** The simulator ran light during the session and the app followed, but no one has
  judged it deliberately.
- **The eleven accent themes.** `AccentPreviews.swift` in the canvas; there is no Settings screen
  to switch them until Phase 3, and the app defaults to VIOLET.

## Session log

Append 3–6 lines per session. Newest first. Archive below 20 entries.

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

### 2026-08-02 — Phase 1a executed (71 tests) · full detail in the [1a plan](phases/phase-1a-database.md)
Schema ground truth was Room's KSP-generated `RecompDatabase_Impl.kt`, not the entity annotations —
they disagree about DEFAULT clauses. `nullif(?, 0)` is spelled `Int64?` in Swift. `error: circular
reference` on every record came from MainActor default isolation meeting a same-module protocol
refining `MutablePersistableRecord`; one `nonisolated` fixed it. Xcode friction, not Swift, cost the
time. Parallel worktree agents worked: 5 agents, 4 merges, zero conflicts.

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
