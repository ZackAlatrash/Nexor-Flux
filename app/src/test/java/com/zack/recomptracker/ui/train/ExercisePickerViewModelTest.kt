package com.zack.recomptracker.ui.train

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class ExercisePickerViewModelTest {

    private class FakeExerciseLibraryRepository : ExerciseLibraryRepository(mock<ExerciseDao>()) {
        var lastName: String? = null
        var lastPrimary: List<String>? = null
        override suspend fun addCustomExercise(
            name: String,
            primaryMuscles: List<String>,
            secondaryMuscles: List<String>,
        ): Long {
            lastName = name
            lastPrimary = primaryMuscles
            return 7L
        }
    }

    @Test
    fun `createCustom passes name and selected muscles through to the repository`() = runTest {
        val repo = FakeExerciseLibraryRepository()
        val vm = ExercisePickerViewModel(repository = repo)

        val id = vm.createCustom(name = "Cable Y-Raise", primaryMuscles = listOf("Shoulders"))

        assertEquals(7L, id)
        assertEquals("Cable Y-Raise", repo.lastName)
        assertEquals(listOf("Shoulders"), repo.lastPrimary)
    }
}
