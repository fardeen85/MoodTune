# Playback Resilience Fixes

Implements the fix described in `mediaRestore_fix.md` (#1 + #2 + #3), plus two related
gaps found while wiring it up. All of this lives in `MediaPlaybackService` (infrastructure),
`MusicPlayerManager` (core/utils), and one new domain use case — see below for exactly where.

## 1. Queue/position now survives process death
**Why:** `MusicPlayerManager` and `MediaPlaybackService`'s `ExoPlayer` only ever lived in memory.
If the whole process died while playing (OEM killer, OOM, crash), reopening the app connected a
*new* `MediaController` successfully, but to a cold `ExoPlayer` with an empty queue — Now Playing
showed nothing, not because reconnecting failed, but because there was nothing to reconnect to.

**What:** `MediaPlaybackService` persists a minimal snapshot (song IDs, index, position,
isPlaying) to DataStore on real events only (`onIsPlayingChanged`, `onMediaItemTransition`, seeks)
— not per progress tick. `RestorePlaybackSessionUseCase` (domain) rehydrates it: looks songs up
by ID, drops any deleted since the snapshot, remaps the current index if an earlier song in the
queue got dropped, returns `null` (and clears the snapshot) if nothing survives.

**Layering:** `PlaybackSnapshot` (domain model) → `PlaybackSessionRepository` (domain interface)
→ `PlaybackSnapshotDataSource` (infrastructure, raw DataStore) → `PlaybackSessionRepositoryImpl`
(data). `MediaPlaybackService` only ever talks to the domain interfaces.

## 2. Two different triggers use the restored queue, not one
**Why:** Media3's `MediaSession.Callback.onPlaybackResumption()` — the standard hook for this —
only fires when a controller explicitly calls `.play()` on an empty timeline (verified against
the Media3 1.5.1 source: `MediaSessionImpl.handleMediaControllerPlayRequest`). That covers a
Bluetooth play button, Android's lock-screen resume card, Android Auto — but **not** just
reopening the app, since nothing in the app calls `play()` automatically on reconnect.

**What:** Two separate call sites, both feeding the same use case:
- `MediaPlaybackService.onPlaybackResumption()` — external "play" requests (system/Bluetooth/Auto).
- `MusicPlayerManager.restoreQueueIfEmpty()` — runs when *our own* controller connects to an
  empty timeline (i.e. the user just reopened the app). Calls `setMediaItems()` + `prepare()`,
  deliberately not `play()` — loads the last song paused, doesn't start audio the user didn't ask for.

## 3. `onTaskRemoved` no longer kills playback on swipe
**Why:** Previous behavior (documented as "intentional" in `CLAUDE.md`) unconditionally paused +
`stopSelf()`'d on swipe-from-recents. Spotify/YT Music don't — a foreground service with an active
notification isn't reclaimed by the OS just because the task was removed; there was no need to
tear it down ourselves.

**What:** `onTaskRemoved` now only pauses + stops when there's nothing to keep alive for (already
paused, or empty queue). If actively playing, service/session are left running. Media3's own
`MediaNotificationManager` already makes the notification non-dismissible while playing and
dismissible-but-still-registered while paused — no extra code needed for that part.

## 4. Notification now opens the app when tapped
**Why:** `MediaSession.Builder` never had `.setSessionActivity(...)` set, so the
playing/paused/stale notification had no guaranteed content-intent.
**What:** `MediaPlaybackService.onCreate()` builds a `PendingIntent` via
`packageManager.getLaunchIntentForPackage(packageName)` (not a direct `MainActivity` reference —
`infrastructure` can't depend on `app`) and passes it to `setSessionActivity()`.

## 5. Flash-of-empty-state on every normal cold reconnect (unrelated bug)
**Why:** `controllerFuture.buildAsync()` is async; `playbackState` sits at its default (looks
identical to "nothing playing") until it resolves — a screen reading state during that window
can't tell "still connecting" from "genuinely idle."
**What:** `PlaybackState.isConnecting` (default `true`), flipped `false` once the controller
connects or fails. Not wired into any screen yet — available for a screen to check if needed.

## 6. SongDetailViewModel: partial merge to the MVI state convention
**Why:** The rest of the app follows one convention per screen: a single `MutableStateFlow<XxxState>`
as the sole source of truth, updated via `.update { it.copy(...) }` (see `HomeState`, `PlaylistState`).
`SongDetailViewModel` instead had three independent `MutableStateFlow`s (`_dominantColor`,
`_lyricsState`, `_hasCachedLyrics`) plus two pass-throughs from `MusicPlayerManager`
(`state: StateFlow<PlaybackState>`, `currentQueue`).

**What we did *not* do:** fold `PlaybackState` into a unified `SongDetailState` too. Two reasons:
- `PlaybackState` ticks every second while playing (`MusicPlayerManager.startProgressUpdate()`).
  Folding it in means any Composable reading *any* field of the unified state — even just
  `lyricsState` — recomposes every second, unless every call site adds its own `derivedStateOf`
  per field. The current split avoids that entirely.
- `PlaybackState` is `MusicPlayerManager`'s app-wide singleton state, also read by the Playlist
  tab's `NowPlayingIndicator` and the widget's `WidgetMediaConnection`. It isn't this ViewModel's
  state to own; wrapping it here would either duplicate it (sync-bug risk) or just add a layer of
  indirection other consumers can't use anyway.

**What we did do:** merged the three screen-owned, low-frequency concerns into one
`SongDetailState` (`dominantColor`, `lyricsState`, `hasCachedLyrics`), backed by a single
`_state: MutableStateFlow<SongDetailState>` mutated only via `.update { it.copy(...) }`. The three
public properties (`dominantColor`, `lyricsState`, `hasCachedLyrics`) stay as separate `StateFlow`s
— each a `.map { }.distinctUntilChanged()` projection of `_state` — so existing Composable call
sites in `SongDetail.kt` didn't need to change, and each still only recomposes on changes to the
field it actually reads.

## Incidental refactor
`Song.toMediaItem()` extracted from `MusicPlayerManager.playPlaylist` into `core/utils` (shared
with `MediaPlaybackService`'s restoration paths, which need to build the identical `MediaItem`
shape). Required adding an `infrastructure → core/utils` module dependency — safe, no cycle,
since `core/utils` only depends on `domain`.
