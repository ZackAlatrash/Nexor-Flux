# Food History Week Navigation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Samsung Health-style week bar chart strip to `FoodScreen` that lets users view and fully edit their food log for any of the past 7 days.

**Architecture:** A new `FoodLogViewModel` (owns multi-date food state) replaces `TodayViewModel` on the food route; `TodayViewModel` stays untouched for `BodyRecoveryScreen`. `WeekCalorieStrip` renders 7 animated bars with a pill highlight on the selected day. The selected date is threaded through to `FoodLibraryScreen` so new entries land on the correct day.

**Tech Stack:** Kotlin, Jetpack Compose, Room (via existing DAOs), kotlinx.coroutines (Flow / flatMapLatest), Mockito-Kotlin (tests)

---

## File Map

| Action | Path |
|--------|------|
| Modify | `app/src/main/java/com/zack/recomptracker/data/repository/LogModels.kt` |
| Modify | `app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt` |
| Create | `app/src/test/java/com/zack/recomptracker/data/LogRepositoryWeekCaloriesTest.kt` |
| Create | `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt` |
| Create | `app/src/test/java/com/zack/recomptracker/ui/today/FoodLogViewModelTest.kt` |
| Modify | `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` |
| Create | `app/src/main/java/com/zack/recomptracker/ui/component/WeekCalorieStrip.kt` |
| Modify | `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt` |
| Modify | `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt` |
| Modify | `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt` |
| Modify | `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt` |

---

## Task 1: `DayCalorieSummary` model + `observeWeekCalories()` repository method

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/LogModels.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt`
- Create: `app/src/test/java/com/zack/recomptracker/data/LogRepositoryWeekCaloriesTest.kt`

- [ ] **Step 1.1 — Write the failing test**

Create `LogRepositoryWeekCaloriesTest.kt`:

```kotlin
package com.zack.recomptracker.data

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
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogRepositoryWeekCaloriesTest {

    private fun entry(date: String, calories: Int) = MealEntryEntity(
        id = 0, date = date, mealType = "FOOD_LIBRARY", name = "food",
        calories = calories, proteinG = 0.0, carbsG = 0.0, fatG = 0.0,
    )

    private fun buildRepo(entries: List<MealEntryEntity>): LogRepository {
        val dao = object : MealEntryDao {
            override fun observeForDate(date: String) = flowOf(entries.filter { it.date == date })
            override suspend fun getForDate(date: String) = entries.filter { it.date == date }
            override fun observeBetween(s: String, e: String): Flow<List<MealEntryEntity>> =
                flowOf(entries.filter { it.date in s..e })
            override fun observeAll() = flowOf(entries)
            override fun observeFoodLibraryEntries() = flowOf(emptyList<MealEntryEntity>())
            override suspend fun getAll() = entries
            override suspend fun insert(entry: MealEntryEntity) = 0L
            override suspend fun insertAll(entries: List<MealEntryEntity>) = Unit
            override suspend fun update(entry: MealEntryEntity) = Unit
            override suspend fun delete(entry: MealEntryEntity) = Unit
            override suspend fun deleteById(id: Long) = Unit
            override suspend fun deleteForDate(date: String) = Unit
            override suspend fun deleteAll() = Unit
            override suspend fun clearSlotId(slotId: Long) = Unit
            override suspend fun getById(id: Long): MealEntryEntity? = null
        }
        return LogRepository(
            dailyLogDao = NoopDailyLogDao,
            mealEntryDao = dao,
            savedFoodDao = NoopSavedFoodDao2,
            savedMealDao = NoopSavedMealDao2,
            performanceDao = NoopPerformanceDao2,
            weeklyReviewDao = NoopWeeklyReviewDao2,
            mealSlotDao = NoopMealSlotDao2,
        )
    }

    @Test
    fun `observeWeekCalories sums calories per date`() = runTest {
        val jun1 = LocalDate.of(2026, 6, 1)
        val jun2 = LocalDate.of(2026, 6, 2)
        val entries = listOf(
            entry("2026-06-01", 500),
            entry("2026-06-01", 700),
            entry("2026-06-02", 400),
        )
        val repo = buildRepo(entries)

        val result = repo.observeWeekCalories(
            start = LocalDate.of(2026, 5, 26),
            end   = LocalDate.of(2026, 6, 1),
        ).first()

        assertEquals(1200, result[jun1])
        assertEquals(null, result[jun2])  // Jun 2 is outside the range
    }

    @Test
    fun `observeWeekCalories returns empty map when no entries`() = runTest {
        val repo = buildRepo(emptyList())
        val result = repo.observeWeekCalories(
            start = LocalDate.of(2026, 5, 26),
            end   = LocalDate.of(2026, 6, 1),
        ).first()
        assertEquals(emptyMap<LocalDate, Int>(), result)
    }
}

// ── Noop stubs ───────────────────────────────────────────────────────────────

private object NoopDailyLogDao : DailyLogDao {
    override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow { emit(null) }
    override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override fun observeBetween(s: String, e: String): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun getByDate(date: String): DailyLogEntity? = null
    override suspend fun getAll(): List<DailyLogEntity> = emptyList()
    override suspend fun upsert(log: DailyLogEntity) = Unit
    override suspend fun insertAll(logs: List<DailyLogEntity>) = Unit
    override suspend fun deleteAll() = Unit
}
private object NoopSavedFoodDao2 : SavedFoodDao {
    override fun observeAll(): Flow<List<SavedFoodEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll() = emptyList<SavedFoodEntity>()
    override suspend fun insert(food: SavedFoodEntity) = 0L
    override suspend fun insertAll(foods: List<SavedFoodEntity>) = Unit
    override suspend fun update(food: SavedFoodEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}
private object NoopSavedMealDao2 : SavedMealDao {
    override fun observeAll(): Flow<List<SavedMealEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll() = emptyList<SavedMealEntity>()
    override suspend fun insert(meal: SavedMealEntity) = 0L
    override suspend fun insertAll(meals: List<SavedMealEntity>) = Unit
    override suspend fun update(meal: SavedMealEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}
private object NoopPerformanceDao2 : PerformanceDao {
    override fun observeAll(): Flow<List<LiftPerformanceEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll() = emptyList<LiftPerformanceEntity>()
    override suspend fun insert(p: LiftPerformanceEntity) = 0L
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}
private object NoopWeeklyReviewDao2 : WeeklyReviewDao {
    override fun observeAll(): Flow<List<WeeklyReviewEntity>> = flow { emit(emptyList()) }
    override suspend fun upsert(review: WeeklyReviewEntity) = Unit
    override suspend fun deleteAll() = Unit
}
private object NoopMealSlotDao2 : MealSlotDao {
    override fun observeAll(): Flow<List<MealSlotEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll() = emptyList<MealSlotEntity>()
    override suspend fun insert(slot: MealSlotEntity) = 0L
    override suspend fun update(slot: MealSlotEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun updateSortOrder(id: Long, order: Int) = Unit
}
```

- [ ] **Step 1.2 — Run test to confirm it fails**

```bash
cd app && ../gradlew testDebugUnitTest --tests "com.zack.recomptracker.data.LogRepositoryWeekCaloriesTest" 2>&1 | tail -20
```
Expected: compilation error — `observeWeekCalories` does not exist.

- [ ] **Step 1.3 — Add `DayCalorieSummary` to `LogModels.kt`**

Append to the end of `LogModels.kt` (after the existing `MealEntryInput` data class):

```kotlin
data class DayCalorieSummary(val date: LocalDate, val calories: Int)
```

- [ ] **Step 1.4 — Add `observeWeekCalories()` to `LogRepository.kt`**

Add this import at the top of `LogRepository.kt` (it's already imported via `Flow` and `map`, but confirm `map` is imported):
```kotlin
import com.zack.recomptracker.data.repository.DayCalorieSummary
```
(No import needed — `DayCalorieSummary` is in the same package.)

Add the method inside the `LogRepository` class body, after `observeRecentFoods`:

```kotlin
fun observeWeekCalories(start: LocalDate, end: LocalDate): Flow<Map<LocalDate, Int>> =
    mealEntryDao.observeBetween(start.toString(), end.toString())
        .map { entries ->
            entries
                .groupBy { LocalDate.parse(it.date) }
                .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.calories } }
        }
```

- [ ] **Step 1.5 — Run test to confirm it passes**

```bash
cd app && ../gradlew testDebugUnitTest --tests "com.zack.recomptracker.data.LogRepositoryWeekCaloriesTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, 2 tests pass.

- [ ] **Step 1.6 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/LogModels.kt \
        app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt \
        app/src/test/java/com/zack/recomptracker/data/LogRepositoryWeekCaloriesTest.kt
git commit -m "feat(food-history): add DayCalorieSummary + observeWeekCalories to LogRepository"
```

---

## Task 2: `FoodLogViewModel` + register in `AppContainer`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ui/today/FoodLogViewModelTest.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 2.1 — Write the failing tests**

Create `FoodLogViewModelTest.kt`:

```kotlin
package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FoodLogViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 6, 4)
    private lateinit var logRepo: LogRepository
    private lateinit var planRepo: PlanRepository

    private val dateProvider = object : DateProvider {
        override fun today() = today
    }

    private fun emptyDayLog(date: LocalDate) = DayLog(
        date = date, dailyLog = null, meals = emptyList(), totals = MacroTotals()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        logRepo = mock()
        planRepo = mock()
        whenever(logRepo.observeDay(any())).thenReturn(flowOf(emptyDayLog(today)))
        whenever(logRepo.observeSlots()).thenReturn(flowOf(emptyList()))
        whenever(logRepo.observeWeekCalories(any(), any())).thenReturn(flowOf(emptyMap()))
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm() = FoodLogViewModel(logRepo, planRepo, dateProvider)

    @Test
    fun `initial selectedDate is today`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()
        assertEquals(today, vm.uiState.value.selectedDate)
    }

    @Test
    fun `initial isToday is true`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isToday)
    }

    @Test
    fun `selectDate updates selectedDate`() = runTest {
        val vm = buildVm()
        val yesterday = today.minusDays(1)
        whenever(logRepo.observeDay(yesterday)).thenReturn(flowOf(emptyDayLog(yesterday)))
        advanceUntilIdle()

        vm.selectDate(yesterday)
        advanceUntilIdle()

        assertEquals(yesterday, vm.uiState.value.selectedDate)
        assertFalse(vm.uiState.value.isToday)
    }

    @Test
    fun `selectDate clamps to today when future date given`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        vm.selectDate(today.plusDays(1))
        advanceUntilIdle()

        assertEquals(today, vm.uiState.value.selectedDate)
    }

    @Test
    fun `selectDate clamps to today minus 6 when older date given`() = runTest {
        val vm = buildVm()
        val sixDaysAgo = today.minusDays(6)
        whenever(logRepo.observeDay(sixDaysAgo)).thenReturn(flowOf(emptyDayLog(sixDaysAgo)))
        advanceUntilIdle()

        vm.selectDate(today.minusDays(10))
        advanceUntilIdle()

        assertEquals(sixDaysAgo, vm.uiState.value.selectedDate)
    }
}
```

- [ ] **Step 2.2 — Run tests to confirm failure**

```bash
cd app && ../gradlew testDebugUnitTest --tests "com.zack.recomptracker.ui.today.FoodLogViewModelTest" 2>&1 | tail -20
```
Expected: compilation error — `FoodLogViewModel` does not exist.

- [ ] **Step 2.3 — Create `FoodLogViewModel.kt`**

Create `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt`:

```kotlin
package com.zack.recomptracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DayCalorieSummary
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodLogUiState(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val target: PlanPreferences = PlanPreferences(),
    val totals: MacroTotals = MacroTotals(),
    val slots: List<MealSlotWithEntries> = emptyList(),
    val slotsEditMode: Boolean = false,
    val weekSummary: List<DayCalorieSummary> = emptyList(),
    val message: String? = null,
) {
    val isToday: Boolean get() = selectedDate == today
}

@OptIn(ExperimentalCoroutinesApi::class)
class FoodLogViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    dateProvider: DateProvider,
) : ViewModel() {

    val today: LocalDate = dateProvider.today()
    private val _selectedDate = MutableStateFlow(today)

    private val _uiState = MutableStateFlow(FoodLogUiState(selectedDate = today, today = today))
    val uiState: StateFlow<FoodLogUiState> = _uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FoodLogUiState(selectedDate = today, today = today),
    )

    init {
        viewModelScope.launch {
            combine(
                _selectedDate.flatMapLatest { date -> logRepository.observeDay(date) },
                planRepository.preferences,
                logRepository.observeSlots(),
            ) { day, prefs, slots ->
                Triple(day, prefs, slots)
            }.collect { (day, prefs, slots) ->
                val slotMap = day.meals.groupBy { it.slotId }
                val slottedEntries = slots.map { slot ->
                    val entries = slotMap[slot.id].orEmpty()
                    MealSlotWithEntries(slot = slot, entries = entries, totals = entries.macroTotals())
                }
                _uiState.update {
                    it.copy(
                        selectedDate = day.date,
                        target = prefs,
                        totals = day.totals,
                        slots = slottedEntries,
                    )
                }
            }
        }

        viewModelScope.launch {
            logRepository.observeWeekCalories(today.minusDays(6), today).collect { weekMap ->
                val summaries = (0..6).map { i ->
                    val d = today.minusDays((6 - i).toLong())
                    DayCalorieSummary(date = d, calories = weekMap[d] ?: 0)
                }
                _uiState.update { it.copy(weekSummary = summaries) }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        val clamped = date.coerceIn(today.minusDays(6), today)
        _selectedDate.value = clamped
        _uiState.update { it.copy(selectedDate = clamped) }
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
        viewModelScope.launch { logRepository.deleteMeal(id) }
    }

    fun updateMealMacros(
        entry: MealEntryEntity,
        calories: Int,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
    ) {
        viewModelScope.launch {
            logRepository.updateMealEntry(
                entry.copy(calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG),
            )
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
```

- [ ] **Step 2.4 — Register `FoodLogViewModel` in `AppContainer.kt`**

Add the import at the top of `AppContainer.kt`:
```kotlin
import com.zack.recomptracker.ui.today.FoodLogViewModel
```

Add this case inside `AppViewModelFactory.create()` before the `else ->` clause:

```kotlin
FoodLogViewModel::class.java -> FoodLogViewModel(
    logRepository = container.logRepository,
    planRepository = container.planRepository,
    dateProvider = container.dateProvider,
)
```

- [ ] **Step 2.5 — Run tests to confirm they pass**

```bash
cd app && ../gradlew testDebugUnitTest --tests "com.zack.recomptracker.ui.today.FoodLogViewModelTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, 5 tests pass.

- [ ] **Step 2.6 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt \
        app/src/main/java/com/zack/recomptracker/core/AppContainer.kt \
        app/src/test/java/com/zack/recomptracker/ui/today/FoodLogViewModelTest.kt
git commit -m "feat(food-history): add FoodLogViewModel with multi-date selection"
```

---

## Task 3: `WeekCalorieStrip` composable

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/WeekCalorieStrip.kt`

- [ ] **Step 3.1 — Create `WeekCalorieStrip.kt`**

```kotlin
package com.zack.recomptracker.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.data.repository.DayCalorieSummary
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekCalorieStrip(
    weekData: List<DayCalorieSummary>,
    selectedDate: LocalDate,
    today: LocalDate,
    targetLow: Int,
    targetHigh: Int,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (weekData.isEmpty()) return

    val scaleMax = (targetHigh * 1.3f).toInt().coerceAtLeast(1)
    val zoneLowFrac  = (targetLow.toFloat()  / scaleMax).coerceIn(0f, 1f)
    val zoneHighFrac = (targetHigh.toFloat() / scaleMax).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x0D000000), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        // ── Bar area with continuous dotted zone lines ─────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .drawBehind {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
                    val yHigh = size.height * (1f - zoneHighFrac)
                    val yLow  = size.height * (1f - zoneLowFrac)
                    drawLine(
                        color = Color(0x408B5CF6),
                        start = Offset(0f, yHigh), end = Offset(size.width, yHigh),
                        strokeWidth = 1.dp.toPx(), pathEffect = dash,
                    )
                    drawLine(
                        color = Color(0x258B5CF6),
                        start = Offset(0f, yLow), end = Offset(size.width, yLow),
                        strokeWidth = 1.dp.toPx(), pathEffect = dash,
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            weekData.forEach { summary ->
                WeekBarItem(
                    summary     = summary,
                    isSelected  = summary.date == selectedDate,
                    scaleMax    = scaleMax,
                    targetLow   = targetLow,
                    targetHigh  = targetHigh,
                    onSelected  = { onDaySelected(summary.date) },
                    modifier    = Modifier.weight(1f),
                )
            }
        }

        // ── Day labels ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            weekData.forEach { summary ->
                val sel = summary.date == selectedDate
                Text(
                    text = summary.date.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .take(2),
                    fontSize = if (sel) 9.sp else 8.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) Violet400 else Color(0xFF555555),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekBarItem(
    summary: DayCalorieSummary,
    isSelected: Boolean,
    scaleMax: Int,
    targetLow: Int,
    targetHigh: Int,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = summary.calories == 0
    val targetFrac = if (empty) 0.04f else (summary.calories.toFloat() / scaleMax).coerceIn(0f, 1f)
    val animFrac by animateFloatAsState(targetFrac, tween(400), label = "bar_${summary.date}")

    val barColor = when {
        empty -> Color.White.copy(alpha = if (isSelected) 0.18f else 0.07f)
        summary.calories in targetLow..targetHigh ->
            Violet400.copy(alpha = if (isSelected) 1f else 0.50f)
        summary.calories > targetHigh ->
            Color(0xFFF97316).copy(alpha = if (isSelected) 1f else 0.50f)
        else -> Violet300.copy(alpha = if (isSelected) 0.80f else 0.30f)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0x158B5CF6) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0x308B5CF6) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelected,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .width(if (isSelected) 12.dp else 8.dp)
                .fillMaxHeight(animFrac)
                .background(barColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
        )
    }
}
```

- [ ] **Step 3.2 — Verify compilation**

```bash
cd app && ../gradlew assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -10
```
Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 3.3 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/WeekCalorieStrip.kt
git commit -m "feat(food-history): add WeekCalorieStrip composable"
```

---

## Task 4: Update `FoodScreen` to use `FoodLogViewModel`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`

- [ ] **Step 4.1 — Replace the `FoodScreen` entry point function**

Find and replace the top-level `FoodScreen` function (lines 72–94 in the current file). The rest of the file (`FoodContent`, `NutritionStrip`, slot cards, etc.) stays untouched — only the entry point and header change.

Replace the `FoodScreen` function:

```kotlin
@Composable
fun FoodScreen(
    viewModel: FoodLogViewModel,
    onAddToSlot: (slotId: Long, slotName: String, date: LocalDate) -> Unit,
    onBrowseLibrary: () -> Unit,
    onEditEntryAmount: (slotId: Long?, slotName: String, entryId: Long, date: LocalDate) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FoodContent(
        state             = state,
        actions           = FoodActions(
            onToggleEditMode = viewModel::toggleEditMode,
            onAddSlot        = viewModel::addSlot,
            onRenameSlot     = viewModel::renameSlot,
            onDeleteSlot     = viewModel::deleteSlot,
            onReorderSlots   = viewModel::reorderSlots,
            onDeleteMeal     = viewModel::deleteMeal,
            onEditMacros     = viewModel::updateMealMacros,
        ),
        onAddToSlot       = { slotId, slotName -> onAddToSlot(slotId, slotName, state.selectedDate) },
        onBrowseLibrary   = onBrowseLibrary,
        onEditEntryAmount = { slotId, slotName, entryId -> onEditEntryAmount(slotId, slotName, entryId, state.selectedDate) },
        onSelectDate      = viewModel::selectDate,
        onTodayClick      = { viewModel.selectDate(viewModel.today) },
    )
}
```

- [ ] **Step 4.2 — Update `FoodContent` signature and body**

Replace the `FoodContent` function signature and its `Box`/`Column` structure. The changes are: (a) `state: TodayUiState` → `state: FoodLogUiState`, (b) add `onSelectDate` and `onTodayClick` params, (c) insert `WeekCalorieStrip` between the header and the nutrition strip, (d) pass `state.selectedDate` to `FoodScreenHeader`.

Replace the `FoodContent` function:

```kotlin
@Composable
fun FoodContent(
    state: FoodLogUiState,
    actions: FoodActions,
    onAddToSlot: (slotId: Long, slotName: String) -> Unit,
    onBrowseLibrary: () -> Unit,
    onEditEntryAmount: (slotId: Long?, slotName: String, entryId: Long) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var newSlotName by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(Brush.radialGradient(colors = listOf(Color(0x298B5CF6), Color.Transparent))),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            FoodScreenHeader(
                date = state.selectedDate,
                showTodayPill = !state.isToday,
                onTodayClick = onTodayClick,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    WeekCalorieStrip(
                        weekData      = state.weekSummary,
                        selectedDate  = state.selectedDate,
                        today         = state.today,
                        targetLow     = state.target.calorieZoneLowerBound,
                        targetHigh    = state.target.calorieZoneUpperBound,
                        onDaySelected = onSelectDate,
                    )
                }

                item { NutritionStrip(state) }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "MEALS",
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = TextMuted, letterSpacing = 0.12.sp,
                        )
                        Text(
                            text = if (state.slotsEditMode) "Done" else "Reorder",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = Color(0xB38B5CF6),
                            modifier = Modifier.clickable(onClick = actions.onToggleEditMode),
                        )
                    }
                }

                items(state.slots, key = { it.slot.id }) { slotWithEntries ->
                    val index = state.slots.indexOf(slotWithEntries)
                    if (state.slotsEditMode) {
                        EditModeSlotCard(
                            slotWithEntries = slotWithEntries,
                            canMoveUp   = index > 0,
                            canMoveDown = index < state.slots.lastIndex,
                            onMoveUp    = {
                                val ids = state.slots.map { it.slot.id }.toMutableList()
                                val i = ids.indexOf(slotWithEntries.slot.id)
                                if (i > 0) { ids.add(i - 1, ids.removeAt(i)); actions.onReorderSlots(ids) }
                            },
                            onMoveDown  = {
                                val ids = state.slots.map { it.slot.id }.toMutableList()
                                val i = ids.indexOf(slotWithEntries.slot.id)
                                if (i < ids.lastIndex) { ids.add(i + 1, ids.removeAt(i)); actions.onReorderSlots(ids) }
                            },
                            onRename = { actions.onRenameSlot(slotWithEntries.slot.id, it) },
                            onDelete = { actions.onDeleteSlot(slotWithEntries.slot.id) },
                        )
                    } else {
                        LockedSlotCard(
                            slotWithEntries   = slotWithEntries,
                            onAddClick        = { onAddToSlot(slotWithEntries.slot.id, slotWithEntries.slot.name) },
                            onDeleteEntry     = actions.onDeleteMeal,
                            onEditEntryAmount = { entryId -> onEditEntryAmount(slotWithEntries.slot.id, slotWithEntries.slot.name, entryId) },
                            onEditMacros      = actions.onEditMacros,
                        )
                    }
                }

                item { LiquidSecondaryButton(text = "+ Add meal slot", onClick = { showAddSlotDialog = true }) }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (showAddSlotDialog) {
        AlertDialog(
            onDismissRequest = { showAddSlotDialog = false; newSlotName = "" },
            title = { Text("New meal slot") },
            text = {
                OutlinedTextField(
                    value = newSlotName, onValueChange = { newSlotName = it },
                    label = { Text("Slot name") }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { actions.onAddSlot(newSlotName); newSlotName = ""; showAddSlotDialog = false }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSlotDialog = false; newSlotName = "" }) { Text("Cancel") }
            },
        )
    }
}
```

- [ ] **Step 4.3 — Update `FoodScreenHeader` to show the "Today" pill**

Replace the private `FoodScreenHeader` function:

```kotlin
@Composable
private fun FoodScreenHeader(
    date: LocalDate,
    showTodayPill: Boolean,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateStr = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEE, MMMM d", Locale.getDefault()))
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "Food Log",
            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
            color = Color.White, letterSpacing = (-0.8).sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = dateStr, fontSize = 12.sp, color = TextMuted)
            if (showTodayPill) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x208B5CF6))
                        .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onTodayClick,
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(text = "Today", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Violet400)
                }
            }
        }
    }
}
```

Add the missing import at the top of `FoodScreen.kt` (if not already present):
```kotlin
import com.zack.recomptracker.ui.component.WeekCalorieStrip
import com.zack.recomptracker.ui.today.FoodLogUiState
import com.zack.recomptracker.ui.today.FoodLogViewModel
```

Remove the now-unused import:
```kotlin
// Remove: import com.zack.recomptracker.ui.today.TodayUiState  (if it was there)
```

Also update the `NutritionStrip` function signature — it currently takes `TodayUiState`. Change it to take `FoodLogUiState`:

```kotlin
@Composable
private fun NutritionStrip(state: FoodLogUiState) {
    // body is unchanged
}
```

- [ ] **Step 4.4 — Verify compilation**

```bash
cd app && ../gradlew assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -15
```
Expected: `BUILD SUCCESSFUL`. Fix any import errors that arise.

- [ ] **Step 4.5 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
git commit -m "feat(food-history): update FoodScreen to use FoodLogViewModel + add WeekCalorieStrip"
```

---

## Task 5: Thread `logDate` through `FoodLibraryViewModel` and `FoodLibraryScreen`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 5.1 — Add `logDate` field and update `init()` in `FoodLibraryViewModel`**

Add a private mutable field after the `initialized` var:

```kotlin
private var initialized = false
private var logDate: LocalDate = dateProvider.today()
```

Update the `init()` signature and first lines to capture the date before the `initialized` guard:

```kotlin
fun init(slotId: Long?, slotName: String, editEntryId: Long? = null, logDateStr: String = "") {
    logDate = if (logDateStr.isNotEmpty()) LocalDate.parse(logDateStr) else dateProvider.today()
    _uiState.update { it.copy(slotId = slotId, slotName = slotName.ifBlank { "Food Log" }) }
    // ... rest of init unchanged
```

Replace all four occurrences of `dateProvider.today()` used as a *write* date (in `confirmAmount`, `logMeal`, `confirmQuickAdd`, `confirmSaveMeal`) with `logDate`. For example:

In `confirmAmount` (two spots inside the `launch` block):
```kotlin
// was: date = dateProvider.today()
date = logDate,
```

In `logMeal`:
```kotlin
// was: date = dateProvider.today()
date = logDate,
```

In `confirmQuickAdd`:
```kotlin
// was: date = dateProvider.today()
date = logDate,
```

In `confirmSaveMeal` (reading entries for the slot):
```kotlin
// was: date = dateProvider.today().toString()
date = logDate.toString(),
```

The `val today = dateProvider.today()` line inside `init()` (used for `logRepository.observeDay(today)` for remaining-calories) stays as-is — that read-only observation always uses today's date.

- [ ] **Step 5.2 — Update `FoodLibraryScreen` to accept and forward `logDate`**

In `FoodLibraryScreen.kt`, add `logDate: String = ""` to the composable's parameter list (after `editEntryId`):

```kotlin
fun FoodLibraryScreen(
    viewModel: FoodLibraryViewModel,
    slotId: Long?,
    slotName: String,
    onBack: () -> Unit,
    editEntryId: Long? = null,
    logDate: String = "",
    onScanBarcode: () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.init(slotId, slotName, editEntryId, logDate) }
```

- [ ] **Step 5.3 — Verify compilation**

```bash
cd app && ../gradlew assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5.4 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt \
        app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
git commit -m "feat(food-history): thread logDate through FoodLibraryViewModel so past-day adds land on correct date"
```

---

## Task 6: Wire navigation in `AppNavGraph`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

- [ ] **Step 6.1 — Update `Routes` object and `AppNavGraph`**

In `AppNavGraph.kt`, make the following changes:

**Add `FoodLogViewModel` import** (at the top with other UI imports):
```kotlin
import com.zack.recomptracker.ui.today.FoodLogViewModel
```

**Update `Routes.FoodLibrary`** in the `Routes` object — add `date` support:
```kotlin
// Replace:
const val FoodLibrary = "food_library"

// With (no change to the const — the full route string is built inline in composable()):
const val FoodLibrary = "food_library"
// (the date param is optional and added at navigation time, not in the const)
```

**Update the `composable(Routes.Food)` destination** — swap `TodayViewModel` for `FoodLogViewModel` and thread the selected date through the navigation callbacks:

```kotlin
composable(Routes.Food) {
    val viewModel = viewModel<FoodLogViewModel>(factory = factory)
    FoodScreen(
        viewModel = viewModel,
        onAddToSlot = { slotId, slotName, date ->
            navController.navigate(
                "${Routes.FoodLibrary}?slotId=$slotId&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}&date=$date"
            )
        },
        onBrowseLibrary = { navController.navigate(Routes.FoodLibrary) },
        onEditEntryAmount = { slotId, slotName, entryId, date ->
            navController.navigate(
                "${Routes.FoodLibrary}?slotId=${slotId ?: -1L}&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}&editEntryId=$entryId&date=$date"
            )
        },
    )
}
```

**Update the `FoodLibrary` composable destination** — add the optional `date` nav argument and forward it to `FoodLibraryScreen`:

```kotlin
composable(
    route = "${Routes.FoodLibrary}?slotId={slotId}&slotName={slotName}&editEntryId={editEntryId}&date={date}",
    arguments = listOf(
        androidx.navigation.navArgument("slotId") {
            type = androidx.navigation.NavType.LongType; defaultValue = -1L
        },
        androidx.navigation.navArgument("slotName") {
            type = androidx.navigation.NavType.StringType; defaultValue = ""
        },
        androidx.navigation.navArgument("editEntryId") {
            type = androidx.navigation.NavType.LongType; defaultValue = -1L
        },
        androidx.navigation.navArgument("date") {
            type = androidx.navigation.NavType.StringType; defaultValue = ""
        },
    ),
) { backStackEntry ->
    val slotId = backStackEntry.arguments?.getLong("slotId")?.takeIf { it != -1L }
    val slotName = java.net.URLDecoder.decode(
        backStackEntry.arguments?.getString("slotName").orEmpty(), "UTF-8"
    )
    val editEntryId = backStackEntry.arguments?.getLong("editEntryId")?.takeIf { it != -1L }
    val logDate = backStackEntry.arguments?.getString("date").orEmpty()
    FoodLibraryScreen(
        viewModel    = viewModel<FoodLibraryViewModel>(factory = factory),
        slotId       = slotId,
        slotName     = slotName,
        onBack       = { navController.popBackStack() },
        editEntryId  = editEntryId,
        logDate      = logDate,
        onScanBarcode = {
            navController.navigate(Routes.barcodeScanner(slotId, slotName))
        },
    )
}
```

- [ ] **Step 6.2 — Verify compilation**

```bash
cd app && ../gradlew assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -15
```
Expected: `BUILD SUCCESSFUL`. If there are `Unresolved reference` errors for removed `TodayViewModel` usage in `Routes.Food`, fix by confirming the import change.

- [ ] **Step 6.3 — Run the full test suite**

```bash
cd app && ../gradlew testDebugUnitTest 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, all existing tests pass.

- [ ] **Step 6.4 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(food-history): wire FoodLogViewModel into nav graph + pass date to FoodLibrary"
```

---

## Task 7: Final build verification

- [ ] **Step 7.1 — Clean build**

```bash
cd app && ../gradlew assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7.2 — Full unit test run**

```bash
cd app && ../gradlew testDebugUnitTest 2>&1 | grep -E "tests|failures|errors|BUILD" | tail -10
```
Expected: all tests pass, no failures.

- [ ] **Step 7.3 — Install and smoke-test on device/emulator**

```bash
cd app && ../gradlew installDebug 2>&1 | tail -5
```

Manual checks:
1. Open **Food Log** — week strip appears with today highlighted.
2. Tap a past day — header date updates, nutrition strip and meal slots reflect that day.
3. Tap **Today** pill — returns to today instantly.
4. On a past day, tap **＋ Add** on a slot → `FoodLibraryScreen` opens, log a food → returns to FoodScreen, food appears in the past day's slot.
5. On the same past day, edit and delete an existing entry — both work.
6. Navigate to **Body** tab — `BodyRecoveryScreen` still works normally (no regression).
