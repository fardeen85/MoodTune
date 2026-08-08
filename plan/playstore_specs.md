# MoodTune — Play Store Specifications

## App Identity
- **App Name:** MoodTune
- **Package:** com.fardeenkhan.moodtune
- **Category:** Music & Audio
- **Content Rating:** Everyone
- **Target Audience:** 16–35, music lovers who listen by emotion/context not genre

## Short Description (max 80 chars)
Describe your mood. AI builds your perfect playlist. Feel the music.

## Long Description

MoodTune turns your feelings into music.

Instead of scrolling through artists or genres, just tell MoodTune how you feel. "Rainy Sunday afternoon with coffee" or "Late-night highway drive" — and our AI instantly builds a personalized playlist that matches your exact emotional moment.

Every song comes with an AI explanation of why it fits your mood, including its energy level and emotional context. It's not just a playlist — it's a musical story built around you.

**What makes MoodTune different:**
- Natural language mood input → instant AI-curated playlist
- Each song has an AI explanation of its emotional fit
- Add your own local music files alongside AI picks
- Tracks your listening history to surface your most-played moods
- Beautiful dark UI with mood-based color themes
- Home screen widget for quick playback control
- Works offline with your local files

**Perfect for:**
- Focus & deep work sessions
- Workout motivation
- Late-night chill listening
- Road trips and commutes
- Emotional processing and relaxation

## Keywords
mood music, AI playlist, music generator, emotion playlist, vibe music, AI DJ, mood-based music, playlist creator, music for moods, Gemini AI music

## Privacy Policy
Required before submission — app sends mood text to Google Gemini API.
Must disclose: mood text sent to Gemini, no account required, data not sold.
Suggested host: GitHub Pages or any static site.

## Store Assets Needed
- [ ] App icon (512x512 PNG, no alpha)
- [ ] Feature graphic (1024x500 PNG)
- [ ] 4–8 phone screenshots (minimum 2 required)
- [ ] Optional: 10-second promo video

## Suggested Screenshots
1. Home screen — Featured carousel + Playlists For You
2. Vibe generator dialog — user typing a mood
3. Playlist songs screen — song list with AI energy badges
4. Now Playing — morphing controls + album art
5. Now Playing — "About this song" AI explanation section
6. Widget on home screen (small + medium sizes)

---

# Play Store Readiness Checklist

## Critical Fixes (Blocking Submission)

| # | Issue | File | Fix |
|---|-------|------|-----|
| 1 | INTERNET permission missing | `app/src/main/AndroidManifest.xml` | Add `<uses-permission android:name="android.permission.INTERNET"/>` |
| 2 | Random explicit badge (`hashCode % 5 == 0`) | `feature/playlist/ui/Playlist.kt` → SongListItem | Remove hash fallback; add `isExplicit: Boolean = false` to `SongEntity` |
| 3 | Hardcoded "3:30" duration on every song | `feature/playlist/ui/Playlist.kt` → SongListItem | Add `durationMs: Long?` to `SongEntity`; parse from API; fallback `"--:--"` |
| 4 | `getMoodColor()` duplicated with different palettes | `Home.kt`, `Playlist.kt` | Move single canonical version to `core/ui/theme/Color.kt` |
| 5 | Shuffle button is no-op | `Playlist.kt`, `MusicPlayerManager.kt` | Wire `toggleShuffle()` via ExoPlayer `shuffleModeEnabled`; tint icon when active |
| 6 | Song 3-dot menu is no-op | `Playlist.kt`, `PlaylistViewModel.kt` | Add `ModalBottomSheet` with Remove from Playlist + Share options |
| 7 | No onboarding / empty state on fresh install | `Home.kt`, `HomeViewModel.kt` | Show "Describe your mood" empty state with CTA that opens vibe generator |
| 8 | Raw exception message shown on Gemini failure | `GeminiAPIDataSource.kt`, `Home.kt` | Typed errors (`NetworkError`, `ParseError`, `RateLimitError`) + retry button |
| 9 | No accessibility content descriptions | `Playlist.kt`, `Home.kt`, `MainActivity.kt` | Add `contentDescription` to all IconButtons, Cards, AsyncImages |

## High Impact Features (v1.0)

| # | Feature | Files | Notes |
|---|---------|-------|-------|
| 10 | Share song | `SongDetail.kt` | `Intent.ACTION_SEND` with title, artist, mood, AI snippet, URL |
| 11 | Filter / Sort songs | `Playlist.kt`, `PlaylistViewModel.kt` | By Energy, Mood, Artist — in-memory sort, not persisted |
| 12 | Playlist rename | `PlaylistEntity`, `PlaylistViewModel` | Add `displayName: String?`; display `displayName ?: mood`; DB migration |
| 13 | Sleep timer | `MusicPlayerManager.kt`, `SongDetail.kt` | Countdown `StateFlow`, moon icon → bottom sheet with 15/30/45/60 min chips |
| 14 | Enhanced AI explanation card | `SongDetail.kt` | Animated `LinearProgressIndicator` for energy; pulsing `AutoAwesome` icon |
| 15 | Gapless / crossfade playback | `MediaPlaybackService.kt` | `setAudioOffloadSchedulingEnabled(true)` on ExoPlayer |
| 16 | Search | `Home.kt`, `HomeViewModel.kt`, DAOs | Material3 `SearchBar`; move Vibe Generator to "+" FAB; 300ms debounce |
| 17 | Offline mode indicator | `MainActivity.kt`, `Home.kt` | `NetworkMonitor` via `ConnectivityManager`; banner + disable AI button |
| 18 | Analytics wiring | `core/analytics/`, ViewModels | Firebase Analytics; key events: playlist_generated, song_played, sleep_timer_set |

## Post v1.0 Roadmap

| Feature | Notes |
|---------|-------|
| Playlist cover customization | `PickVisualMedia`, store URI in `PlaylistEntity.coverImagePath` |
| Mood History screen | Emotional trends chart from existing `lastPlayedAt` + `mood` DB fields |
| Personalized Gemini prompts | Pass top-played songs as context to avoid duplicate suggestions |
| Wear OS polish | Full Now Playing screen using Horologist library |
| Haptic feedback | `LocalHapticFeedback` on morphing controls and drag-to-reorder |
| Tests | Unit: `GeneratePlaylistUseCase`, `HomeViewModel`; Instrumentation: Room DAOs |

---

# Unique Feature Ideas (MoodTune Differentiators)

Features that no mainstream music app has — lean into the emotion + AI angle.

## Tier A — Build These First (Low–Medium Effort)

### 1. Mood Journal
After finishing a playlist, prompt: "How are you feeling now?" Store emoji/text check-ins with timestamp and playlist. Show a "mood journey" timeline. No music app tracks emotional change through listening.
- New table: `MoodCheckInEntity(id, timestamp, playlistId, beforeMood, afterMood, note)`
- New screen: Mood Journal / Timeline

### 2. AI DJ Mode
Between songs, show a 2-second animated overlay with a Gemini-generated transition line:
*"And as the energy softens, here's something more introspective..."*
Makes the app feel curated, not algorithmic.
- Extend `SongExplanation` with `transitionNote: String?`
- Animated overlay in `SongDetail.kt` on `onMediaItemTransition`

### 3. Smart Mood Fusion
Two mood inputs + a blend slider → Gemini creates a fusion playlist.
*"60% focused work, 40% upbeat"*
- Extend Gemini prompt to accept two moods + ratio
- New UI in the vibe generator dialog

### 4. Time-of-Day Mood Suggestions
Greet users with a contextual suggestion based on time + day:
*"Monday 8 AM — here's a focus playlist to start your week"*
Single "Generate this vibe" button. Zero backend, pure logic.
- `HomeViewModel` checks `LocalTime` + `DayOfWeek`
- Banner card above Featured carousel on Home

### 5. Mood Capsule
"Save this moment" button in Now Playing. Captures: current song, playlist, date/time, optional user note. Like a musical diary entry. Shareable as an image card (Compose Canvas).
- New table: `MoodCapsuleEntity`
- New screen: Capsule Gallery

## Tier B — v2.0 (Higher Effort, Maximum Differentiation)

### 6. Spotify / YouTube Music Export
After AI generates a playlist, offer "Export to Spotify" or "Export to YouTube Music."
Bridges MoodTune's AI curation with the user's existing streaming account. Highest practical value for paid streaming subscribers.
- Spotify OAuth + Spotify Web API
- YouTube Data API v3

### 7. Mood Weather Sync
Open-Meteo (free, no API key needed) → rainy day suggests "cozy" mood, sunny weekend suggests "summer energy." Makes the app feel world-aware.
- `WeatherDataSource` using Open-Meteo
- Optional location permission; graceful fallback
- Contextual suggestion banner on Home

### 8. Weekly Mood Wrap
Every Sunday, WorkManager triggers a Gemini summary:
*"This week you were mostly in a reflective headspace with bursts of energy on Wednesday. Here's your week in music."*
Like Spotify Wrapped, but weekly and emotionally intelligent. Shareable as an image card.
- WorkManager Sunday trigger
- Gemini prompt using weekly play history

### 9. Collaborative Mood Room
Generate a shareable link for a playlist. Friends join the "mood room" and can suggest songs. Highest viral potential — group listening around a shared emotion.
- Requires Firebase Realtime Database or Supabase
- v2.0 feature

## Recommended Build Order for Uniqueness

1. Mood Journal — zero competition, high emotional resonance
2. Time-of-Day Suggestions — zero backend, instant delight on every open
3. AI DJ Mode — extends existing Gemini integration, no new screens needed
4. Mood Capsule — shareable content drives organic Play Store discovery
5. Mood Weather Sync (v1.1) — free Open-Meteo API, no backend
6. Spotify Export + Weekly Mood Wrap (v2.0)
