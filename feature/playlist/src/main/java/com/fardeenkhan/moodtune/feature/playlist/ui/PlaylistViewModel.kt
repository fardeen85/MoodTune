package com.fardeenkhan.moodtune.feature.playlist.ui

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fardeenkhan.moodtune.core.utils.MusicPlayerManager
import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.repo.PlaylistRepository
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import com.fardeenkhan.moodtune.domain.usecase.GetDeviceSongsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DeviceFile(
    val id: String,
    val name: String,
    val path: String,
    val imageUrl: Uri? = null,
    val localAlbumArtPath: String? = null,
    val isSelected: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModel(
    private val getDeviceSongsUseCase: GetDeviceSongsUseCase,
    private val playlistRepository: PlaylistRepository,
    private val songsRepository: SongsRepository,
    private val musicPlayerManager: MusicPlayerManager
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistState())
    val state: StateFlow<PlaylistState> = _state.asStateFlow()

    init {
        loadPlaylists()
        observeCurrentPlaylist()
    }

    fun onIntent(intent: PlaylistIntent) {
        when (intent) {
            is PlaylistIntent.SelectPlaylist -> {
                _state.update { it.copy(currentMood = intent.mood) }
            }
            is PlaylistIntent.MarkPlaylistAsPlayed -> {
                viewModelScope.launch {
                    playlistRepository.markPlaylistAsPlayed(intent.playlistId)
                }
            }
            is PlaylistIntent.LoadDeviceSongs -> {
                loadDeviceSongs()
            }
            is PlaylistIntent.ToggleFileSelection -> {
                _state.update { state ->
                    state.copy(
                        deviceFiles = state.deviceFiles.map {
                            if (it.id == intent.fileId) it.copy(isSelected = !it.isSelected) else it
                        }
                    )
                }
            }
            is PlaylistIntent.AddSelectedSongsToPlaylist -> {
                addSelectedSongsToPlaylist(intent.mood)
            }
            is PlaylistIntent.PlayPlaylist -> {
                val songs = _state.value.currentPlaylist?.songs ?: return
                musicPlayerManager.playPlaylist(songs, intent.startIndex)
            }
            is PlaylistIntent.ReorderSongs -> {
                reorderSongs(intent.fromIndex, intent.toIndex)
            }
            is PlaylistIntent.ToggleReorderMode -> {
                val isCurrentlyReordering = _state.value.isReorderMode
                if (isCurrentlyReordering) {
                    // We are turning OFF reorder mode, save the final state to DB
                    _state.update { it.copy(isSaving = true) }
                    _state.value.currentPlaylist?.let { playlist ->
                        viewModelScope.launch {
                            playlistRepository.savePlaylist(playlist)
                            _state.update { it.copy(isSaving = false, isReorderMode = false) }
                        }
                    }
                } else {
                    _state.update { it.copy(isReorderMode = true) }
                }
            }
            is PlaylistIntent.StartDrag -> {
                _state.update { it.copy(draggedSongId = intent.songId) }
            }
            is PlaylistIntent.EndDrag -> {
                _state.update { it.copy(draggedSongId = null) }
            }
            is PlaylistIntent.DeletePlaylist -> {
                viewModelScope.launch {
                    playlistRepository.deletePlaylist(intent.playlistId)
                }
            }
            is PlaylistIntent.ToggleShuffle -> {
                musicPlayerManager.toggleShuffle()
                _state.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
            }
            is PlaylistIntent.RemoveSong -> {
                val currentPlaylist = _state.value.currentPlaylist ?: return
                val updatedSongs = currentPlaylist.songs.filter { it.id != intent.songId }
                val updatedPlaylist = currentPlaylist.copy(songs = updatedSongs)
                _state.update { it.copy(currentPlaylist = updatedPlaylist) }
                viewModelScope.launch {
                    playlistRepository.savePlaylist(updatedPlaylist)
                }
            }
            is PlaylistIntent.CreatePlaylist -> {
                createPlaylist(intent.mood)
            }
        }
    }

    private fun createPlaylist(mood: String) {
        viewModelScope.launch {
            val existing = playlistRepository.getPlaylistByMood(mood)
            if (existing == null) {
                val newPlaylist = Playlist(
                    id = UUID.randomUUID().toString(),
                    mood = mood,
                    songs = emptyList(),
                    createdAt = System.currentTimeMillis()
                )
                playlistRepository.savePlaylist(newPlaylist)
            }
        }
    }

    private fun reorderSongs(fromIndex: Int, toIndex: Int) {
        val currentPlaylist = _state.value.currentPlaylist ?: return
        val songs = currentPlaylist.songs.toMutableList()
        if (fromIndex !in songs.indices || toIndex !in songs.indices) return

        val song = songs.removeAt(fromIndex)
        songs.add(toIndex, song)

        val updatedPlaylist = currentPlaylist.copy(songs = songs)
        _state.update { it.copy(currentPlaylist = updatedPlaylist) }
    }

    private fun observeCurrentPlaylist() {
        _state.map { it.currentMood }
            .distinctUntilChanged()
            .flatMapLatest { mood ->
                if (mood != null) {
                    _state.update { it.copy(isLoadingCurrentPlaylist = true) }
                    playlistRepository.getPlaylistByMoodFlow(mood)
                } else {
                    flowOf(null)
                }
            }
            .onEach { playlist ->
                // Only update from DB if we aren't in the middle of reordering or saving
                if (!_state.value.isReorderMode && !_state.value.isSaving) {
                    _state.update { it.copy(
                        currentPlaylist = playlist,
                        isLoadingCurrentPlaylist = false
                    ) }
                } else {
                    _state.update { it.copy(isLoadingCurrentPlaylist = false) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadPlaylists() {
        _state.update { it.copy(isLoadingPlaylists = true) }
        playlistRepository.getPlaylists()
            .onEach { playlists ->
                _state.update { it.copy(
                    playlists = playlists,
                    isLoadingPlaylists = false
                ) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadDeviceSongs() {
        _state.update { it.copy(isLoadingDeviceSongs = true) }
        getDeviceSongsUseCase()
            .onEach { songs ->
                _state.update { state ->
                    state.copy(
                        deviceFiles = songs.map { song ->
                            DeviceFile(
                                id = song.id,
                                name = song.title,
                                path = song.externalUrl ?: "",
                                imageUrl = song.imageUrl?.toUri(),
                                localAlbumArtPath = song.localAlbumArtPath,
                                isSelected = false
                            )
                        },
                        isLoadingDeviceSongs = false
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun addSelectedSongsToPlaylist(mood: String) {
        val selectedIds = _state.value.deviceFiles.filter { it.isSelected }.map { it.id }
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val deviceSongs = songsRepository.getDeviceFile().first()
            val selectedSongs = deviceSongs.filter { it.id in selectedIds }

            val existingPlaylist = playlistRepository.getPlaylistByMood(mood)
            if (existingPlaylist != null) {
                val updatedSongs = (existingPlaylist.songs + selectedSongs).distinctBy { it.id }
                playlistRepository.savePlaylist(existingPlaylist.copy(songs = updatedSongs))
            } else {
                val newPlaylist = Playlist(
                    id = UUID.randomUUID().toString(),
                    mood = mood,
                    songs = selectedSongs,
                    createdAt = System.currentTimeMillis()
                )
                playlistRepository.savePlaylist(newPlaylist)
            }
            
            // Clear selection after adding
            _state.update { state ->
                state.copy(
                    deviceFiles = state.deviceFiles.map { it.copy(isSelected = false) }
                )
            }
        }
    }
}

data class PlaylistState(
    val deviceFiles: List<DeviceFile> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentMood: String? = null,
    val currentPlaylist: Playlist? = null,
    val isReorderMode: Boolean = false,
    val isSaving: Boolean = false,
    val draggedSongId: String? = null,
    val isLoadingPlaylists: Boolean = false,
    val isLoadingCurrentPlaylist: Boolean = false,
    val isLoadingDeviceSongs: Boolean = false,
    val isShuffleEnabled: Boolean = false
)

sealed class PlaylistIntent {
    data class SelectPlaylist(val mood: String) : PlaylistIntent()
    data class MarkPlaylistAsPlayed(val playlistId: String) : PlaylistIntent()
    data object LoadDeviceSongs : PlaylistIntent()
    data class ToggleFileSelection(val fileId: String) : PlaylistIntent()
    data class AddSelectedSongsToPlaylist(val mood: String) : PlaylistIntent()
    data class PlayPlaylist(val startIndex: Int = 0) : PlaylistIntent()
    data class ReorderSongs(val fromIndex: Int, val toIndex: Int) : PlaylistIntent()
    data object ToggleReorderMode : PlaylistIntent()
    data class StartDrag(val songId: String) : PlaylistIntent()
    data object EndDrag : PlaylistIntent()
    data class DeletePlaylist(val playlistId: String) : PlaylistIntent()
    data object ToggleShuffle : PlaylistIntent()
    data class RemoveSong(val songId: String) : PlaylistIntent()
    data class CreatePlaylist(val mood: String) : PlaylistIntent()
}
