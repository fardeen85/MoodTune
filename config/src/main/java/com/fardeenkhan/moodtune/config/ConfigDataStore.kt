package com.fardeenkhan.moodtune.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

class ConfigDataStore(private val context: Context) {

    private object Keys {
        val GEMINI_KEY = stringPreferencesKey("gemini_key")
        val GEMINI_URL = stringPreferencesKey("gemini_url")
        val YOUTUBE_KEY = stringPreferencesKey("youtube_key")
    }

    val configFlow: Flow<AppConfig> = context.dataStore.data.map { prefs ->
        AppConfig(
            geminiKey = prefs[Keys.GEMINI_KEY] ?: "",
            geminiUrl = prefs[Keys.GEMINI_URL] ?: "",
            youtubeKey = prefs[Keys.YOUTUBE_KEY] ?: ""
        )
    }

    suspend fun saveConfig(config: AppConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GEMINI_KEY] = config.geminiKey
            prefs[Keys.GEMINI_URL] = config.geminiUrl
            prefs[Keys.YOUTUBE_KEY] = config.youtubeKey
        }
    }
}
