package com.zack.recomptracker.ui.train.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleGroupTest {

    @Test
    fun `maps front-view muscles`() {
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("chest")), muscleTargetFor(listOf("chest")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("deltoids")), muscleTargetFor(listOf("shoulders")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("abs", "obliques")), muscleTargetFor(listOf("abdominals")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("biceps")), muscleTargetFor(listOf("biceps")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("forearm")), muscleTargetFor(listOf("forearms")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("quadriceps")), muscleTargetFor(listOf("quadriceps")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("adductors")), muscleTargetFor(listOf("adductors")))
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("neck")), muscleTargetFor(listOf("neck")))
    }

    @Test
    fun `maps back-view muscles`() {
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("triceps")), muscleTargetFor(listOf("triceps")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("upper-back")), muscleTargetFor(listOf("lats")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("upper-back")), muscleTargetFor(listOf("middle back")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("trapezius")), muscleTargetFor(listOf("traps")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("lower-back")), muscleTargetFor(listOf("lower back")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("gluteal")), muscleTargetFor(listOf("glutes")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("hamstring")), muscleTargetFor(listOf("hamstrings")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("calves")), muscleTargetFor(listOf("calves")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("gluteal")), muscleTargetFor(listOf("abductors")))
    }

    @Test
    fun `uses first muscle, is case and space insensitive`() {
        assertEquals(MuscleTarget(MuscleView.FRONT, setOf("chest")), muscleTargetFor(listOf("  Chest ", "triceps")))
        assertEquals(MuscleTarget(MuscleView.BACK, setOf("upper-back")), muscleTargetFor(listOf("LATS")))
    }

    @Test
    fun `unknown or empty returns null`() {
        assertNull(muscleTargetFor(emptyList()))
        assertNull(muscleTargetFor(listOf("eyeballs")))
        assertNull(muscleTargetFor(listOf("")))
    }
}
