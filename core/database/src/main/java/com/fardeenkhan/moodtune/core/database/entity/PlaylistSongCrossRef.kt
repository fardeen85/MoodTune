package com.fardeenkhan.moodtune.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index(value = ["songId"])]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val position: Int = 0,
    /** How many times this song plays in a row (within this playlist) before advancing. 1 = no repeat. */
    val repeatCount: Int = 1
)
