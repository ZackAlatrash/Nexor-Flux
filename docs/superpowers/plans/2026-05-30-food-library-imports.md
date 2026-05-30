# Food Library Imports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add review-before-save Health Connect historical-food import, local official NEVO CSV catalog import, and local personal-food JSON import/export while keeping Room as the source of truth.

**Architecture:** Keep editable personal foods in `saved_foods` and add a read-only `catalog_foods` table for imported NEVO rows. Put parsing, normalization, and deduplication in pure Kotlin classes; keep Android file pickers and Health Connect reads at the repository/ViewModel boundary. Extend the existing manual `AppContainer` wiring and `StateFlow` UI pattern.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, Coroutines/Flow, kotlinx.serialization JSON, Health Connect, Android Storage Access Framework, JUnit 4.

---

### Task 1: Pure Food Import Models And Normalization

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/foodimport/FoodImportModels.kt`
- Create: `app/src/main/java/com/zack/recomptracker/domain/foodimport/FoodNameNormalizer.kt`
- Create: `app/src/test/java/com/zack/recomptracker/domain/foodimport/FoodNameNormalizerTest.kt`

- [ ] **Step 1: Write failing normalization tests**

```kotlin
assertEquals("greek yogurt", FoodNameNormalizer.normalize("  Greek   Yogurt, "))
assertEquals(
    FoodIdentity("greek yogurt", 160, 25.0, 10.0, 2.0),
    FoodImportCandidate(null, "Greek yogurt", "100g", 160, 25.04, 10.01, 2.0).identity(),
)
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*FoodNameNormalizerTest'`

Expected: FAIL because the `domain.foodimport` classes do not exist.

- [ ] **Step 3: Add minimal pure Kotlin models and normalizer**

```kotlin
data class FoodImportCandidate(...)
data class FoodImportSummary(...)
data class FoodIdentity(...)
object FoodNameNormalizer { fun normalize(value: String): String = ... }
fun FoodImportCandidate.identity(): FoodIdentity = ...
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*FoodNameNormalizerTest'`

Expected: PASS.

### Task 2: NEVO CSV Parser

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/foodimport/NevoCsvParser.kt`
- Create: `app/src/test/java/com/zack/recomptracker/domain/foodimport/NevoCsvParserTest.kt`

- [ ] **Step 1: Write failing parser tests**

Cover quoted fields, semicolon CSV with decimal commas, comma CSV with decimal points, required-header rejection, malformed row rejection, and duplicate NEVO-code rejection.

```kotlin
val result = NevoCsvParser().parse(
    "NEVO-code;Voedingsmiddelnaam Nederlands;ENERCC kcal;PROT g;CHO g;FAT g\n" +
        "123;Halfvolle melk;47;3,5;4,8;1,5"
)
assertEquals("123", result.single().externalId)
assertEquals(3.5, result.single().proteinG, 0.001)
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*NevoCsvParserTest'`

Expected: FAIL because `NevoCsvParser` does not exist.

- [ ] **Step 3: Implement the CSV parser**

Implement a small quoted-field parser with delimiter detection (`;` or `,`), normalized header lookup, numeric parsing that accepts decimal comma or point, and fail-fast validation. Return `List<FoodImportCandidate>` with `servingName = "100g"`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*NevoCsvParserTest'`

Expected: PASS.

### Task 3: Personal-Food JSON Codec And Pure Merge Rules

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/foodimport/PersonalFoodsJsonCodec.kt`
- Create: `app/src/main/java/com/zack/recomptracker/domain/foodimport/PersonalFoodMerger.kt`
- Create: `app/src/test/java/com/zack/recomptracker/domain/foodimport/PersonalFoodsJsonCodecTest.kt`
- Create: `app/src/test/java/com/zack/recomptracker/domain/foodimport/PersonalFoodMergerTest.kt`

- [ ] **Step 1: Write failing JSON and merge tests**

```kotlin
val encoded = PersonalFoodsJsonCodec().encode(listOf(candidate))
assertEquals(listOf(candidate), PersonalFoodsJsonCodec().decode(encoded).foods)

val result = PersonalFoodMerger.newFoods(existing = listOf(candidate), incoming = listOf(candidate, newCandidate))
assertEquals(listOf(newCandidate), result.foodsToInsert)
assertEquals(1, result.duplicateCount)
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*PersonalFoodsJsonCodecTest' --tests '*PersonalFoodMergerTest'`

Expected: FAIL because codec and merger classes do not exist.

- [ ] **Step 3: Add serializable payload and deterministic merge logic**

Use `PersonalFoodsPayload(version = 1, exportedAt, foods)`. Validate non-blank names, non-negative macro values, supported version, and deduplicate by `FoodIdentity`.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*PersonalFoodsJsonCodecTest' --tests '*PersonalFoodMergerTest'`

Expected: PASS.

### Task 4: Room Catalog Storage And Repositories

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/local/entity/CatalogFoodEntity.kt`
- Create: `app/src/main/java/com/zack/recomptracker/data/local/dao/CatalogFoodDao.kt`
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/FoodCatalogRepository.kt`
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/PersonalFoodRepository.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`
- Modify: `app/src/androidTest/java/com/zack/recomptracker/data/RecompDatabaseTest.kt`

- [ ] **Step 1: Extend the Room instrumented test before production edits**

Add a catalog insert/read assertion:

```kotlin
database.catalogFoodDao().insertAll(listOf(CatalogFoodEntity(...)))
assertEquals(1, database.catalogFoodDao().getAll().size)
```

- [ ] **Step 2: Run the focused instrumented test when a device is available and verify RED**

Run: `./gradlew connectedDebugAndroidTest --tests 'com.zack.recomptracker.data.RecompDatabaseTest'`

Expected: FAIL because catalog Room types do not exist. If no device is available, record that boundary and continue with compile-time RED from `assembleDebug`.

- [ ] **Step 3: Add catalog entity, DAO, migration, and repositories**

Create `CatalogFoodEntity` with unique `(source, externalId)` index. Add `CatalogFoodDao.observeAll()`, `getAll()`, `getSourceVersion(source)`, `insertAll()`, and `deleteBySource()`. Advance Room version to `3` and create `MIGRATION_2_3`. Add:

```kotlin
suspend fun replaceNevoCatalog(rawCsv: String): FoodImportSummary
suspend fun removeNevoCatalog()
fun observeCatalogFoods(): Flow<List<CatalogFoodEntity>>
suspend fun createPersonalFoodsJson(): String
suspend fun mergePersonalFoodsJson(rawJson: String): FoodImportSummary
suspend fun mergePersonalFoods(candidates: List<FoodImportCandidate>): FoodImportSummary
```

- [ ] **Step 4: Wire repositories through `AppContainer`**

Instantiate `FoodCatalogRepository(database, parser)` and `PersonalFoodRepository(savedFoodDao, codec, merger)` and pass them to ViewModels in later tasks.

- [ ] **Step 5: Run compile and available Room test verification**

Run: `./gradlew assembleDebug`

Expected: PASS.

Run when a device exists: `./gradlew connectedDebugAndroidTest --tests 'com.zack.recomptracker.data.RecompDatabaseTest'`

Expected: PASS.

### Task 5: Health Connect Nutrition History And Candidate Mapping

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/foodimport/HistoricalFoodImporter.kt`
- Create: `app/src/test/java/com/zack/recomptracker/domain/foodimport/HistoricalFoodImporterTest.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/health/HealthConnectRepository.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/test/java/com/zack/recomptracker/HealthConnectManifestTest.kt`

- [ ] **Step 1: Write failing historical candidate tests**

```kotlin
val result = HistoricalFoodImporter.reviewCandidates(existing, history)
assertEquals(listOf(expectedUniqueNewFood), result)
```

Cover duplicate history records, already-saved personal foods, blank names, and zero-filled optional macros.

- [ ] **Step 2: Run focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*HistoricalFoodImporterTest'`

Expected: FAIL because `HistoricalFoodImporter` does not exist.

- [ ] **Step 3: Add pure historical importer**

Map and deduplicate `FoodImportCandidate` rows by `FoodIdentity`, filtering blank names.

- [ ] **Step 4: Add manifest RED assertions**

Extend `HealthConnectManifestTest` to require `android.permission.health.READ_NUTRITION` and `android.permission.health.READ_HEALTH_DATA_HISTORY`.

- [ ] **Step 5: Run manifest test and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*HealthConnectManifestTest'`

Expected: FAIL because the manifest permission is absent.

- [ ] **Step 6: Add nutrition and history permissions plus Health Connect history read**

Add the nutrition permission, `HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY`, `supportsHistoricalNutritionImport()`, `hasHistoricalNutritionPermissions()`, and `readHistoricalNutrition(days = 365)`. Guard the scan with `HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY`. Paginate `ReadRecordsRequest(NutritionRecord::class, ..., pageToken = pageToken)` until the token is null. Map name, energy kcal, protein grams, carbohydrate grams, and fat grams into pure candidates.

- [ ] **Step 7: Run focused tests and compile verification**

Run: `./gradlew testDebugUnitTest --tests '*HistoricalFoodImporterTest' --tests '*HealthConnectManifestTest' && ./gradlew assembleDebug`

Expected: PASS.

### Task 6: Settings Import And Export UI

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add repository-backed Settings state and actions**

Add:

```kotlin
val nevoSourceVersion: String? = null
val historicalFoodCandidates: List<FoodImportCandidate> = emptyList()
val selectedHistoricalFoodIdentities: Set<FoodIdentity> = emptySet()
val pendingNutritionPermissionRequest: Boolean = false
```

Add URI actions for `importNevoFromUri`, `exportPersonalFoodsToUri`, and `importPersonalFoodsFromUri`. Add historical-food actions for permission request, scan, selection toggle, review dismissal, and selected merge.

- [ ] **Step 2: Add Storage Access Framework launchers**

Use `OpenDocument` for NEVO CSV and personal-food JSON. Use `CreateDocument("application/json")` for personal-food export. Add a separate Health Connect nutrition-permission launcher.

- [ ] **Step 3: Add Settings controls and historical-food review dialog**

Under Backup add personal-food JSON controls. Add a `Dutch food catalog` card with NEVO import/remove controls and required attribution when loaded. Add a Health Connect history-import button and a review dialog with selectable rows.

- [ ] **Step 4: Run compile verification**

Run: `./gradlew assembleDebug`

Expected: PASS.

### Task 7: Combined Personal And NEVO Food Search

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add search display model**

```kotlin
data class FoodLibraryItem(
    val key: String,
    val food: SavedFoodEntity,
    val sourceLabel: String? = null,
)
```

Observe catalog rows alongside saved foods. Convert catalog rows to loggable `SavedFoodEntity` values only at the UI boundary. Exclude catalog rows for blank queries and label NEVO results.

- [ ] **Step 2: Update Compose result rendering**

Render `FoodLibraryItem`, show the optional `NEVO` source label, and keep the existing quantity dialog and scaled logging behavior.

- [ ] **Step 3: Run compile verification**

Run: `./gradlew assembleDebug`

Expected: PASS.

### Task 8: Documentation And Full Verification

**Files:**
- Modify: `docs/DATA_MODEL.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `README.md`

- [ ] **Step 1: Document catalog ownership and import boundaries**

Describe `catalog_foods`, personal-only JSON, official NEVO CSV import, required RIVM attribution, and Health Connect history limits.

- [ ] **Step 2: Run JVM test suite**

Run: `./gradlew test`

Expected: PASS.

- [ ] **Step 3: Run debug build**

Run: `./gradlew assembleDebug`

Expected: PASS.

- [ ] **Step 4: Run instrumented tests when a device is available**

Run: `./gradlew connectedAndroidTest`

Expected: PASS when an emulator or device is available. Record the verification boundary otherwise.

- [ ] **Step 5: Inspect final diff**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; preserve unrelated pre-existing worktree changes.
