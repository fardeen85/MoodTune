package com.fardeenkhan.moodtune.data.repo

import com.fardeenkhan.moodtune.domain.model.PlaybackSnapshot
import com.fardeenkhan.moodtune.domain.repo.PlaybackSessionRepository
import com.fardeenkhan.moodtune.infrastructure.datasource.PlaybackSnapshotDataSource

class PlaybackSessionRepositoryImpl(
    private val playbackSnapshotDataSource: PlaybackSnapshotDataSource
) : PlaybackSessionRepository {

    override suspend fun saveSnapshot(snapshot: PlaybackSnapshot) {
        playbackSnapshotDataSource.save(snapshot)
    }

    override suspend fun getSnapshot(): PlaybackSnapshot? {
        return playbackSnapshotDataSource.get()
    }

    override suspend fun clearSnapshot() {
        playbackSnapshotDataSource.clear()
    }
}
