package com.zack.recomptracker.data.repository

import com.zack.recomptracker.ai.ActionBlock
import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.ai.WeeklyBriefing
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyBriefingRepositoryTest {

    private class FakeDao : WeeklyReviewDao {
        val rows = mutableMapOf<String, WeeklyReviewEntity>()
        override fun observeAll(): Flow<List<WeeklyReviewEntity>> = flowOf(rows.values.toList())
        override suspend fun getAll(): List<WeeklyReviewEntity> = rows.values.toList()
        override suspend fun getByWeekStart(weekStart: String): WeeklyReviewEntity? = rows[weekStart]
        override suspend fun upsert(review: WeeklyReviewEntity) { rows[review.weekStart] = review }
        override suspend fun insertAll(reviews: List<WeeklyReviewEntity>) { reviews.forEach { rows[it.weekStart] = it } }
        override suspend fun deleteAll() { rows.clear() }
    }

    private fun briefing(headline: String) = WeeklyBriefing(
        weekStart = "2026-06-08", phase = BriefingPhase.FULL, headline = headline,
        narrative = "n", signals = emptyList(),
        action = ActionBlock("Hold calories", "r", null), watchNext = "w",
    )

    @Test
    fun `generates and caches when no row exists`() = runTest {
        val dao = FakeDao()
        val repo = WeeklyBriefingRepository(dao)
        var calls = 0
        val result = repo.briefingFor("2026-06-08", "sig-1") { calls++; briefing("first") }
        assertEquals("first", result.headline)
        assertEquals(1, calls)
        assertEquals("sig-1", dao.rows["2026-06-08"]?.briefingSignature)
    }

    @Test
    fun `returns cached briefing when signature matches`() = runTest {
        val dao = FakeDao()
        val repo = WeeklyBriefingRepository(dao)
        repo.briefingFor("2026-06-08", "sig-1") { briefing("first") }
        var calls = 0
        val result = repo.briefingFor("2026-06-08", "sig-1") { calls++; briefing("second") }
        assertEquals("first", result.headline)
        assertEquals(0, calls)
    }

    @Test
    fun `regenerates when signature changes`() = runTest {
        val dao = FakeDao()
        val repo = WeeklyBriefingRepository(dao)
        repo.briefingFor("2026-06-08", "sig-1") { briefing("first") }
        val result = repo.briefingFor("2026-06-08", "sig-2") { briefing("second") }
        assertEquals("second", result.headline)
        assertEquals("sig-2", dao.rows["2026-06-08"]?.briefingSignature)
    }

    @Test
    fun `regenerates when cached json is corrupt`() = runTest {
        val dao = FakeDao()
        dao.rows["2026-06-08"] = WeeklyReviewEntity(
            weekStart = "2026-06-08", verdict = "", recommendedCalorieChange = 0,
            reasonCodes = "", generatedAt = "",
            briefingJson = "{ not valid", briefingSignature = "sig-1", briefingGeneratedAt = "t",
        )
        val repo = WeeklyBriefingRepository(dao)
        var calls = 0
        val result = repo.briefingFor("2026-06-08", "sig-1") { calls++; briefing("regen") }
        assertEquals("regen", result.headline)
        assertEquals(1, calls)
    }
}
