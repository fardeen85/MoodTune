// RestrictedApi is a known Lint false-positive here: androidx.glance.unit.ColorProvider(Color)
// is a public, unrestricted factory - only the sibling ColorProvider(@ColorRes Int) overload is
// @RestrictTo(LIBRARY_GROUP). Lint's resolution of the Color overload's name-mangled JVM
// signature (Color is an inline value class) misattributes the restriction to it. Confirmed by
// decompiling the actual glance-1.2.0-rc01 dependency jar and checking both overloads' bytecode
// annotations directly.
@file:Suppress("RestrictedApi")

package com.fardeenkhan.moodtune.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.graphics.shapes.toPath
import com.fardeenkhan.moodtune.app.MainActivity
import com.fardeenkhan.moodtune.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Responsive Widget for MoodTune using Jetpack Glance. Full-bleed album art with a bottom
 * scrim, in the style of YouTube Music's widget - the art itself is the background, text and
 * controls sit in white over a gradient fade rather than inside a separate card.
 */
class MoodTuneWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // moodtune_widget_info.xml declares minWidth=220dp/minResizeWidth=220dp - width can never go
    // below that, it only ever gets wider. Height is what actually varies (minResizeHeight=40dp
    // up to maxResizeHeight=400dp). A previous 100x100/200x100/200x200 breakpoint set had a
    // 100dp-wide entry that's physically impossible for this widget, which made
    // SizeMode.Responsive match the wrong declared size for a short, wide real widget - LocalSize
    // reported a 100x100 square (triggering the icon-only Small layout with no text) while the
    // actual box was a ~220-450dp-wide, ~40-90dp-tall bar, so text never rendered and the
    // full-bleed art got cropped against the wrong aspect ratio. These breakpoints match the real
    // constraint shape instead.
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(220.dp, 70.dp),  // Small - shortest possible row
            DpSize(250.dp, 110.dp), // Medium - a bit taller
            DpSize(250.dp, 250.dp)  // Large - tall enough for full art + progress bar
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Make sure the widget's MediaController connection (and its Player.Listener) exists
        // whenever the widget is being rendered, rather than only lazily on a button tap -
        // otherwise playback started from the app screen never reaches an unconnected widget.
        WidgetMediaConnection.ensureConnected(context)

        // GlanceAppWidget.update()/updateAll() do NOT restart provideGlance() while a session is
        // already running - confirmed against the Glance team (kotlinlang Slack #glance). A
        // one-shot prefs read here would only ever reflect whatever was true the moment this
        // function last ran, so it's used only as the cold-start fallback (process death, or a
        // widget session that already timed out and needs a fresh render). The live source of
        // truth is WidgetMediaConnection.uiState, observed reactively below so THIS composition
        // recomposes itself on every change instead of relying on provideGlance() re-running.
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initialSnapshot = WidgetMediaConnection.PlayerSnapshot(
            title = prefs[WidgetKeys.TRACK_TITLE] ?: "",
            artist = prefs[WidgetKeys.ARTIST_NAME] ?: "",
            albumArtPath = prefs[WidgetKeys.ALBUM_ART_PATH] ?: "",
            isPlaying = prefs[WidgetKeys.IS_PLAYING] ?: false,
            progress = prefs[WidgetKeys.CURRENT_PROGRESS] ?: 0f
        )

        // Wallpaper's Material You primary color (Android 12+), or null on older devices/OS
        // versions without dynamic color - same source and same nullable-fallback pattern as
        // the app's own MoodTuneTheme (see plan/Color_system.md). Text stays hardcoded white
        // regardless (it must stay legible over unpredictable album art), but the play button's
        // "glass" tint and the progress bar fill pick this up so the widget's accent matches
        // whatever the rest of the app is currently tinted with.
        val accentColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context).primary
        } else {
            null
        }

        provideContent {
            val state by WidgetMediaConnection.uiState.collectAsState(initial = initialSnapshot)
            var albumArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(state.albumArtPath) {
                albumArtBitmap = loadAlbumArtBitmap(context, state.albumArtPath)
            }

            GlanceTheme {
                Content(
                    trackTitle = state.title,
                    artistName = state.artist,
                    isPlaying = state.isPlaying,
                    currentProgress = state.progress,
                    albumArtBitmap = albumArtBitmap,
                    accentColor = accentColor
                )
            }
        }
    }

    @Composable
    private fun Content(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        currentProgress: Float,
        albumArtBitmap: Bitmap?,
        accentColor: Color?
    ) {
        val size = LocalSize.current

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(ImageProvider(R.drawable.widget_gradient_bg))
        ) {
            if (trackTitle.isEmpty()) {
                EmptyState(GlanceModifier.fillMaxSize())
            } else {
                // Full-bleed album art IS the background - no separate thumbnail box. The Small
                // layout is a short, wide bar (~70dp tall, 220dp+ wide) - cropping a roughly
                // square album art into that aspect ratio hides most of it, so that breakpoint
                // fits the image instead of cropping it (matches the Small/Medium threshold used
                // for the layout switch below).
                if (albumArtBitmap != null) {
                    val artContentScale = if (size.height < 90.dp) ContentScale.Fit else ContentScale.Crop
                    Image(
                        provider = ImageProvider(albumArtBitmap),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = artContentScale
                    )
                }

                // Bottom scrim so white text/controls stay legible over any art.
                Image(
                    provider = ImageProvider(R.drawable.widget_scrim_gradient),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                // Width is always >= 220dp for this widget (see sizeMode above) so it's not a
                // useful signal here - height is what actually distinguishes the three sizes.
                when {
                    size.height >= 180.dp -> LargeLayout(
                        trackTitle, artistName, isPlaying, currentProgress, accentColor, GlanceModifier.fillMaxSize()
                    )
                    size.height >= 90.dp -> MediumLayout(
                        trackTitle, artistName, isPlaying, GlanceModifier.fillMaxSize()
                    )
                    else -> SmallLayout(trackTitle, artistName, isPlaying, GlanceModifier.fillMaxSize())
                }
            }
        }
    }

    // The shortest possible size (~70dp tall) but always >= 220dp wide - a single compact row,
    // not the icon-only square this used to assume. 220dp is always enough room for a truncated
    // title/artist plus prev/play/next together, so all three controls always show - no need to
    // gate them on width like the other layouts do (SizeMode.Responsive reports the *declared*
    // breakpoint size exactly, never the real device width, so a width-based condition here would
    // just always evaluate the same way regardless of the widget's actual resized dimensions).
    @Composable
    private fun SmallLayout(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        modifier: GlanceModifier
    ) {
        Row(
            modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(
                    Intent(LocalContext.current, MainActivity::class.java)
                ))
            ) {
                Text(text = trackTitle, style = widgetTitleStyle(13.sp), maxLines = 1)
                Text(text = artistName, style = widgetSubtitleStyle(11.sp), maxLines = 1)
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            PreviousButton(size = 22.dp)
            Spacer(modifier = GlanceModifier.width(6.dp))
            PlayPauseButton(isPlaying, size = 36.dp)
            Spacer(modifier = GlanceModifier.width(6.dp))
            NextButton(size = 22.dp)
        }
    }

    @Composable
    private fun MediumLayout(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        modifier: GlanceModifier
    ) {
        Column(modifier = modifier, verticalAlignment = Alignment.Bottom) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(
                        Intent(LocalContext.current, MainActivity::class.java)
                    ))
                ) {
                    Text(text = trackTitle, style = widgetTitleStyle(14.sp), maxLines = 1)
                    Text(text = artistName, style = widgetSubtitleStyle(12.sp), maxLines = 1)
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                PreviousButton(size = 24.dp)
                Spacer(modifier = GlanceModifier.width(6.dp))
                PlayPauseButton(isPlaying, size = 40.dp)
                Spacer(modifier = GlanceModifier.width(6.dp))
                NextButton(size = 24.dp)
            }
        }
    }

    @Composable
    private fun LargeLayout(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        currentProgress: Float,
        accentColor: Color?,
        modifier: GlanceModifier
    ) {
        Column(modifier = modifier, verticalAlignment = Alignment.Bottom) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clickable(actionStartActivity(
                        Intent(LocalContext.current, MainActivity::class.java)
                    ))
            ) {
                Text(text = trackTitle, style = widgetTitleStyle(18.sp), maxLines = 1)
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(text = artistName, style = widgetSubtitleStyle(13.sp), maxLines = 1)
                Spacer(modifier = GlanceModifier.height(14.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviousButton(size = 30.dp)
                    Spacer(modifier = GlanceModifier.width(20.dp))
                    PlayPauseButton(isPlaying, size = 56.dp)
                    Spacer(modifier = GlanceModifier.width(20.dp))
                    NextButton(size = 30.dp)
                }
            }

            LinearProgressIndicator(
                progress = currentProgress,
                modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                color = ColorProvider(accentColor ?: Color.White),
                backgroundColor = ColorProvider(Color.White.copy(alpha = 0.25f))
            )
        }
    }

    @Composable
    private fun EmptyState(modifier: GlanceModifier) {
        val size = LocalSize.current
        val isSmallHeight = size.height < 120.dp

        Column(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isSmallHeight) {
                Image(
                    provider = ImageProvider(R.drawable.ic_music_note),
                    contentDescription = null,
                    modifier = GlanceModifier.size(48.dp)
                )
                Spacer(modifier = GlanceModifier.height(12.dp))
            }
            Text(
                text = "No song playing",
                style = TextStyle(
                    fontSize = if (isSmallHeight) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
            if (!isSmallHeight) {
                Text(
                    text = "Pick a vibe in MoodTune",
                    style = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center)
                )
                Spacer(modifier = GlanceModifier.height(20.dp))
            } else {
                Spacer(modifier = GlanceModifier.height(8.dp))
            }

            Box(
                modifier = GlanceModifier
                    .height(if (isSmallHeight) 36.dp else 48.dp)
                    .padding(horizontal = if (isSmallHeight) 16.dp else 24.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(if (isSmallHeight) 18.dp else 24.dp)
                    .clickable(actionStartActivity(
                        Intent(LocalContext.current, MainActivity::class.java)
                    )),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open App",
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallHeight) 12.sp else 14.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun PlayPauseButton(isPlaying: Boolean, size: androidx.compose.ui.unit.Dp = 48.dp) {
        val context = LocalContext.current
        val sizePx = (size.value * context.resources.displayMetrics.density).toInt()

        Box(
            modifier = GlanceModifier
                .size(size)
                .background(ImageProvider(getCookieShapeBitmap(sizePx)))
                .clickable(actionRunCallback<MediaActionCallback>(
                    actionParametersOf(MediaActionCallback.ACTION_KEY to "play_pause")
                )),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = "Play/Pause",
                modifier = GlanceModifier.size(size * 0.5f),
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black))
            )
        }
    }

    @Composable
    private fun NextButton(size: androidx.compose.ui.unit.Dp = 32.dp) {
        Image(
            provider = ImageProvider(R.drawable.ic_skip_next),
            contentDescription = "Next",
            modifier = GlanceModifier.size(size).clickable(actionRunCallback<MediaActionCallback>(
                actionParametersOf(MediaActionCallback.ACTION_KEY to "next")
            )),
            colorFilter = ColorFilter.tint(ColorProvider(Color.White.copy(alpha = 0.9f)))
        )
    }

    @Composable
    private fun PreviousButton(size: androidx.compose.ui.unit.Dp = 32.dp) {
        Image(
            provider = ImageProvider(R.drawable.ic_skip_previous),
            contentDescription = "Previous",
            modifier = GlanceModifier.size(size).clickable(actionRunCallback<MediaActionCallback>(
                actionParametersOf(MediaActionCallback.ACTION_KEY to "previous")
            )),
            colorFilter = ColorFilter.tint(ColorProvider(Color.White.copy(alpha = 0.9f)))
        )
    }
}

private fun widgetTitleStyle(fontSize: androidx.compose.ui.unit.TextUnit) = TextStyle(
    color = ColorProvider(Color.White),
    fontSize = fontSize,
    fontWeight = FontWeight.Bold
)

private fun widgetSubtitleStyle(fontSize: androidx.compose.ui.unit.TextUnit) = TextStyle(
    color = ColorProvider(Color.White.copy(alpha = 0.75f)),
    fontSize = fontSize
)

// RemoteViews dedupes embedded bitmaps by object identity, not pixel content, and caps total
// bitmap memory per update. Reusing the same Bitmap instance across repeated updates for the
// same song (instead of decoding a fresh one each time) keeps repeated updates under that
// budget. Held as a single (path, bitmap) pair behind an AtomicReference - two separate
// @Volatile fields could be read/written out of sync when multiple provideGlance() calls race
// (e.g. several widget sizes/instances recomposing back to back), serving a "torn" pairing
// where the path already points at the new song but the bitmap is still the previous song's art.
private val cachedArt = java.util.concurrent.atomic.AtomicReference<Pair<String, Bitmap>?>(null)

// Glance can't clip to an arbitrary Compose Shape (MaterialShapes.Cookie7Sided.toShape(), the
// same one Now Playing uses, only works with real Compose UI/Canvas) - widgets render via
// RemoteViews. Rasterizing the exact same RoundedPolygon into a small white bitmap once per
// pixel size and using it as the button's background image gets the identical shape. Only 3
// sizes are ever requested (36/40/56dp across breakpoints), so this cache stays tiny.
private val cookieShapeCache = ConcurrentHashMap<Int, Bitmap>()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun getCookieShapeBitmap(sizePx: Int): Bitmap = cookieShapeCache.getOrPut(sizePx) {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val path = MaterialShapes.Cookie7Sided.toPath().apply {
        transform(Matrix().apply { setScale(sizePx.toFloat(), sizePx.toFloat()) })
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    Canvas(bitmap).drawPath(path, paint)
    bitmap
}

/**
 * Widgets render via RemoteViews in the launcher's process, which can't resolve a
 * file:// Uri into our private storage or fetch a raw http(s) Uri. Decode the art to a
 * Bitmap here (in-process, suspend-safe) so Glance can embed it directly instead.
 */
private suspend fun loadAlbumArtBitmap(context: Context, path: String): Bitmap? {
    if (path.isEmpty()) return null
    cachedArt.get()?.let { (cachedPath, cachedBitmap) -> if (cachedPath == path) return cachedBitmap }

    return withContext(Dispatchers.IO) {
        try {
            val bytes = when {
                path.startsWith("file://") -> {
                    val file = File(Uri.parse(path).path ?: return@withContext null)
                    if (file.exists()) file.readBytes() else null
                }
                path.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
                }
                path.startsWith("http://") || path.startsWith("https://") -> {
                    java.net.URL(path).openStream().use { it.readBytes() }
                }
                else -> null
            } ?: return@withContext null

            // Downsample - RemoteViews has a strict transaction size budget, and full-res
            // embedded art can blow past it and crash the widget update.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= 320 && bounds.outHeight / (sampleSize * 2) >= 320) {
                sampleSize *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
                ?.also { cachedArt.set(path to it) }
        } catch (e: Exception) {
            android.util.Log.e("MoodTuneWidget", "Failed to load album art from $path", e)
            null
        }
    }
}

/**
 * Action Callback for Media Controls
 */
class MediaActionCallback : ActionCallback {
    companion object {
        val ACTION_KEY = ActionParameters.Key<String>("action")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[ACTION_KEY] ?: return
        WidgetMediaConnection.execute(context, action)
    }
}
