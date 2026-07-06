package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.coach.CoachMemory
import com.zack.recomptracker.data.coach.CoachMemoryEntry
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class CoachToolExecutorMemoryTest {
    private val dateProvider = object : DateProvider { override fun today() = LocalDate.of(2026, 6, 5) }

    private class FakeMemory(seed: List<String> = emptyList()) : CoachMemory {
        val items = seed.mapIndexed { i, t -> CoachMemoryEntry("${i + 1}", t, "2026-06-05") }.toMutableList()
        override fun observe(): Flow<List<CoachMemoryEntry>> = flowOf(items)
        override suspend fun all() = items.toList()
        override suspend fun add(text: String): CoachMemoryEntry? {
            val e = CoachMemoryEntry("${items.size + 1}", text.trim(), "2026-06-05"); items.add(e); return e
        }
        override suspend fun update(id: String, text: String) {}
        override suspend fun delete(id: String) {}
        override suspend fun removeMatching(query: String): CoachMemoryEntry? {
            val m = items.firstOrNull { it.text.contains(query, ignoreCase = true) || query.contains(it.text, ignoreCase = true) }
            if (m != null) items.remove(m); return m
        }
    }

    private fun executor(mem: CoachMemory) = CoachToolExecutor(
        logRepository = mock<LogRepository>(),
        planRepository = mock<PlanRepository>(),
        dateProvider = dateProvider,
        coachMemory = mem,
    )

    @Test fun `remember adds a fact to memory`() = runTest {
        val mem = FakeMemory()
        val json = executor(mem).execute("remember", mapOf("text" to "Vegetarian"))
        assertTrue(json.contains("\"success\":true"))
        assertTrue(mem.items.any { it.text == "Vegetarian" })
    }

    @Test fun `remember rejects blank text`() = runTest {
        val json = executor(FakeMemory()).execute("remember", mapOf("text" to "  "))
        assertTrue(json.contains("error"))
    }

    @Test fun `forget removes a matching fact`() = runTest {
        val mem = FakeMemory(listOf("Vegetarian", "Trains at home"))
        val json = executor(mem).execute("forget", mapOf("text" to "vegetarian"))
        assertTrue(json.contains("\"success\":true"))
        assertTrue(mem.items.none { it.text == "Vegetarian" })
    }

    @Test fun `forget with no match returns an error`() = runTest {
        val json = executor(FakeMemory(listOf("Vegetarian"))).execute("forget", mapOf("text" to "deadlift"))
        assertTrue(json.contains("error"))
    }
}
