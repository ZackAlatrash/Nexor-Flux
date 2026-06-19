package com.zack.recomptracker.domain.workout

import com.zack.recomptracker.ui.train.component.BodyMapGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyMapGeometryTest {
    @Test fun scalesToFitNarrowerDimensionAndCenters() {
        // content 100x200 into canvas 100x100 -> scale 0.5, content drawn 50x100, centered horizontally
        val t = BodyMapGeometry.fit(contentLeft = 0f, contentTop = 0f, contentRight = 100f, contentBottom = 200f, canvasW = 100f, canvasH = 100f)
        assertEquals(0.5f, t.scale, 0.0001f)
        assertEquals(25f, t.dx, 0.0001f) // (100 - 100*0.5)/2 - 0*0.5
        assertEquals(0f, t.dy, 0.0001f)
    }

    @Test fun forwardAndInverseRoundTrip() {
        val t = BodyMapGeometry.fit(10f, 20f, 110f, 220f, 300f, 300f)
        val sx = 42f * t.scale + t.dx
        val sy = 88f * t.scale + t.dy
        assertEquals(42f, t.toContentX(sx), 0.001f)
        assertEquals(88f, t.toContentY(sy), 0.001f)
    }
}
