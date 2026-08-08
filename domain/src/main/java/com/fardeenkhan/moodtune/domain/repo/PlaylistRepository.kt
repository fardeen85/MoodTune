package com.fardeenkhan.moodtune.domain.repo

import com.fardeenkhan.moodtune.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

/** Persisted playlists (Room-backed). Flow-returning methods stay live-updated; suspend methods are one-shot reads/writes. */
interface PlaylistRepository {
    fun getPlaylists(): Flow<List<Playlist>>
    fun getRecentlyPlayedPlaylists(): Flow<List<Playlist>>
    fun getMostPlayedPlaylists(): Flow<List<Playlist>>
    fun getPlaylistById(id: String): Flow<Playlist?>
    fun getPlaylistByMoodFlow(mood: String): Flow<Playlist?>
    suspend fun markPlaylistAsPlayed(id: String)
    suspend fun getPlaylistByMood(mood: String): Playlist?
    suspend fun savePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(id: String)
}
