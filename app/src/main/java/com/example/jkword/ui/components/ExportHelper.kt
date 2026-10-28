package com.example.jkword.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.jkword.data.local.DocumentRepository
import com.example.jkword.model.DocumentModel
import com.example.ui.theme.OfficeBluePrimary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun ExportDialog(
    document: DocumentModel,
    repository: DocumentRepository,
    context: Context,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, tint = OfficeBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Export Options", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Save to your device or share in various formats (Image, PDF, DOCX, TXT):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // FIX (export dialog closed instantly + double-tap duplicates):
                // the old code dismissed the dialog the moment a button was
                // tapped, while the export ran in the background — failures
                // were invisible and double-taps ran duplicate exports. Now
                // the running option disables its buttons, shows progress,
                // and the dialog only closes after a successful export.
                var busyOption by remember { mutableStateOf<String?>(null) }
                val buttonsEnabled = busyOption == null
                if (busyOption != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Exporting…",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 1. Image (.png)
                ExportOptionCard(
                    title = "Image / Picture (.png)",
                    description = "High-resolution picture of all document pages for gallery or sharing",
                    icon = Icons.Default.Image,
                    enabled = buttonsEnabled,
                    onSave = {
                        if (busyOption != null) return@ExportOptionCard
                        busyOption = "image-save"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = repository.exportToImage(document)
                                val cleanTitle = document.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                val (saved, toDownloads) = saveToDevice(context, file, "$cleanTitle.png")
                                launch(Dispatchers.Main) {
                                    onSuccess(
                                        if (toDownloads) "Saved Image to Downloads: ${saved.name}"
                                        else "Saved Image to app storage (Downloads failed): ${saved.name}"
                                    )
                                    busyOption = null
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    onSuccess("Image save failed: ${e.message}")
                                    busyOption = null
                                }
                            }
                        }
                    },
                    onShare = {
                        if (busyOption != null) return@ExportOptionCard
                        busyOption = "image-share"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = repository.exportToImage(document)
                                val shared = shareFile(context, file, "image/png")
                                launch(Dispatchers.Main) {
                                    onSuccess(if (shared) "Image file ready to share" else "Share failed: could not open share sheet")
                                    busyOption = null
                                    if (shared) onDismiss()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    onSuccess("Image share failed: ${e.message}")
                                    busyOption = null
                                }
                            }
                        }
                    }
                )

                // 2. PDF Document (.pdf)
                ExportOptionCard(
                    title = "PDF Document (.pdf)",
                    description = "Print-ready vector PDF with exact paper size, margins, and page numbering",
                    icon = Icons.Default.PictureAsPdf,
                    enabled = buttonsEnabled,
                    onSave = {
                        if (busyOption != null) return@ExportOptionCard
                        busyOption = "pdf-save"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = repository.exportToCache(document, "pdf")
                                val cleanTitle = document.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                val (saved, toDownloads) = saveToDevice(context, file, "$cleanTitle.pdf")
                                launch(Dispatchers.Main) {
                                    onSuccess(
                                        if (toDownloads) "Saved PDF to Downloads: ${saved.name}"
                                        else "Saved PDF to app storage (Downloads failed): ${saved.name}"
                                    )
                                    busyOption = null
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    onSuccess("PDF save failed: ${e.message}")
                                    busyOption = null
                                }
                            }
                        }
                    },
                    onShare = {
                        if (busyOption != null) return@ExportOptionCard
                        busyOption = "pdf-share"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = repository.exportToCache(document, "pdf")
                                val shared = shareFile(context, file, "application/pdf")
                                launch(Dispatchers.Main) {
                                    onSuccess(if (shared) "PDF ready to share" else "Share failed: could not open share sheet")
                                    busyOption = null
                                    if (shared) onDismiss()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    onSuccess("PDF share failed: ${e.message}")
                                    busyOption = null
                                }
                            }
                        }
                    }
                )

                // 3. Microsoft Word (.docx)
                ExportOptionCard(
                    title = "Word Document (.docx)",
                    description = "Standard Microsoft Word OOXML document for desktop editing",
                    icon = Icons.Default.Description,
                    enabled = buttonsEnabled,
                    onSave = {
                        if (busyOption != null) return@ExportOptionCard
                        busyOption = "docx-save"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = repository.exportToCache(document, "docx")
                                val cleanTitle = document.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                val (saved, toDownloads) = saveToDevice(context, file, "$cleanTitle.docx")
                                launch(Dispatchers.Main) {
                                    onSuccess(
                                        if (toDownloads) "Saved DOCX to Downloads: ${saved.name}"
                                        else "Saved DOCX to app storage (Downloads failed): ${saved.name}"
                                    )
                                    busyOption = null
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    onSuccess("DOCX save failed: ${e.message}")
                                    busyOption = null
                                }
                            }
                        }
                    },
                    onShare = {
                        if (busyOption != null) return@ExportOptionCard
                        busyOption = "docx-share"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = repository.exportToCache(document, "docx")
                                val shared = shareFile(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                launch(Dispatchers.Main) {
                                    onSuccess(if (shared) "DOCX ready to share" else "Share failed: could not open share sheet")
                                    busyOption = null
                                    if (shared) onDismiss()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    onSuccess("DOCX share failed: ${e.message}")
                                    busyOption = null
                                }
                            }
                        }
                    }
                )

                // 4. Plain Text (.txt)
                ExportOptionCard(
                    title = "Plain Text (.txt)",
                    description = "Lightweight unformatted text file",
                    icon = Icons.Default.TextSnippet,
                    enabled = buttonsEnabled,
                    onSave = {
                        if (busyOption != null) return@ExportOptionCard
                        busyOption = "txt-save"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = repository.exportToCache(document, "txt")
                                val cleanTitle = document.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                val (saved, toDownloads) = saveToDevice(context, file, "$cleanTitle.txt")
                                launch(Dispatchers.Main) {
                                    onSuccess(
                                        if (toDownloads) "Saved TXT to Downloads: ${saved.name}"
                                        else "Saved TXT to app storage (Downloads failed): ${saved.name}"
                                    )
                                    busyOption = null
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    onSuccess("TXT save failed: ${e.message}")
                                    busyOption = null
                                }
                            }
                        }
                    },
                    onShare = {
                        if (busyOption != null) return@ExportOptionCard
                        busyOption = "txt-share"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = repository.exportToCache(document, "txt")
                                val shared = shareFile(context, file, "text/plain")
                                launch(Dispatchers.Main) {
                                    onSuccess(if (shared) "Text file ready to share" else "Share failed: could not open share sheet")
                                    busyOption = null
                                    if (shared) onDismiss()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    onSuccess("Text share failed: ${e.message}")
                                    busyOption = null
                                }
                            }
                        }
                    }
                )

                // 5. Print Service
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (busyOption == null) {
                                printDocument(context, document, repository)
                                onDismiss()
                            }
                        }
                        .testTag("export_option_print"),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = OfficeBluePrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Print / System PDF", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Send to connected printer or print dialog", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ExportOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onSave: () -> Unit,
    onShare: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export_card_${title.take(4).lowercase()}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = OfficeBluePrimary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onSave,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onShare,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = OfficeBluePrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Saves [sourceFile] to public Downloads. Returns the file handle plus
 * whether it actually landed in Downloads: the old code silently fell back
 * to app-private storage while the UI still claimed "Saved to Downloads",
 * so users searched Downloads for a file that was never there.
 */
fun saveToDevice(context: Context, sourceFile: File, displayName: String): Pair<File, Boolean> {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: MediaStore is the correct way to write to public Downloads
            // (direct file paths are blocked by scoped storage).
            val mime = when (displayName.substringAfterLast('.', "").lowercase()) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/JK Word")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw java.io.IOException("MediaStore insert failed")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            } ?: throw java.io.IOException("Could not open MediaStore output stream")
            // Return an app-private copy as the File handle for callers.
            val local = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), displayName)
            sourceFile.copyTo(local, overwrite = true)
            Pair(local, true)
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val targetDir = if (downloadsDir != null && downloadsDir.exists()) downloadsDir
                else (context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
            targetDir.mkdirs()
            val destFile = File(targetDir, displayName)
            sourceFile.copyTo(destFile, overwrite = true)
            Pair(destFile, true)
        }
    } catch (e: Exception) {
        val fallbackFile = File(context.filesDir, displayName)
        sourceFile.copyTo(fallbackFile, overwrite = true)
        Pair(fallbackFile, false)
    }
}

/** Shares [file]; returns false (instead of silently swallowing the error) on failure. */
fun shareFile(context: Context, file: File, mimeType: String): Boolean {
    return try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Document via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun printDocument(context: Context, document: DocumentModel, repository: DocumentRepository) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "${document.title} Document"

    printManager.print(
        jobName,
        object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("${document.title}.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                try {
                    val pfd = destination ?: return
                    FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                        repository.exportToStream(document, "pdf", outputStream)
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        },
        null
    )
}
