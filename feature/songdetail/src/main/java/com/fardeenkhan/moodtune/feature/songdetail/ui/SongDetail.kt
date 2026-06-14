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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.fardeenkhan.moodtune.domain.model.Song
import java.io.File
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.PI

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
                    Log.d("TAG","clicked invoked")
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
                        // Animate alpha based on morph progress to support return animation
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

    // Force navigate to Detail pane so it shows the player immediately on Compact screens
    LaunchedEffect(Unit) {
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    // Handle back navigation within the Two-Pane scaffold (e.g., from Player to Queue on phone)
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
                    // Switch to Player pane if in single-pane mode
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
        
        androidx.compose.foundation.lazy.LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        if (song.imageUrl != null) {
                            AsyncImage(
                                model = song.imageUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
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
    
    val currentSong = state.currentSong
    val isExternal = currentSong?.externalUrl?.startsWith("http") == true
    val fileExists = currentSong?.externalUrl?.let { if (!it.startsWith("http")) File(it).exists() else false } ?: false
    val isMissingLocal = !isExternal && !fileExists && currentSong != null

    // Local states for smooth seeking
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }


    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Album Art
            if (currentSong?.imageUrl != null) {
                AsyncImage(
                    model = currentSong.imageUrl,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
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

            Spacer(modifier = Modifier.height(48.dp))

            // Song Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "No Song Playing",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = currentSong?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 20.sp
                        )
                    )
                }
            }


            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            Box( modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center){

                // Glassy Effect
                albumArtColor?.let { color ->
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .graphicsLayer {
                                alpha = 0.8f
                            }
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(color, Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                )
                {
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
                        // Show Spotify & YouTube buttons instead of Play/Pause
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconButton(
                                onClick = {
                                    val spotifyUri = "spotify:search:${currentSong?.title} ${currentSong?.artist}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)))
                                },
                                modifier = Modifier.size(64.dp).background(Color(0xFF1DB954), CircleShape)
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
                                modifier = Modifier.size(64.dp).background(Color.White, CircleShape)
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
                        var boxSize by remember { mutableStateOf(IntSize.Zero) }
                        val currentProgress = if (state.duration > 0) state.progress.toFloat() / state.duration else 0f
                        val displayProgress = if (isDragging) dragProgress else currentProgress

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(160.dp)
                        ) {


                            // Seeking Container (Behind the button)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { boxSize = it.size }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { offset ->
                                                val newProgress = calculateProgressFromOffset(offset, boxSize)
                                                viewModel.onIntent(SongDetailIntent.SeekTo((newProgress * state.duration).toLong()))
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                isDragging = true
                                                dragProgress = calculateProgressFromOffset(offset, boxSize)
                                            },
                                            onDragEnd = { isDragging = false },
                                            onDragCancel = { isDragging = false },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                dragProgress = calculateProgressFromOffset(change.position, boxSize)
                                                viewModel.onIntent(SongDetailIntent.SeekTo((dragProgress * state.duration).toLong()))
                                            }
                                        )
                                    }
                            ) {
                                CircularWavyProgressIndicator(
                                    progress = { displayProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    amplitude = { 1f },
                                    wavelength = 20.dp
                                )
                            }

                            // Play/Pause Button (Top Layer, Independent)
                            Box(contentAlignment = Alignment.Center) {


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
                        }
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

            Spacer(modifier = Modifier.height(16.dp))
            
            // Times
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val displayMs = if (isDragging) (dragProgress * state.duration).toLong() else state.progress
                Text(formatTime(displayMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(state.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(48.dp))

            // About this song section
            currentSong?.let { AboutThisSongSection(it) }

            Spacer(modifier = Modifier.height(32.dp))

            // YouTube Button (Full width)
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
                        Text("Play on YouTube", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.Black)
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

private fun calculateProgressFromOffset(offset: androidx.compose.ui.geometry.Offset, size: IntSize): Float {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val angle = atan2(offset.y - centerY, offset.x - centerX)
    // Adjust angle to match progress indicator (starting from top, clockwise)
    var normalizedAngle = (angle * 180 / PI).toFloat() + 90f
    if (normalizedAngle < 0) normalizedAngle += 360f
    return (normalizedAngle / 360f).coerceIn(0f, 1f)
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
            
            // Stats Grid
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
