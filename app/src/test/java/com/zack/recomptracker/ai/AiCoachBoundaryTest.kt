package com.zack.recomptracker.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cloud-only guard (Phase 8). The entire on-device/Gemma/LiteRT/routing stack was deleted; the app
 * is now cloud-only for AI. This test keeps a future edit from re-introducing that stack: it scans
 * the whole `ai` package plus the `domain/coach` / `data/coach` packages for the forbidden symbols
 * and fails if any reappears.
 *
 * Pure JVM test — no Android, no model, no network.
 */
class AiCoachBoundaryTest {

    /** Symbols that belonged to the deleted local stack. None may reappear anywhere in the scan set. */
    private val forbiddenTokens = listOf(
        "Gemma",              // Gemma*Coordinator / GemmaInsightService / GemmaServiceHolder
        "RoutingCoachCoordinator",
        "RoutingInsightCoordinator",
        "RoutingRecipeNamer",
        "ModelVariant",       // on-device model selector enum
        "AiBackend",          // LOCAL/CLOUD backend toggle
        "AiCapabilities",     // per-backend capability flags
        "LocalNameGenerator", // on-device recipe namer
    )

    /** Packages that must stay free of the deleted local stack. */
    private val guardedPackages = listOf("ai", "domain/coach", "data/coach")

    private fun sourceRoot(): File {
        // testDebugUnitTest runs with the module dir as cwd, but be robust to either.
        val candidates = listOf(
            File("src/main/java/com/zack/recomptracker"),
            File("app/src/main/java/com/zack/recomptracker"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate the source root from ${File(".").absolutePath}")
    }

    private fun violationsIn(file: File): List<String> {
        if (!file.isFile) return emptyList()
        return file.readLines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .filter { line -> forbiddenTokens.any { it in line } }
            .map { "${file.name}: ${it.trim()}" }
    }

    @Test
    fun `ai and coach packages contain no local-stack symbols`() {
        val root = sourceRoot()
        val violations = guardedPackages
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.flatMap { violationsIn(it) } }
        assertTrue(
            "The on-device/local AI stack was removed in Phase 8 and must not return:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `deleted local-stack source files do not exist`() {
        val root = File(sourceRoot(), "ai")
        val deleted = listOf(
            "GemmaInsightCoordinator.kt",
            "GemmaCoachCoordinator.kt",
            "GemmaInsightService.kt",
            "GemmaServiceHolder.kt",
            "RoutingInsightCoordinator.kt",
            "RoutingCoachCoordinator.kt",
            "RoutingRecipeNamer.kt",
            "AiBackend.kt",
            "ModelVariant.kt",
        )
        val stillPresent = deleted.filter { File(root, it).exists() }
        assertTrue("These files should have been deleted in Phase 8: $stillPresent", stillPresent.isEmpty())
    }
}
