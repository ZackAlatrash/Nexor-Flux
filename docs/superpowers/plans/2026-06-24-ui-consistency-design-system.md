# UI Consistency Design System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate cross-screen UI inconsistency by introducing a named typography scale, spacing tokens, two-tier header components, and a reusable screen scaffold, then migrating every screen onto them.

**Architecture:** Add a foundation layer (`AppType` text styles, `Spacing` tokens, `ScreenScaffold`/`ScreenHeader`/`SubScreenHeader`) that re-expresses the dominant existing values as single sources of truth. Then migrate screens batch-by-batch to consume the foundation, deleting per-screen ad-hoc headers, inline `fontSize`/`fontWeight`, raw card boxes, and non-standard buttons. Finally, write a durable design guide for future contributors.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, single-module Android app. Package root `com.zack.recomptracker`.

---

## Verification model (read first)

This is a **visual Compose refactor**. The project has no composable/screenshot tests and
`CLAUDE.md` convention is that the user verifies UI visually. Therefore each task's gate is:

1. **`./gradlew :app:compileDebugKotlin`** must pass (type-check; fast, no device).
2. For screen-migration tasks, after compile passes, **hand off to the user for visual
   verification** of that screen in the running app before moving on.

Foundation tasks (1–5) are pure additions and only need compile + commit. Do **not** run the
emulator or drive the app yourself.

## File structure

**New files**
- `app/src/main/java/com/zack/recomptracker/ui/theme/Typography.kt` — `AppType` named `TextStyle`s.
- `app/src/main/java/com/zack/recomptracker/ui/component/ScreenScaffold.kt` — `ScreenScaffold`, `ScreenHeader`, `SubScreenHeader`, shared `BackButton`.
- `docs/design-system.md` — durable design guide (linked from `CLAUDE.md`).

**Modified foundation files**
- `app/src/main/java/com/zack/recomptracker/ui/theme/DesignTokens.kt` — add `ScreenPaddingH`, `ScreenSpacing`, `Spacing`.
- `app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt` — `SectionLabel` reads `AppType.sectionLabel`.

**Migrated screens** (each its own task): Dashboard, FoodScreen, BodyRecovery, Progress, More, TrainHome, ActiveSession, SessionSummary, SessionDetail, RoutineBuilder, ExercisePicker, ExerciseStats, Profile, Appearance, Integrations, DataBackup, Plan, FoodLibrary, Foods, RecipeBuilder, BarcodeScanner, Coach, AiCoach, Onboarding, BodyEdit, BodyHistory, Weekly Review overlay.

---

## PHASE 1 — Foundation

### Task 1: Typography scale (`AppType`)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/theme/Typography.kt`

- [ ] **Step 1: Create the file with the full named scale**

```kotlin
package com.zack.recomptracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Named typography scale for the whole app. Screens MUST use these instead of inline
 * fontSize/fontWeight so text stays consistent.
 *
 * Color is intentionally NOT set here — it is theme-dependent (dark/light) and must be
 * passed at the call site, e.g. `Text(..., style = AppType.cardTitle, color = appColors.textPrimary)`.
 */
object AppType {
    // ── Screen headers ───────────────────────────────────────────────────────
    /** Tier-1 big display header for top-level tab destinations. */
    val screenTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
    /** Tier-2 compact header for pushed sub-screens. */
    val screenTitleCompact = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    /** Header subtitle / supporting line under a title. */
    val screenSubtitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)

    // ── Sections & rows ──────────────────────────────────────────────────────
    /** Uppercase section label (matches the existing SectionLabel values). */
    val sectionLabel = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.14.sp)
    /** Primary title inside a card / list row. */
    val cardTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    /** Secondary / supporting text inside a card / list row. */
    val cardSubtitle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    /** Default body copy. */
    val body = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    /** Small inline label. */
    val label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    /** Tiny uppercase caption for tiles / stats. */
    val metaLabel = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)

    // ── Data display (emphasis preserved) ────────────────────────────────────
    /** Largest hero number, e.g. onboarding result calorie. */
    val displayHero = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.0).sp)
    /** Hero metric number, e.g. Body metric / Dashboard calorie. */
    val displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
    /** Tile / summary big number. */
    val statValue = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    /** Compact stat value. */
    val statValueSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/theme/Typography.kt
git commit -m "feat(ui): add AppType named typography scale"
```

---

### Task 2: Spacing & layout tokens

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/theme/DesignTokens.kt` (append after the corner-radius block near line 100)

- [ ] **Step 1: Append the tokens**

Add below the `CornerPill` declaration:

```kotlin
// ── Screen layout tokens ──────────────────────────────────────────────────────
/** Canonical horizontal padding for every screen's content. */
val ScreenPaddingH = 16.dp
/** Canonical vertical gap between cards / sections within a screen. */
val ScreenSpacing = 10.dp

/** In-card / inline spacing scale. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/theme/DesignTokens.kt
git commit -m "feat(ui): add screen padding + spacing tokens"
```

---

### Task 3: Point `SectionLabel` at `AppType`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt:184-194`

- [ ] **Step 1: Replace the `SectionLabel` body**

Replace the existing `SectionLabel` composable with:

```kotlin
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = com.zack.recomptracker.ui.theme.AppType.sectionLabel,
        color = LocalAppColors.current.textFaint,
        modifier = modifier,
    )
}
```

(The values are identical to today's inline ones — 9sp/Bold/0.14/textFaint — so this is a
no-visual-change refactor that makes `AppType.sectionLabel` the single source of truth.)

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt
git commit -m "refactor(ui): SectionLabel uses AppType.sectionLabel"
```

---

### Task 4: Screen scaffold + two-tier headers

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/ScreenScaffold.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.theme.ScreenPaddingH
import com.zack.recomptracker.ui.theme.ScreenSpacing

/**
 * Standard screen frame. Wraps a LazyColumn with the canonical horizontal padding,
 * inter-section spacing, and a bottom inset that clears the floating nav bar.
 *
 * @param withNavBarInset true for tab destinations (reserves space for the floating nav);
 *        false for pushed sub-screens that have no nav bar.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    withNavBarInset: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenPaddingH,
                end = ScreenPaddingH,
                top = 4.dp,
                bottom = if (withNavBarInset) FloatingNavHeight + 16.dp else 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ScreenSpacing),
            content = content,
        )
    }
}

/**
 * Tier-1 header for top-level tab destinations. Big display title, optional subtitle,
 * optional trailing slot (avatar / action).
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppType.screenTitle, color = appColors.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = AppType.screenSubtitle, color = appColors.textMuted)
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * Tier-2 header for pushed sub-screens. Standard back button + compact title, optional
 * subtitle, optional trailing slot.
 */
@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppType.screenTitleCompact, color = appColors.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = AppType.screenSubtitle, color = appColors.textMuted)
            }
        }
        if (trailing != null) trailing()
    }
}

/** Canonical 40dp circular back button used by every sub-screen header. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = appColors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/ScreenScaffold.kt
git commit -m "feat(ui): add ScreenScaffold + two-tier header components"
```

---

## PHASE 2 — Tab destinations

> **Per-screen migration recipe** (apply to every screen task below):
> 1. **Header:** replace the screen's private/inline header with `ScreenHeader(...)` (tier-1
>    tab destinations) or `SubScreenHeader(...)` (pushed screens). Delete the now-unused
>    private `ScreenHeader`/back-button code and its imports.
> 2. **Scaffold:** if the screen is a `Box { LazyColumn { ... } }` with the standard
>    contentPadding, replace it with `ScreenScaffold { ... }`. Keep screens that need a pinned
>    (non-scrolling) header as-is structurally, but still use the shared header composable.
> 3. **Typography:** replace every inline `fontSize`/`fontWeight`/`letterSpacing` on `Text`
>    with `style = AppType.<token>` per the mapping table in the spec. Pass `color` separately.
> 4. **Spacing/padding:** replace literal `16.dp`/`20.dp`/`14.dp` horizontal paddings inside
>    the content with `ScreenPaddingH` where they represent screen padding; replace inline
>    section gaps with `ScreenSpacing` / `Spacing.*`.
> 5. **Section labels:** replace inline label `Text`s with `SectionLabel("...")`.
> 6. **Cards/buttons/icons:** swap raw card boxes for `FrostedCard`/`NeutralCard`; swap
>    non-standard buttons for the `Liquid*Button` family; replace text-glyph icons with
>    Material icons.
> 7. Compile, then hand off for visual verification.

### Task 5: Migrate DashboardScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Replace the private `ScreenHeader` (line ~300)** with a call to the shared
  `ScreenHeader`, moving the profile avatar into the `trailing` slot:

```kotlin
ScreenHeader(
    title = "Dashboard",
    subtitle = dateStr,
    trailing = { ProfileAvatarButton(onClick = onOpenProfile) }, // existing 40dp avatar box, extracted
)
```
Delete the old private `ScreenHeader` composable and its hardcoded `Text("Dashboard", fontSize = 28.sp, ...)`.

- [ ] **Step 2: Map remaining inline text styles** to `AppType`:
  - `fontSize = 36.sp` calorie → `style = AppType.displayLarge`
  - `fontSize = 22.sp` → `style = AppType.statValue`
  - `fontSize = 16.sp` stat tile values → `style = AppType.statValueSmall`
  - `fontSize = 13.sp` labels → `style = AppType.body`
  - inline `Text("TODAY", fontSize = 9.sp, ...)` → `SectionLabel("Today")` (or `AppType.metaLabel` if it must stay inline within a card row)
  - `fontSize = 9.sp` macro/zone labels → `style = AppType.metaLabel`

- [ ] **Step 3: Keep cards as `FrostedCard`/`TintedCard` (already correct).** Replace the
  motivational message raw `RoundedCornerShape(16.dp)` box only if it duplicates `FrostedCard`;
  otherwise leave (it's an intentional gradient hero).

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "refactor(ui): migrate Dashboard to design system"
```
Then: **ask the user to visually verify Dashboard before continuing.**

---

### Task 6: Migrate FoodScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`

- [ ] **Step 1: Header** — replace the inline `Text("Food Log", fontSize = 28.sp, ...)` block
  with `ScreenHeader(title = "Food Log", subtitle = dateStr, trailing = { /* date nav / planning badge */ })`.
  Keep the day-navigation row as its own item below the header.
- [ ] **Step 2: Typography map** —
  - `22.sp` calorie total → `AppType.statValue`
  - `13.sp` macro values → `AppType.body`; `12.sp` slot name → `AppType.cardSubtitle`
  - `9.sp` entry name → `AppType.metaLabel` where it's a caption, else `AppType.label`
  - inline `Text("MEALS", fontSize = 9.sp, ...)` → `SectionLabel("Meals")`
  - the "PLANNING" badge keeps `AppType.metaLabel`.
- [ ] **Step 3: Buttons/icons** — confirm/postpone/delete raw `Box` buttons stay (inline row
  actions) but ensure they use `CornerSmall`. Replace text-glyph icons (`✓ ✎ ⤍ ✕ ＋`) with
  Material icons (`Check`, `Edit`, `Close`, `Add`) where they are affordances.
- [ ] **Step 4: Compile** — `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
git commit -m "refactor(ui): migrate FoodScreen to design system"
```
Then: **ask the user to visually verify FoodScreen.**

---

### Task 7: Migrate BodyRecoveryScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt`

- [ ] **Step 1:** Replace the private `ScreenHeader(state)` (line ~209) with shared
  `ScreenHeader(title = "Body", subtitle = dateStr)`. Delete the private one.
- [ ] **Step 2: Typography map** — `36.sp` metric → `displayLarge`; `19.sp` tile value →
  `statValueSmall`; `13.sp` unit/history title → `body`/`cardTitle`; `9.sp` metric label →
  `metaLabel`; the inline "LATEST CHECK-IN ·" label → `SectionLabel(...)`.
- [ ] **Step 3:** Cards already `FrostedCard`/`NeutralCard` — keep. `LiquidPrimaryButton` stays.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt
git commit -m "refactor(ui): migrate BodyRecovery to design system"
```
Then: **ask the user to visually verify Body.**

---

### Task 8: Migrate ProgressScreen (Trends)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt`

- [ ] **Step 1:** This screen has a back button → it is reached from More, so it is a tier-2
  sub-screen. Replace the inline header + 40dp back box with
  `SubScreenHeader(title = "Trends", onBack = onBack)`.
- [ ] **Step 2: Typography map** — `26.sp` featured value → `statValue`; `20.sp` mini value →
  `statValueSmall`; `12.sp` units → `cardSubtitle`; `9.sp`/`8.sp` chart captions → `metaLabel`.
  `SectionLabel` already used — keep.
- [ ] **Step 3:** `MiniChartCard` raw box → `NeutralCard`. `RangeSelector` raw `Box` buttons
  keep (segmented control) but use `CornerSmall`.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt
git commit -m "refactor(ui): migrate Progress/Trends to design system"
```
Then: **ask the user to visually verify Trends.**

---

### Task 9: Migrate MoreScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt`

- [ ] **Step 1:** Tier-1 tab destination. Replace the inline `Column` header with
  `ScreenHeader(title = "More", subtitle = "Insights & setup")`. Wrap content in `ScreenScaffold`.
- [ ] **Step 2: Typography map** — menu titles `14.sp SemiBold` → `cardTitle`; details
  `11.sp` → `label`; verdict number `30.sp Black` → `statValue` (or keep a one-off
  `displayLarge` if 30 reads better — prefer `statValue` for consistency); subtitle `12.sp` →
  `cardSubtitle`. `SectionLabel` already used.
- [ ] **Step 3:** `MenuCard` raw box → `NeutralCard`. Chevron `Text("›", 16.sp)` →
  `Icon(Icons.Default.ChevronRight, ...)`.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt
git commit -m "refactor(ui): migrate More to design system"
```
Then: **ask the user to visually verify More.**

---

## PHASE 3 — Train screens

> These are the biggest outliers (small/light titles, manual `padding(bottom=…)` spacing).
> All Train sub-screens (Active, Summary, Detail, RoutineBuilder, ExercisePicker, Stats) are
> tier-2 → use `SubScreenHeader`. TrainHome is the tab destination → tier-1 `ScreenHeader`.

### Task 10: Migrate TrainHomeScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt`

- [ ] **Step 1:** Replace `Text("Train", fontSize = 21.sp, FontWeight.Medium)` header with
  `ScreenHeader(title = "Train", trailing = { /* 34dp create-routine FAB → keep as trailing action */ })`.
- [ ] **Step 2:** Change card horizontal padding from `14.dp` to `ScreenPaddingH` (16) and
  header from `16.dp` to match (now handled by scaffold). Replace manual `padding(bottom = 12/14.dp)`
  gaps with `ScreenSpacing` via `ScreenScaffold` where the list allows.
- [ ] **Step 3: Typography map** — routine name `16.sp` → `cardTitle`; counts `11.sp` →
  `label`; the `"MY ROUTINES · N"` / month / `"BY MUSCLE"` labels (`11–12.sp Normal 0.4`) →
  `SectionLabel(...)`. History decorators (`⏱ ▦ ◆`) → Material icons (`Timer`, `Numbers`/`ViewModule`, `FitnessCenter`) or keep if intentional; prefer icons.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt
git commit -m "refactor(ui): migrate TrainHome to design system"
```
Then: **ask the user to visually verify Train.**

---

### Task 11: Migrate ActiveSessionScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt`

- [ ] **Step 1:** Header is a pinned Row (minimize · title · timer · finish · menu). Keep the
  pinned structure but render the title via `AppType.screenTitleCompact`. (Do not force
  `SubScreenHeader` here — this header has a custom action cluster; just apply the type token
  and standard `vertical = 14.dp` padding.)
- [ ] **Step 2: Typography map** — workout title `19.sp Bold` → `screenTitleCompact`; timer
  `16.sp` → `statValueSmall`; note placeholder/input `13.sp` → `body`; `"SESSION NOTES"`
  label `9.sp 0.4 textSecondary` → `SectionLabel("Session notes")`; unfinished-sets overlay
  title `18.sp` → `screenTitleCompact`.
- [ ] **Step 3:** Card padding `14.dp` → `ScreenPaddingH`. `LiquidGlassButton` heights → use
  48 (add exercise) / 40 (finish) per standard; leave if already so.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt
git commit -m "refactor(ui): migrate ActiveSession to design system"
```
Then: **ask the user to visually verify Active Session.**

---

### Task 12: Migrate SessionSummaryScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryScreen.kt`

- [ ] **Step 1:** The centered completion header is a deliberate one-off (flag icon + centered
  title). Keep it centered but render title via `AppType.screenTitleCompact` and subtitle via
  `AppType.screenSubtitle`. Change header horizontal padding `20.dp` → `ScreenPaddingH`.
- [ ] **Step 2: Typography map** — stat value `22.sp` → `statValue`; stat label `9.sp 0.4` →
  `metaLabel`; exercise name `14.sp SemiBold` → `cardTitle`; subtitle `12.sp` → `cardSubtitle`;
  `"PR"` badge `10.sp` → keep inline (badge). `SectionLabel` already used — keep.
- [ ] **Step 3:** Card padding `14.dp` → `ScreenPaddingH`. `LiquidGlassButton` save height 50 →
  48; Discard `TextButton` keep.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryScreen.kt
git commit -m "refactor(ui): migrate SessionSummary to design system"
```
Then: **ask the user to visually verify Session Summary.**

---

### Task 13: Migrate SessionDetailScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/SessionDetailScreen.kt`

- [ ] **Step 1:** Replace the `34dp RoundedCornerShape(100)` back box + `Text(workoutName, 18.sp SemiBold)`
  with `SubScreenHeader(title = state.workoutName, subtitle = <existing subtitle string>, onBack = onBack)`.
- [ ] **Step 2: Typography map** — exercise title `15.sp SemiBold` → `cardTitle`; sparkline /
  "Not enough data" `12.sp` → `cardSubtitle`; PR badge keep inline.
- [ ] **Step 3:** Card padding `14.dp` → `ScreenPaddingH`.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/SessionDetailScreen.kt
git commit -m "refactor(ui): migrate SessionDetail to design system"
```
Then: **ask the user to visually verify Session Detail.**

---

### Task 14: Migrate RoutineBuilderScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/RoutineBuilderScreen.kt`

- [ ] **Step 1:** Header is a close-button + title + Save-button cluster. Render title via
  `AppType.screenTitleCompact`; keep the `Close` `IconButton` and the `LiquidGlassButton` Save
  (height 36). Change header horizontal padding `8.dp` → `ScreenPaddingH`.
- [ ] **Step 2: Typography map** — `"NOTE (OPTIONAL)"` / `"EXERCISES · N"` labels → `SectionLabel(...)`;
  empty-state `13.sp` → `body`. Form fields use `GlassInputField`/`GlassTextArea` — keep.
- [ ] **Step 3:** Card/field padding `14.dp` → `ScreenPaddingH`.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/RoutineBuilderScreen.kt
git commit -m "refactor(ui): migrate RoutineBuilder to design system"
```
Then: **ask the user to visually verify Routine Builder.**

---

### Task 15: Migrate ExercisePickerScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ExercisePickerScreen.kt`

- [ ] **Step 1:** Header is close-button + title. Render title via `AppType.screenTitleCompact`,
  keep the `Close` `IconButton`. Header horizontal padding `8.dp` → `ScreenPaddingH`.
- [ ] **Step 2: Typography map** — exercise name `14.sp Medium` → `cardTitle`; subtitle `12.sp`
  → `cardSubtitle`; empty-state `15.sp` → `cardTitle`; filter chip `12.sp` → `label`.
- [ ] **Step 3:** Card/row padding `14.dp` → `ScreenPaddingH`. Sticky "Add N" `LiquidGlassButton`
  keep (height 48).
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ExercisePickerScreen.kt
git commit -m "refactor(ui): migrate ExercisePicker to design system"
```
Then: **ask the user to visually verify Exercise Picker.**

---

### Task 16: Migrate ExerciseStatsScreen

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ExerciseStatsScreen.kt`

- [ ] **Step 1:** Replace the `22.dp` `ArrowBack` icon + `Text(exerciseName, 19.sp SemiBold)`
  with `SubScreenHeader(title = state.exerciseName, onBack = onBack)`. Header horizontal
  padding `12.dp` → `ScreenPaddingH`.
- [ ] **Step 2: Typography map** — stat value `17.sp Bold` → `statValueSmall`; stat label
  `9.sp 0.6` → `metaLabel`; PR value `15.sp` → `statValueSmall`/`cardTitle`; PR label `8.sp` →
  `metaLabel`; recent date `13.sp` → `cardTitle`. `SectionLabel` already used.
- [ ] **Step 3:** Card padding `11–14.dp` → `ScreenPaddingH` (or keep 14 inside FrostedCard).
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ExerciseStatsScreen.kt
git commit -m "refactor(ui): migrate ExerciseStats to design system"
```
Then: **ask the user to visually verify Exercise Stats.**

---

## PHASE 4 — Settings / profile

> All tier-2 sub-screens (reached from More). They already use `28sp ExtraBold` titles and
> `40dp CircleShape` back buttons → swap to `SubScreenHeader` (which standardizes them to the
> compact 20sp tier). Profile/Appearance/Integrations/DataBackup already use `ScreenScaffold`-
> shaped LazyColumns and `SectionLabel`.

### Task 17: Migrate ProfileScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/profile/ProfileScreen.kt`

- [ ] **Step 1:** Replace inline header (`Text("Profile", 28.sp …)` + 40dp back box) with
  `SubScreenHeader(title = "Profile", onBack = onBack)`. Wrap in `ScreenScaffold(withNavBarInset = true)`
  (Profile keeps the nav bar — confirm against current `FloatingNavHeight + 16` bottom inset).
- [ ] **Step 2: Typography map** — current weight `22.sp ExtraBold` → `statValue`; picker label
  `14.sp SemiBold` → `cardTitle`; subtitle `11.sp` → `label`. `SectionLabel` already used.
- [ ] **Step 3:** `PickerRow` keep (uses `CornerSmall`). Chevron `18.sp` → `Icon(ChevronRight)`.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/profile/ProfileScreen.kt
git commit -m "refactor(ui): migrate Profile to design system"
```
Then: **ask the user to visually verify Profile.**

---

### Task 18: Migrate AppearanceScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/appearance/AppearanceScreen.kt`

- [ ] **Step 1:** Inline header → `SubScreenHeader(title = "Appearance", onBack = onBack)`.
- [ ] **Step 2: Typography map** — preview label `18.sp ExtraBold` → `screenTitleCompact` or
  `statValueSmall`; description `12.sp` → `cardSubtitle`. `SectionLabel` already used.
- [ ] **Step 3:** Keep `TintedCard` preview + `LiquidPrimaryButton`.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/appearance/AppearanceScreen.kt
git commit -m "refactor(ui): migrate Appearance to design system"
```
Then: **ask the user to visually verify Appearance.**

---

### Task 19: Migrate IntegrationsScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/integrations/IntegrationsScreen.kt`

- [ ] **Step 1:** Inline header → `SubScreenHeader(title = "Integrations", onBack = onBack)`.
- [ ] **Step 2: Typography map** — `SettingRow` title `14.sp SemiBold` → `cardTitle`; detail
  `11.sp` → `label`. `SectionLabel` already used.
- [ ] **Step 3:** `SettingsCard` → confirm it wraps `NeutralCard`; if it's a bespoke raw box,
  re-point it at `NeutralCard`. `Liquid*Button` family keep.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/integrations/IntegrationsScreen.kt
git commit -m "refactor(ui): migrate Integrations to design system"
```
Then: **ask the user to visually verify Integrations.**

---

### Task 20: Migrate DataBackupScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/databackup/DataBackupScreen.kt`

- [ ] **Step 1:** Inline header → `SubScreenHeader(title = "Data & Backup", onBack = onBack)`.
- [ ] **Step 2: Typography map** — `DataRow` title `14.sp SemiBold` → `cardTitle`; detail
  `11.sp` → `label`; chevron `18.sp` → `Icon(ChevronRight)`. `SectionLabel` already used.
- [ ] **Step 3:** `SettingsCard` → `NeutralCard`; `DangerCard` keep (semantic red). Icon boxes
  `34dp/RoundedCornerShape(10)` keep.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/databackup/DataBackupScreen.kt
git commit -m "refactor(ui): migrate DataBackup to design system"
```
Then: **ask the user to visually verify Data & Backup.**

---

### Task 21: Migrate PlanScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt`

- [ ] **Step 1:** Replace the `40dp` back box + `Text("Plan", style = headlineMedium, Bold)` +
  subtitle with `SubScreenHeader(title = "Plan", subtitle = "Targets and review thresholds", onBack = onBack)`.
  Change root `LazyColumn` padding `16.dp` + `spacedBy(14.dp)` → `ScreenScaffold(withNavBarInset = false)`
  (uses `ScreenSpacing` 10).
- [ ] **Step 2: Typography** — replace `MaterialTheme.typography.headlineMedium` title usage;
  `GenerateHeroCard` keep (gradient hero). `SectionLabel` already used.
- [ ] **Step 3:** `LiquidPrimaryButton`/`LiquidSecondaryButton` keep.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt
git commit -m "refactor(ui): migrate Plan to design system"
```
Then: **ask the user to visually verify Plan.**

---

## PHASE 5 — Food / library

### Task 22: Migrate FoodLibraryScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 1:** Replace `FoodLibraryTopBar` (`34dp` back box + `Text(17.sp ExtraBold -0.3)`)
  with `SubScreenHeader(title = "Food Library", onBack = onBack, trailing = { /* search / camera actions */ })`.
  Header padding `horizontal = 16.dp` → `ScreenPaddingH`.
- [ ] **Step 2: Typography map** — food name `13.sp SemiBold` → `cardTitle` (note: drops to 15;
  if rows feel too tall keep `body`+SemiBold via a local style — prefer `cardTitle`); macro
  `10.sp` → `metaLabel`; calories `12.sp Bold` → `statValueSmall` only if it's a feature number,
  else `label`; category chip `11.sp` → `label`. `"LEGACY SAVED MEALS"` inline → `SectionLabel`.
- [ ] **Step 3:** Grouped row boxes (corner logic for first/last) keep — this is an intentional
  grouped-list pattern; ensure they use `appColors.cardSurface`/`cardBorder` + `CornerCard`.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
git commit -m "refactor(ui): migrate FoodLibrary to design system"
```
Then: **ask the user to visually verify Food Library.**

---

### Task 23: Migrate FoodsScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/foods/FoodsScreen.kt`

- [ ] **Step 1:** Title is an in-list `Text(style = headlineMedium, Bold)`. Replace with
  `SubScreenHeader`-style header if the screen has a back affordance, or a tier-1 `ScreenHeader`
  if it's a tab; from navigation it is a pushed screen → `SubScreenHeader(title = "Foods & Meals",
  subtitle = "Saved shortcuts for faster logging", onBack = onBack)`. Root `LazyColumn` `16.dp` +
  `spacedBy(14.dp)` → `ScreenScaffold(withNavBarInset = false)`.
- [ ] **Step 2: Typography** — replace default-size `Text`s for food name/serving/macros with
  `cardTitle`/`cardSubtitle`/`metaLabel`. Replace `headlineMedium` section headers with `SectionLabel`.
- [ ] **Step 3:** `SectionCard` → `NeutralCard`/`FrostedCard`. Replace `OutlinedTextField` with
  `GlassInputField` for visual consistency. `LiquidPrimaryButton`/`LiquidActionButton` keep.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foods/FoodsScreen.kt
git commit -m "refactor(ui): migrate Foods to design system"
```
Then: **ask the user to visually verify Foods.**

---

### Task 24: Migrate RecipeBuilderScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderScreen.kt`

- [ ] **Step 1:** Top bar (`34dp` back box + `Text(17.sp ExtraBold)`) → `SubScreenHeader(title =
  "Recipe", onBack = onBack, trailing = { /* AI generate name button */ })`.
- [ ] **Step 2: Typography map** — ingredient name `13.sp SemiBold` → `cardTitle`; details
  `11.sp` → `label`; delete glyph `"✕"` → `Icon(Icons.Default.Delete)` or `Close`.
- [ ] **Step 3:** Grouped ingredient rows keep. `LiquidPrimaryButton` Save keep.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/recipes/RecipeBuilderScreen.kt
git commit -m "refactor(ui): migrate RecipeBuilder to design system"
```
Then: **ask the user to visually verify Recipe Builder.**

---

### Task 25: Migrate BarcodeScannerScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerScreen.kt`

- [ ] **Step 1:** Full-screen camera. Keep the `TopStart` back `IconButton` but swap to the
  shared `BackButton` for visual consistency (on a scrim). No `ScreenScaffold` (overlay layout).
- [ ] **Step 2: Typography map** — product sheet name `titleLarge Bold` → `screenTitleCompact`;
  macro chip value `13.sp Bold` → `statValueSmall`; macro label `11.sp` → `metaLabel`; the
  hardcoded `Color(0xFF6b7280)` label color → `appColors.textMuted`.
- [ ] **Step 3:** Product sheet padding `horizontal = 20.dp` → `ScreenPaddingH`.
  `LiquidPrimaryButton`/`LiquidSecondaryButton` keep.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerScreen.kt
git commit -m "refactor(ui): migrate BarcodeScanner to design system"
```
Then: **ask the user to visually verify Scanner.**

---

## PHASE 6 — Coach / onboarding / body

### Task 26: Migrate CoachScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt`

- [ ] **Step 1:** Header (`Text("...", 22.sp Bold)` + `AiBadge`) → keep `AiBadge` in `trailing`,
  render title via `AppType.screenTitleCompact` (this is a pushed screen with an AI badge — use
  a `SubScreenHeader` if there's a back affordance, else a tier-1-styled header with the badge).
  Header padding `horizontal = 16.dp, vertical = 18.dp` → `ScreenPaddingH` + `vertical = 18`.
- [ ] **Step 2: Typography map** — chat message `14.sp` → `body` (note 14→13; if chat
  readability suffers, define a local 14sp via `AppType.body.copy(fontSize = 14.sp)` — prefer
  plain `body`); empty-state `15.sp` → `cardTitle`; suggestion `14.sp` → `body`.
- [ ] **Step 3:** Chat bubbles keep (Surface + AiInsightCard). `FilterChip` keep. Content
  padding `horizontal = 24.dp` → `ScreenPaddingH` (unless the wider chat gutter is intentional;
  keep 24 only for the message column if needed).
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/coach/CoachScreen.kt
git commit -m "refactor(ui): migrate Coach to design system"
```
Then: **ask the user to visually verify Coach.**

---

### Task 27: Migrate AiCoachScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachScreen.kt`

- [ ] **Step 1:** Replace `40dp` back box + `Text("...", 28.sp ExtraBold -0.8)` with
  `SubScreenHeader(title = "AI Coach", onBack = onBack)`.
- [ ] **Step 2: Typography map** — "Enable AI" `15.sp Bold` → `cardTitle`; subtitle `11.sp` →
  `label`; field labels `13.sp` → `body`. `SectionLabel` already used.
- [ ] **Step 3:** **Replace the Material `Button` (save/clear key) with `LiquidPrimaryButton`/
  `LiquidActionButton`.** `TintedCard` keep.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachScreen.kt
git commit -m "refactor(ui): migrate AiCoach to design system"
```
Then: **ask the user to visually verify AI Coach settings.**

---

### Task 28: Migrate OnboardingScreen

**Files:** Modify `app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1:** Onboarding is a standalone flow (no nav bar). Keep `padding(horizontal = 20.dp)`
  → change to `ScreenPaddingH` for consistency (note: tightens 20→16). Step progress label
  `10.sp 2.sp` keep as a one-off (it's a deliberate tracking label) or map to `metaLabel`.
- [ ] **Step 2: Typography map** — `StepHeader` title `21.sp Bold` → `screenTitleCompact`;
  subtitle `13.sp` → `screenSubtitle`; macro tile value `17.sp` → `statValueSmall`; macro label
  `9.sp 1.sp` → `metaLabel`; main calorie `44.sp ExtraBold` → `displayHero`. `SectionLabel` keep.
- [ ] **Step 3:** `FrostedCard` keep. `LiquidPrimaryButton`/`LiquidSecondaryButton` keep.
  `PillToggle` keep.
- [ ] **Step 4: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/onboarding/OnboardingScreen.kt
git commit -m "refactor(ui): migrate Onboarding to design system"
```
Then: **ask the user to visually verify Onboarding.**

---

### Task 29: Migrate BodyEditScreen + BodyHistoryScreen (TopAppBar → SubScreenHeader)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/body/BodyEditScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/body/BodyHistoryScreen.kt`

- [ ] **Step 1 (BodyEdit):** Remove the `Scaffold` + Material `TopAppBar`. Use `ScreenScaffold(withNavBarInset = false)`
  with a first item `SubScreenHeader(title = state.date.format(HEADER_FMT), subtitle = "Past check-in", onBack = onBack)`.
  Replace `SectionCard("Check-in for …")` with `FrostedCard` + a `SectionLabel`.
- [ ] **Step 2 (BodyEdit):** Form fields via `BodyCheckInFormContent` — leave its internals, but
  ensure any inline `Text` uses `AppType`.
- [ ] **Step 3 (BodyHistory):** Remove the `Scaffold` + `TopAppBar`. Header item
  `SubScreenHeader(title = "Check-in History", onBack = onBack)`. Replace the
  `Surface(primaryContainer)` "+ Add" button with `LiquidActionButton(text = "Add", isPrimary = true, small = true)`.
  Map row text: date `bodySmall` → `cardSubtitle`; summary `bodyMedium` → `cardTitle`; detail
  `bodySmall` → `label`; edit link → `label` accent. Keep the emoji in the detail string (content).
- [ ] **Step 4: Compile** → `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/body/BodyEditScreen.kt app/src/main/java/com/zack/recomptracker/ui/body/BodyHistoryScreen.kt
git commit -m "refactor(ui): migrate Body edit/history off TopAppBar to design system"
```
Then: **ask the user to visually verify Body Edit + History.**

---

### Task 30: Migrate Weekly Review overlay

**Files:** Modify the review screen file under `app/src/main/java/com/zack/recomptracker/ui/review/` (e.g. `WeeklyBriefingOverlay.kt`).

- [ ] **Step 1:** This is a modal `Dialog`, not a screen — no `ScreenScaffold`. Keep
  `BriefingGlassCard`. Map text: headline `17.sp ExtraBold -0.3` → `statValueSmall` (or keep a
  one-off if the AI hero needs it); narrative `14.sp` → `body`; signal label `13.sp SemiBold` →
  `cardTitle`/`body`; signal value `13.sp Bold` → `body` accent; interpretation `12.5.sp` →
  `cardSubtitle`. `"WEEKLY REVIEW"` `9.sp 0.14` → `SectionLabel`.
- [ ] **Step 2:** `BriefingPrimaryButton` keep (modal-specific). Direction glyphs `↑ ↓ →`
  keep (content/semantics).
- [ ] **Step 3: Compile** → BUILD SUCCESSFUL.
- [ ] **Step 4: Commit + hand off**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/review/
git commit -m "refactor(ui): align Weekly Review overlay typography to AppType"
```
Then: **ask the user to visually verify the Weekly Review overlay.**

---

## PHASE 7 — Documentation

### Task 31: Write the durable design guide

**Files:**
- Create: `docs/design-system.md`
- Modify: `CLAUDE.md` (add a link under a new "Design System" section)

- [ ] **Step 1: Create `docs/design-system.md`** with these sections (fill with the real values
  from `AppType`, `DesignTokens`, and the components — no placeholders):
  1. **Principles** — use tokens/components, never inline `fontSize`; two-tier headers; one card
     family; one button family.
  2. **Typography** — the full `AppType` table (token → size/weight/spacing → when to use).
  3. **Color** — `LocalAppColors` tokens (textPrimary/Muted/…); rule: pass color at call site.
  4. **Spacing & layout** — `ScreenPaddingH`, `ScreenSpacing`, `Spacing.*`, corner radii.
  5. **Screen anatomy** — `ScreenScaffold` + tier-1 `ScreenHeader` (tab destinations) vs tier-2
     `SubScreenHeader` (pushed screens); when to use each; the canonical `BackButton`.
  6. **Cards** — `FrostedCard` (primary/featured) vs `NeutralCard` (rows/forms) vs `TintedCard`
     (AI) vs `DangerCard` (destructive). Don't hand-roll `.background/.border` boxes.
  7. **Buttons** — `LiquidPrimaryButton` / `LiquidSecondaryButton` / `LiquidActionButton` /
     `LiquidStepButton`; standard heights 48 / 36 / 32. No Material `Button` / `Surface` buttons.
  8. **Inputs** — `GlassInputField` / `GlassTextArea` / `ScoreStepper` / `VioletSlider` / `VioletToggle`.
  9. **Section labels** — always `SectionLabel("…")`.
  10. **Icons** — Material icons for affordances; emoji only for content. Standard chevron.
  11. **Do / Don't** — short before→after code snippets (inline `fontSize` ❌ → `AppType` ✅;
      raw card box ❌ → `FrostedCard` ✅; Material `TopAppBar` ❌ → `SubScreenHeader` ✅).

- [ ] **Step 2: Link from `CLAUDE.md`** — add under the layer structure section:

```markdown
## Design System

@docs/design-system.md

All UI must use the shared design tokens and components. Never hardcode `fontSize`/
`fontWeight` — use `AppType`. Use `ScreenScaffold` + `ScreenHeader`/`SubScreenHeader` for
screen frames, the `FrostedCard`/`NeutralCard` family for cards, and the `Liquid*Button`
family for buttons.
```

- [ ] **Step 3: Commit**

```bash
git add docs/design-system.md CLAUDE.md
git commit -m "docs: add design-system guide + link from CLAUDE.md"
```

---

## Self-review notes

- **Spec coverage:** every spec section maps to a task — typography (T1), spacing (T2),
  SectionLabel (T3), scaffold/headers (T4), all 27 screen migrations (T5–T30 incl. Body
  TopAppBar removal and AiCoach/BodyHistory button replacement), design guide + CLAUDE.md (T31).
- **Verification:** compile-gated + per-screen visual handoff (project has no composable tests;
  CLAUDE.md convention = user verifies visually). Stated up front.
- **Naming consistency:** `AppType`, `ScreenScaffold`, `ScreenHeader`, `SubScreenHeader`,
  `BackButton`, `ScreenPaddingH`, `ScreenSpacing`, `Spacing` used identically across all tasks.
- **Known judgement calls flagged inline:** a few size drops (food-row name 13→15, chat 14→13)
  are called out with a fallback (`AppType.body.copy(fontSize = …)`) so the implementer can
  preserve readability if visual verification flags it.
