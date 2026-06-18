package com.zack.recomptracker.ui.train.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleGroupLabelTest {

    @Test
    fun `direct group names`() {
        assertEquals("Chest", muscleGroupLabel(listOf("chest")))
        assertEquals("Shoulders", muscleGroupLabel(listOf("shoulders")))
        assertEquals("Biceps", muscleGroupLabel(listOf("biceps")))
        assertEquals("Triceps", muscleGroupLabel(listOf("triceps")))
        assertEquals("Forearms", muscleGroupLabel(listOf("forearms")))
        assertEquals("Abs", muscleGroupLabel(listOf("abdominals")))
        assertEquals("Glutes", muscleGroupLabel(listOf("glutes")))
        assertEquals("Traps", muscleGroupLabel(listOf("traps")))
        assertEquals("Neck", muscleGroupLabel(listOf("neck")))
    }

    @Test
    fun `back muscles group to Back`() {
        assertEquals("Back", muscleGroupLabel(listOf("lats")))
        assertEquals("Back", muscleGroupLabel(listOf("middle back")))
        assertEquals("Back", muscleGroupLabel(listOf("lower back")))
    }

    @Test
    fun `lower-body muscles group to Legs`() {
        assertEquals("Legs", muscleGroupLabel(listOf("quadriceps")))
        assertEquals("Legs", muscleGroupLabel(listOf("hamstrings")))
        assertEquals("Legs", muscleGroupLabel(listOf("calves")))
        assertEquals("Legs", muscleGroupLabel(listOf("adductors")))
        assertEquals("Legs", muscleGroupLabel(listOf("abductors")))
    }

    @Test
    fun `uses first, case and space insensitive`() {
        assertEquals("Chest", muscleGroupLabel(listOf("  Chest ", "triceps")))
        assertEquals("Back", muscleGroupLabel(listOf("LATS")))
    }

    @Test
    fun `unknown or empty returns null`() {
        assertNull(muscleGroupLabel(emptyList()))
        assertNull(muscleGroupLabel(listOf("eyeballs")))
        assertNull(muscleGroupLabel(listOf("")))
    }
}
