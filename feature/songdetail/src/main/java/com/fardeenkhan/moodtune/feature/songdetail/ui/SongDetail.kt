package com.fardeenkhan.moodtune.feature.songdetail.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage
import com.fardeenkhan.moodtune.core.ui.theme.MoodTuneTheme
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel1
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel2
import com.fardeenkhan.moodtune.domain.model.LyricLine
import com.fardeenkhan.moodtune.domain.model.Song
import java.io.File
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingControl(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    iconSize: Dp = 32.dp,
    baseShape: RoundedPolygon = MaterialShapes.Circle,
    morphShape: RoundedPolygon = MaterialShapes.PuffyDiamond
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var isAnimTriggered by remember { mutableStateOf(false) }

    val morph = remember(baseShape, morphShape) { Morph(baseShape, morphShape) }

    val morphProgress by animateFloatAsState(
        targetValue = if (isPressed || isAnimTriggered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "morphProgress"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed || isAnimTriggered) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    scope.launch {
                        isAnimTriggered = true
                        delay(150)
                        isAnimTriggered = false
                    }
                    onClick()
                    Log.d("TAG", "clicked invoked")
                }
            )
            .drawWithCache {
                onDrawBehind {
                    val matrix = android.graphics.Matrix()
                    matrix.setScale(this.size.width, this.size.height)
                    val androidPath = morph.toPath(morphProgress)
                    androidPath.transform(matrix)

                    val alpha = if (containerColor != Color.Transparent) {
                        if (enabled) 1f else 0.3f
                    } else {
                        0.2f * morphProgress
                    }

                    if (alpha > 0.01f) {
                        drawPath(
                            path = androidPath.asComposePath(),
                            color = (if (containerColor != Color.Transparent) containerColor else contentColor).copy(alpha = alpha)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(animationSpec = spring(stiffness = 400f)) +
                        scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)))
                    .togetherWith(fadeOut(animationSpec = spring(stiffness = 400f)))
            },
            label = "iconTransition"
        ) { targetIcon ->
            Icon(
                imageVector = targetIcon,
                contentDescription = contentDescription,
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.3f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NowPlayingScreenRoot() {
    val viewModel: SongDetailViewModel = koinViewModel()
    val navigator = rememberListDetailPaneScaffoldNavigator<Nothing>()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch {
            navigator.navigateBack()
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            val queue by viewModel.currentQueue.collectAsState()
            val state by viewModel.state.collectAsState()
            QueueList(
                queue = queue,
                currentSongId = state.currentSong?.id,
                onSongClick = { index ->
                    viewModel.onIntent(SongDetailIntent.PlayFromQueue(index))
                    scope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                    }
                }
            )
        },
        detailPane = {
            NowPlayingScreen(
                viewModel = viewModel,
                onMinimize = {
                    if (navigator.canNavigateBack()) {
                        scope.launch {
                            navigator.navigateBack()
                        }
                    }
                }
            )
        }
    )
}

@Composable
fun QueueList(
    queue: List<Song>,
    currentSongId: String?,
    onSongClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = "Up Next",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(queue) { index, song ->
                val isPlaying = song.id == currentSongId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPlaying) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSongClick(index) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        val queueArtModel = song.localAlbumArtPath?.let { java.io.File(it) } ?: song.imageUrl
                        if (queueArtModel != null) {
                            AsyncImage(
                                model = queueArtModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    if (isPlaying) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncedLyricsPanel(
    lines: List<LyricLine>,
    progressMs: Long,
    modifier: Modifier = Modifier
) {
    val activeIndex by remember(progressMs, lines) {
        derivedStateOf { lines.indexOfLast { it.ms <= progressMs }.coerceAtLeast(0) }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem(activeIndex, scrollOffset = -200)
    }

    Box(modifier = modifier.clip(RoundedCornerShape(24.dp))) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines) { index, lyric ->
                val isActive = index == activeIndex
                val lineScale by animateFloatAsState(
                    targetValue = if (isActive) 1.05f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "lyricScale"
                )
                Text(
                    text = lyric.line,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(scaleX = lineScale, scaleY = lineScale)
                        .padding(vertical = 10.dp, horizontal = 16.dp)
                )
            }
        }
      /*  // Top fade mask
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.95f), Color.Transparent)
                    )
                )
        )
        // Bottom fade mask
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                    )
                )
        )*/
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: SongDetailViewModel,
    onMinimize: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val albumArtColor by viewModel.dominantColor.collectAsState()
    val lyricsState by viewModel.lyricsState.collectAsState()

    val currentSong = state.currentSong
    val isExternal = currentSong?.externalUrl?.startsWith("http") == true
    val fileExists = currentSong?.externalUrl?.let { if (!it.startsWith("http")) File(it).exists() else false } ?: false
    val isMissingLocal = !isExternal && !fileExists && currentSong != null

    val showLyricsMode = lyricsState is LyricsState.Loaded || lyricsState is LyricsState.Loading

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = albumArtColor ?: Color.Black,
        animationSpec = tween(durationMillis = 1000),
        label = "backgroundColor"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Blurred color background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(animatedBackgroundColor.copy(alpha = 0.5f))
                .blur(40.dp)
        )

        // Dimming overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        // Radial gradient accent for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            (albumArtColor ?: Color.Transparent).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
                }
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                IconButton(
                    onClick = {
                        if (showLyricsMode) {
                            viewModel.onIntent(SongDetailIntent.DismissLyrics)
                        } else {
                            viewModel.onIntent(SongDetailIntent.RequestLyrics)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (showLyricsMode) Icons.Default.Close else Icons.Default.Lyrics,
                        contentDescription = if (showLyricsMode) "Close Lyrics" else "Generate Lyrics",
                        tint = if (showLyricsMode) Color.White else Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Compact header (small art + title/artist) — visible only in lyrics mode
            AnimatedVisibility(
                visible = showLyricsMode,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)),
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    val headerArtModel = currentSong?.localAlbumArtPath?.let { java.io.File(it) } ?: currentSong?.imageUrl
                    if (headerArtModel != null) {
                        AsyncImage(
                            model = headerArtModel,
                            contentDescription = "Album Art",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceLevel1),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(0.5f))
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = currentSong?.title ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentSong?.artist ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Center zone: album art ↔ lyrics panel (crossfade)
            AnimatedContent(
                targetState = lyricsState,
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                label = "centerZone"
            ) { lyricState ->
                when (lyricState) {
                    is LyricsState.Loaded -> {
                        SyncedLyricsPanel(
                            lines = lyricState.lines,
                            progressMs = state.progress,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is LyricsState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularWavyProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    "Generating lyrics…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    is LyricsState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(SurfaceLevel1),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    lyricState.message,
                                    color = Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.onIntent(SongDetailIntent.RetryLyrics) }) {
                                    Text("Retry", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    LyricsState.Idle -> {
                        val mainArtModel = currentSong?.localAlbumArtPath?.let { java.io.File(it) } ?: currentSong?.imageUrl
                    if (mainArtModel != null) {
                            AsyncImage(
                                model = mainArtModel,
                                contentDescription = "Album Art",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(SurfaceLevel1),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Song info (title + artist) — hidden in lyrics mode (replaced by compact header)
            AnimatedVisibility(
                visible = !showLyricsMode,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong?.title ?: "No Song Playing",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = currentSong?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 20.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Seekbar
            if (!isMissingLocal && !isExternal) {
                var seekbarSize by remember { mutableStateOf(IntSize.Zero) }
                val currentProgress = if (state.duration > 0) state.progress.toFloat() / state.duration else 0f
                val displayProgress = if (isDragging) dragProgress else currentProgress

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .onGloballyPositioned { seekbarSize = it.size }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val newProgress = (offset.x / seekbarSize.width).coerceIn(0f, 1f)
                                        viewModel.onIntent(SongDetailIntent.SeekTo((newProgress * state.duration).toLong()))
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isDragging = true
                                        dragProgress = (offset.x / seekbarSize.width).coerceIn(0f, 1f)
                                    },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        dragProgress = (change.position.x / seekbarSize.width).coerceIn(0f, 1f)
                                        viewModel.onIntent(SongDetailIntent.SeekTo((dragProgress * state.duration).toLong()))
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        LinearWavyProgressIndicator(
                            progress = { displayProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            amplitude = { 1f },
                            wavelength = 20.dp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayMs = if (isDragging) (dragProgress * state.duration).toLong() else state.progress
                        Text(formatTime(displayMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        Text(formatTime(state.duration), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MorphingControl(
                        onClick = { viewModel.onIntent(SongDetailIntent.SkipPrevious) },
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        enabled = state.hasPrevious,
                        size = 64.dp,
                        iconSize = 36.dp,
                        baseShape = MaterialShapes.Diamond,
                        morphShape = MaterialShapes.Circle
                    )

                    if (isMissingLocal || isExternal) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconButton(
                                onClick = {
                                    val spotifyUri = "spotify:search:${currentSong?.title} ${currentSong?.artist}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)))
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFF1DB954), CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(id = com.fardeenkhan.moodtune.core.ui.R.drawable.spotify_logo),
                                    contentDescription = "Spotify",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    currentSong?.externalUrl?.let {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                                    }
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color.White, CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(id = com.fardeenkhan.moodtune.core.ui.R.drawable.youtube_logo),
                                    contentDescription = "YouTube",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        MorphingControl(
                            onClick = { viewModel.onIntent(SongDetailIntent.TogglePlayPause) },
                            icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            size = 88.dp,
                            iconSize = 48.dp,
                            baseShape = MaterialShapes.Cookie7Sided,
                            morphShape = MaterialShapes.PuffyDiamond
                        )
                    }

                    MorphingControl(
                        onClick = { viewModel.onIntent(SongDetailIntent.SkipNext) },
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        enabled = state.hasNext,
                        size = 64.dp,
                        iconSize = 36.dp,
                        baseShape = MaterialShapes.Diamond,
                        morphShape = MaterialShapes.Circle
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // About this song section
            currentSong?.let { AboutThisSongSection(it) }

            Spacer(modifier = Modifier.height(32.dp))

            // YouTube Button
            if (currentSong?.externalUrl != null) {
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentSong.externalUrl)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = com.fardeenkhan.moodtune.core.ui.R.drawable.youtube_logo),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Play on YouTube",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Loading Overlay
        if (state.isLoading && !isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun AboutThisSongSection(song: Song) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLevel1)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "About this song",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = song.explanation.about,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = song.explanation.mood,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Context: ${song.explanation.context}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SongStatCard("ENERGY", song.energy ?: "Medium", Modifier.weight(1f))
                    SongStatCard("MOOD", song.mood ?: "Vibe", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SongStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(60.dp)
            .background(SurfaceLevel2, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
    }
}

@Preview
@Composable
fun NowPlayingScreenPreview() {
    MoodTuneTheme {
        NowPlayingScreen(
            viewModel = koinViewModel(),
            onMinimize = {}
        )
    }
}
