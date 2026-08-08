# Generate Lyrics Feature — Implementation Plan

## Overview

Add AI-generated, playback-synchronized lyrics to the Now Playing screen. When lyrics are active, the large album art fades away and the center zone transforms into a scrollable lyrics panel — current line highlighted and auto-scrolled. A compact thumbnail of the album art and song info row replaces the full-size art at the top. The SongDetail screen also gets a modern visual upgrade.

---

## Visual Design

### Default Mode (no lyrics)
```
[↓]  NOW PLAYING  [♫]
                        ← ♫ icon top-right opens lyrics

[     Large Album Art 350dp     ]

Song Title (bold, large)
Artist (muted)

~~~ Wavy Seekbar ~~~
  00:34                3:45

   [◁◁]   [▶ / ‖]   [▷▷]

 [ About this song ↓ ]
 [ Play on YouTube   ]
```

### Lyrics Mode
```
[↓]  NOW PLAYING  [✕]
                        ← ✕ icon closes lyrics

[●56dp art]  Song Title
             Artist

┌─────────────── lyrics panel ────────────────┐
│ ░░░░░░░░░░░░░░░░░░░  ← top fade gradient    │
│                                              │
│  dim faded line                              │
│  dim faded line                              │
│  ★ ACTIVE LINE — bold · white · scale 1.05  │
│  dim faded line                              │
│  dim faded line                              │
│                                              │
│ ░░░░░░░░░░░░░░░░░░░  ← bottom fade gradient │
└──────────────────────────────────────────────┘

~~~ Wavy Seekbar ~~~
  00:34                3:45

   [◁◁]   [▶ / ‖]   [▷▷]

 [ About this song ↓ ]
 [ Play on YouTube   ]
```

---

## Architecture Flow

```
SongDetail.kt  ──intent──►  SongDetailViewModel
                                  │
                          SongsRepository (interface)
                                  │
                    ┌─────────────┴──────────────┐
                    ▼                             ▼
          GeminiAPIDataSource             SongDao
          getLyrics()  [new]       getLyricsForSong()  [new]
                                   updateLyrics()      [new]
                                          │
                                   SongEntity.lyrics   [new column]
                                   DB version 3 → 4
```

---

## File-by-File Changes

### 1. `domain/build.gradle.kts`
Add the Kotlin serialization compiler plugin so `@Serializable` generates serializers:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)   // ADD THIS
}
```

---

### 2. `domain/src/main/java/.../domain/model/domainModel.kt`
New data class at the bottom of the file:
```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(val ms: Long, val line: String)
```

---

### 3. `infrastructure/src/main/java/.../infrastructure/remote/Dto.kt`
New DTO at the bottom:
```kotlin
@Serializable
data class LyricLineDto(val ms: Long, val line: String)
```

---

### 4. `infrastructure/src/main/java/.../datasource/GeminiAPIDataSource.kt`
New method — reuses existing `client`, `apiKey`, `baseUrl`, JSON fence-stripping, and `GeminiRequest`/`GeminiResponse` DTOs:
```kotlin
suspend fun getLyrics(title: String, artist: String, durationMs: Long): List<LyricLineDto> {
    val prompt = """
        Generate timestamped lyrics for "$title" by "$artist" (duration: ${durationMs}ms).
        Return ONLY a JSON array, no markdown. Each element: {"ms": <milliseconds>, "line": "<lyric line>"}.
        Space lines proportionally across the full duration. Start with {"ms":0,"line":"♪ Intro ♪"}.
    """.trimIndent()

    // Same HTTP call pattern as getSongRecommendations:
    // POST to baseUrl?key=apiKey with GeminiRequest(contents=[Content(parts=[Part(prompt)])])
    // Parse response → strip ```json fences → Json.decodeFromString<List<LyricLineDto>>(text)
}
```

---

### 5. `core/database/src/main/java/.../entity/SongEntity.kt`
Add nullable field after `durationMs`:
```kotlin
val lyrics: String? = null  // stored as JSON blob: "[{\"ms\":0,\"line\":\"...\"}]"
```

---

### 6. `core/database/src/main/java/.../dao/SongDao.kt`
Two targeted queries (avoid touching other columns):
```kotlin
@Query("SELECT lyrics FROM songs WHERE id = :id")
suspend fun getLyricsForSong(id: String): String?

@Query("UPDATE songs SET lyrics = :lyricsJson WHERE id = :id")
suspend fun updateLyrics(id: String, lyricsJson: String)
```

---

### 7. `core/database/src/main/java/.../MoodTuneDatabase.kt`
Bump version and add migration (same pattern as `MIGRATION_2_3`):
```kotlin
@Database(..., version = 4, ...)

companion object {
    val MIGRATION_2_3 = ...  // existing

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN lyrics TEXT DEFAULT NULL")
        }
    }
}
```

---

### 8. `core/database/src/main/java/.../di/DatabaseModule.kt`
Register the new migration:
```kotlin
.addMigrations(MoodTuneDatabase.MIGRATION_2_3, MoodTuneDatabase.MIGRATION_3_4)
```

---

### 9. `core/database/src/main/java/.../mapper/SongMapper.kt`
In `Song.toEntity()` set `lyrics = null` (lyrics are managed via `updateLyrics`, not through general song saves).

In `data/.../repo/songsRepositoryImpl.kt` → `saveSongs()`, do a read-before-write to prevent `OnConflictStrategy.REPLACE` from wiping cached lyrics when a playlist is re-saved:
```kotlin
override suspend fun saveSongs(songs: List<Song>) {
    val entities = songs.map { song ->
        song.toEntity().copy(lyrics = songDao.getLyricsForSong(song.id))
    }
    songDao.insertSongs(entities)
}
```

---

### 10. `domain/src/main/java/.../repo/SongsRepository.kt`
Add two methods to the interface:
```kotlin
suspend fun generateLyrics(songId: String, title: String, artist: String, durationMs: Long): List<LyricLine>
suspend fun getCachedLyrics(songId: String): List<LyricLine>?
```

---

### 11. `data/src/main/java/.../repo/songsRepositoryImpl.kt`
Implement the two new methods:
```kotlin
override suspend fun getCachedLyrics(songId: String): List<LyricLine>? {
    val json = songDao.getLyricsForSong(songId) ?: return null
    return Json.decodeFromString<List<LyricLine>>(json)
}

override suspend fun generateLyrics(
    songId: String, title: String, artist: String, durationMs: Long
): List<LyricLine> {
    val lines = geminiAPIDataSource.getLyrics(title, artist, durationMs)
        .map { LyricLine(it.ms, it.line) }
    songDao.updateLyrics(songId, Json.encodeToString(lines))
    return lines
}
```

---

### 12. `feature/songdetail/src/main/java/.../SongDetailViewModel.kt`

**Add `SongsRepository` as 3rd constructor parameter:**
```kotlin
class SongDetailViewModel(
    private val context: Context,
    private val musicPlayerManager: MusicPlayerManager,
    private val songsRepository: SongsRepository   // NEW
) : ViewModel()
```

**New sealed class `LyricsState`** (add after `SongDetailIntent`):
```kotlin
sealed class LyricsState {
    object Idle : LyricsState()
    object Loading : LyricsState()
    data class Loaded(val lines: List<LyricLine>) : LyricsState()
    data class Error(val message: String) : LyricsState()
}
```

**New intents** (add to `SongDetailIntent`):
```kotlin
object RequestLyrics : SongDetailIntent()   // tap ♫ icon
object RetryLyrics : SongDetailIntent()
object DismissLyrics : SongDetailIntent()
```

**New StateFlow + generate function:**
```kotlin
private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

private fun generateLyrics() {
    val song = state.value.currentSong ?: return
    val duration = state.value.duration.takeIf { it > 0 }
        ?: song.durationMs?.takeIf { it > 0 }
        ?: 240_000L  // 4-minute fallback
    viewModelScope.launch {
        _lyricsState.value = LyricsState.Loading
        runCatching {
            val cached = songsRepository.getCachedLyrics(song.id)
            cached ?: songsRepository.generateLyrics(song.id, song.title, song.artist, duration)
        }.onSuccess { lines ->
            _lyricsState.value = LyricsState.Loaded(lines)
        }.onFailure {
            _lyricsState.value = LyricsState.Error("Could not generate lyrics. Tap to retry.")
        }
    }
}
```

**In `init` block** — reset lyrics when song changes (extend existing `state.onEach` block):
```kotlin
if (url != lastImageUrl) {
    lastImageUrl = url
    _lyricsState.value = LyricsState.Idle  // ADD: reset on song change
    ...
}
```

**Route new intents in `onIntent()`:**
```kotlin
is SongDetailIntent.RequestLyrics -> generateLyrics()
is SongDetailIntent.RetryLyrics -> generateLyrics()
is SongDetailIntent.DismissLyrics -> _lyricsState.value = LyricsState.Idle
```

---

### 13. `app/src/main/java/.../di/AppModules.kt`
Update Koin binding for `SongDetailViewModel` (currently line 47):
```kotlin
viewModel { SongDetailViewModel(androidContext(), get(), get()) }
```

---

### 14. `feature/songdetail/src/main/java/.../SongDetail.kt`

This is the largest change. Key new composables and modifications:

#### New: `SyncedLyricsPanel`
Fills the same 350dp center zone as the album art. Uses `LazyColumn` + `animateScrollToItem`. Gradient masks fade top/bottom edges:

```kotlin
@Composable
fun SyncedLyricsPanel(lines: List<LyricLine>, progressMs: Long, modifier: Modifier = Modifier) {
    val activeIndex by remember(progressMs, lines) {
        derivedStateOf { lines.indexOfLast { it.ms <= progressMs }.coerceAtLeast(0) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem(activeIndex, scrollOffset = -300)
    }

    Box(modifier = modifier.clip(RoundedCornerShape(24.dp))) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines) { index, lyric ->
                val isActive = index == activeIndex
                val scale by animateFloatAsState(if (isActive) 1.05f else 1f, spring(stiffness = 400f))
                Text(
                    text = lyric.line,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .padding(vertical = 10.dp, horizontal = 16.dp)
                )
            }
        }
        // Top fade
        Box(Modifier.fillMaxWidth().height(80.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(0.9f), Color.Transparent))))
        // Bottom fade
        Box(Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)))))
    }
}
```

#### Modified: `NowPlayingScreen`

**Top row** — swap Share icon with a Lyrics/Close toggle:
```kotlin
val showLyricsMode = lyricsState is LyricsState.Loaded || lyricsState is LyricsState.Loading
val lyricsState by viewModel.lyricsState.collectAsState()

// Replace Share icon button with:
IconButton(onClick = {
    if (showLyricsMode) viewModel.onIntent(SongDetailIntent.DismissLyrics)
    else viewModel.onIntent(SongDetailIntent.RequestLyrics)
}) {
    Icon(
        imageVector = if (showLyricsMode) Icons.Default.Close else Icons.Default.Lyrics,
        contentDescription = if (showLyricsMode) "Close Lyrics" else "Generate Lyrics",
        tint = Color.White
    )
}
```

**Compact song header** — shown only in lyrics mode (fades in below the top row):
```kotlin
AnimatedVisibility(
    visible = showLyricsMode,
    enter = fadeIn() + slideInVertically(),
    exit = fadeOut() + slideOutVertically()
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        AsyncImage(
            model = currentSong?.imageUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(currentSong?.title ?: "", fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(currentSong?.artist ?: "", color = Color.White.copy(0.6f))
        }
    }
}
```

**Center zone** — `AnimatedContent` crossfades between album art and lyrics panel:
```kotlin
AnimatedContent(
    targetState = lyricsState,
    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
    modifier = Modifier.fillMaxWidth().height(350.dp)
) { lyricState ->
    when (lyricState) {
        is LyricsState.Loaded ->
            SyncedLyricsPanel(lyricState.lines, state.progress, Modifier.fillMaxSize())

        is LyricsState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Generating lyrics…", color = Color.White.copy(0.7f))
                }
            }

        is LyricsState.Error ->
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(SurfaceLevel1),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(lyricState.message, color = Color.White.copy(0.7f), textAlign = TextAlign.Center)
                    TextButton(onClick = { viewModel.onIntent(SongDetailIntent.RetryLyrics) }) {
                        Text("Retry", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

        LyricsState.Idle -> // Existing album art block (unchanged)
    }
}
```

**Full-size song info row** — hidden when lyrics panel is active:
```kotlin
AnimatedVisibility(visible = !showLyricsMode, enter = fadeIn(), exit = fadeOut()) {
    Row(...) { /* existing title + artist row — unchanged */ }
}
```

**Modern background enhancement** — add a radial gradient accent on top of existing blurred background layers:
```kotlin
// After existing blur + dimming Box layers:
Box(
    modifier = Modifier.fillMaxSize().background(
        Brush.radialGradient(
            colors = listOf(
                (albumArtColor ?: Color.Transparent).copy(alpha = 0.3f),
                Color.Transparent
            ),
            radius = 800f
        )
    )
)
```

---

## Execution Order

| # | File | Change |
|---|------|--------|
| 1 | `domain/build.gradle.kts` | Add serialization plugin |
| 2 | `domain/.../domainModel.kt` | Add `LyricLine` |
| 3 | `infrastructure/.../Dto.kt` | Add `LyricLineDto` |
| 4 | `infrastructure/.../GeminiAPIDataSource.kt` | Add `getLyrics()` |
| 5 | `core/database/.../entity/SongEntity.kt` | Add `lyrics` column |
| 6 | `core/database/.../dao/SongDao.kt` | Two new queries |
| 7 | `core/database/.../MoodTuneDatabase.kt` | v4 + `MIGRATION_3_4` |
| 8 | `core/database/.../di/DatabaseModule.kt` | Register migration |
| 9 | `core/database/.../mapper/SongMapper.kt` + `songsRepositoryImpl.saveSongs` | Preserve lyrics on re-save |
| 10 | `domain/.../repo/SongsRepository.kt` | Two new interface methods |
| 11 | `data/.../repo/songsRepositoryImpl.kt` | Implement both methods |
| 12 | `feature/songdetail/.../SongDetailViewModel.kt` | `LyricsState`, intents, state flow, `generateLyrics()` |
| 13 | `app/.../di/AppModules.kt` | Update Koin binding |
| 14 | `feature/songdetail/.../SongDetail.kt` | Full UI overhaul |

---

## Verification Steps

1. **Build** — ensure no compile errors across all modules
2. **Play local song** → tap ♫ icon top-right → loading spinner in center zone
3. After ~5-10s: album art crossfades out, compact header fades in, lyrics panel appears
4. As song plays: lyrics auto-scroll, active line is white+bold+scaled, others are dimmed
5. Tap ✕ → lyrics fade out, full album art crossfades back in
6. Skip to next song → lyrics automatically reset to Idle
7. Return to same song → tap ♫ → lyrics appear instantly from DB cache (no Gemini call)
8. Test song with no `durationMs` stored → 4-minute fallback used, lyrics still generate
