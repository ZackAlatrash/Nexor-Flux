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
- [ ] Body screen
- [ ] Progress screen
- [ ] Food Library screen
- [ ] Scanner screen
- [ ] Settings / More screen
