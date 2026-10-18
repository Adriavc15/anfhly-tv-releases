package com.anfhly.streaming.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.Episode
import com.anfhly.streaming.data.model.Season
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.ui.theme.AnfhlyColors
import com.anfhly.streaming.ui.viewmodel.ContentDetailViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonsScreen(
    series: Content.Series,
    onNavigateBack: () -> Unit,
    onPlayEpisode: (Content.Series, Season, Episode, List<Episode>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ContentDetailViewModel = viewModel()

    // Initialize repository and load content details sequentially
    LaunchedEffect(series.id) {
        try {
            // Initialize repository first
            viewModel.initWatchProgressRepository(context)

            // Then load content details
            viewModel.loadContentDetails(series)
        } catch (e: Exception) {
            android.util.Log.e("SeasonsScreen", "Error initializing or loading content", e)
        }
    }
    
    val contentDetail by viewModel.contentDetail.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    
    val currentSeries = contentDetail as? Content.Series ?: series
    val focusRequester = remember { FocusRequester() }
    
    // Animation states
    var isVisible by remember { mutableStateOf(false) }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "fade"
    )
    val animatedOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 50f,
        animationSpec = tween(durationMillis = 500),
        label = "slide"
    )

    // Request focus on first season when screen opens
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    onNavigateBack()
                    true
                } else {
                    false
                }
            }
    ) {
        // App background image (fallback)
        AppBackground()
        
        // Background - series backdrop full screen with fade animation
        AsyncImage(
            model = currentSeries.backdropUrl ?: currentSeries.posterUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay - full screen darkening
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black
                        )
                    )
                )
        )

        // Content with animation
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp)
                .graphicsLayer {
                    alpha = animatedAlpha
                    translationY = animatedOffset
                }
        ) {
            // Top bar with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.tv.material3.Surface(
                    onClick = onNavigateBack,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        focusedContainerColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(onNavigateBack) {
                            detectTapGestures {
                                onNavigateBack()
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = currentSeries.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Seasons selector
            AnimatedVisibility(
                visible = seasons.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(600)) + slideInHorizontally { -it / 3 }
            ) {
                Column {
                    Text(
                        text = "Selecciona una temporada",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        items(
                            items = seasons,
                            key = { it.id }
                        ) { season ->
                            val isSelected = selectedSeason?.id == season.id
                            SeasonTabCard(
                                season = season,
                                isSelected = isSelected,
                                focusRequester = if (seasons.firstOrNull()?.id == season.id) focusRequester else null,
                                onClick = { viewModel.selectSeason(season) }
                            )
                        }
                    }
                }
            }

            // Episodes grid - 16:9 cards with animations
            AnimatedVisibility(
                visible = episodes.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(700)) + slideInVertically { it / 2 }
            ) {
                Column {
                    Text(
                        text = "${selectedSeason?.name ?: "Episodios"} - ${episodes.size} episodios",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = episodes,
                            key = { it.id }
                        ) { episode ->
                            EpisodeItemWithAnimation(
                                episode = episode,
                                fallbackPosterUrl = currentSeries.posterUrl,
                                onClick = {
                                    selectedSeason?.let { season ->
                                        onPlayEpisode(currentSeries, season, episode, episodes)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonTabCard(
    season: Season,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    // Quest 3 hover-to-focus
    val seasonInteraction = remember { MutableInteractionSource() }
    val internalFocusRequester = remember { focusRequester ?: FocusRequester() }
    val isSeasonHovered by seasonInteraction.collectIsHoveredAsState()
    LaunchedEffect(isSeasonHovered) {
        if (isSeasonHovered) {
            try { internalFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    val modifier = Modifier.focusRequester(internalFocusRequester)

    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier
            .width(180.dp)
            .hoverable(seasonInteraction)
            .onFocusChanged { isFocused = it.isFocused }
            .pointerInput(onClick) {
                detectTapGestures {
                    onClick()
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = season.name,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isFocused) Color.Black else if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard16x9(
    episode: Episode,
    fallbackPosterUrl: String?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    // Quest 3 hover-to-focus
    val episodeFocusRequester = remember { FocusRequester() }
    val episodeInteraction = remember { MutableInteractionSource() }
    val isEpisodeHovered by episodeInteraction.collectIsHoveredAsState()
    LaunchedEffect(isEpisodeHovered) {
        if (isEpisodeHovered) {
            try { episodeFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    // 16:9 aspect ratio - common width 320dp, height 180dp
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .width(320.dp)
            .height(180.dp)
            .focusRequester(episodeFocusRequester)
            .hoverable(episodeInteraction)
            .onFocusChanged { isFocused = it.isFocused }
            .pointerInput(onClick) {
                detectTapGestures {
                    onClick()
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White))
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        ) {
            // Episode still image (16:9)
            AsyncImage(
                model = episode.stillUrl ?: fallbackPosterUrl,
                contentDescription = episode.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Episode number badge
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${episode.episodeNumber}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Episode info at bottom - only runtime (name is now outside)
            if (episode.runtime != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "${episode.runtime} min",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Play icon overlay when focused
            if (isFocused) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeItemWithAnimation(
    episode: Episode,
    fallbackPosterUrl: String?,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(episode.id) {
        delay(50)
        isVisible = true
    }
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "alpha"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(320.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
            }
    ) {
        EpisodeCard16x9(
            episode = episode,
            fallbackPosterUrl = fallbackPosterUrl,
            onClick = onClick
        )
        
        // Episode name below the card
        Text(
            text = episode.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp)
                .fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
