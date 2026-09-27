package com.fardeenkhan.moodtune.core.wearsync

import kotlinx.serialization.Serializable

/**
 * Data crossing the Wearable Data Layer between the phone and watch, plus the types
 * `implementation/WearPlan.md` §2.1 groups under `core:wearsync`. Kept deliberately minimal -
 * these are wire/UI-state shapes, not the full `domain` models (the watch doesn't need
 * `Song.reason`, `explanation`, or anything Gemini-related).
 */

/** Which device produced a [WearPlaybackSnapshot] - lets a renderer tag its source. */
enum class WearPlaybackSource { PHONE_REMOTE, WATCH_LOCAL }

/**
 * A snapshot of "what's playing right now," synced on real state-change events only (never on a
 * progress tick - see §2.2). Doubles as the watch's own unified render state in
 * `WearPlaybackController` (§2.4), which is why every field has a sensible idle default.
 */
@Serializable
data class WearPlaybackSnapshot(
    val songId: String = "",
    val title: String = "",
    val artist: String = "",
    val artAssetKey: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMsAtSync: Long = 0L,
    val durationMs: Long = 0L,
    val syncedAtElapsedRealtime: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val source: WearPlaybackSource = WearPlaybackSource.PHONE_REMOTE
)

/** Watch -> phone transport/navigation commands, routed to the phone's `MusicPlayerManager`. */
@Serializable
sealed class WearCommand {
    data object PlayPause : WearCommand()
    data object Next : WearCommand()
    data object Previous : WearCommand()
    data class SeekTo(val positionMs: Long) : WearCommand()

    /** Plays an existing, already-created playlist on the phone - no generation involved. */
    data class StartPlaylist(val playlistId: String) : WearCommand()
}

/** One of the phone's existing playlists, as shown in the watch's playlists screen. */
@Serializable
data class WearPlaylistSummary(val playlistId: String, val name: String, val songCount: Int)

/** User-selected subset of songs from one playlist, sent from the phone's "Send to Watch" screen. */
@Serializable
data class WearDownloadRequest(
    val playlistId: String,
    val playlistName: String,
    val songs: List<WearDownloadSong>
)

/** One song within a [WearDownloadRequest]. */
@Serializable
data class WearDownloadSong(
    val songId: String,
    val title: String,
    val artist: String,
    val artAssetKey: String?,
    val fileSizeBytes: Long
)

/** Watch -> phone: current downloaded-storage usage, so the phone's send screen reflects reality. */
@Serializable
data class WearStorageStatus(val usedBytes: Long, val capBytes: Long = WearStorageBudget.CAP_BYTES)

/** Watch -> phone: pushed whenever the watch's own local playback starts/stops (§1.5/§2.6). */
@Serializable
data class WatchActivePlaybackStatus(
    val isActive: Boolean,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val songTitle: String? = null,
    val artist: String? = null
)

/** Phone -> watch: the watch-owns-playback lock's "give me control back" action (§1.5/§2.6). */
@Serializable
sealed class PhoneToWatchCommand {
    data object StopLocalPlayback : PhoneToWatchCommand()
}
