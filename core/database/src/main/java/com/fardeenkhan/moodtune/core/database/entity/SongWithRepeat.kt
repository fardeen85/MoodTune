package com.fardeenkhan.moodtune.core.database.entity

import androidx.room.Embedded

/**
 * A [SongEntity] decorated with its `repeatCount` from `playlist_song_cross_ref` for whichever
 * playlist it was just joined against - the same raw-query shape `PlaylistDao`'s other queries
 * already use, just carrying one more cross-ref column through to the caller.
 */
data class SongWithRepeat(
    @Embedded val song: SongEntity,
    val repeatCount: Int
)
