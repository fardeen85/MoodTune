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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.fardeenkhan.moodtune.core.ui.theme.MoodTuneTheme
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel1
import com.fardeenkhan.moodtune.domain.model.Playlist
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlaylistScreenRoot(
    initialMood: String? = null,
    onNavigateToNowPlaying: () -> Unit
) {
    PlaylistScreen(initialMood, onNavigateToNowPlaying)
}

sealed class PlaylistNavigation {
    object List : PlaylistNavigation()
    data class Detail(val mood: String) : PlaylistNavigation()
    object DeviceFiles : PlaylistNavigation()
}


@OptIn( ExperimentalPermissionsApi::class)
@Composable
fun MusicPermissionScreen(onLoadMusic:()-> Unit) {
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
    onNavigateToNowPlaying: () -> Unit
) {
    var navigationState by remember { 
        mutableStateOf<PlaylistNavigation>(
            if (initialMood != null) PlaylistNavigation.Detail(initialMood) else PlaylistNavigation.List 
        ) 
    }
    val viewModel : PlaylistViewModel = koinViewModel()
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
                }
            )
        }
        is PlaylistNavigation.Detail -> {
            MoodDetailScreen(
                mood = navState.mood,
                state = state,
                onIntent = viewModel::onIntent,
                onBack = { navigationState = PlaylistNavigation.List },
                onAddFromDevice = { navigationState = PlaylistNavigation.DeviceFiles },
                onNavigateToNowPlaying = onNavigateToNowPlaying
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
    onDeletePlaylist: (String) -> Unit
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Your Moods",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (state.isLoadingPlaylists) {
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
        } else if (state.playlists.isEmpty()) {
            item {
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
                PlaylistCard(
                    playlist = playlist, 
                    onClick = { onMoodClick(playlist.mood) },
                    onLongClick = { playlistToDelete = playlist }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: Playlist, 
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val color = getMoodColor(playlist.mood)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLevel1)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = playlist.mood,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Playlist for your ${playlist.mood} vibe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
fun MoodDetailScreen(
    mood: String,
    state: PlaylistState,
    onIntent: (PlaylistIntent) -> Unit,
    onBack: () -> Unit,
    onAddFromDevice: () -> Unit,
    onNavigateToNowPlaying: () -> Unit
) {
    val songs = state.currentPlaylist?.songs ?: emptyList()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Use updated state to keep the target stable while still having access to latest data
    val currentSongs by rememberUpdatedState(songs)
    val currentDraggedId by rememberUpdatedState(state.draggedSongId)
    val currentOnIntent by rememberUpdatedState(onIntent)

    val listDndTarget = remember {
        object : DragAndDropTarget {
            override fun onMoved(event: DragAndDropEvent) {
                val draggedId = currentDraggedId ?: return
                val dragEvent = event.toAndroidDragEvent()
                val y = dragEvent.y
                val layoutInfo = lazyListState.layoutInfo
                
                // 1. Handle Auto-scrolling
                val viewHeight = layoutInfo.viewportSize.height
                val scrollThreshold = viewHeight * 0.15f
                if (y < scrollThreshold) {
                    coroutineScope.launch { lazyListState.animateScrollBy(-50f) }
                } else if (y > viewHeight - scrollThreshold) {
                    coroutineScope.launch { lazyListState.animateScrollBy(50f) }
                }

                // 2. Find the item under the y coordinate
                val item = layoutInfo.visibleItemsInfo.find { visibleItem ->
                    y.toInt() in visibleItem.offset..(visibleItem.offset + visibleItem.size)
                }

                if (item != null) {
                    val headerCount = 2 // Header and Actions
                    val targetIndex = (item.index - headerCount).coerceIn(0, currentSongs.size - 1)
                    val fromIndex = currentSongs.indexOfFirst { it.id == draggedId }

                    if (fromIndex != -1 && fromIndex != targetIndex) {
                        currentOnIntent(PlaylistIntent.ReorderSongs(fromIndex, targetIndex))
                    }
                }
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentOnIntent(PlaylistIntent.EndDrag)
                return true
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentOnIntent(PlaylistIntent.EndDrag)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFromDevice,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add from device file") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                    },
                    target = listDndTarget
                )
        ) {
            item {
                PlaylistHeader(
                    mood = mood, 
                    songCount = songs.size, 
                    isReorderMode = state.isReorderMode,
                    onBack = onBack,
                    onReorderClick = { onIntent(PlaylistIntent.ToggleReorderMode) }
                )
            }
            item {
                PlaylistActions(onPlayClick = { 
                    onIntent(PlaylistIntent.PlayPlaylist(0))
                    onNavigateToNowPlaying()
                })
            }
            if (state.isLoadingCurrentPlaylist) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
            } else if (songs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No songs in this playlist", color = Color.Gray)
                    }
                }
            } else {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongListItem(
                        song = song,
                        isReorderMode = state.isReorderMode,
                        modifier = Modifier.animateItem(),
                        onClick = { 
                            if (!state.isReorderMode) {
                                onIntent(PlaylistIntent.PlayPlaylist(index))
                                onNavigateToNowPlaying()
                            }
                        },
                        dragModifier = if (state.isReorderMode) {
                            Modifier.dragAndDropSource { _ ->
                                onIntent(PlaylistIntent.StartDrag(song.id))
                                DragAndDropTransferData(
                                    clipData = ClipData.newPlainText("songId", song.id),
                                    flags = View.DRAG_FLAG_GLOBAL
                                )
                            }
                        } else Modifier
                    )
                }
            }
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
                contentDescription = null,
                tint = if (file.isSelected) MaterialTheme.colorScheme.primary else Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            
            if (file.imageUrl != null) {
                AsyncImage(
                    model = file.imageUrl,
                    contentDescription = null,
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

@Composable
fun SongListItem(
    song: Song,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isExternal = song.externalUrl?.startsWith("http") == true

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
                modifier = dragModifier.padding(end = 12.dp)
            )
        }

        if (song.imageUrl != null) {
            AsyncImage(
                model = song.imageUrl,
                contentDescription = null,
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
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        if (isExternal) {
            IconButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(song.externalUrl))
                context.startActivity(intent)
            }) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.fardeenkhan.moodtune.core.ui.R.drawable.youtube_logo),
                    contentDescription = "Open in YouTube",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = {
                val spotifyUri = "spotify:search:${song.title} ${song.artist}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri))
                context.startActivity(intent)
            }) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.fardeenkhan.moodtune.core.ui.R.drawable.spotify_logo),
                    contentDescription = "Search on Spotify",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            IconButton(onClick = { }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.LightGray)
            }
        }
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
