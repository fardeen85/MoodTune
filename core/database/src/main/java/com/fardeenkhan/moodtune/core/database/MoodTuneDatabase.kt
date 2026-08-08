package com.fardeenkhan.moodtune.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fardeenkhan.moodtune.core.database.dao.PlaylistDao
import com.fardeenkhan.moodtune.core.database.dao.SongDao
import com.fardeenkhan.moodtune.core.database.entity.PlaylistEntity
import com.fardeenkhan.moodtune.core.database.entity.PlaylistSongCrossRef
import com.fardeenkhan.moodtune.core.database.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 6,
    exportSchema = false
)
abstract class MoodTuneDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN durationMs INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN lyrics TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN localAlbumArtPath TEXT DEFAULT NULL")
            }
        }

        // Drops the unused `energy` column (Song.energy was always null - never set by Gemini
        // or the device scanner). SQLite's ALTER TABLE DROP COLUMN needs SQLite 3.35+, not
        // reliably available at minSdk 29, so this recreates the table without that column
        // instead - the standard safe pattern for a column drop across all supported devices.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE songs_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        imageUrl TEXT,
                        externalUrl TEXT,
                        mood TEXT,
                        explanation_about TEXT NOT NULL,
                        explanation_mood TEXT NOT NULL,
                        explanation_context TEXT NOT NULL,
                        playCount INTEGER NOT NULL,
                        lastPlayedAt INTEGER,
                        durationMs INTEGER,
                        lyrics TEXT,
                        localAlbumArtPath TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO songs_new (id, title, artist, reason, imageUrl, externalUrl, mood,
                        explanation_about, explanation_mood, explanation_context, playCount,
                        lastPlayedAt, durationMs, lyrics, localAlbumArtPath)
                    SELECT id, title, artist, reason, imageUrl, externalUrl, mood,
                        explanation_about, explanation_mood, explanation_context, playCount,
                        lastPlayedAt, durationMs, lyrics, localAlbumArtPath
                    FROM songs
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE songs")
                db.execSQL("ALTER TABLE songs_new RENAME TO songs")
            }
        }
    }
}
