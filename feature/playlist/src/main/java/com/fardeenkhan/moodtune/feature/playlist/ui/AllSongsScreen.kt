package com.fardeenkhan.moodtune.feature.playlist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import org.koin.compose.viewmodel.koinViewModel

class AllSongsViewModel(
    private val songsRepository: SongsRepository,
    private val musicPlayerManager: MusicPlayerManager
) : ViewModel() {

    private val _state = MutableStateFlow(AllSongsState())
    val state: StateFlow<AllSongsState> = _state.asStateFlow()

    init {
        songsRepository.getSavedSongs()
            .onEach { songs ->
                _state.update { it.copy(songs = songs, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: AllSongsIntent) {
        when (intent) {
            is AllSongsIntent.PlaySong -> {
                val songs = _state.value.songs
                val startIndex = songs.indexOf(intent.song).coerceAtLeast(0)
                musicPlayerManager.playPlaylist(songs, startIndex)
            }
        }
    }
}

data class AllSongsState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true
)

sealed class AllSongsIntent {
    data class PlaySong(val song: Song) : AllSongsIntent()
}

@Composable
fun AllSongsScreenRoot(
    onBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit
) {
    val viewModel: AllSongsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    AllSongsScreen(
        state = state,
        onBack = onBack,
        onSongClick = { song ->
            viewModel.onIntent(AllSongsIntent.PlaySong(song))
            onNavigateToNowPlaying()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSongsScreen(
    state: AllSongsState,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "All Songs from Playlists",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.songs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No songs yet", color = Color.Gray)
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.songs, key = { it.id }) { song ->
                            SongListItem(
                                song = song,
                                isReorderMode = false,
                                onClick = { onSongClick(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}
