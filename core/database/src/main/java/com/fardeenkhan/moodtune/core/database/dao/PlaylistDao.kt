package com.fardeenkhan.moodtune.core.database.dao

import androidx.room.*
import com.fardeenkhan.moodtune.core.database.entity.PlaylistEntity
import com.fardeenkhan.moodtune.core.database.entity.PlaylistSongCrossRef
import com.fardeenkhan.moodtune.core.database.entity.PlaylistWithSongs
import com.fardeenkhan.moodtune.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Transaction
    @Query("""
        SELECT * FROM playlists
        LEFT JOIN playlist_song_cross_ref ON playlists.id = playlist_song_cross_ref.playlistId
        LEFT JOIN songs ON songs.id = playlist_song_cross_ref.songId
        WHERE LOWER(playlists.mood) = LOWER(:mood)
        ORDER BY playlist_song_cross_ref.position ASC
    """)
    suspend fun getPlaylistByMood(mood: String): Map<PlaylistEntity, List<SongEntity>>

    @Transaction
    @Query("""
        SELECT * FROM playlists
        LEFT JOIN playlist_song_cross_ref ON playlists.id = playlist_song_cross_ref.playlistId
        LEFT JOIN songs ON songs.id = playlist_song_cross_ref.songId
        ORDER BY playlist_song_cross_ref.position ASC
    """)
    fun getPlaylistsWithSongs(): Flow<Map<PlaylistEntity, List<SongEntity>>>

    @Transaction
    @Query("""
        SELECT * FROM playlists
        LEFT JOIN playlist_song_cross_ref ON playlists.id = playlist_song_cross_ref.playlistId
        LEFT JOIN songs ON songs.id = playlist_song_cross_ref.songId
        WHERE playlists.lastPlayedAt IS NOT NULL
        ORDER BY playlists.lastPlayedAt DESC, playlist_song_cross_ref.position ASC
        LIMIT 10
    """)
    fun getRecentlyPlayedPlaylists(): Flow<Map<PlaylistEntity, List<SongEntity>>>

    @Query("UPDATE playlists SET lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun updateLastPlayedAt(id: String, timestamp: Long)

    @Transaction
    @Query("""
        SELECT * FROM playlists
        LEFT JOIN playlist_song_cross_ref ON playlists.id = playlist_song_cross_ref.playlistId
        LEFT JOIN songs ON songs.id = playlist_song_cross_ref.songId
        WHERE playlists.id = :id
        ORDER BY playlist_song_cross_ref.position ASC
    """)
    fun getPlaylistWithSongsById(id: String): Flow<Map<PlaylistEntity, List<SongEntity>>>

    @Transaction
    @Query("""
        SELECT * FROM playlists
        LEFT JOIN playlist_song_cross_ref ON playlists.id = playlist_song_cross_ref.playlistId
        LEFT JOIN songs ON songs.id = playlist_song_cross_ref.songId
        WHERE LOWER(playlists.mood) = LOWER(:mood)
        ORDER BY playlist_song_cross_ref.position ASC
    """)
    fun getPlaylistByMoodFlow(mood: String): Flow<Map<PlaylistEntity, List<SongEntity>>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongCrossRefs(crossRefs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongCrossRefsByPlaylistId(playlistId: String)
}
