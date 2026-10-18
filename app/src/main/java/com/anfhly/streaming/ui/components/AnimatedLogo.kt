package com.anfhly.streaming.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlinx.coroutines.delay
import com.anfhly.streaming.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun AnimatedLogo(modifier: Modifier = Modifier) {
    var introPhase by remember { mutableStateOf(0) }
    val animationTrigger = introPhase >= 4
    LaunchedEffect(Unit) {
        delay(100)
        introPhase = 1 // Aparece cabeza gigante
        delay(600)
        introPhase = 2 // Da una vuelta
        delay(700)
        introPhase = 3 // Se oculta
        delay(350)
        introPhase = 4 // Empieza la asamblea normal
    }

    // INTRO BIG HEAD ANIMATIONS
    val bigHeadScale by animateFloatAsState(
        targetValue = when(introPhase) {
            0 -> 0f
            1, 2 -> 2.2f // Se mantiene grande mientras gira
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "bigHeadScale"
    )
    val bigHeadAlpha by animateFloatAsState(
        targetValue = when(introPhase) {
            0 -> 0f
            1, 2 -> 1f
            else -> 0f
        },
        animationSpec = tween(300),
        label = "bigHeadAlpha"
    )
    val bigHeadRotation by animateFloatAsState(
        targetValue = if (introPhase >= 2) 360f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bigHeadRotation"
    )

    // CABEZA NORMAL: drops down and bounces slightly
    val cabezaY by animateFloatAsState(
        targetValue = if (animationTrigger) 0f else -150f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessLow
        ),
        label = "cabezaY"
    )
    val cabezaAlpha by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(800),
        label = "cabezaAlpha"
    )

    // PALABRA: fades in and slides down from behind the head
    val palabraY by animateFloatAsState(
        targetValue = if (animationTrigger) 0f else -50f,
        animationSpec = tween(800, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "palabraY"
    )
    val palabraAlpha by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(800, delayMillis = 400),
        label = "palabraAlpha"
    )


    // Infinite floating animation after it settles
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    
    // Breathing scale effect (like the previous tv.png animation)
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleAnim"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // LAYER ASAMBLEA NORMAL
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .graphicsLayer {
                    translationY = if (animationTrigger) floatY else 0f
                    scaleX = scaleAnim
                    scaleY = scaleAnim
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // CABEZA (Frente)
        Image(
            painter = painterResource(id = R.drawable.cabeza),
            contentDescription = null,
            modifier = Modifier
                .height(90.dp)
                .zIndex(1f) // Frente
                .graphicsLayer {
                    translationY = cabezaY
                    alpha = cabezaAlpha
                },
            contentScale = ContentScale.Fit
        )
        
            // PALABRA (Detrás de la cabeza)
            Image(
                painter = painterResource(id = R.drawable.palabra),
                contentDescription = null,
                modifier = Modifier
                    .height(40.dp)
                    .offset(y = (-15).dp) // Acercar más a la cabeza verticalmente
                    .zIndex(-1f) // Detrás de la cabeza para que salga de ahí
                    .graphicsLayer {
                        translationY = palabraY
                        alpha = palabraAlpha
                    },
                contentScale = ContentScale.Fit
            )
        }

        // CABEZA GIGANTE INTRO
        if (introPhase < 4) {
            Image(
                painter = painterResource(id = R.drawable.cabezon),
                contentDescription = null,
                modifier = Modifier
                    .size(90.dp) // Tamaño base, escalado por bigHeadScale
                    .graphicsLayer {
                        scaleX = bigHeadScale
                        scaleY = bigHeadScale
                        alpha = bigHeadAlpha
                        rotationY = bigHeadRotation
                        cameraDistance = 8f * density // Mejora el efecto 3D del giro
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}

