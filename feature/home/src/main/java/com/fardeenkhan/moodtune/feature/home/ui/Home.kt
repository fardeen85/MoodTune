@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
package com.fardeenkhan.moodtune.feature.home.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fardeenkhan.moodtune.core.ui.theme.MoodTuneTheme
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel1
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel2
import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreenRoot(onNavigateToPlaylist: (String) -> Unit) {
    val viewModel: HomeViewModel = koinViewModel()
    HomeScreen(viewModel, onNavigateToPlaylist)
}

@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigateToPlaylist: (String) -> Unit) {
    val state by viewModel.state.collectAsState()

    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateToPlaylist = onNavigateToPlaylist
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    if (state.showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(HomeIntent.DismissConfirmationDialog) },
            title = { Text("Create Playlist") },
            text = { 
                Column {
                    Text("How would you like to create a playlist for \"${state.pendingMood}\"?")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI Generation: We'll find the perfect songs for your mood (Requires Internet).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Empty Playlist: Create a fresh list and add your own songs (Works Offline).",
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
                scope.launch { snackbarHostState.showSnackbar(status.message) }
                onIntent(HomeIntent.ResetState)
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HomeTopBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                item {
                    VibeGenerationSection(
                        vibeInput = state.vibeInput,
                        onVibeInputChange = { onIntent(HomeIntent.OnVibeInputChange(it)) },
                        onGenerateClick = { onIntent(HomeIntent.RequestPlaylistGeneration()) },
                        isLoading = state.generationStatus is HomeGenerationStatus.Loading
                    )
                }
                item {
                    QuickMoodsSection(
                        onMoodClick = { onIntent(HomeIntent.RequestPlaylistGeneration(it)) },
                        isLoading = state.generationStatus is HomeGenerationStatus.Loading
                    )
                }
                item {
                    RecentlyPlayedSection(
                        playlists = state.recentlyPlayedPlaylists,
                        isLoading = state.isLoadingRecentlyPlayed,
                        onPlaylistClick = { onIntent(HomeIntent.MarkPlaylistAsPlayed(it.id)) }
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
fun HomeTopBar() {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "MoodTune AI",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        navigationIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = { /* TODO */ }) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        modifier = Modifier.statusBarsPadding()
    )
}

@Composable
fun VibeGenerationSection(
    vibeInput: String,
    onVibeInputChange: (String) -> Unit,
    onGenerateClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "How are you\nfeeling?",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 44.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 32.dp)
        )
        
        OutlinedTextField(
            value = vibeInput,
            onValueChange = onVibeInputChange,
            placeholder = { 
                Text(
                    "e.g. Late night coding, feeling calm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ) 
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            enabled = !isLoading
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onGenerateClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = !isLoading && vibeInput.isNotBlank(),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome, 
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Generate Mood",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun QuickMoodsSection(
    onMoodClick: (String) -> Unit,
    isLoading: Boolean
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Quick Moods",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        val moods = listOf(
            QuickMoodItem("Energetic", Icons.Default.Bolt, MaterialTheme.colorScheme.primary),
            QuickMoodItem("Melancholic", Icons.Default.WaterDrop, MaterialTheme.colorScheme.secondary),
            QuickMoodItem("Focused", Icons.Default.FilterCenterFocus, MaterialTheme.colorScheme.tertiary),
            QuickMoodItem("Calm", Icons.Default.Spa, MaterialTheme.colorScheme.primaryContainer)
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoodGridItem(moods[0], Modifier.weight(1f), onMoodClick, isLoading)
                MoodGridItem(moods[1], Modifier.weight(1f), onMoodClick, isLoading)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoodGridItem(moods[2], Modifier.weight(1f), onMoodClick, isLoading)
                MoodGridItem(moods[3], Modifier.weight(1f), onMoodClick, isLoading)
            }
        }
    }
}

data class QuickMoodItem(val title: String, val icon: ImageVector, val color: Color)

@Composable
fun MoodGridItem(
    item: QuickMoodItem, 
    modifier: Modifier = Modifier,
    onMoodClick: (String) -> Unit,
    isLoading: Boolean
) {
    ElevatedCard(
        modifier = modifier
            .height(110.dp)
            .clickable(enabled = !isLoading) { onMoodClick(item.title) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.icon, 
                    contentDescription = null, 
                    tint = item.color, 
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun RecentlyPlayedSection(
    playlists: List<Playlist>,
    isLoading: Boolean,
    onPlaylistClick: (Playlist) -> Unit
) {
    if (!isLoading && playlists.isEmpty()) return

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recently Played",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!isLoading && playlists.size > 3) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(playlists) { playlist ->
                    RecentlyPlayedCard(
                        playlist = playlist,
                        modifier = Modifier
                            .width(160.dp)
                            .clickable { onPlaylistClick(playlist) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecentlyPlayedCard(playlist: Playlist, modifier: Modifier = Modifier) {
    val color = getMoodColor(playlist.mood)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
             Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color)
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = playlist.mood,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = "${playlist.songs.size} tracks • AI Generated",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

fun getMoodColor(mood: String): Color {
    val palette = listOf(
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF4CAF50), // Green
        Color(0xFF2196F3), // Blue
        Color(0xFF9C27B0), // Purple
        Color(0xFFE91E63), // Pink
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF00BCD4), // Cyan
        Color(0xFF673AB7), // Deep Purple
        Color(0xFF009688), // Teal
        Color(0xFFFF9800)  // Orange
    )
    val index = Math.abs(mood.lowercase().hashCode()) % palette.size
    return palette[index]
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
