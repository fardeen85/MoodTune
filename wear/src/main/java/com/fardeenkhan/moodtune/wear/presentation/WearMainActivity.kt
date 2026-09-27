package com.fardeenkhan.moodtune.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.fardeenkhan.moodtune.wear.presentation.theme.MoodTuneTheme

/**
 * Entry point for the watch app. Hosts navigation between [NowPlayingScreen] and
 * [PlaylistsScreen].
 *
 * TODO(§2.4): wrap [WearApp] in an `AmbientLifecycleObserver` so both screens can render their
 * ambient-mode layout while a session is playing (§1.2).
 *
 * See `implementation/WearPlan.md` §1.1–§1.4, §2.4.
 */
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoodTuneTheme {
                WearApp()
            }
        }
    }
}

/**
 * Root composable. Will host a `SwipeDismissableNavHost` (`androidx.wear.compose.navigation`)
 * switching between [NowPlayingScreen] and [PlaylistsScreen] once navigation is wired up.
 */
@Composable
fun WearApp() {
    // TODO(§2.4): SwipeDismissableNavHost between NowPlayingScreen and PlaylistsScreen.
    NowPlayingScreen()
}
