package com.zack.recomptracker.domain.workout

import com.zack.recomptracker.ui.train.component.categoryForSlug
import com.zack.recomptracker.ui.train.component.highlightFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleCategoryHighlightTest {
    @Test fun armsHighlightsBicepsFrontAndTricepsBack() {
        val h = highlightFor(MuscleCategory.ARMS)
        assertTrue("biceps" in h.front)
        assertTrue("triceps" in h.back)
        assertTrue("forearm" in h.front)
    }

    @Test fun chestHighlightsChestFrontOnly() {
        val h = highlightFor(MuscleCategory.CHEST)
        assertEquals(setOf("chest"), h.front)
        assertTrue(h.back.isEmpty())
    }

    @Test fun tappingBicepsSlugResolvesToArms() {
        assertEquals(MuscleCategory.ARMS, categoryForSlug("biceps"))
        assertEquals(MuscleCategory.BACK, categoryForSlug("upper-back"))
        assertEquals(MuscleCategory.LEGS, categoryForSlug("hamstring"))
        assertEquals(MuscleCategory.CORE, categoryForSlug("abs"))
    }

    @Test fun nonMuscleSlugResolvesToNull() {
        assertEquals(null, categoryForSlug("hair"))
        assertEquals(null, categoryForSlug("head"))
    }
}
