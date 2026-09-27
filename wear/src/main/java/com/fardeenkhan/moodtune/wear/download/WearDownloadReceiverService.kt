package com.fardeenkhan.moodtune.wear.download

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Handles everything the phone sends the watch outside of Controller Mode's transport commands:
 * incoming download requests
 * ([com.fardeenkhan.moodtune.core.wearsync.WearPaths.DOWNLOAD_REQUEST] +
 * per-song audio bytes over `ChannelClient`), and the watch-owns-playback lock's stop command
 * ([com.fardeenkhan.moodtune.core.wearsync.WearPaths.PHONE_COMMAND]).
 *
 * TODO(§2.4): on a download request, check `WearDownloadStore.hasRoomFor(...)`, then receive the
 * matching `ChannelClient` channel(s) and commit to the manifest only once the full byte count
 * matches; publish a fresh `WearStorageStatus` after.
 * TODO(§2.6): on `PhoneToWatchCommand.StopLocalPlayback`, stop `LocalBackend`'s session.
 * TODO: override `onChannelOpened` for the actual audio-byte transfer once §2.4 is implemented -
 * verify the current exact Wearable Data Layer manifest/registration requirements for
 * `ChannelClient` against Google's docs at that point rather than assuming this file's minimal
 * manifest registration (§0) is already sufficient.
 *
 * See `implementation/WearPlan.md` §2.4, §2.6.
 */
class WearDownloadReceiverService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        // TODO: route messageEvent.path (DOWNLOAD_REQUEST vs PHONE_COMMAND) to the right handler.
    }
}
