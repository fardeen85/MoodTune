package com.fardeenkhan.moodtune.wear.playback

import com.fardeenkhan.moodtune.core.wearsync.WearPlaybackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unifies [LocalBackend] and [RemoteMirrorBackend] behind one [WearPlaybackSnapshot] - the
 * watch-side equivalent of the phone's `MusicPlayerManager`. [LocalBackend] takes priority
 * whenever it has an active/loaded queue; otherwise [RemoteMirrorBackend]'s mirrored state is
 * shown.
 *
 * TODO(§2.4): implement the priority rule and route transport commands to whichever backend is
 * currently active.
 *
 * See `implementation/WearPlan.md` §2.4.
 */
class WearPlaybackController(
    private val localBackend: LocalBackend,
    private val remoteMirrorBackend: RemoteMirrorBackend
) {
    private val _state = MutableStateFlow(WearPlaybackSnapshot())
    val state: StateFlow<WearPlaybackSnapshot> = _state.asStateFlow()
}
