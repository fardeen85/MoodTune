package com.fardeenkhan.moodtune.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

class RemoteConfigManager {

    private val remoteConfig = FirebaseRemoteConfig.getInstance().apply {
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        setConfigSettingsAsync(settings)
        setDefaultsAsync(
            mapOf(
                KEY_GEMINI_KEY to "",
                KEY_GEMINI_URL to "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
                KEY_YOUTUBE_KEY to ""
            )
        )
    }

    suspend fun fetchConfig(): AppConfig {
        remoteConfig.fetchAndActivate().await()
        return AppConfig(
            geminiKey = remoteConfig.getString(KEY_GEMINI_KEY),
            geminiUrl = remoteConfig.getString(KEY_GEMINI_URL),
            youtubeKey = remoteConfig.getString(KEY_YOUTUBE_KEY)
        )
    }

    companion object {
        const val KEY_GEMINI_KEY = "Gemini_key"
        const val KEY_GEMINI_URL = "Gemini_url"
        const val KEY_YOUTUBE_KEY = "youtube_key"
    }
}
