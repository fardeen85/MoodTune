package com.fardeenkhan.moodtune.domain.repo

import com.fardeenkhan.moodtune.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface SongsRepository {
    fun getDeviceFile(): Flow<List<Song>>

    fun getSavedSongs(): Flow<List<Song>>
    suspend fun saveSongs(songs: List<Song>)
    suspend fun deleteSong(id: String)
    suspend fun generateMoodSongs(mood: String): List<Song>
}