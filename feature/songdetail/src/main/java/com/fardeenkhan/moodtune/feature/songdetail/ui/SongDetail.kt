package com.fardeenkhan.moodtune.feature.songdetail.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import com.fardeenkhan.moodtune.core.ui.theme.MoodTuneTheme
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel1
import com.fardeenkhan.moodtune.core.ui.theme.SurfaceLevel2
import com.fardeenkhan.moodtune.core.ui.util.albumArtModel
import com.fardeenkhan.moodtune.core.utils.PlaybackState
import com.fardeenkhan.moodtune.domain.model.LyricLine
import com.fardeenkhan.moodtune.domain.model.Song
import java.io.File
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                        val queueArtModel = song.albumArtModel()
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val activeIndex by remember(progressMs, lines) {
        derivedStateOf { lines.indexOfLast { it.ms <= progressMs }.coerceAtLeast(0) }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem(activeIndex, scrollOffset = if (compact) -30 else -200)
    }

    val lineStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
    val verticalContentPadding = if (compact) 32.dp else 80.dp
    val linePadding = if (compact) PaddingValues(vertical = 4.dp, horizontal = 12.dp) else PaddingValues(vertical = 10.dp, horizontal = 16.dp)

    Box(modifier = modifier.clip(RoundedCornerShape(24.dp))) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = verticalContentPadding),
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
                    style = lineStyle,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(scaleX = lineScale, scaleY = lineScale)
                        .padding(linePadding)
                )
            }
        }
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
    val backdropArtModel = currentSong.albumArtModel()

    val showLyricsMode = lyricsState is LyricsState.Loaded || lyricsState is LyricsState.Loading

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = albumArtColor ?: Color.Black,
        animationSpec = tween(durationMillis = 1000),
        label = "backgroundColor"
    )

    // A window shorter than the MEDIUM height breakpoint (480dp) is the reliable,
    // orientation-agnostic signal for a phone-in-landscape (or split-screen) window — unlike
    // Configuration.orientation it stays correct across foldables and multi-window.
    val isCompactHeight = !currentWindowAdaptiveInfo().windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Blurred album art backdrop — real photo behind the glass, not just a flat color
        if (backdropArtModel != null) {
            AsyncImage(
                model = backdropArtModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp),
                contentScale = ContentScale.Crop
            )
        }

        // Dominant-color tint, kept for cohesive theming over the blurred photo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(animatedBackgroundColor.copy(alpha = 0.2f))
        )

        // Dimming overlay — just enough for text/control contrast without burying the photo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
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

        if (isCompactHeight) {
            CompactHeightNowPlayingContent(
                viewModel = viewModel,
                state = state,
                scrollState = scrollState,
                context = context,
                lyricsState = lyricsState,
                showLyricsMode = showLyricsMode,
                currentSong = currentSong,
                isExternal = isExternal,
                isMissingLocal = isMissingLocal,
                isDragging = isDragging,
                onDraggingChange = { isDragging = it },
                dragProgress = dragProgress,
                onDragProgressChange = { dragProgress = it },
                onMinimize = onMinimize
            )
        } else {
            PortraitNowPlayingContent(
                viewModel = viewModel,
                state = state,
                scrollState = scrollState,
                context = context,
                lyricsState = lyricsState,
                showLyricsMode = showLyricsMode,
                currentSong = currentSong,
                isExternal = isExternal,
                isMissingLocal = isMissingLocal,
                isDragging = isDragging,
                onDraggingChange = { isDragging = it },
                dragProgress = dragProgress,
                onDragProgressChange = { dragProgress = it },
                onMinimize = onMinimize
            )
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

/**
 * Confirms the exact song title and artist before requesting AI lyrics generation.
 * Gemini can occasionally hallucinate lyrics for a different song when the title alone
 * is ambiguous (thousands of songs share the same name), so the user gets a chance to
 * correct it here first.
 */
@Composable
private fun LyricsConfirmDialog(
    initialTitle: String,
    initialArtist: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, artist: String, movie: String?) -> Unit
) {
    var titleInput by remember { mutableStateOf(initialTitle) }
    var artistInput by remember { mutableStateOf(initialArtist) }
    var movieInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Song Details") },
        text = {
            Column {
                Text(
                    text = "AI lyrics generation can mix up songs that share a title. Confirm the exact song and artist first.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Song title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = artistInput,
                    onValueChange = { artistInput = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = movieInput,
                    onValueChange = { movieInput = it },
                    label = { Text("Movie (optional)") },
                    singleLine = true,
                    supportingText = {
                        Text("Giving the movie name can increase AI accuracy for generating the right lyrics.")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(titleInput.trim(), artistInput.trim(), movieInput.trim().ifBlank { null }) },
                enabled = titleInput.isNotBlank() && artistInput.isNotBlank()
            ) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Portrait / default layout: full-size album art, large title, generous spacing —
 * unchanged from the original single-column NowPlayingScreen body.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PortraitNowPlayingContent(
    viewModel: SongDetailViewModel,
    state: PlaybackState,
    scrollState: ScrollState,
    context: Context,
    lyricsState: LyricsState,
    showLyricsMode: Boolean,
    currentSong: Song?,
    isExternal: Boolean,
    isMissingLocal: Boolean,
    isDragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    dragProgress: Float,
    onDragProgressChange: (Float) -> Unit,
    onMinimize: () -> Unit
) {
    var showLyricsConfirmDialog by remember { mutableStateOf(false) }
    val hasCachedLyrics by viewModel.hasCachedLyrics.collectAsState()

    if (showLyricsConfirmDialog) {
        LyricsConfirmDialog(
            initialTitle = currentSong?.title.orEmpty(),
            initialArtist = currentSong?.artist.orEmpty(),
            onDismiss = { showLyricsConfirmDialog = false },
            onConfirm = { title, artist, movie ->
                showLyricsConfirmDialog = false
                viewModel.onIntent(SongDetailIntent.RequestLyrics(title, artist, movie))
            }
        )
    }

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
                    } else if (hasCachedLyrics && currentSong != null) {
                        // Lyrics already generated and saved — just show them, no need to
                        // re-confirm song details.
                        viewModel.onIntent(SongDetailIntent.RequestLyrics(currentSong.title, currentSong.artist))
                    } else {
                        showLyricsConfirmDialog = true
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
                val headerArtModel = currentSong.albumArtModel()
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
                    val mainArtModel = currentSong.albumArtModel()
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (mainArtModel != null) {
                            AsyncImage(
                                model = mainArtModel,
                                contentDescription = "Album Art",
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    // Fade the bottom edge to transparent so the blurred album
                                    // art backdrop behind it shows through — a seamless "melt"
                                    // into the glass instead of a hard rectangular cutoff.
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color.Black, Color.Transparent),
                                                startY = size.height * 0.65f,
                                                endY = size.height
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
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
                                    onDraggingChange(true)
                                    onDragProgressChange((offset.x / seekbarSize.width).coerceIn(0f, 1f))
                                },
                                onDragEnd = { onDraggingChange(false) },
                                onDragCancel = { onDraggingChange(false) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val newDragProgress = (change.position.x / seekbarSize.width).coerceIn(0f, 1f)
                                    onDragProgressChange(newDragProgress)
                                    viewModel.onIntent(SongDetailIntent.SeekTo((newDragProgress * state.duration).toLong()))
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
}

/**
 * Landscape / compact-height layout: shrinks the album art and drops it, the seekbar, and the
 * transport controls into a tight block right under the top bar so playback is reachable without
 * scrolling. About/stats/YouTube stay below, reachable by scrolling — same as portrait.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompactHeightNowPlayingContent(
    viewModel: SongDetailViewModel,
    state: PlaybackState,
    scrollState: ScrollState,
    context: Context,
    lyricsState: LyricsState,
    showLyricsMode: Boolean,
    currentSong: Song?,
    isExternal: Boolean,
    isMissingLocal: Boolean,
    isDragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    dragProgress: Float,
    onDragProgressChange: (Float) -> Unit,
    onMinimize: () -> Unit
) {
    val artSize = 72.dp
    val compactHeaderArtSize = 22.dp
    val primaryControlSize = 56.dp
    val primaryControlIconSize = 28.dp
    val sideControlSize = 44.dp
    val sideControlIconSize = 22.dp

    var showLyricsConfirmDialog by remember { mutableStateOf(false) }
    val hasCachedLyrics by viewModel.hasCachedLyrics.collectAsState()

    if (showLyricsConfirmDialog) {
        LyricsConfirmDialog(
            initialTitle = currentSong?.title.orEmpty(),
            initialArtist = currentSong?.artist.orEmpty(),
            onDismiss = { showLyricsConfirmDialog = false },
            onConfirm = { title, artist, movie ->
                showLyricsConfirmDialog = false
                viewModel.onIntent(SongDetailIntent.RequestLyrics(title, artist, movie))
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 8.dp),
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
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            IconButton(
                onClick = {
                    if (showLyricsMode) {
                        viewModel.onIntent(SongDetailIntent.DismissLyrics)
                    } else if (hasCachedLyrics && currentSong != null) {
                        viewModel.onIntent(SongDetailIntent.RequestLyrics(currentSong.title, currentSong.artist))
                    } else {
                        showLyricsConfirmDialog = true
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

        Spacer(modifier = Modifier.height(4.dp))

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
                    .padding(bottom = 8.dp)
            ) {
                val headerArtModel = currentSong.albumArtModel()
                if (headerArtModel != null) {
                    AsyncImage(
                        model = headerArtModel,
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .size(compactHeaderArtSize)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(compactHeaderArtSize)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceLevel1),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(0.5f))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = currentSong?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Center zone: small album art ↔ compact lyrics panel (crossfade)
        AnimatedContent(
            targetState = lyricsState,
            transitionSpec = {
                fadeIn(tween(400)) togetherWith fadeOut(tween(400))
            },
            modifier = if (showLyricsMode) {
                Modifier
                    .fillMaxWidth()
                    .height(artSize * 1.6f)
            } else {
                Modifier.size(artSize)
            },
            label = "centerZoneCompact"
        ) { lyricState ->
            when (lyricState) {
                is LyricsState.Loaded -> {
                    SyncedLyricsPanel(
                        lines = lyricState.lines,
                        progressMs = state.progress,
                        modifier = Modifier.fillMaxSize(),
                        compact = true
                    )
                }

                is LyricsState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                is LyricsState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceLevel1),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.onIntent(SongDetailIntent.RetryLyrics) }) {
                                Text("Retry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                LyricsState.Idle -> {
                    val mainArtModel = currentSong.albumArtModel()
                    if (mainArtModel != null) {
                        AsyncImage(
                            model = mainArtModel,
                            contentDescription = "Album Art",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceLevel1),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Song info (title + artist) — hidden in lyrics mode (replaced by compact header)
        AnimatedVisibility(
            visible = !showLyricsMode,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentSong?.title ?: "No Song Playing",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .basicMarquee()
                )
                Text(
                    text = currentSong?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Seekbar
        if (!isMissingLocal && !isExternal) {
            var seekbarSize by remember { mutableStateOf(IntSize.Zero) }
            val currentProgress = if (state.duration > 0) state.progress.toFloat() / state.duration else 0f
            val displayProgress = if (isDragging) dragProgress else currentProgress

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
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
                                    onDraggingChange(true)
                                    onDragProgressChange((offset.x / seekbarSize.width).coerceIn(0f, 1f))
                                },
                                onDragEnd = { onDraggingChange(false) },
                                onDragCancel = { onDraggingChange(false) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val newDragProgress = (change.position.x / seekbarSize.width).coerceIn(0f, 1f)
                                    onDragProgressChange(newDragProgress)
                                    viewModel.onIntent(SongDetailIntent.SeekTo((newDragProgress * state.duration).toLong()))
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    LinearWavyProgressIndicator(
                        progress = { displayProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        amplitude = { 1f },
                        wavelength = 14.dp
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

        Spacer(modifier = Modifier.height(8.dp))

        // Controls
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 280.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MorphingControl(
                    onClick = { viewModel.onIntent(SongDetailIntent.SkipPrevious) },
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    enabled = state.hasPrevious,
                    size = sideControlSize,
                    iconSize = sideControlIconSize,
                    baseShape = MaterialShapes.Diamond,
                    morphShape = MaterialShapes.Circle
                )

                if (isMissingLocal || isExternal) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = {
                                val spotifyUri = "spotify:search:${currentSong?.title} ${currentSong?.artist}"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)))
                            },
                            modifier = Modifier
                                .size(sideControlSize)
                                .background(Color(0xFF1DB954), CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = com.fardeenkhan.moodtune.core.ui.R.drawable.spotify_logo),
                                contentDescription = "Spotify",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(sideControlIconSize)
                            )
                        }
                        IconButton(
                            onClick = {
                                currentSong?.externalUrl?.let {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                                }
                            },
                            modifier = Modifier
                                .size(sideControlSize)
                                .background(Color.White, CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = com.fardeenkhan.moodtune.core.ui.R.drawable.youtube_logo),
                                contentDescription = "YouTube",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(sideControlIconSize)
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
                        size = primaryControlSize,
                        iconSize = primaryControlIconSize,
                        baseShape = MaterialShapes.Cookie7Sided,
                        morphShape = MaterialShapes.PuffyDiamond
                    )
                }

                MorphingControl(
                    onClick = { viewModel.onIntent(SongDetailIntent.SkipNext) },
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    enabled = state.hasNext,
                    size = sideControlSize,
                    iconSize = sideControlIconSize,
                    baseShape = MaterialShapes.Diamond,
                    morphShape = MaterialShapes.Circle
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About this song section — full-size, reachable by scrolling
        currentSong?.let { AboutThisSongSection(it) }

        Spacer(modifier = Modifier.height(16.dp))

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
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun AboutThisSongSection(song: Song, compact: Boolean = false) {
    val cardPadding = if (compact) 14.dp else 24.dp
    val titleStyle = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge
    val aboutStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val moodStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 16.dp else 24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLevel1)
    ) {
        Column(modifier = Modifier.padding(cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 16.dp else 20.dp)
                )
                Spacer(modifier = Modifier.width(if (compact) 8.dp else 12.dp))
                Text(
                    text = "About this song",
                    style = titleStyle.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))

            Text(
                text = song.explanation.about,
                style = aboutStyle.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
            Text(
                text = song.explanation.mood,
                style = moodStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!compact) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Context: ${song.explanation.context}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 24.dp))

            SongStatCard("MOOD", song.mood ?: "Vibe", Modifier.fillMaxWidth(), compact = compact)
        }
    }
}

@Composable
fun SongStatCard(label: String, value: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier = modifier
            .height(if (compact) 44.dp else 60.dp)
            .background(SurfaceLevel2, RoundedCornerShape(12.dp))
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 4.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
            Text(
                value,
                style = (if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium).copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
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

@Preview(widthDp = 800, heightDp = 400)
@Composable
fun NowPlayingScreenCompactHeightPreview() {
    MoodTuneTheme {
        NowPlayingScreen(
            viewModel = koinViewModel(),
            onMinimize = {}
        )
    }
}
