# Building a Media Player Home Screen Widget with Jetpack Glance

A step-by-step approach for wiring a Glance `AppWidget` to a live `Media3`
`MediaSession`, based on what actually worked (and what silently didn't) while
building MoodTune's widget. `plan/implementations/GLANCE_WIDGET_PLAN.md` is the
original design doc for this widget — it describes the architecture that caused
the bugs documented here. This file is the corrected version.

## The three pitfalls that will break a naive implementation

Read this section before writing any code — all three are silent failures
(no crash, no exception in the common case) that look like "the widget just
doesn't update," and none of them are mentioned in the official Glance docs.

### 1. `update()` / `updateAll()` do NOT re-run `provideGlance()` once a session is live

This is the big one. The obvious-looking pattern —

```kotlin
override suspend fun provideGlance(context: Context, id: GlanceId) {
    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
    val title = prefs[TitleKey] ?: ""
    provideContent { Text(title) }   // title is a plain val, captured once
}

// elsewhere, on every playback event:
updateAppWidgetState(context, id) { it[TitleKey] = newTitle }
MyWidget().updateAll(context)
```

— only works **the first time**. Confirmed directly against the Glance team
(kotlinlang Slack `#glance` channel): `update()`/`updateAll()` do not restart
`provideGlance()` while a session for that widget is already running. Once
`provideContent { }` has been called, that composition stays alive and keeps
recomposing on its own for roughly 45 seconds (extendable by further
interaction) — but only in reaction to state it's *actively observing*, not
because something outside called `updateAll()` again. A `val` captured from a
one-shot `prefs` read has nothing for Compose to invalidate, so nothing
happens.

**Fix:** treat `provideGlance()`'s prefs read as the **cold-start value
only** (used the moment a session doesn't exist yet: freshly placed widget,
process death, or a session that already timed out). For the live case,
expose your real state as a `StateFlow` from wherever your `MediaController`
lives, and read it *inside* `provideContent { }` with `collectAsState()`:

```kotlin
override suspend fun provideGlance(context: Context, id: GlanceId) {
    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
    val initial = PlayerSnapshot(title = prefs[TitleKey] ?: "", /* ... */)

    provideContent {
        val state by WidgetMediaConnection.uiState.collectAsState(initial = initial)
        Text(state.title)   // now genuinely reactive
    }
}
```

Now the *already-running* composition recomposes itself whenever
`WidgetMediaConnection.uiState.value` changes — no dependency on
`updateAll()` succeeding or being called again at all. Keep persisting to
DataStore + calling `updateAll()` on state changes too, but only as a
best-effort fallback for the cold-start case (a session that's timed out or
a fresh process) — it is not what drives a currently visible widget anymore.

### 2. `MediaController` calls from the wrong thread throw silently (from Glance's perspective)

`MediaController` requires every call to happen on the thread it was built
on. Glance invokes `ActionCallback.onAction()` on a **background
dispatcher**, not Main. If your `MediaController` was built on
`Dispatchers.Main.immediate` (the usual choice) and you touch it directly
inside `onAction()`, you get:

```
IllegalStateException: MediaController method is called from a wrong thread.
```

This does **not** propagate as a crash the user sees — Glance's action
runner catches it and just logs `E/GlanceAppWidget: Error in Glance App
Widget`, dropping the tap entirely. It looks exactly like "the button just
doesn't do anything."

**Fix:** hop back to Main before touching the controller:

```kotlin
suspend fun execute(context: Context, command: String) = withContext(Dispatchers.Main.immediate) {
    val controller = connect(context) ?: return@withContext
    when (command) { /* ... */ }
}
```

### 3. Cancelling an in-flight `updateAll()` corrupts it — don't use `collectLatest`

If multiple state pushes can happen close together (which they will — a
single play/pause tap can fire both `onIsPlayingChanged` and
`onPlaybackStateChanged` within the same millisecond), the instinct is to
coalesce them with `Flow.collectLatest`, which cancels an in-progress
collection whenever a newer value arrives. Don't do this around
`GlanceAppWidget.updateAll()` — cancelling it mid-flight throws

```
kotlinx.coroutines.flow.internal.ChildCancelledException: Child of the scoped flow was cancelled
```

from inside Glance's own internals, and the update is lost rather than
cleanly aborted.

**Fix:** either use plain `Flow.collect` (a `StateFlow` is already
conflated, so a slow collector naturally skips stale intermediate values
without ever being interrupted), or — once you've applied fix #1 above —
just fire-and-forget each persistence call independently, since correctness
no longer depends on it succeeding synchronously.

## Step-by-step implementation

### 1. Dependencies

`gradle/libs.versions.toml`:
```toml
[versions]
glance = "1.1.1"   # see note below

[libraries]
androidx-glance = { group = "androidx.glance", name = "glance", version.ref = "glance" }
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
```

The module that owns the widget's `MediaController` connection needs
`media3-session`/`media3-common` directly (not just transitively through
whatever module owns the playback service) — you'll be building a real
`MediaController` from widget code:
```kotlin
implementation(libs.androidx.media3.session)
implementation(libs.androidx.media3.common)
```

### 2. Keep the playback service dumb

`MediaSessionService` should own the `ExoPlayer`/`MediaSession` and nothing
else — no DataStore writes, no widget imports, no reflection to reach a
widget class in another module. Anything that wants playback state (a Now
Playing screen, the widget) connects its own `MediaController` to the
session. Multiple simultaneous `MediaController`s against one session is
the normal, supported pattern — it's exactly how the app's own UI likely
already talks to the service.

### 3. The widget's own connection + reactive state holder

A singleton (object) that:
- Builds one `MediaController`, lazily, on `Dispatchers.Main.immediate`, and
  reuses it — not a fresh controller per button tap.
- Registers a `Player.Listener` that updates an in-memory `MutableStateFlow`
  on every relevant event.
- Exposes that flow publicly as `val uiState: StateFlow<PlayerSnapshot>` —
  this is what `provideGlance()` observes (pitfall #1).
- Separately, best-effort persists the same snapshot to
  `updateAppWidgetState()` + calls `updateAll()`, for the cold-start case.
- Wraps any button-tap command handling in `withContext(Dispatchers.Main.immediate)`
  (pitfall #2).
- Connect it up front — call it from both `GlanceAppWidgetReceiver.onEnabled()`
  (first widget placed) *and* the top of `provideGlance()` (covers a process
  restart where `onEnabled()` won't fire again for an already-placed widget).
  Also worth calling from `Application.onCreate()`, since `updatePeriodMillis="0"`
  widgets are otherwise only ever driven by explicit calls — nothing wakes
  the connection up on its own if the widget doesn't happen to redraw first.
- Release the controller in `GlanceAppWidgetReceiver.onDisabled()` (last
  widget instance removed).

### 4. The widget itself

- `provideGlance()`: read prefs once for the cold-start fallback value, then
  inside `provideContent { }`, `collectAsState()` on the connection's
  `uiState` and render from that (pitfall #1). Load album art reactively
  too (`LaunchedEffect(state.albumArtPath) { ... }`), not as a one-shot
  suspend call outside the composition.
- Action buttons: `clickable(actionRunCallback<YourActionCallback>(actionParametersOf(...)))`.
- `ActionCallback.onAction()`: delegate straight to the connection's
  `execute()` function (pitfall #2 lives inside that function, not here).
- `updatePeriodMillis="0"` in the widget's `appwidget-provider` XML — this
  is a push-only widget; don't fight that by adding a system periodic
  update.

### 5. What NOT to build

- **No progress-bar ticker pushing `updateAll()` every 1-2 seconds.** A
  home-screen widget isn't a notification; a live per-second progress bar
  isn't standard (Spotify/YouTube Music widgets don't animate one either),
  and it's the single biggest source of the update flood that triggers
  pitfall #3. Update the progress bar only on real play/pause/seek/track
  events.
- **No per-tap `MediaController` reconnect.** Build once, reuse.
- **No reflection to cross a module boundary just to reach a widget/session
  class.** If widget code needs the playback service's class or the widget
  needs the app's `MainActivity`, add the module dependency directly rather
  than `Class.forName(...)`-ing around it.

## Debugging checklist if updates still aren't showing

1. Log every `Player.Listener` callback, every state push, and
   `provideGlance()`'s entry — confirm state pushes are actually happening
   before assuming Glance is at fault.
2. `adb logcat | grep -i "GlanceAppWidget\|ChildCancelledException\|wrong thread"`
   — Glance swallows a lot of exceptions into `E/GlanceAppWidget` logs
   rather than crashing; this is often the only visible trace of pitfalls
   #2 and #3.
3. If in doubt about what a specific Glance version actually does versus
   what a blog post/StackOverflow answer *claims* it does, decompile the
   exact jar the project depends on
   (`~/.gradle/caches/modules-2/files-2.1/androidx.glance/.../*.aar`, unzip,
   `javap -p` the `classes.jar` contents) rather than trusting secondhand
   claims about API surface — an AI-summarized web page asserted a
   `runComposition()` bypass API existed in `glance-appwidget:1.2.0-rc01`
   during this investigation; it didn't. Decompiling the real jar caught it.
