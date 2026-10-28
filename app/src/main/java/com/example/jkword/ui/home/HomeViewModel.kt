package com.example.jkword.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jkword.data.local.DocumentEntity
import com.example.jkword.data.local.DocumentRepository
import com.example.jkword.data.local.WordDatabase
import com.example.jkword.model.DocumentTemplates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

enum class HomeTab {
    RECENT,
    STARRED,
    ALL,
    TRASH
}

enum class SortOrder(val label: String) {
    DATE_DESC("Newest modified"),
    DATE_ASC("Oldest modified"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    SIZE_DESC("File size")
}

data class HomeUiState(
    val currentTab: HomeTab = HomeTab.RECENT,
    val searchQuery: String = "",
    val isGridView: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val isTemplatesSheetVisible: Boolean = false,
    val selectedDocForOptions: DocumentEntity? = null,
    val docToRename: DocumentEntity? = null,
    val isCreateLoading: Boolean = false,
    val toastMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(
        context = application.applicationContext,
        dao = WordDatabase.getInstance(application.applicationContext).documentDao()
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        bootstrapInitialDocumentsIfEmpty()
    }

    private fun bootstrapInitialDocumentsIfEmpty() {
        viewModelScope.launch {
            val existing = repository.activeDocuments.first()
            if (existing.isEmpty()) {
                // Seed 2 real editable starter templates so user has immediate rich documents
                val businessTemplate = DocumentTemplates.ALL_TEMPLATES.first { it.id == "business_report" }
                val resumeTemplate = DocumentTemplates.ALL_TEMPLATES.first { it.id == "resume" }
                repository.createNewDocument(businessTemplate)
                repository.createNewDocument(resumeTemplate)
            }
        }
    }

    // Observe documents based on active tab and search query
    val documents: StateFlow<List<DocumentEntity>> = combine(
        _uiState.map { it.currentTab }.distinctUntilChanged(),
        _uiState.map { it.searchQuery }.distinctUntilChanged(),
        _uiState.map { it.sortOrder }.distinctUntilChanged()
    ) { tab, query, sort ->
        Triple(tab, query, sort)
    }.flatMapLatest { (tab, query, sort) ->
        val baseFlow = if (query.isNotBlank()) {
            repository.searchDocuments(query.trim())
        } else {
            when (tab) {
                HomeTab.RECENT, HomeTab.ALL -> repository.activeDocuments
                HomeTab.STARRED -> repository.starredDocuments
                HomeTab.TRASH -> repository.trashDocuments
            }
        }

        baseFlow.map { list ->
            when (sort) {
                SortOrder.DATE_DESC -> list.sortedByDescending { it.lastModified }
                SortOrder.DATE_ASC -> list.sortedBy { it.lastModified }
                SortOrder.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
                SortOrder.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
                SortOrder.SIZE_DESC -> list.sortedByDescending { it.fileSize }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTab(tab: HomeTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleLayoutView() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun setTemplatesSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isTemplatesSheetVisible = visible) }
    }

    fun createDocumentFromTemplate(template: DocumentTemplates.TemplateInfo, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreateLoading = true, isTemplatesSheetVisible = false) }
            val id = repository.createNewDocument(template)
            _uiState.update { it.copy(isCreateLoading = false) }
            onCreated(id)
        }
    }

    fun createBlankDocument(onCreated: (Long) -> Unit) {
        val blankTemplate = DocumentTemplates.ALL_TEMPLATES.first()
        createDocumentFromTemplate(blankTemplate, onCreated)
    }

    fun importDocument(data: ByteArray, filename: String, onImported: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreateLoading = true) }
            try {
                val id = repository.importExternalDocument(ByteArrayInputStream(data), filename)
                _uiState.update { it.copy(isCreateLoading = false, toastMessage = "Document imported successfully") }
                onImported(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(isCreateLoading = false, toastMessage = "Import failed: ${e.message}") }
            }
        }
    }

    fun toggleStarred(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleStarred(doc.id, !doc.isStarred)
        }
    }

    fun renameDocument(docId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameDocument(docId, newTitle)
            _uiState.update { it.copy(docToRename = null) }
        }
    }

    fun duplicateDocument(docId: Long) {
        viewModelScope.launch {
            repository.duplicateDocument(docId)
            _uiState.update { it.copy(selectedDocForOptions = null, toastMessage = "Document duplicated") }
        }
    }

    fun moveToTrash(docId: Long) {
        viewModelScope.launch {
            repository.moveToTrash(docId)
            _uiState.update { it.copy(selectedDocForOptions = null, toastMessage = "Moved to Trash") }
        }
    }

    fun restoreFromTrash(docId: Long) {
        viewModelScope.launch {
            repository.restoreFromTrash(docId)
            _uiState.update { it.copy(selectedDocForOptions = null, toastMessage = "Document restored") }
        }
    }

    fun deletePermanently(docId: Long) {
        viewModelScope.launch {
            repository.deletePermanently(docId)
            _uiState.update { it.copy(selectedDocForOptions = null, toastMessage = "Deleted permanently") }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            _uiState.update { it.copy(toastMessage = "Trash emptied") }
        }
    }

    fun selectDocForOptions(doc: DocumentEntity?) {
        _uiState.update { it.copy(selectedDocForOptions = doc) }
    }

    fun setDocToRename(doc: DocumentEntity?) {
        _uiState.update { it.copy(docToRename = doc) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
