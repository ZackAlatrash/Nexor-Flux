package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.coach.CoachMemory
import com.zack.recomptracker.data.coach.CoachMemoryEntry
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CoachToolsAdapterMemoryTest {

    private val fixedDate: LocalDate = LocalDate.of(2026, 7, 1)
    private val dateProvider = object : DateProvider {
        override fun today(): LocalDate = fixedDate
    }

    private class FakeMemory(private val entries: List<CoachMemoryEntry>) : CoachMemory {
        override fun observe(): Flow<List<CoachMemoryEntry>> = flowOf(entries)
        override suspend fun all(): List<CoachMemoryEntry> = entries
        override suspend fun add(text: String): CoachMemoryEntry? = null
        override suspend fun update(id: String, text: String) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun removeMatching(query: String): CoachMemoryEntry? = null
    }

    private fun fakeMemory(texts: List<String>): CoachMemory =
        FakeMemory(texts.mapIndexed { i, t -> CoachMemoryEntry("${i + 1}", t, "2026-06-05") })

    private suspend fun adapterWith(memory: CoachMemory): CoachToolsAdapter {
        val executor = mock<CoachToolExecutor>()
        whenever(executor.execute(any(), any())).thenReturn("{\"meals\":[]}")
        val planRepo = mock<PlanRepository>()
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
        val profileStore = mock<UserProfilePreferencesStore>()
        whenever(profileStore.preferences).thenReturn(flowOf(UserProfilePreferences()))
        return CoachToolsAdapter(
            toolExecutor = executor,
            planRepository = planRepo,
            userProfileStore = profileStore,
            dateProvider = dateProvider,
            handoffStore = CoachHandoffStore(),
            coachMemory = memory,
        )
    }

    @Test
    fun `systemPromptSnapshot includes a memory block when memory is non-empty`() = runTest {
        val adapter = adapterWith(memory = fakeMemory(listOf("Vegetarian", "Bad left knee")))
        val prompt = adapter.systemPromptSnapshot()
        assertTrue(prompt.contains("WHAT I KNOW ABOUT YOU"))
        assertTrue(prompt.contains("Vegetarian"))
        assertTrue(prompt.contains("Bad left knee"))
    }

    @Test
    fun `systemPromptSnapshot omits the memory block when memory is empty`() = runTest {
        val adapter = adapterWith(memory = fakeMemory(emptyList()))
        assertFalse(adapter.systemPromptSnapshot().contains("WHAT I KNOW ABOUT YOU"))
    }
}
