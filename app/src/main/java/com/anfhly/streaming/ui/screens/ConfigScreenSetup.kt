package com.anfhly.streaming.ui.screens

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.anfhly.streaming.utils.UserPreferences

/**
 * Configura la actividad para usar la implementación basada en SettingsDataStore
 */
@Composable
fun ConfigScreenSetup(
    context: Context,
    onSetupComplete: () -> Unit = {}
) {
    SideEffect {
        // Asegurar que las preferencias estén inicializadas con la implementación correcta
        // Esta función debe ser llamada al inicio de ConfigScreen o en el Application class
        com.anfhly.streaming.utils.UserPreferences.isSidebarOnly(context)
        com.anfhly.streaming.utils.UserPreferences.isTabEnabled(context, 1)
        
        // Inicializar valor por defecto si es None
        if (!com.anfhly.streaming.utils.UserPreferences.isSidebarOnly(context)) {
            com.anfhly.streaming.utils.UserPreferences.setSidebarOnly(context, false)
        }
        if (!com.anfhly.streaming.utils.UserPreferences.isTabEnabled(context, 1)) {
            com.anfhly.streaming.utils.UserPreferences.setTabEnabled(context, 1, true)
        }
        
        onSetupComplete()
    }
}
