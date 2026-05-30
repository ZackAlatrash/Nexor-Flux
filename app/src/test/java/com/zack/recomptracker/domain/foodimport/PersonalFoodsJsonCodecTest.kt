package com.zack.recomptracker.domain.foodimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PersonalFoodsJsonCodecTest {
    private val codec = PersonalFoodsJsonCodec { "2026-05-30T12:00:00Z" }
    private val food = FoodImportCandidate(null, "Greek yogurt", "250g", 160, 25.0, 10.0, 2.0)

    @Test
    fun roundTripsPersonalFoodsWithoutRoomIds() {
        val decoded = codec.decode(codec.encode(listOf(food)))

        assertEquals(1, decoded.version)
        assertEquals("2026-05-30T12:00:00Z", decoded.exportedAt)
        assertEquals(listOf(food), decoded.foods)
    }

    @Test
    fun rejectsUnsupportedPayloadVersion() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            codec.decode("""{"version":2,"exportedAt":"now","foods":[]}""")
        }

        assertEquals("Unsupported personal-food JSON version: 2", error.message)
    }

    @Test
    fun rejectsNegativeMacrosBeforeImport() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            codec.decode(
                """{"version":1,"exportedAt":"now","foods":[{"name":"Bad","servingName":"100g","calories":10,"proteinG":-1.0,"carbsG":0.0,"fatG":0.0}]}""",
            )
        }

        assertEquals("Personal food 'Bad' has negative macro values.", error.message)
    }
}
