package com.anfhly.streaming.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class TmdbSeriesResponse(
    val id: Int,
    val name: String,
    val genres: List<TmdbGenre> = emptyList()
)

@Serializable
data class TmdbMovieResponse(
    val id: Int,
    val title: String,
    val genres: List<TmdbGenre> = emptyList()
)

@Serializable
data class TmdbGenre(
    val id: Int,
    val name: String
)

class TmdbApiClient {
    
    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val API_KEY = "b27409ea512b7325fbd926df32b5f395"
        private const val TAG = "TmdbApiClient"
        
        // TMDB Genre ID to name mapping (fallback)
        val GENRE_MAP = mapOf(
            28 to "Acción",
            12 to "Aventura",
            16 to "Animación",
            35 to "Comedia",
            80 to "Crimen",
            99 to "Documental",
            18 to "Drama",
            10751 to "Familia",
            14 to "Fantasía",
            36 to "Historia",
            27 to "Terror",
            10402 to "Música",
            9648 to "Misterio",
            10749 to "Romance",
            878 to "Ciencia Ficción",
            10770 to "Película de TV",
            53 to "Suspenso",
            10752 to "Bélica",
            37 to "Western",
            // TV Genres
            10759 to "Acción y Aventura",
            10762 to "Infantil",
            10763 to "Noticias",
            10764 to "Reality",
            10765 to "Ciencia Ficción y Fantasía",
            10766 to "Telenovela",
            10767 to "Talk Show",
            10768 to "Guerra y Política"
        )
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Get series details from TMDB including genres
     */
    suspend fun getSeriesDetails(tmdbId: Int): TmdbSeriesResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/tv/$tmdbId".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", API_KEY)
                .addQueryParameter("language", "es-ES")
                .build()
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val seriesDetails = json.decodeFromString<TmdbSeriesResponse>(body)
                    Log.d(TAG, "Got series details for ID $tmdbId: ${seriesDetails.genres.map { it.name }}")
                    return@withContext seriesDetails
                }
            } else {
                Log.w(TAG, "Failed to get series $tmdbId: HTTP ${response.code}")
            }
            
            response.close()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching series $tmdbId from TMDB", e)
            null
        }
    }
    
    /**
     * Get movie details from TMDB including genres
     */
    suspend fun getMovieDetails(tmdbId: Int): TmdbMovieResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/movie/$tmdbId".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", API_KEY)
                .addQueryParameter("language", "es-ES")
                .build()
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val movieDetails = json.decodeFromString<TmdbMovieResponse>(body)
                    Log.d(TAG, "Got movie details for ID $tmdbId: ${movieDetails.genres.map { it.name }}")
                    return@withContext movieDetails
                }
            } else {
                Log.w(TAG, "Failed to get movie $tmdbId: HTTP ${response.code}")
            }
            
            response.close()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movie $tmdbId from TMDB", e)
            null
        }
    }
    
    /**
     * Get genre names from genre IDs
     */
    fun getGenreNames(genreIds: List<Int>): List<String> {
        return genreIds.mapNotNull { GENRE_MAP[it] }
    }
}
