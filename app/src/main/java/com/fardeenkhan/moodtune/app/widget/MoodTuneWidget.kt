package com.fardeenkhan.moodtune.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.background
import com.fardeenkhan.moodtune.app.R
import com.fardeenkhan.moodtune.infrastructure.WidgetKeys

/**
 * Responsive Widget for MoodTune using Jetpack Glance.
 */
class MoodTuneWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(100.dp, 100.dp), // Small
            DpSize(200.dp, 100.dp), // Medium
            DpSize(200.dp, 200.dp)  // Large
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val trackTitle = prefs[WidgetKeys.TRACK_TITLE] ?: ""
            val artistName = prefs[WidgetKeys.ARTIST_NAME] ?: ""
            val isPlaying = prefs[WidgetKeys.IS_PLAYING] ?: false
            val currentProgress = prefs[WidgetKeys.CURRENT_PROGRESS] ?: 0f
            val albumArtPath = prefs[WidgetKeys.ALBUM_ART_PATH] ?: ""

            GlanceTheme {
                Content(
                    trackTitle = trackTitle,
                    artistName = artistName,
                    isPlaying = isPlaying,
                    currentProgress = currentProgress,
                    albumArtPath = albumArtPath
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
        albumArtPath: String
    ) {
        val size = LocalSize.current

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            // Background Album Art (Dimmed Overlay)
            if (albumArtPath.isNotEmpty()) {
                Image(
                    provider = ImageProvider(android.net.Uri.parse(albumArtPath)),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = androidx.glance.ColorFilter.tint(
                        androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
                    )
                )
            } else {
                // Background Gradient as fallback
                Image(
                    provider = ImageProvider(R.drawable.widget_gradient_bg),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                if (trackTitle.isEmpty()) {
                    EmptyState(GlanceModifier.fillMaxSize())
                } else {
                    when {
                        size.height >= 180.dp -> LargeLayout(
                            trackTitle, artistName, isPlaying, currentProgress, albumArtPath, GlanceModifier.fillMaxSize()
                        )
                        size.width >= 180.dp -> MediumLayout(
                            trackTitle, artistName, isPlaying, albumArtPath, GlanceModifier.fillMaxSize()
                        )
                        else -> SmallLayout(isPlaying, albumArtPath, GlanceModifier.fillMaxSize())
                    }
                }
            }
        }
    }

    @Composable
    private fun SmallLayout(
        isPlaying: Boolean,
        albumArtPath: String,
        modifier: GlanceModifier
    ) {
        val size = LocalSize.current
        val isVerySmall = size.width < 140.dp

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumArt(albumArtPath, GlanceModifier.size(if (isVerySmall) 40.dp else 48.dp))
            Spacer(modifier = GlanceModifier.width(if (isVerySmall) 6.dp else 8.dp))
            if (!isVerySmall) {
                PreviousButton()
                Spacer(modifier = GlanceModifier.width(6.dp))
            }
            PlayPauseButton(isPlaying, size = if (isVerySmall) 40.dp else 48.dp)
            Spacer(modifier = GlanceModifier.width(if (isVerySmall) 6.dp else 8.dp))
            NextButton()
        }
    }

    @Composable
    private fun MediumLayout(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        albumArtPath: String,
        modifier: GlanceModifier
    ) {
        val size = LocalSize.current
        val isNarrow = size.width < 220.dp

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(albumArtPath, GlanceModifier.size(if (isNarrow) 56.dp else 64.dp))
            Spacer(modifier = GlanceModifier.width(if (isNarrow) 8.dp else 12.dp))
            
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(
                Intent(LocalContext.current, Class.forName("com.fardeenkhan.moodtune.app.MainActivity"))
            ))) {
                Text(
                    text = trackTitle,
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFF53E076)),
                        fontSize = if (isNarrow) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = artistName,
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFFC9BFFF)),
                        fontSize = if (isNarrow) 12.sp else 14.sp
                    ),
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviousButton()
                Spacer(modifier = GlanceModifier.width(8.dp))
                PlayPauseButton(isPlaying, size = if (isNarrow) 40.dp else 48.dp)
                Spacer(modifier = GlanceModifier.width(8.dp))
                NextButton()
            }
        }
    }

    @Composable
    private fun LargeLayout(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        currentProgress: Float,
        albumArtPath: String,
        modifier: GlanceModifier
    ) {
        Column(modifier = modifier) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(albumArtPath, GlanceModifier.size(90.dp))
                Spacer(modifier = GlanceModifier.width(16.dp))
                
                Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(
                    Intent(LocalContext.current, Class.forName("com.fardeenkhan.moodtune.app.MainActivity"))
                ))) {
                    Text(
                        text = trackTitle,
                        style = TextStyle(
                            color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFF53E076)),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2
                    )
                    Text(
                        text = artistName,
                        style = TextStyle(
                            color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFFC9BFFF)),
                            fontSize = 16.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            LinearProgressIndicator(
                progress = currentProgress,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFF53E076)),
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )

            Spacer(modifier = GlanceModifier.height(16.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviousButton()
                Spacer(modifier = GlanceModifier.width(24.dp))
                PlayPauseButton(isPlaying, size = 56.dp)
                Spacer(modifier = GlanceModifier.width(24.dp))
                NextButton()
            }
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
                    modifier = GlanceModifier.size(48.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFF53E076)))
                )
                Spacer(modifier = GlanceModifier.height(12.dp))
            }
            Text(
                text = "No song playing",
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFF53E076)),
                    fontSize = if (isSmallHeight) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.glance.text.TextAlign.Center
                )
            )
            if (!isSmallHeight) {
                Text(
                    text = "Pick a vibe in MoodTune",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFFC9BFFF)),
                        fontSize = 14.sp,
                        textAlign = androidx.glance.text.TextAlign.Center
                    )
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
                        Intent(LocalContext.current, Class.forName("com.fardeenkhan.moodtune.app.MainActivity"))
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
    private fun AlbumArt(path: String, modifier: GlanceModifier) {
        val provider = if (path.isNotEmpty()) {
            ImageProvider(android.net.Uri.parse(path))
        } else {
            ImageProvider(R.drawable.ic_music_note)
        }

        Box(
            modifier = modifier
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = provider,
                contentDescription = "Album Art",
                modifier = if (path.isNotEmpty()) GlanceModifier.fillMaxSize() else GlanceModifier.size(32.dp),
                contentScale = ContentScale.Crop
            )
        }
    }

    @Composable
    private fun PlayPauseButton(isPlaying: Boolean, size: androidx.compose.ui.unit.Dp = 48.dp) {
        Box(
            modifier = GlanceModifier
                .size(size)
                .background(GlanceTheme.colors.primary)
                .cornerRadius(size / 2)
                .clickable(actionRunCallback<MediaActionCallback>(
                    actionParametersOf(MediaActionCallback.ACTION_KEY to "play_pause")
                )),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = "Play/Pause",
                modifier = GlanceModifier.size(size * 0.5f),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimary)
            )
        }
    }

    @Composable
    private fun NextButton() {
        Image(
            provider = ImageProvider(R.drawable.ic_skip_next),
            contentDescription = "Next",
            modifier = GlanceModifier.size(32.dp).clickable(actionRunCallback<MediaActionCallback>(
                actionParametersOf(MediaActionCallback.ACTION_KEY to "next")
            )),
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.primary)
        )
    }

    @Composable
    private fun PreviousButton() {
        Image(
            provider = ImageProvider(R.drawable.ic_skip_previous),
            contentDescription = "Previous",
            modifier = GlanceModifier.size(32.dp).clickable(actionRunCallback<MediaActionCallback>(
                actionParametersOf(MediaActionCallback.ACTION_KEY to "previous")
            )),
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.primary)
        )
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
        
        val intent = Intent(context, Class.forName("com.fardeenkhan.moodtune.infrastructure.MediaPlaybackService")).apply {
            this.action = "com.fardeenkhan.moodtune.ACTION_WIDGET_COMMAND"
            putExtra("EXTRA_COMMAND", action)
        }
        
        context.startService(intent)
    }
}
