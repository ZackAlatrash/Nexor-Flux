# iOS Port — STATUS

**Read this first, every session. Keep it under ~100 lines.**
If you need more than this to start, the thing you need belongs in a phase plan or a reference doc.

**Last updated:** 2026-08-02 · **Current phase:** Phase 1a — planned, not started ·
**Branch:** Phase 0 merged + pushed. iOS work happens in `~/Desktop/RecompTracker-IOS/`.

---

## Where we are

✅ **Phase 0 complete, gate passed (D10).** The shared core is kept and merged to `develop`.

**Phase 1 is now split into 1a and 1b (D13).**
[Phase 1a](phases/phase-1a-database.md) is **planned and ready to execute** — 15 tasks, 71 steps:
GRDB, the 19 tables, record types, the non-trivial queries and the seven transaction bodies. It
ends with a test asserting the schema matches Room's v15 byte for byte.
Phase 1b (preference stores, Keychain, bundled assets, file codecs) needs its own planning session.

**The domain extraction is complete and green on both platforms.** `:shared` holds 66 files /
5,905 LOC of `commonMain`, compiling for Android, `iosArm64` and `iosSimulatorArm64`. Exactly the
10 intended files remain in `:app`'s `domain/` (the backup DTO, `foodimport`, and the two
Room-typed food files).

### Numbers

| | |
|---|---|
| `:shared` commonMain | 66 files, **5,905 LOC** (predicted ~5,550) |
| `:shared` commonTest | **372 golden assertions** — run on JVM *and* iOS |
| `:shared` androidUnitTest | 44 files, 5,430 LOC (moved JUnit4, JVM-only) |
| Remaining in `:app` `domain/` | 10 files, 741 LOC — all deliberate |
| Tests | `:app` 1017 · `:shared` JVM 400 · `:shared` iOS 11 |
| Kotlin/Native cold compile | **13.2 s** · warm incremental **0.8 s** |
| XCFramework assembly | 22.5 s · **release iOS app 9.5 MB** |
| Boundary date conversions in `:app` | **112 expressions across 26 files** |

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
| **1a** | GRDB + 19 tables + records + queries + transactions | 📋 [planned](phases/phase-1a-database.md) |
| **1b** | Preference stores, Keychain, bundled assets, file codecs | ⬜ needs planning |
| 2 | App shell, design system, **Food Log end-to-end** | ⬜ |
| 3 | Dashboard, Body, Food Library, Plan, Profile, Onboarding, Streaks, charts | ⬜ |
| 4 | HealthKit, scanner, notifications, background → **TestFlight** | ⬜ |
| 5 | AI coach, insight cards, briefing, SSE, tool executor | ⬜ |
| 6 | Store readiness → submit | ⬜ |

Detail: [parity-ledger.md](parity-ledger.md) for surface-level progress.

## Blocked / needs you

**1. 🔴 Export a real Android backup** — Phase 1b's acceptance test is *"import a real Android
backup and assert every table and every slot link"*, and **no fixture exists anywhere in the repo**
(verified). A synthetic one I write would only prove my Swift matches my Swift. Export from
Settings → Data Backup on a populated install (meals across several slots, body entries, ideally a
routine — the training tables are what backup v2 added) and drop it in the iOS repo. Not needed for
1a; needed before 1b can be verified.

**2. Two small cleanups in Xcode when convenient** (both need the GUI, as they touch the pbxproj):
- Delete `RecompTracker/Item.swift` — leftover SwiftData template scaffolding, now unreferenced.
  Persistence is GRDB per D3.
- Deployment target is **26.5**; consider lowering to **26.0** to widen device coverage. D5 only
  requires 26+.

**3. Apple Developer Program enrolment ($99/yr)** — not needed for the simulator work above, but
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

### 2026-08-01 — Phase 0 executed and gated (subagent-driven, 21 commits)
Full criteria table and reasoning: **D10** in [decisions.md](decisions.md). Highlights:
- 11 domain packages + 3 value types moved in dependency order; Android green at every wave.
  `RebalanceEngine.size()` needed **no edits at all**.
- 🟢 **Bit-exactness holds on Kotlin/Native** — 372 golden assertions green on both platforms and in
  a running app. `PushEvent` JSON byte-identical, so no rate-limiter reset for existing users.
- 🔴 **The final behavioural-drift review caught a real bug** the corpus missed: `formatFixed` threw
  on scientific-notation doubles, reachable from flat-trend detectors via OLS residue, silently
  suppressing a day's coach signal. Fixed in `4f6d96d`. *A golden corpus is only as good as its
  input set* — carry that into Phase 1.
- 🟡 Two accepted costs: the **klib ABI ceiling** (serialization pinned 1.11.0 → 1.9.0 project-wide;
  every future `commonMain` dep needs the same check) and **112 boundary date conversions**.
- 🟡 Swift ergonomics: functions read well, but **Kotlin enums export as classes, not Swift enums**
  — no exhaustive `switch`. Evaluate SKIE in Phase 1.
- Bugs fixed in passing: **P2-10** (corrupt stored plan crash-looping the dashboard) and a latent
  default-locale bug in `WeeklyReviewComputer`.

### 2026-08-01 — feasibility
- 6 research agents → [00-feasibility-and-roadmap.md](00-feasibility-and-roadmap.md) + 6 reference
  docs. Decisions D1–D8. Scope and iOS 26+ target settled.
- **Surprises:** the July review's 3 P0s are already fixed in the tree (doc is stale); Vico is a
  dead dependency (declared, zero source refs — all charts are hand-rolled Canvas).

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
| [reference/healthkit-notes.md](reference/healthkit-notes.md) | Phase 4 |
| [reference/architecture-evidence.md](reference/architecture-evidence.md) | only if revisiting the architecture |
| `phases/phase-N-*.md` | the phase you are executing |
