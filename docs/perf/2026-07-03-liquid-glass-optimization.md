# Liquid Glass Performance Optimization

Branch: `perf/liquid-glass-optimization` (6 commits on top of `develop` @ a885e65).
Goal: improve smoothness/responsiveness — especially scroll jank from heavy Liquid Glass usage —
**without changing behavior or the visual identity**.

## How the evidence was gathered

Three independent read-only analysis passes before any change:

1. **Usage census** — every glass/liquid call site, classified static vs repeated-in-list, per screen.
2. **Compose audit** — recomposition, lazy-list hygiene, state plumbing, startup path, draw allocations.
3. **Library cost model** — the Kyant `backdrop:2.0.0` sources were unpacked and read; every claim
   below about per-frame mechanics is verified against `DrawBackdropModifier.kt` / effect sources,
   not guessed.

### The cost model in one paragraph

Every `drawBackdrop` node owns an offscreen `GraphicsLayer` that is re-recorded and re-effected
(blur/vibrancy/lens are GPU passes) whenever it invalidates — which on a position-dependent
`LayerBackdrop` means **every scroll frame**. The `highlight` and `shadow` parameters silently
default ON, each adding *another* per-frame GraphicsLayer. Blur radius inflates the offscreen
target by the radius on each side. The app was already well-architected in one key way: cards blur
a **static** background capture (`contentBackdrop` = the orb image only), so a frosted card's
blurred output is visually near-constant — which is exactly why an opaque look-alike ("lite") can
match it.

## What changed

### Glass component library (`perf(glass)`)
- `FrostedCard` / `TintedCard` / `GlassSpeechBubble`: dropped the library's default
  highlight+shadow layers (2 extra GraphicsLayers per card per frame). The cards' own hairline
  border + top sheen keep the edge treatment — visually indistinguishable.
- `TintedCard` blur 20dp → 12dp (matches FrostedCard; imperceptible over the soft static backdrop).
- New `lite: Boolean = false` mode on `FrostedCard`, `TintedCard`, `AiInsightCard`,
  `LiquidGlassButton` (+ Primary/Secondary/Action wrappers): same tokens
  (`frostedSurfaceFallback` — the app's pre-existing non-blur frost approximation), same border,
  sheen, shape, typography and press-scale, **zero** backdrop cost. Applied only to
  repeated-in-list call sites.
- `aiEdgeGlow`: the 16s infinite hue loop now runs **only** while Generating/Preparing. Resting
  (Ready/Static) AI cards draw a static glow once and stop invalidating — previously every visible
  AI card repainted at 60fps forever, allocating 3 IntArrays + 3 SweepGradients per frame. The
  animated hue is read in the draw phase, so even active cards invalidate drawing only.

### Screen call sites (`perf(coach)`, `perf(train)`, `perf(food,dashboard,body)`)
- **Coach chat (worst path in the app):** ~10 simultaneous full-glass assistant bubbles, each with
  its own infinite glow, on a scrolling tab → history bubbles are now lite (still glowing, still
  bordered — the glow is static at rest); only the **live streaming bubble** keeps premium glass.
  Chat list got stable keys + contentType.
- **MarkdownText streaming:** parsing (blocks *and* inline styling) is fully memoized; a growing
  message re-parses only the tail after the last safe blank-line boundary (code-fence parity
  tracked). Previously the entire accumulated string re-parsed on every streamed token.
- **Active session:** the hidden swipe-to-delete pill (one per set row, ~24 latent glass buttons
  per session) is composed only while actually revealing, and is lite. Exercise cards lite.
  `sessionRows` remembered per emission; RIR expansion reads per-row (one row's toggle no longer
  recomposes siblings); `contentType` added.
- **Train home / session detail / exercise picker:** repeated routine/history/exercise/result
  cards lite; the picker's singleton sticky "Add" button stays premium. Formatters hoisted to
  file-level constants.
- **Food log:** per-slot Add buttons lite (slot cards were already cheap). Gradient hoists.
- **Dashboard:** the app's only `Modifier.blur` (a live 26dp pass for the Weekly-Review glow, on
  the landing tab) replaced with a remembered radial gradient in the file's own orb idiom.
- **Body history:** per-day action buttons lite.
- **Charts:** dash `PathEffect`, day labels, gradient colour lists hoisted out of per-frame scope.
- **RoutineBuilder:** target edits update only the touched exercise/set, preserving reference
  identity of everything else (was a full nested rebuild per keystroke).

### Main thread & startup (`perf(vm)`, `perf(startup)`)
- 7 combine-based ViewModels (Dashboard, Progress, Today, FoodLog, Train, Streak, FoodLibrary) now
  run per-emission CPU work (grouping, macro summing, date parsing, stats builders) on an
  injectable `computeDispatcher` (default `Dispatchers.Default`) instead of the main thread.
  ProgressViewModel also stops computing `macroTotals` 4–5× per day.
- The 116KB knowledge corpus no longer parses synchronously in `AppContainer`'s constructor on the
  main thread at cold start — it loads on IO behind a delegate-swapping injector (the code's own
  TODO, same pattern as the exercise library). Behavior unchanged; consumers only read it at
  generation time.
- `:app` is now wired as a **baseline profile consumer** of the existing `:macrobenchmark`
  generator (`automaticGenerationDuringBuild = false` — normal/CI builds stay device-free).

## Why it should feel smoother

- **Scrolling** any list-heavy screen (Coach chat, Train lists, session logging, exercise picker):
  each visible row previously cost 3–4 offscreen GraphicsLayer re-records + a blur pass per frame;
  now repeated rows cost a plain draw. Hero surfaces that kept real glass cost roughly half their
  former layers (highlight/shadow removal).
- **Idle**: screens with a resting AI card (Dashboard, Coach, Body, Progress, Train) no longer
  redraw at 60fps.
- **Typing** (workout logging, routine builder): keystrokes now invalidate one row / one set
  instead of whole trees.
- **Chat streaming**: per-token work went from O(message length) parsing + N glass bubbles to
  tail-only parsing + 1 glass bubble.
- **Anything that logs data while a screen observes Room**: state rebuilds happen off-main.
- **Cold start**: no 116KB JSON parse on the main thread; baseline profile (once generated)
  AOT-compiles the hot paths.

## Deliberately NOT changed (visual identity)

- Nav bar: full premium glass, untouched — including the "invisible" accent row, which analysis
  proved is sampled at rest by the tab indicator (gating it would visibly change the selected tab).
- Dashboard hero cards, AI insight/suggestion cards, the live chat bubble, pickers' sticky action,
  onboarding, profile/plan/stats cards: full premium glass.
- Slider/toggle/nav-indicator chromatic dispersion (drag-only, small pixels, premium feel): kept.
- `GlassBottomSheet` (already a cheap tinted solid), `AuroraBackground` (already disabled — do NOT
  re-enable it naively: its per-frame loop would force every glass consumer to re-blur every frame).

## Tradeoffs

- Lite surfaces don't live-blur what's behind them. Over the static background this is
  near-invisible; the one place it could be noticed is content scrolling *under* a lite row —
  which doesn't happen for these call sites (rows scroll *with* their content).
- Resting AI cards no longer slowly shift hue (they hold the canonical accent-led palette;
  animation returns while generating).
- `ChatMessage` gained a per-instance id outside its value semantics (equality/copy unchanged).

## Verification

- `./gradlew :app:compileDebugKotlin` + `:app:testDebugUnitTest`: green — 1113 tests, the only
  failure is the pre-existing, environment-dependent `ai.harness.InsightHarnessTest` (OpenRouter
  HTTP 429 rate limit), which also failed on the unmodified baseline.
- New tests: markdown split invariants (9), RoutineBuilder reference-identity (2), ChatMessage id (2).
- An independent full-diff code review was run before merge readiness.
- **On-device visual + feel verification is pending** (owner does this): check FrostedCard-lite vs
  glass side by side (Train lists vs Dashboard cards), chat scroll, workout typing, nav feel.

## Follow-ups (need a real device)

1. **Generate the baseline profile** (biggest remaining startup win, ~zero risk):
   `./gradlew :app:generateReleaseBaselineProfile` with a device/emulator connected, then commit
   the generated profile.
2. Profile-then-decide candidates (mechanically sound, need frame traces to justify):
   `CanvasBackdrop` instead of `LayerBackdrop` for the static orb capture; dropping chromatic
   aberration (7× shader samples) on slider/toggle/nav-indicator drags; FoodsScreen's eagerly
   composed saved-foods rows (real cost only for large libraries).
3. Perfetto/Macrobenchmark scroll traces on release builds for before/after numbers — all changes
   here are mechanism-verified but not device-measured.
