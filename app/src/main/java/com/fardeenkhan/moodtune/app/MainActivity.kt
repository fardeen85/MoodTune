package com.fardeenkhan.moodtune.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.fardeenkhan.moodtune.app.navigation.Route
import com.fardeenkhan.moodtune.core.ui.theme.MoodTuneTheme
import com.fardeenkhan.moodtune.core.utils.MusicPlayerManager
import com.fardeenkhan.moodtune.feature.home.ui.HomeScreenRoot
import com.fardeenkhan.moodtune.feature.home.ui.SearchScreenRoot
import com.fardeenkhan.moodtune.feature.playlist.ui.AllSongsScreenRoot
import com.fardeenkhan.moodtune.feature.playlist.ui.PlaylistScreenRoot
import com.fardeenkhan.moodtune.feature.songdetail.ui.NowPlayingScreenRoot
import org.koin.android.ext.android.inject

/**
 * Single-activity host for the app. Owns the [MusicPlayerManager] instance for the process's
 * UI lifetime and releases it in [onDestroy] only when the activity is actually finishing
 * (not on a configuration change), since playback must survive rotation/multi-window resizes.
 */
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
 
/**
 * Root composable: hosts the Navigation3 back stack behind a [NavigationSuiteScaffold] (bottom
 * bar on phones, rail on wider layouts). In landscape, the nav bar/rail auto-hides on scroll-down
 * and reappears on scroll-up ([nestedScrollConnection]) to give content more vertical room on
 * short screens; portrait always keeps it visible.
 */
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
        // NavigationRail defaults to colorScheme.surface (unblended) while NavigationBar
        // defaults to surfaceContainer (wallpaper-blended, see MoodTuneTheme) - pin both to
        // surfaceContainer explicitly so the bar and rail always look the same regardless of
        // which one the current window size picks.
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationDrawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
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
                            },
                            onSearchClick = {
                                if (backStack.last() != Route.Search) {
                                    backStack.add(Route.Search)
                                }
                            },
                            onNavigateToAllSongs = {
                                if (backStack.last() != Route.AllSongs) {
                                    backStack.add(Route.AllSongs)
                                }
                            }
                        )
                    }
                    entry<Route.AllSongs> {
                        AllSongsScreenRoot(
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.size - 1)
                                }
                            },
                            onNavigateToNowPlaying = {
                                if (backStack.last() != Route.NowPlaying) {
                                    backStack.add(Route.NowPlaying)
                                }
                            }
                        )
                    }
                    entry<Route.Search> {
                        SearchScreenRoot(
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.size - 1)
                                }
                            },
                            onNavigateToNowPlaying = {
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
        }
    }
}
