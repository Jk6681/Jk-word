package com.example.jkword.engine.pdf

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.jkword.model.*
import java.io.OutputStream

/**
 * Native Android PDF Exporter.
 * Generates true vector/text multi-page PDF documents using android.graphics.pdf.PdfDocument.
 * Accurately calculates page breaks, margins, headers/footers, tables, and Urdu/Arabic RTL text.
 */
object PdfExporter {

    /**
     * @param imageBytesProvider resolves an ImageBlock's uriOrPath to raw
     * image bytes (the repository supplies one). Without it, images render
     * as the old placeholder box.
     */
    fun exportToPdf(
        document: DocumentModel,
        outputStream: OutputStream,
        imageBytesProvider: ((String) -> ByteArray?)? = null
    ) {
        val pdfDoc = PdfDocument()
        // Decoded bitmaps must stay alive until their page is written, so
        // they are recycled only after writeTo, not right after drawing.
        val decodedBitmaps = mutableListOf<android.graphics.Bitmap>()
        try {
        val settings = document.pageSettings
        val isLandscape = settings.orientation == PageOrientation.LANDSCAPE

        val baseWidth = settings.pageSize.widthPt
        val baseHeight = settings.pageSize.heightPt
        val pageWidth = if (isLandscape) baseHeight else baseWidth
        val pageHeight = if (isLandscape) baseWidth else baseHeight

        val marginLeft = settings.margins.leftPt.toFloat()
        val marginRight = settings.margins.rightPt.toFloat()
        val marginTop = settings.margins.topPt.toFloat()
        val marginBottom = settings.margins.bottomPt.toFloat()

        val contentWidth = (pageWidth - marginLeft - marginRight).coerceAtLeast(100f)

        // Text Paints
        val normalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
        }

        val headerFooterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9f
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Layout pass: Measure and assemble items into pages
        var currentPageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
        var page = pdfDoc.startPage(pageInfo)
        var canvas = page.canvas

        var cursorY = marginTop

        fun drawHeaderFooter(pageIndex: Int) {
            // Header
            if (settings.headerText.isNotEmpty()) {
                canvas.drawText(settings.headerText, marginLeft, marginTop - 18f, headerFooterPaint)
            }
            // Footer
            val footerY = pageHeight - marginBottom + 24f
            if (settings.footerText.isNotEmpty()) {
                canvas.drawText(settings.footerText, marginLeft, footerY, headerFooterPaint)
            }
            if (settings.showPageNumbers) {
                val pageStr = "Page $pageIndex"
                val textW = headerFooterPaint.measureText(pageStr)
                when (settings.pageNumberPosition) {
                    PageNumberPosition.BOTTOM_CENTER -> {
                        canvas.drawText(pageStr, (pageWidth - textW) / 2f, footerY, headerFooterPaint)
                    }
                    PageNumberPosition.BOTTOM_RIGHT -> {
                        canvas.drawText(pageStr, pageWidth - marginRight - textW, footerY, headerFooterPaint)
                    }
                    PageNumberPosition.TOP_RIGHT -> {
                        canvas.drawText(pageStr, pageWidth - marginRight - textW, marginTop - 18f, headerFooterPaint)
                    }
                }
            }
        }

        // Draw header/footer on page 1
        drawHeaderFooter(currentPageNum)

        fun checkNewPage(neededHeight: Float) {
            val maxBottom = pageHeight - marginBottom
            if (cursorY + neededHeight > maxBottom) {
                pdfDoc.finishPage(page)
                currentPageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
                page = pdfDoc.startPage(pageInfo)
                canvas = page.canvas
                cursorY = marginTop
                drawHeaderFooter(currentPageNum)
            }
        }

        // FIX (numbered lists): share the editor's sequence numbers so PDF
        // shows "1. 2. 3." instead of nothing (the old code only knew "• ").
        val listPrefixes = computeNumberedPrefixes(document.blocks)

        for (block in document.blocks) {
            when (block) {
                is ParagraphBlock -> {
                    val fullText = block.plainText
                    if (fullText.isEmpty()) {
                        cursorY += 12f
                        continue
                    }

                    val firstRun = block.runs.firstOrNull() ?: TextRun("")
                    val pPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = when (block.style) {
                            ParagraphStyle.TITLE -> 22f
                            ParagraphStyle.SUBTITLE -> 14f
                            ParagraphStyle.HEADING_1 -> 18f
                            ParagraphStyle.HEADING_2 -> 15f
                            ParagraphStyle.HEADING_3 -> 13f
                            else -> firstRun.fontSizePt.coerceIn(8f, 36f)
                        }
                        color = runCatching { Color.parseColor(firstRun.fontColorHex) }.getOrDefault(Color.BLACK)
                        typeface = when {
                            block.style.isBold || firstRun.isBold && firstRun.isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                            block.style.isBold || firstRun.isBold -> Typeface.DEFAULT_BOLD
                            firstRun.isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                            else -> Typeface.DEFAULT
                        }
                    }

                    val align = when (block.alignment) {
                        BlockAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                        BlockAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                        else -> Layout.Alignment.ALIGN_NORMAL
                    }

                    val displayPrefix = when (block.listType) {
                        ListType.BULLET -> "• "
                        ListType.NUMBERED -> listPrefixes[block.id] ?: ""
                        ListType.NONE -> ""
                    }
                    val displayText = displayPrefix + fullText

                    @Suppress("DEPRECATION")
                    val layout = StaticLayout(
                        displayText,
                        pPaint,
                        contentWidth.toInt(),
                        align,
                        block.lineSpacing,
                        0f,
                        true
                    )

                    val layoutHeight = layout.height.toFloat()
                    checkNewPage(layoutHeight + 6f)

                    canvas.save()
                    canvas.translate(marginLeft, cursorY)
                    layout.draw(canvas)
                    canvas.restore()

                    cursorY += layoutHeight + 8f
                }

                is TableBlock -> {
                    val rowHeight = 26f
                    val colWidth = contentWidth / block.cols.coerceAtLeast(1)

                    // Draw row-by-row so tall tables split across pages instead
                    // of overflowing a single page.
                    for (r in 0 until block.rows) {
                        checkNewPage(rowHeight)
                        val rowCells = block.cells.getOrNull(r) ?: emptyList()
                        val currentY = cursorY

                        for (c in 0 until block.cols) {
                            val cell = rowCells.getOrNull(c) ?: TableCell()
                            val cellLeft = marginLeft + (c * colWidth)
                            val cellRight = cellLeft + colWidth
                            val cellTop = currentY
                            val cellBottom = cellTop + rowHeight

                            // Background
                            if (cell.backgroundColorHex != null) {
                                fillPaint.color = runCatching { Color.parseColor(cell.backgroundColorHex) }.getOrDefault(Color.WHITE)
                                canvas.drawRect(cellLeft, cellTop, cellRight, cellBottom, fillPaint)
                            } else if (cell.isHeader) {
                                fillPaint.color = Color.parseColor("#E8F0FE")
                                canvas.drawRect(cellLeft, cellTop, cellRight, cellBottom, fillPaint)
                            }

                            // Border
                            canvas.drawRect(cellLeft, cellTop, cellRight, cellBottom, borderPaint)

                            // Text
                            val cellPaint = TextPaint(normalPaint).apply {
                                textSize = 10f
                                if (cell.isHeader || cell.isBold) typeface = Typeface.DEFAULT_BOLD
                            }
                            val textToDraw = cell.text.take(30)
                            canvas.drawText(textToDraw, cellLeft + 6f, cellTop + 17f, cellPaint)
                        }
                        cursorY += rowHeight
                    }

                    cursorY += 12f
                }

                is HorizontalRuleBlock -> {
                    checkNewPage(12f)
                    val rulePaint = Paint().apply {
                        color = Color.parseColor("#185ABD")
                        strokeWidth = 1.5f
                    }
                    canvas.drawLine(marginLeft, cursorY + 6f, marginLeft + contentWidth, cursorY + 6f, rulePaint)
                    cursorY += 16f
                }

                is PageBreakBlock -> {
                    pdfDoc.finishPage(page)
                    currentPageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas
                    cursorY = marginTop
                    drawHeaderFooter(currentPageNum)
                }

                is ImageBlock -> {
                    // FIX (images were placeholder boxes): draw the real
                    // picture when its bytes resolve, keeping the aspect
                    // ratio and the block's width. The placeholder box is
                    // now only for images whose bytes cannot be read.
                    val bytes = imageBytesProvider?.let { runCatching { it(block.uriOrPath) }.getOrNull() }
                    val bitmap = bytes?.let {
                        runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
                    }
                    if (bitmap != null) {
                        decodedBitmaps.add(bitmap)
                        val drawWidth = block.widthDp.coerceIn(40f, 600f)
                            .coerceAtMost(contentWidth)
                        val drawHeight = (drawWidth * bitmap.height / bitmap.width.toFloat())
                            .coerceAtLeast(20f)
                        checkNewPage(drawHeight + 12f)
                        val left = marginLeft + (contentWidth - drawWidth) / 2f
                        canvas.drawBitmap(
                            bitmap,
                            null,
                            RectF(left, cursorY, left + drawWidth, cursorY + drawHeight),
                            null
                        )
                        cursorY += drawHeight + 12f
                        if (!block.caption.isNullOrBlank()) {
                            canvas.drawText(
                                block.caption,
                                marginLeft,
                                cursorY - 2f,
                                normalPaint
                            )
                            cursorY += 10f
                        }
                    } else {
                        // Render image placeholder card in PDF
                        val imgHeight = 100f
                        checkNewPage(imgHeight + 10f)
                        val imgBoxPaint = Paint().apply {
                            color = Color.parseColor("#F1F5F9")
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(marginLeft, cursorY, marginLeft + contentWidth, cursorY + imgHeight, imgBoxPaint)
                        canvas.drawRect(marginLeft, cursorY, marginLeft + contentWidth, cursorY + imgHeight, borderPaint)
                        val caption = block.caption ?: "Attached Image"
                        canvas.drawText("[ $caption ]", marginLeft + 16f, cursorY + 54f, normalPaint)
                        cursorY += imgHeight + 12f
                    }
                }
            }
        }

        pdfDoc.finishPage(page)
        pdfDoc.writeTo(outputStream)
        decodedBitmaps.forEach { runCatching { it.recycle() } }
        } finally {
            // Always release the native PdfDocument, even if export threw midway.
            runCatching { pdfDoc.close() }
        }
    }
}
