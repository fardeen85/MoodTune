package com.fardeenkhan.moodtune.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation3 back stack entries. Each destination must stay [Serializable] - NavDisplay
 * persists the back stack (e.g. across process death) by serializing every [Route] on it.
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    /** [mood] is null when opened from the bottom nav (shows the playlist picker/list). */
    @Serializable
    data class Playlist(val mood: String?) : Route

    @Serializable
    data object NowPlaying : Route
    @Serializable
    data object Search : Route
    @Serializable
    data object AllSongs : Route
}