package com.zack.recomptracker.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Enforces the Phase-0 boundary rule (docs/ai-redesign/08-technical-architecture.md §5, invariant #7):
 * the cloud-first coach building blocks — and anything under the new `domain/coach` / `data/coach`
 * packages — must have ZERO compile-time dependency on the legacy on-device stack. This guard keeps a
 * future edit from silently re-coupling the new coach to Gemma/Routing/model-lifecycle code, so the
 * local stack can be deleted in Phase 6 without touching the new system.
 *
 * It works by scanning `import` lines in the guarded source files for forbidden tokens. Pure JVM
 * test — no Android, no model, no network.
 */
class AiCoachBoundaryTest {

    /** Import-line substrings that indicate a dependency on the legacy on-device/model path. */
    private val forbiddenImportTokens = listOf(
        "Gemma",              // Gemma*Coordinator / GemmaInsightService / GemmaServiceHolder
        "Routing",            // RoutingInsightCoordinator / RoutingCoachCoordinator / RoutingRecipeNamer
        "ModelVariant",       // on-device model selector enum
        "AiBackend",          // LOCAL/CLOUD backend toggle + AiCapabilities
        "LocalNameGenerator", // on-device recipe namer
    )

    /**
     * The cloud-first coach path the new AI coach system is (and will be) built on. NOTE: the legacy
     * insight-card generator `CloudInsightCoordinator` is intentionally excluded — it retains a
     * documented, inert `ModelVariant` stub required by the shared interface (removed in Phase 6) and
     * is not part of the new coach's phrasing path.
     */
    private val guardedRelativePaths = listOf(
        "ai/CoachPhrasingService.kt",
        "ai/CoachPhrasingPromptBuilder.kt",
        "ai/CoachTools.kt",
        "ai/CloudCoachCoordinator.kt",
        "ai/CoachToolExecutor.kt",
        "ai/CoachToolsAdapter.kt",
        "ai/WeeklyBriefing.kt",
        "ai/WeeklyBriefingGenerator.kt",
        "ai/WeeklyBriefingPromptBuilder.kt",
    )

    /** Whole new-coach packages that must stay clean once they exist (empty until Phase 2). */
    private val guardedNewPackages = listOf("domain/coach", "data/coach")

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
            .filter { it.trimStart().startsWith("import ") }
            .filter { line -> forbiddenImportTokens.any { it in line } }
            .map { "${file.name}: ${it.trim()}" }
    }

    @Test
    fun `cloud-first coach path does not import the legacy local stack`() {
        val root = sourceRoot()
        // Fail loudly if a guarded file was renamed/moved so the guard silently stops covering it.
        val missing = guardedRelativePaths.filterNot { File(root, it).isFile }
        assertTrue("Guarded files not found (rename? update the guard): $missing", missing.isEmpty())

        val violations = guardedRelativePaths.flatMap { violationsIn(File(root, it)) }
        if (violations.isNotEmpty()) {
            fail("New coach cloud path must not depend on the legacy on-device stack:\n" +
                violations.joinToString("\n"))
        }
    }

    @Test
    fun `new coach packages stay free of local-stack imports`() {
        val root = sourceRoot()
        val violations = guardedNewPackages
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.flatMap { violationsIn(it) } }
        if (violations.isNotEmpty()) {
            fail("domain/coach & data/coach must not depend on the legacy on-device stack:\n" +
                violations.joinToString("\n"))
        }
    }
}
