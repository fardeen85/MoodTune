# MoodTune Color System

The single source of truth for every color in the app. Two files own it:

- `core/ui/src/main/java/com/fardeenkhan/moodtune/core/ui/theme/Color.kt` — the
  static palette (the colors the app has when there's no wallpaper-derived
  theming, and the values everything else falls back to).
- `core/ui/src/main/java/com/fardeenkhan/moodtune/core/ui/theme/Theme.kt` — the
  `MoodTuneTheme` composable that turns that static palette into the actual
  `ColorScheme` the app renders with, optionally blended with the device
  wallpaper's Material You palette.

Everything downstream (feature screens, `MainActivity`'s nav bar, the home
screen widget) either reads `MaterialTheme.colorScheme.*` or calls
`getMoodColor(mood)` — nothing should hardcode a duplicate of a color that
already exists in `Color.kt`.

## `Color.kt`, line by line

```kotlin
val Primary = Color(0xFFC9BFFF)          // Light lavender - brand accent, buttons/FAB/selected states
val OnPrimary = Color(0xFF2E009C)        // Deep indigo - text/icons drawn on top of Primary
val PrimaryContainer = Color(0xFF917EFF) // Mid-purple - lower-emphasis "container" variant of Primary
val OnPrimaryContainer = Color(0xFF28008A) // Text/icons on PrimaryContainer
```
The brand's signature purple. Everything with "high emphasis" (the "Add
songs" FAB, primary buttons) uses `Primary`/`OnPrimary`; anything that needs a
softer background chip/pill (the selected nav item's indicator) uses
`PrimaryContainer`/`OnPrimaryContainer`.

```kotlin
val Secondary = Color(0xFF53E076)          // Green
val OnSecondary = Color(0xFF003914)
val SecondaryContainer = Color(0xFF02B04C)
val OnSecondaryContainer = Color(0xFF003A14)
```
A secondary accent, currently only used as the static *fallback* for the
`secondary` role (see Theme.kt below - on a device with dynamic color,
`secondary` is overridden to track the wallpaper instead).

```kotlin
val Tertiary = Color(0xFFC6C6C7)          // Neutral grey family - least-used M3 role in this app
val OnTertiary = Color(0xFF2F3131)
val TertiaryContainer = Color(0xFF909191)
val OnTertiaryContainer = Color(0xFF282A2A)
```

```kotlin
val Background = Color(0xFF131313)   // The app's signature near-black
val OnBackground = Color(0xFFE5E2E1) // Off-white text on Background
val Surface = Color(0xFF131313)      // Same near-black as Background - cards/sheets sit flush with the screen
val OnSurface = Color(0xFFE5E2E1)
val SurfaceVariant = Color(0xFF353534)   // A visibly lighter dark grey - used to differentiate a surface from its parent (e.g. an input field)
val OnSurfaceVariant = Color(0xFFC9C4D8)
```
`Background`/`Surface` being the same value is deliberate - this is the
"premium dark" identity described in `DESIGN.md`. Nothing about it is a mistake
to "fix"; if you want a screen to visually separate from its background, use
`SurfaceVariant`, not a hardcoded grey.

```kotlin
val Outline = Color(0xFF928EA1)        // Dividers, input borders
val OutlineVariant = Color(0xFF484555) // Lower-emphasis dividers
```

```kotlin
val Error = Color(0xFFFFB4AB)
val OnError = Color(0xFF690005)
val ErrorContainer = Color(0xFF93000A)
val OnErrorContainer = Color(0xFFFFDAD6)
```
Standard Material error roles - used by the delete-playlist confirmation
dialog's destructive button, for example.

```kotlin
val SurfaceLevel1 = Color(0xFF1E1E1E)
val SurfaceLevel2 = Color(0xFF2A2A2A)
```
Two hand-picked "elevation" tones for the rare cases where an M3 tonal
surface role doesn't give enough visual separation on a plain black
background (e.g. a card floating above `Background`).

```kotlin
private val moodColorPalette = listOf(
    Color(0xFFFF5722), // Deep Orange
    Color(0xFF4CAF50), // Green
    Color(0xFF2196F3), // Blue
    Color(0xFF9C27B0), // Purple
    Color(0xFFE91E63), // Pink
    Color(0xFFFFEB3B), // Yellow
    Color(0xFF00BCD4), // Cyan
    Color(0xFF673AB7), // Deep Purple
    Color(0xFF009688), // Teal
    Color(0xFFFF9800)  // Orange
)

fun getMoodColor(mood: String): Color {
    val index = Math.abs(mood.lowercase().hashCode()) % moodColorPalette.size
    return moodColorPalette[index]
}
```
Deterministic color-per-mood: the same mood string always hashes to the same
color, so a playlist's card color is stable across app restarts without being
persisted anywhere. **This is the only correct way to get a mood's color** -
never re-derive a mood-to-color mapping locally in a screen; call
`getMoodColor(mood)` from `core/ui`.

## `Theme.kt`: static palette -> wallpaper-blended `ColorScheme`

### The two color schemes

```kotlin
private val DarkColorScheme = darkColorScheme(primary = Primary, /* ...all of Color.kt... */)
private val LightColorScheme = DarkColorScheme
```
`LightColorScheme` is intentionally identical to `DarkColorScheme` - the app
has one visual identity ("premium dark") and doesn't attempt a separate light
theme yet. If a real light theme is ever wanted, this is where it starts.

### Blend fractions

```kotlin
private const val BackgroundBlendFraction = 0.08f
private const val NavContainerBlendFraction = 0.22f
```
How much of the wallpaper's primary color gets mixed into the near-black
`Background` for two different roles:
- `BackgroundBlendFraction` (8%): the screen background. Kept low - the app
  should still read as "black", just with the faintest wallpaper cast.
- `NavContainerBlendFraction` (22%): the bottom nav bar / nav rail background.
  Deliberately much stronger, so the nav chrome reads as visibly
  wallpaper-tinted rather than a flat black bar. Tune this constant alone to
  make the nav bar/rail lighter or darker - both surfaces read from the same
  value (see "How `MainActivity` consumes this" below), so one edit affects
  both.

Both are plain `lerp(Background, wallpaperColor, fraction)` calls - a linear
interpolation between the static near-black and whatever the wallpaper's
primary tone is that day.

### `LocalWallpaperAccent`

```kotlin
val LocalWallpaperAccent = staticCompositionLocalOf<Color?> { null }
```
A `CompositionLocal` exposing the *raw* wallpaper primary color (or `null` if
unavailable) to any composable in the app, not just ones that read
`MaterialTheme.colorScheme`. This exists because some UI - the decorative
gradient shapes on the Playlist and MoodDetail screens - has its own fixed
two-color gradients that should shift toward the wallpaper while keeping
their own original hue identity (see "Decorative shapes" below), which isn't
something a single `ColorScheme` role can express.

### The blending logic itself

```kotlin
var wallpaperPrimary: Color? = null
var wallpaperPrimaryContainer: Color? = null
var wallpaperOnPrimaryContainer: Color? = null

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    wallpaperPrimary = dynamicScheme.primary
    wallpaperPrimaryContainer = dynamicScheme.primaryContainer
    wallpaperOnPrimaryContainer = dynamicScheme.onPrimaryContainer
}
```
Below Android 12 (API 31, `VERSION_CODES.S`), or wherever
`dynamicDarkColorScheme`/`dynamicLightColorScheme` just isn't meaningful,
these three stay `null`. **This null-ness is the fallback mechanism** - every
role below reads `wallpaperX ?: staticFallback`, so on an unsupported device
the app renders with exactly the original `Color.kt` values, unblended. There
is no synthetic "pretend wallpaper color" substitute anywhere in this file -
if there's no real wallpaper palette, nothing gets tinted.

```kotlin
val colorScheme = baseScheme.copy(
    primary = wallpaperPrimary ?: Primary,
    onPrimary = OnPrimary,
    primaryContainer = wallpaperPrimaryContainer ?: PrimaryContainer,
    onPrimaryContainer = wallpaperOnPrimaryContainer ?: OnPrimaryContainer,
    secondary = wallpaperPrimary ?: Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = wallpaperPrimaryContainer ?: PrimaryContainer,
    onSecondaryContainer = wallpaperOnPrimaryContainer ?: OnPrimaryContainer,
    background = wallpaperPrimary?.let { lerp(Background, it, BackgroundBlendFraction) } ?: Background,
    surface = wallpaperPrimary?.let { lerp(Background, it, BackgroundBlendFraction) } ?: Surface,
    surfaceContainer = wallpaperPrimary?.let { lerp(Background, it, NavContainerBlendFraction) } ?: baseScheme.surfaceContainer,
)
```

What each override is actually for:

| Role | Value | Why |
|---|---|---|
| `primary` / `onPrimary` | wallpaper primary, else static `Primary` | Buttons, FAB ("Add songs"), any high-emphasis accent |
| `primaryContainer` / `onPrimaryContainer` | wallpaper primaryContainer, else static | Softer container-style accents |
| `secondary` | same as `primary` | The app doesn't currently use a genuinely distinct secondary hue - kept aliased to primary rather than introducing an unused, disconnected accent |
| `secondaryContainer` / `onSecondaryContainer` | **same as `primaryContainer`/`onPrimaryContainer`**, not the wallpaper's own secondary tonal role | This is what the bottom nav's selected-item indicator pill + icon read by default (Material3's `NavigationBarItemColors`/`NavigationRailItemColors`). Deliberately aliased to the *primary* container family so the selected nav item visually matches the "Add songs" FAB, instead of standing out in the wallpaper's unrelated secondary hue |
| `background` / `surface` | `lerp(Background, wallpaperPrimary, 0.08)` | The near-black screen background, barely tinted |
| `surfaceContainer` | `lerp(Background, wallpaperPrimary, 0.22)` | The bottom nav bar's own default background role - see below for how the nav *rail* gets the same value despite defaulting to a different role natively |

### How `MainActivity` consumes this

Material3's `NavigationBar` defaults to `colorScheme.surfaceContainer` for its
background, but `NavigationRail` defaults to plain `colorScheme.surface`
instead - meaning without intervention, the bar would be wallpaper-tinted
chrome while the rail (shown on tablets/landscape) would look like flat
near-black. `MainActivity`'s `NavigationSuiteScaffold` call pins both
explicitly to the same value:

```kotlin
navigationSuiteColors = NavigationSuiteDefaults.colors(
    navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    navigationDrawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)
```
This is why `NavContainerBlendFraction` alone controls both surfaces - they
read the identical `colorScheme.surfaceContainer` value, just through two
different Material3 components.

### Decorative shapes (Playlist grid + MoodDetail screens)

Both screens have a rotated, gradient-filled square in the top-right corner
(`feature/playlist/ui/Playlist.kt`, `PlaylistListScreen` and
`MoodDetailScreen`). These use their own fixed two-color gradients (e.g.
purple-to-green) rather than a `ColorScheme` role, so they blend toward
`LocalWallpaperAccent.current` directly:

```kotlin
val wallpaperAccent = LocalWallpaperAccent.current
Brush.linearGradient(
    colors = listOf(
        wallpaperAccent?.let { lerp(Color(0xFF7B61FF), it, 0.35f) } ?: Color(0xFF7B61FF),
        wallpaperAccent?.let { lerp(Color(0xFF1DB954), it, 0.35f) } ?: Color(0xFF1DB954)
    )
)
```
Same fallback pattern as everything else: `null` accent (no dynamic color)
means the original two hardcoded colors, completely unblended.

## The widget is a deliberate exception

`app/src/main/java/com/fardeenkhan/moodtune/app/widget/MoodTuneWidget.kt` does
**not** use `MaterialTheme`, `getMoodColor`, or any of the above. It renders
via Jetpack Glance -> `RemoteViews` in the launcher's process, over full-bleed
album art with a dark scrim, so its text/icon colors are hardcoded to white
(`ColorProvider(Color.White)` at various alphas) for legibility against
whatever art happens to be showing - a wallpaper- or mood-derived tint would
fight the album art's own colors rather than complement them. If the widget
ever needs to read app theme colors, wire that through explicitly rather than
assuming `MaterialTheme` is available - Glance compositions run outside the
app's normal Activity/Compose tree.

## Rules for adding new UI

1. **Never hardcode a color that already exists in `Color.kt`** - reference
   the `val`, or `MaterialTheme.colorScheme.*` if it's a themed role.
2. **Mood-based colors always go through `getMoodColor(mood)`** - never
   re-implement the hash-to-color mapping locally.
3. **If new UI needs to react to the wallpaper**, decide which pattern fits:
   - A genuine `ColorScheme` role (button, container, text) -> add it to the
     `colorScheme = baseScheme.copy(...)` block in `Theme.kt`, following the
     `wallpaperX ?: staticFallback` pattern.
   - A one-off fixed color/gradient outside the `ColorScheme` (a decorative
     shape, like the Playlist header) -> read `LocalWallpaperAccent.current`
     and `lerp()` toward it, with the same nullable fallback.
4. **Always test the fallback path** - the correctness of this whole system
   hinges on `wallpaperPrimary == null` producing pixel-identical output to
   the pre-dynamic-color app. On the emulator/device you're testing with,
   temporarily forcing `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` to
   `false` (or testing on an API <31 emulator image) is the way to verify
   this without needing to change the device's actual wallpaper.
