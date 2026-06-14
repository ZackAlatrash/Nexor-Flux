# Themed Backgrounds + Light Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each accent theme its own background image, add a full light color scheme, and a System/Light/Dark selector in Appearance — while preserving the existing glass/blur aesthetic.

**Architecture:** Extend the existing `LocalAppAccent` CompositionLocal idiom with a parallel mode-aware `AppColors` bag (`LocalAppColors`). `RecompTrackerTheme(accentTheme, darkMode)` selects the dark or light Material color scheme + token set. Backgrounds become per-(theme, mode) WebP drawables painted by `GlassOrbBackground`. A new `ThemeMode` preference (SYSTEM/LIGHT/DARK) drives the effective dark/light decision.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Room/DataStore, `cwebp` (asset conversion), JUnit4 (unit tests over in-memory `Preferences`).

**Spec:** `docs/superpowers/specs/2026-06-14-themed-backgrounds-light-mode-design.md`

**Branch:** `feat/themed-backgrounds-light-mode` (already created and checked out)

---

## File Map

| File | Responsibility | Action |
|---|---|---|
| `app/src/main/res/drawable/bg_<theme>_<mode>.webp` (×22) | Per-theme/mode background images | Create |
| `ui/theme/ThemeMode.kt` | `ThemeMode` enum + pure storage mapping | Create |
| `ui/theme/AppColors.kt` | Mode-aware semantic color bag + `LocalAppColors` | Create |
| `ui/theme/Theme.kt` | Add light color scheme + `darkMode` param + provide `LocalAppColors` | Modify |
| `ui/theme/DesignTokens.kt` | Add `AccentTheme.backgroundRes(darkMode)` mapping | Modify |
| `data/preferences/AppPreferences.kt` | `themeMode` flow + setter on `UiPreferences` | Modify |
| `ui/component/GlassOrbBackground.kt` | Paint per-theme image, mode-aware scrim, drop accent tint | Modify |
| `ui/RecompApp.kt` | Compute effective `darkMode`, pass to theme + background | Modify |
| `MainActivity.kt` | Flip status-bar icon contrast with mode | Modify |
| `ui/component/Components.kt` | Add `ThemeModePicker`; part of color sweep | Modify |
| `ui/appearance/AppearanceViewModel.kt` | Expose `themeMode` + `setThemeMode` | Modify |
| `ui/appearance/AppearanceScreen.kt` | Add Theme section; part of color sweep | Modify |
| `ui/**/*.kt` (~74 files) | Color sweep: hardcoded colors → `AppColors` tokens | Modify (staged) |
| `app/src/test/java/.../ui/theme/ThemeModeTest.kt` | `ThemeMode` mapping test | Create |
| `app/src/test/java/.../ui/theme/AccentThemeBackgroundTest.kt` | `backgroundRes` mapping test | Create |

---

## Sweep Rubric (referenced by Tasks 8–12)

The color sweep is mechanical but requires judgment to distinguish *semantic text/surface* colors (which must become mode-aware) from *structural* colors (which must stay fixed). Apply this exact mapping in each sweep task:

| Old (hardcoded) | New (mode-aware) |
|---|---|
| `Color.White` used as **text/icon color** | `LocalAppColors.current.textPrimary` |
| `TextMuted` | `LocalAppColors.current.textMuted` |
| `TextDim` | `LocalAppColors.current.textDim` |
| `TextFaint` | `LocalAppColors.current.textFaint` |
| `TextVeryMuted` | `LocalAppColors.current.textVeryMuted` |
| `FrostedSurface` | `LocalAppColors.current.frostedSurface` |
| `FrostedSurfaceFallback` | `LocalAppColors.current.frostedSurfaceFallback` |
| `FrostedBorder` | `LocalAppColors.current.frostedBorder` |
| `CardSurface` | `LocalAppColors.current.cardSurface` |
| `CardBorder` | `LocalAppColors.current.cardBorder` |

**DO NOT change (structural — stays fixed in both modes):**
- `Color.White` passed as `onPrimary`/text **on top of an accent-colored fill** (e.g. text on a `LiquidPrimaryButton`, white tick/ring inside a colored accent swatch, white text in a colored badge). These must stay white because the fill underneath is always a saturated accent color in both modes.
- Accent colors themselves (`accent.accent`, `accentLight`, etc.) — unchanged.
- Semantic colors like `ErrorRed`, `SuccessGreen` — unchanged.

**Disambiguation test:** "Is this color drawn directly on the app background / a frosted card?" → make it mode-aware. "Is it drawn on top of a saturated accent fill?" → leave it `Color.White`.

Each sweep task: edit the listed files, add the `LocalAppColors` import where needed, then run `./gradlew :app:compileDebugKotlin` and confirm BUILD SUCCESSFUL before committing.

---

## Task 1: Convert background images to WebP

**Files:**
- Create: `app/src/main/res/drawable/bg_violet_dark.webp` … `bg_silver_light.webp` (22 files)

- [ ] **Step 1: Confirm `cwebp` is available**

Run: `which cwebp`
Expected: a path (e.g. `/opt/homebrew/bin/cwebp`). If missing: `brew install webp`.

- [ ] **Step 2: Create the conversion script**

Create `scripts/convert_backgrounds.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
DEST="app/src/main/res/drawable"
mkdir -p "$DEST"

# hex (filename in LightMode/DarkMode) -> resource theme name
declare -a MAP=(
  "8B5CF6 violet"  "6366F1 indigo" "3B82F6 blue"   "06B6D4 cyan"
  "10B981 emerald" "84CC16 lime"   "F59E0B amber"  "F97316 orange"
  "F43F5E rose"    "64748B slate"  "CBD5E1 silver"
)
for pair in "${MAP[@]}"; do
  hex="${pair%% *}"; name="${pair##* }"
  cwebp -q 80 "DarkMode/#${hex}.png"  -o "${DEST}/bg_${name}_dark.webp"
  cwebp -q 80 "LightMode/#${hex}.png" -o "${DEST}/bg_${name}_light.webp"
done
echo "Done: $(ls "${DEST}"/bg_*_*.webp | wc -l) webp files"
```

- [ ] **Step 3: Run the script**

Run: `chmod +x scripts/convert_backgrounds.sh && ./scripts/convert_backgrounds.sh`
Expected: `Done: 22 webp files`

- [ ] **Step 4: Verify size and count**

Run: `ls app/src/main/res/drawable/bg_*_*.webp | wc -l && du -sh app/src/main/res/drawable/bg_*_*.webp | tail -1 && du -ch app/src/main/res/drawable/bg_*_*.webp | tail -1`
Expected: `22`, and total well under 10 MB (target ~3–6 MB).

- [ ] **Step 5: Commit**

```bash
git add scripts/convert_backgrounds.sh app/src/main/res/drawable/bg_*_*.webp
git commit -m "feat(appearance): add per-theme background webp assets"
```

---

## Task 2: ThemeMode enum + persistence

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/theme/ThemeMode.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ui/theme/ThemeModeTest.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/theme/ThemeModeTest.kt`:

```kotlin
package com.zack.recomptracker.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `round-trips every mode through storage value`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.fromStored(mode.storageValue))
        }
    }

    @Test
    fun `unknown or null storage value defaults to SYSTEM`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("garbage"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ThemeModeTest*"`
Expected: FAIL — `ThemeMode` unresolved.

- [ ] **Step 3: Create the enum**

Create `app/src/main/java/com/zack/recomptracker/ui/theme/ThemeMode.kt`:

```kotlin
package com.zack.recomptracker.ui.theme

/** User's appearance-mode preference. SYSTEM follows the OS dark-mode setting. */
enum class ThemeMode(val storageValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ThemeModeTest*"`
Expected: PASS.

- [ ] **Step 5: Add the preference**

In `AppPreferences.kt`, inside `UiPreferences`, add this flow next to `accentTheme` (after line 125):

```kotlin
    val themeMode: kotlinx.coroutines.flow.Flow<com.zack.recomptracker.ui.theme.ThemeMode> =
        context.uiDataStore.data.map {
            com.zack.recomptracker.ui.theme.ThemeMode.fromStored(it[Keys.ThemeMode])
        }
```

Add this setter next to `setAccentTheme` (after line 137):

```kotlin
    suspend fun setThemeMode(mode: com.zack.recomptracker.ui.theme.ThemeMode) {
        context.uiDataStore.edit { it[Keys.ThemeMode] = mode.storageValue }
    }
```

Add this key inside `UiPreferences.Keys` (after the `AccentTheme` key, line 174):

```kotlin
        val ThemeMode = stringPreferencesKey("theme_mode")
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/theme/ThemeMode.kt \
        app/src/test/java/com/zack/recomptracker/ui/theme/ThemeModeTest.kt \
        app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt
git commit -m "feat(appearance): add ThemeMode preference (system/light/dark)"
```

---

## Task 3: AppColors token bag + light color scheme

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/theme/AppColors.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/theme/Theme.kt`

- [ ] **Step 1: Create the token bag**

Create `app/src/main/java/com/zack/recomptracker/ui/theme/AppColors.kt`:

```kotlin
package com.zack.recomptracker.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Mode-aware semantic color tokens. Provided via [LocalAppColors] inside
 * [RecompTrackerTheme]. The dark set reproduces today's hardcoded values exactly,
 * so dark mode is visually unchanged; the light set flips text dark and surfaces light.
 */
data class AppColors(
    val textPrimary: Color,
    val textMuted: Color,
    val textDim: Color,
    val textFaint: Color,
    val textVeryMuted: Color,
    val frostedSurface: Color,
    val frostedSurfaceFallback: Color,
    val frostedBorder: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val scrim: Color,
) {
    companion object {
        // Dark = current production values (see DesignTokens.kt).
        val Dark = AppColors(
            textPrimary            = Color.White,
            textMuted              = Color(0x47FFFFFF),
            textDim                = Color(0x66FFFFFF),
            textFaint              = Color(0x40FFFFFF),
            textVeryMuted          = Color(0x38FFFFFF),
            frostedSurface         = Color(0x9E120A20),
            frostedSurfaceFallback = Color(0xD1160E26),
            frostedBorder          = Color(0x21FFFFFF),
            cardSurface            = Color(0x0AFFFFFF),
            cardBorder             = Color(0x12FFFFFF),
            scrim                  = Color(0x8C000000),
        )

        // Light = dark ink on light frosted glass. Alphas mirror the dark set's intent.
        val Light = AppColors(
            textPrimary            = Color(0xFF141019),
            textMuted              = Color(0x99141019),
            textDim                = Color(0xBF141019),
            textFaint              = Color(0x80141019),
            textVeryMuted          = Color(0x66141019),
            frostedSurface         = Color(0xCCFFFFFF),
            frostedSurfaceFallback = Color(0xE6FFFFFF),
            frostedBorder          = Color(0x1F000000),
            cardSurface            = Color(0x14FFFFFF),
            cardBorder             = Color(0x1A000000),
            scrim                  = Color(0x40FFFFFF),
        )

        fun of(darkMode: Boolean): AppColors = if (darkMode) Dark else Light
    }
}

/** Provides the current [AppColors] to the composition tree. Defaults to dark. */
val LocalAppColors = compositionLocalOf { AppColors.Dark }
```

- [ ] **Step 2: Add a light color scheme + darkMode wiring to Theme.kt**

Replace the body of `Theme.kt` (the `accentedColorScheme` function and `RecompTrackerTheme`) with:

```kotlin
private fun accentedDarkColorScheme(accent: AppAccent) = darkColorScheme(
    primary              = accent.accent,
    onPrimary            = Color.White,
    primaryContainer     = accent.tintedSurface,
    onPrimaryContainer   = accent.accentLight,
    secondary            = accent.accentLight,
    onSecondary          = Color.White,
    background           = Color(0xFF0D0818),
    onBackground         = Color.White,
    surface              = Color(0xFF0F0B1C),
    onSurface            = Color.White,
    surfaceVariant       = Color(0xFF1A1527),
    onSurfaceVariant     = Color(0x47FFFFFF),
    outline              = Color(0x12FFFFFF),
    error                = Color(0xFFfb7185),
    onError              = Color.White,
)

private fun accentedLightColorScheme(accent: AppAccent) = lightColorScheme(
    primary              = accent.accent,
    onPrimary            = Color.White,
    primaryContainer     = accent.tintedSurface,
    onPrimaryContainer   = accent.accentDark,
    secondary            = accent.accentDark,
    onSecondary          = Color.White,
    background           = Color(0xFFF4F2F7),
    onBackground         = Color(0xFF141019),
    surface              = Color(0xFFFBFAFD),
    onSurface            = Color(0xFF141019),
    surfaceVariant       = Color(0xFFE8E5EF),
    onSurfaceVariant     = Color(0x99141019),
    outline              = Color(0x1A000000),
    error                = Color(0xFFD92D45),
    onError              = Color.White,
)

@Composable
fun RecompTrackerTheme(
    accentTheme: AccentTheme = AccentTheme.VIOLET,
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val appAccent = remember(accentTheme) { AppAccent(accentTheme) }
    val appColors = remember(darkMode) { AppColors.of(darkMode) }
    val colorScheme = remember(accentTheme, darkMode) {
        if (darkMode) accentedDarkColorScheme(appAccent) else accentedLightColorScheme(appAccent)
    }
    CompositionLocalProvider(
        LocalAppAccent provides appAccent,
        LocalAppColors provides appColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
```

Add the needed import at the top of `Theme.kt`:

```kotlin
import androidx.compose.material3.lightColorScheme
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`RecompApp` still calls `RecompTrackerTheme(accentTheme = ...)`; `darkMode` defaults to `true`, so behavior is unchanged here.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/theme/AppColors.kt \
        app/src/main/java/com/zack/recomptracker/ui/theme/Theme.kt
git commit -m "feat(appearance): add AppColors token bag + light color scheme"
```

---

## Task 4: AccentTheme → background drawable mapping

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/theme/DesignTokens.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ui/theme/AccentThemeBackgroundTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/theme/AccentThemeBackgroundTest.kt`:

```kotlin
package com.zack.recomptracker.ui.theme

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentThemeBackgroundTest {

    @Test
    fun `every theme has a non-zero drawable for both modes`() {
        for (theme in AccentTheme.entries) {
            assertTrue("dark res for $theme", theme.backgroundRes(darkMode = true) != 0)
            assertTrue("light res for $theme", theme.backgroundRes(darkMode = false) != 0)
        }
    }

    @Test
    fun `dark and light resources differ per theme`() {
        for (theme in AccentTheme.entries) {
            assertNotEquals(
                "dark/light should differ for $theme",
                theme.backgroundRes(darkMode = true),
                theme.backgroundRes(darkMode = false),
            )
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*AccentThemeBackgroundTest*"`
Expected: FAIL — `backgroundRes` unresolved.

- [ ] **Step 3: Add the mapping**

Append to `DesignTokens.kt` (after the `AppAccent` data class, around line 41):

```kotlin
import com.zack.recomptracker.R

/**
 * Per-theme background drawable for the given mode. Resource names follow
 * `bg_<lowercase-theme>_<dark|light>` (see scripts/convert_backgrounds.sh).
 */
fun AccentTheme.backgroundRes(darkMode: Boolean): Int = when (this to darkMode) {
    AccentTheme.VIOLET  to true  -> R.drawable.bg_violet_dark
    AccentTheme.VIOLET  to false -> R.drawable.bg_violet_light
    AccentTheme.INDIGO  to true  -> R.drawable.bg_indigo_dark
    AccentTheme.INDIGO  to false -> R.drawable.bg_indigo_light
    AccentTheme.BLUE    to true  -> R.drawable.bg_blue_dark
    AccentTheme.BLUE    to false -> R.drawable.bg_blue_light
    AccentTheme.CYAN    to true  -> R.drawable.bg_cyan_dark
    AccentTheme.CYAN    to false -> R.drawable.bg_cyan_light
    AccentTheme.EMERALD to true  -> R.drawable.bg_emerald_dark
    AccentTheme.EMERALD to false -> R.drawable.bg_emerald_light
    AccentTheme.LIME    to true  -> R.drawable.bg_lime_dark
    AccentTheme.LIME    to false -> R.drawable.bg_lime_light
    AccentTheme.AMBER   to true  -> R.drawable.bg_amber_dark
    AccentTheme.AMBER   to false -> R.drawable.bg_amber_light
    AccentTheme.ORANGE  to true  -> R.drawable.bg_orange_dark
    AccentTheme.ORANGE  to false -> R.drawable.bg_orange_light
    AccentTheme.ROSE    to true  -> R.drawable.bg_rose_dark
    AccentTheme.ROSE    to false -> R.drawable.bg_rose_light
    AccentTheme.SLATE   to true  -> R.drawable.bg_slate_dark
    AccentTheme.SLATE   to false -> R.drawable.bg_slate_light
    AccentTheme.SILVER  to true  -> R.drawable.bg_silver_dark
    else                         -> R.drawable.bg_silver_light
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*AccentThemeBackgroundTest*"`
Expected: PASS. (Requires Task 1's drawables to exist so `R.drawable.*` resolve.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/theme/DesignTokens.kt \
        app/src/test/java/com/zack/recomptracker/ui/theme/AccentThemeBackgroundTest.kt
git commit -m "feat(appearance): map each accent theme to its background drawable"
```

---

## Task 5: Rework GlassOrbBackground for per-theme image + mode-aware scrim

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/GlassOrbBackground.kt`

- [ ] **Step 1: Change the signature and image source**

In `GlassOrbBackground.kt`, change the function signature (line 48) to accept the active theme + mode:

```kotlin
@Composable
fun GlassOrbBackground(
    accentTheme: com.zack.recomptracker.ui.theme.AccentTheme,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 2: Paint the selected image and a mode-aware scrim; drop the accent tint**

Replace the `Box { Image(...) ... }` block (lines 96–127) with:

```kotlin
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Image(
            painter = painterResource(accentTheme.backgroundRes(darkMode)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    scaleX = IMAGE_SCALE
                    scaleY = IMAGE_SCALE
                    translationX = (tiltX / MAX_TILT) * parallaxPx
                    translationY = (tiltY / MAX_TILT) * parallaxPx
                },
        )
        // Mode-aware scrim for card readability (dark veil in dark mode, light veil in light).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(com.zack.recomptracker.ui.theme.LocalAppColors.current.scrim),
        )
    }
```

- [ ] **Step 3: Remove now-unused imports**

Remove these imports from `GlassOrbBackground.kt` (no longer referenced after dropping the accent tint and `R`-based painter constant):
- `import com.zack.recomptracker.R`
- `import com.zack.recomptracker.ui.theme.LocalAppAccent`

(Keep `androidx.compose.ui.res.painterResource` — still used.)

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL at `RecompApp.kt:124` — `GlassOrbBackground()` now needs arguments. That is fixed in Task 6. (If you want a green checkpoint first, temporarily pass `GlassOrbBackground(AccentTheme.VIOLET, true)` — but Task 6 supplies the real values, so proceed.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/GlassOrbBackground.kt
git commit -m "feat(appearance): paint per-theme background + mode-aware scrim"
```

---

## Task 6: Wire effective dark/light through RecompApp + status bar

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/MainActivity.kt`

- [ ] **Step 1: Compute effective darkMode and pass it down**

In `RecompApp.kt`, replace the theme setup (lines 84–87) with:

```kotlin
fun RecompApp(container: AppContainer) {
    val accentTheme by container.uiPreferences.accentTheme
        .collectAsStateWithLifecycle(initialValue = AccentTheme.VIOLET)
    val themeMode by container.uiPreferences.themeMode
        .collectAsStateWithLifecycle(initialValue = com.zack.recomptracker.ui.theme.ThemeMode.SYSTEM)
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val darkMode = when (themeMode) {
        com.zack.recomptracker.ui.theme.ThemeMode.SYSTEM -> systemDark
        com.zack.recomptracker.ui.theme.ThemeMode.DARK -> true
        com.zack.recomptracker.ui.theme.ThemeMode.LIGHT -> false
    }
    RecompTrackerTheme(accentTheme = accentTheme, darkMode = darkMode) {
```

- [ ] **Step 2: Pass theme + mode to the background**

In `RecompApp.kt`, change the `GlassOrbBackground()` call (line 124) to:

```kotlin
                        GlassOrbBackground(accentTheme = accentTheme, darkMode = darkMode)
```

- [ ] **Step 3: Flip status-bar icon contrast with mode**

In `MainActivity.kt`, replace the body with:

```kotlin
package com.zack.recomptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.zack.recomptracker.ui.RecompApp
import com.zack.recomptracker.ui.theme.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as RecompTrackerApp
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !app.dbReady.value }
        setContent {
            val themeMode by app.container.uiPreferences.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkMode = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            val window = window
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                // Light status-bar icons (dark glyphs) when the app is in light mode.
                controller.isAppearanceLightStatusBars = !darkMode
                controller.isAppearanceLightNavigationBars = !darkMode
            }
            RecompApp(container = app.container)
        }
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt \
        app/src/main/java/com/zack/recomptracker/MainActivity.kt
git commit -m "feat(appearance): drive effective dark/light mode + status bar contrast"
```

---

## Task 7: ThemeModePicker + Appearance wiring

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/Components.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/appearance/AppearanceViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/appearance/AppearanceScreen.kt`

- [ ] **Step 1: Add the ThemeModePicker (mirrors FontPicker's segmented style)**

In `Components.kt`, add after `FontPicker` (after line 278). Add the import `import com.zack.recomptracker.ui.theme.ThemeMode` to the file's imports:

```kotlin
// ── Theme mode picker (System / Light / Dark) ───────────────────────────────────
@Composable
internal fun ThemeModePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val accent = LocalAppAccent.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ThemeMode.entries.forEach { mode ->
            val isActive = selected == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isActive) accent.accent.copy(alpha = 0.22f) else Color(0x0FFFFFFF))
                    .border(
                        1.dp,
                        if (isActive) accent.accent.copy(alpha = 0.35f) else Color(0x14FFFFFF),
                        RoundedCornerShape(7.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = mode.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) accent.accentLighter else Color(0x59FFFFFF),
                )
            }
        }
    }
}
```

(Note: this picker is part of the Appearance card, which keeps the accent-on-tint styling in both modes; its grey inactive colors are acceptable on a frosted card and will be revisited if the Task 9 sweep flags them.)

- [ ] **Step 2: Expose themeMode from the ViewModel**

In `AppearanceViewModel.kt`, add the import `import com.zack.recomptracker.ui.theme.ThemeMode`, then add this property after `accent` (after line 33):

```kotlin
    val themeMode: StateFlow<ThemeMode> =
        uiPreferences.themeMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )
```

And this setter after `setAccent` (after line 41):

```kotlin
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { uiPreferences.setThemeMode(mode) }
    }
```

- [ ] **Step 3: Add the Theme section to the screen**

In `AppearanceScreen.kt`: add imports `import com.zack.recomptracker.ui.component.ThemeModePicker`, then collect the state after line 54:

```kotlin
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
```

Insert this block in the `LazyColumn` immediately before the `// ── Font ──` section (before line 150):

```kotlin
            // ── Theme mode ────────────────────────────────────────────────────
            item { SectionLabel("Theme") }
            item {
                ThemeModePicker(selected = themeMode, onSelect = viewModel::setThemeMode)
            }
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Build the debug APK and smoke-test manually**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Install on device/emulator, open More → Appearance, toggle System/Light/Dark and cycle a few accents. Confirm the background image swaps per accent and per mode. (Text legibility in light mode is fixed by the sweep in Tasks 8–12 — at this checkpoint light mode will still show white text on some screens.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/Components.kt \
        app/src/main/java/com/zack/recomptracker/ui/appearance/AppearanceViewModel.kt \
        app/src/main/java/com/zack/recomptracker/ui/appearance/AppearanceScreen.kt
git commit -m "feat(appearance): add System/Light/Dark picker to Appearance"
```

---

## Task 8: Color sweep — shared components

**Files (apply the Sweep Rubric above to each):**
- Modify: `ui/component/Components.kt`
- Modify: `ui/component/GlassComponents.kt`
- Modify: `ui/liquidglass/LiquidComponents.kt`
- Modify: `ui/component/AiInsightCard.kt`
- Modify: `ui/integrations/IntegrationsComponents.kt`

- [ ] **Step 1: Apply the rubric to each file**

For every hardcoded color listed in the Sweep Rubric, replace per the mapping; leave structural white (text on accent fills) alone. Add `import com.zack.recomptracker.ui.theme.LocalAppColors` to each file that gains a token reference.

Concrete example — `SettingRow` in `Components.kt` (lines 358–359):

```kotlin
// before
Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
Text(detail, fontSize = 11.sp, color = TextMuted)
// after
Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textPrimary)
Text(detail, fontSize = 11.sp, color = LocalAppColors.current.textMuted)
```

Leave `AccentThemePicker`'s `Color.White`/`Color.Black` tick & ring colors as-is — they sit on top of saturated accent swatches (structural, per the rubric).

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Spot-check both modes**

Install (`./gradlew :app:installDebug`) and verify shared cards, AI insight card, and setting rows render legibly in both Light and Dark.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/Components.kt \
        app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt \
        app/src/main/java/com/zack/recomptracker/ui/liquidglass/LiquidComponents.kt \
        app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt \
        app/src/main/java/com/zack/recomptracker/ui/integrations/IntegrationsComponents.kt
git commit -m "refactor(appearance): make shared components mode-aware"
```

---

## Task 9: Color sweep — Appearance, More, Profile

**Files (apply the Sweep Rubric):**
- Modify: `ui/appearance/AppearanceScreen.kt`
- Modify: `ui/more/MoreScreen.kt`
- Modify: `ui/profile/ProfileScreen.kt`
- Modify: `ui/settings/SettingsViewModel.kt` (only if it references UI colors)

- [ ] **Step 1: Apply the rubric**

Replace per the mapping; add `LocalAppColors` import where needed. In `AppearanceScreen.kt` the header `Text(... color = Color.White)` (line 107, 113) and `Icon(... tint = Color.White)` (line 107) become `LocalAppColors.current.textPrimary`. The `VioletBadge`/`LiquidPrimaryButton` accent surfaces are untouched.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Spot-check both modes** (More hub, Appearance, Profile).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/appearance/AppearanceScreen.kt \
        app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt \
        app/src/main/java/com/zack/recomptracker/ui/profile/ProfileScreen.kt
git commit -m "refactor(appearance): make More/Appearance/Profile mode-aware"
```

---

## Task 10: Color sweep — Dashboard + Today (Food, Body/Recovery)

**Files (apply the Sweep Rubric):**
- Modify: `ui/dashboard/DashboardScreen.kt`
- Modify: `ui/today/FoodScreen.kt`
- Modify: `ui/today/BodyRecoveryScreen.kt`

- [ ] **Step 1: Apply the rubric** (add `LocalAppColors` imports as needed).

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Spot-check both modes** (Home dashboard, Food log, Body/Recovery).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt \
        app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt \
        app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt
git commit -m "refactor(appearance): make Dashboard/Today mode-aware"
```

---

## Task 11: Color sweep — Plan + Progress

**Files (apply the Sweep Rubric):**
- Modify: `ui/plan/PlanScreen.kt`
- Modify: `ui/progress/ProgressScreen.kt`

- [ ] **Step 1: Apply the rubric** (add `LocalAppColors` imports as needed). For chart colors, only convert label/axis text and frosted surfaces — leave data-series accent colors untouched.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Spot-check both modes** (Plan, Progress charts).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt \
        app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt
git commit -m "refactor(appearance): make Plan/Progress mode-aware"
```

---

## Task 12: Color sweep — Integrations, Scanner, and remainder

**Files (apply the Sweep Rubric):**
- Modify: `ui/integrations/IntegrationsScreen.kt`
- Modify: `ui/scanner/BarcodeScannerScreen.kt`
- Modify: any remaining files surfaced by Step 1's grep.

- [ ] **Step 1: Find every remaining hardcoded reference**

Run:
```bash
grep -rn "Color.White\|TextMuted\|TextDim\|TextFaint\|TextVeryMuted\|FrostedSurface\|FrostedBorder\|CardSurface\|CardBorder" \
  app/src/main/java/com/zack/recomptracker/ui --include="*.kt" \
  | grep -vE "AppColors|LocalAppColors|DesignTokens.kt"
```
Expected after this task: only structural-white lines remain (text on accent fills, per the rubric). Apply the rubric to each remaining semantic site.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Spot-check both modes** (Integrations, Barcode scanner overlay).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(appearance): make remaining screens mode-aware"
```

---

## Task 13: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Build the release-shaped debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual verification matrix**

Install (`./gradlew :app:installDebug`) and verify:
- For mode ∈ {System, Light, Dark}: open Home, Body, Food, Coach, More, Plan, Progress, Profile, Appearance, Integrations.
- Confirm: background image matches the selected accent AND mode; all text is legible (no white-on-light or black-on-dark); glass blur/parallax still render; status-bar icons contrast correctly.
- Cycle all 11 accents in both Light and Dark; confirm the background swaps each time.
- Confirm `SYSTEM` follows the OS toggle (change the phone's dark-mode setting; app follows).

- [ ] **Step 4: Confirm asset size**

Run: `du -ch app/src/main/res/drawable/bg_*_*.webp | tail -1`
Expected: total ~3–6 MB.

- [ ] **Step 5: Final commit (if any spot-fixes were made)**

```bash
git add -A
git commit -m "test(appearance): verify themed backgrounds + light mode" || echo "nothing to commit"
```

---

## Self-Review Notes

- **Spec coverage:** per-theme backgrounds (Tasks 1, 4, 5) ✓; full light theme (Tasks 3, 8–12) ✓; light+dark backgrounds per theme (Task 1, 4) ✓; System/Light/Dark selector (Tasks 2, 7) ✓; preserve glass/parallax (Task 5 keeps `IMAGE_SCALE`/parallax, scrim mode-aware) ✓; status-bar contrast edge case (Task 6) ✓; WebP conversion (Task 1) ✓; drop accent-tint overlay (Task 5) ✓.
- **Type consistency:** `ThemeMode.fromStored`/`storageValue` (Task 2) reused in Tasks 6, 7; `AppColors.of(darkMode)` (Task 3) consumed by Theme + sweep; `AccentTheme.backgroundRes(darkMode)` (Task 4) consumed by Task 5; `RecompTrackerTheme(accentTheme, darkMode, content)` (Task 3) called in Task 6; `GlassOrbBackground(accentTheme, darkMode, modifier)` (Task 5) called in Task 6.
- **Sweep granularity:** the sweep is intentionally rubric-driven rather than line-by-line (300+ sites). Each sweep task is build-gated and spot-checked in both modes before commit.
