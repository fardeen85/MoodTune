package com.fardeenkhan.moodtune.wear.presentation

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.Text

/**
 * Shows whichever playback session `WearPlaybackController` currently reports as active -
 * mirrored from the phone or playing locally off the watch's own storage - tagged by
 * [com.fardeenkhan.moodtune.core.wearsync.WearPlaybackSource] so the two can be told apart.
 *
 * TODO(§2.4): render title/artist/art/progress/transport controls from `WearPlaybackController`'s
 * state; hide/simplify while ambient (§1.2).
 *
 * See `implementation/WearPlan.md` §1.1, §1.2, §2.4.
 */
@Composable
fun NowPlayingScreen() {
    Text("Now Playing")
}
