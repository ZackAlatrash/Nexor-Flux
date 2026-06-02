# Screens 3–6 Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the approved Glass Premium redesign (violet dark aesthetic) for all four remaining screens: Body, Progress, Food Library, and More.

**Architecture:** MVVM with Compose UI. Shared glass design primitives extracted into `ui/component/GlassComponents.kt` and `ui/component/SparklineChart.kt`. Each screen owns its ViewModel; the More screen gets a new `MoreViewModel` for inline settings state.

**Tech Stack:** Jetpack Compose, Kotlin, Material3, Compose Canvas for charts, DataStore for preferences.

---

## File Map

### New Files
| File | Purpose |
|---|---|
| `ui/component/GlassComponents.kt` | FeaturedCard, SurfaceCard, SectionLabel, VioletBadge, GlassButton, GlassInputField, VioletSlider, VioletToggle |
| `ui/component/SparklineChart.kt` | Canvas-based smooth bezier area chart |
| `ui/more/MoreViewModel.kt` | Font pref, HC status, AI insights toggle, export/import |

### Modified Files
| File | Change |
|---|---|
| `ui/today/TodayViewModel.kt` | Add lastLogDate, lastLogWeightKg, lastLogWaistCm, sparkline14d fields to TodayUiState |
| `ui/today/BodyRecoveryScreen.kt` | Full redesign: MetricsHeroCard, InlineLogForm, HistoryButton |
| `ui/body/BodyCheckInForm.kt` | Glass-styled form groups with GlassInputField + VioletSlider |
| `ui/progress/ProgressViewModel.kt` | Extend ChartSeries with currentValue + trendLabel + trendIsGood |
| `ui/progress/ProgressScreen.kt` | Full redesign: RangeSelector, featured charts, mini pairs |
| `ui/foodlibrary/FoodLibraryScreen.kt` | Full redesign: glass TopBar, SearchField, CategoryChips, FoodListCard |
| `ui/more/MoreScreen.kt` | Full redesign: MenuCard, SettingsCard, ExportImportRow |
| `data/preferences/AppPreferences.kt` | Add selectedFont + aiInsightsEnabled preference keys |
| `core/AppContainer.kt` | Register MoreViewModel in factory |
| `ui/navigation/AppNavGraph.kt` | Pass MoreViewModel to MoreScreen |

---

## Task 1: Shared Glass Design Components

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/GlassComponents.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/SparklineChart.kt`

Key components:
- `FeaturedCard` — violet-tinted bg `rgba(139,92,246,0.07-0.09)` + border `rgba(139,92,246,0.20-0.24)` + `border-radius: 18-20dp` + ambient glow shadow
- `SurfaceCard` — `rgba(255,255,255,0.04)` bg + `rgba(255,255,255,0.07)` border + `border-radius: 14-16dp`
- `SectionLabel` — 9sp / 700 / rgba(255,255,255,0.25) / uppercase / letter-spacing
- `VioletBadge` — small pill badge with violet tint
- `GlassButton` — violet gradient button for save/primary actions
- `GlassInputField` — dark bg `rgba(0,0,0,0.25)`, white border `rgba(255,255,255,0.10)`, 17sp/800 value + muted unit
- `VioletSlider` — violet gradient fill + `#c4b5fd` thumb with glow ring
- `SparklineChart` — Canvas smooth bezier area chart with violet gradient fill

---

## Task 2: Body Screen Redesign

**Files:**
- Modify: `ui/today/TodayViewModel.kt`
- Modify: `ui/today/BodyRecoveryScreen.kt`
- Modify: `ui/body/BodyCheckInForm.kt`

MetricsHeroCard shows:
- "LATEST CHECK-IN · {date}" header
- 2-column: Weight (36sp/900) + Waist (36sp/900), each with trend
- Bottom sparklines: 14-day weight + 14-day waist SVG charts

InlineLogForm (collapsible):
- Expanded if today not logged
- 3 groups: Measurements / Daily scores / Training+Notes
- Glass-styled inputs + VioletSlider + violet save button

HistoryButton:
- Surface card with title + "47 days logged · tap to view" + violet "→"

---

## Task 3: Progress Screen Redesign

**Files:**
- Modify: `ui/progress/ProgressViewModel.kt`
- Modify: `ui/progress/ProgressScreen.kt`

ChartSeries extended:
```kotlin
data class ChartSeries(
    val title: String,
    val unit: String,
    val values: List<Float>,
    val currentValue: Float? = null,
    val trendLabel: String = "",
    val trendIsGood: Boolean = true,
)
```

Screen layout:
- RangeSelector (7d/14d/28d chips)
- Section "Body": Weight (FeaturedChartCard) + Waist (FeaturedChartCard)
- Section "Nutrition": Calories (FeaturedChartCard) + Protein/Carbs (MiniChartPair) + Fat (ShortChartCard)
- Section "Performance": Adherence/Lifts (MiniChartPair)

---

## Task 4: Food Library Screen Redesign

**Files:**
- Modify: `ui/foodlibrary/FoodLibraryScreen.kt`

Components:
- TopBar: back button (34×34 rounded square), title+subtitle, scan button (violet tint)
- SearchField: glass bg, focused = violet border
- CategoryChips: horizontal scroll, active = violet style
- ActionRow: 2-col (New food violet + Quick add muted)
- FoodListCard: SurfaceCard with food rows (name, macros, kcal, violet "+" add button)

---

## Task 5: More/Settings Screen Redesign

**Files:**
- Create: `ui/more/MoreViewModel.kt`
- Modify: `data/preferences/AppPreferences.kt`
- Modify: `core/AppContainer.kt`
- Modify: `ui/navigation/AppNavGraph.kt`
- Modify: `ui/more/MoreScreen.kt`

MoreViewModel state:
```kotlin
data class MoreUiState(
    val selectedFont: String = "default",   // "default" | "space" | "jakarta"
    val aiInsightsEnabled: Boolean = false,
    val healthConnectConnected: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)
```

Screen sections:
- Insights: Stats + Charts (navigate)
- Planning: Plan (navigate)
- Appearance: font picker (inline 3-way chip)
- App: Health Connect badge + AI Insights toggle
- Data: Export + Import side-by-side cards
