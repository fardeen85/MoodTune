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
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.fardeenkhan.moodtune.core.ui.components.NowPlayingIndicator
import com.fardeenkhan.moodtune.core.ui.theme.MoodTuneTheme
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel1
import com.fardeenkhan.moodtune.core.ui.theme.getMoodColor
import com.fardeenkhan.moodtune.core.ui.util.albumArtModel
import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.model.Song
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreenRoot(
    onNavigateToPlaylist: (String) -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNavigateToAllSongs: () -> Unit = {}
) {
    val viewModel: HomeViewModel = koinViewModel()
    HomeScreen(viewModel, onNavigateToPlaylist, isPlaying, onNowPlayingClick, onSearchClick, onNavigateToAllSongs)
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToPlaylist: (String) -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNavigateToAllSongs: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateToPlaylist = onNavigateToPlaylist,
        isPlaying = isPlaying,
        onNowPlayingClick = onNowPlayingClick,
        onSearchClick = onSearchClick,
        onNavigateToAllSongs = onNavigateToAllSongs
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNavigateToAllSongs: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var showMoodInputDialog by remember { mutableStateOf(false) }
    var moodInput by remember { mutableStateOf("") }

    if (showMoodInputDialog) {
        AlertDialog(
            onDismissRequest = {
                showMoodInputDialog = false
                moodInput = ""
            },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = moodInput,
                    onValueChange = { moodInput = it },
                    label = { Text("Describe your mood") },
                    placeholder = { Text("e.g. late-night drive in the rain") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mood = moodInput.trim()
                        if (mood.isNotEmpty()) {
                            onIntent(HomeIntent.RequestPlaylistGeneration(mood))
                            showMoodInputDialog = false
                            moodInput = ""
                        }
                    },
                    enabled = moodInput.isNotBlank()
                ) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMoodInputDialog = false
                        moodInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(HomeIntent.DismissConfirmationDialog) },
            title = { Text("Create Playlist") },
            text = { Text("Create a playlist for \"${state.pendingMood}\"?") },
            confirmButton = {
                Button(onClick = { onIntent(HomeIntent.CreateEmptyPlaylist) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(HomeIntent.DismissConfirmationDialog) }) {
                    Text("Cancel")
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
                onSearchClick = onSearchClick,
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
                // 1. Featured Carousel — most played songs, ranked by play count (highest first)
                item {
                    val carouselSongs = if (state.mostPlayedSongs.isNotEmpty()) {
                        state.mostPlayedSongs
                    } else if (state.recentlyPlayedPlaylists.isNotEmpty()) {
                        state.recentlyPlayedPlaylists.mapNotNull { it.songs.firstOrNull() }
                    } else {
                        emptyList()
                    }

                    if (carouselSongs.isNotEmpty()) {
                        FeaturedCarousel(
                            songs = carouselSongs,
                            onSongClick = { song ->
                                onIntent(HomeIntent.PlaySong(song))
                                onNowPlayingClick()
                            }
                        )
                    } else {
                        FeaturedCarouselEmptyState()
                    }
                }

                // 2. Playlists for You section
                item {
                    PlaylistsForYouSection(
                        playlists = state.allPlaylists,
                        onPlaylistClick = { playlist ->
                            onIntent(HomeIntent.MarkPlaylistAsPlayed(playlist.id))
                            onNavigateToPlaylist(playlist.mood)
                        },
                        onCreateClick = { showMoodInputDialog = true }
                    )
                }

                // 3. In the Mix section
                item {
                    if (state.mostPlayedSongs.isNotEmpty()) {
                        InTheMixSection(
                            songs = state.mostPlayedSongs,
                            onSongClick = { song ->
                                onIntent(HomeIntent.PlaySong(song))
                                onNowPlayingClick()
                            },
                            onSeeAllClick = onNavigateToAllSongs
                        )
                    }
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
    songs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { songs.size })

    val rankLabels = listOf("#1 Most Played", "#2 Most Played", "#3 Most Played")

    // On landscape phones and unfolded/large foldables the window is much wider than it is
    // tall, so a full-width pager page would stretch the card art far beyond a sensible size.
    // Cap the card to a fixed width there instead of letting it fill the available width.
    val widthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val isCompactWidth = widthSizeClass == WindowWidthSizeClass.COMPACT

    val cardHeight = if (isCompactWidth) 220.dp else 180.dp
    val pageSize = if (isCompactWidth) PageSize.Fill else PageSize.Fixed(260.dp)
    val pagerContentPadding = if (isCompactWidth) {
        PaddingValues(start = 24.dp, end = 64.dp)
    } else {
        PaddingValues(horizontal = 24.dp)
    }

    HorizontalPager(
        state = pagerState,
        pageSize = pageSize,
        contentPadding = pagerContentPadding,
        pageSpacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
    ) { page ->
        val song = songs[page]
        val moodColor = getMoodColor(song.mood ?: "")

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
            val cardArtModel = song.albumArtModel()
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp)
                    .clickable { onSongClick(song) },
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
                            .padding(if (isCompactWidth) 24.dp else 16.dp)
                            .padding(end = if (isCompactWidth) 80.dp else 64.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (page < rankLabels.size) {
                            Text(
                                text = rankLabels[page],
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color(0xFFF5C518)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = song.title,
                            style = (if (isCompactWidth) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
                                .copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            maxLines = 2
                        )
                    }

                    // Cookie-shaped play button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 14.dp, end = 14.dp)
                            .size(if (isCompactWidth) 52.dp else 44.dp)
                            .clip(MaterialShapes.Cookie7Sided.toShape())
                            .background(Color(0xFFF5C518))
                            .clickable { onSongClick(song) },
                        contentAlignment = Alignment.Center
                    ) {

                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF5D3A1A),
                            modifier = Modifier.size(if (isCompactWidth) 26.dp else 22.dp)
                        )
                    }
                }
            }

            // Floating album art at top-right, partially overflows above card
            val floatingArtSize = if (isCompactWidth) 100.dp else 76.dp
            if (cardArtModel != null) {
                AsyncImage(
                    model = cardArtModel,
                    contentDescription = "${song.title} album art",
                    modifier = Modifier
                        .size(floatingArtSize)
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(floatingArtSize - 10.dp)
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
                        modifier = Modifier.size(if (isCompactWidth) 36.dp else 28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturedCarouselEmptyState() {
    val widthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val isCompactWidth = widthSizeClass == WindowWidthSizeClass.COMPACT
    val cardHeight = if (isCompactWidth) 220.dp else 180.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(cardHeight)
            .clip(RoundedCornerShape(24.dp))
    ) {
        // Same album-art-backed card treatment as a real FeaturedCarousel song, so the
        // placeholder reads as "a song card" rather than a generic empty box.
        Image(
            painter = painterResource(id = com.fardeenkhan.moodtune.core.ui.R.drawable.music_placeholder),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your most played songs will appear here",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Play some music to start building your mix",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PlaylistsForYouSection(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onCreateClick: () -> Unit
) {
    Column {
        Text(
            text = "Playlists for You",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp,start = 24.dp,end=24.dp, bottom = 16.dp)
        )

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .clickable { onCreateClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create your first playlist",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
            return@Column
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(playlists) { playlist ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { onPlaylistClick(playlist) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            painter = painterResource(id = com.fardeenkhan.moodtune.core.ui.R.drawable.music_disk),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f))
                        )

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
    onSongClick: (Song) -> Unit,
    onSeeAllClick: () -> Unit = {}
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
                modifier = Modifier.clickable { onSeeAllClick() }
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
                    val mixArtModel = song.albumArtModel()
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
