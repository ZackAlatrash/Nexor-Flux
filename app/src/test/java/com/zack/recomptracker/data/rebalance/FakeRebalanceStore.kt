package com.zack.recomptracker.data.rebalance

import com.zack.recomptracker.domain.rebalance.RebalanceState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared in-memory [RebalanceStore] fake for both ViewModel/repository unit tests and
 * [RebalanceCoordinatorTest]. MutableStateFlow-backed so [save] visibly updates [state] (unlike a
 * fixed `flowOf`), with an optional [seed] (empty by default, which is behaviour-neutral for every
 * effective-targets consumer — pass a populated state to exercise the reduced-target path).
 *
 * Mirrors [DataStoreRebalanceStore.save]'s history-cap trimming so coordinator tests exercise the
 * same trimming the production store performs, and tracks [saveCalls] for call-count assertions.
 */
class FakeRebalanceStore(
    seed: RebalanceState = RebalanceState(),
    private var lastEvaluatedDate: LocalDate? = null,
) : RebalanceStore {
    private val flow = MutableStateFlow(seed)

    /** Number of times [save] has been called. */
    var saveCalls = 0
        private set

    override val state: Flow<RebalanceState> = flow

    override suspend fun current(): RebalanceState = flow.value

    override suspend fun save(state: RebalanceState) {
        // Mirror the production history cap so tests exercise the same trimming.
        flow.value = state.copy(
            history = state.history
                .sortedBy { it.createdAtIso }
                .takeLast(RebalanceState.HISTORY_CAP),
        )
        saveCalls++
    }

    override suspend fun lastEvaluated(): LocalDate? = lastEvaluatedDate

    override suspend fun markEvaluated(date: LocalDate) {
        lastEvaluatedDate = date
    }
}
