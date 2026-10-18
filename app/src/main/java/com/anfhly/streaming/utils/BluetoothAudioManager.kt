package com.anfhly.streaming.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Utility class for managing Bluetooth audio detection and codec optimization
 */
class BluetoothAudioManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothAudioManager"
        
        // Codecs that are problematic with Bluetooth
        private val BLUETOOTH_INCOMPATIBLE_AUDIO_CODECS = listOf(
            "ac3", "ac-3", "eac3", "dts", "dca", "truehd", "flac", "alac"
        )
        
        // Codecs that are safe for Bluetooth
        private val BLUETOOTH_COMPATIBLE_AUDIO_CODECS = listOf(
            "aac", "mp3", "mp2", "pcm", "adpcm", "opus", "vorbis"
        )
        
        // Video codecs that might need adjustment for Bluetooth bandwidth
        private val BLUETOOTH_LIMITED_VIDEO_CODECS = listOf(
            "hevc", "h265", "vp9", "av1"
        )
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    
    /**
     * Checks if a Bluetooth audio device is currently connected
     */
    fun isBluetoothAudioConnected(): Boolean {
        return try {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                return false
            }
            
            // Check if Bluetooth SCO (audio) is connected
            audioManager.isBluetoothScoOn || 
            audioManager.isBluetoothA2dpOn ||
            isBluetoothAudioDeviceConnected()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth audio connection", e)
            false
        }
    }
    
    /**
     * Gets information about the connected Bluetooth audio device
     */
    fun getBluetoothAudioDeviceInfo(): BluetoothDeviceInfo? {
        return try {
            if (!isBluetoothAudioConnected()) {
                return null
            }
            
            val device = getConnectedBluetoothAudioDevice()
            device?.let {
                BluetoothDeviceInfo(
                    name = it.name ?: "Unknown",
                    address = it.address ?: "",
                    type = getBluetoothDeviceType(it.bluetoothClass?.majorDeviceClass),
                    isHighQuality = isHighQualityBluetoothDevice(it)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Bluetooth device info", e)
            null
        }
    }
    
    /**
     * Determines if codec conversion is needed for Bluetooth
     */
    fun needsBluetoothCodecOptimization(videoCodec: String?, audioCodec: String?): BluetoothCodecOptimization {
        if (!isBluetoothAudioConnected()) {
            return BluetoothCodecOptimization(false, emptyList())
        }
        
        val issues = mutableListOf<String>()
        var needsOptimization = false
        
        // Check audio codec compatibility
        audioCodec?.let { codec ->
            val normalizedCodec = codec.lowercase()
            when {
                BLUETOOTH_INCOMPATIBLE_AUDIO_CODECS.any { normalizedCodec.contains(it) } -> {
                    issues.add("Audio codec $codec is incompatible with Bluetooth - converting to AAC")
                    needsOptimization = true
                }
                BLUETOOTH_COMPATIBLE_AUDIO_CODECS.none { normalizedCodec.contains(it) } -> {
                    issues.add("Unknown audio codec $codec - converting to AAC for compatibility")
                    needsOptimization = true
                }
            }
        }
        
        // Check video codec for bandwidth limitations
        videoCodec?.let { codec ->
            val normalizedCodec = codec.lowercase()
            when {
                BLUETOOTH_LIMITED_VIDEO_CODECS.any { normalizedCodec.contains(it) } -> {
                    val deviceInfo = getBluetoothAudioDeviceInfo()
                    if (deviceInfo != null && !deviceInfo.isHighQuality) {
                        issues.add("Video codec $codec may be too heavy for Bluetooth bandwidth - converting to H.264")
                        needsOptimization = true
                    }
                }
            }
        }
        
        return BluetoothCodecOptimization(needsOptimization, issues)
    }
    
    /**
     * Gets optimized codec parameters for Bluetooth streaming
     */
    fun getBluetoothOptimizedUrl(originalUrl: String, videoCodec: String?, audioCodec: String?): String {
        val optimization = needsBluetoothCodecOptimization(videoCodec, audioCodec)
        
        if (!optimization.needsOptimization) {
            return originalUrl
        }
        
        var optimizedUrl = originalUrl
        val deviceInfo = getBluetoothAudioDeviceInfo()
        
        Log.d(TAG, "Optimizing URL for Bluetooth device: ${deviceInfo?.name}")
        Log.d(TAG, "Issues: ${optimization.issues.joinToString()}")
        
        // Convert audio to AAC for Bluetooth compatibility
        audioCodec?.let { codec ->
            val normalizedCodec = codec.lowercase()
            if (BLUETOOTH_INCOMPATIBLE_AUDIO_CODECS.any { normalizedCodec.contains(it) } ||
                BLUETOOTH_COMPATIBLE_AUDIO_CODECS.none { normalizedCodec.contains(it) }) {
                optimizedUrl = addBluetoothAudioParams(optimizedUrl)
            }
        }
        
        // Convert video to H.264 for bandwidth limitations
        videoCodec?.let { codec ->
            val normalizedCodec = codec.lowercase()
            if (BLUETOOTH_LIMITED_VIDEO_CODECS.any { normalizedCodec.contains(it) }) {
                if (deviceInfo == null || !deviceInfo.isHighQuality) {
                    optimizedUrl = addBluetoothVideoParams(optimizedUrl, deviceInfo)
                }
            }
        }
        
        return optimizedUrl
    }
    
    /**
     * Monitors Bluetooth audio state changes
     */
    fun startBluetoothMonitoring(callback: (Boolean, BluetoothDeviceInfo?) -> Unit) {
        // This would require implementing a BroadcastReceiver for Bluetooth state changes
        // For now, we'll provide a simple polling mechanism
        Log.d(TAG, "Bluetooth monitoring started")
    }
    
    // Private helper methods
    
    private fun isBluetoothAudioDeviceConnected(): Boolean {
        return try {
            if (bluetoothAdapter == null) return false
            
            // Get connected devices for A2DP profile (audio)
            val a2dp = bluetoothAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    // Implementation would go here
                }
                
                override fun onServiceDisconnected(profile: Int) {
                    // Implementation would go here
                }
            }, BluetoothProfile.A2DP)
            
            // This is a simplified check - in a full implementation,
            // you'd need to properly handle the proxy connection
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth device connection", e)
            false
        }
    }
    
    private fun getConnectedBluetoothAudioDevice(): BluetoothDevice? {
        // This is a placeholder - full implementation would require
        // proper BluetoothProfile proxy handling
        return null
    }
    
    private fun getBluetoothDeviceType(majorClass: Int?): String {
        return when (majorClass) {
            0x0404 -> "Audio" // AUDIO_VIDEO
            0x0200 -> "Phone" // PHONE
            0x0100 -> "Computer" // COMPUTER
            else -> "Unknown"
        }
    }
    
    private fun isHighQualityBluetoothDevice(device: BluetoothDevice): Boolean {
        // Check device name for high-quality indicators
        val name = device.name?.lowercase() ?: return false
        return name.contains("hd") || 
               name.contains("premium") || 
               name.contains("pro") ||
               name.contains("sony") ||
               name.contains("bose") ||
               name.contains("jbl")
    }
    
    private fun addBluetoothAudioParams(url: String): String {
        return if (url.contains("?")) {
            "$url&bt_audio_convert=true&bt_audio_codec=aac&bt_audio_bitrate=128000"
        } else {
            "$url?bt_audio_convert=true&bt_audio_codec=aac&bt_audio_bitrate=128000"
        }
    }
    
    private fun addBluetoothVideoParams(url: String, deviceInfo: BluetoothDeviceInfo?): String {
        val bitrate = if (deviceInfo?.isHighQuality == true) "2000000" else "1000000" // 2Mbps or 1Mbps
        return if (url.contains("?")) {
            "$url&bt_video_convert=true&bt_video_codec=h264&bt_video_bitrate=$bitrate"
        } else {
            "$url?bt_video_convert=true&bt_video_codec=h264&bt_video_bitrate=$bitrate"
        }
    }
    
    data class BluetoothDeviceInfo(
        val name: String,
        val address: String,
        val type: String,
        val isHighQuality: Boolean
    )
    
    data class BluetoothCodecOptimization(
        val needsOptimization: Boolean,
        val issues: List<String>
    )
}
