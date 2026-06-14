package com.fardeenkhan.moodtune.infrastructure

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * Shared keys for Widget Preferences to ensure consistency between MediaService and Widget UI.
 */
object WidgetKeys {
    val TRACK_TITLE = stringPreferencesKey("trackTitle")
    val ARTIST_NAME = stringPreferencesKey("artistName")
    val IS_PLAYING = booleanPreferencesKey("isPlaying")
    val ALBUM_ART_PATH = stringPreferencesKey("albumArtPath")
    val CURRENT_PROGRESS = floatPreferencesKey("currentProgress")
}

/**
 * Helper to update Widget state from background services.
 */
object MoodTuneWidgetHelper {
    
    suspend fun updateWidgetState(
        context: Context,
        widgetClass: Class<out GlanceAppWidget>,
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        albumArtPath: String,
        currentProgress: Float
    ) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(widgetClass)
        
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    set(WidgetKeys.TRACK_TITLE, trackTitle)
                    set(WidgetKeys.ARTIST_NAME, artistName)
                    set(WidgetKeys.IS_PLAYING, isPlaying)
                    set(WidgetKeys.ALBUM_ART_PATH, albumArtPath)
                    set(WidgetKeys.CURRENT_PROGRESS, currentProgress)
                }
            }
        }
        
        // Trigger a full refresh
        try {
            val widgetInstance = widgetClass.getDeclaredConstructor().newInstance()
            widgetInstance.updateAll(context)
        } catch (e: Exception) {
            android.util.Log.e("MoodTuneWidgetHelper", "Error updating widget instance", e)
        }
    }
}
