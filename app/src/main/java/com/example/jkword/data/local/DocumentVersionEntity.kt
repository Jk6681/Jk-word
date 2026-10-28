package com.example.jkword.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_versions",
    indices = [Index(value = ["documentId", "timestamp"])]
)
data class DocumentVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val versionNumber: Int,
    val title: String,
    val snapshotJson: String,
    val wordCount: Int,
    val charCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String = "Version checkpoint"
)
