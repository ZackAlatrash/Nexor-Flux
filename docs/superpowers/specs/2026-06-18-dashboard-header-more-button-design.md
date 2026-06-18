# Dashboard Header "More" Button — Design

**Date:** 2026-06-18
**Branch:** `feat/workout-tracking`
**Status:** Implemented

## Goal

Fix the dashboard's top-right control. It started as a bare filled **gear**
(`Icons.Default.Settings`) jammed next to the date, which read as "settings"
though it opens the **More** hub (Profile / Plan / Appearance / …), was cramped,
and had no treatment matching the rest of the dashboard.

## Decision history

1. First shipped **Option A** — a frosted circular "⋯" chip with the date moved
   to a subtitle under the title (commits `89dd5aa`, `385e0a9`).
2. Pivoted to **Option B** (final) — a **profile avatar** in that same slot. Same
   destination (tap → More) and same layout (date under title, `Alignment.Top`,
   48dp touch target); only the visual changed from a `⋯` chip to an avatar.

## Scope

`DashboardScreen.kt` (header composables + screen wiring), `DashboardViewModel.kt`
(avatar state), `AppContainer.kt` (one DI argument). No navigation/callback change —
`onOpenSettings` still opens the More screen.

## Avatar content (Option B)

The 40dp circular avatar (inside a 48dp touch target) shows, in order:
1. **Profile photo** (`UserProfilePreferences.profilePhotoUri`) via Coil `AsyncImage`, cropped circular.
2. else **initials** from `UserProfilePreferences.name` — first + last word initials
   (one letter for a single word), uppercased — in `accent.onAccent` on an accent
   gradient (`accent.accent → accent.accentDark`).
3. else **`Icons.Rounded.Person`** on the same gradient (mirrors the Profile screen placeholder).

A 1dp `Color.White @ 0.18` border; single accessibility node
`contentDescription = "Profile and more"` with `Role.Button`.

## Data flow

`DashboardViewModel` takes `UserProfilePreferencesStore` and exposes an isolated
`headerAvatar: StateFlow<HeaderAvatar(photoUri, initials)>` mapped from
`userProfileStore.preferences` (kept out of the existing `combine` that builds
`uiState`). `HomeDashboardScreen` collects it and threads `photoUri`/`initials`
through `HomeDashboardContent` → `ScreenHeader` → `HeaderProfileButton`.

`initialsOf(name): String?` is a pure top-level helper (blank/null → null), unit-tested.

## Layout (unchanged from Option A)

```
Row(fillMaxWidth, SpaceBetween, verticalAlignment = Top)
├─ Column { "Dashboard" (28sp ExtraBold) ; Spacer(3.dp) ; date (12sp textMuted) }
└─ HeaderProfileButton(photoUri, initials, onClick)   // only when onOpenSettings != null
```

## Edge cases

- No name and no photo → person icon.
- Photo present but still loading / fails → the accent gradient shows behind until
  Coil resolves (acceptable placeholder); no separate fallback wiring needed.
- Light theme: `accent.*` / `onAccent` are theme/mode-aware, so the gradient and
  text contrast adapt automatically.

## Testing

`initialsOf` unit-tested (two/three-word, single-word, whitespace/case, null/blank).
Rendering verified by `compileDebugKotlin` + `assembleDebug`, then the user's in-app
visual check (photo, initials, and no-name cases).

## Out of scope

- Removing the control or changing its destination.
- Touching the bottom-nav "More" tab or `MoreScreen`.
- Real liquid-glass material (no backdrop layer on the dashboard).
