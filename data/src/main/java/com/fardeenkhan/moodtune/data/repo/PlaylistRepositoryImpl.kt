package com.fardeenkhan.moodtune.data.repo

import androidx.room.withTransaction
import com.fardeenkhan.moodtune.core.database.MoodTuneDatabase
import com.fardeenkhan.moodtune.core.database.dao.PlaylistDao
import com.fardeenkhan.moodtune.core.database.dao.SongDao
import com.fardeenkhan.moodtune.core.database.entity.PlaylistSongCrossRef
import com.fardeenkhan.moodtune.core.database.mapper.toDomainList
import com.fardeenkhan.moodtune.core.database.mapper.toDomainSingle
import com.fardeenkhan.moodtune.core.database.mapper.toEntity
import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.repo.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlaylistRepositoryImpl(
    private val db: MoodTuneDatabase,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) : PlaylistRepository {

    // Serializes savePlaylist/deletePlaylist so concurrent calls (e.g. two quick RemoveSong
    // taps) commit their DB transactions in launch order instead of racing. Each call always
    // captures the freshest in-memory state right before launching, so as long as writes land
    // in that same order, whichever one commits last is guaranteed to already be the correct
    // final state - without this, a slower-finishing but earlier (now-stale) write could land
    // after a newer one and silently resurrect data the newer write just removed.
    private val writeMutex = Mutex()

    override fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getPlaylistsWithSongs().map { it.toDomainList() }
    }

    override fun getRecentlyPlayedPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getRecentlyPlayedPlaylists().map { it.toDomainList() }
    }

    override fun getMostPlayedPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getMostPlayedPlaylists().map { it.toDomainList() }
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

    // Wrapped in a single DB transaction: this is 4 separate writes (songs, playlist row,
    // delete old cross-refs, insert new cross-refs). Without a transaction, a process death
    // between the delete and re-insert of cross-refs would leave the playlist row committed
    // but with zero songs linked to it - a real risk since this also runs on every drag-drop
    // reorder, not just an explicit save.
    override suspend fun savePlaylist(playlist: Playlist) = writeMutex.withLock {
        db.withTransaction {
            val existingPlaylist = playlistDao.getPlaylistEntityById(playlist.id)
            val playCount = existingPlaylist?.playCount ?: 0
            val lastPlayedAt = playlist.lastPlayedAt ?: existingPlaylist?.lastPlayedAt

            // 1. Save all songs in the playlist first (due to FK or just to ensure they exist)
            val songEntities = playlist.songs.map { song ->
                val existingSong = songDao.getSongById(song.id)
                song.toEntity().copy(
                    playCount = existingSong?.playCount ?: 0,
                    lastPlayedAt = existingSong?.lastPlayedAt
                )
            }
            songDao.insertSongs(songEntities)

            // 2. Save the playlist itself
            playlistDao.insertPlaylist(playlist.toEntity().copy(
                playCount = playCount,
                lastPlayedAt = lastPlayedAt
            ))

            // 3. Save the cross references
            // First clean up old refs if updating
            playlistDao.deletePlaylistSongCrossRefsByPlaylistId(playlist.id)

            val crossRefs = playlist.songs.mapIndexed { index, song ->
                PlaylistSongCrossRef(playlistId = playlist.id, songId = song.id, position = index)
            }
            playlistDao.insertPlaylistSongCrossRefs(crossRefs)
        }
    }

    override suspend fun deletePlaylist(id: String) = writeMutex.withLock {
        db.withTransaction {
            playlistDao.deletePlaylistById(id)
            playlistDao.deletePlaylistSongCrossRefsByPlaylistId(id)
        }
    }
}
