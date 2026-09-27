# MoodTune — Project Explainer

This document explains MoodTune end to end: what it is, why it's built the way it is, and how
every major feature actually works under the hood. It's written to be usable in a client or
stakeholder conversation — each section covers **what** the user experiences, **how** it's built,
and **why** it was built that way.

For terse, dev-facing conventions (file locations, coding rules), see `CLAUDE.md`. This file is
the narrative version.

---

## 1. What MoodTune is

- MoodTune is an Android music app built around one idea: **you shouldn't have to browse genres
  or artists to find music that matches how you feel.**
- You describe a mood or moment in plain language — *"late-night drive in the rain,"* *"need
  energy for a workout,"* *"quiet Sunday morning"* — and the app is designed to hand you back a
  playlist built for that exact feeling, with a short AI-written note on why each song fits.
- Underneath that pitch, it's also a fully-featured local music player:
  - scans and plays music already on your phone
  - remembers what you've played most
  - supports drag-and-drop playlist reordering
  - generates synced lyrics on demand
  - has a home-screen widget for controlling playback without opening the app

> **Important caveat, stated up front:** the AI mood-to-playlist generation pipeline (Gemini +
> YouTube) is fully built and working in the codebase, but as of this writing **there is no
> button in the app that triggers it.** Every "create playlist" path in the current UI creates
> an *empty* playlist that the user fills manually from their own device library.
> See [§10](#10-known-gaps-and-open-items) for the full explanation — this is the single most
> important thing to know before demoing the app.

---

## 2. Who it's for, and the problem it solves

- Streaming apps organize music by artist, album, genre, or algorithmic "radio" — none of which
  map naturally onto *how someone feels in the moment*.
- MoodTune's bet: natural-language mood description is a more human way to ask for music than
  picking a Spotify mood-playlist tile from a curated list someone else made.
- The AI explanation attached to each song ("why this song fits") is meant to make the playlist
  feel curated *for that specific mood*, not just tagged with it.

---

## 3. How the app is put together (architecture, in plain terms)

### The pattern: MVI (Model-View-Intent)

Every screen follows the same shape:

- **State** — one immutable data class describing everything the screen needs to render (e.g.
  `HomeState`, `PlaylistState`). Owned by a `StateFlow` inside a ViewModel.
- **Intent** — a sealed class enumerating every action the user can take on that screen (e.g.
  `HomeIntent.PlaySong`, `PlaylistIntent.RemoveSong`). The UI never mutates state directly — it
  only ever dispatches an Intent.
- **ViewModel** — a single `onIntent(intent)` function is the only entry point. It updates state
  and/or kicks off suspend work (network calls, database writes).

Why it's built this way:

- A screen's entire behavior is legible from its `Intent` sealed class and its `onIntent`
  `when` block — no hunting through scattered click handlers.
- Every feature module (`home`, `playlist`, `songdetail`) follows this exact shape.

### The module map, and why it's split this way

- The codebase is split into ~14 Gradle modules instead of one big app module.
- The reasoning: each module can only see the modules below it, which physically prevents
  architectural drift — a UI screen literally cannot import a Room `Entity` directly, for
  example, it has to go through `domain` models.

```
app                     ← the installable app: navigation host, DI wiring, widget
├── feature/home        ← Home screen, Search
├── feature/playlist    ← Playlist list/detail, drag-drop reorder, "add from device"
├── feature/songdetail  ← Now Playing screen, lyrics, playback controls
├── data                ← repository implementations (the only place domain meets infrastructure)
├── infrastructure      ← Gemini/YouTube API clients, device file scanner, ExoPlayer service
├── domain              ← plain Kotlin: Song/Playlist models, repository interfaces, use cases
├── config               ← remote API key management (Firebase Remote Config), connectivity
└── core/
    ├── database        ← Room: entities, DAOs, migrations, entity↔domain mappers
    ├── network         ← shared Ktor HttpClient (used by infrastructure)
    ├── utils            ← MusicPlayerManager (the app-wide playback controller)
    ├── ui              ← theme, shared composables, mood-color palette
    └── analytics       ← scaffolded, not yet implemented (see §10)
wear                    ← Wear OS companion — default template only, not yet implemented (see §10)
```

- **The rule of thumb:** `domain` has **zero Android dependencies** — it's pure Kotlin models
  and interfaces. Everything else depends inward toward it.
- What that buys us: the core business rules (what a `Song` is, what operations a playlist
  supports) could theoretically be reused by a completely different UI — the `wear` module is
  meant to eventually do exactly this — without rewriting any logic.

### Dependency injection: Koin

- Every repository, use case, and ViewModel is wired together in `app/di/AppModules.kt` (plus a
  `databaseModule` / `networkModule` / `configModule` per relevant core module).
- Koin resolves the dependency graph at runtime rather than at compile time (unlike Dagger/Hilt)
  — simpler to read, slightly less compile-time safety, a reasonable trade-off at this app's
  size.

---

## 4. How API keys work (and why they're not in the app)

Worth explaining on its own — it's a common point of confusion, and a common security question
from reviewers: **the Gemini and YouTube API keys are never bundled into the app's code or APK.**

**How it actually works** (`config` module):

1. **On app startup**, `RemoteConfigManager` asks **Firebase Remote Config** for the current key
   values.
   - Firebase Remote Config is a Google Cloud service that lets an app fetch small configuration
     values at runtime, controlled from the Firebase console — not from anything shipped in the
     app binary.
2. **Whatever comes back is cached locally** on-device via Jetpack DataStore (`ConfigDataStore`).
   - So the app still has working keys the *next* time it launches, even if that particular
     fetch fails (e.g., no internet at that moment).
3. **Before any Gemini or YouTube call**, the code calls `ConfigRepository.ensureConfig { ... }`,
   which:
   - returns immediately if a cached key is already present
   - throws a clear "no internet" error if the device is offline and there's no cached key yet
   - otherwise triggers a fresh fetch and waits up to 15 seconds for a key to arrive, before
     giving up with a "couldn't retrieve configuration" error

**Why this matters:**

- If the Gemini or YouTube API key ever needs to be rotated (leaked, rate limits changed,
  billing account swapped), that's a change made in the Firebase console —
  **no app update required**, and no user gets stuck on a build with a dead key.
- Decompiling the APK doesn't hand someone the API keys directly.
- Caveat: a sufficiently motivated attacker can still intercept the keys off the network at
  runtime — remote config raises the bar, it doesn't eliminate the risk entirely.

---

## 5. The AI pipeline: mood → playlist (built, currently disconnected — see §10)

**What it's meant to do:** user types a mood, taps confirm, and within a few seconds gets a
10-song playlist (5 Bollywood, 5 Hollywood) with a one-line "why this fits" explanation per song.

**How it works, end to end** (`GeneratePlaylistUseCase` in `domain`):

1. **Check for a duplicate.** If a playlist for that exact mood string already exists, the flow
   stops immediately (`Result.AlreadyExists`) rather than creating a second one.
2. **Ask Gemini for recommendations.** `GeminiAPIDataSource.getSongRecommendations(mood)` sends
   a prompt asking for a strict JSON array of 10 songs, each with:
   - a title and artist
   - a short "about this song" blurb
   - a one-line "why it fits this mood" explanation
   - a one-line "what film/context this song is from" note
   - Gemini's raw text response usually comes wrapped in a ` ```json ... ``` ` markdown fence
     even when asked not to — the code strips that before parsing.
3. **Try to match against the user's own device library first.** For each recommended song, the
   app checks whether a song with a matching (or closely matching, case-insensitive) title
   already exists among the user's locally scanned files.
   - If so, that local file is used directly — no network playback needed, and it plays offline.
4. **Otherwise, look it up on YouTube.** `YouTubeAPIDataSource.searchSong(...)` finds a matching
   video and uses its thumbnail + video URL as the song's art and playback source.
5. **Save the playlist.** The finished `Playlist` (with its songs) is persisted via
   `PlaylistRepository.savePlaylist(...)` — see §7 for how that write is made safe.

**Error handling** is deliberately mapped to plain-English messages rather than raw exception
text, each with its own message and a "Retry" action wired through the whole chain:

- no internet connection
- request timeout
- malformed AI response
- Gemini rate-limiting (HTTP 429)

---

## 6. Local music: scanning, playback, and embedded album art

**What it does:** MoodTune can browse and play any audio file already on the device, complete
with whatever album art is embedded in the file itself — not just whatever a streaming API would
provide.

**How the scan works** (`LocalSongDataSource`):

- Queries Android's `MediaStore` for every audio file.
- For each one, extracts any artwork embedded in the file's own metadata
  (`MediaMetadataRetriever`) — separately from whatever MediaStore's own album-art cache has,
  since that cache is frequently stale or empty for files added outside the system media
  scanner's normal flow.
- Extracted art is saved once to a hash-named JPEG on disk, keyed by the file's path — so the
  same file always resolves to the same cached art even if MediaStore reassigns its internal ID.
- Stale art for files that no longer exist gets cleaned up on every scan.

**How playback works** (`core/utils/MusicPlayerManager` + `infrastructure/MediaPlaybackService`):

- Playback runs through Android's Media3/ExoPlayer stack in a **foreground service**
  (`MediaPlaybackService`) — music keeps playing even if the user leaves the app, backed by a
  persistent notification as Android requires.
- Every part of the app that needs to know or control playback state (the Now Playing screen,
  the mini-player-style controls, the home screen widget) doesn't talk to the service directly
  — it connects its own lightweight `MediaController` to the same shared `MediaSession`, and
  reads a `StateFlow<PlaybackState>` that mirrors the player's real state (current song,
  position, play/pause, shuffle, queue).
- This is the standard Media3 pattern: one source of truth (the service), many independent
  observers.

**Deliberate exception:** if the user swipes MoodTune away from the recents list, playback stops
immediately rather than continuing in the background like most music apps. Confirmed as
intentional app behavior, not an oversight.

---

## 7. The database: schema, relationships, and how every write happens

Everything below lives in `core/database` (schema/DAOs/migrations) and `data` (the repository
implementations that DAOs are wrapped in before anything above them touches data).

### Tables and how they relate

Three tables, one classic many-to-many relationship:

- **`songs`** (`SongEntity`) — one row per song, whether it's a local device file or an
  AI/YouTube-sourced recommendation:
  - `id` (primary key), `title`, `artist`, `reason` (why it was picked)
  - `imageUrl`, `externalUrl` (playback source — a local file path or a YouTube URL)
  - `mood`
  - an embedded `explanation` (about/mood/context, stored as three flattened `explanation_*`
    columns rather than a nested table)
  - `playCount`, `lastPlayedAt`, `durationMs`
  - `lyrics` (raw cached JSON, nullable), `localAlbumArtPath`
- **`playlists`** (`PlaylistEntity`) — one row per playlist:
  - `id`, `mood` (doubles as the display name), `createdAt`, `lastPlayedAt`, `playCount`
- **`playlist_song_cross_ref`** (`PlaylistSongCrossRef`) — the join table that makes
  playlists↔songs many-to-many (the same song can legitimately sit in several playlists at once,
  and a playlist obviously holds many songs):
  - composite primary key of `(playlistId, songId)`
  - a `position` column that's the *only* thing that encodes song order within a playlist —
    order is not implied by insertion order or any other column

> **Small inconsistency worth knowing:** there's a fourth type, `PlaylistWithSongs`, built for
> Room's `@Relation` query style. It's declared and has its own mapper, but no actual query in
> `PlaylistDao` returns it. Every real query uses a raw `@Query` with a `LEFT JOIN` instead,
> returning `Map<PlaylistEntity, List<SongEntity>>` directly. `PlaylistWithSongs` and its mapper
> are unused leftover scaffolding, not a bug — just worth knowing about so you don't land on the
> wrong "playlist with songs" query by mistake.

### The queries that actually run

- `PlaylistDao`'s LEFT JOIN queries cover every list the UI needs, each pre-sorted for its
  purpose:
  - all playlists — `getPlaylistsWithSongs`
  - the 10 most recently played — `getRecentlyPlayedPlaylists`, `WHERE lastPlayedAt IS NOT NULL
    ORDER BY lastPlayedAt DESC`
  - the 10 most played — `getMostPlayedPlaylists`, `WHERE playCount > 0 ORDER BY playCount DESC`
  - one playlist by mood name (case-insensitive), or by ID
- All of the list ones exist as `Flow`, so they push new data straight into the observing screen
  the instant the underlying rows change — there's no manual "refresh" concept anywhere in the
  app.
- **A subtlety worth knowing:** a playlist with **zero songs** still needs to show up in these
  lists (as an empty playlist), but a `LEFT JOIN` against an empty match produces a row where
  every song column is `NULL`.
  - Room's generated code for `Map<K, List<V>>`-returning queries already detects this (checks
    whether *every* joined-in column for that row is null) and simply omits an entry from the
    list rather than fabricating a bogus all-null `SongEntity` — confirmed directly against
    Room's generated `..._Impl.kt` code, not assumed.
  - This is why the mapper layer doesn't do any of its own null-filtering on that list — it
    would be redundant.
- `SongDao` is simpler: CRUD on individual songs, `incrementSongPlayCount`, and a
  `getLyricsForSong` / `updateLyrics` pair that treats cached lyrics as a plain nullable text
  column, not its own table (lyrics are just a JSON-encoded `List<LyricLine>` string).

### Play counts and "last played" — how they actually get set, and how they survive edits

Two independent counters, updated from two different places:

- **A song's `playCount` / `lastPlayedAt`** bumps via `MusicPlayerManager`'s player listener —
  every time ExoPlayer transitions to a new track in the queue, it fires
  `songsRepository.incrementSongPlayCount(songId)` in the background.
  - This means a song counts as "played" the moment it *starts*, not after any minimum listen
    duration.
- **A playlist's `playCount` / `lastPlayedAt`** bumps via `PlaylistIntent.MarkPlaylistAsPlayed`,
  dispatched when the user taps into a playlist from the list screen — independent of whether
  they actually play anything once inside.

How this survives edits:

- Neither `Song` nor `Playlist` (the `domain` models the rest of the app works with) actually
  carry these fields — they're pure storage bookkeeping that only exists at the entity/DB layer.
- That matters for editing: every write path that re-saves an existing song or playlist (adding
  a song, reordering, editing) first looks up the *existing* entity and copies its `playCount` /
  `lastPlayedAt` into the new one before writing.
- Otherwise every edit would silently reset play stats back to zero, since a fresh
  domain-model-to-entity mapping has nothing to put there.

### Migrations, and the philosophy behind them

- The database is currently at **version 6**.
- Four migrations exist so far, each adding or removing exactly one column:
  - `durationMs` — v2→3
  - `lyrics` — v3→4
  - `localAlbumArtPath` — v4→5
  - dropping the unused `energy` column — v5→6 (part of this codebase audit)

Two things worth knowing if this ever needs touching again:

- **There's no schema export** (`exportSchema = false`), so there's no JSON snapshot of the
  "true" expected schema to validate a hand-written migration against before shipping it.
  - A subtly wrong migration doesn't fail at compile time — it fails the *next time an existing
    user upgrades*, which is a much worse time to find out.
  - Always test an actual old-build → new-build upgrade before shipping a new migration, not
    just a fresh install.
- **`fallbackToDestructiveMigration()`** is the registered safety net for any version jump that
  doesn't have an explicit migration — it wipes and recreates the database from scratch rather
  than crashing.
  - Reasonable trade-off today — there's no user data valuable enough to be worth a hard crash
    over.
  - But it also means a *forgotten* migration fails silently by deleting every user's playlists
    on their next update, rather than surfacing the mistake anywhere. Worth revisiting if the
    app ever has data worth protecting more strongly.
- **Dropping a column** (the v5→6 migration) can't just be an `ALTER TABLE ... DROP COLUMN` —
  that SQL only works on SQLite 3.35+, which isn't reliably available at this app's
  `minSdk 29`.
  - The safe, portable pattern instead: create a new table with the desired columns, copy every
    row across, drop the old table, rename the new one into place. That's what the v5→6
    migration does.

### Why playlist writes are wrapped the way they are

Saving a playlist (`PlaylistRepositoryImpl.savePlaylist`) isn't one database write — it's four,
in order:

1. save/update every song in the playlist
2. save/update the playlist row itself
3. delete the *old* song-ordering links
4. insert the *new* ones

Two real, verified bugs in this path were found and fixed during this codebase audit (not part
of the original design):

- **Atomicity.** All four writes now run inside a single Room `db.withTransaction { }` block.
  - Without it: if the app's process died at exactly the wrong moment — after the old ordering
    links were deleted but before the new ones were inserted — a playlist would be left
    permanently pointing at zero songs, with no way to recover short of re-adding them by hand.
- **Write ordering.** A subtler bug: if a user removed two songs from the same playlist in quick
  succession, each removal fires its own independent background save.
  - Kotlin coroutines make no guarantee that two independently-launched writes *finish* in the
    order they were *started*.
  - So if the *older* write (still carrying the song that was later also removed) happened to
    land *after* the newer one, that song would silently reappear — because Room's live `Flow`
    on that playlist immediately re-emits whatever the database now actually contains.
  - **Fix:** a `Mutex` around both `savePlaylist` and `deletePlaylist` now forces every write
    through one at a time, strictly in launch order.
  - Why that's sufficient: each write is always built from the freshest in-memory state at the
    moment it was launched, so whichever one lands *last* is guaranteed to already be correct.

---

## 8. Playlists: creating, drag-and-drop reordering, deleting

**Creating.**

- Every current creation path (Home screen's mood dialog, the "Playlists for You" empty-state
  tile, the Playlist tab's `+` button) produces an *empty* playlist tagged with a mood name — see
  [§10](#10-known-gaps-and-open-items) for why this doesn't currently go through the AI pipeline.
- The user then adds songs from their own device library via a dedicated file-picker screen
  (with a system audio-permission flow gating it, per Android's scoped-storage rules).

### Drag-and-drop reordering, mechanically

Handled by a small purpose-built class (`DragDropListState`), not a third-party library — worth
walking through in full since "how does the drag actually work" is a reasonable thing to be
asked to explain.

1. **Entering reorder mode.** Tapping the header's reorder icon dispatches
   `PlaylistIntent.ToggleReorderMode`, flipping `state.isReorderMode = true`. Nothing is written
   to the database at this point — it's purely a UI mode switch.
2. **The gesture is long-press-gated.** While in reorder mode, the song list installs
   `detectDragGesturesAfterLongPress` — a normal tap still plays the song; only a sustained
   long-press starts a drag. Deliberate: it stops an ordinary scroll or tap from being misread
   as the start of a reorder.
3. **Starting a drag.** On long-press, `DragDropListState.onDragStart(offset)`:
   - scans every currently *visible* row's on-screen bounds and finds which one contains the
     touch point
   - remembers that row's stable key (its `song.id`, not its list index) as the dragged item
   - snapshots its current position as the reference point everything else is measured against
   - tracking by ID rather than index is what lets the drag keep following the *same logical
     song* even as its index shifts underneath it mid-drag
4. **While dragging.**
   - every finger movement accumulates into a running distance total
   - from that, the code computes where the dragged row's top/bottom edges *would currently be*
   - it checks every other visible row for whether its bounds now overlap that virtual position
   - it only counts as "crossed" (rather than merely touching) once the dragged edge has moved
     *past* the midpoint of the row being displaced — a one-sided threshold that's what stops
     the list from flickering back and forth once two rows are roughly aligned
5. **The actual swap.** Once a target is found:
   - the drag state calls back up to `PlaylistViewModel.reorderSongs(fromIndex, toIndex)`
   - which does a plain in-memory `MutableList.removeAt` + `.add` on the current playlist's song
     list and updates UI state — instant, synchronous, **not yet a database write**
   - the list visually reorders immediately because Compose recomposes off that new in-memory
     state
6. **Rebasing after each swap.**
   - right after firing that callback, the drag state resets its reference point to the row it
     just swapped with, and zeroes its running distance total
   - without this, the accumulated distance would keep compounding relative to the *original*
     starting position even though the list has now reshuffled underneath it, and the dragged
     row would visually drift out of sync with the finger after a few swaps
7. **Ending the drag.** Lifting the finger (or the drag being cancelled) just clears the drag
   state — there's nothing left to "commit," because every swap already happened live, in
   memory, the moment it was detected in step 5.
8. **Persisting the result.**
   - none of the above touches the database
   - the reordered list lives only in view state until the user taps the reorder icon *again* to
     exit reorder mode
   - that second tap is what actually calls `playlistRepository.savePlaylist(...)`, going
     through the transaction- and `Mutex`-guarded write described in §7
   - a small spinner (`state.isSaving`) replaces the reorder icon while that save is in flight

**Why the database isn't written on every single swap:**

- That would mean a full four-statement transactional write potentially dozens of times per
  second while actively dragging — wasteful, and self-defeating, since the database's own live
  `Flow` would then try to push each of those intermediate states back into the screen while the
  user is still mid-drag.
- `PlaylistViewModel` guards against exactly this: while `isReorderMode` or `isSaving` is true,
  incoming updates from the database's live `Flow` are deliberately ignored, so the reactive DB
  layer never fights with an in-progress local reorder.

---

## 9. The Now Playing screen: adaptive layout, color, and lyrics

**Adaptive layout.**

- Two distinct layouts: a tall "portrait" layout (full-size album art, generous spacing) and a
  "compact-height" layout (small art, tight controls), used whenever the available screen height
  drops below a breakpoint.
- Deliberately keyed off actual available height rather than device orientation, so it also does
  the right thing in split-screen / multi-window and on foldables, not just literal phone
  rotation.

**Dominant color extraction.**

- The screen's background tint is derived from the currently playing song's own album art using
  Android's `Palette` API.
- Each new song's art is decoded once, its "vibrant" color extracted, and the background
  animates toward that color over one second.
- Uses the app's shared, globally-configured image loader (rather than a throwaway one-off
  loader) specifically so it benefits from the same embedded-art fallback logic described in §6,
  instead of only working for songs with clean MediaStore thumbnails.

**Synced lyrics.**

- Lyrics aren't bundled with any song — they're generated on demand by Gemini, given the song's
  title, artist, and (optionally) source film, with each line tagged to a millisecond timestamp.
- Because thousands of songs can share the same title, the user is asked to confirm the exact
  title/artist (and, optionally, the film) before generation — specifically to avoid Gemini
  generating lyrics for the wrong song.
- Once generated, lyrics are cached in the database (`SongEntity.lyrics`) keyed by song ID, so
  re-opening the same song's lyrics is instant and free — no second AI call.

**Morphing controls.**

- The play/pause/skip buttons use Android's new Material 3 Expressive `MaterialShapes`.
- Pressing a button morphs its shape (not just scales or ripples) between two defined
  `RoundedPolygon` shapes, as a physical-feeling press animation.

---

## 10. Known gaps and open items

Documented here deliberately, rather than glossed over, because an accurate account of what's
solid vs. what's unfinished is more useful than a document that oversells the app's current state.

- **AI playlist generation has no UI entry point (headline item).**
  - As explained in §1/§5: the entire Gemini + YouTube recommendation pipeline is implemented,
    tested at the code level, and wired into the ViewModel layer — but no button anywhere in the
    current UI dispatches `HomeIntent.ConfirmPlaylistGeneration`, the only thing that triggers
    it.
  - This was a deliberate removal in an earlier design pass (a "Generate by AI" vs. "Create
    Empty" choice was simplified down to just "Create Empty"), and the decision of
    whether/where to re-expose AI generation was explicitly left open rather than resolved.
  - Right now, MoodTune functions as a local music player with an unused AI backend, not a
    mood-to-playlist generator, from a user's perspective.
- **Wear OS module is an unimplemented template.**
  - `wear/` currently contains only the default Android Studio Wear OS starter code (a "Hello
    World" screen with placeholder buttons) — no MoodTune-specific logic, no shared playback
    state, nothing connected to the rest of the app.
  - Real Wear OS support (Now Playing state, playback controls from the watch) hasn't started
    yet.
- **`core/analytics` is an empty scaffold.** Declared as a Gradle module with a manifest and
  nothing else — reserved for future use, not wired into anything.
- **API keys are logged in every build, including release.**
  - `core/network`'s Ktor client logs full request URLs (including the Gemini API key, which is
    passed as a URL query parameter) at the most verbose logging level, unconditionally — not
    gated behind a debug-only check.
  - Real, if narrow, risk: the key would appear in plaintext in device logs on a release build.
  - Flagged, not yet fixed — fixing it requires enabling Gradle's `buildConfig` feature for that
    module to gate logging behind `BuildConfig.DEBUG`.
- **Two Now Playing layouts are heavily duplicated.**
  - `PortraitNowPlayingContent` and `CompactHeightNowPlayingContent` in `SongDetail.kt` share
    the same structure almost line-for-line (differing mainly in sizing constants).
  - Left as-is deliberately — de-duplicating ~500 lines of actively-designed UI code
    mid-redesign was judged too risky for a cleanup pass. Worth revisiting once the visual
    design settles.
- **Feature modules hardcode SDK versions.**
  - `compileSdk` / `minSdk` are literal numbers in `feature/home`, `feature/playlist`,
    `feature/songdetail`'s `build.gradle.kts`, instead of reading from the shared version
    catalog like every other module does.
  - Consistent enough across all three that fixing just one would be more inconsistent, not
    less — worth doing as a single pass across all three together if ever addressed.

---

## 11. A note on how this document was produced

- Written after a systematic, module-by-module review of the entire codebase — every Kotlin file
  in `app`, `core/*`, `domain`, `data`, `infrastructure`, and every `feature/*` module.
- Along the way, numerous small robustness issues were found and fixed: dead code removed, a
  data-loss window in playlist saves closed, a race condition fixed, a few real correctness bugs
  corrected.
- The gaps listed in §10 are the ones deliberately **not** fixed — either because they're product
  decisions rather than code defects, or because the fix was judged too large/risky for a
  cleanup pass.
- Everything else described above reflects the code as it actually behaves today, not as
  originally planned or documented elsewhere.
