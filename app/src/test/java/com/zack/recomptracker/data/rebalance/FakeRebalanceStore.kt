package com.zack.recomptracker.data.rebalance

import com.zack.recomptracker.domain.rebalance.RebalanceState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Minimal in-memory [RebalanceStore] for ViewModel/repository unit tests. Emits a fixed
 * [RebalanceState] (empty by default, which is behaviour-neutral for every effective-targets
 * consumer). Pass a populated state to exercise the reduced-target path.
 */
class FakeRebalanceStore(
    private val value: RebalanceState = RebalanceState(),
) : RebalanceStore {
    override val state: Flow<RebalanceState> = flowOf(value)
    override suspend fun current(): RebalanceState = value
    override suspend fun save(state: RebalanceState) = Unit
    override suspend fun lastEvaluated(): LocalDate? = null
    override suspend fun markEvaluated(date: LocalDate) = Unit
}
