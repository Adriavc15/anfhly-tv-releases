package com.anfhly.streaming.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.anfhly.streaming.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ConfigScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showMenuModal by remember { mutableStateOf(false) }
    var showInfoModal by remember { mutableStateOf(false) }
    var showPlayerModal by remember { mutableStateOf(false) }
    var showInterfaceModal by remember { mutableStateOf(false) }
    val currentUiStyle = remember { mutableStateOf(com.anfhly.streaming.utils.UserPreferences.getUiStyle(context)) }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFD8D8E0),
                        Color(0xFFB0B0BC)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "Ajustes",
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF444444),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 50.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF9A9AA8),
                                    Color(0xFF7A7A88)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(90.dp)
                    )
                }

                Spacer(modifier = Modifier.width(80.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var menuFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { showMenuModal = true },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.7f),
                            focusedContainerColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                        modifier = Modifier
                            .width(450.dp)
                            .height(42.dp)
                            .onFocusChanged { menuFocused = it.isFocused }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = Color(0xFF333333),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Menú",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    var interfaceFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { showInterfaceModal = true },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.7f),
                            focusedContainerColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                        modifier = Modifier
                            .width(450.dp)
                            .height(42.dp)
                            .onFocusChanged { interfaceFocused = it.isFocused }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF333333),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Interfaz",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = when (currentUiStyle.value) {
                                    "apple" -> "Apple TV"
                                    "netflix" -> "Netflix"
                                    "googletv" -> "Google TV"
                                    else -> "Apple TV"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF555555)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    var playerFocused by remember { mutableStateOf(false) }
                    val currentPlayer = remember { mutableStateOf(com.anfhly.streaming.utils.UserPreferences.getPreferredPlayer(context)) }
                    Surface(
                        onClick = { showPlayerModal = true },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.7f),
                            focusedContainerColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                        modifier = Modifier
                            .width(450.dp)
                            .height(42.dp)
                            .onFocusChanged { playerFocused = it.isFocused }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF333333),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Reproductor",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = when (currentPlayer.value) {
                                    "exoplayer" -> "ExoPlayer"
                                    "vlc" -> "VLC"
                                    "mxplayer" -> "MX Player"
                                    "xplayer" -> "XPlayer"
                                    else -> "ExoPlayer"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF555555)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    var infoFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { showInfoModal = true },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.7f),
                            focusedContainerColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                        modifier = Modifier
                            .width(450.dp)
                            .height(42.dp)
                            .onFocusChanged { infoFocused = it.isFocused }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF333333),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Info",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de Menú
    if (showMenuModal) {
        data class TabItem(val index: Int, val name: String, val iconRes: Int? = null, val iconVector: ImageVector? = null)
        val tabItems = listOf(
            TabItem(1, "Home", iconRes = R.drawable.home),
            TabItem(2, "Series", iconRes = R.drawable.series),
            TabItem(3, "Movies", iconRes = R.drawable.movie),
            TabItem(4, "Sports", iconRes = R.drawable.pelota),
            TabItem(5, "Categories", iconRes = R.drawable.categories),
            TabItem(6, "My List", iconVector = Icons.Filled.Add)
        )
        var currentSidebarOnly by remember { mutableStateOf(com.anfhly.streaming.utils.UserPreferences.isSidebarOnly(context)) }
        var currentTabStates by remember { mutableStateOf(tabItems.associate { it.index to com.anfhly.streaming.utils.UserPreferences.isTabEnabled(context, it.index) }) }

        AlertDialog(
            onDismissRequest = { showMenuModal = false },
            containerColor = Color(0xFFC0C0CC),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Configuración de menú", color = Color(0xFF333333), fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column {
                    Text("Modo de menú", color = Color(0xFF444444), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        var hf by remember { mutableStateOf(false) }
                        var sf by remember { mutableStateOf(false) }
                        Surface(
                            onClick = {
                                currentSidebarOnly = false
                                com.anfhly.streaming.utils.UserPreferences.setSidebarOnly(context, false)
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (!currentSidebarOnly) Color.White else Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color(0xFFD0D0D8)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { hf = it.isFocused }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Header + Sidebar",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hf) Color.White else Color(0xFF333333),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Surface(
                            onClick = {
                                currentSidebarOnly = true
                                com.anfhly.streaming.utils.UserPreferences.setSidebarOnly(context, true)
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (currentSidebarOnly) Color.White else Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color(0xFFD0D0D8)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { sf = it.isFocused }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Solo Sidebar",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sf) Color.White else Color(0xFF333333),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Text("Tabs del header", color = Color(0xFF444444), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tabItems.forEach { tabItem ->
                            val isEnabled = currentTabStates[tabItem.index] ?: true
                            var itemFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = {
                                    val newValue = !isEnabled
                                    currentTabStates = currentTabStates.toMutableMap().apply { put(tabItem.index, newValue) }
                                    com.anfhly.streaming.utils.UserPreferences.setTabEnabled(context, tabItem.index, newValue)
                                },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                                    focusedContainerColor = Color(0xFFD0D0D8)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { itemFocused = it.isFocused }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    if (tabItem.iconVector != null) {
                                        Icon(
                                            imageVector = tabItem.iconVector,
                                            contentDescription = null,
                                            tint = if (itemFocused) Color.White else Color(0xFF555555),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else if (tabItem.iconRes != null) {
                                        Icon(
                                            painter = painterResource(id = tabItem.iconRes),
                                            contentDescription = null,
                                            tint = if (itemFocused) Color.White else Color(0xFF555555),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        tabItem.name,
                                        fontSize = 13.sp,
                                        color = if (itemFocused) Color.White else Color(0xFF333333),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (isEnabled) "Sí" else "No",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEnabled) Color(0xFF333333) else Color(0xFF888888)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Modal de Reproductor
    if (showPlayerModal) {
        data class PlayerOption(val key: String, val name: String, val description: String)
        val playerOptions = listOf(
            PlayerOption("exoplayer", "ExoPlayer", "Reproductor integrado por defecto"),
            PlayerOption("vlc", "VLC", "Reproductor externo VLC"),
            PlayerOption("mxplayer", "MX Player", "Reproductor externo MX Player"),
            PlayerOption("xplayer", "XPlayer", "Reproductor externo XPlayer")
        )
        val currentPreferred = com.anfhly.streaming.utils.UserPreferences.getPreferredPlayer(context)

        AlertDialog(
            onDismissRequest = { showPlayerModal = false },
            containerColor = Color(0xFFC0C0CC),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Seleccionar reproductor", color = Color(0xFF333333), fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    playerOptions.forEach { option ->
                        val isSelected = currentPreferred == option.key
                        var itemFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = {
                                com.anfhly.streaming.utils.UserPreferences.savePreferredPlayer(context, option.key)
                                showPlayerModal = false
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color(0xFFD0D0D8)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { itemFocused = it.isFocused }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (itemFocused) Color.White else Color(0xFF555555),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (itemFocused) Color.White else Color(0xFF333333)
                                    )
                                    Text(
                                        text = option.description,
                                        fontSize = 11.sp,
                                        color = if (itemFocused) Color.White.copy(alpha = 0.8f) else Color(0xFF666666)
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (itemFocused) Color.White else Color(0xFF333333)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Modal de Interfaz
    if (showInterfaceModal) {
        data class StyleOption(val key: String, val name: String, val description: String)
        val styleOptions = listOf(
            StyleOption("apple", "Apple TV", "Diseño clásico por defecto"),
            StyleOption("netflix", "Netflix", "Diseño estilo Netflix")
        )
        val currentStyle = com.anfhly.streaming.utils.UserPreferences.getUiStyle(context)

        AlertDialog(
            onDismissRequest = { showInterfaceModal = false },
            containerColor = Color(0xFFC0C0CC),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Estilo de interfaz", color = Color(0xFF333333), fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    styleOptions.forEach { option ->
                        val isSelected = currentStyle == option.key
                        var itemFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = {
                                com.anfhly.streaming.utils.UserPreferences.saveUiStyle(context, option.key)
                                currentUiStyle.value = option.key
                                showInterfaceModal = false
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color(0xFFD0D0D8)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { itemFocused = it.isFocused }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = if (itemFocused) Color.White else Color(0xFF555555),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (itemFocused) Color.White else Color(0xFF333333)
                                    )
                                    Text(
                                        text = option.description,
                                        fontSize = 11.sp,
                                        color = if (itemFocused) Color.White.copy(alpha = 0.8f) else Color(0xFF666666)
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (itemFocused) Color.White else Color(0xFF333333)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Modal de Info
    if (showInfoModal) {
        val telegramUrl = "https://t.me/+UX8TCeTKrxgzMDE5"

        AlertDialog(
            onDismissRequest = { showInfoModal = false },
            containerColor = Color(0xFFC0C0CC),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Información", color = Color(0xFF333333), fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column {
                    Text("Únete a nuestro canal de Telegram para mantenerte actualizado.", color = Color(0xFF444444), fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                    var linkFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.5f),
                            focusedContainerColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { linkFocused = it.isFocused }
                    ) {
                        Text(
                            text = telegramUrl,
                            fontSize = 13.sp,
                            color = if (linkFocused) Color.Black else Color(0xFF60A5FA),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}
