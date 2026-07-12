package com.zack.recomptracker.domain.share

import com.zack.recomptracker.domain.workout.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineShareResolverTest {

    private fun lib(id: Long, externalId: String, name: String) = Exercise(
        id = id, externalId = externalId, name = name, category = null, force = null, level = null,
        mechanic = null, equipment = null, primaryMuscles = emptyList(), secondaryMuscles = emptyList(),
        instructions = emptyList(), images = emptyList(), userCreated = false,
    )

    private fun shared(externalId: String, name: String) =
        SharedExercise(source = "free-exercise-db", externalId = externalId, name = name, sets = emptyList())

    private val library = listOf(
        lib(10, "Barbell_Bench_Press", "Barbell Bench Press"),
        lib(20, "Squat", "Squat"),
    )

    @Test
    fun `matches by externalId first`() {
        val r = RoutineShareResolver.resolve(listOf(shared("Barbell_Bench_Press", "wrong name")), library)
        assertEquals(listOf(Resolution(shared("Barbell_Bench_Press", "wrong name"), existingId = 10L)), r)
    }

    @Test
    fun `falls back to case-insensitive name`() {
        val r = RoutineShareResolver.resolve(listOf(shared("unknown_ext", "squat")), library)
        assertEquals(20L, r.single().existingId)
    }

    @Test
    fun `flags an unknown exercise for creation`() {
        val r = RoutineShareResolver.resolve(listOf(shared("user_novel", "Novel Move")), library)
        assertEquals(null, r.single().existingId)
        assertEquals(true, r.single().willCreate)
    }

    @Test
    fun `preserves input order`() {
        val input = listOf(shared("Squat", "Squat"), shared("Barbell_Bench_Press", "Barbell Bench Press"))
        val r = RoutineShareResolver.resolve(input, library)
        assertEquals(listOf(20L, 10L), r.map { it.existingId })
    }
}
