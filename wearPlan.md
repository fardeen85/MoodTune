# Wear OS Implementation Plan: `:wear` Module & Battery-Efficient Connectivity

This document outlines the architecture for the MoodTune Wear OS app, focusing on "Controller Mode" and strict battery efficiency.

## 1. Core Architecture: `:wear` Module
The `:wear` module is a standalone Android application module (`com.android.application`) that shares business logic (domain/data) with the mobile app but has a custom UI.

### Key Components:
- **UI Framework**: Compose for Wear OS + Horologist.
- **Remote Control**: Media3 `MediaController` targeting the phone's `MediaSession`.
- **Sync Engine**: Wearable Data Layer API (`DataClient`, `MessageClient`).

## 2. Battery Efficiency Strategy
Wear OS devices have limited battery capacity. To ensure MoodTune doesn't drain the watch, we will implement the following:

### A. On-Demand Services
- **Lazy MediaController**: The `MediaController` will only be initialized when the "Now Playing" screen is active and released as soon as the user navigates away or the screen turns off.
- **Event-Driven Sync**: Instead of polling the phone for status, the watch will listen for `DataClient` events. The watch remains in a low-power state until the phone pushes a state change.

### B. Efficient Background Handling
- **`WearableListenerService` Optimization**: This service will only be used to handle "Wake Up" messages. All heavy data syncing (like playlist updates) will happen via `DataClient`, which is managed by Google Play Services for optimal battery usage.
- **Minimal Background Work**: No custom background threads or polling will run on the watch.

### C. UI/Display Efficiency
- **Ambient Mode Support**: Implement `AmbientLifecycleObserver` to show a simplified, low-power version of the Now Playing screen when the watch is in "Always-on" mode.
- **OLED Optimization**: Use the "Pure Black" theme from the mobile app to save power on OLED watch displays.

## 3. Connectivity & Services Summary

| Feature | Service/API | Battery-Efficiency Note |
| :--- | :--- | :--- |
| **Playback Control** | `MediaController` | Active only when UI is visible. |
| **Metadata Sync** | `DataClient` | Passive listening; managed by system. |
| **Command Delivery** | `MessageClient` | Instant, low-power "fire-and-forget". |
| **App Wake-up** | `WearableListenerService` | Starts on-demand; doesn't run continuously. |

## 4. Implementation Steps

### Phase 1: Shared Infrastructure
1.  **Shared Package Identity**: Ensure both apps use the same package name and signing key.
2.  **`DataLayerManager`**: Create a shared utility in `core:utils` to handle serialization of `Song` and `PlaybackState` for the `DataClient`.

### Phase 2: The `:wear` UI
1.  **Now Playing**: Circular layout using `Horologist`. Support for "Remote" playback state.
2.  **Mood Grid**: `ScalingLazyColumn` showing cached mood items received via `DataClient`.

### Phase 3: Battery & Ambient Polish
1.  **Ambient Support**: Reduce frame rate and hide progress bars in ambient mode.
2.  **Service Lifecycle**: Rigorous testing of `MediaController.release()` to prevent "service leaks".

## 5. Summary of Controller Mode
The watch app does **not** play audio. It sends commands to the phone's `ExoPlayer`. This preserves watch battery and allows the user to leverage the phone's superior audio hardware or connected Bluetooth speakers.
