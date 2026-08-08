# MoodTune v2 — Redesign Plan (FINAL)

> Based on: `moddtune_design_v2/homescreen.png`, `playlists.png`, `playlist_songs.png`
> All open questions answered. Ready for implementation.

---

## ✅ Decisions & Answers

| # | Question | Answer |
|---|---|---|
| Q1 | Playlist Songs title | Show **mood name** (keep current) |
| Q2 | Back arrow on Playlists List | **Remove** — this is a top-level tab |
| Q3 | In the Mix layout | **Horizontal cards** (keep current LazyRow) |
| Q4 | Card stripe / placeholder art | Use **drawable images** added by user; pick randomly per playlist when no album art |
| Q5 | Avatar placeholder | Wire `painterResource` slot, keep `Person` icon until drawables land |

---

## 🎨 Design Language

| Token | Value |
|---|---|
| Background | Near-black `#121212` (current theme dark bg) |
| Card surface | `#1A1A1A` |
| Accent orange | `#FF5722` (See All, play button ring) |
| Accent teal | `#009688` |
| Accent purple | `#7B61FF` |
| Decorative triangle | Top-right corner, already present in Playlist.kt ✅ |
| Playlist card | Square, `RoundedCornerShape(16.dp)`, gradient + diagonal stripe |
| Song art | `52.dp`, `RoundedCornerShape(8.dp)` |
| Circular buttons | `44–48.dp` circles |
| Typography label | `bodySmall`, grey |
| Typography title | `displaySmall`, ExtraBold, white |

---

## 🗃️ Data Flow (no domain model changes needed)

### What already exists ✅
- `PlaylistEntity.playCount` + `PlaylistEntity.lastPlayedAt` → DB columns exist
- `SongEntity.playCount` + `SongEntity.lastPlayedAt` → DB columns exist
- `PlaylistDao.updateLastPlayedAt()` → already **increments** `playCount` + updates timestamp ✅
- `SongDao.incrementSongPlayCount()` → already increments count ✅
- `PlaylistDao.getMostPlayedPlaylists()` → query exists ✅
- `SongDao.getMostPlayedSongs()` → query exists ✅
- `SongsRepository.incrementSongPlayCount()` → exists ✅
- `PlaylistRepository.markPlaylistAsPlayed()` → exists ✅
- `MusicPlayerManager.onMediaItemTransition` → **already calls** `incrementSongPlayCount` when a song starts playing ✅

### What's missing / wiring gaps ⚠️

#### Gap 1 — Playlist visit counter not triggered on playlist open
- **Current:** `markPlaylistAsPlayed()` (which increments `playCount`) is only called when user taps a playlist from:
  - `PlaylistListScreen` (via `PlaylistNavigation.Detail`)
  - `HomeScreen` via `HomeIntent.MarkPlaylistAsPlayed`
- **Problem:** When app navigates to `MoodDetailScreen` via `initialMood` (e.g. from Home carousel), `markPlaylistAsPlayed` IS called in `PlaylistScreen.LaunchedEffect`. ✅
- **Problem:** When tapping from `PlaylistListScreen`, `MarkPlaylistAsPlayed` is called but only if `state.playlists.find { p -> p.mood == mood }` finds a match. If the list hasn't loaded yet, this silently skips. **Risk: low, but worth noting.**
- **Action:** No structural change needed — flow is essentially correct. Will add a safety fallback in `PlaylistViewModel.SelectPlaylist` to also call `markPlaylistAsPlayed` as a secondary trigger.

#### Gap 2 — `HomeScreen.Playlists for You` shows `recentlyPlayedPlaylists`, should show ALL playlists
- **Current:** `PlaylistsForYouSection` is fed `state.recentlyPlayedPlaylists`
- **Target:** Show **all user playlists** (most played first, then rest)
- **Fix:** Use a new state field `allPlaylists` in `HomeState` (or reuse existing `state.recentlyPlayedPlaylists` by replacing the data source). Best approach: add `HomeViewModel.loadAllPlaylists()` which calls `playlistRepository.getPlaylists()` and stores to a new `allPlaylists: List<Playlist>` in `HomeState`.

#### Gap 3 — No `PlaylistRepository` method called `getAllPlaylists` exposed to HomeViewModel
- `PlaylistRepository.getPlaylists()` exists ✅ — just not wired into HomeViewModel yet.
- **Fix:** Add `loadAllPlaylists()` to `HomeViewModel` calling `playlistRepository.getPlaylists()` → stored as `allPlaylists` in `HomeState`. No new interface method needed.

---

## 📋 Files to Change

```
core/database/         → NO CHANGES (schema already complete)
domain/                → NO CHANGES (interfaces already complete)
data/                  → NO CHANGES (implementations already complete)
core/utils/            → NO CHANGES (MusicPlayerManager already increments song count)

feature/home/ui/HomeViewModel.kt    → Add allPlaylists field + loadAllPlaylists()
feature/home/ui/Home.kt             → UI redesign (see below)
feature/playlist/ui/Playlist.kt     → UI redesign (see below)
```

---

## 🔧 Implementation Plan — Phase by Phase

---

### Phase 1 — Fix `Home.kt` build bug (MUST DO FIRST)

**Problem:** `Home.kt` has **duplicate declarations** of `HomeScreenRoot` and `HomeScreen` (lines ~40–66 are stale with orphaned `import` statements between functions).

**Fix:** Remove lines 40–66 entirely (the first stale block with misplaced imports), keep the second complete block.

---

### Phase 2 — HomeViewModel: wire `allPlaylists`

**File:** `HomeViewModel.kt`

1. Add to `HomeState`:
   ```kotlin
   val allPlaylists: List<Playlist> = emptyList()
   ```

2. Add `loadAllPlaylists()` in `init {}`:
   ```kotlin
   private fun loadAllPlaylists() {
       playlistRepository.getPlaylists()
           .onEach { playlists ->
               _state.update { it.copy(allPlaylists = playlists) }
           }
           .launchIn(viewModelScope)
   }
   ```

3. Call `loadAllPlaylists()` in `init`.

---

### Phase 3 — Home Screen UI (`Home.kt`)

#### 3.1 `HomeTopBar`
- Change `CenterAlignedTopAppBar` → `TopAppBar` so "Featured" is left-aligned.
- Keep all existing logic (search icon → vibe generator dialog).

#### 3.2 `FeaturedCarousel`
- **Data:** `state.mostPlayedPlaylists` (already wired ✅)
- **Fallback:** Keep existing dummy playlists fallback ✅
- **UI change:** Increase `contentPadding` end value so next card peeks from right edge.
- **UI change:** Display `song.imageUrl` in the top-right of the card using `AsyncImage` with `offset(y = (-16).dp)` to partially overflow the card top, creating the "floating album art" effect from the mockup. Keep `MusicNote` icon fallback when null.
- **No logic changes.**

#### 3.3 `PlaylistsForYouSection`
- **Data source change:** Use `state.allPlaylists` instead of `state.recentlyPlayedPlaylists`.
- **Fallback:** If `allPlaylists` is empty, show dummies (same as now).
- **Card width:** `140.dp` → `120.dp`.
- **UI:** Add diagonal stripe decoration inside each card:
  - A `Box` rotated 45° with a semi-transparent white/brand color strip overlaid inside the gradient card.
- **Avatar slot:** Keep `Icons.Default.Person` now. Add a `@DrawableRes` parameter placeholder comment so it's easy to swap when drawables arrive.
- **On click:** Same `onPlaylistClick` logic — mark as played + navigate ✅.

#### 3.4 `InTheMixSection`
- **Data:** `state.mostPlayedSongs` (already wired ✅)
- **Layout:** Keep existing horizontal `LazyRow` of square song cards.
- **Header:** "In the Mix" + "See All" in orange — already implemented ✅.
- **No changes needed unless visual polish required.**

---

### Phase 4 — Playlists List Screen (`PlaylistListScreen` in `Playlist.kt`)

- **Remove back arrow:** The header `Row` currently doesn't have a back button — confirmed nothing to remove. ✅ (The mockup arrow we saw is not needed on the List screen.)
- **Three-dot menu button:** Add `IconButton(Icons.Default.MoreVert)` in the header row top-right. No action logic yet — just UI placeholder.
- **`PlaylistGridCard` diagonal stripe:** Add a rotated semi-transparent strip inside the gradient box, same technique as Home cards.
- **Avatar circles:** Already implemented ✅. Ensure `border` import is present.
- **"Trending Now / Playlists" header:** Already correct ✅.
- **Shuffle + Play buttons:** Already correct ✅.

---

### Phase 5 — Playlist Songs Screen (`MoodDetailScreen` in `Playlist.kt`)

- **Title:** Keep showing `mood` as `displaySmall` heading ✅.
- **"Trending Now" label:** Keep ✅.
- **Subtitle:** `"Caziq Music • ${songs.size} songs"` ✅.
- **Filter + Shuffle + Play buttons:** Already correct ✅.
- **`SongListItem`:** Already has album art, Explicit badge, `artist • mood`, duration `"3:30"`, 3-dot menu ✅.
- **No functional changes** — all logic stays intact.
- Minor visual: ensure song art is `52.dp` with `RoundedCornerShape(8.dp)` ✅.

---

## 📦 Drawable Placeholder Strategy

When the user adds playlist cover images to the drawables folder (e.g. `playlist_cover_1.png`, `playlist_cover_2.png` etc.), the cards should use them as fallback when `playlist.songs.firstOrNull()?.imageUrl == null`.

**Implementation:**
```kotlin
// In PlaylistGridCard and PlaylistsForYouSection card
val placeholderDrawables = listOf(
    R.drawable.playlist_cover_1,
    R.drawable.playlist_cover_2,
    // ... more as added
)
// Pick deterministically by mood hash so same playlist always gets same image
val placeholderRes = placeholderDrawables[
    Math.abs(playlist.mood.hashCode()) % placeholderDrawables.size
]
```

For now, we'll define the list with **empty / commented slots** and use the abstract gradient card until the drawables are added. No crash, graceful fallback.

---

## 🧪 Play Counter — How It Works End-to-End

```
User taps playlist (HomeScreen or PlaylistList)
    → PlaylistIntent.MarkPlaylistAsPlayed(id)
    → PlaylistRepository.markPlaylistAsPlayed(id)
    → PlaylistDao.updateLastPlayedAt(id, timestamp)  [SQL: playCount + 1]
    ✅ Playlist play count incremented

User taps a song in MoodDetailScreen
    → PlaylistIntent.PlayPlaylist(index)
    → MusicPlayerManager.playPlaylist(songs, index)
    → MediaController.play()
    → onMediaItemTransition fires
    → SongsRepository.incrementSongPlayCount(songId)
    → SongDao.incrementSongPlayCount(id, timestamp)  [SQL: playCount + 1]
    ✅ Song play count incremented

Home screen loads
    → HomeViewModel.loadMostPlayed()
    → PlaylistRepository.getMostPlayedPlaylists()  [ORDER BY playCount DESC]
    → state.mostPlayedPlaylists → FeaturedCarousel ✅

    → SongsRepository.getMostPlayedSongs()  [ORDER BY playCount DESC]
    → state.mostPlayedSongs → InTheMixSection ✅

    → PlaylistRepository.getPlaylists()  [all playlists]
    → state.allPlaylists → PlaylistsForYouSection ✅
```

---

## 📝 Summary of ALL File Changes

| File | Type of Change | Scope |
|---|---|---|
| `HomeViewModel.kt` | Add `allPlaylists` to state + `loadAllPlaylists()` | Small — 2 additions |
| `Home.kt` | Fix duplicate declarations + TopBar + Carousel + Cards | Medium |
| `Playlist.kt` | Remove back arrow (already absent) + 3-dot button + stripe decoration | Small |
| All domain/data/db files | **No changes** | — |
| `MusicPlayerManager.kt` | **No changes** | — |

---

## ❓ One Remaining Question

> **Q6 — Drawable naming convention:**
> When you add playlist cover images to the drawables folder, what naming convention will you use?
> e.g. `playlist_cover_1`, `mood_reggae`, `cover_art_1`?
> This determines how we wire the fallback array.
> *(Implementation can proceed without this — we'll use a placeholder list that you fill in later.)*
