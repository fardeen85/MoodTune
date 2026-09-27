# Media Restore Fix — Now Playing empty after process death

## Scenario
Music is playing via `MediaPlaybackService` (foreground service). The app process dies
(OS memory pressure, an OEM battery-killer nuking the process when the recents card is
swiped, or a crash) while playback was still active. User reopens the app and the Now
Playing screen shows nothing, even though the notification/session may briefly have
suggested something was playing.

## What actually happens (root cause)

`MusicPlayerManager` is a Koin `single` (`app/di/AppModules.kt:34`), and `MainActivity`
only calls `release()` when `isFinishing` (`MainActivity.kt:64-66`). So the common
case — user backgrounds the app, service plays on, user comes back — already works
fine: the singleton is never torn down, its `MediaController` stays connected, and
`playbackState` still reflects reality.

The bug only shows up when the **whole process dies** while the service is supposed to
be playing:

- The process — Activity, Koin container, `MediaPlaybackService`, and its `ExoPlayer` —
  all die together, since it's one process.
- User reopens the app → fresh process → Koin creates a **new** `MusicPlayerManager` →
  `init{}` builds a new `MediaController` via `SessionToken` → this **connects
  successfully** (binding auto-creates the service), so it's not a connection failure.
- It binds to a **cold, freshly-created `ExoPlayer`** with an empty queue, because
  nothing in `MediaPlaybackService.onCreate()`
  (`infrastructure/MediaPlaybackService.kt:24-37`) or
  `MusicPlayerManager.setupController()` ever restores prior state — it only ever
  lived in memory.

So "Now Playing is empty" isn't a reconnect failure, it's a **missing state-restoration**
problem: there is nothing to reconnect *to*.

### Secondary, unrelated issue
`controllerFuture?.buildAsync()` is async (`MusicPlayerManager.kt:58-66`), and
`_playbackState` starts as default `PlaybackState()` until that future resolves. If a
screen reads `playbackState` before the callback fires, it can't tell "still
connecting" apart from "genuinely nothing playing" — a brief flash-of-empty-state on
every normal cold reconnect, independent of the process-death case above.

## Approaches

1. **Media3 Playback Resumption (the standard fix for this exact scenario)**
   Implement `MediaSession.Callback.onPlaybackResumption()` in `MediaPlaybackService`,
   returning a `MediaItemsWithStartPosition` (queue + start index + position)
   reconstructed from persisted state. Media3 1.5.1 (current version, see
   `gradle/libs.versions.toml:24`) supports this natively — it's what
   Spotify/YT Music-style apps rely on so a cold-started session comes back
   paused-but-populated instead of empty.

2. **Persist a minimal playback snapshot**
   Save current queue song IDs, current index, position, and `isPlaying` to DataStore
   (or a small Room table) — write on `onIsPlayingChanged` / `onMediaItemTransition` /
   pause, not on every progress tick. This is the data source `onPlaybackResumption`
   reads from. `SongEntity` already has everything needed to rehydrate `Song` objects
   by ID, so the snapshot itself can just be a list of IDs + index + position.

3. **Distinguish "connecting" from "idle" in `PlaybackState`**
   Add `isConnecting: Boolean` (true until `controllerFuture` resolves at least once),
   so `SongDetailViewModel` / `PlaylistViewModel` can show a loading state instead of
   "nothing's playing" during the brief legitimate reconnect window. Cheap, and fixes
   the flash-of-empty case independent of #1/#2.

4. **Foreground service type + battery-optimization exemption**
   Won't stop a determined OEM killer, but reduces how often the process-death case
   is triggered at all. Not a code fix — more a "reduce frequency" lever.

## Recommendation
Do #2 + #1 together — that's the actual fix for the described scenario. Treat #3 as a
quick, low-risk companion fix for the separate flash-of-empty-state issue. #4 is a
minor mitigation, not a real solution.
