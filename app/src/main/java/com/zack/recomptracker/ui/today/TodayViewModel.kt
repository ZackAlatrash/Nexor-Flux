package com.zack.recomptracker.ui.today

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.InsightKind
import com.zack.recomptracker.ai.InsightRequest
import com.zack.recomptracker.ai.RecoveryInsightContext
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.core.util.toNullableDouble
import com.zack.recomptracker.domain.body.StepsValidation
import com.zack.recomptracker.domain.body.validateStepsInput
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.health.HealthSyncCoordinator
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
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
    val bodyWeightKg: String = "",
    val waistCm: String = "",
    val waistSkinfoldMm: String = "",
    val steps: String = "",
    /** True once the user types in the steps field — only then does saving mark steps manual. */
    val stepsEdited: Boolean = false,
    val sleepHours: String = "",
    val energyScore: Int = 5,
    val hungerScore: Int = 5,
    val sorenessScore: Int = 5,
    val trained: Boolean = false,
    val notes: String = "",
    val metricsDirty: Boolean = false,
    val checkInDone: Boolean = false,
    val message: String? = null,
    val weightChange7d: Float? = null,
    val waistChange7d: Float? = null,
    // Body screen MetricsHero data
    val lastLogDate: LocalDate? = null,
    val lastLogWeightKg: Double? = null,
    val lastLogWaistCm: Double? = null,
    val weightSparkline14d: List<Float> = emptyList(),
    val waistSparkline14d: List<Float> = emptyList(),
    val totalDaysLogged: Int = 0,
    val recoveryInsightContext: RecoveryInsightContext? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val healthSyncCoordinator: HealthSyncCoordinator,
    private val aiInsightCoordinator: AiInsightCoordinator,
    // Off-main dispatcher for the CPU-bearing collectors (per-day grouping/macro summing, and the
    // 14-day calendar-sparkline build with LocalDate.parse + sorting). _uiState is a
    // MutableStateFlow, so updating from this dispatcher is safe. Default keeps AppContainer unchanged.
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val initialToday = dateProvider.today()
    private val _uiState = MutableStateFlow(TodayUiState(date = initialToday))
    val uiState: StateFlow<TodayUiState> = _uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TodayUiState(date = initialToday),
    )

    private val _savedEvent = MutableSharedFlow<Unit>(replay = 0)
    val savedEvent: SharedFlow<Unit> = _savedEvent

    val recoveryInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.RECOVERY_READINESS)

    fun onRecoveryInsightVisible() {
        val ctx = _uiState.value.recoveryInsightContext ?: return
        if (!ctx.hasSufficientData) return
        aiInsightCoordinator.onInsightVisible(InsightRequest.RecoveryReadiness(ctx))
    }

    fun retryRecoveryInsight() {
        val ctx = _uiState.value.recoveryInsightContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.RecoveryReadiness(ctx))
    }

    init {
        // The working date follows the real calendar day. A tab ViewModel outlives midnight, so
        // without this a check-in saved next morning would land on the day the tab was opened
        // (P1-10). saveMetrics reads _uiState.date, so advancing it here fixes the write too.
        viewModelScope.launch {
            dateProvider.todayFlow().collect { day ->
                _uiState.update { if (it.date == day) it else it.copy(date = day) }
            }
        }
        // Per-day grouping + macro summing runs in the collect body; launch off-main. flatMapLatest
        // re-subscribes observeDay(today) when the day rolls, so the summary tracks the new day.
        viewModelScope.launch(computeDispatcher) {
            dateProvider.todayFlow().flatMapLatest { today ->
                combine(
                    logRepository.observeDay(today),
                    planRepository.preferences,
                    logRepository.observeSlots(),
                ) { day, prefs, slots ->
                    Triple(day, prefs, slots)
                }
            }.collect { (day, prefs, slots) ->
                val allEntries = day.meals
                val slotMap = allEntries.groupBy { it.slotId }
                val slottedEntries = slots.map { slot ->
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
                    metrics.copy(
                        target = prefs,
                        totals = day.totals,
                        slots = slottedEntries,
                        unslottedEntries = unslotted,
                        checkInDone = log != null,
                        recoveryInsightContext = buildRecoveryInsightContext(day.dailyLog),
                    )
                }
            }
        }
        // Health Connect sync is centralized in HealthSyncCoordinator, whose mutex serializes every
        // read-modify-write of the daily log. Doing the read+apply directly here (as before) raced
        // the app-foreground sync (RecompTrackerApp.onStart) and could revert a freshly-written
        // weight/sleep to null. syncStepsNow refreshes steps immediately; syncIfDue does a debounced
        // full sync. Both are fire-and-forget and no-op unless Health Connect is enabled + permitted.
        healthSyncCoordinator.syncStepsNow()
        healthSyncCoordinator.syncIfDue()
        // 14-day sparkline build (LocalDate.parse + interpolation + sorting) runs in the collect
        // body; launch off-main. flatMapLatest re-runs the windows against the new day at midnight.
        viewModelScope.launch(computeDispatcher) {
            dateProvider.todayFlow().flatMapLatest { today ->
                logRepository.observeDailyLogs().map { logs -> logs to today }
            }.collect { (allLogs, today) ->
                val cutoff = today.minusDays(14)
                val priorCutoff = today.minusDays(6)
                val recent = allLogs
                    .map { it to LocalDate.parse(it.date) }
                    .filter { (_, date) -> date >= cutoff }
                    .sortedByDescending { (_, date) -> date }

                val latestWeight = recent.firstNotNullOfOrNull { (log, _) -> log.bodyWeightKg }
                val weight7dAgo = recent
                    .filter { (_, date) -> date <= priorCutoff }
                    .firstNotNullOfOrNull { (log, _) -> log.bodyWeightKg }

                val latestWaist = recent.firstNotNullOfOrNull { (log, _) -> log.waistCm }
                val waist7dAgo = recent
                    .filter { (_, date) -> date <= priorCutoff }
                    .firstNotNullOfOrNull { (log, _) -> log.waistCm }

                // MetricsHero: most recent check-in with weight or waist (includes today once logged)
                val lastEntry = latestCheckIn(allLogs, today)

                // 14-day sparklines, calendar-accurate: each slot is one day, internal gaps are
                // interpolated so a 3-day gap spans 3× the width of a 1-day gap.
                val dates14 = (13 downTo 0).map { today.minusDays(it.toLong()) }
                val byDate = allLogs.associateBy { LocalDate.parse(it.date) }
                val weightSpark = calendarSparkline(dates14.map { byDate[it]?.bodyWeightKg?.toFloat() })
                val waistSpark = calendarSparkline(dates14.map { byDate[it]?.waistCm?.toFloat() })

                _uiState.update {
                    it.copy(
                        weightChange7d = if (latestWeight != null && weight7dAgo != null)
                            (latestWeight - weight7dAgo).toFloat() else null,
                        waistChange7d = if (latestWaist != null && waist7dAgo != null)
                            (latestWaist - waist7dAgo).toFloat() else null,
                        lastLogDate = lastEntry?.second,
                        lastLogWeightKg = lastEntry?.first?.bodyWeightKg,
                        lastLogWaistCm = lastEntry?.first?.waistCm,
                        weightSparkline14d = weightSpark,
                        waistSparkline14d = waistSpark,
                        totalDaysLogged = allLogs.count { log ->
                            log.bodyWeightKg != null || log.waistCm != null
                        },
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
        viewModelScope.launch { logRepository.deleteMeal(id) }
    }

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

    fun onBodyWeightChanged(v: String) = editMetrics { copy(bodyWeightKg = v) }
    fun onWaistChanged(v: String) = editMetrics { copy(waistCm = v) }
    fun onWaistSkinfoldChanged(v: String) = editMetrics { copy(waistSkinfoldMm = v) }
    fun onStepsChanged(v: String) = editMetrics { copy(steps = v, stepsEdited = true) }
    fun onSleepChanged(v: String) = editMetrics { copy(sleepHours = v) }
    fun onEnergyChanged(v: Int) = editMetrics { copy(energyScore = v.coerceIn(1, 10)) }
    fun onHungerChanged(v: Int) = editMetrics { copy(hungerScore = v.coerceIn(1, 10)) }
    fun onSorenessChanged(v: Int) = editMetrics { copy(sorenessScore = v.coerceIn(1, 10)) }
    fun onTrainedChanged(v: Boolean) = editMetrics { copy(trained = v) }
    fun onNotesChanged(v: String) = editMetrics { copy(notes = v) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun saveMetrics() {
        val s = _uiState.value
        val steps = when (val v = validateStepsInput(s.steps)) {
            is StepsValidation.Invalid -> {
                _uiState.update { it.copy(message = v.message) }
                return
            }
            is StepsValidation.Valid -> v.steps
        }
        viewModelScope.launch {
            logRepository.saveDailyMetrics(
                DailyMetricsInput(
                    date = s.date,
                    bodyWeightKg = s.bodyWeightKg.toNullableDouble(),
                    waistCm = s.waistCm.toNullableDouble(),
                    waistSkinfoldMm = s.waistSkinfoldMm.toNullableDouble(),
                    steps = steps,
                    stepsEdited = s.stepsEdited,
                    sleepHours = s.sleepHours.toNullableDouble(),
                    energyScore = s.energyScore,
                    hungerScore = s.hungerScore,
                    sorenessScore = s.sorenessScore,
                    trained = s.trained,
                    notes = s.notes,
                ),
            )
            _uiState.update { it.copy(metricsDirty = false, stepsEdited = false, message = null) }
            _savedEvent.emit(Unit)
        }
    }

    private fun editMetrics(block: TodayUiState.() -> TodayUiState) =
        _uiState.update { it.block().copy(metricsDirty = true, message = null) }
}

/**
 * The most recent daily log that carries a weight or waist measurement, on or before [today].
 * Drives the Metrics hero card — includes today's entry so the hero updates the moment it's logged.
 */
internal fun latestCheckIn(
    logs: List<DailyLogEntity>,
    today: LocalDate,
): Pair<DailyLogEntity, LocalDate>? =
    logs.map { it to LocalDate.parse(it.date) }
        .sortedByDescending { (_, d) -> d }
        .firstOrNull { (log, d) ->
            d <= today && (log.bodyWeightKg != null || log.waistCm != null)
        }

/**
 * Turns a fixed window of daily slots (oldest→newest; null = no log that day) into a
 * calendar-accurate sparkline series. Leading and trailing empty days are trimmed; internal gaps
 * are linearly interpolated so each index is exactly one day — a 3-day gap occupies 3× the
 * horizontal space of a 1-day gap. Interpolated points lie on the connecting line, so the curve's
 * shape is unchanged; only the spacing becomes proportional to elapsed days.
 */
internal fun calendarSparkline(slots: List<Float?>): List<Float> {
    val first = slots.indexOfFirst { it != null }
    val last = slots.indexOfLast { it != null }
    if (first < 0) return emptyList()

    val out = ArrayList<Float>(last - first + 1)
    var prevIndex = first
    var prevValue = slots[first]!!
    out += prevValue
    for (i in (first + 1)..last) {
        val value = slots[i] ?: continue
        val gap = i - prevIndex
        for (k in 1 until gap) {
            out += prevValue + (value - prevValue) * (k.toFloat() / gap)
        }
        out += value
        prevIndex = i
        prevValue = value
    }
    return out
}
