package com.fardeenkhan.moodtune.domain.repo

import com.fardeenkhan.moodtune.domain.model.PlaybackSnapshot

/** Persists the last-known playback queue/position so it can survive process death. */
interface PlaybackSessionRepository {
    suspend fun saveSnapshot(snapshot: PlaybackSnapshot)
    suspend fun getSnapshot(): PlaybackSnapshot?
    suspend fun clearSnapshot()
}
