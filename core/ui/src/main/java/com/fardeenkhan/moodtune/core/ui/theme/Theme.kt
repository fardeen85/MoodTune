package com.fardeenkhan.moodtune.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

// The design is focused on a premium dark experience.
// For now, we'll use a similar dark-centric scheme for light mode to maintain brand identity,
// or you can define a proper LightColorScheme if needed later.
private val LightColorScheme = DarkColorScheme

// How much of the wallpaper's primary color gets mixed into our near-black base. The screen
// background stays close to true black (premium dark identity, minimal tint); nav surfaces
// (bottom bar + rail, via surfaceContainer) get a noticeably stronger tint so they read as
// wallpaper-tinted chrome rather than a flat black bar. Only applied when a real wallpaper
// palette exists (see [LocalWallpaperAccent]) - never blended toward a fake substitute.
private const val BackgroundBlendFraction = 0.08f
private const val NavContainerBlendFraction = 0.22f

/**
 * The wallpaper's extracted primary tonal color (Material You, Android 12+), or null when
 * unavailable - old device, dynamic color disabled, etc. Screens outside core/ui (e.g. the
 * decorative header shapes on the Playlist/MoodDetail screens) read this to blend their own
 * fixed colors toward the wallpaper, while still falling back to their original color exactly
 * when this is null, instead of guessing at a substitute.
 */
val LocalWallpaperAccent = staticCompositionLocalOf<Color?> { null }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoodTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Real wallpaper-derived tonal colors, or null below API 31 / when dynamic color just isn't
    // available - in which case every role below is left exactly as authored in Color.kt, with
    // no blending at all.
    var wallpaperPrimary: Color? = null
    var wallpaperPrimaryContainer: Color? = null
    var wallpaperOnPrimaryContainer: Color? = null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        wallpaperPrimary = dynamicScheme.primary
        wallpaperPrimaryContainer = dynamicScheme.primaryContainer
        wallpaperOnPrimaryContainer = dynamicScheme.onPrimaryContainer
    }

    val colorScheme = baseScheme.copy(
        primary = wallpaperPrimary ?: Primary,
        onPrimary = OnPrimary,
        primaryContainer = wallpaperPrimaryContainer ?: PrimaryContainer,
        onPrimaryContainer = wallpaperOnPrimaryContainer ?: OnPrimaryContainer,
        secondary = wallpaperPrimary ?: Secondary,
        onSecondary = OnSecondary,
        // Bottom nav's selected-item indicator pill + icon - same primaryContainer/
        // onPrimaryContainer family as the "Add songs" FAB (which reads colorScheme.primary/
        // onPrimary), so the selected nav item matches that button instead of standing out in
        // an unrelated hue.
        secondaryContainer = wallpaperPrimaryContainer ?: PrimaryContainer,
        onSecondaryContainer = wallpaperOnPrimaryContainer ?: OnPrimaryContainer,
        background = wallpaperPrimary?.let { lerp(Background, it, BackgroundBlendFraction) } ?: Background,
        surface = wallpaperPrimary?.let { lerp(Background, it, BackgroundBlendFraction) } ?: Surface,
        // Bottom NavigationBar's default container role. NavigationRail defaults to `surface`
        // instead, so MainActivity's NavigationSuiteScaffold pins both bar and rail to this
        // color explicitly - keeping them visually identical and both on the "more blended" side.
        surfaceContainer = wallpaperPrimary?.let { lerp(Background, it, NavContainerBlendFraction) } ?: baseScheme.surfaceContainer,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalWallpaperAccent provides wallpaperPrimary) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
