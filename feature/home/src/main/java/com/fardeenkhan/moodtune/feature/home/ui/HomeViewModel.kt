package com.fardeenkhan.moodtune.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.repo.PlaylistRepository
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import com.fardeenkhan.moodtune.domain.usecase.GeneratePlaylistUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class HomeViewModel(
    private val generatePlaylistUseCase: GeneratePlaylistUseCase,
    private val playlistRepository: PlaylistRepository,
    private val songsRepository: SongsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        loadRecentlyPlayed()
        loadMostPlayed()
        loadAllPlaylists()
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.OnVibeInputChange -> {
                _state.update { it.copy(vibeInput = intent.newValue) }
            }
            is HomeIntent.RequestPlaylistGeneration -> {
                val targetMood = intent.mood ?: _state.value.vibeInput
                if (targetMood.isBlank()) return
                _state.update {
                    it.copy(
                        pendingMood = targetMood,
                        showConfirmationDialog = true
                    )
                }
            }
            is HomeIntent.ConfirmPlaylistGeneration -> {
                val mood = _state.value.pendingMood ?: return
                _state.update {
                    it.copy(
                        showConfirmationDialog = false,
                        pendingMood = null
                    )
                }
                generatePlaylist(mood)
            }
            is HomeIntent.CreateEmptyPlaylist -> {
                val mood = _state.value.pendingMood ?: return
                _state.update {
                    it.copy(
                        showConfirmationDialog = false,
                        pendingMood = null
                    )
                }
                createEmptyPlaylist(mood)
            }
            is HomeIntent.DismissConfirmationDialog -> {
                _state.update {
                    it.copy(
                        showConfirmationDialog = false,
                        pendingMood = null
                    )
                }
            }
            is HomeIntent.MarkPlaylistAsPlayed -> {
                viewModelScope.launch {
                    playlistRepository.markPlaylistAsPlayed(intent.playlistId)
                }
            }
            is HomeIntent.ResetState -> {
                _state.update { it.copy(generationStatus = HomeGenerationStatus.Idle) }
            }
            is HomeIntent.RetryGeneration -> {
                val mood = _state.value.lastAttemptedMood ?: return
                generatePlaylist(mood)
            }
        }
    }

    private fun loadRecentlyPlayed() {
        _state.update { it.copy(isLoadingRecentlyPlayed = true) }
        playlistRepository.getRecentlyPlayedPlaylists()
            .onEach { playlists ->
                _state.update { it.copy(
                    recentlyPlayedPlaylists = playlists,
                    isLoadingRecentlyPlayed = false
                ) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadMostPlayed() {
        _state.update { it.copy(isLoadingMostPlayed = true) }
        playlistRepository.getMostPlayedPlaylists()
            .onEach { playlists ->
                _state.update { it.copy(
                    mostPlayedPlaylists = playlists,
                    isLoadingMostPlayed = false
                ) }
            }
            .launchIn(viewModelScope)

        songsRepository.getMostPlayedSongs()
            .onEach { songs ->
                _state.update { it.copy(
                    mostPlayedSongs = songs
                ) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadAllPlaylists() {
        playlistRepository.getPlaylists()
            .onEach { playlists ->
                _state.update { it.copy(allPlaylists = playlists) }
            }
            .launchIn(viewModelScope)
    }

    private fun generatePlaylist(mood: String) {
        viewModelScope.launch {
            _state.update { it.copy(generationStatus = HomeGenerationStatus.Loading, lastAttemptedMood = mood) }
            val result = generatePlaylistUseCase(mood)
            _state.update {
                it.copy(
                    generatedMood = if (result is GeneratePlaylistUseCase.Result.Success) mood else null,
                    generationStatus = when (result) {
                        is GeneratePlaylistUseCase.Result.Success -> HomeGenerationStatus.Success
                        is GeneratePlaylistUseCase.Result.AlreadyExists -> HomeGenerationStatus.AlreadyCreated
                        is GeneratePlaylistUseCase.Result.Error -> HomeGenerationStatus.Error(result.message)
                    }
                )
            }
        }
    }

    private fun createEmptyPlaylist(mood: String) {
        viewModelScope.launch {
            _state.update { it.copy(generationStatus = HomeGenerationStatus.Loading) }
            val existing = playlistRepository.getPlaylistByMood(mood)
            if (existing != null) {
                _state.update { it.copy(generationStatus = HomeGenerationStatus.AlreadyCreated) }
                return@launch
            }

            val newPlaylist = Playlist(
                id = UUID.randomUUID().toString(),
                mood = mood,
                songs = emptyList(),
                createdAt = System.currentTimeMillis()
            )
            playlistRepository.savePlaylist(newPlaylist)
            _state.update {
                it.copy(
                    generatedMood = mood,
                    generationStatus = HomeGenerationStatus.Success
                )
            }
        }
    }
}

data class HomeState(
    val vibeInput: String = "",
    val recentlyPlayedPlaylists: List<Playlist> = emptyList(),
    val isLoadingRecentlyPlayed: Boolean = false,
    val mostPlayedPlaylists: List<Playlist> = emptyList(),
    val mostPlayedSongs: List<Song> = emptyList(),
    val allPlaylists: List<Playlist> = emptyList(),
    val isLoadingMostPlayed: Boolean = false,
    val showConfirmationDialog: Boolean = false,
    val pendingMood: String? = null,
    val generatedMood: String? = null,
    val generationStatus: HomeGenerationStatus = HomeGenerationStatus.Idle,
    val lastAttemptedMood: String? = null
)

sealed class HomeGenerationStatus {
    object Idle : HomeGenerationStatus()
    object Loading : HomeGenerationStatus()
    object Success : HomeGenerationStatus()
    object AlreadyCreated : HomeGenerationStatus()
    data class Error(val message: String) : HomeGenerationStatus()
}

sealed class HomeIntent {
    data class OnVibeInputChange(val newValue: String) : HomeIntent()
    data class RequestPlaylistGeneration(val mood: String? = null) : HomeIntent()
    object ConfirmPlaylistGeneration : HomeIntent()
    object CreateEmptyPlaylist : HomeIntent()
    object DismissConfirmationDialog : HomeIntent()
    data class MarkPlaylistAsPlayed(val playlistId: String) : HomeIntent()
    object ResetState : HomeIntent()
    object RetryGeneration : HomeIntent()
}
