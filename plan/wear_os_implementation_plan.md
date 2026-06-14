# Wear OS Support Implementation Plan

This implementation plan outlines the steps required to introduce Wear OS support into the MoodTune codebase by creating a new standalone `:wear` module, implementing a battery-efficient connectivity and synchronization engine via the Google Play Services Wearable Data Layer API, and developing a premium Wear OS user interface using Compose for Wear OS and Horologist.

## User Review Required

> [!IMPORTANT]
> Both the Mobile and Wear OS application modules MUST share the exact same package identity (`com.fardeenkhan.moodtune`) and must be signed with the same signing key in production to permit access to the Wearable Data Layer API.
>
> **Battery Optimization & Ambient Mode Support:**
> - To ensure high power efficiency on Wear OS devices, the `:wear` app operates in a strictly event-driven **"Controller Mode"**. It does not perform audio decoding or playback itself; instead, it delegates all playback logic to the phone's `ExoPlayer` instance via the `MediaSession`.
> - Wear OS Ambient Mode will be implemented using `AmbientLifecycleObserver` to transition the watch face into a battery-saving, high-contrast, pure-black theme with reduced frame rates and hidden progress bars when the user's wrist is lowered.

## Open Questions

> [!NOTE]
> 1. Should we support local caching of the current playlist song items on the watch to show a simple list of queued songs, or is it sufficient to display only the "Now Playing" controller and a grid of moods to initiate playback? (Proposed: Keep it minimal with "Now Playing" and "Mood Grid" screens to maximize battery life).
> 2. Are there specific signing key configurations we should set up in the gradle files for local development/emulators? (Proposed: Standard debug signing configuration is sufficient for local Wear OS emulator testing).

---

## Proposed Changes

### Component: Dependency Management

We need to add the required Google Play Services Wearable, Wear Compose Material 3, and Horologist dependencies to the version catalog and include them in the respective modules.

#### [MODIFY] [libs.versions.toml](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/gradle/libs.versions.toml)
- Add new versions:
  - `playServicesWearable = "19.0.0"`
  - `wearCompose = "1.6.1"`
  - `horologist = "0.7.15"`
- Add new libraries under `[libraries]`:
  - `play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }`
  - `androidx-wear-compose-material3 = { group = "androidx.wear.compose", name = "compose-material3", version.ref = "wearCompose" }`
  - `androidx-wear-compose-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "wearCompose" }`
  - `androidx-wear-compose-navigation = { group = "androidx.wear.compose", name = "compose-navigation", version.ref = "wearCompose" }`
  - `horologist-compose-layout = { group = "com.google.android.horologist", name = "horologist-compose-layout", version.ref = "horologist" }`
  - `horologist-media-data = { group = "com.google.android.horologist", name = "horologist-media3-data", version.ref = "horologist" }`

#### [MODIFY] [settings.gradle.kts](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/settings.gradle.kts)
- Include the new `:wear` module in the root project:
  ```kotlin
  include(":wear")
  ```

---

### Component: Shared Infrastructure (`:core:utils`)

Create a shared utility class that manages serializing data (such as song information and playback state) to/from byte arrays to easily pass them through the Google Play Services Wearable `DataClient` and `MessageClient`.

#### [NEW] [DataLayerManager.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/core/utils/src/main/java/com/fardeenkhan/moodtune/core/utils/DataLayerManager.kt)
- Create a helper object/class that:
  - Serializes `Song` and `PlaybackState` into JSON (utilizing KotlinX Serialization).
  - Exposes constants for Data Layer paths:
    - `/playback_state` (DataClient path for passive sync).
    - `/commands` (MessageClient path for on-demand playback actions).
    - `/moods` (MessageClient or DataClient path for available mood grids).
  - Handles basic sending of messages and updating of data items, optimizing threads.

---

### Component: Mobile Companion App Setup (`:app`)

The mobile app must listen to the playback state of `MusicPlayerManager` and push it to the wearable data layer. It also needs to listen to messages sent by the wear device and act upon them.

#### [NEW] [PhoneWearableListenerService.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/app/src/main/java/com/fardeenkhan/moodtune/app/wear/PhoneWearableListenerService.kt)
- Subclass `WearableListenerService` to receive messages from the watch.
- Handle incoming commands:
  - Play / Pause / Skip Next / Skip Previous / Seek commands.
  - Playlist initiation command (e.g., `/start_mood_playlist?mood=Happy`).
- Interact directly with `MusicPlayerManager` via DI (Koin) to apply these commands to the playback state.

#### [MODIFY] [AndroidManifest.xml](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/app/src/main/AndroidManifest.xml)
- Register `PhoneWearableListenerService` with the `<service>` tag, specifying the intent filter:
  ```xml
  <service android:name=".wear.PhoneWearableListenerService" android:exported="true">
      <intent-filter>
          <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
          <data android:scheme="wear" android:host="*" android:pathPrefix="/commands" />
      </intent-filter>
  </service>
  ```

#### [MODIFY] [MainActivity.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/app/src/main/java/com/fardeenkhan/moodtune/app/MainActivity.kt)
- In `onCreate` or standard lifecycles, start a Coroutine scope that observes `MusicPlayerManager.playbackState` and invokes `DataLayerManager` to write state changes to the Wearable `DataClient`. This keeps the wear device updated in a battery-efficient, event-driven manner.

---

### Component: The `:wear` Application Module

Create a brand new `:wear` Android application module.

#### [NEW] [build.gradle.kts](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/wear/build.gradle.kts)
- Configure the Android application plugin for Wear OS, targeting SDK 37 (matching mobile project configurations).
- Import `play-services-wearable`, Wear Compose Material 3, Wear Compose Foundation, Wear Compose Navigation, and Horologist dependencies.
- Make sure it uses same namespace package structure: `com.fardeenkhan.moodtune.wear`.

#### [NEW] [AndroidManifest.xml](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/wear/src/main/AndroidManifest.xml)
- Configure the `<manifest>` with the standard Wear OS configurations:
  - `<uses-feature android:name="android.hardware.type.watch" />`
  - Setup `WearMainActivity` as standard launcher activity.
  - Use the exact same shared package identity: `com.fardeenkhan.moodtune`.

#### [NEW] [WearMainActivity.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/wear/src/main/java/com/fardeenkhan/moodtune/wear/WearMainActivity.kt)
- Setup a `ComponentActivity` that sets up edge-to-edge support for circular displays and boots `WearApp`.
- Implement `AmbientLifecycleObserver.AmbientCallback` and initialize it:
  ```kotlin
  private val ambientCallback = object : AmbientLifecycleObserver.AmbientCallback { ... }
  private val ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
  ```

#### [NEW] [WearApp.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/wear/src/main/java/com/fardeenkhan/moodtune/wear/presentation/WearApp.kt)
- Root Composable using Horologist `AppScaffold` and `ScreenScaffold` for circular scrollbars and layout support.
- Implement standard Swipe-to-Dismiss Navigation for circular screens:
  - Route `/mood_grid` (Home Screen)
  - Route `/now_playing` (Controller Screen)
- Standard premium OLED-friendly deep-black background styling.

#### [NEW] [NowPlayingScreen.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/wear/src/main/java/com/fardeenkhan/moodtune/wear/presentation/NowPlayingScreen.kt)
- Circular layout that shows:
  - Song title and artist in center.
  - Control buttons: Previous, Play/Pause Toggle, Next.
  - Progress circular track indicator (hidden in Ambient Mode).
  - Passive updates driven by the phone's `DataClient` updates via standard Compose `collectAsState`.
  - When buttons are pressed, fires on-demand messages to `/commands` via `MessageClient`.

#### [NEW] [MoodGridScreen.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/wear/src/main/java/com/fardeenkhan/moodtune/wear/presentation/MoodGridScreen.kt)
- Grid layout using `ScalingLazyColumn` showing beautiful, rounded mood cards matching brand themes (e.g. "Chill", "Energetic", "Happy", "Focus").
- Clicking a card pushes a command `/commands/start_mood_playlist?mood=x` to the phone's listener service to immediately generate/play the list.

---

## Verification Plan

### Automated Tests
- Since Wear OS connectivity depends on Google Play Services and system broadcasts, we'll write:
  - Local unit tests in `:core:utils` to verify serialization and deserialization of songs and states.
  ```bash
  ./gradlew :core:utils:testDebugUnitTest
  ```
  - Layout preview tests for circular screens:
  ```bash
  ./gradlew :wear:compileDebugKotlin
  ```

### Manual Verification
1. Launch Wear OS emulator alongside mobile emulator in Android Studio.
2. Pair the devices using Wear OS companion pairing tooling.
3. Open MoodTune on the mobile device and play a song.
4. Launch MoodTune on the watch; verify that the active song title and artist are synced immediately.
5. Tap Pause on the watch; verify that the song pauses on the mobile emulator.
6. Navigate to the Mood Grid on the watch, select a mood (e.g., "Energetic"); verify that the phone starts playing an Energetic playlist.
7. Put the watch emulator in Ambient Mode; verify the UI transitions to a pure-black/white simplified layout.
