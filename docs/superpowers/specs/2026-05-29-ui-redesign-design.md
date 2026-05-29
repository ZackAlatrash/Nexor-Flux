# UI Redesign — Design Spec
**Date:** 2026-05-29  
**Scope:** Dark theme overhaul, calorie bar with target zone, redesigned Today screen with custom meal slots, new food library screen.

---

## 1. Visual Language

### Color palette
| Token | Value | Usage |
|---|---|---|
| Background | `#0f0f0f` | App background |
| Surface | `#1a1a1a` | Cards, panels |
| Track | `#222` | Progress bar backgrounds |
| Primary | `#3b82f6 → #2563eb` | Progress bar fill, buttons, active states |
| Zone | `#15803d` | Target zone on progress bars only |
| Zone stripe | `repeating-linear-gradient(45deg, #15803d 0px, #15803d 3px, #0f4a26 3px, #0f4a26 7px)` | Diagonal hatching on zone segment |
| Text primary | `#ffffff` | Main numbers and labels |
| Text secondary | `#6b7280` | Remaining / "X to go" labels, slot subtitles |
| Text muted | `#555555` | Micro labels, empty states |
| Destructive | `#f87171` on `#3d1515` | Delete actions |

### Icons
Use single-color Material icons throughout. No colored emoji in any UI element.

### Typography
- Screen titles: 20–22 sp, weight 800
- Section labels: 10–11 sp, uppercase, letter-spacing 0.06em, color muted
- Primary numbers (calorie count): 28–36 sp, weight 800
- Secondary text / "to go" labels: 12–13 sp, color secondary

---

## 2. Calorie & Macro Bar Component

A shared `CalorieZoneBar` composable used in both Today and Dashboard.

### Calorie bar
- Full-width track in `#222`, height 16 dp, pill-shaped
- Blue fill from 0 to `(eaten / scaleMax)` — gradient `#3b82f6 → #2563eb`
- Green diagonal-striped zone segment positioned at `(lowerBound / scaleMax)` to `(upperBound / scaleMax)` width — rendered on top of the track, behind the blue fill so the fill covers it once reached
- Scale max = `targetCalories * 1.25` (gives 25% overage room on the right)
- Below bar: `"[eaten] eaten · [remaining] to zone"` — "eaten" in white, "· [remaining] to zone" in `#6b7280`
- Zone label below right of bar: small green text `"▌ [lower]–[upper] target zone"`

### Per-macro mini bars
Three columns (Protein / Carbs / Fat) each with:
- Current amount in white, bold
- Mini bar (4 dp height) — same blue fill, small green zone marker at the target position
- "Xg to go" in `#6b7280`

### When bar is inside the zone
- "to zone" label changes to "in zone" in `#22c55e`
- Blue fill overlaps the striped zone (showing progress through it)

### When bar exceeds the zone
- Fill color shifts to `#f87171` (red) for the overage portion
- Label changes to "+[over] over target" in `#f87171`

---

## 3. Today Screen

### Layout (top to bottom)
1. Date header — "Thursday · May 29" in secondary color
2. `CalorieZoneBar` card (Surface background, 14 dp padding)
3. Meals section

### Meals section header
```
MEALS                                    [lock-icon] Reorder
```
- "MEALS" in muted uppercase label style
- Right side: single-color lock icon + "Reorder" label in `#6b7280`
  - Tapping enters **edit mode** (see §3.1)

### Meal slot (locked state)
Each slot is a Surface card (`#1a1a1a`, 12 dp radius):
```
SLOT NAME                    [total kcal]  [+ Add]
Food name
X P  Y C  Z F
```
- Slot name: 11 sp, uppercase, weight 700, color secondary
- Food entries listed below name (name + P/C/F summary)
- Empty slot shows italic "Empty — tap + Add" in muted color
- "+ Add" button: blue pill, opens Food Library screen with this slot pre-selected

### 3.1 Edit mode (unlocked)
Triggered by tapping the lock/reorder button. Header button turns blue and shows "Done".

Each slot card shows:
- Blue drag handle (⠿) on the left — enable drag-to-reorder
- Slot name (editable feel)
- **Rename** button: `#222` background, secondary text
- **Delete** button: `#3d1515` background, `#f87171` text
- "+ Add" button hidden in this mode
- Slot kcal total hidden

Tapping "Done" saves order and returns to locked state.

### Add meal slot
Below all slots, always visible:
```
[dashed border card]   + Add meal slot
```
Tapping opens a bottom sheet with a single text field: "Slot name". Confirm creates the slot and adds it at the bottom.

### Body metrics section
Below meals — collapsible card for weight, waist, steps, sleep, energy, hunger, soreness, training toggle, notes. Unchanged functionally, dark theme applied.

---

## 4. Food Library Screen

### Entry point
- Opened from Today → "+ Add" on any meal slot. Passed the slot name and slot ID.
- Also accessible standalone from **More → Foods/Meals** (manages the library; no slot context).

### Header (when opened from a slot)
```
← Back          Add to [Slot Name]
                [eaten] kcal logged · [remaining] to zone
```
- Back arrow (single-color icon) left
- Slot name bold, remaining shown in secondary gray below

### Header (standalone)
```
← Back          Foods & Meals
```

### Search bar
Always visible below header. Placeholder: "Search saved foods…"

### Category chips
Horizontal scrollable row: **All** · Proteins · Carbs · Meals  
Active chip: blue background. Inactive: `#1a1a1a` background, muted text.

### Food list item
```
[Food name]                             [Log]
per [serving] · [kcal] kcal · [P]P [C]C [F]F
```
- "Log" button: blue rounded rectangle
- Tapping "Log" when opened from a slot:
  - If the food is stored per 100g: opens a quantity bottom sheet (number input, default 100, minimum 1, suffix "g") then logs
  - If the food has a fixed serving: logs immediately
  - Adds a `MealEntryEntity` for today's date and the selected slot
  - Returns to Today screen after logging

### Bottom actions (always shown below list)

**When opened from a slot:**
```
[star icon]  Save current slot as meal
             Save "[Slot Name]" (all logged foods) as a reusable meal
```
Only shown when the slot has at least one food logged. Tapping opens a bottom sheet: "Meal name" text field pre-filled with the slot name. Confirm saves a `SavedMealEntity` with the combined macros of all foods in the slot.

```
[plus icon]  Create new food
             Enter name + macros, save to library
```

**When standalone:** only "Create new food" shown.

### Create new food bottom sheet / screen
Fields: Name, Serving description (e.g. "100g" or "1 scoop"), Calories, Protein (g), Carbs (g), Fat (g).  
Save stores a `SavedFoodEntity`. Cancel dismisses.

---

## 5. Saved Meals

Saved meals appear in the Food Library under the "Meals" category chip.

### Saved meal list item
```
[Meal name]                             [Log all]
[total kcal] kcal · [P]P [C]C [F]F
```
- "Log all" adds all foods in the meal as individual `MealEntryEntity` rows for the selected slot

---

## 6. Navigation Bar

### Tab labels
| Route | Label | Icon (Material) |
|---|---|---|
| Today | Today | `Home` |
| Dashboard | Stats | `GridView` |
| Progress | Charts | `TrendingUp` |
| Plan | Plan | `AccountCircle` |
| More | More | `MoreHoriz` |

More contains: Foods/Meals (food library standalone), Settings.

### Active indicator — Pill style
- Active tab: blue pill background (`#1e3a5f`), blue icon (`#3b82f6`), bold blue label (`#3b82f6`, weight 700)
- Inactive tab: muted icon (`#444444`), muted label (`#444444`, weight 400), no background
- Nav bar background: `#111111`, top border `1dp` at `#1e1e1e`
- Pill shape: `border-radius = 16dp`, horizontal padding 16dp, vertical padding 7dp
- Implemented via Material 3 `NavigationBar` + `NavigationBarItem` with custom `colors` overriding `indicatorColor`

---

## 7. Other Screens — Dark Theme Only

Dashboard, Progress, Plan, Settings receive the dark color palette (§1) applied to their existing layouts. No structural or functional changes in this redesign.

---

## 8. Data Model Changes

### New field: `MealSlotEntity`
```kotlin
@Entity(tableName = "meal_slots")
data class MealSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,          // "Meal 1", "Lunch", "Post Workout"
    val sortOrder: Int         // user-defined order
)
```
`MealEntryEntity` gains a nullable `slotId: Long?` foreign key. Existing entries with no slot are shown in a default "Uncategorized" slot.

The lock/unlock edit mode is **UI-only state** (not persisted) — slots always start locked when the app opens. It is held in `TodayViewModel.slotsEditMode: Boolean`.

### Calorie target zone
`PlanPreferences` gains two fields:
```kotlin
val calorieZoneLowerBound: Int = 2400
val calorieZoneUpperBound: Int = 2600
```
Defaults are ±75 kcal around the 2550 target. Editable in Plan screen.

---

## 9. What Does Not Change

- AdjustmentEngine logic
- Dashboard verdict card content
- Progress charts (dark theme only)
- Backup/export/restore
- Room schema migrations (additive only)
- All domain-layer unit tests
