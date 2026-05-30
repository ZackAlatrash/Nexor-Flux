# Data Model

## Room entities
- `DailyLogEntity`: one row per date; bodyweight, waist, steps, sleep, energy, hunger, soreness, trained flag, and notes.
- `MealEntryEntity`: manual and quick-added meals; date, meal type, name, calories, protein, carbs, and fat.
- `SavedFoodEntity`: personal saved foods; name, serving, calories, protein, carbs, and fat.
- `CatalogFoodEntity`: read-only locally imported reference foods; source, source version, external ID, name, serving, calories, protein, carbs, and fat.
- `SavedMealEntity`: reusable meals; name, meal type, calories, protein, carbs, and fat.
- `LiftPerformanceEntity`: marker lifts; date, lift name, weight, reps, sets, and optional RIR.
- `WeeklyReviewEntity`: stored deterministic verdict; week start, verdict, calorie change, reason codes, and generated timestamp.

## Units
- Weight: kg
- Waist: cm
- Sleep: hours
- Scores: 1 to 10
- Calories: kcal
- Macros: grams

## Migration policy
The MVP starts at Room schema version 1 with destructive migrations disabled. Future schema changes should add explicit migrations before release use.

## Backup contract
`BackupPayload` serializes personal Room tables plus `PlanPreferences` to local JSON. Reference catalog rows are intentionally excluded because the user imports them separately from an official accepted dataset.

`PersonalFoodsPayload` is a separate local JSON contract for merging editable personal foods without deleting existing rows.
