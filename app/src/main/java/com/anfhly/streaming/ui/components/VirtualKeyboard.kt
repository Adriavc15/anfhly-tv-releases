package com.anfhly.streaming.ui.components

import android.util.Log
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VirtualKeyboard(
    onKeyPressed: (String) -> Unit,
    onDeletePressed: () -> Unit,
    onSpacePressed: () -> Unit,
    onClearPressed: () -> Unit,
    onNextPressed: () -> Unit = {},
    onNavigateToCards: () -> Unit = {},
    onNavigateToTabs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var focusedKey by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .padding(16.dp)
            .focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Title
        Text(
            text = "TECLADO VIRTUAL",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Row 1: QWERTYUIOP
        KeyboardRow(
            keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            onKeyPressed = { key -> onKeyPressed(key.uppercase()) },
            onKeyFocused = { key -> focusedKey = key }
        )

        // Row 2: ASDFGHJKL
        KeyboardRow(
            keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            onKeyPressed = { key -> onKeyPressed(key.uppercase()) },
            onKeyFocused = { key -> focusedKey = key }
        )

        // Row 3: Z X C V B N M
        KeyboardRow(
            keys = listOf("z", "x", "c", "v", "b", "n", "m"),
            onKeyPressed = { key -> onKeyPressed(key.uppercase()) },
            onKeyFocused = { key -> focusedKey = key }
        )
        
        // Row 4: Special keys
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Clear button
            VirtualKeyButton(
                text = "CLEAR",
                keyValue = "clear",
                onClick = onClearPressed,
                onFocus = { focusedKey = "clear" },
                modifier = Modifier.weight(1f)
            )

            // Space button
            VirtualKeyButton(
                text = "SPACE",
                keyValue = "space",
                onClick = onSpacePressed,
                onFocus = { focusedKey = "space" },
                modifier = Modifier.weight(1f)
            )

            // Enter/Next button
            VirtualKeyButton(
                text = "NEXT",
                keyValue = "next",
                onClick = onNextPressed,
                onFocus = { focusedKey = "next" },
                modifier = Modifier.weight(1f)
            )

            // Delete button
            VirtualKeyButton(
                text = "DEL",
                keyValue = "delete",
                onClick = onDeletePressed,
                onFocus = { focusedKey = "delete" },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyboardRow(
    keys: List<String>,
    onKeyPressed: (String) -> Unit,
    onKeyFocused: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
    ) {
        keys.forEach { key ->
            VirtualKeyButton(
                text = key.uppercase(),
                keyValue = key,
                onClick = { onKeyPressed(key) },
                onFocus = onKeyFocused,
                modifier = Modifier.weight(1f)
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
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyFocusRequester = remember { FocusRequester() }
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
            .height(40.dp)
            .focusRequester(keyFocusRequester)
            .hoverable(keyInteraction)
            .focusable()
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    Log.d("VirtualKeyboard", "Tecla enfocada: $keyValue")
                    onFocus(keyValue)
                }
            }
            .pointerInput(safeOnClick) {
                detectTapGestures {
                    safeOnClick()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    Log.d("VirtualKeyboard", "Tecla $keyValue - Evento: ${event.key}")
                }
                false
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.15f),
            focusedContainerColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp))
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
