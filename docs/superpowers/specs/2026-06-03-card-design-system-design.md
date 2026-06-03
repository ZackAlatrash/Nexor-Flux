# Card Design System — Spec

**Date:** 2026-06-03
**Status:** Approved

---

## Problem

Cards across the app are visually inconsistent: five different corner radii (12, 14, 16, 18, 20dp), three different background opacities, two duplicate composables (`MenuCard` / `SettingsCard` in `MoreScreen.kt` are identical), and no rule for when to use which style. The design tokens exist but screens bypass them and hardcode values inline.

---

## Glass Tiers

Four tiers. Three active, one reserved.

### Tier 1 — Neutral Glass
- **Surface:** `rgba(255,255,255,0.04)`
- **Border:** `rgba(255,255,255,0.07)`, 1dp
- **Blur:** none
- **Corner radius:** `CornerCard` (16dp)
- **Use for:** list rows, food log slot cards, form containers, menu/settings cards, mini chart pairs, range selector, edit-mode cards
- **Composable:** `NeutralCard {}`

### Tier 2 — M3 Frosted Blur
- **Surface:** `rgba(18,10,32,0.62)` — dark frosted
- **Border:** `rgba(255,255,255,0.13)`, 1dp — with top shimmer line `rgba(255,255,255,0.30)`
- **Blur:** `blur(24px)` via Android `RenderEffect` (API 31+)
- **Corner radius:** `CornerCard` (16dp)
- **Use for:** all primary data summary cards (Today nutrition strip, Today calorie card, body check-in forms) and all featured chart cards (7-day, Weight, Waist, Calories, Progress charts)
- **Composable:** `FrostedCard {}`

### Tier 3 — Tinted Glass *(reserved — not used yet)*
- **Surface:** `rgba(139,92,246,0.09)` — violet-tinted
- **Border:** `rgba(139,92,246,0.28)`, 1dp — with violet shimmer line
- **Blur:** `blur(16px)` via Android `RenderEffect`
- **Corner radius:** `CornerCard` (16dp)
- **Use for:** AI insight cards, weekly verdict banners, plan recommendation callouts — ONLY when AI features are shipped
- **Composable:** `TintedCard {}` — coded, zero call sites until AI features land

### Tier 4 — Liquid Glass
- **Implementation:** [FletchMcKee/liquid](https://github.com/FletchMcKee/liquid) Compose library — do not hand-roll this effect
- **Shape:** full pill (`CornerPill` = 100dp), matching Apple iOS 26 tab bar geometry
- **Use for:** bottom nav pill, bottom sheet overlays, `+ Add` slot buttons, confirm dialogs, floating action buttons — **chrome elements only, never content cards**
- **Composable:** uses library API directly

---

## Corner Radius Tokens

Add to `DesignTokens.kt` as `Dp` values:

| Token | Value | Used on |
|---|---|---|
| `CornerSmall` | 10dp | Inputs, step buttons, small controls |
| `CornerCard` | 16dp | All content cards (Neutral + Frosted + Tinted) |
| `CornerChip` | 20dp | Badges, pill indicators, tag chips |
| `CornerPill` | 100dp | Liquid Glass elements only |

All existing inline corner radii (12, 14, 18, 20dp on cards) converge to `CornerCard` = 16dp.

---

## Tier Assignment Map

### Dashboard
| Element | Tier |
|---|---|
| Today card (calories + macros) | M3 Frosted |
| 7-day chart card | M3 Frosted |
| Bottom nav pill | Liquid Glass |

### Food Log
| Element | Tier |
|---|---|
| Nutrition strip (top summary) | M3 Frosted |
| Meal slot cards | Neutral |
| `+ Add` button inside each slot | Liquid Glass |
| Bottom nav pill | Liquid Glass |

### Progress
| Element | Tier |
|---|---|
| Range selector (7d/14d/28d) | Neutral |
| All chart cards (Weight, Waist, Calories, Protein, Carbs, Fat, Adherence, Lifts) | M3 Frosted |
| Mini chart pairs | Neutral |
| Bottom nav pill | Liquid Glass |

### More
| Element | Tier |
|---|---|
| All menu cards (Insights, Planning) | Neutral |
| All settings cards (Appearance, App) | Neutral |
| Data action cards (Export, Import) | Neutral |
| Bottom nav pill | Liquid Glass |

### Body Check-In / Recovery
| Element | Tier |
|---|---|
| Form container cards | M3 Frosted |
| Input fields | Unchanged (existing `GlassInputField`) |
| Bottom nav pill | Liquid Glass |

---

## Code Architecture

### Files changed

**`ui/theme/DesignTokens.kt`**
- Add `CornerSmall`, `CornerCard`, `CornerChip`, `CornerPill` tokens
- Add `FrostedSurface`, `FrostedBorder` colour tokens
- Keep `CardSurface`, `CardBorder` (Neutral) and `FeaturedSurface`, `FeaturedBorder` (Tinted, dormant)

**`ui/component/GlassComponents.kt`**
- `GlassSurfaceCard` → renamed to `NeutralCard` (same implementation, new name)
- `FeaturedCard` → replaced by `FrostedCard` with blur effect
- Add `TintedCard` — implemented, no call sites
- All existing controls (`GlassButton`, `GlassInputField`, `VioletSlider`, `VioletToggle`, `GlassTextArea`, `ScoreStepper`, `AmbientOrb`) unchanged

**`app/build.gradle`**
- Add FletchMcKee/liquid as a dependency

**`ui/RecompApp.kt`**
- Nav pill migrated to FletchMcKee/liquid Liquid Glass API

**`ui/dashboard/DashboardScreen.kt`**
- Inline card blocks → `FrostedCard {}` and `NeutralCard {}`

**`ui/today/FoodScreen.kt`**
- Nutrition strip → `FrostedCard {}`
- Slot cards → `NeutralCard {}`
- `+ Add` buttons → Liquid Glass from FletchMcKee/liquid

**`ui/progress/ProgressScreen.kt`**
- `FeaturedCard` → `FrostedCard`
- `GlassSurfaceCard` → `NeutralCard`
- Inline `MiniChartCard` → `NeutralCard`

**`ui/more/MoreScreen.kt`**
- `MenuCard` and `SettingsCard` are identical — both replaced with `NeutralCard {}`
- `DataActionCard` variants → `NeutralCard {}`

### Files not changed
- All ViewModel files
- All data/domain layer files
- `ui/component/charts/` — chart drawing code unchanged
- `ui/component/Components.kt`
- Navigation files

---

## Constraints

### API level — blur fallback required
`minSdk = 26` but `RenderEffect.createBlurEffect()` requires API 31+. `FrostedCard` and `TintedCard` must use an `if (Build.VERSION.SDK_INT >= 31)` guard:
- **API 31+:** full blur + surface colour as designed
- **API 26–30 fallback:** no blur, surface opacity raised to `rgba(22,14,38,0.82)` and border to `rgba(255,255,255,0.16)` to compensate visually — still clearly a distinct tier from Neutral, just without the blur

### FletchMcKee/liquid dependency
Confirm the library is published to Maven Central or JitPack before implementation starts. If only available as source, add it as a local Gradle module under `/libraries/liquid`. Do not vendor the source directly into the app module.

### Tinted Glass
Zero call sites intentionally. Do not add any until AI features are specced and approved.
