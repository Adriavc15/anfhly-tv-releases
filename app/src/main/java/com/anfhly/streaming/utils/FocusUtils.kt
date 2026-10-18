package com.anfhly.streaming.utils

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Utilidades para manejo de foco en TV
 */
object FocusUtils {
    
    /**
     * Crea un FocusRequester seguro que nunca es nulo
     */
    @Composable
    fun createSafeFocusRequester(): FocusRequester {
        return remember { FocusRequester() }
    }
    
    /**
     * Modificador para manejo seguro de foco en items de lista
     */
    fun Modifier.safeItemFocus(
        focusRequester: FocusRequester,
        onFocusChanged: (Boolean) -> Unit = {},
        onFocusEnter: () -> Unit = {},
        onFocusExit: () -> Unit = {}
    ): Modifier {
        return this
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                try {
                    onFocusChanged(focusState.hasFocus)
                    if (focusState.hasFocus) {
                        onFocusEnter()
                    } else {
                        onFocusExit()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FocusUtils", "Error in focus change handler", e)
                }
            }
    }
    
    /**
     * Componente para limpiar el foco cuando sea necesario
     */
    @Composable
    fun FocusCleanup() {
        val focusManager = LocalFocusManager.current
        
        LaunchedEffect(Unit) {
            // Limpiar foco cada 30 segundos para prevenir problemas
            kotlinx.coroutines.delay(30000)
            try {
                focusManager.clearFocus()
                android.util.Log.d("FocusUtils", "Focus cleanup performed")
            } catch (e: Exception) {
                android.util.Log.e("FocusUtils", "Error in focus cleanup", e)
            }
        }
    }
    
    /**
     * Solicita foco de forma segura con manejo de errores
     */
    fun safeRequestFocus(focusRequester: FocusRequester) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            android.util.Log.e("FocusUtils", "Error requesting focus", e)
        }
    }
}

/**
 * Función remember para FocusRequester
 */
@Composable
fun rememberFocusRequester(): FocusRequester {
    return androidx.compose.runtime.remember { FocusRequester() }
}
