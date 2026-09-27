package com.fardeenkhan.moodtune.wear.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * The watch's own, much smaller sibling of the phone's `infrastructure.MediaPlaybackService` - a
 * local-file-only `ExoPlayer` for standalone playback of songs already downloaded via
 * [com.fardeenkhan.moodtune.wear.download.WearDownloadStore]. No YouTube data source, and no
 * crash-resumption/repeat-count machinery - both confirmed skipped for MVP.
 *
 * TODO(§2.4): build the `ExoPlayer` + [MediaSession] in `onCreate()`, publish
 * `WatchActivePlaybackStatus` on start/stop per §1.5/§2.6.
 *
 * See `implementation/WearPlan.md` §1.4, §2.4, §2.5.
 */
class WearMediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
