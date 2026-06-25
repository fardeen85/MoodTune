package com.fardeenkhan.moodtune.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.fardeenkhan.moodtune.app.navigation.Route
import kotlinx.serialization.Serializable
import com.fardeenkhan.moodtune.core.ui.theme.MoodTuneTheme
import com.fardeenkhan.moodtune.core.utils.MusicPlayerManager
import com.fardeenkhan.moodtune.feature.home.ui.HomeScreenRoot
import com.fardeenkhan.moodtune.feature.playlist.ui.PlaylistScreenRoot
import com.fardeenkhan.moodtune.feature.songdetail.ui.NowPlayingScreenRoot
import org.koin.android.ext.android.inject



import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage

class MainActivity : ComponentActivity() {
    private val musicPlayerManager: MusicPlayerManager by inject()
 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoodTuneTheme {
                MainScreen(musicPlayerManager)
            }
        }
    }
 
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            musicPlayerManager.release()
        }
    }
}
 
@Composable
fun MainScreen(musicPlayerManager: MusicPlayerManager) {
    val backStack = rememberNavBackStack(Route.Home)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val playbackState by musicPlayerManager.playbackState.collectAsState()
    var isNavigationBarVisible by remember { mutableStateOf(true) }
    
    val nestedScrollConnection = remember(isLandscape) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isLandscape) {
                    if (available.y < -10f && isNavigationBarVisible) {
                        isNavigationBarVisible = false
                    } else if (available.y > 10f && !isNavigationBarVisible) {
                        isNavigationBarVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }
 
    // Reset visibility when orientation changes
    LaunchedEffect(isLandscape) {
        isNavigationBarVisible = true
    }
 
    BackHandler(enabled = backStack.size > 1 || backStack.last() != Route.Home) {
        if (backStack.last() != Route.Home) {
            // Remove everything until Home is reached
            while (backStack.size > 1 && backStack.last() != Route.Home) {
                backStack.removeAt(backStack.size - 1)
            }
            // If Home is not there (should not happen), add it
            if (backStack.last() != Route.Home) {
                backStack.clear()
                backStack.add(Route.Home)
            }
        } else if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }
 
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val defaultLayoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    
    val layoutType = if (isLandscape && !isNavigationBarVisible) {
        NavigationSuiteType.None
    } else {
        defaultLayoutType
    }
 
    NavigationSuiteScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        layoutType = layoutType,
        navigationSuiteItems = {
            item(
                selected = backStack.last() == Route.Home,
                onClick = { 
                    if (backStack.last() != Route.Home) {
                        backStack.add(Route.Home)
                    }
                },
                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                label = { Text("Home") }
            )
            item(
                selected = backStack.last() is Route.Playlist,
                onClick = { 
                    if (backStack.last() !is Route.Playlist) {
                        backStack.add(Route.Playlist(null))
                    }
                },
                icon = { Icon(Icons.Default.List, contentDescription = "Playlist") },
                label = { Text("Playlist") }
            )
            item(
                selected = backStack.last() == Route.NowPlaying,
                onClick = { 
                    if (backStack.last() != Route.NowPlaying) {
                        backStack.add(Route.NowPlaying)
                    }
                },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Playing") },
                label = { Text("Playing") }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { 
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.size - 1)
                    }
                },
                modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
                entryProvider = entryProvider {
                    entry<Route.Home> {
                        HomeScreenRoot(
                            onNavigateToPlaylist = { mood ->
                                backStack.add(Route.Playlist(mood))
                            },
                            isPlaying = playbackState.isPlaying,
                            onNowPlayingClick = {
                                if (backStack.last() != Route.NowPlaying) {
                                    backStack.add(Route.NowPlaying)
                                }
                            }
                        )
                    }
                    entry<Route.Playlist> { route ->
                        PlaylistScreenRoot(
                            initialMood = route.mood,
                            onNavigateToNowPlaying = { backStack.add(Route.NowPlaying) },
                            isPlaying = playbackState.isPlaying,
                            onNowPlayingClick = {
                                if (backStack.last() != Route.NowPlaying) {
                                    backStack.add(Route.NowPlaying)
                                }
                            }
                        )
                    }
                    entry<Route.NowPlaying> {
                        NowPlayingScreenRoot()
                    }
                }
            )

            // MiniPlayer commented out — replaced by NowPlayingIndicator in each screen's top bar
//            val song = playbackState.currentSong
//            if (song != null) {
//                Box(
//                    modifier = Modifier
//                        .align(Alignment.BottomCenter)
//                        .padding(horizontal = 16.dp, vertical = 8.dp)
//                ) {
//                    MiniPlayer(
//                        playbackState = playbackState,
//                        onPlayPauseClick = { musicPlayerManager.togglePlayPause() },
//                        onDismissClick = { musicPlayerManager.release() },
//                        onClick = { backStack.add(Route.NowPlaying) }
//                    )
//                }
//            }
        }
    }
}

@Composable
fun MiniPlayer(
    playbackState: com.fardeenkhan.moodtune.core.utils.PlaybackState,
    onPlayPauseClick: () -> Unit,
    onDismissClick: () -> Unit,
    onClick: () -> Unit
) {
    val song = playbackState.currentSong ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (song.imageUrl != null) {
                    AsyncImage(
                        model = song.imageUrl,
                        contentDescription = "${song.title} album art",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Gray.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.LightGray)
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = 1
                    )
                }
                
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color(0xFFFF5722)
                    )
                }
                
                IconButton(onClick = onDismissClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.LightGray
                    )
                }
            }
            
            // Linear Progress Bar at bottom
            val progressFraction = if (playbackState.duration > 0) {
                playbackState.progress.toFloat() / playbackState.duration
            } else 0f
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progressFraction)
                    .height(3.dp)
                    .background(Color(0xFFFF5722))
            )
        }
    }
}
