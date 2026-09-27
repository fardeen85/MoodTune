package com.fardeenkhan.moodtune.domain.model

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Minimal persisted record of what was playing, so a cold-started process (after the whole app
 * was killed while `MediaPlaybackService` was playing) has something to rebuild the queue from.
 */
@Serializable
data class PlaybackSnapshot(
    val songs: List<SnapshotSong>,
    val currentIndex: Int,
    val positionMs: Long,
    val isPlaying: Boolean
)

/**
 * One queue entry as it was actually playing - [repeatCount] rides along here (rather than being
 * re-resolved from the DB on restore) since it's per-playlist and the snapshot has no playlistId
 * to look it up by; this way restore just re-arms the full count fresh, no partial-progress
 * tracking needed.
 */
@Serializable
data class SnapshotSong(
    val songId: String,
    val repeatCount: Int = 1
)
