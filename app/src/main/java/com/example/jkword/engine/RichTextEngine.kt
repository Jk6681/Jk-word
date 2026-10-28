package com.example.jkword.engine

import androidx.compose.ui.text.TextRange
import com.example.jkword.model.ParagraphBlock
import com.example.jkword.model.TextRun
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance Rich Text Engine for WordMate / JK Word Office.
 * Handles character-accurate span formatting, selection-based styling,
 * and active typing styles for cursor positions.
 */
object RichTextEngine {

    /**
     * Applies a style transformation strictly to the characters in [range] (start until end).
     * Characters outside [range] remain untouched.
     * Adjacent runs with identical styles are coalesced.
     */
    fun applyFormattingToRange(
        runs: List<TextRun>,
        range: TextRange,
        transform: (TextRun) -> TextRun
    ): List<TextRun> {
        val selStart = min(range.start, range.end).coerceAtLeast(0)
        val selEnd = max(range.start, range.end).coerceAtLeast(0)

        if (selStart >= selEnd) {
            // Collapsed selection: no characters are selected
            return runs
        }

        val result = mutableListOf<TextRun>()
        var currentOffset = 0

        for (run in runs) {
            val runLen = run.text.length
            val runStart = currentOffset
            val runEnd = currentOffset + runLen
            currentOffset += runLen

            if (runLen == 0) continue

            if (runEnd <= selStart || runStart >= selEnd) {
                // Entirely outside the selection range
                result.add(run)
            } else {
                // Overlaps with selection: slice into up to 3 segments
                val overlapStart = max(runStart, selStart)
                val overlapEnd = min(runEnd, selEnd)

                val prefixLen = overlapStart - runStart
                val selectedLen = overlapEnd - overlapStart
                val suffixLen = runEnd - overlapEnd

                if (prefixLen > 0) {
                    val prefixText = run.text.substring(0, prefixLen)
                    result.add(run.copy(text = prefixText))
                }

                if (selectedLen > 0) {
                    val selectedText = run.text.substring(prefixLen, prefixLen + selectedLen)
                    result.add(transform(run).copy(text = selectedText))
                }

                if (suffixLen > 0) {
                    val suffixText = run.text.substring(prefixLen + selectedLen)
                    result.add(run.copy(text = suffixText))
                }
            }
        }

        return coalesceRuns(result)
    }

    /**
     * Merges adjacent runs that share identical formatting properties,
     * reducing memory usage and recomposition cost.
     */
    fun coalesceRuns(runs: List<TextRun>): List<TextRun> {
        if (runs.isEmpty()) return listOf(TextRun(""))
        val coalesced = mutableListOf<TextRun>()
        var current = runs[0]

        for (i in 1 until runs.size) {
            val next = runs[i]
            if (next.text.isEmpty()) continue
            if (current.text.isEmpty()) {
                current = next
                continue
            }

            if (hasSameFormatting(current, next)) {
                current = current.copy(text = current.text + next.text)
            } else {
                coalesced.add(current)
                current = next
            }
        }
        coalesced.add(current)
        return if (coalesced.isEmpty()) listOf(TextRun("")) else coalesced
    }

    /**
     * Checks if two runs share identical visual formatting.
     */
    fun hasSameFormatting(a: TextRun, b: TextRun): Boolean {
        return a.isBold == b.isBold &&
                a.isItalic == b.isItalic &&
                a.isUnderline == b.isUnderline &&
                a.isStrikethrough == b.isStrikethrough &&
                a.isSuperscript == b.isSuperscript &&
                a.isSubscript == b.isSubscript &&
                a.fontSizePt == b.fontSizePt &&
                a.fontColorHex.equals(b.fontColorHex, ignoreCase = true) &&
                a.highlightHex.equals(b.highlightHex, ignoreCase = true) &&
                a.fontFamily.equals(b.fontFamily, ignoreCase = true) &&
                a.linkUrl == b.linkUrl
    }

    /**
     * Finds the character style at a specific cursor offset.
     * Useful for updating toolbar toggles when the user positions the cursor.
     */
    fun getStyleAtOffset(runs: List<TextRun>, offset: Int): TextRun {
        if (runs.isEmpty()) return TextRun("")
        var current = 0
        for (run in runs) {
            val next = current + run.text.length
            if (offset in current..next) {
                return run
            }
            current = next
        }
        return runs.lastOrNull() ?: TextRun("")
    }

    /**
     * Inserts new text into existing runs at [insertOffset], applying [style] to the inserted characters.
     */
    fun insertTextAt(
        runs: List<TextRun>,
        insertOffset: Int,
        insertedText: String,
        style: TextRun
    ): List<TextRun> {
        if (insertedText.isEmpty()) return runs

        val totalLen = runs.sumOf { it.text.length }
        val offset = insertOffset.coerceIn(0, totalLen)

        if (runs.isEmpty() || totalLen == 0) {
            return listOf(style.copy(text = insertedText))
        }

        val result = mutableListOf<TextRun>()
        var current = 0
        var inserted = false

        for (run in runs) {
            val runLen = run.text.length
            val runStart = current
            val runEnd = current + runLen
            current += runLen

            if (!inserted && offset in runStart..runEnd) {
                val splitIdx = offset - runStart
                val before = run.text.substring(0, splitIdx)
                val after = run.text.substring(splitIdx)

                if (before.isNotEmpty()) result.add(run.copy(text = before))
                result.add(style.copy(text = insertedText))
                if (after.isNotEmpty()) result.add(run.copy(text = after))
                inserted = true
            } else {
                result.add(run)
            }
        }

        if (!inserted) {
            result.add(style.copy(text = insertedText))
        }

        return coalesceRuns(result)
    }

    /**
     * Deletes a range of characters [deleteStart until deleteEnd] from the runs.
     */
    fun deleteRange(
        runs: List<TextRun>,
        deleteStart: Int,
        deleteEnd: Int
    ): List<TextRun> {
        val start = min(deleteStart, deleteEnd).coerceAtLeast(0)
        val end = max(deleteStart, deleteEnd).coerceAtLeast(0)
        if (start >= end) return runs

        val result = mutableListOf<TextRun>()
        var current = 0

        for (run in runs) {
            val runLen = run.text.length
            val runStart = current
            val runEnd = current + runLen
            current += runLen

            if (runEnd <= start || runStart >= end) {
                result.add(run)
            } else {
                val overlapStart = max(runStart, start)
                val overlapEnd = min(runEnd, end)

                val prefixLen = overlapStart - runStart
                val suffixLen = runEnd - overlapEnd

                if (prefixLen > 0) {
                    result.add(run.copy(text = run.text.substring(0, prefixLen)))
                }
                if (suffixLen > 0) {
                    result.add(run.copy(text = run.text.substring(runLen - suffixLen)))
                }
            }
        }

        val cleaned = coalesceRuns(result)
        return if (cleaned.isEmpty() || cleaned.all { it.text.isEmpty() }) {
            val sample = runs.firstOrNull() ?: TextRun("")
            listOf(sample.copy(text = ""))
        } else {
            cleaned
        }
    }

    /**
     * Atomically replaces characters in range [rangeStart until rangeEnd] with [replacement],
     * styled with [style]. If rangeStart == rangeEnd, this behaves as pure insertion.
     */
    fun replaceRange(
        runs: List<TextRun>,
        rangeStart: Int,
        rangeEnd: Int,
        replacement: String,
        style: TextRun
    ): List<TextRun> {
        val start = min(rangeStart, rangeEnd).coerceAtLeast(0)
        val end = max(rangeStart, rangeEnd).coerceAtLeast(0)

        val afterDelete = if (start < end) {
            deleteRange(runs, start, end)
        } else {
            runs
        }

        return if (replacement.isNotEmpty()) {
            insertTextAt(afterDelete, start, replacement, style)
        } else {
            afterDelete
        }
    }

    data class TextEditDiff(
        val rangeStart: Int,
        val rangeEnd: Int,
        val insertedText: String
    )

    /**
     * Calculates the exact minimal difference between oldText and newText using prefix & suffix matching.
     * Guarantees 100% correct delta for typing, backspace, selection replacement, paste, and autocorrect.
     */
    fun calculateDiff(oldText: String, newText: String): TextEditDiff {
        if (oldText == newText) {
            return TextEditDiff(0, 0, "")
        }
        var start = 0
        while (start < oldText.length && start < newText.length && oldText[start] == newText[start]) {
            start++
        }
        var oldEnd = oldText.length
        var newEnd = newText.length
        while (oldEnd > start && newEnd > start && oldText[oldEnd - 1] == newText[newEnd - 1]) {
            oldEnd--
            newEnd--
        }
        val inserted = newText.substring(start, newEnd)
        return TextEditDiff(rangeStart = start, rangeEnd = oldEnd, insertedText = inserted)
    }

    /**
     * Applies a text edit from oldText to newText onto the existing runs list,
     * maintaining all styling on untouched characters and applying [style] to inserted text.
     */
    fun applyTextEdit(
        runs: List<TextRun>,
        oldText: String,
        newText: String,
        style: TextRun
    ): List<TextRun> {
        if (oldText == newText) return runs
        val diff = calculateDiff(oldText, newText)
        return replaceRange(runs, diff.rangeStart, diff.rangeEnd, diff.insertedText, style)
    }

    /**
     * Fast plain text extraction minimizing string allocations.
     */
    fun fastPlainText(runs: List<TextRun>): String {
        return when (runs.size) {
            0 -> ""
            1 -> runs[0].text
            else -> {
                val sb = StringBuilder()
                for (r in runs) sb.append(r.text)
                sb.toString()
            }
        }
    }
}
