package com.fardeenkhan.moodtune.domain.repo

import com.fardeenkhan.moodtune.domain.model.LyricLine
import com.fardeenkhan.moodtune.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface SongsRepository {
    fun getDeviceFile(): Flow<List<Song>>

    fun getSavedSongs(): Flow<List<Song>>
    fun getMostPlayedSongs(): Flow<List<Song>>
    suspend fun saveSongs(songs: List<Song>)
    suspend fun deleteSong(id: String)
    suspend fun generateMoodSongs(mood: String): List<Song>
    suspend fun incrementSongPlayCount(id: String)
    suspend fun generateLyrics(songId: String, title: String, artist: String, durationMs: Long): List<LyricLine>
    suspend fun getCachedLyrics(songId: String): List<LyricLine>?
}