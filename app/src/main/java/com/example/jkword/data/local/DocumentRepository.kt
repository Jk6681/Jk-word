package com.example.jkword.data.local

import android.content.Context
import com.example.jkword.engine.docx.DocxEngine
import com.example.jkword.engine.pdf.PdfExporter
import com.example.jkword.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class DocumentRepository(
    private val context: Context,
    private val dao: DocumentDao,
    private val versionDao: DocumentVersionDao = WordDatabase.getInstance(context).documentVersionDao()
) {
    private val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val checkpointsDir = File(context.filesDir, "checkpoints").apply { mkdirs() }
    private val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
    private val imagesDir = File(context.filesDir, "images").apply { mkdirs() }

    /**
     * FIX (images were URI-only): copy a picked image into app-private
     * storage (filesDir/images/) and return the copy's path, so the document
     * stays portable across restarts and the export engines can read it.
     * Returns null when the copy fails; callers then keep the original URI.
     */
    fun copyImageToAppStorage(uriString: String): String? {
        return try {
            if (uriString.startsWith(imagesDir.absolutePath)) return uriString
            val mimeExt = when (runCatching {
                context.contentResolver.getType(android.net.Uri.parse(uriString))
            }.getOrNull()?.lowercase()) {
                "image/png" -> "png"
                "image/jpeg", "image/jpg" -> "jpg"
                "image/webp" -> "webp"
                else -> null
            }
            val nameExt = uriString.substringAfterLast('.', "")
                .lowercase().takeIf { it in setOf("png", "jpg", "jpeg", "webp") }
            val target = File(imagesDir, "${java.util.UUID.randomUUID()}.${nameExt ?: mimeExt ?: "jpg"}")
            if (uriString.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: return null
            } else {
                File(uriString).inputStream().use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
            if (target.exists() && target.length() > 0) target.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    /** Resolve an ImageBlock's uriOrPath to raw bytes for DOCX/PDF export. */
    fun resolveImageBytes(uriOrPath: String): ByteArray? {
        return try {
            if (uriOrPath.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(uriOrPath))
                    ?.use { it.readBytes() }
            } else {
                val file = File(uriOrPath)
                if (file.exists()) file.readBytes() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    val activeDocuments: Flow<List<DocumentEntity>> = dao.getAllActive()
    val starredDocuments: Flow<List<DocumentEntity>> = dao.getStarred()
    val trashDocuments: Flow<List<DocumentEntity>> = dao.getTrash()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> = dao.search(query)

    suspend fun getDocumentEntity(id: Long): DocumentEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun loadDocumentModel(entity: DocumentEntity): DocumentModel = withContext(Dispatchers.IO) {
        val file = File(entity.filePath)
        if (file.exists()) {
            try {
                val json = file.readText()
                return@withContext JsonDocumentSerializer.deserialize(json)
            } catch (e: Exception) {
                // If json read fails, check recovery checkpoint
                val checkpointFile = File(checkpointsDir, "${entity.docUuid}.checkpoint")
                if (checkpointFile.exists()) {
                    try {
                        val checkpointJson = checkpointFile.readText()
                        return@withContext JsonDocumentSerializer.deserialize(checkpointJson)
                    } catch (e2: Exception) {
                        // FIX (crash safety): a corrupt checkpoint used to
                        // throw out of here and crash the app. Fall through
                        // to a blank document instead — never crash on load.
                    }
                }
            }
        }
        // Fallback default
        DocumentTemplates.createBlankDocument(entity.title)
    }

    suspend fun createNewDocument(templateInfo: DocumentTemplates.TemplateInfo): Long = withContext(Dispatchers.IO) {
        val docModel = templateInfo.factory()
        val file = File(docsDir, "${docModel.id}.json")
        val json = JsonDocumentSerializer.serialize(docModel)
        file.writeText(json)

        val snippet = extractSnippet(docModel)
        val wordStats = calculateStats(docModel)

        val entity = DocumentEntity(
            docUuid = docModel.id,
            title = docModel.title,
            filePath = file.absolutePath,
            previewSnippet = snippet,
            wordCount = wordStats.wordCount,
            charCount = wordStats.charCount,
            pageCount = wordStats.estimatedPages,
            lastModified = docModel.modifiedAt,
            createdAt = docModel.createdAt,
            templateType = templateInfo.id,
            pageSize = docModel.pageSettings.pageSize.name,
            orientation = docModel.pageSettings.orientation.name,
            fileSize = file.length()
        )
        dao.insert(entity)
    }

    suspend fun saveDocument(documentId: Long, model: DocumentModel): Boolean = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getById(documentId) ?: return@withContext false
            val file = File(entity.filePath)

            // Safe temporary write to avoid data corruption. The temp name
            // is unique per save (old code reused "${model.id}.tmp", so a
            // concurrent autosave + manual save could corrupt each other).
            val tempFile = File(docsDir, "${model.id}-${System.nanoTime()}.tmp")
            val json = JsonDocumentSerializer.serialize(model)
            tempFile.writeText(json)

            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()

                // Also update checkpoint for crash recovery
                val checkpointFile = File(checkpointsDir, "${model.id}.checkpoint")
                checkpointFile.writeText(json)
            }

            val snippet = extractSnippet(model)
            val stats = calculateStats(model)

            val updatedEntity = entity.copy(
                title = model.title,
                previewSnippet = snippet,
                wordCount = stats.wordCount,
                charCount = stats.charCount,
                pageCount = stats.estimatedPages,
                lastModified = System.currentTimeMillis(),
                pageSize = model.pageSettings.pageSize.name,
                orientation = model.pageSettings.orientation.name,
                fileSize = file.length()
            )
            dao.update(updatedEntity)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun renameDocument(id: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        val model = loadDocumentModel(entity)
        val updatedModel = model.copy(title = newTitle, modifiedAt = System.currentTimeMillis())
        saveDocument(id, updatedModel)
    }

    suspend fun duplicateDocument(id: Long): Long = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext 0L
        val model = loadDocumentModel(entity)
        val newId = java.util.UUID.randomUUID().toString()
        val duplicatedModel = model.copy(
            id = newId,
            title = "${model.title} (Copy)",
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

        val newFile = File(docsDir, "$newId.json")
        val json = JsonDocumentSerializer.serialize(duplicatedModel)
        newFile.writeText(json)

        val stats = calculateStats(duplicatedModel)
        val newEntity = DocumentEntity(
            docUuid = newId,
            title = duplicatedModel.title,
            filePath = newFile.absolutePath,
            previewSnippet = extractSnippet(duplicatedModel),
            wordCount = stats.wordCount,
            charCount = stats.charCount,
            pageCount = stats.estimatedPages,
            lastModified = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            isStarred = false,
            templateType = entity.templateType,
            pageSize = duplicatedModel.pageSettings.pageSize.name,
            orientation = duplicatedModel.pageSettings.orientation.name,
            fileSize = newFile.length()
        )
        dao.insert(newEntity)
    }

    suspend fun toggleStarred(id: Long, isStarred: Boolean) = withContext(Dispatchers.IO) {
        dao.toggleStarred(id, isStarred)
    }

    suspend fun moveToTrash(id: Long) = withContext(Dispatchers.IO) {
        dao.moveToTrash(id)
    }

    suspend fun restoreFromTrash(id: Long) = withContext(Dispatchers.IO) {
        dao.restoreFromTrash(id)
    }

    suspend fun deletePermanently(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id)
        if (entity != null) {
            // FIX (orphaned image files): delete this document's app-storage
            // image copies — but only if no other document references them
            // (duplicated documents share the same image files).
            runCatching {
                val model = JsonDocumentSerializer.deserialize(File(entity.filePath).readText())
                val ownImages = model.blocks.filterIsInstance<ImageBlock>()
                    .map { it.uriOrPath }
                    .filter { it.startsWith(imagesDir.absolutePath) }
                if (ownImages.isNotEmpty()) {
                    val otherJson = (dao.getAllActive().first() + dao.getTrash().first())
                        .filter { it.id != id }
                        .mapNotNull { runCatching { File(it.filePath).readText() }.getOrNull() }
                    for (path in ownImages) {
                        val name = File(path).name
                        if (otherJson.none { it.contains(name) }) {
                            File(path).delete()
                        }
                    }
                }
            }
            File(entity.filePath).delete()
            File(checkpointsDir, "${entity.docUuid}.checkpoint").delete()
            versionDao.deleteAllVersionsForDocument(id)
            dao.deleteById(id)
        }
    }

    fun getVersionHistory(docId: Long): Flow<List<DocumentVersionEntity>> {
        return versionDao.getVersionsForDocument(docId)
    }

    suspend fun saveVersionSnapshot(docId: Long, model: DocumentModel, summary: String = "Version checkpoint") = withContext(Dispatchers.IO) {
        try {
            val stats = calculateStats(model)
            val json = JsonDocumentSerializer.serialize(model)
            val count = versionDao.getVersionCount(docId)
            val versionEntity = DocumentVersionEntity(
                documentId = docId,
                versionNumber = count + 1,
                title = model.title,
                snapshotJson = json,
                wordCount = stats.wordCount,
                charCount = stats.charCount,
                timestamp = System.currentTimeMillis(),
                summary = summary
            )
            versionDao.insertVersion(versionEntity)
            versionDao.trimOldVersions(docId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun revertToVersion(docId: Long, versionEntity: DocumentVersionEntity): DocumentModel = withContext(Dispatchers.IO) {
        val revertedModel = JsonDocumentSerializer.deserialize(versionEntity.snapshotJson)
        saveDocument(docId, revertedModel)
        revertedModel
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        dao.emptyTrash()
    }

    /**
     * Imports an external stream (DOCX or TXT) into a new document.
     */
    suspend fun importExternalDocument(inputStream: InputStream, filename: String): Long = withContext(Dispatchers.IO) {
        val title = filename.substringBeforeLast(".")
        val isDocx = filename.endsWith(".docx", ignoreCase = true)

        val model: DocumentModel = if (isDocx) {
            // FIX (import stream bug): a DOCX parse failure is now a real failure.
            // The old code swallowed it into a fake "Note: Advanced OOXML..."
            // paragraph, so users thought the import worked when it had not.
            DocxEngine.importFromDocx(inputStream, title)
        } else {
            // Treat as plain text. Split ONLY on blank lines: a wrapped text
            // file's single newlines stay line breaks inside one paragraph
            // (the old split("\n\n", "\n") made every line its own paragraph),
            // and Windows \r\n line endings are normalized first.
            val text = inputStream.bufferedReader().use { it.readText() }
            val paragraphs = splitPlainTextParagraphs(text)
            val blocks = if (paragraphs.isEmpty()) {
                listOf(ParagraphBlock())
            } else {
                paragraphs.map { p ->
                    ParagraphBlock(runs = listOf(TextRun(p)))
                }
            }
            DocumentModel(title = title, blocks = blocks)
        }

        val file = File(docsDir, "${model.id}.json")
        file.writeText(JsonDocumentSerializer.serialize(model))

        val stats = calculateStats(model)
        val entity = DocumentEntity(
            docUuid = model.id,
            title = model.title,
            filePath = file.absolutePath,
            previewSnippet = extractSnippet(model),
            wordCount = stats.wordCount,
            charCount = stats.charCount,
            pageCount = stats.estimatedPages,
            lastModified = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            templateType = if (isDocx) "docx_imported" else "txt_imported",
            fileSize = file.length()
        )
        dao.insert(entity)
    }

    /**
     * Exports document to cache for sharing.
     */
    suspend fun exportToCache(model: DocumentModel, format: String): File = withContext(Dispatchers.IO) {
        val cleanTitle = model.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val file = File(cacheDir, "$cleanTitle.$format")
        FileOutputStream(file).use { out ->
            when (format.lowercase()) {
                "docx" -> DocxEngine.exportToDocx(model, out, ::resolveImageBytes)
                "pdf" -> PdfExporter.exportToPdf(model, out, ::resolveImageBytes)
                "txt" -> exportToPlainText(model, out)
                else -> DocxEngine.exportToDocx(model, out, ::resolveImageBytes)
            }
        }
        file
    }

    suspend fun exportToImage(model: DocumentModel): File = withContext(Dispatchers.IO) {
        val cleanTitle = model.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val pdfFile = File(cacheDir, "${cleanTitle}_temp.pdf")
        FileOutputStream(pdfFile).use { out ->
            PdfExporter.exportToPdf(model, out, ::resolveImageBytes)
        }
        val imgFile = File(cacheDir, "$cleanTitle.png")
        val pfd = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        if (pageCount > 0) {
            val scale = 2
            var totalHeight = 0
            var maxWidth = 0
            for (i in 0 until pageCount) {
                val p = renderer.openPage(i)
                maxWidth = maxOf(maxWidth, p.width * scale)
                totalHeight += (p.height * scale) + (if (i > 0) 16 else 0)
                p.close()
            }
            val compositeBitmap = android.graphics.Bitmap.createBitmap(
                maxWidth,
                totalHeight,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(compositeBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            var currentY = 0f
            for (i in 0 until pageCount) {
                val p = renderer.openPage(i)
                val pWidth = p.width * scale
                val pHeight = p.height * scale
                val left = (maxWidth - pWidth) / 2f
                val pageBmp = android.graphics.Bitmap.createBitmap(pWidth, pHeight, android.graphics.Bitmap.Config.ARGB_8888)
                val pageCanvas = android.graphics.Canvas(pageBmp)
                pageCanvas.drawColor(android.graphics.Color.WHITE)
                p.render(pageBmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                canvas.drawBitmap(pageBmp, left, currentY, null)
                currentY += pHeight + 16f
                p.close()
            }

            FileOutputStream(imgFile).use { out ->
                compositeBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        renderer.close()
        pfd.close()
        pdfFile.delete()
        imgFile
    }

    fun exportToStream(model: DocumentModel, format: String, outputStream: OutputStream) {
        when (format.lowercase()) {
            "docx" -> DocxEngine.exportToDocx(model, outputStream, ::resolveImageBytes)
            "pdf" -> PdfExporter.exportToPdf(model, outputStream, ::resolveImageBytes)
            "txt" -> exportToPlainText(model, outputStream)
        }
    }

    private fun exportToPlainText(model: DocumentModel, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.write(model.title)
            writer.write("\n========================================\n\n")
            for (block in model.blocks) {
                when (block) {
                    is ParagraphBlock -> {
                        writer.write(block.plainText)
                        writer.write("\n\n")
                    }
                    is TableBlock -> {
                        for (row in block.cells) {
                            writer.write(row.joinToString(" | ") { it.text })
                            writer.write("\n")
                        }
                        writer.write("\n")
                    }
                    is HorizontalRuleBlock -> {
                        writer.write("----------------------------------------\n\n")
                    }
                    is PageBreakBlock -> {
                        writer.write("\n--- [PAGE BREAK] ---\n\n")
                    }
                    is ImageBlock -> {
                        writer.write("[Image: ${block.caption ?: "Attached"}]\n\n")
                    }
                }
            }
        }
    }

    private fun extractSnippet(model: DocumentModel): String {
        for (block in model.blocks) {
            if (block is ParagraphBlock && block.plainText.isNotBlank()) {
                return block.plainText.take(160).replace("\n", " ")
            }
        }
        return "Empty document"
    }

    data class DocumentStats(
        val wordCount: Int,
        val charCount: Int,
        val charCountNoSpaces: Int,
        val paragraphCount: Int,
        val estimatedPages: Int,
        val readingTimeMinutes: Int
    )

    fun calculateStats(model: DocumentModel): DocumentStats {
        var words = 0
        var chars = 0
        var charsNoSpaces = 0
        var paragraphs = 0

        for (block in model.blocks) {
            when (block) {
                is ParagraphBlock -> {
                    paragraphs++
                    val text = block.plainText
                    chars += text.length
                    charsNoSpaces += text.count { !it.isWhitespace() }
                    val wordsInPara = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    words += wordsInPara.size
                }
                is TableBlock -> {
                    paragraphs += block.rows
                    for (row in block.cells) {
                        for (cell in row) {
                            chars += cell.text.length
                            charsNoSpaces += cell.text.count { !it.isWhitespace() }
                            val wordsInCell = cell.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                            words += wordsInCell.size
                        }
                    }
                }
                else -> {}
            }
        }

        val estimatedPages = (words / 350).coerceAtLeast(1)
        val readingTime = (words / 200).coerceAtLeast(1)

        return DocumentStats(
            wordCount = words,
            charCount = chars,
            charCountNoSpaces = charsNoSpaces,
            paragraphCount = paragraphs,
            estimatedPages = estimatedPages,
            readingTimeMinutes = readingTime
        )
    }
}
