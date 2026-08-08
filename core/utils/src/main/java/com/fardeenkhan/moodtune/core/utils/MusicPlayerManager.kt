package com.fardeenkhan.moodtune.core.utils

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "MusicPlayerManager"

/**
 * App-wide wrapper around a [MediaController] connected to `MediaPlaybackService`
 * (infrastructure module). Owns the single source of truth for playback UI state
 * ([playbackState]) and the currently loaded queue ([currentQueue]).
 *
 * core/utils cannot depend on the infrastructure module (that would create a dependency
 * cycle, since infrastructure depends on core/utils), so the service is targeted by its
 * fully-qualified class name string in [SessionToken] rather than `MediaPlaybackService::class`.
 */
class MusicPlayerManager(
    context: Context,
    private val songsRepository: SongsRepository
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private var currentPlaylistSongs: List<Song> = emptyList()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, "com.fardeenkhan.moodtune.infrastructure.MediaPlaybackService"))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupController()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupController() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateState()
                mediaItem?.mediaId?.let { songId ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            songsRepository.incrementSongPlayCount(songId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to increment play count for $songId", e)
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                updateState()
                if (state == Player.STATE_ENDED) {
                    _playbackState.value = PlaybackState()
                }
            }
        })
        updateState()
    }

    private fun updateState() {
        val controller = mediaController ?: return
        val currentMediaId = controller.currentMediaItem?.mediaId
        val fullSong = currentPlaylistSongs.find { it.id == currentMediaId }

        _playbackState.value = PlaybackState(
            currentSong = fullSong ?: controller.currentMediaItem?.let { item ->
                Song(
                    id = item.mediaId,
                    title = item.mediaMetadata.title?.toString() ?: "Unknown",
                    artist = item.mediaMetadata.artist?.toString() ?: "Unknown",
                    reason = "",
                    imageUrl = item.mediaMetadata.artworkUri?.toString(),
                    externalUrl = item.localConfiguration?.uri?.toString(),
                    mood = null,
                    explanation = SongExplanation(item.mediaId, "", "", "")
                )
            },
            isPlaying = controller.isPlaying,
            isLoading = controller.playbackState == Player.STATE_BUFFERING,
            progress = controller.currentPosition,
            duration = controller.duration.coerceAtLeast(0L),
            hasNext = controller.hasNextMediaItem(),
            hasPrevious = controller.hasPreviousMediaItem(),
            isShuffleEnabled = controller.shuffleModeEnabled
        )
    }

    /** Polls [updateState] every second so [playbackState].progress advances during playback. */
    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                updateState()
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int = 0) {
        val controller = mediaController ?: return
        currentPlaylistSongs = songs
        _currentQueue.value = songs
        val mediaItems = songs.map { song ->
            // Locally embedded album art (extracted once, cached on disk) takes priority over
            // the remote imageUrl so on-device songs render art without a network round-trip.
            val artworkUri = song.localAlbumArtPath?.let { Uri.parse("file://$it") }
                ?: song.imageUrl?.let { Uri.parse(it) }
            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(Uri.parse(song.externalUrl ?: ""))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setArtworkUri(artworkUri)
                        .build()
                )
                .build()
        }
        controller.setMediaItems(mediaItems)
        controller.prepare()
        controller.seekTo(startIndex, 0L)
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipNext() {
        mediaController?.seekToNext()
    }

    fun skipPrevious() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        updateState()
    }

    fun release() {
        stopProgressUpdate()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controllerFuture = null
        mediaController = null
        _playbackState.value = PlaybackState()
    }
}

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isShuffleEnabled: Boolean = false
)
