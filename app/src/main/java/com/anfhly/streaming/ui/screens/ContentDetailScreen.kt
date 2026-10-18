package com.anfhly.streaming.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.ui.viewmodel.ContentDetailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import com.anfhly.streaming.data.model.Season
import com.anfhly.streaming.data.model.Episode

import android.util.Log
import com.anfhly.streaming.R

/**
 * Función helper para formatear fechas en español
 * Maneja múltiples formatos de entrada y muestra formato legible
 */
private fun formatDateSpanish(dateString: String?): String {
    if (dateString.isNullOrBlank()) return "Fecha no disponible"
    
    // Si solo tiene el año, devolver el año
    if (dateString.length == 4 && dateString.all { it.isDigit() }) {
        return dateString
    }
    
    // Intentar diferentes formatos de entrada
    val inputFormats = listOf(
        "yyyy-MM-dd",    // Formato estándar TMDB
        "dd/MM/yyyy",    // Formato europeo
        "MM/dd/yyyy",    // Formato americano
        "yyyy/MM/dd",    // Otro formato común
        "dd-MM-yyyy"     // Formato con guiones
    )
    
    val outputFormatter = java.text.SimpleDateFormat("d 'de' MMMM 'de' yyyy", java.util.Locale("es", "ES"))
    
    for (format in inputFormats) {
        try {
            val inputFormatter = java.text.SimpleDateFormat(format, java.util.Locale.getDefault())
            val date = inputFormatter.parse(dateString)
            if (date != null) {
                return outputFormatter.format(date)
            }
        } catch (e: Exception) {
            // Continuar con el siguiente formato
        }
    }
    
    // Si no se puede parsear, intentar extraer al menos el año
    val year = dateString.take(4).filter { it.isDigit() }
    return if (year.length == 4) year else "Fecha no disponible"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContentDetailScreen(
    content: Content,
    similarContent: List<Content> = emptyList(),
    onNavigateBack: () -> Unit,
    onPlayContent: (Content, Long, String?) -> Unit,
    onNavigateToSeasons: (Content.Series) -> Unit,
    onContinueSeries: (Content.Series, Int, Int, Long) -> Unit = { _, _, _, _ -> },
    onSimilarContentSelected: (Content) -> Unit,
    onRandomContentSelected: (Content) -> Unit = {},
    onNavigateToCast: (Content) -> Unit = {},
    onViewTrailer: (String) -> Unit = {},
    onPlayEpisode: (Content.Series, Season, Episode, List<Episode>, Long) -> Unit = { _, _, _, _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ContentDetailViewModel = viewModel()
    
    val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    
    val settingsDataStore = remember { com.anfhly.streaming.data.local.SettingsDataStore(context) }
    val profile by settingsDataStore.selectedProfile.collectAsStateWithLifecycle(initialValue = null)
    val profileId = profile?.id ?: ""

    // Validar que el content sea válido
    if (content.id.isEmpty() || content.id == "error" || content.title.isEmpty()) {
        android.util.Log.e("ContentDetailScreen", "Invalid content received: id=${content.id}, title=${content.title}")
        // Mostrar pantalla de error en lugar de hacer crash
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Error al cargar contenido",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = Color.Red
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNavigateBack
                ) {
                    Text("Volver")
                }
            }
        }
        return
    }

    android.util.Log.d("ContentDetailScreen", "Loading details for: ${content.title} (${content::class.simpleName})")

    // Initialize watch progress repository and load content details sequentially
    LaunchedEffect(content.id) {
        Log.d("ContentDetailScreen", "Loading content details for: ${content.title} (${content::class.simpleName})")

        try {
            // Initialize repository first
            viewModel.initWatchProgressRepository(context)

            // Limpiar estado anterior del ViewModel para evitar conflicto
            viewModel.clearContentState()

            // Then load content details
            viewModel.loadContentDetails(content)
            
            // Check watchlist status
            if (userId.isNotEmpty() && profileId.isNotEmpty()) {
                viewModel.checkWatchlist(userId, profileId, content.id)
            }
        } catch (e: Exception) {
            Log.e("ContentDetailScreen", "Error initializing or loading content", e)
        }
    }

    // Refresh watch progress when screen resumes (e.g., returning from player)
    Log.d("ContentDetailScreen", "ON_RESUME refresh disabled for debugging")

    val contentDetail by viewModel.contentDetail.collectAsStateWithLifecycle()
    val rawHasWatchProgress by viewModel.hasWatchProgress.collectAsStateWithLifecycle()
    val rawWatchProgress by viewModel.watchProgress.collectAsStateWithLifecycle()
    val isInWatchlist by viewModel.isInWatchlist.collectAsStateWithLifecycle()
    
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    
    val currentContent = contentDetail ?: content
    val isSeries = currentContent is Content.Series
    val movie = currentContent as? Content.Movie
    val isLive = !isSeries && movie != null && movie.releaseDate.isNullOrBlank()
    
    val hasWatchProgress = if (isLive) false else rawHasWatchProgress
    val watchProgress = if (isLive) null else rawWatchProgress
    
    val hasVersions = !isSeries && movie != null && (movie.fileUrlImax != null || movie.fileUrlOpenmatte != null || movie.fileUrl43 != null)
    val resumePosition = watchProgress?.positionMs ?: 0L
    
    // Logging para depurar qué contenido se está mostrando
    Log.d("ContentDetailScreen", "Displaying content: ${currentContent.title} (${currentContent::class.simpleName})")
    val focusRequester = remember { FocusRequester() }
    var isPlayButtonFocused by remember { mutableStateOf(true) }
    var isCastButtonFocused by remember { mutableStateOf(false) }
    var isDecorativeBackgroundMode by remember { mutableStateOf(false) }
    // Quest 3 hover-to-focus for play button
    val playInteraction = remember { MutableInteractionSource() }
    val isPlayHovered by playInteraction.collectIsHoveredAsState()
    LaunchedEffect(isPlayHovered) {
        if (isPlayHovered) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Animation states
    var isVisible by remember { mutableStateOf(false) }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = EaseOutQuart),
        label = "fade"
    )
    val animatedOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 60f,
        animationSpec = tween(durationMillis = 600, easing = EaseOutQuart),
        label = "slide"
    )

    // Infinite transition for the Ken Burns motion effect on the backdrop background
    val infiniteTransition = rememberInfiniteTransition(label = "backdropMotion")
    
    val backdropScale by infiniteTransition.animateFloat(
        initialValue = 1.18f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 35000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val backdropTranslationX by infiniteTransition.animateFloat(
        initialValue = -35f,
        targetValue = 35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 45000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translationX"
    )

    val backdropTranslationY by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 40000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translationY"
    )

    // 3D Parallax Tilt Animations (rotationX and rotationY) (intensified)
    val backdropRotationX by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotationX"
    )

    val backdropRotationY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 38000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotationY"
    )

    // Breathing Darkness Animation (creates pulsing ambient brightness)
    val breathingDarkness by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingDarkness"
    )

    // Dynamic Light Sweep Offset Animation
    val sweepOffset by infiniteTransition.animateFloat(
        initialValue = -800f,
        targetValue = 2500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepOffset"
    )

    // Active states that transition smoothly when entering/exiting decorative background mode
    val activeScale by animateFloatAsState(
        targetValue = if (isDecorativeBackgroundMode) backdropScale else 1.00f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "activeScale"
    )
    val activeTranslationX by animateFloatAsState(
        targetValue = if (isDecorativeBackgroundMode) backdropTranslationX else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "activeTranslationX"
    )
    val activeTranslationY by animateFloatAsState(
        targetValue = if (isDecorativeBackgroundMode) backdropTranslationY else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "activeTranslationY"
    )
    val activeRotationX by animateFloatAsState(
        targetValue = if (isDecorativeBackgroundMode) backdropRotationX else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "activeRotationX"
    )
    val activeRotationY by animateFloatAsState(
        targetValue = if (isDecorativeBackgroundMode) backdropRotationY else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "activeRotationY"
    )
    val activeBreathingDarkness by animateFloatAsState(
        targetValue = if (isDecorativeBackgroundMode) breathingDarkness else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "activeBreathingDarkness"
    )
    val activeAberrationAlpha by animateFloatAsState(
        targetValue = if (isDecorativeBackgroundMode) 0.20f else 0.0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "activeAberrationAlpha"
    )
    val activeSweepAlpha by animateFloatAsState(
        targetValue = if (isDecorativeBackgroundMode) 0.06f else 0.0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "activeSweepAlpha"
    )

    // Request focus on play button when screen opens or exits decorative mode
    LaunchedEffect(isDecorativeBackgroundMode) {
        if (!isDecorativeBackgroundMode) {
            delay(100)
            isVisible = true
            focusRequester.requestFocus()
        }
    }

    // Handle back button properly
    androidx.activity.compose.BackHandler {
        if (isDecorativeBackgroundMode) {
            isDecorativeBackgroundMode = false
        } else {
            onNavigateBack()
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    var heroFocusJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isHeroSectionFocused by remember { mutableStateOf(false) }
    
    var seasonsFocusJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isSeasonsSectionFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // App background image (fallback)
        AppBackground()
        // Full screen background backdrop with fade animation and 3D motion
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = animatedAlpha
                    scaleX = activeScale
                    scaleY = activeScale
                    translationX = activeTranslationX
                    translationY = activeTranslationY
                    rotationX = activeRotationX
                    rotationY = activeRotationY
                    cameraDistance = 6f * density.density
                    clip = true
                }
        ) {
            // Opaque base backdrop image (ensures the backdrop is sharp and complete)
            AsyncImage(
                model = currentContent.backdropUrl ?: currentContent.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Red channel aberration bleed (shifted left, active opacity)
            if (activeAberrationAlpha > 0.01f) {
                AsyncImage(
                    model = currentContent.backdropUrl ?: currentContent.posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -1.5f - (backdropTranslationX * 0.03f)
                            alpha = activeAberrationAlpha
                        },
                    contentScale = ContentScale.Crop,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                        androidx.compose.ui.graphics.ColorMatrix(
                            floatArrayOf(
                                1f, 0f, 0f, 0f, 0f,
                                0f, 0f, 0f, 0f, 0f,
                                0f, 0f, 0f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                )
            }

            // Cyan channel aberration bleed (shifted right, active opacity)
            if (activeAberrationAlpha > 0.01f) {
                AsyncImage(
                    model = currentContent.backdropUrl ?: currentContent.posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = 1.5f + (backdropTranslationX * 0.03f)
                            alpha = activeAberrationAlpha
                        },
                    contentScale = ContentScale.Crop,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                        androidx.compose.ui.graphics.ColorMatrix(
                            floatArrayOf(
                                0f, 0f, 0f, 0f, 0f,
                                0f, 1f, 0f, 0f, 0f,
                                0f, 0f, 1f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                )
            }

            // 1. Ambient Breathing Darkness Overlay (creates a breathing/pulsing atmosphere)
            if (activeBreathingDarkness > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = activeBreathingDarkness }
                        .background(Color.Black)
                )
            }

            // 2. Dynamic Ambient Light Sweep Overlay
            if (activeSweepAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // Dynamic Ambient Light Sweep
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.0f),
                                        Color.White.copy(alpha = activeSweepAlpha),
                                        Color.White.copy(alpha = 0.0f)
                                    ),
                                    start = androidx.compose.ui.geometry.Offset(sweepOffset, 0f),
                                    end = androidx.compose.ui.geometry.Offset(sweepOffset + 500f, size.height)
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                            )
                        }
                )
            }
        }

        if (!isDecorativeBackgroundMode) {
            // Light gradient overlay from left to right (darker on left for content visibility)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = animatedAlpha }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f), // 70% negro en la izquierda (donde está el contenido)
                                Color.Black.copy(alpha = 0.5f), // 50% negro en el centro-izquierda
                                Color.Black.copy(alpha = 0.3f), // 30% negro en el centro-derecha
                                Color.Black.copy(alpha = 0.1f), // 10% negro en la derecha (similar content)
                                Color.Transparent               // Transparente al final derecho
                            ),
                            startX = 0f,
                            endX = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Vertical gradient overlay (darker at the bottom to transition to black for seasons/episodes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = animatedAlpha }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black
                            )
                        )
                    )
            )

            // Main content - Scrollable LazyColumn
            LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = animatedAlpha
                    translationY = animatedOffset
                }
        ) {
            item {
                // Hero Section taking wrapped content height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 64.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                heroFocusJob?.cancel()
                                if (!isHeroSectionFocused) {
                                    isHeroSectionFocused = true
                                    coroutineScope.launch {
                                        delay(100)
                                        listState.animateScrollToItem(0)
                                    }
                                }
                            } else {
                                heroFocusJob?.cancel()
                                heroFocusJob = coroutineScope.launch {
                                    delay(150)
                                    isHeroSectionFocused = false
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left column - content info (60% width)
                        Column(
                            modifier = Modifier
                                .weight(0.6f)
                                .wrapContentHeight(),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Spacer(modifier = Modifier.height(40.dp))

                            // Logo or Title - centered within description margin
                            val logoImage = currentContent.logoUrl
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 350.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (logoImage != null) {
                                    AsyncImage(
                                        model = logoImage,
                                        contentDescription = currentContent.title,
                                        modifier = Modifier
                                            .width(350.dp)
                                            .heightIn(max = 120.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(
                                        text = currentContent.title,
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Overview - justified text
                            if (currentContent.overview.isNotBlank()) {
                                Text(
                                    text = currentContent.overview,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Justify,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 350.dp),
                                    lineHeight = 20.sp
                                )
                            } else {
                                Text(
                                    text = "Sin descripción disponible",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Justify
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Tags - certificación, rating, año, géneros - centered
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 350.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Certificación (PG-13, G, etc.) - siempre mostrar
                                    val cert = (currentContent as? Content.Movie)?.certification ?: (currentContent as? Content.Series)?.certification
                                    val displayCert = if (!cert.isNullOrBlank()) cert else "TV-MA"
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = displayCert,
                                            fontSize = 10.sp,
                                            color = Color.White
                                        )
                                    }

                                    // Fecha de lanzamiento completa
                                    val releaseDate = when (currentContent) {
                                        is Content.Movie -> formatDateSpanish(currentContent.releaseDate)
                                        is Content.Series -> formatDateSpanish(currentContent.releaseDate)
                                        else -> null
                                    }
                                    releaseDate?.let { date ->
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    Color.White.copy(alpha = 0.2f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = date,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Puntuación como badge
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = String.format("%.1f", currentContent.voteAverage),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    // Géneros (solo uno)
                                    currentContent.genres.take(1).forEach { genre ->
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    Color.White.copy(alpha = 0.2f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = genre,
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons - vertical layout, Netflix style
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.widthIn(max = 350.dp)
                            ) {
                                // Play / Continue Button
                                val primaryButtonText = if (isSeries) {
                                    val series = currentContent as Content.Series
                                    if (series.seasons.isEmpty()) {
                                        "No disponible"
                                    } else if (hasWatchProgress) {
                                        val season = watchProgress?.seasonNumber ?: 1
                                        val episode = watchProgress?.episodeNumber ?: 1
                                        "Continuar viendo S${season}E${episode}"
                                    } else {
                                        "Ver primer episodio"
                                    }
                                } else {
                                    val movie = currentContent as Content.Movie
                                    if (movie.videoUrl.isNullOrBlank()) {
                                        "No disponible"
                                    } else if (isLive) {
                                        "Ver ahora"
                                    } else if (hasWatchProgress) {
                                        "Continuar viendo"
                                    } else {
                                        "Ver ${currentContent.title} (Digital)"
                                    }
                                }

                                val primaryIcon = Icons.Default.PlayArrow

                                val onPlayClick = {
                                    if (isSeries) {
                                        val series = currentContent as Content.Series
                                        if (series.seasons.isEmpty()) {
                                            android.widget.Toast.makeText(context, "Este contenido no está disponible para reproducir", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            if (hasWatchProgress && watchProgress != null) {
                                                val progress = watchProgress!!
                                                // Find season and episode in currently loaded state or model
                                                val currentSeason = selectedSeason
                                                val loadedEpisode = if (currentSeason?.seasonNumber == progress.seasonNumber) {
                                                    episodes.find { it.episodeNumber == progress.episodeNumber }
                                                } else null
                                                
                                                if (currentSeason != null && loadedEpisode != null) {
                                                    Log.d("ContentDetailScreen", "Playing continuing episode: S${progress.seasonNumber}E${progress.episodeNumber} at ${resumePosition}ms")
                                                    onPlayEpisode(series, currentSeason, loadedEpisode, episodes, resumePosition)
                                                } else {
                                                    val targetSeason = series.seasons.find { it.seasonNumber == progress.seasonNumber }
                                                    val targetEpisode = targetSeason?.episodes?.find { it.episodeNumber == progress.episodeNumber }
                                                    if (targetSeason != null && targetEpisode != null) {
                                                        onPlayEpisode(series, targetSeason, targetEpisode, targetSeason.episodes, resumePosition)
                                                    } else {
                                                        onContinueSeries(series, progress.seasonNumber ?: 1, progress.episodeNumber ?: 1, resumePosition)
                                                    }
                                                }
                                            } else {
                                                // Play first episode
                                                val firstSeason = seasons.firstOrNull() ?: selectedSeason
                                                val targetEpisodes = if (episodes.isNotEmpty()) episodes else firstSeason?.episodes ?: emptyList()
                                                val firstEpisode = targetEpisodes.firstOrNull()
                                                
                                                if (firstSeason != null && firstEpisode != null) {
                                                    onPlayEpisode(series, firstSeason, firstEpisode, targetEpisodes, 0L)
                                                } else {
                                                    android.widget.Toast.makeText(context, "Cargando episodios...", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } else {
                                        val movie = currentContent as Content.Movie
                                        if (movie.videoUrl.isNullOrBlank()) {
                                            android.widget.Toast.makeText(context, "Este contenido no está disponible para reproducir", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            onPlayContent(currentContent, if (hasWatchProgress) resumePosition else 0L, null)
                                        }
                                    }
                                }

                                androidx.tv.material3.Surface(
                                    onClick = onPlayClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        .hoverable(playInteraction)
                                        .onFocusChanged { isPlayButtonFocused = it.isFocused }
                                        .pointerInput(onPlayClick) {
                                            detectTapGestures {
                                                onPlayClick()
                                            }
                                        },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        focusedContainerColor = Color.White
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = primaryIcon,
                                            contentDescription = null,
                                            tint = if (isPlayButtonFocused) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = primaryButtonText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isPlayButtonFocused) Color.Black else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }



                                // Trailer button (if trailer URL is available)
                                val trailerUrl = when (currentContent) {
                                    is Content.Movie -> (currentContent as Content.Movie).trailerUrl
                                    is Content.Series -> (currentContent as Content.Series).trailerUrl
                                    else -> null
                                }
                                if (!trailerUrl.isNullOrBlank()) {
                                    var isTrailerButtonFocused by remember { mutableStateOf(false) }

                                    androidx.tv.material3.Surface(
                                        onClick = { onViewTrailer(trailerUrl) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { isTrailerButtonFocused = it.isFocused }
                                            .pointerInput(trailerUrl) {
                                                detectTapGestures {
                                                    onViewTrailer(trailerUrl)
                                                }
                                            },
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.2f),
                                            focusedContainerColor = Color.White
                                        ),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isTrailerButtonFocused) Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Ver Trailer",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isTrailerButtonFocused) Color.Black else Color.White
                                            )
                                        }
                                    }
                                }

                                // Cast button
                                androidx.tv.material3.Surface(
                                    onClick = { onNavigateToCast(currentContent) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isCastButtonFocused = it.isFocused }
                                        .pointerInput(currentContent) {
                                            detectTapGestures {
                                                onNavigateToCast(currentContent)
                                            }
                                        },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        focusedContainerColor = Color.White
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isCastButtonFocused) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Reparto",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isCastButtonFocused) Color.Black else Color.White
                                        )
                                    }
                                }


                                // Mi Lista button
                                var isWatchlistButtonFocused by remember { mutableStateOf(false) }
                                androidx.tv.material3.Surface(
                                    onClick = { 
                                        if (userId.isNotEmpty() && profileId.isNotEmpty()) {
                                            viewModel.toggleWatchlist(userId, profileId, currentContent)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isWatchlistButtonFocused = it.isFocused }
                                        .pointerInput(currentContent) {
                                            detectTapGestures {
                                                if (userId.isNotEmpty() && profileId.isNotEmpty()) {
                                                    viewModel.toggleWatchlist(userId, profileId, currentContent)
                                                }
                                            }
                                        },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        focusedContainerColor = Color.White
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = if (isWatchlistButtonFocused) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isInWatchlist) "En Mi Lista" else "Mi Lista",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isWatchlistButtonFocused) Color.Black else Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Spacer(modifier = Modifier.weight(0.4f))
                    }
                }
            }

            // Seasons and Episodes Section
            if (isSeries) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp, end = 64.dp, top = 8.dp, bottom = 0.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.hasFocus) {
                                    seasonsFocusJob?.cancel()
                                    if (!isSeasonsSectionFocused) {
                                        isSeasonsSectionFocused = true
                                        coroutineScope.launch {
                                            delay(100)
                                            val offset = with(density) { (-100).dp.roundToPx() }
                                            listState.animateScrollToItem(1, offset)
                                        }
                                    }
                                } else {
                                    seasonsFocusJob?.cancel()
                                    seasonsFocusJob = coroutineScope.launch {
                                        delay(150)
                                        isSeasonsSectionFocused = false
                                    }
                                }
                            }
                    ) {
                        if (seasons.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(bottom = 24.dp)
                            ) {
                                items(seasons, key = { it.id }) { season ->
                                    val isSelected = selectedSeason?.id == season.id
                                    SeasonTabCard(
                                        season = season,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectSeason(season) }
                                    )
                                }
                            }
                        }

                        if (episodes.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 0.dp),
                                modifier = Modifier.graphicsLayer { clip = false }
                            ) {
                                items(episodes, key = { it.id }) { episode ->
                                    EpisodeItemWithAnimation(
                                        episode = episode,
                                        fallbackPosterUrl = currentContent.posterUrl,
                                        onClick = {
                                            selectedSeason?.let { season ->
                                                val progress = watchProgress
                                                val resumePos = if (progress != null &&
                                                    progress.seasonNumber == season.seasonNumber &&
                                                    progress.episodeNumber == episode.episodeNumber) {
                                                    progress.positionMs
                                                } else {
                                                    0L
                                                }
                                                onPlayEpisode(currentContent as Content.Series, season, episode, episodes, resumePos)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Movie Versions Section
            if (!isSeries && hasVersions) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp, end = 64.dp, top = 8.dp, bottom = 0.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.hasFocus) {
                                    seasonsFocusJob?.cancel()
                                    if (!isSeasonsSectionFocused) {
                                        isSeasonsSectionFocused = true
                                        coroutineScope.launch {
                                            delay(100)
                                            val offset = with(density) { (-100).dp.roundToPx() }
                                            listState.animateScrollToItem(1, offset)
                                        }
                                    }
                                } else {
                                    seasonsFocusJob?.cancel()
                                    seasonsFocusJob = coroutineScope.launch {
                                        delay(150)
                                        isSeasonsSectionFocused = false
                                    }
                                }
                            }
                    ) {
                        Text(
                            text = "Versiones disponibles",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 0.dp),
                            modifier = Modifier.graphicsLayer { clip = false }
                        ) {
                            // 1. Standard / Default Version
                            if (movie != null && !movie.videoUrl.isNullOrEmpty()) {
                                item {
                                    val fallbackStill = movie.customBackdropUrl ?: movie.backdropUrl ?: movie.posterUrl
                                    MovieVersionItemWithAnimation(
                                        title = "Versión Estándar",
                                        logoResId = R.drawable.bluray,
                                        centerText = null,
                                        stillUrl = movie.customBackdropUrlBluray ?: fallbackStill,
                                        onClick = {
                                            onPlayContent(currentContent, if (hasWatchProgress) resumePosition else 0L, null)
                                        }
                                    )
                                }
                            }

                            // 2. IMAX Version
                            if (movie?.fileUrlImax != null) {
                                item {
                                    val fallbackStill = movie.customBackdropUrl ?: movie.backdropUrl ?: movie.posterUrl
                                    MovieVersionItemWithAnimation(
                                        title = "Versión IMAX",
                                        logoResId = R.drawable.imax,
                                        centerText = null,
                                        stillUrl = movie.customBackdropUrlImax ?: fallbackStill,
                                        onClick = {
                                            onPlayContent(currentContent, if (hasWatchProgress) resumePosition else 0L, "imax")
                                        }
                                    )
                                }
                            }

                            // 3. Open Matte Version
                            if (movie?.fileUrlOpenmatte != null) {
                                item {
                                    val fallbackStill = movie.customBackdropUrl ?: movie.backdropUrl ?: movie.posterUrl
                                    MovieVersionItemWithAnimation(
                                        title = "Versión Open Matte",
                                        logoResId = R.drawable.openmate,
                                        centerText = null,
                                        stillUrl = movie.customBackdropUrlOpenmatte ?: fallbackStill,
                                        onClick = {
                                            onPlayContent(currentContent, if (hasWatchProgress) resumePosition else 0L, "openmatte")
                                        }
                                    )
                                }
                            }

                            // 4. 4:3 Version
                            if (movie?.fileUrl43 != null) {
                                item {
                                    val fallbackStill = movie.customBackdropUrl ?: movie.backdropUrl ?: movie.posterUrl
                                    MovieVersionItemWithAnimation(
                                        title = "Versión 4:3",
                                        logoResId = null,
                                        centerText = "4:3",
                                        stillUrl = movie.customBackdropUrl43 ?: fallbackStill,
                                        onClick = {
                                            onPlayContent(currentContent, if (hasWatchProgress) resumePosition else 0L, "43")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (isDecorativeBackgroundMode) {
            val decorativeFocusRequester = remember { FocusRequester() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(decorativeFocusRequester)
                    .focusable()
                    .clickable { isDecorativeBackgroundMode = false }
            ) {
                // Branding metadata, Logo and Title Column in the bottom-left corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, bottom = 48.dp)
                        .widthIn(max = 350.dp)
                        .wrapContentHeight()
                        .graphicsLayer {
                            // Opposite translations and rotation to create 3DS-style multi-layered depth (intensified)
                            translationX = -backdropTranslationX * 1.5f
                            translationY = -backdropTranslationY * 1.5f
                            rotationX = -backdropRotationX * 1.3f
                            rotationY = -backdropRotationY * 1.3f
                            cameraDistance = 6f * density.density // brought the virtual camera slightly closer to increase perspective tilt distortion
                        }
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Branding text (Disponible Ahora en AnfhlyTV+)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Disponible Ahora en AnfhlyTV+",
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                        blurRadius = 4f
                                    )
                                )
                            )
                            
                            val releaseDate = when (currentContent) {
                                is Content.Movie -> formatDateSpanish(currentContent.releaseDate)
                                is Content.Series -> formatDateSpanish(currentContent.releaseDate)
                                else -> ""
                            }
                            if (releaseDate.isNotBlank() && releaseDate != "Fecha no disponible") {
                                Text(
                                    text = releaseDate,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    style = androidx.compose.ui.text.TextStyle(
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                            blurRadius = 4f
                                        )
                                    )
                                )
                            }
                        }

                        // Logo or Title
                        val logoImage = currentContent.logoUrl
                        if (!logoImage.isNullOrBlank()) {
                            AsyncImage(
                                model = logoImage,
                                contentDescription = currentContent.title,
                                modifier = Modifier
                                    .width(350.dp)
                                    .heightIn(max = 120.dp),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.BottomStart
                            )
                        } else {
                            Text(
                                text = currentContent.title,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        offset = androidx.compose.ui.geometry.Offset(3f, 3f),
                                        blurRadius = 6f
                                    )
                                )
                            )
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                decorativeFocusRequester.requestFocus()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonTabCard(
    season: Season,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    androidx.tv.material3.Surface(
                                 onClick = onClick,
                                 modifier = Modifier
                                     .widthIn(min = 120.dp)
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = season.name,
                fontSize = 12.sp,
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
private fun EpisodeItemWithAnimation(
    episode: Episode,
    fallbackPosterUrl: String?,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
    LaunchedEffect(episode.id) {
        delay(50)
        isVisible = true
    }
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "alpha"
    )
    
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .width(300.dp)
            .onFocusChanged { isFocused = it.hasFocus }
            .pointerInput(onClick) {
                detectTapGestures {
                    onClick()
                }
            }
            .graphicsLayer {
                alpha = animatedAlpha
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.12f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail (16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                AsyncImage(
                    model = episode.stillUrl ?: fallbackPosterUrl,
                    contentDescription = episode.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Contraste: el thumbnail sin foco se oscurece, el enfocado se ve a brillo completo
                val overlayAlpha by animateFloatAsState(
                    targetValue = if (isFocused) 0f else 0.35f,
                    animationSpec = tween(200),
                    label = "episodeCardContrast"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                )
                
                // Episode number badge
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = "E${episode.episodeNumber}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Play icon overlay when focused
                if (isFocused) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
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
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
            
            // Info below
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = episode.name.ifBlank { "Episodio ${episode.episodeNumber}" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (episode.overview.isNotBlank()) episode.overview else "Sin descripción disponible",
                    fontSize = 11.sp,
                    color = if (isFocused) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieVersionItemWithAnimation(
    title: String,
    logoResId: Int?,
    centerText: String?,
    stillUrl: String?,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
    LaunchedEffect(title) {
        delay(50)
        isVisible = true
    }
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "alpha"
    )
    
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .width(300.dp)
            .onFocusChanged { isFocused = it.hasFocus }
            .pointerInput(onClick) {
                detectTapGestures {
                    onClick()
                }
            }
            .graphicsLayer {
                alpha = animatedAlpha
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.12f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        // Thumbnail (16:9)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AsyncImage(
                model = stillUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Contraste: el thumbnail sin foco se oscurece, el enfocado se ve a brillo completo
            val overlayAlpha by animateFloatAsState(
                targetValue = if (isFocused) 0f else 0.35f,
                animationSpec = tween(200),
                label = "versionCardContrast"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlayAlpha))
            )
            
            // Logo or text in the center
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val logoScale by animateFloatAsState(
                    targetValue = if (isFocused) 1.12f else 1.0f,
                    animationSpec = tween(200),
                    label = "logoScale"
                )
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    },
                    contentAlignment = Alignment.Center
                ) {
                    if (logoResId != null) {
                        val logoHeight = if (logoResId == R.drawable.bluray) 48.dp else 36.dp
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = logoResId),
                            contentDescription = title,
                            modifier = Modifier
                                .height(logoHeight)
                                .widthIn(max = 180.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else if (!centerText.isNullOrEmpty()) {
                        Text(
                            text = centerText,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

