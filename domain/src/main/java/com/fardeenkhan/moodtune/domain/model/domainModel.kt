package com.fardeenkhan.moodtune.domain.model

import kotlinx.serialization.Serializable

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val reason: String,
    val imageUrl: String?,
    val externalUrl: String?,
    val mood: String?,
    val energy: String?,
    val explanation: SongExplanation,
    val durationMs: Long? = null,
    val localAlbumArtPath: String? = null
)

data class Playlist(
    val id: String,
    val mood: String,
    val songs: List<Song>,
    val createdAt: Long,
    val lastPlayedAt: Long? = null,
)


data class SongExplanation(
    val songId: String,
    val about: String,
    val mood: String,
    val context: String
)

@Serializable
data class LyricLine(val ms: Long, val line: String)