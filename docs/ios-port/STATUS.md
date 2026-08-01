# iOS Port — STATUS

**Read this first, every session. Keep it under ~100 lines.**
If you need more than this to start, the thing you need belongs in a phase plan or a reference doc.

**Last updated:** 2026-08-01 · **Current phase:** Phase 0 — planned, not started · **Branch:** none yet

---

## Where we are

Feasibility assessment complete, architecture decided, scaffolding written, **Phase 0 plan written**.
**No iOS code exists.**
Next action: create a branch and execute [phases/phase-0-shared-core.md](phases/phase-0-shared-core.md)
(16 tasks, 84 steps).

## The decision in one paragraph

Native Swift/SwiftUI on an **iOS 26+** floor, sharing **only `domain/`** (≈5,550 LOC of pure
synchronous math) via a KMP `:shared` module. Everything else is rebuilt natively: **GRDB** for
persistence, `URLSession` for HTTP/SSE, Keychain for secrets, HealthKit, `UNUserNotificationCenter`.
v1 = core loop **+ AI coach**; Train module and NEVO/Samsung CSV import are **v1.1**.
Full reasoning: [00-feasibility-and-roadmap.md](00-feasibility-and-roadmap.md).

## Phase board

| Phase | What | Status |
|---|---|---|
| **0** | Extract `:shared`, `java.time` → kotlinx-datetime, golden-value tests. **Decision gate.** | 📋 [planned](phases/phase-0-shared-core.md) |
| 1 | GRDB + 19 tables + 14 migrations, prefs, Keychain, file codecs | ⬜ |
| 2 | App shell, design system, **Food Log end-to-end** | ⬜ |
| 3 | Dashboard, Body, Food Library, Plan, Profile, Onboarding, Streaks, charts | ⬜ |
| 4 | HealthKit, scanner, notifications, background → **TestFlight** | ⬜ |
| 5 | AI coach, insight cards, briefing, SSE, tool executor | ⬜ |
| 6 | Store readiness → submit | ⬜ |

Detail: [parity-ledger.md](parity-ledger.md) for surface-level progress.

## Blocked / needs you

- **Apple Developer Program enrolment ($99/yr)** — needed *from day one*, not at submission:
  HealthKit and background-delivery entitlements require a paid account. There is a lead time.
- **Reserve the bundle identifier** once enrolled. It becomes the permanent invariant (unlike
  Android, the signing key does not matter; the bundle ID does).

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
