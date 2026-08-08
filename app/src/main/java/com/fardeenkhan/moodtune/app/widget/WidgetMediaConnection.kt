package com.fardeenkhan.moodtune.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fardeenkhan.moodtune.infrastructure.MediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The widget's own persistent connection to the live MediaSession - the widget-side twin of
 * MusicPlayerManager (core/utils). Kept as a process-wide singleton rather than something tied
 * to an Activity, because the widget must keep working (and keep reflecting playback state)
 * after MainActivity is destroyed while MediaPlaybackService keeps playing in the foreground.
 *
 * One MediaController is built lazily on first use and reused for every subsequent button tap
 * and state push, instead of connecting fresh per tap.
 *
 * [uiState] is the actual source of truth the widget renders from - MoodTuneWidget observes it
 * directly with collectAsState() inside its composition. This matters because
 * GlanceAppWidget.update()/updateAll() do NOT restart provideGlance() while a session is already
 * running (confirmed against the Glance team directly, see the kotlinlang Slack #glance channel)
 * - so a widget that reads state once at the top of provideGlance() and calls updateAll() on
 * every change will only ever show the FIRST update, exactly the bug this class used to have.
 * The fix is to read state reactively inside the composition instead of expecting updateAll() to
 * re-trigger it. DataStore persistence ([persistAsync]) still happens on every change, but only
 * as a best-effort fallback for a cold start (process death, or a widget session that already
 * timed out) - it is not what drives a visible widget's live updates anymore.
 */
object WidgetMediaConnection {

    private const val TAG = "WidgetMediaConnection"

    data class PlayerSnapshot(
        val title: String = "",
        val artist: String = "",
        val albumArtPath: String = "",
        val isPlaying: Boolean = false,
        val progress: Float = 0f
    )

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var appContext: Context

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _uiState = MutableStateFlow(PlayerSnapshot())
    val uiState: StateFlow<PlayerSnapshot> = _uiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            android.util.Log.d(TAG, "onIsPlayingChanged: $isPlaying")
            queueSnapshot()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            android.util.Log.d(TAG, "onMediaItemTransition: ${mediaItem?.mediaMetadata?.title}, reason=$reason")
            queueSnapshot()
        }

        override fun onPlaybackStateChanged(state: Int) {
            android.util.Log.d(TAG, "onPlaybackStateChanged: $state")
            queueSnapshot()
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            android.util.Log.d(TAG, "onDisconnected")
            this@WidgetMediaConnection.controller = null
            controllerFuture = null
            val empty = PlayerSnapshot()
            _uiState.value = empty
            persistAsync(empty)
        }
    }

    /**
     * Establishes the connection (and its Player.Listener) up front, fire-and-forget, so state
     * pushes start flowing as soon as the widget exists - instead of waiting for a button tap to
     * lazily trigger [connect]. Safe to call repeatedly; a no-op once already connected.
     */
    fun ensureConnected(context: Context) {
        android.util.Log.d(TAG, "ensureConnected called, already connected=${controller != null}")
        if (controller != null) return
        scope.launch { connect(context) }
    }

    /** Connects on first use, then reuses the same controller for every later call. */
    private suspend fun connect(context: Context): MediaController? {
        controller?.let { return it }
        appContext = context.applicationContext

        val future = controllerFuture ?: run {
            android.util.Log.d(TAG, "connect: building new MediaController")
            val token = SessionToken(appContext, ComponentName(appContext, MediaPlaybackService::class.java))
            MediaController.Builder(appContext, token)
                .setListener(controllerListener)
                .buildAsync()
                .also { controllerFuture = it }
        }

        return try {
            val connected = future.await()
            android.util.Log.d(TAG, "connect: success, isPlaying=${connected.isPlaying}, title=${connected.currentMediaItem?.mediaMetadata?.title}")
            if (controller == null) {
                controller = connected
                connected.addListener(playerListener)
                // Sync immediately - otherwise stale values from a previous session sit
                // untouched until the next play/pause/track-change event fires.
                queueSnapshot()
            }
            connected
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to connect", e)
            null
        }
    }

    /**
     * Runs a button-tap command against the live controller. [uiState] updates synchronously
     * (before this returns), so a widget that's currently visible reflects the tap immediately
     * via its own collectAsState() observer - no DataStore round-trip needed for that.
     *
     * Glance invokes ActionCallback.onAction() on a background dispatcher, but MediaController
     * only accepts calls from the thread it was built on (Main, since [connect] runs on
     * Dispatchers.Main.immediate) - touching it from any other thread throws
     * IllegalStateException. withContext(Main.immediate) hops back before touching the
     * controller at all.
     */
    suspend fun execute(context: Context, command: String) = withContext(Dispatchers.Main.immediate) {
        android.util.Log.d(TAG, "execute: command=$command")
        val c = connect(context) ?: run {
            android.util.Log.w(TAG, "execute: no controller, dropping command=$command")
            return@withContext
        }
        when (command) {
            "play_pause" -> if (c.isPlaying) {
                c.pause()
            } else {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                c.play()
            }
            "next" -> if (c.hasNextMediaItem()) c.seekToNextMediaItem()
            "previous", "prev" -> if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem()
        }
        queueSnapshot()
    }

    fun release() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        controllerFuture = null
    }

    private fun snapshotOf(c: MediaController): PlayerSnapshot {
        val title = c.currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
        val artist = c.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
        val albumArtPath = c.currentMediaItem?.mediaMetadata?.artworkUri?.toString() ?: ""
        val isPlaying = c.isPlaying
        val duration = c.duration
        val progress = if (duration > 0) c.currentPosition.toFloat() / duration.toFloat() else 0f
        return PlayerSnapshot(title, artist, albumArtPath, isPlaying, progress)
    }

    private fun queueSnapshot() {
        val c = controller ?: return
        val snapshot = snapshotOf(c)
        _uiState.value = snapshot
        persistAsync(snapshot)
    }

    /**
     * Best-effort DataStore write + updateAll(), fire-and-forget. Not what drives a visible
     * widget's updates (see class doc) - this only matters for the widget's next cold
     * provideGlance() read, after process death or once its live session has timed out.
     */
    private fun persistAsync(snapshot: PlayerSnapshot) {
        val context = appContext
        scope.launch {
            android.util.Log.d(TAG, "persistAsync: title=${snapshot.title}, isPlaying=${snapshot.isPlaying}")
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(MoodTuneWidget::class.java)
                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            set(WidgetKeys.TRACK_TITLE, snapshot.title)
                            set(WidgetKeys.ARTIST_NAME, snapshot.artist)
                            set(WidgetKeys.IS_PLAYING, snapshot.isPlaying)
                            set(WidgetKeys.ALBUM_ART_PATH, snapshot.albumArtPath)
                            set(WidgetKeys.CURRENT_PROGRESS, snapshot.progress)
                        }
                    }
                }
                MoodTuneWidget().updateAll(context)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to persist widget state", e)
            }
        }
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, MoreExecutors.directExecutor())
    }
}
