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
import com.example.jkword.data.local.DocumentVersionEntity
import com.example.ui.theme.OfficeBluePrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryDialog(
    versions: List<DocumentVersionEntity>,
    currentVersionSnapshot: String? = null,
    onDismiss: () -> Unit,
    onRevert: (DocumentVersionEntity) -> Unit
) {
    var selectedVersionForRevert by remember { mutableStateOf<DocumentVersionEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm:ss a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(OfficeBluePrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = OfficeBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Document History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${versions.size} versions recorded locally",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            if (versions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No previous versions saved yet.\nVersions are recorded automatically as you edit.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(versions, key = { it.id }) { version ->
                        val formattedTime = remember(version.timestamp) {
                            dateFormat.format(Date(version.timestamp))
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("version_item_${version.id}"),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Version ${version.versionNumber}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• ${version.wordCount} words",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formattedTime,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = version.summary,
                                        fontSize = 10.sp,
                                        color = OfficeBluePrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Button(
                                    onClick = { selectedVersionForRevert = version },
                                    modifier = Modifier.testTag("revert_btn_${version.id}"),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OfficeBluePrimary)
                                ) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Revert", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_history_btn")) {
                Text("Close")
            }
        }
    )

    // Revert confirmation dialog
    selectedVersionForRevert?.let { targetVersion ->
        AlertDialog(
            onDismissRequest = { selectedVersionForRevert = null },
            title = { Text("Revert to Version ${targetVersion.versionNumber}?") },
            text = {
                Text(
                    text = "This will restore the document to the state from ${dateFormat.format(Date(targetVersion.timestamp))}. Your current edits will also be saved to history so you won't lose work."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRevert(targetVersion)
                        selectedVersionForRevert = null
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OfficeBluePrimary),
                    modifier = Modifier.testTag("confirm_revert_btn")
                ) {
                    Text("Confirm Revert")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedVersionForRevert = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
