# Body & Recovery Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a history screen with missing-day indicators, backdating support, a belly skinfold caliper field, and a 7-day trend card to the Body & Recovery section.

**Architecture:** New `BodyHistoryViewModel` and `BodyEditViewModel` with dedicated screens; `TodayViewModel` untouched except for two new trend-delta fields; shared `BodyCheckInFormContent` composable reused by both today's form and the edit screen. Database bumped to version 5 with one new nullable column.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Kotlin Coroutines/Flow, Compose Navigation, `kotlinx.coroutines.flow.first`, Material3

---

## File Map

| File | Action |
|------|--------|
| `data/local/entity/DailyLogEntity.kt` | Modify — add `waistSkinfoldMm` |
| `data/repository/LogModels.kt` | Modify — add `waistSkinfoldMm` to `DailyMetricsInput` |
| `data/repository/LogRepository.kt` | Modify — pass `waistSkinfoldMm` in `saveDailyMetrics` |
| `data/local/RecompDatabase.kt` | Modify — version 4→5, `MIGRATION_4_5` |
| `ui/body/BodyCheckInForm.kt` | **Create** — shared `BodyCheckInFormState`, `BodyCheckInFormActions`, `BodyCheckInFormContent` composable |
| `ui/body/BodyHistoryViewModel.kt` | **Create** — history list logic |
| `ui/body/BodyHistoryScreen.kt` | **Create** — history list UI |
| `ui/body/BodyEditViewModel.kt` | **Create** — single-day edit logic |
| `ui/body/BodyEditScreen.kt` | **Create** — edit form UI |
| `ui/today/TodayViewModel.kt` | Modify — add `waistSkinfoldMm`, `weightChange7d`, `waistChange7d` |
| `ui/today/BodyRecoveryScreen.kt` | Modify — add caliper field, trend card, View History button, use `BodyCheckInFormContent` |
| `ui/navigation/AppNavGraph.kt` | Modify — add `BodyHistory` + `BodyEdit` routes, update Body tab |
| `core/AppContainer.kt` | Modify — register `BodyHistoryViewModel` and `BodyEditViewModel` |
| `test/.../body/BodyHistoryViewModelTest.kt` | **Create** — unit tests |
| `test/.../body/BodyEditViewModelTest.kt` | **Create** — unit tests |

---

## Task 1: Data layer — waistSkinfoldMm field + DB migration

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/entity/DailyLogEntity.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/LogModels.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt:56-71`
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt`

- [ ] **Step 1.1 — Add field to DailyLogEntity**

Replace the entire file content of `DailyLogEntity.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "daily_logs")
data class DailyLogEntity(
    @PrimaryKey val date: String,
    val bodyWeightKg: Double? = null,
    val waistCm: Double? = null,
    val waistSkinfoldMm: Double? = null,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val energyScore: Int? = null,
    val hungerScore: Int? = null,
    val sorenessScore: Int? = null,
    val trained: Boolean = false,
    val notes: String = "",
)
```

- [ ] **Step 1.2 — Add field to DailyMetricsInput**

In `LogModels.kt`, replace `DailyMetricsInput`:

```kotlin
data class DailyMetricsInput(
    val date: LocalDate,
    val bodyWeightKg: Double?,
    val waistCm: Double?,
    val waistSkinfoldMm: Double? = null,
    val steps: Int?,
    val sleepHours: Double?,
    val energyScore: Int?,
    val hungerScore: Int?,
    val sorenessScore: Int?,
    val trained: Boolean,
    val notes: String,
)
```

- [ ] **Step 1.3 — Pass waistSkinfoldMm through LogRepository.saveDailyMetrics**

In `LogRepository.kt`, replace the body of `saveDailyMetrics`:

```kotlin
suspend fun saveDailyMetrics(input: DailyMetricsInput) {
    dailyLogDao.upsert(
        DailyLogEntity(
            date = input.date.toString(),
            bodyWeightKg = input.bodyWeightKg,
            waistCm = input.waistCm,
            waistSkinfoldMm = input.waistSkinfoldMm,
            steps = input.steps,
            sleepHours = input.sleepHours,
            energyScore = input.energyScore?.coerceIn(1, 10),
            hungerScore = input.hungerScore?.coerceIn(1, 10),
            sorenessScore = input.sorenessScore?.coerceIn(1, 10),
            trained = input.trained,
            notes = input.notes.trim(),
        ),
    )
}
```

- [ ] **Step 1.4 — Add MIGRATION_4_5 to RecompDatabase**

In `RecompDatabase.kt`:

1. Change `version = 4` to `version = 5` in the `@Database` annotation.

2. Add this constant inside `companion object`, after `MIGRATION_3_4`:

```kotlin
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_logs ADD COLUMN waistSkinfoldMm REAL")
    }
}
```

3. In `create()`, add `MIGRATION_4_5` to `addMigrations`:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

- [ ] **Step 1.5 — Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL with no compilation errors.

- [ ] **Step 1.6 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/local/entity/DailyLogEntity.kt \
        app/src/main/java/com/zack/recomptracker/data/repository/LogModels.kt \
        app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt \
        app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt
git commit -m "feat: add waistSkinfoldMm field to daily log, db migration 4→5"
```

---

## Task 2: Shared BodyCheckInFormContent composable

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/body/BodyCheckInForm.kt`

This composable is used by both `BodyRecoveryContent` (today) and `BodyEditScreen` (past dates).

- [ ] **Step 2.1 — Create the file**

```kotlin
package com.zack.recomptracker.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.NumberField
import com.zack.recomptracker.ui.component.ScoreSlider
import com.zack.recomptracker.ui.component.ToggleRow
import java.time.LocalDate

data class BodyCheckInFormState(
    val date: LocalDate,
    val bodyWeightKg: String = "",
    val waistCm: String = "",
    val waistSkinfoldMm: String = "",
    val steps: String = "",
    val sleepHours: String = "",
    val energyScore: Int = 5,
    val hungerScore: Int = 5,
    val sorenessScore: Int = 5,
    val trained: Boolean = false,
    val notes: String = "",
    val message: String? = null,
)

data class BodyCheckInFormActions(
    val onBodyWeightChanged: (String) -> Unit,
    val onWaistChanged: (String) -> Unit,
    val onWaistSkinfoldChanged: (String) -> Unit,
    val onStepsChanged: (String) -> Unit,
    val onSleepChanged: (String) -> Unit,
    val onEnergyChanged: (Int) -> Unit,
    val onHungerChanged: (Int) -> Unit,
    val onSorenessChanged: (Int) -> Unit,
    val onTrainedChanged: (Boolean) -> Unit,
    val onNotesChanged: (String) -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun BodyCheckInFormContent(
    state: BodyCheckInFormState,
    actions: BodyCheckInFormActions,
    saveLabel: String,
    modifier: Modifier = Modifier,
) {
    MessageText(state.message)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NumberField("Weight", state.bodyWeightKg, actions.onBodyWeightChanged, Modifier.weight(1f), "kg")
        NumberField("Waist", state.waistCm, actions.onWaistChanged, Modifier.weight(1f), "cm")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NumberField("Belly skinfold", state.waistSkinfoldMm, actions.onWaistSkinfoldChanged, Modifier.weight(1f), "mm")
        NumberField("Sleep", state.sleepHours, actions.onSleepChanged, Modifier.weight(1f), "h")
    }
    NumberField("Steps", state.steps, actions.onStepsChanged, Modifier.fillMaxWidth())
    ScoreSlider("Energy", state.energyScore, actions.onEnergyChanged)
    ScoreSlider("Hunger", state.hungerScore, actions.onHungerChanged)
    ScoreSlider("Soreness", state.sorenessScore, actions.onSorenessChanged)
    ToggleRow("Training day", state.trained, actions.onTrainedChanged)
    OutlinedTextField(
        value = state.notes,
        onValueChange = actions.onNotesChanged,
        label = { Text("Notes") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = actions.onSave, modifier = Modifier.fillMaxWidth()) {
        Text(saveLabel)
    }
}
```

- [ ] **Step 2.2 — Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.3 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/body/BodyCheckInForm.kt
git commit -m "feat: add shared BodyCheckInFormContent composable with caliper field"
```

---

## Task 3: Update TodayViewModel + BodyRecoveryScreen (caliper, trend card, history button)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt`

- [ ] **Step 3.1 — Add waistSkinfoldMm + trend fields to TodayUiState**

In `TodayViewModel.kt`, update `TodayUiState` to add three fields after `waistCm`:

```kotlin
data class TodayUiState(
    val date: LocalDate,
    val target: PlanPreferences = PlanPreferences(),
    val totals: MacroTotals = MacroTotals(),
    val slots: List<MealSlotWithEntries> = emptyList(),
    val unslottedEntries: List<MealEntryEntity> = emptyList(),
    val slotsEditMode: Boolean = false,
    val bodyWeightKg: String = "",
    val waistCm: String = "",
    val waistSkinfoldMm: String = "",
    val steps: String = "",
    val sleepHours: String = "",
    val energyScore: Int = 5,
    val hungerScore: Int = 5,
    val sorenessScore: Int = 5,
    val trained: Boolean = false,
    val notes: String = "",
    val metricsDirty: Boolean = false,
    val message: String? = null,
    val weightChange7d: Float? = null,
    val waistChange7d: Float? = null,
)
```

- [ ] **Step 3.2 — Add waistSkinfoldMm handler + update init + saveMetrics in TodayViewModel**

Add the following import at the top of `TodayViewModel.kt`:

```kotlin
import java.time.LocalDate
```

(It may already be present — skip if so.)

Add this method after `onWaistChanged`:

```kotlin
fun onWaistSkinfoldChanged(v: String) = editMetrics { copy(waistSkinfoldMm = v) }
```

In the `init` block, inside the `if (!current.metricsDirty && log != null)` branch, add `waistSkinfoldMm` loading alongside the other fields:

```kotlin
val metrics = if (!current.metricsDirty && log != null) {
    current.copy(
        bodyWeightKg = log.bodyWeightKg?.toString().orEmpty(),
        waistCm = log.waistCm?.toString().orEmpty(),
        waistSkinfoldMm = log.waistSkinfoldMm?.toString().orEmpty(),
        steps = log.steps?.toString().orEmpty(),
        sleepHours = log.sleepHours?.toString().orEmpty(),
        energyScore = log.energyScore ?: 5,
        hungerScore = log.hungerScore ?: 5,
        sorenessScore = log.sorenessScore ?: 5,
        trained = log.trained,
        notes = log.notes,
    )
} else current
```

In `saveMetrics()`, add `waistSkinfoldMm` to the `DailyMetricsInput` call:

```kotlin
logRepository.saveDailyMetrics(
    DailyMetricsInput(
        date = s.date,
        bodyWeightKg = s.bodyWeightKg.toNullableDouble(),
        waistCm = s.waistCm.toNullableDouble(),
        waistSkinfoldMm = s.waistSkinfoldMm.toNullableDouble(),
        steps = steps,
        sleepHours = s.sleepHours.toNullableDouble(),
        energyScore = s.energyScore,
        hungerScore = s.hungerScore,
        sorenessScore = s.sorenessScore,
        trained = s.trained,
        notes = s.notes,
    ),
)
```

- [ ] **Step 3.3 — Add 7-day trend computation to TodayViewModel.init**

Add a second `viewModelScope.launch` block at the end of `init`, after the HealthConnect block:

```kotlin
viewModelScope.launch {
    logRepository.observeDailyLogs().collect { allLogs ->
        val cutoff = today.minusDays(14)
        val recent = allLogs
            .filter { LocalDate.parse(it.date) >= cutoff }
            .sortedByDescending { LocalDate.parse(it.date) }

        val latestWeight = recent.firstNotNullOfOrNull { it.bodyWeightKg }
        val weight7dAgo = recent
            .filter { LocalDate.parse(it.date) <= today.minusDays(6) }
            .firstNotNullOfOrNull { it.bodyWeightKg }

        val latestWaist = recent.firstNotNullOfOrNull { it.waistCm }
        val waist7dAgo = recent
            .filter { LocalDate.parse(it.date) <= today.minusDays(6) }
            .firstNotNullOfOrNull { it.waistCm }

        _uiState.update {
            it.copy(
                weightChange7d = if (latestWeight != null && weight7dAgo != null)
                    (latestWeight - weight7dAgo).toFloat() else null,
                waistChange7d = if (latestWaist != null && waist7dAgo != null)
                    (latestWaist - waist7dAgo).toFloat() else null,
            )
        }
    }
}
```

- [ ] **Step 3.4 — Rewrite BodyRecoveryScreen.kt**

Replace the entire file:

```kotlin
package com.zack.recomptracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.body.BodyCheckInFormActions
import com.zack.recomptracker.ui.body.BodyCheckInFormContent
import com.zack.recomptracker.ui.body.BodyCheckInFormState
import com.zack.recomptracker.ui.component.SectionCard

@Composable
fun BodyRecoveryScreen(
    viewModel: TodayViewModel,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BodyRecoveryContent(
        state = state,
        onViewHistory = onViewHistory,
        actions = BodyCheckInFormActions(
            onBodyWeightChanged = viewModel::onBodyWeightChanged,
            onWaistChanged = viewModel::onWaistChanged,
            onWaistSkinfoldChanged = viewModel::onWaistSkinfoldChanged,
            onStepsChanged = viewModel::onStepsChanged,
            onSleepChanged = viewModel::onSleepChanged,
            onEnergyChanged = viewModel::onEnergyChanged,
            onHungerChanged = viewModel::onHungerChanged,
            onSorenessChanged = viewModel::onSorenessChanged,
            onTrainedChanged = viewModel::onTrainedChanged,
            onNotesChanged = viewModel::onNotesChanged,
            onSave = viewModel::saveMetrics,
        ),
        modifier = modifier,
    )
}

@Composable
fun BodyRecoveryContent(
    state: TodayUiState,
    actions: BodyCheckInFormActions,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formState = BodyCheckInFormState(
        date = state.date,
        bodyWeightKg = state.bodyWeightKg,
        waistCm = state.waistCm,
        waistSkinfoldMm = state.waistSkinfoldMm,
        steps = state.steps,
        sleepHours = state.sleepHours,
        energyScore = state.energyScore,
        hungerScore = state.hungerScore,
        sorenessScore = state.sorenessScore,
        trained = state.trained,
        notes = state.notes,
        message = state.message,
    )
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Body & Recovery",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Daily check-in · ${state.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.weightChange7d != null || state.waistChange7d != null) {
            item { TrendCard(state.weightChange7d, state.waistChange7d) }
        }
        item {
            SectionCard("Daily check-in") {
                BodyCheckInFormContent(
                    state = formState,
                    actions = actions,
                    saveLabel = "Save daily check-in",
                )
            }
        }
        item {
            OutlinedButton(onClick = onViewHistory, modifier = Modifier.fillMaxWidth()) {
                Text("View History")
            }
        }
    }
}

@Composable
private fun TrendCard(weightChange7d: Float?, waistChange7d: Float?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (weightChange7d != null) {
                    TrendChip(
                        value = weightChange7d,
                        unit = "kg",
                        label = "weight this week",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (waistChange7d != null) {
                    TrendChip(
                        value = waistChange7d,
                        unit = "cm",
                        label = "waist this week",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendChip(value: Float, unit: String, label: String, modifier: Modifier = Modifier) {
    val isDown = value < 0f
    val color = if (isDown) androidx.compose.ui.graphics.Color(0xFF34d399) else MaterialTheme.colorScheme.error
    val sign = if (value > 0f) "+" else ""
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "$sign${"%.1f".format(value)} $unit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

- [ ] **Step 3.5 — Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.6 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt \
        app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt
git commit -m "feat: add caliper field, 7-day trend card, and View History button to body tab"
```

---

## Task 4: BodyHistoryViewModel + unit test

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/body/BodyHistoryViewModel.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ui/body/BodyHistoryViewModelTest.kt`

- [ ] **Step 4.1 — Write the failing tests**

Create `app/src/test/java/com/zack/recomptracker/ui/body/BodyHistoryViewModelTest.kt`:

```kotlin
package com.zack.recomptracker.ui.body

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyHistoryViewModelTest {

    private val today = LocalDate.of(2026, 5, 31)

    private fun buildVm(
        logs: List<DailyLogEntity>,
        todayDate: LocalDate = today,
    ): BodyHistoryViewModel {
        val logsFlow = MutableStateFlow(logs)
        val repo = object : FakeLogRepository() {
            override fun observeDailyLogs(): Flow<List<DailyLogEntity>> = logsFlow
        }
        val dateProvider = object : DateProvider { override fun today() = todayDate }
        return BodyHistoryViewModel(repo, dateProvider)
    }

    @Test
    fun `logged entry appears as Logged item`() = runTest {
        val log = DailyLogEntity(date = today.toString(), bodyWeightKg = 82.4)
        val vm = buildVm(listOf(log))
        val items = vm.items.first()
        val first = items.first()
        assertTrue(first is BodyHistoryItem.Logged)
        assertEquals(today, (first as BodyHistoryItem.Logged).date)
    }

    @Test
    fun `missing day appears as Missing item`() = runTest {
        val vm = buildVm(emptyList())
        val items = vm.items.first()
        assertTrue(items.all { it is BodyHistoryItem.Missing })
        assertEquals(today, (items.first() as BodyHistoryItem.Missing).date)
    }

    @Test
    fun `items are sorted newest first`() = runTest {
        val logs = listOf(
            DailyLogEntity(date = today.minusDays(2).toString()),
            DailyLogEntity(date = today.toString()),
        )
        val vm = buildVm(logs)
        val items = vm.items.first()
        assertEquals(today, items.first().date())
        assertTrue(items[1].date() < items[0].date())
    }

    @Test
    fun `includes entry dates earlier than 90 days`() = runTest {
        val old = today.minusDays(120)
        val logs = listOf(DailyLogEntity(date = old.toString()))
        val vm = buildVm(logs)
        val items = vm.items.first()
        assertTrue(items.any { it.date() == old })
    }

    private fun BodyHistoryItem.date() = when (this) {
        is BodyHistoryItem.Logged -> date
        is BodyHistoryItem.Missing -> date
    }
}

// Minimal fake — only observeDailyLogs matters; everything else throws
private abstract class FakeLogRepository : LogRepository(
    dailyLogDao = TODO_DAO, mealEntryDao = TODO_DAO2, savedFoodDao = TODO_DAO3,
    savedMealDao = TODO_DAO4, performanceDao = TODO_DAO5, weeklyReviewDao = TODO_DAO6,
    mealSlotDao = TODO_DAO7,
)
```

Wait — `LogRepository` is a concrete class, not an interface, so we cannot extend it easily in tests. Use a wrapper interface pattern instead. Define a minimal interface `LogDataSource` or just use a lambda-based fake as in the existing tests.

Replace the test file content:

```kotlin
package com.zack.recomptracker.ui.body

import com.zack.recomptracker.core.time.DateProvider
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyHistoryViewModelTest {

    private val today = LocalDate.of(2026, 5, 31)

    private fun buildVm(logs: List<DailyLogEntity>, todayDate: LocalDate = today): BodyHistoryViewModel {
        val logsFlow = MutableStateFlow(logs)
        val repo = LogRepository(
            dailyLogDao = object : NoopDailyLogDao() {
                override fun observeAll(): Flow<List<DailyLogEntity>> = logsFlow
            },
            mealEntryDao = NoopMealEntryDao,
            savedFoodDao = NoopSavedFoodDao,
            savedMealDao = NoopSavedMealDao,
            performanceDao = NoopPerformanceDao,
            weeklyReviewDao = NoopWeeklyReviewDao,
            mealSlotDao = NoopMealSlotDao,
        )
        val dateProvider = object : DateProvider { override fun today() = todayDate }
        return BodyHistoryViewModel(repo, dateProvider)
    }

    @Test
    fun `logged entry appears as Logged item`() = runTest {
        val log = DailyLogEntity(date = today.toString(), bodyWeightKg = 82.4)
        val vm = buildVm(listOf(log))
        val first = vm.items.first().first()
        assertTrue(first is BodyHistoryItem.Logged)
        assertEquals(today, (first as BodyHistoryItem.Logged).date)
    }

    @Test
    fun `missing day appears as Missing item`() = runTest {
        val vm = buildVm(emptyList())
        val first = vm.items.first().first()
        assertTrue(first is BodyHistoryItem.Missing)
        assertEquals(today, (first as BodyHistoryItem.Missing).date)
    }

    @Test
    fun `items are sorted newest first`() = runTest {
        val logs = listOf(
            DailyLogEntity(date = today.minusDays(2).toString()),
            DailyLogEntity(date = today.toString()),
        )
        val items = buildVm(logs).items.first()
        assertEquals(today, items.first().itemDate())
        assertTrue(items[1].itemDate() < items[0].itemDate())
    }

    @Test
    fun `includes entry earlier than 90 days`() = runTest {
        val old = today.minusDays(120)
        val items = buildVm(listOf(DailyLogEntity(date = old.toString()))).items.first()
        assertTrue(items.any { it.itemDate() == old })
    }

    private fun BodyHistoryItem.itemDate() = when (this) {
        is BodyHistoryItem.Logged -> date
        is BodyHistoryItem.Missing -> date
    }
}

// ── Noop stubs (copy pattern from LogRepositorySyncTest) ────────────────────

private abstract class NoopDailyLogDao : DailyLogDao {
    override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow { emit(null) }
    override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun getByDate(date: String): DailyLogEntity? = null
    override suspend fun getAll(): List<DailyLogEntity> = emptyList()
    override fun observeBetween(s: String, e: String): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun upsert(log: DailyLogEntity) = Unit
    override suspend fun insertAll(logs: List<DailyLogEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopMealEntryDao : MealEntryDao {
    override fun observeForDate(date: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getForDate(date: String): List<MealEntryEntity> = emptyList()
    override fun observeBetween(s: String, e: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override fun observeAll(): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override fun observeFoodLibraryEntries(): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<MealEntryEntity> = emptyList()
    override suspend fun insert(entry: MealEntryEntity): Long = 0L
    override suspend fun insertAll(entries: List<MealEntryEntity>) = Unit
    override suspend fun update(entry: MealEntryEntity) = Unit
    override suspend fun delete(entry: MealEntryEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteForDate(date: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun clearSlotId(slotId: Long) = Unit
    override suspend fun getById(id: Long): MealEntryEntity? = null
}

private object NoopSavedFoodDao : SavedFoodDao {
    override fun observeAll(): Flow<List<SavedFoodEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedFoodEntity> = emptyList()
    override suspend fun insert(food: SavedFoodEntity): Long = 0L
    override suspend fun insertAll(foods: List<SavedFoodEntity>) = Unit
    override suspend fun update(food: SavedFoodEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopSavedMealDao : SavedMealDao {
    override fun observeAll(): Flow<List<SavedMealEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedMealEntity> = emptyList()
    override suspend fun insert(meal: SavedMealEntity): Long = 0L
    override suspend fun insertAll(meals: List<SavedMealEntity>) = Unit
    override suspend fun update(meal: SavedMealEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopPerformanceDao : PerformanceDao {
    override fun observeAll(): Flow<List<LiftPerformanceEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<LiftPerformanceEntity> = emptyList()
    override suspend fun insert(p: LiftPerformanceEntity): Long = 0L
    override suspend fun insertAll(ps: List<LiftPerformanceEntity>) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopWeeklyReviewDao : WeeklyReviewDao {
    override fun observeAll(): Flow<List<WeeklyReviewEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<WeeklyReviewEntity> = emptyList()
    override suspend fun upsert(review: WeeklyReviewEntity) = Unit
    override suspend fun insertAll(reviews: List<WeeklyReviewEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopMealSlotDao : MealSlotDao {
    override fun observeAll(): Flow<List<MealSlotEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<MealSlotEntity> = emptyList()
    override suspend fun insert(slot: MealSlotEntity): Long = 0L
    override suspend fun update(slot: MealSlotEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun updateSortOrder(id: Long, order: Int) = Unit
}
```

- [ ] **Step 4.2 — Run tests to confirm they fail**

```bash
./gradlew test --tests "com.zack.recomptracker.ui.body.BodyHistoryViewModelTest" 2>&1 | tail -20
```

Expected: compilation error — `BodyHistoryViewModel` and `BodyHistoryItem` not found.

- [ ] **Step 4.3 — Create BodyHistoryViewModel**

Create `app/src/main/java/com/zack/recomptracker/ui/body/BodyHistoryViewModel.kt`:

```kotlin
package com.zack.recomptracker.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed class BodyHistoryItem {
    data class Logged(val date: LocalDate, val entity: DailyLogEntity) : BodyHistoryItem()
    data class Missing(val date: LocalDate) : BodyHistoryItem()
}

class BodyHistoryViewModel(
    logRepository: LogRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    val items: StateFlow<List<BodyHistoryItem>> = logRepository.observeDailyLogs()
        .map { logs -> buildItems(logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildItems(logs: List<DailyLogEntity>): List<BodyHistoryItem> {
        val today = dateProvider.today()
        val logsByDate = logs.associateBy { LocalDate.parse(it.date) }
        val earliest = logs.minOfOrNull { LocalDate.parse(it.date) }
        val start = listOfNotNull(today.minusDays(89), earliest).min()
        val dayCount = ChronoUnit.DAYS.between(start, today)
        return (0..dayCount).map { offset ->
            val date = today.minusDays(offset)
            val entity = logsByDate[date]
            if (entity != null) BodyHistoryItem.Logged(date, entity)
            else BodyHistoryItem.Missing(date)
        }
    }
}
```

- [ ] **Step 4.4 — Run tests to confirm they pass**

```bash
./gradlew test --tests "com.zack.recomptracker.ui.body.BodyHistoryViewModelTest" 2>&1 | tail -20
```

Expected: 4 tests PASSED.

- [ ] **Step 4.5 — Register BodyHistoryViewModel in AppContainer**

In `AppContainer.kt`, add import:

```kotlin
import com.zack.recomptracker.ui.body.BodyHistoryViewModel
```

In `AppViewModelFactory.create`, add before the `else -> error(...)` line:

```kotlin
BodyHistoryViewModel::class.java -> BodyHistoryViewModel(
    logRepository = container.logRepository,
    dateProvider = container.dateProvider,
)
```

- [ ] **Step 4.6 — Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.7 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/body/BodyHistoryViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/body/BodyHistoryViewModelTest.kt \
        app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat: add BodyHistoryViewModel with missing-day logic"
```

---

## Task 5: BodyHistoryScreen

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/body/BodyHistoryScreen.kt`

- [ ] **Step 5.1 — Create BodyHistoryScreen**

```kotlin
package com.zack.recomptracker.ui.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyHistoryScreen(
    viewModel: BodyHistoryViewModel,
    onEditDay: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check-in History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(items) { item ->
                when (item) {
                    is BodyHistoryItem.Logged -> LoggedRow(item, onEditDay)
                    is BodyHistoryItem.Missing -> MissingRow(item, onEditDay)
                }
            }
            item {
                Text(
                    "Showing up to 90 days · Tap any row to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .wrapContentWidth(),
                )
            }
        }
    }
}

@Composable
private fun LoggedRow(item: BodyHistoryItem.Logged, onEdit: (LocalDate) -> Unit) {
    val e: DailyLogEntity = item.entity
    val summary = buildString {
        e.bodyWeightKg?.let { append("$it kg") }
        e.waistCm?.let { if (isNotEmpty()) append(" · "); append("$it cm") }
        e.waistSkinfoldMm?.let { if (isNotEmpty()) append(" · "); append("$it mm") }
    }
    val detail = buildString {
        e.energyScore?.let { append("⚡$it") }
        e.sleepHours?.let { if (isNotEmpty()) append(" · "); append("😴${it}h") }
        if (e.trained) { if (isNotEmpty()) append(" · "); append("🏋️ trained") }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(item.date) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.date.format(DATE_FMT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun MissingRow(item: BodyHistoryItem.Missing, onAdd: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd(item.date) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.date.format(DATE_FMT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text("no entry", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "+ Add",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
```

- [ ] **Step 5.2 — Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.3 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/body/BodyHistoryScreen.kt
git commit -m "feat: add BodyHistoryScreen with logged/missing row layout"
```

---

## Task 6: BodyEditViewModel + unit test

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/body/BodyEditViewModel.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ui/body/BodyEditViewModelTest.kt`

- [ ] **Step 6.1 — Write the failing tests**

Create `app/src/test/java/com/zack/recomptracker/ui/body/BodyEditViewModelTest.kt`:

```kotlin
package com.zack.recomptracker.ui.body

import androidx.lifecycle.SavedStateHandle
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
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BodyEditViewModelTest {

    private val date = LocalDate.of(2026, 5, 29)

    private fun buildVm(
        existing: DailyLogEntity? = null,
        upserted: MutableList<DailyLogEntity> = mutableListOf(),
    ): BodyEditViewModel {
        val logsFlow = MutableStateFlow(listOfNotNull(existing))
        val repo = LogRepository(
            dailyLogDao = object : NoopDailyLogDao() {
                override fun observeByDate(date: String): Flow<DailyLogEntity?> =
                    flow { emit(logsFlow.value.firstOrNull { it.date == date }) }
                override suspend fun upsert(log: DailyLogEntity) { upserted.add(log) }
            },
            mealEntryDao = NoopMealEntryDao,
            savedFoodDao = NoopSavedFoodDao,
            savedMealDao = NoopSavedMealDao,
            performanceDao = NoopPerformanceDao,
            weeklyReviewDao = NoopWeeklyReviewDao,
            mealSlotDao = NoopMealSlotDao,
        )
        return BodyEditViewModel(repo, SavedStateHandle(mapOf("date" to date.toString())))
    }

    @Test
    fun `initial state has correct date`() = runTest {
        val vm = buildVm()
        assertEquals(date, vm.uiState.first().date)
    }

    @Test
    fun `prefills state from existing log`() = runTest {
        val log = DailyLogEntity(date = date.toString(), bodyWeightKg = 82.4, waistSkinfoldMm = 18.0)
        val vm = buildVm(existing = log)
        // wait for init coroutine
        val state = vm.uiState.first { it.bodyWeightKg.isNotEmpty() }
        assertEquals("82.4", state.bodyWeightKg)
        assertEquals("18.0", state.waistSkinfoldMm)
    }

    @Test
    fun `saveMetrics writes DailyMetricsInput with correct date`() = runTest {
        val upserted = mutableListOf<DailyLogEntity>()
        val vm = buildVm(upserted = upserted)
        vm.onBodyWeightChanged("83.0")
        vm.saveMetrics()
        // wait for save coroutine
        vm.saved.first()
        assertEquals(1, upserted.size)
        assertEquals(date.toString(), upserted[0].date)
        assertEquals(83.0, upserted[0].bodyWeightKg)
    }

    @Test
    fun `saveMetrics emits saved event`() = runTest {
        val vm = buildVm()
        vm.saveMetrics()
        assertNotNull(vm.saved.first())
    }
}

// ── Noop stubs ───────────────────────────────────────────────────────────────

private abstract class NoopDailyLogDao : DailyLogDao {
    override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow { emit(null) }
    override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun getByDate(date: String): DailyLogEntity? = null
    override suspend fun getAll(): List<DailyLogEntity> = emptyList()
    override fun observeBetween(s: String, e: String): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun upsert(log: DailyLogEntity) = Unit
    override suspend fun insertAll(logs: List<DailyLogEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopMealEntryDao : MealEntryDao {
    override fun observeForDate(date: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getForDate(date: String): List<MealEntryEntity> = emptyList()
    override fun observeBetween(s: String, e: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override fun observeAll(): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override fun observeFoodLibraryEntries(): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<MealEntryEntity> = emptyList()
    override suspend fun insert(entry: MealEntryEntity): Long = 0L
    override suspend fun insertAll(entries: List<MealEntryEntity>) = Unit
    override suspend fun update(entry: MealEntryEntity) = Unit
    override suspend fun delete(entry: MealEntryEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteForDate(date: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun clearSlotId(slotId: Long) = Unit
    override suspend fun getById(id: Long): MealEntryEntity? = null
}

private object NoopSavedFoodDao : SavedFoodDao {
    override fun observeAll(): Flow<List<SavedFoodEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedFoodEntity> = emptyList()
    override suspend fun insert(food: SavedFoodEntity): Long = 0L
    override suspend fun insertAll(foods: List<SavedFoodEntity>) = Unit
    override suspend fun update(food: SavedFoodEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopSavedMealDao : SavedMealDao {
    override fun observeAll(): Flow<List<SavedMealEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedMealEntity> = emptyList()
    override suspend fun insert(meal: SavedMealEntity): Long = 0L
    override suspend fun insertAll(meals: List<SavedMealEntity>) = Unit
    override suspend fun update(meal: SavedMealEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopPerformanceDao : PerformanceDao {
    override fun observeAll(): Flow<List<LiftPerformanceEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<LiftPerformanceEntity> = emptyList()
    override suspend fun insert(p: LiftPerformanceEntity): Long = 0L
    override suspend fun insertAll(ps: List<LiftPerformanceEntity>) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopWeeklyReviewDao : WeeklyReviewDao {
    override fun observeAll(): Flow<List<WeeklyReviewEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<WeeklyReviewEntity> = emptyList()
    override suspend fun upsert(review: WeeklyReviewEntity) = Unit
    override suspend fun insertAll(reviews: List<WeeklyReviewEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopMealSlotDao : MealSlotDao {
    override fun observeAll(): Flow<List<MealSlotEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<MealSlotEntity> = emptyList()
    override suspend fun insert(slot: MealSlotEntity): Long = 0L
    override suspend fun update(slot: MealSlotEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun updateSortOrder(id: Long, order: Int) = Unit
}
```

- [ ] **Step 6.2 — Run tests to confirm they fail**

```bash
./gradlew test --tests "com.zack.recomptracker.ui.body.BodyEditViewModelTest" 2>&1 | tail -20
```

Expected: compilation error — `BodyEditViewModel` not found.

- [ ] **Step 6.3 — Create BodyEditViewModel**

Create `app/src/main/java/com/zack/recomptracker/ui/body/BodyEditViewModel.kt`:

```kotlin
package com.zack.recomptracker.ui.body

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.util.toNullableDouble
import com.zack.recomptracker.core.util.toNullableInt
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BodyEditUiState(
    val date: LocalDate,
    val bodyWeightKg: String = "",
    val waistCm: String = "",
    val waistSkinfoldMm: String = "",
    val steps: String = "",
    val sleepHours: String = "",
    val energyScore: Int = 5,
    val hungerScore: Int = 5,
    val sorenessScore: Int = 5,
    val trained: Boolean = false,
    val notes: String = "",
    val message: String? = null,
)

class BodyEditViewModel(
    private val logRepository: LogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val date: LocalDate = LocalDate.parse(
        checkNotNull(savedStateHandle["date"]) { "date nav arg required" }
    )

    private val _uiState = MutableStateFlow(BodyEditUiState(date = date))
    val uiState: StateFlow<BodyEditUiState> = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        viewModelScope.launch {
            val dayLog = logRepository.observeDay(date).first()
            val log = dayLog.dailyLog ?: return@launch
            _uiState.update {
                it.copy(
                    bodyWeightKg = log.bodyWeightKg?.toString().orEmpty(),
                    waistCm = log.waistCm?.toString().orEmpty(),
                    waistSkinfoldMm = log.waistSkinfoldMm?.toString().orEmpty(),
                    steps = log.steps?.toString().orEmpty(),
                    sleepHours = log.sleepHours?.toString().orEmpty(),
                    energyScore = log.energyScore ?: 5,
                    hungerScore = log.hungerScore ?: 5,
                    sorenessScore = log.sorenessScore ?: 5,
                    trained = log.trained,
                    notes = log.notes,
                )
            }
        }
    }

    fun onBodyWeightChanged(v: String) = edit { copy(bodyWeightKg = v) }
    fun onWaistChanged(v: String) = edit { copy(waistCm = v) }
    fun onWaistSkinfoldChanged(v: String) = edit { copy(waistSkinfoldMm = v) }
    fun onStepsChanged(v: String) = edit { copy(steps = v) }
    fun onSleepChanged(v: String) = edit { copy(sleepHours = v) }
    fun onEnergyChanged(v: Int) = edit { copy(energyScore = v.coerceIn(1, 10)) }
    fun onHungerChanged(v: Int) = edit { copy(hungerScore = v.coerceIn(1, 10)) }
    fun onSorenessChanged(v: Int) = edit { copy(sorenessScore = v.coerceIn(1, 10)) }
    fun onTrainedChanged(v: Boolean) = edit { copy(trained = v) }
    fun onNotesChanged(v: String) = edit { copy(notes = v) }

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
                    waistSkinfoldMm = s.waistSkinfoldMm.toNullableDouble(),
                    steps = steps,
                    sleepHours = s.sleepHours.toNullableDouble(),
                    energyScore = s.energyScore,
                    hungerScore = s.hungerScore,
                    sorenessScore = s.sorenessScore,
                    trained = s.trained,
                    notes = s.notes,
                ),
            )
            _saved.emit(Unit)
        }
    }

    private fun edit(block: BodyEditUiState.() -> BodyEditUiState) =
        _uiState.update { it.block().copy(message = null) }
}
```

- [ ] **Step 6.4 — Run tests to confirm they pass**

```bash
./gradlew test --tests "com.zack.recomptracker.ui.body.BodyEditViewModelTest" 2>&1 | tail -20
```

Expected: 4 tests PASSED.

- [ ] **Step 6.5 — Register BodyEditViewModel in AppContainer**

In `AppContainer.kt`, add import:

```kotlin
import com.zack.recomptracker.ui.body.BodyEditViewModel
```

In `AppViewModelFactory.create`, add before `else -> error(...)`:

```kotlin
BodyEditViewModel::class.java -> BodyEditViewModel(
    logRepository = container.logRepository,
    savedStateHandle = androidx.lifecycle.SavedStateHandle(),
)
```

Wait — `SavedStateHandle` in the factory must come from the `CreationExtras` passed to `create`. The standard pattern for this is to use `CreationExtras`. Replace the `AppViewModelFactory` to extend `ViewModelProvider.Factory` and override `create(modelClass, extras)`. However, since all other ViewModels in this factory don't need `SavedStateHandle`, the simplest correct approach is:

Replace `AppViewModelFactory.create` with the two-arg version:

```kotlin
@Suppress("UNCHECKED_CAST")
override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
    return when (modelClass) {
        // ... all existing cases unchanged ...
        BodyHistoryViewModel::class.java -> BodyHistoryViewModel(
            logRepository = container.logRepository,
            dateProvider = container.dateProvider,
        )
        BodyEditViewModel::class.java -> BodyEditViewModel(
            logRepository = container.logRepository,
            savedStateHandle = extras.createSavedStateHandle(),
        )
        else -> error("Unknown ViewModel class: ${modelClass.name}")
    } as T
}
```

Add this import to `AppContainer.kt`:

```kotlin
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
```

Keep the existing single-arg `create(modelClass)` or remove it — the two-arg version supersedes it. Replace the entire `create` method with the two-arg version.

- [ ] **Step 6.6 — Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.7 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/body/BodyEditViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/body/BodyEditViewModelTest.kt \
        app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat: add BodyEditViewModel with SavedStateHandle date injection"
```

---

## Task 7: BodyEditScreen

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/body/BodyEditScreen.kt`

- [ ] **Step 7.1 — Create BodyEditScreen**

```kotlin
package com.zack.recomptracker.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.SectionCard
import java.time.format.DateTimeFormatter

private val HEADER_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val CARD_FMT = DateTimeFormatter.ofPattern("MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyEditScreen(
    viewModel: BodyEditViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.date.format(HEADER_FMT), fontWeight = FontWeight.Bold)
                        Text(
                            "Past check-in",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        val formState = BodyCheckInFormState(
            date = state.date,
            bodyWeightKg = state.bodyWeightKg,
            waistCm = state.waistCm,
            waistSkinfoldMm = state.waistSkinfoldMm,
            steps = state.steps,
            sleepHours = state.sleepHours,
            energyScore = state.energyScore,
            hungerScore = state.hungerScore,
            sorenessScore = state.sorenessScore,
            trained = state.trained,
            notes = state.notes,
            message = state.message,
        )
        val formActions = BodyCheckInFormActions(
            onBodyWeightChanged = viewModel::onBodyWeightChanged,
            onWaistChanged = viewModel::onWaistChanged,
            onWaistSkinfoldChanged = viewModel::onWaistSkinfoldChanged,
            onStepsChanged = viewModel::onStepsChanged,
            onSleepChanged = viewModel::onSleepChanged,
            onEnergyChanged = viewModel::onEnergyChanged,
            onHungerChanged = viewModel::onHungerChanged,
            onSorenessChanged = viewModel::onSorenessChanged,
            onTrainedChanged = viewModel::onTrainedChanged,
            onNotesChanged = viewModel::onNotesChanged,
            onSave = viewModel::saveMetrics,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("Check-in for ${state.date.format(CARD_FMT)}") {
                    BodyCheckInFormContent(
                        state = formState,
                        actions = formActions,
                        saveLabel = "Save check-in for ${state.date.format(CARD_FMT)}",
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 7.2 — Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.3 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/body/BodyEditScreen.kt
git commit -m "feat: add BodyEditScreen for editing past check-in entries"
```

---

## Task 8: Wire navigation — routes + AppNavGraph

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

- [ ] **Step 8.1 — Add BodyHistory and BodyEdit routes**

In `AppNavGraph.kt`, add two new constants to the `Routes` object:

```kotlin
object Routes {
    const val Stats = "stats"
    const val Charts = "charts"
    const val Plan = "plan"
    const val FoodLibrary = "food_library"
    const val Foods = "foods"
    const val Settings = "settings"
    const val BodyHistory = "body_history"
    const val BodyEdit = "body_edit/{date}"
    fun bodyEdit(date: java.time.LocalDate) = "body_edit/$date"
}
```

- [ ] **Step 8.2 — Add imports to AppNavGraph.kt**

Add these imports at the top:

```kotlin
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zack.recomptracker.ui.body.BodyEditViewModel
import com.zack.recomptracker.ui.body.BodyHistoryViewModel
import com.zack.recomptracker.ui.body.BodyHistoryScreen
import com.zack.recomptracker.ui.body.BodyEditScreen
import java.time.LocalDate
```

- [ ] **Step 8.3 — Update Body tab composable to pass onViewHistory**

Replace the existing Body composable:

```kotlin
composable(TopLevelDestination.Body.route) {
    BodyRecoveryScreen(
        viewModel = viewModel<TodayViewModel>(factory = factory),
        onViewHistory = { navController.navigate(Routes.BodyHistory) },
    )
}
```

- [ ] **Step 8.4 — Add BodyHistory composable**

Inside `NavHost`, add after the Body composable:

```kotlin
composable(Routes.BodyHistory) {
    BodyHistoryScreen(
        viewModel = viewModel<BodyHistoryViewModel>(factory = factory),
        onEditDay = { date -> navController.navigate(Routes.bodyEdit(date)) },
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 8.5 — Add BodyEdit composable**

Inside `NavHost`, add after the BodyHistory composable:

```kotlin
composable(
    route = Routes.BodyEdit,
    arguments = listOf(
        androidx.navigation.navArgument("date") {
            type = androidx.navigation.NavType.StringType
        },
    ),
) {
    BodyEditScreen(
        viewModel = viewModel<BodyEditViewModel>(factory = factory),
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 8.6 — Build and run all unit tests**

```bash
./gradlew assembleDebug test 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL, all tests PASSED.

- [ ] **Step 8.7 — Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat: wire body history and edit routes into nav graph"
```

---

## Self-Review Checklist

- [x] **Spec § Data layer** — covered by Task 1 (DailyLogEntity, LogModels, LogRepository, DB migration)
- [x] **Spec § History list** — covered by Tasks 4 + 5 (BodyHistoryViewModel + BodyHistoryScreen)
- [x] **Spec § Edit form** — covered by Tasks 2 + 6 + 7 (BodyCheckInFormContent + BodyEditViewModel + BodyEditScreen)
- [x] **Spec § Trend card** — covered by Task 3 (TodayViewModel + TrendCard in BodyRecoveryContent)
- [x] **Spec § View History button** — covered by Task 3 (BodyRecoveryContent)
- [x] **Spec § Navigation** — covered by Task 8 (AppNavGraph)
- [x] **AppContainer registration** — covered in Tasks 4.5 and 6.5
- [x] **Type consistency** — `BodyCheckInFormState`/`BodyCheckInFormActions` defined in Task 2 and used in Tasks 3 and 7; `BodyHistoryItem` defined in Task 4 and used in Task 5; `BodyEditUiState` defined in Task 6 and used in Task 7
- [x] **SavedStateHandle** — Task 6.5 uses `extras.createSavedStateHandle()` (correct Compose Navigation pattern)
