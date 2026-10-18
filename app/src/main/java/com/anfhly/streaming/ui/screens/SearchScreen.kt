package com.anfhly.streaming.ui.screens

import com.anfhly.streaming.data.model.Content
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.compose.foundation.layout.Box
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.compose.foundation.layout.wrapContentSize
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.itemsIndexed
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.ui.theme.AnfhlyColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    searchResults: List<Content>,
    onSearchQueryChange: (String) -> Unit,
    onContentSelected: (Content) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var localQuery by remember { mutableStateOf(searchQuery) }
    
    // Sincronizar localQuery si la búsqueda externa cambia (ej. al limpiar)
    LaunchedEffect(searchQuery) {
        if (searchQuery != localQuery) {
            localQuery = searchQuery
        }
    }

    val scope = rememberCoroutineScope()
    val headerFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    val keyboardFocusRequester = remember { FocusRequester() }

    // Inicialmente poner foco en el teclado virtual
    LaunchedEffect(Unit) {
        delay(100)
        try {
            keyboardFocusRequester.requestFocus()
        } catch (_: Exception) {
            // Ignorar errores
        }
    }

    // Manejar botón retroceder del control remoto
    BackHandler {
        onNavigateBack()
    }

    val defaultSuggestions = remember {
        listOf("Acción", "Comedia", "Terror", "Anime", "Marvel", "Series", "Películas", "Fantasía", "Ciencia Ficción")
    }

    fun normalizeText(text: String): String {
        return text.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
    }

    val predictiveSuggestions by remember(localQuery, searchResults) {
        derivedStateOf {
            val query = localQuery.trim()
            if (query.isEmpty()) {
                defaultSuggestions.take(6)
            } else {
                val cleanQuery = normalizeText(query)

                val allCandidates = (searchResults.map { it.title } + defaultSuggestions).distinct()

                // 1. Priorizar títulos que COMIENZAN con el texto buscado
                val startsWithMatches = allCandidates.filter { title ->
                    normalizeText(title).startsWith(cleanQuery)
                }.sortedBy { title -> normalizeText(title) }

                // 2. Secundario: Títulos que CONTIENEN el texto pero no comienzan con él
                val containsMatches = allCandidates.filter { title ->
                    val cleanTitle = normalizeText(title)
                    cleanTitle.contains(cleanQuery) && !cleanTitle.startsWith(cleanQuery)
                }.sortedBy { title -> normalizeText(title) }

                // Combinar ambos resultados estrictamente filtrados (máximo 6)
                (startsWithMatches + containsMatches).take(6)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // App background image
        AppBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
    // Header con SearchBar y navegación
            SearchHeader(
                query = localQuery,
                onNavigateDown = {
                    scope.launch {
                        try { firstCardFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Layout de dos columnas: Teclado + Resultados
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Columna izquierda: Teclado virtual (35% del ancho)
                VirtualKeyboard(
                    onKeyPressed = { key ->
                        localQuery = localQuery + key
                    },
                    onDeletePressed = {
                        if (localQuery.isNotEmpty()) {
                            localQuery = localQuery.dropLast(1)
                        }
                    },
                    onSpacePressed = {
                        localQuery = localQuery + " "
                    },
                    onClearPressed = {
                        localQuery = ""
                        onSearchQueryChange("") // Limpiar resultados inmediatamente
                    },
                    onSearchTriggered = {
                        onSearchQueryChange(localQuery)
                    },
                    onNavigateToHeader = {
                        scope.launch {
                            try {
                                headerFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    },
                    onNavigateToCards = {
                        scope.launch {
                            try { firstCardFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    },
                    suggestions = predictiveSuggestions,
                    onSuggestionSelected = { suggestion ->
                        localQuery = suggestion
                        onSearchQueryChange(suggestion)
                    },
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .focusRequester(keyboardFocusRequester)
                )
                
                // Columna derecha: Resultados de búsqueda (65% del ancho)
                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                ) {
                    SearchResultsGrid(
                        results = searchResults,
                        onContentSelected = onContentSelected,
                        modifier = Modifier.focusRequester(firstCardFocusRequester)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchHeader(
    query: String,
    onNavigateDown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onNavigateDown()
                    true
                } else false
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search bar
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (query.isEmpty()) "Escribe para buscar..." else query,
                    fontSize = 16.sp,
                    color = if (query.isEmpty()) Color.White.copy(alpha = 0.5f) else Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VirtualKeyboard(
    onKeyPressed: (String) -> Unit,
    onDeletePressed: () -> Unit,
    onSpacePressed: () -> Unit,
    onClearPressed: () -> Unit,
    onSearchTriggered: () -> Unit,
    onNavigateToHeader: () -> Unit = {},
    onNavigateToCards: () -> Unit = {},
    suggestions: List<String> = emptyList(),
    onSuggestionSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isUppercase by remember { mutableStateOf(false) }
    var focusedKey by remember { mutableStateOf("") }
    var currentRow by remember { mutableStateOf(0) }

    // Keys for each row to check navigation
    val row1Keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val row2Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val row3Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val row4Keys = listOf("z", "x", "c", "v", "b", "n", "ñ", "m")
    val row5Keys = listOf("á", "é", "í", "ó", "ú")

    Column(
        modifier = modifier
            .padding(16.dp)
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            // Solo navegar al header si estamos en la primera fila (fila 1 - números)
                            if (currentRow == 1) {
                                onNavigateToHeader()
                                true
                            } else {
                                // Permitir navegación natural hacia arriba entre teclas
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            // Desde la última tecla de cada fila o cápsula derecha, navegar a cards
                            val lastKeys = listOf("0", "p", "l", "m", "ú", "search_btn", "suggestion_chip_last")
                            if (focusedKey in lastKeys) {
                                onNavigateToCards()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Numbers 1-9 and 0
        KeyboardRow(
            keys = row1Keys,
            isUppercase = true, // Numbers are case-agnostic
            onKeyPressed = { key -> onKeyPressed(key) },
            onKeyFocused = { key ->
                focusedKey = key
                currentRow = 1
            }
        )

        // Row 2: QWERTYUIOP
        KeyboardRow(
            keys = row2Keys,
            isUppercase = isUppercase,
            onKeyPressed = { key -> onKeyPressed(if (isUppercase) key.uppercase() else key.lowercase()) },
            onKeyFocused = { key ->
                focusedKey = key
                currentRow = 2
            }
        )

        // Row 3: ASDFGHJKL
        KeyboardRow(
            keys = row3Keys,
            isUppercase = isUppercase,
            onKeyPressed = { key -> onKeyPressed(if (isUppercase) key.uppercase() else key.lowercase()) },
            onKeyFocused = { key ->
                focusedKey = key
                currentRow = 3
            }
        )

        // Row 4: Z X C V B N Ñ M
        KeyboardRow(
            keys = row4Keys,
            isUppercase = isUppercase,
            onKeyPressed = { key -> onKeyPressed(if (isUppercase) key.uppercase() else key.lowercase()) },
            onKeyFocused = { key ->
                focusedKey = key
                currentRow = 4
            }
        )

        // Row 5: Accented vowels Á É Í Ó Ú
        KeyboardRow(
            keys = row5Keys,
            isUppercase = isUppercase,
            onKeyPressed = { key -> onKeyPressed(if (isUppercase) key.uppercase() else key.lowercase()) },
            onKeyFocused = { key ->
                focusedKey = key
                currentRow = 5
            },
            fillEqually = false
        )

        // Row 6: Special keys
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Case Toggle button (Shift/Abc)
            VirtualKeyButton(
                text = "SHIFT",
                keyValue = "shift",
                onClick = { isUppercase = !isUppercase },
                onFocus = { key ->
                    focusedKey = key
                    currentRow = 6
                },
                modifier = Modifier.weight(0.7f),
                icon = Icons.Default.ArrowUpward
            )

            // Clear button (Delete icon)
            VirtualKeyButton(
                text = "CLEAR",
                keyValue = "clear",
                onClick = onClearPressed,
                onFocus = { key ->
                    focusedKey = key
                    currentRow = 6
                },
                modifier = Modifier.weight(0.9f),
                icon = Icons.Default.Delete
            )

            // Delete button (Backspace icon)
            VirtualKeyButton(
                text = "DEL",
                keyValue = "delete",
                onClick = onDeletePressed,
                onFocus = { key ->
                    focusedKey = key
                    currentRow = 6
                },
                modifier = Modifier.weight(0.9f),
                icon = Icons.Default.Backspace
            )

            // Space button (SpaceBar icon)
            VirtualKeyButton(
                text = "SPACE",
                keyValue = "space",
                onClick = onSpacePressed,
                onFocus = { key ->
                    focusedKey = key
                    currentRow = 6
                },
                modifier = Modifier.weight(1.1f),
                icon = Icons.Default.SpaceBar
            )

            // Search button (Search icon)
            VirtualKeyButton(
                text = "BUSCAR",
                keyValue = "search_btn",
                onClick = onSearchTriggered,
                onFocus = { key ->
                    focusedKey = key
                    currentRow = 6
                },
                modifier = Modifier.weight(1.1f),
                isHighlight = false,
                icon = Icons.Default.Search
            )
        }

        // Sección de Búsqueda Predictiva (Cápsulas en Lista Vertical)
        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                suggestions.forEachIndexed { index, suggestion ->
                    PredictiveCapsuleChip(
                        text = suggestion,
                        onClick = { onSuggestionSelected(suggestion) },
                        onFocus = {
                            focusedKey = "suggestion_chip_last"
                            currentRow = 7 + index
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PredictiveCapsuleChip(
    text: String,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val chipFocusRequester = remember { FocusRequester() }
    val chipInteraction = remember { MutableInteractionSource() }
    val isChipHovered by chipInteraction.collectIsHoveredAsState()

    LaunchedEffect(isChipHovered) {
        if (isChipHovered) {
            try { chipFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val lastClickTime = remember { mutableStateOf(0L) }
    val safeOnClick = remember(onClick) {
        {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime.value > 200L) {
                lastClickTime.value = currentTime
                onClick()
            }
        }
    }

    Surface(
        onClick = safeOnClick,
        modifier = modifier
            .height(36.dp)
            .focusRequester(chipFocusRequester)
            .hoverable(chipInteraction)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocus()
                }
            }
            .pointerInput(safeOnClick) {
                detectTapGestures {
                    safeOnClick()
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyboardRow(
    keys: List<String>,
    isUppercase: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyFocused: (String) -> Unit,
    fillEqually: Boolean = true
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth()
    ) {
        keys.forEach { key ->
            VirtualKeyButton(
                text = if (isUppercase) key.uppercase() else key.lowercase(),
                keyValue = key,
                onClick = { onKeyPressed(key) },
                onFocus = onKeyFocused,
                modifier = if (fillEqually) Modifier.weight(1f) else Modifier.width(30.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VirtualKeyButton(
    text: String,
    keyValue: String,
    onClick: () -> Unit,
    onFocus: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    isHighlight: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyFocusRequester = remember { FocusRequester() }
    // Quest 3 hover-to-focus support
    val keyInteraction = remember { MutableInteractionSource() }
    val isKeyHovered by keyInteraction.collectIsHoveredAsState()
    LaunchedEffect(isKeyHovered) {
        if (isKeyHovered) {
            try { keyFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val lastClickTime = remember { mutableStateOf(0L) }
    val safeOnClick = remember(onClick) {
        {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime.value > 200L) {
                lastClickTime.value = currentTime
                onClick()
            }
        }
    }

    Surface(
        onClick = safeOnClick,
        modifier = modifier
            .height(30.dp)
            .focusRequester(keyFocusRequester)
            .hoverable(keyInteraction)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocus(keyValue)
                }
            }
            .pointerInput(safeOnClick) {
                detectTapGestures {
                    safeOnClick()
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isHighlight) AnfhlyColors.primary.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
            focusedContainerColor = if (isHighlight) AnfhlyColors.primary else Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = if (isFocused) (if (isHighlight) Color.White else Color.Black) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = text,
                    color = if (isFocused) (if (isHighlight) Color.White else Color.Black) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SearchResultsGrid(
    results: List<Content>,
    onContentSelected: (Content) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isNotEmpty()) {
        TvLazyVerticalGrid(
            columns = TvGridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier
        ) {
            items(
                items = results,
                key = { it.id }
            ) { item ->
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(170.dp)
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    LargeContentCard(
                        content = item,
                        onClick = { onContentSelected(item) }
                    )
                }
            }
        }
    } else {
        Text(
            text = "No se encontraron resultados",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 32.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LargeContentCard(
    content: Content,
    onClick: () -> Unit,
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

    // Get backdrop URL for categories-style display
    val backdropUrl = if (!content.backdropUrl.isNullOrBlank()) {
        content.backdropUrl
    } else if (!content.backdropPublicityUrl.isNullOrBlank()) {
        content.backdropPublicityUrl
    } else {
        content.posterUrl
    }

    // Animación de padding: cuando tiene foco, el padding se reduce y la card "crece"
    val animatedPadding by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isFocused) 0.dp else 6.dp,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "cardPadding"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(170.dp + 12.dp)
            .width(300.dp + 12.dp)
            .focusRequester(cardFocusRequester)
            .hoverable(cardInteraction)
            .onFocusChanged { state ->
                isFocused = state.isFocused
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(animatedPadding)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Backdrop image for categories-style display
            if (!backdropUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(backdropUrl)
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
                // Placeholder when no image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF555555))
                )
            }

            // Contraste: cards sin foco se oscurecen
            val overlayAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isFocused) 0.3f else 0.45f,
                animationSpec = androidx.compose.animation.core.tween(200),
                label = "cardContrast"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlayAlpha))
            )
            
            // Logo o Texto en el centro
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
                        Text(
                            text = content.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
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
    }
}
