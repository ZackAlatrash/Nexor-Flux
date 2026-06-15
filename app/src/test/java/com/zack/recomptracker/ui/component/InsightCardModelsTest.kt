package com.zack.recomptracker.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightCardModelsTest {
    @Test fun highPriorityMapsToHigh() {
        assertEquals(ConfidenceLevel.HIGH, confidenceFrom(3))
        assertEquals(ConfidenceLevel.HIGH, confidenceFrom(10))
    }

    @Test fun midPriorityMapsToMedium() {
        assertEquals(ConfidenceLevel.MEDIUM, confidenceFrom(1))
        assertEquals(ConfidenceLevel.MEDIUM, confidenceFrom(2))
    }

    @Test fun nonPositivePriorityIsNull() {
        assertNull(confidenceFrom(0))
        assertNull(confidenceFrom(-5))
    }
}
