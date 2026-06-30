# Section Improvement Plans

Audit + focused improvement plans produced 2026-06-30 (branch `audit/section-improvement-plan`).
The app was inventoried section-by-section, each section scored, and a focused improvement
plan written per section so we can improve **one whole section at a time** rather than adding
disconnected features.

## How the sections scored

Scored 1–10. Focus Value = Importance + (10−Quality) + Frequency + Vision + Potential.

| Section | Quality now | Potential | Focus Value | Difficulty | Plan |
|---|:-:|:-:|:-:|:-:|---|
| Food / Calorie | 8 | 5 | 37 | Med | [03-food-calorie-tracking.md](03-food-calorie-tracking.md) |
| UI / Design System | 6 | 8 | 35 | Low | [02-ui-design-system.md](02-ui-design-system.md) |
| **Steps & Activity** ⭐ | 5 | 9 | 34 | Med | [01-steps-and-activity.md](01-steps-and-activity.md) |
| Progress / Analytics | 8 | 6 | 31 | Med | [04-progress-analytics.md](04-progress-analytics.md) |
| Workout / Training | 8 | 5 | 31 | Med | [05-workout-training.md](05-workout-training.md) |
| AI / Coaching | 9 | 5 | 25 | High | [06-ai-coaching.md](06-ai-coaching.md) |
| Onboarding / Profile / Settings | 6 | 7 | — | Med | [07-onboarding-profile-settings.md](07-onboarding-profile-settings.md) |

## Recommended order

1. **Steps & Activity** ⭐ — the one pillar the vision names but the app stubs. Current focus.
2. **UI / Design System** — foundational, lowest risk; do before other UI-heavy passes.
3. Food / Progress / Workout polish, then AI, then the broader Settings consolidation.

Each plan follows the same shape: current problems → UX → UI → data/model → AI opportunities
→ quick wins → medium → bigger refactors → what to avoid for now.
