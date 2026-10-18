package com.anfhly.streaming.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.ui.viewmodel.ActorFilmographyViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ActorFilmographyScreen(
    actorName: String,
    backgroundUrl: String? = null,
    onNavigateBack: () -> Unit,
    onContentSelected: (Content) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ActorFilmographyViewModel = viewModel(key = actorName) { ActorFilmographyViewModel() }
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(actorName) {
        viewModel.load(actorName)
    }

    val backFocus = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }
    var backFocused by remember { mutableStateOf(false) }
    var currentBackgroundUrl by remember { mutableStateOf(backgroundUrl) }

    BackHandler { onNavigateBack() }

    LaunchedEffect(loading, items.size) {
        delay(150)
        try {
            if (!loading && items.isNotEmpty()) {
                firstCardFocus.requestFocus()
            } else if (!loading) {
                backFocus.requestFocus()
            }
        } catch (_: Exception) {
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppBackground()
        if (!currentBackgroundUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(currentBackgroundUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Quest 3 hover-to-focus support
                val backInteraction = remember { MutableInteractionSource() }
                val isBackHovered by backInteraction.collectIsHoveredAsState()
                LaunchedEffect(isBackHovered) {
                    if (isBackHovered) {
                        try { backFocus.requestFocus() } catch (_: Exception) {}
                    }
                }
                Surface(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .focusRequester(backFocus)
                        .hoverable(backInteraction)
                        .onFocusChanged { backFocused = it.isFocused }
                        .pointerInput(onNavigateBack) {
                            detectTapGestures {
                                onNavigateBack()
                            }
                        },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF3A3A4A),
                        focusedContainerColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(3.dp, Color.White))
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                ) {
                    Text(
                        text = "Volver",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (backFocused) Color.Black else Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Películas y series",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = actorName,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when {
                loading -> {
                    Text(
                        text = "Cargando…",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
                items.isEmpty() -> {
                    Text(
                        text = "No hay más títulos con este actor en el catálogo.",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
                else -> {
                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(end = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        itemsIndexed(
                            items = items,
                            key = { _, item -> "${item.id}_${item::class.simpleName}" }
                        ) { index, item ->
                            FilmographyContentCard(
                                content = item,
                                onClick = { onContentSelected(item) },
                                onFocus = {
                                    val newBg = item.backdropUrl
                                    currentBackgroundUrl = if (!newBg.isNullOrBlank()) newBg else backgroundUrl
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
private fun FilmographyContentCard(
    content: Content,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardFocusRequester = remember { FocusRequester() }
    // Quest 3 hover-to-focus support
    val cardInteraction = remember { MutableInteractionSource() }
    val isCardHovered by cardInteraction.collectIsHoveredAsState()
    LaunchedEffect(isCardHovered) {
        if (isCardHovered) {
            try { cardFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    val imageUrl = content.posterUrl ?: content.backdropUrl

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(300.dp)
            .width(200.dp)
            .focusRequester(cardFocusRequester)
            .hoverable(cardInteraction)
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocus()
                }
            }
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(if (isFocused) 3.dp else 0.dp)
        ) {
            if (!imageUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build(),
                    contentDescription = content.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF555555))
                )
            }
        }
    }
}
