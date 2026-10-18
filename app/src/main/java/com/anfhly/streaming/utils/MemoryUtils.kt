package com.anfhly.streaming.utils

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileInputStream

object MemoryUtils {
    
    private const val TAG = "MemoryUtils"
    
    /**
     * Check if app is running low on memory
     */
    fun isLowMemory(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return activityManager?.isLowRamDevice ?: false
    }
    
    /**
     * Get current memory usage information
     */
    fun getMemoryInfo(context: Context): String {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            
            val totalMemory = memoryInfo.totalMem / (1024 * 1024) // MB
            val availableMemory = memoryInfo.availMem / (1024 * 1024) // MB
            val usedMemory = totalMemory - availableMemory
            val memoryPercent = (usedMemory * 100) / totalMemory
            
            "Memory: $usedMemory/$totalMemory MB (${memoryPercent}%)"
        } catch (e: Exception) {
            "Memory info unavailable: ${e.message}"
        }
    }
    
    /**
     * Log memory usage for debugging
     */
    fun logMemoryUsage(context: Context, tag: String = "MemoryCheck") {
        val memoryInfo = getMemoryInfo(context)
        Log.d(TAG, "[$tag] $memoryInfo")
        
        // Also log heap info
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        val totalMemory = runtime.totalMemory() / (1024 * 1024) // MB
        val freeMemory = runtime.freeMemory() / (1024 * 1024) // MB
        val usedMemory = totalMemory - freeMemory
        
        Log.d(TAG, "[$tag] Heap: $usedMemory/$maxMemory MB (Total: $totalMemory MB)")
    }
    
    /**
     * Force garbage collection (use sparingly)
     */
    fun forceGarbageCollection() {
        System.gc()
        Log.d(TAG, "Forced garbage collection")
    }
    
    /**
     * Get memory usage by process
     */
    fun getProcessMemoryUsage(): String {
        return try {
            // Method 1: Try using Runtime memory info
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
            val totalMemory = runtime.totalMemory() / (1024 * 1024) // MB
            val freeMemory = runtime.freeMemory() / (1024 * 1024) // MB
            val usedMemory = totalMemory - freeMemory
            val memoryPercent = if (maxMemory > 0) (usedMemory * 100) / maxMemory else 0
            
            "Heap: $usedMemory/$maxMemory MB (${memoryPercent}%)"
        } catch (e: Exception) {
            // Method 2: Try reading from /proc/self/status
            try {
                val reader = BufferedReader(InputStreamReader(java.io.FileInputStream("/proc/self/status")))
                var line: String?
                var vmRSS = 0
                
                while (reader.readLine().also { line = it } != null) {
                    if (line?.startsWith("VmRSS:") == true) {
                        val parts = line!!.split("\\s+".toRegex())
                        vmRSS = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        break
                    }
                }
                reader.close()
                
                if (vmRSS > 0) {
                    "Process Memory: ${vmRSS}KB"
                } else {
                    "Process memory info not available"
                }
            } catch (e2: Exception) {
                "Memory info unavailable: ${e.message}"
            }
        }
    }
}
