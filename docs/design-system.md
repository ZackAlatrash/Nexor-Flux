# Design System

How every screen in this app is built. **Read this before creating or changing any UI.**
The goal is that screens look like one app: same titles, spacing, cards, buttons, and text
sizes everywhere. Don't hand-roll what a token or component already provides.

## Principles

1. **Never hardcode `fontSize`/`fontWeight`/`letterSpacing` on a `Text`.** Use a named
   `AppType` style. If you're typing `fontSize = 14.sp`, you're doing it wrong.
2. **Two header tiers, no third.** Top-level tab destinations get the big `ScreenHeader`;
   pushed sub-screens get the compact `SubScreenHeader`. Never invent a bespoke header.
3. **One card family, one button family.** `FrostedCard`/`NeutralCard`/`TintedCard` for cards,
   the `Liquid*Button` family for buttons. Don't build raw `.background().border()` cards or
   use Material `Button`/`Surface` buttons.
4. **One gutter: 16dp.** Screen content sits at `ScreenPaddingH` (16dp). Don't use 14/20dp
   gutters.
5. **Color is passed at the call site, never baked into a style.** `AppType` styles carry
   size/weight/spacing only; pass `color = appColors.<token>` separately (it's theme-dependent).

## Typography — `AppType` (`ui/theme/Typography.kt`)

Usage: `Text(text = x, style = AppType.cardTitle, color = appColors.textPrimary)`.

| Token | Size / weight | Use for |
|---|---|---|
| `screenTitle` | 28 ExtraBold, -0.8 | tier-1 tab-destination titles (via `ScreenHeader`) |
| `screenTitleCompact` | 20 Bold, -0.4 | tier-2 sub-screen titles (via `SubScreenHeader`) |
| `screenSubtitle` | 13 Normal | header subtitle / supporting line |
| `sectionLabel` | 9 Bold, 0.14, UPPERCASE | section headers (via `SectionLabel`) |
| `cardTitle` | 15 SemiBold | card / list-row titles |
| `cardSubtitle` | 12 Normal | secondary / supporting row text |
| `body` | 13 Normal | body copy, chat text |
| `label` | 11 Medium | small inline labels |
| `metaLabel` | 9 Bold, 0.4, UPPERCASE | tile / stat captions |
| `displayHero` | 44 ExtraBold, -1.0 | the single biggest number (onboarding result) |
| `displayLarge` | 36 ExtraBold, -0.5 | hero metric numbers (calorie, body metric, verdict) |
| `statValue` | 22 Bold, -0.3 | tile / summary big numbers |
| `statValueSmall` | 17 Bold | compact stat values |

**Picking a token:** match the role, not the old pixel value. Row title → `cardTitle`. Caption
→ `metaLabel`. Big number → `statValue`/`displayLarge`. When a weight is genuinely load-bearing
(an *active* control state, a button label), use `AppType.<token>.copy(fontWeight = …)` — but
prefer the plain token so labels match across screens. **Never** leave a bare `fontSize` or
`fontWeight` next to a `style =` (the bare arg silently overrides the token).

## Color — `LocalAppColors` (`ui/theme/AppColors.kt`)

Read `val appColors = LocalAppColors.current` and pass the semantic token as the `color`:
`textPrimary`, `textSecondary`, `textMuted`, `textDim`, `textFaint`, `textVeryMuted`,
`cardSurface`, `cardBorder`, `frostedSurface`, `frostedBorder`, … Accent colors come from
`LocalAppAccent.current` (`accent`, `accentLight`, `inkLight`, `onAccent`, `tintedSurface`,
`tintedBorder`). Never hardcode a hex color (e.g. `Color(0xFF6b7280)`) — use a token.

## Spacing & layout — `ui/theme/DesignTokens.kt`

| Token | Value | Use |
|---|---|---|
| `ScreenPaddingH` | 16.dp | horizontal gutter for all screen content |
| `ScreenSpacing` | 10.dp | vertical gap between cards/sections |
| `Spacing.xs/sm/md/lg/xl` | 4/8/12/16/20.dp | in-card / inline spacing |
| `CornerSmall/Card/Chip/Pill` | 10/16/20/100.dp | corner radii (inputs / cards / chips / glass) |

Interior insets *inside* a chip, text field, or card stay as the component defines them — the
16dp rule is for screen-level gutters, not for tightening a pill's internal padding.

## Screen anatomy — `ui/component/ScreenScaffold.kt`

- **`ScreenScaffold(withNavBarInset, content)`** — the standard screen frame: a `LazyColumn`
  with the 16dp gutter, 10dp section spacing, and a bottom inset that clears the floating nav
  (`withNavBarInset = true` for tab destinations; `false` for pushed screens).
- **`ScreenHeader(title, subtitle?, onBack?, trailing?)`** — **tier-1**, big 28sp title. Use for
  the bottom-nav tab destinations (Home/Dashboard, Body, Coach, Train, More, Food Log). Put an
  avatar/action/badge in `trailing`. A hub tab reached by a push (e.g. More) may pass `onBack`.
- **`SubScreenHeader(title, onBack, subtitle?, trailing?)`** — **tier-2**, compact 20sp title +
  the standard 40dp circular back button. Use for every pushed sub-screen (Profile, Plan,
  Session detail, Body edit/history, Food library, …).
- **`BackButton(onClick)`** — the canonical 40dp circular back button, if you need it standalone
  (e.g. on a full-screen camera overlay).

When a header sits inside a `LazyColumn` that has no horizontal `contentPadding`, wrap it in
`Modifier.padding(horizontal = 16.dp)`. Inside `ScreenScaffold` (which pads 16dp) it needs no
wrap.

**Which tier?** If the screen has a back button / is pushed onto the back stack → `SubScreenHeader`.
If it's a bottom-nav tab → `ScreenHeader`. Modal editors with a Close (✕) + Save cluster keep
their custom header but use `AppType.screenTitleCompact` for the title.

**Date in the header?** Only on **day-scoped** screens whose content *is* today's data
(Dashboard, Body, Food Log) — pass it as the `subtitle`. Hubs and tools (Train, More, Coach)
get no date.

## Cards — `ui/component/GlassComponents.kt`

- **`FrostedCard(contentPadding, surfaceTint?, borderColor?)`** — primary / featured data cards,
  charts, hero tiles. The default.
- **`NeutralCard`** — quieter list rows, menus, form containers.
- **`TintedCard`** — AI features only (accent-tinted glass).
- **`DangerCard`** (DataBackup) — destructive actions (red tint). Keep for that semantic.

Don't build a raw `Column.clip().background().border()` card — use these. A zero-padding grouped
*list* container (rows that pad themselves) is the one exception.

## Buttons — `ui/liquidglass/LiquidComponents.kt`

- **`LiquidPrimaryButton(text, onClick)`** — full-width primary action (accent glass).
- **`LiquidSecondaryButton(text, onClick)`** — full-width secondary (clear glass).
- **`LiquidActionButton(text, onClick, isPrimary, small)`** — compact inline actions / pairs.
- **`LiquidStepButton(symbol, …)`** — +/- steppers.

Standard heights: full-width primary **48dp**, compact **36dp**, small **32dp**. Never use a
Material `Button` or a `Surface`-as-button.

## Inputs — `ui/component/GlassComponents.kt`

`GlassInputField` (labelled single-line), `GlassTextArea`, `ScoreStepper`,
`VioletToggle`. Prefer these over `OutlinedTextField`/`Switch` for visual consistency. (Complex
readonly dropdowns / suffixed number fields may stay Material where a clean swap isn't possible.)

## Bottom sheets & toggles

- **`GlassBottomSheet(onDismiss, sheetState?, content)`** — the app-themed bottom sheet. Wraps
  Material `ModalBottomSheet` (keeps drag-to-dismiss / scrim / animation) but skins it as frosted
  glass: transparent Material container, the app `scrim`, a frosted surface with a hairline top
  border, 26dp rounded top corners, and a slim grab handle. **Never use a bare `ModalBottomSheet`**
  — use this so popups match `FrostedCard`.
- **`GlassSegmentedToggle(options, selectedIndex, onSelect)`** — glass pill toggle; the
  replacement for Material `SegmentedButton`.
- For an editable amount with +/- steppers use `AmountStepper`; for macro preview tiles use
  `AmountPreviewStat`.

## Section labels & icons

- **Section headers** → always `SectionLabel("My section")` (it uppercases for you). Never an
  inline styled `Text` for a section heading.
- **Icons** → Material icons for affordances (back, chevron `Icons.AutoMirrored.Filled.KeyboardArrowRight`,
  add, close, edit). **Don't** use text glyphs (`›`, `→`, `✕`, `✓`) as tap affordances. Emoji is
  fine when it's genuine *content* (a food icon, a recovery glyph), not a control.

## Do / Don't

```kotlin
// ❌ Don't — inline type, raw card, hardcoded gutter, glyph affordance
Column(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp))
    .background(appColors.cardSurface).border(1.dp, appColors.cardBorder, …)) {
    Text("Calories", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = appColors.textPrimary)
    Text("›", fontSize = 16.sp)
}

// ✅ Do — tokens + components
NeutralCard {
    Text("Calories", style = AppType.cardTitle, color = appColors.textPrimary)
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = appColors.textVeryMuted,
        modifier = Modifier.size(20.dp))
}
```

```kotlin
// ❌ Don't — Material TopAppBar / bespoke back button
Scaffold(topBar = { TopAppBar(title = { Text("History") }, navigationIcon = { … }) }) { … }

// ✅ Do — tier-2 header
ScreenScaffold(withNavBarInset = false) {
    item { SubScreenHeader(title = "History", onBack = onBack) }
    …
}
```
