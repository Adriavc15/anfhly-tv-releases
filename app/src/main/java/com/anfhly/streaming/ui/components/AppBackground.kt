package com.anfhly.streaming.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.anfhly.streaming.R

/**
 * Fondo de aplicación optimizado con la imagen fondo.jpg
 * Uso: Box(modifier = Modifier.fillMaxSize()) { AppBackground() }
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    // La imagen se carga eficientemente sin recargas innecesarias
    Image(
        painter = painterResource(id = R.drawable.fondo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}
