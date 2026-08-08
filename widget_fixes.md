# MoodTuneWidget Fixes

Two separate bugs were reported and fixed. Both are in the widget/playback-service update
pipeline. Emulator testing confirmed fix #1 works; fix #2 was implemented and compiles, but
was not yet re-verified live on-device (emulator was closed mid-session) - test this before
considering it done.

## Bug 1: Widget doesn't update accurately when the song changes

### Root cause

`MediaPlaybackService.kt` had 7 independent triggers that could all fire within the same
event burst (a song change fires `onMediaItemTransition` + `onPlaybackStateChanged` +
`onEvents` almost simultaneously):

- `onIsPlayingChanged`
- `onMediaItemTransition`
- `onPlaybackStateChanged`
- `onEvents` (batched)
- widget button taps (`onStartCommand`)
- the 2s progress ticker

Each one spawned its own independent `serviceScope.launch { ... }` that hopped
`Dispatchers.Default -> Dispatchers.Main` to read the player, then wrote to the widget's
DataStore prefs. With no ordering guarantee between these concurrent coroutines, a **stale**
write could finish *after* a fresher one and silently overwrite it - especially right at a
song change, or right as playback paused (which also stopped the 2s ticker that might
otherwise have self-corrected it).

There was also a secondary tearing bug in `MoodTuneWidget.kt`'s album-art bitmap cache: two
independent `@Volatile` fields (`cachedArtPath` / `cachedArtBitmap`) could be read out of sync
under concurrent widget recompositions, occasionally serving a mismatched (new path, old
bitmap) pair.

### Fix

**`infrastructure/.../MediaPlaybackService.kt`**
- `serviceScope` now runs on `Dispatchers.Main.immediate` instead of `Dispatchers.Default`.
  `updateWidget()` reads the player synchronously - no more per-call dispatcher hop.
- All snapshots now flow through `widgetUpdateFlow: MutableStateFlow<PlayerStats?>`, collected
  via `collectLatest { persistWidgetState(it) }` (subscribed once in `onCreate`).
  `collectLatest` cancels any in-flight widget-state write the instant a newer snapshot
  arrives, so only the *latest* state ever finishes being persisted, regardless of how many
  listener callbacks fire in a burst.
- `persistWidgetState()` offloads the actual DataStore write + `updateAll()` to
  `Dispatchers.Default` so the Main-thread collector isn't blocked, while `collectLatest`
  still cancels it correctly if a fresher snapshot shows up mid-write.
- `updateWidget()` / `clearWidget()` are now plain synchronous functions that just set
  `widgetUpdateFlow.value` - no coroutine per call site.

**`app/.../widget/MoodTuneWidget.kt`**
- Replaced the two separate `@Volatile` cache fields with a single
  `AtomicReference<Pair<String, Bitmap>?>` (`cachedArt`), so the (path, bitmap) pair is always
  read/written atomically together - no more torn reads.

### Verification (done, on emulator-5554)

Rebuilt, installed, and live-tested via adb:
- Confirmed via added (and since removed) diagnostic logs that `updateWidgetState` found the
  correct `glanceId`, wrote prefs, and called `updateAll()` successfully.
- Toggled playback state to empty (`"No song playing"`) and confirmed the widget visually
  updated to match within ~2 seconds of the tap (log timestamps showed persistence completing
  in ~55ms once the service process was warm).

## Bug 2: Widget's own play/pause button appears "stuck"

### Root cause

`MediaActionCallback.onAction()` (the Glance `ActionCallback` behind the widget's play/pause/
next/previous buttons) only ever did:

```kotlin
context.startForegroundService(intent)  // fire-and-forget
```

Glance recomposes the widget **as soon as `onAction()` returns**. Since starting the service
and having it process the command was fully asynchronous, Glance's recompose could (and did,
per live testing) run *before* `MediaPlaybackService` had actually toggled the player and
persisted the new state - so the recompose rendered the stale pre-tap state. The button
appeared to do nothing, or require a second tap, or "flip" inconsistently.

An intermediate attempt fixed this with a `WidgetActionSync` signal object (a
`MutableStateFlow` the ActionCallback awaited after starting the service). This was found to
still be racy: because `onIsPlayingChanged`/`onPlaybackStateChanged`/`onEvents` can all fire
for one tap and each spawn a `persistWidgetState` attempt that `collectLatest` may cancel
mid-flight, the `finally` block's `notifyUpdated()` signal could fire from an **cancelled,
incomplete** write, unblocking the callback before the real write landed. This was visible
live as "the icon changes sometimes and sometimes not." **`WidgetActionSync` was removed.**

### Fix (current, deterministic approach)

Instead of firing an intent and waiting on a side-channel signal, the button now drives the
command **directly and synchronously** within `onAction()`, using the same
`MediaController`-over-`SessionToken` connection pattern `MusicPlayerManager` already uses
elsewhere in the app (a bound connection, which also sidesteps the
`BackgroundServiceStartNotAllowedException` concern the original `startForegroundService()`
call existed for - bound connections were never subject to that restriction, only
`startService()`/`startForegroundService()` calls are).

**New file: `infrastructure/.../WidgetCommandHandler.kt`**
- `WidgetCommandHandler.execute(context, glanceId, command)`:
  1. Builds a `MediaController` via `SessionToken` pointed at `MediaPlaybackService`.
  2. Applies `play_pause` / `next` / `previous` directly on the controller.
  3. Reads the resulting state directly off the same controller (title/artist/art/isPlaying/
     progress) - no dependency on the service's listener pipeline catching up.
  4. Calls the new targeted `MoodTuneWidgetHelper.updateWidgetState(context, widgetClass,
     glanceId, ...)` overload (below) for **this specific `glanceId`** before returning.
  5. Releases the controller future in a `finally` block.

**`infrastructure/.../WidgetContract.kt`**
- Added a second `updateWidgetState(...)` overload that takes an explicit `glanceId: GlanceId`
  instead of re-querying `GlanceAppWidgetManager.getGlanceIds()` and calling `updateAll()`. It
  writes prefs for just that id and calls `GlanceAppWidget.update(context, glanceId)` directly
  - the precise, targeted refresh `onAction()` needs since it already knows exactly which
    widget instance triggered the tap.
- The original broad `updateWidgetState(...)` (no `glanceId` param, loops all instances +
  `updateAll()`) is unchanged and still used by `MediaPlaybackService`'s listener-driven path
  (song changes, progress ticks, and anything not initiated by a widget tap).

**`app/.../widget/MoodTuneWidget.kt`**
- `MediaActionCallback.onAction()` now just calls:
  ```kotlin
  WidgetCommandHandler.execute(context, glanceId, action)
  ```

**`infrastructure/.../MediaPlaybackService.kt`**
- Removed the now-dead `ACTION_WIDGET_COMMAND` intent handling from `onStartCommand()` (the
  widget was its only sender, and it no longer sends it - confirmed via repo-wide grep before
  removing). Leaving both paths in place would have caused a **double toggle** per tap (once
  via the old intent path, once via the new `MediaController` path).

### Status / what to verify next

- All modules compile clean (`:infrastructure:compileDebugKotlin`, `:app:compileDebugKotlin`).
- **Not yet re-verified live** - the emulator was closed before this final version could be
  installed and tested. Before considering this done, on-device testing should confirm:
  1. Rapid repeated taps on play/pause always flip the icon immediately and correctly (no
     lag, no double-toggle, no "stuck" state) - this was the original complaint.
  2. Next/previous still work correctly and update title/artist/art immediately.
  3. A cold tap (service process previously killed, e.g. after `onTaskRemoved`/`onDestroy`)
     still works - `MediaController.Builder(...).buildAsync()` should auto-restart the bound
     service process same as `MusicPlayerManager` already relies on elsewhere.
  4. Bug 1's fix still holds under this new code path (song-change-driven updates via the
     listener pipeline vs. tap-driven updates via `WidgetCommandHandler` don't reintroduce a
     race between each other - they write to the same DataStore-backed prefs, so the last
     write physically wins either way, but both paths compute correct state so this should be
     fine in practice).
