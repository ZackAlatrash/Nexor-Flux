package com.zack.recomptracker.data.coach

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.domain.coach.CoachExperiment
import com.zack.recomptracker.domain.coach.CoachExperimentSerialization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.coachExperimentDataStore by preferencesDataStore(name = "coach_experiment")

/**
 * The minimal read/write surface for the single active Cross-Signal Discovery experiment (Phase 5),
 * extracted so callers (the Today slot ViewModel, the evaluation) can be unit-tested against a simple
 * in-memory fake instead of the concrete DataStore class. [CoachExperimentStore] is the production
 * implementation. Mirrors [CoachInbox] / [CoachJourney].
 *
 * Single active experiment (Q7b): [start] overwrites any existing one; [active] reads it (or `null`);
 * [clear] ends it (the evaluation clears once its week is up).
 */
interface CoachExperiments {
    /** The active experiment as an observable stream, or `null` when none is running. */
    val active: Flow<CoachExperiment?>

    /** Start [experiment], overwriting any existing active one (single active). */
    suspend fun start(experiment: CoachExperiment)

    /** A one-shot suspend read of the active experiment, or `null` when none is running. */
    suspend fun current(): CoachExperiment?

    /** Clear the active experiment (its week is up, or the user replaced it). */
    suspend fun clear()
}

/** In-memory [CoachExperiments] fake for tests / Context-free construction. */
class FakeCoachExperiments(initial: CoachExperiment? = null) : CoachExperiments {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(initial)
    override val active: Flow<CoachExperiment?> = state
    override suspend fun start(experiment: CoachExperiment) { state.value = experiment }
    override suspend fun current(): CoachExperiment? = state.value
    override suspend fun clear() { state.value = null }
}

/**
 * DataStore-backed persistence for the ONE active Cross-Signal Discovery experiment. Mirrors
 * [CoachInboxRepository]: a `preferencesDataStore` delegate, a `.data.map{}` [Flow], `.edit{}` suspend
 * setters, and a typed key. All (de)serialization lives in the pure [CoachExperimentSerialization]
 * object (unit-tested there); this class is thin I/O only.
 *
 * Boundary rule (§5): imports nothing from the legacy `ai/local` stack.
 */
class CoachExperimentStore(
    private val dataStore: DataStore<Preferences>,
) : CoachExperiments {

    /** Production constructor — binds to the app-scoped `coach_experiment` DataStore. */
    constructor(context: Context) : this(context.coachExperimentDataStore)

    override val active: Flow<CoachExperiment?> = dataStore.data.map { prefs ->
        CoachExperimentSerialization.decode(prefs[Keys.Active])
    }

    override suspend fun start(experiment: CoachExperiment) {
        dataStore.edit { prefs ->
            prefs[Keys.Active] = CoachExperimentSerialization.encode(experiment)
        }
    }

    override suspend fun current(): CoachExperiment? =
        CoachExperimentSerialization.decode(dataStore.data.first()[Keys.Active])

    override suspend fun clear() {
        dataStore.edit { it.remove(Keys.Active) }
    }

    private object Keys {
        val Active = stringPreferencesKey("active_experiment")
    }
}
