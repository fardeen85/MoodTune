package com.fardeenkhan.moodtune.infrastructure.datasource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fardeenkhan.moodtune.domain.model.PlaybackSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.playbackSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_session")

class PlaybackSnapshotDataSource(private val context: Context) {

    private object Keys {
        val SNAPSHOT = stringPreferencesKey("playback_snapshot")
    }

    suspend fun save(snapshot: PlaybackSnapshot) {
        context.playbackSessionDataStore.edit { prefs ->
            prefs[Keys.SNAPSHOT] = Json.encodeToString(snapshot)
        }
    }

    suspend fun get(): PlaybackSnapshot? {
        val json = context.playbackSessionDataStore.data.first()[Keys.SNAPSHOT] ?: return null
        return try {
            Json.decodeFromString<PlaybackSnapshot>(json)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clear() {
        context.playbackSessionDataStore.edit { it.remove(Keys.SNAPSHOT) }
    }
}
