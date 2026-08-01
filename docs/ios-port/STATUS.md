# iOS Port — STATUS

**Read this first, every session. Keep it under ~100 lines.**
If you need more than this to start, the thing you need belongs in a phase plan or a reference doc.

**Last updated:** 2026-08-01 · **Current phase:** Phase 0 — 13 of 16 tasks done ·
**Branch:** `feat/ios-shared-core` (17 commits, unmerged)

---

## Where we are

**The domain extraction is complete and green on both platforms.** `:shared` holds 66 files /
5,905 LOC of `commonMain`, compiling for Android, `iosArm64` and `iosSimulatorArm64`. Exactly the
10 intended files remain in `:app`'s `domain/` (the backup DTO, `foodimport`, and the two
Room-typed food files).

**Next action is yours** — Task 14 needs ~15 minutes of Xcode GUI work that cannot be scripted.
See *Blocked / needs you* below. After that: Task 15 (docs) and Task 16 (the gate).

### Numbers

| | |
|---|---|
| `:shared` commonMain | 66 files, **5,905 LOC** (predicted ~5,550) |
| `:shared` commonTest | 2 files, 351 LOC — **runs on JVM *and* iOS** |
| `:shared` androidUnitTest | 44 files, 5,430 LOC (moved JUnit4, JVM-only) |
| Remaining in `:app` `domain/` | 10 files, 741 LOC — all deliberate |
| Tests | `:app` 1017 · `:shared` JVM 397 · `:shared` iOS 8 |
| Kotlin/Native cold compile | **13.2 s** · warm incremental **0.8 s** |
| XCFramework assembly | 22.5 s, 73 MB debug (both slices) |
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
| **0** | Extract `:shared`, `java.time` → kotlinx-datetime, golden-value tests. **Decision gate.** | 🔨 13/16 — [plan](phases/phase-0-shared-core.md) |
| 1 | GRDB + 19 tables + 14 migrations, prefs, Keychain, file codecs | ⬜ |
| 2 | App shell, design system, **Food Log end-to-end** | ⬜ |
| 3 | Dashboard, Body, Food Library, Plan, Profile, Onboarding, Streaks, charts | ⬜ |
| 4 | HealthKit, scanner, notifications, background → **TestFlight** | ⬜ |
| 5 | AI coach, insight cards, briefing, SSE, tool executor | ⬜ |
| 6 | Store readiness → submit | ⬜ |

Detail: [parity-ledger.md](parity-ledger.md) for surface-level progress.

## Blocked / needs you

**1. Press ⌘R.** Task 14 is otherwise done — the app **builds clean** in Debug and Release and links
10,618 Kotlin symbols. All that remains is running it once on an iOS 26 simulator and confirming
every row shows a green check. (Low risk: the same values already pass as 269 assertions on
`iosSimulatorArm64Test`, i.e. real Kotlin/Native. This confirms it also works in an app process.)
A red X would be gate-blocking — record the exact row here.

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

1. **Phase 0 is exclusive** — it restructures Gradle (`:app` → `:app` + `:shared`). No parallel
   Android branch work while it lands. Every later phase is additive and parallel-safe.
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

*(nothing yet)*

## Session log

Append 3–6 lines per session. Newest first. Archive below 20 entries.

### 2026-08-01 — Task 14: iOS repo + XCFramework workflow proven
- `~/Desktop/RecompTracker-IOS/` is a working, independent repo. App builds clean in Debug **and**
  Release against the synced XCFramework. **10,618 `kfun:` symbols** linked; every domain package present.
- 🟢 **Criterion 8 — resync loop is 13 s** (10.7 s `sync-shared.sh` + 2.6 s incremental Xcode build),
  **no manual clean needed.** Target was under 2 min.
- **Release app size: 9.5 MB** (simulator, arm64) for SwiftUI + the whole domain layer + the
  Kotlin/Native runtime. Debug is ~10 MB, split into a 58 KB stub plus `RecompTracker.debug.dylib` —
  that is Xcode 26's debug-dylib scheme, not bloat.
- 🟡 **Criterion 6 — Swift ergonomics, mixed.** Top-level functions land on `<File>Kt` classes
  (`DecimalFormatKt.signed1(value:)`) which reads fine. **Kotlin enums export as classes with static
  properties, not Swift enums** — `switch` over them is not exhaustive. That is the SKIE gap, and the
  first concrete argument for adopting it. Of the four symbol names I predicted from the header,
  three enum spellings were right and `GeneratedPlan.targets` was wrong (the type is flat).
- ✅ "Do Not Embed" verified correct — no `Frameworks/` directory in the built bundle.
- Xcode nested the project one level (`RecompTracker-IOS/RecompTracker/`), which is idiomatic;
  Framework Search Paths turned out to be **unnecessary** — adding the xcframework recorded a
  working path automatically.

### 2026-08-01 — Phase 0 execution, Tasks 1–13 (subagent-driven)
- **Extraction complete.** 11 domain packages + 3 value-type files moved to `commonMain` in
  dependency order, Android green at every step. Exactly the 10 intended files left behind.
- 🟢 **Gate criterion 3 passed — the big one.** All 270 golden assertions reproduce **bit-exactly on
  Kotlin/Native**. `Double.toString()` gives the same shortest decimal representation as the JVM, so
  no hand-rolled Ryū was needed. Coach `dedupKey`s (`2026-W27`) verified through the real call path.
- 🟢 **`PushEvent` JSON is byte-identical** to Java's `LocalDateTime.toString()` — no silent reset
  of the push-history rate limiter for existing users.
- 🟡 **Cost discovered: the Kotlin/Native klib ABI ceiling.** Staying on Kotlin 2.2.21 caps every
  `commonMain` dependency at releases built with Kotlin ≤2.2.x. kotlinx-serialization was pinned
  **1.11.0 → 1.9.0 project-wide** (56 files in `:app`). No post-1.9.0 API is used; all tests pass.
  Every future shared dependency needs the same check. Recorded as gate criterion **7a**.
- 🟡 **Boundary conversions: 112 across 26 files**, vastly over the ≤10 target I originally set.
  That target was miscalibrated — criterion **4a** recalibrates it. A full `:app` migration to
  kotlinx-datetime would remove them but is not viable: the UI needs locale-aware formatting, which
  kotlinx-datetime explicitly does not do. Some boundary conversion is inherent here.
- **Bugs fixed in passing:** P2-10 (strict `LocalDate.parse` crash-looping the dashboard on a
  corrupt stored plan) and a latent default-locale bug in `WeeklyReviewComputer`'s `"%,d"` grouping.
- **Three JVM-only API classes the verification grep cannot see** — `toSortedSet()`/`toSortedMap()`,
  `"%,d".format()`, and `LocalDate.MIN` (`internal` in kotlinx-datetime). All only surfaced at
  Kotlin/Native compile. Errata folded into the plan's mapping table.
- **Deviation worth knowing:** `CoachContextFixtures` is shared between `:app` and `:shared` test
  source sets via a `shared/src/testFixtures/kotlin` directory added to both, rather than
  duplicating 170 lines.
- Perf is a non-issue at this size: Kotlin/Native cold **13.2 s**, warm **0.8 s**.

### 2026-08-01 — Phase 0 planning session
- Wrote [phases/phase-0-shared-core.md](phases/phase-0-shared-core.md) — 16 tasks, 84 steps.
- **Found a blocker the feasibility pass missed:** 3 `:app` test files reference `internal`
  declarations that move to `:shared` (`PatternDetectorsTest`, `CrossSignalDiscoveryDetectorTest`,
  `ExperimentEvaluationTest`). `internal` is module-scoped, so tests must move with their code.
  Plan resolves this by putting all moved JUnit4 tests in `shared/src/androidUnitTest`.
- **Found a 4th inline `java.time` use** beyond the 3 documented (a descriptor string in
  `LocalDateTimeIsoSerializer.kt:18`; that file gets deleted anyway).
- `domain/share`'s `ExerciseLibraryJson` reference is **only a comment** — not a real dependency.
- `ExerciseLibraryJson.kt` is pure except the trailing `toEntity`; splitting it frees all of `workout`.
- ✅ Verified **no `BuildConfig` usage and no product flavors** — neither blocks the module split.
- **No Kotlin/AGP/Gradle upgrade is required** — sharing only `domain/` avoids Room-KMP entirely.

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
