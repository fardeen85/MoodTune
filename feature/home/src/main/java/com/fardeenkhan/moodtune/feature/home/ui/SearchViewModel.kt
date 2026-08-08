package com.fardeenkhan.moodtune.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fardeenkhan.moodtune.core.utils.MusicPlayerManager
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class SearchViewModel(
    private val songsRepository: SongsRepository,
    private val musicPlayerManager: MusicPlayerManager
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    init {
        songsRepository.getSavedSongs()
            .onEach { songs -> _state.update { it.copy(allSongs = songs) } }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.OnQueryChange -> {
                _state.update { it.copy(query = intent.query) }
            }
            is SearchIntent.PlaySong -> {
                musicPlayerManager.playPlaylist(listOf(intent.song))
            }
        }
    }
}

data class SearchState(
    val query: String = "",
    val allSongs: List<Song> = emptyList()
) {
    val results: List<Song>
        get() = if (query.isBlank()) emptyList()
        else allSongs.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
}

sealed class SearchIntent {
    data class OnQueryChange(val query: String) : SearchIntent()
    data class PlaySong(val song: Song) : SearchIntent()
}
