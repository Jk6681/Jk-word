package com.example.jkword.model

import java.util.UUID

/**
 * Core rich-text document data model for JK word.
 */
data class DocumentModel(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Document",
    val blocks: List<DocumentBlock> = listOf(ParagraphBlock()),
    val pageSettings: PageLayoutSettings = PageLayoutSettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

sealed class DocumentBlock {
    abstract val id: String
}

data class ParagraphBlock(
    override val id: String = UUID.randomUUID().toString(),
    val runs: List<TextRun> = listOf(TextRun("")),
    val style: ParagraphStyle = ParagraphStyle.NORMAL,
    val alignment: BlockAlignment = BlockAlignment.LEFT,
    val listType: ListType = ListType.NONE,
    val listLevel: Int = 0,
    val lineSpacing: Float = 1.15f
) : DocumentBlock() {
    val plainText: String
        get() = when (runs.size) {
            0 -> ""
            1 -> runs[0].text
            else -> {
                val sb = StringBuilder()
                for (r in runs) sb.append(r.text)
                sb.toString()
            }
        }

    val charCount: Int
        get() = runs.sumOf { it.text.length }
}

data class TableBlock(
    override val id: String = UUID.randomUUID().toString(),
    val rows: Int = 2,
    val cols: Int = 2,
    val cells: List<List<TableCell>> = createDefaultCells(rows, cols)
) : DocumentBlock() {
    companion object {
        fun createDefaultCells(rows: Int, cols: Int): List<List<TableCell>> {
            return List(rows) { r ->
                List(cols) { c ->
                    TableCell(
                        text = if (r == 0) "Header ${c + 1}" else "Data",
                        isHeader = (r == 0)
                    )
                }
            }
        }
    }
}

data class TableCell(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isHeader: Boolean = false,
    val backgroundColorHex: String? = null,
    val isBold: Boolean = false,
    val alignment: BlockAlignment = BlockAlignment.LEFT
)

data class ImageBlock(
    override val id: String = UUID.randomUUID().toString(),
    val uriOrPath: String,
    val widthDp: Float = 260f,
    val heightDp: Float = 180f,
    val alignment: BlockAlignment = BlockAlignment.CENTER,
    val caption: String? = null
) : DocumentBlock()

data class HorizontalRuleBlock(
    override val id: String = UUID.randomUUID().toString()
) : DocumentBlock()

data class PageBreakBlock(
    override val id: String = UUID.randomUUID().toString()
) : DocumentBlock()

data class TextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isSuperscript: Boolean = false,
    val isSubscript: Boolean = false,
    val fontSizePt: Float = 11f,
    val fontColorHex: String = "#1A1A1A",
    val highlightHex: String? = null,
    val fontFamily: String = "Calibri",
    val linkUrl: String? = null
)

enum class ParagraphStyle(val displayName: String, val defaultSizePt: Float, val isBold: Boolean) {
    NORMAL("Normal", 11f, false),
    TITLE("Title", 26f, true),
    SUBTITLE("Subtitle", 16f, false),
    HEADING_1("Heading 1", 20f, true),
    HEADING_2("Heading 2", 16f, true),
    HEADING_3("Heading 3", 14f, true),
    HEADING_4("Heading 4", 12f, true),
    QUOTE("Quote", 11f, false),
    CAPTION("Caption", 9f, false)
}

enum class BlockAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

enum class ListType {
    NONE,
    BULLET,
    NUMBERED
}

data class PageLayoutSettings(
    val pageSize: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val margins: PageMargins = PageMargins.NORMAL,
    val headerText: String = "",
    val footerText: String = "",
    val showPageNumbers: Boolean = true,
    val pageNumberPosition: PageNumberPosition = PageNumberPosition.BOTTOM_RIGHT
)

enum class PageSize(
    val displayName: String,
    val widthPt: Int,
    val heightPt: Int,
    val shortLabel: String
) {
    A4("A4 (210 × 297 mm)", 595, 842, "A4"),
    EIGHT_BY_THIRTEEN("8 × 13 Folio (203 × 330 mm)", 576, 936, "8×13"),
    LEGAL("Legal (8.5 × 14 in)", 612, 1008, "Legal"),
    LETTER("Letter (8.5 × 11 in)", 612, 792, "Letter"),
    EXECUTIVE("Executive (7.25 × 10.5 in)", 522, 756, "Executive"),
    A5("A5 (148 × 210 mm)", 420, 595, "A5"),
    A3("A3 (297 × 420 mm)", 842, 1191, "A3"),
    STATEMENT("Statement (5.5 × 8.5 in)", 396, 612, "Statement"),
    TABLOID("Tabloid (11 × 17 in)", 792, 1224, "Tabloid");

    fun getAspectRatio(orientation: PageOrientation): Float {
        return if (orientation == PageOrientation.PORTRAIT) {
            widthPt.toFloat() / heightPt.toFloat()
        } else {
            heightPt.toFloat() / widthPt.toFloat()
        }
    }
}

enum class PageOrientation {
    PORTRAIT,
    LANDSCAPE
}

data class PageMargins(
    val displayName: String,
    val topPt: Int,
    val bottomPt: Int,
    val leftPt: Int,
    val rightPt: Int
) {
    companion object {
        val NORMAL = PageMargins("Normal (1 in)", 72, 72, 72, 72)
        val NARROW = PageMargins("Narrow (0.5 in)", 36, 36, 36, 36)
        val MODERATE = PageMargins("Moderate (0.75 in)", 54, 54, 54, 54)
        val WIDE = PageMargins("Wide (1.5 in)", 72, 72, 108, 108)
    }
}

enum class PageNumberPosition {
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    TOP_RIGHT
}

/**
 * Plain-text import helper (pure, unit-testable): split imported text into
 * paragraphs ONLY on blank lines. Single newlines stay inside a paragraph as
 * line breaks, and Windows \r\n endings are normalized first. The old code
 * split on every "\n", turning each wrapped line into its own paragraph.
 */
fun splitPlainTextParagraphs(text: String): List<String> {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    return normalized.split(Regex("\\n[ \\t]*\\n"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

/**
 * Numbered-list numbering (pure, unit-testable). One counter sequence per
 * indent level: a deeper level counts on its own, and any non-numbered block
 * (or a different list) restarts every level at 1.
 */
object ListNumbering {
    fun prefixFor(level: Int, number: Int): String = when (level) {
        1 -> "${toLetters(number)}. "
        2 -> "${toRoman(number)}. "
        else -> "$number. "
    }

    class Counter {
        private val counts = mutableMapOf<Int, Int>()

        fun next(level: Int): Int {
            val n = (counts[level] ?: 0) + 1
            counts[level] = n
            // Deeper levels restart when a shallower level advances.
            counts.keys.filter { it > level }.forEach { counts.remove(it) }
            return n
        }

        fun reset() = counts.clear()
    }

    private fun toLetters(n: Int): String {
        var value = n.coerceAtLeast(1)
        val sb = StringBuilder()
        while (value > 0) {
            value--
            sb.append('a' + (value % 26))
            value /= 26
        }
        return sb.reverse().toString()
    }

    private fun toRoman(n: Int): String {
        var value = n.coerceAtLeast(1)
        val table = listOf(
            1000 to "m", 900 to "cm", 500 to "d", 400 to "cd",
            100 to "c", 90 to "xc", 50 to "l", 40 to "xl",
            10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i"
        )
        val sb = StringBuilder()
        for ((arabic, roman) in table) {
            while (value >= arabic) {
                sb.append(roman)
                value -= arabic
            }
        }
        return sb.toString()
    }
}

/**
 * Compute the display prefix ("1. ", "2. ", "a. ", ...) for every numbered
 * paragraph in document order, keyed by block id. The editor, PDF and DOCX
 * exporters all share this so numbering is identical everywhere.
 */
fun computeNumberedPrefixes(blocks: List<DocumentBlock>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val counter = ListNumbering.Counter()
    for (block in blocks) {
        val paragraph = block as? ParagraphBlock
        if (paragraph != null && paragraph.listType == ListType.NUMBERED) {
            result[paragraph.id] =
                ListNumbering.prefixFor(paragraph.listLevel, counter.next(paragraph.listLevel))
        } else {
            counter.reset()
        }
    }
    return result
}
