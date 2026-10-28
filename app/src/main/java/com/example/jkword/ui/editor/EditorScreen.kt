package com.example.jkword.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jkword.data.local.AppThemeMode
import com.example.jkword.data.local.DocumentRepository
import com.example.jkword.data.local.WordDatabase
import com.example.jkword.editor.*
import com.example.jkword.engine.*
import com.example.jkword.model.*
import com.example.jkword.ui.components.*
import com.example.ui.theme.LocalThemeMode
import com.example.ui.theme.LocalToggleTheme
import com.example.ui.theme.OfficeBluePrimary
import com.example.ui.theme.OfficeNavy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    documentId: Long,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showHeaderFooterDialog by remember { mutableStateOf(false) }
    // Shared scroll helper: assigned once pages are computed below; maps a
    // document block index to the correct LazyColumn item in both view modes.
    var scrollToBlockIndex: (Int) -> Unit by remember { mutableStateOf({ _: Int -> }) }

    val repository = remember {
        DocumentRepository(context, WordDatabase.getInstance(context).documentDao())
    }

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }



    BackHandler {
        // Wait for the save to finish before leaving, so no keystroke is lost.
        coroutineScope.launch {
            viewModel.manualSaveAndWait()
            onNavigateBack()
        }
    }

    // Photo Picker for inserting images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.insertImage(uri.toString())
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // FIX (invisible toasts): ViewModel toast messages (save, export, trash,
    // import, ...) had no SnackbarHost anywhere, so they never reached the
    // user. Show each message once, then clear it.
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            // FIX (top-bar back data loss): wait for the save like
                            // the system Back button already does. The old code
                            // fired manualSave() and navigated immediately, so the
                            // last keystrokes could be lost.
                            coroutineScope.launch {
                                viewModel.manualSaveAndWait()
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    // Document Title & Stable Non-Jumping Status Badge
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setShowRenameDialog(true) }
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = uiState.document.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (uiState.saveStatus) {
                                            // FIX (fake "Saved" badge): DIRTY must never
                                            // look saved — unsaved changes get a yellow dot.
                                            SaveStatus.SAVED -> Color(0xFF10B981)
                                            SaveStatus.DIRTY -> Color(0xFFF59E0B)
                                            SaveStatus.SAVING -> Color(0xFF3B82F6)
                                            SaveStatus.ERROR -> Color(0xFFEF4444)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (uiState.saveStatus) {
                                    SaveStatus.SAVED -> "Saved"
                                    SaveStatus.DIRTY -> "Unsaved changes"
                                    SaveStatus.SAVING -> "Saving..."
                                    SaveStatus.ERROR -> "Offline"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Undo Button
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (uiState.canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (uiState.canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    // Search / Find & Replace toggle
                    IconButton(
                        onClick = { viewModel.toggleFindReplace() },
                        modifier = Modifier.testTag("search_toggle_button")
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Find & Replace",
                            tint = if (uiState.isFindReplaceVisible) OfficeBluePrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // View Mode toggle (Page Layout vs Mobile Reflow)
                    IconButton(
                        onClick = { viewModel.toggleViewMode() },
                        modifier = Modifier.testTag("view_mode_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.viewMode == ViewMode.PAGE_LAYOUT) Icons.Default.MenuBook else Icons.Default.ViewAgenda,
                            contentDescription = "View Mode",
                            tint = OfficeBluePrimary
                        )
                    }

                    // Theme Toggle (System / Light / Dark)
                    val themeMode = LocalThemeMode.current
                    val toggleTheme = LocalToggleTheme.current
                    IconButton(
                        onClick = toggleTheme,
                        modifier = Modifier.testTag("editor_theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = when (themeMode) {
                                AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                AppThemeMode.LIGHT -> Icons.Default.LightMode
                                AppThemeMode.DARK -> Icons.Default.DarkMode
                            },
                            contentDescription = "Theme: ${themeMode.displayName}",
                            tint = OfficeBluePrimary
                        )
                    }

                    // Export / Share
                    IconButton(
                        onClick = { viewModel.setShowExportDialog(true) },
                        modifier = Modifier.testTag("export_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export & Share")
                    }

                    // More Menu
                    var showMoreMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Theme: ${themeMode.displayName}") },
                                leadingIcon = {
                                    Icon(
                                        when (themeMode) {
                                            AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                            AppThemeMode.LIGHT -> Icons.Default.LightMode
                                            AppThemeMode.DARK -> Icons.Default.DarkMode
                                        },
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    toggleTheme()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Word Count & Statistics") },
                                leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.setShowStatsDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Document History") },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.setShowHistoryDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename Document") },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.setShowRenameDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Document Now") },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.manualSave()
                                }
                            )
                        }
                    }
                }

                // Expandable Find & Replace Bar
                AnimatedVisibility(visible = uiState.isFindReplaceVisible) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.findQuery,
                                onValueChange = { viewModel.updateFindQuery(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("find_query_input"),
                                placeholder = { Text("Find in document", fontSize = 12.sp) },
                                singleLine = true
                            )

                            Text(
                                text = if (uiState.matchIndices.isNotEmpty()) {
                                    "${uiState.currentMatchIndex + 1}/${uiState.matchIndices.size}"
                                } else {
                                    "0"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = {
                                    val blockIdx = viewModel.prevMatch()
                                    if (blockIdx >= 0) scrollToBlockIndex(blockIdx)
                                },
                                enabled = uiState.matchIndices.isNotEmpty()
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev Match")
                            }

                            IconButton(
                                onClick = {
                                    val blockIdx = viewModel.nextMatch()
                                    if (blockIdx >= 0) scrollToBlockIndex(blockIdx)
                                },
                                enabled = uiState.matchIndices.isNotEmpty()
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                            }

                            IconButton(onClick = { viewModel.toggleFindReplace() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search")
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.replaceQuery,
                                onValueChange = { viewModel.updateReplaceQuery(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("replace_query_input"),
                                placeholder = { Text("Replace with", fontSize = 12.sp) },
                                singleLine = true
                            )

                            Button(
                                onClick = { viewModel.replaceCurrent() },
                                enabled = uiState.matchIndices.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Replace", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.replaceAll() },
                                enabled = uiState.matchIndices.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("All", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Word Mobile Ribbon Toolbar
            WordRibbonToolbar(
                viewModel = viewModel,
                uiState = uiState,
                onPickImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenHeaderFooter = {
                    showHeaderFooterDialog = true
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    if (uiState.viewMode == ViewMode.PAGE_LAYOUT) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.background
                    }
                )
        ) {
            // Document Canvas
            val isPageMode = uiState.viewMode == ViewMode.PAGE_LAYOUT
            val pageSettings = uiState.document.pageSettings

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isPageMode) 8.dp else 0.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val availableWidth = maxWidth
                val isPortrait = pageSettings.orientation == PageOrientation.PORTRAIT

                // Dynamically calculate realistic sheet dimensions based on chosen PageSize!
                val targetSheetWidth = if (isPageMode) {
                    if (isPortrait) {
                        when (pageSettings.pageSize) {
                            PageSize.A5 -> (availableWidth * 0.86f).coerceAtMost(460.dp)
                            PageSize.STATEMENT -> (availableWidth * 0.84f).coerceAtMost(440.dp)
                            PageSize.EIGHT_BY_THIRTEEN -> (availableWidth * 0.94f).coerceAtMost(520.dp)
                            PageSize.LEGAL -> (availableWidth * 0.94f).coerceAtMost(520.dp)
                            else -> (availableWidth * 0.96f).coerceAtMost(560.dp)
                        }
                    } else {
                        // Landscape orientation: wide sheet
                        (availableWidth * 0.98f).coerceAtMost(760.dp)
                    }
                } else {
                    availableWidth
                }

                val ratio = pageSettings.pageSize.getAspectRatio(pageSettings.orientation)
                val targetSheetHeight = if (isPageMode) targetSheetWidth / ratio else 0.dp

                // Smoothly animated transitions when user changes page size or orientation
                val animatedSheetWidth by animateDpAsState(
                    targetValue = targetSheetWidth,
                    label = "sheet_width"
                )
                val animatedSheetHeight by animateDpAsState(
                    targetValue = targetSheetHeight,
                    label = "sheet_height"
                )

                val horizontalMargin = if (isPageMode) {
                    ((pageSettings.margins.leftPt * 0.22f) + 8f).dp
                } else {
                    12.dp
                }
                val verticalMargin = if (isPageMode) {
                    ((pageSettings.margins.topPt * 0.18f) + 6f).dp
                } else {
                    8.dp
                }

                // Realistic Paper Sheet in Page Mode
                val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val paperBg = if (isDarkTheme) {
                    if (isPageMode) Color(0xFF22252B) else MaterialTheme.colorScheme.background
                } else {
                    Color.White
                }

                val usableContentHeightDp = (animatedSheetHeight.value - 90f).coerceAtLeast(200f)
                val pages = remember(uiState.document.blocks, usableContentHeightDp) {
                    PaginationEngine.paginate(
                        blocks = uiState.document.blocks,
                        usableContentHeightDp = usableContentHeightDp
                    )
                }

                // FIX (numbered lists "1. 1. 1."): compute each numbered item's
                // real sequence number once over the whole document (continuous
                // across pages), keyed by block id. Shared with DOCX/PDF export.
                val listPrefixes = remember(uiState.document.blocks) {
                    computeNumberedPrefixes(uiState.document.blocks)
                }

                // Map a document block index to the LazyColumn item index for the
                // current view mode (blocks in continuous mode, pages in page mode).
                scrollToBlockIndex = { blockIdx ->
                    val target = if (isPageMode) {
                        pages.indexOfFirst { page ->
                            page.blocks.any { it.globalIndex == blockIdx }
                        }.coerceAtLeast(0)
                    } else {
                        blockIdx
                    }
                    coroutineScope.launch { listState.animateScrollToItem(target) }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 12.dp, bottom = 360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isPageMode) {
                        itemsIndexed(
                            items = pages,
                            // Stable key: first block's id survives repagination far
                            // better than pageNumber, which shifts on every keystroke.
                            key = { _, page -> page.blocks.firstOrNull()?.block?.id ?: "page_${page.pageNumber}" }
                        ) { _, page ->
                            DocumentPageSheet(
                                page = page,
                                pageSettings = pageSettings,
                                sheetWidth = animatedSheetWidth,
                                sheetHeight = animatedSheetHeight,
                                paperBg = paperBg,
                                horizontalMargin = horizontalMargin,
                                verticalMargin = verticalMargin,
                                selectedBlockIndex = uiState.selectedBlockIndex,
                                selectedTableCell = uiState.selectedTableCell,
                                viewModel = viewModel,
                                numberPrefixes = listPrefixes,
                                onOpenHeaderFooter = { showHeaderFooterDialog = true }
                            )
                        }

                        item {
                            OutlinedButton(
                                onClick = { viewModel.addNewPage() },
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .testTag("btn_add_page")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Next Page (Page ${pages.size + 1})")
                            }
                        }
                    } else {
                        // High-performance lazy continuous view for long documents
                        itemsIndexed(
                            items = uiState.document.blocks,
                            key = { _, block -> block.id }
                        ) { index, block ->
                            val isSelected = (index == uiState.selectedBlockIndex)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(containerColor = paperBg)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = horizontalMargin, vertical = 4.dp)
                                ) {
                                    RenderDocumentBlock(
                                        globalIndex = index,
                                        block = block,
                                        isSelected = isSelected,
                                        selectedTableCell = if (isSelected) uiState.selectedTableCell else null,
                                        viewModel = viewModel,
                                        numberPrefix = listPrefixes[block.id] ?: ""
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (uiState.showStatsDialog) {
        val stats = remember(uiState.document) { repository.calculateStats(uiState.document) }
        DocumentStatsDialog(
            stats = stats,
            onDismiss = { viewModel.setShowStatsDialog(false) }
        )
    }

    if (uiState.showInsertTableDialog) {
        InsertTableDialog(
            onDismiss = { viewModel.setShowInsertTableDialog(false) },
            onConfirm = { r, c -> viewModel.insertTable(r, c) }
        )
    }

    if (uiState.showRenameDialog) {
        RenameDialog(
            initialTitle = uiState.document.title,
            onDismiss = { viewModel.setShowRenameDialog(false) },
            onConfirm = { newTitle ->
                viewModel.updateTitle(newTitle)
                viewModel.setShowRenameDialog(false)
            }
        )
    }

    if (uiState.showExportDialog) {
        ExportDialog(
            document = uiState.document,
            repository = repository,
            context = context,
            scope = coroutineScope,
            onDismiss = { viewModel.setShowExportDialog(false) },
            onSuccess = { msg ->
                // Surface the export confirmation as a toast.
                viewModel.showToast(msg)
            }
        )
    }

    if (uiState.showHistoryDialog) {
        VersionHistoryDialog(
            versions = uiState.versionHistory,
            onDismiss = { viewModel.setShowHistoryDialog(false) },
            onRevert = { version ->
                viewModel.revertToVersion(version)
            }
        )
    }

    if (showHeaderFooterDialog) {
        HeaderFooterDialog(
            initialHeader = uiState.document.pageSettings.headerText,
            initialFooter = uiState.document.pageSettings.footerText,
            initialShowPageNumbers = uiState.document.pageSettings.showPageNumbers,
            onDismiss = { showHeaderFooterDialog = false },
            onConfirm = { header, footer, showNumbers ->
                viewModel.updateHeaderFooter(header, footer, showNumbers)
                showHeaderFooterDialog = false
            }
        )
    }
}

@Composable
private fun DocumentPageSheet(
    page: DocumentPage,
    pageSettings: PageLayoutSettings,
    sheetWidth: Dp,
    sheetHeight: Dp,
    paperBg: Color,
    horizontalMargin: Dp,
    verticalMargin: Dp,
    selectedBlockIndex: Int,
    selectedTableCell: Pair<Int, Int>?,
    viewModel: EditorViewModel,
    numberPrefixes: Map<String, String> = emptyMap(),
    onOpenHeaderFooter: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Page Header spec pill
        Row(
            modifier = Modifier
                .width(sheetWidth)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = OfficeBluePrimary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "Page ${page.pageNumber} of ${page.totalPages} • ${pageSettings.pageSize.shortLabel}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OfficeBluePrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                text = pageSettings.margins.displayName,
                fontSize = 9.sp,
                color = Color.Gray
            )
        }

        // Fixed-size physical Paper Sheet (exact boundaries, never expands vertically)
        Card(
            modifier = Modifier
                .width(sheetWidth)
                .height(sheetHeight),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = paperBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalMargin, vertical = verticalMargin)
            ) {
                // Header Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onOpenHeaderFooter() }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pageSettings.headerText.isNotEmpty()) {
                        Text(
                            text = pageSettings.headerText,
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "+ Header (tap to edit)",
                            fontSize = 10.sp,
                            color = Color(0xFF9CA3AF),
                            fontStyle = FontStyle.Italic
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Header",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(13.dp)
                    )
                }
                HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(4.dp))

                // Content Area for this page's blocks
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    page.blocks.forEach { indexedBlock ->
                        val globalIndex = indexedBlock.globalIndex
                        val block = indexedBlock.block
                        val isSelected = (globalIndex == selectedBlockIndex)
                        RenderDocumentBlock(
                            globalIndex = globalIndex,
                            block = block,
                            isSelected = isSelected,
                            selectedTableCell = if (isSelected) selectedTableCell else null,
                            viewModel = viewModel,
                            numberPrefix = numberPrefixes[block.id] ?: ""
                        )
                    }
                }

                // Footer Area
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pageSettings.footerText,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    if (pageSettings.showPageNumbers) {
                        Text(
                            text = "Page ${page.pageNumber} of ${page.totalPages}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDocumentBlock(
    globalIndex: Int,
    block: DocumentBlock,
    isSelected: Boolean,
    selectedTableCell: Pair<Int, Int>?,
    viewModel: EditorViewModel,
    numberPrefix: String = ""
) {
    when (block) {
        is ParagraphBlock -> {
            ParagraphBlockItem(
                block = block,
                isSelected = isSelected,
                numberPrefix = numberPrefix,
                onFocus = { viewModel.selectBlock(globalIndex) },
                onSelectionChange = { sel -> viewModel.onSelectionChange(globalIndex, sel) },
                onTextEdited = { oldText, newText, newSel ->
                    viewModel.onParagraphEdited(globalIndex, oldText, newText, newSel)
                }
            )
        }
        is TableBlock -> {
            TableBlockItem(
                table = block,
                isSelected = isSelected,
                selectedCell = selectedTableCell,
                onSelectCell = { r, c ->
                    viewModel.selectBlock(globalIndex)
                    viewModel.selectTableCell(r, c)
                },
                onCellTextChange = { r, c, txt ->
                    viewModel.updateTableCellText(globalIndex, r, c, txt)
                },
                onDeleteTable = {
                    viewModel.deleteBlock(globalIndex)
                }
            )
        }
        is ImageBlock -> {
            ImageBlockItem(
                image = block,
                isSelected = isSelected,
                onSelect = { viewModel.selectBlock(globalIndex) },
                onDelete = { viewModel.deleteBlock(globalIndex) }
            )
        }
        is HorizontalRuleBlock -> {
            HorizontalRuleItem(
                isSelected = isSelected,
                onSelect = { viewModel.selectBlock(globalIndex) },
                onDelete = { viewModel.deleteBlock(globalIndex) }
            )
        }
        is PageBreakBlock -> {
            PageBreakItem(
                isSelected = isSelected,
                onSelect = { viewModel.selectBlock(globalIndex) },
                onDelete = { viewModel.deleteBlock(globalIndex) }
            )
        }
    }
}

fun runsToAnnotatedString(runs: List<TextRun>, defaultTextColor: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    for (run in runs) {
        val start = builder.length
        builder.append(run.text)
        val end = builder.length
        if (start < end) {
            val fontFamily = when (run.fontFamily.lowercase()) {
                "serif", "times new roman", "georgia", "garamond" -> FontFamily.Serif
                "monospace", "courier", "courier new", "consolas" -> FontFamily.Monospace
                "cursive", "comic sans" -> FontFamily.Cursive
                else -> FontFamily.SansSerif
            }
            val color = if (run.fontColorHex.isBlank() || run.fontColorHex.equals("#000000", ignoreCase = true) || run.fontColorHex.equals("#1A1A1A", ignoreCase = true)) {
                defaultTextColor
            } else {
                runCatching { Color(android.graphics.Color.parseColor(run.fontColorHex)) }.getOrDefault(defaultTextColor)
            }
            val bgColor = if (run.highlightHex != null) {
                runCatching { Color(android.graphics.Color.parseColor(run.highlightHex)) }.getOrNull()
            } else null

            builder.addStyle(
                SpanStyle(
                    color = color,
                    fontSize = run.fontSizePt.sp,
                    fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = fontFamily,
                    textDecoration = when {
                        run.isUnderline && run.isStrikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                        run.isUnderline -> TextDecoration.Underline
                        run.isStrikethrough -> TextDecoration.LineThrough
                        else -> TextDecoration.None
                    },
                    background = bgColor ?: Color.Transparent,
                    baselineShift = when {
                        run.isSuperscript -> BaselineShift.Superscript
                        run.isSubscript -> BaselineShift.Subscript
                        else -> BaselineShift.None
                    }
                ),
                start,
                end
            )
        }
    }
    return builder.toAnnotatedString()
}

@Composable
private fun ParagraphBlockItem(
    block: ParagraphBlock,
    isSelected: Boolean,
    numberPrefix: String = "",
    onFocus: () -> Unit,
    onSelectionChange: (TextRange) -> Unit,
    onTextEdited: (String, String, TextRange) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val defaultTextColor = if (isDarkTheme) Color(0xFFE2E2E6) else Color(0xFF1A1A1A)

    val currentAnnotatedString = remember(block.runs, defaultTextColor) {
        runsToAnnotatedString(block.runs, defaultTextColor)
    }

    var textFieldValue by remember(block.id) {
        mutableStateOf(
            TextFieldValue(
                annotatedString = currentAnnotatedString,
                selection = TextRange(currentAnnotatedString.length)
            )
        )
    }

    var lastRenderedRuns by remember(block.id) {
        mutableStateOf(block.runs)
    }

    // Synchronize when external changes occur (toolbar formatting, undo/redo, file load).
    // Skip the resync when the change originated from local typing: the local
    // TextFieldValue was already updated in onValueChange, so replacing it again
    // would disrupt the IME/composition and cause typing jank.
    if (block.runs !== lastRenderedRuns && block.runs != lastRenderedRuns) {
        lastRenderedRuns = block.runs
        val blockPlainText = block.runs.joinToString("") { it.text }
        if (blockPlainText != textFieldValue.text) {
            val safeStart = textFieldValue.selection.start.coerceIn(0, currentAnnotatedString.length)
            val safeEnd = textFieldValue.selection.end.coerceIn(0, currentAnnotatedString.length)
            textFieldValue = textFieldValue.copy(
                annotatedString = currentAnnotatedString,
                selection = TextRange(safeStart, safeEnd)
            )
        } else if (currentAnnotatedString != textFieldValue.annotatedString &&
            textFieldValue.composition == null
        ) {
            // FIX (toolbar formatting invisible): spans-only change — e.g. Bold
            // pressed, or undo/redo of formatting — where the plain text is
            // unchanged. The old code skipped this case entirely, so formatting
            // never appeared until the user typed or deleted. Refresh the
            // styling but keep the cursor/selection exactly where it is.
            // A local keystroke echo has identical text AND spans, so it still
            // takes the skip path below; active IME composition is never touched.
            val safeStart = textFieldValue.selection.start.coerceIn(0, currentAnnotatedString.length)
            val safeEnd = textFieldValue.selection.end.coerceIn(0, currentAnnotatedString.length)
            textFieldValue = textFieldValue.copy(
                annotatedString = currentAnnotatedString,
                selection = TextRange(safeStart, safeEnd)
            )
        }
        // else: local keystroke echoed back — keep local TextFieldValue as-is.
    }

    val textAlign = remember(block.alignment) {
        when (block.alignment) {
            BlockAlignment.CENTER -> TextAlign.Center
            BlockAlignment.RIGHT -> TextAlign.Right
            BlockAlignment.JUSTIFY -> TextAlign.Justify
            BlockAlignment.LEFT -> TextAlign.Left
        }
    }

    val prefix = remember(block.listType, numberPrefix) {
        when (block.listType) {
            ListType.BULLET -> "• "
            // FIX (numbered lists "1. 1. 1."): the old code hardcoded "1. ".
            // The real sequence number is computed over the whole document
            // (see computeNumberedPrefixes) and passed in.
            ListType.NUMBERED -> numberPrefix.ifBlank { "1. " }
            ListType.NONE -> ""
        }
    }

    val indentPadding = remember(block.listLevel) { (block.listLevel * 16).dp }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .padding(start = indentPadding),
        verticalAlignment = Alignment.Top
    ) {
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                fontWeight = FontWeight.Bold,
                color = defaultTextColor,
                fontSize = (block.runs.firstOrNull()?.fontSizePt ?: 11f).sp,
                modifier = Modifier.padding(end = 4.dp, top = 2.dp)
            )
        }

        BasicTextField(
            value = textFieldValue,
            onValueChange = { newTfv ->
                val oldTfv = textFieldValue
                val oldText = oldTfv.text
                val newText = newTfv.text

                // Immediately update local state on the current frame so typing has 0ms delay
                textFieldValue = newTfv

                if (newText != oldText) {
                    onTextEdited(oldText, newText, newTfv.selection)
                } else if (newTfv.selection != oldTfv.selection) {
                    onSelectionChange(newTfv.selection)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 24.dp)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocus()
                        onSelectionChange(textFieldValue.selection)
                    }
                }
                .testTag("paragraph_input_${block.id.take(4)}"),
            textStyle = TextStyle(
                color = defaultTextColor,
                fontSize = (block.runs.firstOrNull()?.fontSizePt ?: 11f).sp,
                textAlign = textAlign,
                lineHeight = 22.sp
            ),
            cursorBrush = SolidColor(OfficeBluePrimary),
            decorationBox = { innerTextField ->
                innerTextField()
            }
        )
    }
}

@Composable
private fun TableBlockItem(
    table: TableBlock,
    isSelected: Boolean,
    selectedCell: Pair<Int, Int>?,
    onSelectCell: (Int, Int) -> Unit,
    onCellTextChange: (Int, Int, String) -> Unit,
    onDeleteTable: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) OfficeBluePrimary else Color(0xFFD1D5DB),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(4.dp)
    ) {
        if (isSelected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Table (${table.rows} × ${table.cols})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OfficeBluePrimary
                )
                IconButton(
                    onClick = onDeleteTable,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Table", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Table Grid (Scrollable if wide)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            table.cells.forEachIndexed { r, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { c, cell ->
                        val isCellFocused = (selectedCell?.first == r && selectedCell?.second == c)
                        // Theme-aware cell background: explicit hex wins; otherwise use
                        // surface colors so text stays readable in dark theme.
                        val cellBg = if (cell.backgroundColorHex != null) {
                            runCatching { Color(android.graphics.Color.parseColor(cell.backgroundColorHex)) }
                                .getOrDefault(MaterialTheme.colorScheme.surface)
                        } else if (cell.isHeader) {
                            if (isDarkTheme) Color(0xFF2A3A4D) else Color(0xFFE8F0FE)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }

                        Box(
                            modifier = Modifier
                                .widthIn(min = 90.dp)
                                .heightIn(min = 36.dp)
                                .background(cellBg)
                                .border(
                                    width = if (isCellFocused) 1.5.dp else 0.5.dp,
                                    color = if (isCellFocused) OfficeBluePrimary else Color(0xFFD1D5DB)
                                )
                                .clickable { onSelectCell(r, c) }
                                .padding(6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = cell.text,
                                onValueChange = { onCellTextChange(r, c, it) },
                                textStyle = TextStyle(
                                    // Theme-aware: hardcoded Black/White made text
                                    // invisible in dark theme.
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = if (cell.isHeader || cell.isBold) FontWeight.Bold else FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(OfficeBluePrimary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBlockItem(
    image: ImageBlock,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) OfficeBluePrimary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // FIX (image size ignored + silent broken images): honor the
            // block's widthDp/heightDp instead of always going full-width,
            // and show an error placeholder instead of blank space when the
            // picture cannot load.
            var imageLoadFailed by remember(image.uriOrPath) { mutableStateOf(false) }
            if (imageLoadFailed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Image unavailable — please re-insert it",
                        fontSize = 12.sp,
                        color = Color(0xFFB91C1C)
                    )
                }
            } else {
                // Actual image rendering via Coil (was a placeholder icon only).
                coil.compose.AsyncImage(
                    model = image.uriOrPath,
                    contentDescription = image.caption ?: "Inserted image",
                    modifier = Modifier
                        .width(image.widthDp.dp.coerceIn(80.dp, 600.dp))
                        .height(image.heightDp.dp.coerceIn(60.dp, 480.dp))
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                    onError = { imageLoadFailed = true }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = image.caption ?: "Inserted Image",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSelected) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove Image", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalRuleItem(
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = if (isSelected) OfficeBluePrimary else Color(0xFF185ABD),
            thickness = if (isSelected) 3.dp else 1.5.dp
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp).testTag("delete_horizontal_rule")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Line", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PageBreakItem(
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = if (isSelected) OfficeBluePrimary else Color.Gray, thickness = 1.dp)
        Text(
            text = " PAGE BREAK ",
            fontSize = 9.sp,
            color = if (isSelected) OfficeBluePrimary else Color.Gray,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = if (isSelected) OfficeBluePrimary else Color.Gray, thickness = 1.dp)
        if (isSelected) {
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp).testTag("delete_page_break")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Page Break", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun WordRibbonToolbar(
    viewModel: EditorViewModel,
    uiState: EditorUiState,
    onPickImage: () -> Unit,
    onOpenHeaderFooter: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Ribbon Tabs Bar (Home, Insert, Layout, Review, Table)
            ScrollableTabRow(
                selectedTabIndex = uiState.activeRibbonTab.ordinal,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = OfficeBluePrimary,
                divider = { HorizontalDivider(thickness = 0.5.dp) }
            ) {
                RibbonTab.values().forEach { tab ->
                    Tab(
                        selected = uiState.activeRibbonTab == tab,
                        onClick = { viewModel.selectRibbonTab(tab) },
                        text = {
                            Text(
                                text = when (tab) {
                                    RibbonTab.HOME -> "Home"
                                    RibbonTab.INSERT -> "Insert"
                                    RibbonTab.LAYOUT -> "Layout"
                                    RibbonTab.REVIEW -> "Review"
                                    RibbonTab.TABLE -> "Table"
                                },
                                fontWeight = if (uiState.activeRibbonTab == tab) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.testTag("ribbon_tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Ribbon Content Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                when (uiState.activeRibbonTab) {
                    RibbonTab.HOME -> HomeRibbonPanel(viewModel = viewModel, uiState = uiState)
                    RibbonTab.INSERT -> InsertRibbonPanel(viewModel = viewModel, onPickImage = onPickImage, onOpenHeader = onOpenHeaderFooter)
                    RibbonTab.LAYOUT -> LayoutRibbonPanel(viewModel = viewModel, uiState = uiState, onOpenHeader = onOpenHeaderFooter)
                    RibbonTab.REVIEW -> ReviewRibbonPanel(viewModel = viewModel, uiState = uiState)
                    RibbonTab.TABLE -> TableRibbonPanel(viewModel = viewModel, uiState = uiState)
                }
            }
        }
    }
}

@Composable
private fun HomeRibbonPanel(
    viewModel: EditorViewModel,
    uiState: EditorUiState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick Character Formatting Row (Font Family, B, I, U, Strike, Colors)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Font Family Dropdown
            var showFontMenu by remember { mutableStateOf(false) }
            val currentFont = uiState.activeTypingStyle.fontFamily.ifBlank { "Calibri" }

            Box {
                OutlinedButton(
                    onClick = { showFontMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("font_family_dropdown")
                ) {
                    Icon(
                        Icons.Default.FontDownload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = OfficeBluePrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Select Font Family",
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showFontMenu,
                    onDismissRequest = { showFontMenu = false }
                ) {
                    listOf(
                        "Calibri" to FontFamily.SansSerif,
                        "Arial" to FontFamily.SansSerif,
                        "Times New Roman" to FontFamily.Serif,
                        "Serif" to FontFamily.Serif,
                        "Georgia" to FontFamily.Serif,
                        "Monospace" to FontFamily.Monospace,
                        "Courier New" to FontFamily.Monospace,
                        "Cursive" to FontFamily.Cursive,
                        "Sans-Serif" to FontFamily.SansSerif
                    ).forEach { (fontName, composeFamily) ->
                        val isSelected = currentFont.equals(fontName, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = fontName,
                                        fontFamily = composeFamily,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) OfficeBluePrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = OfficeBluePrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.setFontFamily(fontName)
                                showFontMenu = false
                            }
                        )
                    }
                }
            }

            VerticalDivider(modifier = Modifier.height(24.dp))

            val isBoldActive = uiState.activeTypingStyle.isBold
            val isItalicActive = uiState.activeTypingStyle.isItalic
            val isUnderlineActive = uiState.activeTypingStyle.isUnderline
            val isStrikeActive = uiState.activeTypingStyle.isStrikethrough

            IconButton(
                onClick = { viewModel.toggleBold() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isBoldActive) OfficeBluePrimary.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (isBoldActive) OfficeBluePrimary else MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("btn_bold")
            ) {
                Icon(Icons.Default.FormatBold, contentDescription = "Bold")
            }
            IconButton(
                onClick = { viewModel.toggleItalic() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isItalicActive) OfficeBluePrimary.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (isItalicActive) OfficeBluePrimary else MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("btn_italic")
            ) {
                Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
            }
            IconButton(
                onClick = { viewModel.toggleUnderline() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isUnderlineActive) OfficeBluePrimary.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (isUnderlineActive) OfficeBluePrimary else MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("btn_underline")
            ) {
                Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
            }
            IconButton(
                onClick = { viewModel.toggleStrikethrough() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isStrikeActive) OfficeBluePrimary.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (isStrikeActive) OfficeBluePrimary else MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("btn_strikethrough")
            ) {
                Icon(Icons.Default.FormatStrikethrough, contentDescription = "Strikethrough")
            }

            VerticalDivider(modifier = Modifier.height(24.dp))

            // Font Color Palette (Quick selection)
            var showColorMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showColorMenu = true }) {
                    Icon(Icons.Default.FormatColorText, contentDescription = "Text Color", tint = OfficeBluePrimary)
                }
                DropdownMenu(expanded = showColorMenu, onDismissRequest = { showColorMenu = false }) {
                    listOf(
                        "Black" to "#000000",
                        "Executive Blue" to "#185ABD",
                        "Dark Blue" to "#0F4C81",
                        "Crimson Red" to "#D32F2F",
                        "Emerald Green" to "#2E7D32",
                        "Dark Gray" to "#4A5568"
                    ).forEach { (name, hex) ->
                        DropdownMenuItem(
                            text = { Text(name, color = Color(android.graphics.Color.parseColor(hex))) },
                            onClick = {
                                viewModel.setFontColor(hex)
                                showColorMenu = false
                            }
                        )
                    }
                }
            }

            // Highlight Color Palette
            var showHighlightMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showHighlightMenu = true }) {
                    Icon(Icons.Default.BorderColor, contentDescription = "Highlight", tint = Color(0xFFF59E0B))
                }
                DropdownMenu(expanded = showHighlightMenu, onDismissRequest = { showHighlightMenu = false }) {
                    DropdownMenuItem(text = { Text("No Highlight") }, onClick = { viewModel.setHighlightColor(null); showHighlightMenu = false })
                    DropdownMenuItem(text = { Text("Yellow") }, onClick = { viewModel.setHighlightColor("#FFF59D"); showHighlightMenu = false })
                    DropdownMenuItem(text = { Text("Green") }, onClick = { viewModel.setHighlightColor("#A5D6A7"); showHighlightMenu = false })
                    DropdownMenuItem(text = { Text("Cyan") }, onClick = { viewModel.setHighlightColor("#80DEEA"); showHighlightMenu = false })
                    DropdownMenuItem(text = { Text("Pink") }, onClick = { viewModel.setHighlightColor("#F48FB1"); showHighlightMenu = false })
                }
            }

            // Clear Formatting
            IconButton(onClick = { viewModel.clearFormatting() }) {
                Icon(Icons.Default.FormatClear, contentDescription = "Clear Formatting")
            }

            VerticalDivider(modifier = Modifier.height(24.dp))

            // Font Size Stepper & Dropdown
            var showFontSizeMenu by remember { mutableStateOf(false) }
            val currentFontSize = uiState.activeTypingStyle.fontSizePt

            IconButton(
                onClick = { viewModel.adjustFontSize(-1f) },
                modifier = Modifier.testTag("btn_decrease_font")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size", modifier = Modifier.size(16.dp))
            }

            Box {
                OutlinedButton(
                    onClick = { showFontSizeMenu = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("font_size_dropdown")
                ) {
                    Text(
                        text = "${currentFontSize.toInt()} pt",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OfficeBluePrimary
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Select Font Size",
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showFontSizeMenu,
                    onDismissRequest = { showFontSizeMenu = false }
                ) {
                    listOf(8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f, 22f, 24f, 28f, 32f, 36f, 48f, 72f).forEach { size ->
                        val isCurrent = currentFontSize.toInt() == size.toInt()
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${size.toInt()} pt",
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) OfficeBluePrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = OfficeBluePrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.setFontSize(size)
                                showFontSizeMenu = false
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = { viewModel.adjustFontSize(+1f) },
                modifier = Modifier.testTag("btn_increase_font")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase Font Size", modifier = Modifier.size(16.dp))
            }
        }

        // Paragraph Alignments & Lists Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Alignment buttons
            IconButton(onClick = { viewModel.setAlignment(BlockAlignment.LEFT) }) {
                Icon(Icons.Default.FormatAlignLeft, contentDescription = "Left Align")
            }
            IconButton(onClick = { viewModel.setAlignment(BlockAlignment.CENTER) }) {
                Icon(Icons.Default.FormatAlignCenter, contentDescription = "Center Align")
            }
            IconButton(onClick = { viewModel.setAlignment(BlockAlignment.RIGHT) }) {
                Icon(Icons.Default.FormatAlignRight, contentDescription = "Right Align")
            }
            IconButton(onClick = { viewModel.setAlignment(BlockAlignment.JUSTIFY) }) {
                Icon(Icons.Default.FormatAlignJustify, contentDescription = "Justify")
            }

            VerticalDivider(modifier = Modifier.height(24.dp))

            // Lists & Indent
            IconButton(onClick = { viewModel.toggleBulletList() }) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet List")
            }
            IconButton(onClick = { viewModel.toggleNumberedList() }) {
                Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered List")
            }
            IconButton(onClick = { viewModel.decreaseIndent() }) {
                Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = "Decrease Indent")
            }
            IconButton(onClick = { viewModel.increaseIndent() }) {
                Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = "Increase Indent")
            }

            VerticalDivider(modifier = Modifier.height(24.dp))

            // Styles Chips (Normal, Title, H1, H2, Quote)
            ParagraphStyle.values().take(6).forEach { style ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.setParagraphStyle(style) },
                    label = { Text(style.displayName, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun InsertRibbonPanel(
    viewModel: EditorViewModel,
    onPickImage: () -> Unit,
    onOpenHeader: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RibbonActionButton(
            label = "Table",
            icon = Icons.Default.TableChart,
            onClick = { viewModel.setShowInsertTableDialog(true) }
        )
        RibbonActionButton(
            label = "Picture",
            icon = Icons.Default.Image,
            onClick = onPickImage
        )
        RibbonActionButton(
            label = "Header",
            icon = Icons.Default.BorderTop,
            onClick = onOpenHeader
        )
        RibbonActionButton(
            label = "Page Break",
            icon = Icons.Default.InsertPageBreak,
            onClick = { viewModel.insertPageBreak() }
        )
        RibbonActionButton(
            label = "Divider Line",
            icon = Icons.Default.HorizontalRule,
            onClick = { viewModel.insertHorizontalRule() }
        )
        RibbonActionButton(
            label = "Date & Time",
            icon = Icons.Default.Schedule,
            onClick = { viewModel.insertDateTime() }
        )
    }
}

@Composable
private fun LayoutRibbonPanel(
    viewModel: EditorViewModel,
    uiState: EditorUiState,
    onOpenHeader: () -> Unit
) {
    val pageSettings = uiState.document.pageSettings

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RibbonActionButton(
            label = "Header & Footer",
            icon = Icons.Default.BorderTop,
            onClick = onOpenHeader
        )
        // Page Size Picker
        var showSizeMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { showSizeMenu = true }) {
                Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Size: ${pageSettings.pageSize.shortLabel}", fontSize = 12.sp)
            }
            DropdownMenu(expanded = showSizeMenu, onDismissRequest = { showSizeMenu = false }) {
                PageSize.values().forEach { size ->
                    val isCurrent = pageSettings.pageSize == size
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = size.displayName,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) OfficeBluePrimary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = OfficeBluePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            viewModel.setPageSize(size)
                            showSizeMenu = false
                        }
                    )
                }
            }
        }

        // Quick Size Chips: A4, 8x13, Legal, Letter, A5
        listOf(PageSize.A4, PageSize.EIGHT_BY_THIRTEEN, PageSize.LEGAL, PageSize.LETTER, PageSize.A5).forEach { size ->
            FilterChip(
                selected = pageSettings.pageSize == size,
                onClick = { viewModel.setPageSize(size) },
                label = { Text(size.shortLabel, fontSize = 11.sp) }
            )
        }

        // Orientation
        OutlinedButton(
            onClick = {
                val nextOrient = if (pageSettings.orientation == PageOrientation.PORTRAIT) PageOrientation.LANDSCAPE else PageOrientation.PORTRAIT
                viewModel.setOrientation(nextOrient)
            }
        ) {
            Icon(
                imageVector = if (pageSettings.orientation == PageOrientation.PORTRAIT) Icons.Default.StayCurrentPortrait else Icons.Default.StayCurrentLandscape,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (pageSettings.orientation == PageOrientation.PORTRAIT) "Portrait" else "Landscape", fontSize = 12.sp)
        }

        // Margins
        var showMarginMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { showMarginMenu = true }) {
                Text("Margins", fontSize = 12.sp)
            }
            DropdownMenu(expanded = showMarginMenu, onDismissRequest = { showMarginMenu = false }) {
                listOf(
                    PageMargins.NORMAL,
                    PageMargins.NARROW,
                    PageMargins.MODERATE,
                    PageMargins.WIDE
                ).forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.displayName) },
                        onClick = {
                            viewModel.setMargins(m)
                            showMarginMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewRibbonPanel(
    viewModel: EditorViewModel,
    uiState: EditorUiState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RibbonActionButton(
            label = "Word Count",
            icon = Icons.Default.Analytics,
            onClick = { viewModel.setShowStatsDialog(true) }
        )
        RibbonActionButton(
            label = "Find & Replace",
            icon = Icons.Default.Search,
            onClick = { viewModel.toggleFindReplace() }
        )
        RibbonActionButton(
            label = "History",
            icon = Icons.Default.History,
            onClick = { viewModel.setShowHistoryDialog(true) }
        )
    }
}

@Composable
private fun TableRibbonPanel(
    viewModel: EditorViewModel,
    uiState: EditorUiState
) {
    val selectedBlockIdx = uiState.selectedBlockIndex

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RibbonActionButton(
            label = "+ Row Below",
            icon = Icons.Default.TableRows,
            onClick = { viewModel.addTableRow(selectedBlockIdx, below = true) }
        )
        RibbonActionButton(
            label = "+ Row Above",
            icon = Icons.Default.TableRows,
            onClick = { viewModel.addTableRow(selectedBlockIdx, below = false) }
        )
        RibbonActionButton(
            label = "- Row",
            icon = Icons.Default.Remove,
            onClick = { viewModel.deleteTableRow(selectedBlockIdx) }
        )

        VerticalDivider(modifier = Modifier.height(24.dp))

        RibbonActionButton(
            label = "+ Column",
            icon = Icons.Default.ViewColumn,
            onClick = { viewModel.addTableCol(selectedBlockIdx) }
        )
        RibbonActionButton(
            label = "- Column",
            icon = Icons.Default.Remove,
            onClick = { viewModel.deleteTableCol(selectedBlockIdx) }
        )

        VerticalDivider(modifier = Modifier.height(24.dp))

        // Cell Background Shading
        var showShadeMenu by remember { mutableStateOf(false) }
        Box {
            RibbonActionButton(
                label = "Cell Shading",
                icon = Icons.Default.FormatColorFill,
                onClick = { showShadeMenu = true }
            )
            DropdownMenu(expanded = showShadeMenu, onDismissRequest = { showShadeMenu = false }) {
                DropdownMenuItem(text = { Text("No Color") }, onClick = { viewModel.setTableCellBgColor(selectedBlockIdx, null); showShadeMenu = false })
                DropdownMenuItem(text = { Text("Light Blue") }, onClick = { viewModel.setTableCellBgColor(selectedBlockIdx, "#E8F0FE"); showShadeMenu = false })
                DropdownMenuItem(text = { Text("Light Gray") }, onClick = { viewModel.setTableCellBgColor(selectedBlockIdx, "#F3F4F6"); showShadeMenu = false })
                DropdownMenuItem(text = { Text("Soft Yellow") }, onClick = { viewModel.setTableCellBgColor(selectedBlockIdx, "#FFF9C4"); showShadeMenu = false })
                DropdownMenuItem(text = { Text("Soft Green") }, onClick = { viewModel.setTableCellBgColor(selectedBlockIdx, "#E8F5E9"); showShadeMenu = false })
            }
        }
    }
}

@Composable
private fun RibbonActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("ribbon_btn_${label.take(5).lowercase()}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = OfficeBluePrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}
