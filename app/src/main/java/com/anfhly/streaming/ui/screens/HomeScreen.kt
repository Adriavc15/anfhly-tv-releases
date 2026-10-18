package com.anfhly.streaming.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import com.anfhly.streaming.ui.components.StreamingPlatformsRow
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.*
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.InterfaceMode
import com.anfhly.streaming.data.model.UserProfile
import com.anfhly.streaming.data.model.WatchlistItem
import com.anfhly.streaming.data.repository.ContentRepository
import com.anfhly.streaming.ui.viewmodel.HomeViewModel
import com.anfhly.streaming.ui.components.VirtualKeyboard
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.R
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Wrapper que agrega un marco blanco alrededor del contenido.
 * El borde se muestra solo cuando el padre tiene foco.
 */
@Composable
fun GlowBorder(
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    glowColor: Color = Color.White,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit
) {
    val borderModifier = if (isFocused) {
        Modifier
            .padding(1.5.dp) // Espacio externo para el borde
            .border(
                width = 1.5.dp,
                color = glowColor,
                shape = shape
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(borderModifier),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}


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


@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            com.anfhly.streaming.ui.components.AnimatedLogo(
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Efecto CRT superpuesto
        com.anfhly.streaming.ui.components.CrtEffectOverlay()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "⚠",
                fontSize = 56.sp
            )
            Text(
                text = message,
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Surface(
                onClick = onRetry,
                modifier = Modifier.pointerInput(onRetry) {
                    detectTapGestures {
                        onRetry()
                    }
                },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFFFFD700),
                    focusedContainerColor = Color(0xFFFFE55C)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Reintentar",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp)
                )
            }
        }
    }
}

/**
 * Card de contenido estilo gaming - Más grande con badges, animaciones y glow
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun GamingContentCard(
    content: Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    itemIndex: Int = 0,
    useBackdrop: Boolean = false,
    isChannel: Boolean = false,
    isCategoryCard: Boolean = false,
    isTrending: Boolean = false,
    cardFocusRequester: FocusRequester? = null
) {
    // Animación de entrada escalonada - ultra optimizada
    val animatedVisibility = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(itemIndex * 10L) // Reducido de 30L a 10L
        animatedVisibility.value = true
    }

    val cardAlpha by animateFloatAsState(
        targetValue = if (animatedVisibility.value) 1f else 0f,
        animationSpec = tween(200), // Reducido de 300ms a 200ms
        label = "cardAlpha"
    )

    // Card con efecto glow blanco al enfocar
    var isFocused by remember { mutableStateOf(false) }
    val defaultFocusRequester = remember { FocusRequester() }
    val focusRequester = cardFocusRequester ?: defaultFocusRequester
    // Quest 3 hover-to-focus support
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // Cuando el puntero del Quest entra en la card, solicitar foco
    LaunchedEffect(isHovered) {
        if (isHovered) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    // Reportar cambio de foco al padre
    LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }
    
    // Animación de padding: cuando tiene foco, el padding se reduce y la card "crece"
    val animatedPadding by animateDpAsState(
        targetValue = if (isFocused) 0.dp else 6.dp,
        animationSpec = tween(200),
        label = "cardPadding"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .width((if (useBackdrop) 380.dp else 135.dp) + 12.dp)
            .height((if (useBackdrop) 214.dp else 205.dp) + 12.dp)
            .focusRequester(focusRequester)
            .hoverable(interactionSource)
            .onFocusChanged { focusState -> isFocused = focusState.hasFocus }
            .pointerInput(onClick) {
                detectTapGestures {
                    onClick()
                }
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(animatedPadding)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Imagen
            val cardImage = if (isCategoryCard && !content.backdropUrl.isNullOrBlank()) {
                content.backdropUrl
            } else if (useBackdrop && !content.backdropUrl.isNullOrBlank()) {
                content.backdropUrl
            } else {
                content.posterUrl
            }

            SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(cardImage)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = content.title,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A2A3A))
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "❌",
                            fontSize = 24.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            // Contraste: cards sin foco se oscurecen, la enfocada se ve a brillo completo
            val overlayAlpha by animateFloatAsState(
                targetValue = if (isFocused) (if (isChannel || isCategoryCard) 0.3f else 0f) else 0.45f,
                animationSpec = tween(200),
                label = "cardContrast"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlayAlpha))
            )

            if (isChannel || isCategoryCard || useBackdrop) {
                if (!content.logoUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(content.logoUrl)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .build(),
                        contentDescription = content.title,
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = content.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize(0.6f),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = content.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (isTrending) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(28.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${itemIndex + 1}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Fila de contenido estilo gaming - Con badges "NUEVO" y animaciones
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun ContentRowGamingStyle(
    title: String,
    items: List<Content>,
    onContentSelected: (Content) -> Unit,
    onContentFocused: (Content?) -> Unit = {},
    isFocused: Boolean,
    onFocusChange: () -> Unit,
    modifier: Modifier = Modifier,
    useBackdrop: Boolean = false,
    showTitle: Boolean = true,
    cardSpacing: androidx.compose.ui.unit.Dp = if (useBackdrop) 24.dp else 32.dp,
    isChannel: Boolean = false,
    isCategoryCard: Boolean = false,
    isTrending: Boolean = false,
    firstCardFocusRequester: FocusRequester? = null
) {
    val listState = rememberTvLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer { clip = false }
    ) {
        if (showTitle) {
            val titleVisible = remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(20)
                titleVisible.value = true
            }

            val titleAlpha by animateFloatAsState(
                targetValue = if (titleVisible.value) 1f else 0f,
                animationSpec = tween(250),
                label = "titleAlpha"
            )

            val titleSlide by animateFloatAsState(
                targetValue = if (titleVisible.value) 0f else -20f,
                animationSpec = tween(250),
                label = "titleSlide"
            )

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp)
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationX = titleSlide
                    }
                    .onFocusChanged {
                        if (it.hasFocus) onFocusChange()
                    }
            )
        }

        // Lista horizontal - solo una fila visible
        TvLazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(cardSpacing),
            pivotOffsets = PivotOffsets(0.15f, 0.15f),
            modifier = Modifier
                .graphicsLayer { clip = false }
                .onFocusChanged {
                    if (it.hasFocus) onFocusChange()
                }
        ) {
            itemsIndexed(items, key = { index, item -> item.id ?: index }) { index, item ->
                GamingContentCard(
                    content = item,
                    onClick = { onContentSelected(item) },
                    onFocusChange = { isCardFocused ->
                        if (isCardFocused) {
                            onContentFocused(item)
                        }
                    },
                    itemIndex = index,
                    useBackdrop = useBackdrop,
                    isChannel = isChannel,
                    isCategoryCard = isCategoryCard,
                    isTrending = isTrending,
                    cardFocusRequester = if (index == 0) firstCardFocusRequester else null
                )
            }
        }
    }
}

/**
 * Card para la fila de Continuar Viendo con barra de progreso y diseño estilo Netflix
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    progress: com.anfhly.streaming.data.repository.WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    cardFocusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val defaultFocusRequester = remember { FocusRequester() }
    val focusRequester = cardFocusRequester ?: defaultFocusRequester
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(isHovered) {
        if (isHovered) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }

    val animatedPadding by animateDpAsState(
        targetValue = if (isFocused) 0.dp else 4.dp,
        animationSpec = tween(150),
        label = "cwPadding"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(165.dp)
            .focusRequester(focusRequester)
            .hoverable(interactionSource)
            .onFocusChanged { isFocused = it.hasFocus }
            .pointerInput(onClick) {
                detectTapGestures { onClick() }
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(animatedPadding)
                .clip(RoundedCornerShape(12.dp))
        ) {
            // Imagen de fondo (backdrop o poster)
            val imageUrl = progress.backdropUrl ?: progress.posterUrl
            if (!imageUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build(),
                    contentDescription = progress.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2A2A3A))
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A1A2A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "❌",
                                fontSize = 24.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF1F1F2F), Color(0xFF11111B))))
                )
            }

            // Contraste: cards sin foco se oscurecen, la enfocada se ve a brillo completo
            val overlayAlpha by animateFloatAsState(
                targetValue = if (isFocused) 0f else 0.45f,
                animationSpec = tween(200),
                label = "cardContrast"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlayAlpha))
            )

            // Gradiente oscuro inferior para texto
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x66000000),
                                Color(0xEE0A0A10)
                            )
                        )
                    )
            )

            // Icono play en el centro
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.Center)
                    .background(
                        color = if (isFocused) Color(0xFFE50914) else Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Textos en el pie
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = progress.title ?: "Continuar viendo",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subText = if (progress.contentType == "series" && progress.seasonNumber != null && progress.episodeNumber != null) {
                    val epName = if (!progress.episodeTitle.isNullOrBlank()) " - ${progress.episodeTitle}" else ""
                    "T${progress.seasonNumber}:E${progress.episodeNumber}$epName"
                } else {
                    val remainingMs = maxOf(0L, progress.durationMs - progress.positionMs)
                    val mins = remainingMs / 60000
                    "${mins}m restantes"
                }

                Text(
                    text = subText,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Barra de progreso roja al pie
            val progressFraction = if (progress.durationMs > 0) {
                (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0.02f, 0.98f)
            } else 0f

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progressFraction)
                        .background(Color.White)
                )
            }
        }
    }
}

/**
 * Fila horizontal para Continuar Viendo
 */
@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun ContinueWatchingRow(
    items: List<com.anfhly.streaming.data.repository.WatchProgress>,
    allMovies: List<Content.Movie>,
    allSeries: List<Content.Series>,
    onContentSelected: (Content) -> Unit,
    onPlayMovie: (Content.Movie, Long) -> Unit,
    onContentFocused: (Content?) -> Unit = {},
    firstCardFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val listState = rememberTvLazyListState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "Continuar viendo",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 2.dp)
        )

        TvLazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            pivotOffsets = PivotOffsets(0.15f, 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        event.key == androidx.compose.ui.input.key.Key.DirectionUp &&
                        onNavigateUp != null) {
                        onNavigateUp()
                        true
                    } else {
                        false
                    }
                }
        ) {
            itemsIndexed(items, key = { _, item -> "${item.contentType}_${item.contentId}_${item.seasonNumber}_${item.episodeNumber}" }) { index, item ->
                // Completar URLs de imágenes si faltan (para datos guardados antes de la actualización)
                val enhancedProgress = if (item.posterUrl.isNullOrBlank() || item.backdropUrl.isNullOrBlank()) {
                    if (item.contentType == "movie") {
                        val movie = allMovies.find { it.id == item.contentId }
                        item.copy(
                            posterUrl = item.posterUrl ?: movie?.posterUrl,
                            backdropUrl = item.backdropUrl ?: movie?.backdropUrl,
                            title = item.title ?: movie?.title
                        )
                    } else {
                        val series = allSeries.find { it.id == item.contentId }
                        item.copy(
                            posterUrl = item.posterUrl ?: series?.posterUrl,
                            backdropUrl = item.backdropUrl ?: series?.backdropUrl,
                            title = item.title ?: series?.title
                        )
                    }
                } else {
                    item
                }

                ContinueWatchingCard(
                    progress = enhancedProgress,
                    onClick = {
                        if (item.contentType == "movie") {
                            val movie = allMovies.find { it.id == item.contentId }
                                ?: Content.Movie(
                                    id = item.contentId,
                                    title = item.title ?: "",
                                    posterUrl = item.posterUrl,
                                    backdropUrl = item.backdropUrl
                                )
                            onPlayMovie(movie, item.positionMs)
                        } else {
                            val series = allSeries.find { it.id == item.contentId }
                                ?: Content.Series(
                                    id = item.contentId,
                                    title = item.title ?: "",
                                    posterUrl = item.posterUrl,
                                    backdropUrl = item.backdropUrl
                                )
                            val targetEpisode = series.seasons
                                .find { it.seasonNumber == item.seasonNumber }
                                ?.episodes
                                ?.find { it.episodeNumber == item.episodeNumber }

                            if (targetEpisode != null) {
                                (context as? android.app.Activity)?.let { act ->
                                    com.anfhly.streaming.PlayerActivity.start(act, series, targetEpisode, item.positionMs)
                                } ?: onContentSelected(series)
                            } else {
                                onContentSelected(series)
                            }
                        }
                    },
                    onFocusChange = { isFocused ->
                        if (isFocused) {
                            val content = if (item.contentType == "movie") {
                                allMovies.find { it.id == item.contentId }
                            } else {
                                allSeries.find { it.id == item.contentId }
                            }
                            onContentFocused(content)
                        }
                    },
                    cardFocusRequester = if (index == 0) firstCardFocusRequester else null
                )
            }
        }
    }
}

// Iconos personalizados para los tabs
val HomeIcon: ImageVector
    get() = ImageVector.Builder(
        name = "HomeIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 2f
        ) {
            // Casa minimalista con bordes redondeados
            // Techo con esquinas redondeadas
            moveTo(12f, 3f)
            lineTo(4f, 11f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5.5f, 12.5f)
            lineTo(5.5f, 19f)
            arcTo(1.5f, 1.5f, 0f, false, true, 7f, 20.5f)
            lineTo(17f, 20.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 18.5f, 19f)
            lineTo(18.5f, 12.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 20f, 11f)
            lineTo(12f, 3f)
            close()
        }
    }.build()

val FilmIcon: ImageVector
    get() = ImageVector.Builder(
        name = "FilmIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 1.5f
        ) {
            // Rollo de película: rectángulo central con círculos a los lados
            // Rectángulo principal
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            // Círculos perforaciones izquierda
            moveTo(6f, 6f)
            arcTo(1.5f, 1.5f, 0f, false, true, 9f, 6f)
            arcTo(1.5f, 1.5f, 0f, false, true, 6f, 6f)
            moveTo(6f, 12f)
            arcTo(1.5f, 1.5f, 0f, false, true, 9f, 12f)
            arcTo(1.5f, 1.5f, 0f, false, true, 6f, 12f)
            moveTo(6f, 18f)
            arcTo(1.5f, 1.5f, 0f, false, true, 9f, 18f)
            arcTo(1.5f, 1.5f, 0f, false, true, 6f, 18f)
            // Círculos perforaciones derecha
            moveTo(18f, 6f)
            arcTo(1.5f, 1.5f, 0f, false, true, 15f, 6f)
            arcTo(1.5f, 1.5f, 0f, false, true, 18f, 6f)
            moveTo(18f, 12f)
            arcTo(1.5f, 1.5f, 0f, false, true, 15f, 12f)
            arcTo(1.5f, 1.5f, 0f, false, true, 18f, 12f)
            moveTo(18f, 18f)
            arcTo(1.5f, 1.5f, 0f, false, true, 15f, 18f)
            arcTo(1.5f, 1.5f, 0f, false, true, 18f, 18f)
        }
    }.build()

val HeartOutlineIcon: ImageVector
    get() = ImageVector.Builder(
        name = "HeartOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 2f
        ) {
            // Corazón outline
            moveTo(12f, 21f)
            lineTo(10.55f, 19.7f)
            curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
            curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
            curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
            curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
            curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
            curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 19.7f)
            close()
        }
    }.build()

val HeartFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "HeartFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
            stroke = null
        ) {
            // Corazón relleno
            moveTo(12f, 21f)
            lineTo(10.55f, 19.7f)
            curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
            curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
            curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
            curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
            curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
            curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 19.7f)
            close()
        }
    }.build()

/**
 * Header estilo Free eShop - Logo + Search Bar con animaciones
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GamingStyleHeader(
    selectedProfile: UserProfile?,
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    onProfilesClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    headerFocusRequester: FocusRequester = FocusRequester(),
    onNavigateDown: () -> Unit = {},
    onOpenSidebar: () -> Unit = {},
    isSidebarOnly: Boolean = false,
    tabEnabled: Map<Int, Boolean> = emptyMap()
) {
    val tabs = listOf(
        Pair(Icons.Default.Person, "Profile"),
        Pair(R.drawable.home, "Home"),
        Pair(R.drawable.series, "Series"),
        Pair(R.drawable.movie, "Movies"),
        Pair(R.drawable.pelota, "Sports"),
        Pair(R.drawable.categories, "Categories"),
        Pair(Icons.Default.Add, "My List"),
        Pair(Icons.Default.Search, "Search")
    )


    // Animación de entrada del header - ultra optimizada
    val headerVisible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50) // Reducido de 100ms a 50ms
        headerVisible.value = true
    }

    val headerAlpha by animateFloatAsState(
        targetValue = if (headerVisible.value) 1f else 0f,
        animationSpec = tween(300), // Reducido de 600ms a 300ms
        label = "headerAlpha"
    )

    val headerSlide by animateFloatAsState(
        targetValue = if (headerVisible.value) 0f else -30f, // Reducido de -50f a -30f
        animationSpec = tween(300), // Reducido de 600ms a 300ms
        label = "headerSlide"
    )

    val profileFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }

    val enabledTabIndices = remember(tabEnabled) {
        (1 until tabs.size - 1).filter { tabEnabled[it] != false }
    }
    val firstEnabledTabIndex = enabledTabIndices.firstOrNull() ?: 1
    val lastEnabledTabIndex = enabledTabIndices.lastOrNull() ?: (tabs.size - 2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = headerAlpha
                translationY = headerSlide
            }
            .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 8.dp)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.hasFocus)
            }
    ) {
        // Box container to allow left-aligned profile and centered main tabs capsule
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            // 1. Botón de Perfil en el extremo izquierdo (cápsula independiente)
            val isProfileSelected = activeTab == 0
            var isProfileFocused by remember { mutableStateOf(false) }

            val profileTextColor by animateColorAsState(
                targetValue = when {
                    isProfileFocused -> Color.Black
                    isProfileSelected -> Color.White
                    else -> Color.White.copy(alpha = 0.85f)
                },
                animationSpec = tween(200),
                label = "profileText"
            )

            Box(
                modifier = Modifier
                    .background(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Surface(
                    onClick = onOpenSidebar,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                    modifier = Modifier
                        .focusRequester(if (isSidebarOnly || activeTab == 0) headerFocusRequester else profileFocusRequester)
                        .onFocusChanged { state ->
                            isProfileFocused = state.isFocused
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onOpenSidebar()
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                when (event.key) {
                                    androidx.compose.ui.input.key.Key.DirectionDown -> {
                                        onNavigateDown()
                                        true
                                    }
                                    androidx.compose.ui.input.key.Key.DirectionLeft -> {
                                        onOpenSidebar()
                                        true
                                    }
                                    androidx.compose.ui.input.key.Key.DirectionRight -> {
                                        if (!isSidebarOnly && enabledTabIndices.isNotEmpty()) {
                                            val targetTab = if (activeTab in enabledTabIndices) activeTab else firstEnabledTabIndex
                                            try {
                                                tabFocusRequesters[targetTab].requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        if (selectedProfile?.customImageUrl != null && selectedProfile.customImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = selectedProfile.customImageUrl,
                                contentDescription = selectedProfile.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            val bgColor = try {
                                Color(android.graphics.Color.parseColor(selectedProfile?.profileColor ?: "#3b82f6"))
                            } catch (e: Exception) {
                                Color(0xFF3b82f6)
                            }

                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .background(bgColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = selectedProfile?.name?.firstOrNull()?.uppercase() ?: "P",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedProfile?.name ?: "Perfil",
                            fontSize = 12.sp,
                            fontWeight = if (isProfileSelected) FontWeight.Bold else FontWeight.Medium,
                            color = profileTextColor
                        )
                    }
                }
            }

            // 2. Cápsula de otros Tabs Centrada (oculta si sidebarOnly)
            if (!isSidebarOnly) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Solo renderizar del Home en adelante hasta Mi Lista (índices 1 a 6)
                    for (index in 1 until tabs.size - 1) {
                        // Saltar tabs deshabilitados en configuración
                        if (tabEnabled[index] == false) continue
                        val tab = tabs[index]
                        val (iconRes, label) = tab
                        val isSelected = activeTab == index
                        var isTabFocused by remember { mutableStateOf(false) }
                        val effectiveTabRequester = if (activeTab == index) headerFocusRequester else tabFocusRequesters[index]
                        // Quest 3 hover support for tabs
                        val tabInteraction = remember { MutableInteractionSource() }
                        val isTabHovered by tabInteraction.collectIsHoveredAsState()
                        LaunchedEffect(isTabHovered) {
                            if (isTabHovered) {
                                try { effectiveTabRequester.requestFocus() } catch (_: Exception) {}
                            }
                        }

                        val textColor by animateColorAsState(
                            targetValue = when {
                                isTabFocused -> Color.Black
                                isSelected -> Color.White
                                else -> Color.White.copy(alpha = 0.85f)
                            },
                            animationSpec = tween(200),
                            label = "tabText"
                        )

                        Surface(
                            onClick = { 
                                onTabSelected(index)
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                        focusedContainerColor = Color.White
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                            modifier = Modifier
                                .focusRequester(effectiveTabRequester)
                                .hoverable(tabInteraction)
                                .onFocusChanged { state ->
                                    isTabFocused = state.isFocused
                                }
                                .pointerInput(index) {
                                    detectTapGestures {
                                        onTabSelected(index)
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                        when (event.key) {
                                            androidx.compose.ui.input.key.Key.DirectionDown -> {
                                                onNavigateDown()
                                                true
                                            }
                                            androidx.compose.ui.input.key.Key.DirectionLeft -> {
                                                if (index == firstEnabledTabIndex) {
                                                    try {
                                                        profileFocusRequester.requestFocus()
                                                        true
                                                    } catch (_: Exception) {
                                                        false
                                                    }
                                                } else {
                                                    val prevIndex = enabledTabIndices.filter { it < index }.lastOrNull()
                                                    if (prevIndex != null) {
                                                        try {
                                                            tabFocusRequesters[prevIndex].requestFocus()
                                                            true
                                                        } catch (_: Exception) {
                                                            false
                                                        }
                                                    } else {
                                                        false
                                                    }
                                                }
                                            }
                                            androidx.compose.ui.input.key.Key.DirectionRight -> {
                                                if (index == lastEnabledTabIndex) {
                                                    try {
                                                        searchFocusRequester.requestFocus()
                                                        true
                                                    } catch (_: Exception) {
                                                        false
                                                    }
                                                } else {
                                                    val nextIndex = enabledTabIndices.filter { it > index }.firstOrNull()
                                                    if (nextIndex != null) {
                                                        try {
                                                            tabFocusRequesters[nextIndex].requestFocus()
                                                            true
                                                        } catch (_: Exception) {
                                                            false
                                                        }
                                                    } else {
                                                        false
                                                    }
                                                }
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                if (iconRes is String) {
                                    Text(
                                        text = iconRes,
                                        fontSize = 20.sp,
                                        color = textColor,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .offset(y = (-3).dp)
                                    )
                                } else if (iconRes is ImageVector) {
                                    Icon(
                                        imageVector = iconRes,
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = iconRes as Int),
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
            }

            // 3. Botón de Búsqueda en el extremo derecho (cápsula independiente, oculta si sidebarOnly)
            if (!isSidebarOnly) {
            val searchIndex = tabs.size - 1
            val isSearchSelected = activeTab == searchIndex
            val effectiveSearchRequester = if (isSearchSelected) headerFocusRequester else searchFocusRequester
            var isSearchFocused by remember { mutableStateOf(false) }

            val searchTextColor by animateColorAsState(
                targetValue = when {
                    isSearchFocused -> Color.Black
                    isSearchSelected -> Color.White
                    else -> Color.White.copy(alpha = 0.85f)
                },
                animationSpec = tween(200),
                label = "searchText"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .background(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Surface(
                    onClick = { onTabSelected(searchIndex) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                    modifier = Modifier
                        .focusRequester(effectiveSearchRequester)
                        .onFocusChanged { state ->
                            isSearchFocused = state.isFocused
                        }
                        .pointerInput(searchIndex) {
                            detectTapGestures {
                                onTabSelected(searchIndex)
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                when (event.key) {
                                    androidx.compose.ui.input.key.Key.DirectionDown -> {
                                        onNavigateDown()
                                        true
                                    }
                                    androidx.compose.ui.input.key.Key.DirectionLeft -> {
                                        if (enabledTabIndices.isNotEmpty()) {
                                            try {
                                                tabFocusRequesters[lastEnabledTabIndex].requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        } else {
                                            try {
                                                profileFocusRequester.requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = searchTextColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Search",
                            fontSize = 12.sp,
                            fontWeight = if (isSearchSelected) FontWeight.Bold else FontWeight.Medium,
                            color = searchTextColor
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Iconos estáticos optimizados para Blu-ray, Atmos y DVD - se cargan una sola vez
 */
@Composable
fun FormatIcons() {
    // Iconos que se cargan una sola vez y se reutilizan
    val blurayIcon = painterResource(id = R.drawable.bluray)
    val imaxIcon = painterResource(id = R.drawable.imax)
    val atmosIcon = painterResource(id = R.drawable.atmos)
    val dvdIcon = painterResource(id = R.drawable.dvd)
    val openmateIcon = painterResource(id = R.drawable.openmate)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = imaxIcon,
            contentDescription = "IMAX",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
        Icon(
            painter = blurayIcon,
            contentDescription = "Blu-ray",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Icon(
            painter = dvdIcon,
            contentDescription = "DVD",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Icon(
            painter = atmosIcon,
            contentDescription = "Atmos",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
        Icon(
            painter = openmateIcon,
            contentDescription = "OpenMate",
            tint = Color.White,
            modifier = Modifier.size(60.dp)
        )
    }
}

/**
 * Iconos estáticos grandes para Blu-ray, Atmos y DVD - se cargan una sola vez
 */
@Composable
fun FormatIconsLarge() {
    // Iconos que se cargan una sola vez y se reutilizan
    val blurayIcon = painterResource(id = R.drawable.bluray)
    val imaxIcon = painterResource(id = R.drawable.imax)
    val atmosIcon = painterResource(id = R.drawable.atmos)
    val dvdIcon = painterResource(id = R.drawable.dvd)
    val openmateIcon = painterResource(id = R.drawable.openmate)
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = imaxIcon,
            contentDescription = "IMAX",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
        Icon(
            painter = blurayIcon,
            contentDescription = "Blu-ray",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Icon(
            painter = dvdIcon,
            contentDescription = "DVD",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Icon(
            painter = atmosIcon,
            contentDescription = "Atmos",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
        Icon(
            painter = openmateIcon,
            contentDescription = "OpenMate",
            tint = Color.White,
            modifier = Modifier.size(60.dp)
        )
    }
}

/**
 * Compact content info overlay - Logo, tags debajo del logo, descripción al final
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CompactContentOverlay(
    content: Content,
    modifier: Modifier = Modifier,
    isInWatchlist: Boolean = false,
    heroFocusRequester: FocusRequester? = null,
    headerFocusRequester: FocusRequester? = null,
    showButtons: Boolean = true,
    onPlayClick: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onWatchlistClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onNavigateDown: (() -> Unit)? = null,
    isLiveChannel: Boolean = false
) {
    val logoUrl = content.logoUrl

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Disponible Ahora en AnfhlyTV+ con fecha pegada
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Disponible Ahora en AnfhlyTV+",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
            
            // Fecha del contenido sin fondo
            val contentDate = remember(content.releaseDate) {
                when (content) {
                    is Content.Movie -> formatDateSpanish(content.releaseDate)
                    is Content.Series -> formatDateSpanish(content.releaseDate)
                    else -> "Fecha no disponible"
                }
            }
            Text(
                text = contentDate,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
        
        // Logo or Title
        Row(
            modifier = Modifier.width(350.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!logoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .build(),
                    contentDescription = content.title,
                    loading = {
                        Box(
                            modifier = Modifier
                                .width(350.dp)
                                .height(60.dp)
                                .background(Color.Transparent)
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .width(350.dp)
                                .heightIn(max = 120.dp)
                                .background(Color.Transparent),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = content.title,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    modifier = Modifier
                        .width(350.dp)
                        .heightIn(max = 120.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Text(
                    text = content.title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        

        
        Spacer(modifier = Modifier.height(4.dp))
        
        AnimatedVisibility(
            visible = !showButtons,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Disponible en:",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 4.dp)
                )
                Image(
                    painter = painterResource(id = R.drawable.imax),
                    contentDescription = "IMAX",
                    modifier = Modifier.height(8.dp),
                    contentScale = ContentScale.Fit
                )
                Image(
                    painter = painterResource(id = R.drawable.openmate),
                    contentDescription = "OpenMate",
                    modifier = Modifier.height(10.dp),
                    contentScale = ContentScale.Fit
                )

            }
        }
        
        AnimatedVisibility(
            visible = showButtons,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
        ) {
            // Botones de acción
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .onPreviewKeyEvent { event ->
                        if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            androidx.compose.ui.input.key.Key.DirectionUp -> {
                                if (headerFocusRequester != null) {
                                    try {
                                        headerFocusRequester.requestFocus()
                                        return@onPreviewKeyEvent true
                                    } catch (e: Exception) {
                                        return@onPreviewKeyEvent false
                                    }
                                }
                                false
                            }
                            androidx.compose.ui.input.key.Key.DirectionDown -> {
                                if (onNavigateDown != null) {
                                    onNavigateDown()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
            ) {
            // Botón Ver Ahora
            var isPlayFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onPlayClick,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                modifier = Modifier
                    .then(if (heroFocusRequester != null) Modifier.focusRequester(heroFocusRequester) else Modifier)
                    .onFocusChanged { isPlayFocused = it.isFocused }
                    .pointerInput(onPlayClick) {
                        detectTapGestures {
                            onPlayClick()
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Ver ahora",
                        tint = if (isPlayFocused) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Ver ahora",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlayFocused) Color.Black else Color.White
                    )
                }
            }

            // Botón Reparto (oculto para canales en vivo)
            if (!isLiveChannel) {
                var isCastFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = onCastClick,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                    modifier = Modifier
                        .onFocusChanged { isCastFocused = it.isFocused }
                        .pointerInput(onCastClick) {
                            detectTapGestures {
                                onCastClick()
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Reparto",
                            tint = if (isCastFocused) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Botón Mi Lista (oculto para canales en vivo)
            if (!isLiveChannel) {
                var isWatchlistFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = onWatchlistClick,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                    modifier = Modifier
                        .onFocusChanged { isWatchlistFocused = it.isFocused }
                        .pointerInput(onWatchlistClick) {
                            detectTapGestures {
                                onWatchlistClick()
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Mi Lista",
                            tint = if (isWatchlistFocused) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Botón Anterior
            var isPrevFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onPreviousClick,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                modifier = Modifier
                    .onFocusChanged { isPrevFocused = it.isFocused }
                    .pointerInput(onPreviousClick) {
                        detectTapGestures {
                            onPreviousClick()
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Anterior",
                        tint = if (isPrevFocused) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Botón Siguiente
            var isNextFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onNextClick,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                modifier = Modifier
                    .onFocusChanged { isNextFocused = it.isFocused }
                    .pointerInput(onNextClick) {
                        detectTapGestures {
                            onNextClick()
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Siguiente",
                        tint = if (isNextFocused) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
}

/**
 * Hero Content Info - Logo, tags debajo del logo, descripción al final
 * Navegable pero sin efectos visuales de foco
 */
@Composable
fun HeroContentInfo(
    content: Content,
    onContentSelected: (Content) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 300.dp)
            .focusable()
            .onFocusChanged { focusState ->
                onFocusChange(focusState.hasFocus)
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Disponible Ahora en AnfhlyTV+ con fecha pegada
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Disponible Ahora en AnfhlyTV+",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1
            )
            
            // Fecha del contenido sin fondo
            val contentDate = remember(content.releaseDate) {
                when (content) {
                    is Content.Movie -> formatDateSpanish(content.releaseDate)
                    is Content.Series -> formatDateSpanish(content.releaseDate)
                    else -> "Fecha no disponible"
                }
            }
            Text(
                text = contentDate,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
        
        // LOGO o Título
        val logoImage = content.logoUrl
        if (logoImage != null) {
            Box(
                modifier = Modifier
                    .height(450.dp)
                    .width(600.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoImage)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .build(),
                    contentDescription = content.title,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = content.title,
                                fontSize = 64.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Text(
                text = content.title,
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(550.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        
        Spacer(modifier = Modifier.height(8.dp))



        // Espacio reducido para pegar las cards más al margen inferior
        Spacer(modifier = Modifier.height(100.dp))
    }
}



/**
 * Componente de puntuación como badge
 */
@Composable
fun RatingBadge(voteAverage: Double) {
    Box(
        modifier = Modifier
            .background(
                Color(0xFFFFD700).copy(alpha = 0.3f), // Fondo dorado semi-transparente
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "★",
                fontSize = 14.sp,
                color = Color(0xFFFFD700) // Estrella dorada
            )
            Text(
                text = String.format("%.1f", voteAverage),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun TechBadge(text: String) {
    Box(
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

/**
 * Sidebar flotante estilo gaming - Se abre al presionar izquierda en el tab de Perfil
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FloatingSidebar(
    selectedProfile: UserProfile?,
    activeInterface: InterfaceMode,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onProfilesClick: () -> Unit,
    onConfigClick: () -> Unit,
    tabEnabled: Map<Int, Boolean> = emptyMap()
) {
    val sidebarFocusRequester = remember { FocusRequester() }

    // Focus the first item when sidebar opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(100)
            try { sidebarFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    if (!isVisible) return

    // Overlay semitransparente
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                    event.key == androidx.compose.ui.input.key.Key.DirectionLeft) {
                    onDismiss()
                    true
                } else if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                    event.key == androidx.compose.ui.input.key.Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
    ) {
        // Sidebar panel
        Box(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                .background(
                    color = Color(0xFFC0C0CC),
                    shape = RoundedCornerShape(20.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* consume, don't dismiss */ }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Menu items
                data class SidebarItem(val icon: Any?, val label: String, val tabIndex: Int)
                val allItems = listOf(
                    SidebarItem(null, "Profile", 0),
                    SidebarItem(R.drawable.home, "Home", 1),
                    SidebarItem(R.drawable.series, "Series", 2),
                    SidebarItem(R.drawable.movie, "Movies", 3),
                    SidebarItem(R.drawable.pelota, "Sports", 4),
                    SidebarItem(R.drawable.categories, "Categories", 5),
                    SidebarItem(Icons.Default.Add, "My List", 6),
                    SidebarItem(Icons.Default.Search, "Search", 7),
                    SidebarItem(Icons.Default.Settings, "Config", -1)
                ).filter { item ->
                    item.tabIndex == -1 || item.tabIndex == 0 || item.tabIndex == 7 || tabEnabled[item.tabIndex] != false
                }

                allItems.forEachIndexed { index, item ->
                    val iconRes = item.icon
                    val label = item.label
                    val tabIndex = item.tabIndex
                    var isItemFocused by remember { mutableStateOf(false) }

                    // Divider between Profile and main tabs
                    if (label == "Home") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF333333).copy(alpha = 0.15f))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Spacer + divider to push Search/Config to bottom
                    if (label == "Search") {
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF333333).copy(alpha = 0.15f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    val itemFocusRequester = remember { FocusRequester() }
                    val itemInteraction = remember { MutableInteractionSource() }
                    val isItemHovered by itemInteraction.collectIsHoveredAsState()

                    LaunchedEffect(isItemHovered) {
                        if (isItemHovered) {
                            try { itemFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }

                    val itemTextColor by animateColorAsState(
                        targetValue = Color(0xFF333333),
                        animationSpec = tween(150),
                        label = "sidebarItemText"
                    )

                    val itemIconTint by animateColorAsState(
                        targetValue = Color(0xFF333333),
                        animationSpec = tween(150),
                        label = "sidebarItemIcon"
                    )

                    Surface(
                        onClick = {
                            when (tabIndex) {
                                0 -> onProfilesClick()
                                -1 -> onConfigClick()
                                else -> onTabSelected(tabIndex)
                            }
                            onDismiss()
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFD0D0D8)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(sidebarFocusRequester) else Modifier)
                            .focusRequester(itemFocusRequester)
                            .hoverable(itemInteraction)
                            .onFocusChanged { isItemFocused = it.isFocused }
                            .pointerInput(tabIndex) {
                                detectTapGestures {
                                    when (tabIndex) {
                                        0 -> onProfilesClick()
                                        -1 -> onConfigClick()
                                        else -> onTabSelected(tabIndex)
                                    }
                                    onDismiss()
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                    when (event.key) {
                                        androidx.compose.ui.input.key.Key.DirectionDown -> false
                                        androidx.compose.ui.input.key.Key.DirectionUp -> false
                                        androidx.compose.ui.input.key.Key.DirectionRight -> {
                                            onDismiss()
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when {
                                // Profile - show avatar image
                                label == "Profile" -> {
                                    if (selectedProfile?.customImageUrl != null && selectedProfile.customImageUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = selectedProfile.customImageUrl,
                                            contentDescription = selectedProfile.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        val bgColor = try {
                                            Color(android.graphics.Color.parseColor(selectedProfile?.profileColor ?: "#3b82f6"))
                                        } catch (e: Exception) {
                                            Color(0xFF3b82f6)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(bgColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = selectedProfile?.name?.firstOrNull()?.uppercase() ?: "P",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                iconRes is Int && iconRes != 0 -> {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = label,
                                        tint = itemIconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                iconRes is ImageVector -> {
                                    Icon(
                                        imageVector = iconRes,
                                        contentDescription = label,
                                        tint = itemIconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (label == "Profile" && selectedProfile != null) selectedProfile.name else label,
                                fontSize = 14.sp,
                                fontWeight = if (isItemFocused) FontWeight.Bold else FontWeight.Normal,
                                color = itemTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * HomeScreen con diseño híbrido Gaming + Streaming
 * Inspirado en Free eShop (gaming UI) y Netflix/Streaming services
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    selectedProfile: UserProfile?,
    onContentSelected: (Content) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToConfig: () -> Unit = {},
    onPlayContent: (Content, String?) -> Unit = { content, _ -> },
    onNavigateToCast: (Content) -> Unit = {},
    onNavigateToPlatform: (String) -> Unit = {}
) {
    val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val featuredContent by viewModel.featuredContent.collectAsStateWithLifecycle()
    val featuredSeries by viewModel.featuredSeries.collectAsStateWithLifecycle()
    val featuredMovies by viewModel.featuredMovies.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val seriesCategories by viewModel.seriesCategories.collectAsStateWithLifecycle()
    val moviesCategories by viewModel.moviesCategories.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val currentFeaturedIndex by viewModel.currentFeaturedIndex.collectAsStateWithLifecycle()
    val activeInterface by viewModel.activeInterface.collectAsStateWithLifecycle()
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val channelsCategories by viewModel.channelsCategories.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val isSidebarOnly = com.anfhly.streaming.utils.UserPreferences.isSidebarOnly(context)
    val tabEnabled = (1..6).associateWith { com.anfhly.streaming.utils.UserPreferences.isTabEnabled(context, it) }

    val uiStyle = remember { mutableStateOf(com.anfhly.streaming.utils.UserPreferences.getUiStyle(context)) }

    var focusedRowIndex by remember { mutableStateOf(-1) }
    var focusedCardContent by remember { mutableStateOf<Content?>(null) }
    var headerHasFocus by remember { mutableStateOf(false) }
    var heroHasFocus by remember { mutableStateOf(false) }
    var sidebarOpen by remember { mutableStateOf(false) }
    val verticalScrollState = rememberScrollState()
    val listState = remember { androidx.tv.foundation.lazy.list.TvLazyListState() }
    val cardsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.initWatchProgressRepository(context)
    }

    // Cargar continue watching cuando películas y series estén disponibles
    LaunchedEffect(movies.isNotEmpty() || series.isNotEmpty()) {
        if (movies.isNotEmpty() || series.isNotEmpty()) {
            viewModel.loadContinueWatching()
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadContinueWatching()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-rotate carousel cada 10 segundos
    LaunchedEffect(activeInterface) {
        if (activeInterface == InterfaceMode.HOME) {
            viewModel.loadContinueWatching()
        }
        while (true) {
            delay(10000)
            viewModel.goToNextFeatured()
        }
    }

    // Reset featured index y scroll al inicio cuando cambia de tab (optimizado)
    LaunchedEffect(activeInterface) {
        viewModel.setHeaderIndex(0)
        focusedCardContent = null
        heroHasFocus = false
        // Usar scrollTo en lugar de animateScrollTo para mejor rendimiento
        verticalScrollState.scrollTo(0)
        try {
            listState.scrollToItem(0)
        } catch (e: Exception) {
            // Ignorar
        }
    }

    // Convertir watchlist items a Content para poder usarlos en el hero
    val watchlistAsContent = remember(watchlist, movies, series) {
        watchlist.map { item ->
            val actualContent = movies.find { 
                it.id == item.id || 
                it.id == item.firebaseId || 
                (it.tmdbId != null && it.tmdbId != 0 && it.tmdbId == item.tmdbId) 
            } ?: series.find { 
                it.id == item.id || 
                it.id == item.firebaseId || 
                (it.tmdbId != null && it.tmdbId != 0 && it.tmdbId == item.tmdbId) 
            }
            
            actualContent ?: if (item.type == "movie" || item.type.isEmpty()) {
                Content.Movie(
                    id = item.firebaseId.ifEmpty { item.id },
                    title = item.displayName,
                    overview = item.overview ?: "",
                    posterUrl = item.posterUrl.ifEmpty { item.posterPath },
                    backdropUrl = item.backdropUrl,
                    backdropPublicityUrl = item.backdropPublicityUrl,
                    voteAverage = item.vote_average ?: 0.0,
                    releaseDate = item.release_date ?: item.first_air_date,
                    logoUrl = item.logoUrl,
                    tmdbId = item.tmdbId
                )
            } else {
                Content.Series(
                    id = item.firebaseId.ifEmpty { item.id },
                    title = item.displayName,
                    overview = item.overview ?: "",
                    posterUrl = item.posterUrl.ifEmpty { item.posterPath },
                    backdropUrl = item.backdropUrl,
                    backdropPublicityUrl = item.backdropPublicityUrl,
                    voteAverage = item.vote_average ?: 0.0,
                    releaseDate = item.first_air_date ?: item.release_date,
                    logoUrl = item.logoUrl,
                    tmdbId = item.tmdbId
                )
            }
        }
    }

    // Obtener el contenido a mostrar: card enfocada > primera card > contenido destacado
    val heroContent = when (activeInterface) {
        InterfaceMode.SERIES -> featuredSeries
        InterfaceMode.MOVIES -> featuredMovies
        InterfaceMode.LIVE -> channels
        InterfaceMode.MY_LIST -> watchlistAsContent
        else -> featuredContent
    }

    // Precargar imágenes de las primeras cards para mejorar la experiencia (solo en dispositivos con >= 2GB RAM)
    val isLowRam = remember(context) {
        try {
            val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem < 1.8 * 1024 * 1024 * 1024 // Menos de 1.8 GB de RAM total
        } catch (e: Exception) {
            false
        }
    }
    
    val imageLoader = remember { coil.Coil.imageLoader(context) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(activeInterface, categories, seriesCategories, moviesCategories) {
        if (isLowRam) return@LaunchedEffect // Evitar sobrecargar CPU/Red en dispositivos de gama baja
        coroutineScope.launch {
            val imagesToPreload = mutableListOf<String>()
            
            // Añadir imágenes del contenido destacado
            heroContent?.take(5)?.forEach { content ->
                content.backdropUrl?.let { imagesToPreload.add(it) }
                content.posterUrl?.let { imagesToPreload.add(it) }
            }
            
            // Añadir primeras imágenes de las categorías (primeras 4 cards de las primeras 2 categorías)
            val currentCategories = when (activeInterface) {
                InterfaceMode.HOME -> categories
                InterfaceMode.SERIES -> seriesCategories
                InterfaceMode.MOVIES -> moviesCategories
                else -> emptyList()
            }
            
            currentCategories.take(2).forEach { category ->
                category.items.take(4).forEach { content ->
                    content.posterUrl?.let { imagesToPreload.add(it) }
                    content.backdropUrl?.let { imagesToPreload.add(it) }
                }
            }
            
            // Precargar imágenes en segundo plano
            imagesToPreload.distinct().take(10).forEach { imageUrl ->
                try {
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(request)
                } catch (e: Exception) {
                    // Ignorar errores de precargado
                }
            }
        }
    }

    // Obtener la primera card de la primera categoría como fallback
    val firstCategoryCard = when (activeInterface) {
        InterfaceMode.HOME -> categories.getOrNull(0)?.items?.getOrNull(0)
        InterfaceMode.SERIES -> seriesCategories.getOrNull(0)?.items?.getOrNull(0)
        InterfaceMode.MOVIES -> moviesCategories.getOrNull(0)?.items?.getOrNull(0)
        InterfaceMode.LIVE -> channelsCategories.getOrNull(0)?.items?.getOrNull(0)
        InterfaceMode.MY_LIST -> watchlistAsContent.getOrNull(0)
        else -> null
    }
    
    val currentHeroItem = when (activeInterface) {
        InterfaceMode.CATEGORIES -> null // Forzar null en categorías para usar fondo negro
        else -> heroContent.getOrNull(currentFeaturedIndex) ?: firstCategoryCard
    }
    
    val showHero = currentHeroItem != null && activeInterface != InterfaceMode.CATEGORIES
    
    val backdropContent = currentHeroItem
    
    val backdropUrl = backdropContent?.backdropUrl

    val hasBackdrop = !backdropUrl.isNullOrBlank() && activeInterface != InterfaceMode.CATEGORIES

    val gradientColors = remember { mutableStateOf(listOf(Color(0xFF0D0D1A), Color(0xFF1A1A2E), Color(0xFF0D0D1A))) }
    val isNetflixStyle = uiStyle.value == "netflix"

    LaunchedEffect(backdropUrl, isNetflixStyle) {
        if (isNetflixStyle && !backdropUrl.isNullOrBlank()) {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val url = java.net.URL(backdropUrl)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    val input = connection.inputStream
                    val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    connection.disconnect()
                    if (bitmap != null) {
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 100, 56, true)
                        val palette = androidx.palette.graphics.Palette.from(scaled).generate()

                        val color1 = palette.darkVibrantSwatch?.rgb
                            ?: palette.dominantSwatch?.rgb
                            ?: palette.vibrantSwatch?.rgb

                        val color2 = palette.dominantSwatch?.rgb
                            ?: palette.vibrantSwatch?.rgb
                            ?: palette.darkMutedSwatch?.rgb

                        val color3 = palette.mutedSwatch?.rgb
                            ?: palette.lightMutedSwatch?.rgb
                            ?: palette.darkMutedSwatch?.rgb

                        val c1 = color1?.let { Color(it) } ?: Color(0xFF0D0D1A)
                        val c2 = color2?.let { Color(it) } ?: Color(0xFF1A1A2E)
                        val c3 = color3?.let { Color(it) } ?: Color(0xFF0D0D1A)

                        gradientColors.value = listOf(c1, c2, c3)

                        if (scaled != bitmap) scaled.recycle()
                        bitmap.recycle()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // App background (visible always fullscreen behind everything)
        AppBackground()
        // Overlay dinámico con degradado según imagen del hero
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isNetflixStyle) Brush.verticalGradient(
                        colors = gradientColors.value
                    )
                    else Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )

        androidx.compose.animation.Crossfade(
            targetState = uiState,
            animationSpec = tween(600),
            label = "uiStateCrossfade"
        ) { currentState ->
            when (currentState) {
                is HomeViewModel.HomeUiState.Loading -> LoadingContent()
                is HomeViewModel.HomeUiState.Error -> {
                    val message = currentState.message
                    ErrorContent(message = message, onRetry = { viewModel.loadContent() })
                }
                else -> {
                    // LAYOUT SIMPLIFICADO - Header fijo, contenido scrollable
                Box(modifier = Modifier.fillMaxSize()) {
                    val headerFocusRequester = remember { FocusRequester() }
                    val firstCardFocusRequester = remember { FocusRequester() }
                    val heroFocusRequester = remember { FocusRequester() }
                    val streamingFocusRequester = remember { FocusRequester() }
                    val keyboardFocusRequester = remember { FocusRequester() }
                    val scope = rememberCoroutineScope()
                    val showStreamingPlatforms =
                        activeInterface == InterfaceMode.HOME || activeInterface == InterfaceMode.MOVIES
                    // Índice en TvLazyColumn: hero (opcional) + plataformas (opcional) + continue watching (opcional) + cards
                    val streamingListIndex = if (showHero) 1 else 0
                    val firstCardsListIndex =
                        (if (showHero) 1 else 0) + (if (showStreamingPlatforms) 1 else 0) + (if (continueWatching.isNotEmpty()) 1 else 0)

                    val focusStreamingPlatforms = {
                        heroHasFocus = false
                        headerHasFocus = false
                        scope.launch {
                            try {
                                listState.animateScrollToItem(streamingListIndex)
                            } catch (_: Exception) {}
                            delay(100)
                            try {
                                streamingFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    val focusFirstCards = {
                        heroHasFocus = false
                        headerHasFocus = false
                        focusedRowIndex = 0
                        scope.launch {
                            try {
                                listState.animateScrollToItem(firstCardsListIndex)
                            } catch (_: Exception) {}
                            delay(100)
                            try {
                                firstCardFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    val focusContinueWatching = {
                        heroHasFocus = false
                        headerHasFocus = false
                        focusedRowIndex = 0
                        scope.launch {
                            try {
                                val continueWatchingIndex = (if (showHero) 1 else 0) + (if (showStreamingPlatforms) 1 else 0)
                                listState.animateScrollToItem(continueWatchingIndex)
                            } catch (_: Exception) {}
                            delay(100)
                            try {
                                firstCardFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    val navigateUpFromFirstRow = {
                        if (continueWatching.isNotEmpty()) {
                            focusContinueWatching()
                        } else if (showStreamingPlatforms) {
                            focusStreamingPlatforms()
                        } else if (showHero) {
                            heroHasFocus = true
                            scope.launch {
                                try {
                                    listState.animateScrollToItem(0)
                                } catch (_: Exception) {}
                                delay(100)
                                try {
                                    heroFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                        } else {
                            headerHasFocus = true
                            scope.launch {
                                try {
                                    listState.animateScrollToItem(0)
                                } catch (_: Exception) {}
                                delay(100)
                                try {
                                    headerFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    val cardsAlpha = 1f
                    
                    // Foco inicial en el header solo al inicio de la app
                    var hasInitialized by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        if (!hasInitialized) {
                            headerHasFocus = true
                            headerFocusRequester.requestFocus()
                            hasInitialized = true
                        }
                    }
                    
                    // Manejo de botón Atrás en control remoto:
                    // 1. Cerrar sidebar si está abierto
                    // 2. Si está en el contenido (hero, plataformas o cards), hacer scroll al inicio y enfocar el header
                    // 3. Si está en otra pestaña y en el header, volver a Home
                    // 4. Si está en Home y en el header, permitir que el Activity maneje el doble retroceso para salir
                    BackHandler(enabled = sidebarOpen || !headerHasFocus || activeInterface != InterfaceMode.HOME) {
                        if (sidebarOpen) {
                            sidebarOpen = false
                            headerHasFocus = true
                            scope.launch {
                                try { headerFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        } else if (!headerHasFocus) {
                            headerHasFocus = true
                            scope.launch {
                                try {
                                    listState.scrollToItem(0)
                                } catch (_: Exception) {}
                                for (d in listOf(30L, 80L, 150L)) {
                                    delay(d)
                                    try {
                                        headerFocusRequester.requestFocus()
                                        break
                                    } catch (_: Exception) {}
                                }
                            }
                        } else if (activeInterface != InterfaceMode.HOME) {
                            viewModel.setActiveInterface(InterfaceMode.HOME)
                        }
                    }
                    
                    val headerAlpha by animateFloatAsState(
                        targetValue = if (isNetflixStyle || headerHasFocus) 1f else 0f,
                        animationSpec = tween(250),
                        label = "headerAlpha"
                    )
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val headerTranslationY by animateDpAsState(
                        targetValue = if (isNetflixStyle || headerHasFocus) 0.dp else (-100).dp,
                        animationSpec = tween(250),
                        label = "headerTranslationY"
                    )

                    // HEADER FIJO - Siempre mostrar el header normal por encima
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .zIndex(2f)
                            .graphicsLayer {
                                alpha = headerAlpha
                                translationY = with(density) { headerTranslationY.toPx() }
                            }
                    ) {
                        GamingStyleHeader(
                            selectedProfile = selectedProfile,
                            activeTab = when (activeInterface) {
                                InterfaceMode.HOME -> 1
                                InterfaceMode.SERIES -> 2
                                InterfaceMode.MOVIES -> 3
                                InterfaceMode.LIVE -> 4
                                InterfaceMode.CATEGORIES -> 5
                                InterfaceMode.MY_LIST -> 6
                                else -> 7 // Tab de búsqueda activo
                            },
                            onTabSelected = { index ->
                                when (index) {
                                    1 -> viewModel.setActiveInterface(InterfaceMode.HOME)
                                    2 -> viewModel.setActiveInterface(InterfaceMode.SERIES)
                                    3 -> viewModel.setActiveInterface(InterfaceMode.MOVIES)
                                    4 -> viewModel.setActiveInterface(InterfaceMode.LIVE)
                                    5 -> viewModel.setActiveInterface(InterfaceMode.CATEGORIES)
                                    6 -> {
                                        viewModel.setActiveInterface(InterfaceMode.MY_LIST)
                                        if (userId.isNotEmpty() && selectedProfile != null) {
                                            viewModel.loadWatchlist(userId, selectedProfile.id)
                                        }
                                    }
                                    7 -> onNavigateToSearch() // Navegar a SearchScreen
                                }
                            },

                            onProfilesClick = onNavigateToProfiles,
                            onFocusChanged = { focused ->
                                headerHasFocus = focused
                            },
                            headerFocusRequester = headerFocusRequester,
                            onOpenSidebar = {
                                sidebarOpen = true
                            },
                            onNavigateDown = {
                                headerHasFocus = false
                                if (showHero) {
                                    scope.launch {
                                        try {
                                            heroFocusRequester.requestFocus()
                                        } catch (e: Exception) {
                                            if (showStreamingPlatforms) {
                                                focusStreamingPlatforms()
                                            } else {
                                                focusFirstCards()
                                            }
                                        }
                                    }
                                } else if (showStreamingPlatforms) {
                                    focusStreamingPlatforms()
                                } else {
                                    focusFirstCards()
                                }
                            },
                            isSidebarOnly = isSidebarOnly,
                            tabEnabled = tabEnabled
                        )
                    }



                    // SIDEBAR FLLOTANTE
                    FloatingSidebar(
                        selectedProfile = selectedProfile,
                        activeInterface = activeInterface,
                        isVisible = sidebarOpen,
                        onDismiss = {
                            sidebarOpen = false
                            headerHasFocus = true
                            scope.launch {
                                try { headerFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        },
                        onTabSelected = { index ->
                            when (index) {
                                1 -> viewModel.setActiveInterface(InterfaceMode.HOME)
                                2 -> viewModel.setActiveInterface(InterfaceMode.SERIES)
                                3 -> viewModel.setActiveInterface(InterfaceMode.MOVIES)
                                4 -> viewModel.setActiveInterface(InterfaceMode.LIVE)
                                5 -> viewModel.setActiveInterface(InterfaceMode.CATEGORIES)
                                6 -> {
                                    viewModel.setActiveInterface(InterfaceMode.MY_LIST)
                                    if (userId.isNotEmpty() && selectedProfile != null) {
                                        viewModel.loadWatchlist(userId, selectedProfile.id)
                                    }
                                }
                                7 -> onNavigateToSearch()
                            }
                        },
                        onProfilesClick = onNavigateToProfiles,
                        onConfigClick = onNavigateToConfig,
                        tabEnabled = tabEnabled
                    )

                    // CONTENIDO SCROLLABLE - Cards con scroll para mostrar primera fila
                    // listState is defined in outer scope
                    
                    // showHero is defined in outer scope
                    

                    
                    val dynamicPivot = if (headerHasFocus || heroHasFocus) PivotOffsets(0.9f) else PivotOffsets(0.6f, 0f)
                    
                    TvLazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                            .graphicsLayer { clip = false },
                        contentPadding = PaddingValues(
                            top = if (isNetflixStyle) 90.dp else if (showHero) 0.dp else 90.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        pivotOffsets = dynamicPivot
                    ) {
                        
                        if (showHero) {
                            item(key = "hero_area") {
                                val heroHeight = if (isNetflixStyle) 380.dp else 540.dp

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (isNetflixStyle) Modifier.padding(horizontal = 32.dp, vertical = 16.dp) else Modifier)
                                        .height(heroHeight)
                                        .then(if (isNetflixStyle) Modifier.clip(RoundedCornerShape(20.dp)) else Modifier)
                                        .onFocusChanged {
                                            heroHasFocus = it.hasFocus
                                            if (it.hasFocus) focusedCardContent = null
                                        }
                                ) {
                                    // BACKDROP IMAGE inside the hero area Box
                                    if (hasBackdrop) {
                                         SubcomposeAsyncImage(
                                             model = ImageRequest.Builder(LocalContext.current)
                                                 .data(backdropUrl)
                                                 .memoryCachePolicy(CachePolicy.ENABLED)
                                                 .diskCachePolicy(CachePolicy.ENABLED)
                                                 .crossfade(true)
                                                 .build(),
                                             contentDescription = null,
                                             loading = {
                                                 Box(modifier = Modifier.fillMaxSize())
                                             },
                                             error = {
                                                 Box(
                                                     modifier = Modifier.fillMaxSize(),
                                                     contentAlignment = Alignment.Center
                                                 ) {
                                                     Text(
                                                         text = "❌",
                                                         fontSize = 48.sp,
                                                         color = Color.White.copy(alpha = 0.3f)
                                                     )
                                                 }
                                             },
                                             modifier = Modifier.fillMaxSize(),
                                             contentScale = ContentScale.Crop,
                                             alpha = 1f
                                         )
                                         
                                         // Gradient overlay inside the hero Box to fade to black/background
                                         Box(
                                             modifier = Modifier
                                                 .fillMaxSize()
                                                 .background(
                                                     Brush.verticalGradient(
                                                         colors = listOf(
                                                             Color.Black.copy(alpha = 0.3f), // 30% negro en la parte superior
                                                             Color.Black.copy(alpha = 0.1f), // 10% negro en el centro
                                                             Color.Black.copy(alpha = 1.0f)  // 100% negro en la parte inferior para fusionarse con el fondo
                                                         )
                                                     )
                                                 )
                                         )
                                    }

                                    val overlayItem = backdropContent ?: currentHeroItem!!
                                    CompactContentOverlay(
                                        content = overlayItem,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomStart)
                                            .padding(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 56.dp),
                                        isInWatchlist = watchlist.any { 
                                            val itemTmdbId = (overlayItem as? Content.Movie)?.tmdbId ?: (overlayItem as? Content.Series)?.tmdbId ?: 0
                                            it.id == overlayItem.id || (itemTmdbId != 0 && it.tmdbId == itemTmdbId) 
                                        },
                                        heroFocusRequester = heroFocusRequester,
                                        headerFocusRequester = headerFocusRequester,
                                        showButtons = true,
                                        onPlayClick = { onPlayContent(overlayItem, null) },
                                        onCastClick = { onNavigateToCast(overlayItem) },
                                        onWatchlistClick = {
                                            if (userId.isNotEmpty() && selectedProfile != null) {
                                                viewModel.toggleWatchlist(userId, selectedProfile.id, overlayItem)
                                            }
                                        },
                                        onPreviousClick = { viewModel.goToPreviousFeatured() },
                                        onNextClick = { viewModel.goToNextFeatured() },
                                        onNavigateDown = {
                                            if (showStreamingPlatforms) {
                                                focusStreamingPlatforms()
                                            } else {
                                                focusFirstCards()
                                            }
                                        },
                                        isLiveChannel = activeInterface == InterfaceMode.LIVE
                                    )
                                    
                                    // Indicador de carrusel (puntitos)
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = false,
                                        enter = fadeIn(tween(300)),
                                        exit = fadeOut(tween(300)),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 24.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val maxDots = minOf(heroContent.size, 5) // Mostrar máximo 5 puntos
                                            for (i in 0 until maxDots) {
                                                val isSelected = i == (currentFeaturedIndex % maxDots)
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (isSelected) 8.dp else 6.dp)
                                                        .background(
                                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                                            shape = androidx.compose.foundation.shape.CircleShape
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // FILA DE PLATAFORMAS DE STREAMING (ENTRE HERO Y CARDS)
                        if (showStreamingPlatforms) {
                            item(key = "streaming_platforms_section") {
                                StreamingPlatformsRow(
                                    onPlatformSelected = onNavigateToPlatform,
                                    firstPlatformFocusRequester = streamingFocusRequester,
                                    onNavigateUp = {
                                        if (showHero) {
                                            heroHasFocus = true
                                            headerHasFocus = false
                                            scope.launch {
                                                try {
                                                    listState.animateScrollToItem(0)
                                                    delay(100)
                                                    heroFocusRequester.requestFocus()
                                                } catch (_: Exception) {
                                                    try {
                                                        headerFocusRequester.requestFocus()
                                                    } catch (_: Exception) {
                                                        // ignore
                                                    }
                                                }
                                            }
                                        } else {
                                            headerHasFocus = true
                                            try {
                                                headerFocusRequester.requestFocus()
                                            } catch (_: Exception) {
                                                // ignore
                                            }
                                        }
                                    },
                                    onNavigateDown = {
                                        if (continueWatching.isNotEmpty()) {
                                            focusContinueWatching()
                                        } else {
                                            focusFirstCards()
                                        }
                                    }
                                )
                            }
                        }
                        
                        // CONTENIDO SEGÚN PESTAÑA ACTIVA
                        when (activeInterface) {
                            InterfaceMode.HOME -> {
                                if (continueWatching.isNotEmpty()) {
                                    item(key = "continue_watching_section") {
                                        ContinueWatchingRow(
                                            items = continueWatching,
                                            allMovies = movies,
                                            allSeries = series,
                                            onContentSelected = onContentSelected,
                                            onPlayMovie = { movie, resumePos ->
                                                (context as? android.app.Activity)?.let { act ->
                                                    com.anfhly.streaming.PlayerActivity.start(
                                                        activity = act,
                                                        content = movie,
                                                        episode = null,
                                                        resumePositionMs = resumePos
                                                    )
                                                }
                                            },
                                            onContentFocused = { content ->
                                                focusedCardContent = content
                                            },
                                            firstCardFocusRequester = firstCardFocusRequester,
                                            onNavigateUp = {
                                                if (showStreamingPlatforms) {
                                                    focusStreamingPlatforms()
                                                } else if (showHero) {
                                                    heroHasFocus = true
                                                    scope.launch {
                                                        try {
                                                            listState.animateScrollToItem(0)
                                                        } catch (_: Exception) {}
                                                        delay(100)
                                                        try {
                                                            heroFocusRequester.requestFocus()
                                                        } catch (_: Exception) {}
                                                    }
                                                } else {
                                                    headerHasFocus = true
                                                    scope.launch {
                                                        try {
                                                            headerFocusRequester.requestFocus()
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .alpha(cardsAlpha)
                                                .onFocusChanged {
                                                    if (it.hasFocus) {
                                                        focusedRowIndex = 0
                                                        headerHasFocus = false
                                                    }
                                                }
                                        )
                                    }
                                }

                                items(categories.size) { index ->
                                    val category = categories[index]
                                    val isFirstRow = continueWatching.isEmpty() && index == 0
                                    val rowIndex = if (continueWatching.isNotEmpty()) index + 1 else index

                                    ContentRowGamingStyle(
                                        title = category.title,
                                        items = category.items,
                                        onContentSelected = onContentSelected,
                                        onContentFocused = { content ->
                                            focusedCardContent = content
                                        },
                                        isFocused = focusedRowIndex == rowIndex,
                                        onFocusChange = { 
                                            focusedRowIndex = rowIndex
                                            headerHasFocus = false
                                        },
                                        showTitle = true,
                                        useBackdrop = category.title.contains("Tendencia", ignoreCase = true) || category.title.contains("Top 10", ignoreCase = true),
                                        isTrending = category.title.contains("Tendencia", ignoreCase = true) || category.title.contains("Top 10", ignoreCase = true),
                                        firstCardFocusRequester = if (isFirstRow) firstCardFocusRequester else null,
                                        modifier = Modifier
                                            .alpha(cardsAlpha)
                                            .onFocusChanged {
                                                if (it.hasFocus) {
                                                    focusedRowIndex = rowIndex
                                                    headerHasFocus = false
                                                }
                                            }
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && 
                                                    event.key == androidx.compose.ui.input.key.Key.DirectionUp && 
                                                    rowIndex == 0) {
                                                    // Navegar a plataformas / hero / header desde la primera fila
                                                    navigateUpFromFirstRow()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                    )
                                }
                            }
                            InterfaceMode.MOVIES -> {
                                if (movies.isNotEmpty()) {
                                    item {
                                        val sortedMovies = remember(movies) {
                                            movies.sortedByDescending { movie ->
                                                val added = movie.addedAt ?: java.util.Date(0)
                                                val repaired = movie.repairedAt ?: java.util.Date(0)
                                                val updated = movie.updatedAt ?: java.util.Date(0)
                                                maxOf(added, repaired, updated)
                                            }
                                        }
                                        ContentRowGamingStyle(
                                            title = "Todas las Películas",
                                            items = sortedMovies,
                                            onContentSelected = onContentSelected,
                                            onContentFocused = { content ->
                                                focusedCardContent = content
                                            },
                                            isFocused = focusedRowIndex == 0,
                                            onFocusChange = { 
                                                focusedRowIndex = 0
                                                headerHasFocus = false
                                            },
                                            showTitle = false,
                                            firstCardFocusRequester = firstCardFocusRequester,
                                            modifier = Modifier
                                                .alpha(cardsAlpha)
                                                .onFocusChanged {
                                                    if (it.hasFocus) {
                                                        focusedRowIndex = 0
                                                        headerHasFocus = false
                                                    }
                                                }
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && 
                                                        event.key == androidx.compose.ui.input.key.Key.DirectionUp && 
                                                        focusedRowIndex == 0) {
                                                        navigateUpFromFirstRow()
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                            InterfaceMode.CATEGORIES -> {
                                // Películas por categoría
                                items(moviesCategories.size) { index ->
                                    val category = moviesCategories[index]
                                    val isFirstRow = index == 0
                                    
                                    ContentRowGamingStyle(
                                        title = "Películas: ${category.title}",
                                        items = category.items,
                                        onContentSelected = onContentSelected,
                                        onContentFocused = { content ->
                                            focusedCardContent = content
                                        },
                                        isFocused = focusedRowIndex == index,
                                        onFocusChange = { 
                                            focusedRowIndex = index
                                            headerHasFocus = false
                                        },
                                        useBackdrop = true,
                                        isCategoryCard = true,
                                        firstCardFocusRequester = if (isFirstRow) firstCardFocusRequester else null,
                                        modifier = Modifier
                                            .alpha(cardsAlpha)
                                            .onFocusChanged {
                                                if (it.hasFocus) {
                                                    focusedRowIndex = index
                                                    headerHasFocus = false
                                                }
                                            }
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && 
                                                    event.key == androidx.compose.ui.input.key.Key.DirectionUp && 
                                                    index == 0) {
                                                    navigateUpFromFirstRow()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                    )
                                }
                                
                                // Series por categoría
                                items(seriesCategories.size) { index ->
                                    val category = seriesCategories[index]
                                    val adjustedIndex = index + moviesCategories.size
                                    
                                    ContentRowGamingStyle(
                                        title = "Series: ${category.title}",
                                        items = category.items,
                                        onContentSelected = onContentSelected,
                                        onContentFocused = { content ->
                                            focusedCardContent = content
                                        },
                                        isFocused = focusedRowIndex == adjustedIndex,
                                        onFocusChange = { 
                                            focusedRowIndex = adjustedIndex
                                            headerHasFocus = false
                                        },
                                        useBackdrop = true,
                                        isCategoryCard = true,
                                        firstCardFocusRequester = if (adjustedIndex == 0) firstCardFocusRequester else null,
                                        modifier = Modifier
                                            .alpha(cardsAlpha)
                                            .onFocusChanged {
                                                if (it.hasFocus) {
                                                    focusedRowIndex = adjustedIndex
                                                    headerHasFocus = false
                                                }
                                            }
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && 
                                                    event.key == androidx.compose.ui.input.key.Key.DirectionUp && 
                                                    adjustedIndex == 0) {
                                                    navigateUpFromFirstRow()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                    )
                                }
                            }
                            InterfaceMode.SERIES -> {
                                if (series.isNotEmpty()) {
                                    item {
                                        val sortedSeries = remember(series) {
                                            series.sortedByDescending { serie ->
                                                val added = serie.addedAt ?: java.util.Date(0)
                                                val repaired = serie.repairedAt ?: java.util.Date(0)
                                                val updated = serie.updatedAt ?: java.util.Date(0)
                                                maxOf(added, repaired, updated)
                                            }
                                        }
                                        ContentRowGamingStyle(
                                            title = "Todas las Series",
                                            items = sortedSeries,
                                            onContentSelected = onContentSelected,
                                            onContentFocused = { content ->
                                                focusedCardContent = content
                                            },
                                            isFocused = focusedRowIndex == 0,
                                            onFocusChange = { 
                                                focusedRowIndex = 0
                                                headerHasFocus = false
                                            },
                                            showTitle = false,
                                            firstCardFocusRequester = firstCardFocusRequester,
                                            modifier = Modifier
                                                .alpha(cardsAlpha)
                                                .onFocusChanged {
                                                    if (it.hasFocus) {
                                                        focusedRowIndex = 0
                                                        headerHasFocus = false
                                                    }
                                                }
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && 
                                                        event.key == androidx.compose.ui.input.key.Key.DirectionUp && 
                                                        focusedRowIndex == 0) {
                                                        navigateUpFromFirstRow()
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                            InterfaceMode.MY_LIST -> {
                                if (watchlist.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(300.dp)
                                                .focusable()
                                                .focusRequester(firstCardFocusRequester)
                                                .onFocusChanged {
                                                    if (it.hasFocus) {
                                                        headerHasFocus = false
                                                    }
                                                }
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && 
                                                        event.key == androidx.compose.ui.input.key.Key.DirectionUp) {
                                                        navigateUpFromFirstRow()
                                                        true
                                                    } else false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Icon(
                                                        imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                                Text(
                                                    text = "Aún no has agregado contenido a Mi Lista",
                                                    fontSize = 20.sp,
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    item {
                                        val watchlistContents = watchlist.map { item ->
                                            val actualContent = movies.find { 
                                                 it.id == item.id || 
                                                 it.id == item.firebaseId || 
                                                 (it.tmdbId != null && it.tmdbId != 0 && it.tmdbId == item.tmdbId) 
                                             } ?: series.find { 
                                                 it.id == item.id || 
                                                 it.id == item.firebaseId || 
                                                 (it.tmdbId != null && it.tmdbId != 0 && it.tmdbId == item.tmdbId) 
                                             }
                                            
                                            
                                            actualContent ?: if (item.type == "movie" || item.type.isEmpty()) {
                                                Content.Movie(
                                                    id = item.id,
                                                    tmdbId = item.tmdbId,
                                                    title = item.displayName,
                                                    overview = item.overview ?: "",
                                                    posterUrl = item.posterUrl,
                                                    backdropUrl = item.backdropUrl,
                                                    logoUrl = item.logoUrl,
                                                    backdropPublicityUrl = item.backdropPublicityUrl,
                                                    voteAverage = item.vote_average ?: 0.0,
                                                    releaseDate = item.release_date
                                                )
                                            } else {
                                                Content.Series(
                                                    id = item.id,
                                                    tmdbId = item.tmdbId,
                                                    title = item.displayName,
                                                    overview = item.overview ?: "",
                                                    posterUrl = item.posterUrl,
                                                    backdropUrl = item.backdropUrl,
                                                    logoUrl = item.logoUrl,
                                                    backdropPublicityUrl = item.backdropPublicityUrl,
                                                    voteAverage = item.vote_average ?: 0.0,
                                                    releaseDate = item.first_air_date ?: item.release_date
                                                )
                                            }
                                        }

                                        ContentRowGamingStyle(
                                            title = "Mi Lista",
                                            items = watchlistContents,
                                            onContentSelected = onContentSelected,
                                            onContentFocused = { content ->
                                                focusedCardContent = content
                                            },
                                            isFocused = true,
                                            onFocusChange = {
                                                headerHasFocus = false
                                            },
                                            showTitle = false,
                                            firstCardFocusRequester = firstCardFocusRequester,
                                            modifier = Modifier
                                                .alpha(cardsAlpha)
                                                .onFocusChanged {
                                                    if (it.hasFocus) {
                                                        headerHasFocus = false
                                                    }
                                                }
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && 
                                                        event.key == androidx.compose.ui.input.key.Key.DirectionUp) {
                                                        navigateUpFromFirstRow()
                                                        true
                                                    } else false
                                                }
                                        )
                                    }
                                }
                            }
                            InterfaceMode.LIVE -> {
                                if (channelsCategories.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(300.dp)
                                                .focusable()
                                                .focusRequester(firstCardFocusRequester)
                                                .onFocusChanged {
                                                    if (it.hasFocus) {
                                                        headerHasFocus = false
                                                    }
                                                }
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && 
                                                        event.key == androidx.compose.ui.input.key.Key.DirectionUp) {
                                                        navigateUpFromFirstRow()
                                                        true
                                                    } else false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.pelota),
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                                Text(
                                                    text = "No hay canales disponibles en este momento",
                                                    fontSize = 20.sp,
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(channelsCategories.size) { index ->
                                        val category = channelsCategories[index]
                                        val isFirstRow = index == 0
                                        
                                        ContentRowGamingStyle(
                                            title = category.title,
                                            items = category.items,
                                            onContentSelected = { content -> onPlayContent(content, null) },
                                            onContentFocused = { content ->
                                                focusedCardContent = content
                                            },
                                            isFocused = focusedRowIndex == index,
                                            onFocusChange = { 
                                                focusedRowIndex = index
                                                headerHasFocus = false
                                            },
                                            showTitle = true,
                                            useBackdrop = true,
                                             isChannel = true,
                                            firstCardFocusRequester = if (isFirstRow) firstCardFocusRequester else null,
                                            modifier = Modifier
                                                .alpha(cardsAlpha)
                                                .onFocusChanged {
                                                    if (it.hasFocus) {
                                                        focusedRowIndex = index
                                                        headerHasFocus = false
                                                    }
                                                }
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && 
                                                        event.key == androidx.compose.ui.input.key.Key.DirectionUp && 
                                                        focusedRowIndex == 0) {
                                                        navigateUpFromFirstRow()
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                            InterfaceMode.SHOWS, InterfaceMode.SEARCH, InterfaceMode.SETTINGS -> {
                                // Modos no implementados - mostrar placeholder
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Modo no disponible",
                                            fontSize = 24.sp,
                                            color = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(64.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
            }
        }
    } // End of Crossfade block
}
}
}


