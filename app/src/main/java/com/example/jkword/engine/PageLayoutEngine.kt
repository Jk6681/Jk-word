package com.example.jkword.engine

import com.example.jkword.model.*
import kotlin.math.ceil

/**
 * Physical Document Pagination Engine for WordMate / JK Word Office.
 *
 * Guarantees that:
 * 1. Every document page maintains its exact physical paper dimensions (A4, 8x13 Legal, Letter, etc.).
 * 2. Pages never expand vertically beyond their physical boundaries.
 * 3. Content flows automatically onto the next page when a page is full.
 * 4. Page breaks cleanly split content onto new pages.
 * 5. Page numbers (Page X of Y) are computed accurately.
 */
data class PagePartition(
    val pageIndex: Int, // 0-indexed
    val totalPages: Int,
    val blockIndices: List<Int>
)

object PageLayoutEngine {

    /**
     * Calculates the pixel/dp aspect ratio and dimensions for a given paper size and orientation.
     */
    fun calculateSheetDimensions(
        pageSize: PageSize,
        orientation: PageOrientation,
        availableContainerWidthDp: Float
    ): Pair<Float, Float> {
        val isLandscape = orientation == PageOrientation.LANDSCAPE
        val pWidth = if (isLandscape) pageSize.heightPt.toFloat() else pageSize.widthPt.toFloat()
        val pHeight = if (isLandscape) pageSize.widthPt.toFloat() else pageSize.heightPt.toFloat()

        val aspectRatio = pHeight / pWidth

        // Responsive width clamped for comfortable handheld reading/editing
        val sheetWidth = (availableContainerWidthDp - 24f).coerceIn(280f, 540f)
        val sheetHeight = sheetWidth * aspectRatio

        return Pair(sheetWidth, sheetHeight)
    }

    /**
     * Paginates document blocks into individual fixed-height physical pages.
     */
    fun paginate(
        blocks: List<DocumentBlock>,
        pageSettings: PageLayoutSettings,
        sheetHeightDp: Float,
        contentWidthDp: Float
    ): List<PagePartition> {
        if (blocks.isEmpty()) {
            return listOf(PagePartition(pageIndex = 0, totalPages = 1, blockIndices = emptyList()))
        }

        // Available printable body height inside margins and header/footer
        val topMarginDp = (pageSettings.margins.topPt * 0.20f) + 16f
        val bottomMarginDp = (pageSettings.margins.bottomPt * 0.20f) + 20f
        val headerAreaDp = if (pageSettings.headerText.isNotEmpty()) 24f else 12f
        val footerAreaDp = if (pageSettings.showPageNumbers || pageSettings.footerText.isNotEmpty()) 24f else 12f

        val usableBodyHeightDp = (sheetHeightDp - topMarginDp - bottomMarginDp - headerAreaDp - footerAreaDp)
            .coerceAtLeast(180f)

        val pages = mutableListOf<MutableList<Int>>()
        var currentPageBlocks = mutableListOf<Int>()
        var currentUsedHeight = 0f

        for (index in blocks.indices) {
            val block = blocks[index]

            if (block is PageBreakBlock) {
                // Explicit page break forces new page
                currentPageBlocks.add(index)
                pages.add(currentPageBlocks)
                currentPageBlocks = mutableListOf()
                currentUsedHeight = 0f
                continue
            }

            val estimatedHeight = estimateBlockHeight(block, contentWidthDp)

            // If block causes overflow and current page already has content, push to next page
            if (currentUsedHeight + estimatedHeight > usableBodyHeightDp && currentPageBlocks.isNotEmpty()) {
                pages.add(currentPageBlocks)
                currentPageBlocks = mutableListOf(index)
                currentUsedHeight = estimatedHeight
            } else {
                currentPageBlocks.add(index)
                currentUsedHeight += estimatedHeight
            }
        }

        if (currentPageBlocks.isNotEmpty() || pages.isEmpty()) {
            pages.add(currentPageBlocks)
        }

        val totalPages = pages.size
        return pages.mapIndexed { pIdx, blockList ->
            PagePartition(
                pageIndex = pIdx,
                totalPages = totalPages,
                blockIndices = blockList
            )
        }
    }

    /**
     * Estimates the visual vertical height in dp occupied by a block.
     */
    fun estimateBlockHeight(block: DocumentBlock, contentWidthDp: Float): Float {
        return when (block) {
            is ParagraphBlock -> {
                val text = block.plainText
                if (text.isEmpty()) {
                    return 24f // Empty paragraph minimum line height
                }
                val firstRun = block.runs.firstOrNull()
                val fontPt = when (block.style) {
                    ParagraphStyle.TITLE -> 24f
                    ParagraphStyle.SUBTITLE -> 16f
                    ParagraphStyle.HEADING_1 -> 20f
                    ParagraphStyle.HEADING_2 -> 16f
                    ParagraphStyle.HEADING_3 -> 14f
                    ParagraphStyle.CAPTION -> 10f
                    else -> firstRun?.fontSizePt ?: 11f
                }
                val approxCharWidth = fontPt * 0.48f
                val charsPerLine = (contentWidthDp / approxCharWidth).coerceAtLeast(10f)
                val lines = maxOf(1, ceil(text.length / charsPerLine).toInt())
                val lineHeight = fontPt * 1.35f * block.lineSpacing
                (lines * lineHeight) + 8f
            }
            is TableBlock -> {
                (block.rows * 38f) + 20f
            }
            is ImageBlock -> {
                (block.heightDp * 0.75f) + 20f
            }
            is HorizontalRuleBlock -> {
                20f
            }
            is PageBreakBlock -> {
                24f
            }
        }
    }
}
