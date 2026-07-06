# Weekly Rebalance — UI Redesign Spec

**Date:** 2026-07-06 · **Branch:** `feat/weekly-rebalance` · **Status:** Approved (design agreed via interactive prototype)

Presentation-layer redesign of the shipped Weekly Rebalance card. The **engine, effective-target
overlay, persistence, coordinator transitions, and coach integration are unchanged** — this spec only
restructures how the three faces are surfaced, adds the visualizations, and animates everything.
Supersedes the "inline card" presentation in
[`2026-07-05-weekly-rebalance-design.md`](2026-07-05-weekly-rebalance-design.md) §3; that doc remains the
source of truth for trigger/plan math (§5), the state machine (§7), and edge cases (§10).

---

## 1. The three surfaces

| Face | Old (shipped) | New (this spec) |
|---|---|---|
| **OFFER** | inline `TintedCard` in the dashboard scroll | **Floating popup Dialog** (reuses the AI/Weekly-Review glass card + `aiEdgeGlow`), with **weekly-bars** viz; tap-outside/▾ → **re-openable glass pill** above the nav. |
| **PROGRESS** (ACTIVE) | inline card | **Merged ribbon** on the existing Today calorie card; tapping it floats a **progress-detail Dialog** (day-dots + convergence readout), same glass card + glow. Zero new dashboard cards. |
| **NOTE** (completion / graceful-end / no-adjustment) | inline card, one plain text style | **Inline cards, three skins**: completion = gold celebration, graceful-end = calm green, no-adjustment = neutral green. Animated enter. |

Rationale: the OFFER is a **one-time event** → deserves a spotlight (popup). PROGRESS is **ambient and
recurring** (shown every day for 2–5 days) → must not occupy a card; it rides the Today card. Endings
are brief and emotional → distinct skins.

## 2. Component reuse (single source of truth)

Reuse the app's AI-card + edge-glow stack exactly as the Weekly Review overlay does. Because a `Dialog`
is a separate Android window, `AiInsightCard`/`FrostedCard`/`TintedCard`/`Liquid*Button` (which sample
`LocalBackdrop`) **cannot** be used inside it — the Weekly Review overlay works around this with a
backdrop-free twin (`BriefingGlassCard`) + hand-rolled Dialog-safe buttons.

**Extract shared primitives** (removes duplication the codebase already has):

- **`ui/component/AiDialogCard.kt`** — NEW. `AiDialogCard(borderMode: AiBorderMode, modifier: Modifier =
  Modifier, cornerRadius: Dp = 24.dp, scrollable: Boolean = true, content: @Composable ColumnScope.() ->
  Unit)`. Body = the exact `BriefingGlassCard` recipe: `clip(RoundedCornerShape(cornerRadius))` →
  translucent solid fill (`Color(0xFF101014).copy(0.92f)` dark / `Color(0xFFF6F6F8).copy(0.95f)` light) →
  top-sheen `drawBehind` → `border(0.5.dp, white 0.16/0.24)` → `aiEdgeGlow(borderMode, cornerRadius)` →
  optional `heightIn(max = 640.dp).verticalScroll()` → `padding(20.dp)`. **Refactor
  `WeeklyBriefingOverlay.BriefingGlassCard` to delegate to it** (behavior must stay pixel-identical —
  verify Weekly Review still renders). Both rebalance dialogs use it.
- **`ui/component/DismissButton.kt`** — NEW shared 32dp circular close (promoted from the verbatim
  duplicate in `CoachTodaySlot`/`RebalanceCard`). Used by the new note cards + dialogs.
- **`ui/component/AnimationGate.kt`** — NEW `@Composable fun rememberAnimationsEnabled(): Boolean`
  wrapping the `Settings.Global.ANIMATOR_DURATION_SCALE > 0f` check (currently duplicated in
  `AiEdgeGlow`/`PrBanner`). New rebalance animations read this; existing call sites left untouched.
- Reuse `BriefingPrimaryButton` / `BriefingGhostButton` / `ConfirmChip` from `WeeklyBriefingOverlay`
  (Dialog-safe). Promote them to `internal` (or a small shared file) so the rebalance overlays can call
  them without copying. Reuse `AiBadge`, `SectionLabel`, `GlassSegmentedToggle`.

## 3. OFFER — floating popup + reopen pill

**Presentation.** A `Dialog(onDismissRequest = onMinimize, properties = DialogProperties(
usePlatformDefaultWidth = false))` rendered as a **sibling of `HomeDashboardContent`** in
`HomeDashboardScreen` (mirroring how `WeeklyBriefingOverlay` is hoisted), early-returning when
`face != OFFER || offerMinimized`. Card = `AiDialogCard(borderMode)`, `fillMaxWidth().padding(horizontal
= 20.dp, vertical = 28.dp)`.

Contents, top→bottom: header row (`SectionLabel("Weekly Rebalance")` + `AiBadge()` + a ▾ minimize
`DismissButton`), headline (`AppType.screenTitleCompact`, "Your weekly goal is still within reach."),
body (`AppType.body`, the phrased/fallback `OFFER_BODY`), **weekly-bars viz** (§6), **lever tiles**
(−kcal / +steps / days), `BriefingPrimaryButton("Start rebalance")`, a quiet `BriefingGhostButton`-style
"Keep my normal plan" (decline), an "Adjust the balance" text button that **inline-expands** the
`GlassSegmentedToggle` (Eat less / Balanced / Move more) via `animateContentSize`, and a footer hint
"Tap outside to decide later".

**Actions.** Start → `onAccept` (`coordinator.accept()`). Keep my normal plan → `onDecline`
(`coordinator.decline()`). ▾ / tap-outside → `onMinimize` (VM `offerMinimized = true`) — **this is
presentation-only, it does NOT decline**. Adjust → `onCustomize(mode)` (`coordinator.customize(mode)`).

**Border mode.** `AiBorderMode.Generating` while the copy-phrasing job is in flight (animated
iridescent glow), settling to `AiBorderMode.Ready` once phrased copy arrives or the fallback is final.

**Reopen pill.** When `face == OFFER && offerMinimized`, show a glass pill above the nav (copy of the
Weekly Review pill: `LiquidGlassButton` + radial glow halo, `bottom = FloatingNavHeight + 12.dp`),
label "✦  Weekly Rebalance". **Pill stacking:** if the Weekly Review pill is also present, the rebalance
pill stacks **above** it (`bottom = FloatingNavHeight + 12.dp + 60.dp`); otherwise it takes the base
slot. Tapping → `onExpand` (VM `offerMinimized = false`).

## 4. PROGRESS — Today-card ribbon + detail dialog

**Ribbon.** When `rebalanceCardState.face == PROGRESS`, render a tappable ribbon as the first child of
`TodayCard`'s `FrostedCard` column (its own `Modifier.clickable` — inner clickable wins, the card's
Food-Log tap is not hijacked). Ribbon = animated **mini day-dots** + `SectionLabel`-styled "Rebalance ·
day X of Y" + "−{reduction} kcal" + a `›` chevron. The Today card's big number already shows the
effective (reduced) target (`DashboardUiState.preferences` is the effective prefs) — the ribbon labels
it "rebalanced target". **Day-0 special-case** (accepted late, `dayX == 0`): the ribbon reads "Rebalance
starts tomorrow" with no dots/chevron detail (there is no reduction in effect today).

**Detail dialog.** Tapping the ribbon opens `RebalanceProgressDetailOverlay` — a `Dialog` using the same
`AiDialogCard(AiBorderMode.Ready)`. Contents: header (`SectionLabel` + ✕), "Day X of Y"
(`AppType.screenTitleCompact`), **full animated day-dots** (done ✓ / today ● glowing / upcoming) with
Mon–Thu labels, a **convergence readout** ("2,560 → 2,490 kcal · target 2,500", animated count),
and a momentum line ("Today 2,300 kcal and +1,200 steps · N days to go — you're almost at your planned
average"). Open state = a local `remember { mutableStateOf(false) }` in `HomeDashboardScreen`; ✕ /
tap-outside closes it.

## 5. NOTE — inline ending cards (three skins)

Rendered inline in the dashboard scroll (same window → `FrostedCard`/`TintedCard` are fine) via a new
`RebalanceNoteCard(state, onDismiss)`, driven by a new `noteKind: NoteKind` on the UI state:

| `noteKind` | Skin | Content |
|---|---|---|
| `COMPLETION` | **Gold** (`FrostedCard(surfaceTint = appColors.celebrationSurface, borderColor = celebrationBorder)`) + 🏆 + `celebrationInk` label | "Back on track." + the completion line + a subtle celebratory scale-in |
| `GRACEFUL_END` | **Green** (emerald-tinted `FrostedCard`) + 🌿 | the graceful-end line |
| `NO_ADJUSTMENT` | **Neutral green** + 🧭 | the no-adjustment line |

All three carry the shared `DismissButton`; dismiss = `onDismiss` (existing coordinator
`dismissEndedNotice()` / `dismissNote()` semantics). `plan_edited` ended notices remain **auto-dismissed
and never rendered** (existing VM behavior).

## 6. Visualizations (`ui/dashboard/RebalanceViz.kt`, new)

Only the two chosen for the picked combo — no gauge/sparkline.

- **`WeeklyBarsChart(bars: List<RebalanceDayBar>)`** — 7 vertical bars (trailing 7 days ending
  yesterday) + a dashed target line + zone band, over-target days flagged (`#F97316`/red), then a thin
  divider + the plan's lighter days ahead in accent. Mirror `WeekCalorieStrip.WeekBarItem`'s draw logic
  (read-only, no selection chrome). `RebalanceDayBar(label: String, eatenKcal: Int, baseTargetKcal: Int,
  isPlanDay: Boolean, isOver: Boolean)`.
- **`DayDots(dayX: Int, ofY: Int, mini: Boolean)`** — N dots: done = filled ✓, today = ringed + glow,
  upcoming = hollow. `mini` = the compact ribbon variant.
- **`ConvergenceReadout(fromKcal, toKcal, targetKcal)`** — the "now → avg · target" text line with an
  animated count on `toKcal`.
- **`LeverTiles(reduction, extraSteps, days)`** — three stat tiles (`−kcal / +steps / days`), muted when 0.

## 7. Animation contract ("animate everything")

Every specced animation reads `rememberAnimationsEnabled()`; when false it degrades to a plain fade or
an instant snap (mirroring `PrBanner`). Use `ChartDefaults.AnimSpec` for all data animations.

| Element | Animation | Spec |
|---|---|---|
| Offer/detail dialog **card enter** | scale 0.94→1 + fade, via `MutableTransitionState`/`animateFloatAsState` on the card (introduce; Dialog window has no in-repo transition) | `spring(DampingRatioLowBouncy, StiffnessMediumLow)`; reduce-motion → fade only |
| Offer **edge glow** | `Generating` (animated hue) while phrasing, → `Ready` | `aiEdgeGlow` (existing) |
| **Weekly bars** rise | per-bar `Animatable(0f)`→1, staggered | `AnimSpec.barRise` (tween 800) + `barStaggerMs` (60ms) |
| **Day-dots** fill | per-dot `animateFloatAsState`, staggered; today-dot glow pulse | `AnimSpec.dotPop` (spring MediumBouncy) + `barStaggerMs`; glow via `rememberInfiniteTransition` gated by animations-enabled |
| **Progress bar** fill | `animateFloatAsState(fraction)` | `AnimSpec.progressBar` (existing rebalance baseline) |
| **Convergence count** | `animateIntAsState` on the avg kcal | `tween(600)` |
| **Lever tiles** | staggered `fadeIn + slideInVertically` | `tween(220)` |
| **Ribbon** appear/disappear | `AnimatedVisibility(fadeIn + expandVertically(Top) / fadeOut + shrinkVertically)` | `tween(220/170)` (WeekCalorieStrip pill idiom) |
| **Reopen pill** appear/disappear | `AnimatedVisibility(slideInVertically { it/2 } + fadeIn / …out)` | `tween(220/180)` (ToastOverlay idiom) |
| **Note cards** enter | `AnimatedVisibility(slideInVertically { -it } + fadeIn)`; completion adds a 🏆 scale-in pop | `spring`/`tween(220)` (PrBanner idiom) |
| **Customize** expand | `animateContentSize(spring())` + toggle `fadeIn` | default spring |
| **Face swap** offer→progress | dialog dismiss (fade) then ribbon `expandVertically` enter — no in-place crossfade needed | — |

## 8. VM / coordinator surface changes (additive only — no engine/persistence change)

`RebalanceViewModel` (`ui/dashboard/RebalanceViewModel.kt`):
- `RebalanceCardUiState` gains `noteKind: NoteKind? = null` (enum `COMPLETION, GRACEFUL_END,
  NO_ADJUSTMENT`), set in `noteFace()` from the existing status `when`; and `weeklyBars:
  ImmutableList<RebalanceDayBar> = persistentListOf()`, populated only in `offerFace()`.
- New `offerMinimized: StateFlow<Boolean>` (in-memory, **not persisted**) + `onMinimizeOffer()` /
  `onExpandOffer()`. Reset to `false` whenever a new OFFER is derived (so a fresh offer always shows the
  popup, never silently a pill). Independent from decline/dismiss.
- Combine `coordinator.lastOfferWindow` into the state pipeline (3-way combine or nested, matching
  `DashboardViewModel`'s nested-combine idiom) to fill `weeklyBars`.

`RebalanceCoordinator` (`data/rebalance/RebalanceCoordinator.kt`):
- Add `val lastOfferWindow: StateFlow<ImmutableList<RebalanceDayBar>?>` (default null), **set once inside
  the existing `runOnce()` right where `buildInput()` is already called** — derived from the
  already-fetched `baseTargetsByDate` + `eatenByDate` (trailing 7 days ending yesterday) + the freshly
  evaluated plan's days. No new repository reads, no transition-logic change. Null when there's no offer.

`AppContainer` — thread the new coordinator flow into the `RebalanceViewModel` factory; no other change.

`DashboardScreen` / `HomeDashboardScreen` / `TodayCard` — remove the inline offer/progress item (keep
only `NOTE` → `RebalanceNoteCard`); hoist the two overlays; add the reopen pill (stacked); pass
`rebalanceCardState` + `onRibbonClick` into `TodayCard` and render the ribbon; new callbacks
(`onRebalanceMinimize`, `onRebalanceExpand`) + a local progress-detail-open state.

## 9. Edge cases the UI must honor (from §7/§10 of the base spec + explorer findings)

1. **Day-0 accepted-late** (`dayX == 0`): ribbon shows "starts tomorrow", no dots/detail affordance; detail dialog, if somehow opened, shows the starts-tomorrow line.
2. **Offer minimized → new day / process death:** `offerMinimized` is in-memory; a relaunched session shows the popup again (acceptable — same day's offer, and it's not gating anything numeric).
3. **plan_edited cancel:** ended notice auto-dismissed, never rendered; ensure the ribbon/overlay don't crash when `state.active` disappears mid-render (drive UI off `rebalanceCardState`, which the VM already recomputes to NONE).
4. **Offer expiry / decline / cooldown:** silent — the popup/pill/ribbon simply stop showing on the next state emission; no special face.
5. **AI off / cloud fail:** fallback copy is seeded synchronously (existing) — the popup never shows blank; the edge glow settles to `Ready` immediately.
6. **No steps in plan** (`extraSteps == 0`): lever "steps" tile muted; bars/dots unaffected; body copy already handles it.
7. **Both pills present** (Weekly Review + Rebalance): stack, rebalance above.
8. **Reduce motion on:** every animation degrades to fade/snap via `rememberAnimationsEnabled()`; the infinite glow/dot-pulse do not subscribe a clock.
9. **NO_ADJUSTMENT:** inline neutral card, dismiss-only, no buttons — occupies the NOTE slot, not a popup.
10. **Rotation / config change:** `offerMinimized` and progress-detail-open survive via VM StateFlow / `rememberSaveable` where it matters.

## 10. Testing

Engine/coordinator numeric logic is unchanged and already covered. New coverage (JVM unit, house style):
- `RebalanceViewModel`: `noteKind` set correctly per COMPLETED/ENDED_EARLY/NO_ADJUSTMENT; `weeklyBars`
  populated for OFFER and empty otherwise; `offerMinimized` toggles via onMinimize/onExpand and **resets
  to false on a new offer**; minimize does **not** call decline; existing tests updated for new fields
  (behavior-neutral — no expectation changes beyond the additive fields).
- `RebalanceCoordinator`: `lastOfferWindow` published (non-empty) when `runOnce()` produces an offer,
  null when Silent/NoAdjustment; existing 17 coordinator tests stay green (additive).
- Compose UI is verified **on-device by the user** (house practice) — no Compose UI unit tests; the
  agents build (`compileDebugKotlin`), run the full unit suite, and hand off for visual verification.

## 11. Files

**New:** `ui/component/AiDialogCard.kt`, `ui/component/DismissButton.kt`, `ui/component/AnimationGate.kt`,
`ui/dashboard/RebalanceViz.kt`, `ui/dashboard/RebalanceOfferOverlay.kt`,
`ui/dashboard/RebalanceProgressDetailOverlay.kt`, `ui/dashboard/RebalanceRibbon.kt`,
`ui/dashboard/RebalanceNoteCard.kt`, `ui/dashboard/RebalanceReopenPill.kt`.
**Modified:** `ui/dashboard/RebalanceViewModel.kt`, `data/rebalance/RebalanceCoordinator.kt`,
`core/AppContainer.kt`, `ui/dashboard/DashboardScreen.kt`, `ui/review/WeeklyBriefingOverlay.kt` (delegate
`BriefingGlassCard` to `AiDialogCard`; promote shared buttons).
**Deleted:** `ui/dashboard/RebalanceCard.kt` (faces migrate to the overlays/ribbon/note card).

## 12. Follow-ups (out of scope)

- Optionally normalize `WeekCalorieStrip`'s bar animation onto `ChartDefaults.AnimSpec.barRise`.
- Optionally refactor `AiEdgeGlow`/`PrBanner` onto the new `rememberAnimationsEnabled()` helper.
- Persisting `offerMinimized` across process death (via `SavedStateHandle`) if the transient reset ever feels wrong in practice.
