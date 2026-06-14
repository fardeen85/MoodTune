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



class MainActivity : ComponentActivity() {
    private val musicPlayerManager: MusicPlayerManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoodTuneTheme {
                MainScreen()
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
fun MainScreen() {
    val backStack = rememberNavBackStack(Route.Home)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
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
                        }
                    )
                }
                entry<Route.Playlist> { route ->
                    PlaylistScreenRoot(
                        initialMood = route.mood,
                        onNavigateToNowPlaying = { backStack.add(Route.NowPlaying) }
                    )
                }
                entry<Route.NowPlaying> {
                    NowPlayingScreenRoot()
                }
            }
        )
    }
}
