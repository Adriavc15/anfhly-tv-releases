package com.anfhly.streaming.data.model

/**
 * Result wrapper for Firebase operations
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String = exception.message ?: "Unknown error") : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

/**
 * Auth state
 */
sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Content loading state
 */
sealed class ContentLoadState {
    data object Loading : ContentLoadState()
    data class Success(val categories: List<ContentCategory>, val featured: List<Content>) : ContentLoadState()
    data class Error(val message: String) : ContentLoadState()
}

/**
 * Player state
 */
sealed class PlayerState {
    data object Idle : PlayerState()
    data object Loading : PlayerState()
    data object Playing : PlayerState()
    data object Paused : PlayerState()
    data object Buffering : PlayerState()
    data object Finished : PlayerState()
    data class Error(val message: String) : PlayerState()
}
