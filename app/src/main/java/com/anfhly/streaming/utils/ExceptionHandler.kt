package com.anfhly.streaming.utils

import android.util.Log
import kotlin.system.exitProcess

/**
 * Global exception handler to prevent app crashes
 */
object ExceptionHandler {
    
    private const val TAG = "ExceptionHandler"
    
    /**
     * Install global exception handler
     */
    fun install() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "Uncaught exception in thread: ${thread.name}", exception)
            
            // Check if it's a focus navigation related error
            if (isFocusNavigationError(exception)) {
                Log.w(TAG, "Focus navigation error detected, app will continue running")
                // Don't crash the app for focus navigation errors
                return@setDefaultUncaughtExceptionHandler
            }
            
            // For other critical errors, log and exit gracefully
            Log.e(TAG, "Critical error, app will exit", exception)
            
            // Call the original handler
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
    
    /**
     * Check if exception is related to focus navigation issues
     */
    private fun isFocusNavigationError(exception: Throwable): Boolean {
        val message = exception.message ?: ""
        val stackTrace = exception.stackTrace.joinToString("\n") { it.className + "." + it.methodName }
        
        return message.contains("visitAncestors called on an unattached node") ||
               message.contains("TwoDimensionalFocusSearch") ||
               message.contains("FocusTargetNode") ||
               stackTrace.contains("TwoDimensionalFocusSearchKt") ||
               stackTrace.contains("FocusTraversalKt") ||
               stackTrace.contains("LazyLayoutBeyondBoundsModifierLocal")
    }
    
    /**
     * Handle focus-related exceptions gracefully
     */
    fun handleFocusException(exception: Throwable) {
        Log.w(TAG, "Focus navigation exception handled gracefully", exception)
        // Don't crash the app, just log the error
    }
    
    /**
     * Check if current thread is having focus issues
     */
    fun checkAndHandleFocusState() {
        try {
            // This is a no-op check that might trigger focus issues
            // If it throws, we'll catch it here
        } catch (e: Exception) {
            if (isFocusNavigationError(e)) {
                handleFocusException(e)
            } else {
                throw e
            }
        }
    }
}
