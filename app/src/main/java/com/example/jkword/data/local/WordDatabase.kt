package com.example.jkword.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentEntity::class, DocumentVersionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class WordDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun documentVersionDao(): DocumentVersionDao

    companion object {
        /**
         * FIX (destructive migration): the old code used
         * fallbackToDestructiveMigration(), so any future schema change
         * would silently wipe every user document. This migration preserves
         * data: it adds any v2 column that a v1 database is missing
         * (checked via PRAGMA, so it is a no-op on databases that already
         * have them) and creates the versions table if it is absent.
         * Future schema changes must add a real Migration here instead of
         * restoring the destructive fallback.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun hasColumn(table: String, column: String): Boolean {
                    val cursor = db.query("SELECT name FROM pragma_table_info('$table')")
                    try {
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex) == column) return true
                        }
                    } finally {
                        cursor.close()
                    }
                    return false
                }
                fun ensureColumn(table: String, column: String, ddl: String) {
                    if (!hasColumn(table, column)) {
                        db.execSQL("ALTER TABLE $table ADD COLUMN $column $ddl")
                    }
                }
                // documents table (v2 columns with entity defaults).
                ensureColumn("documents", "wordCount", "INTEGER NOT NULL DEFAULT 0")
                ensureColumn("documents", "charCount", "INTEGER NOT NULL DEFAULT 0")
                ensureColumn("documents", "pageCount", "INTEGER NOT NULL DEFAULT 1")
                ensureColumn("documents", "isStarred", "INTEGER NOT NULL DEFAULT 0")
                ensureColumn("documents", "isTrash", "INTEGER NOT NULL DEFAULT 0")
                ensureColumn("documents", "templateType", "TEXT NOT NULL DEFAULT 'blank'")
                ensureColumn("documents", "pageSize", "TEXT NOT NULL DEFAULT 'A4'")
                ensureColumn("documents", "orientation", "TEXT NOT NULL DEFAULT 'PORTRAIT'")
                ensureColumn("documents", "fileSize", "INTEGER NOT NULL DEFAULT 0")
                // document_versions table (created if a v1 database lacks it).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `document_versions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`documentId` INTEGER NOT NULL, " +
                        "`versionNumber` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`snapshotJson` TEXT NOT NULL, " +
                        "`wordCount` INTEGER NOT NULL, " +
                        "`charCount` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`summary` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_document_versions_documentId_timestamp` " +
                        "ON `document_versions` (`documentId`, `timestamp`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: WordDatabase? = null

        fun getInstance(context: Context): WordDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WordDatabase::class.java,
                    "jkword_database.db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
