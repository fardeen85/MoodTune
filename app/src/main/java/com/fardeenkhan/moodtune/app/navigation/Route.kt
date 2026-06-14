package com.fardeenkhan.moodtune.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route
    @kotlinx.serialization.Serializable
    data class Playlist(val mood: String?) : Route
    @kotlinx.serialization.Serializable
    data object NowPlaying : Route
}