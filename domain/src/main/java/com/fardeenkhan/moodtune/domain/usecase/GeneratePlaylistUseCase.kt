package com.fardeenkhan.moodtune.domain.usecase

import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.repo.PlaylistRepository
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import java.util.UUID

class GeneratePlaylistUseCase(
    private val playlistRepository: PlaylistRepository,
    private val songsRepository: SongsRepository
) {
    sealed class Result {
        object Success : Result()
        object AlreadyExists : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(mood: String): Result {
        return try {
            // 1. Check if playlist exists
            val existing = playlistRepository.getPlaylistByMood(mood)
            if (existing != null) {
                return Result.AlreadyExists
            }

            // 2. Fetch songs using Gemini and YouTube
            val selectedSongs = songsRepository.generateMoodSongs(mood)

            // 3. Create and save playlist
            val playlist = Playlist(
                id = UUID.randomUUID().toString(),
                mood = mood,
                songs = selectedSongs,
                createdAt = System.currentTimeMillis()
            )
            playlistRepository.savePlaylist(playlist)

            Result.Success
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }
}
