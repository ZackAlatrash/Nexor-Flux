# App Redesign — Visual Design Spec
_Date: 2026-06-02_

> All 6 screens locked. Interactive HTML mockups in `docs/superpowers/designs/`.
> Open any `.html` file in a browser to see the exact approved design.

---

## Design Language

| Token | Value |
|---|---|
| Aesthetic | Glass Premium — Balanced Glow |
| Accent | Violet `#8b5cf6` / `#a78bfa` (light) / `#c4b5fd` (surface) |
| Background | Deep violet-tinted dark `#0d0818` → `#0f0b1c` → `#090a12` gradient |
| Surface card | `rgba(255,255,255,0.04)` bg + `rgba(255,255,255,0.07)` border |
| Featured card | `rgba(139,92,246,0.08)` bg + `rgba(139,92,246,0.22)` border + violet glow |
| Ambient orb | `radial-gradient` top-left `rgba(139,92,246,0.20)`, secondary bottom-right `rgba(167,139,250,0.08)` |
| Typography | Roboto (system default) — user-switchable to Space Grotesk or Plus Jakarta Sans in Settings |
| Theme | Dark only |
| Error / negative | `#fb7185` (red-400) |
| Success / positive | `#a78bfa` (violet-400) |

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
- **Log button (center)**: violet gradient capsule `linear-gradient(145deg, #9f75f7, #7c3aed)`, `border-radius: 28px`, margin `-6px 2px` to poke slightly out of pill, shadow `0 4px 16px rgba(139,92,246,0.50)`

---

## Screen 1 — Dashboard ✅
_Mockup: `docs/superpowers/designs/01-dashboard.html`_

**Card order (top to bottom):**

### Card 1 — Today
- Background: `rgba(255,255,255,0.04)` / border `rgba(255,255,255,0.07)` / `border-radius: 16px`
- Header row: "TODAY" label (uppercase, muted) + "In zone" violet badge
- Big calorie number: `font-size: 36px / font-weight: 900`, followed by "kcal" + "· 360 to zone"
- Calorie progress bar: `height: 4px`, violet gradient fill, zone labels below
- P/C/F macro mini-bars: 3 columns, each with label, value, thin violet bar

### Card 2 — Last 7 Days Chart
- Background: `rgba(139,92,246,0.08)` / border `rgba(139,92,246,0.22)` / `border-radius: 20px`
- Top shine: `1px linear-gradient` at top edge; ambient glow `0 0 50px rgba(139,92,246,0.11)`
- Header row: "LAST 7 DAYS" label (violet, uppercase) + "5 of 7 in zone" pill
- SVG area chart: smooth bezier curves, violet area fill (gradient), violet line gradient
- Zone band: `rgba(139,92,246,0.08)` rect + dashed border lines `rgba(139,92,246,0.25)`
- Dot color coding: `#a78bfa` = in zone, `rgba(255,255,255,0.18)` = below, `#fb7185` = over
- Today dot: larger `r=4.5`, `#c4b5fd`, with outer glow ring
- Day labels below chart: 9px muted, today = `#c4b5fd` bold
- Divider then 3 stat columns: Trend/wk (`#fb7185`) · Adherence (`#a78bfa`) · Days logged (white)

### Card 3 — Metrics Row
- 2-column grid, each cell `background: rgba(255,255,255,0.04)` / `border-radius: 16px`
- Weight trend: value `#fb7185`, label + detail muted
- Adherence: value `#a78bfa`, label + detail muted

---

## Screen 2 — Food Log ✅
_Mockup: `docs/superpowers/designs/02-food-log.html`_

**Accessed via:** Center Log button in pill nav. Log button glows brighter with outer violet ring when active.

### Nutrition Strip (top card)
- Background: `rgba(139,92,246,0.08)` / border `rgba(139,92,246,0.20)` / `border-radius: 16px`
- Calorie row: `22px / 900` white number + muted subtitle + "In zone" violet badge
- Calorie progress bar: `3px`, violet gradient
- 3-column macro section (Protein / Carbs / Fat), each column:
  - Label (9px muted uppercase) + value (13px bold white) on same row
  - `3px` violet progress bar
  - "Xg to go" in 8px muted text below

### Meals Section
- "MEALS" label (9px uppercase muted) + "Reorder" violet link — same row, no card wrapper
- Slots with entries: `border-color: rgba(139,92,246,0.18)` + `background: rgba(139,92,246,0.04)`
- Slots without entries: default muted surface

### Slot Card
- **Header**: slot name (13px / 800 white), calorie total (10px violet if has entries, muted if empty), violet "+ Add" button (right)
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

## Screen 3 — Body ✅
_Mockup: `docs/superpowers/designs/03-body.html`_

**Nav tab:** Body (second item, left of Log button).

### Metrics Hero Card
- Background: `rgba(139,92,246,0.09)` / border `rgba(139,92,246,0.24)` / `border-radius: 20px`
- Top shine: 1px gradient line; ambient glow `0 0 50px rgba(139,92,246,0.12)`
- Header: "LATEST CHECK-IN · Jun 1" (9px violet uppercase)
- 2-column grid: Weight (36px/900) + Waist (36px/900), each with label + `↓ X.X unit / week` violet trend
- Bottom section (separated by divider): 2-column sparkline grid (weight 14d + waist 14d), SVG area charts

### Inline Log Form Card
- Background: `rgba(139,92,246,0.06)` / border `rgba(139,92,246,0.28)` / `border-radius: 18px`
- Header row: "Today's check-in" + date sub + "Collapse ↑" button (top-right)
- **Collapsed state**: just the header row as a tappable CTA
- **Expanded state**: full form shown by default if today not yet logged

**3 form groups (separated by `1px rgba(255,255,255,0.05)` dividers):**

1. **Measurements**: Weight (kg) | Waist (cm) · Skinfold (mm) | Sleep (hrs) · Steps (full width). Each field: `rgba(0,0,0,0.25)` bg, `rgba(255,255,255,0.10)` border, `border-radius: 10px`, 17px/800 value + muted unit. Focused: violet border + tint.
2. **Daily scores**: Energy / Hunger / Soreness as slider rows. Each: label (12px/600) + violet value badge, then 5px track with violet gradient fill + 16px `#c4b5fd` thumb (glow ring `0 0 0 3px rgba(139,92,246,0.25)`) + 1–5–10 scale labels.
3. **Training + Notes**: "Training day" toggle (on = `#8b5cf6`) + multi-line Notes field.

**Save button**: full-width violet gradient, `border-radius: 12px`, 14px/800.

### View History Button
- `rgba(255,255,255,0.04)` bg / `rgba(255,255,255,0.09)` border / `border-radius: 14px`
- "Check-in history" + "47 days logged · tap to view all" · right arrow `→` in violet
- Navigates to full BodyHistoryScreen

---

## Screen 4 — Progress ✅
_Mockup: `docs/superpowers/designs/04-progress.html`_

**Nav tab:** Progress (fourth item, right of Log button).

### Range Selector
- 3 equal-flex chips: 7d / 14d / 28d · `border-radius: 10px` · 12px/700
- Inactive: `rgba(255,255,255,0.05)` bg, `rgba(255,255,255,0.07)` border, 35% white text
- Active: `rgba(139,92,246,0.20)` bg, `rgba(139,92,246,0.35)` border, `#c4b5fd` text

### 3 Section Groups
- **Body**: Weight (full-width featured) · Waist (full-width featured)
- **Nutrition**: Calories (full-width featured) · Protein + Carbs (2-col mini pair) · Fat (full-width short)
- **Performance**: Adherence + Lifts (2-col mini pair)
- Section labels: 9px / 700 / `rgba(255,255,255,0.25)` / uppercase / `letter-spacing: 0.14em`

### Full-Width Featured Chart Card (Weight, Waist, Calories)
- Background: `rgba(139,92,246,0.07)` / border `rgba(139,92,246,0.20)` / `border-radius: 18px`
- Header: metric name (11px uppercase muted) + current value (26px/900) + trend indicator
- Trend colors: violet = good · red = warn · muted = neutral
- SVG: smooth bezier area chart, violet gradient fill + 1.8px line + glowing end dot
- Calories chart includes dashed target zone band

### 2-Column Mini Chart Card (Protein/Carbs, Adherence/Lifts)
- Background: `rgba(255,255,255,0.04)` / border `rgba(255,255,255,0.07)` / `border-radius: 14px`
- Metric name (9px) · value (20px/900) · trend · 36px SVG sparkline

### Fat Chart
- Full-width, shorter 44px SVG height, same surface card style

---

## Screen 5 — Food Library ✅
_Mockup: `docs/superpowers/designs/05-food-library.html`_

**Accessed from:** "+ Add" on any meal slot. Full-screen destination — no bottom pill nav.

### Top Bar
- Back button: 34×34px rounded square, `rgba(255,255,255,0.06)` bg
- Title: slot name (17px/800) + remaining kcal sub (11px violet)
- Camera scan button: 34×34px, `rgba(139,92,246,0.15)` bg, `rgba(139,92,246,0.25)` border

### Search Field
- Full-width, `border-radius: 14px`
- Default: `rgba(255,255,255,0.06)` bg, `rgba(255,255,255,0.10)` border
- Focused: `rgba(139,92,246,0.07)` bg, `rgba(139,92,246,0.45)` border

### Category Chips (horizontal scroll)
- All · Proteins · Carbs · Saved Meals · NEVO · Open Food Facts
- Active: `rgba(139,92,246,0.20)` bg, `rgba(139,92,246,0.35)` border, `#c4b5fd` text

### Action Row (2-column)
- "+ New food": `rgba(139,92,246,0.18)` bg, `rgba(139,92,246,0.32)` border, `#c4b5fd` text
- "⚡ Quick add": `rgba(255,255,255,0.05)` bg, `rgba(255,255,255,0.09)` border, muted text
- Both: `border-radius: 11px`, 12px/700

### Food List Card
- `rgba(255,255,255,0.04)` bg / `rgba(255,255,255,0.07)` border / `border-radius: 14px`
- Rows separated by `1px rgba(255,255,255,0.04)` border

### Food Row
- Name: 13px/600/white (ellipsis) · Macros: 10px/`rgba(255,255,255,0.28)` · kcal: 12px/700/`rgba(255,255,255,0.40)`
- "+" button: 28×28px, `border-radius: 8px`, `#8b5cf6` bg, violet glow

### Saved Meal Row
- Row bg: `rgba(139,92,246,0.04)` tint
- "Meal" tag inline beside name: 8px/700, violet tint chip

---

## Screen 6 — More / Settings ✅
_Mockup: `docs/superpowers/designs/06-more-settings.html`_

**Nav tab:** More (fifth item, rightmost in pill nav).

### Section Groups

**Insights** — Stats · Charts (both navigate deeper)
**Planning** — Plan (navigates deeper)
**Appearance** — Font picker (inline, no navigation)
**App** — Health Connect status · AI Insights toggle
**Data** — Export backup · Import backup (side-by-side action cards)

### Menu Row Style
- Icon: 34×34px, `border-radius: 10px`, `rgba(139,92,246,0.14)` bg, `rgba(139,92,246,0.22)` border
- Title: 14px/600/white · Detail: 11px/`rgba(255,255,255,0.28)` · Chevron: `›` in `rgba(255,255,255,0.20)`
- Row divider: `1px solid rgba(255,255,255,0.05)`, none on last row

### Font Picker (inline chips in Appearance row)
- 3 options: Default · Space · Jakarta · `border-radius: 7px` · 10px/700
- Active: `rgba(139,92,246,0.22)` bg, `rgba(139,92,246,0.35)` border, `#c4b5fd`

### Health Connect Badge
- Connected: `rgba(52,211,153,0.12)` bg, `#34d399` text + 5px dot indicator

### Export / Import Action Cards
- Side-by-side 2-column, `border-radius: 12px`, icon + label stacked
- Export: violet tint (`rgba(139,92,246,0.12)` bg, `#c4b5fd` text)
- Import: muted surface
