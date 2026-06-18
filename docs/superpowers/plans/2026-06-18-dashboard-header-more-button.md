# Dashboard Header "More" Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the dashboard's cramped gear icon with a frosted circular "⋯" chip, and move the date to a subtitle under the title.

**Architecture:** Pure presentational rework of the private `ScreenHeader` composable in `DashboardScreen.kt`: restructure to title+date `Column` on the left and a new private `HeaderMoreButton` (frosted circular chip reusing `cardSurface`/`frostedBorder` tokens) on the right. No navigation/callback/other-file changes.

**Tech Stack:** Kotlin, Jetpack Compose (Material3).

---

## File Structure

- **Modify** `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt` — rewrite `ScreenHeader`, add private `HeaderMoreButton`, swap one icon import, add `Role` import.

This is a single self-contained presentational change. No unit test (no logic to assert); verification is build + in-app visual check.

---

## Task 1: Frosted "more" chip + date-under-title header

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Swap the icon import and add the `Role` import**

In the import block of `DashboardScreen.kt`:

Replace this line:

```kotlin
import androidx.compose.material.icons.filled.Settings
```

with:

```kotlin
import androidx.compose.material.icons.filled.MoreHoriz
```

And add this import (next to the other `androidx.compose.ui.*` imports):

```kotlin
import androidx.compose.ui.semantics.Role
```

(`Settings` was used only by the header gear being removed. `clickable`, `Box`, `Column`, `Spacer`, `height`, `size`, `clip`, `background`, `border`, `CircleShape`, `Icon`, `Alignment` are already imported in this file.)

- [ ] **Step 2: Replace the `ScreenHeader` composable**

Replace the entire existing `ScreenHeader` composable (currently `DashboardScreen.kt:286-326`, the `@Composable private fun ScreenHeader(...) { ... }` block that ends right before the `// ── Card 1: TODAY ──` comment) with:

```kotlin
@Composable
private fun ScreenHeader(
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val today = remember { LocalDate.now() }
    val dateStr = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEE, MMMM d", Locale.getDefault()))
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = "Dashboard",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = appColors.textPrimary,
                letterSpacing = (-0.8).sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = dateStr,
                fontSize = 12.sp,
                color = appColors.textMuted,
            )
        }
        if (onOpenSettings != null) {
            HeaderMoreButton(onClick = onOpenSettings)
        }
    }
}

@Composable
private fun HeaderMoreButton(onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(appColors.cardSurface)
            .border(1.dp, appColors.frostedBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = "More",
            tint = appColors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
```

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, no unresolved references.

Contingency — if (and only if) the compiler reports `MoreHoriz` is unresolved (not in the bundled Material icon set), revert the import change and instead draw three dots inside the `Box` (replacing the `Icon(...)` call):

```kotlin
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(appColors.textMuted),
                )
            }
        }
```

If `MoreHoriz` resolves (the expected case), ignore this contingency and keep the `Icon`.

- [ ] **Step 4: Verify no stale `Settings`/`IconButton` references break the build**

Run: `grep -n "Icons.Default.Settings" app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`
Expected: no output (the gear usage is gone). If `IconButton` is now unused elsewhere in the file the leftover `import androidx.compose.material3.IconButton` is only a warning — leave it unless the build fails on it.

- [ ] **Step 5: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat(dashboard): frosted More chip in header, date under title"
```

- [ ] **Step 7: Manual verification (hand off to user)**

Install the debug build, open the dashboard. Confirm: the title shows "Dashboard" with the date as a subtitle directly beneath it; the top-right shows a circular frosted "⋯" chip (not a gear) that looks like a real control; tapping it still opens the More screen.

---

## Self-Review Notes

- **Spec coverage:** frosted circular `⋯` chip reusing `cardSurface`/`frostedBorder` (Step 2 `HeaderMoreButton`); date moved under the title via the left `Column` (Step 2); `verticalAlignment = Top` so the chip aligns to the title; rendered only when `onOpenSettings != null`; `Settings`→`MoreHoriz` import swap (Step 1); `MoreHoriz` fallback (Step 3). All spec points covered.
- **No nav/callback/other-file changes:** only `DashboardScreen.kt` touched; `onOpenSettings` signature unchanged.
- **No placeholders:** all code shown in full.
- **Theme-aware:** `cardSurface`/`frostedBorder`/`textPrimary`/`textMuted` are `LocalAppColors` tokens (already used elsewhere in this file/app), so light theme adapts automatically.
