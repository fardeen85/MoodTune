package com.fardeenkhan.moodtune.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fardeenkhan.moodtune.app.R

import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.LinearProgressIndicator
import com.fardeenkhan.moodtune.infrastructure.WidgetKeys
import com.fardeenkhan.moodtune.infrastructure.MoodTuneWidgetHelper

/**
 * Responsive Widget for MoodTune using Jetpack Glance.
 */
class MoodTuneWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Define responsive layout buckets
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
            // albumArtPath would be used with ImageProvider(Uri) or similar, 
            // but for this implementation we'll use a placeholder or local resource if path is empty
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
                .background(GlanceTheme.colors.background)
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
        ) {
            // Background Album Art (Dimmed)
            if (albumArtPath.isNotEmpty()) {
                Image(
                    provider = ImageProvider(android.net.Uri.parse(albumArtPath)),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = androidx.glance.layout.ContentScale.Crop,
                    colorFilter = androidx.glance.ColorFilter.tint(
                        androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                    )
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
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumArt(albumArtPath, GlanceModifier.size(48.dp))
            Spacer(modifier = GlanceModifier.width(8.dp))
            PlayPauseButton(isPlaying)
            Spacer(modifier = GlanceModifier.width(4.dp))
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
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(albumArtPath, GlanceModifier.size(64.dp))
            Spacer(modifier = GlanceModifier.width(12.dp))
            
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(
                Intent(LocalContext.current, Class.forName("com.fardeenkhan.moodtune.app.MainActivity"))
            ))) {
                Text(
                    text = trackTitle,
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = artistName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp
                    ),
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviousButton()
                PlayPauseButton(isPlaying)
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
                AlbumArt(albumArtPath, GlanceModifier.size(80.dp))
                Spacer(modifier = GlanceModifier.width(16.dp))
                
                Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(
                    Intent(LocalContext.current, Class.forName("com.fardeenkhan.moodtune.app.MainActivity"))
                ))) {
                    Text(
                        text = trackTitle,
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2
                    )
                    Text(
                        text = artistName,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 16.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Wavy Progress Bar
            LinearProgressIndicator(
                progress = currentProgress
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_favorite_border),
                    contentDescription = "Like",
                    modifier = GlanceModifier.size(24.dp).clickable(actionRunCallback<MediaActionCallback>(
                        actionParametersOf(MediaActionCallback.ACTION_KEY to "like")
                    ))
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                PreviousButton()
                Spacer(modifier = GlanceModifier.width(16.dp))
                PlayPauseButton(isPlaying, size = 48.dp)
                Spacer(modifier = GlanceModifier.width(16.dp))
                NextButton()
                Spacer(modifier = GlanceModifier.defaultWeight())
                Image(
                    provider = ImageProvider(R.drawable.ic_playlist_play),
                    contentDescription = "Queue",
                    modifier = GlanceModifier.size(24.dp)
                )
            }
        }
    }

    @Composable
    private fun EmptyState(modifier: GlanceModifier) {
        Column(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select a track to start listening",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 14.sp,
                    textAlign = androidx.glance.text.TextAlign.Center
                )
            )
            Spacer(modifier = GlanceModifier.height(12.dp))
            Button(
                text = "Open MoodTune",
                onClick = actionStartActivity(
                    Intent(LocalContext.current, Class.forName("com.fardeenkhan.moodtune.app.MainActivity"))
                )
            )
        }
    }

    @Composable
    private fun WavyProgressIndicator(progress: Float, modifier: GlanceModifier) {
        val bars = 30
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0 until bars) {
                val barProgress = i.toFloat() / bars.toFloat()
                val isFilled = barProgress <= progress
                val height = if (i % 2 == 0) 12.dp else 18.dp
                
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(height)
                        .padding(horizontal = 1.dp)
                        .background(if (isFilled) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant)
                        .cornerRadius(2.dp)
                ) {}
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
                .cornerRadius(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = provider,
                contentDescription = "Album Art",
                modifier = if (path.isNotEmpty()) GlanceModifier.fillMaxSize() else GlanceModifier.size(24.dp),
                contentScale = if (path.isNotEmpty()) androidx.glance.layout.ContentScale.Crop else androidx.glance.layout.ContentScale.Fit
            )
        }
    }

    @Composable
    private fun PlayPauseButton(isPlaying: Boolean, size: androidx.compose.ui.unit.Dp = 40.dp) {
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
                modifier = GlanceModifier.size(size * 0.6f),
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
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onBackground)
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
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onBackground)
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
