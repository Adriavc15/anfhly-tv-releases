package com.anfhly.streaming.ui.screens

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.anfhly.streaming.ui.components.AppBackground
import androidx.activity.compose.BackHandler
import com.anfhly.streaming.ui.theme.AnfhlyColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AvatarSelectionScreen(
    currentAvatarUrl: String,
    onAvatarSelected: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onSaveProfile: (() -> Unit)? = null // Callback opcional para guardar
) {
    BackHandler {
        onNavigateBack()
    }
    val avatars = listOf(
        "https://preview.redd.it/the-boys-soldier-boy-build-v0-184k9gzbleb91.jpg?width=640&crop=smart&auto=webp&s=2620fe50c1b516db0aa2dae38b6b6bf6186cf797",
        "https://wallpapers.com/images/high/the-boys-bloody-homelander-480j6btuoxiobjlj.jpg",
        "https://images.thedirect.com/media/article_full/atrain.jpg",
        "https://i.pinimg.com/736x/fe/1b/34/fe1b345a176b99c660a8e4006e436d17.jpg",
        "https://i.pinimg.com/736x/77/09/3c/77093c6cb24aaadb1931f20f5c3272a7.jpg",
        "https://i.pinimg.com/736x/27/12/c9/2712c99fe31db1609f1cd95c81644f64.jpg",
        "https://i.pinimg.com/736x/54/d4/a5/54d4a53922cfea4a361a706f75190ebb.jpg",
        "https://i.pinimg.com/474x/5a/4e/11/5a4e115ebdc5e4bd2f50e9e20a12a9cd.jpg",
        "https://www.biografiasyvidas.com/biografia/j/fotos/jackson_michael_1.jpg",
        "https://i.pinimg.com/736x/ea/28/86/ea2886bdf313cd82f12ba4b143735e30.jpg",
        "https://i.pinimg.com/736x/2e/ac/6d/2eac6d3e0b29e208535113ea3f2ca3ab.jpg",
        "https://i.pinimg.com/736x/31/37/32/313732aa86a872499a246d97b04dbf9b.jpg",
        "https://i.pinimg.com/736x/b8/90/d9/b890d901634961895f1b59aad4afb539.jpg",
        "https://i.pinimg.com/736x/ee/52/82/ee5282420939b0f73430116cf372f55a.jpg",
        "https://i.pinimg.com/236x/ec/18/0e/ec180e230e72e39d61bfdc7f11ace8b3.jpg",
        "https://i.pinimg.com/736x/44/a1/ea/44a1eab47e47909195c86e25765498df.jpg",
        "https://i.pinimg.com/736x/f2/0f/1a/f20f1a4af19a0b520074d8acbdbcdc82.jpg",
        "https://www.nit.pt/wp-content/uploads/2024/12/24db7a2d06147cd586e8afe2626a52ff.jpg",
        "https://i.blogs.es/5b679f/taquilla-blade-runner-2049-ryan-gosling/1200_900.jpg",
        "https://i.pinimg.com/736x/11/17/1f/11171fd3098fd8695455d4e0a947e668.jpg",
        "https://i.ibb.co/q3jWDPTK/3-AWO6-VGCRZCXTHKE5-Z24-BZWN6-Q.jpg",
        "https://i.pinimg.com/1200x/ca/45/b9/ca45b967fd970deccd323b6756a43ea1.jpg",
        "https://i.ibb.co/7xc0WqP9/LXVO3-NGPWJDNVFFKLYAGLC3-L5-Q.jpg",
        "https://s2-techtudo.glbimg.com/E-RYtfyv1rsR-4STrNGcgDqcT74=/0x0:1596x888/984x0/smart/filters:strip_icc()/i.s3.glbimg.com/v1/AUTH_08fbf48bc0524877943fe86e43087e7a/internal_photos/bs/2024/S/B/cFhlA1S3G8B7crStsmgw/the-boys-prime-video-techtudo-06.jpg",
        "https://www.looper.com/img/gallery/stormfronts-strongest-power-on-the-boys-isnt-what-you-think/l-intro-1600366075.jpg",
        "https://www.giantfreakinrobot.com/wp-content/uploads/2021/07/the-boys-homelander-antony-starr.jpg",
        "https://i.pinimg.com/736x/9c/84/88/9c8488eb100746a5fb7821e22f36ac7a.jpg",
        "https://indiehoy.com/wp-content/uploads/2020/10/Annie-January2.jpg",
        "https://i.ibb.co/vvPDcXtY/KD2-DQAV7-L5-OHZOGTEEFTUIQAEY.png",
        "https://i.ibb.co/Hpbfdrdc/the-series-supernatural-jensen-ackles-season-1-wallpaper-preview.png",
        "https://i.pinimg.com/736x/95/71/01/957101717be534403d1269eed4d0578f.jpg"
    )

    var selectedIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    // Find current avatar index
    LaunchedEffect(Unit) {
        val index = avatars.indexOf(currentAvatarUrl)
        if (index >= 0) {
            selectedIndex = index
            listState.scrollToItem(index)
        }
    }

    // Auto-scroll to keep selected item visible - sin animación para mejor rendimiento
    LaunchedEffect(selectedIndex) {
        listState.scrollToItem(selectedIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Escape, Key.Back -> {
                            onNavigateBack()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (selectedIndex > 0) {
                                selectedIndex--
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (selectedIndex < avatars.size - 1) {
                                selectedIndex++
                            }
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            // Up/Down navigate between row and preview
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            println("DEBUG: Enter presionado en avatar ${selectedIndex}")
                            onAvatarSelected(avatars[selectedIndex])
                            // Si hay callback de guardar, llamarlo también
                            println("DEBUG: Llamando onSaveProfile...")
                            onSaveProfile?.invoke()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // App background image
        AppBackground()
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text(
                text = "Seleccionar Avatar",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Elige un avatar para tu perfil",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Avatar horizontal row - centered
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(start = 120.dp, end = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(avatars) { index, avatarUrl ->
                    val isSelected = index == selectedIndex
                    val isCurrent = avatarUrl == currentAvatarUrl

                    AvatarRowItem(
                        avatarUrl = avatarUrl,
                        isFocused = isSelected,
                        isCurrent = isCurrent,
                        onClick = {
                            selectedIndex = index
                            onAvatarSelected(avatarUrl)
                        },
                        index = index
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Large preview of selected avatar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .background(
                        Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Selected avatar large preview
                    val currentAvatar = avatars.getOrNull(selectedIndex) ?: avatars.first()
                    AsyncImage(
                        model = currentAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "Avatar seleccionado",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Presiona ENTER para confirmar",
                            fontSize = 12.sp,
                            color = AnfhlyColors.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "BACK para volver sin cambios",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AvatarRowItem(
    avatarUrl: String,
    isFocused: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    index: Int
) {
    val focusRequester = remember { FocusRequester() }
    // Quest 3 hover-to-focus support
    val avatarInteraction = remember { MutableInteractionSource() }
    val isAvatarHovered by avatarInteraction.collectIsHoveredAsState()
    LaunchedEffect(isAvatarHovered) {
        if (isAvatarHovered) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Aplicar focus cuando este item está seleccionado
    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }

    val borderColor = when {
        isFocused -> Color.White
        isCurrent -> AnfhlyColors.primary
        else -> Color.Transparent
    }

    val borderWidth = when {
        isFocused -> 3.dp
        isCurrent -> 3.dp
        else -> 0.dp
    }

    val size = 80.dp // Tamaño consistente sin zoom

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .focusRequester(focusRequester)
                .hoverable(avatarInteraction)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        // Actualizar el selected index a través del callback
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(borderWidth, borderColor))
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape)
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar ${index + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }
}
