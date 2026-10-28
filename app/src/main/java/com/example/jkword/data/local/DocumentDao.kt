package com.example.jkword.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE isTrash = 0 ORDER BY lastModified DESC")
    fun getAllActive(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isTrash = 0 AND isStarred = 1 ORDER BY lastModified DESC")
    fun getStarred(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isTrash = 1 ORDER BY lastModified DESC")
    fun getTrash(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isTrash = 0 AND (title LIKE '%' || :query || '%' OR previewSnippet LIKE '%' || :query || '%') ORDER BY lastModified DESC")
    fun search(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE docUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Delete
    suspend fun delete(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM documents WHERE isTrash = 1")
    suspend fun emptyTrash()

    @Query("UPDATE documents SET isTrash = 1, lastModified = :time WHERE id = :id")
    suspend fun moveToTrash(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isTrash = 0, lastModified = :time WHERE id = :id")
    suspend fun restoreFromTrash(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isStarred = :isStarred WHERE id = :id")
    suspend fun toggleStarred(id: Long, isStarred: Boolean)

    @Query("UPDATE documents SET title = :newTitle, lastModified = :time WHERE id = :id")
    suspend fun rename(id: Long, newTitle: String, time: Long = System.currentTimeMillis())
}
