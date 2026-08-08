# MoodTune v2 — Redesign Plan (FINAL)

> **Screens:** Home, Playlists, Playlist Songs
> **Mockups:** `moddtune_design_v2/homescreen.png`, `playlists.png`, `playlist_songs.png`
> **Rule:** All existing functionality, logic, navigation, ViewModels, domain models, and DB schema are preserved. UI layer only — except the HomeViewModel `allPlaylists` wiring gap.

---

## ✅ All Decisions Resolved

| # | Question | Answer |
|---|---|---|
| Q1 | Playlist Songs screen title | Show **mood name** (keep current) |
| Q2 | Back arrow on Playlists List screen | **Remove** — this is a top-level tab destination |
| Q3 | "In the Mix" layout | Keep **horizontal `LazyRow`** of cards |
| Q4 | Card placeholder art | User will add drawables; pick one **randomly by mood hash** when no `imageUrl` |
| Q5 | Avatar placeholder slot | Keep `Icons.Default.Person` now; slot ready for drawables later |
| Q6 | Drawable naming convention | **`playlist_cover_1`, `playlist_cover_2`, ...** (sequential numbering) |

---

## 🎨 Design Language (from mockups)

| Token | Value |
|---|---|
| Background | Near-black `#121212` |
| Card surface | `#1A1A1A` |
| Accent orange | `#FF5722` — used for "See All" link and play button accent |
| Accent teal | `#009688` |
| Accent purple | `#7B61FF` |
| Decorative corner | Top-right rotated triangle (already in Playlist.kt ✅) |
| Playlist card | Square, `RoundedCornerShape(16.dp)`, gradient + diagonal stripe layer |
| Song row album art | `52.dp`, `RoundedCornerShape(8.dp)` |
| Circular action buttons | `44–48.dp` filled circles |
| Typography — section label | `bodySmall`, grey, e.g. "Trending Now" |
| Typography — screen title | `displaySmall`, ExtraBold, white |
| Typography — subtitle | `bodySmall`, grey, e.g. "Caziq Music • 20 playlists" |

---

## 🗃️ Data Layer Audit — What Already Exists

The **entire tracking infrastructure is already built**. No DB migrations, no new repository methods, no new domain models.

### Database Schema ✅
```
PlaylistEntity  → playCount: Int, lastPlayedAt: Long?
SongEntity      → playCount: Int, lastPlayedAt: Long?
```

### DAOs ✅
```kotlin
// PlaylistDao
UPDATE playlists SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :id
SELECT * FROM playlists WHERE playCount > 0 ORDER BY playCount DESC LIMIT 10   // getMostPlayedPlaylists
SELECT * FROM playlists ORDER BY position ASC                                    // getPlaylists (all)

// SongDao
UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :id
SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT 10        // getMostPlayedSongs
```

### Repository & Player ✅
```kotlin
PlaylistRepository.markPlaylistAsPlayed(id)        // increments playCount
PlaylistRepository.getMostPlayedPlaylists()        // Flow<List<Playlist>>
PlaylistRepository.getPlaylists()                  // Flow<List<Playlist>> — all
SongsRepository.incrementSongPlayCount(id)         // increments playCount
SongsRepository.getMostPlayedSongs()               // Flow<List<Song>>

// MusicPlayerManager — already wired!
onMediaItemTransition { songId ->
    songsRepository.incrementSongPlayCount(songId)  // fires every song start ✅
}
```

---

## 🔄 Play Counter — Full End-to-End Flow

```
╔══════════════════════════════════════════════════════════════════╗
║  PLAYLIST PLAY COUNT                                             ║
╠══════════════════════════════════════════════════════════════════╣
║  User taps playlist (HomeScreen or PlaylistListScreen)           ║
║    → HomeIntent.MarkPlaylistAsPlayed(id)  OR                     ║
║      PlaylistIntent.MarkPlaylistAsPlayed(id)                     ║
║    → PlaylistRepository.markPlaylistAsPlayed(id)                 ║
║    → PlaylistDao: UPDATE playlists SET playCount = playCount + 1  ║
║    ✅ Playlist play count incremented in DB                       ║
╠══════════════════════════════════════════════════════════════════╣
║  SONG PLAY COUNT                                                 ║
╠══════════════════════════════════════════════════════════════════╣
║  User taps song in MoodDetailScreen                              ║
║    → PlaylistIntent.PlayPlaylist(index)                          ║
║    → MusicPlayerManager.playPlaylist(songs, index)               ║
║    → MediaController.play() → onMediaItemTransition fires        ║
║    → SongsRepository.incrementSongPlayCount(songId)              ║
║    → SongDao: UPDATE songs SET playCount = playCount + 1          ║
║    ✅ Song play count incremented in DB                           ║
╠══════════════════════════════════════════════════════════════════╣
║  HOME SCREEN READS                                               ║
╠══════════════════════════════════════════════════════════════════╣
║  FeaturedCarousel   ← state.mostPlayedPlaylists                  ║
║                       (PlaylistRepository.getMostPlayedPlaylists)║
║  PlaylistsForYou    ← state.allPlaylists (NEW wiring)            ║
║                       (PlaylistRepository.getPlaylists)          ║
║  InTheMix           ← state.mostPlayedSongs                      ║
║                       (SongsRepository.getMostPlayedSongs)       ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## 📋 Files to Change

| File | Change Type | Scope |
|---|---|---|
| `feature/home/ui/HomeViewModel.kt` | Add `allPlaylists` state + `loadAllPlaylists()` | Small — ~10 lines |
| `feature/home/ui/Home.kt` | UI redesign across 4 components | Medium |
| `feature/playlist/ui/Playlist.kt` | 3-dot button + stripe decoration, remove back arrow | Small |
| `core/database/**` | **NO CHANGES** | — |
| `domain/**` | **NO CHANGES** | — |
| `data/**` | **NO CHANGES** | — |
| `core/utils/MusicPlayerManager.kt` | **NO CHANGES** | — |

---

## 🔧 Implementation — Phase by Phase

---

### Phase 1 — Fix Build Bug in `Home.kt` (DO FIRST)

**Problem:** `Home.kt` has stale duplicate declarations of `HomeScreenRoot` and `HomeScreen` between lines ~40–66, with orphaned `import` statements placed between function bodies (invalid Kotlin). This is a current build error.

**Fix:** Delete lines 40–66 (first stale block). Keep only the second, complete block starting at line 67.

Also add missing import:
```kotlin
import androidx.compose.foundation.border
```

---

### Phase 2 — `HomeViewModel.kt` — Wire `allPlaylists`

**File:** `feature/home/ui/HomeViewModel.kt`

#### 2a. Add field to `HomeState`
```kotlin
data class HomeState(
    // ... existing fields ...
    val allPlaylists: List<Playlist> = emptyList(),   // ← ADD THIS
)
```

#### 2b. Add loader function
```kotlin
private fun loadAllPlaylists() {
    playlistRepository.getPlaylists()
        .onEach { playlists ->
            _state.update { it.copy(allPlaylists = playlists) }
        }
        .launchIn(viewModelScope)
}
```

#### 2c. Call in `init`
```kotlin
init {
    loadRecentlyPlayed()
    loadMostPlayed()
    loadAllPlaylists()   // ← ADD THIS
}
```

---

### Phase 3 — `Home.kt` — UI Redesign

#### 3.1 `HomeTopBar`

| | Before | After |
|---|---|---|
| Composable | `CenterAlignedTopAppBar` | `TopAppBar` |
| Title alignment | Centered | Left-aligned (matches mockup) |
| Logic | Unchanged | Unchanged |

```kotlin
// Change only the composable type:
TopAppBar(   // was: CenterAlignedTopAppBar
    title = {
        Text(
            text = "Featured",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White
        )
    },
    // ... rest unchanged
)
```

---

#### 3.2 `FeaturedCarousel`

**Data source:** `state.mostPlayedPlaylists` (already wired ✅)

Changes:
1. **Next-card peek:** Increase `contentPadding` end to `64.dp` (from 32.dp) so the next card is visible on the right edge.
2. **Floating album art:** Move `AsyncImage` to top-right of card with `offset(y = (-20).dp)` so it partially overflows above the card, matching the mockup.

```kotlin
HorizontalPager(
    state = pagerState,
    contentPadding = PaddingValues(start = 24.dp, end = 64.dp),  // end increased for peek
    pageSpacing = 12.dp,
    modifier = Modifier.fillMaxWidth().height(220.dp)
) { page ->
    // Card layout stays the same
    // Album art change:
    AsyncImage(
        model = song.imageUrl,
        contentDescription = null,
        modifier = Modifier
            .size(100.dp)
            .offset(y = (-20).dp)          // ← floats above card top edge
            .clip(RoundedCornerShape(16.dp))
            .align(Alignment.TopEnd)
            .padding(end = 16.dp),
        contentScale = ContentScale.Crop
    )
}
```

---

#### 3.3 `PlaylistsForYouSection`

**Data source change:** `state.allPlaylists` instead of `state.recentlyPlayedPlaylists`

```kotlin
// In HomeScreen composable, change:
PlaylistsForYouSection(
    playlists = state.allPlaylists.ifEmpty { dummyFallback },  // ← changed
    onPlaylistClick = { playlist -> ... }
)
```

UI changes in each card:
1. Card width: `140.dp` → `120.dp`
2. Add diagonal stripe decoration inside the gradient card:

```kotlin
// Inside the gradient Box:
Box(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { rotationZ = -35f }
        .background(Color.White.copy(alpha = 0.07f))
        .width(20.dp)
        .align(Alignment.Center)
)
```

3. **Placeholder image fallback** (slot ready for when user adds drawables):
```kotlin
// Define at top of file:
private val playlistCoverDrawables = listOf(
    // R.drawable.playlist_cover_1,  ← uncomment as you add files
    // R.drawable.playlist_cover_2,
)

// In card: if imageUrl == null AND placeholders available:
if (playlistCoverDrawables.isNotEmpty()) {
    val res = playlistCoverDrawables[
        Math.abs(playlist.mood.hashCode()) % playlistCoverDrawables.size
    ]
    Image(painter = painterResource(res), ...)
} else {
    // existing gradient + Person icon fallback
}
```

---

#### 3.4 `InTheMixSection`

**No changes required.** Already matches mockup:
- "In the Mix" left + "See All" orange right ✅
- `LazyRow` horizontal cards ✅
- `state.mostPlayedSongs` data source ✅

---

### Phase 4 — `Playlist.kt` — Playlists List Screen

#### 4.1 Remove back arrow (confirmed absent — no action)
The current `PlaylistListScreen` has no back arrow. The mockup's arrow is not applicable to this top-level screen. ✅

#### 4.2 Add 3-dot menu button
In the header `Row` inside the `LazyVerticalGrid` spanning `GridItemSpan(3)`:

```kotlin
// In the Row containing back+play+shuffle, add:
IconButton(
    onClick = { /* Placeholder — no action yet */ },
    modifier = Modifier.size(36.dp)
) {
    Icon(
        Icons.Default.MoreVert,
        contentDescription = "More options",
        tint = Color.White
    )
}
```

#### 4.3 `PlaylistGridCard` — diagonal stripe decoration
Inside the `Box` with the gradient background, add a rotated stripe layer:

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(16.dp))
        .background(gradient)
) {
    // ← ADD: diagonal stripe
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(40.dp)
            .offset(x = 20.dp)
            .graphicsLayer { rotationZ = 25f }
            .background(Color.White.copy(alpha = 0.08f))
    )
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(20.dp)
            .offset(x = 50.dp)
            .graphicsLayer { rotationZ = 25f }
            .background(Color.White.copy(alpha = 0.05f))
    )
    // ← existing avatar overlap code stays
}
```

#### 4.4 Placeholder cover image slot (same as Home cards)
```kotlin
// PlaylistGridCard — card background:
// When playlist has songs with imageUrl, show image instead of gradient
val coverUrl = playlist.songs.firstOrNull()?.imageUrl
if (coverUrl != null) {
    AsyncImage(
        model = coverUrl,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop
    )
} else if (playlistCoverDrawables.isNotEmpty()) {
    val res = playlistCoverDrawables[
        Math.abs(playlist.mood.hashCode()) % playlistCoverDrawables.size
    ]
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop
    )
} else {
    // existing gradient + stripe box
}
```

---

### Phase 5 — `Playlist.kt` — Playlist Songs Screen (`MoodDetailScreen`)

This screen already closely matches the mockup. Only minor verifications:

| Element | Current State | Action |
|---|---|---|
| "Trending Now" label | ✅ Present | None |
| Title (mood name) | ✅ `displaySmall`, ExtraBold | None |
| Subtitle "Caziq Music • N songs" | ✅ Present | None |
| Filter button (Tune icon) | ✅ Present | None |
| Shuffle button | ✅ Present | None |
| Play button (white circle) | ✅ Present | None |
| Back arrow `←` | ✅ Present | None |
| 3-dot (toggles reorder mode) | ✅ Present | None |
| Song album art `52.dp` rounded | ✅ Present | None |
| Explicit `E` badge | ✅ Present | None |
| `artist • mood` subtitle | ✅ Present | None |
| Duration `"3:30"` placeholder | ✅ Present | None |
| 3-dot per song | ✅ Present | None |
| All drag-and-drop reorder logic | ✅ Present | **Preserve as-is** |

**No changes to this screen.** ✅

---

### Phase 6 — Polish & Verification

- [ ] Remove dummy fallback playlists in `FeaturedCarousel` comment — they're fine to keep but confirm they only show when `mostPlayedPlaylists` is truly empty (first launch).
- [ ] Verify `getMoodColor()` palette is consistent across Home and Playlist cards.
- [ ] Ensure all `import` statements are clean after the Home.kt duplicate fix.
- [ ] Confirm build compiles cleanly before UI testing.

---

## 📦 Drawable Placeholder — How to Add Later

1. Add image files to:
   `app/src/main/res/drawable/playlist_cover_1.png`
   `app/src/main/res/drawable/playlist_cover_2.png`
   ... etc.

2. Uncomment entries in the `playlistCoverDrawables` list in `Home.kt` and `Playlist.kt`:
   ```kotlin
   private val playlistCoverDrawables = listOf(
       R.drawable.playlist_cover_1,
       R.drawable.playlist_cover_2,
       R.drawable.playlist_cover_3,
   )
   ```

3. The hash-based selection ensures each playlist always shows the same cover image deterministically.

---

## 🧪 Testing Checklist (Post-Implementation)

- [ ] Home screen loads: Carousel shows most played playlists
- [ ] Home screen loads: Playlists for You shows ALL playlists (not just recent)
- [ ] Home screen loads: In the Mix shows most played songs
- [ ] Tapping a playlist from Home → increments playlist `playCount` in DB
- [ ] Tapping a playlist from Playlists tab → increments playlist `playCount` in DB
- [ ] Playing a song → increments song `playCount` in DB
- [ ] After plays, most played items appear at top of Carousel and In the Mix
- [ ] Playlist grid shows 3-column layout with stripe decoration
- [ ] Top-right 3-dot button renders on Playlists screen (tapping does nothing yet)
- [ ] "Featured" title is left-aligned on Home
- [ ] Carousel next-card peeks from right edge
- [ ] All existing drag-and-drop reorder logic works on Playlist Songs screen
- [ ] Delete playlist dialog still works (long-press)
- [ ] Build compiles with zero errors

---

## 📌 Status

| Phase | Description | Status |
|---|---|---|
| 1 | Fix Home.kt duplicate build bug | ⏳ Pending |
| 2 | HomeViewModel `allPlaylists` wiring | ⏳ Pending |
| 3 | Home Screen UI (TopBar, Carousel, Cards) | ⏳ Pending |
| 4 | Playlists List Screen (3-dot, stripe, placeholder) | ⏳ Pending |
| 5 | Playlist Songs Screen (no changes) | ✅ Already done |
| 6 | Polish & build verification | ⏳ Pending |
