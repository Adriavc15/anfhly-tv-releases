package com.anfhly.streaming.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.anfhly.streaming.ui.theme.AnfhlyColors
import com.anfhly.streaming.util.ExternalPlayerLauncher

/**
 * Diálogo para seleccionar el reproductor de video
 */
@Composable
fun PlayerSelectorDialog(
    videoUrl: String,
    title: String,
    onDismiss: () -> Unit,
    onUseInternalPlayer: () -> Unit
) {
    val context = LocalContext.current
    
    // Check available players
    val hasJustPlayer = remember { ExternalPlayerLauncher.isJustPlayerInstalled(context) }
    val externalPlayer = remember { ExternalPlayerLauncher.getAvailableExternalPlayer(context) }
    val hasExternalPlayer = externalPlayer != null
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A1A),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Seleccionar Reproductor",
                    fontSize = 22.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = title,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Internal Player Option
                PlayerOption(
                    icon = "▶",
                    name = "Reproductor Integrado",
                    description = "ExoPlayer nativo de la app",
                    onClick = {
                        onDismiss()
                        onUseInternalPlayer()
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Just Player Option (if installed)
                if (hasJustPlayer) {
                    PlayerOption(
                        icon = "⭐",
                        name = "Just Player",
                        description = "Recomendado - Excelente soporte para Android TV",
                        highlighted = true,
                        onClick = {
                            onDismiss()
                            ExternalPlayerLauncher.launchJustPlayer(
                                context as android.app.Activity,
                                videoUrl,
                                title
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Other external players
                if (hasExternalPlayer && !hasJustPlayer) {
                    val playerName = when (externalPlayer) {
                        "com.mxtech.videoplayer.pro" -> "MX Player Pro"
                        "com.mxtech.videoplayer.ad" -> "MX Player"
                        "org.videolan.vlc" -> "VLC"
                        else -> "Reproductor Externo"
                    }
                    
                    PlayerOption(
                        icon = "🎬",
                        name = playerName,
                        description = "Usar reproductor externo instalado",
                        onClick = {
                            onDismiss()
                            ExternalPlayerLauncher.launchBestPlayer(
                                context as android.app.Activity,
                                videoUrl,
                                title
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Cancel button
                Surface(
                    onClick = onDismiss,
                    color = Color.Gray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Cancelar",
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
                
                // Install Just Player suggestion
                if (!hasJustPlayer) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "💡 Tip: Instala 'Just Player' desde Play Store para mejor experiencia",
                        fontSize = 12.sp,
                        color = Color(0xFFFFA500),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerOption(
    icon: String,
    name: String,
    description: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = if (highlighted) {
        AnfhlyColors.primary.copy(alpha = 0.2f)
    } else {
        Color(0xFF2A2A2A)
    }
    
    val borderColor = if (highlighted) {
        AnfhlyColors.primary
    } else {
        Color.Transparent
    }
    
    Surface(
        onClick = onClick,
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (highlighted) 2.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 28.sp
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = name,
                    fontSize = 16.sp,
                    color = if (highlighted) AnfhlyColors.primary else Color.White
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
