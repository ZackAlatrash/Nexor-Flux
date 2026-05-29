# UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the app with a forced dark theme, a blue progress bar with diagonal-striped calorie target zone, custom meal slots on the Today screen, and a dedicated food library screen.

**Architecture:** Forced dark Material 3 theme throughout. New `MealSlotEntity` Room table tracks user-created meal slots with drag-to-reorder support. New `FoodLibraryScreen` replaces the existing FoodsScreen and is navigated to from the Today screen slot buttons. A shared `CalorieZoneBar` composable renders the progress bar with zone marker for both Today and Dashboard.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room (migration 1→2), DataStore, Compose Navigation, Vico (unchanged).

---

## File Map

**Create:**
- `app/src/main/java/com/zack/recomptracker/data/local/entity/MealSlotEntity.kt`
- `app/src/main/java/com/zack/recomptracker/data/local/dao/MealSlotDao.kt`
- `app/src/main/java/com/zack/recomptracker/ui/component/CalorieZoneBar.kt`
- `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`
- `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- `app/src/test/java/com/zack/recomptracker/ui/CalorieZoneBarKtTest.kt`

**Modify:**
- `app/src/main/java/com/zack/recomptracker/ui/theme/Theme.kt`
- `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`
- `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`
- `app/src/main/java/com/zack/recomptracker/data/local/entity/MealEntryEntity.kt`
- `app/src/main/java/com/zack/recomptracker/data/local/dao/MealEntryDao.kt`
- `app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt`
- `app/src/main/java/com/zack/recomptracker/data/preferences/PlanPreferences.kt`
- `app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt`
- `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`
- `app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt`
- `app/src/main/java/com/zack/recomptracker/ui/today/TodayScreen.kt`
- `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt`
- `app/src/main/java/com/zack/recomptracker/domain/export/BackupModels.kt`
- `app/src/main/java/com/zack/recomptracker/data/repository/BackupRepository.kt`
- `gradle/libs.versions.toml`

---

## Task 1: Dark Theme

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/theme/Theme.kt`

- [ ] **Step 1: Replace Theme.kt**

```kotlin
package com.zack.recomptracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppDarkColors = darkColorScheme(
    primary = Color(0xFF3b82f6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1e3a5f),
    onPrimaryContainer = Color(0xFF3b82f6),
    secondary = Color(0xFF6b7280),
    onSecondary = Color.White,
    background = Color(0xFF0f0f0f),
    onBackground = Color.White,
    surface = Color(0xFF1a1a1a),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF9ca3af),
    outline = Color(0xFF374151),
    error = Color(0xFFf87171),
    onError = Color(0xFF3d1515),
)

@Composable
fun RecompTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppDarkColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
```

- [ ] **Step 2: Update RecompApp.kt to remove the darkTheme parameter**

Open `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`. The call to `RecompTrackerTheme` currently passes no parameters — it will now compile without the `darkTheme` parameter since `Theme.kt` no longer accepts one. No code change needed in `RecompApp.kt` for this step.

- [ ] **Step 3: Build to confirm theme compiles**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/theme/Theme.kt
git commit -m "feat: force dark theme with custom color palette"
```

---

## Task 2: Navigation Bar — Pill Indicator + Real Icons

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`

- [ ] **Step 1: Add Material Icons Extended to version catalog**

In `gradle/libs.versions.toml`, under `[libraries]` add:
```toml
compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
```

- [ ] **Step 2: Add dependency to app/build.gradle.kts**

In the `dependencies` block, add:
```kotlin
implementation(libs.compose.material.icons.extended)
```

- [ ] **Step 3: Update TopLevelDestination in AppNavGraph.kt**

Replace the existing `TopLevelDestination` enum:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Today("today", "Today", Icons.Default.Home),
    Dashboard("dashboard", "Stats", Icons.Default.GridView),
    Progress("progress", "Charts", Icons.AutoMirrored.Filled.TrendingUp),
    Plan("plan", "Plan", Icons.Default.Person),
    More("more", "More", Icons.Default.MoreHoriz),
}
```

Remove the now-unused `shortLabel` property if it still exists in the file.

- [ ] **Step 4: Rewrite the NavigationBar in RecompApp.kt**

Replace the entire `bottomBar` lambda in `RecompApp.kt`:

```kotlin
package com.zack.recomptracker.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zack.recomptracker.core.AppContainer
import com.zack.recomptracker.ui.navigation.AppNavGraph
import com.zack.recomptracker.ui.navigation.TopLevelDestination
import com.zack.recomptracker.ui.theme.RecompTrackerTheme

val LocalAppContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }

private val NavBlue = Color(0xFF3b82f6)
private val NavPillBg = Color(0xFF1e3a5f)
private val NavInactive = Color(0xFF444444)
private val NavBarBg = Color(0xFF111111)

@Composable
fun RecompApp(container: AppContainer) {
    RecompTrackerTheme {
        CompositionLocalProvider(LocalAppContainer provides container) {
            val navController = rememberNavController()
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar(
                        containerColor = NavBarBg,
                        tonalElevation = 0.dp,
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(TopLevelDestination.Today.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = NavPillBg,
                                    selectedIconColor = NavBlue,
                                    selectedTextColor = NavBlue,
                                    unselectedIconColor = NavInactive,
                                    unselectedTextColor = NavInactive,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                AppNavGraph(
                    navController = navController,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
```

- [ ] **Step 5: Build to confirm**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt \
  app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt
git commit -m "feat: pill indicator nav bar with Material icons"
```

---

## Task 3: Data Model — MealSlotEntity, slotId, Zone Bounds

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/local/entity/MealSlotEntity.kt`
- Create: `app/src/main/java/com/zack/recomptracker/data/local/dao/MealSlotDao.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/entity/MealEntryEntity.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/dao/MealEntryDao.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/PlanPreferences.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/domain/export/BackupModels.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/BackupRepository.kt`

- [ ] **Step 1: Create MealSlotEntity.kt**

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "meal_slots")
data class MealSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
```

- [ ] **Step 2: Create MealSlotDao.kt**

```kotlin
package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealSlotDao {
    @Query("SELECT * FROM meal_slots ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<MealSlotEntity>>

    @Query("SELECT * FROM meal_slots ORDER BY sort_order ASC")
    suspend fun getAll(): List<MealSlotEntity>

    @Insert
    suspend fun insert(slot: MealSlotEntity): Long

    @Update
    suspend fun update(slot: MealSlotEntity)

    @Query("DELETE FROM meal_slots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE meal_slots SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
```

- [ ] **Step 3: Add slotId to MealEntryEntity.kt**

Replace the entire file:
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
)
```

- [ ] **Step 4: Add clearSlotId to MealEntryDao.kt**

Add this query to `MealEntryDao`:
```kotlin
@Query("UPDATE meal_entries SET slotId = NULL WHERE slotId = :slotId")
suspend fun clearSlotId(slotId: Long)
```

- [ ] **Step 5: Update RecompDatabase.kt to version 2 with migration**

Replace the entire file:
```kotlin
package com.zack.recomptracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.dao.MealEntryDao
import com.zack.recomptracker.data.local.dao.MealSlotDao
import com.zack.recomptracker.data.local.dao.PerformanceDao
import com.zack.recomptracker.data.local.dao.SavedFoodDao
import com.zack.recomptracker.data.local.dao.SavedMealDao
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity

@Database(
    entities = [
        DailyLogEntity::class,
        MealEntryEntity::class,
        SavedFoodEntity::class,
        SavedMealEntity::class,
        LiftPerformanceEntity::class,
        WeeklyReviewEntity::class,
        MealSlotEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class RecompDatabase : RoomDatabase() {
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun savedFoodDao(): SavedFoodDao
    abstract fun savedMealDao(): SavedMealDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun weeklyReviewDao(): WeeklyReviewDao
    abstract fun mealSlotDao(): MealSlotDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS meal_slots (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "sort_order INTEGER NOT NULL)"
                )
                database.execSQL("ALTER TABLE meal_entries ADD COLUMN slotId INTEGER")
                // Seed three default slots
                database.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Meal 1', 0)")
                database.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Lunch', 1)")
                database.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Dinner', 2)")
            }
        }

        fun create(context: Context): RecompDatabase = Room.databaseBuilder(
            context.applicationContext,
            RecompDatabase::class.java,
            "recomp_tracker.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
```

- [ ] **Step 6: Add zone bounds to PlanPreferences.kt**

Replace the entire file:
```kotlin
package com.zack.recomptracker.data.preferences

import kotlinx.serialization.Serializable

@Serializable
data class PlanPreferences(
    val targetCalories: Int = 2550,
    val targetProteinG: Int = 165,
    val targetCarbsG: Int = 320,
    val targetFatG: Int = 68,
    val maintenancePhaseStartDate: String? = null,
    val weightTrendThresholdKgPerWeek: Double = 0.20,
    val waistIncreaseThresholdCm: Double = 0.5,
    val adherenceMinimumPercent: Double = 85.0,
    val reviewCadenceDays: Int = 7,
    val useMetricUnits: Boolean = true,
    val calorieZoneLowerBound: Int = 2400,
    val calorieZoneUpperBound: Int = 2600,
)
```

- [ ] **Step 7: Add MealSlotEntity to BackupModels.kt**

Open `app/src/main/java/com/zack/recomptracker/domain/export/BackupModels.kt`. Add `mealSlots` to the `BackupPayload`:
```kotlin
// Add this import at the top
import com.zack.recomptracker.data.local.entity.MealSlotEntity

// Add this field to BackupPayload (alongside the existing fields)
val mealSlots: List<MealSlotEntity> = emptyList(),
```

- [ ] **Step 8: Update BackupRepository.kt to include meal slots**

In `createBackupJson()`, add `mealSlots = database.mealSlotDao().getAll()` to the `BackupPayload(...)` constructor call.

In `restoreFromJson()`, after `database.clearAllTables()`, add:
```kotlin
database.mealSlotDao().let { dao ->
    payload.mealSlots.forEach { dao.insert(it.copy(id = 0)) }
}
```

If `mealSlots` is empty (restoring from an older backup), seed the defaults:
```kotlin
if (payload.mealSlots.isEmpty()) {
    listOf("Meal 1", "Lunch", "Dinner").forEachIndexed { i, name ->
        database.mealSlotDao().insert(MealSlotEntity(name = name, sortOrder = i))
    }
}
```

- [ ] **Step 9: Build to confirm**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/local/entity/MealSlotEntity.kt \
  app/src/main/java/com/zack/recomptracker/data/local/dao/MealSlotDao.kt \
  app/src/main/java/com/zack/recomptracker/data/local/entity/MealEntryEntity.kt \
  app/src/main/java/com/zack/recomptracker/data/local/dao/MealEntryDao.kt \
  app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt \
  app/src/main/java/com/zack/recomptracker/data/preferences/PlanPreferences.kt \
  app/src/main/java/com/zack/recomptracker/domain/export/BackupModels.kt \
  app/src/main/java/com/zack/recomptracker/data/repository/BackupRepository.kt
git commit -m "feat: add MealSlotEntity, slotId on MealEntryEntity, calorie zone bounds"
```

---

## Task 4: CalorieZoneBar Composable

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/CalorieZoneBar.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ui/CalorieZoneBarKtTest.kt`

- [ ] **Step 1: Write failing unit tests**

Create `app/src/test/java/com/zack/recomptracker/ui/CalorieZoneBarKtTest.kt`:
```kotlin
package com.zack.recomptracker.ui

import com.zack.recomptracker.ui.component.calorieFraction
import com.zack.recomptracker.ui.component.calorieScaleMax
import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieZoneBarKtTest {

    @Test
    fun scaleMaxIsUpperBoundTimes1Point2() {
        assertEquals(3120, calorieScaleMax(2600))
    }

    @Test
    fun fractionIsZeroWhenEatenIsZero() {
        assertEquals(0f, calorieFraction(0, 3120))
    }

    @Test
    fun fractionIsClampedToOneWhenEatenExceedsScaleMax() {
        assertEquals(1f, calorieFraction(5000, 3120))
    }

    @Test
    fun fractionIsCorrectMidRange() {
        assertEquals(0.5f, calorieFraction(1560, 3120))
    }

    @Test
    fun zoneFractionsAreWithinTrack() {
        val scaleMax = calorieScaleMax(2600)
        val lower = calorieFraction(2400, scaleMax)
        val upper = calorieFraction(2600, scaleMax)
        assert(lower in 0f..1f) { "lower=$lower" }
        assert(upper in 0f..1f) { "upper=$upper" }
        assert(lower < upper) { "lower must be < upper" }
    }
}
```

- [ ] **Step 2: Run to confirm tests fail**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:test --tests "com.zack.recomptracker.ui.CalorieZoneBarKtTest" --no-daemon 2>&1 | tail -20
```
Expected: FAIL — `calorieFraction` and `calorieScaleMax` not found.

- [ ] **Step 3: Create CalorieZoneBar.kt**

```kotlin
package com.zack.recomptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Internal helpers exposed for unit tests
internal fun calorieScaleMax(zoneUpper: Int): Int = (zoneUpper * 1.2).toInt()
internal fun calorieFraction(value: Int, scaleMax: Int): Float =
    (value.toFloat() / scaleMax).coerceIn(0f, 1f)

private val BarFillStart = Color(0xFF3b82f6)
private val BarFillEnd = Color(0xFF2563eb)
private val ZoneDark = Color(0xFF0f4a26)
private val ZoneLight = Color(0xFF15803d)
private val ZoneLabel = Color(0xFF22c55e)
private val TrackColor = Color(0xFF222222)
private val SecondaryText = Color(0xFF6b7280)

/**
 * Progress bar with a diagonal-striped green target zone.
 *
 * @param eaten      calories consumed today
 * @param zoneLower  lower bound of target zone (e.g. 2400)
 * @param zoneUpper  upper bound of target zone (e.g. 2600)
 */
@Composable
fun CalorieZoneBar(
    eaten: Int,
    zoneLower: Int,
    zoneUpper: Int,
    modifier: Modifier = Modifier,
) {
    val scaleMax = calorieScaleMax(zoneUpper)
    val fillFrac = calorieFraction(eaten, scaleMax)
    val zoneLowerFrac = calorieFraction(zoneLower, scaleMax)
    val zoneUpperFrac = calorieFraction(zoneUpper, scaleMax)

    val remaining = (zoneLower - eaten).coerceAtLeast(0)
    val over = (eaten - zoneUpper).coerceAtLeast(0)
    val inZone = eaten in zoneLower..zoneUpper

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
        ) {
            val w = size.width
            val h = size.height

            // Track
            drawRect(color = TrackColor)

            // Diagonal-striped zone
            val zoneLeft = w * zoneLowerFrac
            val zoneRight = w * zoneUpperFrac
            withTransform({
                clipRect(left = zoneLeft, top = 0f, right = zoneRight, bottom = h)
            }) {
                drawRect(
                    color = ZoneLight,
                    topLeft = Offset(zoneLeft, 0f),
                    size = Size(zoneRight - zoneLeft, h),
                )
                val stripeStrokeWidth = 3.dp.toPx()
                val stripeSpacing = 7.dp.toPx()
                var x = zoneLeft - h
                while (x < zoneRight + h) {
                    drawLine(
                        color = ZoneDark,
                        start = Offset(x, h),
                        end = Offset(x + h, 0f),
                        strokeWidth = stripeStrokeWidth,
                    )
                    x += stripeSpacing
                }
            }

            // Blue fill (drawn over track and zone)
            if (fillFrac > 0f) {
                clipRect(left = 0f, top = 0f, right = w * fillFrac, bottom = h) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(BarFillStart, BarFillEnd),
                            startX = 0f,
                            endX = w * fillFrac,
                        ),
                    )
                }
            }
        }

        // Labels row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$eaten",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        inZone -> "eaten · in zone"
                        over > 0 -> "eaten · +$over over target"
                        else -> "eaten · $remaining to zone"
                    },
                    fontSize = 12.sp,
                    color = if (over > 0) Color(0xFFf87171) else SecondaryText,
                )
            }
            Text(
                text = "▌ $zoneLower–$zoneUpper",
                fontSize = 9.sp,
                color = ZoneLabel,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Single macro mini-bar with zone marker, used for P/C/F columns.
 *
 * @param label   "Protein", "Carbs", or "Fat"
 * @param eaten   grams consumed
 * @param target  grams target
 */
@Composable
fun MacroMiniBar(
    label: String,
    eaten: Double,
    target: Int,
    modifier: Modifier = Modifier,
) {
    val scaleMax = (target * 1.2).toInt().coerceAtLeast(1)
    val fillFrac = (eaten.toFloat() / scaleMax).coerceIn(0f, 1f)
    val zoneFrac = (target.toFloat() / scaleMax).coerceIn(0f, 1f)
    val remaining = (target - eaten).coerceAtLeast(0.0)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "${eaten.toInt()}g",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        ) {
            val w = size.width
            val h = size.height
            drawRect(color = TrackColor)
            // Small zone marker
            val zoneX = w * zoneFrac
            drawRect(
                color = ZoneLight,
                topLeft = Offset((zoneX - 3.dp.toPx()).coerceAtLeast(0f), 0f),
                size = Size(3.dp.toPx(), h),
            )
            if (fillFrac > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(BarFillStart, BarFillEnd),
                        startX = 0f,
                        endX = w * fillFrac,
                    ),
                    size = Size(w * fillFrac, h),
                )
            }
        }
        Text(
            text = "${remaining.toInt()}g to go",
            fontSize = 10.sp,
            color = SecondaryText,
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = SecondaryText,
        )
    }
}
```

- [ ] **Step 4: Run tests to confirm pass**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:test --tests "com.zack.recomptracker.ui.CalorieZoneBarKtTest" --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/CalorieZoneBar.kt \
  app/src/test/java/com/zack/recomptracker/ui/CalorieZoneBarKtTest.kt
git commit -m "feat: CalorieZoneBar composable with diagonal-striped target zone"
```

---

## Task 5: LogRepository Slot Operations + AppContainer

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add MealSlotDao to LogRepository**

At the top of `LogRepository`, add `mealSlotDao: MealSlotDao` as a constructor parameter after `weeklyReviewDao`:
```kotlin
import com.zack.recomptracker.data.local.dao.MealSlotDao
import com.zack.recomptracker.data.local.entity.MealSlotEntity

class LogRepository(
    private val dailyLogDao: DailyLogDao,
    private val mealEntryDao: MealEntryDao,
    private val savedFoodDao: SavedFoodDao,
    private val savedMealDao: SavedMealDao,
    private val performanceDao: PerformanceDao,
    private val weeklyReviewDao: WeeklyReviewDao,
    private val mealSlotDao: MealSlotDao,
) {
```

- [ ] **Step 2: Add slot observation and CRUD methods to LogRepository**

Add these methods to `LogRepository` (after `observeWeeklyReviews()`):
```kotlin
fun observeSlots(): Flow<List<MealSlotEntity>> = mealSlotDao.observeAll()

suspend fun addSlot(name: String) {
    val maxOrder = mealSlotDao.getAll().maxOfOrNull { it.sortOrder } ?: -1
    mealSlotDao.insert(MealSlotEntity(name = name.trim(), sortOrder = maxOrder + 1))
}

suspend fun renameSlot(id: Long, name: String) {
    mealSlotDao.getAll()
        .firstOrNull { it.id == id }
        ?.let { mealSlotDao.update(it.copy(name = name.trim())) }
}

suspend fun deleteSlot(id: Long) {
    mealSlotDao.deleteById(id)
    mealEntryDao.clearSlotId(id)
}

suspend fun reorderSlots(orderedIds: List<Long>) {
    orderedIds.forEachIndexed { index, id ->
        mealSlotDao.updateSortOrder(id, index)
    }
}

suspend fun getMealEntriesForSlot(date: String, slotId: Long?): List<MealEntryEntity> =
    mealEntryDao.getForDate(date).filter { it.slotId == slotId }

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
    ),
)
```

- [ ] **Step 3: Update AppContainer.kt to wire MealSlotDao**

In `AppContainer.kt`, add `mealSlotDao = database.mealSlotDao()` to the `LogRepository(...)` constructor call:
```kotlin
val logRepository = LogRepository(
    dailyLogDao = database.dailyLogDao(),
    mealEntryDao = database.mealEntryDao(),
    savedFoodDao = database.savedFoodDao(),
    savedMealDao = database.savedMealDao(),
    performanceDao = database.performanceDao(),
    weeklyReviewDao = database.weeklyReviewDao(),
    mealSlotDao = database.mealSlotDao(),
)
```

Also add `FoodLibraryViewModel` to `AppViewModelFactory` inside `AppContainer.kt`. First import it, then add this case to the `when` block:
```kotlin
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryViewModel

// Inside AppViewModelFactory.create():
FoodLibraryViewModel::class.java -> FoodLibraryViewModel(
    logRepository = container.logRepository,
    planRepository = container.planRepository,
    dateProvider = container.dateProvider,
)
```

- [ ] **Step 4: Build to confirm**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt \
  app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat: slot CRUD operations in LogRepository, wire FoodLibraryViewModel"
```

---

## Task 6: Today Screen Redesign

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/TodayScreen.kt`

- [ ] **Step 1: Update TodayUiState and TodayViewModel**

Replace the entire `TodayViewModel.kt`:
```kotlin
package com.zack.recomptracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.core.util.toNullableDouble
import com.zack.recomptracker.core.util.toNullableInt
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealSlotWithEntries(
    val slot: MealSlotEntity,
    val entries: List<MealEntryEntity>,
    val totals: MacroTotals,
)

data class TodayUiState(
    val date: LocalDate,
    val target: PlanPreferences = PlanPreferences(),
    val totals: MacroTotals = MacroTotals(),
    val slots: List<MealSlotWithEntries> = emptyList(),
    val unslottedEntries: List<MealEntryEntity> = emptyList(),
    val slotsEditMode: Boolean = false,
    // body metrics
    val bodyWeightKg: String = "",
    val waistCm: String = "",
    val steps: String = "",
    val sleepHours: String = "",
    val energyScore: Int = 5,
    val hungerScore: Int = 5,
    val sorenessScore: Int = 5,
    val trained: Boolean = false,
    val notes: String = "",
    val metricsDirty: Boolean = false,
    val message: String? = null,
)

class TodayViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    dateProvider: DateProvider,
) : ViewModel() {
    private val today = dateProvider.today()
    private val _uiState = MutableStateFlow(TodayUiState(date = today))
    val uiState: StateFlow<TodayUiState> = _uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TodayUiState(date = today),
    )

    init {
        viewModelScope.launch {
            combine(
                logRepository.observeDay(today),
                planRepository.preferences,
                logRepository.observeSlots(),
            ) { day, prefs, slots ->
                Triple(day, prefs, slots)
            }.collect { (day, prefs, slots) ->
                val allEntries = day.meals
                val slotMap = allEntries.groupBy { it.slotId }
                val slotedEntries = slots.map { slot ->
                    val entries = slotMap[slot.id].orEmpty()
                    MealSlotWithEntries(
                        slot = slot,
                        entries = entries,
                        totals = entries.macroTotals(),
                    )
                }
                val unslotted = slotMap[null].orEmpty()
                _uiState.update { current ->
                    val log = day.dailyLog
                    val metrics = if (!current.metricsDirty && log != null) {
                        current.copy(
                            bodyWeightKg = log.bodyWeightKg?.toString().orEmpty(),
                            waistCm = log.waistCm?.toString().orEmpty(),
                            steps = log.steps?.toString().orEmpty(),
                            sleepHours = log.sleepHours?.toString().orEmpty(),
                            energyScore = log.energyScore ?: 5,
                            hungerScore = log.hungerScore ?: 5,
                            sorenessScore = log.sorenessScore ?: 5,
                            trained = log.trained,
                            notes = log.notes,
                        )
                    } else current
                    metrics.copy(
                        target = prefs,
                        totals = day.totals,
                        slots = slotedEntries,
                        unslottedEntries = unslotted,
                    )
                }
            }
        }
    }

    fun toggleEditMode() = _uiState.update { it.copy(slotsEditMode = !it.slotsEditMode) }

    fun addSlot(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { logRepository.addSlot(name) }
    }

    fun renameSlot(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { logRepository.renameSlot(id, name) }
    }

    fun deleteSlot(id: Long) {
        viewModelScope.launch { logRepository.deleteSlot(id) }
    }

    fun reorderSlots(orderedIds: List<Long>) {
        viewModelScope.launch { logRepository.reorderSlots(orderedIds) }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch {
            logRepository.deleteMeal(id)
        }
    }

    fun onBodyWeightChanged(v: String) = editMetrics { copy(bodyWeightKg = v) }
    fun onWaistChanged(v: String) = editMetrics { copy(waistCm = v) }
    fun onStepsChanged(v: String) = editMetrics { copy(steps = v) }
    fun onSleepChanged(v: String) = editMetrics { copy(sleepHours = v) }
    fun onEnergyChanged(v: Int) = editMetrics { copy(energyScore = v.coerceIn(1, 10)) }
    fun onHungerChanged(v: Int) = editMetrics { copy(hungerScore = v.coerceIn(1, 10)) }
    fun onSorenessChanged(v: Int) = editMetrics { copy(sorenessScore = v.coerceIn(1, 10)) }
    fun onTrainedChanged(v: Boolean) = editMetrics { copy(trained = v) }
    fun onNotesChanged(v: String) = editMetrics { copy(notes = v) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun saveMetrics() {
        val s = _uiState.value
        val steps = s.steps.toNullableInt()
        if (s.steps.isNotBlank() && steps == null) {
            _uiState.update { it.copy(message = "Steps must be a whole number.") }
            return
        }
        viewModelScope.launch {
            logRepository.saveDailyMetrics(
                DailyMetricsInput(
                    date = s.date,
                    bodyWeightKg = s.bodyWeightKg.toNullableDouble(),
                    waistCm = s.waistCm.toNullableDouble(),
                    steps = steps,
                    sleepHours = s.sleepHours.toNullableDouble(),
                    energyScore = s.energyScore,
                    hungerScore = s.hungerScore,
                    sorenessScore = s.sorenessScore,
                    trained = s.trained,
                    notes = s.notes,
                ),
            )
            _uiState.update { it.copy(metricsDirty = false, message = "Metrics saved.") }
        }
    }

    private fun editMetrics(block: TodayUiState.() -> TodayUiState) =
        _uiState.update { it.block().copy(metricsDirty = true, message = null) }
}
```

- [ ] **Step 2: Rewrite TodayScreen.kt**

Replace the entire `TodayScreen.kt`:
```kotlin
package com.zack.recomptracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.ui.component.CalorieZoneBar
import com.zack.recomptracker.ui.component.MacroMiniBar
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.ScoreSlider
import com.zack.recomptracker.ui.component.SectionCard
import com.zack.recomptracker.ui.component.ToggleRow

private val Blue = Color(0xFF3b82f6)
private val DangerBg = Color(0xFF3d1515)
private val DangerText = Color(0xFFf87171)
private val Secondary = Color(0xFF6b7280)

@Composable
fun TodayScreen(viewModel: TodayViewModel, onAddToSlot: (slotId: Long, slotName: String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var newSlotName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = state.date.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                MessageText(state.message)
            }
        }

        // Calorie zone bar
        item {
            SectionCard("Calories") {
                CalorieZoneBar(
                    eaten = state.totals.calories,
                    zoneLower = state.target.calorieZoneLowerBound,
                    zoneUpper = state.target.calorieZoneUpperBound,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroMiniBar(
                        label = "Protein",
                        eaten = state.totals.proteinG,
                        target = state.target.targetProteinG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Carbs",
                        eaten = state.totals.carbsG,
                        target = state.target.targetCarbsG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Fat",
                        eaten = state.totals.fatG,
                        target = state.target.targetFatG,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Meals header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MEALS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                    color = Secondary,
                )
                TextButton(onClick = viewModel::toggleEditMode) {
                    Icon(
                        imageVector = if (state.slotsEditMode) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = if (state.slotsEditMode) "Done" else "Reorder",
                        tint = if (state.slotsEditMode) Blue else Secondary,
                    )
                    Text(
                        text = if (state.slotsEditMode) "Done" else "Reorder",
                        color = if (state.slotsEditMode) Blue else Secondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        // Meal slots
        items(state.slots, key = { it.slot.id }) { slotWithEntries ->
            if (state.slotsEditMode) {
                EditModeSlotCard(
                    slotWithEntries = slotWithEntries,
                    onRename = { viewModel.renameSlot(slotWithEntries.slot.id, it) },
                    onDelete = { viewModel.deleteSlot(slotWithEntries.slot.id) },
                )
            } else {
                LockedSlotCard(
                    slotWithEntries = slotWithEntries,
                    onAddClick = { onAddToSlot(slotWithEntries.slot.id, slotWithEntries.slot.name) },
                    onDeleteEntry = viewModel::deleteMeal,
                )
            }
        }

        // Add meal slot button
        item {
            OutlinedButton(
                onClick = { showAddSlotDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add meal slot", modifier = Modifier.padding(start = 4.dp))
            }
        }

        // Body metrics
        item {
            SectionCard("Body & recovery") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    com.zack.recomptracker.ui.component.NumberField(
                        "Weight", state.bodyWeightKg, viewModel::onBodyWeightChanged,
                        Modifier.weight(1f), "kg",
                    )
                    com.zack.recomptracker.ui.component.NumberField(
                        "Waist", state.waistCm, viewModel::onWaistChanged,
                        Modifier.weight(1f), "cm",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    com.zack.recomptracker.ui.component.NumberField(
                        "Steps", state.steps, viewModel::onStepsChanged, Modifier.weight(1f),
                    )
                    com.zack.recomptracker.ui.component.NumberField(
                        "Sleep", state.sleepHours, viewModel::onSleepChanged,
                        Modifier.weight(1f), "h",
                    )
                }
                ScoreSlider("Energy", state.energyScore, viewModel::onEnergyChanged)
                ScoreSlider("Hunger", state.hungerScore, viewModel::onHungerChanged)
                ScoreSlider("Soreness", state.sorenessScore, viewModel::onSorenessChanged)
                ToggleRow("Training day", state.trained, viewModel::onTrainedChanged)
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChanged,
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = viewModel::saveMetrics, modifier = Modifier.fillMaxWidth()) {
                    Text("Save metrics")
                }
            }
        }
    }

    // Add slot dialog
    if (showAddSlotDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddSlotDialog = false; newSlotName = "" },
            title = { Text("New meal slot") },
            text = {
                OutlinedTextField(
                    value = newSlotName,
                    onValueChange = { newSlotName = it },
                    label = { Text("Slot name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addSlot(newSlotName)
                    newSlotName = ""
                    showAddSlotDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddSlotDialog = false; newSlotName = "" }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun LockedSlotCard(
    slotWithEntries: MealSlotWithEntries,
    onAddClick: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
) {
    SectionCard(content = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = slotWithEntries.slot.name.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Secondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (slotWithEntries.entries.isNotEmpty()) {
                        Text(
                            text = "${slotWithEntries.totals.calories} kcal",
                            fontSize = 11.sp,
                            color = Secondary,
                        )
                    }
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    ) {
                        Text("+ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (slotWithEntries.entries.isEmpty()) {
                Text("Empty — tap + Add", fontSize = 12.sp, color = Color(0xFF444444))
            } else {
                slotWithEntries.entries.forEachIndexed { i, entry ->
                    if (i > 0) HorizontalDivider(color = Color(0xFF222222))
                    SlotEntryRow(entry = entry, onDelete = onDeleteEntry)
                }
            }
        }
    })
}

@Composable
private fun SlotEntryRow(entry: MealEntryEntity, onDelete: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
}

@Composable
private fun EditModeSlotCard(
    slotWithEntries: MealSlotWithEntries,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(slotWithEntries.slot.name) }

    SectionCard(content = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("⠿", fontSize = 20.sp, color = Blue)
                Column {
                    Text(slotWithEntries.slot.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${slotWithEntries.entries.size} items · ${slotWithEntries.totals.calories} kcal",
                        fontSize = 11.sp,
                        color = Secondary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { showRename = true; renameValue = slotWithEntries.slot.name },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                ) { Text("Rename", fontSize = 11.sp) }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerBg),
                ) { Text("Delete", fontSize = 11.sp, color = DangerText) }
            }
        }
    })

    if (showRename) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename slot") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(renameValue); showRename = false }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }
}
```

- [ ] **Step 3: Update AppNavGraph.kt to pass onAddToSlot callback to TodayScreen**

In `AppNavGraph.kt`, the `TodayScreen` composable call must now pass `onAddToSlot`. Update it:
```kotlin
composable(TopLevelDestination.Today.route) {
    TodayScreen(
        viewModel = viewModel(factory = factory),
        onAddToSlot = { slotId, slotName ->
            navController.navigate("${Routes.FoodLibrary}?slotId=$slotId&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}")
        },
    )
}
```

Add `Routes.FoodLibrary` to the `Routes` object (the full route constant):
```kotlin
object Routes {
    const val FoodLibrary = "food_library"
    const val Settings = "settings"
}
```

- [ ] **Step 4: Build to confirm**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt \
  app/src/main/java/com/zack/recomptracker/ui/today/TodayScreen.kt \
  app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat: Today screen redesign with meal slots and lock/unlock reorder"
```

---

## Task 7: Food Library Screen

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

- [ ] **Step 1: Create FoodLibraryViewModel.kt**

```kotlin
package com.zack.recomptracker.ui.foodlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FoodCategory { ALL, PROTEINS, CARBS, MEALS }

data class FoodLibraryUiState(
    val slotId: Long? = null,
    val slotName: String = "Food Log",
    val remainingCalories: Int = 0,
    val query: String = "",
    val category: FoodCategory = FoodCategory.ALL,
    val allFoods: List<SavedFoodEntity> = emptyList(),
    val allMeals: List<SavedMealEntity> = emptyList(),
    val message: String? = null,
    val showQuantityDialog: Boolean = false,
    val pendingFood: SavedFoodEntity? = null,
    val quantityGrams: String = "100",
    // create-food form
    val newFoodName: String = "",
    val newFoodServing: String = "100g",
    val newFoodCalories: String = "",
    val newFoodProtein: String = "",
    val newFoodCarbs: String = "",
    val newFoodFat: String = "",
    val showCreateFoodForm: Boolean = false,
    // save-meal form
    val showSaveMealDialog: Boolean = false,
    val saveMealName: String = "",
) {
    val filteredFoods: List<SavedFoodEntity>
        get() {
            val q = query.trim().lowercase()
            return allFoods.filter { food ->
                (q.isEmpty() || food.name.lowercase().contains(q)) &&
                when (category) {
                    FoodCategory.PROTEINS -> food.proteinG >= food.carbsG && food.proteinG >= food.fatG
                    FoodCategory.CARBS -> food.carbsG >= food.proteinG && food.carbsG > food.fatG
                    else -> true
                }
            }
        }

    val filteredMeals: List<SavedMealEntity>
        get() {
            val q = query.trim().lowercase()
            return allMeals.filter { q.isEmpty() || it.name.lowercase().contains(q) }
        }
}

class FoodLibraryViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodLibraryUiState())
    val uiState: StateFlow<FoodLibraryUiState> = _uiState

    fun init(slotId: Long?, slotName: String) {
        _uiState.update { it.copy(slotId = slotId, slotName = slotName.ifBlank { "Food Log" }) }
        viewModelScope.launch {
            combine(
                logRepository.observeSavedFoods(),
                logRepository.observeSavedMeals(),
                planRepository.preferences,
            ) { foods, meals, prefs ->
                Triple(foods, meals, prefs)
            }.collect { (foods, meals, prefs) ->
                _uiState.update { current ->
                    current.copy(
                        allFoods = foods,
                        allMeals = meals,
                        remainingCalories = prefs.calorieZoneLowerBound,
                    )
                }
            }
        }
    }

    fun onQueryChanged(q: String) = _uiState.update { it.copy(query = q, message = null) }
    fun onCategoryChanged(c: FoodCategory) = _uiState.update { it.copy(category = c) }

    fun requestLogFood(food: SavedFoodEntity) {
        _uiState.update { it.copy(showQuantityDialog = true, pendingFood = food, quantityGrams = "100") }
    }

    fun onQuantityChanged(v: String) = _uiState.update { it.copy(quantityGrams = v) }

    fun confirmLogFood() {
        val state = _uiState.value
        val food = state.pendingFood ?: return
        val grams = state.quantityGrams.toDoubleOrNull()
        if (grams == null || grams < 1) {
            _uiState.update { it.copy(message = "Enter a valid quantity (min 1g).") }
            return
        }
        val scale = grams / 100.0
        viewModelScope.launch {
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = dateProvider.today(),
                    mealType = "FOOD_LIBRARY",
                    name = food.name,
                    calories = (food.calories * scale).toInt(),
                    proteinG = food.proteinG * scale,
                    carbsG = food.carbsG * scale,
                    fatG = food.fatG * scale,
                ),
                slotId = state.slotId,
            )
            _uiState.update {
                it.copy(
                    showQuantityDialog = false,
                    pendingFood = null,
                    message = "${food.name} logged.",
                )
            }
        }
    }

    fun dismissQuantityDialog() = _uiState.update { it.copy(showQuantityDialog = false, pendingFood = null) }

    fun logMeal(meal: SavedMealEntity) {
        viewModelScope.launch {
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = dateProvider.today(),
                    mealType = meal.mealType,
                    name = meal.name,
                    calories = meal.calories,
                    proteinG = meal.proteinG,
                    carbsG = meal.carbsG,
                    fatG = meal.fatG,
                ),
                slotId = _uiState.value.slotId,
            )
            _uiState.update { it.copy(message = "${meal.name} logged.") }
        }
    }

    // Create food form
    fun onNewFoodNameChanged(v: String) = _uiState.update { it.copy(newFoodName = v) }
    fun onNewFoodServingChanged(v: String) = _uiState.update { it.copy(newFoodServing = v) }
    fun onNewFoodCaloriesChanged(v: String) = _uiState.update { it.copy(newFoodCalories = v) }
    fun onNewFoodProteinChanged(v: String) = _uiState.update { it.copy(newFoodProtein = v) }
    fun onNewFoodCarbsChanged(v: String) = _uiState.update { it.copy(newFoodCarbs = v) }
    fun onNewFoodFatChanged(v: String) = _uiState.update { it.copy(newFoodFat = v) }
    fun toggleCreateFoodForm() = _uiState.update { it.copy(showCreateFoodForm = !it.showCreateFoodForm, message = null) }

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
        viewModelScope.launch {
            logRepository.saveFood(
                SavedFoodEntity(
                    name = s.newFoodName.trim(),
                    servingName = s.newFoodServing.trim(),
                    calories = cal,
                    proteinG = p,
                    carbsG = c,
                    fatG = f,
                ),
            )
            _uiState.update {
                it.copy(
                    showCreateFoodForm = false,
                    newFoodName = "", newFoodServing = "100g",
                    newFoodCalories = "", newFoodProtein = "",
                    newFoodCarbs = "", newFoodFat = "",
                    message = "Food saved to library.",
                )
            }
        }
    }

    // Save slot as meal
    fun openSaveMealDialog() {
        _uiState.update { it.copy(showSaveMealDialog = true, saveMealName = it.slotName) }
    }

    fun onSaveMealNameChanged(v: String) = _uiState.update { it.copy(saveMealName = v) }

    fun confirmSaveMeal() {
        val s = _uiState.value
        if (s.saveMealName.isBlank()) return
        viewModelScope.launch {
            val slotEntries = logRepository.getMealEntriesForSlot(
                date = dateProvider.today().toString(),
                slotId = s.slotId,
            )
            if (slotEntries.isEmpty()) {
                _uiState.update { it.copy(message = "No foods in slot to save.") }
                return@launch
            }
            logRepository.saveMeal(
                SavedMealEntity(
                    name = s.saveMealName.trim(),
                    mealType = "SAVED",
                    calories = slotEntries.sumOf { it.calories },
                    proteinG = slotEntries.sumOf { it.proteinG },
                    carbsG = slotEntries.sumOf { it.carbsG },
                    fatG = slotEntries.sumOf { it.fatG },
                ),
            )
            _uiState.update {
                it.copy(showSaveMealDialog = false, saveMealName = "", message = "Meal saved.")
            }
        }
    }

    fun dismissSaveMealDialog() = _uiState.update { it.copy(showSaveMealDialog = false) }
}
```

- [ ] **Step 2: Create FoodLibraryScreen.kt**

```kotlin
package com.zack.recomptracker.ui.foodlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.SectionCard

private val Blue = Color(0xFF3b82f6)
private val Secondary = Color(0xFF6b7280)
private val GreenStar = Color(0xFF15803d)

@Composable
fun FoodLibraryScreen(
    viewModel: FoodLibraryViewModel,
    slotId: Long?,
    slotName: String,
    onBack: () -> Unit,
) {
    LaunchedEffect(slotId, slotName) { viewModel.init(slotId, slotName) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = if (slotId != null) "Add to ${state.slotName}" else "Foods & Meals",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (slotId != null) {
                        Text("${state.remainingCalories} kcal to zone", fontSize = 11.sp, color = Secondary)
                    }
                }
            }
            MessageText(state.message)
        }

        // Search
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = { Text("Search saved foods…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Category chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FoodCategory.entries) { cat ->
                    FilterChip(
                        selected = state.category == cat,
                        onClick = { viewModel.onCategoryChanged(cat) },
                        label = {
                            Text(
                                when (cat) {
                                    FoodCategory.ALL -> "All"
                                    FoodCategory.PROTEINS -> "Proteins"
                                    FoodCategory.CARBS -> "Carbs"
                                    FoodCategory.MEALS -> "Saved Meals"
                                },
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Blue,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        // Food list
        if (state.category != FoodCategory.MEALS) {
            if (state.filteredFoods.isEmpty()) {
                item { Text("No foods found.", color = Secondary) }
            } else {
                items(state.filteredFoods, key = { it.id }) { food ->
                    FoodRow(food = food, onLog = { viewModel.requestLogFood(food) })
                }
            }
        }

        // Meal list
        if (state.category == FoodCategory.ALL || state.category == FoodCategory.MEALS) {
            items(state.filteredMeals, key = { "meal_${it.id}" }) { meal ->
                MealRow(meal = meal, onLog = { viewModel.logMeal(meal) })
            }
        }

        // Bottom actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (slotId != null) {
                    OutlinedButton(
                        onClick = viewModel::openSaveMealDialog,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenStar),
                    ) {
                        Text("Save current slot as meal")
                    }
                }
                OutlinedButton(
                    onClick = viewModel::toggleCreateFoodForm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                ) {
                    Text(if (state.showCreateFoodForm) "Cancel" else "+ Create new food")
                }
                if (state.showCreateFoodForm) {
                    CreateFoodForm(state = state, viewModel = viewModel)
                }
            }
        }
    }

    // Quantity dialog
    if (state.showQuantityDialog && state.pendingFood != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissQuantityDialog,
            title = { Text("How many grams?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.pendingFood!!.name, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.quantityGrams,
                        onValueChange = viewModel::onQuantityChanged,
                        label = { Text("Grams") },
                        singleLine = true,
                        suffix = { Text("g") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLogFood) { Text("Log") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQuantityDialog) { Text("Cancel") }
            },
        )
    }

    // Save meal dialog
    if (state.showSaveMealDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveMealDialog,
            title = { Text("Save as meal") },
            text = {
                OutlinedTextField(
                    value = state.saveMealName,
                    onValueChange = viewModel::onSaveMealNameChanged,
                    label = { Text("Meal name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSaveMeal() }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSaveMealDialog) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FoodRow(food: SavedFoodEntity, onLog: () -> Unit) {
    SectionCard(content = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "${food.servingName} · ${food.calories} kcal · ${food.proteinG.toInt()}P ${food.carbsG.toInt()}C ${food.fatG.toInt()}F",
                    fontSize = 11.sp,
                    color = Secondary,
                )
            }
            Button(
                onClick = onLog,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) { Text("Log", fontSize = 11.sp) }
        }
    })
}

@Composable
private fun MealRow(meal: SavedMealEntity, onLog: () -> Unit) {
    SectionCard(content = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(meal.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "${meal.calories} kcal · ${meal.proteinG.toInt()}P ${meal.carbsG.toInt()}C ${meal.fatG.toInt()}F",
                    fontSize = 11.sp,
                    color = Secondary,
                )
            }
            Button(
                onClick = onLog,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) { Text("Log all", fontSize = 11.sp) }
        }
    })
}

@Composable
private fun CreateFoodForm(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    SectionCard(content = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New food", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.newFoodName,
                onValueChange = viewModel::onNewFoodNameChanged,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.newFoodServing,
                onValueChange = viewModel::onNewFoodServingChanged,
                label = { Text("Serving (e.g. 100g, 1 scoop)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Calories", state.newFoodCalories, viewModel::onNewFoodCaloriesChanged, Modifier.weight(1f), "kcal")
                NumberField("Protein", state.newFoodProtein, viewModel::onNewFoodProteinChanged, Modifier.weight(1f), "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Carbs", state.newFoodCarbs, viewModel::onNewFoodCarbsChanged, Modifier.weight(1f), "g")
                NumberField("Fat", state.newFoodFat, viewModel::onNewFoodFatChanged, Modifier.weight(1f), "g")
            }
            Button(onClick = viewModel::saveNewFood, modifier = Modifier.fillMaxWidth()) {
                Text("Save food")
            }
        }
    })
}
```

- [ ] **Step 3: Register FoodLibrary route in AppNavGraph.kt**

Add this composable route inside the `NavHost` block in `AppNavGraph.kt`:
```kotlin
composable(
    route = "${Routes.FoodLibrary}?slotId={slotId}&slotName={slotName}",
    arguments = listOf(
        androidx.navigation.navArgument("slotId") {
            type = androidx.navigation.NavType.LongType
            defaultValue = -1L
        },
        androidx.navigation.navArgument("slotName") {
            type = androidx.navigation.NavType.StringType
            defaultValue = ""
        },
    ),
) { backStackEntry ->
    val slotId = backStackEntry.arguments?.getLong("slotId")?.takeIf { it != -1L }
    val slotName = backStackEntry.arguments?.getString("slotName").orEmpty()
    FoodLibraryScreen(
        viewModel = viewModel<FoodLibraryViewModel>(factory = factory),
        slotId = slotId,
        slotName = java.net.URLDecoder.decode(slotName, "UTF-8"),
        onBack = { navController.popBackStack() },
    )
}
```

Also update the `MoreScreen` Foods navigation:
```kotlin
composable(TopLevelDestination.More.route) {
    MoreScreen(
        onFoodsClick = { navController.navigate(Routes.FoodLibrary) },
        onSettingsClick = { navController.navigate(Routes.Settings) },
    )
}
```

- [ ] **Step 4: Build to confirm**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/ \
  app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat: food library screen with search, categories, log, create food, save meal"
```

---

## Task 8: Dashboard Uses CalorieZoneBar + Run Full Test Suite

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt`

- [ ] **Step 1: Expose zone bounds from DashboardViewModel**

Open `DashboardViewModel.kt`. The `DashboardUiState` already holds `preferences: PlanPreferences`. Since `PlanPreferences` now includes `calorieZoneLowerBound` and `calorieZoneUpperBound`, no change is needed in the ViewModel — the values are already available via `state.preferences.calorieZoneLowerBound`.

- [ ] **Step 2: Replace calorie display in DashboardScreen.kt**

In `DashboardScreen.kt`, find the "Calorie verdict" and "Current targets" section cards. Add a new `CalorieZoneBar` item above them:
```kotlin
// Add this import at the top of DashboardScreen.kt
import com.zack.recomptracker.ui.component.CalorieZoneBar
import com.zack.recomptracker.ui.component.MacroMiniBar

// Replace the "Today" section card item with this:
item {
    SectionCard("Today") {
        CalorieZoneBar(
            eaten = state.todayTotals.calories,
            zoneLower = state.preferences.calorieZoneLowerBound,
            zoneUpper = state.preferences.calorieZoneUpperBound,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroMiniBar("Protein", state.todayTotals.proteinG, state.preferences.targetProteinG, Modifier.weight(1f))
            MacroMiniBar("Carbs", state.todayTotals.carbsG, state.preferences.targetCarbsG, Modifier.weight(1f))
            MacroMiniBar("Fat", state.todayTotals.fatG, state.preferences.targetFatG, Modifier.weight(1f))
        }
    }
}
```

- [ ] **Step 3: Run all unit tests**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew test --no-daemon 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`, all existing tests (AdjustmentEngineTest, TrendCalculatorTest, AdherenceCalculatorTest) + new CalorieZoneBarKtTest pass.

- [ ] **Step 4: Build full debug APK**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt \
  app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt
git commit -m "feat: Dashboard uses CalorieZoneBar"
```

---

## Task 9: Plan Screen — Editable Zone Bounds

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanViewModel.kt`

- [ ] **Step 1: Add zone bound state and handlers to PlanViewModel**

Open `PlanViewModel.kt`. Add `calorieZoneLowerBound` and `calorieZoneUpperBound` to its `UiState` data class (or whatever state holder it uses). Then add two update handlers.

Inspect the current `PlanViewModel.kt` to confirm its state structure, then add these fields and functions following the existing pattern:

```kotlin
// In PlanUiState (alongside existing targetCalories, etc.):
val calorieZoneLowerBound: String = "",
val calorieZoneUpperBound: String = "",

// In PlanViewModel init block, map from PlanPreferences:
calorieZoneLowerBound = prefs.calorieZoneLowerBound.toString(),
calorieZoneUpperBound = prefs.calorieZoneUpperBound.toString(),

// New update functions:
fun updateZoneLower(v: String) = _uiState.update { it.copy(calorieZoneLowerBound = v) }
fun updateZoneUpper(v: String) = _uiState.update { it.copy(calorieZoneUpperBound = v) }

// In the save() function, add to PlanPreferences:
calorieZoneLowerBound = state.calorieZoneLowerBound.toIntOrNull() ?: 2400,
calorieZoneUpperBound = state.calorieZoneUpperBound.toIntOrNull() ?: 2600,
```

- [ ] **Step 2: Add zone bound fields to PlanScreen.kt**

In the "Review rules" `SectionCard` in `PlanScreen.kt`, add two `NumberField` rows after the existing threshold fields:

```kotlin
NumberField(
    "Calorie zone lower",
    state.calorieZoneLowerBound,
    viewModel::updateZoneLower,
    suffix = "kcal",
)
NumberField(
    "Calorie zone upper",
    state.calorieZoneUpperBound,
    viewModel::updateZoneUpper,
    suffix = "kcal",
)
```

- [ ] **Step 3: Build to confirm**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run full test suite and build APK**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew test assembleDebug --no-daemon 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt \
  app/src/main/java/com/zack/recomptracker/ui/plan/PlanViewModel.kt
git commit -m "feat: editable calorie zone bounds in Plan screen — redesign complete"
```
