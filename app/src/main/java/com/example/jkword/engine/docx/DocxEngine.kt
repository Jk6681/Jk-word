package com.example.jkword.engine.docx

import com.example.jkword.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Native Android OOXML (DOCX) Engine.
 * Supports round-trip export and import of Microsoft Word (.docx) files.
 * Zero external heavyweight jars; 100% Android compatible and fast.
 */
object DocxEngine {

    /** An image resolved to bytes, ready to embed in the .docx package. */
    private data class ResolvedImage(
        val blockId: String,
        val bytes: ByteArray,
        val extension: String,
        val rId: String,
        val mediaName: String
    )

    /**
     * Exports a DocumentModel to a valid OOXML .docx file.
     *
     * @param imageBytesProvider resolves an ImageBlock's uriOrPath to raw
     * image bytes (the repository supplies one backed by ContentResolver /
     * app storage). When it is null, or an image has no bytes, that image
     * falls back to the old "[Image: ...]" placeholder paragraph so export
     * never fails because of one picture.
     */
    fun exportToDocx(
        document: DocumentModel,
        outputStream: OutputStream,
        imageBytesProvider: ((String) -> ByteArray?)? = null
    ) {
        // Resolve images FIRST so document.xml, rels and media agree on rIds.
        val images = mutableListOf<ResolvedImage>()
        if (imageBytesProvider != null) {
            var count = 0
            for (block in document.blocks) {
                if (block is ImageBlock) {
                    val bytes = runCatching { imageBytesProvider(block.uriOrPath) }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        count++
                        val isJpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
                        val ext = if (isJpeg) "jpeg" else "png"
                        images.add(
                            ResolvedImage(
                                blockId = block.id,
                                bytes = bytes,
                                extension = ext,
                                rId = "rIdImg$count",
                                mediaName = "image$count.$ext"
                            )
                        )
                    }
                }
            }
        }
        val imageByBlockId = images.associateBy { it.blockId }

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
            // 1. [Content_Types].xml
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(buildContentTypesXml().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            // 2. _rels/.rels
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(buildPackageRelsXml().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            // 3. word/_rels/document.xml.rels
            zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zip.write(buildDocumentRelsXml(images).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            // 4. word/styles.xml
            zip.putNextEntry(ZipEntry("word/styles.xml"))
            zip.write(buildStylesXml().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            // 5. word/numbering.xml (bullet list definition)
            zip.putNextEntry(ZipEntry("word/numbering.xml"))
            zip.write(buildNumberingXml().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            // 6. word/media/* (embedded images)
            for (image in images) {
                zip.putNextEntry(ZipEntry("word/media/${image.mediaName}"))
                zip.write(image.bytes)
                zip.closeEntry()
            }

            // 7. word/document.xml
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(buildDocumentXml(document, imageByBlockId).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
    }

    /**
     * Reads a .docx file and parses it into a DocumentModel.
     */
    fun importFromDocx(inputStream: InputStream, titleFallback: String = "Imported Document"): DocumentModel {
        var documentXmlBytes: ByteArray? = null
        var numberingXmlBytes: ByteArray? = null

        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml" || entry.name == "word/numbering.xml") {
                    val buffer = ByteArrayOutputStream()
                    val temp = ByteArray(4096)
                    var read: Int
                    while (zip.read(temp).also { read = it } != -1) {
                        buffer.write(temp, 0, read)
                    }
                    if (entry.name == "word/document.xml") {
                        documentXmlBytes = buffer.toByteArray()
                    } else {
                        numberingXmlBytes = buffer.toByteArray()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (documentXmlBytes == null) {
            throw IllegalArgumentException("Invalid DOCX file: word/document.xml was not found in the package.")
        }

        // FIX (numbered import): resolve numId/ilvl through the file's own
        // numbering part. The old code mapped EVERY numPr to BULLET, so Word
        // numbered lists came back as bullet lists.
        val numbering = numberingXmlBytes?.let { parseNumberingXml(it) } ?: emptyMap()
        return parseDocumentXml(ByteArrayInputStream(documentXmlBytes), titleFallback, numbering)
    }

    /**
     * Namespace-proof attribute read: OOXML attributes are w:-prefixed, and
     * getAttributeValue(null, ...) misses them under namespace-aware parsing.
     * Matching on the attribute's local name works either way.
     */
    private fun attrValue(parser: XmlPullParser, localName: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == localName) return parser.getAttributeValue(i)
        }
        return null
    }

    /**
     * Parse word/numbering.xml into (numId, ilvl) -> numFmt ("bullet",
     * "decimal", "lowerLetter", ...). Unknown numIds resolve to null and the
     * caller falls back to the legacy bullet behavior.
     */
    private fun parseNumberingXml(bytes: ByteArray): Map<Pair<Int, Int>, String> {
        val abstractFormats = mutableMapOf<Int, MutableMap<Int, String>>()
        val numToAbstract = mutableMapOf<Int, Int>()
        var pendingNumId: Int? = null
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var currentAbstract: Int? = null
            var currentIlvl: Int? = null
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name ?: ""
                if (eventType == XmlPullParser.START_TAG) {
                    when (name) {
                        "abstractNum" -> currentAbstract =
                            attrValue(parser, "abstractNumId")?.toIntOrNull()
                        "lvl" -> currentIlvl =
                            attrValue(parser, "ilvl")?.toIntOrNull()
                        "numFmt" -> {
                            val fmt = attrValue(parser, "val")
                            if (currentAbstract != null && currentIlvl != null && fmt != null) {
                                abstractFormats.getOrPut(currentAbstract) { mutableMapOf() }[currentIlvl] = fmt
                            }
                        }
                        "num" -> {
                            val numId = attrValue(parser, "numId")?.toIntOrNull()
                            if (numId != null) {
                                // The abstractNumId child comes as the next start tag;
                                // it is resolved in the "abstractNumId" branch below
                                // via a pending marker.
                                pendingNumId = numId
                            }
                        }
                        "abstractNumId" -> {
                            val absId = attrValue(parser, "val")?.toIntOrNull()
                            if (pendingNumId != null && absId != null) {
                                numToAbstract[pendingNumId] = absId
                                pendingNumId = null
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    when (name) {
                        "abstractNum" -> currentAbstract = null
                        "lvl" -> currentIlvl = null
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // A broken numbering part must not kill the whole import; the
            // caller falls back to the legacy bullet mapping.
            return emptyMap()
        }
        val result = mutableMapOf<Pair<Int, Int>, String>()
        for ((numId, absId) in numToAbstract) {
            for ((ilvl, fmt) in abstractFormats[absId].orEmpty()) {
                result[numId to ilvl] = fmt
            }
        }
        return result
    }

    private fun buildNumberingXml(): String {
        // FIX (numbered lists): the old part defined ONLY a bullet list
        // (numId 1). Numbered lists now get their own decimal definition
        // (numId 2, levels 0-5: decimal, lower-letter, lower-roman, ...).
        val decimalLevels = StringBuilder()
        val levelFormats = listOf("decimal", "lowerLetter", "lowerRoman", "decimal", "lowerLetter", "lowerRoman")
        val levelTexts = listOf("%1.", "%2.", "%3.", "%4.", "%5.", "%6.")
        for (ilvl in 0..5) {
            decimalLevels.append("""
        <w:lvl w:ilvl="$ilvl">
            <w:start w:val="1"/>
            <w:numFmt w:val="${levelFormats[ilvl]}"/>
            <w:lvlText w:val="${levelTexts[ilvl]}"/>
            <w:lvlJc w:val="left"/>
            <w:pPr><w:ind w:left="${720 * (ilvl + 1)}" w:hanging="360"/></w:pPr>
        </w:lvl>""")
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:abstractNum w:abstractNumId="0">
        <w:lvl w:ilvl="0">
            <w:start w:val="1"/>
            <w:numFmt w:val="bullet"/>
            <w:lvlText w:val="•"/>
            <w:lvlJc w:val="left"/>
            <w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr>
        </w:lvl>
    </w:abstractNum>
    <w:abstractNum w:abstractNumId="1">$decimalLevels
    </w:abstractNum>
    <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
    <w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
</w:numbering>"""
    }

    private fun buildContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Default Extension="png" ContentType="image/png"/>
    <Default Extension="jpeg" ContentType="image/jpeg"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
    <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
    <Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
</Types>"""
    }

    private fun buildPackageRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
    }

    private fun buildDocumentRelsXml(images: List<ResolvedImage> = emptyList()): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sb.append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        // Numbering part (was missing: Word could not always resolve numPr).
        sb.append("""<Relationship Id="rIdNum" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""")
        for (image in images) {
            sb.append("""<Relationship Id="${image.rId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${image.mediaName}"/>""")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:docDefaults>
        <w:rPrDefault>
            <w:rPr>
                <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>
                <w:sz w:val="22"/>
                <w:szCs w:val="22"/>
                <w:lang w:val="en-US"/>
            </w:rPr>
        </w:rPrDefault>
    </w:docDefaults>
    <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
        <w:name w:val="Normal"/>
        <w:qFormat/>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Heading1">
        <w:name w:val="heading 1"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:rPr>
            <w:b/>
            <w:sz w:val="40"/>
            <w:color w:val="185ABD"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Heading2">
        <w:name w:val="heading 2"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:rPr>
            <w:b/>
            <w:sz w:val="32"/>
            <w:color w:val="0F4C81"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Heading3">
        <w:name w:val="heading 3"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:rPr>
            <w:b/>
            <w:sz w:val="28"/>
            <w:color w:val="185ABD"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Title">
        <w:name w:val="Title"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:rPr>
            <w:b/>
            <w:sz w:val="52"/>
            <w:color w:val="185ABD"/>
        </w:rPr>
    </w:style>
</w:styles>"""
    }

    private fun buildDocumentXml(
        document: DocumentModel,
        images: Map<String, ResolvedImage> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<w:body>""")

        for (block in document.blocks) {
            when (block) {
                is ParagraphBlock -> {
                    sb.append("<w:p>")
                    sb.append("<w:pPr>")

                    // Style
                    val styleId = when (block.style) {
                        ParagraphStyle.TITLE -> "Title"
                        ParagraphStyle.HEADING_1 -> "Heading1"
                        ParagraphStyle.HEADING_2 -> "Heading2"
                        ParagraphStyle.HEADING_3 -> "Heading3"
                        else -> "Normal"
                    }
                    sb.append("""<w:pStyle w:val="$styleId"/>""")

                    // Alignment
                    val jcVal = when (block.alignment) {
                        BlockAlignment.CENTER -> "center"
                        BlockAlignment.RIGHT -> "right"
                        BlockAlignment.JUSTIFY -> "both"
                        BlockAlignment.LEFT -> "left"
                    }
                    sb.append("""<w:jc w:val="$jcVal"/>""")

                    // Proper OOXML list numbering (no baked-in "• " prefix —
                    // that caused duplicated bullets on re-import). Bullets use
                    // numId 1, numbered lists numId 2, with the REAL indent
                    // level (the old code hardcoded ilvl 0 and wrote nothing
                    // at all for numbered lists, so their numbering vanished).
                    if (block.listType == ListType.BULLET) {
                        sb.append("""<w:numPr><w:ilvl w:val="${block.listLevel}"/><w:numId w:val="1"/></w:numPr>""")
                    } else if (block.listType == ListType.NUMBERED) {
                        sb.append("""<w:numPr><w:ilvl w:val="${block.listLevel}"/><w:numId w:val="2"/></w:numPr>""")
                    }

                    // Bullet / Indentation
                    if (block.listType != ListType.NONE || block.listLevel > 0) {
                        val leftTwips = 720 * (block.listLevel + 1)
                        sb.append("""<w:ind w:left="$leftTwips"/>""")
                    }

                    sb.append("</w:pPr>")

                    // Runs
                    for (run in block.runs) {
                        sb.append("<w:r>")
                        sb.append("<w:rPr>")

                        if (run.isBold) sb.append("<w:b/>")
                        if (run.isItalic) sb.append("<w:i/>")
                        if (run.isUnderline) sb.append("""<w:u w:val="single"/>""")
                        if (run.isStrikethrough) sb.append("<w:strike/>")
                        if (run.isSuperscript) sb.append("""<w:vertAlign w:val="superscript"/>""")
                        if (run.isSubscript) sb.append("""<w:vertAlign w:val="subscript"/>""")

                        // Font size in half-points
                        val szVal = (run.fontSizePt * 2).toInt()
                        sb.append("""<w:sz w:val="$szVal"/>""")
                        sb.append("""<w:szCs w:val="$szVal"/>""")

                        // Font Color (hex without #)
                        val cleanColor = run.fontColorHex.replace("#", "").take(6)
                        if (cleanColor.isNotEmpty()) {
                            sb.append("""<w:color w:val="$cleanColor"/>""")
                        }

                        // Highlight
                        if (run.highlightHex != null) {
                            sb.append("""<w:highlight w:val="yellow"/>""")
                        }

                        // Font family
                        val family = escapeXml(run.fontFamily)
                        sb.append("""<w:rFonts w:ascii="$family" w:hAnsi="$family" w:cs="$family"/>""")

                        sb.append("</w:rPr>")

                        // Text (bullet glyph comes from numbering.xml via numPr, not baked in)
                        sb.append("""<w:t xml:space="preserve">${escapeXml(run.text)}</w:t>""")
                        sb.append("</w:r>")
                    }

                    sb.append("</w:p>")
                }

                is TableBlock -> {
                    sb.append("<w:tbl>")
                    sb.append("""<w:tblPr><w:tblW w:w="5000" w:type="pct"/><w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="D1D5DB"/><w:left w:val="single" w:sz="4" w:space="0" w:color="D1D5DB"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="D1D5DB"/><w:right w:val="single" w:sz="4" w:space="0" w:color="D1D5DB"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="E5E7EB"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="E5E7EB"/></w:tblBorders></w:tblPr>""")

                    for (row in block.cells) {
                        sb.append("<w:tr>")
                        for (cell in row) {
                            sb.append("<w:tc>")
                            sb.append("<w:tcPr>")
                            if (cell.backgroundColorHex != null) {
                                val cleanHex = cell.backgroundColorHex.replace("#", "").take(6)
                                sb.append("""<w:shd w:val="clear" w:color="auto" w:fill="$cleanHex"/>""")
                            }
                            sb.append("</w:tcPr>")

                            sb.append("<w:p>")
                            sb.append("<w:r>")
                            if (cell.isHeader || cell.isBold) {
                                sb.append("<w:rPr><w:b/><w:sz w:val=\"24\"/></w:rPr>")
                            }
                            sb.append("""<w:t xml:space="preserve">${escapeXml(cell.text)}</w:t>""")
                            sb.append("</w:r>")
                            sb.append("</w:p>")
                            sb.append("</w:tc>")
                        }
                        sb.append("</w:tr>")
                    }
                    sb.append("</w:tbl>")
                }

                is HorizontalRuleBlock -> {
                    sb.append("""<w:p><w:pPr><w:pBdr><w:bottom w:val="single" w:sz="12" w:space="1" w:color="185ABD"/></w:pBdr></w:pPr></w:p>""")
                }

                is PageBreakBlock -> {
                    sb.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
                }

                is ImageBlock -> {
                    val image = images[block.id]
                    if (image != null) {
                        // FIX (images were text-only): embed the real picture
                        // as drawingML (wp:inline + a:blip) in word/media/.
                        // 1 dp = 5715 EMU; size honors the block's width/height.
                        val cx = (block.widthDp.coerceIn(40f, 600f) * 5715).toInt()
                        val cy = (block.heightDp.coerceIn(40f, 600f) * 5715).toInt()
                        val name = escapeXml(block.caption ?: "Image")
                        sb.append("""<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing>""")
                        sb.append("""<wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">""")
                        sb.append("""<wp:extent cx="$cx" cy="$cy"/><wp:docPr id="1" name="$name"/>""")
                        sb.append("""<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">""")
                        sb.append("""<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="1" name="${image.mediaName}"/><pic:cNvPicPr/></pic:nvPicPr>""")
                        sb.append("""<pic:blipFill><a:blip r:embed="${image.rId}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>""")
                        sb.append("""<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>""")
                        sb.append("""</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")
                    } else {
                        // No bytes available (old content:// URI, unreadable
                        // file): keep the old placeholder so export still works.
                        sb.append("""<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t xml:space="preserve">[Image: ${escapeXml(block.caption ?: "Attached Image")}]</w:t></w:r></w:p>""")
                    }
                }
            }
        }

        // Section settings (Paper size & margins in twips: 1 pt = 20 twips)
        val pWidthTwips = document.pageSettings.pageSize.widthPt * 20
        val pHeightTwips = document.pageSettings.pageSize.heightPt * 20
        val orientStr = if (document.pageSettings.orientation == PageOrientation.LANDSCAPE) "landscape" else "portrait"
        val topTwips = document.pageSettings.margins.topPt * 20
        val bottomTwips = document.pageSettings.margins.bottomPt * 20
        val leftTwips = document.pageSettings.margins.leftPt * 20
        val rightTwips = document.pageSettings.margins.rightPt * 20

        sb.append("<w:sectPr>")
        sb.append("""<w:pgSz w:w="$pWidthTwips" w:h="$pHeightTwips" w:orient="$orientStr"/>""")
        sb.append("""<w:pgMar w:top="$topTwips" w:bottom="$bottomTwips" w:left="$leftTwips" w:right="$rightTwips"/>""")
        sb.append("</w:sectPr>")

        sb.append("</w:body>")
        sb.append("</w:document>")

        return sb.toString()
    }

    private fun parseDocumentXml(
        inputStream: InputStream,
        title: String,
        numbering: Map<Pair<Int, Int>, String> = emptyMap()
    ): DocumentModel {
        val blocks = mutableListOf<DocumentBlock>()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var inParagraph = false
        var inTable = false
        var inTableRow = false
        var inTableCell = false

        var curStyle = ParagraphStyle.NORMAL
        var curAlign = BlockAlignment.LEFT
        var curListType = ListType.NONE
        var curListLevel = 0
        var inNumPr = false
        var curNumId = 1
        var curIlvl = 0
        var curRuns = mutableListOf<TextRun>()

        var isBoldRun = false
        var isItalicRun = false
        var isUnderlineRun = false
        var isStrikeRun = false
        var runColor = "#1A1A1A"
        var runFontSize = 11f
        var runFontFamily = "Calibri"

        val tableRows = mutableListOf<MutableList<TableCell>>()
        var curRowCells = mutableListOf<TableCell>()
        var curCellText = StringBuilder()
        var curCellBgColor: String? = null
        var curCellIsHeader = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "tbl" -> {
                            inTable = true
                            tableRows.clear()
                        }
                        "tr" -> {
                            inTableRow = true
                            curRowCells = mutableListOf()
                        }
                        "tc" -> {
                            inTableCell = true
                            curCellText = StringBuilder()
                            curCellBgColor = null
                            curCellIsHeader = false
                        }
                        "shd" -> {
                            val fill = attrValue(parser, "fill")
                            if (!fill.isNullOrEmpty() && fill != "auto" && fill != "clear") {
                                curCellBgColor = "#$fill"
                            }
                        }
                        "p" -> {
                            if (!inTableCell) {
                                inParagraph = true
                                curRuns = mutableListOf()
                                curStyle = ParagraphStyle.NORMAL
                                curAlign = BlockAlignment.LEFT
                                curListType = ListType.NONE
                                curListLevel = 0
                            }
                        }
                        "numPr" -> {
                            // Numbering marker: resolve the real list type and
                            // level from the file's numbering part (see
                            // parseNumberingXml). The old code forced every
                            // numPr to BULLET, turning Word numbered lists
                            // into bullet lists on import.
                            inNumPr = true
                            curNumId = 1
                            curIlvl = 0
                        }
                        "ilvl" -> {
                            if (inNumPr) curIlvl = attrValue(parser, "val")?.toIntOrNull() ?: 0
                        }
                        "numId" -> {
                            if (inNumPr) curNumId = attrValue(parser, "val")?.toIntOrNull() ?: 1
                        }
                        "pStyle" -> {
                            val styleVal = attrValue(parser, "val") ?: ""
                            curStyle = when (styleVal.lowercase()) {
                                "title" -> ParagraphStyle.TITLE
                                "heading1", "heading 1" -> ParagraphStyle.HEADING_1
                                "heading2", "heading 2" -> ParagraphStyle.HEADING_2
                                "heading3", "heading 3" -> ParagraphStyle.HEADING_3
                                "subtitle" -> ParagraphStyle.SUBTITLE
                                else -> ParagraphStyle.NORMAL
                            }
                        }
                        "jc" -> {
                            val jcVal = attrValue(parser, "val") ?: ""
                            curAlign = when (jcVal.lowercase()) {
                                "center" -> BlockAlignment.CENTER
                                "right" -> BlockAlignment.RIGHT
                                "both" -> BlockAlignment.JUSTIFY
                                else -> BlockAlignment.LEFT
                            }
                        }
                        "r" -> {
                            isBoldRun = false
                            isItalicRun = false
                            isUnderlineRun = false
                            isStrikeRun = false
                            runColor = "#1A1A1A"
                            runFontSize = 11f
                            runFontFamily = "Calibri"
                        }
                        "b" -> isBoldRun = true
                        "i" -> isItalicRun = true
                        "u" -> isUnderlineRun = true
                        "strike" -> isStrikeRun = true
                        "color" -> {
                            val colorVal = attrValue(parser, "val")
                            if (!colorVal.isNullOrEmpty() && colorVal != "auto") {
                                runColor = "#$colorVal"
                            }
                        }
                        "sz" -> {
                            val szVal = attrValue(parser, "val")?.toIntOrNull()
                            if (szVal != null && szVal > 0) {
                                runFontSize = (szVal / 2f).coerceIn(8f, 72f)
                            }
                        }
                        "rFonts" -> {
                            val ascii = attrValue(parser, "ascii")
                            if (!ascii.isNullOrEmpty()) {
                                runFontFamily = ascii
                            }
                        }
                        "br" -> {
                            val type = attrValue(parser, "type")
                            if (type == "page") {
                                blocks.add(PageBreakBlock())
                            }
                        }
                        "t" -> {
                            val text = parser.nextText() ?: ""
                            if (inTableCell) {
                                if (curCellText.isNotEmpty()) curCellText.append(" ")
                                curCellText.append(text)
                                if (isBoldRun) curCellIsHeader = true
                            } else if (inParagraph) {
                                curRuns.add(
                                    TextRun(
                                        text = text,
                                        isBold = isBoldRun,
                                        isItalic = isItalicRun,
                                        isUnderline = isUnderlineRun,
                                        isStrikethrough = isStrikeRun,
                                        fontSizePt = runFontSize,
                                        fontColorHex = runColor,
                                        fontFamily = runFontFamily
                                    )
                                )
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "numPr" -> {
                            // Resolve the list type/level now that ilvl + numId
                            // are both known. Unknown numIds keep the legacy
                            // bullet mapping (old exporter wrote numId 1 only).
                            val fmt = numbering[curNumId to curIlvl]
                                ?: numbering[curNumId to 0]
                            curListType =
                                if (fmt == null || fmt == "bullet") ListType.BULLET else ListType.NUMBERED
                            curListLevel = curIlvl.coerceIn(0, 8)
                            inNumPr = false
                        }
                        "p" -> {
                            if (inParagraph && !inTableCell) {
                                inParagraph = false
                                var cleanedRuns = if (curRuns.isEmpty()) listOf(TextRun("")) else curRuns
                                // Strip legacy baked-in "• " prefix (old exporter versions
                                // wrote it into the text); numbering now comes from numPr.
                                if (cleanedRuns.isNotEmpty() && cleanedRuns[0].text.startsWith("• ")) {
                                    cleanedRuns = cleanedRuns.toMutableList().also {
                                        it[0] = it[0].copy(text = it[0].text.removePrefix("• "))
                                    }
                                }
                                blocks.add(
                                    ParagraphBlock(
                                        runs = cleanedRuns,
                                        style = curStyle,
                                        alignment = curAlign,
                                        listType = curListType,
                                        listLevel = curListLevel
                                    )
                                )
                            }
                        }
                        "tc" -> {
                            inTableCell = false
                            curRowCells.add(
                                TableCell(
                                    text = curCellText.toString(),
                                    isHeader = curCellIsHeader,
                                    backgroundColorHex = curCellBgColor
                                )
                            )
                        }
                        "tr" -> {
                            inTableRow = false
                            if (curRowCells.isNotEmpty()) {
                                tableRows.add(curRowCells)
                            }
                        }
                        "tbl" -> {
                            inTable = false
                            if (tableRows.isNotEmpty()) {
                                val cols = tableRows.maxOfOrNull { it.size } ?: 1
                                // Normalize row cell counts
                                val normalized = tableRows.map { r ->
                                    if (r.size < cols) {
                                        r + List(cols - r.size) { TableCell() }
                                    } else r
                                }
                                blocks.add(
                                    TableBlock(
                                        rows = normalized.size,
                                        cols = cols,
                                        cells = normalized
                                    )
                                )
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (blocks.isEmpty()) {
            blocks.add(ParagraphBlock())
        }

        return DocumentModel(
            title = title,
            blocks = blocks
        )
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
