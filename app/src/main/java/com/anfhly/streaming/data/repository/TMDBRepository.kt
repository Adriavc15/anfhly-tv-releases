package com.anfhly.streaming.data.repository

import android.util.Log
import com.anfhly.streaming.data.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Deferred
import com.anfhly.streaming.data.model.Content
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TMDBRepository {
    
    private val TAG = "TMDBRepository"
    private val TMDB_API_KEY = "b27409ea512b7325fbd926df32b5f395"
    private val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    private val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p"
    
    /**
     * Get TMDB image URL
     */
    fun getTMDBImageUrl(path: String?, size: String = "w500"): String? {
        if (path.isNullOrEmpty()) return null
        if (path.startsWith("http")) return path
        return "$TMDB_IMAGE_BASE_URL/$size$path"
    }
    
    /**
     * Enrich episodes with TMDB data (still_path and proper name)
     * This calls TMDB API to get episode details with language fallback:
     * es-MX -> es-ES -> en-US
     */
    suspend fun enrichEpisodesWithTMDB(
        tmdbId: Int?, 
        seasonNumber: Int, 
        episodes: List<Episode>
    ): List<Episode> {
        if (tmdbId == null) {
            Log.w(TAG, "No tmdbId provided, cannot enrich episodes")
            return episodes
        }
        
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Enriching episodes with TMDB: tmdbId=$tmdbId, season=$seasonNumber")
                
                // Fetch with language fallback: es-MX -> es-ES -> en-US
                val tmdbEpisodes = fetchSeasonEpisodesWithLanguageFallback(tmdbId, seasonNumber)
                
                if (tmdbEpisodes.isEmpty()) {
                    Log.w(TAG, "No TMDB episodes found")
                    return@withContext episodes
                }
                
                // Map TMDB data to our episodes
                val enrichedEpisodes = episodes.map { episode ->
                    val tmdbEpisode = tmdbEpisodes.find { 
                        it.optInt("episode_number") == episode.episodeNumber 
                    }
                    
                    if (tmdbEpisode != null) {
                        val stillPath = tmdbEpisode.optString("still_path", "")
                        val tmdbName = tmdbEpisode.optString("name", "")
                        val tmdbOverview = tmdbEpisode.optString("overview", "")
                        
                        episode.copy(
                            name = if (tmdbName.isNotEmpty()) tmdbName else episode.name,
                            stillUrl = if (stillPath.isNotEmpty()) {
                                getTMDBImageUrl(stillPath, "w500")
                            } else episode.stillUrl,
                            overview = if (tmdbOverview.isNotEmpty()) tmdbOverview else episode.overview
                        )
                    } else {
                        Log.w(TAG, "No TMDB match for episode ${episode.episodeNumber}")
                        episode
                    }
                }
                
                Log.d(TAG, "Enriched ${enrichedEpisodes.size} episodes with TMDB data")
                enrichedEpisodes
                
            } catch (e: Exception) {
                Log.e(TAG, "Error enriching episodes with TMDB", e)
                episodes
            }
        }
    }
    
    /**
     * Fetch season episodes with language fallback:
     * 1. Try es-MX (Spanish Mexico)
     * 2. Try es-ES (Spanish Spain) if es-MX name is empty
     * 3. Try en-US (English) if both Spanish versions are empty
     */
    private fun fetchSeasonEpisodesWithLanguageFallback(tvId: Int, seasonNumber: Int): List<JSONObject> {
        // Try es-MX first
        val esMXEpisodes = fetchSeasonEpisodesFromTMDB(tvId, seasonNumber, "es-MX")
        Log.d(TAG, "es-MX: Found ${esMXEpisodes.size} episodes")
        
        // Check if es-MX has valid data (non-empty names)
        val hasValidESMX = esMXEpisodes.any { it.optString("name", "").isNotEmpty() }
        
        if (hasValidESMX) {
            Log.d(TAG, "Using es-MX data (has valid names)")
            return esMXEpisodes
        }
        
        // Try es-ES if es-MX has no valid names
        val esESEpisodes = fetchSeasonEpisodesFromTMDB(tvId, seasonNumber, "es-ES")
        Log.d(TAG, "es-ES: Found ${esESEpisodes.size} episodes")
        
        val hasValidESES = esESEpisodes.any { it.optString("name", "").isNotEmpty() }
        
        if (hasValidESES) {
            Log.d(TAG, "Using es-ES data (has valid names)")
            return esESEpisodes
        }
        
        // Try en-US as last resort
        val enUSEpisodes = fetchSeasonEpisodesFromTMDB(tvId, seasonNumber, "en-US")
        Log.d(TAG, "en-US: Found ${enUSEpisodes.size} episodes")
        
        val hasValidENUS = enUSEpisodes.any { it.optString("name", "").isNotEmpty() }
        
        if (hasValidENUS) {
            Log.d(TAG, "Using en-US data (has valid names)")
            return enUSEpisodes
        }
        
        // Return whatever we got first, even if empty
        Log.w(TAG, "No valid episode names found in any language")
        return esMXEpisodes
    }
    
    /**
     * Fetch season episodes from TMDB API with specific language
     */
    private fun fetchSeasonEpisodesFromTMDB(tvId: Int, seasonNumber: Int, language: String): List<JSONObject> {
        val url = URL("$TMDB_BASE_URL/tv/$tvId/season/$seasonNumber?api_key=$TMDB_API_KEY&language=$language")
        
        return try {
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "TMDB API response code for $language: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val episodesArray = json.optJSONArray("episodes") ?: return emptyList()
                
                val episodes = mutableListOf<JSONObject>()
                for (i in 0 until episodesArray.length()) {
                    episodes.add(episodesArray.getJSONObject(i))
                }
                
                Log.d(TAG, "Fetched ${episodes.size} episodes from TMDB in $language")
                episodes
            } else {
                Log.e(TAG, "TMDB API error for $language: $responseCode")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from TMDB in $language", e)
            emptyList()
        }
    }

    /**
     * Get Top Trending Movies & TV Shows of the Week from TMDB (with logo fetching)
     */
    suspend fun getTrendingMoviesAndSeries(): List<Content> = kotlinx.coroutines.coroutineScope {
        withContext(Dispatchers.IO) {
            val url = URL("$TMDB_BASE_URL/trending/all/week?api_key=$TMDB_API_KEY&language=es-MX")
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "TMDB Trending API response code: $responseCode")
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val resultsArray = json.optJSONArray("results") ?: return@withContext emptyList()
                    
                    val limit = minOf(resultsArray.length(), 12) // Fetch top 12 trending
                    val deferredList = (0 until limit).map { i ->
                        async {
                            try {
                                val item = resultsArray.getJSONObject(i)
                                val id = item.optInt("id")
                                val mediaType = item.optString("media_type")
                                
                                val title = item.optString("title", "")
                                    .ifEmpty { item.optString("name", "") }
                                    .ifEmpty { item.optString("original_title", "") }
                                    .ifEmpty { item.optString("original_name", "") }
                                    
                                val overview = item.optString("overview", "")
                                val posterPath = item.optString("poster_path", "")
                                val backdropPath = item.optString("backdrop_path", "")
                                val voteAverage = item.optDouble("vote_average", 0.0)
                                val releaseDate = item.optString("release_date", "")
                                    .ifEmpty { item.optString("first_air_date", "") }
                                    
                                val posterUrl = getTMDBImageUrl(posterPath, "w500")
                                val backdropUrl = getTMDBImageUrl(backdropPath, "original")
                                val backdropPublicityUrl = getTMDBImageUrl(backdropPath, "original")
                                
                                // Fetch logo in parallel
                                val logoPath = getLogoPathFromTMDB(mediaType, id)
                                val logoUrl = getTMDBImageUrl(logoPath, "w500")
                                
                                if (id != 0 && title.isNotEmpty()) {
                                    if (mediaType == "movie") {
                                        Content.Movie(
                                            id = "tmdb_$id",
                                            title = title,
                                            overview = overview,
                                            posterUrl = posterUrl,
                                            backdropUrl = backdropUrl,
                                            backdropPublicityUrl = backdropPublicityUrl,
                                            voteAverage = voteAverage,
                                            releaseDate = releaseDate,
                                            tmdbId = id,
                                            logoUrl = logoUrl
                                        )
                                    } else if (mediaType == "tv") {
                                        Content.Series(
                                            id = "tmdb_$id",
                                            title = title,
                                            overview = overview,
                                            posterUrl = posterUrl,
                                            backdropUrl = backdropUrl,
                                            backdropPublicityUrl = backdropPublicityUrl,
                                            voteAverage = voteAverage,
                                            releaseDate = releaseDate,
                                            tmdbId = id,
                                            logoUrl = logoUrl
                                        )
                                    } else {
                                        null
                                    }
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing item $i", e)
                                null
                            }
                        }
                    }
                    deferredList.awaitAll().filterNotNull()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching trending movies and series from TMDB", e)
                emptyList()
            }
        }
    }

    /**
     * Fetch Logo image path from TMDB images API
     */
    suspend fun getLogoPathFromTMDB(mediaType: String, id: Int): String? {
        val typePath = if (mediaType == "movie") "movie" else "tv"
        val url = URL("$TMDB_BASE_URL/$typePath/$id/images?api_key=$TMDB_API_KEY")
        return withContext(Dispatchers.IO) {
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val logosArray = json.optJSONArray("logos") ?: return@withContext null
                    if (logosArray.length() > 0) {
                        // Try to find Spanish/Mexican logo first
                        var bestLogoPath: String? = null
                        for (i in 0 until logosArray.length()) {
                            val logoObj = logosArray.getJSONObject(i)
                            val lang = logoObj.optString("iso_639_1")
                            if (lang == "es" || lang == "mx") {
                                bestLogoPath = logoObj.optString("file_path")
                                break
                            }
                        }
                        // Fallback to English logo
                        if (bestLogoPath == null) {
                            for (i in 0 until logosArray.length()) {
                                val logoObj = logosArray.getJSONObject(i)
                                val lang = logoObj.optString("iso_639_1")
                                if (lang == "en") {
                                    bestLogoPath = logoObj.optString("file_path")
                                    break
                                }
                            }
                        }
                        // Fallback to first available logo
                        if (bestLogoPath == null) {
                            bestLogoPath = logosArray.getJSONObject(0).optString("file_path")
                        }
                        bestLogoPath
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching logo for $mediaType $id", e)
                null
            }
        }
    }
}
