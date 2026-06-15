package com.zack.recomptracker.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightCardModelsTest {
    @Test fun highPriorityMapsToHigh() {
        assertEquals(ConfidenceLevel.HIGH, confidenceFrom(30))
        assertEquals(ConfidenceLevel.HIGH, confidenceFrom(45))
    }

    @Test fun midPriorityMapsToMedium() {
        assertEquals(ConfidenceLevel.MEDIUM, confidenceFrom(10))
        assertEquals(ConfidenceLevel.MEDIUM, confidenceFrom(29))
    }

    @Test fun nonPositivePriorityIsNull() {
        assertNull(confidenceFrom(0))
        assertNull(confidenceFrom(-5))
    }
}
