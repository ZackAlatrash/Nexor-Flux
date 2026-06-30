# 02 — UI / Design System Consistency

**Scope:** Token/component **conformance only**. The design system in `docs/design-system.md` is
good; the problem is patchy **adherence**. This plan does **not** redesign any screen, change any
layout, or alter behavior — it makes existing screens use the tokens and components the system
already defines, and adds the few missing tokens that are *causing* the drift.

All counts below were re-verified by grep against `app/src/main/java` on branch `develop`
(2026-06-30). Paths are relative to repo root `/Users/zackalatrash/Desktop/Personal Dietitian`.

---

## 1. Current state & problems

### Verified counts (app-wide, `app/src/main/java`)

| Violation | Count | Grep |
|---|---:|---|
| Inline `fontSize =` | **169** | `grep -rn --include=*.kt "fontSize =" app/src/main/java` |
| Inline `fontWeight =` | **175** | `grep -rn --include=*.kt "fontWeight ="` |
| `OutlinedTextField` (should be `GlassInputField`/`GlassTextArea`) | **24** | `grep -rn --include=*.kt "OutlinedTextField"` |
| Material `Button(` (should be `Liquid*`) | **2** | `grep -rn --include=*.kt -E "[ (]Button\("` |
| `Surface(`-as-bubble | **1** | `CoachScreen.kt:578` |
| Legacy `TopAppBar`/`TopAppBarDefaults` imports | **4 imports / 2 files** | `grep -rn --include=*.kt "TopAppBar"` |
| Hardcoded `Color(0x…)` literals | **164** | `grep -rn --include=*.kt "Color(0x"` |
| `.border(` calls | 94 | (subset are bespoke cards) |

> Note: the raw `.background(` count is **202** and `.border(` is **94**, but most are legitimate
> (chips, pills, bars, icon circles, the glass component internals themselves). The "bespoke card"
> problem is the subset of `.clip().background().border()` **card-shaped containers** that should be
> `NeutralCard`/`FrostedCard` — concentrated in the files below, not all 202.

### Worst offenders — `fontSize` (re-grepped, corrects the original audit)

The original audit under-counted. The actual top files:

| File | `fontSize` | `fontWeight` |
|---|---:|---:|
| `ui/train/component/SetGrid.kt` | **23** | **22** |
| `ui/aicoach/AiCoachComponents.kt` | **20** | 5 |
| `ui/component/GlassComponents.kt` | 14 | 8 |
| `ui/theme/Typography.kt` | 13 | 13 | *(legitimate — this is the token definition)* |
| `ui/today/FoodScreen.kt` | 11 | 13 |
| `ui/component/GeneratedInsightCard.kt` | 11 | 3 |
| `ui/train/ExerciseDetailSheet.kt` | 10 | 8 |
| `ui/foodlibrary/FoodLibraryScreen.kt` | 10 | 11 |
| `ui/component/MarkdownText.kt` | 7 | 4 |
| `ui/component/CalorieZoneBar.kt` | 6 | 3 |
| `ui/onboarding/OnboardingScreen.kt` | 5 | 3 |
| `ui/RecompApp.kt` | 5 | 5 |
| `ui/review/WeeklyBriefingOverlay.kt` | 1 | 10 |
| `ui/today/BodyRecoveryScreen.kt` | 2 | 8 |
| `ui/toast/ToastOverlay.kt` | 3 | 3 |

`Typography.kt`'s 13/13 are the *token definitions themselves* and must stay raw — exclude it from
the sweep and from any guardrail.

### Concrete confirmed violations

- **Nav tab labels** — `ui/RecompApp.kt:225,244,263,282,301`: five literal
  `Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Medium, …)` rows. No matching `AppType` token exists.
- **Toast text** — `ui/toast/ToastOverlay.kt:126,132,140`: `13.sp Bold`, `13.sp Medium`, `12.sp SemiBold`. No token.
- **Input field label** — `ui/component/GlassComponents.kt:354,383`: `8/9.sp` (the labelled-input UPPERCASE label). No token.
- **Slider range caption** — `ui/component/GlassComponents.kt:483,484,485`: `8.sp` range end labels. No token.
- **Hardcoded error hex** — `ui/toast/ToastOverlay.kt:95` `Color(0x2EFB7185)`, `:100` `Color(0x66FB7185)`. No `errorRed`/`danger` token exists in `AppColors`.
- **Material Button** — `ui/aicoach/AiCoachComponents.kt:85,123`: two `Button(onClick = onDownload)`. (DashboardScreen/FoodLibraryScreen from the original audit did **not** verify — they have no `Button(`; the real second offender is the second instance in AiCoachComponents.)
- **Surface-as-bubble** — `ui/coach/CoachScreen.kt:578`: `Surface(` used as a chat bubble container.
- **`OutlinedTextField`** — `FoodLibraryScreen.kt` (6), `CoachScreen.kt` (4), `FoodScreen.kt` (3), `component/Components.kt` (3), `ExercisePickerScreen.kt` (2), `RecipeBuilderScreen.kt` (2), `PlanGenerationDialogs.kt` (2). `Theme.kt:1` and `FoodsScreen.kt:1` are import-only / colors-config — verify before touching.
- **Legacy imports** — `ui/body/BodyEditScreen.kt:18,19` and `ui/body/BodyHistoryScreen.kt:25,26` import `TopAppBar`/`TopAppBarDefaults` but **never call them** (zero `TopAppBar(` usages app-wide). Dead imports — safe delete.

### Root cause

The four worst-hit areas (nav labels, toast, input label, slider caption) have **no `AppType` token
that fits**, so contributors hardcoded. **The gaps are the cause of the drift.** Add the tokens
first or the sweep will have nowhere to point.

---

## 2. UX improvements

Consistency the user actually feels — no new features, just uniformity:

- **One type scale everywhere.** Nav labels, toasts, input labels, and slider captions currently
  drift by 1–2sp and by weight between screens. After the sweep, every "small label" is the same
  pixel size and weight on every screen — the app reads as one product, not several.
- **Uniform inputs.** Replacing the 24 `OutlinedTextField`s with `GlassInputField`/`GlassTextArea`
  gives every text field the same height, corner radius, label treatment, focus color, and cursor —
  so the food search box, the coach composer, and the recipe builder all feel identical to type in.
- **Uniform buttons.** Swapping the Material `Button`s and the `Surface` bubble for the `Liquid*`
  family makes every tappable primary/secondary action share the same height, press feedback, and
  glass material — no stray Material ripple-on-grey button in the AI download card.
- **Consistent error color.** A single `errorRed` token means every error state (toast, future
  validation) is the exact same red, instead of one-off hex tints that can drift.

These are low-risk because pixel sizes/roles are preserved — we map each hardcoded value to the
nearest existing/added token of the same role, not to a "nicer" value.

---

## 3. UI improvements (core of this plan)

Execute in this dependency order.

### (a) Add the missing tokens FIRST

**`ui/theme/Typography.kt` — add to `object AppType`:**

| New token | Size / weight | Replaces hardcoded | Used at |
|---|---|---|---|
| `navLabel` | `10.sp, Medium` | nav tab labels | `RecompApp.kt:225,244,263,282,301` |
| `toastTitle` | `13.sp, Bold` | toast title | `ToastOverlay.kt:126` |
| `toastBody` | `13.sp, Medium` | toast body | `ToastOverlay.kt:132` |
| `toastAction` | `12.sp, SemiBold` | toast action label | `ToastOverlay.kt:140` |
| `inputLabel` | `9.sp, Bold, 0.14 spacing` (UPPERCASE applied at call site, like `sectionLabel`) | `GlassInputField` label | `GlassComponents.kt:354,383` |
| `captionTiny` | `8.sp, Normal` | slider range end caption | `GlassComponents.kt:483–485` |

> Reuse where possible instead of inventing: the toast title/body are both `13.sp` and could map to
> `body`/`label` if weight is acceptable — but toast weights are load-bearing (Bold/Medium), so
> distinct `toast*` tokens are cleaner and keep the toast self-documenting. `inputLabel` and
> `sectionLabel` share `9.sp Bold 0.14` — consider making `inputLabel = sectionLabel` (alias) rather
> than a second TextStyle, unless the input label is meant to diverge later. Decide at implementation
> time; document the choice in the token's KDoc.

Then update `docs/design-system.md`'s `AppType` table to list the six new tokens so the doc stays
the source of truth.

**`ui/theme/AppColors.kt` — add a semantic error token:**

- Add `val errorRed: Color` (and `val errorRedSurface: Color` for the faint tint) to the `AppColors`
  data class, populate in both `Dark` and `Light` companions. Base it on the existing
  `0xFFFB7185` family already used inline (`0x2E…` ≈ surface tint, `0x66…` ≈ border) so the visual
  result is unchanged.
- This also gives `DangerCard` (currently an inline red per the design doc) a token to consume —
  a small future cleanup, not required now.

### (b) Sweep file-by-file replacing inline type

For each file in the worst-offenders table, replace every `fontSize = X.sp, fontWeight = Y` on a
`Text` with the nearest `AppType.<token>` of the **same role** (per the design-system "pick the
role, not the pixel" rule). Pass `color = appColors.<token>` separately. **Never** leave a bare
`fontSize`/`fontWeight` next to a `style =`. Exclude `Typography.kt` (the definitions).

Where a weight is genuinely active-state load-bearing (e.g. selected tab), use
`AppType.<token>.copy(fontWeight = …)` rather than a bare arg.

### (c) Component swaps

- **`OutlinedTextField` → `GlassInputField`/`GlassTextArea`** (24 occurrences): `FoodLibraryScreen`
  (6), `CoachScreen` (4), `FoodScreen` (3), `component/Components.kt` (3), `ExercisePickerScreen`
  (2), `RecipeBuilderScreen` (2), `PlanGenerationDialogs` (2). Verify `Theme.kt`/`FoodsScreen.kt`
  hits are import/config-only before touching. Multi-line fields → `GlassTextArea`; single-line →
  `GlassInputField`. Suffixed/readonly number fields that don't map cleanly may stay Material (the
  design doc explicitly allows this) — note any that are intentionally left.
- **Material `Button` → `Liquid*`** (`AiCoachComponents.kt:85,123`): full-width → `LiquidPrimaryButton`;
  inline → `LiquidActionButton`. Match the existing label/onClick.
- **`Surface`-as-bubble → glass** (`CoachScreen.kt:578`): replace with `FrostedCard`/`NeutralCard`
  (or `TintedCard` if it's the AI side of the chat).
- **Bespoke `.clip().background().border()` cards → `NeutralCard`/`FrostedCard`**: target the
  card-shaped containers in `BodyRecoveryScreen.kt`, `WeeklyBriefingOverlay.kt`, and the chart
  wrappers. Do **not** blindly convert every `.background()` — chips, pills, bars, and icon circles
  stay as-is. Convert only containers that are visually a card.

### (d) Remove legacy imports

Delete the four dead imports: `BodyEditScreen.kt:18,19` and `BodyHistoryScreen.kt:25,26`
(`TopAppBar`, `TopAppBarDefaults`). No usages exist, so it's a pure import cleanup that lets the
guardrail (Section 8) stay green.

---

## 4. Data / model improvements

Minimal — this is a UI conformance plan. The only structural cleanup:

- **`AppColors` gains a semantic `errorRed`/`errorRedSurface`** (Section 3a) so the error red has a
  single source of truth instead of being a hex literal. This is the one "model" change.
- Optionally fold the `DangerCard` red and any other one-off red hex into the same token in a later
  pass (out of scope for the first pass, noted for completeness).

No Room/DataStore/schema changes. No domain changes.

---

## 5. AI opportunities

**N/A / minor.** This is mechanical token/component conformance — not an AI-suited task and not
AI-feature work. The only AI-adjacent surfaces touched are *UI* of AI features
(`AiCoachComponents.kt` Material buttons, `CoachScreen.kt` bubble, `GeneratedInsightCard.kt`
inline type, AI cards using `TintedCard`); the on-device Gemma coach logic is untouched. One minor
note: a subagent could mechanically apply the per-file type sweep, but each change needs a build +
visual check, so human-reviewed batches are safer than a blind AI rewrite.

---

## 6. Quick wins (do first, hour-scale)

1. **Add the 6 `AppType` tokens** (Section 3a) — unblocks everything else.
2. **Add `errorRed`/`errorRedSurface` to `AppColors`** (Dark + Light).
3. **Fix the 2 hex error tints** — `ToastOverlay.kt:95,100` → `appColors.errorRedSurface` / border token.
4. **Fix the 2 Material `Button`s** — `AiCoachComponents.kt:85,123` → `Liquid*`.
5. **Fix the 5 nav labels** — `RecompApp.kt:225,244,263,282,301` → `AppType.navLabel`.
6. **Delete 4 dead `TopAppBar` imports** — `BodyEditScreen.kt`, `BodyHistoryScreen.kt`.
7. **Convert the 3 toast `Text`s** — `ToastOverlay.kt:126,132,140` → `toastTitle`/`toastBody`/`toastAction`.

Each is independent and individually verifiable; ship as one small PR after the tokens land.

---

## 7. Medium improvements (day-scale)

- **Per-screen typography sweep** (Section 3b), grouped by area so each PR is one coherent surface:
  - **Train**: `SetGrid.kt` (23+22 — the single biggest file), `ExerciseDetailSheet.kt`,
    `SessionSummaryScreen.kt`, `ActiveSessionScreen.kt`, `ExerciseCard.kt`, `SessionDetailScreen.kt`, `BodyMap.kt`.
  - **Food/Log**: `FoodScreen.kt`, `FoodLibraryScreen.kt`, `BodyRecoveryScreen.kt`, `CalorieZoneBar.kt`, `WeekCalorieStrip.kt`.
  - **AI/Coach**: `AiCoachComponents.kt`, `GeneratedInsightCard.kt`, `MarkdownText.kt`, `CoachScreen.kt`.
  - **Shared components**: `GlassComponents.kt`, `Components.kt`, `LiquidComponents.kt` (these define
    components, so use the new `inputLabel`/`captionTiny` tokens; be careful not to break component internals).
  - **Onboarding/Dashboard/Review/Profile/Plan/Recipes**: remaining smaller files.
- **`OutlinedTextField` → `GlassInputField`/`GlassTextArea`** swap (Section 3c), batched per area
  alongside that area's type sweep.
- **Bespoke card → `NeutralCard`/`FrostedCard`** conversions (Section 3c) in `BodyRecoveryScreen`,
  `WeeklyBriefingOverlay`, charts.

Verify each screen in the running app after its PR (per project workflow: build one area, hand off
for visual check, then the next).

---

## 8. Bigger refactor — a guardrail so drift can't return

Add a CI check that **fails the build** on bare `fontSize`/`fontWeight`/`Color(0x…)` in `ui/`
(excluding the token definition files). Two viable forms:

- **Lightweight unit test** (preferred — no new tooling, runs in `:app:testDebugUnitTest`): a JVM
  test that walks `app/src/main/java/com/zack/recomptracker/ui/**.kt`, skips an allowlist
  (`theme/Typography.kt`, `theme/AppColors.kt`, `theme/Theme.kt`, and any file with a
  `// design-system-exempt` marker), and asserts zero matches of `fontSize =` / `fontWeight =` /
  `Color(0x`. Seed the allowlist with the *current* offender list and ratchet it down as the sweep
  lands, so the bar only ever tightens. Fails with a clear message pointing at the file:line and the
  token to use.
- **detekt custom rule** (heavier): a `ForbiddenInlineTextStyle` rule on the same patterns, wired
  into the existing Gradle check. Use only if detekt is already (or about to be) in the build;
  otherwise the unit test is lower-friction.

Either way: the guardrail goes in **last**, after the sweep, seeded so it's green on day one, then
the allowlist is emptied. Document the exemption marker in `docs/design-system.md`.

---

## 9. What to avoid for now

- **No redesigns.** Don't change visual hierarchy, spacing, or layout. Map each hardcoded value to
  the same-role token; if a value looks "wrong," leave it and note it — fixing it is a separate
  design decision.
- **No behavior changes.** Swapping `OutlinedTextField`→`GlassInputField` must preserve
  value/onValueChange/imeAction/keyboardType/singleLine. Swapping `Button`→`Liquid*` must preserve
  onClick/enabled.
- **Don't convert non-card `.background()`/`.border()`.** Chips, pills, bars, progress fills, and
  icon circles are not cards — leave them. Only convert card-shaped containers.
- **Don't touch `Typography.kt`'s raw values** — they are the definitions.
- **Don't force Material→Glass where the design doc explicitly allows Material** (complex readonly
  dropdowns, suffixed number fields). Note each intentional exception.
- **No new components.** Reuse the existing glass library (per project convention); only add the six
  tokens and one color — nothing else new.

---

## 10. Suggested implementation order

1. **Tokens first** — add 6 `AppType` tokens + `errorRed`/`errorRedSurface` to `AppColors`; update
   `docs/design-system.md` tables. *(PR 1 — unblocks everything.)*
2. **Quick wins** — hex error fix, 2 Material buttons, 5 nav labels, 4 dead imports, 3 toast texts.
   *(PR 2 — small, high-signal, fully covered by the new tokens.)*
3. **Screen-by-screen sweep, grouped by area** (verify each area in-app before the next):
   - PR 3 — **Train** (start with `SetGrid.kt`, the worst file).
   - PR 4 — **Food/Log** (+ `OutlinedTextField` swaps + bespoke-card conversions here).
   - PR 5 — **AI/Coach** (+ `Surface` bubble fix + `OutlinedTextField` swaps).
   - PR 6 — **Shared components** (`GlassComponents`, `Components`, `LiquidComponents`).
   - PR 7 — **Onboarding/Dashboard/Review/Profile/Plan/Recipes** remainder.
4. **Guardrail last** — add the unit test (or detekt rule), seeded green, then empty the allowlist as
   the final commit. *(PR 8 — locks the gains in.)*

Build (`./gradlew :app:compileDebugKotlin`) after every file; run the area in the app before moving
on; the guardrail test gives the objective "done" signal at the end.
