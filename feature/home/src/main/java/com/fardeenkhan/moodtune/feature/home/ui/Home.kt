@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
package com.fardeenkhan.moodtune.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.fardeenkhan.moodtune.core.ui.components.NowPlayingIndicator
import com.fardeenkhan.moodtune.core.ui.theme.MoodTuneTheme
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel1
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel2
import com.fardeenkhan.moodtune.core.ui.theme.getMoodColor
import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation
import androidx.compose.ui.geometry.Offset
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.collections.isNotEmpty

@Composable
fun HomeScreenRoot(
    onNavigateToPlaylist: (String) -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {}
) {
    val viewModel: HomeViewModel = koinViewModel()
    HomeScreen(viewModel, onNavigateToPlaylist, isPlaying, onNowPlayingClick)
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToPlaylist: (String) -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateToPlaylist = onNavigateToPlaylist,
        isPlaying = isPlaying,
        onNowPlayingClick = onNowPlayingClick
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showVibeGenerator by remember { mutableStateOf(false) }

    // Vibe Generator Dialog (retrains generation feature in search icon)
    if (showVibeGenerator) {
        AlertDialog(
            onDismissRequest = { showVibeGenerator = false },
            title = { Text("Generate a Vibe Playlist") },
            text = {
                Column {
                    Text("Describe how you are feeling to let AI curate the perfect music list.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.vibeInput,
                        onValueChange = { onIntent(HomeIntent.OnVibeInputChange(it)) },
                        placeholder = { Text("e.g. Night driving, chill beats") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVibeGenerator = false
                        onIntent(HomeIntent.RequestPlaylistGeneration())
                    },
                    enabled = state.vibeInput.isNotBlank()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVibeGenerator = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(HomeIntent.DismissConfirmationDialog) },
            title = { Text("Create Playlist") },
            text = { 
                Column {
                    Text("How would you like to create a playlist for \"${state.pendingMood}\"?")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI Generation: We'll find the perfect songs for your mood.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onIntent(HomeIntent.ConfirmPlaylistGeneration) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate by AI")
                    }

                    OutlinedButton(
                        onClick = { onIntent(HomeIntent.CreateEmptyPlaylist) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create Empty (Offline)")
                    }
                    TextButton(
                        onClick = { onIntent(HomeIntent.DismissConfirmationDialog) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(state.generationStatus) {
        when (val status = state.generationStatus) {
            is HomeGenerationStatus.AlreadyCreated -> {
                scope.launch { snackbarHostState.showSnackbar("Playlist already created") }
                onIntent(HomeIntent.ResetState)
            }
            is HomeGenerationStatus.Success -> {
                scope.launch { snackbarHostState.showSnackbar("Playlist created successfully") }
                val mood = state.generatedMood
                if (mood != null) {
                    onIntent(HomeIntent.ResetState)
                    onNavigateToPlaylist(mood)
                }
            }
            is HomeGenerationStatus.Error -> {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = status.message,
                        actionLabel = "Retry",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onIntent(HomeIntent.RetryGeneration)
                    } else {
                        onIntent(HomeIntent.ResetState)
                    }
                }
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeTopBar(
                onSearchClick = { showVibeGenerator = true },
                isPlaying = isPlaying,
                onNowPlayingClick = onNowPlayingClick
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // 1. Featured Carousel section (representing most played playlists)
                item {
                    val carouselPlaylists = if (state.mostPlayedPlaylists.isNotEmpty()) {
                        state.mostPlayedPlaylists
                    } else if (state.recentlyPlayedPlaylists.isNotEmpty()) {
                        state.recentlyPlayedPlaylists
                    } else {
                        // Fallback dummy playlists so it is never empty and looks good immediately
                        listOf(
                            Playlist("dummy_1", "Reggae & Chill", listOf(Song("1", "Is This The Right Way?", "Beres Hammond", "", null, null, null, null, SongExplanation("", "", "", ""))), 0),
                            Playlist("dummy_2", "Slow Motion", listOf(Song("2", "Slow Motion", "Wackies", "", null, null, null, null, SongExplanation("", "", "", ""))), 0),
                            Playlist("dummy_3", "Gym Time Again", listOf(Song("3", "Pump It Up", "Gym Vibe", "", null, null, null, null, SongExplanation("", "", "", ""))), 0)
                        )
                    }

                    FeaturedCarousel(
                        playlists = carouselPlaylists,
                        onPlaylistClick = { playlist ->
                            if (!playlist.id.startsWith("dummy_")) {
                                onIntent(HomeIntent.MarkPlaylistAsPlayed(playlist.id))
                                onNavigateToPlaylist(playlist.mood)
                            } else {
                                // Trigger empty/mock generation for first play
                                onIntent(HomeIntent.RequestPlaylistGeneration(playlist.mood))
                            }
                        }
                    )
                }

                // 2. Playlists for You section
                item {
                    PlaylistsForYouSection(
                        playlists = state.allPlaylists.ifEmpty {
                            listOf(
                                Playlist("dummy_1", "Reggae and Chill", emptyList(), 0),
                                Playlist("dummy_2", "Slow Motion", emptyList(), 0),
                                Playlist("dummy_3", "Gym Time Again", emptyList(), 0),
                                Playlist("dummy_4", "Party Dance", emptyList(), 0)
                            )
                        },
                        onPlaylistClick = { playlist ->
                            if (!playlist.id.startsWith("dummy_")) {
                                onIntent(HomeIntent.MarkPlaylistAsPlayed(playlist.id))
                                onNavigateToPlaylist(playlist.mood)
                            } else {
                                onIntent(HomeIntent.RequestPlaylistGeneration(playlist.mood))
                            }
                        }
                    )
                }

                // 3. In the Mix section
                item {
                    InTheMixSection(
                        songs = state.mostPlayedSongs.ifEmpty {
                            listOf(
                                Song("mix_1", "Wack We A Wack", "Chris Gayle", "", null, null, null, null, SongExplanation("", "", "", "")),
                                Song("mix_2", "Major Lazer", "Chris Gayle", "", null, null, null, null, SongExplanation("", "", "", "")),
                                Song("mix_3", "Doh Wanna Go Bad", "Chris Gayle", "", null, null, null, null, SongExplanation("", "", "", ""))
                            )
                        },
                        onSongClick = { /* Can play via MusicPlayerManager or navigate to details */ }
                    )
                }
            }

            if (state.generationStatus is HomeGenerationStatus.Loading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    onSearchClick: () -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = "Featured",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
        },
        navigationIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "User profile",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
            }
        },
        actions = {
            NowPlayingIndicator(
                isPlaying = isPlaying,
                onClick = onNowPlayingClick
            )
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search & Vibe",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White
        ),
        modifier = Modifier.statusBarsPadding()
    )
}

@Composable
fun FeaturedCarousel(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { playlists.size })


    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(start = 24.dp, end = 64.dp),
        pageSpacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) { page ->
        val playlist = playlists[page]
        val moodColor = getMoodColor(playlist.mood)
        val song = playlist.songs.firstOrNull()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pageOffset = (
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    ).absoluteValue
                    alpha = lerp(start = 0.5f, stop = 1f, fraction = 1f - pageOffset.coerceIn(0f, 1f))
                    scaleY = lerp(start = 0.85f, stop = 1f, fraction = 1f - pageOffset.coerceIn(0f, 1f))
                }
        ) {
            val cardArtModel = song?.localAlbumArtPath?.let { java.io.File(it) } ?: song?.imageUrl
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp)
                    .clickable { onPlaylistClick(playlist) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = moodColor)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Album art background
                    if (cardArtModel != null) {
                        AsyncImage(
                            model = cardArtModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Dim overlay for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.10f),
                                        Color.Black.copy(alpha = 0.52f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .padding(end = 80.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = song?.artist ?: "Curated Playlist",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = song?.title ?: playlist.mood,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            maxLines = 2
                        )
                    }

                    // Cookie-shaped play button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 14.dp, end = 14.dp)
                            .size(52.dp)
                            .clip(MaterialShapes.Cookie7Sided.toShape())
                            .background(Color(0xFFF5C518))
                            .clickable { onPlaylistClick(playlist) },
                        contentAlignment = Alignment.Center
                    ) {

                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF5D3A1A),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Floating album art at top-right, partially overflows above card
            if (cardArtModel != null) {
                AsyncImage(
                    model = cardArtModel,
                    contentDescription = "${song?.title} album art",
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(moodColor.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsForYouSection(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit
) {
    Column {
        Text(
            text = "Playlists for You",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp,start = 24.dp,end=24.dp, bottom = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(playlists) { playlist ->
                val gradient = Brush.linearGradient(
                    colors = listOf(
                        getMoodColor(playlist.mood),
                        getMoodColor(playlist.mood).copy(alpha = 0.5f)
                    )
                )

                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { onPlaylistClick(playlist) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(gradient)
                    ) {
                        // Diagonal stripe decoration
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(20.dp)
                                .offset(x = 30.dp)
                                .graphicsLayer { rotationZ = -35f }
                                .background(Color.White.copy(alpha = 0.07f))
                        )

                        // Overlapping avatars
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.DarkGray)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .padding(start = 20.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = playlist.mood,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
fun InTheMixSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "In the Mix",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            
            Text(
                text = "See All",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFFF5722), // Orange/red See All link
                modifier = Modifier.clickable { /* Handle See All */ }
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(songs) { song ->
                Column(
                    modifier = Modifier
                        .width(130.dp)
                        .clickable { onSongClick(song) }
                ) {
                    val mixArtModel = song.localAlbumArtPath?.let { java.io.File(it) } ?: song.imageUrl
                    if (mixArtModel != null) {
                        AsyncImage(
                            model = mixArtModel,
                            contentDescription = "${song.title} by ${song.artist}",
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceLevel1),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1
                    )
                    
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

val mockSongs = listOf(
    Song("1", "Midnight City", "M83", "Vibe", null, null, "Dreamy", "High", SongExplanation("1", "", "", "")),
    Song("2", "Starboy", "The Weeknd", "Vibe", null, null, "Dark", "High", SongExplanation("2", "", "", "")),
    Song("3", "Blinding Lights", "The Weeknd", "Vibe", null, null, "Upbeat", "High", SongExplanation("3", "", "", ""))
)

@Preview
@Composable
fun HomeScreenPreview() {
    MoodTuneTheme {
        HomeScreen(
            state = HomeState(
                vibeInput = "Feeling happy",
                generationStatus = HomeGenerationStatus.Idle
            ),
            onIntent = {},
            onNavigateToPlaylist = {}
        )
    }
}
