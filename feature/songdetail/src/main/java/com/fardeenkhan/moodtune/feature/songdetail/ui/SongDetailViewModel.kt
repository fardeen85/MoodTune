package com.fardeenkhan.moodtune.feature.songdetail.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import androidx.palette.graphics.Palette
import coil3.request.allowHardware
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

    private var lastImageUrl: String? = null

    init {
        state.onEach { playbackState ->
            val song = playbackState.currentSong
            // Prefer the saved local art file; fall back to remote imageUrl
            val artSource = song?.localAlbumArtPath?.let { "file://$it" } ?: song?.imageUrl
            if (artSource != lastImageUrl) {
                lastImageUrl = artSource
                _lyricsState.value = LyricsState.Idle
                if (artSource != null) {
                    extractColorFromUrl(artSource)
                } else {
                    _dominantColor.value = null
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun extractColorFromUrl(url: String) {
        viewModelScope.launch {
            try {
                val imageLoader = ImageLoader(context)
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
                e.printStackTrace()
            }
        }
    }

    private fun generateLyrics() {
        val song = state.value.currentSong ?: return
        val duration = state.value.duration.takeIf { it > 0 }
            ?: song.durationMs?.takeIf { it > 0 }
            ?: 240_000L
        viewModelScope.launch {
            _lyricsState.value = LyricsState.Loading
            runCatching {
                val cached = songsRepository.getCachedLyrics(song.id)
                cached ?: songsRepository.generateLyrics(song.id, song.title, song.artist, duration)
            }.onSuccess { lines ->
                _lyricsState.value = LyricsState.Loaded(lines)
            }.onFailure {
                _lyricsState.value = LyricsState.Error("Could not generate lyrics. Tap retry to try again.")
            }
        }
    }

    fun onIntent(intent: SongDetailIntent) {
        when (intent) {
            is SongDetailIntent.TogglePlayPause -> musicPlayerManager.togglePlayPause()
            is SongDetailIntent.SkipNext -> musicPlayerManager.skipNext()
            is SongDetailIntent.SkipPrevious -> musicPlayerManager.skipPrevious()
            is SongDetailIntent.SeekTo -> musicPlayerManager.seekTo(intent.position)
            is SongDetailIntent.PlayFromQueue -> musicPlayerManager.playPlaylist(currentQueue.value, intent.index)
            is SongDetailIntent.RequestLyrics -> generateLyrics()
            is SongDetailIntent.RetryLyrics -> generateLyrics()
            is SongDetailIntent.DismissLyrics -> _lyricsState.value = LyricsState.Idle
        }
    }
}

sealed class SongDetailIntent {
    object TogglePlayPause : SongDetailIntent()
    object SkipNext : SongDetailIntent()
    object SkipPrevious : SongDetailIntent()
    data class SeekTo(val position: Long) : SongDetailIntent()
    data class PlayFromQueue(val index: Int) : SongDetailIntent()
    object RequestLyrics : SongDetailIntent()
    object RetryLyrics : SongDetailIntent()
    object DismissLyrics : SongDetailIntent()
}

sealed class LyricsState {
    object Idle : LyricsState()
    object Loading : LyricsState()
    data class Loaded(val lines: List<LyricLine>) : LyricsState()
    data class Error(val message: String) : LyricsState()
}
