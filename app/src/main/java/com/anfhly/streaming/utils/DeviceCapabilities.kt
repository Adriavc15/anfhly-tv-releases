package com.anfhly.streaming.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import kotlin.math.min

/**
 * Detecta las capacidades del dispositivo para determinar la calidad óptima de video
 */
@UnstableApi
class DeviceCapabilities(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceCapabilities"
        
        // Niveles de capacidad del dispositivo
        enum class PerformanceLevel {
            LOW,      // Dispositivos de gama baja
            MEDIUM,   // Dispositivos de gama media
            HIGH,      // Dispositivos de gama alta
            PREMIUM    // Dispositivos premium
        }
        
        // Calidades de video recomendadas por nivel
        data class QualityProfile(
            val maxHeight: Int,
            val maxBitrate: Int, // en bits por segundo
            val preferredCodec: String,
            val name: String
        )
    }
    
    private val performanceLevel: PerformanceLevel
    private val maxVideoResolution: Pair<Int, Int>
    private val supportedCodecs: Set<String>
    private val hasHardwareAcceleration: Boolean
    
    init {
        performanceLevel = detectPerformanceLevel()
        maxVideoResolution = detectMaxVideoResolution()
        supportedCodecs = detectSupportedCodecs()
        hasHardwareAcceleration = detectHardwareAcceleration()
        
        Log.d(TAG, "Device capabilities - Level: $performanceLevel, Max Resolution: $maxVideoResolution, HW Acceleration: $hasHardwareAcceleration")
    }
    
    /**
     * Detecta el nivel de rendimiento del dispositivo
     */
    private fun detectPerformanceLevel(): PerformanceLevel {
        val ramInfo = getRamInfo()
        val cpuInfo = getCpuInfo()
        val gpuInfo = getGpuInfo()
        
        // Puntuación basada en múltiples factores
        var score = 0
        
        // RAM (máximo 4 puntos)
        score += when {
            ramInfo.first >= 8192 -> 4 // 8GB+
            ramInfo.first >= 6144 -> 3 // 6GB+
            ramInfo.first >= 4096 -> 2 // 4GB+
            ramInfo.first >= 2048 -> 1 // 2GB+
            else -> 0
        }
        
        // CPU cores (máximo 3 puntos)
        score += when {
            cpuInfo.first >= 8 -> 3 // 8+ cores
            cpuInfo.first >= 6 -> 2 // 6-7 cores
            cpuInfo.first >= 4 -> 1 // 4-5 cores
            else -> 0
        }
        
        // GPU (máximo 3 puntos)
        score += when {
            gpuInfo.contains("adreno") && gpuInfo.contains("7") -> 3 // GPU Adreno 7xx (alta)
            gpuInfo.contains("adreno") && gpuInfo.contains("6") -> 2 // GPU Adreno 6xx (media)
            gpuInfo.contains("mali") && gpuInfo.contains("g7") -> 2 // Mali G7x (media)
            gpuInfo.contains("mali") && gpuInfo.contains("g5") -> 1 // Mali G5x (baja)
            gpuInfo.contains("powervr") -> 2 // PowerVR (media)
            else -> 0
        }
        
        // Versión de Android (máximo 2 puntos)
        val apiLevel = Build.VERSION.SDK_INT
        score += when {
            apiLevel >= 33 -> 2 // Android 13+
            apiLevel >= 30 -> 1 // Android 11+
            else -> 0
        }
        
        return when {
            score >= 10 -> PerformanceLevel.PREMIUM
            score >= 7 -> PerformanceLevel.HIGH
            score >= 4 -> PerformanceLevel.MEDIUM
            else -> PerformanceLevel.LOW
        }
    }
    
    /**
     * Detecta la resolución máxima de video soportada
     */
    private fun detectMaxVideoResolution(): Pair<Int, Int> {
        return when (performanceLevel) {
            PerformanceLevel.PREMIUM -> Pair(3840, 2160)   // 4K
            PerformanceLevel.HIGH -> Pair(2560, 1440)      // 2K
            PerformanceLevel.MEDIUM -> Pair(1920, 1080)    // Full HD
            PerformanceLevel.LOW -> Pair(1280, 720)       // HD
        }
    }
    
    /**
     * Detecta los codecs soportados por el dispositivo
     */
    private fun detectSupportedCodecs(): Set<String> {
        val codecs = mutableSetOf<String>()
        
        try {
            val packageManager = context.packageManager
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                // Asumir soporte básico para codecs comunes
                codecs.addAll(listOf("h264", "hevc", "vp9", "av1"))
            }
            
            // Detección más específica basada en el nivel de API
            if (Build.VERSION.SDK_INT >= 29) {
                codecs.add("av1") // Android 10+ soporta AV1
            }
            if (Build.VERSION.SDK_INT >= 21) {
                codecs.add("hevc") // Android 5+ soporta HEVC
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting supported codecs", e)
        }
        
        return codecs
    }
    
    /**
     * Detecta si hay aceleración por hardware
     */
    private fun detectHardwareAcceleration(): Boolean {
        return try {
            val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) ||
                           context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
            hasFeature || performanceLevel >= PerformanceLevel.MEDIUM
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting hardware acceleration", e)
            false
        }
    }
    
    /**
     * Obtiene información de la RAM
     */
    private fun getRamInfo(): Pair<Int, String> {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo) // BUG FIX: se debe llamar para poblar los campos
            val totalRam = (memoryInfo.totalMem / (1024 * 1024)).toInt() // Convertir a MB
            Pair(totalRam, "${totalRam}MB")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RAM info", e)
            Pair(2048, "2GB") // Valor por defecto
        }
    }

    /**
     * Obtiene la RAM libre disponible en tiempo real (en MB).
     * Más preciso que totalRam para decisiones de rendimiento durante reproducción.
     */
    fun getFreeRamMb(): Int {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            (memoryInfo.availMem / (1024 * 1024)).toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting free RAM", e)
            512 // Asumir 512 MB libres si hay error
        }
    }

    /**
     * Niveles de rendimiento para reproducción de video según RAM libre disponible.
     */
    enum class PlaybackTier { CRITICAL, LOW, MEDIUM, HIGH }

    /**
     * Determina el tier de reproducción basado en RAM libre real en el momento de reproducir.
     */
    fun getPlaybackTier(): PlaybackTier {
        val freeMb = getFreeRamMb()
        return when {
            freeMb < 300  -> PlaybackTier.CRITICAL // < 300 MB libres: emergencia
            freeMb < 600  -> PlaybackTier.LOW      // 300-600 MB libres: gama muy baja
            freeMb < 1200 -> PlaybackTier.MEDIUM   // 600 MB - 1.2 GB libres: gama media
            else          -> PlaybackTier.HIGH     // > 1.2 GB libres: gama alta
        }.also {
            Log.d(TAG, "PlaybackTier=$it (freeRam=${freeMb}MB)")
        }
    }
    
    /**
     * Obtiene información del CPU
     */
    private fun getCpuInfo(): Pair<Int, String> {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            val cpuName = when {
                Build.HARDWARE.contains("qcom") -> "Qualcomm"
                Build.HARDWARE.contains("mtk") -> "MediaTek"
                Build.HARDWARE.contains("exynos") -> "Samsung Exynos"
                Build.HARDWARE.contains("kirin") -> "HiSilicon Kirin"
                Build.HARDWARE.contains("tegra") -> "NVIDIA Tegra"
                else -> Build.HARDWARE
            }
            Pair(cores, cpuName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting CPU info", e)
            Pair(4, "Unknown")
        }
    }
    
    /**
     * Obtiene información del GPU
     */
    private fun getGpuInfo(): String {
        return try {
            // Fallback a información de Build (más compatible)
            when {
                Build.HARDWARE.contains("qcom") -> "adreno"
                Build.HARDWARE.contains("mtk") -> "mali"
                Build.HARDWARE.contains("exynos") -> "mali"
                Build.HARDWARE.contains("tegra") -> "tegra"
                else -> "unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting GPU info", e)
            "unknown"
        }
    }
    
    /**
     * Obtiene el perfil de calidad óptimo para el dispositivo
     */
    fun getOptimalQualityProfile(): QualityProfile {
        return when (performanceLevel) {
            PerformanceLevel.PREMIUM -> QualityProfile(
                maxHeight = 2160,
                maxBitrate = 20_000_000, // 20 Mbps
                preferredCodec = "hevc",
                name = "4K Premium"
            )
            PerformanceLevel.HIGH -> QualityProfile(
                maxHeight = 1440,
                maxBitrate = 12_000_000, // 12 Mbps
                preferredCodec = "h264",
                name = "2K Alta"
            )
            PerformanceLevel.MEDIUM -> QualityProfile(
                maxHeight = 1080,
                maxBitrate = 8_000_000, // 8 Mbps
                preferredCodec = "h264",
                name = "HD Estándar"
            )
            PerformanceLevel.LOW -> QualityProfile(
                maxHeight = 720,
                maxBitrate = 4_000_000, // 4 Mbps
                preferredCodec = "h264",
                name = "HD Básica"
            )
        }
    }
    
    /**
     * Determina si una calidad específica es compatible con el dispositivo
     */
    fun isQualityCompatible(height: Int, bitrate: Int, codec: String): Boolean {
        val maxHeight = maxVideoResolution.second
        val maxBitrate = getOptimalQualityProfile().maxBitrate
        
        val heightCompatible = height <= maxHeight
        val bitrateCompatible = bitrate <= maxBitrate
        val codecCompatible = supportedCodecs.contains(codec.lowercase())
        
        Log.d(TAG, "Quality compatibility - Height: $height <= $maxHeight = $heightCompatible, Bitrate: $bitrate <= $maxBitrate = $bitrateCompatible, Codec: $codec in $supportedCodecs = $codecCompatible")
        
        return heightCompatible && bitrateCompatible && codecCompatible
    }
    
    /**
     * Selecciona la mejor calidad disponible de una lista de opciones
     */
    fun selectBestQuality(availableQualities: List<Triple<String, Int, Int>>): Triple<String, Int, Int>? {
        if (availableQualities.isEmpty()) return null
        
        val optimalProfile = getOptimalQualityProfile()
        
        // Filtrar calidades compatibles
        val compatibleQualities = availableQualities.filter { (url, height, bitrate) ->
            isQualityCompatible(height, bitrate, "h264") // Asumir H264 como codec base
        }
        
        if (compatibleQualities.isEmpty()) {
            Log.w(TAG, "No compatible qualities found, returning highest quality")
            return availableQualities.maxByOrNull { (url, height, bitrate) -> 
                height * 1000 + bitrate / 1000 // Ponderar altura más que bitrate
            }
        }
        
        // Seleccionar la mejor calidad compatible basada en el perfil óptimo
        return compatibleQualities.minByOrNull { (url, height, bitrate) ->
            // Calcular distancia del perfil óptimo
            val heightDiff = kotlin.math.abs(height - optimalProfile.maxHeight)
            val bitrateDiff = kotlin.math.abs(bitrate - optimalProfile.maxBitrate)
            
            // Normalizar diferencias (altura más importante que bitrate)
            val normalizedHeightDiff = heightDiff.toDouble() / optimalProfile.maxHeight
            val normalizedBitrateDiff = bitrateDiff.toDouble() / optimalProfile.maxBitrate
            
            // Ponderar: 70% altura, 30% bitrate
            normalizedHeightDiff * 0.7 + normalizedBitrateDiff * 0.3
        }
    }
    
    /**
     * Selecciona la mejor calidad disponible de una lista de opciones (versión para indices)
     */
    fun selectBestQualityFromIndices(availableQualities: List<Triple<Int, Int, Int>>): Triple<Int, Int, Int>? {
        if (availableQualities.isEmpty()) return null
        
        val optimalProfile = getOptimalQualityProfile()
        
        // Filtrar calidades compatibles
        val compatibleQualities = availableQualities.filter { (url, height, bitrate) ->
            isQualityCompatible(height, bitrate, "h264") // Asumir H264 como codec base
        }
        
        if (compatibleQualities.isEmpty()) {
            Log.w(TAG, "No compatible qualities found, returning highest quality")
            return availableQualities.maxByOrNull { (url, height, bitrate) -> 
                height * 1000 + bitrate / 1000 // Ponderar altura más que bitrate
            }
        }
        
        // Seleccionar la mejor calidad compatible basada en el perfil óptimo
        return compatibleQualities.minByOrNull { (url, height, bitrate) ->
            // Calcular distancia del perfil óptimo
            val heightDiff = kotlin.math.abs(height - optimalProfile.maxHeight)
            val bitrateDiff = kotlin.math.abs(bitrate - optimalProfile.maxBitrate)
            
            // Normalizar diferencias (altura más importante que bitrate)
            val normalizedHeightDiff = heightDiff.toDouble() / optimalProfile.maxHeight
            val normalizedBitrateDiff = bitrateDiff.toDouble() / optimalProfile.maxBitrate
            
            // Ponderar: 70% altura, 30% bitrate
            normalizedHeightDiff * 0.7 + normalizedBitrateDiff * 0.3
        }
    }
    
    /**
     * Crea una configuración de LoadControl optimizada para el dispositivo.
     * Usa RAM libre real en lugar del nivel de performance estático.
     */
    fun createOptimizedLoadControl(): DefaultLoadControl {
        return when (getPlaybackTier()) {
            PlaybackTier.CRITICAL -> DefaultLoadControl.Builder()
                // Buffer mínimo: reduce footprint de memoria al máximo
                .setBufferDurationsMs(3000, 10000, 1000, 2000)
                .setTargetBufferBytes(8 * 1024 * 1024)  // 8 MB
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            PlaybackTier.LOW -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 20000, 1500, 2500)
                .setTargetBufferBytes(16 * 1024 * 1024) // 16 MB
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            PlaybackTier.MEDIUM -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(8000, 30000, 1500, 3000)
                .setTargetBufferBytes(32 * 1024 * 1024) // 32 MB
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            PlaybackTier.HIGH -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 60000, 2500, 5000)
                .setTargetBufferBytes(64 * 1024 * 1024) // 64 MB
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
    }

    /**
     * Devuelve la resolución máxima de video que el dispositivo puede manejar
     * en el momento de reproducción, basado en RAM libre real.
     * Independiente de la preferencia del usuario: es el techo físico real.
     */
    fun getAdaptiveMaxVideoSize(): Pair<Int, Int>? {
        return when (getPlaybackTier()) {
            PlaybackTier.CRITICAL -> Pair(1280, 720)   // 720p máximo si RAM crítica
            PlaybackTier.LOW      -> Pair(1920, 1080)  // 1080p si poca RAM
            PlaybackTier.MEDIUM   -> Pair(2560, 1440)  // 1440p si RAM media
            PlaybackTier.HIGH     -> null               // Sin límite si hay RAM suficiente
        }
    }

    /**
     * Indica si el Audio Offload debe activarse.
     * En dispositivos con poca RAM, el offload puede presionar aún más el sistema.
     */
    fun shouldEnableAudioOffload(): Boolean {
        val tier = getPlaybackTier()
        return tier == PlaybackTier.HIGH || tier == PlaybackTier.MEDIUM
    }
    
    /**
     * Obtiene información detallada del dispositivo para logging
     */
    fun getDeviceInfo(): String {
        val (ram, ramStr) = getRamInfo()
        val (cores, cpuName) = getCpuInfo()
        val gpu = getGpuInfo()
        
        return buildString {
            appendLine("=== Device Capabilities ===")
            appendLine("Performance Level: $performanceLevel")
            appendLine("CPU: $cpuName ($cores cores)")
            appendLine("GPU: $gpu")
            appendLine("RAM: $ramStr")
            appendLine("Max Resolution: ${maxVideoResolution.first}x${maxVideoResolution.second}")
            appendLine("Supported Codecs: ${supportedCodecs.joinToString()}")
            appendLine("Hardware Acceleration: $hasHardwareAcceleration")
            appendLine("Optimal Quality: ${getOptimalQualityProfile().name}")
            appendLine("=========================")
        }
    }
}
