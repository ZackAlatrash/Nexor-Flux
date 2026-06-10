# AI Card Liquid-Glass Redesign Design

**Date:** 2026-06-10
**Status:** Approved for implementation planning
**Scope:** Visual restyle of the shared `AiInsightCard` component to an iOS-26 "Liquid Glass + iridescent edge" look. All AI surfaces inherit it.

---

## 1. Goal

Replace the current dark, single-accent "comet border" AI card with an Apple-Intelligence-adjacent treatment: a translucent **liquid-glass body** with a thin, slow **iridescent edge**. The motion must **flow** (hue cycling in place), never spatially rotate (the earlier prototype's rotating ring read as a spinning "helicopter" and is explicitly rejected).

This is a pure visual change. No behavior, state machine, data flow, or consumer API changes.

---

## 2. Scope

Restyle **only** the internals of `app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt`. Because it is the shared card, every consumer inherits the new look with **no edits**:
- The dashboard "Why this verdict" card (all of its lifecycle states: ModelMissing / Downloading / DownloadFailed / ModelVerifying / Preparing / Generating / Ready / Error).
- The three new cards (Progress / Recovery / Food) via `GeneratedInsightCard`.

The `AiBorderMode` enum (`Static / Preparing / Generating / Ready`) is unchanged. `GeneratedInsightCard`, `DashboardScreen`'s `AiInsightSection`, and all call sites are untouched.

---

## 3. Visual design

### 3.1 Body — liquid glass

Built on the existing `com.kyant.backdrop` `drawBackdrop` stack (same library the card already uses, and that `LiquidButton`/`TintedCard` use):

- **Frost:** keep `vibrancy()` + `blur(~22.dp)`. Change the surface fill from the current dark `accent.tintedSurface` to a **lighter translucent frost** (a low-alpha near-white, e.g. white at ~7–10% over the dark backdrop) so the colorful background bleeds through — the iOS-26 material feel. A faint accent tint may remain for app harmony.
- **Specular highlight:** a bright inset top edge plus a soft top-left gleam, via the `drawBackdrop` `highlight` slot (`Highlight.Default`/ambient) and/or a drawn 1.5.dp top edge line at ~30–40% white.
- **Chromatic refractive edge:** add `lens(…, chromaticAberration = true)` — the same effect `LiquidButton` already applies — for the glassy RGB fringe at the rim.
- **Float shadow:** keep a subtle drop shadow for depth.

### 3.2 Edge — iridescent rim

Replaces the single-accent sweep "comet":

- A thin (~1.3.dp) **full-spectrum** sweep-gradient rim (violet → pink → blue → cyan → amber → pink → violet), drawn in the card's existing `drawWithContent` border slot.
- **Resting opacity ~0.5.**
- **Motion = hue flow, not rotation.** Animate a `huePhase` 0→1 over **~16–18 s** (infinite, linear) and apply it as a hue rotation to the sweep's stop colors each frame, so the spectrum drifts *in place*. The gradient geometry does **not** rotate. (The rim may also breathe in opacity where specified below.)

### 3.3 State → motion mapping (`AiBorderMode`)

| Mode | Rim opacity | Motion |
|---|---|---|
| `Static` | ~0.35 (dim) | None (still iridescent, no animation) |
| `Preparing` | ~0.5, gentle opacity breathe | Hue flow + slow opacity pulse |
| `Generating` | ~0.7 (active) | Hue flow, slightly faster — signals "thinking" |
| `Ready` | settles to ~0.5 | Brief brighten-then-settle on entry (reuse the existing ready-fade `animateFloatAsState`), then slow hue flow |

### 3.4 Accent harmony

The AI badge, body text, and specular highlight stay tied to the app accent (`LocalAppAccent`). Only the rim is full-spectrum.

### 3.5 Accessibility

Keep the existing `Settings.Global.ANIMATOR_DURATION_SCALE` check: when animations are disabled, render a **static iridescent rim** (no hue flow, no breathe, no sheen) — same gate the card uses today to fall back to `Static`.

---

## 4. Reuse

- **Body material:** `drawBackdrop` (`vibrancy` + `blur` + `lens(chromaticAberration=true)` + `highlight` + shadow) — all already available; `lens`/`highlight` usage can mirror `LiquidComponents.kt`'s `LiquidButton`.
- **Rim:** the card's existing `drawWithContent` + `Brush.sweepGradient` border slot — swap the single-accent stops for the iridescent palette and replace the `rotate(cometPhase * 360f)` with per-frame hue shifting of the stop colors.
- **Hue shift helper:** a small pure function `hueShifted(color, degrees)` (HSV rotate) — unit-testable.

---

## 5. Verification

- **Compose `@Preview`s** refreshed for each state (`Static`, `Preparing`, `Generating`, `Ready`) showing the new look — the component's existing verification mechanism (it has previews, no unit tests).
- **Unit test** for the pure `hueShifted(...)` helper (the only non-visual logic introduced).
- **On-device visual check** across a couple of accent themes and with reduce-motion on.

---

## 6. Out of scope

- Any change to `AiBorderMode`, `GeneratedInsightCard`, the coordinator, or any screen/ViewModel.
- The model download/verify flow and its copy.
- Changing which states render (generation-only behavior is unchanged).
- New accent themes or a rainbow accent system in `AppAccent` (the rim palette is local to the card).

---

## 7. Acceptance criteria

- `AiInsightCard` renders a translucent liquid-glass body (frost + specular + chromatic edge) and a thin full-spectrum iridescent rim whose hue flows in place (no spatial rotation).
- The four `AiBorderMode` states map to the intensities/motions in §3.3.
- Reduce-motion yields a static iridescent rim.
- All consumers (dashboard + the three new cards) inherit the look with no edits.
- `hueShifted(...)` is unit-tested; previews compile and show the new look; `:app:testDebugUnitTest` stays green.
