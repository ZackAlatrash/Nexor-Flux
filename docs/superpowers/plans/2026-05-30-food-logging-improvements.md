# Food Logging Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give food logging a Samsung-style amount picker (servings↔grams toggle, +/- stepper, live macro preview), editable personal foods and logged entries, a Recents quick-access row, and a quick-add-calories one-off entry.

**Architecture:** Add a pure-Kotlin scaling/recents core in `domain/food/` (JVM-tested, no Android). Extend `SavedFoodEntity` (optional household serving) and `MealEntryEntity` (amount + per-100g base snapshot) with a nullable-only Room migration `3 → 4`. Logging math is computed in `FoodLibraryViewModel`/`FoodLibraryUiState` and rendered as a Material3 `ModalBottomSheet`. Macros are always stored per 100 g. The app stays offline/local-first with manual DI in `AppContainer`.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Room, JUnit4 (JVM unit tests under `app/src/test`, instrumented tests under `app/src/androidTest`), Gradle with local SDK at `.android-sdk/`.

---

## File Structure

**Create:**
- `app/src/main/java/com/zack/recomptracker/domain/food/FoodScaling.kt` — `FoodMacros` + per-100g scaling math.
- `app/src/main/java/com/zack/recomptracker/domain/food/RecentFoods.kt` — derive distinct recent foods from logged entries.
- `app/src/test/java/com/zack/recomptracker/domain/food/FoodScalingTest.kt`
- `app/src/test/java/com/zack/recomptracker/domain/food/RecentFoodsTest.kt`

**Modify:**
- `app/src/main/java/com/zack/recomptracker/data/local/entity/SavedFoodEntity.kt` — add `householdServingName`, `householdServingGrams`.
- `app/src/main/java/com/zack/recomptracker/data/local/entity/MealEntryEntity.kt` — add amount + base-snapshot columns.
- `app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt` — version 4 + `MIGRATION_3_4`.
- `app/src/main/java/com/zack/recomptracker/data/local/dao/MealEntryDao.kt` — add `observeFoodLibraryEntries()`.
- `app/src/main/java/com/zack/recomptracker/data/repository/LogModels.kt` — extend `MealEntryInput`.
- `app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt` — write snapshot in `addMealToSlot`, add `updateMealEntry`, `observeRecentFoods`.
- `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt` — amount-picker state, recents, quick add, edit food.
- `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt` — bottom sheet, recents row, quick-add form, edit affordance.
- `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt` — tap-to-edit a logged entry.
- `app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt` — update-entry action.
- `app/src/test/java/com/zack/recomptracker/ui/FoodLibraryUiStateTest.kt` — preview/defaults/recents state tests.
- `app/src/androidTest/java/com/zack/recomptracker/data/RecompDatabaseTest.kt` — migration `3 → 4` test.

---

## Conventions

- Build/test commands assume the repo root and the local SDK (`local.properties` already points at `.android-sdk`).
- JVM unit tests: `./gradlew testDebugUnitTest`. A single class: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.domain.food.FoodScalingTest"`.
- Instrumented tests (need emulator/device): `./gradlew connectedAndroidTest`.
- Commit after each task with the message shown.

---

## Task 1: Pure-Kotlin scaling core

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/food/FoodScaling.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/food/FoodScalingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/domain/food/FoodScalingTest.kt`:

```kotlin
package com.zack.recomptracker.domain.food

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodScalingTest {
    private val base = FoodMacros(calories = 120, proteinG = 24.0, carbsG = 3.0, fatG = 2.0)

    @Test
    fun scalesPer100gByGrams() {
        val result = FoodScaling.scale(base, grams = 60.0)
        assertEquals(72, result.calories)
        assertEquals(14.4, result.proteinG, 0.001)
        assertEquals(1.8, result.carbsG, 0.001)
        assertEquals(1.2, result.fatG, 0.001)
    }

    @Test
    fun roundsCaloriesToNearestInt() {
        // 120 kcal/100g * 33g = 39.6 -> 40
        assertEquals(40, FoodScaling.scale(base, grams = 33.0).calories)
    }

    @Test
    fun convertsServingsToGrams() {
        assertEquals(90.0, FoodScaling.gramsForServings(servings = 3.0, servingGrams = 30.0), 0.001)
    }

    @Test
    fun clampsGramsToMinimum() {
        assertEquals(FoodScaling.MIN_GRAMS, FoodScaling.normalizeGrams(0.0), 0.001)
        assertEquals(FoodScaling.MIN_GRAMS, FoodScaling.normalizeGrams(-5.0), 0.001)
        assertEquals(250.0, FoodScaling.normalizeGrams(250.0), 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.domain.food.FoodScalingTest"`
Expected: FAIL — `Unresolved reference: FoodMacros` / `FoodScaling`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/domain/food/FoodScaling.kt`:

```kotlin
package com.zack.recomptracker.domain.food

import kotlin.math.roundToInt

/** Macros expressed for a specific amount of food. As a per-100g base, all four are "per 100 g". */
data class FoodMacros(
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

object FoodScaling {
    const val MIN_GRAMS: Double = 1.0

    /** Scales a per-100g base to [grams]. Calories round to the nearest Int; macros stay Double. */
    fun scale(basePer100: FoodMacros, grams: Double): FoodMacros {
        val factor = grams / 100.0
        return FoodMacros(
            calories = (basePer100.calories * factor).roundToInt(),
            proteinG = basePer100.proteinG * factor,
            carbsG = basePer100.carbsG * factor,
            fatG = basePer100.fatG * factor,
        )
    }

    fun gramsForServings(servings: Double, servingGrams: Double): Double = servings * servingGrams

    fun normalizeGrams(grams: Double): Double = if (grams < MIN_GRAMS) MIN_GRAMS else grams
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.domain.food.FoodScalingTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/food/FoodScaling.kt app/src/test/java/com/zack/recomptracker/domain/food/FoodScalingTest.kt
git commit -m "feat: add pure-Kotlin food scaling core"
```

---

## Task 2: Recents derivation

`MealEntryEntity` is a plain data class at runtime, so it is usable directly in JVM tests (see existing `FoodLibraryUiStateTest`).

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/food/RecentFoods.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/food/RecentFoodsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/domain/food/RecentFoodsTest.kt`:

```kotlin
package com.zack.recomptracker.domain.food

import com.zack.recomptracker.data.local.entity.MealEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFoodsTest {
    private fun entry(
        id: Long,
        name: String,
        mealType: String = "FOOD_LIBRARY",
        base: Int? = 120,
    ) = MealEntryEntity(
        id = id,
        date = "2026-05-30",
        mealType = mealType,
        name = name,
        calories = 100,
        proteinG = 1.0,
        carbsG = 1.0,
        fatG = 1.0,
        amountGrams = 100.0,
        basePer100Calories = base,
        basePer100ProteinG = base?.let { 1.0 },
        basePer100CarbsG = base?.let { 1.0 },
        basePer100FatG = base?.let { 1.0 },
    )

    @Test
    fun keepsMostRecentEntryPerNameNewestFirst() {
        val result = RecentFoods.fromEntries(
            listOf(
                entry(1, "Whey"),
                entry(2, "Oats"),
                entry(3, "whey"), // same name, newer id
            ),
        )
        assertEquals(listOf("whey", "Oats"), result.map { it.name })
        assertEquals(3L, result.first().id)
    }

    @Test
    fun ignoresQuickAddAndUnscalableEntries() {
        val result = RecentFoods.fromEntries(
            listOf(
                entry(1, "Quick add", mealType = "QUICK_ADD"),
                entry(2, "Mystery", base = null),
                entry(3, "Chicken"),
            ),
        )
        assertEquals(listOf("Chicken"), result.map { it.name })
    }

    @Test
    fun capsToLimit() {
        val entries = (1..12).map { entry(it.toLong(), "Food$it") }
        assertEquals(8, RecentFoods.fromEntries(entries, limit = 8).size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.domain.food.RecentFoodsTest"`
Expected: FAIL — `Unresolved reference: RecentFoods`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/domain/food/RecentFoods.kt`:

```kotlin
package com.zack.recomptracker.domain.food

import com.zack.recomptracker.data.local.entity.MealEntryEntity

object RecentFoods {
    const val DEFAULT_LIMIT: Int = 8

    /**
     * Most recently logged, amount-editable library foods: one row per case-insensitive
     * trimmed name, newest first (highest id), capped at [limit].
     */
    fun fromEntries(entries: List<MealEntryEntity>, limit: Int = DEFAULT_LIMIT): List<MealEntryEntity> =
        entries
            .filter { it.mealType == "FOOD_LIBRARY" && it.basePer100Calories != null }
            .sortedByDescending { it.id }
            .distinctBy { it.name.trim().lowercase() }
            .take(limit)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.domain.food.RecentFoodsTest"`
Expected: PASS.

> Note: this test depends on `MealEntryEntity` having the new snapshot fields. If it does not compile yet, do Task 3 first, then return. (If executing in order, Task 3 follows immediately and the suite is run green at the end of Task 3.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/food/RecentFoods.kt app/src/test/java/com/zack/recomptracker/domain/food/RecentFoodsTest.kt
git commit -m "feat: derive recent foods from logged entries"
```

---

## Task 3: Extend entities

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/entity/SavedFoodEntity.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/entity/MealEntryEntity.kt`

- [ ] **Step 1: Add household serving to `SavedFoodEntity`**

Replace the body of `SavedFoodEntity.kt` with:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "saved_foods")
data class SavedFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servingName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val householdServingName: String? = null,
    val householdServingGrams: Double? = null,
)
```

- [ ] **Step 2: Add amount + base snapshot to `MealEntryEntity`**

Replace the body of `MealEntryEntity.kt` with:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "meal_entries",
    indices = [Index(value = ["date"])],
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealType: String,
    val name: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val slotId: Long? = null,
    val amountGrams: Double? = null,
    val basePer100Calories: Int? = null,
    val basePer100ProteinG: Double? = null,
    val basePer100CarbsG: Double? = null,
    val basePer100FatG: Double? = null,
    val entryServingName: String? = null,
    val entryServingGrams: Double? = null,
)
```

- [ ] **Step 3: Bump DB version and add migration**

In `app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt`:

Change `version = 3,` to `version = 4,`.

Add this migration object inside `companion object`, immediately after `MIGRATION_2_3`:

```kotlin
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_foods ADD COLUMN householdServingName TEXT")
                db.execSQL("ALTER TABLE saved_foods ADD COLUMN householdServingGrams REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN amountGrams REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN basePer100Calories INTEGER")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN basePer100ProteinG REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN basePer100CarbsG REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN basePer100FatG REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN entryServingName TEXT")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN entryServingGrams REAL")
            }
        }
```

Change the builder line from:

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

to:

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```

- [ ] **Step 4: Verify build and the domain suite compile/pass**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.domain.food.*"`
Expected: PASS (Task 1 + Task 2 tests now compile against the new fields).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/local/entity/SavedFoodEntity.kt app/src/main/java/com/zack/recomptracker/data/local/entity/MealEntryEntity.kt app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt
git commit -m "feat: add household serving and entry amount/base snapshot columns (db v4)"
```

---

## Task 4: Migration 3→4 instrumented test

**Files:**
- Modify: `app/src/androidTest/java/com/zack/recomptracker/data/RecompDatabaseTest.kt`

- [ ] **Step 1: Write the migration test**

In `RecompDatabaseTest.kt`, add this test method after `migratesVersion2To3WithoutDroppingPersonalFoods()` (before `createVersion2Schema`):

```kotlin
    @Test
    fun migratesVersion3To4PreservingDataAndAddingColumns() = runBlocking {
        val databaseName = "migration-3-4-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersion2Schema(db)
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS catalog_foods (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, source TEXT NOT NULL, " +
                                    "sourceVersion TEXT NOT NULL, externalId TEXT NOT NULL, name TEXT NOT NULL, " +
                                    "servingName TEXT NOT NULL, calories INTEGER NOT NULL, proteinG REAL NOT NULL, " +
                                    "carbsG REAL NOT NULL, fatG REAL NOT NULL)",
                            )
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO saved_foods (name, servingName, calories, proteinG, carbsG, fatG) " +
                "VALUES ('Whey', '100g', 120, 24.0, 3.0, 2.0)",
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO meal_entries (date, mealType, name, calories, proteinG, carbsG, fatG, slotId) " +
                "VALUES ('2026-05-30', 'FOOD_LIBRARY', 'Whey', 72, 14, 2, 1, NULL)",
        )
        helper.close()

        val migrated = Room.databaseBuilder(context, RecompDatabase::class.java, databaseName)
            .addMigrations(RecompDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val foods = migrated.savedFoodDao().getAll()
            val meals = migrated.mealEntryDao().getAll()
            assertEquals(1, foods.size)
            assertEquals(null, foods.first().householdServingName)
            assertEquals(1, meals.size)
            assertEquals(null, meals.first().amountGrams)
            assertEquals(null, meals.first().basePer100Calories)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }
```

- [ ] **Step 2: Run the migration test (requires emulator/device)**

Run: `./gradlew connectedAndroidTest --tests "com.zack.recomptracker.data.RecompDatabaseTest"`
Expected: PASS. If no device is available, note it and continue; this must be run before merge.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/zack/recomptracker/data/RecompDatabaseTest.kt
git commit -m "test: cover migration 3->4 column add and data preservation"
```

---

## Task 5: Repository + DAO + input model

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/dao/MealEntryDao.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/LogModels.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt`

- [ ] **Step 1: Add the food-library-entries query to `MealEntryDao`**

Add inside the `MealEntryDao` interface (after `observeAll()`):

```kotlin
    @Query("SELECT * FROM meal_entries WHERE mealType = 'FOOD_LIBRARY' ORDER BY id DESC")
    fun observeFoodLibraryEntries(): Flow<List<MealEntryEntity>>
```

- [ ] **Step 2: Extend `MealEntryInput`**

In `LogModels.kt`, replace the `MealEntryInput` data class with:

```kotlin
data class MealEntryInput(
    val date: LocalDate,
    val mealType: String,
    val name: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val amountGrams: Double? = null,
    val basePer100Calories: Int? = null,
    val basePer100ProteinG: Double? = null,
    val basePer100CarbsG: Double? = null,
    val basePer100FatG: Double? = null,
    val entryServingName: String? = null,
    val entryServingGrams: Double? = null,
)
```

- [ ] **Step 3: Carry the snapshot through `addMealToSlot` and add new methods**

In `LogRepository.kt`, replace the existing `addMealToSlot(...)` function with:

```kotlin
    suspend fun addMealToSlot(
        input: MealEntryInput,
        slotId: Long?,
    ): Long = mealEntryDao.insert(
        MealEntryEntity(
            date = input.date.toString(),
            mealType = input.mealType,
            name = input.name.trim(),
            calories = input.calories.coerceAtLeast(0),
            proteinG = input.proteinG.coerceAtLeast(0.0),
            carbsG = input.carbsG.coerceAtLeast(0.0),
            fatG = input.fatG.coerceAtLeast(0.0),
            slotId = slotId,
            amountGrams = input.amountGrams,
            basePer100Calories = input.basePer100Calories,
            basePer100ProteinG = input.basePer100ProteinG,
            basePer100CarbsG = input.basePer100CarbsG,
            basePer100FatG = input.basePer100FatG,
            entryServingName = input.entryServingName,
            entryServingGrams = input.entryServingGrams,
        ),
    )

    suspend fun updateMealEntry(entry: MealEntryEntity) {
        mealEntryDao.update(
            entry.copy(
                name = entry.name.trim(),
                calories = entry.calories.coerceAtLeast(0),
                proteinG = entry.proteinG.coerceAtLeast(0.0),
                carbsG = entry.carbsG.coerceAtLeast(0.0),
                fatG = entry.fatG.coerceAtLeast(0.0),
            ),
        )
    }

    fun observeRecentFoods(limit: Int = RecentFoods.DEFAULT_LIMIT): Flow<List<MealEntryEntity>> =
        mealEntryDao.observeFoodLibraryEntries().map { RecentFoods.fromEntries(it, limit) }
```

Add these imports near the top of `LogRepository.kt`:

```kotlin
import com.zack.recomptracker.domain.food.RecentFoods
import kotlinx.coroutines.flow.map
```

- [ ] **Step 4: Build to verify the data layer compiles**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.domain.food.*"`
Expected: PASS (and the module compiles with the new repository API).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/local/dao/MealEntryDao.kt app/src/main/java/com/zack/recomptracker/data/repository/LogModels.kt app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt
git commit -m "feat: persist entry amount snapshot, add updateMealEntry and observeRecentFoods"
```

---

## Task 6: Amount-picker state + preview (ViewModel, TDD via UI-state tests)

This task adds the testable logic for the bottom sheet. The sheet replaces the old "How many grams?" `AlertDialog`.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/FoodLibraryUiStateTest.kt`

- [ ] **Step 1: Write the failing UI-state tests**

Append to `FoodLibraryUiStateTest.kt` (inside the class):

```kotlin
    private val whey = SavedFoodEntity(
        id = 5,
        name = "Whey",
        servingName = "100g",
        calories = 120,
        proteinG = 24.0,
        carbsG = 3.0,
        fatG = 2.0,
        householdServingName = "scoop",
        householdServingGrams = 30.0,
    )

    @Test
    fun servingsModeComputesGramsAndPreviewFromHouseholdServing() {
        val state = FoodLibraryUiState(
            pendingFood = whey,
            amountMode = AmountMode.SERVINGS,
            servingsValue = "2",
        )
        assertEquals(60.0, state.resolvedGrams!!, 0.001)
        assertEquals(72, state.previewMacros!!.calories)
    }

    @Test
    fun gramsModeComputesPreviewDirectly() {
        val state = FoodLibraryUiState(
            pendingFood = whey,
            amountMode = AmountMode.GRAMS,
            gramsValue = "150",
        )
        assertEquals(150.0, state.resolvedGrams!!, 0.001)
        assertEquals(180, state.previewMacros!!.calories)
    }

    @Test
    fun foodWithoutHouseholdServingIsGramsOnly() {
        val plain = whey.copy(householdServingName = null, householdServingGrams = null)
        val state = FoodLibraryUiState(pendingFood = plain)
        assertEquals(false, state.canUseServings)
    }

    @Test
    fun invalidAmountYieldsNullPreview() {
        val state = FoodLibraryUiState(
            pendingFood = whey,
            amountMode = AmountMode.GRAMS,
            gramsValue = "abc",
        )
        assertEquals(null, state.resolvedGrams)
        assertEquals(null, state.previewMacros)
    }
```

Add the import at the top of the test file:

```kotlin
import com.zack.recomptracker.ui.foodlibrary.AmountMode
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.ui.FoodLibraryUiStateTest"`
Expected: FAIL — `Unresolved reference: AmountMode` / `amountMode` / `resolvedGrams`.

- [ ] **Step 3: Add picker state to `FoodLibraryUiState` and the `AmountMode` enum**

In `FoodLibraryViewModel.kt`, add the enum next to `FoodCategory`:

```kotlin
enum class AmountMode { SERVINGS, GRAMS }
```

Add these imports near the top:

```kotlin
import com.zack.recomptracker.domain.food.FoodMacros
import com.zack.recomptracker.domain.food.FoodScaling
```

In `FoodLibraryUiState`: the original state already has `pendingFood: SavedFoodEntity?` — **keep it**. Delete the old `showQuantityDialog` and `quantityGrams` fields and add the rest:

```kotlin
    val showAmountSheet: Boolean = false,
    // pendingFood already exists — do not redeclare it
    val editingEntryId: Long? = null,
    val amountMode: AmountMode = AmountMode.SERVINGS,
    val servingsValue: String = "1",
    val gramsValue: String = "100",
```

Add these computed properties inside `FoodLibraryUiState` (after `filteredMeals`):

```kotlin
    val canUseServings: Boolean
        get() = (pendingFood?.householdServingGrams ?: 0.0) >= 1.0 &&
            !pendingFood?.householdServingName.isNullOrBlank()

    val resolvedGrams: Double?
        get() {
            val food = pendingFood ?: return null
            return when (amountMode) {
                AmountMode.SERVINGS -> {
                    val servings = servingsValue.toDoubleOrNull() ?: return null
                    val perServing = food.householdServingGrams ?: return null
                    if (servings < 1.0 || perServing < 1.0) null
                    else FoodScaling.gramsForServings(servings, perServing)
                }
                AmountMode.GRAMS -> {
                    val g = gramsValue.toDoubleOrNull() ?: return null
                    if (g < FoodScaling.MIN_GRAMS) null else g
                }
            }
        }

    val previewMacros: FoodMacros?
        get() {
            val food = pendingFood ?: return null
            val grams = resolvedGrams ?: return null
            return FoodScaling.scale(
                FoodMacros(food.calories, food.proteinG, food.carbsG, food.fatG),
                grams,
            )
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.ui.FoodLibraryUiStateTest"`
Expected: PASS.

> If the ViewModel body still references the removed `showQuantityDialog`/`quantityGrams`/`confirmLogFood`/`onQuantityChanged`/`dismissQuantityDialog`, it will not compile. Replace those in Step 5 (next task references them). For now, also update the ViewModel actions in this step so the module compiles — see Step 5 code below; run the test again after.

- [ ] **Step 5: Replace the ViewModel logging actions**

In `FoodLibraryViewModel.kt`, replace `requestLogFood`, `onQuantityChanged`, `confirmLogFood`, and `dismissQuantityDialog` with:

```kotlin
    fun requestLogFood(food: SavedFoodEntity) {
        val canServings = (food.householdServingGrams ?: 0.0) >= 1.0 && !food.householdServingName.isNullOrBlank()
        _uiState.update {
            it.copy(
                showAmountSheet = true,
                pendingFood = food,
                editingEntryId = null,
                amountMode = if (canServings) AmountMode.SERVINGS else AmountMode.GRAMS,
                servingsValue = "1",
                gramsValue = "100",
                message = null,
            )
        }
    }

    fun onAmountModeChanged(mode: AmountMode) = _uiState.update { it.copy(amountMode = mode) }
    fun onServingsChanged(v: String) = _uiState.update { it.copy(servingsValue = v) }
    fun onGramsChanged(v: String) = _uiState.update { it.copy(gramsValue = v) }

    fun stepServings(delta: Int) = _uiState.update {
        val current = it.servingsValue.toIntOrNull() ?: 1
        it.copy(servingsValue = (current + delta).coerceAtLeast(1).toString())
    }

    fun stepGrams(delta: Int) = _uiState.update {
        val current = it.gramsValue.toDoubleOrNull() ?: 100.0
        it.copy(gramsValue = (current + delta).coerceAtLeast(FoodScaling.MIN_GRAMS).toInt().toString())
    }

    fun confirmAmount() {
        val state = _uiState.value
        val food = state.pendingFood ?: return
        val grams = state.resolvedGrams
        val preview = state.previewMacros
        if (grams == null || preview == null) {
            _uiState.update { it.copy(message = "Enter a valid amount (min ${FoodScaling.MIN_GRAMS.toInt()}g).") }
            return
        }
        val servingName = food.householdServingName?.takeIf { state.amountMode == AmountMode.SERVINGS }
        val servingGrams = food.householdServingGrams?.takeIf { state.amountMode == AmountMode.SERVINGS }
        viewModelScope.launch {
            val editingId = state.editingEntryId
            if (editingId == null) {
                logRepository.addMealToSlot(
                    input = MealEntryInput(
                        date = dateProvider.today(),
                        mealType = "FOOD_LIBRARY",
                        name = food.name,
                        calories = preview.calories,
                        proteinG = preview.proteinG,
                        carbsG = preview.carbsG,
                        fatG = preview.fatG,
                        amountGrams = grams,
                        basePer100Calories = food.calories,
                        basePer100ProteinG = food.proteinG,
                        basePer100CarbsG = food.carbsG,
                        basePer100FatG = food.fatG,
                        entryServingName = servingName,
                        entryServingGrams = servingGrams,
                    ),
                    slotId = state.slotId,
                )
                _uiState.update {
                    it.copy(showAmountSheet = false, pendingFood = null, message = "${food.name} logged.")
                }
            } else {
                val existing = logRepository.getMealEntriesForSlot(
                    date = dateProvider.today().toString(),
                    slotId = state.slotId,
                ).firstOrNull { it.id == editingId }
                if (existing != null) {
                    logRepository.updateMealEntry(
                        existing.copy(
                            name = food.name,
                            calories = preview.calories,
                            proteinG = preview.proteinG,
                            carbsG = preview.carbsG,
                            fatG = preview.fatG,
                            amountGrams = grams,
                            basePer100Calories = food.calories,
                            basePer100ProteinG = food.proteinG,
                            basePer100CarbsG = food.carbsG,
                            basePer100FatG = food.fatG,
                            entryServingName = servingName,
                            entryServingGrams = servingGrams,
                        ),
                    )
                }
                _uiState.update {
                    it.copy(showAmountSheet = false, pendingFood = null, editingEntryId = null, message = "Entry updated.")
                }
            }
        }
    }

    fun dismissAmountSheet() = _uiState.update {
        it.copy(showAmountSheet = false, pendingFood = null, editingEntryId = null)
    }
```

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.ui.FoodLibraryUiStateTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt app/src/test/java/com/zack/recomptracker/ui/FoodLibraryUiStateTest.kt
git commit -m "feat: add amount-picker state with servings/grams modes and live preview"
```

---

## Task 7: Amount-picker bottom sheet UI

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 1: Replace the quantity `AlertDialog` with a `ModalBottomSheet`**

In `FoodLibraryScreen.kt`, remove the old `if (state.showQuantityDialog && state.pendingFood != null) { AlertDialog(...) }` block and add this composable call after the `LazyColumn` (and define the sheet composable below). At the top of the file add imports:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextAlign
```

Add the sheet trigger right after the `LazyColumn { ... }` closes:

```kotlin
    if (state.showAmountSheet && state.pendingFood != null) {
        AmountSheet(state = state, viewModel = viewModel)
    }
```

- [ ] **Step 2: Add the `AmountSheet` composable**

Add at the bottom of `FoodLibraryScreen.kt`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSheet(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    val food = state.pendingFood ?: return
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissAmountSheet,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(food.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val reference = if (state.canUseServings) {
                "1 ${food.householdServingName} = ${food.householdServingGrams?.toInt()} g · ${food.calories} kcal / 100 g"
            } else {
                "${food.calories} kcal / 100 g"
            }
            Text(reference, color = Secondary, fontSize = 11.sp)

            if (state.canUseServings) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.amountMode == AmountMode.SERVINGS,
                        onClick = { viewModel.onAmountModeChanged(AmountMode.SERVINGS) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Servings") }
                    SegmentedButton(
                        selected = state.amountMode == AmountMode.GRAMS,
                        onClick = { viewModel.onAmountModeChanged(AmountMode.GRAMS) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Grams") }
                }
            }

            if (state.amountMode == AmountMode.SERVINGS && state.canUseServings) {
                Stepper(
                    value = state.servingsValue,
                    onValueChange = viewModel::onServingsChanged,
                    onMinus = { viewModel.stepServings(-1) },
                    onPlus = { viewModel.stepServings(1) },
                    caption = state.resolvedGrams?.let { "${it.toInt()} g" } ?: "",
                    suffix = "servings",
                )
            } else {
                Stepper(
                    value = state.gramsValue,
                    onValueChange = viewModel::onGramsChanged,
                    onMinus = { viewModel.stepGrams(-10) },
                    onPlus = { viewModel.stepGrams(10) },
                    caption = "",
                    suffix = "g",
                )
            }

            val preview = state.previewMacros
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PreviewStat("kcal", preview?.calories?.toString() ?: "—")
                PreviewStat("P", preview?.proteinG?.toInt()?.toString() ?: "—")
                PreviewStat("C", preview?.carbsG?.toInt()?.toString() ?: "—")
                PreviewStat("F", preview?.fatG?.toInt()?.toString() ?: "—")
            }

            MessageText(state.message)

            Button(
                onClick = viewModel::confirmAmount,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) {
                Text(
                    if (state.editingEntryId == null) {
                        if (state.slotId != null) "Add to ${state.slotName}" else "Add"
                    } else {
                        "Save"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    caption: String,
    suffix: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.size(48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("−", fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                suffix = { Text(suffix) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (caption.isNotBlank()) {
                Text(caption, color = Secondary, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        OutlinedButton(onClick = onPlus, modifier = Modifier.size(48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("+", fontSize = 20.sp)
        }
    }
}

@Composable
private fun PreviewStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = Secondary, fontSize = 10.sp)
    }
}
```

- [ ] **Step 2b: Remove the now-unused `NumberField`/quantity references**

Confirm there are no remaining references to `state.showQuantityDialog`, `state.quantityGrams`, or `viewModel.onQuantityChanged`/`confirmLogFood`/`dismissQuantityDialog` anywhere in `FoodLibraryScreen.kt`. If found, delete them.

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification**

Launch the app, open a meal slot's **+ Add**, tap a personal food that has a household serving: the bottom sheet shows the Servings/Grams toggle, stepper, and a live macro preview; tapping a NEVO food shows grams-only. Confirm "Add to {slot}" logs the scaled macros.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
git commit -m "feat: add Samsung-style amount picker bottom sheet"
```

---

## Task 8: Recents row in the Food Library

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/FoodLibraryUiStateTest.kt`

- [ ] **Step 1: Write the failing recents-mapping test**

Append to `FoodLibraryUiStateTest.kt`:

```kotlin
    @Test
    fun recentFoodsExposeBaseSnapshotAsLoggableFood() {
        val recentEntry = com.zack.recomptracker.data.local.entity.MealEntryEntity(
            id = 9,
            date = "2026-05-30",
            mealType = "FOOD_LIBRARY",
            name = "Whey",
            calories = 72,
            proteinG = 14.0,
            carbsG = 2.0,
            fatG = 1.0,
            amountGrams = 60.0,
            basePer100Calories = 120,
            basePer100ProteinG = 24.0,
            basePer100CarbsG = 3.0,
            basePer100FatG = 2.0,
            entryServingName = "scoop",
            entryServingGrams = 30.0,
        )
        val state = FoodLibraryUiState(recentEntries = listOf(recentEntry))
        val recent = state.recentFoods.single()
        assertEquals("Whey", recent.name)
        assertEquals(120, recent.calories)
        assertEquals("scoop", recent.householdServingName)
        assertEquals(30.0, recent.householdServingGrams!!, 0.001)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.ui.FoodLibraryUiStateTest"`
Expected: FAIL — `Unresolved reference: recentEntries` / `recentFoods`.

- [ ] **Step 3: Add recents to state and wire the flow**

In `FoodLibraryUiState`, add the field:

```kotlin
    val recentEntries: List<com.zack.recomptracker.data.local.entity.MealEntryEntity> = emptyList(),
```

Add the computed property (after `previewMacros`):

```kotlin
    val recentFoods: List<SavedFoodEntity>
        get() = recentEntries.map { e ->
            SavedFoodEntity(
                name = e.name,
                servingName = e.entryServingName?.let { "1 $it" } ?: "100g",
                calories = e.basePer100Calories ?: 0,
                proteinG = e.basePer100ProteinG ?: 0.0,
                carbsG = e.basePer100CarbsG ?: 0.0,
                fatG = e.basePer100FatG ?: 0.0,
                householdServingName = e.entryServingName,
                householdServingGrams = e.entryServingGrams,
            )
        }
```

In the `init` block's `combine(...)`, add the recents flow. Replace the existing `combine(` call with a version that also collects recents. Since `combine` supports up to 5 typed args here already, add recents by nesting: after the existing `combine` collects `data`, also start a second collector. Simplest: add this inside `init`, after the existing `viewModelScope.launch { ... }` block:

```kotlin
        viewModelScope.launch {
            logRepository.observeRecentFoods().collect { recents ->
                _uiState.update { it.copy(recentEntries = recents) }
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.zack.recomptracker.ui.FoodLibraryUiStateTest"`
Expected: PASS.

- [ ] **Step 5: Render the Recents row**

In `FoodLibraryScreen.kt`, add a Recents section to the `LazyColumn`, immediately before the `if (state.category != FoodCategory.MEALS) { ... }` foods block:

```kotlin
        if (state.recentFoods.isNotEmpty() && state.query.isBlank()) {
            item {
                Text(
                    "RECENTS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Secondary,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.recentFoods, key = { "recent_${it.name}" }) { food ->
                        OutlinedButton(onClick = { viewModel.requestLogFood(food) }) {
                            Text(food.name, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
```

- [ ] **Step 6: Build and commit**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt app/src/test/java/com/zack/recomptracker/ui/FoodLibraryUiStateTest.kt
git commit -m "feat: add Recents quick-access row to the food library"
```

---

## Task 9: Quick add calories

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 1: Add quick-add state and action to the ViewModel**

In `FoodLibraryUiState`, add fields:

```kotlin
    val showQuickAddDialog: Boolean = false,
    val quickAddName: String = "",
    val quickAddCalories: String = "",
    val quickAddProtein: String = "",
    val quickAddCarbs: String = "",
    val quickAddFat: String = "",
```

In `FoodLibraryViewModel`, add:

```kotlin
    fun openQuickAdd() = _uiState.update {
        it.copy(showQuickAddDialog = true, quickAddName = "", quickAddCalories = "", quickAddProtein = "", quickAddCarbs = "", quickAddFat = "", message = null)
    }
    fun dismissQuickAdd() = _uiState.update { it.copy(showQuickAddDialog = false) }
    fun onQuickAddNameChanged(v: String) = _uiState.update { it.copy(quickAddName = v) }
    fun onQuickAddCaloriesChanged(v: String) = _uiState.update { it.copy(quickAddCalories = v) }
    fun onQuickAddProteinChanged(v: String) = _uiState.update { it.copy(quickAddProtein = v) }
    fun onQuickAddCarbsChanged(v: String) = _uiState.update { it.copy(quickAddCarbs = v) }
    fun onQuickAddFatChanged(v: String) = _uiState.update { it.copy(quickAddFat = v) }

    fun confirmQuickAdd() {
        val s = _uiState.value
        val cal = s.quickAddCalories.toIntOrNull()
        if (cal == null || cal < 0) {
            _uiState.update { it.copy(message = "Enter a calorie amount.") }
            return
        }
        viewModelScope.launch {
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = dateProvider.today(),
                    mealType = "QUICK_ADD",
                    name = s.quickAddName.ifBlank { "Quick add" },
                    calories = cal,
                    proteinG = s.quickAddProtein.toDoubleOrNull() ?: 0.0,
                    carbsG = s.quickAddCarbs.toDoubleOrNull() ?: 0.0,
                    fatG = s.quickAddFat.toDoubleOrNull() ?: 0.0,
                ),
                slotId = s.slotId,
            )
            _uiState.update { it.copy(showQuickAddDialog = false, message = "Quick add logged.") }
        }
    }
```

- [ ] **Step 2: Add the Quick add button + dialog to the screen**

In `FoodLibraryScreen.kt`, inside the `Column` of action buttons (the block guarded by `if (state.category != FoodCategory.NEVO)`), add after the "+ Create new food" button:

```kotlin
                    OutlinedButton(
                        onClick = viewModel::openQuickAdd,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                    ) {
                        Text("Quick add calories")
                    }
```

Add this dialog after the existing `if (state.showSaveMealDialog) { ... }` block:

```kotlin
    if (state.showQuickAddDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissQuickAdd,
            title = { Text("Quick add") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.quickAddName,
                        onValueChange = viewModel::onQuickAddNameChanged,
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NumberField("Calories", state.quickAddCalories, viewModel::onQuickAddCaloriesChanged, suffix = "kcal")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField("Protein", state.quickAddProtein, viewModel::onQuickAddProteinChanged, Modifier.weight(1f), "g")
                        NumberField("Carbs", state.quickAddCarbs, viewModel::onQuickAddCarbsChanged, Modifier.weight(1f), "g")
                        NumberField("Fat", state.quickAddFat, viewModel::onQuickAddFatChanged, Modifier.weight(1f), "g")
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmQuickAdd) { Text("Add") } },
            dismissButton = { TextButton(onClick = viewModel::dismissQuickAdd) { Text("Cancel") } },
        )
    }
```

Add the import if missing: `import com.zack.recomptracker.ui.component.NumberField` (already present in this file).

- [ ] **Step 3: Build and commit**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
git commit -m "feat: add quick-add-calories one-off entry"
```

---

## Task 10: Edit a personal food

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 1: Add edit state and household-serving fields to the create form**

In `FoodLibraryUiState`, add:

```kotlin
    val editingFoodId: Long? = null,
    val newFoodServingName: String = "",
    val newFoodServingGrams: String = "",
```

In `FoodLibraryViewModel`, add field setters and edit-open/save:

```kotlin
    fun onNewFoodServingNameChanged(v: String) = _uiState.update { it.copy(newFoodServingName = v) }
    fun onNewFoodServingGramsChanged(v: String) = _uiState.update { it.copy(newFoodServingGrams = v) }

    fun openEditFood(food: SavedFoodEntity) = _uiState.update {
        it.copy(
            showCreateFoodForm = true,
            editingFoodId = food.id,
            newFoodName = food.name,
            newFoodServing = food.servingName,
            newFoodCalories = food.calories.toString(),
            newFoodProtein = food.proteinG.toString(),
            newFoodCarbs = food.carbsG.toString(),
            newFoodFat = food.fatG.toString(),
            newFoodServingName = food.householdServingName.orEmpty(),
            newFoodServingGrams = food.householdServingGrams?.toInt()?.toString().orEmpty(),
            message = null,
        )
    }
```

Replace `saveNewFood()` with a version that handles edit + household serving:

```kotlin
    fun saveNewFood() {
        val s = _uiState.value
        val cal = s.newFoodCalories.toIntOrNull()
        val p = s.newFoodProtein.toDoubleOrNull()
        val c = s.newFoodCarbs.toDoubleOrNull()
        val f = s.newFoodFat.toDoubleOrNull()
        if (s.newFoodName.isBlank() || cal == null || p == null || c == null || f == null) {
            _uiState.update { it.copy(message = "Fill in all fields with valid numbers.") }
            return
        }
        val servingGrams = s.newFoodServingGrams.toDoubleOrNull()
        val servingName = s.newFoodServingName.trim().ifBlank { null }
        viewModelScope.launch {
            logRepository.saveFood(
                SavedFoodEntity(
                    id = s.editingFoodId ?: 0,
                    name = s.newFoodName.trim(),
                    servingName = s.newFoodServing.trim(),
                    calories = cal,
                    proteinG = p,
                    carbsG = c,
                    fatG = f,
                    householdServingName = servingName?.takeIf { servingGrams != null && servingGrams >= 1.0 },
                    householdServingGrams = servingGrams?.takeIf { it >= 1.0 && servingName != null },
                ),
            )
            _uiState.update {
                it.copy(
                    showCreateFoodForm = false,
                    editingFoodId = null,
                    newFoodName = "", newFoodServing = "100g",
                    newFoodCalories = "", newFoodProtein = "",
                    newFoodCarbs = "", newFoodFat = "",
                    newFoodServingName = "", newFoodServingGrams = "",
                    message = if (s.editingFoodId != null) "Food updated." else "Food saved to library.",
                )
            }
        }
    }
```

- [ ] **Step 2: Add household-serving inputs to `CreateFoodForm` and an edit affordance to `FoodRow`**

In `FoodLibraryScreen.kt`, in `CreateFoodForm`, add after the macro rows (before the Save button):

```kotlin
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.newFoodServingName,
                    onValueChange = viewModel::onNewFoodServingNameChanged,
                    label = { Text("Serving name (e.g. scoop)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                NumberField("Serving grams", state.newFoodServingGrams, viewModel::onNewFoodServingGramsChanged, Modifier.weight(1f), "g")
            }
```

Change the `FoodRow` signature and call site to allow editing personal rows. Replace the `FoodRow` composable header and add an edit action. Update the `items(...)` call in the foods list to pass `onEdit`:

```kotlin
                items(state.filteredFoods, key = { it.key }) { item ->
                    FoodRow(
                        item = item,
                        onLog = { viewModel.requestLogFood(item.food) },
                        onEdit = if (item.sourceLabel == null) { { viewModel.openEditFood(item.food) } } else null,
                    )
                }
```

Replace the `FoodRow` composable with:

```kotlin
@Composable
private fun FoodRow(item: FoodLibraryItem, onLog: () -> Unit, onEdit: (() -> Unit)? = null) {
    val food = item.food
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (item.sourceLabel != null) {
                    Text(item.sourceLabel, fontSize = 10.sp, color = Blue, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${food.servingName} · ${food.calories} kcal · ${food.proteinG.toInt()}P ${food.carbsG.toInt()}C ${food.fatG.toInt()}F",
                    fontSize = 11.sp,
                    color = Secondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onEdit != null) {
                    TextButton(onClick = onEdit) { Text("Edit", fontSize = 11.sp, color = Secondary) }
                }
                Button(
                    onClick = onLog,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                ) { Text("Log", fontSize = 11.sp) }
            }
        }
    }
}
```

- [ ] **Step 3: Build, manually verify, commit**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Manually: edit a personal food, add a household serving ("scoop", 30), save, then log it — the picker now defaults to Servings mode.

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
git commit -m "feat: edit personal foods incl. optional household serving"
```

---

## Task 11: Edit a logged entry

The amount sheet already supports editing (Task 6 `confirmAmount` honors `editingEntryId`). This task adds the entry point from the day's log: tapping an entry opens the sheet (amount-editable) or a macro dialog (quick-add/meal/legacy).

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/dao/MealEntryDao.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

> Editing an amount-editable entry navigates into the Food Library amount sheet (reusing `confirmAmount`, so the scaling path lives in one place); editing a macro-only entry uses an inline dialog on the Food screen (no navigation).

- [ ] **Step 0: Add `getById` to the DAO and repository**

In `MealEntryDao.kt`, add inside the interface:

```kotlin
    @Query("SELECT * FROM meal_entries WHERE id = :id")
    suspend fun getById(id: Long): MealEntryEntity?
```

In `LogRepository.kt`, add:

```kotlin
    suspend fun getMealEntry(id: Long): MealEntryEntity? = mealEntryDao.getById(id)
```

- [ ] **Step 1: Add an amount-edit entry point in the ViewModel**

In `FoodLibraryViewModel`, change the `init` signature and add the lookup + open methods. Replace the `fun init(slotId: Long?, slotName: String)` signature line with:

```kotlin
    fun init(slotId: Long?, slotName: String, editEntryId: Long? = null) {
```

Inside `init`, immediately after the existing `_uiState.update { it.copy(slotId = ..., slotName = ...) }` line, add:

```kotlin
        if (editEntryId != null && _uiState.value.editingEntryId != editEntryId) {
            viewModelScope.launch {
                logRepository.getMealEntry(editEntryId)?.let { requestEditEntry(it) }
            }
        }
```

Then add the `requestEditEntry` method:

```kotlin
    fun requestEditEntry(entry: com.zack.recomptracker.data.local.entity.MealEntryEntity) {
        val base = SavedFoodEntity(
            name = entry.name,
            servingName = entry.entryServingName?.let { "1 $it" } ?: "100g",
            calories = entry.basePer100Calories ?: return,
            proteinG = entry.basePer100ProteinG ?: 0.0,
            carbsG = entry.basePer100CarbsG ?: 0.0,
            fatG = entry.basePer100FatG ?: 0.0,
            householdServingName = entry.entryServingName,
            householdServingGrams = entry.entryServingGrams,
        )
        val useServings = entry.entryServingGrams != null && entry.entryServingName != null
        _uiState.update {
            it.copy(
                showAmountSheet = true,
                pendingFood = base,
                editingEntryId = entry.id,
                amountMode = if (useServings) AmountMode.SERVINGS else AmountMode.GRAMS,
                servingsValue = if (useServings && entry.entryServingGrams!! >= 1.0) {
                    ((entry.amountGrams ?: 0.0) / entry.entryServingGrams!!).toInt().coerceAtLeast(1).toString()
                } else "1",
                gramsValue = (entry.amountGrams ?: 100.0).toInt().toString(),
                message = null,
            )
        }
    }
```

- [ ] **Step 2: Add a macro-edit dialog to the Food screen for non-scalable entries**

In `TodayViewModel.kt`, add an update method that recomputes day totals. Find the existing `deleteMeal` method and add next to it:

```kotlin
    fun updateMealMacros(entry: MealEntryEntity, calories: Int, proteinG: Double, carbsG: Double, fatG: Double) {
        viewModelScope.launch {
            logRepository.updateMealEntry(
                entry.copy(
                    calories = calories,
                    proteinG = proteinG,
                    carbsG = carbsG,
                    fatG = fatG,
                ),
            )
        }
    }
```

Add the import if missing: `import com.zack.recomptracker.data.local.entity.MealEntryEntity` (likely already present).

- [ ] **Step 3: Make `SlotEntryRow` tappable and route amount-editable vs macro-editable**

In `FoodScreen.kt`, extend `FoodActions` with an edit-macros callback and a navigation callback. Replace the `FoodActions` data class with:

```kotlin
data class FoodActions(
    val onToggleEditMode: () -> Unit,
    val onAddSlot: (String) -> Unit,
    val onRenameSlot: (Long, String) -> Unit,
    val onDeleteSlot: (Long) -> Unit,
    val onReorderSlots: (List<Long>) -> Unit,
    val onDeleteMeal: (Long) -> Unit,
    val onEditMacros: (MealEntryEntity, Int, Double, Double, Double) -> Unit,
)
```

In `FoodScreen(...)`, add `onEditEntryAmount: (MealEntryEntity) -> Unit` to the parameters and pass `onEditMacros = viewModel::updateMealMacros` into `FoodActions`. Thread `onEditEntryAmount` down to `LockedSlotCard` → `SlotEntryRow`. In `SlotEntryRow`, wrap the row in a clickable that decides:

```kotlin
@Composable
private fun SlotEntryRow(
    entry: MealEntryEntity,
    onDelete: (Long) -> Unit,
    onEditAmount: () -> Unit,
    onEditMacros: (MealEntryEntity, Int, Double, Double, Double) -> Unit,
) {
    var showMacroEdit by remember { mutableStateOf(false) }
    val amountEditable = entry.amountGrams != null && entry.basePer100Calories != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (amountEditable) onEditAmount() else showMacroEdit = true },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "${entry.calories} kcal · ${entry.proteinG.toInt()}P ${entry.carbsG.toInt()}C ${entry.fatG.toInt()}F",
                fontSize = 11.sp,
                color = Secondary,
            )
        }
        TextButton(onClick = { onDelete(entry.id) }) {
            Text("Delete", color = DangerText, fontSize = 11.sp)
        }
    }

    if (showMacroEdit) {
        MacroEditDialog(
            entry = entry,
            onDismiss = { showMacroEdit = false },
            onSave = { cal, p, c, f -> onEditMacros(entry, cal, p, c, f); showMacroEdit = false },
        )
    }
}

@Composable
private fun MacroEditDialog(
    entry: MealEntryEntity,
    onDismiss: () -> Unit,
    onSave: (Int, Double, Double, Double) -> Unit,
) {
    var cal by remember { mutableStateOf(entry.calories.toString()) }
    var p by remember { mutableStateOf(entry.proteinG.toInt().toString()) }
    var c by remember { mutableStateOf(entry.carbsG.toInt().toString()) }
    var f by remember { mutableStateOf(entry.fatG.toInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${entry.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Calories", cal, { cal = it }, suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Protein", p, { p = it }, Modifier.weight(1f), "g")
                    NumberField("Carbs", c, { c = it }, Modifier.weight(1f), "g")
                    NumberField("Fat", f, { f = it }, Modifier.weight(1f), "g")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    cal.toIntOrNull() ?: entry.calories,
                    p.toDoubleOrNull() ?: entry.proteinG,
                    c.toDoubleOrNull() ?: entry.carbsG,
                    f.toDoubleOrNull() ?: entry.fatG,
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

Add imports to `FoodScreen.kt`:

```kotlin
import androidx.compose.foundation.clickable
import com.zack.recomptracker.ui.component.NumberField
```

Update `LockedSlotCard` to accept and forward `onEditAmount` and `onEditMacros` to `SlotEntryRow`, and update its call site in `FoodContent` to pass them through (route `onEditAmount` to the new `onEditEntryAmount` param).

- [ ] **Step 4: Wire navigation from the Food screen to the Food Library amount sheet**

The Food screen passes `(slotId, slotName, entryId)` up; the nav graph opens the Food Library with an `editEntryId` query arg; `FoodLibraryScreen` forwards it to `init`, which calls `requestEditEntry`.

**4a.** In `FoodScreen.kt`, add the parameter to `FoodScreen(...)` (alongside `onAddToSlot`):

```kotlin
    onEditEntryAmount: (slotId: Long?, slotName: String, entryId: Long) -> Unit,
```

Thread it: `FoodContent(... onEditEntryAmount = onEditEntryAmount ...)` → `LockedSlotCard(... onEditEntryAmount = { entryId -> onEditEntryAmount(slotWithEntries.slot.id, slotWithEntries.slot.name, entryId) } ...)` → `SlotEntryRow(... onEditAmount = { onEditEntryAmount(entry.id) } ...)`. In `SlotEntryRow`, replace the earlier `onEditAmount: (MealEntryEntity) -> Unit` with `onEditAmount: () -> Unit` and call it directly when `amountEditable`.

**4b.** In `FoodLibraryScreen.kt`, add an `editEntryId: Long? = null` parameter to `FoodLibraryScreen(...)` and change the `LaunchedEffect`:

```kotlin
    LaunchedEffect(Unit) { viewModel.init(slotId, slotName, editEntryId) }
```

**4c.** In `AppNavGraph.kt`, add the `editEntryId` arg to the Food Library route. Change the route string to:

```kotlin
            route = "${Routes.FoodLibrary}?slotId={slotId}&slotName={slotName}&editEntryId={editEntryId}",
```

Add to the `arguments` list:

```kotlin
                androidx.navigation.navArgument("editEntryId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
```

In the route body, read it and pass it down:

```kotlin
            val editEntryId = backStackEntry.arguments?.getLong("editEntryId")?.takeIf { it != -1L }
            FoodLibraryScreen(
                viewModel = viewModel<FoodLibraryViewModel>(factory = factory),
                slotId = slotId,
                slotName = slotName,
                editEntryId = editEntryId,
                onBack = { navController.popBackStack() },
            )
```

In the `composable(TopLevelDestination.Food.route)` block, add the new callback to the `FoodScreen(...)` call:

```kotlin
                onEditEntryAmount = { slotId, slotName, entryId ->
                    navController.navigate(
                        "${Routes.FoodLibrary}?slotId=${slotId ?: -1L}&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}&editEntryId=$entryId"
                    )
                },
```

This reuses the existing `FoodLibraryViewModel.confirmAmount` scaling path; no scaling math is duplicated.

- [ ] **Step 5: Build, manually verify, commit**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Manually: tap a logged library food → amount sheet opens prefilled → change servings → Save updates the entry and day totals. Tap a quick-add entry → macro dialog opens → Save updates it.

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat: edit a logged entry (amount sheet or macro dialog)"
```

---

## Task 12: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full JVM unit suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (all domain + UI-state tests).

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run instrumented tests (emulator/device)**

Run: `./gradlew connectedAndroidTest`
Expected: PASS, including `migratesVersion3To4PreservingDataAndAddingColumns`. If no device is available, record that this step is pending and must run before merge.

- [ ] **Step 4: Manual smoke test**

- Log a personal food by servings and by grams; confirm the day total updates.
- Log a NEVO food (grams-only) and confirm it is not saved as a personal food.
- Use Recents to re-log a food in one tap.
- Quick-add calories.
- Edit a personal food's macros and add a household serving.
- Edit a logged entry's amount and a quick-add entry's macros.

- [ ] **Step 5: Final commit (if any verification fixups were needed)**

```bash
git add -A
git commit -m "test: verify food logging improvements end to end"
```

---

## Notes for the implementer

- Macros are **always stored per 100 g** on `SavedFoodEntity`; never reintroduce scaling off the free-text `servingName`.
- All scaling goes through `FoodScaling.scale`; do not inline `grams / 100.0` math in the UI.
- A household serving is valid only when **both** name (non-blank) and grams (`>= 1`) are present; otherwise the food is grams-only and the Servings toggle is hidden.
- `QUICK_ADD` and saved-meal entries have no base snapshot and are macro-editable only.
- Keep DI manual in `AppContainer`. Do not add Hilt, Retrofit, WorkManager, or networking.
