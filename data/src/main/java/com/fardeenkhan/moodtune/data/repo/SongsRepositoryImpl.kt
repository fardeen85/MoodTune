package com.fardeenkhan.moodtune.data.repo

import com.fardeenkhan.moodtune.core.database.dao.SongDao
import com.fardeenkhan.moodtune.core.database.mapper.toDomain
import com.fardeenkhan.moodtune.core.database.mapper.toEntity
import com.fardeenkhan.moodtune.domain.model.LyricLine
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    override fun getMostPlayedSongs(): Flow<List<Song>> {
        return songDao.getMostPlayedSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun incrementSongPlayCount(id: String) {
        songDao.incrementSongPlayCount(id, System.currentTimeMillis())
    }

    override suspend fun saveSongs(songs: List<Song>) {
        val entities = songs.map { song ->
            song.toEntity().copy(lyrics = songDao.getLyricsForSong(song.id))
        }
        songDao.insertSongs(entities)
    }

    override suspend fun deleteSong(id: String) {
        songDao.deleteSongById(id)
    }

    override suspend fun getCachedLyrics(songId: String): List<LyricLine>? {
        val json = songDao.getLyricsForSong(songId) ?: return null
        return Json.decodeFromString<List<LyricLine>>(json)
    }

    override suspend fun generateLyrics(songId: String, title: String, artist: String, durationMs: Long, movie: String?): List<LyricLine> {
        val lines = geminiAPIDataSource.getLyrics(title, artist, durationMs, movie).map { LyricLine(it.ms, it.line) }
        songDao.updateLyrics(songId, Json.encodeToString(lines))
        return lines
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
