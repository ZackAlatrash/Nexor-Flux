package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.zack.recomptracker.ui.theme.LocalAppColors
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal markdown renderer for AI chat output. Handles the common subset cloud models emit:
 * **bold**, *italic*, `inline code`, bullet lists (-, *, +), numbered lists (1.), and #/##/###
 * headings. Paragraphs separated by blank lines. Intentionally does NOT treat single underscores
 * as emphasis, so identifiers like `weight_kg` render verbatim. Unclosed markers (common mid-stream)
 * are appended literally rather than dropped.
 *
 * ## Parsing cost model
 *
 * The FULL parse — block splitting AND per-block inline styling ([parseInline]) — is folded into a
 * single memoized [buildMarkdown] result. A recomposition with an unchanged [text] therefore does
 * zero parsing work (previously `parseInline` re-ran char-by-char for every block on every
 * recomposition).
 *
 * ## Streaming cost model
 *
 * While a message streams in token-by-token, re-parsing the whole accumulated string each token is
 * O(n²) over the message. Instead the text is split at the last *closed block boundary* (a blank
 * line that is not inside an open ``` code fence) into a stable `prefix` (complete blocks) and a
 * volatile `tail` (the still-growing block). The prefix is memoized on its own value, so only the
 * short tail re-parses per token. The split preserves rendering exactly: because every block
 * construct in this grammar terminates at a blank line, `buildMarkdown(prefix) + buildMarkdown(tail)`
 * is identical to `buildMarkdown(prefix + tail)` (see [safeMarkdownSplit]). When no safe boundary
 * exists the code falls back to a single full parse memoized on `text`.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 14.sp,
) {
    val appColors = LocalAppColors.current
    val codeBackground = appColors.cardSurface

    // Split into a stable prefix (fully-formed blocks) + volatile tail (the growing block). Each
    // half is memoized independently, so a streaming update re-parses only the tail. `split == -1`
    // means "no safe boundary" → parse the whole thing (still memoized on `text`).
    val split = remember(text) { safeMarkdownSplit(text) }
    val prefixText = if (split >= 0) text.substring(0, split) else text
    val tailText = if (split >= 0) text.substring(split) else ""

    val prefixBlocks = remember(prefixText, codeBackground) { buildMarkdown(prefixText, codeBackground) }
    val tailBlocks = remember(tailText, codeBackground) {
        if (tailText.isEmpty()) emptyList() else buildMarkdown(tailText, codeBackground)
    }
    val blocks = if (tailBlocks.isEmpty()) prefixBlocks else prefixBlocks + tailBlocks

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdRender.Heading -> Text(
                    text = block.text,
                    color = color,
                    fontSize = when (block.level) {
                        1 -> 18.sp
                        2 -> 16.sp
                        else -> 15.sp
                    },
                    fontWeight = FontWeight.Bold,
                    lineHeight = fontSize.times(1.4f),
                )

                is MdRender.Paragraph -> Text(
                    text = block.text,
                    color = color,
                    fontSize = fontSize,
                    lineHeight = fontSize.times(1.4f),
                )

                is MdRender.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = color, fontSize = fontSize, lineHeight = fontSize.times(1.4f))
                    Text(
                        text = block.text,
                        color = color,
                        fontSize = fontSize,
                        lineHeight = fontSize.times(1.4f),
                        modifier = Modifier.weight(1f),
                    )
                }

                is MdRender.Numbered -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${block.number}.", color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold, lineHeight = fontSize.times(1.4f))
                    Text(
                        text = block.text,
                        color = color,
                        fontSize = fontSize,
                        lineHeight = fontSize.times(1.4f),
                        modifier = Modifier.weight(1f),
                    )
                }

                is MdRender.Table -> MarkdownTable(block, color, fontSize)
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MdRender.Table, color: Color, fontSize: TextUnit) {
    // Size each column to its longest cell (estimated) so columns align and the table keeps
    // its natural width. When that exceeds the bubble, the table scrolls horizontally while
    // the rest of the message stays at normal reading width.
    val appColors = LocalAppColors.current
    val colWidths: List<Dp> = remember(table) {
        val colCount = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
        val allRows = listOf(table.header) + table.rows
        (0 until colCount).map { c ->
            val maxLen = allRows.maxOf { (it.getOrNull(c)?.length ?: 0) }
            (maxLen * 8 + 16).dp.coerceIn(56.dp, 200.dp)
        }
    }
    val tableWidth = colWidths.fold(0.dp) { acc, w -> acc + w }

    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier
                .width(tableWidth)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, appColors.cardBorder, RoundedCornerShape(8.dp)),
        ) {
            MarkdownTableRow(table.header, table.aligns, colWidths, color, fontSize, header = true, headerFill = appColors.cardBorder)
            table.rows.forEach { row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(appColors.cardBorder))
                MarkdownTableRow(row, table.aligns, colWidths, color, fontSize, header = false, headerFill = appColors.cardBorder)
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<AnnotatedString>,
    aligns: List<TextAlign>,
    colWidths: List<Dp>,
    color: Color,
    fontSize: TextUnit,
    header: Boolean,
    headerFill: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (header) headerFill else Color.Transparent),
    ) {
        colWidths.forEachIndexed { c, w ->
            Text(
                text = cells.getOrNull(c) ?: AnnotatedString(""),
                color = color,
                fontSize = fontSize,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                textAlign = aligns.getOrNull(c) ?: TextAlign.Start,
                lineHeight = fontSize.times(1.35f),
                modifier = Modifier
                    .width(w)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

// ── Render model ──────────────────────────────────────────────────────────────
// Render-ready blocks: the inline emphasis parse ([parseInline]) is already folded into the
// AnnotatedString(s) here, so composition just lays them out. Colour/fontSize still come from the
// call site (they are theme/size inputs, not part of the parse), so only `codeBackground` — the one
// style the AnnotatedString bakes in — is a parse input.

internal sealed interface MdRender {
    data class Heading(val text: AnnotatedString, val level: Int) : MdRender
    data class Paragraph(val text: AnnotatedString) : MdRender
    data class Bullet(val text: AnnotatedString) : MdRender
    data class Numbered(val number: String, val text: AnnotatedString) : MdRender
    data class Table(
        val header: List<AnnotatedString>,
        val rows: List<List<AnnotatedString>>,
        val aligns: List<TextAlign>,
    ) : MdRender
}

private sealed interface MdBlock {
    data class Heading(val text: String, val level: Int) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: String, val text: String) : MdBlock
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val aligns: List<TextAlign>,
    ) : MdBlock
}

/**
 * Full parse: split [src] into blocks and fold each block's inline emphasis into render-ready
 * [MdRender] values. Pure (no Compose), so it is memoizable and unit-testable.
 */
internal fun buildMarkdown(src: String, codeBackground: Color): List<MdRender> =
    parseMarkdownBlocks(src).map { block ->
        when (block) {
            is MdBlock.Heading -> MdRender.Heading(parseInline(block.text, codeBackground), block.level)
            is MdBlock.Paragraph -> MdRender.Paragraph(parseInline(block.text, codeBackground))
            is MdBlock.Bullet -> MdRender.Bullet(parseInline(block.text, codeBackground))
            is MdBlock.Numbered -> MdRender.Numbered(block.number, parseInline(block.text, codeBackground))
            is MdBlock.Table -> MdRender.Table(
                header = block.header.map { parseInline(it, codeBackground) },
                rows = block.rows.map { row -> row.map { parseInline(it, codeBackground) } },
                aligns = block.aligns,
            )
        }
    }

private val BULLET_REGEX = Regex("""^[-*+]\s+(.*)""")
private val NUMBERED_REGEX = Regex("""^(\d+)\.\s+(.*)""")
private const val FENCE = "```"

private fun parseMarkdownBlocks(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks.add(MdBlock.Paragraph(paragraph.trim().toString()))
        paragraph.setLength(0)
    }

    val lines = src.lines()
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trim()

        // GFM table: a row line immediately followed by a separator row (|---|---|).
        if (trimmed.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1].trim())) {
            flushParagraph()
            val header = parseTableRow(trimmed)
            val aligns = parseAlignments(lines[i + 1].trim())
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                rows.add(parseTableRow(lines[i].trim()))
                i++
            }
            blocks.add(MdBlock.Table(header, rows, aligns))
            continue
        }

        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed.startsWith("### ") -> { flushParagraph(); blocks.add(MdBlock.Heading(trimmed.removePrefix("### ").trim(), 3)) }
            trimmed.startsWith("## ") -> { flushParagraph(); blocks.add(MdBlock.Heading(trimmed.removePrefix("## ").trim(), 2)) }
            trimmed.startsWith("# ") -> { flushParagraph(); blocks.add(MdBlock.Heading(trimmed.removePrefix("# ").trim(), 1)) }
            NUMBERED_REGEX.matches(trimmed) -> {
                flushParagraph()
                val m = NUMBERED_REGEX.find(trimmed)!!
                blocks.add(MdBlock.Numbered(m.groupValues[1], m.groupValues[2]))
            }
            BULLET_REGEX.matches(trimmed) -> {
                flushParagraph()
                blocks.add(MdBlock.Bullet(BULLET_REGEX.find(trimmed)!!.groupValues[1]))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

/**
 * Index at which to split [text] into a stable prefix + volatile tail for streaming, or -1 if there
 * is no safe split point.
 *
 * Returns an index `k` such that `text.substring(0, k)` (the prefix) contains only fully-formed,
 * closed blocks and `text.substring(k)` (the tail) is the still-growing remainder, with the
 * guarantee `buildMarkdown(prefix) + buildMarkdown(tail) == buildMarkdown(text)`.
 *
 * The split point is placed immediately after the newline that terminates the last **blank line**
 * that is NOT inside an open ``` code fence. Every block construct in this grammar (paragraph,
 * list item, table) terminates at a blank line, so cutting there leaves the prefix's blocks closed
 * and the tail parseable on its own. Fence parity is tracked so we never cut inside a fenced block
 * (whose blank lines are content, not boundaries); if the final fence is still open we refuse to
 * split (return -1) and the caller full-parses.
 */
internal fun safeMarkdownSplit(text: String): Int {
    if (text.isEmpty()) return -1
    var i = 0
    var lineStart = 0
    var fenceOpen = false
    var lastSafeSplit = -1
    val n = text.length
    while (i <= n) {
        if (i == n || text[i] == '\n') {
            // [lineStart, i) is one line (excluding the '\n').
            val line = text.substring(lineStart, i)
            val trimmed = line.trim()
            if (trimmed.startsWith(FENCE)) {
                // A fence marker toggles fenced state. It is itself a boundary line, but the region
                // it opens/closes is only safe to cut once the fence is balanced (closed).
                fenceOpen = !fenceOpen
            } else if (trimmed.isEmpty() && !fenceOpen && i < n) {
                // A blank line outside any open fence closes the preceding block. Safe to cut just
                // after this line's terminating newline (i points at the '\n', so +1). Require a
                // real newline here (i < n) so the tail is non-empty.
                lastSafeSplit = i + 1
            }
            if (i == n) break
            lineStart = i + 1
        }
        i++
    }
    // If the whole text is one big open fence (or has no safe blank-line boundary) → no split.
    if (fenceOpen) return -1
    return lastSafeSplit
}

private val SEPARATOR_CELL_REGEX = Regex("""^:?-{1,}:?$""")

/** A markdown table separator row: every pipe-delimited cell is dashes with optional colons. */
private fun isTableSeparator(line: String): Boolean {
    if (!line.contains('-') || !line.contains('|')) return false
    val cells = line.trim().trim('|').split('|')
    return cells.isNotEmpty() && cells.all { SEPARATOR_CELL_REGEX.matches(it.trim()) }
}

private fun parseTableRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private fun parseAlignments(separator: String): List<TextAlign> =
    separator.trim().trim('|').split('|').map { raw ->
        val s = raw.trim()
        val left = s.startsWith(":")
        val right = s.endsWith(":")
        when {
            left && right -> TextAlign.Center
            right -> TextAlign.End
            else -> TextAlign.Start
        }
    }

/** Inline emphasis: **bold**, *italic*, `code`. Unclosed markers are emitted literally. */
private fun parseInline(text: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = text.length
    while (i < n) {
        val c = text[i]
        when {
            c == '*' && i + 1 < n && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(c); i++ }
            }
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(c); i++ }
            }
            c == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(c); i++ }
            }
            else -> { append(c); i++ }
        }
    }
}
