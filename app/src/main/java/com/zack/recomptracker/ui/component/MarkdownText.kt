package com.zack.recomptracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal markdown renderer for AI chat output. Handles the common subset cloud models emit:
 * **bold**, *italic*, `inline code`, bullet lists (-, *, +), numbered lists (1.), and #/##/###
 * headings. Paragraphs separated by blank lines. Intentionally does NOT treat single underscores
 * as emphasis, so identifiers like `weight_kg` render verbatim. Unclosed markers (common mid-stream)
 * are appended literally rather than dropped.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 14.sp,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = parseInline(block.text),
                    color = color,
                    fontSize = when (block.level) {
                        1 -> 18.sp
                        2 -> 16.sp
                        else -> 15.sp
                    },
                    fontWeight = FontWeight.Bold,
                    lineHeight = fontSize.times(1.4f),
                )

                is MdBlock.Paragraph -> Text(
                    text = parseInline(block.text),
                    color = color,
                    fontSize = fontSize,
                    lineHeight = fontSize.times(1.4f),
                )

                is MdBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = color, fontSize = fontSize, lineHeight = fontSize.times(1.4f))
                    Text(
                        text = parseInline(block.text),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = fontSize.times(1.4f),
                        modifier = Modifier.weight(1f),
                    )
                }

                is MdBlock.Numbered -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${block.number}.", color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold, lineHeight = fontSize.times(1.4f))
                    Text(
                        text = parseInline(block.text),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = fontSize.times(1.4f),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val text: String, val level: Int) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: String, val text: String) : MdBlock
}

private val BULLET_REGEX = Regex("""^[-*+]\s+(.*)""")
private val NUMBERED_REGEX = Regex("""^(\d+)\.\s+(.*)""")

private fun parseMarkdownBlocks(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks.add(MdBlock.Paragraph(paragraph.trim().toString()))
        paragraph.setLength(0)
    }

    src.lines().forEach { raw ->
        val trimmed = raw.trim()
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
    }
    flushParagraph()
    return blocks
}

/** Inline emphasis: **bold**, *italic*, `code`. Unclosed markers are emitted literally. */
private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
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
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22FFFFFF))) {
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
