package com.example.jkword.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val docUuid: String,
    val title: String,
    val filePath: String,
    val previewSnippet: String,
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val pageCount: Int = 1,
    val lastModified: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val isStarred: Boolean = false,
    val isTrash: Boolean = false,
    val templateType: String = "blank",
    val pageSize: String = "A4",
    val orientation: String = "PORTRAIT",
    val fileSize: Long = 0L
)
