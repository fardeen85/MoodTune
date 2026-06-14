package com.fardeenkhan.moodtune.data.repo

import com.fardeenkhan.moodtune.core.database.dao.PlaylistDao
import com.fardeenkhan.moodtune.core.database.dao.SongDao
import com.fardeenkhan.moodtune.core.database.entity.PlaylistSongCrossRef
import com.fardeenkhan.moodtune.core.database.mapper.toDomain
import com.fardeenkhan.moodtune.core.database.mapper.toDomainList
import com.fardeenkhan.moodtune.core.database.mapper.toDomainSingle
import com.fardeenkhan.moodtune.core.database.mapper.toEntity
import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.repo.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) : PlaylistRepository {

    override fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getPlaylistsWithSongs().map { it.toDomainList() }
    }

    override fun getRecentlyPlayedPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getRecentlyPlayedPlaylists().map { it.toDomainList() }
    }

    override suspend fun markPlaylistAsPlayed(id: String) {
        playlistDao.updateLastPlayedAt(id, System.currentTimeMillis())
    }

    override fun getPlaylistById(id: String): Flow<Playlist?> {
        return playlistDao.getPlaylistWithSongsById(id).map { it.toDomainSingle() }
    }

    override fun getPlaylistByMoodFlow(mood: String): Flow<Playlist?> {
        return playlistDao.getPlaylistByMoodFlow(mood).map { it.toDomainSingle() }
    }

    override suspend fun getPlaylistByMood(mood: String): Playlist? {
        return playlistDao.getPlaylistByMood(mood).toDomainSingle()
    }

    override suspend fun savePlaylist(playlist: Playlist) {
        // 1. Save all songs in the playlist first (due to FK or just to ensure they exist)
        songDao.insertSongs(playlist.songs.map { it.toEntity() })
        
        // 2. Save the playlist itself
        playlistDao.insertPlaylist(playlist.toEntity())
        
        // 3. Save the cross references
        // First clean up old refs if updating
        playlistDao.deletePlaylistSongCrossRefsByPlaylistId(playlist.id)
        
        val crossRefs = playlist.songs.mapIndexed { index, song ->
            PlaylistSongCrossRef(playlistId = playlist.id, songId = song.id, position = index)
        }
        playlistDao.insertPlaylistSongCrossRefs(crossRefs)
    }

    override suspend fun deletePlaylist(id: String) {
        playlistDao.deletePlaylistById(id)
        playlistDao.deletePlaylistSongCrossRefsByPlaylistId(id)
    }
}
