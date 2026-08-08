# MoodTune

A mood-based AI music playlist app for Android. Users describe their current mood in natural language, and Gemini AI is designed to generate a personalized playlist with songs that match the emotional context, each with an AI explanation of why it fits. **As of this writing, that generation flow has no UI entry point** — see "Current State" below and `PROJECT.md` §10 before assuming it's reachable.

For the full narrative explanation of every feature (what/how/why, written for a client or stakeholder audience), see **`PROJECT.md`**. This file stays terse and dev-facing.

## Core Purpose
Bridge the gap between how people feel and what they listen to. Instead of browsing genres or artists, users describe feelings ("I need something for a late-night drive in the rain") and get a curated playlist with an emotional narrative.

## Current State (read this before demoing or documenting)
- The Gemini + YouTube AI recommendation pipeline (`GeneratePlaylistUseCase`) is fully implemented and wired into `HomeViewModel`, but no composable anywhere dispatches `HomeIntent.ConfirmPlaylistGeneration` — the only thing that triggers it. Every current "create playlist" path (Home's mood dialog, the empty-state tile, the Playlist tab's `+`) only creates an **empty** playlist that the user fills manually from device files. This was a deliberate simplification in an earlier session that left the decision of whether/where to re-expose AI generation explicitly unresolved (see `summary.md` §2).
- `wear/` is the unmodified Android Studio Wear OS starter template — no MoodTune logic, not yet started.
- `core/analytics` is a scaffolded empty module (manifest only), reserved for later.

## Architecture
- **Pattern:** MVI (Model-View-Intent) with Unidirectional Data Flow
- **Modules:** app, core/{database,utils,network,ui,analytics}, domain, data, infrastructure, config, feature/{home,playlist,songdetail}, wear
- **UI:** Jetpack Compose + Material3
- **Playback:** Media3 / ExoPlayer via MediaPlaybackService (foreground service). Playback intentionally stops on `onTaskRemoved` (swiping the app from recents) rather than continuing in the background — confirmed intentional, not a bug.
- **Database:** Room, currently version 6 — PlaylistEntity (id, mood, playCount, lastPlayedAt), SongEntity (id, title, artist, reason, imageUrl, externalUrl, mood, explanation, playCount, lastPlayedAt, durationMs, lyrics, localAlbumArtPath). `PlaylistRepositoryImpl` writes are transaction-wrapped *and* Mutex-serialized — see `PROJECT.md` §7 before touching `savePlaylist`/`deletePlaylist`.
- **AI:** Google Gemini API via Ktor HTTP client; API keys are never bundled in the app — fetched at runtime from Firebase Remote Config via the `config` module (`ConfigRepository.ensureConfig`), cached locally via DataStore for offline resilience. See `PROJECT.md` §4.
- **DI:** Koin
- **Navigation:** Navigation3 (NavDisplay + rememberNavBackStack)
- **Images:** Coil3, with a custom `AlbumArtFetcher` (app module) registered on the app-wide singleton loader for MediaStore album-art URIs. Always use `SingletonImageLoader.get(context)` if you ever need a raw `ImageLoader` outside Compose — a throwaway `ImageLoader(context)` skips both the shared cache and this fetcher.
- **Widget:** Jetpack Glance (small/medium/large responsive sizes)

## Key Features
- Gemini AI playlist generation from natural language mood input — **implemented but currently unreachable from the UI**, see "Current State"
- AI-generated, playback-synchronized lyrics for any song (Now Playing screen, cached in `SongEntity.lyrics`), gated behind a title/artist confirmation dialog to reduce Gemini mixing up same-titled songs
- Full ExoPlayer playback queue (local files + external URL fallback)
- On-device audio scan with embedded album art extraction + on-disk caching (`LocalSongDataSource`), independent of MediaStore's own (often stale) album-art cache
- Drag-and-drop song reorder within playlists (`DragDropListState`, tracks by stable key not index)
- Device file picker to add local music to playlists
- Play count tracking for both songs and playlists
- Home screen widget with playback controls
- Adaptive two-pane layout for tablets (Now Playing + Queue), and a separate compact-height layout for landscape/split-screen Now Playing
- Wear OS companion module — **not started**, template only

## Key Files
- `feature/home/ui/Home.kt` + `HomeViewModel.kt` — main screen, vibe generator dialog
- `feature/playlist/ui/Playlist.kt` + `PlaylistViewModel.kt` — playlist grid, song list, drag-drop
- `feature/songdetail/ui/SongDetail.kt` — Now Playing screen with morphing controls
- `core/utils/MusicPlayerManager.kt` — ExoPlayer wrapper, playback state StateFlow
- `core/ui/util/SongArt.kt` — `Song?.albumArtModel()`, the single source of truth for resolving a song's art (local embedded art first, remote `imageUrl` fallback); use this instead of re-deriving the fallback inline
- `infrastructure/MediaPlaybackService.kt` — foreground MediaSessionService
- `infrastructure/GeminiAPIDataSource.kt` — Gemini prompt + JSON parsing, shared `promptGemini()` helper for both recommendations and lyrics
- `data/repo/PlaylistRepositoryImpl.kt` — transaction + Mutex-guarded playlist writes
- `config/ConfigRepository.kt` — API key resolution (Remote Config + DataStore cache + connectivity gating)
- `app/MainActivity.kt` — navigation host, bottom nav/rail (no mini player currently — removed as dead code, replaced by per-screen `NowPlayingIndicator`)

## Conventions
- Screens follow: `XxxScreenRoot` (injects ViewModel) → `XxxScreen` (pure composable with state/intent)
- Colors: background #121212, accent orange #FF5722, accent teal #009688, accent purple #7B61FF
- `getMoodColor(mood)` must come from `core/ui/theme/Color.kt` — single source of truth, never duplicate locally
- A song's art model for `AsyncImage` must come from `Song?.albumArtModel()` (`core/ui/util/SongArt.kt`) — never re-derive the local-path-then-imageUrl fallback inline
- DB migrations: bump version in `MoodTuneDatabase.kt` and add a migration object. `exportSchema = false`, so there's no schema JSON to validate a hand-written migration against — test the actual upgrade path (old build → new build, confirm data survives) before shipping one
- Gemini generates songs as a JSON array; parsed in `GeminiAPIDataSource`
- Sealed class singletons use `data object`, not plain `object` (consistent across the codebase)
- Repository writes with multiple related DB statements must be wrapped in `db.withTransaction { }`; if the same repository can receive overlapping concurrent writes to the same row (e.g. rapid user actions), also serialize with a `Mutex` — see `PlaylistRepositoryImpl` for the pattern
- `core/database/entity/PlaylistWithSongs.kt` and its mapper are unused — every real playlist+songs query is a raw `@Query` LEFT JOIN returning `Map<PlaylistEntity, List<SongEntity>>` (see `PlaylistDao`), not Room's `@Relation` style. Don't assume `PlaylistWithSongs` is "the" query to use.
- Drag-and-drop reordering (`DragDropListState` in `feature/playlist`) only mutates in-memory state per swap — it does **not** write to the database live. The DB write happens once, when the user exits reorder mode (`PlaylistIntent.ToggleReorderMode`). Don't add a DB write inside the drag callback itself — see `PROJECT.md` §8 for why.
