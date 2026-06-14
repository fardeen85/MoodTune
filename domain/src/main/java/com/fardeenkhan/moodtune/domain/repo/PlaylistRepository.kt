package com.fardeenkhan.moodtune.domain.repo

import com.fardeenkhan.moodtune.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylists(): Flow<List<Playlist>>
    fun getRecentlyPlayedPlaylists(): Flow<List<Playlist>>
    fun getPlaylistById(id: String): Flow<Playlist?>
    fun getPlaylistByMoodFlow(mood: String): Flow<Playlist?>
    suspend fun markPlaylistAsPlayed(id: String)
    suspend fun getPlaylistByMood(mood: String): Playlist?
    suspend fun savePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(id: String)
}
