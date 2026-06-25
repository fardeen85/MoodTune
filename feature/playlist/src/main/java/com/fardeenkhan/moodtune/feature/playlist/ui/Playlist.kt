@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
package com.fardeenkhan.moodtune.feature.playlist.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.ui.draganddrop.toAndroidDragEvent
import android.view.View
import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import com.fardeenkhan.moodtune.core.ui.components.NowPlayingIndicator
import com.fardeenkhan.moodtune.core.ui.theme.MoodTuneTheme
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel1
import com.fardeenkhan.moodtune.core.ui.theme.getMoodColor
import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation
import com.fardeenkhan.moodtune.feature.playlist.ui.components.rememberDragDropListState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlaylistScreenRoot(
    initialMood: String? = null,
    onNavigateToNowPlaying: () -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {}
) {
    PlaylistScreen(initialMood, onNavigateToNowPlaying, isPlaying, onNowPlayingClick)
}

sealed class PlaylistNavigation {
    object List : PlaylistNavigation()
    data class Detail(val mood: String) : PlaylistNavigation()
    object DeviceFiles : PlaylistNavigation()
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MusicPermissionScreen(onLoadMusic: () -> Unit) {
    // 1. Determine correct permission for API level
    var showDialog by remember { mutableStateOf(false) }
    val musicPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // 2. Create the permission state
    val permissionState = rememberPermissionState(permission = musicPermission)

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        when {
            // Case A: Permission is granted
            permissionState.status.isGranted -> {
                Text("Permission Granted! Showing your music...")
                showDialog = false
                onLoadMusic()
            }

            // Case B: Show rationale or request
            permissionState.status.shouldShowRationale -> {
                showDialog = true
            }

            // Case C: Initial state or permanently denied
            else -> {

                showDialog = true
            }
        }
    }


    if(showDialog) {
        PermissionRationaleDialog({

            permissionState.launchPermissionRequest()
        }, {

            showDialog = false
        })
    }
}

@Composable
fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Music Permission Needed")
        },
        text = {
            Text("This app requires access to your audio files so you can browse and play your local music library.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}




@Composable
fun PlaylistScreen(
    initialMood: String? = null,
    onNavigateToNowPlaying: () -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {}
) {
    var navigationState by remember {
        mutableStateOf<PlaylistNavigation>(
            if (initialMood != null) PlaylistNavigation.Detail(initialMood) else PlaylistNavigation.List
        )
    }
    val viewModel: PlaylistViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(initialMood) {
        if (initialMood != null) {
            viewModel.onIntent(PlaylistIntent.SelectPlaylist(initialMood))
            navigationState = PlaylistNavigation.Detail(initialMood)
        }
    }

    when (val navState = navigationState) {
        is PlaylistNavigation.List -> {
            PlaylistListScreen(
                state = state,
                onMoodClick = { mood ->
                    viewModel.onIntent(PlaylistIntent.SelectPlaylist(mood))
                    state.playlists.find { p -> p.mood.equals(mood, ignoreCase = true) }?.let { p ->
                        viewModel.onIntent(PlaylistIntent.MarkPlaylistAsPlayed(p.id))
                    }
                    navigationState = PlaylistNavigation.Detail(mood)
                },
                onDeletePlaylist = { id ->
                    viewModel.onIntent(PlaylistIntent.DeletePlaylist(id))
                },
                isPlaying = isPlaying,
                onNowPlayingClick = onNowPlayingClick
            )
        }
        is PlaylistNavigation.Detail -> {
            MoodDetailScreen(
                mood = navState.mood,
                state = state,
                onIntent = viewModel::onIntent,
                onBack = { navigationState = PlaylistNavigation.List },
                onAddFromDevice = { navigationState = PlaylistNavigation.DeviceFiles },
                onNavigateToNowPlaying = onNavigateToNowPlaying,
                isPlaying = isPlaying,
                onNowPlayingClick = onNowPlayingClick
            )
        }
        is PlaylistNavigation.DeviceFiles -> {
            val mood = (navState as? PlaylistNavigation.Detail)?.mood
                ?: (state.currentPlaylist?.mood ?: "")

            DeviceFilesScreen(
                state = state,
                onIntent = viewModel::onIntent,
                mood = mood,
                onBack = { 
                   navigationState = PlaylistNavigation.Detail(mood)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistListScreen(
    state: PlaylistState,
    onMoodClick: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {}
) {
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete the \"${playlistToDelete?.mood}\" playlist? This will only remove this playlist and won't affect songs in other playlists.") },
            confirmButton = {
                Button(
                    onClick = { 
                        playlistToDelete?.let { onDeletePlaylist(it.id) }
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Decorative triangle accent at top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(100.dp)
                .graphicsLayer {
                    translationX = 30f
                    translationY = -30f
                    rotationZ = 45f
                }
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF7B61FF), Color(0xFF1DB954))
                    )
                )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(3) }) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = "Trending Now",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Playlists",
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Caziq Music • ${state.playlists.size} playlists",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        // Right-aligned play and shuffle buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NowPlayingIndicator(
                                isPlaying = isPlaying,
                                onClick = onNowPlayingClick
                            )
                            // 3-dot options button
                            IconButton(
                                onClick = { /* Placeholder — no action yet */ },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                            }

                            // Shuffle Button (grey circular)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable { /* Shuffle action */ },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color.White)
                            }

                            // Play Button (white circular)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        if (state.playlists.isNotEmpty()) {
                                            onMoodClick(state.playlists.first().mood)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
                            }
                        }
                    }
                }
            }

            if (state.isLoadingPlaylists) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
            } else if (state.playlists.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No playlists generated yet", color = Color.Gray)
                    }
                }
            } else {
                items(state.playlists) { playlist ->
                    PlaylistGridCard(
                        playlist = playlist,
                        onClick = { onMoodClick(playlist.mood) },
                        onLongClick = { playlistToDelete = playlist }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistGridCard(
    playlist: Playlist, 
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            getMoodColor(playlist.mood),
            getMoodColor(playlist.mood).copy(alpha = 0.5f)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(gradient)
        ) {
            // Diagonal stripe decoration
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(40.dp)
                    .offset(x = 20.dp)
                    .graphicsLayer { rotationZ = 25f }
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(20.dp)
                    .offset(x = 50.dp)
                    .graphicsLayer { rotationZ = 25f }
                    .background(Color.White.copy(alpha = 0.05f))
            )

            // Overlapping Avatars inside the playlist card
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
                Box(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.mood,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1
        )
        Text(
            text = "${playlist.songs.size} songs",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodDetailScreen(
    mood: String,
    state: PlaylistState,
    onIntent: (PlaylistIntent) -> Unit,
    onBack: () -> Unit,
    onAddFromDevice: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {}
) {
    val songs = state.currentPlaylist?.songs ?: emptyList()
    val lazyListState = rememberLazyListState()
    var songOptionsTarget by remember { mutableStateOf<Song?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    
    val currentOnMove by rememberUpdatedState<(Int, Int) -> Unit> { from, to ->
        // Subtract 1 because of the header item in LazyColumn
        val headerCount = 1
        val actualFrom = (from - headerCount).coerceAtLeast(0)
        val actualTo = (to - headerCount).coerceAtLeast(0)
        
        if (actualFrom < songs.size && actualTo < songs.size) {
            onIntent(PlaylistIntent.ReorderSongs(actualFrom, actualTo))
        }
    }

    val dragdropState = rememberDragDropListState(lazyListState,
        onMove = { from, to -> currentOnMove(from, to) })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFromDevice,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add songs") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Decorative triangle accent at top right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(100.dp)
                    .graphicsLayer {
                        translationX = 30f
                        translationY = -30f
                        rotationZ = 45f
                    }
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFF5722), Color(0xFF7B61FF))
                        )
                    )
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.isReorderMode) {
                        if (state.isReorderMode) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset -> dragdropState.onDragStart(offset) },
                                onDragEnd = { dragdropState.onDragEnd() },
                                onDragCancel = { dragdropState.onDragEnd() },
                                onDrag = { _, dragAmount -> dragdropState.onDrag(dragAmount) }
                            )
                        }
                    },
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    // Merged top header matching playlist_songs.png
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                NowPlayingIndicator(
                                    isPlaying = isPlaying,
                                    onClick = onNowPlayingClick
                                )
                                IconButton(onClick = { if (!state.isSaving) onIntent(PlaylistIntent.ToggleReorderMode) }) {
                                    Icon(
                                        if (state.isReorderMode) Icons.Default.Check else Icons.Default.MoreVert,
                                        contentDescription = "Options/Reorder",
                                        tint = if (state.isReorderMode) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Trending Now",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = mood,
                                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Caziq Music • ${songs.size} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            // Right-aligned buttons: Filter, Shuffle, Play
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Filter Button (grey circular)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .clickable { /* Toggle filter/sort */ },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = "Filter", tint = Color.White, modifier = Modifier.size(20.dp))
                                }

                                // Shuffle Button (grey circular)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.isShuffleEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else Color.White.copy(alpha = 0.1f)
                                        )
                                        .clickable { onIntent(PlaylistIntent.ToggleShuffle) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        contentDescription = if (state.isShuffleEnabled) "Disable shuffle" else "Enable shuffle",
                                        tint = if (state.isShuffleEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Play Button (white circular)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable { 
                                            if (songs.isNotEmpty()) {
                                                onIntent(PlaylistIntent.PlayPlaylist(0))
                                                onNavigateToNowPlaying()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                if (state.isLoadingCurrentPlaylist && songs.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                } else if (songs.isEmpty() && !state.isLoadingCurrentPlaylist) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text(text = "No songs in this playlist", color = Color.Gray)
                        }
                    }
                } else {
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        val isDragging = dragdropState.draggedItemId == song.id

                        val animatedOffset by animateFloatAsState(
                            targetValue = if (isDragging) dragdropState.getDraggedOffset() else 0f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy
                            ),
                            label = "dragOffset"
                        )

                        SongListItem(
                            song = song,
                            isReorderMode = state.isReorderMode,
                            modifier = Modifier
                                .animateItem()
                                .graphicsLayer {
                                    translationY = animatedOffset
                                    shadowElevation = if (isDragging) 8f else 0f
                                    scaleX = if (isDragging) 1.05f else 1f
                                    scaleY = if (isDragging) 1.05f else 1f
                                }
                                .background(
                                    if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else Color.Transparent
                                ),
                            onClick = {
                                if (!state.isReorderMode) {
                                    onIntent(PlaylistIntent.PlayPlaylist(index))
                                    onNavigateToNowPlaying()
                                }
                            },
                            onOptionsClick = { songOptionsTarget = song }
                        )
                    }
                }
            }
        }
    }

    songOptionsTarget?.let { targetSong ->
        ModalBottomSheet(
            onDismissRequest = { songOptionsTarget = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text(targetSong.title, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(targetSong.artist, color = Color.Gray) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceLevel1),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
                        }
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Remove from playlist") },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        onIntent(PlaylistIntent.RemoveSong(targetSong.id))
                        songOptionsTarget = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Share") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.clickable {
                        val shareText = buildString {
                            append("${targetSong.title} by ${targetSong.artist}")
                            if (targetSong.externalUrl != null) append("\n${targetSong.externalUrl}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share song"))
                        songOptionsTarget = null
                    }
                )
            }
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOptionsClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isReorderMode) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isReorderMode) {
            Icon(
                Icons.Default.Reorder,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        val songArtModel = song.localAlbumArtPath?.let { java.io.File(it) } ?: song.imageUrl
        if (songArtModel != null) {
            AsyncImage(
                model = songArtModel,
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

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.mood ?: "Chill"}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = song.durationMs?.let { ms ->
                val totalSeconds = ms / 1000
                "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
            } ?: "--:--",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Song options", tint = Color.Gray)
        }
    }
}

@Composable
fun DeviceFilesScreen(
    state: PlaylistState,
    onIntent: (PlaylistIntent) -> Unit,
    mood: String,
    onBack: () -> Unit
) {
    val selectedCount = state.deviceFiles.count { it.isSelected }

    MusicPermissionScreen(
        onLoadMusic = {
            onIntent(PlaylistIntent.LoadDeviceSongs)
        }
    )

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
                    text = "Add from device",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        bottomBar = {
            if (selectedCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceLevel1,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = { 
                            onIntent(PlaylistIntent.AddSelectedSongsToPlaylist(mood))
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add $selectedCount songs to $mood")
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoadingDeviceSongs) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
            } else if (state.deviceFiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No audio files found on device", color = Color.Gray)
                    }
                }
            } else {
                items(state.deviceFiles) { file ->
                    FileItem(file = file, onToggle = { onIntent(PlaylistIntent.ToggleFileSelection(file.id)) })
                }
            }
        }
    }
}

@Composable
fun FileItem(file: DeviceFile, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (file.isSelected) MaterialTheme.colorScheme.primaryContainer else SurfaceLevel1
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (file.isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (file.isSelected) "Selected" else "Not selected",
                tint = if (file.isSelected) MaterialTheme.colorScheme.primary else Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            
            val artModel = file.localAlbumArtPath?.let { java.io.File(it) } ?: file.imageUrl
            if (artModel != null) {
                AsyncImage(
                    model = artModel,
                    contentDescription = "${file.name} album art",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceLevel1),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (file.isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                )
                Text(
                    text = file.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (file.isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun PlaylistHeader(
    mood: String, 
    songCount: Int, 
    isReorderMode: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onReorderClick: () -> Unit
) {
    val color = getMoodColor(mood)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Mood: $mood",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$songCount songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(12.dp).size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = onReorderClick) {
                    Icon(
                        if (isReorderMode) Icons.Default.Check else Icons.Default.Reorder, 
                        contentDescription = if (isReorderMode) "Done" else "Reorder", 
                        tint = if (isReorderMode) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistActions(onPlayClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }
        FloatingActionButton(
            onClick = onPlayClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(50)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
    }
}

val mockPlaylistSongs = listOf(
    Song("1", "Starboy", "The Weeknd", "Energetic", null, null, "Upbeat", "High", SongExplanation("1", "", "", "")),
    Song("2", "Blinding Lights", "The Weeknd", "Energetic", null, null, "Upbeat", "High", SongExplanation("2", "", "", "")),
    Song("3", "Midnight City", "M83", "Energetic", null, null, "Dreamy", "High", SongExplanation("3", "", "", "")),
    Song("4", "The Hills", "The Weeknd", "Energetic", null, null, "Dark", "Medium", SongExplanation("4", "", "", "")),
    Song("5", "Save Your Tears", "The Weeknd", "Energetic", null, null, "Sad", "Medium", SongExplanation("5", "", "", ""))
)

@Preview
@Composable
fun PlaylistScreenPreview() {
    MoodTuneTheme {
        PlaylistScreen(onNavigateToNowPlaying = {})
    }
}
