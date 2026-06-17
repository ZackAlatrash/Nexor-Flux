package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLibraryJsonTest {

    private val sample = """
    [
      {
        "id": "Barbell_Squat",
        "name": "Barbell Squat",
        "force": "push",
        "level": "intermediate",
        "mechanic": "compound",
        "equipment": "barbell",
        "category": "strength",
        "primaryMuscles": ["quadriceps"],
        "secondaryMuscles": ["glutes", "hamstrings"],
        "instructions": ["Step 1.", "Step 2."],
        "images": ["Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"]
      },
      {
        "id": "Bodyweight_Plank",
        "name": "Plank",
        "level": "beginner",
        "category": "strength",
        "primaryMuscles": ["abdominals"],
        "secondaryMuscles": [],
        "instructions": ["Hold."],
        "images": []
      }
    ]
    """.trimIndent()

    @Test
    fun `parses array into entities mapping id to externalId`() {
        val entities = ExerciseLibraryJson.parse(sample)
            .map { it.toEntity(source = "free-exercise-db", sourceVersion = "v1") }

        assertEquals(2, entities.size)
        val squat = entities[0]
        assertEquals("free-exercise-db", squat.source)
        assertEquals("v1", squat.sourceVersion)
        assertEquals("Barbell_Squat", squat.externalId)
        assertEquals("Barbell Squat", squat.name)
        assertEquals("compound", squat.mechanic)
        assertEquals(false, squat.userCreated)
    }

    @Test
    fun `encodes and decodes list columns round-trip`() {
        val squat = ExerciseLibraryJson.parse(sample).first().toEntity("free-exercise-db", "v1")

        assertEquals(listOf("quadriceps"), ExerciseLibraryJson.decodeList(squat.primaryMuscles))
        assertEquals(listOf("glutes", "hamstrings"), ExerciseLibraryJson.decodeList(squat.secondaryMuscles))
        assertEquals(listOf("Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"), ExerciseLibraryJson.decodeList(squat.images))
    }

    @Test
    fun `tolerates missing optional fields`() {
        val plank = ExerciseLibraryJson.parse(sample)[1].toEntity("free-exercise-db", "v1")

        assertNull(plank.force)
        assertNull(plank.mechanic)
        assertNull(plank.equipment)
        assertEquals("beginner", plank.level)
        assertTrue(ExerciseLibraryJson.decodeList(plank.images).isEmpty())
    }

    @Test
    fun `decodeList returns empty for blank`() {
        assertTrue(ExerciseLibraryJson.decodeList("").isEmpty())
    }
}
