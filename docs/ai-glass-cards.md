# AI Glass Cards — Component Guide

A practical reference for the app's AI glass card system: what each piece is, the variations,
when to use them, and the buttons/actions they can carry (now and in future).

For the *design rationale*, see [ai-card-design.md](ai-card-design.md). This doc is the
*how-to-use* companion.

All composables live in `ui/component/` unless noted. Package: `com.zack.recomptracker.ui.component`.

---

## 1. The material (what makes a card a "glass card")

Every AI card is the same glass body with an inner edge glow:

- **Liquid glass** — the real Kyant nav-bar material (`drawBackdrop` + `vibrancy()` + `blur(8dp)`
  + `lens(24,24)` over a neutral wash), plus a top sheen + hairline border for glass character.
- **Inner edge glow** (`Modifier.aiEdgeGlow(mode, cornerRadius)`) — an Apple-Intelligence-style
  iridescent glow that reads as light *inside* the glass edge: a thin core line + a soft
  `BlurMaskFilter` bloom + a faint "light-spill" that washes onto the glass face. The hue breathes;
  it brightens/speeds up while generating. Honours reduced-motion; degrades to a thin stroke below
  API 28.

```kotlin
AiInsightCard(
    borderMode = AiBorderMode.Ready,   // drives the glow's mood
    collapsed = false,                  // true → Capsule pill, false → rounded card
    contentPadding = 16.dp,
) { /* ColumnScope content */ }
```

### `AiBorderMode` — the glow's mood

| Mode | Use | Glow |
|---|---|---|
| `Static` | settled / non-AI-busy | soft, slow breathe |
| `Preparing` | warming up | gentle pulse |
| `Generating` | actively producing | brighter + faster |
| `Ready` | finished | settles to slow breathe |

`AiInsightCard` is the low-level building block. Most surfaces use the higher-level
`GeneratedInsightCard` (below), which picks the mode/variant for you from an `AiInsightState`.

---

## 2. The engine: `GeneratedInsightCard`

One composable renders every insight surface. Backward-compatible signature — only the first three
args are required:

```kotlin
GeneratedInsightCard(
    title: String,                                  // section label, e.g. "Coach spotted"
    state: AiInsightState,                          // Generating / Ready / Error / lifecycle
    onRetry: () -> Unit,                            // refresh action
    modifier: Modifier = Modifier,
    variant: InsightCardVariant = STANDARD,         // HERO | STANDARD | PILL
    evidence: String? = null,                       // the "because…" line under the verdict
    confidence: ConfidenceLevel? = null,            // High / Medium badge (or null = no badge)
    onTellMeMore: (() -> Unit)? = null,             // optional action (see §5)
    onFeedback: ((helpful: Boolean) -> Unit)? = null, // optional action (see §5)
)
```

It maps `AiInsightState` → the right card automatically:

| State | Renders |
|---|---|
| `Generating(partialText)` | collapsed loading pill (compact shimmer + generating glow) |
| `Ready(text)` | the collapsible card (variant-driven) |
| `Error(message)` | a static card with the message + "Try again" |
| model-lifecycle states (`Disabled`, `ModelMissing`, `Downloading`, `ModelReady`, …) | nothing (card hidden) |

---

## 3. The variations

### 3.1 Collapse model (applies to all)

Every Ready card is **collapsed by default** — a glass **pill** showing `✦` + the one-line verdict
+ the expand toggle. Tapping the header expands it to the full card (verdict, evidence, confidence,
actions) with one spring. Collapse state is remembered across scroll (`rememberSaveable`, keyed by
title). So the "pill" is not a separate component — it's the collapsed state of any card.

```
Collapsed:  ( ✦  Weekends are where protein slips           ⌄ )
Expanded:   ┌─────────────────────────────────────────────────┐
            │ ✦  PATTERN · THIS WEEK              [High]   ⌃   │
            │ Weekends are where protein slips.               │
            │ Under target 4 of 7 days — Sat & Sun −38g.      │
            │                                          ( ↻ )  │
            └─────────────────────────────────────────────────┘
```

### 3.2 Variants — pick by importance

| Variant | When to use | Differences |
|---|---|---|
| **`HERO`** | the single headline insight on a screen (e.g. dashboard `WEEKLY_PATTERN`) | larger verdict (20sp); show `evidence` + `confidence` |
| **`STANDARD`** | per-screen insights (trend, recovery) | verdict 16sp; evidence/confidence optional |
| **`PILL`** | lightweight nudges tied to a number (e.g. "Rest of day") | identical to Standard once expanded; just emphasises the collapsed-first intent |

> Today all three look the same collapsed (a pill). The variant changes the expanded prominence.

### 3.3 Loading variations

| Composable | Shape | Use |
|---|---|---|
| collapsed loading (default for `Generating`) | pill | the normal on-card loading state — `✦` + a compact `InsightShimmerBar` |
| **`InsightGeneratingExpanded(title, partialText)`** (public) | full card | reusable full loading card — header + 3-line `InsightShimmerLines`, or streaming text once tokens arrive. Use when you want the big "thinking" treatment instead of a pill. |

### 3.4 Coach process card

The coach chat's thinking state is a dedicated expanded glass card showing the **live process**:
`CoachState.Thinking` carries a running `steps: List<String>` (thinking + tool calls); the chat
renders it (`ThinkingProcess`) inside an `AiInsightCard(Generating)`:

```
┌─────────────────────────────────────────────┐
│ ✓  Reading your food log…                    │
│ ✓  Logging meal…                             │
│ ›  Thinking…                          • • •   │   ← active step + thinking dots
└─────────────────────────────────────────────┘
```

Completed steps get `✓` (dimmed); the active (last) step is highlighted with `ThinkingDots`.

### 3.5 Weekly briefing modal

`BriefingGlassCard` (in `ui/review/WeeklyBriefingOverlay.kt`) is the **dialog-safe** variant: no
backdrop is available inside a `Dialog`, so it uses a neutral translucent dark surface + top sheen
+ hairline + the same `aiEdgeGlow`. Use this pattern for any AI card shown inside a Dialog.

---

## 4. States cheat-sheet

```kotlin
sealed class AiInsightState {
    object Disabled; object ModelMissing
    data class Downloading(progress: Float?); object DownloadFailed
    object ModelVerifying; object ModelReady; object LoadingModel   // → card hidden
    data class Generating(partialText: String)                       // → loading pill
    data class Ready(text: String)                                   // → collapsible card
    data class Error(message: String)                                // → static + retry
}
```

The model-lifecycle states intentionally render nothing on insight surfaces — model
download/management lives on the dashboard and the More screen, never duplicated per card.

---

## 5. Buttons & actions

### 5.1 Shipped (wired today)

| Action | Control | Behaviour |
|---|---|---|
| **Expand / collapse** | `GlassIconButton` chevron (`KeyboardArrowDown`, rotates 180°) | toggles the pill ↔ full card. The whole header row is the tap target. |
| **Refresh** | `GlassIconButton` `Refresh` icon | re-runs generation (`onRetry`), right-aligned in the action row |

Both are small **liquid-glass pills** (nav-bar material) with centred Material vector icons —
`GlassIconButton(icon, contentDescription, onClick, rotation)`.

### 5.2 Available as hooks (render only when you pass them)

`GeneratedInsightCard` renders these *only if the matching lambda is non-null*, so a surface opts in
by passing the hook:

| Action | Hook | Control | Status |
|---|---|---|---|
| **Tell me more** | `onTellMeMore` | `ActionChip` (text pill) | hook present, currently unwired — intended to open the coach seeded with the insight |
| **Feedback** | `onFeedback(helpful)` | `IconAction` `♡` (48dp target) | hook present, currently unwired — intended to tune the top-fact ranking |

To enable one, just pass it at the call site:

```kotlin
GeneratedInsightCard(
    title = "Coach spotted", state = state, onRetry = ::retry,
    variant = InsightCardVariant.HERO,
    onTellMeMore = { navigateToCoach(seed = fact.statement) },   // enables the chip
)
```

### 5.3 Future / re-introducible buttons

The action row (`InsightActions`) is the extension point. Candidates, each a small chip or
`GlassIconButton`:

| Idea | Control suggestion | Notes / needs |
|---|---|---|
| **Dismiss** (✕) | `GlassIconButton` | was shipped then removed; reintroduce with durable persistence (DataStore keyed by insight kind) |
| **Snooze** (⏾) | `GlassIconButton` or chip | hide-until-later; tiered resurface; needs the same store |
| **Thumbs up / down** | two `IconAction`s | richer than a single ♡; feed ranking |
| **Direct log action** ("Log yogurt") | primary `ActionChip` | route through the coach **write-tool confirmation** flow so logging stays consistent |
| **Save / pin** | `GlassIconButton` | keep an insight around |
| **Explain / "why am I seeing this"** | `ActionChip` → expands a reasoning block | progressive disclosure of the computed evidence |
| **Share** | `GlassIconButton` | export the verdict + evidence |

**Rule of thumb:** ≤2 text chips inline (left), icon-buttons on the right; keep destructive/rare
actions behind an overflow if the row gets crowded. Everything interactive must carry `Role.Button`,
an `onClickLabel`/`contentDescription`, and a 48dp touch target (use `GlassIconButton` or
`minimumInteractiveComponentSize()`).

---

## 6. Building blocks (reference)

| Composable | What it is |
|---|---|
| `AiInsightCard(borderMode, collapsed, contentPadding, content)` | the glass body + inner glow |
| `Modifier.aiEdgeGlow(mode, cornerRadius)` | the inner edge glow (apply after `.clip` + surface) |
| `GlassIconButton(icon, contentDescription, onClick, rotation)` | small liquid-glass icon pill (toggle/refresh; `onClick = null` = decorative, parent owns the tap) |
| `ActionChip(text, color, onClick)` | bordered text pill (Tell me more, log actions) |
| `IconAction(glyph, description, color, onClick)` | glyph button with a 48dp target (feedback) |
| `ConfidenceBadge(level)` | High (teal) / Medium (amber) pill — display only |
| `AiBadge()` | the `✦ AI` label |
| `InsightShimmerLines()` | 3-line shimmer skeleton (full loading) |
| `InsightShimmerBar(modifier)` | single self-contained shimmer bar (compact loading) |
| `ThinkingDots()` | the 3-dot "working" animation (coach) |

### Confidence

```kotlin
confidenceFrom(priority: Int): ConfidenceLevel?   // >=30 HIGH, 1..29 MEDIUM, <=0 null
```
Derived from `InsightFact.priority` (real detector range ~10–45). `null` → no badge.

---

## 7. Usage examples

**A standard per-screen insight** (Progress/Recovery):
```kotlin
GeneratedInsightCard(
    title = "Trend analysis",
    state = insightState,
    onRetry = viewModel::retryProgressInsight,
    variant = InsightCardVariant.STANDARD,
)
```

**A hero insight with evidence + confidence** (dashboard):
```kotlin
GeneratedInsightCard(
    title = "Coach spotted",
    state = patternInsightState,
    onRetry = onRetryPatternInsight,
    variant = InsightCardVariant.HERO,
    evidence = fact?.statement,
    confidence = confidenceFrom(fact?.priority ?: 0),
)
```

**A raw glass card** (custom content, not an insight):
```kotlin
AiInsightCard(borderMode = AiBorderMode.Static) {
    Text("Download the AI model in More → AI Model", color = appColors.textPrimary)
}
```

---

## 8. Customization knobs

| What | Where |
|---|---|
| Glow thickness / softness / inset | `GlowStroke`, `GlowBlur`, `GlowInset` in `AiEdgeGlow.kt` |
| Light-spill amount | `GlowSpill*` constants + the `a * 0.16f` alpha in `AiEdgeGlow.kt` |
| Glow brightness per mode | `targetAlpha` `when` in `AiEdgeGlow.kt` |
| Glass wash / sheen / hairline | `containerColor`, the sheen gradient, `hairline` in `AiInsightCard.kt` |
| Card vs pill corner | `CornerCard` (16dp) / `CornerPill` (100dp) in theme `DesignTokens.kt` |
| Glass icon button size | `30.dp` / icon `16.dp` in `GlassIconButton` |

---

## 9. Accessibility & performance

- **Glass material** is library-handled (Kyant lens/refraction, API-gated with fallback) and ships
  at `minSdk 26` via the nav bar — no extra gating.
- **Glow** draws in the **draw phase** (animation read inside `drawWithContent`, never in
  composition); only the visible card animates. `BlurMaskFilter` softness is API 28+; below that the
  glow is a crisp thin stroke (still legible).
- **Touch targets** ≥48dp on all icon controls; `Role.Button` + labels on every interactive element;
  decorative glow/sheen are not announced.
- **Reduced motion**: the glow falls back to static when system animations are off.

---

## 10. Adding a new variant or action

- **New action** → add a control to `InsightActions` in `GeneratedInsightCard.kt`, gated on a new
  optional hook param (so surfaces opt in). Reuse `GlassIconButton` / `ActionChip`.
- **New variant** → extend `InsightCardVariant` and branch in `ReadyCard` (font size, default
  expansion, which slots render). Keep the collapse model.
- **New glass surface** (outside insights) → use `AiInsightCard` directly, or the `BriefingGlassCard`
  pattern if inside a Dialog.
- **Carousel / Hero metric** are designed but deferred — they need `InsightEngine` to expose multiple
  ranked facts + structured numbers (today `detectTopFact` returns one `statement` sentence).
