package com.anfhly.streaming.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import com.anfhly.streaming.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.ui.components.AppBackground
import kotlinx.coroutines.delay

private data class PlatformInfo(
    val name: String,
    val brandColor: Color,
    val description: String,
    val drawableRes: Int? = null
)

private enum class PlatformContentFilter {
    ALL, MOVIES, SERIES
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlatformMoviesScreen(
    platformId: String,
    allMovies: List<Content.Movie>,
    allSeries: List<Content.Series> = emptyList(),
    onContentSelected: (Content) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val platformInfo = remember(platformId) {
        when (platformId.lowercase()) {
            "netflix" -> PlatformInfo("Netflix", Color(0xFFE50914), "Películas y series exclusivas de Netflix", R.drawable.netflix)
            "disney" -> PlatformInfo("Disney+", Color(0xFF113CCF), "Lo mejor de Disney, Pixar, Marvel y Star Wars", R.drawable.disney)
            "hbo" -> PlatformInfo("HBO Max", Color(0xFF9E00FF), "Cine de Warner Bros, DC y producciones HBO", R.drawable.hbo_max)
            "prime" -> PlatformInfo("Prime Video", Color(0xFF00A8E1), "Cine y series originales de Amazon Prime", R.drawable.prime_video)
            "universal" -> PlatformInfo("Universal+", Color(0xFF00A3E0), "Grandes éxitos de Universal Studios e Illumination", R.drawable.universal)
            "appletv", "apple" -> PlatformInfo("Apple TV+", Color(0xFFE5E5EA), "Producciones Apple Original y cine exclusivo", R.drawable.appletv)
            "crunchyroll" -> PlatformInfo("Crunchyroll", Color(0xFFFF6400), "El catálogo de animación y anime", R.drawable.crunchyroll)
            "paramount" -> PlatformInfo("Paramount+", Color(0xFF1B4DB1), "Grandes éxitos de Paramount Pictures y Nickelodeon", R.drawable.paramount)
            else -> PlatformInfo(platformId.replaceFirstChar { it.uppercase() }, Color.White, "Contenido de la plataforma", null)
        }
    }
    val platformName = platformInfo.name
    val platformDrawable = platformInfo.drawableRes

    // Filtrar películas por plataforma
    val filteredMovies = remember(allMovies, platformId) {
        val directMatches = allMovies.filter { movie ->
            val p = movie.platform?.lowercase()
            p == platformId.lowercase() || 
            (platformId.startsWith("apple") && p?.contains("apple") == true) ||
            (platformId.equals("prime", ignoreCase = true) && p?.contains("prime") == true) ||
            (platformId.equals("paramount", ignoreCase = true) && p?.contains("paramount") == true)
        }

        if (directMatches.isNotEmpty()) {
            directMatches
        } else {
            // Fallback inteligente si aún no tienen el campo `platform` en Firestore
            allMovies.filter { movie ->
                when (platformId.lowercase()) {
                    "netflix" -> {
                        movie.title.contains("netflix", ignoreCase = true) ||
                        movie.overview.contains("netflix", ignoreCase = true) ||
                        movie.genres.any { it.contains("netflix", ignoreCase = true) }
                    }
                    "disney" -> {
                        movie.title.contains("disney", ignoreCase = true) ||
                        movie.overview.contains("disney", ignoreCase = true) ||
                        movie.overview.contains("pixar", ignoreCase = true) ||
                        movie.overview.contains("marvel", ignoreCase = true) ||
                        movie.genres.any { it.contains("disney", ignoreCase = true) || it.contains("infantil", ignoreCase = true) }
                    }
                    "hbo" -> {
                        movie.title.contains("hbo", ignoreCase = true) ||
                        movie.overview.contains("hbo", ignoreCase = true) ||
                        movie.overview.contains("warner", ignoreCase = true) ||
                        movie.genres.any { it.contains("hbo", ignoreCase = true) }
                    }
                    "prime" -> {
                        movie.title.contains("prime", ignoreCase = true) ||
                        movie.title.contains("amazon", ignoreCase = true) ||
                        movie.overview.contains("prime", ignoreCase = true) ||
                        movie.genres.any { it.contains("prime", ignoreCase = true) }
                    }
                    "crunchyroll" -> {
                        movie.title.contains("crunchyroll", ignoreCase = true) ||
                        movie.overview.contains("crunchyroll", ignoreCase = true) ||
                        movie.genres.any { it.contains("anime", ignoreCase = true) || it.contains("animación", ignoreCase = true) || it.contains("animacion", ignoreCase = true) }
                    }
                    "appletv", "apple" -> {
                        movie.title.contains("apple", ignoreCase = true) ||
                        movie.overview.contains("apple", ignoreCase = true) ||
                        movie.genres.any { it.contains("apple", ignoreCase = true) }
                    }
                    "universal" -> {
                        movie.title.contains("universal", ignoreCase = true) ||
                        movie.overview.contains("universal", ignoreCase = true) ||
                        movie.overview.contains("illumination", ignoreCase = true) ||
                        movie.overview.contains("dreamworks", ignoreCase = true) ||
                        movie.genres.any { it.contains("universal", ignoreCase = true) }
                    }
                    "paramount" -> {
                        movie.title.contains("paramount", ignoreCase = true) ||
                        movie.overview.contains("paramount", ignoreCase = true) ||
                        movie.overview.contains("nickelodeon", ignoreCase = true) ||
                        movie.overview.contains("mtv", ignoreCase = true) ||
                        movie.overview.contains("viacom", ignoreCase = true) ||
                        movie.genres.any { it.contains("paramount", ignoreCase = true) }
                    }
                    else -> false
                }
            }
        }
    }

    // Filtrar series por plataforma
    val filteredSeries = remember(allSeries, platformId) {
        val directMatches = allSeries.filter { serie ->
            val p = serie.platform?.lowercase()
            p == platformId.lowercase() || 
            (platformId.startsWith("apple") && p?.contains("apple") == true) ||
            (platformId.equals("prime", ignoreCase = true) && p?.contains("prime") == true) ||
            (platformId.equals("paramount", ignoreCase = true) && p?.contains("paramount") == true)
        }

        if (directMatches.isNotEmpty()) {
            directMatches
        } else {
            allSeries.filter { serie ->
                when (platformId.lowercase()) {
                    "netflix" -> {
                        serie.title.contains("netflix", ignoreCase = true) ||
                        serie.overview.contains("netflix", ignoreCase = true) ||
                        serie.genres.any { it.contains("netflix", ignoreCase = true) }
                    }
                    "disney" -> {
                        serie.title.contains("disney", ignoreCase = true) ||
                        serie.overview.contains("disney", ignoreCase = true) ||
                        serie.genres.any { it.contains("disney", ignoreCase = true) }
                    }
                    "hbo" -> {
                        serie.title.contains("hbo", ignoreCase = true) ||
                        serie.overview.contains("hbo", ignoreCase = true) ||
                        serie.genres.any { it.contains("hbo", ignoreCase = true) }
                    }
                    "prime" -> {
                        serie.title.contains("prime", ignoreCase = true) ||
                        serie.title.contains("amazon", ignoreCase = true) ||
                        serie.overview.contains("prime", ignoreCase = true) ||
                        serie.genres.any { it.contains("prime", ignoreCase = true) }
                    }
                    "crunchyroll" -> {
                        serie.title.contains("crunchyroll", ignoreCase = true) ||
                        serie.genres.any { it.contains("anime", ignoreCase = true) || it.contains("animación", ignoreCase = true) }
                    }
                    "appletv", "apple" -> {
                        serie.title.contains("apple", ignoreCase = true) ||
                        serie.genres.any { it.contains("apple", ignoreCase = true) }
                    }
                    "universal" -> {
                        serie.title.contains("universal", ignoreCase = true) ||
                        serie.genres.any { it.contains("universal", ignoreCase = true) }
                    }
                    "paramount" -> {
                        serie.title.contains("paramount", ignoreCase = true) ||
                        serie.overview.contains("paramount", ignoreCase = true) ||
                        serie.overview.contains("nickelodeon", ignoreCase = true) ||
                        serie.overview.contains("mtv", ignoreCase = true) ||
                        serie.overview.contains("viacom", ignoreCase = true) ||
                        serie.genres.any { it.contains("paramount", ignoreCase = true) }
                    }
                    else -> false
                }
            }
        }
    }

    var selectedFilter by remember { mutableStateOf(PlatformContentFilter.ALL) }

    val displayContent = remember(filteredMovies, filteredSeries, selectedFilter) {
        when (selectedFilter) {
            PlatformContentFilter.ALL -> (filteredMovies as List<Content>) + (filteredSeries as List<Content>)
            PlatformContentFilter.MOVIES -> filteredMovies
            PlatformContentFilter.SERIES -> filteredSeries
        }
    }

    val firstCardFocus = remember { FocusRequester() }
    var currentBackdropUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(displayContent.size) {
        delay(150)
        try {
            if (displayContent.isNotEmpty()) {
                firstCardFocus.requestFocus()
            }
        } catch (_: Exception) {}
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppBackground()

        // Fondo dinámico según el contenido enfocado
        if (!currentBackdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentBackdropUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color(0xFF0C0C14).copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 28.dp)
        ) {
            // Header: Logo de plataforma y filtros (si hay películas y series)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (platformDrawable != null) {
                        Image(
                            painter = painterResource(id = platformDrawable),
                            contentDescription = platformName,
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = platformName,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }

                // Selector de pestañas si hay ambos tipos
                if (filteredMovies.isNotEmpty() && filteredSeries.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlatformFilterTab(
                            title = "Todo (${filteredMovies.size + filteredSeries.size})",
                            isSelected = selectedFilter == PlatformContentFilter.ALL,
                            onClick = { selectedFilter = PlatformContentFilter.ALL }
                        )
                        PlatformFilterTab(
                            title = "Películas (${filteredMovies.size})",
                            isSelected = selectedFilter == PlatformContentFilter.MOVIES,
                            onClick = { selectedFilter = PlatformContentFilter.MOVIES }
                        )
                        PlatformFilterTab(
                            title = "Series (${filteredSeries.size})",
                            isSelected = selectedFilter == PlatformContentFilter.SERIES,
                            onClick = { selectedFilter = PlatformContentFilter.SERIES }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (displayContent.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🎬",
                            fontSize = 48.sp
                        )
                        Text(
                            text = "No se encontró contenido para $platformName",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "Puedes asignar el campo 'platform': '$platformId' en tus películas o series de Firestore.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            } else {
                TvLazyVerticalGrid(
                    columns = TvGridCells.Fixed(3),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(displayContent, key = { _, it -> "${it.id}_${it::class.simpleName}" }) { index, item ->
                        Box(
                            modifier = Modifier
                                .width(300.dp)
                                .height(170.dp)
                                .wrapContentSize(Alignment.TopStart)
                        ) {
                            PlatformContentCard(
                                content = item,
                                onClick = { onContentSelected(item) },
                                onFocus = {
                                    val bg = item.backdropUrl ?: item.posterUrl
                                    currentBackdropUrl = bg
                                },
                                modifier = if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier
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
private fun PlatformFilterTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
            focusedContainerColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.Black else Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlatformContentCard(
    content: Content,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardFocusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(isHovered) {
        if (isHovered) {
            try { cardFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Backdrop preferido, fallback a poster
    val backdropUrl = when {
        !content.backdropUrl.isNullOrBlank() -> content.backdropUrl
        !content.backdropPublicityUrl.isNullOrBlank() -> content.backdropPublicityUrl
        else -> content.posterUrl
    }

    // Padding animado: al enfocar la card "crece" eliminando el padding
    val animatedPadding by animateDpAsState(
        targetValue = if (isFocused) 0.dp else 5.dp,
        animationSpec = tween(200),
        label = "cardPadding"
    )

    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxSize()
            .focusRequester(cardFocusRequester)
            .hoverable(interactionSource)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .pointerInput(onClick) {
                detectTapGestures { onClick() }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(animatedPadding)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Imagen de fondo (backdrop o poster)
            if (!backdropUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(backdropUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = content.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF222233))
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF222233))
                        )
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF333344))
                )
            }

            // Oscurecido: sin foco más oscuro, con foco más claro
            val overlayAlpha by animateFloatAsState(
                targetValue = if (isFocused) 0.25f else 0.45f,
                animationSpec = tween(200),
                label = "cardOverlay"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlayAlpha))
            )

            // Distintivo sutil si es Serie
            if (content is Content.Series) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SERIE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Logo centrado (o título como fallback)
            if (!content.logoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(content.logoUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build(),
                    contentDescription = content.title,
                    error = {
                        Text(
                            text = content.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                    },
                    modifier = Modifier.fillMaxSize(0.6f),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = content.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
