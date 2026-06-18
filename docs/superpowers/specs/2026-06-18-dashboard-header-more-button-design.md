# Dashboard Header "More" Button — Design

**Date:** 2026-06-18
**Branch:** `feat/workout-tracking`
**Status:** Approved

## Goal

Fix the dashboard's top-right control. Today it's a bare filled **gear**
(`Icons.Default.Settings`) jammed next to the date, which (a) reads as "settings"
though it opens the **More** hub (Profile / Plan / Appearance / …), (b) is cramped
against the date with no container, and (c) has no glass treatment to match the
rest of the dashboard.

## Decision (Option A)

A frosted circular **"more" chip** (`⋯`) on the right, with the date moved to a
subtitle under the title.

## Scope

Rework **only** the `ScreenHeader` composable in
`app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`.
No navigation, callback, or other-screen changes. The `onOpenSettings` callback
and its wiring are unchanged.

## Why frosted (not real liquid glass)

The dashboard provides no `LocalBackdrop` / `drawBackdrop` layer, so the real
Kyant liquid-glass material can't sample a backdrop here without new plumbing —
disproportionate for one header button. The dashboard is built entirely from
`FrostedCard`, so the chip reuses the **same frosted tokens** (`cardSurface` +
`frostedBorder`), staying visually consistent with every card on the screen.

## Layout

Replace the current `Row(SpaceBetween) { Title ; Row { date, gear } }` with:

```
Row(fillMaxWidth, horizontalArrangement = SpaceBetween, verticalAlignment = Top)
├─ Column
│   ├─ Text("Dashboard")            // 28sp ExtraBold, letterSpacing -0.8sp (unchanged)
│   ├─ Spacer(3.dp)
│   └─ Text(dateStr)                // 12sp, appColors.textMuted (unchanged style)
└─ HeaderMoreButton(onClick)        // only when onOpenSettings != null
```

`verticalAlignment = Top` so the chip lines up with the title, not the centre of
the taller title+date column.

## Component: `HeaderMoreButton` (new private composable, same file)

```
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

- 40dp box → adequate touch target; circular frosted material matches `FrostedCard`.
- `⋯` (`MoreHoriz`) honestly signals "more options," matching the destination.
- Rendered only when `onOpenSettings != null` (preserves the existing nullability
  guard; the preview/`@Preview` path passes null and shows no button, as today).

## Edge Cases / Risks

- **`Icons.Default.MoreHoriz` availability:** it's in the standard Material icons
  set (sibling of the already-used `MoreVert`). If the compile step reports it
  missing, fall back to three small drawn dots in the same `Box`. The build step
  confirms.
- Light theme: `cardSurface` + `frostedBorder` are theme-aware tokens, so the
  chip adapts automatically (no hard-coded colors).

## Testing

Pure presentational change — no unit test. Verified by `compileDebugKotlin` +
`assembleDebug`, then the user's in-app visual check (header spacing, chip looks
like a real frosted control, tap still opens More).

## Out of Scope

- Removing the control (Option D) or changing its destination.
- Touching the bottom-nav "More" tab or `MoreScreen`.
- Adding a backdrop layer / real liquid-glass material to the dashboard.
