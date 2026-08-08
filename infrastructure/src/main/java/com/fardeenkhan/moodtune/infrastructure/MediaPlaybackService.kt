package com.fardeenkhan.moodtune.infrastructure

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

/**
 * Owns the ExoPlayer instance and MediaSession - nothing else. Anything that needs playback
 * state (the app's Now Playing screen via MusicPlayerManager, or the home screen widget via
 * WidgetMediaConnection) connects its own MediaController to this session rather than this
 * service pushing state out to specific consumers.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MediaPlaybackService", "onCreate")
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        android.util.Log.d("MediaPlaybackService", "onGetSession: package=${controllerInfo.packageName}")
        return mediaSession
    }

    // Intentionally stops playback when the app is swiped away from recents, rather than
    // continuing in the background like most music apps - not an oversight.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession?.player?.let { player ->
            player.pause()
        }
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

