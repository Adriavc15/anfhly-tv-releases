package com.anfhly.streaming.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CrtEffectOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline_anim")
    
    // Smooth downward scroll for scanlines
    val scanlineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline_offset"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Scanlines with Chromatic Aberration
        val lineSpacing = 3.dp.toPx()
        val numLines = (height / lineSpacing).toInt() + 1
        
        val offsetPx = scanlineOffset * lineSpacing

        for (i in -1..numLines) {
            val baseY = i * lineSpacing + offsetPx
            
            // Dark scanline to remove RGB colors
            drawLine(
                color = Color.Black.copy(alpha = 0.2f),
                start = Offset(0f, baseY),
                end = Offset(width, baseY),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        // Vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                center = Offset(width / 2, height / 2),
                radius = width * 0.7f
            )
        )
    }
}
