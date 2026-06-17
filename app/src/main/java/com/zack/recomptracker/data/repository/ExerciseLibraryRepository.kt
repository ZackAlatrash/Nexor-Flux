package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.ExerciseLibraryJson
import com.zack.recomptracker.domain.workout.toEntity
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class ExerciseLibraryRepository(private val exerciseDao: ExerciseDao) {

    open fun observeAll(): Flow<List<Exercise>> =
        exerciseDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    open suspend fun search(query: String): List<Exercise> =
        exerciseDao.search(query.trim()).map { it.toDomain() }

    open suspend fun getById(id: Long): Exercise? = exerciseDao.getById(id)?.toDomain()

    open suspend fun addCustomExercise(name: String): Long {
        val entity = ExerciseEntity(
            source = "user",
            sourceVersion = "1",
            externalId = "user_" + name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_"),
            name = name.trim(),
            category = null,
            force = null,
            level = null,
            mechanic = null,
            equipment = null,
            primaryMuscles = "[]",
            secondaryMuscles = "[]",
            instructions = "[]",
            images = "[]",
            userCreated = true,
        )
        return exerciseDao.insertReturningId(entity)
    }

    /**
     * Seeds the library from a free-exercise-db JSON stream the first time, or when [version]
     * differs from what is stored. Idempotent otherwise. [openStream] is invoked only when a
     * (re)seed is actually needed.
     */
    open suspend fun seedIfEmpty(version: String, openStream: () -> InputStream) {
        val storedVersion = exerciseDao.sourceVersion(SOURCE)
        if (storedVersion == version && exerciseDao.count() > 0) return

        val raw = openStream().bufferedReader().use { it.readText() }
        val entities = ExerciseLibraryJson.parse(raw).map { it.toEntity(SOURCE, version) }
        exerciseDao.deleteBySource(SOURCE)
        exerciseDao.insertAll(entities)
    }

    companion object {
        const val SOURCE = "free-exercise-db"
        /** Bump when the bundled exercises.json is refreshed to trigger a re-seed. */
        const val VERSION = "2026-06-17"
    }
}
