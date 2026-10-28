package com.example.jkword.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jkword.data.local.AppThemeMode
import com.example.jkword.data.local.DocumentEntity
import com.example.jkword.ui.components.RenameDialog
import com.example.jkword.ui.components.TemplatesBottomSheet
import com.example.ui.theme.LocalThemeMode
import com.example.ui.theme.LocalToggleTheme
import com.example.ui.theme.OfficeBluePrimary
import com.example.ui.theme.OfficeBlueLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDocument: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // FIX (invisible toasts): HomeViewModel toast messages (import, trash,
    // duplicate, ...) had no SnackbarHost anywhere, so they never reached
    // the user. Show each message once, then clear it.
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearToast()
        }
    }

    // SAF File Picker for importing DOCX / TXT files
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val filename = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex) else "Imported.docx"
            } ?: "Imported.docx"

            contentResolver.openInputStream(uri)?.use { stream ->
                // FIX (import stream bug): read the bytes NOW, inside the .use block.
                // The old code passed the live stream to an async ViewModel call, so
                // .use closed the stream before the coroutine ever read it — TXT
                // imports failed and DOCX imports fell into the fake-"Note:" fallback.
                val bytes = stream.readBytes()
                // If the picked file has no usable extension, decide DOCX vs TXT
                // from its MIME type so it never lands in the wrong parser.
                val effectiveFilename = when {
                    filename.endsWith(".docx", ignoreCase = true) ||
                        filename.endsWith(".txt", ignoreCase = true) -> filename
                    contentResolver.getType(uri) ==
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "$filename.docx"
                    contentResolver.getType(uri) == "text/plain" -> "$filename.txt"
                    else -> filename
                }
                viewModel.importDocument(bytes, effectiveFilename) { newId ->
                    onOpenDocument(newId)
                }
            }
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
                // Top App Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(OfficeBluePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "W",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "JK Word",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Native Word Processor",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Theme toggle (System / Light / Dark)
                    val themeMode = LocalThemeMode.current
                    val toggleTheme = LocalToggleTheme.current
                    IconButton(
                        onClick = toggleTheme,
                        modifier = Modifier.testTag("theme_toggle_button")
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

                    // Layout toggle (Grid / List)
                    IconButton(
                        onClick = { viewModel.toggleLayoutView() },
                        modifier = Modifier.testTag("toggle_layout_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View"
                        )
                    }

                    // Templates launcher button
                    IconButton(
                        onClick = { viewModel.setTemplatesSheetVisible(true) },
                        modifier = Modifier.testTag("templates_button")
                    ) {
                        Icon(Icons.Default.AutoAwesomeMotion, contentDescription = "Templates", tint = OfficeBluePrimary)
                    }

                    // About Version 8.0
                    IconButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier.testTag("about_app_button")
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = "About Version 8.0", tint = OfficeBluePrimary)
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("search_input"),
                    placeholder = { Text("Search documents...", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = OfficeBluePrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                PrimaryTabRow(
                    selectedTabIndex = uiState.currentTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = OfficeBluePrimary
                ) {
                    HomeTab.values().forEach { tab ->
                        Tab(
                            selected = uiState.currentTab == tab,
                            onClick = { viewModel.setTab(tab) },
                            text = {
                                Text(
                                    text = when (tab) {
                                        HomeTab.RECENT -> "Recent"
                                        HomeTab.STARRED -> "Starred"
                                        HomeTab.ALL -> "All Files"
                                        HomeTab.TRASH -> "Trash"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = if (uiState.currentTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.createBlankDocument { newId ->
                        onOpenDocument(newId)
                    }
                },
                containerColor = OfficeBluePrimary,
                contentColor = Color.White,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("create_document_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Document")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Document", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Quick Action Cards (Shown in Recent and All tabs)
                if (uiState.currentTab == HomeTab.RECENT || uiState.currentTab == HomeTab.ALL) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionCard(
                            title = "Blank Page",
                            subtitle = "Clean slate",
                            icon = Icons.Default.NoteAdd,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.createBlankDocument { newId ->
                                    onOpenDocument(newId)
                                }
                            }
                        )

                        QuickActionCard(
                            title = "Open DOCX",
                            subtitle = "From storage",
                            icon = Icons.Default.FolderOpen,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                importLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "text/plain",
                                        "application/msword"
                                    )
                                )
                            }
                        )

                        QuickActionCard(
                            title = "Templates",
                            subtitle = "10+ styles",
                            icon = Icons.Default.DashboardCustomize,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setTemplatesSheetVisible(true) }
                        )
                    }
                }

                // Trash Header Action Bar
                if (uiState.currentTab == HomeTab.TRASH && documents.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${documents.size} items in trash",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(
                            onClick = { viewModel.emptyTrash() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Empty Trash", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Documents List or Grid
                if (documents.isEmpty()) {
                    EmptyStateView(
                        tab = uiState.currentTab,
                        onCreateBlank = {
                            viewModel.createBlankDocument { onOpenDocument(it) }
                        },
                        onOpenTemplates = { viewModel.setTemplatesSheetVisible(true) }
                    )
                } else if (uiState.isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            DocumentGridCard(
                                doc = doc,
                                isTrash = uiState.currentTab == HomeTab.TRASH,
                                onClick = {
                                    if (uiState.currentTab != HomeTab.TRASH) onOpenDocument(doc.id)
                                },
                                onToggleStar = { viewModel.toggleStarred(doc) },
                                onShowOptions = { viewModel.selectDocForOptions(doc) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            DocumentRowCard(
                                doc = doc,
                                isTrash = uiState.currentTab == HomeTab.TRASH,
                                onClick = {
                                    if (uiState.currentTab != HomeTab.TRASH) onOpenDocument(doc.id)
                                },
                                onToggleStar = { viewModel.toggleStarred(doc) },
                                onShowOptions = { viewModel.selectDocForOptions(doc) }
                            )
                        }
                    }
                }
            }

            // Loading indicator overlay during creation
            if (uiState.isCreateLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = OfficeBluePrimary)
                }
            }
        }
    }

    // Templates Bottom Sheet
    if (uiState.isTemplatesSheetVisible) {
        TemplatesBottomSheet(
            onDismiss = { viewModel.setTemplatesSheetVisible(false) },
            onSelectTemplate = { template ->
                viewModel.createDocumentFromTemplate(template) { newId ->
                    onOpenDocument(newId)
                }
            }
        )
    }

    if (showAboutDialog) {
        AboutVersion8Dialog(onDismiss = { showAboutDialog = false })
    }

    // Rename Dialog
    uiState.docToRename?.let { doc ->
        RenameDialog(
            initialTitle = doc.title,
            onDismiss = { viewModel.setDocToRename(null) },
            onConfirm = { newTitle ->
                viewModel.renameDocument(doc.id, newTitle)
            }
        )
    }

    // Document Options Popup Menu
    uiState.selectedDocForOptions?.let { doc ->
        DocumentOptionsBottomSheet(
            doc = doc,
            isTrash = uiState.currentTab == HomeTab.TRASH,
            onDismiss = { viewModel.selectDocForOptions(null) },
            onOpen = {
                viewModel.selectDocForOptions(null)
                onOpenDocument(doc.id)
            },
            onRename = {
                viewModel.selectDocForOptions(null)
                viewModel.setDocToRename(doc)
            },
            onDuplicate = {
                viewModel.duplicateDocument(doc.id)
            },
            onMoveToTrash = {
                viewModel.moveToTrash(doc.id)
            },
            onRestore = {
                viewModel.restoreFromTrash(doc.id)
            },
            onDeletePermanently = {
                viewModel.deletePermanently(doc.id)
            }
        )
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("quick_action_${title.take(5).lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = OfficeBlueLight.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(OfficeBluePrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = OfficeBluePrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun DocumentRowCard(
    doc: DocumentEntity,
    isTrash: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onShowOptions: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val formattedDate = remember(doc.lastModified) { dateFormat.format(Date(doc.lastModified)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("doc_row_${doc.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Page Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(OfficeBluePrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = OfficeBluePrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$formattedDate • ${doc.wordCount} words",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (doc.previewSnippet.isNotBlank() && doc.previewSnippet != "Empty document") {
                    Text(
                        text = doc.previewSnippet,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isTrash) {
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Star Document",
                        tint = if (doc.isStarred) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onShowOptions,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DocumentGridCard(
    doc: DocumentEntity,
    isTrash: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onShowOptions: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val formattedDate = remember(doc.lastModified) { dateFormat.format(Date(doc.lastModified)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("doc_grid_${doc.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Paper preview block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text(
                    text = if (doc.previewSnippet.isNotBlank()) doc.previewSnippet else "Empty document",
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = doc.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$formattedDate • ${doc.wordCount}w",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    if (!isTrash) {
                        IconButton(
                            onClick = onToggleStar,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Star",
                                tint = if (doc.isStarred) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onShowOptions,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    tab: HomeTab,
    onCreateBlank: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(OfficeBluePrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (tab) {
                    HomeTab.STARRED -> Icons.Outlined.StarBorder
                    HomeTab.TRASH -> Icons.Outlined.Delete
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                tint = OfficeBluePrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (tab) {
                HomeTab.STARRED -> "No Starred Documents"
                HomeTab.TRASH -> "Trash is Empty"
                else -> "No Documents Yet"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when (tab) {
                HomeTab.STARRED -> "Star important documents to access them quickly here."
                HomeTab.TRASH -> "Deleted documents will appear here before permanent removal."
                else -> "Create your first document or pick from professional templates."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (tab != HomeTab.TRASH) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCreateBlank,
                    colors = ButtonDefaults.buttonColors(containerColor = OfficeBluePrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Blank Document")
                }
                OutlinedButton(onClick = onOpenTemplates) {
                    Text("Templates")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentOptionsBottomSheet(
    doc: DocumentEntity,
    isTrash: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = doc.title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${doc.wordCount} words • Page size: ${doc.pageSize}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            if (!isTrash) {
                ListItem(
                    headlineContent = { Text("Open in Editor") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = OfficeBluePrimary) },
                    modifier = Modifier.clickable(onClick = onOpen)
                )
                ListItem(
                    headlineContent = { Text("Rename") },
                    leadingContent = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onRename)
                )
                ListItem(
                    headlineContent = { Text("Duplicate") },
                    leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onDuplicate)
                )
                ListItem(
                    headlineContent = { Text("Move to Trash", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable(onClick = onMoveToTrash)
                )
            } else {
                ListItem(
                    headlineContent = { Text("Restore Document") },
                    leadingContent = { Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = OfficeBluePrimary) },
                    modifier = Modifier.clickable(onClick = onRestore)
                )
                ListItem(
                    headlineContent = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable(onClick = onDeletePermanently)
                )
            }
        }
    }
}

@Composable
fun AboutVersion8Dialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OfficeBluePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("W", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("JK Word Office", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Version 8.0 (Build 80)", fontSize = 12.sp, color = OfficeBluePrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OfficeBluePrimary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "🚀 High-Performance & Stability Release",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OfficeBluePrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = "Key upgrades in Version 8.0:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("⚡ ", fontSize = 13.sp)
                        Text(
                            "Zero-Latency Typing: Instant character rendering without delay or input lag on all Android devices.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("✨ ", fontSize = 13.sp)
                        Text(
                            "No Black Text or Flickering: Synchronous span styling guarantees text never turns black or flickers during cursor movement.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("📄 ", fontSize = 13.sp)
                        Text(
                            "Strict Physical Page Layout: Exact paper boundaries for A4, 8x13 Legal, Letter, and continuous mode with smooth, jump-free scrolling.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🎯 ", fontSize = 13.sp)
                        Text(
                            "Precision Selection & Formatting: Bold, italic, highlights, and colors apply strictly to selected text without altering other content.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("💾 ", fontSize = 13.sp)
                        Text(
                            "Non-blocking Asynchronous Autosave: Background persistence with local checkpoints to prevent data loss.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("📦 ", fontSize = 13.sp)
                        Text(
                            "Universal Export: Save directly to Device/Downloads or share as DOCX, PDF, PNG Images, and TXT.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = OfficeBluePrimary),
                modifier = Modifier.testTag("dismiss_about_button")
            ) {
                Text("Got It")
            }
        }
    )
}
