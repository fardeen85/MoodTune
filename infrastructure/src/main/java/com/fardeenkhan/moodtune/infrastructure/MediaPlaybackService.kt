package com.fardeenkhan.moodtune.infrastructure

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fardeenkhan.moodtune.domain.model.PlaybackSnapshot
import com.fardeenkhan.moodtune.domain.model.SnapshotSong
import com.fardeenkhan.moodtune.domain.repo.PlaybackSessionRepository
import com.fardeenkhan.moodtune.domain.usecase.RestorePlaybackSessionUseCase
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import com.fardeenkhan.moodtune.core.utils.REPEAT_COUNT_EXTRA_KEY
import com.fardeenkhan.moodtune.core.utils.toMediaItem
import org.koin.android.ext.android.inject

/**
 * Foreground [MediaSessionService] that owns the single [ExoPlayer] instance and its
 * [MediaSession] for the whole app - nothing else. It is the one process-wide source of truth
 * for playback; it never reaches out to app UI directly.
 *
 * Anything that needs playback state (the app's Now Playing screen via `MusicPlayerManager`, or
 * the home screen widget via `WidgetMediaConnection`) connects its own `MediaController` to this
 * session by resolving a `SessionToken` for this service - the standard Media3 client/session
 * split. This service only ever pushes state through the `MediaSession`/`Player` APIs (which
 * `MediaController`s observe); it does not hold references to any UI-layer class.
 *
 * Two extra responsibilities live here beyond "play audio":
 * - **Crash/process-death resumption**: [PlaybackSnapshotListener] persists the queue on every
 *   real state change, and [PlaybackResumptionCallback] rebuilds it via
 *   [restorePlaybackSessionUseCase] when a controller reconnects to a cold-started session.
 * - **Per-song repeat count**: [RepeatSongListener] replays the current item in place a fixed
 *   number of times (a value the user set per-song, carried in via [REPEAT_COUNT_EXTRA_KEY])
 *   before letting the queue advance normally.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

    /** The single session for this service's lifetime. Null before [onCreate] / after [onDestroy]. */
    private var mediaSession: MediaSession? = null

    /**
     * Backs the async work this service kicks off and forgets (snapshot persistence, session
     * restoration). [Dispatchers.IO] because both paths hit DataStore/Room; [SupervisorJob] so
     * one failed save doesn't cancel unrelated in-flight work. Never explicitly cancelled - it
     * dies with the process, which is fine since nothing here needs to outlive the service.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** DataStore-backed store for the last-known queue/position; see [PlaybackSnapshotListener]. */
    private val playbackSessionRepository: PlaybackSessionRepository by inject()

    /** Rebuilds a playable queue from the last persisted snapshot; see [PlaybackResumptionCallback]. */
    private val restorePlaybackSessionUseCase: RestorePlaybackSessionUseCase by inject()

    /**
     * Builds the [ExoPlayer] and [MediaSession] once, when the service process is first created
     * (not on every controller connection - see [onGetSession] for that).
     */
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MediaPlaybackService", "onCreate")
        val player = ExoPlayer.Builder(this).build().apply {
            // USAGE_MEDIA + CONTENT_TYPE_MUSIC gives ExoPlayer standard music audio-focus
            // handling for free: duck (not stop) for transient interruptions like notification
            // dings, pause for other music apps, honor the ringer/silent-mode audio stream.
            // `true` = ExoPlayer manages audio focus itself; we never call requestAudioFocus.
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            addListener(PlaybackSnapshotListener())
            addListener(RepeatSongListener())
        }
        // Tapping the media notification (or the widget's album art) should reopen the app at
        // its existing task rather than launching a duplicate one - hence FLAG_UPDATE_CURRENT
        // and re-targeting the launch intent at this package explicitly.
        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            setPackage(packageName)
        }
        val sessionActivityPendingIntent = sessionActivityIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(PlaybackResumptionCallback())
            .apply { sessionActivityPendingIntent?.let { setSessionActivity(it) } }
            .build()
    }

    /**
     * Called by the framework every time a [MediaController] (in-app player UI, the widget, or
     * an external surface like Android Auto/Bluetooth) attaches to this service. Every caller
     * gets the same single [mediaSession] - this app has no need to scope playback per-client.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        android.util.Log.d("MediaPlaybackService", "onGetSession: package=${controllerInfo.packageName}")
        return mediaSession
    }

    /**
     * Called when the user swipes the app's task away from Recents.
     *
     * Matches Spotify/YT Music: swiping the task away doesn't stop playback. If something is
     * actively playing, leave the service + session running (Media3 already keeps the
     * notification non-dismissible/"ongoing" while playing, so the OS won't reclaim it just for
     * this). Only tear down when there's nothing to keep alive for - paused or an empty queue -
     * in which case we pause explicitly (defensive; should already be paused/idle) and call
     * [stopSelf] so the foreground notification doesn't linger with nothing to show for it.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            player.pause()
            stopSelf()
        }
    }

    /**
     * Releases the [ExoPlayer] and [MediaSession] when the service process is actually torn down
     * (after [stopSelf] above, or the system reclaiming the process). Order matters: the player
     * must be released before the session, and [mediaSession] is nulled out afterward so any
     * late [onGetSession] call during shutdown can't hand out a half-released session.
     */
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * Persists queue/position/isPlaying on real state changes (not per progress tick), so a
     * fresh process has something for [PlaybackResumptionCallback] to rebuild from after the
     * whole app was killed mid-playback. See `mediaRestore_fix.md`.
     *
     * Each callback below is a Media3 [Player.Listener] hook fired on a distinct kind of state
     * change; all of them funnel into the same [saveSnapshot], so whichever one fires last wins
     * and the persisted snapshot always reflects the player's current queue/position.
     */
    private inner class PlaybackSnapshotListener : Player.Listener {
        /** Play/pause toggled - save so a resumed session comes back in the same play state. */
        override fun onIsPlayingChanged(isPlaying: Boolean) = saveSnapshot()

        /** The current item changed (auto-advance, manual skip, or a brand-new queue was set). */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = saveSnapshot()

        /**
         * Fires on many kinds of position jumps; only a user-initiated seek is worth persisting
         * here; playback-driven position changes (e.g. normal progress) don't come through this
         * callback at all, and other discontinuity reasons (like an auto item transition) are
         * already captured by [onMediaItemTransition].
         */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) saveSnapshot()
        }

        /**
         * When the whole queue finishes playing there's nothing meaningful left to resume into,
         * so the stale snapshot is cleared rather than left pointing at a finished queue.
         */
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                scope.launch { playbackSessionRepository.clearSnapshot() }
            }
        }

        /**
         * Builds a [PlaybackSnapshot] from the player's current queue and writes it via
         * [playbackSessionRepository]. Each queue entry's repeat count is read back out of its
         * [MediaItem]'s [androidx.media3.common.MediaMetadata.extras] (see [REPEAT_COUNT_EXTRA_KEY])
         * rather than looked up from the DB, since [RepeatSongListener] only mutates the player's
         * live [Player.getRepeatMode], not the [MediaItem] itself - the extras stay the
         * authoritative source of each song's configured repeat count.
         *
         * No-ops on an empty queue (nothing worth snapshotting) rather than persisting an empty
         * snapshot that would just overwrite a still-useful previous one.
         */
        private fun saveSnapshot() {
            val player = mediaSession?.player ?: return
            val songs = (0 until player.mediaItemCount).map { index ->
                val item = player.getMediaItemAt(index)
                SnapshotSong(
                    songId = item.mediaId,
                    repeatCount = item.mediaMetadata.extras?.getInt(REPEAT_COUNT_EXTRA_KEY, 1) ?: 1
                )
            }
            if (songs.isEmpty()) return
            val snapshot = PlaybackSnapshot(
                songs = songs,
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition,
                isPlaying = player.isPlaying
            )
            scope.launch { playbackSessionRepository.saveSnapshot(snapshot) }
        }
    }

    /**
     * Repeats the current [MediaItem] in place (via [Player.REPEAT_MODE_ONE]) for the number of
     * times carried in its [REPEAT_COUNT_EXTRA_KEY] extra, then lets playback advance normally.
     *
     * `REPEAT_MODE_ONE` only governs automatic end-of-item continuation - `seekToNext`/
     * `seekToPrevious` (manual skip, wired from [com.fardeenkhan.moodtune.core.utils.MusicPlayerManager])
     * always navigate to the actual adjacent item regardless of repeat mode, so a manual skip
     * abandons any remaining repeats on the current song rather than being blocked by them.
     */
    private inner class RepeatSongListener : Player.Listener {
        /**
         * How many *more* automatic replays of the current item are left. Decremented each time
         * [Player.REPEAT_MODE_ONE] loops the item back to itself; once it hits zero, repeat mode
         * is turned off so the queue advances normally on the next natural completion.
         */
        private var remainingRepeats = 0

        /**
         * Single hook that both drives the repeat countdown and re-arms it for the next song.
         *
         * Two cases, distinguished by [reason]:
         * - [Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT]: ExoPlayer itself looped the current
         *   item back to its start because [Player.repeatMode] was [Player.REPEAT_MODE_ONE].
         *   This is *not* a move to a different song, so just decrement [remainingRepeats] and,
         *   once it runs out, flip repeat mode off so the *next* completion advances the queue.
         * - Any other reason (auto-advance past a non-repeating item, a manual skip via
         *   `seekToNext`/`seekToPrevious`, or a brand-new queue being set): this is a genuine
         *   move onto a different song, so any repeats left on the *old* song are discarded, and
         *   the repeat count for the *new* song ([mediaItem]) is read from its
         *   [REPEAT_COUNT_EXTRA_KEY] extra and used to decide whether to re-arm repeat mode.
         *   A count of 1 (or missing) means "play once" - no special handling needed.
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = mediaSession?.player ?: return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                remainingRepeats--
                if (remainingRepeats <= 0) player.repeatMode = Player.REPEAT_MODE_OFF
                return
            }

            // A real advance (auto, manual skip, or a fresh queue) - reset the repeat state for
            // whichever song we actually landed on, discarding any repeats left on the old one.
            val repeatCount = mediaItem?.mediaMetadata?.extras?.getInt(REPEAT_COUNT_EXTRA_KEY, 1) ?: 1
            if (repeatCount > 1) {
                remainingRepeats = repeatCount - 1
                player.repeatMode = Player.REPEAT_MODE_ONE
            } else {
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }

    /**
     * Rebuilds the queue for a cold-started session — Media3's standard playback-resumption hook.
     *
     * The framework calls [onPlaybackResumption] when a [MediaController] (e.g. the widget, or
     * the app reopening) connects to this session *before* [onCreate] ever set up a real queue -
     * i.e. the whole process was killed while a queue existed, and this is a fresh instance with
     * an empty player. Without this callback, that controller would just see "nothing playing".
     */
    private inner class PlaybackResumptionCallback : MediaSession.Callback {
        /**
         * Returns a [ListenableFuture] (Media3's callback contract here, since resolving the
         * queue requires an async DB/DataStore read) that completes with the restored queue.
         *
         * Delegates the actual rebuilding to [restorePlaybackSessionUseCase], which turns the
         * last persisted [PlaybackSnapshot] back into real [com.fardeenkhan.moodtune.domain.model.Song]s
         * (already stripped of ids that no longer exist, e.g. songs deleted since the snapshot
         * was taken). Each restored song is converted to a [MediaItem] via `toMediaItem()` -
         * the same conversion used everywhere else a queue is built, so a resumed session's
         * items are indistinguishable from a freshly-started one.
         *
         * If nothing was persisted (or nothing could be restored), completes with an empty
         * queue at index 0 / position 0 rather than failing the future - the controller then
         * simply sees an idle player, which is the correct "nothing to resume" state.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val restored = restorePlaybackSessionUseCase()
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        restored?.songs?.map { it.toMediaItem() } ?: emptyList(),
                        restored?.startIndex ?: 0,
                        restored?.startPositionMs ?: 0L
                    )
                )
            }
            return future
        }
    }
}
