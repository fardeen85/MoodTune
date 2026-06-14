# Material 3 Expressive Upgrade Plan

This plan outlines the steps needed to upgrade the MoodTune project to leverage the new **Material 3 Expressive** design system. This system introduces emotionally resonant, motion-rich, and physics-driven user experiences through the latest standard Compose Material 3 features.

## User Review Required

> [!IMPORTANT]
> **Experimental API Opt-ins:**
> Several Material 3 Expressive features are currently marked as experimental APIs (e.g., `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`). We will isolate these opt-ins to `core:ui`'s custom components or specific feature views to keep the codebase clean.
>
> **Motion Schemes and Performance:**
> Physics-based spring animations will replace standard linear/cubic animations. To maintain buttery-smooth performance, we must verify that all rendering is performant and layout passes are minimized on low-end test devices.

## Open Questions

> [!NOTE]
> 1. Should we completely replace standard progress indicators with the new Expressive `LoadingIndicator` globally? (Proposed: Yes, doing so ensures visual consistency and gives a premium, active feel).
> 2. Should we implement custom font weight axes for Plus Jakarta Sans to fully support M3 Expressive dynamic typography? (Proposed: Yes, we will configure this in `Type.kt`).

---

## Proposed Changes

### Component: Version Upgrade

We will update the `androidx-compose-material3` version in the version catalog to `1.4.0` or higher to unlock the Expressive APIs, while maintaining full compatibility with other Compose libraries.

#### [MODIFY] [libs.versions.toml](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/gradle/libs.versions.toml)
- We will explicitly declare the material3 version or upgrade the Compose BOM:
  ```toml
  [versions]
  # Update Compose BOM to 2026.05.00 (which brings Material 3 1.4.x stable or newer)
  composeBom = "2026.05.00"
  ```
  *Alternatively, if overriding material3 specifically:*
  ```toml
  androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version = "1.4.0" }
  ```

---

### Component: Expressive Theming & Motion System (`:core:ui`)

Upgrade the theme configuration to apply M3 Expressive motion physics, typography configurations, and enhanced shapes.

#### [MODIFY] [Theme.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/core/ui/src/main/java/com/fardeenkhan/moodtune/core/ui/theme/Theme.kt)
- Update `MoodTuneTheme` to use the new `MaterialExpressiveTheme` instead of `MaterialTheme` (or apply the custom `MotionScheme` inside `MaterialTheme`).
- Configure a custom expressive motion scheme:
  ```kotlin
  import androidx.compose.material3.MaterialExpressiveTheme
  import androidx.compose.material3.motionScheme

  // We can utilize Standard or Expressive motion schemes (physics-based springs)
  val expressiveMotionScheme = motionScheme {
      // Configure custom spring physics or use defaults
  }
  ```

#### [MODIFY] [Type.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/core/ui/src/main/java/com/fardeenkhan/moodtune/core/ui/theme/Type.kt)
- Update standard styles to utilize M3 Expressive typographic scales (dynamic, high-contrast, variable font weights).

---

### Component: Feature UI Enhancements

Introduce new Expressive components into existing features to enhance user interaction and emotional feedback.

#### [MODIFY] [Home.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/feature/home/src/main/java/com/fardeenkhan/moodtune/feature/home/ui/Home.kt)
- **Replace Loading Indicator:** Switch standard circular progress bar to the new `LoadingIndicator` with expressive spring motion to represent background playlist generation.
- **Split Button for Generation:** Use the new `SplitButton` for the "Generate by AI" action on the mood dialog—the primary portion triggers rapid AI generation, while the dropdown portion allows selecting generation filters (e.g. length, tempo).

#### [MODIFY] [Playlist.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/feature/playlist/src/main/java/com/fardeenkhan/moodtune/feature/playlist/ui/Playlist.kt)
- **Button Group:** Use the new `ButtonGroup` to arrange the playlist filters at the top of the screen (e.g. "Recently Added", "Energy", "Tempo"), utilizing spring transitions on selection state change.

#### [MODIFY] [NowPlaying.kt](file:///Users/fardeenkhan/AndroidStudioProjects/MoodTune/feature/songdetail/src/main/java/com/fardeenkhan/moodtune/feature/songdetail/ui/NowPlaying.kt)
- **Interactive Micro-animations:** Apply physics-based scaling on buttons (Previous, Play/Pause, Next) when pressed to provide a highly premium tactile response.
- **Loading/Buffer States:** Integrate the expressive `LoadingIndicator` for player buffering state overlay.

---

## Verification Plan

### Automated Tests
- Build verification to ensure all experimental APIs are resolved correctly:
  ```bash
  ./gradlew :app:assembleDebug
  ```

### Manual Verification
1. Launch the mobile app and navigate to the Home screen.
2. Trigger the "Generate by AI" action; observe the new `LoadingIndicator` animation. Verify it feels fluid and responsive.
3. Check the "Generate by AI" dialog and inspect the `SplitButton` formatting.
4. Interact with the `ButtonGroup` filters on the Playlist screen; verify that selection state changes trigger smooth spring-based transitions.
5. In the Now Playing screen, verify the play/pause tactile micro-animations.
