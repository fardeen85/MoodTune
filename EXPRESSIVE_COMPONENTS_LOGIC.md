# Expressive UI Components Implementation Logic

This document details the implementation of the Material 3 Expressive UI components added to the `NowPlayingScreen`, focusing on shape morphing and circular playback controls.

## 1. Shape Morphing Control (`MorphingControl`)

The `MorphingControl` is a reusable component that transitions between two `RoundedPolygon` shapes based on user interaction.

### Core Logic:
- **Geometry Interpolation**: Uses `androidx.graphics.shapes.Morph` to pre-calculate the transformation path between a `baseShape` (e.g., Clover) and a `morphShape` (e.g., Puffy Diamond).
- **Animation**:
    - `morphProgress`: A `spring` animation (0f to 1f) triggered by the `isPressed` state.
    - `scale`: A simultaneous spring animation that scales the button down slightly (0.85x) when pressed to provide tactile feedback.
- **Rendering**: Uses `drawWithCache` to efficiently draw the interpolated `Path` on the canvas. The `android.graphics.Matrix` is used to scale the normalized (0..1) polygon coordinates to the actual component size.
- **Icon Transitions**: Uses `AnimatedContent` with a combination of `fadeIn` and `scaleIn` to smoothly swap icons (like Play to Pause) instead of an instant switch.

```kotlin
val morph = remember(baseShape, morphShape) { Morph(baseShape, morphShape) }
val morphProgress by animateFloatAsState(
    targetValue = if (isPressed) 1f else 0f,
    animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f)
)
// ... in drawWithCache ...
val matrix = android.graphics.Matrix()
matrix.setScale(size.width, size.height)
val androidPath = morph.toPath(morphProgress)
androidPath.transform(matrix)
drawPath(path = androidPath.asComposePath(), color = containerColor)
```

## 2. Circular Wavy Progress Indicator

The playback progress is visualized using the native Material 3 Expressive `CircularWavyProgressIndicator`.

### Integration:
- **Placement**: Centered around the main Play/Pause button inside a `Box`.
- **Customization**: Uses `amplitude` and `wavelength` parameters to create an energetic, modern "waviness" that moves as the song plays.
- **State Binding**: The `progress` lambda is tied to the song's duration and current playback position.

## 3. Circular Seeking Logic

To make the circular progress bar interactive, we implemented custom touch handling using trigonometry.

### Trigonometric Calculation:
To determine where the user touched the circle and convert that to a playback percentage:
1. **Center Calculation**: Find the center of the control box.
2. **Angle Detection**: Use `atan2(deltaY, deltaX)` to get the angle of the touch point relative to the center.
3. **Normalization**:
    - `atan2` returns values in radians from -PI to PI.
    - We convert this to degrees (0..360).
    - We rotate the coordinate system by +90 degrees because standard math starts at 3 o'clock, but playback progress starts at 12 o'clock (the top).
4. **Clamping**: Ensure the resulting progress is between 0f and 1f.

```kotlin
private fun calculateProgressFromOffset(offset: Offset, size: IntSize): Float {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val angle = atan2(offset.y - centerY, offset.x - centerX)
    
    // Convert radians to degrees and adjust for "12 o'clock" start
    var normalizedAngle = (angle * 180 / PI).toFloat() + 90f
    if (normalizedAngle < 0) normalizedAngle += 360f
    
    return (normalizedAngle / 360f).coerceIn(0f, 1f)
}
```

## 4. Optimizing Interactive Seeking

To ensure a smooth "scrubbing" experience without UI flickering or stuttering, we implemented two key optimizations:

### Local State Management (Optimistic UI):
- **Problem**: Rapidly updating the player position during a drag causes a "flick" effect where the UI jumps between the user's finger and the player's last known position due to network/engine latency.
- **Solution**: Introduced `isDragging` and `dragProgress` local states. While dragging, the UI ignores the `state.progress` from the ViewModel and exclusively uses the local `dragProgress` for rendering the wavy bar and time display.

### Loader Suppression:
- **Problem**: Frequent seeking triggers short "buffering" states in the media player. Tied directly to the UI, this causes a loading overlay to rapidly flicker on/off, interrupting the user experience.
- **Solution**: The loading overlay is now conditionally rendered: `if (state.isLoading && !isDragging)`. This suppresses the loader while the user is actively interacting with the progress bar.

```kotlin
// Example of optimistic progress rendering
val displayProgress = if (isDragging) dragProgress else currentProgress

CircularWavyProgressIndicator(
    progress = { displayProgress },
    // ...
)
```
