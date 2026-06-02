# App Redesign — Visual Design Spec
_Date: 2026-06-02_

## Design Language

| Token | Value |
|---|---|
| Aesthetic | Glass Premium — Balanced Glow |
| Accent | Violet `#8b5cf6` / `#a78bfa` (light) / `#c4b5fd` (surface) |
| Background | Deep violet-tinted dark `#0d0818` → `#0f0b1c` → `#090a12` gradient |
| Surface card | `rgba(255,255,255,0.04)` + `rgba(255,255,255,0.07)` border |
| Featured card | `rgba(139,92,246,0.08)` bg + `rgba(139,92,246,0.22)` border + violet glow |
| Ambient orb | `radial-gradient` top-left `rgba(139,92,246,0.20)`, secondary bottom-right `rgba(167,139,250,0.08)` |
| Typography | Roboto (system default) — user-switchable to Space Grotesk or Plus Jakarta Sans in Settings |
| Theme | Dark only |
| Error/negative | `#fb7185` (red-400) |
| Success/positive | `#a78bfa` (violet-400) |

---

## Navigation — Glass Pill

- **Style**: floating pill, `width: calc(100% - 32px)`, `border-radius: 36px`
- **Background**: `rgba(14,9,26,0.90)` + `backdrop-filter: blur(24px)`
- **Border**: `1px solid rgba(139,92,246,0.22)`
- **Shadow**: `0 8px 32px rgba(0,0,0,0.6)` + `inset 0 1px 0 rgba(255,255,255,0.06)`
- **Position**: `absolute bottom: 0`, padded `16px` sides, `18px` from bottom edge
- **Items**: Home · Body · **[Log]** · Progress · More
- **Active item**: `background: rgba(139,92,246,0.18)`, icon/label `#c4b5fd`
- **Inactive item**: icon opacity `0.28`, label `rgba(255,255,255,0.22)`
- **Log button (center)**: violet gradient capsule `linear-gradient(145deg, #9f75f7, #7c3aed)`, `border-radius: 28px`, margin `-6px 2px` to poke out slightly, shadow `0 4px 16px rgba(139,92,246,0.50)`

---

## Screen 1 — Dashboard ✅ LOCKED

**Card order (top to bottom):**

### Card 1 — Today
- Background: `rgba(255,255,255,0.04)` / border `rgba(255,255,255,0.07)` / `border-radius: 16px`
- Header row: "TODAY" label (uppercase, muted) + "In zone" violet badge
- Big calorie number: `font-size: 36px / font-weight: 900`, followed by "kcal" + "· 360 to zone"
- Calorie progress bar: `height: 4px`, violet gradient fill, zone labels below
- P/C/F macro mini-bars: 3 columns, each with label, value, thin violet bar

### Card 2 — Last 7 Days Chart
- Background: `rgba(139,92,246,0.08)` / border `rgba(139,92,246,0.22)` / `border-radius: 20px`
- Top shine: `1px linear-gradient` at top edge
- Ambient glow: `0 0 50px rgba(139,92,246,0.11)`
- Header row: "LAST 7 DAYS" label (violet, uppercase) + "5 of 7 in zone" pill
- SVG area chart: smooth bezier curves, violet area fill (gradient), violet line gradient
- Zone band: `rgba(139,92,246,0.08)` rect + dashed border lines `rgba(139,92,246,0.25)`
- Dot color coding: `#a78bfa` = in zone, `rgba(255,255,255,0.18)` = below, `#fb7185` = over
- Today dot: larger `r=4.5`, `#c4b5fd`, with outer glow ring
- Day labels below chart: 9px, muted, today = `#c4b5fd` bold
- Divider then 3 stat columns: Trend/wk (`#fb7185`), Adherence (`#a78bfa`), Days logged (white)

### Card 3 — Metrics Row
- 2-column grid, each cell `background: rgba(255,255,255,0.04)` / `border-radius: 16px`
- Weight trend: value `#fb7185`, label + detail muted
- Adherence: value `#a78bfa`, label + detail muted

---

---

## Screen 2 — Food Log ✅ LOCKED

**Accessed via:** Center Log button in pill nav (Log button glows brighter with outer violet ring when active).

### Nutrition Strip (top card)
- Background: `rgba(139,92,246,0.08)` / border `rgba(139,92,246,0.20)` / `border-radius: 16px`
- Top shine: 1px gradient line
- Calorie row: `22px / 900` white number + muted subtitle + "In zone" violet badge
- Calorie progress bar: `3px`, violet gradient
- 3-column macro section (Protein / Carbs / Fat), each column has:
  - Label (9px muted uppercase) + value (13px bold white) on same row
  - `3px` violet progress bar
  - "Xg to go" in 8px muted text below

### Meals Section
- "MEALS" label (9px uppercase muted) + "Reorder" violet link — same row, no card wrapper
- Slots with entries: `border-color: rgba(139,92,246,0.18)` + `background: rgba(139,92,246,0.04)`
- Slots without entries: default muted surface

### Slot Card
- **Header** (always visible): slot name (13px / 800 white), calorie total (10px violet if has entries, muted if empty), violet "+ Add" button (right)
- **Entry area** background: `rgba(0,0,0,0.20)` — visually inset/darker than header
- `border-top: 1px solid rgba(255,255,255,0.05)` separates header from entries

### Entry Row
- Left indent `padding-left: 18px` + thin `2px` violet accent bar on left edge (`rgba(139,92,246,0.30)`)
- Food name: 12px / 500 / `rgba(255,255,255,0.75)` — smaller and muted vs slot name
- Macro string: 9px / `rgba(255,255,255,0.25)`
- kcal: 12px / 700 / `rgba(255,255,255,0.40)`
- **Edit button**: 26×26px rounded, `rgba(139,92,246,0.12)` bg, violet `✎`
- **Delete button**: 26×26px rounded, `rgba(251,113,133,0.10)` bg, red `✕`

### Empty Slot State
- Inset dark bg, centered "nothing logged yet" in 10px muted text, flanked by hairlines

### Add Slot Button
- Full-width, dashed border `rgba(255,255,255,0.10)`, muted text, `border-radius: 14px`

---

## Screens to Design (remaining)

- [x] Food / Today screen
- [x] Body screen
- [x] Progress screen
- [x] Food Library screen
- [x] Settings / More screen

---

## Screen 6 — More / Settings ✅ LOCKED

**Nav tab:** More (fifth item, rightmost in pill nav).

### Section Groups (top to bottom)

**Insights**
- Stats row: 📊 icon · "Stats" · "Verdict, targets and trend summary" · chevron
- Charts row: 📈 icon · "Charts" · "Weight, waist, nutrition and lifts" · chevron

**Planning**
- Plan row: 🎯 icon · "Plan" · "Targets, zones and review cadence" · chevron

**Appearance**
- Font row: 🔤 icon · "Font" · "Choose your preferred typeface" · inline font picker (Default / Space / Jakarta chips)

**App**
- Health Connect row: ❤️ icon · "Health Connect" · "Sync steps and heart rate" · green "Connected" badge (`rgba(52,211,153,0.12)` bg, `#34d399` text + dot)
- AI Insights row: ✨ icon · "AI Insights" · "On-device Gemma analysis" · "On-device" violet label + violet toggle

**Data**
- Side-by-side 2-col action cards: Export backup (violet tint) + Import backup (muted)

### Menu Row Style
- `border-bottom: 1px solid rgba(255,255,255,0.05)` between rows, none on last
- Icon container: 34×34px, `border-radius: 10px`, `rgba(139,92,246,0.14)` bg, `rgba(139,92,246,0.22)` border
- Title: 14px / 600 / white · Detail: 11px / `rgba(255,255,255,0.28)`
- Chevron: `›` in `rgba(255,255,255,0.20)`

### Font Picker (inline chips)
- 3 options: Default · Space · Jakarta
- Inactive: `rgba(255,255,255,0.06)` bg, `rgba(255,255,255,0.08)` border, 35% white
- Active: `rgba(139,92,246,0.22)` bg, `rgba(139,92,246,0.35)` border, `#c4b5fd`
- `border-radius: 7px`, 10px / 700

### Export / Import Action Cards
- `border-radius: 12px`, flex-col with icon + label
- Export: `rgba(139,92,246,0.12)` bg, `rgba(139,92,246,0.22)` border, `#c4b5fd` text
- Import: `rgba(255,255,255,0.05)` bg, `rgba(255,255,255,0.09)` border, muted text

---

## Screen 5 — Food Library ✅ LOCKED

**Accessed from:** "+ Add" button on any meal slot in the Food Log. Full-screen destination — no bottom pill nav.

### Top Bar
- Back button: 34×34px rounded square, `rgba(255,255,255,0.06)` bg, `rgba(255,255,255,0.09)` border
- Title: slot name (17px/800 white) + remaining kcal sub (11px violet)
- Camera scan button: 34×34px, `rgba(139,92,246,0.15)` bg, `rgba(139,92,246,0.25)` border

### Search Field
- Full width, `border-radius: 14px`
- Default: `rgba(255,255,255,0.06)` bg, `rgba(255,255,255,0.10)` border
- Focused: `rgba(139,92,246,0.07)` bg, `rgba(139,92,246,0.45)` border
- Left search icon, 14px placeholder/input text

### Category Chips (horizontal scroll)
- All · Proteins · Carbs · Saved Meals · NEVO · Open Food Facts
- Same glass chip style as Progress range selector
- Active: `rgba(139,92,246,0.20)` bg, `rgba(139,92,246,0.35)` border, `#c4b5fd` text

### Action Row (2-column grid)
- "+ New food": `rgba(139,92,246,0.18)` bg, `rgba(139,92,246,0.32)` border, `#c4b5fd` text
- "⚡ Quick add": `rgba(255,255,255,0.05)` bg, `rgba(255,255,255,0.09)` border, muted text
- Both: `border-radius: 11px`, 12px/700

### Section Label
- "Results for X" or "Recent" — 9px / 700 / muted uppercase

### Food List Card
- `rgba(255,255,255,0.04)` bg / `rgba(255,255,255,0.07)` border / `border-radius: 14px`
- Rows separated by `1px rgba(255,255,255,0.04)` bottom border

### Food Row
- Food name: 13px / 600 / white (ellipsis overflow)
- Macros: 10px / `rgba(255,255,255,0.28)` — e.g. "per 100g · 31P 0C 3.6F"
- kcal: 12px / 700 / `rgba(255,255,255,0.40)` (right-aligned)
- "+" add button: 28×28px, `border-radius: 8px`, `#8b5cf6` bg, violet glow shadow

### Saved Meal Row
- Same as food row but: `rgba(139,92,246,0.04)` row bg tint
- "Meal" tag: 8px/700, `rgba(139,92,246,0.12)` bg, `rgba(139,92,246,0.2)` border, violet text. Sits beside the food name inline.

---

## Screen 4 — Progress ✅ LOCKED

**Nav tab:** Progress (fourth item, right of Log button).

### Range Selector
- 3 equal-flex chips: 7d / 14d / 28d
- Inactive: `rgba(255,255,255,0.05)` bg, `rgba(255,255,255,0.07)` border, 35% white text
- Active: `rgba(139,92,246,0.20)` bg, `rgba(139,92,246,0.35)` border, `#c4b5fd` text
- `border-radius: 10px`, `font-size: 12px / 700`

### Section Labels
- 9px / 700 / `rgba(255,255,255,0.25)` / uppercase / `letter-spacing: 0.14em`
- 3 sections: **Body** · **Nutrition** · **Performance**

### Chart Cards — Full Width (featured)
Used for: Weight, Waist, Calories
- Background: `rgba(139,92,246,0.07)` / border `rgba(139,92,246,0.20)` / `border-radius: 18px`
- Glow: `0 0 30px rgba(139,92,246,0.08)`
- Header: metric name (11px uppercase muted) + current value (26px/900 white) + trend (11px/700, violet=good, red=warn, muted=neutral)
- SVG smooth area chart: violet gradient area fill + violet gradient line (1.8px) + glowing end dot
- Calories chart includes dashed target zone band (same as Dashboard)

### Chart Cards — 2-column Mini
Used for: Protein+Carbs pair, Adherence+Lifts pair
- Background: `rgba(255,255,255,0.04)` / border `rgba(255,255,255,0.07)` / `border-radius: 14px`
- Metric name (9px), value (20px/900), trend, small SVG sparkline (36px tall)

### Fat Chart
- Full width but shorter height (44px SVG) — sits between pairs

---

## Screen 3 — Body ✅ LOCKED

**Nav tab:** Body (second item, left of Log button).

### Metrics Hero Card
- Background: `rgba(139,92,246,0.09)` / border `rgba(139,92,246,0.24)` / `border-radius: 20px`
- Top shine: 1px gradient line
- Header: "LATEST CHECK-IN · Jun 1" (9px violet uppercase)
- 2-column grid: Weight (36px/900) + Waist (36px/900), each with label + `↓ X.X unit / week` violet trend
- Bottom section (separated by divider): 2-column sparkline grid (weight 14d + waist 14d), SVG area charts

### Inline Log Form Card
- Background: `rgba(139,92,246,0.06)` / border `rgba(139,92,246,0.28)` / `border-radius: 18px`
- Header: "Today's check-in" + date sub + "Collapse ↑" button (top-right)
- Collapsed state: shows just the header row as a tappable CTA
- Expanded state (shown by default if not yet logged today): full form

**Form sections (3 groups separated by `1px rgba(255,255,255,0.05)` dividers):**

1. **Measurements group:**
   - Row 1: Weight (kg) | Waist (cm) — 2-col grid
   - Row 2: Skinfold (mm) | Sleep (hrs) — 2-col grid
   - Row 3: Steps — full width
   - Each field: `rgba(0,0,0,0.25)` bg, `1px rgba(255,255,255,0.10)` border, `border-radius: 10px`, 17px/800 value + 11px muted unit. Focused field: violet border + tint.

2. **Daily scores group:**
   - Energy / Hunger / Soreness — each as a slider row
   - Each row: label (12px/600 muted) + violet value badge (right), then track + thumb + 1–5–10 scale labels
   - Track: 5px, `rgba(255,255,255,0.07)` bg, violet gradient fill
   - Thumb: 16px circle, `#c4b5fd`, glow ring `0 0 0 3px rgba(139,92,246,0.25)`

3. **Training + Notes group:**
   - "Training day" label + violet toggle (on = `#8b5cf6`, off = `rgba(255,255,255,0.12)`)
   - Notes: multi-line input, same dark field style

- **Save button:** full-width, violet gradient, 12px radius, 14px/800

### View History Button
- `rgba(255,255,255,0.04)` bg / `rgba(255,255,255,0.09)` border / `border-radius: 14px`
- Left: "Check-in history" title + "47 days logged · tap to view all" sub
- Right: `→` in violet
- Taps navigate to the full history screen (BodyHistoryScreen)
