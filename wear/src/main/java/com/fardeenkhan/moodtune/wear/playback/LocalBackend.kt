package com.fardeenkhan.moodtune.wear.playback

/**
 * Wraps a `MediaController` bound to [WearMediaPlaybackService] - the watch's own local playback
 * engine, independent of the phone, routed to whatever Bluetooth audio device is paired directly
 * to the watch.
 *
 * TODO(§2.4): build/connect the `MediaController`, expose `mediaItemCount`/transport calls for
 * `WearPlaybackController`'s priority rule to read.
 *
 * See `implementation/WearPlan.md` §1.4, §2.4.
 */
class LocalBackend
