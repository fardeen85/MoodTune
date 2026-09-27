package com.fardeenkhan.moodtune.wear.playback

/**
 * Mirrors the phone's playback session over the Wearable Data Layer - "Controller Mode." Sends
 * [com.fardeenkhan.moodtune.core.wearsync.WearCommand]s over
 * [com.fardeenkhan.moodtune.core.wearsync.WearPaths.COMMAND] and subscribes to
 * [com.fardeenkhan.moodtune.core.wearsync.WearPaths.PLAYBACK_STATE] only while the Now Playing
 * screen is started.
 *
 * TODO(§2.4): `DataClient` subscription (register in onStart/unregister in onStop),
 * `MessageClient` command sending, elapsed-time extrapolation between sync events.
 *
 * See `implementation/WearPlan.md` §1.1, §2.2, §2.4.
 */
class RemoteMirrorBackend
