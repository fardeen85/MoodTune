package com.fardeenkhan.moodtune.domain.usecase

import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.repo.PlaylistRepository
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import java.net.UnknownHostException
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
        } catch (e: UnknownHostException) {
            Result.Error("No internet connection. Check your network and try again.")
        } catch (e: Exception) {
            val message = when {
                e.javaClass.simpleName.contains("Timeout", ignoreCase = true) ||
                e.cause?.javaClass?.simpleName?.contains("Timeout", ignoreCase = true) == true ->
                    "Request timed out. Please try again."
                e.javaClass.simpleName.contains("Serialization", ignoreCase = true) ||
                e.cause?.javaClass?.simpleName?.contains("Serialization", ignoreCase = true) == true ->
                    "Couldn't read the AI response. Please try again."
                e.message?.contains("429") == true || e.message?.contains("quota", ignoreCase = true) == true ->
                    "AI rate limit reached. Please wait a moment and try again."
                else -> "Something went wrong. Please try again."
            }
            Result.Error(message)
        }
    }
}
