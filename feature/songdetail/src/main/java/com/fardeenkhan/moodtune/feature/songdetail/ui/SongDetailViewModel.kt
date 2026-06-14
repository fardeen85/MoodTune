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
import com.fardeenkhan.moodtune.domain.model.Song
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
    private val musicPlayerManager: MusicPlayerManager
) : ViewModel() {

    val state: StateFlow<PlaybackState> = musicPlayerManager.playbackState
    val currentQueue: StateFlow<List<Song>> = musicPlayerManager.currentQueue

    private val _dominantColor = MutableStateFlow<Color?>(null)
    val dominantColor: StateFlow<Color?> = _dominantColor.asStateFlow()

    private var lastImageUrl: String? = null

    init {
        state.onEach { playbackState ->
            val url = playbackState.currentSong?.imageUrl
            if (url != lastImageUrl) {
                lastImageUrl = url
                if (url != null) {
                    extractColorFromUrl(url)
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
                    .allowHardware(false) // Required for Palette
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

    fun onIntent(intent: SongDetailIntent) {
        when (intent) {
            is SongDetailIntent.TogglePlayPause -> musicPlayerManager.togglePlayPause()
            is SongDetailIntent.SkipNext -> musicPlayerManager.skipNext()
            is SongDetailIntent.SkipPrevious -> musicPlayerManager.skipPrevious()
            is SongDetailIntent.SeekTo -> musicPlayerManager.seekTo(intent.position)
            is SongDetailIntent.PlayFromQueue -> musicPlayerManager.playPlaylist(currentQueue.value, intent.index)
        }
    }
}

sealed class SongDetailIntent {
    object TogglePlayPause : SongDetailIntent()
    object SkipNext : SongDetailIntent()
    object SkipPrevious : SongDetailIntent()
    data class SeekTo(val position: Long) : SongDetailIntent()
    data class PlayFromQueue(val index: Int) : SongDetailIntent()
}
