# Implementation Plan: Jetpack Glance Music Player Widget with Active Playlist Queue for MoodTune

This document outlines the architecture, design, and detailed step-by-step plan to implement a highly aesthetic, responsive Home Screen Widget using **Jetpack Glance** (leveraging Compose-like syntax for widgets) that interfaces directly with the application's existing Media3 `MediaPlaybackService`.

---

## 1. Design & UX Concept

Drawing inspiration from **YouTube Music** and modern Android 14/15 systems, the widget will support multiple sizes using responsive Glance layouts and premium Material 3 aesthetics.

### Visual Key Features
- **Dynamic Playback & Interactive Queue:** Features a scrollable list (`LazyColumn`) of the current playlist songs directly inside the widget (visible in medium/larger sizes).
- **Interactive Queue Selection:** Users can tap any song in the playlist queue to instantly skip directly to and play that song.
- **Dynamic Theming / Colors:** Uses the vibrant or dark palette from the active album cover (or fallback system palette).
- **Glassmorphic Surface:** A translucent background with rounded corners (`16dp` - `28dp`) and premium subtle borders.
- **Premium Controls:** Rounded control buttons, smooth progress indicator status, and a high-resolution album cover frame.

---

## 2. Architecture & Data Flow

To ensure the widget updates in real time with minimum latency and resources:

```mermaid
graph TD
    Service[MediaPlaybackService] -->|Playback State & Queue Changes| Listener[Media3 Player.Listener]
    Listener -->|Update State (Song Metadata + Serialized Queue JSON)| WidgetState[GlanceStateDefinition / Preferences]
    WidgetState -->|Trigger Update| Receiver[MoodTuneWidgetReceiver]
    Receiver -->|Recompose UI with LazyColumn| Widget[MoodTuneWidget]
    Widget -->|User Control / Queue Tap Actions| ActionCallback[Glance ActionCallbacks]
    ActionCallback -->|Send Play/Pause/Skip/Select Playlist Index Intents| Service
```

---

## 3. Step-by-Step Implementation Steps

### Step 1: Add Dependencies
Add Jetpack Glance & Serialization libraries to `gradle/libs.versions.toml` and configure the module gradle files.

#### `gradle/libs.versions.toml`
```toml
[versions]
glance = "1.1.1"

[libraries]
androidx-glance = { group = "androidx.glance", name = "glance", version.ref = "glance" }
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
```

In the `:app` module `build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.kotlinx.serialization.json)
}
```

---

### Step 2: Create Widget Provider Metadata Configuration
Add widget specifications such as sizing, update frequency, and preview properties. We increase default sizing to accommodate the scrollable playlist layout.

Create `app/src/main/res/xml/moodtune_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/xml"
    android:initialWidth="250dp"
    android:initialHeight="180dp"
    android:minWidth="220dp"
    android:minHeight="110dp"
    android:minResizeWidth="220dp"
    android:minResizeHeight="110dp"
    android:updatePeriodMillis="0"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:maxResizeWidth="450dp"
    android:maxResizeHeight="400dp" />
```

---

### Step 3: Implement the Glance Widget State & JSON Queue Serialization
We utilize standard `Preferences` key-value pairs inside Glance's default state management to exchange state. For the current playlist, we serialize the list of songs to a lightweight JSON string.

#### State Keys:
- `IS_PLAYING`: Boolean
- `TRACK_TITLE`: String
- `TRACK_ARTIST`: String
- `TRACK_IMAGE_URL`: String
- `HAS_NEXT`: Boolean
- `HAS_PREV`: Boolean
- `PLAYLIST_QUEUE_JSON`: String (JSON array representing list of `{ "id": "...", "title": "...", "artist": "..." }`)
- `CURRENT_TRACK_INDEX`: Int

---

### Step 4: Build the Glance App Widget UI with `LazyColumn` (`MoodTuneWidget.kt`)
Using Glance composables (`Box`, `Row`, `Column`, `LazyColumn`, `Text`, `Button`) we build the responsive UI matching YouTube Music’s active queue layout.

#### Key Code Snippet (Outline):
```kotlin
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

class MoodTuneWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val isPlaying = prefs[PreferencesKeys.isPlaying] ?: false
            val title = prefs[PreferencesKeys.title] ?: "No Music Playing"
            val artist = prefs[PreferencesKeys.artist] ?: "MoodTune"
            val queueJson = prefs[PreferencesKeys.playlistQueueJson] ?: "[]"
            
            val playlistSongs = remember(queueJson) { parseQueueJson(queueJson) }

            // Render highly adaptive, modern layouts depending on widget size
            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                // Now Playing Row Controller
                NowPlayingRow(title, artist, isPlaying)
                
                Spacer(modifier = GlanceModifier.height(8.dp))
                
                // Active Playlist Scrollable Queue (Shown in medium/large widget sizes)
                Text(text = "Up Next", style = TextStyle(fontWeight = FontWeight.Bold))
                LazyColumn(modifier = GlanceModifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(playlistSongs) { index, song ->
                        PlaylistItemRow(
                            index = index,
                            title = song.title,
                            artist = song.artist,
                            isCurrent = (song.title == title)
                        )
                    }
                }
            }
        }
    }
}
```

---

### Step 5: Implement Interactive Control Callback Actions
Handle user interactions (Play/Pause, Next, Previous, and Selection of playlist items) which send explicit commands directly to the active `MediaPlaybackService`.

```kotlin
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class MusicControlCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[ActionKeys.command] // "play_pause", "next", "prev", "play_index"
        val selectIndex = parameters[ActionKeys.selectIndex] ?: -1
        
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = command
            if (selectIndex != -1) {
                putExtra("PLAYLIST_INDEX", selectIndex)
            }
        }
        context.startService(intent)
    }
}
```

---

### Step 6: Connect `MediaPlaybackService` with the Widget Updates
Modify `MediaPlaybackService` to serialize the current playing queue and update the Glance widget state whenever the media metadata, play/pause state, or active queue playlist changes.

```kotlin
private fun updateWidgetState(song: Song?, queue: List<Song>, isPlaying: Boolean) {
    scope.launch {
        val glanceManager = GlanceAppWidgetManager(context)
        val glanceIds = glanceManager.getGlanceIds(MoodTuneWidget::class.java)
        val queueJson = serializeQueue(queue)
        
        glanceIds.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    set(PreferencesKeys.isPlaying, isPlaying)
                    set(PreferencesKeys.title, song?.title ?: "")
                    set(PreferencesKeys.artist, song?.artist ?: "")
                    set(PreferencesKeys.imageUrl, song?.imageUrl ?: "")
                    set(PreferencesKeys.playlistQueueJson, queueJson)
                }
            }
            MoodTuneWidget().update(context, id)
        }
    }
}
```

---

### Step 7: Declare in the AndroidManifest
Register the widget receiver inside the application manifest:

```xml
<receiver android:name=".widget.MoodTuneWidgetReceiver"
    android:exported="true"
    android:label="MoodTune Player Widget">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/moodtune_widget_info" />
</receiver>
```

---

## 4. Risks & Mitigations
- **Widget Sizing & Layout Constraints:** Widgets have limited height and list loading performance bounds. **Mitigation:** Use highly responsive Glance sizing benchmarks, limit the list serialization to the top 20-30 upcoming tracks to save RAM/storage inside AppWidget `Preferences`, and provide clean fallback state states.
- **State Size Limit:** Android AppWidgets state data inside `Preferences` has a strict storage limit. **Mitigation:** Do not serialize heavy bitmaps or complete song description payloads; only serialize lightweight fields (`id`, `title`, `artist`) in the JSON array.
