package com.fardeenkhan.moodtune.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val mood: String,
    val createdAt: Long,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0
)
