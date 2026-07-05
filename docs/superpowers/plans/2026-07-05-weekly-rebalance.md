# Weekly Rebalance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Weekly Rebalance feature exactly as specified in
`docs/superpowers/specs/2026-07-05-weekly-rebalance-design.md` (the SPEC — read it first; it is the
source of truth for every formula, constant, and semantic rule referenced below).

**Architecture:** Pure deterministic engine (`domain/rebalance/`) + DataStore-JSON state
(`data/rebalance/`) + read-time `EffectiveTargets` overlay at the `PlanHistory.resolve` seam +
dashboard card (`ui/dashboard/`) + cloud copy phrasing with template fallback (`ai/`). No
`PlanPreferences`/`PlanVersion` writes ever. No Room changes. No on-device AI prompt-structure changes.

**Tech stack:** Kotlin, Coroutines/Flow, DataStore Preferences, kotlinx-serialization-style manual JSON
(follow `CoachExperimentSerialization`'s org.json usage — check and mirror it), Compose + app design
system, JUnit4 + kotlinx-coroutines-test + mockito-kotlin.

**Agent assignment (locked by user):** Opus 4.8 → Tasks 2, 5, 6. Sonnet 5 → Tasks 1, 3, 4, 7, 8, 9.
**Parallel groups (agents share one working tree — groups chosen so files never overlap):**
G1: Task 1 → G2: Tasks 2, 3, 4 in parallel → G3: Task 5 → G4: Task 6 → G5: Task 7 → G6: Task 8 → G7: Task 9.

**House rules for every task:** read SPEC §-references before coding; pure domain code has no Android
imports; `today: LocalDate` is a parameter, never `LocalDate.now()`; new UI uses `AppType` tokens +
glass components only (docs/design-system.md); backtick sentence test names; local fakes, no shared
test utils; commit after each task with the trailer
`Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: Models + defaults (Sonnet 5)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/rebalance/RebalanceModels.kt`

- [ ] **Step 1: Transcribe the data model from SPEC §4 verbatim** — `RebalanceMode`,
  `RebalanceStatus` (six values incl. `NO_ADJUSTMENT`), `RebalancePlan` (all 17 fields, exact names
  from the SPEC code block, incl. `recentAvgSteps: Int?`), `RebalanceState` (active/history/mode,
  `HISTORY_CAP = 12` in companion). Plain Kotlin data classes/enums — no serialization annotations
  (codec is hand-written in Task 3), no Android imports.

- [ ] **Step 2: Add `RebalanceDefaults` object in the same file** with exactly the SPEC §5 constants:

```kotlin
object RebalanceDefaults {
    const val MIN_LOGGED_DAYS_WINDOW = 4
    const val HIGH_DAY_ABS_KCAL = 400
    const val HIGH_DAY_PCT = 0.25
    const val WEEKEND_SURPLUS_KCAL = 600
    const val WEEKLY_IMPACT_MIN_KCAL = 50
    const val COOLDOWN_DAYS = 3
    const val RECOVERY_FRACTION = 0.75
    const val RECOVERY_FRACTION_RECOMP = 0.375
    const val RECOMP_MAX_LENGTH_DAYS = 3
    const val MAX_CAL_REDUCTION_PCT = 0.15
    const val MAX_CAL_REDUCTION_ABS = 300
    const val MIN_EFFECTIVE_CAL = 1200
    const val MAX_EXTRA_STEPS_PCT_OF_AVG = 0.25
    const val MAX_EXTRA_STEPS_ABS = 3000
    const val KCAL_PER_STEP = 0.04
    const val BALANCED_LEVER_FRACTION = 0.6
    const val UNRECOVERABLE_SLACK_KCAL = 75
    const val MIN_LENGTH_DAYS = 2
    const val MAX_LENGTH_DAYS = 5
}
```

- [ ] **Step 3: Type-check** — Run: `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `feat(rebalance): domain models and defaults`

---

### Task 2: Pure engine + resolver, test-first (Opus 4.8)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/rebalance/RebalanceEvaluationInput.kt`
- Create: `app/src/main/java/com/zack/recomptracker/domain/rebalance/RebalanceEngine.kt`
- Create: `app/src/main/java/com/zack/recomptracker/domain/rebalance/EffectiveTargets.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/rebalance/RebalanceEngineTest.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/rebalance/RebalanceReconcileTest.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/rebalance/EffectiveTargetsTest.kt`

Contracts (implement exactly — later tasks compile against these):

```kotlin
data class RebalanceEvaluationInput(
    val today: LocalDate,
    val baseTargetsByDate: Map<LocalDate, PlanTargets>, // trailing 7 days ending yesterday
    val eatenByDate: Map<LocalDate, Int>,               // eaten-only kcal (planned excluded upstream)
    val mealCountByDate: Map<LocalDate, Int>,
    val stepsByDate: Map<LocalDate, Int>,
    val baseStepGoal: Int?,
    val goal: FitnessGoal?,                             // import com.zack.recomptracker.data.preferences (check actual package)
    val mode: RebalanceMode,
    val existing: RebalanceState,
)

sealed class RebalanceDecision {
    data object Silent : RebalanceDecision()
    data class NoAdjustment(val plan: RebalancePlan) : RebalanceDecision()  // status NO_ADJUSTMENT, R=E=0
    data class Offer(val plan: RebalancePlan) : RebalanceDecision()          // status OFFERED
}

object RebalanceEngine {
    fun evaluate(input: RebalanceEvaluationInput, newId: () -> String, nowIso: () -> String): RebalanceDecision
    fun reconcile(state: RebalanceState, today: LocalDate,
                  baseTargetsByDate: Map<LocalDate, PlanTargets>, eatenByDate: Map<LocalDate, Int>): ReconcileResult
    fun customize(offer: RebalancePlan, newMode: RebalanceMode): RebalancePlan  // recompute R/E/D from stored facts (SPEC §7)
}
data class ReconcileResult(val state: RebalanceState, val ended: RebalancePlan? = null) // ended → show note

object EffectiveTargets {
    fun resolve(base: PlanTargets, date: LocalDate, state: RebalanceState): PlanTargets
    fun resolveAll(baseByDate: Map<LocalDate, PlanTargets>, state: RebalanceState): Map<LocalDate, PlanTargets>
    fun unionZone(base: PlanTargets, date: LocalDate, state: RebalanceState): IntRange // streaks (SPEC §6)
    fun effectiveStepGoal(baseGoal: Int?, date: LocalDate, state: RebalanceState): Int?
    fun planDayInfo(date: LocalDate, state: RebalanceState): PlanDayInfo? // data class PlanDayInfo(val dayX: Int, val ofY: Int, val plan: RebalancePlan)
}
```

`newId`/`nowIso` are injected (no `UUID.randomUUID()`/`Instant.now()` inside pure code; production
passes them at the coordinator). Formulas: implement SPEC §5.1–§5.8 exactly (gates → high-day/weekend →
S → impact → cooldown + **new-event rule** → derivation with caps/rounding → goal branches → macro
scaling → reconcile incl. TTL anchored on `createdAtIso`'s date). Resolver: SPEC §6 (historical windows
still resolve after revert; ENDED_EARLY only through its ended date — derive that date from
`decidedAtIso`... **no**: use the reconcile-time `today` captured by setting `endDateIso` to the ended
date when ending early, so the record is self-contained; document this in a KDoc line).

- [ ] **Step 1: Write ALL failing tests first** — every case named in SPEC §11 for
  `RebalanceEngineTest` (15 cases), `RebalanceReconcileTest` (4), `EffectiveTargetsTest` (7). Build
  small local helpers: `fun targets(cal: Int) = PlanTargets(cal, 160, 300, 70, cal-100, cal+100)`,
  `fun input(...)` with sensible defaults (today = a fixed Wednesday, e.g. `LocalDate.of(2026, 7, 8)`;
  weekend tests use a Monday). Assert numerically, e.g. the first test: base 2500, yesterday eaten
  3100 → S=600, HIGH (600≥400), impact 600/7≈86≥50 → Offer with `targetRecover = 450`,
  `perDayCap(BALANCED, no steps) = min(round10(375),300) = 300` → wait: no-steps BALANCED puts whole
  perDay into calories capped at 300 → D = smallest in 2..5 with D*300 ≥ 450 → D=2, perDay=ceil(450/2)=225
  → R=230 (rounded to 10, ≤300), E=0, recoveredKcal=460. Encode THIS arithmetic in the assertion.
- [ ] **Step 2: Run to verify failure** — `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.rebalance.*"`. Expected: compilation errors, then failures once stubs exist.
- [ ] **Step 3: Implement `RebalanceEngine` + `EffectiveTargets`** to make tests pass, formulas from SPEC §5/§6 only.
- [ ] **Step 4: Run to verify pass** — same command. Expected: all green.
- [ ] **Step 5: Commit** — `feat(rebalance): pure engine, reconcile and effective-targets resolver`

---

### Task 3: Persistence, test-first (Sonnet 5)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/rebalance/RebalanceSerialization.kt`
- Create: `app/src/main/java/com/zack/recomptracker/data/rebalance/RebalanceStore.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/rebalance/RebalanceSerializationTest.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/rebalance/RebalanceStoreTest.kt`

Clone the `CoachExperimentStore` + `CoachExperimentSerialization` pattern file-for-file (read both
first, and their tests). Contract:

```kotlin
interface RebalanceStore {
    val state: Flow<RebalanceState>
    suspend fun current(): RebalanceState
    suspend fun save(state: RebalanceState)
    suspend fun lastEvaluated(): LocalDate?
    suspend fun markEvaluated(date: LocalDate)
}
class DataStoreRebalanceStore(private val dataStore: DataStore<Preferences>) : RebalanceStore
internal object RebalanceSerialization {
    fun encode(state: RebalanceState): String
    fun decode(raw: String?): RebalanceState   // null/blank/malformed → RebalanceState()
}
```

DataStore instance: `private val Context.rebalanceDataStore by preferencesDataStore(name = "rebalance")`
exposed the same way `CoachExperimentStore`'s is (mirror its construction so AppContainer can wire it in
Task 5). Keys: `stringPreferencesKey("state")`, `stringPreferencesKey("last_evaluated")`.
History cap enforced in `encode` or `save` (keep newest 12 by `createdAtIso`).

- [ ] **Step 1: Write failing tests** — round-trip of a fully-populated state (active OFFERED plan with
  all optionals set + 2 history records + mode MOVE_MORE); decode(null)/""/"{not json"/`"{}"` → empty
  state; history cap: saving 13 terminal records keeps the 12 newest. Store test over
  `PreferenceDataStoreFactory` with a temp file exactly as `CoachExperimentStoreTest` does: save→current,
  flow emits on save, markEvaluated/lastEvaluated round-trip.
- [ ] **Step 2: Verify fail** — `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.rebalance.*"`.
- [ ] **Step 3: Implement** codec + store.
- [ ] **Step 4: Verify pass** — same command.
- [ ] **Step 5: Commit** — `feat(rebalance): DataStore-JSON state persistence`

---

### Task 4: Copy service, test-first (Sonnet 5)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/RebalanceCopyPromptBuilder.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ai/RebalanceCopyService.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/RebalanceCopyServiceTest.kt`

Clone `CoachPhrasingService` structurally (read it + its test first). Contract:

```kotlin
enum class RebalanceCopySlot { OFFER_HEADLINE, OFFER_BODY, PROGRESS_LINE, GRACEFUL_END, COMPLETION, NO_ADJUSTMENT }
data class RebalanceCopyFacts(
    val lengthDays: Int, val dailyCalorieReduction: Int, val extraDailySteps: Int,
    val effectiveCalories: Int, val dayX: Int, val ofY: Int,
)
object RebalanceCopyPromptBuilder {
    fun fallback(slot: RebalanceCopySlot, facts: RebalanceCopyFacts): String  // SPEC §8 templates VERBATIM incl. stepsClause rule
    fun systemPrompt(): String                                                // SPEC §8 wording
    fun userPrompt(slot: RebalanceCopySlot, facts: RebalanceCopyFacts): String // fallback text + facts, ask to rephrase
}
class RebalanceCopyService(private val client: OpenAiCompatClient, private val config: () -> CloudConfig?) {
    suspend fun copy(slot: RebalanceCopySlot, facts: RebalanceCopyFacts): String
}
```

Timeout 15_000 ms; streaming completion; sanitize like `CoachPhrasingService.sanitize` (reuse if
visible, else copy the private impl); the four-branch error shape (null-config, blank, Timeout/Exception
→ fallback, CancellationException rethrown). No tool schemas.

- [ ] **Step 1: Failing tests** — fake client by subclassing `OpenAiCompatClient` (it is `open`):
  success returns sanitized stream text; null config → fallback verbatim; blank stream → fallback;
  throwing client → fallback; `flow { throw CancellationException() }`-style cancellation propagates.
  Plus pure fallback-template tests: stepsClause included iff `extraDailySteps > 0`; MOVE_MORE (R==0)
  body leads with steps.
- [ ] **Step 2: Verify fail** → **Step 3: Implement** → **Step 4: Verify pass** —
  `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RebalanceCopy*"`.
- [ ] **Step 5: Commit** — `feat(rebalance): cloud copy service with deterministic fallbacks`

---

### Task 5: Coordinator + AppContainer wiring, test-first (Opus 4.8)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/rebalance/RebalanceCoordinator.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (construction + input assembly only — NOT the VM factory yet)
- Test: `app/src/test/java/com/zack/recomptracker/data/rebalance/RebalanceCoordinatorTest.kt`

Contract:

```kotlin
class RebalanceCoordinator(
    private val store: RebalanceStore,
    private val buildInput: suspend () -> RebalanceEvaluationInput, // assembled in AppContainer from one-shot reads
    private val planVersions: Flow<List<PlanVersion>>,              // planRepository.observeVersions()
    private val dateProvider: DateProvider,
    private val usageTracker: UsageTracker?,                        // match existing nullability convention — check UsageTracker call sites
    private val scope: CoroutineScope,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val nowIso: () -> String = { Instant.now().toString() },
) {
    fun start()                       // launches the version-observer cancel hook (drop(1))
    suspend fun runIfDue()            // Mutex; lastEvaluated==today short-circuit; reconcile → evaluate → persist; track events
    suspend fun accept()              // OFFERED → ACTIVE, decidedAt, startDate=today+1, endDate=start+len-1
    suspend fun decline()             // OFFERED → history DECLINED
    suspend fun dismissNote()         // NO_ADJUSTMENT → history
    suspend fun customize(mode: RebalanceMode) // engine.customize + sticky state.mode, only while OFFERED
}
```

`buildInput` in AppContainer (private suspend fun, next to `computeWeeklyReviewData` for symmetry):
window = `today.minusDays(7)..today.minusDays(1)`; `eatenByDate` = `logRepository.getWeekCalories(start, end)`;
`mealCountByDate` from meal entries in range (reuse the same underlying query used by getWeekCalories —
read LogRepository and pick the cheapest correct source; add a small repo method ONLY if nothing exposes
counts); `baseTargetsByDate` = `planRepository.targetsByDate(dates)`; `stepsByDate` from
`logRepository.observeDailyLogs().first()` filtered to window (existing house pattern);
`baseStepGoal`/`goal` from `userProfilePreferencesStore` one-shot; `mode`/`existing` from the store.
Construct store/coordinator/copy service in AppContainer next to the coach stores; call
`rebalanceCoordinator.start()` where other appScope observers start (mirror `CoachContextCache`'s
invalidation wiring location).

- [ ] **Step 1: Failing coordinator tests** (fakes: in-memory `RebalanceStore`, `MutableDateProvider`,
  `MutableSharedFlow<List<PlanVersion>>` for versions, stub buildInput): `runIfDue evaluates once per day`
  (second call same day does not re-evaluate — count buildInput invocations); `runIfDue reconciles before
  evaluating` (ACTIVE past end → COMPLETED and no immediate re-offer thanks to cooldown); `accept stamps
  decided and sets start tomorrow`; `decline moves to history and cooldown blocks next evaluation`;
  `a new plan version while active ends the plan as plan_edited`; `initial version emission does not cancel`
  (drop(1)); `concurrent runIfDue calls are serialized` (two launches, one evaluation).
- [ ] **Step 2: Verify fail** → **Step 3: Implement coordinator + AppContainer assembly** → **Step 4: Verify pass** —
  `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.rebalance.*"`; also `./gradlew :app:compileDebugKotlin`.
- [ ] **Step 5: Commit** — `feat(rebalance): coordinator, once-daily evaluation and AppContainer wiring`

---

### Task 6: Consumer seam — effective targets across the app (Opus 4.8)

**Files (modify):**
- `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt`
- `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt`
- `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressViewModel.kt`
- `app/src/main/java/com/zack/recomptracker/data/repository/StreakRepository.kt`
- `app/src/main/java/com/zack/recomptracker/data/coach/CoachContextAssembler.kt` + `CoachContextBuilder.kt` (+ `domain/coach/CoachContext.kt` for the rebalance block)
- `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt` (Plan: line value), `ai/CoachToolExecutor.kt` (get_weekly_trends targets)
- `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (pass `RebalanceStore`/state into the above constructors)
- Tests: extend the affected existing test files + `app/src/test/java/com/zack/recomptracker/data/repository/StreakRepositoryRebalanceTest.kt`

Apply SPEC §6's table EXACTLY. Mechanics per consumer: add `rebalanceStore.state` to the existing
`combine`s (or `.first()` in suspend builders — follow each file's existing style), then wrap the
already-resolved `Map<LocalDate, PlanTargets>` / `PlanTargets` with `EffectiveTargets.resolveAll/resolve`.
Streaks: calorie in-zone check switches to `EffectiveTargets.unionZone(...)`; steps success predicate
keeps `profile.dailyStepGoal` (add a code comment stating the invariant: temporary goals never judge
streaks). Weekly review (`computeWeeklyReviewData`), `AdjustmentEngine` inputs, and `PlanViewModel`
are NOT touched — leave base. On-device coach: only swap the VALUES already printed (no new prompt
lines beyond appending ` | Rebalance: day X of Y` to the existing Plan: line — keep format identical
otherwise); cloud coach context gets the `rebalance {active, dayX, ofY, effectiveCalories, extraSteps}`
block per SPEC §6.

- [ ] **Step 1: Write the two streak tests first** (SPEC §11): `union zone never breaks a kept calorie
  streak during rebalance` (day inside base zone, plan active → in-zone) and `steps streak uses base
  goal not boosted goal`. Follow `StreakRepository`'s existing test file's fake/builder style.
- [ ] **Step 2: Migrate consumers one file at a time**, running `./gradlew :app:compileDebugKotlin`
  after each file. Where a constructor grows a parameter, update its AppContainer construction in the
  same edit.
- [ ] **Step 3: Full unit suite** — `./gradlew :app:testDebugUnitTest`. Expected: green, incl.
  pre-existing dashboard/foodlog/coach tests (fix any compile breaks in existing tests by passing a
  default empty `RebalanceState` fake — an inert state must be behavior-neutral: assert no existing
  test's expected values change).
- [ ] **Step 4: Commit** — `feat(rebalance): effective targets across dashboard, food log, streaks, progress and coach`

---

### Task 7: Dashboard card + ViewModel (Sonnet 5)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceViewModel.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceCard.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt` (conditional item + `LaunchedEffect(Unit) { rebalanceViewModel.onShown() }` beside the coach one at ~line 111)
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (VM factory branch, mirror `CoachTodayViewModel`'s at lines ~637-643)
- Test: `app/src/test/java/com/zack/recomptracker/ui/dashboard/RebalanceViewModelTest.kt`

Read `CoachTodaySlot.kt` + `CoachTodayViewModel.kt` + their tests first and mirror them. UI state:

```kotlin
data class RebalanceCardUiState(
    val face: Face, val headline: String, val body: String,
    val dayX: Int = 0, val ofY: Int = 0, val progressFraction: Float = 0f,
    val effectiveCalories: Int = 0, val extraSteps: Int = 0, val mode: RebalanceMode = RebalanceMode.BALANCED,
) { enum class Face { NONE, OFFER, PROGRESS, NOTE } }
```

VM: collects `store.state` + `dateProvider`; derives face (OFFERED→OFFER, ACTIVE & today in window→PROGRESS,
NO_ADJUSTMENT→NOTE, else NONE); seeds fallback copy synchronously then swaps phrased copy via
`RebalanceCopyService` (the `CoachTodayViewModel.onSignal` job pattern); `onShown()` → coordinator
`runIfDue()` fire-and-forget; `onAccept/onDecline/onDismissNote/onCustomize(mode)` delegate to the
coordinator. Card composable rules (docs/design-system.md is binding): `TintedCard` frame,
`SectionLabel("Weekly Rebalance")`, headline `AppType.cardTitle`, body `AppType.body`, buttons
`LiquidActionButton("Start Weekly Rebalance", isPrimary = true)` + `LiquidActionButton("Keep My Normal Plan",
isPrimary = false)`; a small text-button-style Customize affordance opens `GlassBottomSheet` containing
`GlassSegmentedToggle(listOf("Eat less", "Balanced", "Move more"), ...)` + the recomputed plan line +
a `LiquidPrimaryButton("Done")`; PROGRESS face shows "Day {X} of {Y}" (`AppType.statValueSmall`), a fill
bar reusing the `MacroBarItem` animated-fill pattern (`DashboardScreen.kt:553-569`) with
`progressFraction = dayX / ofY`, and today's effective target; NOTE face is body + a 32dp dismiss icon
button (the `CoachTodaySlot` DismissButton pattern). No hardcoded fontSize/colors — tokens only.
Dashboard slot: insert the conditional `item {}` immediately AFTER the `CoachTodaySlot` item
(`DashboardScreen.kt` ~line 240-251), guarded by `face != NONE`.

- [ ] **Step 1: Failing VM tests** — offer state renders OFFER face with fallback copy; accept flips to
  PROGRESS with correct dayX/ofY; decline → NONE; phrased copy swaps in when the fake phrase lambda
  returns, fallback retained on failure; `onShown` invokes coordinator once (count on a fake).
- [ ] **Step 2: Verify fail** → **Step 3: Implement VM, card, screen slot, factory branch** → **Step 4:**
  `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.dashboard.*"` green AND
  `./gradlew :app:compileDebugKotlin` clean.
- [ ] **Step 5: Commit** — `feat(rebalance): dashboard offer/progress card and view model`

---

### Task 8: Backup + analytics (Sonnet 5)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/domain/export/BackupModels.kt` (+ its serializer — find where BackupPayload is encoded/decoded and extend it)
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/BackupRepository.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/usage/UsageTracker.kt` or the `UsageEvents` constants holder (locate it; add `REBALANCE_OFFERED/ACCEPTED/DECLINED/COMPLETED/ENDED_EARLY`)
- Modify: `app/src/main/java/com/zack/recomptracker/data/rebalance/RebalanceCoordinator.kt` (fire events at each transition — they were stubbed nullable in Task 5)
- Test: extend the existing BackupRepository/serialization tests + coordinator test for events

Rules: `rebalanceState` is a NULLABLE addition to `BackupPayload` with default null (old backups must
decode unchanged — add the regression test); export reads `rebalanceStore.current()`, restore writes
`payload.rebalanceState ?: RebalanceState()`; version bump only if BackupPayload has an explicit version
field (check — follow whatever the planned-meals feature did when it extended backup).

- [ ] **Step 1: Failing tests** — backup round-trip includes an active plan; a legacy payload without
  the field restores to empty state; coordinator fires `REBALANCE_ACCEPTED` on accept (fake tracker).
- [ ] **Step 2-4: Implement, verify** — `./gradlew :app:testDebugUnitTest`. Green.
- [ ] **Step 5: Commit** — `feat(rebalance): backup round-trip and usage analytics`

---

### Task 9: Full verification (Sonnet 5 verification + Fable review)

- [ ] **Step 1:** `./gradlew :app:testDebugUnitTest` — full suite green (paste tail of output).
- [ ] **Step 2:** `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- [ ] **Step 3:** Grep audit (each must return NOTHING):
  `grep -rn "LocalDate.now()" app/src/main/java/com/zack/recomptracker/domain/rebalance/` ·
  `grep -rn "planRepository.save" app/src/main/java/com/zack/recomptracker/data/rebalance/ app/src/main/java/com/zack/recomptracker/domain/rebalance/` ·
  `grep -rn "fontSize\s*=" app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceCard.kt` ·
  `grep -rn "import android" app/src/main/java/com/zack/recomptracker/domain/rebalance/`
- [ ] **Step 4:** Orchestrator (Fable) reviews the full branch diff against SPEC §5/§6 semantics before sign-off.

---

## Self-review notes (done at plan time)

- Spec coverage: §4→T1/T3, §5→T2, §6→T2/T6, §7→T5/T7, §8→T4, §9→T3/T8, §10 cases land in T2/T5 tests, §11→spread verbatim, §12 order preserved. Follow-ups §13 intentionally unplanned.
- Type consistency: `RebalanceDecision.NoAdjustment(plan)` carries a plan (status NO_ADJUSTMENT) so the coordinator can place it in `state.active` per SPEC §5.6 display rule; `ReconcileResult.ended` feeds the GRACEFUL_END note; `customize` lives on the engine (pure) and the coordinator (persisting) — VM calls the coordinator only.
- Known judgment calls delegated to implementers WITH guardrails: meal-count source (T5), UsageEvents holder location (T8), backup version field (T8) — each says "read the existing code and mirror it", never "TBD".
