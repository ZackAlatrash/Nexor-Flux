package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.domain.workout.ExerciseLibraryJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class ExerciseLibraryRepositoryTest {

    private class FakeExerciseDao : ExerciseDao {
        val rows = mutableListOf<ExerciseEntity>()
        var insertCalls = 0
        override fun observeAll(): Flow<List<ExerciseEntity>> = flowOf(rows)
        override suspend fun search(query: String): List<ExerciseEntity> =
            rows.filter { it.name.contains(query, ignoreCase = true) }
        override suspend fun getById(id: Long): ExerciseEntity? = rows.firstOrNull { it.id == id }
        override suspend fun getAll(): List<ExerciseEntity> = rows.toList()
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
        override suspend fun insertReturningId(exercise: ExerciseEntity): Long {
            val nextId = (rows.maxOfOrNull { it.id } ?: 0L) + 1
            val entity = exercise.copy(id = nextId)
            rows.add(entity)
            return nextId
        }
        override suspend fun update(exercise: ExerciseEntity) {
            val idx = rows.indexOfFirst { it.id == exercise.id }
            if (idx >= 0) rows[idx] = exercise
        }
        override suspend fun findIdBySourceAndExternalId(source: String, externalId: String): Long? =
            rows.firstOrNull { it.source == source && it.externalId == externalId }?.id
        override suspend fun stampSourceVersion(source: String, version: String) {
            for (i in rows.indices) if (rows[i].source == source) rows[i] = rows[i].copy(sourceVersion = version)
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
        val rowsAfterFirst = dao.rows.toList()

        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }

        // The version gate skips the re-seed entirely — rows are untouched (no duplicate insert).
        assertEquals(rowsAfterFirst, dao.rows.toList())
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

    /** Fake DAO that captures the inserted entity and returns a fixed id. */
    private class CapturingExerciseDao : ExerciseDao by mock() {
        var inserted: ExerciseEntity? = null
        // addCustomExercise now routes through the idempotent insertCustomOrGetExisting (P1-20);
        // capture the entity it builds here.
        override suspend fun insertCustomOrGetExisting(entity: ExerciseEntity): Long {
            inserted = entity
            return 42L
        }
        override suspend fun insertReturningId(entity: ExerciseEntity): Long {
            inserted = entity
            return 42L
        }
    }

    @Test
    fun `addCustomExercise persists specified primary and secondary muscles as json`() = runTest {
        val dao = CapturingExerciseDao()
        val repo = ExerciseLibraryRepository(dao)

        val id = repo.addCustomExercise(
            name = "Cable Y-Raise",
            primaryMuscles = listOf("Shoulders"),
            secondaryMuscles = listOf("Back"),
        )

        assertEquals(42L, id)
        assertEquals(ExerciseLibraryJson.encodeList(listOf("Shoulders")), dao.inserted!!.primaryMuscles)
        assertEquals(ExerciseLibraryJson.encodeList(listOf("Back")), dao.inserted!!.secondaryMuscles)
        assertEquals("Cable Y-Raise", dao.inserted!!.name)
        assertEquals(true, dao.inserted!!.userCreated)
    }

    @Test
    fun `addCustomExercise defaults to empty muscles when none provided`() = runTest {
        val dao = CapturingExerciseDao()
        val repo = ExerciseLibraryRepository(dao)

        repo.addCustomExercise("Sled Push")

        assertEquals(ExerciseLibraryJson.encodeList(emptyList()), dao.inserted!!.primaryMuscles)
    }
}
