package com.zack.recomptracker.ui.component

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Correctness tests for the streaming prefix/tail split in MarkdownText.
 *
 * The optimization only holds if splitting at [safeMarkdownSplit] and parsing the halves
 * independently yields EXACTLY the same render model as parsing the whole string:
 *
 *     buildMarkdown(prefix) + buildMarkdown(tail) == buildMarkdown(text)
 *
 * These tests assert that invariant over the tricky constructs called out in the design: a code
 * fence spanning the split, a list that continues past the split, and a partial bold marker in the
 * volatile tail. They also assert the split never lands inside an open code fence.
 */
class MarkdownSplitTest {

    private val codeBg = Color(0xFF222222)

    /** parse(prefix) ++ parse(tail) must equal parse(whole), whatever the split decides. */
    private fun assertSplitPreservesRender(text: String) {
        val split = safeMarkdownSplit(text)
        val whole = buildMarkdown(text, codeBg)
        val combined = if (split < 0) {
            buildMarkdown(text, codeBg)
        } else {
            val prefix = text.substring(0, split)
            val tail = text.substring(split)
            // No characters lost across the split.
            assertEquals(text, prefix + tail)
            buildMarkdown(prefix, codeBg) + buildMarkdown(tail, codeBg)
        }
        assertEquals(
            "split=$split did not preserve the render model for:\n$text",
            whole,
            combined,
        )
    }

    @Test
    fun `list continuation across a blank-line boundary is preserved`() {
        // A settled list (prefix) followed by a blank line, then a new growing list in the tail.
        val text = "- first item\n- second item\n\n- third item being typed"
        assertSplitPreservesRender(text)
        // The split should occur (there is a blank-line boundary).
        assertTrue(safeMarkdownSplit(text) > 0)
    }

    @Test
    fun `adjacent list lines with no blank line are not split apart`() {
        // Without a blank line there is no closed boundary; must fall back to full parse so the
        // list is never cut mid-run.
        val text = "- one\n- two\n- three"
        assertEquals(-1, safeMarkdownSplit(text))
        assertSplitPreservesRender(text)
    }

    @Test
    fun `partial bold marker at the tail is preserved and rendered literally`() {
        // Settled paragraph, blank line, then a half-typed bold marker (no closing **).
        val text = "All done above.\n\nHere comes **bold that is not closed yet"
        assertSplitPreservesRender(text)
        // The unterminated ** renders literally (the tail parse keeps the raw asterisks).
        val whole = buildMarkdown(text, codeBg)
        val tailPara = whole.last() as MdRender.Paragraph
        assertTrue(tailPara.text.text.contains("**bold that is not closed yet"))
    }

    @Test
    fun `blank line inside an open code fence is never used as a split point`() {
        // The blank line sits INSIDE the unterminated fence — cutting there would break the fence.
        // With the fence still open the splitter must refuse (-1) and the caller full-parses.
        val text = "intro paragraph\n\n```\ncode line one\n\ncode line two still typing"
        assertEquals(-1, safeMarkdownSplit(text))
        assertSplitPreservesRender(text)
    }

    @Test
    fun `closed code fence spanning an earlier boundary still splits safely after it`() {
        // A complete fenced block, then a blank line, then a growing paragraph. The only safe cut is
        // AFTER the closed fence's trailing blank line — never at the blank line inside the fence.
        val text = "```\nline a\n\nline b\n```\n\nnow typing the next paragraph"
        val split = safeMarkdownSplit(text)
        assertTrue("expected a split after the closed fence", split > 0)
        // The split must be past the closing fence, i.e. the prefix contains the whole fence.
        val prefix = text.substring(0, split)
        assertEquals("prefix should contain both fence markers", 2, countFence(prefix))
        assertSplitPreservesRender(text)
    }

    @Test
    fun `no blank line at all yields no split`() {
        assertEquals(-1, safeMarkdownSplit("just one growing line of text"))
        assertEquals(-1, safeMarkdownSplit(""))
    }

    @Test
    fun `trailing blank line does not create an empty tail`() {
        // A blank line at the very end (i == n after it) must NOT be chosen, or the tail would be
        // empty and the split pointless; the earlier interior blank line is the boundary.
        val text = "para one\n\npara two\n"
        val split = safeMarkdownSplit(text)
        if (split >= 0) {
            assertTrue("tail must be non-empty", text.substring(split).isNotEmpty())
        }
        assertSplitPreservesRender(text)
    }

    @Test
    fun `table fully in tail is preserved across a boundary`() {
        val text = "some intro\n\n| A | B |\n| --- | --- |\n| 1 | 2 |"
        assertSplitPreservesRender(text)
    }

    @Test
    fun `heading then streaming paragraph is preserved`() {
        val text = "# Title\n\nbody text growing token by token"
        assertSplitPreservesRender(text)
    }

    private fun countFence(s: String): Int =
        s.lines().count { it.trim().startsWith("```") }
}
