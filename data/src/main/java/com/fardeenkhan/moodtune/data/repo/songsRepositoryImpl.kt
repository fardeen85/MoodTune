package com.fardeenkhan.moodtune.data.repo

import com.fardeenkhan.moodtune.core.database.dao.SongDao
import com.fardeenkhan.moodtune.core.database.mapper.toDomain
import com.fardeenkhan.moodtune.core.database.mapper.toEntity
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import com.fardeenkhan.moodtune.infrastructure.datasource.GeminiAPIDataSource
import com.fardeenkhan.moodtune.infrastructure.datasource.LocalSongDataSource
import com.fardeenkhan.moodtune.infrastructure.datasource.YouTubeAPIDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class SongsRepositoryImpl(
    private val localSongDataSource: LocalSongDataSource,
    private val geminiAPIDataSource: GeminiAPIDataSource,
    private val youtubeAPIDataSource: YouTubeAPIDataSource,
    private val songDao: SongDao
) : SongsRepository {

    override fun getDeviceFile(): Flow<List<Song>> = flow {
        emit(localSongDataSource.fetchDeviceSongs())
    }

    override fun getSavedSongs(): Flow<List<Song>> {
        return songDao.getAllSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveSongs(songs: List<Song>) {
        songDao.insertSongs(songs.map { it.toEntity() })
    }

    override suspend fun deleteSong(id: String) {
        songDao.deleteSongById(id)
    }

    override suspend fun generateMoodSongs(mood: String): List<Song> {
        val recommendedSongs = geminiAPIDataSource.getSongRecommendations(mood)
        val deviceSongs = localSongDataSource.fetchDeviceSongs()

        return recommendedSongs.map { recommended ->
            // Check if song exists on device (case-insensitive title match)
            val localSong = deviceSongs.find { 
                it.title.equals(recommended.song, ignoreCase = true) || 
                it.title.contains(recommended.song, ignoreCase = true)
            }

            if (localSong != null) {
                localSong.copy(
                    reason = "Recommended for $mood",
                    explanation = SongExplanation(
                        songId = localSong.id,
                        about = recommended.about,
                        mood = recommended.mood,
                        context = recommended.context
                    )
                )
            } else {
                // Fetch metadata from YouTube
                val ytVideo = youtubeAPIDataSource.searchSong(recommended.q)
                val songId = UUID.randomUUID().toString()
                Song(
                    id = songId,
                    title = recommended.song,
                    artist = recommended.artist,
                    reason = "Recommended for $mood (${recommended.cat})",
                    imageUrl = ytVideo?.snippet?.thumbnails?.medium?.url ?: ytVideo?.snippet?.thumbnails?.high?.url,
                    externalUrl = ytVideo?.id?.videoId?.let { "https://www.youtube.com/watch?v=$it" },
                    mood = mood,
                    energy = null,
                    explanation = SongExplanation(
                        songId = songId,
                        about = recommended.about,
                        mood = recommended.mood,
                        context = recommended.context
                    )
                )
            }
        }
    }
}
