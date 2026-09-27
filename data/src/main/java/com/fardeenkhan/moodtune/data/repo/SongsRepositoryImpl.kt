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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class SongsRepositoryImpl(
    private val localSongDataSource: LocalSongDataSource,
    private val geminiAPIDataSource: GeminiAPIDataSource,
    private val youtubeAPIDataSource: YouTubeAPIDataSource,
    private val songDao: SongDao
) : SongsRepository {

    /**
     * [LocalSongDataSource.fetchDeviceSongs] is a synchronous MediaStore scan that also decodes
     * and re-compresses embedded album art per file - genuinely slow for a real music library.
     * Without [flowOn], this flow's collector (a ViewModel's `viewModelScope`, i.e.
     * `Dispatchers.Main.immediate`) runs that entire scan directly on the main thread, freezing
     * the UI (including whatever loading spinner is supposed to be animating) for as long as it
     * takes. `flowOn(Dispatchers.IO)` moves the scan itself off Main; downstream mapping in
     * callers stays wherever they collect it, since that part is cheap.
     */
    override fun getDeviceFile(): Flow<List<Song>> = flow {
        emit(localSongDataSource.fetchDeviceSongs())
    }.flowOn(Dispatchers.IO)

    /**
     * Room already runs the query itself off the main thread by default, but the entity->domain
     * mapping right after it runs on whatever dispatcher the caller collects on
     * (`Dispatchers.Main.immediate` for a ViewModel's `viewModelScope`) unless pinned here too.
     * `flowOn(Dispatchers.IO)` keeps the whole chain - query and mapping - off Main, consistent
     * with [getDeviceFile] above, rather than leaving that boundary implicit.
     */
    override fun getSavedSongs(): Flow<List<Song>> {
        return songDao.getAllSongs()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    /** See [getSavedSongs] - same reasoning. */
    override fun getMostPlayedSongs(): Flow<List<Song>> {
        return songDao.getMostPlayedSongs()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        return songDao.getSongsByIds(ids).map { it.toDomain() }
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
        // Same main-thread-blocking scan as getDeviceFile() above - see its KDoc.
        val deviceSongs = withContext(Dispatchers.IO) { localSongDataSource.fetchDeviceSongs() }

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
