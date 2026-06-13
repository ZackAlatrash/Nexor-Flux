package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingRecipeNamerTest {

    private fun ings() = listOf(
        RecipeIngredientEntity(name = "Oats", calories = 300, proteinG = 10.0, carbsG = 50.0, fatG = 6.0),
    )

    private fun namer(
        backend: AiBackend = AiBackend.LOCAL,
        local: NameGenerator,
        cloud: NameGenerator,
        timeoutMs: Long = 1_000,
    ) = RoutingRecipeNamer(
        local = local,
        cloud = cloud,
        effectiveBackend = MutableStateFlow(backend),
        available = MutableStateFlow(true),
        timeoutMs = timeoutMs,
    )

    @Test
    fun `routes to local generator when backend is LOCAL`() = runTest {
        var localCalled = false
        val result = namer(
            backend = AiBackend.LOCAL,
            local = NameGenerator { _, _ -> localCalled = true; "Anabolic Oats" },
            cloud = NameGenerator { _, _ -> "WRONG" },
        ).generate(ings(), MacroTotals())
        assertTrue(localCalled)
        assertEquals("Anabolic Oats", result.getOrNull())
    }

    @Test
    fun `routes to cloud generator when backend is CLOUD`() = runTest {
        val result = namer(
            backend = AiBackend.CLOUD,
            local = NameGenerator { _, _ -> "WRONG" },
            cloud = NameGenerator { _, _ -> "\"Cloud Cluck\"" },
        ).generate(ings(), MacroTotals())
        assertEquals("Cloud Cluck", result.getOrNull()) // sanitized
    }

    @Test
    fun `generator exception becomes failure`() = runTest {
        val result = namer(
            local = NameGenerator { _, _ -> throw RuntimeException("boom") },
            cloud = NameGenerator { _, _ -> "x" },
        ).generate(ings(), MacroTotals())
        assertTrue(result.isFailure)
    }

    @Test
    fun `blank name becomes failure`() = runTest {
        val result = namer(
            local = NameGenerator { _, _ -> "   " },
            cloud = NameGenerator { _, _ -> "x" },
        ).generate(ings(), MacroTotals())
        assertTrue(result.isFailure)
    }

    @Test
    fun `timeout becomes failure`() = runTest {
        val result = namer(
            local = NameGenerator { _, _ -> delay(10_000); "too late" },
            cloud = NameGenerator { _, _ -> "x" },
            timeoutMs = 50,
        ).generate(ings(), MacroTotals())
        assertTrue(result.isFailure)
    }
}
