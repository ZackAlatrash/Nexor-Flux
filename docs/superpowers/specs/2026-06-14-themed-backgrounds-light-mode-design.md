# Themed Backgrounds + Light Mode — Design

**Branch:** `feat/themed-backgrounds-light-mode`
**Date:** 2026-06-14

## Goal

Give the app a richer, more personal look:

1. **Per-theme backgrounds** — each of the 11 accent themes gets its own background image instead of the single shared `bg_glass_orbs_blurred`.
2. **Light mode** — a full light color scheme, not just a background swap. Light text → dark text, dark frosted glass → light frosted glass, etc.
3. **Light + dark backgrounds per theme** — each accent theme has a distinct background image in both modes (22 images total).
4. **Mode control** — a System / Light / Dark selector in the Appearance tab (More section).
5. **Preserve the glass aesthetic** — keep the existing blur/frosted-glass effects and gyroscope parallax, applied over the new backgrounds.

## Current State (as built today)

- **Background:** `GlassOrbBackground` paints one pre-blurred PNG (`bg_glass_orbs_blurred`), a dark scrim (`0x8C000000`), and an accent-color tint overlay, with gyroscope parallax. Glass/frosted cards blur over it via a `layerBackdrop` backdrop system (set up in `RecompApp.kt`).
- **Themes:** 11 accent presets in `AccentTheme` (Violet…Silver), each a bag of accent colors. Selected via `UiPreferences.accentTheme`, read in `RecompApp`, passed to `RecompTrackerTheme`.
- **Color scheme:** Hardcoded `darkColorScheme` in `Theme.kt`. Across the UI, `Color.White` text (154 sites), muted-text tokens `TextMuted`/`TextDim`/`TextFaint`/`TextVeryMuted` (114 sites), and frosted/card surface tokens `FrostedSurface`/`FrostedBorder`/`CardSurface`/`CardBorder` (50 sites) are used literally. ~74 UI Kotlin files.
- **Appearance screen:** `AppearanceScreen` + `AppearanceViewModel` already exist; expose Font and Accent pickers writing to `UiPreferences`. Reached from `MoreScreen` via `onAppearance`.

## Asset Inventory

- Source images live in `LightMode/` and `DarkMode/` at the repo root, named by accent hex code (e.g. `#8B5CF6.png`).
- 11 files per folder, mapping 1:1 to the `AccentTheme` accent colors:

| Hex | AccentTheme |
|---|---|
| #8B5CF6 | VIOLET |
| #6366F1 | INDIGO |
| #3B82F6 | BLUE |
| #06B6D4 | CYAN |
| #10B981 | EMERALD |
| #84CC16 | LIME |
| #F59E0B | AMBER |
| #F97316 | ORANGE |
| #F43F5E | ROSE |
| #64748B | SLATE |
| #CBD5E1 | SILVER |

- Source PNGs ~768×1376, ~1.4 MB each, ~29 MB total. **Decision: convert all 22 to lossy WebP (~quality 80)** before bundling — expected ~3–6 MB total, no visible loss on a blurred background.

## Decisions (from brainstorming)

1. **Light mode = full light theme.** Flip the entire color scheme (light surfaces, dark text, light borders), not just a background swap.
2. **Assets → WebP.** Convert all 22 to WebP; no resolution downsample.
3. **Mode selector = System / Light / Dark** (3-state). Default `SYSTEM`; `SYSTEM` follows `isSystemInDarkTheme()`.

## Approach

**Mode-aware semantic token bag (chosen over leaning on `MaterialTheme.colorScheme` roles).**
Extend the existing `LocalAppAccent` idiom with a parallel `AppColors` bag carrying mode-aware semantic colors, provided via a new `LocalAppColors`. The glass aesthetic uses custom alpha tokens (frosted surface/border, scrim) that don't map cleanly to M3 roles, so a dedicated bag keeps one source of truth and matches how the codebase already handles accents. `MaterialTheme` still gets the correct `darkColorScheme`/`lightColorScheme` so M3 components behave.

## Design

### 1. Theme-mode preference

- New `enum class ThemeMode { SYSTEM, LIGHT, DARK }` (in `ui/theme`).
- `UiPreferences`: add `theme_mode` string key, a `themeMode: Flow<ThemeMode>` (default `SYSTEM`), and `setThemeMode(mode)` — mirroring the existing `accentTheme` pattern.
- `RecompApp`: collect `themeMode`, combine with `isSystemInDarkTheme()` to compute effective `darkMode: Boolean`, pass to `RecompTrackerTheme`.

### 2. Color token system

- New `data class AppColors` with mode-aware semantic colors. Minimum set (covers the sweep):
  - `textPrimary` (replaces text/icon `Color.White`)
  - `textSecondary`, `textMuted`, `textFaint`, `textDim`, `textVeryMuted` (replace the muted-text tokens)
  - `frostedSurface`, `frostedSurfaceFallback`, `frostedBorder`
  - `cardSurface`, `cardBorder`
  - `scrim` (background scrim — dark in dark mode, light in light mode)
- New `val LocalAppColors = compositionLocalOf { AppColors(darkMode = true) }`.
- `AppColors(darkMode: Boolean)` builds the two sets:
  - **Dark set = today's exact values** (`Color.White`, `0x47FFFFFF`, `FrostedSurface = 0x9E120A20`, etc.) so dark mode is pixel-identical to current.
  - **Light set** = dark text on light frosted glass: e.g. `textPrimary ≈ 0xFF0D0818`, muted variants as dark-alpha blacks, `frostedSurface` as a high-alpha white/light fill, `frostedBorder` as a low-alpha black hairline, `scrim` as a light translucent veil.
- `RecompTrackerTheme(accentTheme, darkMode)`:
  - picks `darkColorScheme(...)` (existing `accentedColorScheme`) or a new light equivalent `accentedLightColorScheme(...)`;
  - builds `AppColors(darkMode)`;
  - provides `LocalAppAccent` (unchanged) **and** `LocalAppColors`.

### 3. Per-theme backgrounds

- Convert the 22 source images to WebP and place in `res/drawable` as `bg_<theme>_dark` and `bg_<theme>_light` (e.g. `bg_violet_dark`, `bg_violet_light`). Resource names use the lowercased `AccentTheme` name.
- Add `fun AccentTheme.backgroundRes(darkMode: Boolean): Int` returning the matching drawable id.
- Rework `GlassOrbBackground`:
  - read `LocalAppAccent`/current accent + `darkMode` (passed in or via a new local) and paint the selected image;
  - keep gyroscope parallax (`graphicsLayer` translation) and `IMAGE_SCALE` exactly as today;
  - replace the fixed dark scrim with `LocalAppColors.current.scrim` (mode-aware);
  - **drop the accent-tint overlay** — the new backgrounds are already accent-colored, so tinting again would double-tint.
- `RecompApp` passes the effective `darkMode` (and accent, already available) down to `GlassOrbBackground`. Glass cards continue to blur over the background via the unchanged backdrop system.

### 4. Appearance UI

- New `ThemeModePicker` composable — a 3-segment glass/pill control (System / Light / Dark), reusing the existing glass component patterns (consistent with `AccentThemePicker` / `FontPicker`). No brand-new bespoke styling where an existing component fits.
- `AppearanceViewModel`: add `themeMode: StateFlow<ThemeMode>` + `setThemeMode(mode)`.
- `AppearanceScreen`: add a "Theme" `SectionLabel` + `ThemeModePicker` above "Accent color". The existing live-preview card now also demonstrates the active mode.

### 5. Color sweep strategy

Staged, screen-by-screen — **not** one giant commit. For each screen/component file:

1. Replace text/icon `Color.White` → `LocalAppColors.current.textPrimary`.
2. Replace muted-text tokens → their `AppColors` equivalents.
3. Replace `FrostedSurface`/`FrostedBorder`/`CardSurface`/`CardBorder` → `AppColors` equivalents.
4. **Leave structural white** that must stay white regardless of mode (e.g. button `onPrimary` text over a colored accent fill, white-on-accent badges) as `Color.White`.
5. Build (`./gradlew :app:compileDebugKotlin`) and spot-check the screen in **both** modes before moving to the next.

Suggested ordering (low-risk → high-traffic): shared components (`Components.kt`, `GlassComponents.kt`, `LiquidComponents.kt`, `AiInsightCard.kt`) → Appearance/More → Dashboard/Today/Food → Plan/Progress/Profile → Integrations/Scanner → remaining.

### 6. Edge cases

- **Status bar icons** flip with mode (light icons on dark background, dark icons on light) — set via the system-bars controller in `MainActivity`/`RecompApp` keyed on effective `darkMode`.
- **AI 🤖 tinted cards** stay accent-tinted but their text/surface tokens become mode-aware (use `AppColors`).
- **Default = `SYSTEM`**; first launch on a dark phone is visually identical to today.
- **Light color scheme** needs `background`/`surface`/`onSurface` values defined (new `accentedLightColorScheme`); ensure M3 components (dialogs, sheets) read sensible light values.

## Out of Scope / YAGNI

- No per-screen custom backgrounds (one background per theme+mode).
- No automatic time-of-day scheduling beyond OS `SYSTEM` following.
- No new accent themes; the 11 existing themes are unchanged.
- No resolution downsampling of the background images.

## Testing / Verification

- Unit-testable: `ThemeMode` persistence round-trip via `UiPreferences`; `AccentTheme.backgroundRes()` mapping returns a valid drawable for every (theme, mode) pair.
- Build gate per sweep stage: `./gradlew :app:compileDebugKotlin`.
- Manual: toggle System/Light/Dark and cycle all 11 accents; verify background swaps, text remains legible, and glass blur renders in both modes on each top-level screen.
