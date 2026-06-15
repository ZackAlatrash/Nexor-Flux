# AI Insight Card — Design Document

Status: **Draft for review** · Date: 2026-06-15 · Branch: `feat/ai-card-redesign`

A redesign of every AI-generated card surface in the app into one cohesive, collapsible,
liquid-glass card family with an Apple-Intelligence-style edge glow. The variety comes from
the *insight*, not from one-off components: a single engine renders five variants chosen by
the insight's importance and actionability.

---

## 1. Why

Today every AI insight renders through the **same** `GeneratedInsightCard`
([GeneratedInsightCard.kt:33](../app/src/main/java/com/zack/recomptracker/ui/component/GeneratedInsightCard.kt))
with only the title changed across five surfaces:

| Surface | Title | Insight kind |
|---|---|---|
| Dashboard home | "Coach spotted" | `WEEKLY_PATTERN` |
| Progress | "Trend analysis" | `PROGRESS_TREND` |
| Body / Recovery | "Recovery readiness" | `RECOVERY_READINESS` |
| Food log | "Rest of day" | `REST_OF_DAY` |
| Weekly Review modal | briefing | (own `BriefingGlassCard`) |

Problems:

1. **No hierarchy** — a headline weekly pattern looks identical to a minor tip.
2. **Verdict buried** — insight text reads like a log line, not a takeaway.
3. **No evidence** — the card never shows the numbers that produced the verdict (the single
   highest-leverage trust move per the research).
4. **No actions** — the user can't act on, dismiss, defer, or react to an insight.
5. **Always full-height** — every card occupies full space even when low-value.

## 2. Goals

- One reusable engine; variants selected by data, not duplicated by hand.
- Keep and sharpen the app's existing **liquid-glass + iridescent** identity (it reads more
  premium than the now-ubiquitous purple-gradient AI look).
- **Verdict-first** copy, with an evidence line ("because …") and the underlying numbers.
- Every card **collapsible** — collapsed is a glass pill, expanded is the full action card.
- On-card **actions**: tell me more, feedback, expand, snooze/dismiss.

Non-goals: changing the insight generation pipeline, the coach chat UI, or the AI model
lifecycle. This is a presentation-layer redesign. Copy tuning of the 2B model output is
tracked separately.

---

## 3. Aesthetic foundation

### 3.1 Liquid glass — the exact nav-bar material (non-negotiable)

Cards use the **real Kyant liquid-glass material**, identical to the nav bar — **not** the
lighter `FrostedCard`, and **not** a CSS-style approximation. The browser mockups only *simulate*
glass; the implementation must use the library.

Library (already a dependency — inspiration: <https://github.com/Kyant0/AndroidLiquidGlass>):

- `io.github.kyant0:backdrop:2.0.0`
- `io.github.kyant0:shapes:1.2.0`

The canonical recipe to **copy from `LiquidBottomTabs`**
([LiquidComponents.kt:237–262](../app/src/main/java/com/zack/recomptracker/ui/liquidglass/LiquidComponents.kt)):

```kotlin
Modifier.drawBackdrop(
    backdrop = backdrop,                       // the shared app Backdrop (LocalBackdrop), NOT EmptyBackdrop
    shape = { if (collapsed) Capsule() else RoundedCornerShape(corner) },
    effects = {
        vibrancy()
        blur(8f.dp.toPx())                     // same 8dp as the nav bar (not the old 22dp)
        lens(24f.dp.toPx(), 24f.dp.toPx())     // same refraction/lens as the nav bar
    },
    onDrawSurface = { drawRect(containerColor) }   // ~0.4-alpha neutral wash, same as nav bar
)
// + InteractiveHighlight on press, matching the nav bar
```

Rules:

- **Reuse the same `Backdrop`** the nav bar reads (`LocalBackdrop.current`) so cards refract the
  live `GlassOrbBackground`. The collapsed pill uses `Capsule()` — literally the nav-bar shape.
- Match the nav bar's effect parameters exactly (blur `8dp`, lens `24/24`, the neutral container
  wash). Do not reuse `AiInsightCard`'s heavier `blur(22dp)` stack — retune it to the nav-bar values.
- The `lens()` refraction uses `RuntimeShader` (API 33+) and the library falls back gracefully on
  older devices via `isRuntimeShaderSupported` — this already ships at `minSdk 26` through the nav
  bar, so no new gating is required for the glass material itself.

Net: the card face is **neutral translucent glass that refracts the background** — the same
material the user already sees in the bottom nav.

### 3.2 Apple-Intelligence edge glow (replaces the full iridescent rim)

The full-spectrum **rim border** of the current `AiInsightCard` is replaced by a soft rainbow
**glow that hugs the perimeter** while the glass face stays clean.

- A blurred conic-gradient layer drawn just outside the card edge (the
  `IridescentStops` palette already in
  [IridescentPalette.kt](../app/src/main/java/com/zack/recomptracker/ui/component/IridescentPalette.kt)).
- Compose: draw the gradient into a layer and apply `Modifier.blur` (API 31+) /
  `RenderEffect`; on API 26–30 fall back to a static soft multi-stop glow (no per-frame blur).
- The glow **breathes** slowly (hue rotation, ~16 s cycle, reusing the existing hue-shift
  animation). It is **not** a thick saturated border.
- Respect the reduced-motion guard already in `aiIridescentRim` (system animation scale = 0 →
  static glow).

### 3.3 Glow as a state signal

| Border mode | Glow behaviour |
|---|---|
| `Static` / Ready | Soft, slow breathing (~16 s), low opacity (~0.5) |
| `Preparing` | Gentle pulse |
| `Generating` | Brighter (~0.72) and faster (~7 s) — signals the coach is writing |
| Error | Glow desaturates to a faint neutral/red edge |

---

## 4. Collapsibility (the unifying model)

Every card has two states driven by **one spring** (`animateContentSize` +
`MaterialTheme.motionScheme` spatial spring):

- **Collapsed** = a glass **pill** (`Capsule()` shape): `✦` + label + one-line verdict +
  chevron. This *is* the "small pill" variant — it is not a separate component.
- **Expanded** = full card (`RoundedCornerShape(20.dp)`): verdict headline, evidence /
  metric, action row.

On toggle: shape morphs pill ↔ card, the edge glow's corner radius follows, the chevron
rotates, and the detail block unfolds (`expandVertically` + `fadeIn`).

Collapsed/expanded state is **remembered per insight** (DataStore-backed, keyed by insight
kind) so the user's preference persists across sessions. Default expansion is per-variant
(see §5).

---

## 5. The variant family

All five are the same composable with a `variant` parameter; they differ in default
expansion, prominence, and which slots render.

| # | Variant | Default | When | Trigger |
|---|---|---|---|---|
| 1 | **Hero** | expanded | The single top-ranked dashboard insight | always-on |
| 2 | **Standard** | expanded | Per-screen insight (trend, recovery) | on-visible generation |
| 3 | **Pill** | collapsed | Lightweight nudge tied to a number | tap to expand |
| 4 | **Generating** | n/a | While Gemma writes on-device | transient state |
| 5 | **Carousel** | expanded | Several co-equal ranked facts | swipe (HorizontalPager) |

### 5.1 Hero
Largest verdict type (~20 sp) + the computed **evidence line** (`InsightFact.statement`) +
confidence badge + action row. One per screen, top of the dashboard `LazyColumn`.
(A numeric **key metric + mini sparkline** is deferred — `InsightFact` carries a `statement`
sentence, not structured numbers; see §12.)

### 5.2 Standard
Verdict (~16 sp) + one **evidence line** + confidence badge + actions. The workhorse for
Progress / Recovery / per-screen insights.

### 5.3 Pill
Collapsed by default. `✦` + one-line verdict in a capsule. Tap expands to a Standard-shaped
card with evidence and a single primary action (often a direct log action).

### 5.4 Generating
Glass card with **brighter/faster edge glow** + **shimmer skeleton lines** (animated
`Brush.linearGradient` sweep, ~1.8 s) that resolve into streaming text as tokens land. Colored
shimmer, never a gray spinner. Maps to `AiInsightState.Generating`.

### 5.5 Carousel (deferred)
`HorizontalPager` of Standard cards with a peeking next-card edge + dot pagination, for when
several co-equal facts exist. **Deferred** in the first implementation: `InsightEngine.detectTopFact`
currently returns a single fact, so there is no multi-fact source to drive it yet (see §12).

---

## 6. Shared actions

Rendered per variant in the expanded action row:

| Action | Icon | Behaviour | Status |
|---|---|---|---|
| **Expand / collapse** | chevron | Toggles the collapsed pill ↔ full card | **Shipped** |
| **Refresh** | chip | Re-runs generation (`onRetry`) | **Shipped** |
| **Dismiss** | ✕ | Hides the card for the session; replaced by an inline "Insight dismissed · Undo" row (`rememberSaveable`, survives scroll) | **Shipped** (session-level) |
| **Tell me more** | `✦` chip | Opens coach chat seeded with the insight context | Deferred — hook present (`onTellMeMore`), unwired; needs coach seeding |
| **Feedback** | ♡ | Helpful / not — tunes the top-fact ranking | Deferred — hook present (`onFeedback`), unwired; needs ranking integration |
| **Snooze** | ⏾ | Hides until later; tiered resurface | Deferred — needs durable store |

The component renders an action only when its hook is provided; deferred actions are simply
not passed by the call sites yet. Durable cross-session dismiss persistence and write-style
actions (e.g. "Log yogurt" on a Pill, via the coach confirmation flow) are follow-ups.

---

## 7. Insight type → variant mapping

| Insight kind | Variant | Default state | Notes |
|---|---|---|---|
| `WEEKLY_PATTERN` | Hero | expanded | Dashboard headline; evidence + confidence from `InsightFact` (Carousel deferred, §12) |
| `PROGRESS_TREND` | Standard | expanded | Progress screen |
| `RECOVERY_READINESS` | Standard | expanded | Body / Recovery screen |
| `REST_OF_DAY` | Pill | collapsed | Food log; tap to expand — replaces the "✨ Rest of day?" reveal |
| Weekly briefing | Hero-in-modal | expanded | Keeps `BriefingGlassCard`'s backdrop-free path for dialogs |

Ranking/feedback already exists via the top-fact engine; the Hero vs Carousel choice is driven
by how many co-equal facts `InsightEngine.detectTopFact` surfaces.

---

## 8. States

| State (`AiInsightState`) | Rendering |
|---|---|
| `Generating(partial)` | Generating variant — bright glow + shimmer → streaming text |
| `Ready(text)` | Resolved card; glow settles to slow breathe; actions appear |
| `Error(message)` | Faint neutral/red edge; message + "Try again" |
| Model-lifecycle states (`Disabled`, `ModelMissing`, `Downloading`, …) | Card not shown (unchanged behaviour) |
| No insight (`context == null`) | Card not shown |

---

## 9. Component architecture

Evolve the existing components rather than introduce parallel ones:

- **`AiInsightCard`** ([AiInsightCard.kt](../app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt))
  → align its `drawBackdrop` params to the nav-bar recipe (blur 8 / lens 24); replace the
  rim border with the edge-glow modifier; add `collapsed` + `variant` params.
- **`GeneratedInsightCard`** → becomes the state→content mapper that picks the variant and
  fills the verdict / evidence / metric / action slots.
- **Edge glow** → new `Modifier.aiEdgeGlow(mode, cornerRadius)` replacing
  `Modifier.aiIridescentRim`, reusing `IridescentStops` and the hue animation.
- **Actions, confidence badge, sparkle metric** → small new sub-composables built from
  existing primitives (`VioletBadge`, `LiquidActionButton`, glass chips). No new design tokens.

Reuse mandate: build only from the existing glass component library and `LocalAppAccent` /
`LocalAppColors` tokens. No new color/spacing/typography tokens.

## 10. Accessibility & performance

- The **glass material** itself is handled by the Kyant library (lens/refraction via
  `RuntimeShader`, API 33+, with built-in fallback) and already runs at `minSdk 26` through the
  nav bar — no new gating needed there.
- The **edge glow** is the only new API-sensitive piece: its blur uses `Modifier.blur` /
  `RenderEffect` (API 31+), so it must branch — a static soft multi-stop glow below API 31. No
  per-frame bitmap blur.
- Limit infinite animations: only the **visible** card animates its glow; collapsed pills and
  off-screen cards hold a static glow. Keep glow/shimmer in the **draw phase**
  (`drawWithCache`), never read animation state in composition.
- Semantics: `mergeDescendants`, `contentDescription = "AI insight: …"`, decorative glow /
  sparkle marked `null`. Maintain WCAG AA contrast over the translucent surface (text sits on
  the ~0.40-alpha neutral wash — the nav-bar value from §3.1 — not on the gradient).
- Interactive affordances carry `Role.Button`, action labels (`onClickLabel`), and a 48 dp
  minimum touch target (`minimumInteractiveComponentSize`).
- Honour the system reduced-motion setting (existing guard).

## 11. Resolved decisions

- **Confidence badge** → derived from `InsightFact.priority` via `confidenceFrom`: `>= 30 → High`,
  `1..29 → Medium`, `<= 0 → null` (no badge). Thresholds match the real detector priority scale
  (~10–45). No new scoring model.
- **Dismiss** → shipped as **session-level** with an inline "Insight dismissed · Undo" affordance
  (`DismissibleInsight`, `rememberSaveable` so it survives list scroll; resets on navigation).
  Durable cross-session persistence is a follow-up (§12).
- **Snooze** → not implemented in this redesign; deferred (§12).

## 12. Out of scope / deferred follow-ups

Out of scope entirely: insight generation pipeline, coach chat UI, model download/lifecycle,
2B prompt/microcopy tuning. Presentation layer only.

Deferred follow-ups (the component supports them via optional hooks where noted):
- **Carousel variant + Hero numeric metric/sparkline** — need `InsightEngine` to expose multiple
  ranked facts and structured numbers (today `detectTopFact` returns one `statement` sentence).
- **"Tell me more" → coach** — `onTellMeMore` hook present, unwired; needs coach-chat seeding.
- **Feedback (♡)** — `onFeedback` hook present, unwired; needs top-fact ranking integration.
- **Snooze** + **durable cross-session dismiss persistence** — need a DataStore store keyed by
  insight kind (with expiry for snooze).
