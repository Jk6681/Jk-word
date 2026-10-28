package com.example.jkword.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.text.TextRange
import com.example.jkword.data.local.DocumentEntity
import com.example.jkword.data.local.DocumentRepository
import com.example.jkword.data.local.DocumentVersionEntity
import com.example.jkword.data.local.WordDatabase
import com.example.jkword.engine.RichTextEngine
import com.example.jkword.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class SaveStatus {
    SAVED,
    SAVING,
    DIRTY,
    ERROR
}

enum class RibbonTab {
    HOME,
    INSERT,
    LAYOUT,
    REVIEW,
    TABLE
}

enum class ViewMode {
    PAGE_LAYOUT,
    MOBILE_CONTINUOUS
}

data class EditorUiState(
    val document: DocumentModel = DocumentModel(),
    val documentEntity: DocumentEntity? = null,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val selectedBlockIndex: Int = 0,
    val selectedTableCell: Pair<Int, Int>? = null, // row, col
    val activeRibbonTab: RibbonTab = RibbonTab.HOME,
    val isFindReplaceVisible: Boolean = false,
    val findQuery: String = "",
    val replaceQuery: String = "",
    val matchIndices: List<Int> = emptyList(),
    val currentMatchIndex: Int = 0,
    val viewMode: ViewMode = ViewMode.PAGE_LAYOUT,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showStatsDialog: Boolean = false,
    val showInsertTableDialog: Boolean = false,
    val showLinkDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showHistoryDialog: Boolean = false,
    val versionHistory: List<DocumentVersionEntity> = emptyList(),
    val activeTypingStyle: TextRun = TextRun(""),
    val currentSelectionRange: TextRange? = null,
    val toastMessage: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(
        context = application.applicationContext,
        dao = WordDatabase.getInstance(application.applicationContext).documentDao()
    )

    private val undoRedoManager = UndoRedoManager(maxHistory = 40)
    private var autosaveJob: Job? = null
    private var documentId: Long = 0L
    private var lastTypingUndoTime: Long = 0L
    // Serializes autosave vs manual save (see performSave / triggerAutosave).
    private val saveMutex = Mutex()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var versionHistoryJob: Job? = null
    private var lastVersionSnapshotTime: Long = System.currentTimeMillis()

    fun loadDocument(id: Long) {
        documentId = id
        viewModelScope.launch {
            val entity = repository.getDocumentEntity(id)
            if (entity != null) {
                val model = repository.loadDocumentModel(entity)
                undoRedoManager.clear()
                _uiState.update {
                    it.copy(
                        document = model,
                        documentEntity = entity,
                        saveStatus = SaveStatus.SAVED,
                        canUndo = false,
                        canRedo = false,
                        selectedBlockIndex = 0
                    )
                }
                repository.saveVersionSnapshot(id, model, "Initial / Open document")
            }
        }
        observeVersionHistory(id)
    }

    private fun observeVersionHistory(id: Long) {
        versionHistoryJob?.cancel()
        versionHistoryJob = viewModelScope.launch {
            repository.getVersionHistory(id).collect { versions ->
                _uiState.update { it.copy(versionHistory = versions) }
            }
        }
    }

    private fun mutateDocument(newDoc: DocumentModel) {
        val currentDoc = _uiState.value.document
        undoRedoManager.pushState(currentDoc)
        _uiState.update {
            it.copy(
                document = newDoc.copy(modifiedAt = System.currentTimeMillis()),
                saveStatus = SaveStatus.DIRTY,
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo
            )
        }
        triggerAutosave()
    }

    private fun triggerAutosave() {
        autosaveJob?.cancel()
        // Nothing to persist until a document is actually loaded/created.
        if (documentId == 0L) return
        autosaveJob = viewModelScope.launch {
            delay(1200) // 1.2 second debounce without flashing on every keystroke
            // FIX (concurrent saves): serialize with manual saves so an
            // autosave and a manual save never write the same file at once.
            saveMutex.withLock {
                val currentDoc = _uiState.value.document
                val success = repository.saveDocument(documentId, currentDoc)
                if (success && System.currentTimeMillis() - lastVersionSnapshotTime > 60_000) {
                    repository.saveVersionSnapshot(documentId, currentDoc, "Auto-saved checkpoint")
                    lastVersionSnapshotTime = System.currentTimeMillis()
                }
                _uiState.update {
                    it.copy(saveStatus = if (success) SaveStatus.SAVED else SaveStatus.ERROR)
                }
            }
        }
    }

    fun manualSave() {
        autosaveJob?.cancel()
        viewModelScope.launch { performSave(isManual = true) }
    }

    /** Suspend version: completes the save before returning (for BackHandler etc.). */
    suspend fun manualSaveAndWait() {
        autosaveJob?.cancel()
        performSave(isManual = true)
    }

    private suspend fun performSave(isManual: Boolean) {
        // FIX (phantom save): never run a save with no document loaded — the
        // old code called saveDocument(0, ...) which always failed.
        if (documentId == 0L) {
            _uiState.update { it.copy(toastMessage = "No document open to save") }
            return
        }
        // FIX (concurrent saves): one save at a time, autosave included.
        saveMutex.withLock {
            _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
            val currentDoc = _uiState.value.document
            val success = repository.saveDocument(documentId, currentDoc)
            if (success) {
                repository.saveVersionSnapshot(documentId, currentDoc, "Manual save version")
                lastVersionSnapshotTime = System.currentTimeMillis()
            }
            _uiState.update {
                it.copy(
                    saveStatus = if (success) SaveStatus.SAVED else SaveStatus.ERROR,
                    toastMessage = if (success) "Document saved" else "Save failed"
                )
            }
        }
    }

    fun revertToVersion(version: DocumentVersionEntity) {
        viewModelScope.launch {
            val currentDoc = _uiState.value.document
            undoRedoManager.pushState(currentDoc)
            val revertedModel = repository.revertToVersion(documentId, version)
            _uiState.update {
                it.copy(
                    document = revertedModel,
                    saveStatus = SaveStatus.SAVED,
                    canUndo = undoRedoManager.canUndo,
                    canRedo = undoRedoManager.canRedo,
                    toastMessage = "Reverted to Version ${version.versionNumber}"
                )
            }
        }
    }

    fun setShowHistoryDialog(show: Boolean) {
        _uiState.update { it.copy(showHistoryDialog = show) }
    }

    fun undo() {
        val currentDoc = _uiState.value.document
        val prevDoc = undoRedoManager.undo(currentDoc)
        if (prevDoc != null) {
            _uiState.update {
                it.copy(
                    document = prevDoc,
                    saveStatus = SaveStatus.DIRTY,
                    canUndo = undoRedoManager.canUndo,
                    canRedo = undoRedoManager.canRedo
                )
            }
            triggerAutosave()
        }
    }

    fun redo() {
        val currentDoc = _uiState.value.document
        val nextDoc = undoRedoManager.redo(currentDoc)
        if (nextDoc != null) {
            _uiState.update {
                it.copy(
                    document = nextDoc,
                    saveStatus = SaveStatus.DIRTY,
                    canUndo = undoRedoManager.canUndo,
                    canRedo = undoRedoManager.canRedo
                )
            }
            triggerAutosave()
        }
    }

    fun updateTitle(newTitle: String) {
        // Blank titles are replaced with a default instead of being silently ignored,
        // so the UI never shows a stale title after the user clears the field.
        val title = newTitle.ifBlank { "Untitled Document" }
        mutateDocument(_uiState.value.document.copy(title = title))
    }

    fun selectBlock(index: Int) {
        val blocks = _uiState.value.document.blocks
        val safeIndex = index.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
        val isTable = blocks.getOrNull(safeIndex) is TableBlock
        _uiState.update {
            it.copy(
                selectedBlockIndex = safeIndex,
                activeRibbonTab = if (isTable) RibbonTab.TABLE else it.activeRibbonTab
            )
        }
    }

    fun selectRibbonTab(tab: RibbonTab) {
        _uiState.update { it.copy(activeRibbonTab = tab) }
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(
                viewMode = if (it.viewMode == ViewMode.PAGE_LAYOUT) ViewMode.MOBILE_CONTINUOUS else ViewMode.PAGE_LAYOUT
            )
        }
    }

    // --- Block Text Edits ---
    fun onSelectionChange(blockIndex: Int, selection: TextRange) {
        val currentState = _uiState.value
        if (currentState.selectedBlockIndex == blockIndex && currentState.currentSelectionRange == selection) {
            return
        }

        val block = currentState.document.blocks.getOrNull(blockIndex) as? ParagraphBlock
        val styleAtCursor = if (block != null && block.runs.isNotEmpty()) {
            if (selection.collapsed) {
                RichTextEngine.getStyleAtOffset(block.runs, selection.start)
            } else {
                RichTextEngine.getStyleAtOffset(block.runs, kotlin.math.min(selection.start, selection.end))
            }
        } else {
            currentState.activeTypingStyle
        }

        _uiState.update {
            it.copy(
                selectedBlockIndex = blockIndex,
                currentSelectionRange = selection,
                activeTypingStyle = styleAtCursor
            )
        }
    }

    /**
     * Ultra-fast atomic typing and editing engine for Version 8.0.
     * Computes the minimal diff, updates runs preserving all other formatting,
     * updates active typing style at new cursor position, and emits a single atomic UI state.
     */
    fun onParagraphEdited(
        blockIndex: Int,
        oldText: String,
        newText: String,
        newSelection: TextRange
    ) {
        if (oldText == newText) {
            onSelectionChange(blockIndex, newSelection)
            return
        }

        val blocks = _uiState.value.document.blocks.toMutableList()
        val currentBlock = blocks.getOrNull(blockIndex) as? ParagraphBlock ?: return

        val now = System.currentTimeMillis()
        if (now - lastTypingUndoTime > 1500) {
            undoRedoManager.pushState(_uiState.value.document)
            lastTypingUndoTime = now
        }

        val updatedRuns = RichTextEngine.applyTextEdit(
            runs = currentBlock.runs,
            oldText = oldText,
            newText = newText,
            style = _uiState.value.activeTypingStyle
        )
        blocks[blockIndex] = currentBlock.copy(runs = updatedRuns)

        val newDoc = _uiState.value.document.copy(
            blocks = blocks,
            modifiedAt = now
        )

        val cursorOffset = if (newSelection.collapsed) {
            newSelection.start
        } else {
            kotlin.math.min(newSelection.start, newSelection.end)
        }
        val styleAtCursor = RichTextEngine.getStyleAtOffset(updatedRuns, cursorOffset)

        _uiState.update {
            it.copy(
                document = newDoc,
                selectedBlockIndex = blockIndex,
                currentSelectionRange = newSelection,
                activeTypingStyle = styleAtCursor,
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo,
                saveStatus = SaveStatus.DIRTY
            )
        }
        triggerAutosave()
    }

    fun insertTextInParagraph(blockIndex: Int, insertOffset: Int, insertedText: String) {
        val currentBlock = _uiState.value.document.blocks.getOrNull(blockIndex) as? ParagraphBlock ?: return
        val oldText = currentBlock.plainText
        val safeOffset = insertOffset.coerceIn(0, oldText.length)
        val newText = oldText.substring(0, safeOffset) + insertedText + oldText.substring(safeOffset)
        val newSel = TextRange(safeOffset + insertedText.length)
        onParagraphEdited(blockIndex, oldText, newText, newSel)
    }

    fun deleteTextInParagraph(blockIndex: Int, deleteStart: Int, deleteEnd: Int) {
        val currentBlock = _uiState.value.document.blocks.getOrNull(blockIndex) as? ParagraphBlock ?: return
        val oldText = currentBlock.plainText
        val s = kotlin.math.min(deleteStart, deleteEnd).coerceIn(0, oldText.length)
        val e = kotlin.math.max(deleteStart, deleteEnd).coerceIn(0, oldText.length)
        if (s >= e) return
        val newText = oldText.substring(0, s) + oldText.substring(e)
        val newSel = TextRange(s)
        onParagraphEdited(blockIndex, oldText, newText, newSel)
    }

    fun updateParagraphText(blockIndex: Int, newText: String) {
        val currentBlock = _uiState.value.document.blocks.getOrNull(blockIndex) as? ParagraphBlock ?: return
        val oldText = currentBlock.plainText
        val newSel = TextRange(newText.length)
        onParagraphEdited(blockIndex, oldText, newText, newSel)
    }

    fun addNewPage() {
        val blocks = _uiState.value.document.blocks.toMutableList()
        blocks.add(PageBreakBlock())
        blocks.add(ParagraphBlock(runs = listOf(_uiState.value.activeTypingStyle.copy(text = ""))))
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
        selectBlock(blocks.size - 1)
        _uiState.update { it.copy(toastMessage = "New page created") }
    }

    fun insertParagraphAfter(blockIndex: Int) {
        val blocks = _uiState.value.document.blocks.toMutableList()
        val insertAt = (blockIndex + 1).coerceIn(0, blocks.size)
        val newStyle = _uiState.value.activeTypingStyle.copy(text = "")
        blocks.add(insertAt, ParagraphBlock(runs = listOf(newStyle)))
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
        selectBlock(insertAt)
    }

    fun deleteBlock(blockIndex: Int) {
        val blocks = _uiState.value.document.blocks.toMutableList()
        if (blocks.size <= 1) {
            // Keep at least one empty paragraph
            blocks[0] = ParagraphBlock(runs = listOf(_uiState.value.activeTypingStyle.copy(text = "")))
        } else {
            blocks.removeAt(blockIndex)
        }
        val newSelection = (blockIndex - 1).coerceAtLeast(0)
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
        selectBlock(newSelection)
    }

    // --- Text & Paragraph Formatting ---
    /**
     * Applies formatting changes.
     * If text IS selected: strictly transforms only the selected characters via RichTextEngine.
     * If NO text is selected: preserves existing text unchanged and updates activeTypingStyle for future typing.
     */
    fun updateFormatting(transform: (TextRun) -> TextRun) {
        val state = _uiState.value
        val blockIndex = state.selectedBlockIndex
        val block = state.document.blocks.getOrNull(blockIndex) as? ParagraphBlock
        val selection = state.currentSelectionRange

        val hasNonEmptySelection = selection != null && !selection.collapsed &&
                selection.start != selection.end

        if (block != null && hasNonEmptySelection && block.plainText.isNotEmpty()) {
            // Selection is active: format ONLY the selected range!
            val updatedRuns = RichTextEngine.applyFormattingToRange(block.runs, selection, transform)
            val blocks = state.document.blocks.toMutableList()
            blocks[blockIndex] = block.copy(runs = updatedRuns)
            val newDoc = state.document.copy(blocks = blocks, modifiedAt = System.currentTimeMillis())
            val newActiveStyle = transform(state.activeTypingStyle)

            undoRedoManager.pushState(state.document)
            _uiState.update {
                it.copy(
                    document = newDoc,
                    activeTypingStyle = newActiveStyle,
                    canUndo = undoRedoManager.canUndo,
                    canRedo = undoRedoManager.canRedo,
                    saveStatus = SaveStatus.DIRTY
                )
            }
            triggerAutosave()
        } else {
            // No selection: update active typing style only without modifying existing characters!
            val newActiveStyle = transform(state.activeTypingStyle)
            _uiState.update { it.copy(activeTypingStyle = newActiveStyle) }
        }
    }

    fun toggleBold() {
        updateFormatting { it.copy(isBold = !it.isBold) }
    }

    fun toggleItalic() {
        updateFormatting { it.copy(isItalic = !it.isItalic) }
    }

    fun toggleUnderline() {
        updateFormatting { it.copy(isUnderline = !it.isUnderline) }
    }

    fun toggleStrikethrough() {
        updateFormatting { it.copy(isStrikethrough = !it.isStrikethrough) }
    }

    fun toggleSuperscript() {
        updateFormatting { it.copy(isSuperscript = !it.isSuperscript, isSubscript = false) }
    }

    fun toggleSubscript() {
        updateFormatting { it.copy(isSubscript = !it.isSubscript, isSuperscript = false) }
    }

    fun setFontSize(sizePt: Float) {
        updateFormatting { it.copy(fontSizePt = sizePt) }
    }

    fun adjustFontSize(deltaPt: Float) {
        val currentSize = _uiState.value.activeTypingStyle.fontSizePt
        val newSize = (currentSize + deltaPt).coerceIn(8f, 96f)
        setFontSize(newSize)
    }

    fun setFontColor(colorHex: String) {
        updateFormatting { it.copy(fontColorHex = colorHex) }
    }

    fun setHighlightColor(highlightHex: String?) {
        updateFormatting { it.copy(highlightHex = highlightHex) }
    }

    fun setFontFamily(fontFamily: String) {
        updateFormatting { it.copy(fontFamily = fontFamily) }
    }

    fun clearFormatting() {
        updateFormatting {
            it.copy(
                isBold = false,
                isItalic = false,
                isUnderline = false,
                isStrikethrough = false,
                isSuperscript = false,
                isSubscript = false,
                fontSizePt = 11f,
                fontColorHex = "#1A1A1A",
                highlightHex = null,
                fontFamily = "Calibri"
            )
        }
    }

    fun setAlignment(alignment: BlockAlignment) {
        val idx = _uiState.value.selectedBlockIndex
        val block = getCurrentParagraph() ?: return
        val blocks = _uiState.value.document.blocks.toMutableList()
        blocks[idx] = block.copy(alignment = alignment)
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
    }

    fun setParagraphStyle(style: ParagraphStyle) {
        val idx = _uiState.value.selectedBlockIndex
        val block = getCurrentParagraph() ?: return
        val updatedRuns = block.runs.map { run ->
            run.copy(
                fontSizePt = style.defaultSizePt,
                isBold = style.isBold,
                fontColorHex = if (style == ParagraphStyle.HEADING_1 || style == ParagraphStyle.TITLE) "#185ABD" else run.fontColorHex
            )
        }
        val blocks = _uiState.value.document.blocks.toMutableList()
        blocks[idx] = block.copy(style = style, runs = updatedRuns)
        _uiState.update {
            it.copy(
                activeTypingStyle = it.activeTypingStyle.copy(
                    fontSizePt = style.defaultSizePt,
                    isBold = style.isBold
                )
            )
        }
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
    }

    fun toggleBulletList() {
        val idx = _uiState.value.selectedBlockIndex
        val block = getCurrentParagraph() ?: return
        val newList = if (block.listType == ListType.BULLET) ListType.NONE else ListType.BULLET
        val blocks = _uiState.value.document.blocks.toMutableList()
        blocks[idx] = block.copy(listType = newList)
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
    }

    fun toggleNumberedList() {
        val idx = _uiState.value.selectedBlockIndex
        val block = getCurrentParagraph() ?: return
        val newList = if (block.listType == ListType.NUMBERED) ListType.NONE else ListType.NUMBERED
        val blocks = _uiState.value.document.blocks.toMutableList()
        blocks[idx] = block.copy(listType = newList)
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
    }

    fun increaseIndent() {
        val idx = _uiState.value.selectedBlockIndex
        val block = getCurrentParagraph() ?: return
        val blocks = _uiState.value.document.blocks.toMutableList()
        blocks[idx] = block.copy(listLevel = (block.listLevel + 1).coerceAtMost(5))
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
    }

    fun decreaseIndent() {
        val idx = _uiState.value.selectedBlockIndex
        val block = getCurrentParagraph() ?: return
        val blocks = _uiState.value.document.blocks.toMutableList()
        blocks[idx] = block.copy(listLevel = (block.listLevel - 1).coerceAtLeast(0))
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
    }

    private fun getCurrentParagraph(): ParagraphBlock? {
        val idx = _uiState.value.selectedBlockIndex
        return _uiState.value.document.blocks.getOrNull(idx) as? ParagraphBlock
    }

    // --- Insert Menu Actions ---
    fun insertTable(rows: Int, cols: Int) {
        val idx = _uiState.value.selectedBlockIndex
        val blocks = _uiState.value.document.blocks.toMutableList()
        val insertPos = (idx + 1).coerceIn(0, blocks.size)
        val table = TableBlock(
            rows = rows.coerceIn(1, 10),
            cols = cols.coerceIn(1, 8),
            cells = TableBlock.createDefaultCells(rows, cols)
        )
        blocks.add(insertPos, table)
        blocks.add(insertPos + 1, ParagraphBlock(runs = listOf(TextRun(""))))
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
        selectBlock(insertPos)
        _uiState.update { it.copy(showInsertTableDialog = false, activeRibbonTab = RibbonTab.TABLE) }
    }

    fun insertImage(uri: String) {
        // FIX (images were URI-only): copy the picked image into app-private
        // storage first, so the document keeps its picture across restarts
        // and DOCX/PDF export can embed it. If the copy fails, fall back to
        // the original URI so the image still shows in the editor.
        viewModelScope.launch {
            val storedPath = withContext(Dispatchers.IO) {
                repository.copyImageToAppStorage(uri)
            } ?: uri
            val idx = _uiState.value.selectedBlockIndex
            val blocks = _uiState.value.document.blocks.toMutableList()
            val insertPos = (idx + 1).coerceIn(0, blocks.size)
            blocks.add(insertPos, ImageBlock(uriOrPath = storedPath, caption = "Image"))
            blocks.add(insertPos + 1, ParagraphBlock(runs = listOf(TextRun(""))))
            mutateDocument(_uiState.value.document.copy(blocks = blocks))
            selectBlock(insertPos + 1)
        }
    }

    fun insertHorizontalRule() {
        val idx = _uiState.value.selectedBlockIndex
        val blocks = _uiState.value.document.blocks.toMutableList()
        val insertPos = (idx + 1).coerceIn(0, blocks.size)
        blocks.add(insertPos, HorizontalRuleBlock())
        blocks.add(insertPos + 1, ParagraphBlock(runs = listOf(TextRun(""))))
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
        selectBlock(insertPos + 1)
    }

    fun insertPageBreak() {
        val idx = _uiState.value.selectedBlockIndex
        val blocks = _uiState.value.document.blocks.toMutableList()
        val insertPos = (idx + 1).coerceIn(0, blocks.size)
        blocks.add(insertPos, PageBreakBlock())
        blocks.add(insertPos + 1, ParagraphBlock(runs = listOf(TextRun(""))))
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
        selectBlock(insertPos + 1)
    }

    fun insertDateTime() {
        val dateStr = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date())
        val block = getCurrentParagraph()
        if (block != null) {
            val currentText = block.plainText
            val newText = if (currentText.isBlank()) dateStr else "$currentText $dateStr"
            updateParagraphText(_uiState.value.selectedBlockIndex, newText)
        }
    }

    // --- Table Cell Manipulations ---
    fun selectTableCell(row: Int, col: Int) {
        _uiState.update { it.copy(selectedTableCell = Pair(row, col), activeRibbonTab = RibbonTab.TABLE) }
    }

    fun updateTableCellText(tableBlockIndex: Int, row: Int, col: Int, newText: String) {
        val blocks = _uiState.value.document.blocks.toMutableList()
        val table = blocks.getOrNull(tableBlockIndex) as? TableBlock ?: return
        val currentCells = table.cells.map { it.toMutableList() }.toMutableList()
        if (row in currentCells.indices && col in currentCells[row].indices) {
            val cell = currentCells[row][col]
            currentCells[row][col] = cell.copy(text = newText)
            blocks[tableBlockIndex] = table.copy(cells = currentCells)
            mutateDocument(_uiState.value.document.copy(blocks = blocks))
        }
    }

    fun addTableRow(tableBlockIndex: Int, below: Boolean = true) {
        val blocks = _uiState.value.document.blocks.toMutableList()
        val table = blocks.getOrNull(tableBlockIndex) as? TableBlock ?: return
        val currentCells = table.cells.map { it.toMutableList() }.toMutableList()
        val cols = table.cols
        val newRow = MutableList(cols) { TableCell(text = "Cell") }
        if (below) {
            currentCells.add(newRow)
        } else {
            currentCells.add(0, newRow)
        }
        blocks[tableBlockIndex] = table.copy(rows = currentCells.size, cells = currentCells)
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
    }

    fun deleteTableRow(tableBlockIndex: Int) {
        val blocks = _uiState.value.document.blocks.toMutableList()
        val table = blocks.getOrNull(tableBlockIndex) as? TableBlock ?: return
        if (table.rows <= 1) {
            // Delete entire table
            deleteBlock(tableBlockIndex)
            return
        }
        val targetRow = _uiState.value.selectedTableCell?.first ?: (table.rows - 1)
        val currentCells = table.cells.toMutableList()
        if (targetRow in currentCells.indices) {
            currentCells.removeAt(targetRow)
            blocks[tableBlockIndex] = table.copy(rows = currentCells.size, cells = currentCells)
            mutateDocument(_uiState.value.document.copy(blocks = blocks))
        }
    }

    fun addTableCol(tableBlockIndex: Int) {
        val blocks = _uiState.value.document.blocks.toMutableList()
        val table = blocks.getOrNull(tableBlockIndex) as? TableBlock ?: return
        val currentCells = table.cells.map { it.toMutableList() }.toMutableList()
        for (r in currentCells.indices) {
            currentCells[r].add(TableCell(text = "Cell", isHeader = (r == 0)))
        }
        val newCols = table.cols + 1
        blocks[tableBlockIndex] = table.copy(cols = newCols, cells = currentCells)
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
    }

    fun deleteTableCol(tableBlockIndex: Int) {
        val blocks = _uiState.value.document.blocks.toMutableList()
        val table = blocks.getOrNull(tableBlockIndex) as? TableBlock ?: return
        if (table.cols <= 1) {
            deleteBlock(tableBlockIndex)
            return
        }
        val targetCol = _uiState.value.selectedTableCell?.second ?: (table.cols - 1)
        val currentCells = table.cells.map { it.toMutableList() }.toMutableList()
        for (r in currentCells.indices) {
            if (targetCol in currentCells[r].indices) {
                currentCells[r].removeAt(targetCol)
            }
        }
        val newCols = table.cols - 1
        blocks[tableBlockIndex] = table.copy(cols = newCols, cells = currentCells)
        mutateDocument(_uiState.value.document.copy(blocks = blocks))
    }

    fun setTableCellBgColor(tableBlockIndex: Int, hexColor: String?) {
        val target = _uiState.value.selectedTableCell ?: return
        val blocks = _uiState.value.document.blocks.toMutableList()
        val table = blocks.getOrNull(tableBlockIndex) as? TableBlock ?: return
        val currentCells = table.cells.map { it.toMutableList() }.toMutableList()
        if (target.first in currentCells.indices && target.second in currentCells[target.first].indices) {
            val cell = currentCells[target.first][target.second]
            currentCells[target.first][target.second] = cell.copy(backgroundColorHex = hexColor)
            blocks[tableBlockIndex] = table.copy(cells = currentCells)
            mutateDocument(_uiState.value.document.copy(blocks = blocks))
        }
    }

    // --- Page Layout Settings ---
    fun setPageSize(size: PageSize) {
        val current = _uiState.value.document.pageSettings
        mutateDocument(_uiState.value.document.copy(pageSettings = current.copy(pageSize = size)))
    }

    fun setOrientation(orientation: PageOrientation) {
        val current = _uiState.value.document.pageSettings
        mutateDocument(_uiState.value.document.copy(pageSettings = current.copy(orientation = orientation)))
    }

    fun setMargins(margins: PageMargins) {
        val current = _uiState.value.document.pageSettings
        mutateDocument(_uiState.value.document.copy(pageSettings = current.copy(margins = margins)))
    }

    fun updateHeaderFooter(header: String, footer: String, showNumbers: Boolean) {
        val current = _uiState.value.document.pageSettings
        mutateDocument(
            _uiState.value.document.copy(
                pageSettings = current.copy(
                    headerText = header,
                    footerText = footer,
                    showPageNumbers = showNumbers
                )
            )
        )
    }

    // --- Find and Replace ---
    fun toggleFindReplace() {
        val isVisible = !_uiState.value.isFindReplaceVisible
        _uiState.update { it.copy(isFindReplaceVisible = isVisible) }
        if (!isVisible) {
            clearFind()
        }
    }

    fun updateFindQuery(query: String) {
        _uiState.update { it.copy(findQuery = query) }
        performFind(query)
    }

    fun updateReplaceQuery(query: String) {
        _uiState.update { it.copy(replaceQuery = query) }
    }

    private fun performFind(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(matchIndices = emptyList(), currentMatchIndex = 0) }
            return
        }
        val matches = mutableListOf<Int>()
        val blocks = _uiState.value.document.blocks
        for (i in blocks.indices) {
            val b = blocks[i]
            if (b is ParagraphBlock && b.plainText.contains(query, ignoreCase = true)) {
                matches.add(i)
            }
        }
        _uiState.update {
            it.copy(
                matchIndices = matches,
                currentMatchIndex = if (matches.isNotEmpty()) 0 else 0
            )
        }
        if (matches.isNotEmpty()) {
            selectBlock(matches[0])
        }
    }

    fun nextMatch(): Int {
        val matches = _uiState.value.matchIndices
        if (matches.isEmpty()) return -1
        val nextIdx = (_uiState.value.currentMatchIndex + 1) % matches.size
        _uiState.update { it.copy(currentMatchIndex = nextIdx) }
        selectBlock(matches[nextIdx])
        return matches[nextIdx]
    }

    fun prevMatch(): Int {
        val matches = _uiState.value.matchIndices
        if (matches.isEmpty()) return -1
        val prevIdx = if (_uiState.value.currentMatchIndex - 1 < 0) matches.size - 1 else _uiState.value.currentMatchIndex - 1
        _uiState.update { it.copy(currentMatchIndex = prevIdx) }
        selectBlock(matches[prevIdx])
        return matches[prevIdx]
    }

    fun replaceCurrent() {
        val query = _uiState.value.findQuery
        val replace = _uiState.value.replaceQuery
        val matches = _uiState.value.matchIndices
        if (query.isBlank() || matches.isEmpty()) return
        val currentBlockIdx = matches[_uiState.value.currentMatchIndex]
        val block = _uiState.value.document.blocks.getOrNull(currentBlockIdx) as? ParagraphBlock ?: return

        val newText = block.plainText.replaceFirst(query, replace, ignoreCase = true)
        updateParagraphText(currentBlockIdx, newText)
        performFind(query)
    }

    fun replaceAll() {
        val query = _uiState.value.findQuery
        val replace = _uiState.value.replaceQuery
        if (query.isBlank()) return
        val blocks = _uiState.value.document.blocks.toMutableList()
        var modified = false
        for (i in blocks.indices) {
            val b = blocks[i]
            if (b is ParagraphBlock && b.plainText.contains(query, ignoreCase = true)) {
                // Replace inside each run so per-run formatting (bold, color, …) survives.
                val newRuns = b.runs.map { run ->
                    if (run.text.contains(query, ignoreCase = true))
                        run.copy(text = run.text.replace(query, replace, ignoreCase = true))
                    else run
                }
                blocks[i] = b.copy(runs = newRuns)
                modified = true
            }
        }
        if (modified) {
            mutateDocument(_uiState.value.document.copy(blocks = blocks))
            performFind(query)
            _uiState.update { it.copy(toastMessage = "All matches replaced") }
        }
    }

    private fun clearFind() {
        _uiState.update {
            it.copy(
                findQuery = "",
                replaceQuery = "",
                matchIndices = emptyList(),
                currentMatchIndex = 0
            )
        }
    }

    // --- Dialog Toggles ---
    fun setShowStatsDialog(show: Boolean) = _uiState.update { it.copy(showStatsDialog = show) }
    fun setShowInsertTableDialog(show: Boolean) = _uiState.update { it.copy(showInsertTableDialog = show) }
    fun setShowLinkDialog(show: Boolean) = _uiState.update { it.copy(showLinkDialog = show) }
    fun setShowExportDialog(show: Boolean) = _uiState.update { it.copy(showExportDialog = show) }
    fun setShowRenameDialog(show: Boolean) = _uiState.update { it.copy(showRenameDialog = show) }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }

    fun showToast(msg: String) = _uiState.update { it.copy(toastMessage = msg) }
}
