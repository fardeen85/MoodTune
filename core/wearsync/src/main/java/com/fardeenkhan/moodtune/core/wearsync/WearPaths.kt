package com.fardeenkhan.moodtune.core.wearsync

/**
 * Wearable Data Layer path constants shared by the phone (`app`) and watch (`:wear`) modules, so
 * neither side can drift from the other on what a path means.
 *
 * See `implementation/WearPlan.md` §2.1.
 */
object WearPaths {
    /** DataClient, watch subscribes: passive mirror of the phone's current playback state. */
    const val PLAYBACK_STATE = "/moodtune/playback_state"

    /** MessageClient, watch -> phone: transport commands and "start this existing playlist." */
    const val COMMAND = "/moodtune/command"

    /** DataClient, watch subscribes: the phone's existing playlists, low-frequency updates. */
    const val PLAYLISTS = "/moodtune/playlists"

    /** MessageClient, phone -> watch: metadata for a user-selected batch of songs to download. */
    const val DOWNLOAD_REQUEST = "/moodtune/download_request"

    /** DataClient, phone subscribes: the watch's current downloaded-storage usage. */
    const val STORAGE_STATUS = "/moodtune/storage_status"

    /** ChannelClient path prefix, phone -> watch: append a songId for the actual audio bytes. */
    const val TRANSFER_CHANNEL_PREFIX = "/moodtune/transfer/"

    /** DataClient, phone subscribes: whether the watch is currently playing locally, and what. */
    const val WATCH_ACTIVE_PLAYBACK = "/moodtune/watch_active_playback"

    /** MessageClient, phone -> watch: the watch-owns-playback lock's "give back control" action. */
    const val PHONE_COMMAND = "/moodtune/phone_command"
}
