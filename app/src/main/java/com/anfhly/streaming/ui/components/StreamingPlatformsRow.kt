package com.anfhly.streaming.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.anfhly.streaming.R

data class StreamingPlatform(
    val id: String,
    val name: String,
    val brandColor: Color,
    val gradientColors: List<Color>,
    @DrawableRes val drawableRes: Int
)

val streamingPlatformsList = listOf(
    StreamingPlatform(
        id = "netflix",
        name = "Netflix",
        brandColor = Color(0xFFE50914),
        gradientColors = listOf(Color(0xFF140304), Color(0xFF240A0C)),
        drawableRes = R.drawable.netflix_2x
    ),
    StreamingPlatform(
        id = "disney",
        name = "Disney+",
        brandColor = Color(0xFF113CCF),
        gradientColors = listOf(Color(0xFF040714), Color(0xFF0B1A45)),
        drawableRes = R.drawable.disney_2x
    ),
    StreamingPlatform(
        id = "hbo",
        name = "HBO Max",
        brandColor = Color(0xFF9E00FF),
        gradientColors = listOf(Color(0xFF0F041F), Color(0xFF220A40)),
        drawableRes = R.drawable.max1
    ),
    StreamingPlatform(
        id = "prime",
        name = "Prime Video",
        brandColor = Color(0xFF00A8E1),
        gradientColors = listOf(Color(0xFF021726), Color(0xFF062C47)),
        drawableRes = R.drawable.prime_video_2x
    ),
    StreamingPlatform(
        id = "universal",
        name = "Universal+",
        brandColor = Color(0xFF00A3E0),
        gradientColors = listOf(Color(0xFF02162B), Color(0xFF072E54)),
        drawableRes = R.drawable.universal_plus
    ),
    StreamingPlatform(
        id = "appletv",
        name = "Apple TV+",
        brandColor = Color(0xFFE5E5EA),
        gradientColors = listOf(Color(0xFF141416), Color(0xFF28282C)),
        drawableRes = R.drawable.apple_tv_2x
    ),
    StreamingPlatform(
        id = "crunchyroll",
        name = "Crunchyroll",
        brandColor = Color(0xFFFF6400),
        gradientColors = listOf(Color(0xFF1A0E04), Color(0xFF331B08)),
        drawableRes = R.drawable.crunchyroll
    ),
    StreamingPlatform(
        id = "paramount",
        name = "Paramount+",
        brandColor = Color(0xFF1B4DB1),
        gradientColors = listOf(Color(0xFF0A0E1F), Color(0xFF14244F)),
        drawableRes = R.drawable.paramount_2x
    )
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamingPlatformsRow(
    onPlatformSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    firstPlatformFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    TvLazyRow(
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                onFocusChanged?.invoke(focusState.hasFocus)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        onNavigateUp?.invoke()
                        onNavigateUp != null
                    }
                    Key.DirectionDown -> {
                        onNavigateDown?.invoke()
                        onNavigateDown != null
                    }
                    else -> false
                }
            }
    ) {
        itemsIndexed(streamingPlatformsList, key = { _, it -> it.id }) { index, platform ->
            PlatformButton(
                platform = platform,
                onClick = { onPlatformSelected(platform.id) },
                modifier = if (index == 0 && firstPlatformFocusRequester != null) {
                    Modifier.focusRequester(firstPlatformFocusRequester)
                } else {
                    Modifier
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlatformButton(
    platform: StreamingPlatform,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.25f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        modifier = modifier
            .width(100.dp)
            .height(65.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // SOLO LA IMAGEN DE LA PLATAFORMA (SIN CUADRADO, SIN FONDO, SIN BORDE)
            Image(
                painter = painterResource(id = platform.drawableRes),
                contentDescription = platform.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isFocused) 1.0f else 0.70f
                    }
            )
        }
    }
}
