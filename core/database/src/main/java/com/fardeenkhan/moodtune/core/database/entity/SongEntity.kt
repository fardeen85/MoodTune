package com.fardeenkhan.moodtune.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val reason: String,
    val imageUrl: String?,
    val externalUrl: String?,
    val mood: String?,
    @Embedded(prefix = "explanation_")
    val explanation: SongExplanationEntity,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val durationMs: Long? = null,
    val lyrics: String? = null,
    val localAlbumArtPath: String? = null
)

data class SongExplanationEntity(
    val about: String,
    val mood: String,
    val context: String
)
