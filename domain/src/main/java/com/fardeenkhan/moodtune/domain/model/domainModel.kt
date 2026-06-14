package com.fardeenkhan.moodtune.domain.model

data class Song(
    val id: String,              // unique (can be generated)
    val title: String,
    val artist: String,
    val reason: String,          // why AI selected it
    val imageUrl: String?,       // from Spotify/iTunes
    val externalUrl: String?,     // YouTube or Spotify link
    val mood: String?,
    val energy: String?,
    val explanation: SongExplanation

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