package com.fardeenkhan.moodtune.domain.model

import kotlinx.serialization.Serializable

/**
 * A single track, whether AI-recommended or found on-device.
 *
 * @param reason short human-readable blurb on why the song was picked for its playlist
 * @param imageUrl remote album art (YouTube thumbnail); null for on-device songs, which use
 * [localAlbumArtPath] instead
 * @param externalUrl playback source - a YouTube watch URL for recommended songs, or a local
 * device file path/URI for on-device songs
 * @param mood the mood this song was generated for; null for on-device songs not tied to any
 * AI generation
 * @param localAlbumArtPath path to art extracted from the file's embedded metadata, cached on
 * disk; only set for on-device songs (see the "Embedded album art handling" notes)
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val reason: String,
    val imageUrl: String?,
    val externalUrl: String?,
    val mood: String?,
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

/** AI-generated blurb on why [Song] with id [songId] fits its playlist's mood. */
data class SongExplanation(
    val songId: String,
    val about: String,
    val mood: String,
    val context: String
)

/** One synced lyric line: [line] of text starting at [ms] milliseconds into playback. */
@Serializable
data class LyricLine(val ms: Long, val line: String)