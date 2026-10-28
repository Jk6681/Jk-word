package com.example.jkword.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentVersionDao {

    @Query("SELECT * FROM document_versions WHERE documentId = :docId ORDER BY timestamp DESC")
    fun getVersionsForDocument(docId: Long): Flow<List<DocumentVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: DocumentVersionEntity): Long

    @Query("SELECT COUNT(*) FROM document_versions WHERE documentId = :docId")
    suspend fun getVersionCount(docId: Long): Int

    @Query("SELECT * FROM document_versions WHERE id = :versionId LIMIT 1")
    suspend fun getVersionById(versionId: Long): DocumentVersionEntity?

    @Query("DELETE FROM document_versions WHERE documentId = :docId AND id NOT IN (SELECT id FROM document_versions WHERE documentId = :docId ORDER BY timestamp DESC LIMIT 30)")
    suspend fun trimOldVersions(docId: Long)

    @Query("DELETE FROM document_versions WHERE documentId = :docId")
    suspend fun deleteAllVersionsForDocument(docId: Long)
}
