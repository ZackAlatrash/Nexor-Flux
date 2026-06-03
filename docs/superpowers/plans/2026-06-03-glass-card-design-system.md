# Glass Card Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardise every card and chrome element to one of four glass tiers (Neutral, M3 Frosted, Tinted reserved, Liquid), eliminating five different corner radii and all scattered inline card implementations.

**Architecture:** New tokens land in `DesignTokens.kt`. `GlassComponents.kt` becomes the single source of truth with three card composables (`NeutralCard`, `FrostedCard`, `TintedCard`). All screens replace inline implementations and deprecated composables (`FeaturedCard`, `GlassSurfaceCard`) with the new ones. Nav pill and interactive buttons use the FletchMcKee/liquid library for Liquid Glass.

**Tech Stack:** Jetpack Compose, Kotlin, FletchMcKee/liquid (Compose Liquid Glass library), Gradle version catalogs (`libs.versions.toml`)

**Spec:** `docs/superpowers/specs/2026-06-03-card-design-system-design.md`
**Usage guide:** `docs/GlassSystem.md`

---

### Task 1: Add design tokens to DesignTokens.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/theme/DesignTokens.kt`

- [ ] **Step 1: Add corner radius scale and frosted surface tokens**

Open `DesignTokens.kt` and append at the bottom:

```kotlin
import androidx.compose.ui.unit.dp

// ── Corner radius scale ────────────────────────────────────────────────────────
val CornerSmall = 10.dp   // inputs, step buttons, small controls
val CornerCard  = 16.dp   // all content cards — Neutral, Frosted, Tinted
val CornerChip  = 20.dp   // badges, pill indicators, tag chips
val CornerPill  = 100.dp  // Liquid Glass elements only (nav, buttons, modals)

// ── M3 Frosted Blur surface tokens ────────────────────────────────────────────
// rgba(18,10,32, 0.62) — dark frosted fill
val FrostedSurface         = Color(0x9E120A20)
// rgba(255,255,255, 0.13) — hairline white border
val FrostedBorder          = Color(0x21FFFFFF)
// rgba(22,14,38, 0.82) — used on API < 31 where blur is unavailable
val FrostedSurfaceFallback = Color(0xD1160E26)
```

- [ ] **Step 2: Verify the file compiles**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/theme/DesignTokens.kt
git commit -m "feat(tokens): add corner radius scale and frosted surface tokens"
```

---

### Task 2: Implement NeutralCard, FrostedCard, and TintedCard in GlassComponents.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt`

- [ ] **Step 1: Add new imports at the top of GlassComponents.kt**

The file already imports `Brush`, `Color`, `drawBehind`. Add the missing ones to the existing import block:

```kotlin
import androidx.compose.ui.geometry.Offset
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.FrostedBorder
import com.zack.recomptracker.ui.theme.FrostedSurface
import com.zack.recomptracker.ui.theme.FrostedSurfaceFallback
```

- [ ] **Step 2: Add NeutralCard directly after the file-level comment block (before FeaturedCard)**

```kotlin
// ── Neutral Card (workhorse — list rows, menus, form containers) ──────────────

@Composable
fun NeutralCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(CornerCard))
            .padding(16.dp),
        content = content,
    )
}
```

- [ ] **Step 3: Add FrostedCard after NeutralCard**

```kotlin
// ── Frosted Card (M3 Expressive — primary data cards, featured charts) ────────

@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FrostedSurface
    } else {
        FrostedSurfaceFallback
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .drawBehind {
                // Dark frosted fill
                drawRect(color = surface)
                // Top-edge catchlight — the glass shimmer
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x4CFFFFFF),
                            Color(0x4CFFFFFF),
                            Color.Transparent,
                        ),
                        startX = size.width * 0.12f,
                        endX   = size.width * 0.88f,
                    ),
                    start       = Offset(0f, 0.75f),
                    end         = Offset(size.width, 0.75f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .border(1.dp, FrostedBorder, RoundedCornerShape(CornerCard))
            .padding(16.dp),
        content = content,
    )
}
```

Add the missing import for `Build`:
```kotlin
import android.os.Build
```

- [ ] **Step 4: Add TintedCard after FrostedCard**

```kotlin
// ── Tinted Card (reserved — AI features only, zero call sites) ────────────────

@Composable
fun TintedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .drawBehind {
                drawRect(color = FeaturedSurface)
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            FeaturedBorder,
                            FeaturedBorder,
                            Color.Transparent,
                        ),
                        startX = size.width * 0.10f,
                        endX   = size.width * 0.90f,
                    ),
                    start       = Offset(0f, 0.75f),
                    end         = Offset(size.width, 0.75f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .border(1.dp, FeaturedBorder, RoundedCornerShape(CornerCard))
            .padding(16.dp),
        content = content,
    )
}
```

- [ ] **Step 5: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt
git commit -m "feat(glass): add NeutralCard, FrostedCard, TintedCard composables"
```

---

### Task 3: Add FletchMcKee/liquid dependency

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

> **Before coding:** Open https://github.com/FletchMcKee/liquid — read the README to find:
> 1. The exact Maven group, artifact name, and version
> 2. Whether it's on Maven Central or JitPack only
> 3. The composable or modifier API (e.g., `LiquidGlass {}`, `.liquidGlass()`, etc.)
> Note these down — you will need them in the steps below.

- [ ] **Step 1: If the library is JitPack-only, add JitPack to settings.gradle.kts**

In `settings.gradle.kts`, inside `dependencyResolutionManagement { repositories { ... } }`, add:

```kotlin
maven("https://jitpack.io")
```

Full block after change:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Skip this step if the library is on Maven Central.

- [ ] **Step 2: Add the library to gradle/libs.versions.toml**

In the `[versions]` section, add (use the actual version from the README):
```toml
liquid = "<version-from-readme>"
```

In the `[libraries]` section, add (use the actual group/artifact from the README):
```toml
liquid-glass = { group = "<group-from-readme>", name = "<artifact-from-readme>", version.ref = "liquid" }
```

- [ ] **Step 3: Add the dependency to app/build.gradle.kts**

In the `dependencies { }` block, add:
```kotlin
implementation(libs.liquid.glass)
```

- [ ] **Step 4: Sync Gradle and verify no errors**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add FletchMcKee/liquid dependency for Liquid Glass"
```

---

### Task 4: Migrate nav pill to Liquid Glass in RecompApp.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`

> **Before coding:** Check the FletchMcKee/liquid README for the exact API. The examples below show the most common pattern for Compose glass libraries — adjust to match the actual API.

- [ ] **Step 1: Add imports for the liquid library and CornerPill token**

At the top of `RecompApp.kt`, add:
```kotlin
// Add the library's import — exact package from README, e.g.:
// import io.github.fletchmckee.liquid.LiquidGlass
// or
// import io.github.fletchmckee.liquid.liquidGlass
import com.zack.recomptracker.ui.theme.CornerPill
```

Remove no-longer-needed imports after the migration:
```kotlin
// Remove these — no longer used after replacing drawBehind:
import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
```

- [ ] **Step 2: Replace the drawBehind glass painting in CompactPillNav**

Find the `CompactPillNav` composable. The current modifier chain on the `Row` is:

```kotlin
modifier = modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp, vertical = 16.dp)
    .drawBehind {
        val r = 20.dp.toPx()
        // ... shadow, fill, luminance wash, border, catchlight drawing ...
    }
    .padding(horizontal = 2.dp),
```

Replace with (adjust to match the actual library API from the README):

**If the library provides a Modifier extension:**
```kotlin
modifier = modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp, vertical = 16.dp)
    .liquidGlass(shape = RoundedCornerShape(CornerPill))
    .padding(horizontal = 2.dp),
```

**If the library provides a composable wrapper:**
Wrap the `Row` body in the wrapper:
```kotlin
LiquidGlass(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 16.dp),
    shape = RoundedCornerShape(CornerPill),
) {
    Row(
        modifier = Modifier.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem(...)
        TabItem(...)
        LogTab(...)
        TabItem(...)
        TabItem(...)
    }
}
```

The design comment block above `CompactPillNav` references `20dp` radius — update it:
```kotlin
// ─────────────────────────────────────────────────────────────────────────────
// Liquid Glass Nav Pill — full pill shape (CornerPill = 100dp), Apple iOS 26 style
// Implemented via FletchMcKee/liquid library.
// ─────────────────────────────────────────────────────────────────────────────
```

- [ ] **Step 3: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Build and run on a device or emulator — check the nav pill looks correct**

```bash
./gradlew :app:installDebug 2>&1 | tail -10
```

Confirm: the nav pill is a full pill shape (semicircle ends), Liquid Glass effect visible.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt
git commit -m "feat(nav): migrate nav pill to Liquid Glass full-pill shape"
```

---

### Task 5: Migrate DashboardScreen.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Update imports**

Add:
```kotlin
import com.zack.recomptracker.ui.component.FrostedCard
```

Remove (no longer referenced after migration):
```kotlin
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.FeaturedBorder
import com.zack.recomptracker.ui.theme.FeaturedSurface
```

- [ ] **Step 2: Replace TodayCard's inline Column with FrostedCard**

Find the `TodayCard` composable. Replace:

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(CardSurface)
        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(0.dp),
) {
    // ... all existing content ...
}
```

With:

```kotlin
FrostedCard {
    // ... all existing content unchanged ...
}
```

- [ ] **Step 3: Replace SevenDayChartCard's inline Column with FrostedCard**

Find `SevenDayChartCard`. Replace:

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(FeaturedSurface)
        .border(1.dp, FeaturedBorder, RoundedCornerShape(20.dp))
        .padding(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(0.dp),
) {
    // ... all existing content ...
}
```

With:

```kotlin
FrostedCard {
    // ... all existing content unchanged ...
}
```

- [ ] **Step 4: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat(dashboard): migrate TodayCard and SevenDayChartCard to FrostedCard"
```

---

### Task 6: Migrate FoodScreen.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`

- [ ] **Step 1: Update imports**

Add:
```kotlin
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.NeutralCard
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerPill
import com.zack.recomptracker.ui.theme.FrostedBorder
import com.zack.recomptracker.ui.theme.FrostedSurface
// Also add the FletchMcKee/liquid import for the + Add button
// e.g.: import io.github.fletchmckee.liquid.liquidGlass  (exact name from README)
```

Remove:
```kotlin
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
```

- [ ] **Step 2: Replace NutritionStrip's inline Column with FrostedCard**

Find `NutritionStrip`. Replace:

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(CardSurface)
        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
        .padding(14.dp),
) {
    // ... all existing content ...
}
```

With:

```kotlin
FrostedCard {
    // ... all existing content unchanged ...
}
```

- [ ] **Step 3: Replace LockedSlotCard's inline container with NeutralCard tokens**

`LockedSlotCard` has a custom two-part structure (header + inset body) that can't use the composable wrapper directly. Update just the background/border/radius values in place.

Find:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(cardBg)
        .border(1.dp, cardBorder, RoundedCornerShape(16.dp)),
) {
```

The `cardBg` / `cardBorder` variables are:
```kotlin
val cardBg     = if (hasEntries) Color(0x0A8B5CF6) else CardSurface
val cardBorder = if (hasEntries) Color(0x2E8B5CF6) else CardBorder
```

Replace those two `val` declarations and the Column modifier with:
```kotlin
val cardBg     = if (hasEntries) Color(0x0A8B5CF6) else CardSurface
val cardBorder = if (hasEntries) Color(0x2E8B5CF6) else CardBorder

Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(CornerCard))
        .background(cardBg)
        .border(1.dp, cardBorder, RoundedCornerShape(CornerCard)),
) {
```

(Only the radius changes: `16.dp` → `CornerCard`; the conditional background/border is intentional and stays.)

- [ ] **Step 4: Replace EditModeSlotCard's container with NeutralCard tokens**

Find the `EditModeSlotCard` composable. Replace:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(CardSurface)
        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp),
```

With:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(CornerCard))
        .background(CardSurface)
        .border(1.dp, CardBorder, RoundedCornerShape(CornerCard))
        .padding(horizontal = 12.dp, vertical = 8.dp),
```

- [ ] **Step 5: Migrate the + Add button inside LockedSlotCard to Liquid Glass**

Find the `+ Add` button Box inside `LockedSlotCard`:

```kotlin
Box(
    modifier = Modifier
        .clip(RoundedCornerShape(10.dp))
        .background(Violet500)
        .clickable(onClick = onAddClick)
        .padding(horizontal = 12.dp, vertical = 5.dp),
) {
    Text("＋ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
}
```

Replace with (adjust modifier/composable name to match the FletchMcKee/liquid API):

```kotlin
Box(
    modifier = Modifier
        .clip(RoundedCornerShape(CornerSmall))
        .liquidGlass(shape = RoundedCornerShape(CornerSmall))  // ← library API
        .clickable(onClick = onAddClick)
        .padding(horizontal = 12.dp, vertical = 5.dp),
) {
    Text("＋ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
}
```

Also add:
```kotlin
import com.zack.recomptracker.ui.theme.CornerSmall
```

- [ ] **Step 6: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
git commit -m "feat(food): migrate cards to NeutralCard/FrostedCard tokens, Add button to Liquid Glass"
```

---

### Task 7: Migrate ProgressScreen.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt`

- [ ] **Step 1: Update imports**

Add:
```kotlin
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.NeutralCard
import com.zack.recomptracker.ui.theme.CornerCard
```

Remove:
```kotlin
import com.zack.recomptracker.ui.component.FeaturedCard
import com.zack.recomptracker.ui.component.GlassSurfaceCard
```

- [ ] **Step 2: Replace FeaturedChartCard to use FrostedCard**

Find `FeaturedChartCard`. Replace:

```kotlin
@Composable
private fun FeaturedChartCard(series: ChartSeries) {
    var scrubValue by remember { mutableStateOf<Float?>(null) }
    FeaturedCard {
        ChartHeader(series, overrideValue = scrubValue)
        Spacer(Modifier.height(10.dp))
        if (series.values.isNotEmpty() && series.values.any { it != 0f }) {
            SparklineChart(
                values = series.values,
                height = 72.dp,
                showGlowDot = true,
                showScrubber = true,
                onScrubValue = { scrubValue = it },
            )
        } else {
            NoDataLabel()
        }
    }
}
```

With:

```kotlin
@Composable
private fun FeaturedChartCard(series: ChartSeries) {
    var scrubValue by remember { mutableStateOf<Float?>(null) }
    FrostedCard {
        ChartHeader(series, overrideValue = scrubValue)
        Spacer(Modifier.height(10.dp))
        if (series.values.isNotEmpty() && series.values.any { it != 0f }) {
            SparklineChart(
                values = series.values,
                height = 72.dp,
                showGlowDot = true,
                showScrubber = true,
                onScrubValue = { scrubValue = it },
            )
        } else {
            NoDataLabel()
        }
    }
}
```

- [ ] **Step 3: Replace ShortChartCard to use NeutralCard**

Find `ShortChartCard`. Replace:

```kotlin
@Composable
private fun ShortChartCard(series: ChartSeries) {
    GlassSurfaceCard(cornerRadius = 18) {
        ChartHeader(series)
        Spacer(Modifier.height(8.dp))
        if (series.values.isNotEmpty() && series.values.any { it != 0f }) {
            SparklineChart(values = series.values, height = 44.dp, showGlowDot = false)
        } else {
            NoDataLabel()
        }
    }
}
```

With:

```kotlin
@Composable
private fun ShortChartCard(series: ChartSeries) {
    NeutralCard {
        ChartHeader(series)
        Spacer(Modifier.height(8.dp))
        if (series.values.isNotEmpty() && series.values.any { it != 0f }) {
            SparklineChart(values = series.values, height = 44.dp, showGlowDot = false)
        } else {
            NoDataLabel()
        }
    }
}
```

- [ ] **Step 4: Replace MiniChartCard's inline block with NeutralCard tokens**

`MiniChartCard` uses a half-width (`.weight(1f)`) modifier which `NeutralCard` won't apply. Update its modifier chain in place:

Find:
```kotlin
Column(
    modifier = modifier
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0x0AFFFFFF))
        .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(14.dp))
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
) {
```

Replace with:
```kotlin
Column(
    modifier = modifier
        .clip(RoundedCornerShape(CornerCard))
        .background(CardSurface)
        .border(1.dp, CardBorder, RoundedCornerShape(CornerCard))
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
) {
```

Add these imports:
```kotlin
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
```

- [ ] **Step 5: Replace RangeSelector buttons with NeutralCard tokens**

Find the two `Box` modifiers in `RangeSelector` (active and inactive states). Replace hardcoded values:

```kotlin
Box(
    modifier = Modifier
        .weight(1f)
        .clip(RoundedCornerShape(10.dp))           // ← change to CornerSmall
        .background(if (isActive) Color(0x338B5CF6) else Color(0x0DFFFFFF))
        .border(
            1.dp,
            if (isActive) Color(0x598B5CF6) else Color(0x12FFFFFF),
            RoundedCornerShape(10.dp),             // ← change to CornerSmall
        )
```

With:

```kotlin
Box(
    modifier = Modifier
        .weight(1f)
        .clip(RoundedCornerShape(CornerSmall))
        .background(if (isActive) Color(0x338B5CF6) else CardSurface)
        .border(
            1.dp,
            if (isActive) Color(0x598B5CF6) else CardBorder,
            RoundedCornerShape(CornerSmall),
        )
```

Add:
```kotlin
import com.zack.recomptracker.ui.theme.CornerSmall
```

- [ ] **Step 6: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt
git commit -m "feat(progress): migrate chart cards to FrostedCard and NeutralCard"
```

---

### Task 8: Migrate MoreScreen.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt`

- [ ] **Step 1: Update imports**

Add:
```kotlin
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.CornerCard
```

- [ ] **Step 2: Merge MenuCard and SettingsCard — they are identical**

`MenuCard` and `SettingsCard` have exactly the same implementation. Delete `SettingsCard` entirely. Then replace all usages of `SettingsCard { }` with `MenuCard { }` in the screen body. There are two call sites: Appearance section and App section.

Delete:
```kotlin
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}
```

Replace each `SettingsCard {` call with `MenuCard {`.

- [ ] **Step 3: Standardise MenuCard to use design tokens**

Replace the `MenuCard` implementation:

```kotlin
@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}
```

With:

```kotlin
@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(CornerCard)),
    ) {
        content()
    }
}
```

- [ ] **Step 4: Standardise DataActionCard to use design tokens**

Find `DataActionCard`. Replace:

```kotlin
Column(
    modifier = modifier
        .clip(RoundedCornerShape(12.dp))
        .background(if (isPrimary) Color(0x1F8B5CF6) else Color(0x0DFFFFFF))
        .border(
            1.dp,
            if (isPrimary) Color(0x388B5CF6) else Color(0x17FFFFFF),
            RoundedCornerShape(12.dp),
        )
```

With:

```kotlin
Column(
    modifier = modifier
        .clip(RoundedCornerShape(CornerCard))
        .background(if (isPrimary) Color(0x1F8B5CF6) else CardSurface)
        .border(
            1.dp,
            if (isPrimary) Color(0x388B5CF6) else CardBorder,
            RoundedCornerShape(CornerCard),
        )
```

- [ ] **Step 5: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt
git commit -m "feat(more): merge duplicate MenuCard/SettingsCard, standardise tokens"
```

---

### Task 9: Migrate BodyRecoveryScreen.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt`

- [ ] **Step 1: Update imports**

Add:
```kotlin
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.NeutralCard
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.FrostedBorder
import com.zack.recomptracker.ui.theme.FrostedSurface
```

Remove:
```kotlin
import com.zack.recomptracker.ui.component.FeaturedCard
import com.zack.recomptracker.ui.component.GlassSurfaceCard
import com.zack.recomptracker.ui.theme.FeaturedBorder
import com.zack.recomptracker.ui.theme.FeaturedSurface
```

- [ ] **Step 2: Migrate MetricsHeroCard to FrostedCard**

Find `MetricsHeroCard`. Replace `FeaturedCard {` with `FrostedCard {`. The content inside is unchanged.

- [ ] **Step 3: Migrate HistoryButton from GlassSurfaceCard to NeutralCard**

Find `HistoryButton`. Replace:

```kotlin
GlassSurfaceCard(
    modifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    ),
    cornerRadius = 14,
) {
    // ... content ...
}
```

With:

```kotlin
NeutralCard(
    modifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    ),
) {
    // ... content unchanged ...
}
```

- [ ] **Step 4: Update InlineLogFormCard to use Frosted tokens**

`InlineLogFormCard` has an expandable structure that can't use the composable wrapper, so update its modifiers in place.

Find:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color(0x0F8B5CF6))
        .border(1.dp, Color(0x478B5CF6), RoundedCornerShape(18.dp)),
) {
```

Replace with:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(CornerCard))
        .background(FrostedSurface)
        .border(1.dp, FrostedBorder, RoundedCornerShape(CornerCard)),
) {
```

- [ ] **Step 5: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt
git commit -m "feat(body): migrate MetricsHeroCard to FrostedCard, HistoryButton to NeutralCard"
```

---

### Task 10: Migrate FoodLibraryScreen.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 1: Add design token imports**

```kotlin
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerChip
import com.zack.recomptracker.ui.theme.CornerSmall
```

- [ ] **Step 2: Standardise the food list container card**

Find the `Column` that wraps the food list (inside the `LazyColumn` `item` block):

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0x0AFFFFFF))
        .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(14.dp)),
) {
```

Replace with:

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(CornerCard))
        .background(CardSurface)
        .border(1.dp, CardBorder, RoundedCornerShape(CornerCard)),
) {
```

- [ ] **Step 3: Standardise category and recent food chips**

Category chips (two occurrences of `RoundedCornerShape(20.dp)` on the chip boxes) — `20.dp` is already `CornerChip`. Replace the hardcoded value with the token in both places:

```kotlin
// Before (two separate occurrences):
.clip(RoundedCornerShape(20.dp))
// ...
RoundedCornerShape(20.dp),

// After (both occurrences):
.clip(RoundedCornerShape(CornerChip))
// ...
RoundedCornerShape(CornerChip),
```

Do the same for the recent food chips row (same `20.dp` pattern).

- [ ] **Step 4: Standardise GlassActionButton radius**

Find `GlassActionButton`. Replace `RoundedCornerShape(11.dp)` (both occurrences in the modifier chain) with `RoundedCornerShape(CornerSmall)`:

```kotlin
// Before:
.clip(RoundedCornerShape(11.dp))
// ...
RoundedCornerShape(11.dp),

// After:
.clip(RoundedCornerShape(CornerSmall))
// ...
RoundedCornerShape(CornerSmall),
```

- [ ] **Step 5: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
git commit -m "feat(food-library): standardise card and chip corner radii to design tokens"
```

---

### Task 11: Remove deprecated composables from GlassComponents.kt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt`

- [ ] **Step 1: Confirm no remaining call sites for FeaturedCard and GlassSurfaceCard**

```bash
grep -r "FeaturedCard\|GlassSurfaceCard" \
  app/src/main/java/com/zack/recomptracker/ui/ \
  --include="*.kt"
```

Expected: output contains ONLY `GlassComponents.kt` itself (the definitions). If any screen files still reference them, go back and fix those files first.

- [ ] **Step 2: Delete FeaturedCard from GlassComponents.kt**

Remove the entire `FeaturedCard` composable block:
```kotlin
// ── Featured Card (violet-tinted) ────────────────────────────────────────────

@Composable
fun FeaturedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRect(color = Color(0x1E8B5CF6))
            }
            .background(FeaturedSurface)
            .border(1.dp, FeaturedBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content,
    )
}
```

- [ ] **Step 3: Delete GlassSurfaceCard from GlassComponents.kt**

Remove the entire `GlassSurfaceCard` composable block:
```kotlin
// ── Surface Card (neutral glass) ──────────────────────────────────────────────

@Composable
fun GlassSurfaceCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(cornerRadius.dp))
            .padding(16.dp),
        content = content,
    )
}
```

- [ ] **Step 4: Verify the entire app compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. If there are any remaining references, fix them before proceeding.

- [ ] **Step 5: Final build and run**

```bash
./gradlew :app:installDebug 2>&1 | tail -10
```

Walk through every screen (Dashboard, Food Log, Body, Progress, More) and verify:
- Cards look consistent across all screens
- Nav pill is full pill shape with Liquid Glass effect
- `+ Add` slot buttons have Liquid Glass effect
- No visual regressions on charts or form elements

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt
git commit -m "refactor(glass): remove deprecated FeaturedCard and GlassSurfaceCard"
```
