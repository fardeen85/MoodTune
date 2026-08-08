# MoodTune Testing Plan

Functionality test cases to write. Mood generation logic (Gemini AI playlist/lyrics generation, YouTube search) is excluded for now and will be covered separately.

## Lyrics generation (Gemini)

- [ ] `GeminiAPIDataSource.getLyrics(title, artist, durationMs)` — strips markdown fences (` ```json ` / ` ``` `) before decoding
- [ ] `GeminiAPIDataSource.getLyrics(title, artist, durationMs)` — handles response with no fences / only one fence / extra whitespace
- [ ] `GeminiAPIDataSource.getLyrics(title, artist, durationMs)` — empty/missing `candidates` falls back to `""` and fails decode without crashing
- [ ] `GeminiAPIDataSource.getLyrics(title, artist, durationMs)` — malformed JSON throws `SerializationException`
- [ ] `SongsRepositoryImpl.getCachedLyrics(songId)` — returns decoded lyrics when cache present
- [ ] `SongsRepositoryImpl.getCachedLyrics(songId)` — returns null on cache miss
- [ ] `SongsRepositoryImpl.generateLyrics(...)` — fetches, encodes, and persists to DB (write-through)
- [ ] `SongDetailViewModel.generateLyrics()` — duration fallback chain: state duration → `song.durationMs` → default `240_000L`
- [ ] `SongDetailViewModel.generateLyrics()` — uses cached lyrics when available, skips regeneration
- [ ] `SongDetailViewModel.generateLyrics()` — cache miss triggers generation and caches result
- [ ] `SongDetailViewModel.generateLyrics()` — failure path sets `LyricsState.Error` (via `runCatching`)

## Config

- [ ] `ConfigRepository.ensureConfig(hasKey)` — `config/ConfigRepository.kt` — cached-keys-present short-circuit path
- [ ] `ConfigRepository.ensureConfig(hasKey)` — offline throws `NoInternetException`
- [ ] `ConfigRepository.ensureConfig(hasKey)` — online + missing keys triggers `refresh()` then waits on `configFlow`
- [ ] `ConfigRepository.ensureConfig(hasKey)` — timeout after 15s throws `ConfigNotAvailableException`
- [ ] `ConfigRepository.refresh()` — swallows fetch failures, keeps stale cached config, doesn't propagate exception
- [ ] `ConfigDataStore.configFlow` — maps raw `Preferences` to `AppConfig` with correct `?: ""` defaults
- [ ] `ConnectivityChecker.isConnectedFlow` — emits correct initial state before any callback fires
- [ ] `ConnectivityChecker.isConnectedFlow` — dedupes repeated states via `distinctUntilChanged()`

## Drag-and-drop reorder

- [ ] `PlaylistViewModel.reorderSongs(fromIndex, toIndex)` — valid indices reorder the list correctly
- [ ] `PlaylistViewModel.reorderSongs(fromIndex, toIndex)` — out-of-bounds indices are ignored (guard check)
- [ ] `DragDropListState.onDrag(delta)` — swap triggers when dragging downward past `currentBottom`
- [ ] `DragDropListState.onDrag(delta)` — swap triggers when dragging upward past `currentTop`
- [ ] `LazyListItemInfo.overlaps(top, bottom)` — pure interval-overlap check, boundary cases
- [ ] `DragDropListState.getDraggedOffset()` — arithmetic correctness given original/dragdistance/currentItemInfo

## Playlist persistence

- [ ] `PlaylistRepositoryImpl.savePlaylist(playlist)` — preserves existing playlist `playCount`/`lastPlayedAt` on overwrite
- [ ] `PlaylistRepositoryImpl.savePlaylist(playlist)` — preserves existing song `playCount`/`lastPlayedAt` on re-save
- [ ] `PlaylistRepositoryImpl.savePlaylist(playlist)` — deletes and rebuilds cross-refs with correct position ordering
- [ ] `PlaylistMapper.toDomainList()` — empty playlist (LEFT JOIN null row) maps to empty song list, not `[null]`
- [ ] `PlaylistMapper.toDomainSingle()` — same null-filtering check for single-playlist query
- [ ] `PlaylistDao.getPlaylistByMood` / `getPlaylistByMoodFlow` — case-insensitive mood matching
- [ ] `PlaylistDao.getRecentlyPlayedPlaylists()` — filters `lastPlayedAt IS NOT NULL`, correct ordering/limit
- [ ] `PlaylistDao.getMostPlayedPlaylists()` — filters `playCount > 0`, orders by `playCount DESC, lastPlayedAt DESC`
- [ ] `MIGRATION_2_3` / `MIGRATION_3_4` / `MIGRATION_4_5` — schema upgrades don't crash (Room `MigrationTestHelper`)

## Album art

- [ ] `AlbumArtFetcher.fetch()` — returns art from MediaStore `albumart` cache when present
- [ ] `AlbumArtFetcher.fetch()` — falls through to embedded-art extraction when cache miss/empty
- [ ] `AlbumArtFetcher.fetch()` — returns null cleanly when `albumId` is unparseable
- [ ] `AlbumArtFetcher.querySongContentUriForAlbum(albumId)` — builds correct content URI from cursor
- [ ] `AlbumArtFetcher.getEmbeddedAlbumArt(songUri)` — always calls `retriever.release()`, even on exception
- [ ] `AlbumArtFetcher.Factory.create(...)` — matches only `content://media/.../audio/albums/...` URIs
- [ ] `LocalSongDataSource.saveEmbeddedArt(filePath, activeHashes)` — skips re-extraction if art file already exists
- [ ] `LocalSongDataSource.saveEmbeddedArt(filePath, activeHashes)` — falls back to raw bytes when decode fails
- [ ] `LocalSongDataSource.saveEmbeddedArt(filePath, activeHashes)` — deletes partially-written file on exception
- [ ] `LocalSongDataSource.deleteOrphanedArt(activeHashes)` — deletes unreferenced art, keeps referenced art
- [ ] `LocalSongDataSource.md5(input)` — pinned-output test (stable hash used as cache key)

## MusicPlayerManager

- [ ] `MusicPlayerManager.updateState()` — uses queue-lookup `Song` when `currentMediaId` matches
- [ ] `MusicPlayerManager.updateState()` — falls back to reconstructed `Song` from controller metadata on queue-lookup miss
- [ ] `MusicPlayerManager.playPlaylist(songs, startIndex)` — artwork URI prefers `localAlbumArtPath` over `imageUrl`
- [ ] `MusicPlayerManager.onMediaItemTransition` — increments play count exactly once per transition, correct song id
- [ ] `MusicPlayerManager.release()` — resets `_playbackState.value` and cleans up progress job

## ViewModel intent reducers

- [ ] `HomeViewModel.onIntent(RequestPlaylistGeneration)` — blank mood is a no-op (early return)
- [ ] `HomeViewModel.createEmptyPlaylist(mood)` — short-circuits when a playlist for that mood already exists
- [ ] `HomeViewModel.onIntent(ConfirmPlaylistGeneration / CreateEmptyPlaylist)` — clears `pendingMood`
- [ ] `PlaylistViewModel.onIntent(ToggleReorderMode)` — turning reorder mode off persists to DB
- [ ] `PlaylistViewModel.onIntent(ToggleReorderMode)` — turning reorder mode on does not persist
- [ ] `PlaylistViewModel.onIntent(RemoveSong)` — filters song out of `currentPlaylist.songs` and saves
- [ ] `PlaylistViewModel.addSelectedSongsToPlaylist(mood)` — merges selection into existing playlist, deduped by id
- [ ] `PlaylistViewModel.addSelectedSongsToPlaylist(mood)` — empty selection is a no-op (early return)
- [ ] `PlaylistViewModel.observeCurrentPlaylist()` — suppresses stale DB emissions while `isReorderMode` or `isSaving` is true
