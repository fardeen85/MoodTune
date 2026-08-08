package com.fardeenkhan.moodtune.app.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore preference keys backing the widget's Glance state. Written by
 * [WidgetMediaConnection], read by [MoodTuneWidget].
 */
object WidgetKeys {
    val TRACK_TITLE = stringPreferencesKey("trackTitle")
    val ARTIST_NAME = stringPreferencesKey("artistName")
    val IS_PLAYING = booleanPreferencesKey("isPlaying")
    val ALBUM_ART_PATH = stringPreferencesKey("albumArtPath")
    val CURRENT_PROGRESS = floatPreferencesKey("currentProgress")
}
