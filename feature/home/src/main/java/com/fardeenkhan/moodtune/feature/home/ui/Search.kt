package com.fardeenkhan.moodtune.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.animation.Crossfade
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel1
import com.fardeenkhan.moodtune.core.ui.util.albumArtModel
import com.fardeenkhan.moodtune.core.ui.util.rememberDelayedLoadingVisible
import com.fardeenkhan.moodtune.domain.model.LoadState
import com.fardeenkhan.moodtune.domain.model.Song
import org.koin.compose.viewmodel.koinViewModel

/** Which of Search's mutually-exclusive render branches is currently active - see its usage. */
private enum class SearchRenderShape { PROMPT, LOADING, ERROR, EMPTY_NO_SONGS, NO_RESULTS, RESULTS }

@Composable
fun SearchScreenRoot(
    onBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit
) {
    val viewModel: SearchViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    SearchScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onSongClick = { song ->
            viewModel.onIntent(SearchIntent.PlaySong(song))
            onNavigateToNowPlaying()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onIntent(SearchIntent.OnQueryChange(it)) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search songs or artists") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onIntent(SearchIntent.OnQueryChange("")) }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val showLoader = rememberDelayedLoadingVisible(
                state.allSongsState is LoadState.Idle || state.allSongsState is LoadState.Loading
            )
            // Which of Search's mutually-exclusive render branches is currently active - a
            // stable Crossfade targetState so switching between them fades instead of popping,
            // without replaying the fade on every keystroke within the same branch (e.g. typing
            // more letters while still matching some results stays in RESULTS the whole time).
            val searchShape = when (val allSongsState = state.allSongsState) {
                is LoadState.Idle, is LoadState.Loading ->
                    if (state.query.isBlank()) SearchRenderShape.PROMPT else SearchRenderShape.LOADING
                is LoadState.Error -> SearchRenderShape.ERROR
                is LoadState.Loaded -> when {
                    state.query.isBlank() -> SearchRenderShape.PROMPT
                    allSongsState.data.isEmpty() -> SearchRenderShape.EMPTY_NO_SONGS
                    state.results.isEmpty() -> SearchRenderShape.NO_RESULTS
                    else -> SearchRenderShape.RESULTS
                }
            }
            Crossfade(targetState = searchShape, label = "searchShape") { currentShape ->
                when (currentShape) {
                    SearchRenderShape.PROMPT -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Search your saved songs by title or artist",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    SearchRenderShape.LOADING -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (showLoader) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    SearchRenderShape.ERROR -> {
                        val message = (state.allSongsState as? LoadState.Error)?.message.orEmpty()
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    SearchRenderShape.EMPTY_NO_SONGS -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Hmm, you don't have any songs yet",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    SearchRenderShape.NO_RESULTS -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No songs found for \"${state.query}\"",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    SearchRenderShape.RESULTS -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(state.results, key = { it.id }) { song ->
                                SearchResultRow(
                                    song = song,
                                    onClick = { onSongClick(song) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val artModel = song.albumArtModel()
        if (artModel != null) {
            AsyncImage(
                model = artModel,
                contentDescription = "${song.title} album art",
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceLevel1),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
    }
}
