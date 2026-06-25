package com.fardeenkhan.moodtune.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fardeenkhan.moodtune.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: String)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun incrementSongPlayCount(id: String, timestamp: Long)

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT 10")
    fun getMostPlayedSongs(): Flow<List<SongEntity>>

    @Query("SELECT lyrics FROM songs WHERE id = :id")
    suspend fun getLyricsForSong(id: String): String?

    @Query("UPDATE songs SET lyrics = :lyricsJson WHERE id = :id")
    suspend fun updateLyrics(id: String, lyricsJson: String)
}
