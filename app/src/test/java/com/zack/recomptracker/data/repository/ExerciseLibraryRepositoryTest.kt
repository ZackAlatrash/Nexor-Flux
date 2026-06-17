package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseLibraryRepositoryTest {

    private class FakeExerciseDao : ExerciseDao {
        val rows = mutableListOf<ExerciseEntity>()
        var insertCalls = 0
        override fun observeAll(): Flow<List<ExerciseEntity>> = flowOf(rows)
        override suspend fun search(query: String): List<ExerciseEntity> =
            rows.filter { it.name.contains(query, ignoreCase = true) }
        override suspend fun getById(id: Long): ExerciseEntity? = rows.firstOrNull { it.id == id }
        override suspend fun count(): Int = rows.size
        override suspend fun sourceVersion(source: String): String? =
            rows.firstOrNull { it.source == source }?.sourceVersion
        override suspend fun insertAll(exercises: List<ExerciseEntity>) {
            insertCalls++
            var nextId = (rows.maxOfOrNull { it.id } ?: 0L)
            rows.addAll(exercises.map { it.copy(id = ++nextId) })
        }
        override suspend fun deleteBySource(source: String) {
            rows.removeAll { it.source == source }
        }
    }

    private val sampleJson = """
        [{"id":"Squat","name":"Barbell Squat","level":"intermediate","category":"strength",
          "primaryMuscles":["quadriceps"],"secondaryMuscles":[],"instructions":["Go."],"images":[]}]
    """.trimIndent()

    @Test
    fun `seedIfEmpty inserts when library empty`() = runTest {
        val dao = FakeExerciseDao()
        val repo = ExerciseLibraryRepository(dao)

        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }

        assertEquals(1, dao.rows.size)
        assertEquals("Barbell Squat", dao.rows.first().name)
    }

    @Test
    fun `seedIfEmpty is a no-op when same version already present`() = runTest {
        val dao = FakeExerciseDao()
        val repo = ExerciseLibraryRepository(dao)
        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }
        val callsAfterFirst = dao.insertCalls

        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }

        assertEquals(callsAfterFirst, dao.insertCalls)
    }

    @Test
    fun `seedIfEmpty re-seeds when version changes`() = runTest {
        val dao = FakeExerciseDao()
        val repo = ExerciseLibraryRepository(dao)
        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }

        repo.seedIfEmpty(version = "v2") { sampleJson.byteInputStream() }

        assertEquals(1, dao.rows.size)
        assertEquals("v2", dao.rows.first().sourceVersion)
    }

    @Test
    fun `search maps to domain with decoded lists`() = runTest {
        val dao = FakeExerciseDao()
        val repo = ExerciseLibraryRepository(dao)
        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }

        val results = repo.search("squat")

        assertEquals(1, results.size)
        assertEquals(listOf("quadriceps"), results.first().primaryMuscles)
    }
}
