package com.zack.recomptracker.data.rebalance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.rebalanceDataStore by preferencesDataStore(name = "rebalance")

/**
 * The minimal read/write surface the weekly-rebalance coordinator/UI depend on, extracted so callers
 * can be unit-tested against a simple fake instead of the concrete DataStore class.
 * [DataStoreRebalanceStore] is the production implementation. Mirrors
 * [com.zack.recomptracker.data.coach.CoachExperiments] / [com.zack.recomptracker.data.coach.CoachExperimentStore].
 */
interface RebalanceStore {
    /** The persisted [RebalanceState] as an observable stream. */
    val state: Flow<RebalanceState>

    /** A one-shot suspend read of the current [RebalanceState] (the empty default when nothing is stored). */
    suspend fun current(): RebalanceState

    /**
     * Persist [state], overwriting any existing value. [RebalanceState.history] is trimmed to the
     * newest [RebalanceState.HISTORY_CAP] records by [RebalancePlan.createdAtIso] first — the codec
     * itself stays a dumb (de)serializer with no cap logic.
     */
    suspend fun save(state: RebalanceState)

    /** The ISO date the once-daily evaluation last ran, or `null` if it has never run. */
    suspend fun lastEvaluated(): LocalDate?

    /** Record that the once-daily evaluation ran on [date]. */
    suspend fun markEvaluated(date: LocalDate)
}

/**
 * DataStore-backed persistence for the weekly-rebalance [RebalanceState] plus the once-daily
 * evaluation gate date. Mirrors [com.zack.recomptracker.data.coach.CoachExperimentStore]: a
 * `preferencesDataStore` delegate, a `.data.map{}` [Flow], `.edit{}` suspend setters, and typed keys.
 * All (de)serialization lives in the pure [RebalanceSerialization] object (unit-tested there); this
 * class is thin I/O only, plus the history-cap enforcement described on [RebalanceStore.save].
 */
class DataStoreRebalanceStore(
    private val dataStore: DataStore<Preferences>,
) : RebalanceStore {

    /** Production constructor — binds to the app-scoped `rebalance` DataStore. */
    constructor(context: Context) : this(context.rebalanceDataStore)

    override val state: Flow<RebalanceState> = dataStore.data.map { prefs ->
        RebalanceSerialization.decode(prefs[Keys.State])
    }

    override suspend fun current(): RebalanceState =
        RebalanceSerialization.decode(dataStore.data.first()[Keys.State])

    override suspend fun save(state: RebalanceState) {
        val capped = state.copy(
            history = state.history
                .sortedBy { it.createdAtIso }
                .takeLast(RebalanceState.HISTORY_CAP),
        )
        dataStore.edit { prefs ->
            prefs[Keys.State] = RebalanceSerialization.encode(capped)
        }
    }

    override suspend fun lastEvaluated(): LocalDate? =
        dataStore.data.first()[Keys.LastEvaluated]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun markEvaluated(date: LocalDate) {
        dataStore.edit { prefs ->
            prefs[Keys.LastEvaluated] = date.toString()
        }
    }

    private object Keys {
        val State = stringPreferencesKey("state")
        val LastEvaluated = stringPreferencesKey("last_evaluated")
    }
}
