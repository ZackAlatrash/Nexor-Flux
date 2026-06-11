package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyReviewEntitySerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trips including new briefing fields`() {
        val entity = WeeklyReviewEntity(
            weekStart = "2026-06-08", verdict = "HOLD", recommendedCalorieChange = 0,
            reasonCodes = "X", generatedAt = "t",
            briefingJson = "{}", briefingSignature = "sig", briefingGeneratedAt = "cached",
        )
        val decoded = json.decodeFromString(
            WeeklyReviewEntity.serializer(),
            json.encodeToString(WeeklyReviewEntity.serializer(), entity),
        )
        assertEquals(entity, decoded)
    }

    @Test
    fun `old backup without briefing fields decodes with nulls`() {
        val old = """{"weekStart":"2026-06-01","verdict":"HOLD","recommendedCalorieChange":0,
            "reasonCodes":"X","generatedAt":"t"}"""
        val decoded = json.decodeFromString(WeeklyReviewEntity.serializer(), old)
        assertNull(decoded.briefingJson)
        assertNull(decoded.briefingSignature)
    }
}
