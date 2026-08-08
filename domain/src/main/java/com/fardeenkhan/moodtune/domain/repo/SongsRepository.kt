package com.fardeenkhan.moodtune.domain.repo

import com.fardeenkhan.moodtune.domain.model.LyricLine
import com.fardeenkhan.moodtune.domain.model.Song
import kotlinx.coroutines.flow.Flow

/** Songs from both sources: on-device files and AI/YouTube-backed recommendations. */
interface SongsRepository {
    /** Scans the device's media store for local audio files. */
    fun getDeviceFile(): Flow<List<Song>>

    fun getSavedSongs(): Flow<List<Song>>
    fun getMostPlayedSongs(): Flow<List<Song>>
    suspend fun saveSongs(songs: List<Song>)
    suspend fun deleteSong(id: String)
    suspend fun generateMoodSongs(mood: String): List<Song>
    suspend fun incrementSongPlayCount(id: String)
    suspend fun generateLyrics(songId: String, title: String, artist: String, durationMs: Long, movie: String? = null): List<LyricLine>
    suspend fun getCachedLyrics(songId: String): List<LyricLine>?
}