# Weekly Rebalance UI Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Reimplement the Weekly Rebalance presentation exactly as agreed in the prototype and
`docs/superpowers/specs/2026-07-06-weekly-rebalance-ui-redesign.md` (the SPEC — read it first): offer as a
floating AI-card Dialog + reopen pill, progress merged into the Today card + tap-to-float detail dialog,
gold/green ending cards, weekly-bars + day-dots + convergence viz, everything animated. **Engine,
persistence, coordinator transitions, effective-target overlay, and coach integration are unchanged** —
additive VM/coordinator surface only.

**Architecture:** Reuse the app's AI-card + `aiEdgeGlow` stack via a shared `AiDialogCard` (extracted from
Weekly Review's `BriefingGlassCard`). Dialogs are separate windows → no `LocalBackdrop` components inside
(no `AiInsightCard`/`FrostedCard`/`Liquid*Button`); use `AiDialogCard` + Dialog-safe hand-rolled buttons.
All animations use `ChartDefaults.AnimSpec` + `AnimatedVisibility` idioms, gated by a shared
`rememberAnimationsEnabled()` (reduce-motion).

**Tech stack:** Kotlin, Jetpack Compose, `androidx.compose.ui.window.Dialog`, `AnimatedVisibility`,
`animateFloatAsState`/`Animatable`, `ChartDefaults.AnimSpec`, JUnit4 + kotlinx-coroutines-test.

**Agent/model assignment (locked by user policy):** Opus 4.8 → Tasks 2, 7. Sonnet 5 → Tasks 1, 3, 4, 5, 6, 8.
**Order (sequential, per-task 2-stage review):** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8.

**House rules (every task):** read the SPEC + the file:line references before coding; **no `LocalBackdrop`
components inside a `Dialog`**; new UI uses `AppType` tokens + `LocalAppColors`/`LocalAppAccent` only (never
raw `fontSize`/hex); every animation reads `rememberAnimationsEnabled()` and degrades to fade/snap when
false; backtick sentence test names; commit after each task with trailer
`Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`; verify branch is `feat/weekly-rebalance`, never switch.

---

### Task 1: Shared dialog card, dismiss button, animation gate (Sonnet 5)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/AiDialogCard.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/DismissButton.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/AnimationGate.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/review/WeeklyBriefingOverlay.kt`

**Read first:** `WeeklyBriefingOverlay.kt` fully (esp. `BriefingGlassCard` ~lines 109-140, `BriefingPrimaryButton`
~401-415, `BriefingGhostButton`, `ConfirmChip`, `ModalCorner = 24.dp`), `ui/component/AiEdgeGlow.kt` (the
`Settings.Global.ANIMATOR_DURATION_SCALE` gate lines 40-46), `ui/dashboard/CoachTodaySlot.kt:242-262`
(DismissButton).

- [ ] **Step 1: `rememberAnimationsEnabled()`.** Create `AnimationGate.kt`:

```kotlin
package com.zack.recomptracker.ui.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the OS "Animator duration scale" is non-zero (i.e. the user has NOT enabled
 * "Remove animations"). Mirrors the check duplicated in [aiEdgeGlow] and PrBanner; new animations should
 * degrade to a plain fade / instant snap when this is false. Read once per composition (not reactive).
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
```

- [ ] **Step 2: Shared `DismissButton`.** Create `DismissButton.kt` by moving the verbatim 32dp circular
  close from `CoachTodaySlot.kt:242-262` (Box 32dp, clip CircleShape, clickable role Button + semantics
  contentDescription, `Icon(Icons.Rounded.Close, size 18dp, tint = appColors.textVeryMuted)`). Signature:
  `@Composable fun DismissButton(onDismiss: () -> Unit, contentDescription: String, modifier: Modifier = Modifier)`.

- [ ] **Step 3: Extract `AiDialogCard`.** Create `AiDialogCard.kt` with the `BriefingGlassCard` recipe (read
  it verbatim from `WeeklyBriefingOverlay.kt`), parameterized:

```kotlin
@Composable
fun AiDialogCard(
    borderMode: AiBorderMode,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,        // house Dialog corner (ModalCorner / DialogCorner)
    scrollable: Boolean = true,
    maxHeight: Dp = 640.dp,
    content: @Composable ColumnScope.() -> Unit,
)
```
Body = exact `BriefingGlassCard` modifiers in order: `clip(RoundedCornerShape(cornerRadius))` → translucent
solid `background(surface)` (`Color(0xFF101014).copy(0.92f)` dark / `Color(0xFFF6F6F8).copy(0.95f)` light —
read `LocalAppColors.current.isDark`) → top-sheen `drawBehind` (white `0f→0.12f` verticalGradient, `0.14`
stop) → `border(0.5.dp, Color.White.copy(if isDark 0.16f else 0.24f), shape)` → `aiEdgeGlow(borderMode,
cornerRadius)` → `if (scrollable) heightIn(max = maxHeight).verticalScroll(rememberScrollState())` →
`padding(20.dp)`. **No `drawBackdrop`, no `LocalBackdrop`** (Dialog-safe).

- [ ] **Step 4: Refactor `WeeklyBriefingOverlay.BriefingGlassCard` to delegate** to `AiDialogCard`
  (`AiDialogCard(borderMode, modifier, cornerRadius = ModalCorner) { content() }`) so Weekly Review and the
  new rebalance dialogs share one implementation. Behavior must stay identical.

- [ ] **Step 5: Promote Dialog-safe buttons.** Change `BriefingPrimaryButton`, `BriefingGhostButton`,
  `ConfirmChip` in `WeeklyBriefingOverlay.kt` from `private` to `internal` (so `ui/dashboard` overlays can
  reuse them). If any helper they call is `private`, promote as needed. Do not change their look.

- [ ] **Step 6: Compile.** Run `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 7: Sanity grep.** `grep -n "BriefingGlassCard\|AiDialogCard" app/src/main/java/com/zack/recomptracker/ui/review/WeeklyBriefingOverlay.kt` — confirm `BriefingGlassCard` now delegates. Confirm no behavior change to Weekly Review by reading the diff.
- [ ] **Step 8: Commit** — `refactor(ui): shared AiDialogCard, DismissButton, animations gate`

---

### Task 2: VM + coordinator surface (additive, test-first) (Opus 4.8)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/rebalance/RebalanceCoordinator.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/dashboard/RebalanceViewModelTest.kt` (extend)
- Test: `app/src/test/java/com/zack/recomptracker/data/rebalance/RebalanceCoordinatorTest.kt` (extend)

**Read first:** `RebalanceViewModel.kt` fully (state/deriveFace/offerFace/progressFace/noteFace/onStateChanged),
`RebalanceCoordinator.kt` (`runOnce`/`buildInput` call site ~line 113, `endedNotice` pattern), the SPEC §8.

Contracts to add:

```kotlin
// RebalanceViewModel.kt (or a small models file in ui/dashboard)
enum class NoteKind { COMPLETION, GRACEFUL_END, NO_ADJUSTMENT }

data class RebalanceDayBar(
    val label: String,        // "Mon".."Sun"
    val eatenKcal: Int,
    val baseTargetKcal: Int,
    val isPlanDay: Boolean,   // false for the trailing-7 history bars; true for the plan days ahead
    val isOver: Boolean,      // eatenKcal > baseTargetKcal + a margin (engine's "high" sense)
)

// RebalanceCardUiState gains:
//   val noteKind: NoteKind? = null,
//   val weeklyBars: ImmutableList<RebalanceDayBar> = persistentListOf(),
```

- [ ] **Step 1: Failing coordinator test** — `` `runOnce publishes lastOfferWindow when an offer is produced` `` and `` `lastOfferWindow is null when the decision is silent` `` in `RebalanceCoordinatorTest.kt`. Build an input whose trailing-7 produces an offer (reuse the existing test's offer fixture); assert `coordinator.lastOfferWindow.value` is a non-empty list of `RebalanceDayBar` whose `eatenKcal`/`baseTargetKcal` match the input maps and whose `isPlanDay` count == the offered plan's `lengthDays`; assert null for a silent input.

- [ ] **Step 2: Implement `lastOfferWindow` on the coordinator.** Add `private val _lastOfferWindow =
  MutableStateFlow<ImmutableList<RebalanceDayBar>?>(null)`; `val lastOfferWindow: StateFlow<...> =
  _lastOfferWindow.asStateFlow()`. In `runOnce()`, right after `buildInput()` is called and the decision is
  evaluated: if the decision is an `Offer`, build the bar list from `input.baseTargetsByDate` +
  `input.eatenByDate` (trailing 7 days ending yesterday, chronological, `label` = day-of-week short name via
  the injected date) + the plan's `lengthDays` days ahead (`isPlanDay = true`, `baseTargetKcal = base`,
  `eatenKcal` = the effective target for display), and set `_lastOfferWindow.value`. Else set null.
  **No new repository reads, no transition-logic change.** Keep it purely derived from data already fetched.
  (If `RebalanceDayBar` lives in `ui/dashboard`, move it to `domain/rebalance` or `data/rebalance` so the
  coordinator can reference it without a UI dependency — put it in `domain/rebalance/RebalanceModels.kt`.)

- [ ] **Step 3: Run coordinator tests** — `./gradlew :app:testDebugUnitTest --tests "*RebalanceCoordinatorTest"`. Green (existing 17 + 2 new).

- [ ] **Step 4: Failing VM tests** in `RebalanceViewModelTest.kt`:
  - `` `completion note sets noteKind COMPLETION` `` / `` `ended-early note sets noteKind GRACEFUL_END` `` / `` `no-adjustment note sets noteKind NO_ADJUSTMENT` ``
  - `` `offer face carries the weekly bars from the coordinator` `` (fake coordinator exposing a `lastOfferWindow` value → assert `uiState.weeklyBars` non-empty on OFFER, empty on other faces)
  - `` `minimize then expand toggles offerMinimized without declining` `` (assert `offerMinimized` true/false; assert coordinator.decline NOT called)
  - `` `a fresh offer resets offerMinimized to false` `` (minimize, then emit a new OFFERED state, assert `offerMinimized == false`)

- [ ] **Step 5: Implement VM changes.**
  - Add `noteKind` + `weeklyBars` to `RebalanceCardUiState`; set `noteKind` in `noteFace()` from the same
    `status` `when` that picks the copy slot (COMPLETED→COMPLETION, NO_ADJUSTMENT→NO_ADJUSTMENT, else
    GRACEFUL_END); populate `weeklyBars` in `offerFace()` from the combined `lastOfferWindow` value.
  - Add `private val _offerMinimized = MutableStateFlow(false)`; `val offerMinimized: StateFlow<Boolean>`;
    `fun onMinimizeOffer() { _offerMinimized.value = true }`; `fun onExpandOffer() { _offerMinimized.value = false }`.
    In `onStateChanged`, when a **new OFFER** is derived (face transitions to OFFER, or the plan id changed),
    reset `_offerMinimized.value = false`.
  - Extend the `combine(store.state, coordinator.endedNotice)` to also take `coordinator.lastOfferWindow`
    (3-way `combine`), threading it into `deriveFace`/`offerFace`.
  - `RebalanceViewModel` constructor is unchanged (coordinator already injected).

- [ ] **Step 6: AppContainer** — no factory signature change needed (coordinator already passed); confirm it compiles. If `RebalanceDayBar` moved to `domain`, fix imports.

- [ ] **Step 7: Run** — `./gradlew :app:testDebugUnitTest --tests "*RebalanceViewModelTest" --tests "*RebalanceCoordinatorTest"` green; `./gradlew :app:compileDebugKotlin` clean. Update any existing VM test that constructs `RebalanceCardUiState` for the new fields (additive defaults → should be zero changes, but verify).
- [ ] **Step 8: Commit** — `feat(rebalance): VM/coordinator surface for redesign (noteKind, weeklyBars, minimize)`

---

### Task 3: Visualization atoms (Sonnet 5)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceViz.kt`

**Read first:** `ui/component/WeekCalorieStrip.kt:210-265` (`WeekBarItem` — bar draw + dashed target line +
zone band + over-color), `ui/component/charts/SparklineChart.kt:218-251` (dot draw/glow), `ChartDefaults.kt`
(AnimSpec), `ui/component/charts/StackedBarChart.kt:58-66` (Animatable stagger). Use `RebalanceDayBar` from Task 2.

- [ ] **Step 1: `WeeklyBarsChart(bars: ImmutableList<RebalanceDayBar>, modifier)`.** A `Row` of vertical
  bars sized by `eatenKcal / maxScale`, over-days `#F97316` (use the app's warn color if one exists, else
  this hex is acceptable as it matches `WeekBarItem`), plan-days accent gradient, a `Divider`-thin gap
  before plan days, and a dashed target line via `drawBehind` at `baseTargetKcal` fraction. Each bar height
  animates via `Animatable(0f)`→1 staggered `delay(i * ChartDefaults.AnimSpec.barStaggerMs)` then
  `animateTo(1f, ChartDefaults.AnimSpec.barRise)` — **guarded**: if `!rememberAnimationsEnabled()`, snap to
  1f (no stagger). Day labels under bars (`AppType.metaLabel`, `textMuted`; today/plan-start accent). A
  caption Row: "2 higher days → N lighter days" (`AppType.cardSubtitle`).

- [ ] **Step 2: `DayDots(dayX: Int, ofY: Int, mini: Boolean = false, modifier)`.** `ofY` dots: `i < dayX`
  filled accent with ✓, `i == dayX` ringed + glow, `i > dayX` hollow. Non-mini draws a connecting track
  (filled to dayX) + Mon–Thu labels. Each dot scales in via `animateFloatAsState(target, ChartDefaults.AnimSpec.dotPop)`
  staggered by `barStaggerMs`; the today-dot glow uses `rememberInfiniteTransition` **only when
  `rememberAnimationsEnabled()`** (else static). `mini` = 8dp dots, no labels/track (for the ribbon).

- [ ] **Step 3: `ConvergenceReadout(fromKcal: Int, toKcal: Int, targetKcal: Int, modifier)`.** A Row:
  `"$fromKcal"` (`textDim`) → arrow → an **animated** `toKcal` (`animateIntAsState(toKcal, tween(600))`,
  emerald, bold) → `"kcal · target $targetKcal"` (`textDim`). All `AppType.cardSubtitle`, tabular figures
  (use the app's number style if present).

- [ ] **Step 4: `LeverTiles(reduction: Int, extraSteps: Int, days: Int, modifier)`.** Three tiles in a Row
  (`NeutralCard`-ish inner boxes or plain `Box` with `cardSurface`/`cardBorder`): `−{reduction}` / `+{steps}` /
  `{days}` big (`AppType.statValueSmall`) + caption (`AppType.metaLabel`). A tile whose value is 0 renders
  muted (alpha 0.42) with an em-dash. Stagger fade-in via `AnimatedVisibility`/`animateFloatAsState` gated by
  the animations flag.

- [ ] **Step 5: Compile** — `./gradlew :app:compileDebugKotlin`. BUILD SUCCESSFUL. (Add `@Preview`s if the
  app uses them for chart components — check `WeekCalorieStrip` for the preview convention.)
- [ ] **Step 6: Commit** — `feat(rebalance): weekly-bars, day-dots, convergence and lever viz`

---

### Task 4: Offer overlay + reopen pill (Sonnet 5)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceOfferOverlay.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceReopenPill.kt`

**Read first:** `WeeklyBriefingOverlay.kt` (the `Dialog` scaffold + `BriefingPrimaryButton`/`BriefingGhostButton`
now `internal` from Task 1), `DashboardScreen.kt:320-360` (the Weekly Review pill: `LiquidGlassButton` +
radial glow halo + `FloatingNavHeight` — `FloatingNavHeight = 80.dp` in `RecompApp.kt`), Task 1's `AiDialogCard`,
Task 3's viz.

- [ ] **Step 1: `RebalanceOfferOverlay`.**
```kotlin
@Composable
internal fun RebalanceOfferOverlay(
    state: RebalanceCardUiState,
    minimized: Boolean,
    phrasing: Boolean,                 // true while the phrasing job runs → Generating glow
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMinimize: () -> Unit,
    onCustomize: (RebalanceMode) -> Unit,
)
```
Early-return `Unit` when `state.face != OFFER || minimized`. Else `Dialog(onDismissRequest = onMinimize,
properties = DialogProperties(usePlatformDefaultWidth = false))`. Inside, an **enter animation**: a
`MutableTransitionState(false)` set true in `LaunchedEffect(Unit)`; wrap the card so it scales `0.94→1` +
fades in via `graphicsLayer`/`animateFloatAsState` with `spring(DampingRatioLowBouncy, StiffnessMediumLow)`
— when `!rememberAnimationsEnabled()`, skip the scale (fade only). Card =
`AiDialogCard(borderMode = if (phrasing) AiBorderMode.Generating else AiBorderMode.Ready,
Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp))`. Contents per SPEC §3: header
(`SectionLabel("Weekly Rebalance")` + `AiBadge()` + ▾ `DismissButton(onMinimize, "Decide later")`),
headline (`AppType.screenTitleCompact`, `state.headline`), body (`AppType.body`, `state.body`),
`WeeklyBarsChart(state.weeklyBars)`, `LeverTiles(...)` (derive reduction from `state.effectiveCalories` vs
base — or pass via state; simplest: `LeverTiles(reduction = base - state.effectiveCalories, extraSteps =
state.extraSteps, days = state.ofY)` where base comes from the plan; if base isn't on the UI state add it in
Task 2, else compute reduction = `state.weeklyBars`' plan bar delta), `BriefingPrimaryButton("Start rebalance",
onAccept, Modifier.fillMaxWidth())`, a quiet decline (`BriefingGhostButton("Keep my normal plan", onDecline,
Modifier.align(CenterHorizontally))`), an "Adjust the balance" text button toggling a local
`remember { mutableStateOf(false) }` that `animateContentSize`-expands a `GlassSegmentedToggle(listOf("Eat less",
"Balanced","Move more"), state.mode.toIndex(), { onCustomize(it.toMode()) })` + the "recomputes instantly"
line, and a footer "Tap outside to decide later" (`AppType.metaLabel`, `textMuted`).
  > NOTE: `LiquidActionButton`/`GlassBottomSheet` must NOT be used here (Dialog window). Customize is an
  > **inline expand**, not a bottom sheet.
  > If `RebalanceCardUiState` lacks the base calories needed to show the reduction, add `baseCalories: Int`
  > to it in Task 2's `offerFace` (from `plan.baseCalories`) — flag back to the Task-2 agent if missing.

- [ ] **Step 2: `RebalanceReopenPill`.**
```kotlin
@Composable
internal fun RebalanceReopenPill(visible: Boolean, stackedAboveWeeklyReview: Boolean, onExpand: () -> Unit)
```
Copy the Weekly Review pill block (`LiquidGlassButton` + radial-gradient glow halo + `bottom =
FloatingNavHeight + 12.dp + (if stackedAboveWeeklyReview) 60.dp else 0.dp`), label "✦  Weekly Rebalance".
Wrap in `AnimatedVisibility(visible, enter = slideInVertically(tween(220)) { it/2 } + fadeIn(tween(220)),
exit = slideOutVertically(tween(180)) { it/2 } + fadeOut(tween(180)))` (ToastOverlay idiom), degrading to
`fadeIn/out` only when animations disabled. `LiquidGlassButton` IS allowed here (dashboard window, has
`LocalBackdrop`).

- [ ] **Step 3: Compile** — `./gradlew :app:compileDebugKotlin`. BUILD SUCCESSFUL. (No inline call site yet — wired in Task 7.)
- [ ] **Step 4: Commit** — `feat(rebalance): floating offer overlay and reopen pill`

---

### Task 5: Progress ribbon + detail dialog (Sonnet 5)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceRibbon.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceProgressDetailOverlay.kt`

**Read first:** Task 3 viz, Task 1 `AiDialogCard`, `ui/component/WeekCalorieStrip.kt:180-205` (the "Today"
pill `AnimatedVisibility` enter/exit idiom for the ribbon), `RebalanceViewModel.progressFace` (day-0 case).

- [ ] **Step 1: `RebalanceRibbon`.**
```kotlin
@Composable
internal fun RebalanceRibbon(state: RebalanceCardUiState, onClick: () -> Unit, modifier: Modifier = Modifier)
```
Render only when `state.face == PROGRESS`. A `Row` (`Modifier.clickable(onClick)` — its own clickable so
the Today card's Food-Log tap is not hijacked), rounded 10dp, subtle accent hover/press via
`animateFloatAsState` alpha. **Day-0 case** (`state.dayX == 0`): show `SectionLabel`-styled "Rebalance ·
starts tomorrow", no dots/chevron. Else: `DayDots(state.dayX, state.ofY, mini = true)` + "Rebalance · day
X of Y" (`SectionLabel`/`accentLighter`) + "−{reduction} kcal" + a `›` chevron (`Icons.AutoMirrored.Filled.KeyboardArrowRight`,
`accentLight`). The caller wraps it in `AnimatedVisibility(enter = fadeIn(tween(200)) +
expandVertically(tween(220), Alignment.Top), exit = fadeOut(tween(150)) + shrinkVertically(tween(170),
Alignment.Top))` — degrade to fade when animations disabled.

- [ ] **Step 2: `RebalanceProgressDetailOverlay`.**
```kotlin
@Composable
internal fun RebalanceProgressDetailOverlay(open: Boolean, state: RebalanceCardUiState, onClose: () -> Unit)
```
Early-return when `!open || state.face != PROGRESS`. `Dialog(onDismissRequest = onClose,
DialogProperties(usePlatformDefaultWidth = false))` with the same card-scale-fade enter as Task 4, card =
`AiDialogCard(AiBorderMode.Ready, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
scrollable = false)`. Contents: header (`SectionLabel("Weekly Rebalance")` + ✕ `DismissButton(onClose,
"Close")`), "Day X of Y" (`AppType.screenTitleCompact`), `DayDots(state.dayX, state.ofY)` (full),
`ConvergenceReadout(...)` (from the plan's now/after/target — if those numbers aren't on the UI state,
compute from `state.effectiveCalories` + `state.progressFraction`; keep deterministic, no invented numbers),
and the momentum line (`state.body` already carries the phrased PROGRESS_LINE — reuse it; append
`"+{extraSteps} steps"` when `state.extraSteps > 0`). Day-0: show the starts-tomorrow line instead.

- [ ] **Step 3: Compile** — `./gradlew :app:compileDebugKotlin`. BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `feat(rebalance): Today-card progress ribbon and detail overlay`

---

### Task 6: Inline ending note cards (Sonnet 5)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceNoteCard.kt`

**Read first:** `ui/dashboard/CoachTodaySlot.kt` (the celebration/gold `FrostedCard(surfaceTint =
celebrationSurface, borderColor = celebrationBorder)` skin + `celebrationInk`), `AppColors.kt`
(`celebrationSurface/Border/Ink`, and pick emerald tokens for green — reuse the emerald hexes from the
prototype only if no token exists; prefer a token), Task 1 `DismissButton`, `ui/train/component/PrBanner.kt`
(reduce-motion-gated `AnimatedVisibility`).

- [ ] **Step 1: `RebalanceNoteCard`.**
```kotlin
@Composable
internal fun RebalanceNoteCard(state: RebalanceCardUiState, onDismiss: () -> Unit, modifier: Modifier = Modifier)
```
Render only when `state.face == NOTE`. Skin by `state.noteKind`:
  - `COMPLETION` → `FrostedCard(surfaceTint = appColors.celebrationSurface, borderColor =
    appColors.celebrationBorder)`; header `SectionLabel("Rebalance complete", color = celebrationInk)` + a
    🏆 that **scale-pops in** (`animateFloatAsState 0→1, spring(MediumBouncy)`, gated); title "Back on
    track." (`AppType.screenTitleCompact`); body `state.body`; `DismissButton`.
  - `GRACEFUL_END` → green-tinted `FrostedCard` (emerald surface/border tokens) + 🌿; body `state.body`.
  - `NO_ADJUSTMENT` → neutral green `FrostedCard` + 🧭; body `state.body`.
  Wrap the whole card in `AnimatedVisibility(enter = fadeIn(tween(220)) + slideInVertically(tween(220)) {
  -it/3 }, exit = fadeOut(tween(150)))`, degraded to fade when animations disabled (PrBanner idiom). (The
  `AnimatedVisibility` may live at the call site in Task 7 instead — put the enter/exit at whichever level
  matches how `CoachTodaySlot`/`PrBanner` do it; prefer call-site so the LazyColumn item animates.)

- [ ] **Step 2: Compile** — `./gradlew :app:compileDebugKotlin`. BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `feat(rebalance): gold/green inline ending cards`

---

### Task 7: Dashboard integration + delete old card (Opus 4.8)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (only if a VM factory/param signature changes)
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt` (only if `HomeDashboardScreen` params change)
- Delete: `app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceCard.kt`

**Read first:** `DashboardScreen.kt` fully (the rebalance item ~271-284, `HomeDashboardScreen` ~101-188, the
Weekly Review pill ~320-360, `TodayCard` ~439-562), Tasks 4/5/6 composables, `RebalanceViewModel`'s new
`offerMinimized` flow.

- [ ] **Step 1: Collect new VM state** in `HomeDashboardScreen`: `val offerMinimized by
  rebalanceViewModel.offerMinimized.collectAsStateWithLifecycle()`. Track phrasing state if the VM exposes it
  (else pass `phrasing = false` — the Generating glow is a nice-to-have; if the VM doesn't expose an
  in-flight flag, add a tiny `phrasing: StateFlow<Boolean>` in Task 2 or drop to always-`Ready`). Add a local
  `var progressDetailOpen by rememberSaveable { mutableStateOf(false) }`.

- [ ] **Step 2: Replace the inline rebalance item.** In `HomeDashboardContent`, change the
  `if (rebalanceCardState.face != NONE) item { RebalanceCard(...) }` block to render **only the NOTE face
  inline**:
```kotlin
if (rebalanceCardState.face == RebalanceCardUiState.Face.NOTE) {
    item { RebalanceNoteCard(state = rebalanceCardState, onDismiss = onRebalanceDismiss) }
}
```
(Offer + progress no longer render inline.)

- [ ] **Step 3: Ribbon into `TodayCard`.** Add params `rebalanceCardState: RebalanceCardUiState =
  RebalanceCardUiState()` and `onRebalanceRibbonClick: () -> Unit = {}` to `TodayCard`; as the first child of
  its `FrostedCard` column, render `AnimatedVisibility(rebalanceCardState.face == PROGRESS) {
  RebalanceRibbon(rebalanceCardState, onRebalanceRibbonClick) }` (enter/exit per Task 5). Pass them at the
  call site: `TodayCard(state, onClick = onOpenFoodLog, rebalanceCardState = rebalanceCardState,
  onRebalanceRibbonClick = { progressDetailOpen = true })`.

- [ ] **Step 4: Hoist the overlays** as siblings of `HomeDashboardContent` inside `HomeDashboardScreen`'s Box
  (mirroring how `WeeklyBriefingOverlay` is hoisted at ~170-187):
```kotlin
RebalanceOfferOverlay(
    state = rebalanceCardState, minimized = offerMinimized, phrasing = phrasing,
    onAccept = { rebalanceViewModel.onAccept() },
    onDecline = { rebalanceViewModel.onDecline() },
    onMinimize = { rebalanceViewModel.onMinimizeOffer() },
    onCustomize = { rebalanceViewModel.onCustomize(it) },
)
RebalanceProgressDetailOverlay(open = progressDetailOpen, state = rebalanceCardState,
    onClose = { progressDetailOpen = false })
```

- [ ] **Step 5: Reopen pill** stacked with the Weekly Review pill. In the same `BottomCenter` region, add:
```kotlin
RebalanceReopenPill(
    visible = rebalanceCardState.face == RebalanceCardUiState.Face.OFFER && offerMinimized,
    stackedAboveWeeklyReview = onOpenWeeklyReview != null,
    onExpand = { rebalanceViewModel.onExpandOffer() },
)
```
(If both the WeeklyReview pill and the rebalance pill are wrapped in the fillMaxSize Box, ensure z-order and
offsets don't overlap — the rebalance pill sits `+60.dp` above when `stackedAboveWeeklyReview`.)

- [ ] **Step 6: Thread callbacks/params** through `HomeDashboardContent` as needed (it currently takes
  `onRebalanceAccept/Decline/Dismiss/Customize` + `rebalanceToday`; add nothing new to the *content* signature
  for offer/progress since those now render at the screen level — remove the now-unused offer/progress
  callbacks from `HomeDashboardContent` if they're only used by the deleted inline card, keeping `onRebalanceDismiss`
  for the note). Keep `HomeDashboardScreen`/`AppNavGraph` signatures stable unless a param must change; if
  `HomeDashboardScreen` gains no new external params, `AppNavGraph` needs no edit.

- [ ] **Step 7: Delete `RebalanceCard.kt`.** `git rm app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceCard.kt`. Fix any remaining references (the old `RebalanceCard`/`ProgressFaceContent`/`OfferFaceContent`/local `DismissButton` are gone — the shared `DismissButton` replaces it).

- [ ] **Step 8: Compile + full suite** — `./gradlew :app:compileDebugKotlin` clean; `./gradlew :app:testDebugUnitTest` — the ONLY acceptable failure is the pre-existing `InsightHarnessTest` live-network 429 (environment). Fix any other breakage (e.g. a test referencing the deleted `RebalanceCard`).
- [ ] **Step 9: Commit** — `feat(rebalance): wire popup offer, merged progress and inline notes into the dashboard`

---

### Task 8: Verification + review (Sonnet 5 verify + Fable review)

- [ ] **Step 1:** `./gradlew :app:testDebugUnitTest` — full suite; paste the tail + confirm only the known
  `InsightHarnessTest` env failure.
- [ ] **Step 2:** `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- [ ] **Step 3: Grep audit** (each must be empty):
  - `grep -rn "LocalBackdrop\|AiInsightCard\|FrostedCard\|TintedCard\|LiquidActionButton\|LiquidPrimaryButton\|GlassBottomSheet" app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceOfferOverlay.kt app/src/main/java/com/zack/recomptracker/ui/dashboard/RebalanceProgressDetailOverlay.kt` (no backdrop-dependent components inside dialogs)
  - `grep -rn "fontSize\s*=\|Color(0xFF" app/src/main/java/com/zack/recomptracker/ui/dashboard/Rebalance*.kt` (no hardcoded type/hex except the documented over-bar `#F97316` / prototype emerald if no token)
  - `grep -rln "rememberAnimationsEnabled" app/src/main/java/com/zack/recomptracker/ui/dashboard/Rebalance*.kt` (animations gated)
- [ ] **Step 4: Orchestrator (Fable) review** of the full branch diff against the SPEC + the prototype:
  offer popup reuses `AiDialogCard`/`aiEdgeGlow`; reopen pill; merged ribbon + tap-to-float detail; gold/green
  notes; weekly-bars + dots + convergence; every animation present and reduce-motion-gated; all §9 edge cases
  handled. Then hand off to the user for on-device visual verification.

---

## Self-review (done at plan time)

- **Spec coverage:** §1 surfaces → T4/T5/T6/T7; §2 reuse → T1; §3 offer → T4; §4 progress → T5; §5 notes →
  T6; §6 viz → T3; §7 animation contract → T3/T4/T5/T6 (each element mapped); §8 VM/coordinator → T2; §9 edge
  cases → T2 (day-0/minimize/reset) + T5 (day-0 ribbon) + T7 (pill stack, note-only inline) + T4 (fallback/glow);
  §10 tests → T2; §11 files → all tasks; §12 follow-ups intentionally unplanned.
- **Placeholder scan:** the two flagged unknowns (does `RebalanceCardUiState` carry `baseCalories` for the
  reduction label; does the VM expose a `phrasing` in-flight flag) are called out inline with a concrete
  fallback (add the field in T2 / pass `Ready`) — not silent TBDs.
- **Type consistency:** `RebalanceDayBar`, `NoteKind`, `offerMinimized`/`onMinimizeOffer`/`onExpandOffer`,
  `lastOfferWindow`, `AiDialogCard(borderMode, cornerRadius, scrollable)`, `RebalanceOfferOverlay(state,
  minimized, phrasing, on…)` are consistent across T2→T4→T7.
