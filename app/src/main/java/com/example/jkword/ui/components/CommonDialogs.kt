package com.example.jkword.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jkword.data.local.DocumentRepository
import com.example.jkword.model.DocumentTemplates
import com.example.ui.theme.OfficeBluePrimary

@Composable
fun RenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Document", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Document Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title.trim()) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DocumentStatsDialog(
    stats: DocumentRepository.DocumentStats,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = OfficeBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Document Statistics", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatRow("Words", stats.wordCount.toString())
                StatRow("Characters (with spaces)", stats.charCount.toString())
                StatRow("Characters (no spaces)", stats.charCountNoSpaces.toString())
                StatRow("Paragraphs", stats.paragraphCount.toString())
                StatRow("Estimated Pages", stats.estimatedPages.toString())
                StatRow("Estimated Reading Time", "${stats.readingTimeMinutes} min")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                StatRow("Editor Engine", "JK Word v8.0 (Build 80)")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("stats_close_button")) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun InsertTableDialog(
    onDismiss: () -> Unit,
    onConfirm: (rows: Int, cols: Int) -> Unit
) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Table", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rows: $rows", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (rows > 1) rows-- },
                            enabled = rows > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease Rows")
                        }
                        IconButton(
                            onClick = { if (rows < 10) rows++ },
                            enabled = rows < 10
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase Rows")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Columns: $cols", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (cols > 1) cols-- },
                            enabled = cols > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease Columns")
                        }
                        IconButton(
                            onClick = { if (cols < 6) cols++ },
                            enabled = cols < 6
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase Columns")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(rows, cols) },
                modifier = Modifier.testTag("insert_table_confirm_button")
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesBottomSheet(
    onDismiss: () -> Unit,
    onSelectTemplate: (DocumentTemplates.TemplateInfo) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember {
        listOf("All") + DocumentTemplates.ALL_TEMPLATES.map { it.category }.distinct()
    }

    val filteredTemplates = remember(selectedCategory) {
        if (selectedCategory == "All") {
            DocumentTemplates.ALL_TEMPLATES
        } else {
            DocumentTemplates.ALL_TEMPLATES.filter { it.category == selectedCategory }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Document Templates",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Start with a professionally structured document",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxHeight(0.7f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTemplates) { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTemplate(template) }
                            .testTag("template_item_${template.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(OfficeBluePrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (template.category) {
                                        "Career" -> Icons.Default.Badge
                                        "Business" -> Icons.Default.BusinessCenter
                                        "Education" -> Icons.Default.School
                                        "Finance" -> Icons.Default.Receipt
                                        "Multilingual" -> Icons.Default.Translate
                                        else -> Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = OfficeBluePrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = template.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderFooterDialog(
    initialHeader: String,
    initialFooter: String,
    initialShowPageNumbers: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (header: String, footer: String, showNumbers: Boolean) -> Unit
) {
    var headerText by remember { mutableStateOf(initialHeader) }
    var footerText by remember { mutableStateOf(initialFooter) }
    var showNumbers by remember { mutableStateOf(initialShowPageNumbers) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BorderTop, contentDescription = null, tint = OfficeBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Page Header & Footer", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = headerText,
                    onValueChange = { headerText = it },
                    label = { Text("Header Text (Top of page)") },
                    placeholder = { Text("e.g. Document Title, Author, Company") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("header_input")
                )

                OutlinedTextField(
                    value = footerText,
                    onValueChange = { footerText = it },
                    label = { Text("Footer Text (Bottom of page)") },
                    placeholder = { Text("e.g. Confidential, Date, Department") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("footer_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Show Page Numbers", fontSize = 14.sp)
                    Switch(
                        checked = showNumbers,
                        onCheckedChange = { showNumbers = it },
                        modifier = Modifier.testTag("switch_page_numbers")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(headerText.trim(), footerText.trim(), showNumbers) },
                modifier = Modifier.testTag("confirm_header_footer_btn")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
