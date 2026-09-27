package com.fardeenkhan.moodtune.domain.usecase

import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.repo.PlaybackSessionRepository
import com.fardeenkhan.moodtune.domain.repo.SongsRepository

/**
 * Rebuilds a playable queue from the last persisted [com.fardeenkhan.moodtune.domain.model.PlaybackSnapshot],
 * for `MediaSession.Callback.onPlaybackResumption()` in a cold-started `MediaPlaybackService`.
 */
class RestorePlaybackSessionUseCase(
    private val playbackSessionRepository: PlaybackSessionRepository,
    private val songsRepository: SongsRepository
) {
    data class RestoredSession(
        val songs: List<Song>,
        val startIndex: Int,
        val startPositionMs: Long,
        val wasPlaying: Boolean
    )

    suspend operator fun invoke(): RestoredSession? {
        val snapshot = playbackSessionRepository.getSnapshot() ?: return null
        if (snapshot.songs.isEmpty()) return null

        val currentSongId = snapshot.songs.getOrNull(snapshot.currentIndex)?.songId
        val songsById = songsRepository.getSongsByIds(snapshot.songs.map { it.songId }).associateBy { it.id }
        // Preserve original queue order; silently drop any song deleted since the snapshot was taken.
        // repeatCount rides in the snapshot itself (see SnapshotSong) rather than being re-resolved
        // from the DB, since a bare songId has no playlist context to look it up by.
        val orderedSongs = snapshot.songs.mapNotNull { entry ->
            songsById[entry.songId]?.copy(repeatCount = entry.repeatCount)
        }

        if (orderedSongs.isEmpty()) {
            playbackSessionRepository.clearSnapshot()
            return null
        }

        val restoredIndex = currentSongId?.let { id -> orderedSongs.indexOfFirst { it.id == id } } ?: -1

        return RestoredSession(
            songs = orderedSongs,
            startIndex = restoredIndex.takeIf { it >= 0 } ?: 0,
            startPositionMs = if (restoredIndex >= 0) snapshot.positionMs else 0L,
            wasPlaying = snapshot.isPlaying
        )
    }
}
