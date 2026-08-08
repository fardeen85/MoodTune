package com.fardeenkhan.moodtune.feature.songdetail.ui

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.fardeenkhan.moodtune.core.utils.MusicPlayerManager
import com.fardeenkhan.moodtune.core.utils.PlaybackState
import com.fardeenkhan.moodtune.domain.model.LyricLine
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SongDetailViewModel"

class SongDetailViewModel(
    private val context: Context,
    private val musicPlayerManager: MusicPlayerManager,
    private val songsRepository: SongsRepository
) : ViewModel() {

    val state: StateFlow<PlaybackState> = musicPlayerManager.playbackState
    val currentQueue: StateFlow<List<Song>> = musicPlayerManager.currentQueue

    private val _dominantColor = MutableStateFlow<Color?>(null)
    val dominantColor: StateFlow<Color?> = _dominantColor.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    private val _hasCachedLyrics = MutableStateFlow(false)
    val hasCachedLyrics: StateFlow<Boolean> = _hasCachedLyrics.asStateFlow()

    private var lastImageUrl: String? = null
    private var lastLyricsQuery: LyricsQuery? = null

    init {
        state.onEach { playbackState ->
            val song = playbackState.currentSong
            // Prefer the saved local art file; fall back to remote imageUrl
            val artSource = song?.localAlbumArtPath?.let { "file://$it" } ?: song?.imageUrl
            if (artSource != lastImageUrl) {
                lastImageUrl = artSource
                _lyricsState.value = LyricsState.Idle
                lastLyricsQuery = null
                _hasCachedLyrics.value = false
                if (artSource != null) {
                    extractColorFromUrl(artSource)
                } else {
                    _dominantColor.value = null
                }
                if (song != null) {
                    viewModelScope.launch {
                        _hasCachedLyrics.value = songsRepository.getCachedLyrics(song.id) != null
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun extractColorFromUrl(url: String) {
        viewModelScope.launch {
            try {
                // The app-wide singleton loader (registered by MoodTuneApp) rather than a raw
                // ImageLoader(context) - a fresh instance would skip the disk/memory cache and,
                // for on-device songs whose art comes from a MediaStore content:// URI, would
                // also miss AlbumArtFetcher, the custom component that falls back to the file's
                // embedded art when the MediaStore albumart cache is stale or empty.
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    withContext(Dispatchers.Default) {
                        val palette = Palette.from(bitmap).generate()
                        val colorInt = palette.getVibrantColor(
                            palette.getDominantColor(0)
                        )
                        if (colorInt != 0) {
                            _dominantColor.value = Color(colorInt)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract dominant color from $url", e)
            }
        }
    }

    private fun generateLyrics(title: String, artist: String, movie: String?) {
        val song = state.value.currentSong ?: return
        val duration = state.value.duration.takeIf { it > 0 }
            ?: song.durationMs?.takeIf { it > 0 }
            ?: 240_000L // ~4 min fallback when neither the player nor the song metadata knows the real duration
        lastLyricsQuery = LyricsQuery(title, artist, movie)
        viewModelScope.launch {
            _lyricsState.value = LyricsState.Loading
            runCatching {
                val cached = songsRepository.getCachedLyrics(song.id)
                cached ?: songsRepository.generateLyrics(song.id, title, artist, duration, movie)
            }.onSuccess { lines ->
                _lyricsState.value = LyricsState.Loaded(lines)
                _hasCachedLyrics.value = true
            }.onFailure {
                _lyricsState.value = LyricsState.Error("Could not generate lyrics. Tap retry to try again.")
            }
        }
    }

    private fun retryLyrics() {
        val query = lastLyricsQuery ?: return
        generateLyrics(query.title, query.artist, query.movie)
    }

    fun onIntent(intent: SongDetailIntent) {
        when (intent) {
            is SongDetailIntent.TogglePlayPause -> musicPlayerManager.togglePlayPause()
            is SongDetailIntent.SkipNext -> musicPlayerManager.skipNext()
            is SongDetailIntent.SkipPrevious -> musicPlayerManager.skipPrevious()
            is SongDetailIntent.SeekTo -> musicPlayerManager.seekTo(intent.position)
            is SongDetailIntent.PlayFromQueue -> musicPlayerManager.playPlaylist(currentQueue.value, intent.index)
            is SongDetailIntent.RequestLyrics -> generateLyrics(intent.title, intent.artist, intent.movie)
            is SongDetailIntent.RetryLyrics -> retryLyrics()
            is SongDetailIntent.DismissLyrics -> _lyricsState.value = LyricsState.Idle
        }
    }
}

private data class LyricsQuery(val title: String, val artist: String, val movie: String?)

sealed class SongDetailIntent {
    data object TogglePlayPause : SongDetailIntent()
    data object SkipNext : SongDetailIntent()
    data object SkipPrevious : SongDetailIntent()
    data class SeekTo(val position: Long) : SongDetailIntent()
    data class PlayFromQueue(val index: Int) : SongDetailIntent()
    data class RequestLyrics(val title: String, val artist: String, val movie: String? = null) : SongDetailIntent()
    data object RetryLyrics : SongDetailIntent()
    data object DismissLyrics : SongDetailIntent()
}

sealed class LyricsState {
    data object Idle : LyricsState()
    data object Loading : LyricsState()
    data class Loaded(val lines: List<LyricLine>) : LyricsState()
    data class Error(val message: String) : LyricsState()
}
