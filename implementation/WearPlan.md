# Wear OS Plan — MoodTune Companion

**Revision history**
- **2026-09-27, pass 1**: Controller Mode only (watch never plays audio).
- **2026-09-27, pass 2**: added standalone offline playback — phone-to-watch file transfer with
  storage management, since Fardeen confirmed the watch must play audio itself, not just mirror
  the phone.
- **2026-09-27, pass 3**: two corrections from Fardeen —
  1. **Gemini-generated playlists are out of scope for the wear app.** The domain/infrastructure
     code for it exists (`GeneratePlaylistUseCase`, `GeminiAPIDataSource`) and `HomeViewModel`
     calls it, but Fardeen confirmed this functionality **is not actually connected/usable on the
     mobile app right now**. Nothing about the wear plan should assume it works — the watch talks
     to **existing, already-created playlists** the phone already has stored, full stop. No
     watch-triggered generation of anything new. If Gemini generation ships for real on mobile
     later, adding a watch-triggered path is a separate future task, not something to build on
     top of an unconnected feature now.
  2. **Download selection is per-song, not per-playlist.** The user opens a playlist and picks
     which individual songs to send to the watch — not "send this whole playlist." This changes
     the "Send to Watch" screen (2.3) and simplifies the storage-budget UX (2.3/2.4).
- **2026-09-27, pass 4 (this revision)**: answers to the three open questions from pass 3 —
  1. **Crash recovery for local playback**: keep it simple for MVP. Confirmed — no
     crash-resumption for the watch's local queue (2.5); a killed process just restarts clean.
  2. **Watch-owns-playback lock (new spec)**: when the watch is playing its own downloaded
     content, the watch is the sole source of truth for audio, regardless of whether the phone is
     currently connected. Since the phone's own queue can be a *different* playlist than whatever
     the watch is playing, the phone needs to (a) pause its own playback rather than risk two
     devices producing audio at once, (b) show what's actually playing, and (c) keep its own
     queue **locked/frozen**, not cleared, until either the watch stops on its own or the user
     explicitly stops it from the phone. New section 1.5, new architecture in 2.6.
  3. **OS floor**: `minSdk` targets Wear OS 3, `targetSdk` targets Wear OS 7 (Fardeen's primary
     test device is a Pixel Watch 2 on Wear OS 7). See 0 for how this maps to the existing gradle
     config.
- **2026-09-27, pass 5 (this revision)**: two more refinements from Fardeen —
  1. **No dedicated "watch is playing" screen.** Per standard companion-app UX (Spotify
     Connect-style), the phone's *existing* Now Playing screen just becomes source-aware — same
     "current media" surface, a small "Playing on Watch" banner, watch's media shown instead of
     stale phone data, transport controls swapped for a single "Stop on Watch" action. Reworded
     1.5/2.6 accordingly; nothing about the underlying status/command mechanism changed, only the
     phone-side UI shape.
  2. **This document is now organized as a section-by-section implementation checklist** (§5) so
     progress can be tracked one section at a time during implementation, instead of separate
     prose "architecture" and "phase order" sections that didn't map 1:1 to checkable work units.
- **2026-09-27, pass 6 (this revision)**: answers to both remaining open questions — none left
  open at this time.
  1. **Stop-on-Watch stays a single tap, no confirmation dialog.** Confirmed as originally leaned.
  2. **The phone must actively dismiss its notification while the watch owns playback, not just
     pause.** Fardeen's reasoning: a paused-but-still-visible media notification is an accidental
     resume surface — the user could tap play on it and start a *second* session on the phone
     while the watch is already playing, exactly the dual-audio problem 1.5 exists to prevent.
     Pausing alone isn't enough; the notification itself has to go away. Reworded 2.6 accordingly.

  Every section below reflects all corrections through this pass; nothing upstream of this note
  should be treated as current except where explicitly still true.

## 0. Ground truth before planning

Verified directly against code on `design-revamp/mobile`:

- `wear/` is still the stock Android Studio Wear Compose template — no Now Playing UI, no sync,
  no image loading, no Media3 dependency at all. `wear/build.gradle.kts` declares
  `play-services-wearable` but nothing uses it.
- `:wear` is already included in `settings.gradle.kts:39`.
- Two older docs — `plan/wear_os_implementation_plan.md` and root `wearPlan.md` — are stale and
  don't cover any of this (standalone playback, storage management, or the Gemini/selection
  corrections above). **This document supersedes both.**
- Gemini playlist generation exists in code (`domain/.../GeneratePlaylistUseCase.kt`,
  `infrastructure/.../GeminiAPIDataSource.kt`, wired into `HomeViewModel`) but per Fardeen is not
  functionally connected on mobile today — treated as non-existent for this plan's purposes (see
  revision note above). `PlaylistRepository`/`Playlist` (which already exist and are populated
  from on-device songs regardless of generation) are the actual source of truth the wear app talks
  to.
- `WidgetMediaConnection` (`app/.../widget/WidgetMediaConnection.kt`) solves an *easier* adjacent
  problem: it binds a `MediaController` straight to `MediaPlaybackService` because the widget
  lives in the **same process** as the phone app. A watch is a different device, a different
  process, can't read the phone's `filesDir`, and needs to actually *play audio itself*
  sometimes. Its lessons on thread affinity and "sync immediately on reconnect" still transfer
  (see 4); its architecture does not.
- The phone's real playback engine, `infrastructure/.../MediaPlaybackService.kt`, is tightly
  coupled to phone-only concerns (`PlaybackSessionRepository`, `RestorePlaybackSessionUseCase`,
  `core:utils`). The watch needs its **own**, much simpler `MediaSessionService` (2.4), not a
  shared instance — `:wear` has no clean path to depend on `infrastructure`.
- `media3-exoplayer`, `media3-session`, `media3-common` (v1.5.1) are already in
  `gradle/libs.versions.toml`, just not wired into `wear/build.gradle.kts` yet.
- Album art: phone caches embedded art at `context.filesDir/album_art/{md5(filePath)}.jpg`
  ([[project-album-art]]). The watch can't read this path — art has to physically cross the Data
  Layer for either playback mode below.
- **OS floor**: `wear/build.gradle.kts` already sets `minSdk = 30`, which matches the Wear OS 3
  floor Fardeen wants — no change needed there. `compileSdk`/`targetSdk` are already `37`;
  confirm at implementation time that this actually corresponds to the Wear OS 7 tooling
  Fardeen's primary test device (a Pixel Watch 2 running Wear OS 7) needs — don't assume the
  current number is correct just because it matches the phone module, since Wear OS and phone
  Android API numbering don't always track each other 1:1.

## 1. What the wear app actually does

Four things, kept tightly scoped to "offline local player companion for playlists that already
exist," not "phone-free Spotify" and not "a watch UI for an AI feature that doesn't work yet":

### 1.1 Show what's playing + control it (Controller Mode)

When the phone is playing (from its local library, organized into existing on-device playlists)
and the watch hasn't been asked to play something of its own, the watch mirrors that session:
title, artist, art, play/pause/next/previous, progress. No audio ever touches the watch's
speakers/radio for this mode.

### 1.2 Ambient mode while playing

Applies uniformly to *whichever* session is active (mirrored from the phone, or playing locally
off the watch's own storage — 1.3/1.4). Simplified, high-contrast, pure-black layout; progress
indicator hidden or updated at a much lower refresh rate; no new work started while ambient.
First-class from the start (2.4), not a later polish pass.

### 1.3 Phone → watch file transfer, user-picked songs, watch storage respected

The user opens an **existing playlist** on the phone and selects specific songs within it —
individually, not "the whole playlist" — to send to the watch. Selected songs' audio files
transfer over the Wearable Data Layer so the watch can play them without the phone present.

Because a watch has small, shared flash storage other apps and the OS also need:
- A **fixed, hardcoded cap** on how much space MoodTune's downloads may use on the watch (e.g.
  `500 MB` — an implementation constant, not a setting).
- A **safety margin** reserved against the watch's actual free space regardless of the cap (e.g.
  never write if fewer than `150 MB` would remain free afterward). Both numbers live in
  `core:wearsync` (2.1) so phone and watch agree without duplicating them.
- Because selection is per-song and manual, the "Send to Watch" screen can show a **live running
  total** against the remaining budget as the user checks/unchecks songs — the user directly
  controls what fits rather than being told after the fact that a whole playlist didn't fit (see
  2.3 — this is a real simplification the per-song correction buys over the earlier per-playlist
  design).
- The watch is the source of truth for what it's actually storing (the phone can't see the
  watch's filesystem) — it reports usage back over the Data Layer so the "Send to Watch" screen
  reflects reality, not a guess.

### 1.4 The "immersive" piece: real standalone playback

Once specific songs are downloaded, the watch can play them **through its own local player**,
independent of the phone — its own `MediaSessionService`, its own audio focus, routed to
whatever Bluetooth audio device is paired *to the watch* (earbuds), not relayed through the
phone.

### 1.5 Watch-owns-playback lock (new in pass 4)

Standalone playback creates a real problem the earlier passes didn't account for: the phone can
have its own playlist loaded (possibly mid-playback, possibly just paused-and-idle) while the
watch independently starts playing a *different* downloaded playlist. Left alone, that's two
devices potentially producing audio for two different playlists at once — confusing at best.

The fix, as specified by Fardeen — and refined once more (pass 5) for standard Wear OS
companion-app UX rather than inventing a bespoke lock screen:
- **Only one device plays audio at a time, full stop** — the two sessions never do different
  things simultaneously.
- The watch is always the authoritative audio source once it starts local playback — this holds
  whether or not the phone happens to be connected at that moment. The instant it starts, the
  **phone pauses its own playback** (if it was playing) — it does **not** clear its queue, just
  freezes it, so resuming later picks up exactly where it left off.
- **The phone's media notification is dismissed while locked, not just paused (pass 6).** A
  paused-but-still-visible notification is an accidental resume surface — tapping play on it would
  start a second session on the phone while the watch is already playing, exactly the dual-audio
  outcome this whole feature exists to prevent. The notification reappears (reflecting the frozen,
  paused queue) once the lock clears.
- **No separate/dedicated screen on the phone.** The phone's *existing* Now Playing screen simply
  becomes source-aware, the same way the watch's own Now Playing screen already is (2.4): it
  keeps showing "current media," it just displays whichever session is actually active. When
  that's the watch, the screen shows the watch's playlist/song (which can genuinely differ from
  what the phone had loaded) with a small **"Playing on Watch"** banner/indicator — the familiar
  "Now playing on [device]" pattern from Spotify Connect-style apps — plus a single **"Stop on
  Watch"** action in place of its own transport controls, since the phone can't drive the watch's
  session directly, only ask it to stop.
- **Symmetric direction, unchanged from earlier passes**: when the *phone's* queue is the one
  playing, the watch's Now Playing screen shows the phone's playback and standard transport
  controls, exactly like any normal Wear OS media companion app — this is just the existing
  Controller Mode / `RemoteMirrorBackend` behavior (2.2/2.4), reaffirmed here as the mirror image
  of the watch-owns-playback case.
- The lock clears only two ways: the watch's local session ends on its own (playlist finishes,
  user stops it on the watch), or the user taps **"Stop on Watch"** on the phone — no automatic
  "phone wins" behavior beyond that explicit action.
- See 2.6 for the concrete mechanism (new status/command paths in `core:wearsync`).

**Explicitly still out of scope — don't build these unless asked:**
- **No Gemini/AI playlist generation anywhere in the wear flow** (new in this revision) — not
  reachable from the watch, not a target of "send to watch," full stop, until it's actually wired
  up and usable on mobile.
- No automatic/rolling download cache — downloads only happen when the user explicitly picks
  songs to send.
- No user-facing storage-cap setting — the cap is an internal constant.
- No synchronized simultaneous playback across both devices — local and remote sessions are two
  independent things, never the same audio at the same time on both speakers.
- No account system, multi-watch support, or general library browsing/search on the watch —
  content reaches the watch only via "open a playlist, pick songs, send."
- No Horologist unless a specific gap forces it — see 2.5.

## 2. Architecture

### 2.1 Shared module: `core:wearsync`

Both apps need to agree on message paths, DTO shapes, and the storage-budget numbers from 1.3 —
duplicating any of these invites drift, the concrete trigger the architecture skill gives for
splitting something into its own module.

- Pure Kotlin + kotlinx.serialization. No Android SDK dependency, no dependency on `core:utils`
  or `infrastructure`. References `domain`'s `Playlist`/`Song` shapes for inspiration only — the
  wire DTOs stay minimal (the watch doesn't need `Song.reason`, `explanation`, etc., and doesn't
  need anything Gemini-related at all).
- Contents:
  ```kotlin
  object WearPaths {
      const val PLAYBACK_STATE = "/moodtune/playback_state"     // DataClient, passive mirror
      const val COMMAND = "/moodtune/command"                   // MessageClient, fire-and-forget
      const val PLAYLISTS = "/moodtune/playlists"                // DataClient, low-frequency
      const val DOWNLOAD_REQUEST = "/moodtune/download_request" // MessageClient, phone -> watch
      const val STORAGE_STATUS = "/moodtune/storage_status"     // DataClient, watch -> phone
      const val TRANSFER_CHANNEL_PREFIX = "/moodtune/transfer/" // ChannelClient path per song
      const val WATCH_ACTIVE_PLAYBACK = "/moodtune/watch_active_playback" // DataClient, watch -> phone
      const val PHONE_COMMAND = "/moodtune/phone_command"       // MessageClient, phone -> watch
  }

  object WearStorageBudget {
      const val CAP_BYTES = 500L * 1024 * 1024   // 500MB ceiling on MoodTune's own downloads
      const val SAFETY_MARGIN_BYTES = 150L * 1024 * 1024 // never leave less than this free
  }

  @Serializable
  data class WearPlaybackSnapshot(
      val songId: String,
      val title: String,
      val artist: String,
      val artAssetKey: String?,
      val isPlaying: Boolean,
      val isBuffering: Boolean,
      val positionMsAtSync: Long,
      val durationMs: Long,
      val syncedAtElapsedRealtime: Long,
      val hasNext: Boolean,
      val hasPrevious: Boolean,
      val source: WearPlaybackSource = WearPlaybackSource.PHONE_REMOTE
  )

  enum class WearPlaybackSource { PHONE_REMOTE, WATCH_LOCAL }

  @Serializable
  sealed class WearCommand {
      data object PlayPause : WearCommand()
      data object Next : WearCommand()
      data object Previous : WearCommand()
      data class SeekTo(val positionMs: Long) : WearCommand()
      /** Plays an existing, already-created playlist on the phone. No generation involved. */
      data class StartPlaylist(val playlistId: String) : WearCommand()
  }

  /** One of the phone's existing playlists, as shown in the watch's playlist list. */
  @Serializable
  data class WearPlaylistSummary(val playlistId: String, val name: String, val songCount: Int)

  /** User-selected subset of songs from one playlist, sent from the phone's "Send to Watch" screen. */
  @Serializable
  data class WearDownloadRequest(
      val playlistId: String,
      val playlistName: String,
      val songs: List<WearDownloadSong>
  )

  @Serializable
  data class WearDownloadSong(
      val songId: String,
      val title: String,
      val artist: String,
      val artAssetKey: String?,
      val fileSizeBytes: Long
  )

  @Serializable
  data class WearStorageStatus(val usedBytes: Long, val capBytes: Long)

  /** Pushed by the watch whenever its own local playback starts/stops (see 1.5/2.6). */
  @Serializable
  data class WatchActivePlaybackStatus(
      val isActive: Boolean,
      val playlistId: String? = null,
      val playlistName: String? = null,
      val songTitle: String? = null,
      val artist: String? = null
  )

  /** Reverse channel: phone -> watch, currently just the playback-lock "give me control back" action. */
  @Serializable
  sealed class PhoneToWatchCommand {
      data object StopLocalPlayback : PhoneToWatchCommand()
  }
  ```
- Fully unit-testable (serialization round-trips, and the budget-check arithmetic in 2.2/2.4) —
  agent-owned strict TDD per `testing_responsibilities.md`, no device needed.

### 2.2 Phone side (`app` module) — Controller Mode bridge

- **`PhoneWearSyncService`** (Koin `single`, started alongside `MusicPlayerManager`): pushes a
  `WearPlaybackSnapshot` via `DataClient` **only on real state-change events**
  (`onIsPlayingChanged`, `onMediaItemTransition`, `onPlaybackStateChanged` — the same ones
  `MusicPlayerManager.setupController()` already listens for), never on the 1-second progress
  tick `startProgressUpdate()` drives locally. The watch extrapolates elapsed time itself between
  syncs (2.4).
- **`PhoneWearCommandListenerService : WearableListenerService`**: routes incoming
  `WearCommand`s to existing `MusicPlayerManager` calls
  (`togglePlayPause`/`skipNext`/`skipPrevious`/`seekTo`). `StartPlaylist(playlistId)` looks the
  playlist up via the existing `PlaylistRepository` (an already-created, on-device playlist — no
  Gemini call anywhere in this path) and calls `MusicPlayerManager.playPlaylist(...)` with its
  songs. Thin router, no new decision logic.
- **Art for the mirrored session**: sent as a downscaled (~200x200) Data Layer `Asset`, keyed by
  the existing `md5(filePath)` hash, only the first time a given hash is sent to a given watch —
  the watch caches by that hash (2.4) and never re-requests it.
- **Playlist list sync**: publishes the current `WearPlaylistSummary` list (from
  `PlaylistRepository`) on `WearPaths.PLAYLISTS` whenever it changes — low frequency, not a
  timer.
- Register `PhoneWearCommandListenerService` in `app/src/main/AndroidManifest.xml` with the
  `MESSAGE_RECEIVED` filter on `WearPaths.COMMAND`.

### 2.3 Phone side (`app` module) — "Send to Watch" (new)

A new screen, most naturally living inside the existing `feature:playlist` module (a
playlist-scoped action, not a distinct screen *area* — doesn't yet justify its own module per the
architecture skill's modularity test).

- **`SendToWatchScreen`**: opened from an existing playlist's detail view. Shows that playlist's
  songs as a **checklist** (per-song, not a single "send whole playlist" button — this is the
  core correction in this revision), each row showing its on-disk file size. A running total at
  the top/bottom shows `selected size / remaining watch budget` live as the user
  checks/unchecks rows, going into a clear "over budget" visual state if the current selection
  wouldn't fit — the "Send" action is disabled in that state rather than allowing a doomed
  transfer to start.
  - `SendToWatchViewModel` computes remaining budget as
    `WearStorageBudget.CAP_BYTES - lastKnownUsedBytes - WearStorageBudget.SAFETY_MARGIN_BYTES`
    (using the last `WearStorageStatus` the watch reported, cached as a `StateFlow`; if none has
    ever arrived, the screen shows "couldn't confirm watch storage yet" and disables sending
    until one does). This is pure arithmetic over already-known numbers — TDD-able, agent tier.
- **`PhoneWearFileTransferService`**: on confirm, sends a `WearDownloadRequest` (the selected
  song subset) over `WearPaths.DOWNLOAD_REQUEST` (MessageClient, metadata only), then streams
  each selected song's actual audio bytes over a `ChannelClient` channel opened at
  `WearPaths.TRANSFER_CHANNEL_PREFIX + songId` (`ChannelClient` is the right primitive for actual
  file bytes; `Asset`/`DataItem` are for small structured payloads like the art thumbnail, not
  multi-MB audio). Runs as a foreground service with a progress `StateFlow` the screen observes.
  - The watch re-validates room independently on arrival (2.4) and can still reject — surfaced as
    an error in this screen; no silent partial send.
- Also exposes **per-song "remove from watch"** (list of what's currently downloaded, grouped by
  source playlist, each row individually removable) — granular removal matches the granular
  selection naturally, no extra design needed here since storage is already tracked per-song
  (2.4).

### 2.4 Watch side (`:wear` module)

Add to `wear/build.gradle.kts`: `androidx.media3:media3-exoplayer`, `media3-session`,
`media3-common` (already in the catalog, unused by `:wear` today), `androidx-wear-compose-navigation`,
and Koin (`koin-android` + core — matching the phone's DI choice). Do **not** add Coil or
Horologist — see 1's out-of-scope list and 2.5.

**Local playback engine:**
- **`WearMediaPlaybackService : MediaSessionService`** — a much smaller sibling of the phone's
  `MediaPlaybackService`: one `ExoPlayer` over local file `Uri`s only, no YouTube data source, no
  crash-resumption/repeat-count machinery. Exposes the standard Media3 `MediaSession`/
  `SessionToken` for a same-process `MediaController` to bind to.
- **`WearDownloadStore`**: tracks what's actually on disk — one entry per downloaded song:
  `songId`, file path (`filesDir/downloaded_songs/{songId}.mp3`), `playlistId` (which playlist it
  was sent from — a song can only be re-downloaded from the same source playlist in this MVP),
  `sizeBytes`, `downloadedAt`. A flat kotlinx.serialization JSON manifest file is enough at this
  scale — no Room needed for a few dozen song entries. Exposes:
  - `totalUsedBytes()` and `hasRoomFor(newBytes): Boolean` (checks against
    `WearStorageBudget.CAP_BYTES` **and** actual free space via `StatFs` minus
    `SAFETY_MARGIN_BYTES` — the watch is the final authority on its own storage and re-validates
    independently of whatever the phone calculated; a request that no longer fits by arrival time
    is rejected outright, not partially accepted).
  - `songsFor(playlistId): List<...>` — however many of that playlist's songs happen to be
    downloaded (0, some, or all — since selection is per-song, "some" is an expected, normal
    state, not an edge case).
  - `remove(songId)` and `removeAll(playlistId)` — both trivial given storage is already keyed
    per-song; deletes the file(s) and updates the manifest.
  - All of the above is plain logic over a small in-memory/JSON structure — TDD-able, agent tier.
- **`WearDownloadReceiverService : WearableListenerService`** (may share a service class with the
  command listener): on `WearPaths.DOWNLOAD_REQUEST`, checks `WearDownloadStore.hasRoomFor(...)`
  for the total of the incoming request; if it fits, opens a `ChannelClient.ChannelCallback` per
  incoming channel, streams to the target file, and only commits each song into the manifest
  once its full byte count matches what the request said (delete partial files on any
  failure/disconnect — never count a half-written song as downloaded). Publishes a fresh
  `WearStorageStatus` on `WearPaths.STORAGE_STATUS` immediately after, so the phone's "Send to
  Watch" screen reflects the new usage.

**Unified playback state:**
- **`WearPlaybackController`** — the watch's equivalent of `MusicPlayerManager`, owning two
  backends and exposing one `StateFlow<WearPlaybackSnapshot>`:
  - `LocalBackend` — a `MediaController` bound to `WearMediaPlaybackService`.
  - `RemoteMirrorBackend` — the Controller Mode listener (`DataClient` subscription +
    `MessageClient` command sending), registered only while the Now Playing screen is started.
  - **Priority rule**: if the local backend has an active/loaded queue
    (`mediaController.mediaItemCount > 0`), it's the active source. Otherwise the remote mirror is
    shown. Starting local playback always wins over whatever the phone is doing; it never pauses
    or otherwise touches the phone's session.
- **`NowPlayingScreen`**: renders whichever `WearPlaybackSnapshot` is currently active, tagged by
  `source`. Elapsed-time extrapolation between sync events (arithmetic, TDD-able) applies to the
  remote-mirror case only; the local case reads `MediaController.currentPosition` directly.
- **`PlaylistsScreen`** (renamed from "Mood Grid" in earlier passes, since there's no mood/AI
  concept in scope anymore): `ScalingLazyColumn` of `WearPlaylistSummary` synced from
  `WearPaths.PLAYLISTS`. Each row is cross-referenced against `WearDownloadStore.songsFor(
  playlistId)` locally to show how much of it is downloaded (e.g. "5/12 downloaded", or no badge
  if none). Tapping a row:
  - **Any songs downloaded (partial or full)** → plays the locally-available subset immediately
    through `LocalBackend` — no phone round-trip. This is the standalone/immersive path, and
    partial availability is a normal case here, not a special one, given per-song selection.
  - **Nothing downloaded for this playlist** → sends `WearCommand.StartPlaylist(playlistId)` to
    the phone, which plays the full existing playlist there instead.
  - One screen covers both modes; no separate "Downloaded" screen needed.
- **Ambient mode**: `AmbientLifecycleObserver` on the (renamed) `WearMainActivity` applies
  uniformly to whichever backend is active — pure-black theme, hidden/low-rate progress
  rendering, remote-mirror extrapolation tick paused while ambient. Local playback keeps actually
  playing audio while ambient — only the *rendering* work stops.

### 2.5 Dependency discipline

Prefer plain `androidx.wear.compose.material3`/`foundation` (already in the catalog) for both
screens over Horologist. Keep the local player's feature set minimal — **confirmed for MVP: no
repeat-count, no crash-resumption for the wear queue**; a killed watch process just restarts
clean from `WearDownloadStore` with nothing playing, the user taps a playlist again.

### 2.6 Watch-owns-playback lock (new)

**Watch side:** `WearPlaybackController`'s `LocalBackend` publishes a
`WatchActivePlaybackStatus` on `WearPaths.WATCH_ACTIVE_PLAYBACK` at two moments: `isActive=true`
(with the playlist/song info) the instant local playback actually starts, and `isActive=false`
the instant it stops for any reason (playlist finished, user backed out and stopped it on the
watch, or the new `PhoneToWatchCommand.StopLocalPlayback` below was received). A new listener
(can share the existing command-listening service) handles `WearPaths.PHONE_COMMAND`:
`StopLocalPlayback` calls the same stop/release path the watch's own UI would trigger — there's
only one way to stop local playback internally, the phone command just invokes it remotely.

**Phone side:** a small new component (or an addition to `PhoneWearSyncService`) subscribes to
`WearPaths.WATCH_ACTIVE_PLAYBACK` and, on `isActive=true`:
- Calls `MusicPlayerManager`'s existing pause path if it's currently playing — **does not**
  touch/clear its loaded queue, so it resumes exactly where it left off once unlocked.
- **Dismisses the phone's media notification (pass 6), not just pauses it.** Pausing alone leaves
  a tappable play button sitting in the notification shade — an accidental way for the user to
  start a second, competing session on the phone while the watch is already playing. The concrete
  hook is `infrastructure/.../MediaPlaybackService.kt`'s notification path (Media3's
  `MediaNotification.Provider`/`onUpdateNotification` for a `MediaSessionService`): while the
  lock is active, suppress/cancel that notification outright rather than merely reflecting a
  paused state in it. Restore normal notification behavior the moment the lock clears (2.6 below)
  so the user can still resume from the shade once the watch is done. Note this only closes the
  *notification* as an accidental-resume surface, per what was actually asked — other latent
  triggers (a Bluetooth headset's own play button, an Assistant "resume music" voice command)
  aren't addressed here; flag if those turn out to matter once this is in real use.
- Exposes a new `StateFlow<WatchActivePlaybackStatus?>` alongside `MusicPlayerManager.playbackState`
  (same "app-wide singleton, separate pass-through state" pattern the architecture skill already
  uses for high-frequency singleton state) for the UI to observe.
- **No new screen.** The *existing* Now Playing screen/ViewModel combines its own
  `MusicPlayerManager.playbackState` with this new status and picks which to render — exactly the
  same "unified state, tagged by source" shape as the watch's own `WearPlaybackController`
  (2.4). When the watch status is active, the screen shows the watch's title/artist/art (not the
  phone's own stale queue) with a small **"Playing on Watch"** banner, and swaps its normal
  transport-control row for a single **"Stop on Watch"** action that sends
  `PhoneToWatchCommand.StopLocalPlayback` over `WearPaths.PHONE_COMMAND`. Nothing else in the
  phone app should silently start a new playback session while this is active — any such entry
  point should route through (or at minimum warn before) sending that same stop command first, to
  avoid ever landing back in the dual-audio problem this feature exists to prevent.
- On `isActive=false` (whether from the watch stopping itself or from the phone's own stop
  command being honored), the screen reverts to showing the phone's own (frozen) queue. It does
  **not** auto-resume — consistent with the existing "prepare, don't auto-play" convention
  `MusicPlayerManager.restoreQueueIfEmpty()` already follows for cold-start recovery; the user
  presses play themselves.

## 3. Feature interactions worth calling out explicitly

- **Audio itself never crosses between devices — but a status signal now deliberately does.**
  Local and phone playback are still two independent `MediaSession`s; the watch never streams
  audio to the phone or vice versa (not synchronized playback). What's new in 1.5/2.6 is a
  one-way "I'm playing" status from watch to phone (so the phone can pause itself and show what's
  actually happening) and a one-way "stop" command from phone to watch (the only way the phone
  reclaims control). That's arbitration of *who's allowed to make sound*, not audio relay.
- **Partial downloads are the normal case, not an edge case**, because selection is per-song.
  `PlaylistsScreen`'s tap rule (2.4) and `WearDownloadStore.songsFor(...)` are written with that
  in mind from the start — this isn't a follow-up to handle later.
- **Storage status is eventually-consistent, not polled.** The watch pushes a fresh
  `WearStorageStatus` after every download/removal; if the watch and phone haven't talked in a
  while (watch off, out of range), the "Send to Watch" screen should show a clear "couldn't
  confirm current watch storage" state rather than trusting a stale number.

## 4. What the widget rework already taught us (still applies)

- **Thread affinity**: `WearableListenerService.onMessageReceived` and `ChannelCallback` methods
  don't run on the main thread; any `MediaController` touch needs the same
  `withContext(Dispatchers.Main.immediate)` hop `WidgetMediaConnection` already uses.
- **Sync immediately on (re)connect**: both `PhoneWearSyncService` (on the watch's listener
  registering) and `WearPlaybackController`'s local backend (on `MediaController` connecting)
  should push/read a snapshot immediately rather than waiting for the next event.
- **Genuinely new vs. the widget case**: cross-device audio/asset transfer, a watch that can
  sleep/disconnect independent of the phone process, and a second, independent playback engine
  with its own storage budget to enforce.

## 5. Implementation Checklist — track progress section by section

Each section below is one buildable, checkable unit, in dependency order — finish a section
(including its own verification items) before starting the next. Design rationale for each lives
in the matching numbered subsection of §2 (referenced inline); this checklist is "what to actually
do," not a re-explanation of "why." Check items off (`- [x]`) directly in this file as work lands.

> **Scaffolding pass (2026-09-27):** the folder/package structure and empty stub classes for both
> `core:wearsync` and `:wear` now exist on disk (confirmed compiling via
> `./gradlew :core:wearsync:compileDebugKotlin :wear:compileDebugKotlin` and
> `:wear:processDebugMainManifest`), per Fardeen's explicit ask to scaffold structure now and add
> real logic later. This includes: `wear/.../WearApplication.kt` (starts Koin) and
> `wear/.../di/WearModules.kt` (empty `wearModule`, not itemized as its own checklist line but
> needed by every section below that registers a dependency), plus the stub classes noted inline
> next to their checklist items. A checkbox below is only marked `[x]` when the actual behavior it
> describes exists — a class merely existing as an empty shell keeps its box unchecked, annotated
> with where the stub already lives so it isn't recreated from scratch.

### Section 1 — Shared contract module: `core:wearsync` (design: §2.1)
- [x] Create the `core:wearsync` module (pure Kotlin + kotlinx.serialization, no Android SDK dep);
      add to `settings.gradle.kts`. *(2026-09-27: module scaffolded, `com.android.library` +
      `kotlin.serialization` plugins matching `domain`'s existing setup; compiles clean.)*
- [x] Add `WearPaths` (all message/data/channel path constants).
- [x] Add `WearStorageBudget` (`CAP_BYTES`, `SAFETY_MARGIN_BYTES`).
- [x] Add `WearPlaybackSnapshot` + `WearPlaybackSource`.
- [x] Add `WearCommand` sealed class (`PlayPause`/`Next`/`Previous`/`SeekTo`/`StartPlaylist`).
- [x] Add `WearPlaylistSummary`.
- [x] Add `WearDownloadRequest` + `WearDownloadSong`.
- [x] Add `WearStorageStatus`.
- [x] Add `WatchActivePlaybackStatus`.
- [x] Add `PhoneToWatchCommand` sealed class (`StopLocalPlayback`).
- [ ] **(Agent, TDD)** Unit tests: serialization round-trip for every DTO/sealed class above.
      *(Deliberately not written yet — scaffolding pass only, per Fardeen: "we will add the
      implementation later.")*

### Section 2 — Controller Mode: phone → watch bridge (design: §2.2)
- [ ] `PhoneWearSyncService`: push `WearPlaybackSnapshot` via `DataClient` on real state-change
      events only (`onIsPlayingChanged`/`onMediaItemTransition`/`onPlaybackStateChanged`) — never
      on the 1-second progress tick.
- [ ] `PhoneWearCommandListenerService : WearableListenerService`: route incoming `WearCommand`s
      to `MusicPlayerManager`; `StartPlaylist(playlistId)` resolves via `PlaylistRepository` (no
      Gemini call anywhere in this path).
- [ ] Art transfer: downscaled (~200x200) Data Layer `Asset`, keyed by the existing
      `md5(filePath)` hash, sent once per hash.
- [ ] Publish `WearPlaylistSummary` list on `WearPaths.PLAYLISTS` whenever playlists change.
- [ ] Register the listener service in `AndroidManifest.xml`; wire everything into
      `AppModules.kt` (Koin).
- [ ] **(Agent, TDD)** `WearCommand → MusicPlayerManager` call-mapping tests if branching grows
      beyond the trivial 1:1 case.
- [ ] **(Fardeen, device)** Pair emulator/watch with phone; confirm `DataClient` actually
      connects (node/capability discovery has known rough edges).
- [ ] **(Fardeen, device)** Play on phone → snapshot arrives on watch side (can verify via log
      before any watch UI exists).

### Section 3 — Controller Mode: watch UI (design: §2.4, mirror path only)
- [x] Add `media3-exoplayer`/`media3-session`/`media3-common`, `androidx-wear-compose-navigation`,
      Koin to `wear/build.gradle.kts`. *(2026-09-27 — also added
      `androidx-wear-compose-navigation` to `gradle/libs.versions.toml`, wasn't there before.)*
- [ ] `RemoteMirrorBackend`: `DataClient` subscription (registered only while Now Playing is
      started) + `MessageClient` command sending. *(Class stub exists at
      `wear/.../playback/RemoteMirrorBackend.kt`, no logic yet.)*
- [ ] Watch-side art cache (`filesDir/art_cache/{hash}.jpg`), decoded via `BitmapFactory` — no
      Coil.
- [ ] **(Agent, TDD)** Elapsed-time extrapolation arithmetic (`positionMsAtSync` +
      `syncedAtElapsedRealtime` → displayed position, clamped at `durationMs`).
- [ ] `NowPlayingScreen`: title/artist/art/progress/transport controls, sourced from
      `RemoteMirrorBackend` only for now (local backend arrives in Section 6). *(Composable stub
      exists at `wear/.../presentation/NowPlayingScreen.kt`, renders a placeholder `Text` only.)*
- [ ] `PlaylistsScreen` skeleton: list from `WearPaths.PLAYLISTS`, tap → sends
      `WearCommand.StartPlaylist` (no downloaded-badge/local-play logic yet — that's Section 6).
      *(Composable stub exists at `wear/.../presentation/PlaylistsScreen.kt`, placeholder `Text`
      only.)*
- [ ] Ambient mode: `AmbientLifecycleObserver` on `WearMainActivity`, pure-black layout, pause the
      extrapolation tick while ambient.
- [ ] **(Fardeen, device)** Pause/skip on watch → phone reacts; kill phone app mid-playback →
      watch doesn't show stale state indefinitely; ambient transition renders correctly.

**Milestone: 1.1 (show what's playing) and 1.2 (ambient) are fully done and usable at this point,
independent of everything below.**

### Section 4 — Watch storage budget logic (design: §2.4, `WearDownloadStore`)
- [ ] `WearDownloadStore`: JSON manifest (`songId`, file path, `playlistId`, `sizeBytes`,
      `downloadedAt`). *(Class stub exists at `wear/.../download/WearDownloadStore.kt`, no
      manifest logic yet.)*
- [ ] `hasRoomFor(newBytes)`: checks `WearStorageBudget.CAP_BYTES` **and** actual free space via
      `StatFs` minus `SAFETY_MARGIN_BYTES`.
- [ ] `songsFor(playlistId)`, `remove(songId)`, `removeAll(playlistId)`.
- [ ] **(Agent, TDD)** Boundary tests for `hasRoomFor`: exactly at cap, exactly at safety margin,
      both satisfied, either violated. No device needed for this whole section.

### Section 5 — Send to Watch: phone-side transfer flow (design: §2.3)
- [ ] `SendToWatchScreen` (in `feature:playlist`): per-song checklist with file sizes, live
      running total vs. remaining budget, "Send" disabled when over budget.
- [ ] `SendToWatchViewModel`: remaining-budget arithmetic from the last known
      `WearStorageStatus`; "couldn't confirm watch storage yet" state when none has arrived.
- [ ] **(Agent, TDD)** `SendToWatchViewModel` budget-arithmetic tests (same boundary cases as
      Section 4, phone side).
- [ ] `PhoneWearFileTransferService`: `WearDownloadRequest` over `MessageClient`, then audio bytes
      per song over `ChannelClient` at `WearPaths.TRANSFER_CHANNEL_PREFIX + songId`; runs as a
      foreground service with progress `StateFlow`.
- [ ] Per-song "remove from watch" list on the phone (grouped by source playlist).
- [ ] **(Fardeen, device)** Select songs that clearly fit → send → transfer completes, progress UI
      accurate, files playable.
- [ ] **(Fardeen, device)** Select songs that would exceed the budget → "Send" stays disabled,
      nothing transferred.

### Section 6 — Watch-side receiver + standalone local playback (design: §2.4)
- [ ] `WearDownloadReceiverService : WearableListenerService`: validate `hasRoomFor(...)` on
      `WearPaths.DOWNLOAD_REQUEST`, receive bytes via `ChannelClient.ChannelCallback`, commit to
      the manifest only once the full byte count matches (delete partials on failure), publish a
      fresh `WearStorageStatus` after. *(Class stub exists at
      `wear/.../download/WearDownloadReceiverService.kt` with `onMessageReceived` overridden but
      empty, registered in `AndroidManifest.xml` with a `MESSAGE_RECEIVED` intent-filter already —
      `onChannelOpened` and real routing not yet written; verify current `ChannelClient`
      manifest/registration requirements against Google's docs before relying on this
      registration being sufficient, per the TODO left in that file.)*
- [ ] `WearMediaPlaybackService : MediaSessionService`: local-file-only `ExoPlayer`, no
      crash-resumption/repeat-count (confirmed skipped for MVP). *(Class stub exists at
      `wear/.../playback/WearMediaPlaybackService.kt` with `onGetSession`/`onDestroy` wired up,
      registered in the manifest with `foregroundServiceType="mediaPlayback"` — no `ExoPlayer`
      instance created yet.)*
- [ ] `WearPlaybackController`: unifies `LocalBackend`/`RemoteMirrorBackend` behind one
      `StateFlow<WearPlaybackSnapshot>`; local wins whenever it has an active/loaded queue.
      *(Class stub exists at `wear/.../playback/WearPlaybackController.kt`, exposes the
      `StateFlow<WearPlaybackSnapshot>` already but no priority-rule logic yet; `LocalBackend`
      stub also exists.)*
- [ ] Update `NowPlayingScreen` to render whichever backend `WearPlaybackController` reports
      (tagged by `source`), local case reads `MediaController.currentPosition` directly (no
      extrapolation needed).
- [ ] Update `PlaylistsScreen`: cross-reference each row against `WearDownloadStore.songsFor(...)`
      for a "N/total downloaded" badge; tap plays the local subset via `LocalBackend` if anything
      is downloaded, else sends the remote `StartPlaylist` command.
- [ ] **(Fardeen, device)** Disconnect the watch mid-transfer → partially-written file is cleaned
      up, not counted as downloaded.
- [ ] **(Fardeen, device)** Play a partially-downloaded playlist on the watch with the phone
      off/out of range → plays the downloaded subset through earbuds paired directly to the watch.
- [ ] **(Fardeen, device)** Remove individual songs from the phone's list → watch frees the space,
      fresh `WearStorageStatus` reflects it.

**Milestone: 1.3 (transfer + storage management) and 1.4 (standalone/"immersive" playback) are
fully done at this point.**

### Section 7 — Watch-owns-playback lock (design: §1.5/§2.6)
- [ ] Watch: `LocalBackend` publishes `WatchActivePlaybackStatus` on `WearPaths.WATCH_ACTIVE_PLAYBACK`
      the instant local playback starts (`isActive=true`, with playlist/song info) and stops
      (`isActive=false`).
- [ ] Watch: handle `PhoneToWatchCommand.StopLocalPlayback` on `WearPaths.PHONE_COMMAND` — calls
      the same internal stop/release path the watch's own UI would trigger.
- [ ] Phone: subscribe to `WearPaths.WATCH_ACTIVE_PLAYBACK`; on `isActive=true`, pause
      `MusicPlayerManager` (without clearing its queue) and expose the status as a new
      `StateFlow` alongside `playbackState`.
- [ ] Phone: dismiss/suppress `MediaPlaybackService`'s notification while `isActive=true`
      (`onUpdateNotification`/`MediaNotification.Provider` hook — confirmed: pausing alone isn't
      enough, since a visible paused notification is an accidental way to start a second session
      on the phone). Restore normal notification behavior the instant `isActive=false`.
- [ ] Phone: existing Now Playing screen becomes source-aware — shows the watch's title/artist/art
      + "Playing on Watch" banner when active, transport-control row replaced by a single "Stop on
      Watch" action sending `PhoneToWatchCommand.StopLocalPlayback` **immediately on tap, no
      confirmation dialog** (confirmed). **No new screen.**
- [ ] Phone: intercept/warn on any other playback-start entry point while the lock is active.
- [ ] **(Agent, TDD)** `WatchActivePlaybackStatus`/`PhoneToWatchCommand` serialization round-trips
      (if not already covered in Section 1).
- [ ] **(Fardeen, device)** Play something on the phone, then start a *different* downloaded
      playlist locally on the watch → phone pauses within one sync cycle, notification actually
      disappears (not just shows paused), and the screen shows the correct (differing)
      playlist/song info, not stale phone-side data.
- [ ] **(Fardeen, device)** Tap "Stop on Watch" → watch's local session actually stops, phone
      reverts to its original queue intact but **not** auto-resumed, notification reappears.
- [ ] **(Fardeen, device)** Let the watch's local playlist finish naturally (or stop it directly on
      the watch) → phone's lock clears on its own (notification restored too), no phone-side
      action required.

### Section 8 — Final integration & polish pass
- [ ] Battery check: Now Playing screen open 10+ minutes during a mirrored session → no
      continuous per-second Data Layer chatter.
- [ ] Battery check: local playback session running 10+ minutes → no unexpected wakeups from the
      idle remote-mirror backend.
- [ ] Ambient mode transition confirmed correct during **both** local and mirrored playback,
      including while the watch-owns-playback lock is active on the phone.
- [ ] Confirm `targetSdk`/OS-floor assumptions (§0) against the actual Pixel Watch 2 / Wear OS 7
      test device.

## Open questions for Fardeen

None outstanding as of pass 6 — all prior questions (crash recovery, OS floor, stop-tap
confirmation, notification visibility) are answered and folded into the sections above. Add new
ones here as they come up during implementation, rather than letting them go untracked.
