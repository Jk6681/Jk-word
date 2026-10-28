package com.example.jkword.engine

import com.example.jkword.model.*
import kotlin.math.max

data class IndexedBlock(
    val globalIndex: Int,
    val block: DocumentBlock
)

data class DocumentPage(
    val pageNumber: Int,
    val totalPages: Int,
    val blocks: List<IndexedBlock>,
    val isLastPage: Boolean = false
)

object PaginationEngine {

    /**
     * Estimates block height in Dp units to paginate documents into physical pages.
     */
    fun estimateBlockHeightDp(block: DocumentBlock): Float {
        return when (block) {
            is ParagraphBlock -> {
                val firstRun = block.runs.firstOrNull()
                val fontPt = firstRun?.fontSizePt ?: 11f
                val lineHeight = max(18f, fontPt * 1.35f)
                var newlineCount = 0
                var totalChars = 0
                for (r in block.runs) {
                    val txt = r.text
                    totalChars += txt.length
                    for (i in 0 until txt.length) {
                        if (txt[i] == '\n') newlineCount++
                    }
                }
                val estimatedWrapLines = max(1, (totalChars / 40))
                val totalLines = estimatedWrapLines + newlineCount
                (totalLines * lineHeight) + 8f
            }
            is TableBlock -> {
                (block.rows * 36f) + 16f
            }
            is ImageBlock -> {
                block.heightDp + 16f
            }
            is HorizontalRuleBlock -> {
                24f
            }
            is PageBreakBlock -> {
                20f
            }
        }
    }

    /**
     * Splits document blocks into fixed-size pages respecting the physical sheet dimensions.
     * Each page has an exact height and never expands vertically beyond its boundaries.
     */
    fun paginate(
        blocks: List<DocumentBlock>,
        usableContentHeightDp: Float
    ): List<DocumentPage> {
        val safeCapacity = usableContentHeightDp.coerceAtLeast(200f)
        val pages = mutableListOf<MutableList<IndexedBlock>>()
        var currentPage = mutableListOf<IndexedBlock>()
        var currentHeight = 0f

        for (i in blocks.indices) {
            val block = blocks[i]
            val indexed = IndexedBlock(i, block)

            if (block is PageBreakBlock) {
                // Page break belongs to the current page boundary, then split immediately
                currentPage.add(indexed)
                pages.add(currentPage)
                currentPage = mutableListOf()
                currentHeight = 0f
                continue
            }

            val blockHeight = estimateBlockHeightDp(block)

            if (currentHeight + blockHeight > safeCapacity && currentPage.isNotEmpty()) {
                // Current page is full, flow this block to the next page
                pages.add(currentPage)
                currentPage = mutableListOf(indexed)
                currentHeight = blockHeight
            } else {
                currentPage.add(indexed)
                currentHeight += blockHeight
            }
        }

        if (currentPage.isNotEmpty() || pages.isEmpty()) {
            pages.add(currentPage)
        }

        val totalPages = pages.size
        return pages.mapIndexed { index, pageBlocks ->
            DocumentPage(
                pageNumber = index + 1,
                totalPages = totalPages,
                blocks = pageBlocks,
                isLastPage = (index == totalPages - 1)
            )
        }
    }
}
