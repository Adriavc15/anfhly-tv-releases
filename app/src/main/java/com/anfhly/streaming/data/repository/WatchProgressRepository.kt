package com.anfhly.streaming.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.anfhly.streaming.data.model.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WatchProgress(
    val contentId: String,
    val contentType: String, // "movie" or "series"
    val title: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatched: Long = System.currentTimeMillis()
)

class WatchProgressRepository(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "watch_progress"
        private const val TAG = "WatchProgressRepository"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Save watch progress for a movie
     */
    suspend fun saveMovieProgress(
        movieId: String,
        positionMs: Long,
        durationMs: Long,
        title: String? = null,
        posterUrl: String? = null,
        backdropUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val progress = WatchProgress(
                contentId = movieId,
                contentType = "movie",
                title = title,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                positionMs = positionMs,
                durationMs = durationMs,
                lastWatched = System.currentTimeMillis()
            )
            saveProgress(progress)
            Log.d(TAG, "Saved movie progress: $movieId ($title) at ${positionMs}ms / ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving movie progress", e)
        }
    }
    
    /**
     * Save watch progress for a series episode
     */
    suspend fun saveEpisodeProgress(
        seriesId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long,
        title: String? = null,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        episodeTitle: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val progress = WatchProgress(
                contentId = seriesId,
                contentType = "series",
                title = title,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                positionMs = positionMs,
                durationMs = durationMs,
                lastWatched = System.currentTimeMillis()
            )
            saveProgress(progress)
            Log.d(TAG, "Saved episode progress: $seriesId S${seasonNumber}E${episodeNumber} ($title) at ${positionMs}ms / ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving episode progress", e)
        }
    }
    
    /**
     * Get watch progress for a movie
     */
    suspend fun getMovieProgress(movieId: String): WatchProgress? = withContext(Dispatchers.IO) {
        getProgress(movieId, "movie", null, null)
    }
    
    /**
     * Get watch progress for a series episode
     */
    suspend fun getEpisodeProgress(
        seriesId: String,
        seasonNumber: Int,
        episodeNumber: Int
    ): WatchProgress? = withContext(Dispatchers.IO) {
        getProgress(seriesId, "series", seasonNumber, episodeNumber)
    }
    
    /**
     * Get the last watched episode for a series
     */
    suspend fun getLastWatchedEpisode(seriesId: String): WatchProgress? = withContext(Dispatchers.IO) {
        try {
            val allProgress = getAllProgress()
            allProgress
                .filter { it.contentId == seriesId && it.contentType == "series" }
                .maxByOrNull { it.lastWatched }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last watched episode", e)
            null
        }
    }
    
    /**
     * Check if there's progress for a movie that can be continued
     */
    suspend fun hasMovieProgress(movieId: String): Boolean = withContext(Dispatchers.IO) {
        val progress = getMovieProgress(movieId)
        progress != null && progress.positionMs > 10000 && progress.positionMs < progress.durationMs - 10000
    }
    
    /**
     * Check if there's progress for a series episode that can be continued
     */
    suspend fun hasEpisodeProgress(seriesId: String, seasonNumber: Int, episodeNumber: Int): Boolean = withContext(Dispatchers.IO) {
        val progress = getEpisodeProgress(seriesId, seasonNumber, episodeNumber)
        progress != null && progress.positionMs > 10000 && progress.positionMs < progress.durationMs - 10000
    }
    
    /**
     * Clear progress for a movie
     */
    suspend fun clearMovieProgress(movieId: String) = withContext(Dispatchers.IO) {
        val key = getKey(movieId, "movie", null, null)
        prefs.edit().remove(key).apply()
        Log.d(TAG, "Cleared movie progress: $movieId")
    }
    
    /**
     * Clear progress for a series episode
     */
    suspend fun clearEpisodeProgress(seriesId: String, seasonNumber: Int, episodeNumber: Int) = withContext(Dispatchers.IO) {
        val key = getKey(seriesId, "series", seasonNumber, episodeNumber)
        prefs.edit().remove(key).apply()
        Log.d(TAG, "Cleared episode progress: $seriesId S${seasonNumber}E${episodeNumber}")
    }
    
    /**
     * Clear all progress
     */
    suspend fun clearAllProgress() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all watch progress")
    }

    /**
     * Get continue watching list sorted by lastWatched descending.
     * Filters out finished content (>95%) and very short previews (<15s).
     */
    suspend fun getContinueWatchingList(): List<WatchProgress> = withContext(Dispatchers.IO) {
        try {
            getAllProgress()
                .filter { it.positionMs >= 15_000L && it.durationMs > 0 && it.positionMs < (it.durationMs * 0.95) }
                .sortedByDescending { it.lastWatched }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting continue watching list", e)
            emptyList()
        }
    }
    
    private fun saveProgress(progress: WatchProgress) {
        val key = getKey(progress.contentId, progress.contentType, progress.seasonNumber, progress.episodeNumber)
        val jsonString = json.encodeToString(progress)
        prefs.edit().putString(key, jsonString).apply()
    }
    
    private fun getProgress(
        contentId: String,
        contentType: String,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): WatchProgress? {
        val key = getKey(contentId, contentType, seasonNumber, episodeNumber)
        val jsonString = prefs.getString(key, null) ?: return null
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing watch progress", e)
            null
        }
    }
    
    private fun getAllProgress(): List<WatchProgress> {
        return prefs.all.values.mapNotNull { value ->
            try {
                if (value is String) {
                    json.decodeFromString<WatchProgress>(value)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun getKey(contentId: String, contentType: String, seasonNumber: Int?, episodeNumber: Int?): String {
        return if (contentType == "series" && seasonNumber != null && episodeNumber != null) {
            "${contentType}_${contentId}_S${seasonNumber}E${episodeNumber}"
        } else {
            "${contentType}_${contentId}"
        }
    }
}
