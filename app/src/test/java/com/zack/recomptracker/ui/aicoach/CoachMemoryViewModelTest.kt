package com.zack.recomptracker.ui.aicoach

import com.zack.recomptracker.data.coach.CoachMemory
import com.zack.recomptracker.data.coach.CoachMemoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoachMemoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeMemory : CoachMemory {
        val state = MutableStateFlow<List<CoachMemoryEntry>>(emptyList())
        override fun observe(): Flow<List<CoachMemoryEntry>> = state
        override suspend fun all() = state.value
        override suspend fun add(text: String): CoachMemoryEntry? {
            val e = CoachMemoryEntry("${state.value.size + 1}", text.trim(), "2026-06-05")
            state.value = state.value + e; return e
        }
        override suspend fun update(id: String, text: String) {
            state.value = state.value.map { if (it.id == id) it.copy(text = text) else it }
        }
        override suspend fun delete(id: String) { state.value = state.value.filterNot { it.id == id } }
        override suspend fun removeMatching(query: String): CoachMemoryEntry? = null
    }

    @Test fun `add appends and delete removes, reflected in state`() = runTest {
        val mem = FakeMemory()
        val vm = CoachMemoryViewModel(mem)
        vm.add("Vegetarian")
        vm.add("Bad knee")
        advanceUntilIdle()
        assertEquals(listOf("Vegetarian", "Bad knee"), mem.state.value.map { it.text })
        vm.delete(mem.state.value.first().id)
        advanceUntilIdle()
        assertEquals(listOf("Bad knee"), mem.state.value.map { it.text })
    }
}
