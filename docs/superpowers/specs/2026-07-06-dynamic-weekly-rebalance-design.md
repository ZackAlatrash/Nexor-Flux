# Dynamic Weekly Rebalance — Design

**Date:** 2026-07-06 · **Branch:** `feat/weekly-rebalance` · **Status:** Approved by user (brainstorm + interactive localhost prototype)

Builds on `2026-07-05-weekly-rebalance-design.md` (the base feature) and `2026-07-06-weekly-rebalance-ui-redesign.md` (the floating-offer UI). This spec makes the offer **scale with the size of the overage** and gives the user a second per-offer dial, so the feature is useful from a small slip up to a 1,200+ kcal blowout, and never "gives up" on the big ones it was built for.

---

## 1. Why (the problem with the current feature)

The engine recovers a **flat 75%** of the surplus over **2–5 days**. That's weak at both ends of the range the user actually cares about:
- **Small overages** get a trivial `−150 × 2-day` micro-plan — noise dressed as a plan.
- **Large blowouts** (the real use case) can exceed what 5 days at the daily cap can recover, so `size()` returns null and the feature shows *"no adjustment, carry on"* — it **bows out exactly when it's most wanted**.

The fix: make the response **dynamic** — scale in magnitude, duration, and *kind* — and let the user choose how hard to go at each offer.

## 2. The two dials (the core change)

The offer gains one control. It now has **two independent dials**, shown inline on the card (no hidden "Adjust" toggle):

| Dial | Label on card | Answers | Options | Default |
|---|---|---|---|---|
| **Intensity** *(new)* | "How much to recover" | how much of the surplus to claw back | **Light / Standard / Full** | Standard, **non-sticky** |
| **Mix** *(exists)* | "How to recover it" | eat less vs move more | **Eat less / Both / Move more** | **sticky** (unchanged) |

They **compose**: *"Full + Move more"* = recover ~all of it, mostly by walking (steps maxed, a small calorie remainder). Intensity sets ambition; mix sets method; both recompute the plan live.

**Naming decisions (locked):** the amount dial is `Light / Standard / Full` (was `Ease off / Balanced / Get back on track` — dropped because "Balanced" collided with the mix dial's middle option, and "Get back on track" was loaded for a "never punitive" feature). The mix middle is `Both` (was `Balanced`). Card labels are "How much to recover" / "How to recover it".

## 3. Intensity → recovery fraction

| Intensity | Recovers | Fraction constant |
|---|---|---|
| Light | ~50% | `RECOVERY_FRACTION_LIGHT = 0.50` |
| Standard *(default)* | ~75% | `RECOVERY_FRACTION_STANDARD = 0.75` |
| Full | ~90–100% | `RECOVERY_FRACTION_FULL = 1.00` |

`RECOVERY_FRACTION` (the old flat 0.75) is replaced by a per-intensity fraction. `targetRecover = round(surplus × fraction)`.

## 4. Spread & duration — gentle, length scales

The recovery is spread so the **daily** cut stays gentle; more recovery is bought with **more days**, never a harder day.
- **Max plan length 5 → 7 days** (`MAX_LENGTH_DAYS = 7`). `size()` still picks the smallest `D` in `[MIN_LENGTH_DAYS, 7]` whose `D × perDayCap ≥ targetRecover` (or the feasible amount — see §5).
- **Safety rails are unchanged and fixed:** daily calorie cut `≤ min(15% × base, 300)`; effective target never below `MIN_EFFECTIVE_CAL = 1200`; extra steps `≤ min(25% × recentAvgSteps, 3000)`. A huge surplus is absorbed by more days or partial recovery (§5), **never** a deeper daily cut.

## 5. Partial recovery — never give up on a realistic blowout

Today `size()` returns null (→ NO_ADJUSTMENT) whenever `maxDays × perDayCap < targetRecover`. That is the backwards behavior. New rule:

- `feasible = min(targetRecover, round(maxDays × perDayCap))`.
- Size the plan to `feasible` (not `targetRecover`). Set `partial = feasible < targetRecover`.
- The plan is always offered when `feasible > 0` and the surplus is in the plan band (§6). When `partial`, the UI shows an honest line: *"Recovers about {recovered} of {surplus} — a big one, but this keeps you moving the right way."*
- `perDayCap == 0` (sub-floor base with no steps — can't reduce safely) is the only in-band case with no plan → falls through to the reassurance note.

The existing MOVE_MORE two-tier split (steps-only when a week of steps covers it, else steps-maxed + calorie remainder) is preserved and now interacts with `feasible`.

## 6. Bands — supportive at both extremes, a plan in the middle

By **weekly surplus** (after the existing trigger gates fire — §9 base spec unchanged):

| Weekly surplus | Decision | Card |
|---|---|---|
| below `REASSURE_MAX_KCAL` (**500**) | **Reassurance note** *(new)* | "You're still on track — your weekly average is still near target. Nothing to do." |
| `500` … `RESUME_MIN_KCAL` (**4000**) | **Offer** (partial if needed) | the dynamic plan + two dials |
| above `4000` | **Resume note** | "One rough patch won't derail you — you can't sensibly claw all of it back without it feeling like a punishment. Just resume your normal plan tomorrow." |

Both thresholds are `RebalanceDefaults` constants, trivially tunable on-device. `RESUME_MIN_KCAL` is defined so that even **Light** (50%) can't fit in 7 days at the max daily cap (`0.5 × surplus > 7 × MAX_CAL_REDUCTION_ABS` ⇒ `surplus > 4200`); we round to a clean **4000**.

The engine returns one of three decisions (see §8): `Offer`, or a supportive `NoAdjustment` note carrying which flavor (`REASSURE` vs `RESUME`) it is.

## 7. Recomp — same tool as everyone

Drop the Recomp special-casing (`RECOVERY_FRACTION_RECOMP = 0.375`, `RECOMP_MAX_LENGTH_DAYS = 3`). Recomp now uses the same intensity presets and 7-day max. Rationale: a surplus pushes a Recomp user (goal = maintenance) into a gaining state, so recovering it returns them to maintenance — their goal — so full recovery is well-aligned, not aggressive; and the user picks Light if they want gentle. **Bulk goals stay silent** (a surplus while bulking is intended).

`RECOVERY_FRACTION_RECOMP` and `RECOMP_MAX_LENGTH_DAYS` and the goal branch in `size()` are removed. `evaluate()`/`customize()` no longer vary fraction/maxDays by goal (only Bulk still short-circuits to Silent, as today).

## 8. Data model

`RebalanceIntensity` enum (`domain/rebalance/RebalanceModels.kt`):
```kotlin
@Serializable
enum class RebalanceIntensity(val fraction: Double) {
    LIGHT(0.50), STANDARD(0.75), FULL(1.00);
    companion object { val DEFAULT = STANDARD }
}
```

`RebalancePlan` gains `val intensity: RebalanceIntensity = RebalanceIntensity.STANDARD` (records the selection that produced this plan, so the offer UI can show which segment is active and `customize` can re-derive) and `val partial: Boolean = false` (was the surplus only partially recoverable). Both default so existing serialized/history records deserialize.

`RebalanceState` is **unchanged** — intensity is **not** sticky (per §2), unlike `mode`. The current offer's intensity lives on `state.active` (the plan). Default `STANDARD` at `evaluate()`.

**Serialization:** `RebalanceSerialization` (hand-rolled DataStore codec) and the `BackupPayload` round-trip must encode/decode the two new fields with defaults for old blobs. *(exact functions — finalize from seam-map investigation)*.

## 9. Engine changes (`RebalanceEngine.kt`)

- `size(surplus, baseCalories, recentAvgSteps, baseStepGoal, intensity, mode)` — `goal` param removed; `intensity.fraction` replaces the fraction constant; `MAX_LENGTH_DAYS = 7`; returns `Sizing(days, reduction, extraSteps, recovered, partial)` sized to `feasible` (§5); never returns null for an in-band surplus with `perDayCap > 0`.
- `evaluate(input, newId, nowIso)` — after the trigger gates + surplus computation: Bulk → Silent (unchanged); `surplus < REASSURE_MAX` → `NoAdjustment(REASSURE)`; `surplus > RESUME_MIN` → `NoAdjustment(RESUME)`; else `size(..., intensity = STANDARD, mode = state.mode)` → `Offer` (or `NoAdjustment(REASSURE)` if `perDayCap == 0`).
- `customize(offer, mode, intensity)` — `goal` param removed; recomputes via `size()` with the new mode+intensity; returns `offer.copy(mode, intensity, days, reduction, extraSteps, recovered, partial, status = OFFERED)`. No more null-fallback (partial recovery guarantees a plan when in-band).
- `RebalanceDecision.NoAdjustment` carries a `flavor: ReassureFlavor { REASSURE, RESUME }` (or two decision subtypes) so the UI shows the right note copy.
- `EffectiveTargets` is unchanged (reads `dailyCalorieReduction`/`extraDailySteps` off the plan as today).

## 10. Coordinator + ViewModel

- `RebalanceCoordinator.customize(mode, intensity)` — new signature; `runOnce()` writes the reassurance/resume note into `state.active` (a `NO_ADJUSTMENT` plan carrying the flavor) exactly as it writes a `NoAdjustment` today.
- `RebalanceViewModel`: `RebalanceCardUiState` gains `intensity: RebalanceIntensity` and `partial: Boolean`; add `onCustomizeIntensity(intensity)` (and keep `onCustomize(mode)` → now `onCustomizeMode`); `NoteKind` gains a `REASSURANCE` kind (the small-end note) distinct from `NO_ADJUSTMENT` (the resume note) so the note card can pick copy/tone. The offer face passes `intensity`/`partial` through.

## 11. Copy (`RebalanceCopyService` / `RebalanceCopyPromptBuilder`)

New/updated slots + deterministic fallbacks (cloud phrases, template fallback as always):
- `OFFER_BODY` — scale the intro tone with surplus (`"A slightly high stretch…"` / `"A couple of heavier days…"` / `"A big few days pushed your week up"`) + `"Here's a gentle {days}-day way back."`
- `OFFER_PARTIAL_LINE` *(new)* — "Recovers about {recovered} of {surplus} — a big one, but this keeps you moving the right way." (only when `partial`).
- `REASSURANCE_NOTE` *(new)* — "You're still on track — your weekly average is still near target. Nothing to do."
- `RESUME_NOTE` — reword the existing NO_ADJUSTMENT copy to the "one rough patch won't derail you… just resume" line.
`RebalanceCopyFacts` gains `surplusKcal`, `recoveredKcal`, `partial` as needed.

## 12. UI

**Liquid-glass thin dials.** Both dials render in the app's **real Kyant liquid-glass material** (the same `drawBackdrop` + `vibrancy()`/`blur(8dp)`/`lens(24dp,24dp)` stack as the bottom nav bar in `ui/liquidglass/LiquidComponents.kt`), **not** the flat `GlassSegmentedToggle`, and **thin**. Build a reusable **`LiquidSegmentedToggle`** (in `ui/liquidglass/`) that is Dialog-safe:
- Create a **Dialog-local** `rememberLayerBackdrop()` inside the toggle. Draw the track's tinted surface into a `Box` carrying `Modifier.layerBackdrop(localBackdrop)`; draw the selected-segment "thumb" with `Modifier.drawBackdrop(backdrop = localBackdrop, shape = { RoundedCornerShape(...) }, effects = { vibrancy(); blur(8f.dp.toPx()); lens(24f.dp.toPx(), 24f.dp.toPx()) }, onDrawSurface = { drawRect(accentTintedContainer) })`. This mirrors the nav bar's `tabsBackdrop`→indicator structure and deliberately does **not** use `LocalBackdrop` (which points at the wrong window inside a Dialog).
- **Critical:** keep the `layerBackdrop`↔`drawBackdrop` pair **self-contained inside this composable** — do NOT let the offer's enter-animation `graphicsLayer` (scale/alpha in `RebalanceOfferOverlay.kt`) sit *between* the record and consume (an intervening graphics layer isolates the layer and defeats sampling; see the `RebalanceReopenPill` gotcha). Both being descendants of the same outer `graphicsLayer` is fine (identity relative transform).
- **Thin:** ~30–32dp total — per-segment `padding(vertical ≈ 5–6dp)` (vs the current `8.dp` at `GlassSegmentedToggle.kt:69`), track padding `2.dp`. Expose segment height/padding as a param so the offer requests the compact variant.
- **Fallback** if the live-backdrop version proves animation-flaky: the app's blessed backdrop-free `LiteGlassButton` recipe (`LiquidComponents.kt:769-817`) — translucent tinted fill + capsule + hairline + press-scale — which reads as glass without live refraction (used by every other Dialog-hosted control). Ship (a) if solid, (b) if not.

**Offer overlay** (`RebalanceOfferOverlay.kt`): both dials inline under the lever tiles, with the "How much to recover" / "How to recover it" labels; the honest partial line when `partial`; the size-scaled intro; **no "Adjust the balance" toggle** (dials always visible). Selecting either dial calls the VM (`onCustomizeIntensity` / `onCustomizeMode`) and recomputes live.

**Notes:** the reassurance note (small end) and resume note (extreme) are calm supportive cards — reuse the existing note-card face (emerald skin, dismissible), keyed by the new `NoteKind.REASSURANCE` vs the resume note. No plan, no dials, no Start.

Everything else is unchanged: the floating-offer Dialog, the merged progress ribbon, endings, reopen pill, effective-target overlay, auto-revert, safety rails.

## 13. Debug scenarios (`RebalanceDebugScenarios`)

Add scenarios so the user can eyeball the new range on-device: an offer at each intensity (Light/Standard/Full), a partial-recovery offer (big surplus), the reassurance note (small), and the resume note (huge). Keep the existing progress/ending scenarios.

## 14. Testing

- **RebalanceEngineTest**: fraction per intensity; 7-day max; partial recovery (feasible < target ⇒ partial plan, not null); reassurance band (< 500 ⇒ note); resume band (> 4000 ⇒ note); Light/Standard/Full give distinct plans; `customize(mode, intensity)` recomputes; Recomp uses the same fractions/length as a cut (no more halving); Bulk still Silent; `perDayCap == 0` ⇒ reassurance.
- **RebalanceCoordinatorTest**: `customize(mode, intensity)`; runOnce writes reassurance/resume notes.
- **RebalanceViewModelTest**: intensity in UiState; `onCustomizeIntensity`; REASSURANCE NoteKind derivation.
- **RebalanceSerializationTest + backup tests**: round-trip the `intensity` + `partial` fields; old blobs deserialize with defaults.
- Keep the whole suite green (bar the known env-only `InsightHarnessTest`).

## 15. Non-goals / trade-offs

- Intensity is **not** sticky (deliberate — a per-offer choice); mix stays sticky.
- Always-visible dials make the offer card **taller** — accepted (a once-a-day floating dialog; more discoverable than a hidden toggle).
- No push notifications, no new Room tables, no PlanVersion/PlanPreferences writes (unchanged from base spec).
- The reassurance/resume thresholds (500 / 4000) and intensity fractions are tunable constants; final values validated on-device.

## 16. Implementation notes (from code-seam map)

The clean mental model: **`intensity` is a second `mode`** — thread it wherever `mode` is threaded — with two deviations and two genuinely-new mechanics.

**Deviations from pure mirror-`mode`:**
- **Non-sticky.** Per §8, `intensity` lives on `RebalancePlan` but **NOT** on `RebalanceState` (mix stays sticky; intensity resets to STANDARD each offer). So `evaluate()` sizes with a hardcoded `STANDARD`; `RebalanceEvaluationInput` needs **no** intensity field.
- **Band notes reuse `NO_ADJUSTMENT`.** The reassurance (<500) and resume (>4000) notes are both `RebalanceDecision.NoAdjustment` with `RebalanceStatus.NO_ADJUSTMENT`; the ViewModel derives `NoteKind.REASSURANCE` vs the resume note **from `plan.surplusKcal`** (< `SMALL_SURPLUS_KCAL` ⇒ reassurance, else resume). No new decision variant, no new persisted flavor field.

**Two new mechanics (not mirror-`mode`):**
1. **Partial recovery** at `RebalanceEngine.size()` (the `... < targetRecover → return null` branch): cap `targetRecover` at `maxDays × perDayCap`, size to that, set `partial = true`. Return null only when `perDayCap ≤ 0` (no usable lever) → evaluate routes that to the reassurance note.
2. **Surplus-band gate** in `evaluate()` before sizing: `surplus < SMALL_SURPLUS_KCAL (500)` → NoAdjustment(reassurance); `surplus > HUGE_SURPLUS_KCAL (4000)` → NoAdjustment(resume); else size and Offer.

**Engine signature changes:** `size(...)` and `customize(...)` **drop `goal`** and gain `intensity: RebalanceIntensity`. Delete `RECOVERY_FRACTION_RECOMP`, `RECOMP_MAX_LENGTH_DAYS`, and the goal-based fraction/maxDays branch (recomp now uses the shared intensity fraction + 7-day cap). `evaluate()` keeps reading `input.goal` **only** for the existing Bulk→Silent gate.

**Coordinator:** `customize(mode, intensity)`; the `currentGoal` lambda (its only consumer was `customize`) can be **removed** from the coordinator + its `AppContainer` wiring — verify nothing else calls it. `runOnce()`'s decision `when` routes the band notes exactly like today's `NoAdjustment`.

**ViewModel API (keep existing call sites compiling):** keep `onCustomize(mode)` (delegates `customize(mode, currentIntensity)`) and **add** `onCustomizeIntensity(intensity)` (delegates `customize(currentMode, intensity)`), so the offer overlay compiles before the second dial is wired.

**Serialization (the backup-safety rule):** both new fields (`intensity` on the plan; `partial`) MUST have Kotlin defaults so kotlinx-serialization treats a missing key as optional (old backups decode). The hand-rolled `RebalanceSerialization` codec must **separately** default `intensity` on decode (`?: STANDARD`, never `return null`). `RebalanceState` has no intensity field, so its codec is unchanged.

**Verify:** 7-day plans emit up to **14 bars** in `WeeklyBarsChart` (7 history + 7 plan) — confirm it renders (weight-based, should just thin). `CoachContextAssembler`/`RebalanceContext` are intensity-agnostic (no change).
