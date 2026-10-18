package com.anfhly.streaming.utils

import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Modificadores de Compose para soporte de puntero/hover de Meta Quest 3.
 *
 * En Quest 3, el controlador actúa como un puntero (mouse virtual) que envía
 * eventos de hover. Estos modificadores convierten hover → focus para que
 * la app funcione correctamente con la navegación por puntero.
 */

/**
 * Modifier que agrega soporte de puntero Quest 3 a cualquier composable focusable.
 * Cuando el puntero del Quest entra en el área del composable, automáticamente
 * solicita foco, dando feedback visual al usuario.
 *
 * @param focusRequester FocusRequester opcional. Si no se proporciona, crea uno interno.
 * @param onHoverChanged Callback opcional cuando cambia el estado de hover.
 * @param onFocusChanged Callback opcional cuando cambia el estado de foco.
 */
fun Modifier.questPointerSupport(
    focusRequester: FocusRequester? = null,
    onHoverChanged: ((Boolean) -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
): Modifier = composed {
    val internalFocusRequester = remember { focusRequester ?: FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // Cuando el puntero entra, solicitar foco
    LaunchedEffect(isHovered) {
        if (isHovered) {
            try {
                internalFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.w("QuestPointer", "Error requesting focus on hover: ${e.message}")
            }
        }
        onHoverChanged?.invoke(isHovered)
    }
    
    this
        .focusRequester(internalFocusRequester)
        .hoverable(interactionSource)
        .onFocusChanged { state ->
            onFocusChanged?.invoke(state.hasFocus || state.isFocused)
        }
        .focusable(interactionSource = interactionSource)
}

/**
 * Modifier simplificado de hover-to-focus que no requiere un FocusRequester externo.
 * Útil para items de listas o grids donde cada item necesita responder al hover.
 *
 * @param onFocused Callback cuando el item recibe foco (por hover o D-pad).
 */
fun Modifier.hoverToFocus(
    onFocused: ((Boolean) -> Unit)? = null
): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    LaunchedEffect(isHovered) {
        if (isHovered) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignorar - el composable puede no estar aún en el árbol de composición
            }
        }
    }
    
    this
        .focusRequester(focusRequester)
        .hoverable(interactionSource)
        .onFocusChanged { state ->
            onFocused?.invoke(state.hasFocus || state.isFocused)
        }
}

/**
 * Modifier que maneja pointer input (MotionEvent) directamente.
 * Útil cuando se necesita control más fino sobre los eventos de puntero,
 * por ejemplo para el reproductor de video donde hover no debe activar foco
 * sino mostrar/ocultar controles.
 *
 * @param onPointerEnter Callback cuando el puntero entra en el área
 * @param onPointerExit Callback cuando el puntero sale del área
 * @param onPointerClick Callback cuando se hace click (trigger del Quest)
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.questPointerEvents(
    onPointerEnter: (() -> Unit)? = null,
    onPointerExit: (() -> Unit)? = null,
    onPointerClick: (() -> Unit)? = null
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            when (event.type) {
                PointerEventType.Enter -> {
                    onPointerEnter?.invoke()
                }
                PointerEventType.Exit -> {
                    onPointerExit?.invoke()
                }
                PointerEventType.Press -> {
                    onPointerClick?.invoke()
                }
            }
        }
    }
}

/**
 * Extension que combina soporte de Quest pointer con la funcionalidad de Surface de TV.
 * Diseñada para usarse con `androidx.tv.material3.Surface` que ya maneja click y focus.
 *
 * Solo agrega la capa de hover para que el puntero del Quest active el focus visual.
 */
fun Modifier.questSurfaceSupport(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    
    this.hoverable(interactionSource)
}
