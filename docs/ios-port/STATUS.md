# iOS Port — STATUS

**Read this first, every session. Keep it under ~160 lines, and the session log to 3 entries.**
If you need more than this to start, the thing you need belongs in a phase plan or a reference doc.
(The cap was ~100 for Phases 0–1a and was quietly missed twice; 160 is what the file honestly needs
once *Blocked* and the phase board are worth reading. The discipline is the session log, which is
the only part that grows without bound — archive older entries into the docs that carry their detail.)

**Last updated:** 2026-08-02 · **Current phase:** Phase 1b — done bar the acceptance fixture ·
**Branches:** Android `ios/phase-1b-shared-codecs` (unmerged) · iOS `phase-1b-stores` (unmerged).
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

**The persistence foundation is complete.** Nothing above the database layer exists yet: no UI, no
`AppContainer` wiring beyond the database itself. Phase 2 starts there.

### Numbers

| | |
|---|---|
| iOS tests | **265** (256 running + 9 armed) · 0 isolation warnings · Debug + Release green |
| iOS persistence code | ~4,600 LOC across `Persistence/` |
| `:shared` commonMain | 71 files (66 + the 5 moved codecs) |
| `:shared` commonTest | **372 golden assertions** — run on JVM *and* iOS |
| Kotlin tests | `:app` 967 · `:shared` JVM 450 · `:shared` iOS 11 — **1417 total, unchanged** |
| Release iOS app | **16 MB** (was 14 MB after 1a; +1.2 MB of bundled JSON assets) |
| Kotlin/Native cold compile | **13.2 s** · warm incremental **0.8 s** · XCFramework 22.5 s |

## The decision in one paragraph

Native Swift/SwiftUI on an **iOS 26+** floor, sharing **only `domain/`** (≈5,550 LOC of pure
synchronous math) via a KMP `:shared` module. Everything else is rebuilt natively: **GRDB** for
persistence, `URLSession` for HTTP/SSE, Keychain for secrets, HealthKit, `UNUserNotificationCenter`.
v1 = core loop **+ AI coach**; Train module and NEVO/Samsung CSV import are **v1.1**.
Full reasoning: [00-feasibility-and-roadmap.md](00-feasibility-and-roadmap.md).

## Phase board

| Phase | What | Status |
|---|---|---|
| **0** | Extract `:shared`, `java.time` → kotlinx-datetime, golden-value tests. **Decision gate.** | ✅ **done — gate passed** |
| **1a** | GRDB + 19 tables + records + queries + transactions | ✅ **done — 71 tests** |
| **1b** | Preference stores, Keychain, bundled assets, file codecs | ✅ **done — 265 tests** (fixture pending) |
| 2 | App shell, design system, **Food Log end-to-end** | ⬜ |
| 3 | Dashboard, Body, Food Library, Plan, Profile, Onboarding, Streaks, charts | ⬜ |
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

**1b. Give the iOS repo a git remote** — still local-only, now ~35 commits. A private GitHub repo
would do.

**1c. Decide the bundle identifier.** It is still the Xcode template default,
`Epistles-of-Wisdom.RecompTracker`. Unlike Android's signing key a bundle ID is permanent once
reserved, so this wants settling before Phase 4, not during it.

**2. Apple Developer Program enrolment ($99/yr)** — not needed for the simulator work above, but
needed *from day one* of Phase 4: HealthKit and background-delivery entitlements require a paid
account, and enrolment has a lead time. **Reserve the bundle identifier** once enrolled — unlike
Android, the signing key does not matter but the bundle ID is permanent.

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

*(none outstanding — the Phase 0 smoke test was run and all rows were green)*

## Session log

Append 3–6 lines per session. Newest first. Archive below 20 entries.

### 2026-08-02 — Phase 1b executed (Part A inline + 5 parallel worktree agents)
- **iOS 71 → 265 tests**, 0 isolation warnings, Debug *and* Release green. Kotlin total unchanged at
  1417 (`:app` 967 · `:shared` JVM 450 · iOS 11) — 78 tests moved to `:shared` with their code.
- 🔴 **The generated header lies about type names.** It spells `SharedRebalanceSerialization`; with
  `import Shared` Swift sees `RebalanceSerialization`. Now pinned as code in `SharedInteropTests`,
  which is the executable form of [reference/shared-codec-api.md](reference/shared-codec-api.md).
- 🔴 **Synthesised `Decodable` would silently wipe preferences.** It throws on the first missing key;
  `JSONStore` turns that into "return the default", so *adding a field* resets every user. Two agents
  found it independently → **D14**: every persisted mirror decodes per-key.
- 🔴 **Kotlin `encodeDefaults = true` vs Swift's omit-nil.** Eight backup entities give nullables no
  Kotlin default, so a Swift-written backup would fail to decode on Android with
  `MissingFieldException`. Every payload type spells `encode(to:)` out by hand.
- **A Debug run is not enough.** Two isolation warnings appeared only in Release: `nonisolated` on a
  type does *not* propagate to its extensions.
- Buildable folders handle **resources** too — all four JSON assets reached `Bundle.main` with no
  pbxproj edit. The one project change needed was B7's `Info.plist`, which must sit *outside* the
  synchronized folder or it is claimed twice and the build fails.
- Agents corrected the plan four times: `coach_memory` has no `:shared` codec, `profilePhotoUri` is
  not in the backup payload at all, `JSONStore<String>` per store was unworkable (several stores have
  multiple keys), and P2-18's suggested fix was already what Android does.

### 2026-08-02 — Phase 1a executed (71 tests) · full detail in the [1a plan](phases/phase-1a-database.md)
Schema ground truth was Room's KSP-generated `RecompDatabase_Impl.kt`, not the entity annotations —
they disagree about DEFAULT clauses. `nullif(?, 0)` is spelled `Int64?` in Swift. `error: circular
reference` on every record came from MainActor default isolation meeting a same-module protocol
refining `MutablePersistableRecord`; one `nonisolated` fixed it. Xcode friction, not Swift, cost the
time. Parallel worktree agents worked: 5 agents, 4 merges, zero conflicts.

### 2026-08-01 — Phase 0 executed and gated · feasibility
Criteria and reasoning: **D10** in [decisions.md](decisions.md); rationale in
[00-feasibility-and-roadmap.md](00-feasibility-and-roadmap.md). 11 domain packages moved,
**372 golden assertions bit-exact on Kotlin/Native**, and a drift review caught a reachable
`formatFixed` bug the corpus had missed — *a golden corpus is only as good as its input set*.

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
