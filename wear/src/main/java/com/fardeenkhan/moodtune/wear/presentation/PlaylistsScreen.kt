package com.fardeenkhan.moodtune.wear.presentation

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.Text

/**
 * Lists the phone's existing playlists, synced via
 * [com.fardeenkhan.moodtune.core.wearsync.WearPaths.PLAYLISTS]. Each row does double duty:
 * a playlist with any songs downloaded plays that local subset immediately; one with nothing
 * downloaded sends a remote `StartPlaylist` command to the phone instead.
 *
 * TODO(§2.4): `ScalingLazyColumn` of `WearPlaylistSummary`, cross-referenced against
 * `WearDownloadStore.songsFor(playlistId)` for a "N/total downloaded" badge per row.
 *
 * See `implementation/WearPlan.md` §1.4, §2.4.
 */
@Composable
fun PlaylistsScreen() {
    Text("Playlists")
}
