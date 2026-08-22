# iOS Port — STATUS

**Read this first, every session. Keep it under ~160 lines and the session log to 3 entries.**
Anything longer belongs in a phase plan or a reference doc. The session log is the only part that
grows without bound — archive older entries into the docs that carry their detail.

**Last updated:** 2026-08-22 · **Current phase:** **Phase 4 is code-complete.**
[Plan](phases/phase-4-platform-integrations.md) · 4a, 4b and 4c are all built. Everything that
remains in the phase needs **a device or your Apple account**, not more code — see
*Blocked / needs you*. · **Branches:** Android `develop` · iOS
`phase-4c-coach-spine-and-notifications` (unmerged, off `main`, carries 4b's merge). **1123 tests.**
iOS work happens in `~/Desktop/RecompTracker-IOS/`.

---

## Where we are

🎉 **Phases 0 through 3 are built.** The database, the ten preference stores, all three file
formats, the design system on native Liquid Glass, a four-tab shell, and **sixteen screens**. A new
user is onboarded, given a plan, and can log food, weigh in, check trends and be offered a weekly
rebalance — the whole core loop, end to end.

🎉 **Phase 4 is built too.** HealthKit reads steps, weight and sleep and writes them into the log;
the barcode scanner and Open Food Facts work; Data & Backup closes the file-format loop; and the
**proactive coach spine runs** — eighteen detectors, a selector, a staged card and a real
notification, none of which needs a model. **Twenty-one screens.**

**What is left is not screens.** Phase 5 is the AI coach and Phase 6 is store readiness. Everything
either of them needs a *place* for already has one.

Three cross-cutting passes landed on top of the phases and are worth knowing about:
**D28–D32** (one number format, one title system), **D33/D37/D44** (the plan ledger, one store per
file, one coordinator for the app — all three were silent correctness bugs), and **D39** (a working
font picker where Android's is inert).

🔴 **One thing from Phase 1b is still outstanding** — the backup acceptance test needs a real
Android export. Its 9 tests are written and **self-skip** until the fixture lands. See
*Blocked / needs you* item 4.

### Numbers

| | |
|---|---|
| iOS tests | **1123** (1114 running + 9 armed) · 0 warnings · Debug + Release green |
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
| **[3d](phases/phase-3d-onboarding-progress-and-tools.md)** | **Onboarding, Progress/Trends, Usage Stats, Developer** — closes Phase 3 | ✅ **merged — 960 tests** |
| — | **Phase 3 complete on `main`** — sixteen screens, the whole core loop | ✅ **960 tests on `main`** |
| **[4b](phases/phase-4-platform-integrations.md)** | **Barcode scanner, Open Food Facts, Data & Backup** + the per-accent backdrop (**D52–D53**) | ✅ **built — 1008 tests** (unmerged) |
| **4a** | HealthKit, Integrations screen, 365-day import, background delivery | ⬜ **blocked on the HealthKit-capability check** |
| **4c** | The coach spine, notifications, `BGTaskScheduler` → **TestFlight** | ⬜ |
| 5 | AI coach, insight cards, briefing, SSE, tool executor | ⬜ |
| 6 | Store readiness → submit | ⬜ |

Detail: [parity-ledger.md](parity-ledger.md) for surface-level progress.

## Blocked / needs you

Phase 4 has no code left in it. Every item below needs a device, a file, or your Apple account.

**1. 🔴 Take the app to TestFlight — everything else is ready.** 🎉 **The enrolment has cleared**:
Xcode regenerated the provisioning profile with **HealthKit and background-delivery** on it, and it
expires in a year rather than the seven days a free team gets. The blocker that shaped the whole
phase plan is gone.

What is left is yours, in order:
- **Decide the bundle identifier.** Still the Xcode default `Epistles-of-Wisdom.RecompTracker`.
  `com.zack.recomptracker` matches the Android package and the `.rtroutine` UTI already declared.
  🔴 **Permanent once an App Store Connect record exists** — decide before, not after.
- **Create the App Store Connect record**, then `./scripts/archive.sh` and Organizer → Distribute →
  **TestFlight Internal Only** (≤100 testers, no review; external needs Beta App Review). The build
  number is `git rev-list --count HEAD`, applied by that script.
- **Replace the app icon.** One ships so the archive is valid, but it is derived from the Android
  launcher asset, which tops out at **288 px** of real content — it is readable and visibly soft.
  A 1024 export from whatever drew the original replaces one file.

⚠️ **A device build now needs your Apple account signed into Xcode**, because the target carries the
HealthKit entitlement. If it fails with *"provisioning profile doesn't include the HealthKit
capability"*, sign in under Xcode › Settings › Accounts and build again. The simulator is unaffected.

**2. 🔴 Steps with a paired Apple Watch (D46).** The one number in the port that no test can settle.
`HKStatisticsQuery(.cumulativeSum)` de-overlaps samples from the *same* source; iPhone + Watch are
different sources. Android's equivalent bug showed **17k steps on a 4k day**. Walk with both, and
compare the app's figure against the Health app's own for the same day.

**3. 🔴 Background delivery and the background digest, on a device.** Neither can be verified in a
simulator. The observer wakes on new step data (`.hourly`); the coach digest is a
`BGProcessingTaskRequest` the system runs when it likes. Both are written to fail quietly, which is
correct behaviour and also means *nothing tells you they are not working*. The Integrations screen's
last-synced line is the visible tell.

**4. 🔴 Export a real Android backup** — the last outstanding piece of Phase 1b, and now easier
because the restore path is reachable through the UI. The 9 acceptance tests are written and
**self-skip** until it appears, so the suite is green but the claim "iOS reads Android's backup" is
*unproven*. Settings → Data Backup → Export, from a populated install, saved to
`~/Desktop/RecompTracker-IOS/RecompTracker/RecompTrackerTests/Fixtures/android-backup-v2.json`
(note the nested `RecompTracker/` — the test target lives inside the project folder).
It must contain: meals across **several slots** (P0-2 was exactly this), a `slotId = null`
coach-logged meal, a routine with sessions and sets, and a recipe — `theFixtureIsAdversarialEnough…`
fails loudly if it does not, rather than passing over a weak export.

**5. 🔴 Give the iOS repo a git remote** — still local-only, now ~92 commits on top of `main`
including every phase to date. This is the whole port, on one machine, with no copy anywhere.
Cheapest outstanding item and the highest consequence if the disk goes. The CI workflow written in
this phase does nothing until it exists.

**6. 🔴 Rule on `RebalanceCopy` vs the locale sweep (D28).** It is the one place `AppNumber` was
deliberately *not* applied. Its eight interpolated numbers ("Cut 250 kcal/day for 4 days", and the
rest) are asserted character-for-character against Android's `RebalanceCopyServiceTest`, so
localising them breaks that parity contract — and these are the most user-visible numbers in the
feature, the only ones that will not follow a German reader's locale. Two honest options: **(a)**
localise and re-baseline the Swift transcription of those assertions, accepting that the two
platforms' copy now differs by separator, or **(b)** keep the pin and record it as a deliberate
exemption. Nothing is blocked either way; it just should not drift by default.

**7. Re-verify `getEarliestAuthorizedSampleDate(for:)` at iOS 27 GM (D48).** It was in developer beta
at capture, so the 365-day import deliberately does not call it and cannot yet say *"we could only
reach 90 days — grant full history in Settings"*. One method and one UI state when it is real.

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

**Nothing in this app has been judged by you since 3b.** Phase 2's gates A–D and 3a's gates 1–5 were
reviewed live against screenshots; 3b's Dashboard, Body, check-in sheet and streak stats were built
from screenshots `10`–`16` and device-checked by the implementing agents. **Everything after that
was built without your eyes on it, and thirteen surfaces were built with no screenshot at all.**

**Phase 4, newest first — three of these were built blind:**
- 🔴 **Integrations** (4a). `28-integrations-health-sync.jpg` was asked for and never produced, so
  the layout is the Kotlin plus my own design calls. Its behaviour is transcribed and tested; its
  look is a first draft. Four states: unsupported, off, **connected — waiting for data** (the D45
  state, whose *wording* matters most), and connected with a last-synced line.
- 🔴 **The food-import review sheet** (4a), also blind. Everything starts selected; the caption
  carries the duplicate and unnamed-day counts.
- **Notifications** (4c) — an iOS-only screen with no Android counterpart (**D57**). The
  denied-permission card only appears after you refuse the system prompt.
- **The push itself.** ⚠️ The spine needs **fourteen logged days** before any detector fires, so a
  populated install is the fastest route. Tap one from the lock screen: it should open the right
  tab, including on a **cold start** — the case Android's P0-3 crashed on.
- **The barcode scanner** (4b) — only its *unsupported* state is checkable in the simulator; the
  scan flow was driven end to end against the live Open Food Facts API via the DEBUG barcode field.
  **The Product Found sheet** and **Data & Backup** were both built blind.
- 🔴 **The backdrop is behind every screen** (**D53**) — worth looking at on all eleven accents and
  in both modes, since that is the whole point of it. ⚠️ The parallax needs a **device**: Core
  Motion reports nothing in a simulator, so it is static there.

**Built blind in 3c and 3d, and merged to `main` before the visual pass at your request** — so
anything wrong here is on `main`, not on a branch:
- 🔴 **Onboarding**, the most redesigned surface in the port: a question flow rather than Android's
  form. I walked all four steps on device; the *look* needs your judgement.
- **Trends** (grouped rather than Android's flat nine), **Usage Stats**, **Developer**, **More**,
  **Check-in History**, **Body Edit**, the **plan-generation sheets**, and the **calorie decision**
  screen. Treat their spacing and hierarchy as first drafts.
- **Plan**, **Appearance** and **Profile** were built against screenshots 17–19 and device-checked.
  Appearance carries a **working font picker** (**D39**) — try all three on a screen with real
  content, since only the settings screens were looked at closely. The photo picker is untested with
  a real image on device.
- ⚠️ **The generate-from-profile preview needs a complete profile to reach.** With an empty one it
  correctly reports the missing fields instead, so the preview sheet is unverified.

**Older, still open:**
- 🔴 **The rebalance note card, all four skins** (gold completion, graceful end, no-adjustment, the
  defensive fallback). The gold one is the only user of the tinted-glass card overload. The other
  five Developer scenarios also want your eyes — More → Developer, the rows marked "unseen".
- 3a's leftovers: the recipe portion sheet, the reconcile banner, slot selection mode.

**Three things no test can see, on every screen:**
- **Dynamic Type at the largest accessibility size.** `@ScaledMetric` returns its base value outside
  a hosted view, so no unit test sees the ramp. The consistency pass converted the fixed widths that
  would clip first, but only on the surfaces it audited.
- **Light mode.** Never judged deliberately, and it now covers ~30 surfaces.
- **The eleven accent themes.** The 3b screenshots are on **Silver**, so near-white buttons and
  numbers in them are the theme, not the design.

**Changed by the consistency pass — worth a second look even where you saw the screen before:**
- 🔴 **Every title in the app is the native one** (**D32**, **D54**), collapsing to a centred inline
  title as you scroll, with the bar taking its glass as content passes under. Light mode and ten of
  the eleven accents are unchecked.
- **Every number** on Dashboard, Body, Food Log and the rebalance tiles formats through `AppNumber`.
  On a US device nothing moves; the change is only visible on a non-US locale.
- **Error and confirmation messages** across Body, Food Log and Food Library render in one shared
  banner rather than five spellings.

## ~~Open question for 3c — number formatting~~ — **settled, see D28**
The owner ruled: **the app follows the device locale, everywhere.** `AppNumber` now owns all three
spellings and every `String(format: "%.1f", …)` is gone. **One exemption is still open** — see
*Blocked / needs you* item 6.

## Session log

Append 3–6 lines per session. Newest first. Archive below 20 entries.

### 2026-08-22 — **Phase 4 code-complete**: 4c then 4a, in one sitting
- **iOS 1043 → 1123 tests**, 0 warnings, Debug *and* Release green. **D45–D50**, **D56–D58**.
- 🎉 **The enrolment blocker was already gone and nobody had checked.** The phase plan called the
  HealthKit-capability question "unverified and worth checking on day one"; the profile on disk
  expired in *a year*, which a free team never gets. Adding the entitlement and building for a
  device regenerated it with HealthKit and background-delivery on it. **4a was never blocked.**
- 🔴 **Quiet hours defer on iOS rather than reject** (**D56**), and the reasoning is the platform,
  not taste: Android retries under WorkManager, iOS's background trigger runs *overnight while
  charging*, which is precisely the window a rejection would fall in. The `RateLimiter` is then
  asked at the **delivery** clock, so the caps count the day the user is actually interrupted.
- 🔴 **A test aborted the runner rather than failing.** A `CoachSignal` with a blank verdict trips a
  Kotlin `require`, and a Kotlin exception crossing back into Swift is a trap, not a catchable
  error. The gate it was testing turned out to be unreachable for a real signal — which is now what
  the file says instead of the test.
- **Sleep was rewritten, not ported** (**D47**). Android's one-liner has no counterpart:
  `sleepAnalysis` is a flat stream with no statistics query, so the night window, the stage filter
  and the cross-source union are ours. Two sources over one night is the same bug as the steps one
  that showed 17k on a 4k day, and the union is the same fix.
- **Android's P0-3 was made structural rather than flagged**: the tap only *offers* a destination,
  and `RootTabView` — which exists only once onboarding is known complete — is what takes it. Both
  halves of Android's gate collapse into one condition that cannot be forgotten.

### 2026-08-11 (later) — Phase 3d built and merged; **Phase 3 complete on `main`**
- **iOS 928 → 960 tests**, 0 warnings, Debug *and* Release green. **D40–D44**.
- 🔴 **`usage_events` had a record and no writer** — the same shape as the plan-ledger gap. The
  reflex was to move the screen to Phase 5 since most event types are AI; **checking rather than
  assuming** showed 7 of 14 are producible today, so it ships with real data.
- 🔴 **Ordering the Developer screen fourth rather than last paid off immediately** — it closed a
  gap STATUS had carried since 3b, and building it found a real bug: `RebalanceModel.live` built a
  coordinator per screen, so a scenario's note would never have reached the Dashboard (**D44**).
- **Onboarding was redesigned, not transcribed.** The device pass caught the sex picker drawing
  "Male" as chosen while the draft was still `nil` — a control claiming an answer nobody gave.

*Older entries archived — Phase 1a/1b/2/3b/3c detail lives in their phase plans, and the conventions they
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
