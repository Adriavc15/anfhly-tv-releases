package com.anfhly.streaming.utils

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Sistema simplificado para gestión de calidad adaptativa según capacidades del dispositivo
 */
class QualityManager(private val context: Context) {
    
    companion object {
        private const val TAG = "QualityManager"
    }
    
    private val isHighEndDevice = isHighEndDevice()
    private val maxSupportedResolution = getMaxSupportedResolution()
    
    /**
     * Detecta si es un dispositivo de gama alta
     */
    private fun isHighEndDevice(): Boolean {
        val ram = Runtime.getRuntime().maxMemory() / (1024 * 1024) // MB
        val cores = Runtime.getRuntime().availableProcessors()
        
        // Criterios para dispositivo de gama alta
        val hasHighRam = ram >= 6144 // 6GB+
        val hasManyCores = cores >= 8
        val isNewApi = Build.VERSION.SDK_INT >= 30 // Android 11+
        
        return (hasHighRam && hasManyCores) || isNewApi
    }
    
    /**
     * Obtiene la resolución máxima soportada
     */
    private fun getMaxSupportedResolution(): Pair<Int, Int> {
        return if (isHighEndDevice) {
            Pair(3840, 2160) // 4K para dispositivos gama alta
        } else {
            Pair(2560, 1440) // 2K para dispositivos estándar
        }
    }
    
    /**
     * Determina la calidad óptima para el dispositivo
     */
    fun getOptimalQuality(): String {
        return if (isHighEndDevice) {
            "4K" // Dispositivos gama alta pueden manejar 4K
        } else {
            "1080p" // Dispositivos estándar usan Full HD
        }
    }
    
    /**
     * Verifica si una calidad es compatible con el dispositivo
     */
    fun isQualityCompatible(height: Int, bitrate: Int): Boolean {
        val maxHeight = maxSupportedResolution.second
        val maxBitrate = if (isHighEndDevice) 30_000_000 else 15_000_000 // 30Mbps o 15Mbps
        
        return height <= maxHeight && (bitrate <= 0 || bitrate <= maxBitrate)
    }
    
    /**
     * Selecciona la mejor calidad de una lista de opciones
     */
    fun selectBestQuality(availableQualities: List<String>): String? {
        if (availableQualities.isEmpty()) return null
        
        // Extraer altura de las etiquetas de calidad
        val qualityHeights = availableQualities.mapNotNull { quality ->
            when {
                quality.contains("4K") || quality.contains("2160") -> 2160
                quality.contains("2K") || quality.contains("1440") -> 1440
                quality.contains("1080") || quality.contains("Full HD") -> 1080
                quality.contains("720") || quality.contains("HD") -> 720
                quality.contains("480") || quality.contains("SD") -> 480
                else -> 1080 // Default a Full HD
            }
        }
        
        // Filtrar calidades compatibles
        val compatibleQualities = availableQualities.filterIndexed { index, quality ->
            val height = qualityHeights.getOrNull(index) ?: 1080
            isQualityCompatible(height, 0) // Bitrate no disponible en labels
        }
        
        if (compatibleQualities.isEmpty()) {
            Log.w(TAG, "No compatible qualities found")
            return availableQualities.firstOrNull()
        }
        
        // Seleccionar la mejor calidad compatible
        val optimalQuality = getOptimalQuality()
        return when (optimalQuality) {
            "4K" -> compatibleQualities.find { it.contains("4K") || it.contains("2160") }
                ?: compatibleQualities.find { it.contains("2K") || it.contains("1440") }
                ?: compatibleQualities.maxByOrNull { it.contains("1080") || it.contains("Full HD") }
            "2K" -> compatibleQualities.find { it.contains("2K") || it.contains("1440") }
                ?: compatibleQualities.maxByOrNull { it.contains("1080") || it.contains("Full HD") }
            "1080p" -> compatibleQualities.find { it.contains("1080") || it.contains("Full HD") }
                ?: compatibleQualities.find { it.contains("720") || it.contains("HD") }
            else -> compatibleQualities.firstOrNull()
        }
    }
    
    /**
     * Obtiene información del dispositivo para logging
     */
    fun getDeviceInfo(): String {
        val ram = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        
        return buildString {
            appendLine("=== Quality Manager ===")
            appendLine("High End Device: $isHighEndDevice")
            appendLine("Max Resolution: ${maxSupportedResolution.first}x${maxSupportedResolution.second}")
            appendLine("RAM: ${ram}MB")
            appendLine("CPU Cores: $cores")
            appendLine("API Level: ${Build.VERSION.SDK_INT}")
            appendLine("Optimal Quality: ${getOptimalQuality()}")
            appendLine("=========================")
        }
    }
}
