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
    version = 5,
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
    }
}
