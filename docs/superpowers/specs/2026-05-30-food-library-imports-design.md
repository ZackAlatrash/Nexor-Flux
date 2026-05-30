# Food Library Imports - Design Spec
**Date:** 2026-05-30
**Scope:** Add review-before-save import of historical foods exposed by Health Connect, local import of an official NEVO CSV catalog, and local JSON import/export for personal foods. Room remains the source of truth and the app remains local-first.

---

## 1. Behavior Summary

| Feature | Behavior |
|---|---|
| Samsung Health historical foods | Read `NutritionRecord` history from Health Connect for the last 365 days, derive unique food candidates, show a review list, and save only selected rows as personal foods. |
| NEVO catalog | Let the user pick an official NEVO CSV export from local storage. Validate and parse it locally, then transactionally replace the previous NEVO catalog. |
| Personal-food JSON | Export and merge-import only personal foods through Android Storage Access Framework. This stays separate from the existing full-app backup. |
| Food search | Search personal foods and NEVO catalog foods together in the Food Library. |
| Offline behavior | No backend, online search, network sync, or unofficial supermarket API. |

The initial Dutch reference source is NEVO. The catalog boundary must allow another offline source, such as a curated Open Food Facts snapshot, to be added later without changing personal-food storage.

---

## 2. Data Ownership

Personal foods and reference foods have different ownership and lifecycle rules, so they use separate Room tables.

### Existing `saved_foods`

`SavedFoodEntity` continues to represent editable personal foods. These include:
- foods created manually in the app;
- foods selected from the Samsung Health history review;
- foods merged from a personal-food JSON file.

### New `catalog_foods`

`CatalogFoodEntity` represents read-only reference foods imported from external datasets:

```kotlin
@Entity(
    tableName = "catalog_foods",
    indices = [
        Index(value = ["source", "externalId"], unique = true),
        Index(value = ["name"]),
    ],
)
data class CatalogFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val sourceVersion: String,
    val externalId: String,
    val name: String,
    val servingName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
```

For NEVO:
- `source = "NEVO"`
- `externalId` is the NEVO code
- `servingName = "100g"` unless the official name identifies a per-100ml liquid value
- macro values remain unchanged from the imported dataset

The Room database version advances from `2` to `3`. `MIGRATION_2_3` creates `catalog_foods` and its indices without altering existing user data.

---

## 3. Pure Kotlin Import Layer

Parsing, normalization, validation, and deduplication remain pure Kotlin so JVM tests can cover them without Android.

### Shared models

```kotlin
data class FoodImportCandidate(
    val externalId: String?,
    val name: String,
    val servingName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

data class FoodImportSummary(
    val importedCount: Int,
    val skippedCount: Int,
    val duplicateCount: Int,
)
```

### Normalization

`FoodNameNormalizer` trims, lowercases with `Locale.ROOT`, collapses internal whitespace, and removes leading or trailing punctuation used only as separators. It does not rewrite words or macro values.

Personal-food deduplication uses:
- normalized name;
- calories;
- protein, carbs, and fat rounded to one decimal place.

Catalog replacement uses the source and external ID. Invalid or duplicate external IDs fail validation before Room changes.

---

## 4. NEVO CSV Import

### Input contract

The app accepts a CSV file selected through `ActivityResultContracts.OpenDocument`. The initial adapter targets an official NEVO-online CSV export. The parser:
- handles quoted CSV fields and UTF-8 text;
- resolves a fixed set of documented header aliases for NEVO code, name, energy in kcal, protein, carbohydrates, and fat;
- accepts decimal commas or decimal points;
- imports values per 100g, preserving the official source values;
- rejects files missing required columns;
- rejects files with no valid rows;
- reports malformed rows without partially updating Room.

The full NEVO dataset download is behind RIVM's conditions-acceptance flow. If its downloaded package is not the same CSV shape, the user can export CSV from NEVO-online or provide a sample of the accepted package for a later adapter. The app does not bypass the RIVM acceptance flow.

### Transaction

`FoodCatalogRepository.replaceNevoCatalog(rawCsv)` performs:
1. parse and validate the complete CSV in memory;
2. begin a Room transaction;
3. delete existing rows where `source = "NEVO"`;
4. insert the validated NEVO rows;
5. return an import summary.

If parsing, validation, or insertion fails, the previous catalog remains intact.

### Attribution

When NEVO data are present, Settings and Food Library display:

`Based on data from NEVO online version 2025/9.0, RIVM, Bilthoven`

The imported source version is stored with catalog rows. The UI uses the stored version rather than hard-coding it, so a later official dataset can replace the current catalog while preserving the required attribution.

The user can remove the local NEVO catalog from Settings without affecting personal foods or logs.

The initial CSV adapter records `sourceVersion = "2025/9.0"`, the currently supported official NEVO-online release. A later NEVO release requires an adapter-version update before import so attribution cannot silently claim the wrong source version.

---

## 5. Health Connect Historical-Food Import

The existing `HealthConnectRepository` remains the only Android-facing Health Connect adapter.

### Permissions

Add:

```xml
<uses-permission android:name="android.permission.health.READ_NUTRITION" />
```

Keep the existing step, weight, and sleep permissions as the base Health Connect permission set. Add `HealthPermission.getReadPermission(NutritionRecord::class)` as a separate historical-food import permission requested only when the user starts that import. Existing step, weight, and sleep behavior remains unchanged for users who do not grant nutrition access.

### Read flow

`HealthConnectRepository.readHistoricalNutrition(days = 365)`:
- reads `NutritionRecord` rows from `now - 365 days` through `now`;
- handles Health Connect pagination until all records are read;
- maps rows with a non-blank name and usable macro data into pure Kotlin candidate inputs;
- treats absent optional nutrient values as zero;
- catches Health Connect errors and returns a typed failure for visible Settings feedback.

This imports history exposed through Health Connect. It does not claim access to Samsung Health custom foods that were never logged or records Samsung Health did not publish to Health Connect.

### Candidate review

`HistoricalFoodImporter` normalizes and deduplicates candidates, removes foods already present in `saved_foods`, and returns review rows with selection enabled by default.

Settings adds an `Import foods from Health Connect` action under the existing Health Connect section. The action:
1. checks Health Connect availability and nutrition permission;
2. reads the last 365 days;
3. opens a review dialog showing candidate name and macros;
4. lets the user select or deselect candidates;
5. saves selected candidates into `saved_foods`;
6. shows imported, skipped, and duplicate counts.

Saving is explicit. Dismissing the review dialog writes nothing.

---

## 6. Personal-Food JSON

Add a separate JSON contract:

```kotlin
@Serializable
data class PersonalFoodsPayload(
    val version: Int = 1,
    val exportedAt: String,
    val foods: List<PersonalFoodExport>,
)
```

`PersonalFoodExport` contains name, serving name, calories, protein, carbs, and fat. It does not expose Room IDs.

`PersonalFoodRepository` adds:
- `createPersonalFoodsJson()`
- `mergePersonalFoodsJson(rawJson)`
- `mergePersonalFoods(candidates)`

Merge behavior:
- validate the entire payload before writing;
- normalize and deduplicate against existing personal foods;
- preserve existing rows;
- insert only new rows;
- return a summary.

Settings adds `Export personal foods JSON` and `Import personal foods JSON` buttons next to the existing full-app backup actions.

Reference catalog rows are not included in personal-food JSON or the existing full-app backup. They came from a separately accepted NEVO file and can be restored by importing that official file again. `Reset all local data` removes reference catalogs along with the other local Room tables.

---

## 7. Food Library Search

`FoodLibraryViewModel` observes personal foods and catalog foods. Search results use a small display model that records whether the row is personal or catalog-backed.

Behavior:
- empty search shows personal foods first and does not dump the complete NEVO catalog into the initial screen;
- a non-blank query searches both sources;
- personal results appear before catalog results;
- NEVO results carry a visible `NEVO` label;
- selecting either type opens the existing quantity dialog and logs scaled macros;
- selecting a NEVO row does not silently save it as a personal food.

Personal-food category filters remain available. NEVO rows participate in protein and carbohydrate filters only when a query is present.

---

## 8. Dependency Wiring

Continue manual dependency wiring in `AppContainer`, as required by the project:
- add `CatalogFoodDao`;
- add `FoodCatalogRepository`;
- add `PersonalFoodRepository`;
- pass repositories into `FoodLibraryViewModel` and `SettingsViewModel`.

Do not add Hilt, Retrofit, WorkManager, or networking.

---

## 9. Error Handling

| Scenario | Behavior |
|---|---|
| NEVO file has unsupported headers | Show a validation error; keep the existing catalog unchanged. |
| NEVO file contains malformed rows | Show row-level validation count; keep the existing catalog unchanged. |
| NEVO import is cancelled | No write and no error. |
| Personal-food JSON is invalid | Show an import error; write nothing. |
| Personal-food JSON contains existing foods | Skip duplicates and report the count. |
| Health Connect nutrition permission denied | Keep existing Health Connect behavior available where permitted and explain that historical food import needs nutrition permission. |
| Health Connect exposes no named nutrition rows | Show `No importable foods found in the last 365 days.` |
| Health Connect read fails | Show a visible error and write nothing. |

---

## 10. Testing

### JVM tests

Add focused unit tests for:
- CSV parsing with quoted fields and decimal commas;
- required-header rejection;
- malformed-row rejection;
- source/external-ID duplicate rejection;
- food-name normalization;
- Samsung history candidate mapping and deduplication;
- personal-food JSON round trip;
- personal-food merge behavior.

### Instrumented Room tests

Extend `RecompDatabaseTest` for:
- migration `2 -> 3`;
- transactional replacement of only NEVO catalog rows;
- personal foods surviving NEVO replacement and removal.

### Build verification

Run:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew connectedAndroidTest
```

`connectedAndroidTest` is required when an emulator or device is available. A physical Android device with Health Connect nutrition data is required for manual verification of Samsung Health historical import.

---

## 11. Out Of Scope

- Online Open Food Facts search
- Bundling or redistributing NEVO data in the repository or APK
- Direct Samsung Health SDK integration
- Import of Samsung foods that have never appeared in Health Connect history
- Barcode scanning
- Editing NEVO rows
- Hosted AI or AI-driven nutrition decisions

---

## 12. Implementation Order

1. Add pure Kotlin candidate, normalization, CSV parser, and JSON models with tests.
2. Add `catalog_foods`, DAO, migration, repositories, and Room tests.
3. Add personal-food JSON Settings actions through Storage Access Framework.
4. Add NEVO file import, removal, source version, and attribution UI.
5. Add Health Connect nutrition permission, historical read, review dialog, and selected-food merge.
6. Update Food Library combined search.
7. Run JVM tests, debug build, available instrumented tests, and physical-device manual verification where possible.
