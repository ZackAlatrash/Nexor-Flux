package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleCategoryTest {
    @Test fun mapsArmsFromBicepsAndTriceps() {
        assertEquals(MuscleCategory.ARMS, muscleCategoryFor(listOf("Biceps")))
        assertEquals(MuscleCategory.ARMS, muscleCategoryFor(listOf("Triceps")))
        assertEquals(MuscleCategory.ARMS, muscleCategoryFor(listOf("Forearms")))
    }

    @Test fun mapsBackFromLatsTrapsLowerBack() {
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("Lats")))
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("Middle Back")))
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("Lower Back")))
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("Traps")))
    }

    @Test fun mapsLegsChestShouldersCore() {
        assertEquals(MuscleCategory.LEGS, muscleCategoryFor(listOf("Quadriceps")))
        assertEquals(MuscleCategory.LEGS, muscleCategoryFor(listOf("Hamstrings")))
        assertEquals(MuscleCategory.LEGS, muscleCategoryFor(listOf("Glutes")))
        assertEquals(MuscleCategory.CHEST, muscleCategoryFor(listOf("Chest")))
        assertEquals(MuscleCategory.SHOULDERS, muscleCategoryFor(listOf("Shoulders")))
        assertEquals(MuscleCategory.CORE, muscleCategoryFor(listOf("Abdominals")))
    }

    @Test fun unmappedOrEmptyIsNull() {
        assertNull(muscleCategoryFor(emptyList()))
        assertNull(muscleCategoryFor(listOf("Neck")))
        assertNull(muscleCategoryFor(listOf("Wings")))
    }

    @Test fun isCaseAndSpaceInsensitiveOnFirstEntry() {
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("  middle back ", "biceps")))
    }
}
