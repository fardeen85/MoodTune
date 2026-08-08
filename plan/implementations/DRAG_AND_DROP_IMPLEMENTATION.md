# Drag and Drop List Reordering Implementation

This document explains the implementation of the advanced, real-time drag-and-drop reordering for song lists in the MoodTune app.

## Overview
The implementation uses the latest Jetpack Compose Foundation APIs (1.7.0+) for drag-and-drop, specifically `Modifier.dragAndDropSource` and `Modifier.dragAndDropTarget`.

## Key Components

### 1. Drag Source (`Modifier.dragAndDropSource`)
Applied to the reorder handle of each `SongListItem`.
- **Function**: Initiates a system-level drag operation.
- **Data Transfer**: Wraps the unique `song.id` in a `ClipData` object.
- **State Tracking**: Triggers `PlaylistIntent.StartDrag(song.id)` to inform the ViewModel which item is being moved. This is crucial because the platform API only provides `ClipData` on "Drop," but we need the ID during the "Move" phase for real-time feedback.

### 2. Stable Drag Target (`Modifier.dragAndDropTarget`)
Applied to the `LazyColumn` container.
- **Stability**: The `DragAndDropTarget` object is wrapped in a `remember` block with no keys to ensure it remains stable. We use `rememberUpdatedState` for dependencies like the song list and `onIntent` lambda. This prevents the drag operation from being interrupted when the list reorders.
- **Real-time Reordering (`onMoved`)**:
    - Continuously tracks the pointer's Y-coordinate.
    - Uses `LazyListState.layoutInfo` to find the specific item under the user's finger.
    - Calculates the `targetIndex` and triggers `PlaylistIntent.ReorderSongs` instantly.
- **Cleanup**: Implements `onDrop` and `onEnded` to trigger `PlaylistIntent.EndDrag`, resetting the ViewModel state.

### 3. Auto-Scrolling
To support moving items across long lists:
- The `onMoved` callback checks if the pointer is within the top or bottom 15% of the viewport.
- If so, it triggers `lazyListState.animateScrollBy` in a coroutine to scroll the list in the drag direction.

### 4. Visual Feedback
- **`Modifier.animateItem()`**: Used on list items to provide smooth slide animations as they swap positions.
- **System Drag Shadow**: The platform automatically generates a semi-transparent "shadow" of the dragged handle that follows the finger.

## Why this approach?
Unlike older `pointerInput` methods, this utilizes the native Android Drag-and-Drop system, which is more robust and performant. By moving the target logic to the list level and using `onMoved`, we achieved a "weightless" feel where items slide out of the way immediately as you move the finger, supporting multi-position moves in a single gesture.
