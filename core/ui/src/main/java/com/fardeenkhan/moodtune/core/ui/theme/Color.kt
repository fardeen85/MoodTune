package com.fardeenkhan.moodtune.core.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Dark Stream Palette
val Primary = Color(0xFFC9BFFF)
val OnPrimary = Color(0xFF2E009C)
val PrimaryContainer = Color(0xFF917EFF)
val OnPrimaryContainer = Color(0xFF28008A)

val Secondary = Color(0xFF53E076)
val OnSecondary = Color(0xFF003914)
val SecondaryContainer = Color(0xFF02B04C)
val OnSecondaryContainer = Color(0xFF003A14)

val Tertiary = Color(0xFFC6C6C7)
val OnTertiary = Color(0xFF2F3131)
val TertiaryContainer = Color(0xFF909191)
val OnTertiaryContainer = Color(0xFF282A2A)

val Background = Color(0xFF131313)
val OnBackground = Color(0xFFE5E2E1)

val Surface = Color(0xFF131313)
val OnSurface = Color(0xFFE5E2E1)
val SurfaceVariant = Color(0xFF353534)
val OnSurfaceVariant = Color(0xFFC9C4D8)

val Outline = Color(0xFF928EA1)
val OutlineVariant = Color(0xFF484555)

val Error = Color(0xFFFFB4AB)
val OnError = Color(0xFF690005)
val ErrorContainer = Color(0xFF93000A)
val OnErrorContainer = Color(0xFFFFDAD6)

// Levels from Elevation & Depth
val SurfaceLevel1 = Color(0xFF1E1E1E)
val SurfaceLevel2 = Color(0xFF2A2A2A)

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
