 package com.fardeenkhan.moodtune.infrastructure

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.MediaItem

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var progressUpdateJob: kotlinx.coroutines.Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            android.util.Log.d("MediaPlaybackService", "onIsPlayingChanged: $isPlaying")
            updateWidget()
            if (isPlaying) {
                startProgressUpdate()
            } else {
                stopProgressUpdate()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            android.util.Log.d("MediaPlaybackService", "onMediaItemTransition: ${mediaItem?.mediaMetadata?.title}, reason: $reason")
            updateWidget()
        }

        override fun onPlaybackStateChanged(state: Int) {
            android.util.Log.d("MediaPlaybackService", "onPlaybackStateChanged: $state")
            updateWidget()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED
                )
            ) {
                android.util.Log.d("MediaPlaybackService", "onEvents triggered updateWidget")
                updateWidget()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MediaPlaybackService", "onCreate called")
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            addListener(playerListener)
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("MediaPlaybackService", "onStartCommand: action=${intent?.action}")
        if (intent != null && intent.action == "com.fardeenkhan.moodtune.ACTION_WIDGET_COMMAND") {
            val command = intent.getStringExtra("EXTRA_COMMAND")
            val index = intent.getIntExtra("EXTRA_INDEX", -1)
            val player = mediaSession?.player
            android.util.Log.d("MediaPlaybackService", "onStartCommand: command=$command, index=$index, hasPlayer=${player != null}")
            if (player != null) {
                when (command) {
                    "play_pause" -> {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            if (player.playbackState == Player.STATE_IDLE) {
                                player.prepare()
                            }
                            player.play()
                        }
                    }
                    "next" -> {
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                        }
                    }
                    "prev", "previous" -> {
                        if (player.hasPreviousMediaItem()) {
                            player.seekToPreviousMediaItem()
                        }
                    }
                    "play_index" -> {
                        if (index >= 0 && index < player.mediaItemCount) {
                            player.seekTo(index, 0L)
                            player.play()
                        }
                    }
                }
                updateWidget()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        progressUpdateJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2000) // Update every 2 seconds to save battery but keep moving
                updateWidget()
            }
        }
    }

    private fun stopProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun updateWidget() {
        serviceScope.launch {
            // Player must be accessed on the main thread
            val playerStats = withContext(Dispatchers.Main) {
                mediaSession?.player?.let { player ->
                    val currentSong = player.currentMediaItem
                    val title = currentSong?.mediaMetadata?.title?.toString() ?: ""
                    val artist = currentSong?.mediaMetadata?.artist?.toString() ?: ""
                    val imageUrl = currentSong?.mediaMetadata?.artworkUri?.toString() ?: ""
                    val isPlaying = player.isPlaying
                    val duration = player.duration
                    val position = player.currentPosition
                    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
                    
                    PlayerStats(title, artist, imageUrl, isPlaying, progress)
                }
            } ?: return@launch

            android.util.Log.d("MediaPlaybackService", "updateWidget: isPlaying=${playerStats.isPlaying}, title=${playerStats.title}, artist=${playerStats.artist}, progress=${playerStats.progress}")

            try {
                val widgetClass = Class.forName("com.fardeenkhan.moodtune.app.widget.MoodTuneWidget") as Class<out GlanceAppWidget>
                MoodTuneWidgetHelper.updateWidgetState(
                    context = this@MediaPlaybackService,
                    widgetClass = widgetClass,
                    trackTitle = playerStats.title,
                    artistName = playerStats.artist,
                    isPlaying = playerStats.isPlaying,
                    albumArtPath = playerStats.imageUrl,
                    currentProgress = playerStats.progress
                )
            } catch (e: Exception) {
                android.util.Log.e("MediaPlaybackService", "Error updating widget", e)
            }
        }
    }

    private data class PlayerStats(
        val title: String,
        val artist: String,
        val imageUrl: String,
        val isPlaying: Boolean,
        val progress: Float
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
