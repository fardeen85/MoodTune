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
    val energy: String?,
    @Embedded(prefix = "explanation_")
    val explanation: SongExplanationEntity
)

data class SongExplanationEntity(
    val about: String,
    val mood: String,
    val context: String
)
