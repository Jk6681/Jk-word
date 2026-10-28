package com.example.jkword.data.local

import com.example.jkword.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Robust JSON serializer for DocumentModel.
 * Uses Android native org.json for zero external reflection dependencies, high speed, and fault tolerance.
 */
object JsonDocumentSerializer {

    fun serialize(doc: DocumentModel): String {
        val root = JSONObject()
        root.put("id", doc.id)
        root.put("title", doc.title)
        root.put("createdAt", doc.createdAt)
        root.put("modifiedAt", doc.modifiedAt)

        // Page settings
        val pageObj = JSONObject()
        pageObj.put("pageSize", doc.pageSettings.pageSize.name)
        pageObj.put("orientation", doc.pageSettings.orientation.name)
        pageObj.put("marginsName", doc.pageSettings.margins.displayName)
        pageObj.put("marginTop", doc.pageSettings.margins.topPt)
        pageObj.put("marginBottom", doc.pageSettings.margins.bottomPt)
        pageObj.put("marginLeft", doc.pageSettings.margins.leftPt)
        pageObj.put("marginRight", doc.pageSettings.margins.rightPt)
        pageObj.put("headerText", doc.pageSettings.headerText)
        pageObj.put("footerText", doc.pageSettings.footerText)
        pageObj.put("showPageNumbers", doc.pageSettings.showPageNumbers)
        pageObj.put("pageNumberPosition", doc.pageSettings.pageNumberPosition.name)
        root.put("pageSettings", pageObj)

        // Blocks
        val blocksArray = JSONArray()
        for (block in doc.blocks) {
            val blockObj = JSONObject()
            when (block) {
                is ParagraphBlock -> {
                    blockObj.put("type", "paragraph")
                    blockObj.put("id", block.id)
                    blockObj.put("style", block.style.name)
                    blockObj.put("alignment", block.alignment.name)
                    blockObj.put("listType", block.listType.name)
                    blockObj.put("listLevel", block.listLevel)
                    blockObj.put("lineSpacing", block.lineSpacing.toDouble())

                    val runsArray = JSONArray()
                    for (run in block.runs) {
                        val runObj = JSONObject()
                        runObj.put("text", run.text)
                        runObj.put("isBold", run.isBold)
                        runObj.put("isItalic", run.isItalic)
                        runObj.put("isUnderline", run.isUnderline)
                        runObj.put("isStrikethrough", run.isStrikethrough)
                        runObj.put("isSuperscript", run.isSuperscript)
                        runObj.put("isSubscript", run.isSubscript)
                        runObj.put("fontSizePt", run.fontSizePt.toDouble())
                        runObj.put("fontColorHex", run.fontColorHex)
                        if (run.highlightHex != null) runObj.put("highlightHex", run.highlightHex)
                        runObj.put("fontFamily", run.fontFamily)
                        if (run.linkUrl != null) runObj.put("linkUrl", run.linkUrl)
                        runsArray.put(runObj)
                    }
                    blockObj.put("runs", runsArray)
                }
                is TableBlock -> {
                    blockObj.put("type", "table")
                    blockObj.put("id", block.id)
                    blockObj.put("rows", block.rows)
                    blockObj.put("cols", block.cols)

                    val rowsArray = JSONArray()
                    for (row in block.cells) {
                        val rowArray = JSONArray()
                        for (cell in row) {
                            val cellObj = JSONObject()
                            cellObj.put("id", cell.id)
                            cellObj.put("text", cell.text)
                            cellObj.put("isHeader", cell.isHeader)
                            if (cell.backgroundColorHex != null) cellObj.put("bgColor", cell.backgroundColorHex)
                            cellObj.put("isBold", cell.isBold)
                            cellObj.put("alignment", cell.alignment.name)
                            rowArray.put(cellObj)
                        }
                        rowsArray.put(rowArray)
                    }
                    blockObj.put("cells", rowsArray)
                }
                is ImageBlock -> {
                    blockObj.put("type", "image")
                    blockObj.put("id", block.id)
                    blockObj.put("uriOrPath", block.uriOrPath)
                    blockObj.put("widthDp", block.widthDp.toDouble())
                    blockObj.put("heightDp", block.heightDp.toDouble())
                    blockObj.put("alignment", block.alignment.name)
                    if (block.caption != null) blockObj.put("caption", block.caption)
                }
                is HorizontalRuleBlock -> {
                    blockObj.put("type", "hr")
                    blockObj.put("id", block.id)
                }
                is PageBreakBlock -> {
                    blockObj.put("type", "pageBreak")
                    blockObj.put("id", block.id)
                }
            }
            blocksArray.put(blockObj)
        }
        root.put("blocks", blocksArray)

        return root.toString(2)
    }

    fun deserialize(jsonString: String): DocumentModel {
        val root = JSONObject(jsonString)
        val id = root.optString("id", "")
        val title = root.optString("title", "Untitled Document")
        val createdAt = root.optLong("createdAt", System.currentTimeMillis())
        val modifiedAt = root.optLong("modifiedAt", System.currentTimeMillis())

        val pageObj = root.optJSONObject("pageSettings")
        val pageSettings = if (pageObj != null) {
            val pSize = runCatching { PageSize.valueOf(pageObj.optString("pageSize", "A4")) }.getOrDefault(PageSize.A4)
            val pOrient = runCatching { PageOrientation.valueOf(pageObj.optString("orientation", "PORTRAIT")) }.getOrDefault(PageOrientation.PORTRAIT)
            val margins = PageMargins(
                displayName = pageObj.optString("marginsName", "Normal"),
                topPt = pageObj.optInt("marginTop", 72),
                bottomPt = pageObj.optInt("marginBottom", 72),
                leftPt = pageObj.optInt("marginLeft", 72),
                rightPt = pageObj.optInt("marginRight", 72)
            )
            val headerText = pageObj.optString("headerText", "")
            val footerText = pageObj.optString("footerText", "")
            val showPageNumbers = pageObj.optBoolean("showPageNumbers", true)
            val pageNumPos = runCatching { PageNumberPosition.valueOf(pageObj.optString("pageNumberPosition", "BOTTOM_RIGHT")) }
                .getOrDefault(PageNumberPosition.BOTTOM_RIGHT)

            PageLayoutSettings(
                pageSize = pSize,
                orientation = pOrient,
                margins = margins,
                headerText = headerText,
                footerText = footerText,
                showPageNumbers = showPageNumbers,
                pageNumberPosition = pageNumPos
            )
        } else {
            PageLayoutSettings()
        }

        val blocks = mutableListOf<DocumentBlock>()
        val blocksArray = root.optJSONArray("blocks")
        if (blocksArray != null) {
            for (i in 0 until blocksArray.length()) {
                val blockObj = blocksArray.optJSONObject(i) ?: continue
                when (blockObj.optString("type")) {
                    "paragraph" -> {
                        val pId = blockObj.optString("id")
                        val style = runCatching { ParagraphStyle.valueOf(blockObj.optString("style", "NORMAL")) }
                            .getOrDefault(ParagraphStyle.NORMAL)
                        val align = runCatching { BlockAlignment.valueOf(blockObj.optString("alignment", "LEFT")) }
                            .getOrDefault(BlockAlignment.LEFT)
                        val listType = runCatching { ListType.valueOf(blockObj.optString("listType", "NONE")) }
                            .getOrDefault(ListType.NONE)
                        val listLevel = blockObj.optInt("listLevel", 0)
                        val lineSpacing = blockObj.optDouble("lineSpacing", 1.15).toFloat()

                        val runs = mutableListOf<TextRun>()
                        val runsArray = blockObj.optJSONArray("runs")
                        if (runsArray != null) {
                            for (j in 0 until runsArray.length()) {
                                val runObj = runsArray.optJSONObject(j) ?: continue
                                runs.add(
                                    TextRun(
                                        text = runObj.optString("text", ""),
                                        isBold = runObj.optBoolean("isBold", false),
                                        isItalic = runObj.optBoolean("isItalic", false),
                                        isUnderline = runObj.optBoolean("isUnderline", false),
                                        isStrikethrough = runObj.optBoolean("isStrikethrough", false),
                                        isSuperscript = runObj.optBoolean("isSuperscript", false),
                                        isSubscript = runObj.optBoolean("isSubscript", false),
                                        fontSizePt = runObj.optDouble("fontSizePt", 11.0).toFloat(),
                                        fontColorHex = runObj.optString("fontColorHex", "#1A1A1A"),
                                        highlightHex = if (runObj.has("highlightHex")) runObj.optString("highlightHex") else null,
                                        fontFamily = runObj.optString("fontFamily", "Calibri"),
                                        linkUrl = if (runObj.has("linkUrl")) runObj.optString("linkUrl") else null
                                    )
                                )
                            }
                        }
                        if (runs.isEmpty()) {
                            runs.add(TextRun(""))
                        }
                        blocks.add(
                            ParagraphBlock(
                                id = if (pId.isNotEmpty()) pId else java.util.UUID.randomUUID().toString(),
                                runs = runs,
                                style = style,
                                alignment = align,
                                listType = listType,
                                listLevel = listLevel,
                                lineSpacing = lineSpacing
                            )
                        )
                    }
                    "table" -> {
                        val tId = blockObj.optString("id")
                        val rows = blockObj.optInt("rows", 2)
                        val cols = blockObj.optInt("cols", 2)
                        val cells = mutableListOf<List<TableCell>>()
                        val rowsArray = blockObj.optJSONArray("cells")
                        if (rowsArray != null) {
                            for (r in 0 until rowsArray.length()) {
                                val rowArray = rowsArray.optJSONArray(r) ?: continue
                                val rowCells = mutableListOf<TableCell>()
                                for (c in 0 until rowArray.length()) {
                                    val cellObj = rowArray.optJSONObject(c) ?: continue
                                    rowCells.add(
                                        TableCell(
                                            id = cellObj.optString("id", java.util.UUID.randomUUID().toString()),
                                            text = cellObj.optString("text", ""),
                                            isHeader = cellObj.optBoolean("isHeader", false),
                                            backgroundColorHex = if (cellObj.has("bgColor")) cellObj.optString("bgColor") else null,
                                            isBold = cellObj.optBoolean("isBold", false),
                                            alignment = runCatching { BlockAlignment.valueOf(cellObj.optString("alignment", "LEFT")) }
                                                .getOrDefault(BlockAlignment.LEFT)
                                        )
                                    )
                                }
                                cells.add(rowCells)
                            }
                        }
                        blocks.add(
                            TableBlock(
                                id = if (tId.isNotEmpty()) tId else java.util.UUID.randomUUID().toString(),
                                rows = rows,
                                cols = cols,
                                cells = if (cells.isNotEmpty()) cells else TableBlock.createDefaultCells(rows, cols)
                            )
                        )
                    }
                    "image" -> {
                        val imgId = blockObj.optString("id")
                        blocks.add(
                            ImageBlock(
                                id = if (imgId.isNotEmpty()) imgId else java.util.UUID.randomUUID().toString(),
                                uriOrPath = blockObj.optString("uriOrPath", ""),
                                widthDp = blockObj.optDouble("widthDp", 260.0).toFloat(),
                                heightDp = blockObj.optDouble("heightDp", 180.0).toFloat(),
                                alignment = runCatching { BlockAlignment.valueOf(blockObj.optString("alignment", "CENTER")) }
                                    .getOrDefault(BlockAlignment.CENTER),
                                caption = if (blockObj.has("caption")) blockObj.optString("caption") else null
                            )
                        )
                    }
                    "hr" -> {
                        val hrId = blockObj.optString("id")
                        blocks.add(HorizontalRuleBlock(id = if (hrId.isNotEmpty()) hrId else java.util.UUID.randomUUID().toString()))
                    }
                    "pageBreak" -> {
                        val pbId = blockObj.optString("id")
                        blocks.add(PageBreakBlock(id = if (pbId.isNotEmpty()) pbId else java.util.UUID.randomUUID().toString()))
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(ParagraphBlock())
        }

        return DocumentModel(
            id = id,
            title = title,
            blocks = blocks,
            pageSettings = pageSettings,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )
    }
}
